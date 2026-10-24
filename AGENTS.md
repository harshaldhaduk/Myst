# Myst: shared agent rules

**Hub v2.** Before working, read this file, `PROJECT_CONTEXT.md`, `hub/DECISIONS.md`, `hub/PRD.md`, `hub/BUILDING.md`, `hub/META_SUITE.md`, `hub/LOOP.md`, and `hub/status/`. Start the first reply in a session with `[hub v2]`. These instructions govern coding agents editing the repository, not the assistant running on the glasses.

Myst, Meta track hackathon (HackTX 2026): an Android app paired to Ray-Ban Meta glasses for people with early to mid-stage dementia living at home. Two features: Familiar Faces (recognize consenting loved ones and recall trusted shared context) and Medication Routine (guide a dose, alert the caregiver). Judging is assumed to weight a cohesive story and a truthful working demo heavily; that is a team assumption, not a published rubric.

## Tech stack: Meta only

**The entire stack is Meta: Ray-Ban Meta glasses, the Wearables Device Access Toolkit (DAT), the Muse Spark API and the WhatsApp Cloud API, with Android as the DAT host OS. Nothing else** (D3, D5). No third-party face-recognition service, vision model, LLM provider, cloud backend, database service or SDK. Everything the product does must be built from what Meta's developer tools actually expose. Not allowed: on-device OCR, face-blur or face-match libraries, FCM, outside SDKs (call Muse Spark over plain HTTPS).

- Before any technical work, read `hub/META_SUITE.md` (what Meta provides: glasses, DAT, Model API, WhatsApp, policy, with evidence labels) and `hub/LOOP.md` (the glasses-phone-Muse loop and its spike). Then re-check the live Meta docs for anything you are about to build on, and record what is available in the PRD's capability table. Search current DAT docs through Meta's Wearables MCP (`https://mcp.developer.meta.com/wearables`, tool `search_dat_docs`).
- If a capability is not available through Meta/Muse (for example, face matching or camera-frame access), it is **blocked**, not a gap to fill with an outside tool. Surface it to the human, then either redesign around what Meta offers, label it a scripted mock, or move it to roadmap.
- Do not add a dependency, API key, container or hosted service from outside Meta's tooling without a human-accepted decision that changes this rule.
- `face-demo/` (webcam, MediaPipe, CompreFace) is retired (D10). It survives only as an unmerged archive on branch `feature/face-recognition-demo`, outside the stack: do not recreate it, extend it, copy its approach into the product, or cite it as evidence of capability.

## Current system and direction

No glasses or Muse integration exists yet. Direction is `hub/PRD.md` (D4, Proposed), which supersedes `PROJECT_CONTEXT.md` where they differ: Familiar Faces and Medication Routine are both P0, with the caregiver app for setup and alerts. Object help is roadmap (D8). Status of every capability is in the PRD table; the PRD is a target, not proof of implementation.

## Sources of truth

