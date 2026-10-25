# Claude Code entry point

The team's rules live in `AGENTS.md` so every tool shares one copy. Read the files below before doing anything and follow them. Do not add hub-version prefixes to chat replies. When you dispatch a subagent, paste the subagent instruction from `AGENTS.md` into its prompt.

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
