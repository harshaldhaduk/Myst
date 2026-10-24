# Myst Android app

Android host app for the glasses (DAT 1.0.1). Today it does one thing: connect to the glasses, take one photo, and enroll a consenting family member (name, relationship, who recorded consent) in app-private storage on the phone.

## Build

Needs JDK 17 and the Android SDK (platform 36). `local.properties` (git-ignored) holds `sdk.dir`.

```sh
cd app
JAVA_HOME=~/Library/Java/jdk-17.0.21+9/Contents/Home ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Run with no glasses (MOCK)

Any emulator or phone. Turn on "Use simulated glasses", then Take photo. The photo is a generated placeholder card and is labeled MOCK everywhere.

## Run on real glasses (LIVE)

1. Android phone with the Meta AI app (V292+), glasses on firmware V128+ and paired in the Meta AI app.
2. Meta AI app: Settings, App Info, tap the version five times, turn on Developer Mode. Only one Developer Mode app can be registered at a time.
3. Plug the phone into the laptop over USB with USB debugging on; install the APK as above.
4. In Myst: Register with Meta AI app, approve, come back. Status should read "connected".
5. Take photo. The first time, the Meta AI app asks for camera permission.

## Data

Enrollments live in `noBackupFilesDir/people/` on the phone: never uploaded, never backed up, never sent to Muse, removed by Revoke. Face identification is not built (D7, Q2, Q12).
