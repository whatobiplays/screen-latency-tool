# Issue tracker: GitHub

GitHub Issues owns actionable work: issue-level task specifications, dependencies, readiness and status, and completion evidence. An issue body is the authoritative task specification; read it before implementation. Repository documentation owns durable architecture, reference material, subsystem specifications, glossary and terminology, and architectural decision records. Pull requests are implementation and review surfaces, not task intake. Do not create a duplicate Markdown issue list.

## Resolve repository and authentication

Use the active checkout's configured `origin` as the source of truth. Before any GitHub lookup or issue operation, require exactly one configured fetch URL and exactly one effective push URL. Reject an absent or empty origin, more than one URL in either list, or a push URL that does not identify the same GitHub repository after supported protocol normalization. Resolve the surviving URL to canonical `github.com` and `OWNER/REPOSITORY`; reject another host or a malformed owner/repository. Do not substitute a repository name copied from another worktree.

Prefer an authenticated connected GitHub integration that can confirm the resolved repository and the current account's permission. If no suitable integration is available, use authenticated GitHub CLI (`gh`) as the fallback. For either path, compare host and owner/repository before operating, and recheck permission before a write.

This project is hosted on `github.com`. The following `sh` sequence performs the required read-only discovery with `gh`. It accepts common SSH and HTTPS origin forms, permits equivalent fetch and push transports when they resolve to the same repository, follows an SSH host alias, canonicalizes host case and one trailing dot, and rejects ambiguous or mismatched origins before invoking `gh`.

