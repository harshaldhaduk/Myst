# Product: Myst, a glasses companion for memory, connection and medication routine

Source: HackTX 2026 PRD by RJ (2026-10-24), pasted by Mayank; adoption is Proposed D4. Product thesis is `PROJECT_CONTEXT.md`, which this PRD supersedes where they differ (see its banner). `hub/status/` records each lane's current work. A feature listed as a target is not a claim that it is implemented.

## 1. Product and existing foundation

Myst is an Android app paired to Ray-Ban Meta glasses. The wearer (Margaret, early to mid-stage dementia, at home) hears one calm voice and never configures anything; the live-in caregiver (Robert, her husband) does all setup; remote family (Daniel, her son) wants to be recognized and know she is okay. Two features:

- **Familiar Faces:** on request, whisper the name, relationship and one caregiver-written memory of a family member who opted in.
- **Medication Routine (D24, simplified by D27):** at a caregiver-set daily time, whisper the reminder name, where it is kept, and optional water and food rules. Myst does not identify a medicine or track whether it was taken.

It is a presence problem, not a data problem: the family already knows the schedule, medicines and people; Myst moves it to her ear at the right moment. It does not treat dementia, replace caregivers, give dosing advice or monitor medically.

**Stack: Meta only** (D3, D5): Ray-Ban Meta glasses, Wearables Device Access Toolkit (DAT), Muse Spark model API, WhatsApp Cloud API. Android is the host OS the DAT requires. Nothing else.

| Already built | Boundary |
|---|---|
| Product thesis, demo story, judge answers | `PROJECT_CONTEXT.md`. Direction only. |
| Team hub and rules | `AGENTS.md`, `hub/`. |
| Android app (`app/`, DAT 1.0.1) | On `main`. Built vs missing per requirement in section 4; device runs in `AGENTS.md` "Verified implementation facts". |
| Webcam demo (MediaPipe + CompreFace) | Retired (D10). Unmerged archive on branch `feature/face-recognition-demo`; outside the Meta-only stack and not evidence of any glasses capability. |

Medication Routine was frozen for the demo period (D20, Accepted), then reopened only for simplification under D27 (Proposed). D24 removed the bottle check, room check, "What is this?" and Places in efc2270; they remain archived at git tag `archive/bottle-check-places-home-buttons`.

## 2. Capability table (docs read, device pending)

Filled from Meta's official docs on 2026-10-24 (detail in `hub/META_SUITE.md`, D11), with the speech-to-text format rechecked on 2026-10-25. "Docs read" means Meta's page says so; "On device" means a status file records a run on our S23 Ultra and glasses (list in `AGENTS.md` "Verified implementation facts"). "Built" means the code is on `main`.

