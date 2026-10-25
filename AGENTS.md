# Myst: shared agent rules

**Hub v3.** Before working, read this file, `PROJECT_CONTEXT.md`, `hub/DECISIONS.md`, `hub/PRD.md`, `hub/BUILDING.md`, `hub/META_SUITE.md`, `hub/LOOP.md`, and `hub/status/`. Start the first reply in a session with `[hub v3]`. Follow "Keep the docs true" below on every change. These instructions govern coding agents editing the repository, not the assistant running on the glasses.

Myst, Meta track hackathon (HackTX 2026): an Android app paired to Ray-Ban Meta glasses for people with early to mid-stage dementia living at home. Two features: Familiar Faces (recognize consenting loved ones and recall trusted shared context) and Medication Routine (guide a dose, alert the caregiver). Judging is assumed to weight a cohesive story and a truthful working demo heavily; that is a team assumption, not a published rubric.

## Tech stack: Meta only

**The entire stack is Meta: Ray-Ban Meta glasses, the Wearables Device Access Toolkit (DAT), the Muse Spark API and the WhatsApp Cloud API, with Android as the DAT host OS. Nothing else** (D3, D5). No third-party face-recognition service, vision model, LLM provider, cloud backend, database service or SDK. Everything the product does must be built from what Meta's developer tools actually expose. Not allowed: on-device OCR, face-blur or face-match libraries, FCM, outside SDKs (call Muse Spark over plain HTTPS).

- Before any technical work, read `hub/META_SUITE.md` (what Meta provides: glasses, DAT, Model API, WhatsApp, policy, with evidence labels) and `hub/LOOP.md` (the glasses-phone-Muse loop and its spike). Then re-check the live Meta docs for anything you are about to build on, and record what is available in the PRD's capability table. Search current DAT docs through Meta's Wearables MCP (`https://mcp.developer.meta.com/wearables`, tool `search_dat_docs`).
- If a capability is not available through Meta/Muse (for example, face matching or camera-frame access), it is **blocked**, not a gap to fill with an outside tool. Surface it to the human, then either redesign around what Meta offers, label it a scripted mock, or move it to roadmap.
- Do not add a dependency, API key, container or hosted service from outside Meta's tooling without a human-accepted decision that changes this rule.
- `face-demo/` (webcam, MediaPipe, CompreFace) is retired (D10). It survives only as an unmerged archive on branch `feature/face-recognition-demo`, outside the stack: do not recreate it, extend it, copy its approach into the product, or cite it as evidence of capability.

## Current system and direction

An Android app on `main` (`app/`) runs on real Ray-Ban Meta glasses through DAT 1.0.1: one photo per request, DAT Speech trigger (experimental), Muse Spark over HTTPS for face matching only (D12, dev builds only), Android TTS output (D17). Medication is reminders only (D24): no camera and no Muse; the bottle check, room check, "What is this?" and Places were removed in efc2270 (archived at tag `archive/bottle-check-places-home-buttons`). What is built, mocked and missing is in the PRD tables and `hub/status/`; what ran on the device is below. Medication Routine is frozen for the demo period (D20). Direction is `hub/PRD.md` (D4, Proposed), which supersedes `PROJECT_CONTEXT.md` where they differ: Familiar Faces and Medication Routine are both P0, with the caregiver app for setup and alerts. Object help is roadmap (D8). Status of every capability is in the PRD table; the PRD is a target, not proof of implementation.

## Sources of truth

