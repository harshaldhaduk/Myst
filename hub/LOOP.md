# The loop: glasses -> phone -> Muse -> glasses

Everything else (faces, assistant, objects, medication) rides on this. Status: **docs read 2026-10-24, nothing run on a device.** Sources are Meta's Wearables Device Access Toolkit (DAT) docs unless marked "third-party"; treat third-party items as unverified. Proposed as D9.

## 1. The path

```text
Glasses                        Android phone (our app)                    Meta cloud
  camera --Bluetooth Classic--> DAT: session -> photo capture / video frames
                                 |  downscale + JPEG
                                 +--HTTPS--------------------------------> Muse Spark (image + text)
                                 <-------------------------------------- text answer
                                 guard (uncertainty, source records) -> text-to-speech (open, see 4)
  speaker <--Bluetooth A2DP----- normal Android audio output
  mic ---Bluetooth HFP--------> setCommunicationDevice, record (optional)
```

Each leg:

| Leg | Mechanism | Source |
|---|---|---|
| Glasses to phone, video | DAT camera stream. Quality presets HIGH 720x1280, MEDIUM 504x896, LOW 360x640; fps 2, 7, 15, 24 or 30. Bluetooth Classic limits it: DAT drops resolution a step first, then fps (not below 15). | DAT docs, via search; confirm on the camera page |
| Glasses to phone, still | DAT single photo capture. | DAT docs |
| Phone to Muse | Plain HTTPS, image as a message part. Muse Spark is a vision-capable model. $50 credits. | third-party gateways; Meta Model API docs not yet read |
| Phone to glasses, audio | **No DAT API.** The glasses are an ordinary Bluetooth headset. A2DP: output only, stereo, high quality. Play anything through normal Android audio and it routes to them. | DAT mic/speaker page (the page shows iOS only; no Android playback sample) |
| Glasses to phone, mic | HFP: bidirectional but 8 or 16 kHz mono, beamformed to the wearer. Android 12+, `MODE_IN_COMMUNICATION` plus `setCommunicationDevice`. A2DP and HFP are mutually exclusive: while HFP is active, output drops to voice quality. | DAT mic/speaker page |
| Mic via camera stream | PCM audio can ride the camera stream, but only in dev and beta release channels, and the API may change. | DAT mic/speaker page |

Setup: Maven Central `com.meta.wearable` (`mwdat-core`, `mwdat-camera`, `mwdat-mockdevice`), `Wearables.initialize(context)` once, register through the Meta AI app, Bluetooth and Internet permissions, Developer Mode with `APPLICATION_ID` and `CLIENT_TOKEN` both `0`. Mock Device Kit simulates a glasses device with no hardware. Sample app: `github.com/facebook/meta-wearables-dat-android`.

## 2. Constraints that shape the design

- **Developer preview.** No Play Store distribution; registration needs internet.
- **Sessions are not reconfigurable and a stopped one cannot restart.** Make a new session per request or per interaction.
- **Known issues (Meta, May 2026):** rapid captures on a stream running over a minute crash the Android SDK intermittently; `DeviceSession` is unreliable together with a camera stream; a stream started while the glasses are off the face pauses on put-on (a side tap resumes it).
- **Method names differ between docs and community mirrors** (`startStreamSession` vs `createSession` + `addCamera`). Use the SDK version we pin and its sample.
- **Supported glasses (FAQ):** Ray-Ban Meta Gen 1 and Gen 2, Ray-Ban Meta Display, Oakley Meta HSTN and Vanguard, only in AI-glasses countries. Confirm ours.

Design consequence: open a session on request, take one photo (not a continuous stream), close it. That also matches the consent rule (brief, user-triggered capture) and helps battery.

## 3. Latency, honestly

R1 wants speech within 3 s. Serial costs we can name (third-party aggregator snapshots, not measured by us): Muse Spark first token about 2.3 s median on one listing and 8 s on another; Muse Voice Transcribe about 2.3 s. Speech-to-text plus vision plus speech out in series cannot hit 3 s. Plan:

1. Trigger without speech-to-text where possible (see 4), so only vision is on the path.
2. One small photo (LOW or MEDIUM, JPEG), not video.
3. Play a short earcon or "one moment" immediately so it never feels ignored (over 5 s feels ignored per the PRD).
4. Measure end to end on the real device before claiming any number.

## 4. Open problems (need a human call)

1. **Trigger.** The DAT FAQ says custom "Hey Meta" commands are not part of the toolkit, and we found no documented glasses button event for apps. Options: HFP mic plus Muse Voice Transcribe listening for a phrase (battery and passive-listening concerns), a tap on the phone, or a labeled mock trigger. The PRD's "press the glasses' button" is unsupported by anything we read.
2. **Speech out.** We found no Muse text-to-speech. Android's built-in TextToSpeech is the OS, not a Meta tool. Needs a decision under D5 (accept OS TTS as part of the Android host, or label it a mock).
3. **Speech in.** Muse Voice Transcribe appears to exist (third-party sources: streaming, about $0.18 per audio hour); verify in Meta's Model API docs.
4. **Foreground or background.** Whether a session survives screen-off on Android is unverified (only a third-party wrapper claims it).

## 5. Spike: prove the loop before anything else

Stop at the first step that fails and record it here.

1. Build the DAT sample on the teammate's Android phone; register with Mock Device Kit. Pass: frames or a photo arrive in the app.
2. Same on the real glasses. Record the exact model, photo size and time from capture request to bytes in app.
3. Phone plays a fixed phrase (any audio source) to the glasses over A2DP. Record time from call to sound and whether it is clear.
4. Trigger to speech round trip with no Muse: capture -> log -> "got it". Record median over 20 runs.
5. Add one Muse Spark call with the photo ("describe the image in six words"). Record cost per call and median latency over 20 runs.
6. Try the HFP mic and one Muse Voice Transcribe call. Record the A2DP-to-HFP switch cost.

Record results in the owning lane's `hub/status/` file and in `AGENTS.md` "Verified implementation facts" only after they ran on the real device.
