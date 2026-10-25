# Decisions

Append-only. Agents add **Proposed** entries. Only a human marks one **Accepted** or **Rejected**, by changing the status word and adding their name. To reverse a decision, add a new entry that names the one it replaces; never rewrite an old entry.

Entry format:

```
### D<n> · <Proposed | Accepted | Rejected> · <HH:MM> · <who>
**Decision:** one sentence.
**Why:** one sentence.
**Affects:** lanes, files, or the demo.
```

## Open questions (need a human call)

When the team answers one, delete it here and log the answer below as a decision. Mirrors `PROJECT_CONTEXT.md` section 10.

- **Q1. Hardware and SDK.** Exact Ray-Ban Meta model we have, and does it work with the Wearables Device Access Toolkit? PRD says Ray-Ban Meta via DAT; not verified. **Partly answered (status files):** DAT works on our phone and glasses; the glasses' Bluetooth name is RB Meta 02WM. Still open: the marketing model and firmware version.
- **Q2. Recognition.** Do the Meta judges say a consented, on-request family face match qualifies for the accessibility exception (D7)? If not, what is Plan B, and is it Meta-only or a labeled mock? Docs read (`hub/META_SUITE.md` section 6): the Wearables AUP bars unique identification by face or voice except accessibility-type use cases with prior Meta approval; no request channel is documented and Meta's GitHub tracker declines such requests. **Answered for the hackathon by D16 (Proposed):** a Meta representative said the in-app consent form is enough here. That is not a formal accessibility-exception approval; the real-world path stays open.
- **Q3. Trigger and speech.** Docs read (`hub/LOOP.md`): no documented glasses button event or custom voice command for apps; no Muse text-to-speech found; Muse Voice Transcribe exists per third-party sources. Which trigger do we use, and is Android's built-in TTS acceptable under D5? Update (`hub/META_SUITE.md` 3.7): DAT 1.0 does have Voice Invocations ("Hey Meta, start <app>", launches the app only, gated by Developer Center setup, reported flaky in Developer Mode), plus experimental Speech and Inputs (button events, needs Meta approval). Muse Voice Transcribe is confirmed in Meta's docs. **Answered by D17 (Proposed):** spoken "Who is this?" through DAT Speech (experimental), with Android text-to-speech for output.
- **Q4. Bottle check.** On-device OCR is out (D5). What Meta-only rule counts as "agreement" for R7? **Answered by D14 (Proposed, awaiting a human).**
- **Q6. Name and tagline.** The PRD title reads "Myst (Now Myst)", which is ambiguous. Is "Myst" final?
- **Q8. PRD cleanup for RJ.** Undefined priorities (P3, P4, "?"), missing R18, truncated R12, R3 at P2 although R1/R2/R4 need identity, R5 (no retention) at P3, R16 vs R13 alert overlap. See `hub/PRD.md` hub notes.
- **Q10. Conflicting scope docs.** `META_JUDGING_GATE.md` (teammate) puts identity first and medication in P2/roadmap; the PRD (RJ) makes Medication Routine P0 and the gate doc says Meta APIs are "encouraged, not required" while D5 says Meta only. Which is the controlling scope? Until a human decides, build the loop (`hub/LOOP.md`), which both need. **Note:** the branch now builds both features, so this mostly decides what the demo leads with and where the remaining time goes.
- **Q9. Face blur and water detection.** R5 blur and R9 water check have no known Meta tool. Drop, mock or redesign? **Answered by D15 (Proposed, awaiting a human).**
- **Q11. Speech out.** Meta's Model API has no text-to-speech and DAT has no playback call (`hub/META_SUITE.md` 4, 3.6). Options: Android's built-in TextToSpeech as part of the host OS, pre-recorded clips labeled mock, or text on Meta Ray-Ban Display. Which one is acceptable under D5? **Answered by D17 (Proposed):** Android's built-in text-to-speech over Bluetooth, labeled as OS, not Meta.
- **Q12. Frames and Muse.** Use Standard-mode Muse Spark only (the `-contributor` variants let Meta train on the content). Do the Model API terms allow sending frames that show people, and how long are requests retained? Not covered on the pages read. Who checks before any frame with a person is sent? **Still open.** D16 covers consent, not Meta's retention or training terms for photos of people.
- **Q13. WhatsApp alerts.** Sending needs a server-side access token and an approved template; a token in the APK would leak. Is a small relay acceptable under D5's "no cloud backend", or do alerts become a labeled mock for the demo?
- **Q14. Open-weight Meta models.** Is self-hosting a Meta model (for example Muse Glimmer, Apache 2.0, text and image) on the phone inside "Meta stack"? It would keep frames on the phone, but it is not the Muse Spark API named in D5.

## Log

### D1 · Proposed · 00:00 · (seed, from PROJECT_CONTEXT.md)
**Decision:** The product is one story: a glasses companion that recognizes consenting loved ones and gives trusted relationship context. Person recognition plus context is P0, object help is P1, routines and caregiver tooling are roadmap, and medication adherence never leads the pitch.
**Why:** Generic vision Q&A is not differentiated for a social-company track, and a focused, truthful demo beats several simulated capabilities.
**Affects:** every lane; the demo; the pitch. A human should accept, edit or reject this on day one.

