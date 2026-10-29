# Issue tracker: GitHub

Issues and specifications for this repository live in GitHub Issues. Use the GitHub CLI (`gh`) for issue operations and target `whatobiplays/screen-latency-tool`.

## Conventions

- Create issues with `gh issue create --title "..." --body "..."`.
- Read an issue with `gh issue view <number> --json number,title,body,labels,comments`.
- List issues with `gh issue list --state open`, adding label or state filters as needed.
- Add and remove labels with `gh issue edit <number> --add-label "..."` and `--remove-label "..."`.
- Comment on issues with `gh issue comment <number> --body "..."`.
- Close issues after posting any needed resolution comment.

Infer the repository from its Git remote, and confirm operations target `whatobiplays/screen-latency-tool`. Pull requests are not a triage request surface in this repository.
