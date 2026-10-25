# Myst Android app

Android host app for Ray-Ban Meta glasses (DAT 1.0.1). Built vs missing per requirement is in `hub/PRD.md` section 4, device results in `AGENTS.md` "Verified implementation facts".

What it does:

- **Familiar Faces:** "Who is this?" (DAT Speech phrase, experimental; an experimental double-tap via Android `MediaSession` (play/pause or next/previous media events; end-to-end hardware verification pending); or the quiet "Backup: Who is this?" text button at the bottom of Home) takes one glasses photo, matches it with Muse Spark against enrolled reference photos (`MuseIdentity`, only in builds with face ID enabled, D12), and speaks name, relationship, when she last saw them (D18) and one caregiver memory, up to 40 words (`ResponseGuard`), with Android TextToSpeech (D17). Builds without face ID say "Face recognition isn't set up on this phone."
- **Reminders (D24; frozen for the demo, D20):** at the set time Myst says "It's time for your <name>.", "It's <location>." (free text) and, when the caregiver set them, "Take it with a glass of water." / "Have something to eat first." Re-reminds at +10/+20 min, a missed-dose notification on this phone (no remote delivery channel selected, D27). Logs DOSE_REMINDED / DOSE_REREMINDED (30 days); "Mark reported taken" logs once, a second report logs DOSE_BLOCKED_DOUBLE. No camera, no Muse, no medicine identification. No demo button: set a dose a couple of minutes ahead.
- **Setup tab:** Family (consent, photo, memories, revoke) and Reminders (add, edit with a preview of the spoken line, delete, today's timeline). Records stay in app-private storage with backup off. Android schedules an inexact reminder when exact-alarm access is unavailable; the app does not show an access warning.
- **Home:** When one pair of glasses is connected, the status pill and Glasses card show a positive battery percentage reported by DAT 1.0.1. DAT's 0% reading disagreed with Meta AI on our device, so Myst now treats 0 as unavailable. A generic ended session does not claim the glasses need a software update; only DAT's typed update-required result offers that action.
- **Removed (D24):** bottle check, room check, "What is this?", Places, the two Home buttons. Archived at git tag `archive/bottle-check-places-home-buttons`; restore a file with `git checkout archive/bottle-check-places-home-buttons -- <path>` (the tag is the commit before removal, d91d941; see what was removed with `git diff --stat archive/bottle-check-places-home-buttons efc2270 -- app/`). On first start the D24 build copies a medication's place name into its location, then deletes `places.json` and the household photos folder.

Not built: the follow-up "What did we talk about last time?", the D25 conversation nudge, demo reset, voice "Done", spoken already-taken line (R11), unit tests. A debug-only Home control runs a consented 30-second HFP/Meta ASR feasibility spike; it is not a visit or a wearer-facing feature. It buffers audio only in RAM, wipes PCM after the call, and clears the temporary on-screen transcript after 90 seconds. The first run transcribed the wearer; another person's pickup and DAT Speech sharing are not verified.

## Seeded recognition context (D30)

Debug builds add one fictional demo memory to already enrolled profiles named Krishna or RJ (case/spacing insensitive; `R. J.` also matches). Existing photos, consent, relationships and caregiver memories remain. Nothing is enrolled automatically.

- Krishna: “He brought you coffee this morning. His favorite food is dosa.”
- RJ: “You attended Hack Texas together. He just got a new dog.”

After a confident match, Myst speaks the enrolled name/relationship and both sentences through the existing 40-word guard. This bundle takes priority over the generated last-recognized timestamp and ordinary memory rotation. A long relationship falls back to name plus context. Real VISIT records are still logged normally; these stories do not create them.

Family labels the bundle “Demo context” with author “Demo seed (fictional)”; Home labels the spoken answer as scripted. Delete the seed in Family to return to ordinary memories. Deletion survives restarts, and other profiles are unchanged. The seed is separate from the five caregiver-memory slots. Source text lives in `core/records/DemoMemories.kt`; these are demo details, not verified facts about the participants.

## Build

Needs JDK 17 and the Android SDK (platform 36). `local.properties` (git-ignored) holds `sdk.dir`.

```sh
cd app
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **Face ID (D12, dev only, unapproved by Meta):** add `myst.allowFaceId=true` to `app/local.properties`. Enroll and photograph only consenting teammates.
- **Muse key (D22):** not compiled into the APK. Put `MUSE_API_KEY=...` in the repo-root `.env` (git-ignored), install the debug build, then run `scripts/provision-muse-key.sh`. It pipes the key over `adb shell run-as` on stdin; the app encrypts it with the Android Keystore on next start and deletes the plaintext. Home shows "Muse key not set" until this is done. Reinstalling with `adb install -r` keeps the key; an uninstall deletes it. The task handoff says the S23 Ultra was provisioned; this session did not inspect the key.
- Spoken lines (D21): "I think that's <Name>, your <relationship>." plus visit/memory up to 40 words; "I'm not sure who that is." (unsure or no face); "I can't check right now." (Muse, network or missing key); "I can't see right now." (capture failed); "Face recognition isn't set up on this phone." (build without face ID). A short tone (OS audio) plays right after each trigger.
- D25 debug spike needs Android microphone permission in addition to DAT's microphone permission. Start Myst first, obtain everyone's consent, then use the Home spike control. It routes Bluetooth HFP, records 30 seconds at 16 kHz, attempts a DAT photo at 10 seconds, then calls Meta Voice Transcribe and Spark. The result line reports status and timing; the transcript is temporary and never logged. Do not use this as evidence that conversation nudges work.

## Run on real glasses

1. Android phone with the Meta AI app (V292+), glasses on firmware V128+ and paired in the Meta AI app.
2. Meta AI app: Settings, App Info, tap the version five times, turn on Developer Mode. Only one Developer Mode app can be registered at a time. Installing a build signed with a different debug key needs an uninstall, which deletes app data and the registration.
3. Install the APK over USB, open Myst, register with the Meta AI app when asked, grant camera and microphone in the Meta AI app.
4. Home: turn on "Start Myst" (voice phrases, experimental double-tap, and face matching if the build has it). "How Myst works on this phone" on Home lists what is live, mock and experimental.

A single touchpad tap during an active DAT session pauses/resumes the session and never reaches the app (Meta Inputs docs). Try a double-tap, or use Home’s backup button. Android media-session inspection on the S23 Ultra showed `MEDIA_NEXT` from Bluetooth; Myst now handles that and previous-track controls as well as play/pause. This does not prove a physical double-tap reaches recognition; verify it with app tap logs, a photo and an audible answer.

If capture fails with "glasses need an update" or "device unavailable", reconnect the glasses in the Meta AI app and retry.

There is no simulated-glasses switch on screen since D19; `MockGlassesKit` and the debug camera feed remain in code for emulator wiring checks.

## Data

People, memories, sightings (D18), medications and dose events live in app-private `noBackupFilesDir` on the phone and are removed by Revoke or delete. Live glasses photos are sent to Muse Spark (Standard mode) only for face ID, only when the wearer asks and are never stored; enrolled reference photos are stored on the phone and sent with each face-ID request.