### D2 · Proposed · 00:00 · (seed)
**Decision:** Every coding agent coordinates through this hub: `AGENTS.md` at the root, plus `hub/DECISIONS.md`, `hub/status/`, and `hub/IDEAS.md`, following `hub/BUILDING.md`.
**Why:** Several people on different coding agents need one rulebook so stray ideas and invented facts do not derail the build.
**Affects:** every session in every tool.

### D3 · Proposed · 00:00 · Mayank, direct instruction recorded by Claude Code
**Decision:** The entire tech stack is Meta and Muse tools only. No third-party recognition service, model, backend or SDK. A capability Meta/Muse does not expose is blocked, mocked with a clear label, or moved to roadmap, never filled by an outside tool.
**Why:** The project targets Meta's track and must demonstrate what Meta's own platform enables; outside dependencies would also undercut the truthful-demo rule.
**Affects:** every lane; `AGENTS.md` stack section; the pre-existing `face-demo/` (CompreFace, MediaPipe) falls outside it pending Q7.

### D4 · Proposed · 15:12 · Claude Code, from the HackTX PRD Mayank pasted
**Decision:** Adopt the HackTX PRD (RJ, 2026-10-24) as product direction: two features, Familiar Faces and Medication Routine, both P0. This replaces D1's ordering where medication was roadmap and object help was P1.
**Why:** The human supplied the PRD and asked the hub to be updated to match.
**Affects:** `hub/PRD.md` rewritten; `PROJECT_CONTEXT.md` banner; `AGENTS.md` direction, medical and object-help lines; every lane.

### D5 · Proposed · 15:12 · Mayank, direct instruction recorded by Claude Code
**Decision:** Restate D3 concretely: the stack is Ray-Ban Meta glasses, the Wearables Device Access Toolkit, the Muse Spark API and the WhatsApp Cloud API, with Android as the DAT host OS. On-device OCR, face-blur libraries, a third-party face matcher, FCM and any outside SDK are not allowed; capabilities needing them are blocked, mocked or roadmap.
**Why:** The human said the stack is strictly Meta and everything comes directly from Meta.
**Affects:** PRD R3, R5, R7, R13; capability table; the PRD's OCR fallback and push alerts.

### D6 · Proposed · 15:12 · Claude Code
**Decision:** Replace the blanket "no medication verification" rule with: Myst may compare a held bottle's label to the caregiver's record, speak caregiver-entered counts, and log a dose as "reported taken". It never advises on dosing, never infers a count, and never treats a log as proof of ingestion.
**Why:** The PRD makes bottle checking core, and the safe wording keeps the medical boundary honest.
**Affects:** `AGENTS.md` safety section; R7, R10, R12; timeline wording ("reported taken", not "taken").

### D7 · Proposed · 15:12 · Claude Code
**Decision:** Put identity behind one interface. The demo path depends on the Meta judges' answer on the accessibility exception; until then it is a labeled mock. Plan B (the PRD's "family beacon") is not adopted until a human confirms it can be Meta-only.
**Why:** Meta's AUP restricts face identification, and no Meta matching API is known.
**Affects:** PRD R3, capability table; Q2.

### D8 · Proposed · 15:12 · Claude Code
**Decision:** Object finding moves to roadmap and passive approach whispering (R19) stays off and optional.
**Why:** The PRD has no object-help requirement, and passive scanning conflicts with the on-request consent rule.
**Affects:** `AGENTS.md`; `hub/IDEAS.md`; the demo scope.

### D9 · Proposed · 15:40 · Claude Code
**Decision:** The core loop is: one Android session per request, one DAT photo capture, HTTPS to Muse Spark, answer spoken through the phone's normal audio output to the glasses over Bluetooth A2DP. The mic uses HFP only when needed. Prove it with the spike in `hub/LOOP.md` before building any feature on top.
**Why:** Meta's docs give camera access through DAT and audio only through standard Bluetooth profiles, and DAT sessions are fragile, so short single-shot sessions are the safest path.
**Affects:** all lanes; PRD capability table; R1 latency target; Q3.

### D10 · Proposed · 15:55 · Mayank, direct instruction recorded by Claude Code
**Decision:** Retire `face-demo/` (webcam, MediaPipe, CompreFace). The branch `feature/face-recognition-demo` stays on the remote as an unmerged archive and is not used, extended or cited. The local containers, volume, images and files were removed. This answers Q7.
**Why:** The human said to fade it out now that the project is purely Meta's suite; it sits outside D3/D5.
**Affects:** `AGENTS.md`, `hub/BUILDING.md`, `hub/PRD.md` (existing-foundation table); nobody should recreate it. Delete the remote branch only on a human's explicit say-so.

