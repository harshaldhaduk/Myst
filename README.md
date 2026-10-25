# Myst

### A gentle way back to the people and conversations that matter.

**Myst is a smart-glasses companion for people living with early-stage dementia or memory loss.** Imagine a loved one walking in, and a brief moment of uncertainty interrupting the greeting. Myst offers a gentle cue with a name, a relationship, and a piece of shared context so the wearer can return to the conversation. The goal is simple: help people stay connected to the people around them.

Built for **HackTX 2026 · Meta track** with Ray-Ban Meta glasses, Meta's Wearables Device Access Toolkit (DAT), Muse Spark, and Muse Voice Transcribe.

## The experience

| Moment | What Myst does |
|---|---|
| **A familiar face** | The wearer asks, **“Who am I looking at?”** Myst takes one glasses photo, compares it with consented family enrollment photos using Muse Spark, and speaks a gentle, qualified cue with the person's relationship and saved context. When the match is unclear, Myst says it is unsure. |
| **A conversation in progress** | With everyone present agreeing, the wearer says **“Start recording.”** Myst shows live transcription. A glasses double-tap asks Muse Spark for a short recap, which Myst speaks aloud; **“Stop recording”** ends the session. |
| **A daily reminder** | A caregiver sets a medicine's time, pill count, bottle description, location, and food or water instructions. Myst speaks those saved details at reminder time. The Reminders tab has **Play demo reminder** for an immediate, one-shot preview. |

Myst keeps the interaction natural: a brief cue in the glasses can help someone rejoin a greeting or conversation without reaching for a phone in front of another person.

## How it works

```text
Ray-Ban Meta glasses
  ├─ DAT Speech + one requested photo ──> Android app ──> Muse Spark ──> spoken face cue
  └─ consented microphone session ──────> Muse Voice Transcribe ──> live text
                                                        └─> Muse Spark ──> spoken recap

Caregiver-entered reminders ──> Android scheduling + speech ──> glasses / phone
```

- **Meta DAT 1.0.1** connects the Android app to the glasses for camera, speech input, and touch input.
- **Muse Spark 1.3** supplies image-based matching and short conversation recaps. Myst checks the result against the enrolled record before speaking a person's name.
- **Muse Voice Transcribe 1.0** turns an explicitly started conversation into live text.
- **Android TextToSpeech** delivers spoken cues over the glasses' Bluetooth connection. Family profiles and reminder details stay in the app's private phone storage.

## Designed around trust

Familiar Faces is an **experimental, consented developer demo**: people are enrolled by choice, a face photo is taken only after the wearer asks, and uncertain matches do not become confident names. Family enrollment and memories can be edited or revoked. Live conversation audio and text are held for the active session and cleared when it ends; the current app does not save new transcripts. Medication details are read from the caregiver's saved reminder, not inferred from a bottle photo.

Demo builds can include fictional sample relationship context for already enrolled teammates. That context is editable and separate from real recognition results.

## Try Myst

1. Pair supported Meta glasses with the Meta AI app and enable Wearables Developer Mode.
2. Build the Android app with JDK 17 and an Android SDK, then install the debug APK in place:

   ```sh
   cd app
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   For the consented face demo, set `myst.allowFaceId=true` in the ignored `app/local.properties` before building. Put `MUSE_API_KEY=...` in the ignored repo-root `.env`, then run `scripts/provision-muse-key.sh` after installing. The key is not packaged in the APK.

3. Enroll a consenting person in **Setup → Family**, or add a medication in **Setup → Reminders**.
4. Turn on **Start Myst** on Home. Ask **“Who am I looking at?”**, start a consented conversation with **“Start recording”**, or tap **Play demo reminder** in Reminders.

For device setup and permissions, see the [Android app guide](app/README.md).
