# Issue tracker: GitHub

GitHub Issues is the sole task tracker for the repository resolved from the active checkout. An issue body is the authoritative task specification; read it before implementation. Pull requests are implementation and review surfaces, not task intake. Do not create a duplicate Markdown issue list.

## Resolve repository and authentication

Use the active checkout's configured `origin` as the source of truth. Resolve it to the canonical GitHub host and `OWNER/REPOSITORY` before any issue read or write. Reject an absent or ambiguous origin, a CLI or integration repository that differs from `origin`, a missing authentication session, and insufficient repository permission. Do not substitute a repository name copied from another worktree.

Prefer an authenticated connected GitHub integration that can confirm the resolved repository and the current account's permission. If no suitable integration is available, use authenticated GitHub CLI (`gh`) as the fallback. For either path, compare host and owner/repository before operating, and recheck permission before a write.

This project is hosted on `github.com`. The following `sh` sequence performs the required read-only discovery with `gh`. It intentionally supports common SSH and HTTPS origin forms for that host, follows an SSH host alias, verifies CLI authentication against the canonical host, and rejects other hosts, unsupported remote forms, or repository/permission mismatches before exposing the variables used below.

```sh
set -eu

origin_url=$(git remote get-url origin)
transport=

case "$origin_url" in
  git@*:*)
    transport=ssh
    remainder=${origin_url#git@}
    git_host=${remainder%%:*}
    repository_path=${remainder#*:}
    ;;
  ssh://git@*/*)
    transport=ssh
    remainder=${origin_url#ssh://git@}
    git_host=${remainder%%/*}
    repository_path=${remainder#*/}
    ;;
  https://*)
    transport=https
    remainder=${origin_url#https://}
    git_host=${remainder%%/*}
    repository_path=${remainder#*/}
    ;;
  *)
    printf 'Unsupported origin URL: %s\n' "$origin_url" >&2
    exit 1
    ;;
esac

repository_path=${repository_path%.git}
case "$repository_path" in
  */*/*|/*|*/|"")
    printf 'Origin must contain exactly OWNER/REPOSITORY: %s\n' \
      "$repository_path" >&2
    exit 1
    ;;
  */*) ;;
  *)
    printf 'Origin must contain OWNER/REPOSITORY: %s\n' \
      "$repository_path" >&2
    exit 1
    ;;
esac

owner=${repository_path%%/*}
repository=${repository_path#*/}

if [ "$transport" = ssh ]; then
  canonical_host=$(ssh -G "$git_host" 2>/dev/null |
    awk 'tolower($1) == "hostname" { print $2; exit }')
  if [ -z "$canonical_host" ]; then
    printf 'Could not resolve SSH host: %s\n' "$git_host" >&2
    exit 1
  fi
  git_host=$canonical_host
fi

if [ "$git_host" != github.com ]; then
  printf 'Origin host is not canonical GitHub: %s\n' "$git_host" >&2
  exit 1
fi

GH_HOST=$git_host
RESOLVED_REPOSITORY=$owner/$repository
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
  READ|WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository is not readable: %s\n' "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac
```

Before an issue write, require `WRITE`, `MAINTAIN`, or `ADMIN` access in addition to the checks above:

```sh
case "$VIEWER_PERMISSION" in
  WRITE|MAINTAIN|ADMIN) ;;
  *)
    printf 'Repository is not writable: %s\n' "$VIEWER_PERMISSION" >&2
    exit 1
    ;;
esac
```

When using a connected integration, perform the equivalent checks through that integration: the authenticated connection must resolve the same host and owner/repository as `origin`, confirm read access before issue reads, and confirm write access before mutations. Stop on any mismatch or unexpected permission rather than falling back to an unverified account or repository.

## Issue operations

After the repository and access checks pass, these `gh` commands use the verified values. Use the connected integration's equivalent operations when it satisfies the checks above.

```sh
gh issue create --repo "$RESOLVED_REPOSITORY" --title "..." --body "..."
gh issue view "$issue_number" --repo "$RESOLVED_REPOSITORY" \
  --json number,title,body,labels,comments
gh issue list --repo "$RESOLVED_REPOSITORY" --state open
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
gh issue close "$issue_number" --repo "$RESOLVED_REPOSITORY" --reason completed
```