### D11 · Proposed · 15:55 · Claude Code, at Mayank's request
**Decision:** `hub/META_SUITE.md` is the platform reference for everything Meta provides (glasses, DAT, Model API, WhatsApp, policy). Agents read it before technical work, check it before claiming or building a capability, and update it only with verified findings. It sits beside `hub/LOOP.md` (the loop spec), which it refines where the official docs differ.
**Why:** The capability table was blank and agents were guessing at what Meta exposes; the official docs now answer most rows and surface policy blockers (face identification, experimental-feature limits, Contributor-mode training).
**Affects:** every lane; `AGENTS.md` read list and hub tag (bumped to v2); `CLAUDE.md`, `GEMINI.md`, `hub/BUILDING.md`; PRD capability table; Q2, Q3, Q11 to Q14.

### D12 · Accepted (RJ, 18:40) · 18:25 · RJ, direct instruction recorded by Claude Code
**Decision:** Allow automatic face identification (`MuseIdentity`: glasses photo plus enrolled reference photos to Muse Spark, Standard mode) for private developer testing among consenting teammates only, without Meta's AUP accessibility exception. Off by default; enabled per machine with `myst.allowFaceId=true` in `app/local.properties`, and labeled "UNAPPROVED, dev only" on screen.
**Why:** RJ wants automatic recognition for the hackathon build and judged private, consented use among the team acceptable. Meta's approval was not obtained: the Wearables AUP has no developer-testing exception, so this is a policy risk the team accepts (possible loss of DAT access, weaker Meta-track standing). Q12 (Model API retention and training on photos of people) is also unanswered; the team accepts that risk too.
**Affects:** D7 (relaxed for dev testing only; the demo must not present this as Meta-approved), Q2, Q12, AGENTS.md identity and Muse rules; Lane 2; `app/` identity code. Constraints: enroll only teammates who consented; trigger only when only consenting teammates are in frame (any face in the photo is sent to Meta); never store or log photos; speak a name only above the confidence threshold.

### D13 · Proposed · 19:05 · Claude Code, for RJ (Lane 3)
**Decision:** Add `junit:junit:4.13.2` as a `testImplementation` dependency for JVM unit tests (medication state machine, ResponseGuard). Test-only; it never ships in the app.
**Why:** The task requires unit tests and Meta's DAT sample has no JVM test dependency, so under the no-new-dependency rule a human must accept it.
**Affects:** `app/app/build.gradle.kts`, `app/gradle/libs.versions.toml`; Lane 3 tests.

### D14 · Proposed · 19:05 · RJ, direct instruction recorded by Claude Code (answers Q4)
**Decision:** A bottle is confirmed only when two different kinds of evidence agree: (a) a Muse Spark comparison of the glasses photo against the caregiver's bottle reference photos picks exactly one medication at confidence >= 0.9, and (b) a separate Muse read of the label text names that same expected medication. A wrong bottle is named only when both point to the same other enrolled medication. Anything else: "please turn the bottle"; two failed tries alert the caregiver.
**Why:** RJ asked for medicine recognition that works like face matching; pairing it with an independent label read keeps a false "yes" (a stop condition) unlikely without on-device OCR (D5).
**Affects:** PRD R7, R8; Q4 (answered by this once accepted); Lane 3 bottle check.

### D15 · Proposed · 19:05 · RJ, direct instruction recorded by Claude Code (answers Q9)
**Decision:** Readiness is checked with the camera plus voice: the caregiver photographs rooms (no people in view); for "take with food" Myst guides the wearer to the food place ("let's go to the kitchen"), and when she says she is there, one glasses photo is compared with that room's references and checked for food or water in view. Any unsure result falls back to her saying "ready". Photos only on her spoken cue, never continuous.
**Why:** RJ asked for food precautions tied to going to the kitchen, detected from room photos; voice fallback keeps the routine from stalling.
**Affects:** PRD R9; Q9 (answered by this once accepted); Q12 (room photos must not show people); Lane 3.

### D16 · Proposed · 00:08 · kchperla, reporting a Meta representative's verbal answer, recorded by Claude Code
**Decision:** For the hackathon, a consent form signed in the phone app (recorded per person, editable, revocable) is accepted as Myst's privacy handling for identification. The real-world path is explained in the pitch, not built: Meta's approval for identification, a fuller consent and revocation process, and a decision on where matching runs.
**Why:** The user said Meta's head contact told the team this is good enough for a hackathon because the app has a consent sign area, and that a real product would be more complicated. This was verbal and is not written approval; it is not Meta's accessibility-exception approval, and Q12 (retention, training) is not answered by it.
**Affects:** D7 and D12 (supports D12's private, consented testing; the demo still must not say Meta approved identification); Q2; the pitch needs one slide on the real-world privacy path. Ask the contact for the answer in writing if one can be had.

### D17 · Proposed · 00:08 · Claude Code, recording what the branch built (answers Q3 and Q11)
**Decision:** The trigger is the spoken phrase "Who is this?" (and "What is this?" for medication) through the Meta DAT Speech module, labeled experimental, with a phone button as fallback. Answers are spoken by Android's built-in TextToSpeech over Bluetooth, labeled as OS speech, not Meta.
**Why:** Meta's docs offer no custom glasses button command for apps and no text-to-speech in the Model API; this is the working path RJ built and ran on real glasses.
**Affects:** `app/` Speech and Speaker code; Q3, Q11; D5 reading (Android TTS counts as the host OS); a human should accept or change this.
