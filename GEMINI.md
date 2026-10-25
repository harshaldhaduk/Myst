# Gemini CLI entry point

Before any work in this repo, read `AGENTS.md`, `PROJECT_CONTEXT.md`, `hub/DECISIONS.md`, `hub/PRD.md`, `hub/BUILDING.md`, `hub/META_SUITE.md`, and `hub/LOOP.md` at the repo root and follow them. They are the only rulebook; this file only points there. Do not add hub-version prefixes to chat replies.

**Mandatory, every change (full rule: "Keep the docs true" in `AGENTS.md`):**
- Update your `hub/status/<you>.md` and every doc whose described behavior changed (PRD, META_SUITE, `app/README.md`, `META_JUDGING_GATE.md` ledger) in the same commit as the code.
- Real-device results go in `AGENTS.md` "Verified implementation facts"; new choices get a Proposed decision before the code merges.
- Before ending a session, grep the docs for claims your change made false. Commit messages name the docs updated or say `docs: none needed because ...`.

@AGENTS.md
@PROJECT_CONTEXT.md
@hub/DECISIONS.md
@hub/PRD.md
@hub/BUILDING.md
@hub/META_SUITE.md
@hub/LOOP.md
