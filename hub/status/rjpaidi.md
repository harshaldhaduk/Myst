# rjpaidi · Lane 4 (caregiver setup) + glasses capture path

Updated 18:08 by Claude Code

Lane overlap: this branch includes a DAT capture path, which is kchperla's Lane 1. Coordinate before merging; if kchperla's spike lands first, swap its code in behind `GlassesGateway`.

**Shipped** (branch `rjpaidi/glasses-enroll`, not merged, not pushed)
- `app/`: Android project on DAT 1.0.1, same dependencies as Meta's CameraAccess sample (Compose, Material3, AndroidX lifecycle, exifinterface). No new outside dependency.
- `GlassesGateway` + `DatGlassesGateway`: registration through the Meta AI app, camera permission, one photo per request on a new session (stream at MEDIUM/15 fps, `stream.capturePhoto()`, the non-experimental path), shutdown `camera.stop()` then `session.stop()`. PAUSED and `CAPABILITY_DENIED` are returned as normal outcomes.
- `MockGlassesKit`: DAT Mock Device Kit glasses, labeled MOCK.
- Family enrollment: name, relationship, consent (who recorded it, when), optional glasses photo. Stored in app-private `noBackupFilesDir`, `allowBackup=false`. Revoke deletes the record and the photo. Shared schema in `core/records/Schema.kt`.
- Familiar Faces skeleton: "Who is this?" -> one glasses photo (dropped unused) -> `MockIdentity` (operator picks in Demo Controls, labeled MOCK) -> record lookup -> whisper (name, relationship, first memory) -> `ResponseGuard` -> `AndroidTtsSpeaker` (Android OS TTS over A2DP, Q11). `MuseIdentity` is a stub that sends nothing and answers Unsure (D7, Q2, Q12).
- `ResponseGuard`: blocks over 20 words, banned phrases (remember, forgot, wrong, ...), questions, and names of enrolled people other than the source record. No unit tests yet: JUnit is not in the DAT sample's dependency set, so it needs a Proposed decision first.
- Spoken trigger (EXPERIMENTAL, user's choice): a Listening switch keeps one DAT session open with `mwdat-speech` (Meta DAT module, same 1.0.1 version; inside D5). A final transcript matching "who is this / who's this / who is that" runs Familiar Faces; the camera is added to that same session for one photo, then removed. Transcripts are matched only, never stored, logged or shown. Mic permission is requested from the switch tap (Meta AI app).
- Live/Mock/Experimental labels in code (`core/Capability.kt`) and on screen.

**Verified**
- Mock Device Kit on an Android 36 arm64 emulator (2026-10-24): register state, "connected (1 device)", capture, enroll a fictional person (Daniel, son, consent by Robert), file on disk, revoke deletes it. One capture took 253 ms total (permission 18, session 7, stream 177, capture 51). That is an emulator plus mock number and says nothing about real glasses.

- Emulator + Mock Device Kit (17:48): "Who is this?" with Daniel picked spoke "Daniel, your son."; with "Someone not enrolled" spoke "I'm not sure who that is." TTS output not heard (emulator has no speaker routed).
- Real glasses (S23 Ultra, teammate's build of commit de8f4e8, classes matched): first capture attempts failed with `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED`, with Meta AI app logs `Skipping openChannel serviceID=11014 after 5 consecutive failures`. After the user reconnected the glasses, two enrollments with `photoSource: GLASSES` were saved at 17:36-17:37 (76 KB, 102 KB JPEG). Capture timings were not recorded. Glasses firmware still not recorded.

- Emulator + Mock Device Kit (18:05): Listening on -> DAT Speech started (`Speech AddCapability response: active=true`) -> mock transcription "Who is this?" -> spoke "Daniel, your son." Listening off works. Not yet tried on real glasses; whether Speech needs Developer Center enablement in Developer Mode is unknown.
- Real phone: replaced the teammate's build with this Mac's build at 17:54 (user's choice); that deleted the two earlier enrollments.

**Findings from the DAT 1.0.1 library (javap, not docs)**
- Correction: `session.removeCamera()` does exist (extension in `SessionCameraExtensionsKt`); my earlier note said it did not. META_SUITE 3.5's order camera.stop -> removeCamera -> session.stop is right and the code now follows it. Same pattern for `addSpeech`/`removeSpeech`.
- `DeviceSessionError.CAPABILITY_DENIED` exists in `com.meta.wearable.dat.core.types`.
- DAT app id and client token default to blank, matching kchperla's real-glasses registration (META_SUITE 3.2 says "0").
- The Mock Device Kit refuses to stream without `setCameraFeed(...)` ("No camera source configured"), even for a single photo. The feed is Meta's `plant.mp4` sample clip in `app/app/src/debug/assets` (debug builds only).

**Blocked, or need from someone**
- Real-glasses run: needs the S23 Ultra and glasses (kchperla) or another Android phone. This Mac has no Android phone attached.
- Face identification: not built. D7, Q2 and Q12 block it. Enrollment photos are reference data only.
- Lane 2 should confirm that storing a consented reference photo on the phone fits "no frames retained" (it is enrollment data, not a live frame).

**Files I am working in right now**
- `app/` (all), `.gitignore` (Android build lines), `hub/status/rjpaidi.md`
