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
- **Q10. Conflicting scope docs.** `META_JUDGING_GATE.md` (teammate) puts identity first and medication in P2/roadmap; the PRD (RJ) makes Medication Routine P0 and the gate doc says Meta APIs are "encouraged, not required" while D5 says Meta only. Which is the controlling scope? Until a human decides, build the loop (`hub/LOOP.md`), which both need. **Note:** the branch now builds both features, so this mostly decides what the demo leads with and where the remaining time goes. **Answered for the demo period by D20 (Proposed):** Familiar Faces leads; Medication Routine is frozen until after the demo, then revisit.
- **Q9. Face blur and water detection.** R5 blur and R9 water check have no known Meta tool. Drop, mock or redesign? **Answered by D15 (Proposed, awaiting a human).**
- **Q11. Speech out.** Meta's Model API has no text-to-speech and DAT has no playback call (`hub/META_SUITE.md` 4, 3.6). Options: Android's built-in TextToSpeech as part of the host OS, pre-recorded clips labeled mock, or text on Meta Ray-Ban Display. Which one is acceptable under D5? **Answered by D17 (Proposed):** Android's built-in text-to-speech over Bluetooth, labeled as OS, not Meta.
- **Q12. Frames and Muse.** Use Standard-mode Muse Spark only (the `-contributor` variants let Meta train on the content). Do the Model API terms allow sending frames that show people, and how long are requests retained? Not covered on the pages read. Who checks before any frame with a person is sent? **Still open.** D16 covers consent, not Meta's retention or training terms for photos of people.
- **Q14. Open-weight Meta models.** Is self-hosting a Meta model (for example Muse Glimmer, Apache 2.0, text and image) on the phone inside "Meta stack"? It would keep frames on the phone, but it is not the Muse Spark API named in D5.

- **Q15. Remote caregiver delivery.** The user excluded the previously proposed messaging integration (D27). In-app alert records remain in scope; any remote/background delivery needs a separately authorized channel compatible with the stack. This resolves the former Q13 proposal.

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

### D18 · Proposed · 00:40 · kchperla, direct instruction recorded by Claude Code
**Decision:** Myst keeps a visit memory: each time an enrolled person is recognized on request, it logs a VISIT event (time and identity source only). On a later "Who is this?" the whisper adds when she last saw that person, for example "Daniel, your son. You saw Daniel today at 1 PM." Sightings within 30 minutes of each other count as one encounter, so a repeat ask says the earlier encounter, not "2 minutes ago". Nothing is said for a first sighting or one older than 6 days. VISIT events older than 30 days are deleted, and revoking a person deletes their visits.
**Why:** The user wants a stable memory that reminds her of earlier meetings. This extends the one social-connection story with data Myst itself recorded, so no memory is invented.
**Affects:** `FamiliarFaces`, `HouseholdStore`, `EnrollViewModel`; the 20-word guard (visit sentence is added before the caregiver memory, which is dropped if it does not fit); retention rule in `AGENTS.md` (VISIT holds no frames or faces); a sighting made through MockIdentity is logged as MOCK. It records "last recognized by Myst", not proof of a visit.

### D19 · Proposed · 00:55 · kchperla, direct instruction recorded by Claude Code
**Decision:** The phone app becomes two tabs. Home: glasses status, one "Start Myst" switch (voice phrases, glasses tap, and face matching when the build allows it, D12), "Who is this?" / "What is this?" buttons, and the last thing Myst said. Setup: Family, Medicine and Places. Demo controls are removed from the screen: mock identity picker, simulated glasses, demo sighting backfill, fast demo timing and "Remind now (demo)". White ground with pastel blue, purple and pink.
**Why:** The user found the old screen convoluted and asked for one switch, no demo controls, and a warmer, simpler look.
**Affects:** `ui/` (new `MystApp`, `HomeScreen`, `SetupScreen`, `Components`, `theme/`; `EnrollScreen` deleted; Places content unchanged), `EnrollViewModel`. R15 (demo control that fires a dose now) has no button any more: a presenter shortcut is needed before the demo if the team wants it. Without `myst.allowFaceId=true`, "Who is this?" always answers "not sure", because the mock picker is gone. Live/mock/experimental labels moved into "How Myst works on this phone" on Home.