| Need | Meta status | Demo treatment |
|---|---|---|
| Glasses model and developer access | On device: DAT 1.0.1 registers in Developer Mode with blank app id and token; Meta AI app 295.0.0.37.164; Bluetooth name RB Meta 02WM. Marketing model and firmware not recorded (Q1). | Live |
| Glasses battery level | Built: Home observes DAT 1.0.1 device metadata for one connected pair and shows its reported percentage. If DAT has no valid reading, Home says battery unavailable. Not checked on real glasses. | Live DAT data; device verification pending |
| Camera frame access | Built: one photo per request (`DatGlassesGateway`, stream MEDIUM/15 fps then `capturePhoto()`). On device: glasses photos saved in Myst; refusals `..._UPDATE_REQUIRED` and `DEVICE_UNAVAILABLE` cleared by reconnecting. Capture timing not measured. | Live; one photo per request |
| Audio output to wearer | Not a DAT API; Android audio over A2DP. On device: phone audio heard on the glasses (Meta sample, user-confirmed, not timed). | Live |
| Speech out (text to speech) | No Meta TTS (docs read). Built: Android TextToSpeech (`AndroidTtsSpeaker`), labeled OS speech (D17, Proposed). On 2026-10-25 the wearer heard Myst's unsure line through the glasses from the Home backup control (user report; not timed). | OS TTS (D17) |
| Speech in ("Who is this?", "Done") | Built: DAT Speech (experimental) matches "Who is this?" only ("What is this?" removed, D24); transcripts never stored. On device: Speech starts (`RESPONSE_STATUS_OK`), sessions end after about 60 s and restart. That the phrase fired a whisper is not confirmed. "Done" not built. | Experimental |
| Conversation speech in (D25 spike) | Meta docs: `muse-voice-transcribe-1.0` accepts multipart mono 16-bit WAV at `/v1/asr/transcribe` with speaker diarization. Debug-only 30-second HFP spike built. On device, Mayank reported HFP routed, one DAT photo OK, and transcription of his speech. Other-speaker pickup and DAT Speech coexistence remain unproved; Spark summary failed BAD_ANSWER. | Experimental spike only; visit nudge paused |
| Voice or button trigger | Built: DAT Speech phrase (D17) under "Start Myst" and a quiet Home backup text button. On 2026-10-25 the backup finished with an audible unsure line; spoken attempts were intermittent. The speculative Android `MediaSession` tap handler was removed (D28); DAT Inputs is experimental and approval-gated, and no physical touch event is verified for this pair. | Experimental voice; working backup observed once; no touch claim |
| Person identification | Policy-restricted (AUP, `hub/META_SUITE.md` section 6). Built: `MuseIdentity` (Muse Spark, Standard mode, threshold 0.85) only when `myst.allowFaceId=true` (D12, Accepted by RJ, unapproved by Meta; D16 verbal). Without it, Myst says "Face recognition isn't set up on this phone." (D21; MockIdentity removed). Matches are hedged: "I think that's Daniel, your son." On device: 5 of 5 match verdicts, no negative trials. | Experimental, dev builds only (D12) |
| Family memory storage | Built: caregiver-entered memories per person (add, delete, reorder, guard-checked, max 5), rotated in the whisper; visit memory (D18) now refreshes its timestamp on repeat matches and can say "You just saw <name>" (D28, code only, device check pending). App-private, no backup. | Live (app logic); new repeat wording unverified |
| Label reading / bottle check | Removed (D24, efc2270; archived at the tag). Medication sends no frame to Muse. | Not in the demo |
| Muse Spark API access | On device: `muse-spark-1.3` over plain HTTPS works; set `reasoning_effort` (default returned empty content 2 of 3). Key is provisioned by `scripts/provision-muse-key.sh` into Keystore-encrypted app storage, not compiled into the APK (D22; task handoff reports S23 Ultra provisioning, not independently inspected this session). | Live |
| Dose-time scheduling | Built: one daily AlarmManager reminder per saved time, rescheduled at boot; Android time picker in Setup (D27). New behavior not run on the phone yet. | OS feature; device verification pending |
| Caregiver alerts | Removed from the active reminder flow by D27. WhatsApp was never built (Q13). | Not in the demo |
| Face blur before Muse Spark | No Meta tool known. Only face ID sends frames (on request, dev builds, D12); medication sends none (D24). | No blur; frames only on request for face ID |
| Object recognition, saved locations | Roadmap (D8). SAM 3.1 and Muse Spark grounding exist. | Roadmap |

A capability Meta does not expose is blocked, a labeled mock, or roadmap. It is never replaced with an outside tool.

## 3. Target workflows

```text
Familiar Faces
  "Who is this?" or quiet Home backup control
  -> brief capture -> identity interface (opted-in match, or "not sure")
  -> caregiver-written memory -> Muse Spark phrases it, under 20 words
  -> whisper: name, relationship, one memory

Medication Routine (reminders only, D24; no camera, no Muse)
  one daily time (set with Android's time picker)
  -> "It's time for your <name>." "It's <location>."
     + "Take it with a glass of water." / "Have something to eat first." if the caregiver set those rules
  -> log DOSE_REMINDED
  -> schedule tomorrow's reminder; no dose-status tracking or missed-dose alert (D27)
```

Identity matching and memory retrieval stay separate steps. The wearer is never quizzed or corrected ("Do you remember me?", "wrong", "you forgot" are banned).

## 4. Requirements

Priority as written in the source PRD: P0 must work in the live demo, P1 if time allows, P2 roadmap. Status is code on `main`; device evidence is in `AGENTS.md`. No unit tests exist (D13 not adopted). The "Hub note" column is where the source conflicts with the hub or itself; those need a human call (see DECISIONS Q8).

