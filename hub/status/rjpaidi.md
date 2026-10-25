# rjpaidi · Lanes 2-4: identity, medication routine, caregiver setup (+ glasses capture path)

Updated 22:45 by Claude Code

**Code lives on branch `rjpaidi/glasses-enroll`** (not merged to main yet). RJ and Claude Code are actively working Lanes 2-4, including **Lane 3 Medication Routine**; please coordinate before starting on those files.

Lane overlap: this branch includes a DAT capture path, which is kchperla's Lane 1. Coordinate before merging; if kchperla's spike lands first, swap its code in behind `GlassesGateway`.

**Shipped** (branch `rjpaidi/glasses-enroll`, not merged, not pushed)
- `app/`: Android project on DAT 1.0.1, same dependencies as Meta's CameraAccess sample (Compose, Material3, AndroidX lifecycle, exifinterface). No new outside dependency.
- `GlassesGateway` + `DatGlassesGateway`: registration through the Meta AI app, camera permission, one photo per request on a new session (stream at MEDIUM/15 fps, `stream.capturePhoto()`, the non-experimental path), shutdown `camera.stop()` then `session.stop()`. PAUSED and `CAPABILITY_DENIED` are returned as normal outcomes.
- `MockGlassesKit`: DAT Mock Device Kit glasses, labeled MOCK.
- Family enrollment: name, relationship, consent (who recorded it, when), optional glasses photo. Stored in app-private `noBackupFilesDir`, `allowBackup=false`. Revoke deletes the record and the photo. Shared schema in `core/records/Schema.kt`.
- Familiar Faces skeleton: "Who is this?" -> one glasses photo (dropped unused) -> `MockIdentity` (operator picks in Demo Controls, labeled MOCK) -> record lookup -> whisper (name, relationship, first memory) -> `ResponseGuard` -> `AndroidTtsSpeaker` (Android OS TTS over A2DP, Q11). `MuseIdentity` is a stub that sends nothing and answers Unsure (D7, Q2, Q12).
- `ResponseGuard`: blocks over 20 words, banned phrases (remember, forgot, wrong, ...), questions, and names of enrolled people other than the source record. No unit tests yet: JUnit is not in the DAT sample's dependency set, so it needs a Proposed decision first.
- Spoken trigger (EXPERIMENTAL, user's choice): a Listening switch keeps one DAT session open with `mwdat-speech` (Meta DAT module, same 1.0.1 version; inside D5). A final transcript matching "who is this / who's this / who is that" runs Familiar Faces; the camera is added to that same session for one photo, then removed. Transcripts are matched only, never stored, logged or shown. Mic permission is requested from the switch tap (Meta AI app).
- Real glasses (18:16): Listening started on the glasses (`speech start response: RESPONSE_STATUS_OK`) and the screen showed `Spoke: "krishna, your son."` with MockIdentity. Earlier attempts 18:15:49-18:16:06 failed with `START_ERROR_DEVICE_UNAVAILABLE`. Whether the voice phrase or the button triggered it, the capture light and audio on the glasses are not yet confirmed by the user.
- `MuseClient` (HttpURLConnection, Chat Completions, json_schema, reasoning_effort minimal, 15 s timeout, logs task/latency/outcome only) and a real `MuseIdentity` (live photo + enrolled glasses reference photos, `{match_id, confidence}`, threshold 0.85, else "not sure"). Compiled only behind D12: `myst.allowFaceId=true` in `app/local.properties` plus a key in `.env`. Not run: no key on this Mac and D12 not yet accepted.
- Lane 3 slice 1: `Place`, `Medication`, `Event` in the shared schema; `HouseholdStore` (app-private, no backup, photos deleted with records); Family / Medications / Places tabs; glasses photos attach as bottle and room references; "take with food" links to a food place (Kitchen by default). Verified on emulator + Mock Device Kit (Kitchen with 1 photo; Metformin with food -> Kitchen, 1 bottle photo).
- Lane 3 bottle test: "What bottle is this?" = Muse photo match against bottle reference photos + separate label read; names a bottle only if both agree and photo match >= 0.9 (D14). Request formats checked against Muse with a no-label image; not yet run on real bottles.
- Lane 3 reminders: AlarmManager per medication (exact when allowed), notification + speech on the glasses at dose time ("let's go to the kitchen first" when with food), re-remind +10/+20 min, missed-dose caregiver alert at window close (on-phone notification; WhatsApp mock, Q13), "Reported taken" button with double-dose block (R11), "Remind now" demo control (R15) and fast demo timing, AM/PM time entry, reschedule at boot. Emulator: reminder notification + TTS fired and +1/+2/+3 min alarms queued exactly.
- Stores now fsync on write and set aside unreadable files as `.corrupt` (found when an emptied file crashed the boot receiver).
- Live/Mock/Experimental labels in code (`core/Capability.kt`) and on screen.

**Verified**
- Mock Device Kit on an Android 36 arm64 emulator (2026-10-24): register state, "connected (1 device)", capture, enroll a fictional person (Daniel, son, consent by Robert), file on disk, revoke deletes it. One capture took 253 ms total (permission 18, session 7, stream 177, capture 51). That is an emulator plus mock number and says nothing about real glasses.

- Emulator + Mock Device Kit (17:48): "Who is this?" with Daniel picked spoke "Daniel, your son."; with "Someone not enrolled" spoke "I'm not sure who that is." TTS output not heard (emulator has no speaker routed).
- Real glasses (S23 Ultra, teammate's build of commit de8f4e8, classes matched): first capture attempts failed with `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`, with Meta AI app logs `Skipping openChannel serviceID=11014 after 5 consecutive failures`. After the user reconnected the glasses, two enrollments with `photoSource: GLASSES` were saved at 17:36-17:37 (76 KB, 102 KB JPEG). Capture timings were not recorded. Glasses firmware still not recorded.

- Emulator + Mock Device Kit (18:05): Listening on -> DAT Speech started (`Speech AddCapability response: active=true`) -> mock transcription "Who is this?" -> spoke "Daniel, your son." Listening off works. Not yet tried on real glasses; whether Speech needs Developer Center enablement in Developer Mode is unknown.
- Real phone: replaced the teammate's build with this Mac's build at 17:54 (user's choice); that deleted the two earlier enrollments.

- **Real glasses, automatic identity (MuseIdentity, D12, EXPERIMENTAL/UNAPPROVED), 2026-10-24, S23 Ultra, Meta AI app 295.0.0.37.164, glasses firmware not recorded:**
  - First version (single "pick one or none", reasoning minimal): 3 calls at about 1.4-1.5 s returned no name; 1 failed with JSONException. Replaced.
  - Per-reference version (face_visible + same_person/confidence per reference, reasoning low): 5 of 5 verdicts were "match" (confidence 0.95-0.99, Muse 1.5-2.5 s). 3 with one person enrolled, 2 with two enrolled ("1 of 2 said same person"). The user reports every name was correct; the log records Muse's verdict, not who was actually in front.
  - Not yet measured: a non-enrolled person (should be "not sure"), no-face tries, the PRD's 20-trial wrong-name check, end-to-end trigger-to-speech time.
- Real glasses, capture refusals: `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED` at 17:24 and 18:31, `START_ERROR_DEVICE_UNAVAILABLE` 18:15-18:16; both cleared after reconnecting. Real Speech runs end after about 60 s (`Speech session completed`); Myst now restarts them.

**Findings from the DAT 1.0.1 library (javap, not docs)**
- Correction: `session.removeCamera()` does exist (extension in `SessionCameraExtensionsKt`); my earlier note said it did not. META_SUITE 3.5's order camera.stop -> removeCamera -> session.stop is right and the code now follows it. Same pattern for `addSpeech`/`removeSpeech`.
- `DeviceSessionError.CAPABILITY_DENIED` exists in `com.meta.wearable.dat.core.types`.
- DAT app id and client token default to blank, matching kchperla's real-glasses registration (META_SUITE 3.2 says "0").
- The Mock Device Kit refuses to stream without `setCameraFeed(...)` ("No camera source configured"), even for a single photo. The feed is Meta's `plant.mp4` sample clip in `app/app/src/debug/assets` (debug builds only).

**In flight**
- Lane 3 Medication Routine (claimed 18:55, RJ + Claude Code): done so far: records, setup tabs, bottle test, reminders. Next: routine state machine + tests (needs D13), voice "Done"/"Is this right?", room/food/water checks (D15). Was: slice 1 records (Place, Medication, Event) and caregiver setup tabs; then routine state machine + tests, glasses wiring, bottle check (reference match + label read), room/food/water checks.

**Blocked, or need from someone**
- Real-glasses run: needs the S23 Ultra and glasses (kchperla) or another Android phone. This Mac has no Android phone attached.
- Face identification: D12 (Proposed by RJ) would allow it for private dev testing without Meta's AUP exception. Needs a human to mark D12 Accepted. Meta approval (D7, Q2) and Q12 remain unanswered.
- Lane 2 should confirm that storing a consented reference photo on the phone fits "no frames retained" (it is enrollment data, not a live frame).

**Files I am working in right now**
- `app/` (all), `.gitignore` (Android build lines), `hub/status/rjpaidi.md`, `hub/DECISIONS.md` (D12-D15 Proposed entries)