1. Accepted decisions in `hub/DECISIONS.md`, newest applicable entry first.
2. `PROJECT_CONTEXT.md` for the product thesis, demo story and constraints; `hub/PRD.md` for requirements and status.
3. `hub/META_SUITE.md` and `hub/LOOP.md` for what the platform provides (Meta's docs; rows marked verified-on-device ran on our glasses), then Meta's and Muse's own current documentation, which wins if it differs.
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
- Not medical: no diagnosis, treatment, fall detection or emergency response. Medication (D6 as narrowed by D24): Myst speaks caregiver-entered reminders, storage location, water/food rules and counts, and logs "reported taken". It never identifies a medicine, never advises on dosing, never infers a count, and a log is not proof a medicine was taken. Never quiz or correct the wearer.
- No frames, faces or audio are retained. Identity data stays on the phone. The D25 developer spike briefly holds consented HFP PCM and its transcript in RAM only; it wipes the PCM after the run and clears the on-screen transcript after 90 seconds. No ongoing visit recording is built.
- Face identification is restricted by Meta's acceptable use policy without an approved accessibility exception (exact wording, approval status and the missing request channel: `hub/META_SUITE.md` section 6). Keep identity behind one interface (D7). The only exception is D12 (Accepted by RJ): `MuseIdentity` for private testing among consenting teammates, off unless `myst.allowFaceId=true`, labeled experimental and unapproved. D16 (a Meta representative's verbal OK of the in-app consent form) is not Meta approval; the demo must never say Meta approved identification.
- Muse and personal data: use Standard-mode models only (never the `-contributor` variants, which let Meta train on the content), send a frame only when the wearer triggered it, never persist it, and never send a frame that shows a person until Q12 is answered, except D12's accepted risk (consenting teammates only in frame).
- DAT features marked experimental (Speech, Inputs, Voice Invocations, Audio Streaming, standalone photo capture) are for testing only under Meta's terms. Use them for the hackathon but label them experimental in the demo and code.
- Face photos, embeddings and family memories are personal data: never commit them. Use fictional demo data (Margaret, Robert, Daniel) unless a teammate has consented.

## Scope, contracts and secrets

- Build authorized requirements from `PROJECT_CONTEXT.md`, the PRD, accepted decisions, or the human's explicit task. Put unrelated ideas in `hub/IDEAS.md`. A feature that serves neither Familiar Faces nor Medication Routine is deferred.
- Coordinate shared contracts (profile/memory record shape, recognition result shape) in one place once it exists; update callers together.
- Credentials and developer tokens stay in ignored `.env` files. Never commit keys, tokens or private photos.
- Do not call proposed code, mocks or a simulator a verified glasses deployment.

## Verified implementation facts

Only what a status file records from the real device. Device: Samsung Galaxy S23 Ultra (SM-S918U1, Android 16), Meta AI app 295.0.0.37.164, glasses Bluetooth name RB Meta 02WM; glasses marketing model and firmware not recorded (Q1). Dates 2026-10-24 unless noted.

