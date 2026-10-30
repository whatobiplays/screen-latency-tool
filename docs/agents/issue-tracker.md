# Issue tracker: GitHub

Issues and their task specifications live in GitHub Issues in the current repository. GitHub Issues is the sole task tracker; pull requests are an implementation review surface, not task intake.

## Repository and authentication

Resolve the repository at runtime from the connected GitHub integration or the current Git checkout. Do not rely on a repository name copied from another worktree.

Prefer the connected GitHub integration when it is available. When it is unavailable, use authenticated GitHub CLI (`gh`) as the fallback and resolve the current repository from the Git remote:

```sh
gh repo view --json nameWithOwner,url,viewerPermission
gh api user --jq .login
```

Before any issue write, confirm that the resolved repository contains the current checkout, the authenticated identity is the account intended for this operation, and that account has sufficient access. Stop before the write if any check is unexpected.

## Issue lifecycle

- Create issues with `gh issue create --title "..." --body "..."`.
- Read an issue with `gh issue view <number> --json number,title,body,labels,comments`.
- List issues with `gh issue list --state open`, adding label or state filters as needed.
- Add and remove labels with `gh issue edit <number> --add-label "..."` and `--remove-label "..."`.
- Comment on issues with `gh issue comment <number> --body "..."`.

Use the connected integration's equivalent operations when it is the selected interface.

## Closure

Repository owners control issue closure. Close an issue only after the owner explicitly authorizes closure for that issue. A merged pull request, passing checks, completed implementation, or satisfied acceptance criteria does not by itself authorize closure.

Unless the owner has explicitly authorized closure, do not use GitHub auto-closing keywords such as `Closes`, `Fixes`, or `Resolves` with an issue number in pull requests, commits, or issue text. Use a non-closing reference such as `Refs #<number>` while implementation and closure remain separate decisions.

After explicit authorization, post any required resolution comment and close the issue with `gh issue close <number> --reason completed`.