### D20 · Accepted (Mayank, 02:45) · 02:05 · Mayank, direct instruction recorded by Claude Code (answers Q10 for the demo period)
**Decision:** Medication Routine is frozen for the hackathon demo, temporarily: no new medication features, existing medication code stays, and the demo shows at most one reminder and one bottle check as "the same companion helps Robert". Remaining build time goes to Familiar Faces. Revisit after the demo.
**Why:** Familiar Faces carries the human-connection story and its negative cases (stranger, no memory, failure) are not yet tested; splitting time across both features risks neither being demo-ready.
**Affects:** Lane 3 (paused: guided routine state machine, R8, voice "Done", D15 room check, spoken R11 line stay unbuilt); PRD R6-R13 status; Q10; `META_JUDGING_GATE.md` scope. Recorded under Mayank's authorization for this task to update hub docs and status files.

### D21 · Accepted (Mayank, 02:45) · 02:05 · Mayank, direct instruction recorded by Claude Code
**Decision:** Remove `MockIdentity` (operator-picked identity) from the code as legacy; `MuseIdentity` (D12) is the only identity path. When face ID is not enabled on a build, Myst says recognition is not set up instead of pretending. Muse-sourced matches are hedged ("I think that's Daniel, your son..."). A Muse or network failure says "I can't check right now" instead of "I'm not sure who that is". If the guard blocks a whisper, a shorter guarded fallback is spoken instead of silence. A short earcon plays right after a trigger so the wearer knows Myst heard.
**Why:** A mock that always answers "not sure" hides whether recognition ran, a failure must not sound like a stranger (G03, A4), and an unhedged probabilistic name overstates certainty (PROJECT_CONTEXT section 6).
**Affects:** `FamiliarFaces`, `IdentityProvider`, `EnrollViewModel`, `Speaker`, `ResponseGuard` callers; D7 (no labeled identity mock remains; identity is MuseIdentity under D12/D16 or "not set up"); D18 (MOCK sightings no longer created; old ones may remain in stored data); D19. In flight on Mayank's worktree, not on main at 4289fc2. Recorded under Mayank's authorization for this task to update hub docs and status files.

### D22 · Accepted (Mayank, 02:45) · 02:05 · Mayank, direct instruction recorded by Claude Code
**Decision:** The Muse API key is no longer compiled into the APK (`BuildConfig.MUSE_API_KEY`). A script (`scripts/provision-muse-key.sh`) reads `../.env` and pipes the key through `adb shell run-as` into the debug app's private storage, where the app encrypts it with the Android Keystore and deletes the plaintext. The key is never in git and never in the APK. Rotate the old key if any APK built before this change was shared.
**Why:** A key in `BuildConfig` can be extracted from any copy of the APK.
**Affects:** `app/app/build.gradle.kts`, `MuseClient`, `EnrollViewModel`, `scripts/provision-muse-key.sh`, `app/README.md` build steps; debug builds only (run-as needs a debuggable app). In flight on Mayank's worktree, not on main at 4289fc2. Recorded under Mayank's authorization for this task to update hub docs and status files.

### D23 · Accepted (Mayank, 02:45) · 02:05 · Mayank, direct instruction recorded by Claude Code
**Decision:** Docs are updated as you go: every merged change updates its owner's status file and every doc whose described behavior it changes, in the same commit, following "Keep the docs true" in `AGENTS.md`. Hub tag bumps to v3.
**Why:** By 4289fc2 the PRD, META_SUITE, LOOP, AGENTS.md, `app/README.md` and both status files described a state hours out of date (for example "no Meta integration exists" after real-glasses face matches were recorded).
**Affects:** `AGENTS.md` (new section, v3 tag, subagent instruction), `CLAUDE.md`, `GEMINI.md`, every lane. This same task (Mayank's authorization) corrected the hub docs and status files to match main at 4289fc2; corrections are noted in each status file.

### D24 · Accepted (Mayank, 02:45) · 02:30 · Mayank, direct instruction recorded by Claude Code
**Decision:** Remove medication identification and Places: the bottle check (D14), the room check (D15), the "What is this?" command, bottle and room reference photos, and the Places tab are deleted from the code. Medication becomes reminders only: at the set time Myst whispers the medicine, where it is kept, "take it with a glass of water" and "have something to eat first" when the caregiver set those rules, with the existing re-reminds, missed-dose alert and "Reported taken" log. The Setup tab's Medicine section becomes a Reminders page (schedule plus today's timeline). The Home "Who is this?" and "What is this?" buttons are removed; triggers are the DAT Speech phrase and the glasses tap under "Start Myst". The removed code is archived at git tag `archive/bottle-check-places-home-buttons`.
**Why:** The user does not want Myst identifying medicines, only reminding; this removes the false-"yes" stop condition and bottle photos sent to Muse, and narrows the medical surface.
**Affects:** Supersedes D14 and D15 and refines D20; PRD R7, R8, R9 (food/water become spoken caregiver rules), R15; D17 loses its phone-button fallback (the demo has no on-screen trigger if speech or tap fail); Lane 3 and Lane 4 files in `app/`.