- DAT 1.0.1 registers in Developer Mode with blank app id and client token (Meta sample and Myst). Source: `hub/status/kchperla.md`, `hub/status/rjpaidi.md`.
- One DAT photo per request reaches Myst (two enrollments saved with `photoSource: GLASSES`, 76 and 102 KB JPEG). Capture timing not measured; about 0.5 s by feel in Meta's sample.
- Capture can refuse with `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED` or `START_ERROR_DEVICE_UNAVAILABLE`; both cleared after reconnecting the glasses.
- Phone audio plays on the glasses over A2DP (user-confirmed, Meta sample, not timed). On 2026-10-25 after the D25 spike, the user heard Myst's own unsure TTS through the glasses from the Home backup control; not timed. Source: `hub/status/mayank.md`.
- HFP mic: one silent clip, then one with sound; unexplained.
- Muse Spark (`muse-spark-1.3`, Chat Completions, plain HTTPS) answers with a glasses photo; default reasoning returned empty content 2 of 3 runs, `reasoning_effort: minimal` answered in 0.98 s (single run).
- DAT Speech starts on the glasses (`RESPONSE_STATUS_OK`, also 2026-10-25 00:50 with the D19 build); sessions end after about 60 s and Myst restarts them. That the spoken phrase itself fired a whisper is not confirmed.
- MuseIdentity (D12): 5 of 5 Muse verdicts "match" (confidence 0.95-0.99, Muse 1.5-2.5 s); the user reports every name was right. No stranger, no-face or 20-trial wrong-name runs yet; no end-to-end trigger-to-speech timing.
- 2026-10-25 D25 debug spike (same S23 Ultra and Meta AI app; glasses firmware still not recorded): user reported HFP routed, a DAT photo completed during HFP, and Meta Voice Transcribe captured the wearer's conversation about tacos. The other participant was silent in that clip, so their speech at 1 m is untested. Spark summary returned BAD_ANSWER. On a subsequent attempt the user reported "Who is this?" intermittently got no response or an earcon with no audible answer; Home's "Last thing Myst said" stayed empty after the earcon, so that face request did not complete. DAT Speech and HFP coexistence is not established. Source: `hub/status/mayank.md`.
- 2026-10-25 with the spike finished and Start Myst on, Home's quiet backup trigger aimed away from people completed: Home's last-said card updated and the wearer heard the unsure line through the glasses. User report; no timing or photo/API trace collected. Source: `hub/status/mayank.md`.
- A later build (`f7187d2`) showed DAT 0% while Meta AI showed charge; both Speech and photo sessions then failed with a generic unexpected error. `ae9d87b` restores the prior device-tested D25-era app code; its APK rebuilt and installed with `adb install -r`, but post-rollback behavior is pending. Source: `hub/status/mayank.md`.
- After rollback, Mayank reported Start Myst then showed an update warning despite Meta AI saying the glasses are current. Code inspection found `SESSION_ENDED_BY_DEVICE` was rendered as "software needs an update" and any error containing "update" enabled the update action. The correction now reserves update guidance for DAT's typed update-required result and hides battery 0 as unavailable. On the corrected APK, Mayank reports no battery percentage is shown and Start Myst returns the generic "unexpected error"; the false update warning is gone, but the DAT session failure remains. Source: `hub/status/mayank.md`.

Not verified on the device: glasses-tap trigger, dose reminders (emulator only; the D24 build was installed on the S23 Ultra but no reminder has fired there), battery, end-to-end latency.

## Keep the docs true

Every agent, every change. Code on `main` is the truth; docs must match it.

1. **Status file, same commit.** After every merged change or slice, update your `hub/status/<you>.md` sections Shipped, In flight, Verified on device, Gaps in the same commit as the code.
2. **Behavior docs, same commit.** If the change alters behavior a doc describes, edit that doc in the same commit: `hub/PRD.md` (capability table and requirement status), `hub/META_SUITE.md` labels, `app/README.md`, `META_JUDGING_GATE.md` section 9 ledger.
3. **Device results.** A real-device result goes in "Verified implementation facts" above with date, phone, Meta AI app version, glasses firmware and the status file it came from. Emulator, Mock Device Kit and user estimates are labeled as such, never as verified.
4. **Decisions first.** A new product, scope, stack or rule choice gets a Proposed entry in `hub/DECISIONS.md` before its code merges.
5. **Doc check before ending a session.** Grep the docs for claims your change made false (feature names, "not built", "mock", file and class names you renamed or deleted) and fix them.
6. **Commit message.** Name the docs updated, or write `docs: none needed because <reason>`.
7. **Subagents** report the doc changes they think are needed to the parent; the parent makes them.

## File ownership and agent coordination

Direction and rule edits need human authorization or the lane's delegated PRD section, plus a proposed decision. Decisions are append-only; status files belong to their named person.

If subagents or other workers are used, include this at the start of their task:

> Read AGENTS.md, PROJECT_CONTEXT.md, hub/DECISIONS.md, hub/PRD.md, hub/BUILDING.md, hub/META_SUITE.md and hub/LOOP.md first. The stack is Meta and Muse tools only. Your lane is <lane>; your files are <paths>. Do not edit hub/; report findings to the parent, including every doc change your work makes necessary (which file, which claim is now false, what it should say), per "Keep the docs true" in AGENTS.md.

Only the top-level agent edits hub files. Tool entrypoints such as CLAUDE.md and GEMINI.md point here; keep rules in this file rather than copying them.