1. Accepted decisions in `hub/DECISIONS.md`, newest applicable entry first.
2. `PROJECT_CONTEXT.md` for the product thesis, demo story and constraints; `hub/PRD.md` for requirements and status.
3. `hub/META_SUITE.md` and `hub/LOOP.md` for what the platform provides (read from Meta's docs, not yet run on a device), then Meta's and Muse's own current documentation, which wins if it differs.
4. This file and `hub/BUILDING.md` for collaboration and implementation rules.
5. Actual current code for what is implemented, plus recorded verification on real hardware.

Status files identify active work; chat recollections do not establish current behavior. Surface conflicts before overwriting another lane's work. When the human has explicitly resolved a conflict in the current task, record that instruction rather than re-asking. Agents append Proposed decisions; only humans mark Accepted or Rejected.

## Before writing and before pushing

- Run `scripts/hub-sync.sh`, then integrate with `git pull --rebase` or `git rebase origin/main` from a clean checkout. Re-read changed shared files.
- Read every status file and stay in your lane's claimed files. Coordinate cross-lane changes and name them in the commit and status update.
- Re-sync before each slice, at least every 15 minutes during long work, and before pushing. Preserve other agents' uncommitted edits. Never force-push main.
- Follow `hub/BUILDING.md`. Update your own status and relevant proposed decisions with the code they describe. Commit messages start with the lane when applicable.

## Reuse before rebuilding

**Inspect what Meta and Muse already provide before writing anything.** Start with `hub/META_SUITE.md`, then the SDK and docs for camera, audio, triggers, assistant and any recognition or memory features. Use the platform's implementation when it fits; do not hand-roll a substitute.

Custom code is for missing product behavior built on Meta tools: consent and enrollment flow, the relationship-memory content, the response composer (uncertainty handling), demo reset, and a record separating real sensor output from mocks. A new wrapper name alone is not a new capability.

## Truth, consent and safety boundaries

- Recognize only people who opted in. Enrollment must be editable and revocable. Never scan bystanders continuously; capture is brief and user-triggered.
- Never state an uncertain identification as fact. Below adequate confidence the system says it is not sure or stays silent. Never invent names, relationships or memories; every memory spoken must trace to a family-entered record.
- Label every demo element as live (real Meta/Muse capability), mock (scripted), or roadmap. A mock must never be presented as working. Do not claim Muse, SDK, battery, latency or accuracy abilities that were not verified on the actual device.
- Not medical: no diagnosis, treatment, fall detection or emergency response. Medication (D6): Myst may compare a held bottle to the caregiver's record, speak caregiver-entered counts and log a dose as "reported taken". It never advises on dosing, never infers a count, and a log is not proof a medicine was taken. A false "yes" on a wrong bottle is a stop condition. Never quiz or correct the wearer.
- No frames, faces or audio are retained. Identity data stays on the phone.
- Face identification is restricted by Meta's acceptable use policy without an approved accessibility exception (exact wording, approval status and the missing request channel: `hub/META_SUITE.md` section 6). Keep identity behind one interface and treat it as a labeled mock until a human records written approval (D7).
- Muse and personal data: use Standard-mode models only (never the `-contributor` variants, which let Meta train on the content), send a frame only when the wearer triggered it, never persist it, and never send a frame that shows a person until Q12 is answered.
- DAT features marked experimental (Speech, Inputs, Voice Invocations, Audio Streaming, standalone photo capture) are for testing only under Meta's terms. Use them for the hackathon but label them experimental in the demo and code.
- Face photos, embeddings and family memories are personal data: never commit them. Use fictional demo data (Margaret, Robert, Daniel) unless a teammate has consented.

## Scope, contracts and secrets

- Build authorized requirements from `PROJECT_CONTEXT.md`, the PRD, accepted decisions, or the human's explicit task. Put unrelated ideas in `hub/IDEAS.md`. A feature that serves neither Familiar Faces nor Medication Routine is deferred.
- Coordinate shared contracts (profile/memory record shape, recognition result shape) in one place once it exists; update callers together.
- Credentials and developer tokens stay in ignored `.env` files. Never commit keys, tokens or private photos.
- Do not call proposed code, mocks or a simulator a verified glasses deployment.

## Verified implementation facts

None yet for the Meta/Muse stack. Record each here only after it is exercised on the real device or SDK: model, camera access, audio output, trigger, recognition availability, latency, battery. Until then every such claim is unverified.

## File ownership and agent coordination

Direction and rule edits need human authorization or the lane's delegated PRD section, plus a proposed decision. Decisions are append-only; status files belong to their named person.

If subagents or other workers are used, include this at the start of their task:

> Read AGENTS.md, PROJECT_CONTEXT.md, hub/DECISIONS.md, hub/PRD.md, hub/BUILDING.md, hub/META_SUITE.md and hub/LOOP.md first. The stack is Meta and Muse tools only. Your lane is <lane>; your files are <paths>. Do not edit hub/; report findings to the parent.

Only the top-level agent edits hub files. Tool entrypoints such as CLAUDE.md and GEMINI.md point here; keep rules in this file rather than copying them.
