# Domain documentation

This repository uses a single-context domain documentation layout.

## Read before exploring

- Read the root `GLOSSARY.md` when it exists. If `GLOSSARY-MAP.md` exists instead, read the glossary files it identifies that are relevant to the work.
- Read relevant architecture decision records (ADRs) in `docs/adr/` before changing an area they cover.

If these files or directories do not exist, continue without creating them preemptively. Create domain documentation when terminology or an architectural decision has been established and needs to be recorded.

## Use domain terms consistently

Use glossary terms in issue titles, proposals, hypotheses, and tests. If a needed term is missing, treat it as a possible glossary gap and resolve its wording before spreading a new term.

## Surface decision conflicts

If planned work conflicts with an existing ADR, identify the ADR and explain the conflict instead of silently overriding it.

## Layout

Use `GLOSSARY.md` at the repository root for shared domain terms and `docs/adr/` for repository-wide decisions.