```sh
set -eu

normalize_host() {
  NORMALIZED_HOST=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  case "$NORMALIZED_HOST" in
    *.) NORMALIZED_HOST=${NORMALIZED_HOST%.} ;;
  esac
}

parse_github_remote() {
  remote_url=$1

  case "$remote_url" in
    git@*:*)
      transport=ssh
      remainder=${remote_url#git@}
      REMOTE_HOST=${remainder%%:*}
      REMOTE_PATH=${remainder#*:}
      ;;
    ssh://git@*/*)
      transport=ssh
      remainder=${remote_url#ssh://git@}
      REMOTE_HOST=${remainder%%/*}
      REMOTE_PATH=${remainder#*/}
      ;;
    https://*)
      transport=https
      remainder=${remote_url#https://}
      REMOTE_HOST=${remainder%%/*}
      REMOTE_PATH=${remainder#*/}
      ;;
    *)
      printf 'Unsupported origin URL: %s\n' "$remote_url" >&2
      exit 1
      ;;
  esac

  REMOTE_PATH=${REMOTE_PATH%/}
  REMOTE_PATH=${REMOTE_PATH%.git}
  case "$REMOTE_PATH" in
    */*/*|/*|*/|"")
      printf 'Origin must contain exactly OWNER/REPOSITORY: %s\n' \
        "$REMOTE_PATH" >&2
      exit 1
      ;;
    */*) ;;
    *)
      printf 'Origin must contain OWNER/REPOSITORY: %s\n' \
        "$REMOTE_PATH" >&2
      exit 1
      ;;
  esac

  normalize_host "$REMOTE_HOST"
  REMOTE_HOST=$NORMALIZED_HOST
  case "$REMOTE_HOST" in
    *:*)
      printf 'Origin host ports are unsupported: %s\n' "$REMOTE_HOST" >&2
      exit 1
      ;;
  esac

  if [ "$transport" = ssh ]; then
    canonical_host=$(ssh -G "$REMOTE_HOST" 2>/dev/null |
      awk 'tolower($1) == "hostname" { print $2; exit }')
    if [ -z "$canonical_host" ]; then
      printf 'Could not resolve SSH host: %s\n' "$REMOTE_HOST" >&2
      exit 1
    fi
    normalize_host "$canonical_host"
    REMOTE_HOST=$NORMALIZED_HOST
  fi

  if [ "$REMOTE_HOST" != github.com ]; then
    printf 'Origin host is not canonical GitHub: %s\n' "$REMOTE_HOST" >&2
    exit 1
  fi

  owner=${REMOTE_PATH%%/*}
  repository=${REMOTE_PATH#*/}
  REMOTE_REPOSITORY=$owner/$repository
}

read_origin_url() {
  description=$1
  shift

  if ! origin_urls=$("$@" 2>/dev/null); then
    printf 'Git remote origin has no %s URL.\n' "$description" >&2
    exit 1
  fi
  origin_count=$(printf '%s\n' "$origin_urls" | awk 'END { print NR }')
  if [ "$origin_count" -ne 1 ]; then
    printf 'Expected exactly one %s URL, found %s.\n' \
      "$description" "$origin_count" >&2
    exit 1
  fi

  ONE_ORIGIN_URL=$(printf '%s\n' "$origin_urls" | sed -n '1p')
  if [ -z "$ONE_ORIGIN_URL" ]; then
    printf 'Git remote origin has an empty %s URL.\n' "$description" >&2
    exit 1
  fi
}

read_origin_url 'configured fetch' git config --get-all remote.origin.url
configured_fetch_url=$ONE_ORIGIN_URL
parse_github_remote "$configured_fetch_url"
configured_fetch_host=$REMOTE_HOST
configured_fetch_repository=$REMOTE_REPOSITORY

read_origin_url 'effective fetch' git remote get-url --all origin
fetch_url=$ONE_ORIGIN_URL
parse_github_remote "$fetch_url"
fetch_host=$REMOTE_HOST
fetch_repository=$REMOTE_REPOSITORY

read_origin_url 'effective push' git remote get-url --push --all origin
push_url=$ONE_ORIGIN_URL
parse_github_remote "$push_url"
push_host=$REMOTE_HOST
push_repository=$REMOTE_REPOSITORY

configured_fetch_repository_key=$(printf '%s' "$configured_fetch_repository" |
  tr '[:upper:]' '[:lower:]')
fetch_repository_key=$(printf '%s' "$fetch_repository" |
  tr '[:upper:]' '[:lower:]')
push_repository_key=$(printf '%s' "$push_repository" |
  tr '[:upper:]' '[:lower:]')
if [ "$configured_fetch_host" != "$fetch_host" ] ||
  [ "$configured_fetch_repository_key" != "$fetch_repository_key" ]; then
  printf 'Configured and effective fetch repositories differ: %s != %s\n' \
    "$configured_fetch_repository" "$fetch_repository" >&2
  exit 1
fi
if [ "$fetch_host" != "$push_host" ] ||
  [ "$fetch_repository_key" != "$push_repository_key" ]; then
  printf 'Origin fetch and push repositories differ: %s != %s\n' \
    "$fetch_repository" "$push_repository" >&2
  exit 1
fi

GH_HOST=$fetch_host
RESOLVED_REPOSITORY=$fetch_repository
export GH_HOST RESOLVED_REPOSITORY

command -v gh >/dev/null 2>&1 ||
  { printf 'GitHub CLI is not installed.\n' >&2; exit 1; }
gh auth status --hostname "$GH_HOST" >/dev/null 2>&1 ||
  { printf 'GitHub CLI is not authenticated for %s.\n' "$GH_HOST" >&2; exit 1; }

resolved_repository=$(gh repo view "$RESOLVED_REPOSITORY" \
  --json nameWithOwner --jq .nameWithOwner)
resolved_url=$(gh repo view "$RESOLVED_REPOSITORY" --json url --jq .url)
VIEWER_PERMISSION=$(gh repo view "$RESOLVED_REPOSITORY" \
  --json viewerPermission --jq .viewerPermission)
export VIEWER_PERMISSION

if [ "$resolved_repository" != "$RESOLVED_REPOSITORY" ]; then
  printf 'Repository mismatch: origin=%s CLI=%s\n' \
    "$RESOLVED_REPOSITORY" "$resolved_repository" >&2
  exit 1
fi

case "$resolved_url" in
  "https://$GH_HOST/$RESOLVED_REPOSITORY")
    ;;
  *)
    printf 'Host or repository mismatch: %s\n' "$resolved_url" >&2
    exit 1
    ;;
esac

DISCOVERY_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
  printf 'Cannot determine the discovery checkout root.\n' >&2
  exit 1
}

# Verify the active checkout and all origin URLs before every issue operation.
# A subshell keeps remote-parser scratch variables out of the calling shell.
verify_active_checkout() (
  active_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
    printf 'The current directory is not inside a Git checkout.\n' >&2
    exit 1
  }
  if [ "$active_root" != "$DISCOVERY_ROOT" ]; then
    printf 'Checkout changed since discovery; rerun repository discovery.\n' >&2
    exit 1
  fi

  verify_origin_url() {
    description=$1
    shift
    origin_urls=$("$@" 2>/dev/null) || {
      printf 'Cannot read current %s origin URL.\n' "$description" >&2
      exit 1
    }
    origin_count=$(printf '%s\n' "$origin_urls" | awk 'END { print NR }')
    if [ -z "$origin_urls" ] || [ "$origin_count" -ne 1 ]; then
      printf 'Expected exactly one nonempty %s origin URL.\n' "$description" >&2
      exit 1
    fi
    parse_github_remote "$origin_urls"
    current_repo_key=$(printf '%s' "$REMOTE_REPOSITORY" |
      tr '[:upper:]' '[:lower:]')
    discovery_repo_key=$(printf '%s' "$RESOLVED_REPOSITORY" |
      tr '[:upper:]' '[:lower:]')
    if [ "$REMOTE_HOST" != "$GH_HOST" ] ||
      [ "$current_repo_key" != "$discovery_repo_key" ]; then
      printf 'Origin repository changed since discovery (%s); rerun discovery.\n' \
        "$description" >&2
      exit 1
    fi
  }

  verify_origin_url 'configured fetch' git config --get-all remote.origin.url
  verify_origin_url 'effective fetch' git remote get-url --all origin
  verify_origin_url 'effective push' git remote get-url --push --all origin
)

require_issue_access() {
  required_access=${1:?Specify read or write access.}
  : "${GH_HOST:?Run the discovery sequence first.}"
  : "${RESOLVED_REPOSITORY:?Run the discovery sequence first.}"

  verify_active_checkout || exit 1

  if [ "$GH_HOST" != github.com ]; then
    printf 'Unexpected GitHub host: %s\n' "$GH_HOST" >&2
    exit 1
  fi
  case "$RESOLVED_REPOSITORY" in
    */*/*|/*|*/|"")
      printf 'Invalid resolved repository: %s\n' "$RESOLVED_REPOSITORY" >&2
      exit 1
      ;;
  esac

  if ! gh auth status --hostname "$GH_HOST" >/dev/null 2>&1; then
    printf 'GitHub CLI authentication is unavailable for the active checkout.\n' >&2
    exit 1
  fi
  if ! current_repository=$(gh repo view "$RESOLVED_REPOSITORY" \
    --json nameWithOwner --jq .nameWithOwner); then
    printf 'Cannot verify the currently authenticated repository.\n' >&2
    exit 1
  fi
  if [ "$current_repository" != "$RESOLVED_REPOSITORY" ]; then
    printf 'Authenticated repository does not match the active checkout.\n' >&2
    exit 1
  fi
  if ! current_url=$(gh repo view "$RESOLVED_REPOSITORY" --json url --jq .url); then
    printf 'Cannot verify the authenticated repository host.\n' >&2
    exit 1
  fi
  if [ "$current_url" != "https://$GH_HOST/$RESOLVED_REPOSITORY" ]; then
    printf 'Authenticated repository URL does not match the active checkout.\n' >&2
    exit 1
  fi

  if ! current_permission=$(gh repo view "$RESOLVED_REPOSITORY" \
    --json viewerPermission --jq .viewerPermission); then
    printf 'Could not verify current repository permission.\n' >&2
    exit 1
  fi

  case "$required_access" in
    read)
      case "$current_permission" in
        READ|TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
        *)
          printf 'Repository permission is insufficient for issue reads: %s\n' \
            "$current_permission" >&2
          exit 1
          ;;
      esac
      ;;
    write)
      case "$current_permission" in
        TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
        *)
          printf 'Repository permission is insufficient for issue writes: %s\n' \
            "$current_permission" >&2
          exit 1
          ;;
      esac
      ;;
    *)
      printf 'Unsupported issue access mode: %s\n' "$required_access" >&2
      exit 1
      ;;
  esac

  VIEWER_PERMISSION=$current_permission
  export VIEWER_PERMISSION
}

require_issue_access read
```

