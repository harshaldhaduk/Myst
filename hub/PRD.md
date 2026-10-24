# Product: Myst, a glasses companion for memory, connection and medication routine

Source: HackTX 2026 PRD by RJ (2026-10-24), pasted by Mayank; adoption is Proposed D4. Product thesis is `PROJECT_CONTEXT.md`, which this PRD supersedes where they differ (see its banner). `hub/status/` records each lane's current work. A feature listed as a target is not a claim that it is implemented.

## 1. Product and existing foundation

Myst is an Android app paired to Ray-Ban Meta glasses. The wearer (Margaret, early to mid-stage dementia, at home) hears one calm voice and never configures anything; the live-in caregiver (Robert, her husband) does all setup; remote family (Daniel, her son) wants to be recognized and know she is okay. Two features:

- **Familiar Faces:** on request, whisper the name, relationship and one caregiver-written memory of a family member who opted in.
- **Medication Routine:** at dose time, walk her through bottle, water, food if required, and confirm; alert the family about missed, wrong or doubled doses.

It is a presence problem, not a data problem: the family already knows the schedule, bottles and people; Myst moves it to her ear at the right moment. It does not treat dementia, replace caregivers, give dosing advice or monitor medically.

**Stack: Meta only** (D3, D5): Ray-Ban Meta glasses, Wearables Device Access Toolkit (DAT), Muse Spark model API, WhatsApp Cloud API. Android is the host OS the DAT requires. Nothing else.

| Already built | Boundary |
|---|---|
| Product thesis, demo story, judge answers | `PROJECT_CONTEXT.md`. Direction only. |
| Team hub and rules | `AGENTS.md`, `hub/`. |
| Webcam demo (MediaPipe + CompreFace) | Branch `feature/face-recognition-demo` only, not on `main`. Outside the Meta-only stack (Q7); not evidence of any glasses capability. |

No Meta integration has been built or verified.

## 2. Capability table (fill in from Meta docs, then the device)

"PRD claim" means the pasted PRD's cited sources say so; we have not read the docs or tried the device. Nothing below is verified.

| Need | Meta status | Demo treatment |
|---|---|---|
| Glasses model and developer access | PRD claim: Ray-Ban Meta via DAT (Android, developer mode, Mock Device Kit). Exact model unknown. | |
| Camera frame access | Docs read: DAT video stream (360x640 to 720x1280, 2 to 30 fps, limited by Bluetooth Classic) and single photo capture. Not run on a device. See `hub/LOOP.md`. | |
| Audio output to wearer | Docs read: not a DAT API. Glasses are a Bluetooth headset (A2DP), so normal Android audio plays on them. Not run on a device. | |
| Speech out (text to speech) | No Muse TTS found. Android's own TTS is the OS, not Meta (Q3). | |
| Speech in ("Who is this?", "Done") | Docs read: mic via Bluetooth HFP (8/16 kHz mono, Android 12+), and it forces output to voice quality while active. Muse Voice Transcribe exists per third-party sources only. | |
| Voice or button trigger | Docs read: custom Hey Meta commands are not in the toolkit and no glasses button event is documented. Trigger is open (Q3). | |
| Person identification | Policy-restricted: Meta AUP bars face identification without an approved accessibility exception. No Meta matching API known. Blocked pending the Meta judges' answer (D7). | |
| Family memory storage | Caregiver-entered records on the phone (app logic, no outside service). | |
| Label reading / bottle check | PRD claim: Muse Spark reads a label from an image. On-device OCR is not a Meta tool and is out (D5). | |
| Muse Spark API access | PRD claim: US public preview, OpenAI-compatible HTTP API. Call it with plain HTTPS, not a third-party SDK. | |
| Dose-time scheduling | Android alarms (host OS). Confirm it is acceptable under D5. | |
| Caregiver alerts | WhatsApp Cloud API proposed (D5). Android push (FCM) is not Meta. | |
| Face blur before Muse Spark | unknown: no Meta tool known. Alternative: send only close-up bottle frames. | |
| Object recognition, saved locations | Roadmap (D8). | |

A capability Meta does not expose is blocked, a labeled mock, or roadmap. It is never replaced with an outside tool.

## 3. Target workflows

```text
Familiar Faces
  "Who is this?" or glasses button
  -> brief capture -> identity interface (opted-in match, or "not sure")
  -> caregiver-written memory -> Muse Spark phrases it, under 20 words
  -> whisper: name, relationship, one memory

Medication Routine
  dose time (or demo control)
  -> reminder with where the bottle is kept
  -> already logged today? stop and say when
  -> she holds a bottle: label check against the caregiver's record
       wrong: name what she holds, describe the right one; 2 unclear tries -> alert Robert
  -> water, then food if required
  -> pill count (from setup only) -> "Done" by voice -> log -> timeline
  -> no response: re-remind +10, +20 min; window closes -> alert Robert
```

Identity matching and memory retrieval stay separate steps. The wearer is never quizzed or corrected ("Do you remember me?", "wrong", "you forgot" are banned).

## 4. Requirements

Priority as written in the source PRD: P0 must work in the live demo, P1 if time allows, P2 roadmap. The "Hub note" column is where the source conflicts with the hub or itself; those need a human call (see DECISIONS Q8).