### D25 · Accepted (Mayank, 02:49) · 02:45 · Mayank, direct instruction recorded by Claude Code
**Decision:** Add a conversation nudge. During a visit (started only after "Who is this?" matched an enrolled person, auto-stopping after a few minutes), Myst keeps a rolling transcript of about the last 2 minutes in memory only, from Muse Voice Transcribe (realtime, DIARIZATION mode) over the glasses' HFP mic. When the wearer asks "What were we talking about?" (and, optionally, after a long pause of 8 s or more where she spoke last, off by default, at most one nudge every few minutes), Muse Spark (Standard mode) summarizes only that buffer in 12 words or fewer, through ResponseGuard, naming only the enrolled person; if unsure it says nothing. The transcript is never written to disk, logged or kept after the visit. Before building, a device spike must show: Muse Transcribe hears the other speaker at about 1 m, diarization separates two speakers, and a DAT photo capture still works while HFP is active. If HFP and DAT Speech cannot share the mic, Muse Transcribe also carries the "Who is this?" phrase.
**Why:** It keeps the wearer inside the conversation (the human-connection payoff) and puts real AI on the core path; Muse Transcribe keeps it in the Meta stack and adds speaker separation that DAT Speech lacks.
**Affects:** AGENTS.md retention rule ("no audio retained": a RAM-only buffer, wiped after the visit, is the exception this decision asks for), Q12 (conversation audio goes to Meta's API; retention terms still unknown), D17 (speech-in path may move to Muse Transcribe), `glasses/`, `muse/`, `features/` (new conversation package), PRD (new requirement). Supersedes the "What did we talk about last time?" follow-up for the demo.

### D26 · Accepted (Mayank, 02:49) · 02:47 · Mayank, direct task instruction recorded by Codex
**Decision:** For the first conversation nudge, amend D25's realtime transcription plan to a five-minute visit with at most 120 seconds of 16 kHz mono PCM in RAM. On an explicit “What were we talking about?” trigger, upload an in-memory WAV to Muse Voice Transcribe's file endpoint, then request a guarded Spark summary. The optional silence trigger stays off. Do not build the visit feature until the real-glasses spike proves the other speaker is audible and the trigger remains usable alongside HFP, or a separately tested Meta-only trigger replaces DAT Speech.
**Why:** Mayank requested the simpler batch design and a spike-first stop rule. The first spike routed HFP, captured a DAT photo and transcribed the wearer, but did not test the other speaker; DAT Speech response was intermittent and Spark summary returned BAD_ANSWER.
**Affects:** D25 implementation detail, `hub/PRD.md` R22, `hub/LOOP.md`, conversation code and demo claims. Accepted by Mayank; the failed spike still stops the full feature.

### D27 · Proposed · 15:24 · User's direct instruction, recorded by Codex (2026-10-24)
**Decision:** Exclude WhatsApp from the stack and implementation. This supersedes the messaging-provider portion of D5 and the corresponding parked idea. Caregiver alerts remain in-app records; no remote/background delivery channel is selected or authorized as a replacement.
**Why:** The user explicitly instructed, "no whatsapp, edit those docs". This entry records that instruction; it does not claim that a new notification capability exists.
**Affects:** `AGENTS.md`, `PROJECT_CONTEXT.md`, `hub/PRD.md` stack/capability table/R13/R20/caregiver UI wording, and `hub/IDEAS.md`. Historical entries remain unchanged; only a human changes decision status words under the hub rules.

### D28 · Proposed · 01:48 · User's direct instruction, recorded by Codex (2026-10-25)
**Decision:** Remove hub-version prefixes from agent replies and remove the corresponding requirements from the shared rules and tool entrypoints. This supersedes the reply-tag conventions in D11 and D23 without changing the platform reference or collaboration rules.
**Why:** The user explicitly asked to remove the tag after it was repeatedly included in replies.
**Affects:** `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`; no application behavior changes.

### D29 · Proposed · 2026-10-25 · User-reported touchpad failure, recorded by Codex
**Decision:** Correct the experimental Bluetooth trigger to handle next/previous-track media events as well as play/pause, and describe double-tap as the gesture to verify. A single touchpad tap is not an application trigger during a DAT session; retain the quiet Home backup control.
**Why:** The user reports a sound and no Home screen change. On the connected S23 Ultra, Myst owns the media-button session, but Android recorded Bluetooth MEDIA_NEXT events that the old handler consumed without invoking recognition. Current Meta Inputs docs say the device reserves single tap for session pause/resume; it never reaches the app.
**Affects:** `GlassesTapTrigger`, Home instructions, `EnrollViewModel` confirmation, PRD trigger row, META_SUITE, app README and H4 ledger. No new dependency or capture mode; physical double-tap recognition remains unverified until exercised by the wearer.

### D30 · Proposed · 2026-10-25 · User's demo-memory instruction, recorded by Codex
**Decision:** In debug builds, seed a fictional memory bundle once onto already enrolled Krishna and RJ profiles: Krishna brought coffee this morning and likes dosa; the wearer attended Hack Texas with RJ, who just got a new dog. After a real confident match, speak the enrolled name/relationship plus that bundle, through the existing guard. Label the source as scripted demo context in Family and the last-answer note. Do not create profiles, photos, consent, recognition results or VISIT events from the seed.
**Why:** The user explicitly requested hardcoded context for these two demo subjects. The full bundle must survive the 20-word limit instead of being displaced by an automatic last-recognized timestamp.
**Affects:** PeopleStore, Person JSON (backward-compatible seed marker), DemoMemories, FamiliarFaces, memory UI/limits, PRD, app README, platform reference and H3 ledger. Existing caregiver memories are preserved; deleting the seed persists across restarts. Other people retain normal visit/memory behavior. Dignity/uncertainty and recognition guards remain in effect; this is scripted context, not evidence of a recorded conversation.

### D31 · Proposed · 2026-10-25 · User's direct instruction, recorded by Codex
**Decision:** Raise the spoken-answer word limit from 20 to 40 words. ResponseGuard, visit composition and memory-entry validation use the shared limit; update user-facing guidance and active specs. Other source, identity, uncertainty and dignity checks remain unchanged.
**Why:** The user explicitly instructed, “there is no need for a 20 word guard, make it 40”. This supersedes earlier decisions' 20-word references, including D18 and D30.
**Affects:** ResponseGuard, FamiliarFaces comments, EnrollViewModel guidance, PRD R2, app README, platform diagram, Codex status and validation. Teammates' old status entries describe prior builds, not the current limit.

### D32 · Proposed · 2026-10-25 · User's direct instruction, recorded by Codex
**Decision:** Remove demo wording from the app's visible memory UI. Family uses “Shared context” and “Saved context”; the last-answer note uses “Saved relationship context.” Keep the existing internal provenance marker so stored seeds remain identifiable without reseeding or changing their behavior.
**Why:** The user explicitly asked to remove demo references from the app. This supersedes D30's particular on-screen labels; the repository still documents that the supplied stories are scripted rather than recorded conversations.
**Affects:** DemoMemories display note, SetupScreen labels/attribution, AGENTS scope note, app README, PRD, H3 ledger and Codex status. Recognition, saved text, 40-word guard, memory deletion and real visit logging are unchanged.

### D33 · Proposed · 2026-10-25 · Mayank's live-conversation direction, recorded by Codex
**Decision:** Add an on-request Conversation feature alongside Familiar Faces and Medication Routine. With everyone present consenting, “Start recording” begins HFP audio streamed to Muse Voice Transcribe realtime; “Stop recording” ends it. A glasses double-tap through DAT Inputs CAPTOUCH Select, or a phone backup button, sends the session transcript to Muse Spark for a short guarded recap spoken through the glasses. New sessions keep audio and transcript in memory only and clear them at the end; an on-screen recap may remain until the next session. Earlier locally saved transcripts are left untouched, with no new writes. The old 30-second spike control and Bluetooth MediaSession fallback are removed. This replaces the unbuilt visit-nudge direction in D25/D26 for the current demo.
**Why:** Mayank changed the interaction to explicit conversation mode, validated start/stop and spoken double-tap recap on the real glasses, requested a simpler card and RAM-only new sessions, and found the MediaSession fallback could hand playback to Spotify. The current phrase and compact layout are his latest instructions.
**Affects:** VoiceCommands, DAT Inputs, HFP recorder, Muse realtime client and recap, Home, PRD R22, platform/loop docs, judging ledger, app README and status. DAT Inputs remains experimental and physical behavior is based on the reported glasses run; second-speaker pickup is not established.