When using a connected integration, perform equivalent checks before each issue operation: revalidate the active checkout and its current `origin`, resolve the same host and owner/repository, and verify current authentication and permissions (at least `TRIAGE` for writes). Stop on a changed checkout, changed origin, mismatch, or unexpected permission rather than falling back to an unverified account or repository.

Run the following snippets in the same shell as the discovery sequence, or source them from one script. Discovery defines and invokes `require_issue_access`, and exports `GH_HOST`, `RESOLVED_REPOSITORY`, and `VIEWER_PERMISSION`. Each operation invokes that one access check again before acting; set `ISSUE_NUMBER` before running an operation that targets an existing issue. Each block fails fast when a prerequisite is absent.

## Issue specifications and lifecycle

The Git repository contains canonical technical documents (see [docs/README.md](../README.md)). GitHub Issues owns actionable work, dependencies, readiness, status, implementation history, and completion evidence. Never create parallel Markdown task lists, issue mirrors, roadmap status tables, or issue-specific files.

Write one narrow, independently verifiable implementation slice per issue. Include:

1. **What to build**, without copying the subsystem specification.
2. Observable acceptance criteria.
3. **Blocked by** references to real issues in the runtime-resolved repository.
4. Explicit exclusions and non-goals.
5. Required automated evidence and, where applicable, physical qualification.