| ID | Requirement | Pri | Hub note |
|---|---|---|---|
| R1 | "Who is this?" or the glasses button produces a spoken answer within 3 s. | P0 | Trigger support unverified. |
| R2 | Answer leads with name, then relationship and one caregiver-written memory, under 20 words; never quizzes or corrects. | P0 | |
| R3 | Only opted-in family can be recognized, behind one identity interface with an on-phone face match (needs Meta's accessibility exception). | P2 | R1, R2, R4 depend on identity yet R3 is P2. The demo needs a labeled mock or the exception. On-phone matcher has no known Meta tool. |
| R4 | Uncertain identity: never guess a name. Silent unprompted; if asked, "I'm not sure who that is." | P0 | |
| R5 | No frames, faces or audio retained. Identity data never leaves the phone. Faces blurred in frames sent to Muse Spark. | P3 | "P3" is undefined. `AGENTS.md` makes no-retention a hard rule at any priority. Blur has no known Meta tool. |
| R6 | At each dose time, spoken reminder including where the medication is kept. | P0 | |
| R7 | Confirm a bottle only when on-device OCR and Muse Spark agree it is the expected medication. Else ask her to turn the bottle; after two tries defer to the caregiver. | P0 | On-device OCR is out under D5. Needs a Meta-only agreement rule, e.g. two Muse Spark passes. Zero false "yes" is a stop condition. |
| R8 | Wrong bottle: name what she holds and describe the right one in her words. | P0 | |
| R9 | Before every dose check for water; if food is required, confirm she ate or guide to the listed snack. | P3 | The UX section treats this as default behavior. How water is detected is unspecified. |
| R10 | Pill count spoken only from caregiver setup. Never infer, change or advise on a dose. | P0 | |
| R11 | Already-logged dose blocks a second: "You already took your morning pills at 8:04." | P0 | |
| R12 | A dose is confirmed by voice and logged with time and bottle-check result. | P0 | Source sentence is cut off. Log says "reported taken", never "taken" (D6). |
| R13 | Re-remind at +10 and +20 min; when the window closes, push alert to caregiver. | P0 | Alert channel under D5 is WhatsApp, not FCM. |
| R14 | Caregiver enrolls family (consent recorded) and sets up each medication: label name, plain description, photos, schedule and window, water/food rule, storage location. | P0 | Enrollment must be editable and revocable (`AGENTS.md`). |
| R15 | Demo control fires a dose time immediately. | P0 (demo) | Must be labeled as a demo control. |
| R16 | Alert caregiver on wrong bottle twice, blocked double dose, or glasses disconnected 15+ min. | P2 | The UX section and R13 already promise some of these alerts. |
| R17 | Visits and doses on a shared timeline; daily summary to remote family. | P2 | |
| R18 | (absent in source) | | Numbering gap, ask RJ. |
| R19 | Optional passive mode whispers when a known family member approaches. Off by default. | "P4" | Undefined priority. Conflicts with `AGENTS.md` (no passive scanning, no assumed approach detection); stays roadmap. |
| R20 | Missed-dose alerts to remote family via WhatsApp Cloud API. | "P4" | Fits Meta-only; may become the main alert channel. |
| R21 | Pillbox check: correct day and time compartment open and empty. | "?" | Unscoped; roadmap. |

Caregiver app: four screens (Today, Medications, Family, Alerts). Phone stays quiet when things go right; it buzzes only for a missed dose, wrong bottle twice, blocked double dose, or lost glasses connection, one line each.

## 5. Live, mock, roadmap

| Label | Meaning |
|---|---|
| Live | Real Meta capability exercised on the device. |
| Mock | Scripted behavior, announced as such in the demo and in code comments. |
| Roadmap | Not built; presented only as future work. |

Use one fictional household: Margaret, her husband Robert (caregiver), her son Daniel. Do not present a mock as working, and do not claim measured accuracy, latency or battery without measuring on the device.

## 6. Success metrics and pre-build checks

Demo: bottle check right vs wrong over 10 rehearsal trials in real lighting, 10 of 10, and any false "yes" means demo the correction path with caregiver fallback; look-to-whisper under 3 s (over 5 s feels ignored); wrong-name rate zero across 20 trials including unenrolled teammates (one wrong name means raise the threshold first). Ongoing counters: false medication confirmation zero, wrong-name announcements zero, wearer distress under 1 in 10 households, non-actionable caregiver alerts at most 3 a day.

Before committing the first 4 hours (each has a stop rule):

1. **Policy check (hour 0 to 1):** ask the Meta judges whether a consented, on-request family face match qualifies for the accessibility exception. Anything but a clear yes means Plan B for the identity layer (D7).
2. **Wizard-of-Oz voice test (hours 1 to 3):** scripted whispers through the glasses with 3 outsiders; rewrite the voice if more than 1 in 3 finds a line confusing or patronizing. The source suggests phone text-to-speech for this test; that is a test aid only, not product stack.
3. **Bottle-check feasibility (hours 1 to 3):** 20 glasses frames of the demo bottles to Muse Spark; fewer than 18 of 20 right means print larger labels and add a stricter agreement rule. The source's fallback of making on-device OCR primary is not allowed under D5.

## 7. Lane boundaries

Proposed; the team assigns names in `hub/status/`. Do not infer ownership from this table.

| Lane | Responsibility |
|---|---|
| 1. Platform / device | DAT, glasses camera/audio/trigger, Muse Spark access; fills the capability table. |
| 2. Recognition + identity | Identity interface, policy path, confidence handling, uncertainty rules, the Plan B decision. |
| 3. Medication routine | Dose flow, bottle check, double-dose block, log, re-remind and alert logic. |
| 4. Experience + caregiver app | Voice wording and dignity rules, caregiver setup and consent, timeline, alerts. |
| 5. Demo / pitch | Story, reset, operator script, slides, judge Q&A. |

## 8. Done means verified

- State separately: implemented, checked locally or in the Mock Device Kit, and exercised on the real glasses.
- Never present mocks, simulators or proposed work as completed functionality.
- Record remaining gaps in the owning status file. Keep photos, memories and tokens out of Git.
