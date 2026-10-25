# Myst Android app

Android host app for Ray-Ban Meta glasses (DAT 1.0.1). Built vs missing per requirement is in `hub/PRD.md` section 4, device results in `AGENTS.md` "Verified implementation facts".

What it does:

- **Familiar Faces:** "Who is this?" through experimental DAT Speech or the quiet Home backup control takes one glasses photo, matches it with Muse Spark against enrolled reference photos (`MuseIdentity`, only in builds with face ID enabled, D12), and speaks name, relationship, recent recognized time (D18/D28) and one caregiver memory, under 20 words (`ResponseGuard`), with Android TextToSpeech (D17). The MediaSession glasses-tap guess was removed (D28); a supported physical touch trigger is not established. Builds without face ID say "Face recognition isn't set up on this phone."
- **Reminders (D24/D27):** one daily reminder per saved time; Android's time picker sets it. Myst says the caregiver-entered name, optional location, water and food rules. It posts a reminder notification on this phone. It does not identify a medicine, track whether it was taken, re-remind or declare a dose missed. Old stored records remain readable.
- **Setup tab:** Family (consent, photo, memories, revoke) and Reminders (add, edit with a spoken preview, delete, exact-alarm warning). Records stay in app-private storage with backup off.
- **Home:** When one pair of glasses is connected, the status pill and Glasses card show the battery percentage reported by DAT 1.0.1. If DAT has no valid reading, the card says "Battery unavailable". This has not yet been checked on real glasses.
- **Removed (D24):** bottle check, room check, "What is this?", Places, the two Home buttons. Archived at git tag `archive/bottle-check-places-home-buttons`; restore a file with `git checkout archive/bottle-check-places-home-buttons -- <path>` (the tag is the commit before removal, d91d941; see what was removed with `git diff --stat archive/bottle-check-places-home-buttons efc2270 -- app/`). On first start the D24 build copies a medication's place name into its location, then deletes `places.json` and the household photos folder.

Not built: the follow-up "What did we talk about last time?", the D29 sentence-break nudge, demo reset, and unit tests. A debug-only Home control runs a consented 30-second HFP/Meta ASR feasibility spike; it is not a visit or a wearer-facing feature. It buffers audio only in RAM, wipes PCM after the call, and clears the temporary on-screen transcript after 90 seconds. The first run transcribed the wearer; another person's pickup and DAT Speech sharing are not verified.

## Build

Needs JDK 17 and the Android SDK (platform 36). `local.properties` (git-ignored) holds `sdk.dir`.

```sh
cd app
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **Face ID (D12, dev only, unapproved by Meta):** add `myst.allowFaceId=true` to `app/local.properties`. Enroll and photograph only consenting teammates.
- **Muse key (D22):** not compiled into the APK. Put `MUSE_API_KEY=...` in the repo-root `.env` (git-ignored), install the debug build, then run `scripts/provision-muse-key.sh`. It pipes the key over `adb shell run-as` on stdin; the app encrypts it with the Android Keystore on next start and deletes the plaintext. Home shows "Muse key not set" until this is done. Reinstalling with `adb install -r` keeps the key; an uninstall deletes it. The task handoff says the S23 Ultra was provisioned; this session did not inspect the key.
- Spoken lines (D21): "I think that's <Name>, your <relationship>." plus visit/memory under 20 words; "I'm not sure who that is." (unsure or no face); "I can't check right now." (Muse, network or missing key); "I can't see right now." (capture failed); "Face recognition isn't set up on this phone." (build without face ID). A short tone (OS audio) plays right after each trigger.
- D25 debug spike needs Android microphone permission in addition to DAT's microphone permission. Start Myst first, obtain everyone's consent, then use the Home spike control. It routes Bluetooth HFP, records 30 seconds at 16 kHz, attempts a DAT photo at 10 seconds, then calls Meta Voice Transcribe and Spark. The result line reports status and timing; the transcript is temporary and never logged. Do not use this as evidence that conversation nudges work.

## Run on real glasses

1. Android phone with the Meta AI app (V292+), glasses on firmware V128+ and paired in the Meta AI app.
2. Meta AI app: Settings, App Info, tap the version five times, turn on Developer Mode. Only one Developer Mode app can be registered at a time. Installing a build signed with a different debug key needs an uninstall, which deletes app data and the registration.
3. Install the APK over USB, open Myst, register with the Meta AI app when asked, grant camera and microphone in the Meta AI app.
4. Home: turn on "Start Myst" (experimental DAT Speech phrase, with face matching if the build allows it). A quiet phone backup control is available if speech fails. "How Myst works on this phone" lists what is live, mock and experimental.

If capture fails with "glasses need an update" or "device unavailable", reconnect the glasses in the Meta AI app and retry.

There is no simulated-glasses switch on screen since D19; `MockGlassesKit` and the debug camera feed remain in code for emulator wiring checks.

## Data

People, memories, sightings (D18), medications and dose events live in app-private `noBackupFilesDir` on the phone and are removed by Revoke or delete. Live glasses photos are sent to Muse Spark (Standard mode) only for face ID, only when the wearer asks and are never stored; enrolled reference photos are stored on the phone and sent with each face-ID request.