The owner decides product and design choices. Preserve approved decisions and rationale by updating the relevant canonical specification or decision record when a decision changes; do not leave durable architecture only in issue comments.

## Dependencies, readiness, and evidence

Publish prerequisite issues first. Prefer native GitHub blocking relationships where available. Otherwise, use explicit issue references in a **Blocked by** section and never duplicate dependency state into canonical documents.

Dependencies represent actual blockers, not thematic ordering, and must remain acyclic. An issue's presence does not imply that it is ready: do not start downstream work until every declared blocker is complete.

Use the [triage labels](triage-labels.md) to classify readiness. Use `ready-for-agent` for independently actionable software or documentation work, `ready-for-human` for work that requires physical assembly or bench measurements, and `needs-info` or `needs-triage` while the issue is not sufficiently defined. An issue is ready only when its prerequisite specification and tool or equipment conditions are met.

On completion, link the pull request or commit and the observable automated or physical qualification evidence to the issue. Preserve links to source specifications and qualification output. A failed qualification attempt must remain distinguishable from later successful evidence.

## Issue #1 navigation

Issue #1 is the initial documentation-publication slice for this repository, but it is not a portable task identifier for forks or checkouts with different issue history. Run this block in the same shell in which the discovery sequence above completed successfully. The block stops if Issue #1 is not the expected documentation-publication task. Do not reuse Issue #1 navigation after a mismatch; identify and confirm the active repository's current task issue before running issue operations.

```sh
require_issue_access read
ISSUE_NUMBER=1

command -v jq >/dev/null 2>&1 ||
  { printf 'jq is required to validate the Issue #1 title.\n' >&2; exit 1; }

issue_json=$(gh issue view "$ISSUE_NUMBER" --repo "$RESOLVED_REPOSITORY" \
  --json number,title,body,labels,comments,state)
if ! printf '%s\n' "$issue_json" |
  jq -e '.title == "[P0] Review and publish canonical specifications; delete superseded plans"' \
    >/dev/null; then
  printf 'Issue #1 is not the expected documentation-publication task; stop and identify the active repository task issue.\n' >&2
  exit 1
fi
printf '%s\n' "$issue_json"
```

The issue body governs the current task specification and acceptance evidence; this document defines only the shared workflow.

## Issue operations

After repository and authentication checks pass, these `gh` commands use the verified values. Each operation invokes `require_issue_access` from the discovery sequence. GitHub's `READ` role is sufficient for viewing issues; `TRIAGE`, `WRITE`, `MAINTAIN`, or `ADMIN` is required for issue management and writes. Use the connected integration's equivalent operations when it satisfies the checks above.

### Read issues

```sh
require_issue_access read
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}

gh issue view "$issue_number" --repo "$RESOLVED_REPOSITORY" \
  --json number,title,body,labels,comments
gh issue list --repo "$RESOLVED_REPOSITORY" --state open
```

### Create issues

```sh
require_issue_access write

gh issue create --repo "$RESOLVED_REPOSITORY" --title "..." --body "..."
```

### Update or comment on an issue

```sh
require_issue_access write
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}

gh issue edit "$issue_number" --repo "$RESOLVED_REPOSITORY" --add-label "..."
gh issue edit "$issue_number" --repo "$RESOLVED_REPOSITORY" --remove-label "..."
gh issue comment "$issue_number" --repo "$RESOLVED_REPOSITORY" --body "..."
```

Apply the labels documented in [triage-labels.md](triage-labels.md).

## Closure authorization

Completion evidence is necessary but insufficient for issue closure. An issue remains open until the owner explicitly authorizes closure for that specific issue.

Before closure:

1. Verify every acceptance criterion and dependency, including any required physical qualification.
2. Link the relevant pull requests, commits, tests, and qualification evidence in the issue.
3. Record any remaining risk or follow-up work that the owner must consider.
4. Obtain explicit owner authorization to close that issue.

A merged pull request, passing checks, completed implementation, or satisfied acceptance criteria does not by itself authorize closure.

Unless the owner explicitly authorizes closure, do not use GitHub auto-closing keywords such as `Closes`, `Fixes`, or `Resolves` with an issue number in pull request text, commit messages, or issue text. Use a non-closing reference such as `Refs #<number>` while implementation and closure remain separate decisions.

After explicit authorization, close the issue through the verified repository:

```sh
require_issue_access write
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}

gh issue close "$issue_number" --repo "$RESOLVED_REPOSITORY" --reason completed
```
