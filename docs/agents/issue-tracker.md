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

if ! git config --get-all remote.origin.url >/dev/null 2>&1; then
  printf 'Git remote origin is absent.\n' >&2
  exit 1
fi

configured_fetch_count=$(git config --get-all remote.origin.url |
  awk 'END { print NR }')
if [ "$configured_fetch_count" -ne 1 ]; then
  printf 'Expected exactly one configured origin fetch URL, found %s.\n' \
    "$configured_fetch_count" >&2
  exit 1
fi

if ! configured_fetch_url=$(git config --get remote.origin.url 2>/dev/null); then
  printf 'Git remote origin has no configured fetch URL.\n' >&2
  exit 1
fi
if [ -z "$configured_fetch_url" ]; then
  printf 'Git remote origin has an empty configured fetch URL.\n' >&2
  exit 1
fi
parse_github_remote "$configured_fetch_url"
configured_fetch_host=$REMOTE_HOST
configured_fetch_repository=$REMOTE_REPOSITORY

if ! git remote get-url --all origin >/dev/null 2>&1; then
  printf 'Git remote origin has no effective fetch URL.\n' >&2
  exit 1
fi
fetch_count=$(git remote get-url --all origin | awk 'END { print NR }')
if [ "$fetch_count" -ne 1 ]; then
  printf 'Expected exactly one effective origin fetch URL, found %s.\n' \
    "$fetch_count" >&2
  exit 1
fi
if ! fetch_url=$(git remote get-url origin 2>/dev/null); then
  printf 'Git remote origin has no effective fetch URL.\n' >&2
  exit 1
fi
if [ -z "$fetch_url" ]; then
  printf 'Git remote origin has an empty effective fetch URL.\n' >&2
  exit 1
fi
parse_github_remote "$fetch_url"
fetch_host=$REMOTE_HOST
fetch_repository=$REMOTE_REPOSITORY

if ! git remote get-url --push --all origin >/dev/null 2>&1; then
  printf 'Git remote origin has no effective push URL.\n' >&2
  exit 1
fi
push_count=$(git remote get-url --push --all origin | awk 'END { print NR }')
if [ "$push_count" -ne 1 ]; then
  printf 'Expected exactly one effective origin push URL, found %s.\n' \
    "$push_count" >&2
  exit 1
fi
if ! push_url=$(git remote get-url --push origin 2>/dev/null); then
  printf 'Git remote origin has no effective push URL.\n' >&2
  exit 1
fi
if [ -z "$push_url" ]; then
  printf 'Git remote origin has an empty effective push URL.\n' >&2
  exit 1
fi
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

case "$VIEWER_PERMISSION" in
  READ|TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository is not readable: %s\n' "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac
```

When using a connected integration, perform the equivalent checks through that integration: the authenticated connection must resolve the same host and owner/repository as `origin`, confirm read access before issue reads, and confirm at least `TRIAGE` access before issue writes. Stop on any mismatch or unexpected permission rather than falling back to an unverified account or repository.

Run the following snippets in the same shell as the discovery sequence, or source them from one script. Discovery exports `GH_HOST`, `RESOLVED_REPOSITORY`, and `VIEWER_PERMISSION`; set `ISSUE_NUMBER` before running an operation snippet. Each block fails fast when a prerequisite is absent.

## Issue operations

After repository and authentication checks pass, these `gh` commands use the verified values. GitHub's `READ` role is sufficient for viewing issues; `TRIAGE`, `WRITE`, `MAINTAIN`, or `ADMIN` is required for issue management and writes. Use the connected integration's equivalent operations when it satisfies the checks above.

### Read issues

```sh
GH_HOST=${GH_HOST:?Run the discovery sequence first.}
RESOLVED_REPOSITORY=${RESOLVED_REPOSITORY:?Run the discovery sequence first.}
VIEWER_PERMISSION=${VIEWER_PERMISSION:?Run the discovery sequence first.}
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}
export GH_HOST RESOLVED_REPOSITORY VIEWER_PERMISSION

case "$VIEWER_PERMISSION" in
  READ|TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository permission is insufficient for issue reads: %s\n' \
      "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac

gh issue view "$issue_number" --repo "$RESOLVED_REPOSITORY" \
  --json number,title,body,labels,comments
gh issue list --repo "$RESOLVED_REPOSITORY" --state open
```

### Create issues

```sh
GH_HOST=${GH_HOST:?Run the discovery sequence first.}
RESOLVED_REPOSITORY=${RESOLVED_REPOSITORY:?Run the discovery sequence first.}
VIEWER_PERMISSION=${VIEWER_PERMISSION:?Run the discovery sequence first.}
export GH_HOST RESOLVED_REPOSITORY VIEWER_PERMISSION

case "$VIEWER_PERMISSION" in
  TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository permission is insufficient for issue writes: %s\n' \
      "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac

gh issue create --repo "$RESOLVED_REPOSITORY" --title "..." --body "..."
```

### Update or comment on an issue

```sh
GH_HOST=${GH_HOST:?Run the discovery sequence first.}
RESOLVED_REPOSITORY=${RESOLVED_REPOSITORY:?Run the discovery sequence first.}
VIEWER_PERMISSION=${VIEWER_PERMISSION:?Run the discovery sequence first.}
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}
export GH_HOST RESOLVED_REPOSITORY VIEWER_PERMISSION

case "$VIEWER_PERMISSION" in
  TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository permission is insufficient for issue writes: %s\n' \
      "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac

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
GH_HOST=${GH_HOST:?Run the discovery sequence first.}
RESOLVED_REPOSITORY=${RESOLVED_REPOSITORY:?Run the discovery sequence first.}
VIEWER_PERMISSION=${VIEWER_PERMISSION:?Run the discovery sequence first.}
issue_number=${ISSUE_NUMBER:?Set ISSUE_NUMBER to the target issue number.}
export GH_HOST RESOLVED_REPOSITORY VIEWER_PERMISSION

case "$VIEWER_PERMISSION" in
  TRIAGE|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository permission is insufficient for issue writes: %s\n' \
      "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac

gh issue close "$issue_number" --repo "$RESOLVED_REPOSITORY" --reason completed
```
