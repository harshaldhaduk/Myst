# Myst Android app

Android host app for Ray-Ban Meta glasses (DAT 1.0.1). State of `main` at 4289fc2; built vs missing per requirement is in `hub/PRD.md` section 4, device results in `AGENTS.md` "Verified implementation facts".

What it does:

- **Familiar Faces:** "Who is this?" (DAT Speech phrase, experimental; a glasses tap via Android `MediaSession`, untested on glasses; or the phone button) takes one glasses photo, matches it with Muse Spark against enrolled reference photos (`MuseIdentity`, only in builds with face ID enabled, D12), and speaks name, relationship, when she last saw them (D18) and one caregiver memory, under 20 words (`ResponseGuard`), with Android TextToSpeech (D17). Builds without face ID always answer "I'm not sure who that is."
- **Medicine (frozen for the demo, D20):** "What is this?" runs the bottle check (photo match plus label read, D14). Dose reminders, re-reminds at +10/+20 min, and a missed-dose notification on this phone (WhatsApp is a mock, Q13). "Reported taken" button refuses a second log for the same dose.
- **Setup tab:** Family (consent, photo, memories, revoke), Medicine, Places. Records stay in app-private storage with backup off.

Not built: the follow-up "What did we talk about last time?", demo reset, guided dose routine, wrong-bottle correction, voice "Done", room check (D15), unit tests.

## Build

Needs JDK 17 and the Android SDK (platform 36). `local.properties` (git-ignored) holds `sdk.dir`.

```sh
cd app
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **Face ID (D12, dev only, unapproved by Meta):** add `myst.allowFaceId=true` to `app/local.properties`. Enroll and photograph only consenting teammates.
- **Muse key (D22):** not compiled into the APK. Put `MUSE_API_KEY=...` in the repo-root `.env` (git-ignored), install the debug build, then run `scripts/provision-muse-key.sh`. It pipes the key over `adb shell run-as` on stdin; the app encrypts it with the Android Keystore on next start and deletes the plaintext. Home shows "Muse key not set" until this is done. Reinstalling with `adb install -r` keeps the key; an uninstall deletes it. Not yet run on a phone.
- Spoken lines (D21): "I think that's <Name>, your <relationship>." plus visit/memory under 20 words; "I'm not sure who that is." (unsure or no face); "I can't check right now." (Muse, network or missing key); "I can't see right now." (capture failed); "Face recognition isn't set up on this phone." (build without face ID). A short tone (OS audio) plays right after each trigger.

## Run on real glasses

1. Android phone with the Meta AI app (V292+), glasses on firmware V128+ and paired in the Meta AI app.
2. Meta AI app: Settings, App Info, tap the version five times, turn on Developer Mode. Only one Developer Mode app can be registered at a time. Installing a build signed with a different debug key needs an uninstall, which deletes app data and the registration.
3. Install the APK over USB, open Myst, register with the Meta AI app when asked, grant camera and microphone in the Meta AI app.
4. Home: turn on "Start Myst" (voice phrases, glasses tap, and face matching if the build has it). "How Myst works on this phone" on Home lists what is live, mock and experimental.

If capture fails with "glasses need an update" or "device unavailable", reconnect the glasses in the Meta AI app and retry.

There is no simulated-glasses switch on screen since D19; `MockGlassesKit` and the debug camera feed remain in code for emulator wiring checks.

## Data

People, memories, sightings (D18), medications and places live in app-private `noBackupFilesDir` on the phone and are removed by Revoke or delete. Live glasses photos are sent to Muse Spark (Standard mode) only when the wearer asks and are never stored; enrolled reference photos are stored on the phone and sent with each face-ID request.