| ID | Requirement | Pri | Status on `main` (efc2270) | Hub note |
|---|---|---|---|---|
| R1 | "Who is this?" or the glasses button produces a spoken answer within 3 s. | P0 | Partly. DAT Speech phrase and quiet Home backup built; speculative MediaSession tap removed (D28). The backup produced one audible unsure answer on the glasses. Spoken trigger remains intermittent; 3 s not measured end to end. | Physical tap has no verified supported app event for our pair. |
| R2 | Answer leads with name, then relationship and one caregiver-written memory, under 20 words; never quizzes or corrects. | P0 | Built (name, relationship, refreshed recent-recognition line, rotated memory; `ResponseGuard` 20 words). D28 "just saw" wording built but not device-tested. | |
| R3 | Only opted-in family can be recognized, behind one identity interface with an on-phone face match (needs Meta's accessibility exception). | P2 | Built as `MuseIdentity` behind `IdentityProvider`, dev builds only (D12). Not on-phone matching: photos go to Muse. | R1, R2, R4 depend on identity yet R3 is P2. The demo needs a labeled mock or the exception. On-phone matcher has no known Meta tool. |
| R4 | Uncertain identity: never guess a name. Silent unprompted; if asked, "I'm not sure who that is." | P0 | Built: anything short of one confident match is "not sure"; a Muse failure says "I can't check right now" (D21). No stranger trials yet. | |
| R5 | No frames, faces or audio retained. Identity data never leaves the phone. Faces blurred in frames sent to Muse Spark. | P3 | Frames not stored (code). Reference photos are stored as enrollment data. No blur. | "P3" is undefined. `AGENTS.md` makes no-retention a hard rule at any priority. Blur has no known Meta tool. |
| R6 | At each caregiver-set time, spoken reminder including where the medication is kept when entered. | P0 | Built: one daily alarm and spoken reminder with optional free-text location (D27). New behavior not yet tested on the S23 Ultra. | No dose-status tracking. |
| R7 | (Removed by D24.) Was: confirm a bottle only when two reads agree it is the expected medication. | -- | Removed in efc2270; archived at tag `archive/bottle-check-places-home-buttons`. | Supersedes D14. No false-"yes" risk remains. |
| R8 | (Removed by D24.) Was: on a wrong bottle, name what she holds and describe the right one. | -- | Removed (never built). | |
| R9 | Spoken water and food rules from caregiver setup: "Take it with a glass of water.", "Have something to eat first." No detection (D24). | P3 | Built: optional lines from the caregiver's switches. New D27 screen not device-tested. | Was: check for water and confirm she ate. |
| R10 | Pill count only from caregiver setup. Never infer, change or advise on a dose. | P0 | Removed from current reminder setup and output (D27). Legacy count fields remain readable for data compatibility. | The simpler reminder makes no count claim. |
| R11 | Already-logged dose blocks a second: "You already took your morning pills at 8:04." | P0 | Removed from the active flow (D27): Myst no longer records taken/not-taken status. | Historical source requirement superseded. |
| R12 | A dose is confirmed and logged with its time. | P0 | Removed from the active flow (D27): no "Mark reported taken" or voice "Done" action. | A reminder is not proof of ingestion. |
| R13 | Re-remind at +10 and +20 min; when the window closes, push alert to caregiver. | P0 | Removed from the active flow (D27). Old follow-up alarms are cancelled or ignored; there is no missed-dose inference or alert. | WhatsApp was never built (Q13). |
| R14 | Caregiver enrolls family (consent recorded) and sets up each reminder: name, daily time, optional water/food rule and storage location. | P0 | Built: Setup tab = Family + Reminders; add/edit/delete with Android time picker and spoken preview. No timeline or dose report (D27). New screen not yet checked on a phone. | Legacy medication fields stay readable. |
| R15 | Demo control fires a dose time immediately. | P0 (demo) | No button (D19, D24). Operators set a dose a couple of minutes ahead on the Reminders page. | Must be labeled as a demo control if one returns. |
| R16 | Alert caregiver on blocked double dose (wrong-bottle alert removed, D24), or glasses disconnected 15+ min. | P2 | Not built. | The UX section and R13 already promise some of these alerts. |
| R17 | Visits and doses on a shared timeline; daily summary to remote family. | P2 | Not built (visits shown as "Last recognized" per person only). | |
| R18 | (absent in source) | | | Numbering gap, ask RJ. |
| R19 | Optional passive mode whispers when a known family member approaches. Off by default. | "P4" | Not built (roadmap). | Undefined priority. Conflicts with `AGENTS.md` (no passive scanning, no assumed approach detection); stays roadmap. |
| R20 | Missed-dose alerts to remote family via WhatsApp Cloud API. | "P4" | Not built (Q13). | Fits Meta-only; may become the main alert channel. |
| R21 | Pillbox check: correct day and time compartment open and empty. | "?" | Not built (roadmap). | Unscoped; roadmap. |
| R22 | D25 as amended by D26, both Accepted by Mayank: after a confident enrolled match, keep at most about 120 s of consented conversation PCM in RAM during a five-minute visit; on “What were we talking about?” upload an in-memory WAV and give a short, grounded nudge, then wipe audio and transcript. | P1 (target) | Not built. A debug-only 30-second HFP/ASR spike transcribed the wearer's speech once; other-speaker pickup and DAT Speech sharing remain unproved, and Spark summary failed. Stop rule applies. | No visit state, ring buffer, trigger or spoken summary exists. |

Familiar Faces pieces outside R1-R22 that are not built: the follow-up "What did we talk about last time?" (PROJECT_CONTEXT scene 2, gate A3) and a demo reset. D25/D26 direct a different in-visit nudge, but its feasibility spike has not passed. D21 (hedged name, failure wording, guard fallback, earcon) is on `main` since ef9d32c.

Caregiver app (source: four screens; built: two tabs, D19, D24, D27). Home: glasses status with DAT battery percentage when one pair is connected and a valid reading is available, "Start Myst", the last thing Myst said, and a quiet "Backup: Who is this?" text button at the bottom. Setup: Family and daily Reminders (Places removed). The phone shows a reminder notification at its scheduled time; it does not infer a missed dose or send a caregiver alert.

## 5. Live, mock, roadmap

| Label | Meaning |
|---|---|
| Live | Real Meta capability exercised on the device. |
| Mock | Scripted behavior, announced as such in the demo and in code comments. |
| Roadmap | Not built; presented only as future work. |

Use one fictional household: Margaret, her husband Robert (caregiver), her son Daniel. Do not present a mock as working, and do not claim measured accuracy, latency or battery without measuring on the device.

## 6. Success metrics and pre-build checks

Demo: ~~bottle check 10 of 10 over 10 rehearsal trials~~ (superseded by D24: no bottle check); look-to-whisper under 3 s (over 5 s feels ignored); wrong-name rate zero across 20 trials including unenrolled teammates (one wrong name means raise the threshold first). Ongoing counters: wrong-name announcements zero, wearer distress under 1 in 10 households, non-actionable caregiver alerts at most 3 a day.

Before committing the first 4 hours (each has a stop rule):

1. **Policy check (hour 0 to 1):** ask the Meta judges whether a consented, on-request family face match qualifies for the accessibility exception. Anything but a clear yes means Plan B for the identity layer (D7).
2. **Wizard-of-Oz voice test (hours 1 to 3):** scripted whispers through the glasses with 3 outsiders; rewrite the voice if more than 1 in 3 finds a line confusing or patronizing. The source suggests phone text-to-speech for this test; that is a test aid only, not product stack.
3. ~~**Bottle-check feasibility:** 20 glasses frames of the demo bottles to Muse Spark.~~ Superseded by D24 (bottle check removed); never run.

## 7. Lane boundaries

Proposed; the team assigns names in `hub/status/`. Do not infer ownership from this table.

| Lane | Responsibility |
|---|---|
| 1. Platform / device | DAT, glasses camera/audio/trigger, Muse Spark access; fills the capability table. |
| 2. Recognition + identity | Identity interface, policy path, confidence handling, uncertainty rules, the Plan B decision. |
| 3. Medication routine | One caregiver-set daily reminder per saved time, with optional location and water/food lines (D27). |
| 4. Experience + caregiver app | Voice wording and dignity rules, caregiver setup and consent, timeline, alerts. |
| 5. Demo / pitch | Story, reset, operator script, slides, judge Q&A. |

## 8. Done means verified

- State separately: implemented, checked locally or in the Mock Device Kit, and exercised on the real glasses.
- Never present mocks, simulators or proposed work as completed functionality.
- Record remaining gaps in the owning status file. Keep photos, memories and tokens out of Git.
