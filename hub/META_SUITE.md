# Meta platform reference: the whole suite and how it works

Read this before any technical work. It is the platform reference for D3/D5 (Meta only) and feeds the PRD capability table. Proposed as D11.

**Status: docs read on 2026-10-24. Items labeled [Verified on device] ran on our S23 Ultra (Android 16, Meta AI app 295.0.0.37.164) and glasses (Bluetooth name RB Meta 02WM, firmware not recorded) on 2026-10-24/25, per `hub/status/`; everything else is still docs only.** Pages were read through a summarizing fetch tool, so confirm exact class and method names against the SDK version we pin and its sample app before coding. Evidence labels:

- **[Docs]** Meta's own page, read 2026-10-24.
- **[Reported]** GitHub issue, news or third-party write-up. Treat as unverified.
- **[Inferred]** Our reading of the above, or general knowledge not on a page we read.
- **[Unknown]** Not found. Resolve on the device or with Meta before relying on it.
- **[Verified on device]** Ran on our phone and glasses; the full list is `AGENTS.md` "Verified implementation facts".

DAT 1.0.1 exposes `Device.batteryLevel` through `Wearables.devicesMetadata[id]` as a live `StateFlow` (Meta's Android SDK changelog, 1.0.0; confirmed in the pinned 1.0.1 library). The Home display uses this value for a single connected pair; its behavior on our glasses has not been checked. [Docs and SDK, not verified on device]

## 0. Read this first

1. **The camera is reachable only through the Wearables Device Access Toolkit (DAT)** on a phone app. Meta says only DAT can use the glasses' camera or capture mic audio; Web Apps and Connectors cannot. [Docs]
2. **There is no Meta API to play audio on the glasses.** They are a Bluetooth headset: normal Android audio output reaches them over A2DP. **Meta's Model API has no text-to-speech** ("It does not synthesize speech or provide a speech-to-speech conversation API"). Speech out is an open decision (Q11). [Docs]
3. **Face identification is policy-gated.** The Wearables Developer AUP bars "unique identification of people based on physical characteristics, such as voices and faces", with exceptions "for specific use cases, including accessibility apps, with prior Meta approval." No request channel is documented, and Meta's GitHub tracker explicitly refuses such requests. No Meta face-matching API exists. Without written approval, identity runs only as D12's private, consented dev testing (Accepted by RJ), never presented as Meta-approved. See section 6. [Docs]
4. **Meta's Model API (Muse Spark etc.) is plain HTTPS**, OpenAI- and Anthropic-compatible, base URL `https://api.meta.ai/v1`. It does vision, structured output, tool calling and speech-to-text. [Docs]
5. **Never use the `-contributor` model variants for anything about people.** Contributor mode is cheaper because you grant permission to train future Meta models on your content. Use Standard mode. [Docs]
6. **DAT is brand new and rough.** 1.0 rolls out from 2026-09-30; current pinned example is 1.0.1 needing Meta AI app V292 and glasses firmware V128. Voice Invocations, Inputs, Speech, Audio Streaming and standalone photo capture are experimental, and experimental features cannot ship in production without Meta's written permission. Fine for a hackathon, but label them. [Docs]
7. **"Hey Meta" can launch our app but cannot carry a custom command like "who is this".** Invocation is gated by Developer Center setup and has reported failures in Developer Mode. [Docs, Reported]
8. **Developer Mode allows only one third-party app registered at a time**, resets on some updates, and uses `APPLICATION_ID` and `CLIENT_TOKEN` of `0` [Docs]. Registration with these left blank (the DAT 1.0.1 default) worked on our glasses [Verified on device].
9. **Caregiver alerts via WhatsApp Cloud API need an approved template** for any message we start, and a server-held access token (a token inside the APK is a leak). Decision Q13. [Docs, Inferred]
10. **Latency is only partly measured.** Meta publishes none. Muse Spark answered a glasses photo in 0.98 s with `reasoning_effort: minimal` (one run) and face-match calls took 1.5-2.5 s (5 runs); capture time and end-to-end trigger-to-speech are not measured. The PRD's 3-second target is not proven. [Verified on device, partial]

## 1. The suite at a glance

| Surface | What it is | Direction of calls | Access today | Use for Myst |
|---|---|---|---|---|
| Glasses (Ray-Ban Meta Gen 1/2, Optics, "Meta glasses", Meta Ray-Ban Display, Oakley Meta HSTN/Vanguard) | Camera, mics, open-ear speakers, touch; Display model adds a screen and Neural Band | Bluetooth to the phone | Retail. DAT list is these six. Which one we own is Q1. | The wearable |
| Meta AI app (phone) | Pairs glasses, hosts permission prompts, registration, Developer Mode, App connections list | Mediates every DAT registration and permission | Required for pairing | Mandatory plumbing |
| **Wearables Device Access Toolkit (DAT)** | Kotlin/Swift SDK: camera, mic, motion, inputs, speech, voice invocation, display | Our phone app calls glasses | 1.0 from 2026-09-30; Android 10+, iOS 17.2+ | The glasses I/O layer |
| Display capability and Web Apps | HTML/CSS/JS apps on Meta Ray-Ban Display, Neural Band input | Runs on the glasses | Display glasses only | Optional text output; not needed |
| Meta AI Connectors | Our REST API/MCP server exposed as tools Meta AI can call | Meta AI calls us | Developer preview, application, spots limited, not public | Not on the critical path |
| **Meta Model API** | Muse Spark (vision, text, tools), Muse Voice Transcribe (STT), Muse Image, SAM 3.1, Muse Wonder | Our app calls Meta cloud | API key from dashboard; see conflicts in section 10 | Reasoning, label reading, STT |
| Open-weight models (Muse Spark Open, Muse Glimmer) | Self-hosted Meta models; Glimmer is edge-sized, Apache 2.0 | We host them | Download | Possible on-phone privacy path (Q14) |
| Muse Code and Wearables MCP | Coding agent plus docs server `https://mcp.developer.meta.com/wearables` (`search_dat_docs`, no auth) | Agents read docs | Free | How agents should look things up (section 11) |
| WhatsApp Cloud API | Business messaging over the Graph API | Our backend or app calls Meta | Test number is created automatically | Caregiver alerts (R13, R20) |
| Wearables Developer Center | Organizations, app IDs, release channels, Voice Invocation config, capability approvals | Web console | Full access only in AI-glasses markets | Setup |

## 2. Hardware (what the sources say)

- **Ray-Ban Meta Gen 2:** 12 MP ultra-wide camera, 3K/30 video, open-ear speakers, five or six mics (sources disagree), up to 8 h typical battery plus case, from $379. [Reported]
- **Meta Ray-Ban Display:** 600x600 color display in the right lens, $799, ships with Meta Neural Band (EMG wristband, IPX7, about 18 h), glasses about 6 h. [Reported]
- **Ray-Ban Meta Audio:** a camera-free model announced at Connect 2026 (from $349). It has no camera, so it cannot feed Familiar Faces. DAT support is [Unknown]. [Reported]
- All DAT-supported models need firmware V128 for DAT 1.0.1. Check on the phone: Meta AI app, Devices, device, Device settings, General, About, Version. [Docs]
- Meta says it does not access photos, video or audio the glasses send directly to a third-party app. [Docs]

## 3. DAT in depth

### 3.1 Architecture

Our Android app is the host. The glasses talk to the phone over Bluetooth; the Meta AI app brokers registration and permissions; our code calls the DAT. Modules under Maven Central group `com.meta.wearable`: `mwdat-core` (registration, permissions, devices, sessions), `mwdat-camera`, `mwdat-display`, `mwdat-inputs`, `mwdat-motion`, `mwdat-speech`, `mwdat-mockdevice`. Android needs Kotlin 2.2 and Android Studio Flamingo or later. Analytics and crash reporting are on by default; opt out with the `com.meta.wearable.mwdat.ANALYTICS_OPT_OUT` and `CRASH_REPORTING_OPT_OUT` manifest flags (do this for a privacy-first product). [Docs]

### 3.2 Setup

1. Meta AI app and glasses firmware at the versions in the table below; glasses connected to the Meta AI app.
2. Turn on Developer Mode: Meta AI app, Settings, App Info, tap the app version five times, enable the toggle. The unpublished app then appears under Meta AI settings, App connections, Developer mode apps.
3. Manifest: `BLUETOOTH`, `BLUETOOTH_CONNECT`, `INTERNET`; `<meta-data>` `com.meta.wearable.mwdat.APPLICATION_ID` and `CLIENT_TOKEN` (both `0` in Developer Mode); an intent-filter with a URI scheme that the Meta AI app uses to return to our app.
4. Call `Wearables.initialize(context)` once per process in `Application.onCreate()`. A second call fails with `ALREADY_INITIALIZED`; any other call before it throws `NOT_INITIALIZED`.

| DAT SDK | Meta AI app | Glasses firmware | Note |
|---|---|---|---|
| 1.0.1 | V292 | V128 (all models) | Latest listed |
| 1.0.0 | V290 | V128 | Adds Meta AI-initiated registration (`Wearables.handleIntent`) |
| 0.9.0 | V282 | V126 (Display V125) | |

A device that meets the minimums can still return `DeviceSessionError.CAPABILITY_DENIED`; handle it as an expected outcome. [Docs] On our glasses, capture also refused with `START_ERROR_DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED` and `START_ERROR_DEVICE_UNAVAILABLE`; both cleared after reconnecting. [Verified on device]

### 3.3 Registration and permissions

- `Wearables.startRegistration(activity)` sends the user to the Meta AI app and back; observe `Wearables.registrationState` until `REGISTERED`. Users can unregister any time in the Meta AI app; we can offer `startUnregistration`.
- Runtime permissions: `Permission.CAMERA` and `Permission.MICROPHONE`, granted in the Meta AI app as "Allow once" or "Allow always", per device. Checks return granted if any connected device approved. Permission requests fail without registration.
- Inputs needs capability approval in the Developer Center. Voice Invocations needs the Voice Invocation permission, added by creating a new integration version. Motion needs no runtime permission.
- The user can pause, resume or stop a session by tapping the glasses, taking them off or closing the hinges. Only one session runs per device. [Docs]

### 3.4 Session lifecycle

`Wearables.createSession(selector)` (`AutoDeviceSelector` or `SpecificDeviceSelector`), then `session.start()` (fire and forget; failures arrive on `session.errors`). States: `IDLE`, `STARTING`, `STARTED`, `PAUSED`, `STOPPING`, `STOPPED`. Add capabilities only after `STARTED`. `STOPPED` is terminal, so create a new session. Subscribe to `state` and `errors` before `start()`. Camera, stream and session objects are not restartable. Typed errors use `DatResult<T, E>`; avoid `getOrThrow()` in user-facing code. `PAUSED` can occur without app action (for example a cap-touch tap) and resolves on its own. [Docs]

### 3.5 Camera

- `session.addCamera(StreamConfiguration(videoQuality, frameRate, compressVideo))`. Quality: `HIGH` 720x1280, `MEDIUM` 504x896, `LOW` 360x640 (portrait). Frame rates: 2, 7, 15, 24, 30.
- `camera.stream.videoStream` emits frames: YUV by default, or HEVC when `compressVideo = true` (we decode ourselves).
- `camera.stream.capturePhoto()` works only while streaming; returns `PhotoData.Bitmap` or `PhotoData.HEIC`; a second concurrent capture fails with `CaptureError.CaptureInProgress`.
- Standalone high-quality capture via `camera.photo` (stop any stream first, wait for `PhotoState.STARTED`). **Experimental, not for production publishing.**
- Cleanup order: `camera.stop()`, `session.removeCamera()`, `session.stop()` (`removeCamera` confirmed in the 1.0.1 library by javap).
- Stream at MEDIUM/15 fps then `capturePhoto()` delivered glasses photos to Myst (76 and 102 KB JPEG). Capture time not timed. [Verified on device]
- Bluetooth bandwidth, real latency, battery life and thermal behavior under Myst: not measured. DAT reports battery level, but this does not establish runtime. [Unknown] LOOP.md quotes a downscale rule from search results; confirm on the camera guide. [Docs]

### 3.6 Audio

- **Playback to the wearer:** no DAT call. Use normal Android audio output; it routes to the glasses over A2DP (output only, stereo 44.1/48 kHz, suits music and TTS). [Docs] Phone audio heard on the glasses with Meta's sample, not timed. [Verified on device]
- **Mic capture:** Bluetooth HFP (bidirectional, mono 8 or 16 kHz, beamformed to the wearer). On Android 12+, pick the `TYPE_BLUETOOTH_SCO` device from `availableCommunicationDevices`, set `MODE_IN_COMMUNICATION`, call `setCommunicationDevice`. [Docs] One silent clip, then one with sound; unexplained. [Verified on device]
- **A2DP and HFP are mutually exclusive.** While HFP is active, output drops to mono voice quality. Meta's iOS guidance: start HFP and let the route settle (their example waits 2 s) before starting the camera stream, or the audio route "can fail silently". [Docs]
- **Camera-stream audio (beta):** set `AudioCodec.PCM` on the stream config and collect `audioStream`; 16-bit PCM at 16, 44.1 or 48 kHz, stays on A2DP. Dev and beta release channels only. [Docs]

### 3.7 Speech, inputs, motion, voice invocation (all experimental)

- **Speech:** `session.addSpeech`, flows for `state`, `locale`, `errors`, `transcriptions` (partial and final). Needs the DAT mic permission, granted from a user action. The README calls it on-device recognition. Where it actually runs and its accuracy are [Unknown]. A Speech session starts on our glasses (`RESPONSE_STATUS_OK`) and ends after about 60 s; that a spoken phrase fired Myst's action is not recorded. [Verified on device, partial]
- **Inputs:** navigation, button, capture and drag events from the glasses. Needs capability approval in the Developer Center. The earlier "no documented glasses button event" finding in LOOP.md is superseded: the capability exists but is gated. [Docs]
- **Motion:** accelerometer, gyroscope, magnetometer, orientation; sensor fields are nullable.
- **Voice Invocations:** wearer says "Hey Meta, start <app name>". Meta AI launches or foregrounds our app and sends an invocation on a `Wearables`-level stream (no session needed). Check `isVoiceInvocationsIntent(intent)` in `onCreate` and `onNewIntent`; collect `Wearables.startVoiceInvocationsStream(...)`; answer every invocation exactly once with `sendSuccess` or `sendFailure`. The app name is the keyword (configured in the Developer Center, clear phonetics, avoid "AI" in the name). Phrases cannot be declared in the app; this launches the app, it does not deliver "who is this". A WebMCP web app in the foreground on the glasses captures the request instead. [Docs]
- **Reported problems:** invocation not reaching an app from Developer Mode or release channels, Meta AI saying it has no tool to start the app, and the permission missing from the Developer Center configuration page (GitHub issues #344, #355, #311 on `meta-wearables-dat-ios`). Do not plan the demo around "Hey Meta" until it works on our device. [Reported]

### 3.8 Display (Meta Ray-Ban Display only)

`session.addDisplay()` after `STARTED`, then `display.sendContent { ... }` with one root view (`flexBox` or `video`), text and buttons, MP4 video. Button callbacks run on the phone. The FAQ says the Mock Device Kit does not support display glasses. [Docs]

### 3.9 Testing without hardware

`MockDeviceKit.getInstance(context).enable()`, `pairGlasses(GlassesModel.RAYBAN_META)`, simulate `powerOn()`, `unfold()`, `don()`, `doff()`, `fold()`, `powerOff()`, set permissions, feed camera from a video file (H.264/H.265; Android needs H.265) or the phone camera, set captured images. Mock audio does not produce deterministic PCM. A mock proves wiring, not a real device (`hub/BUILDING.md`). The CameraAccess sample has a debug menu for it, and `BirdSpotter` combines motion, camera, speech and display. [Docs]

### 3.10 Distribution

Developer Mode and invite-only release channels only. Meta's own review is required before general release (Developer Terms 4.1); submissions are "coming soon". For the hackathon this is fine: demo from Developer Mode or a release channel. Experimental features may be used "only for experimental or testing purposes" (Terms 2.8), so label them as such in the demo. [Docs]

## 4. Meta Model API

Base URL `https://api.meta.ai/v1`, bearer token in `MODEL_API_KEY` (the team's `.env` uses `MUSE_API_KEY`). `muse-spark-1.3` Chat Completions with an image data URI works; default reasoning returned empty content 2 of 3 runs, so set `reasoning_effort`; a `reasoning` object is rejected (400). [Verified on device] Protocols: Responses API (recommended), Chat Completions (OpenAI-compatible), Messages (Anthropic-compatible). A bad key returns 401 `invalid_api_key`. We call it with plain HTTPS (D5), not a third-party SDK. [Docs]

| Model | ID | Does | Price per 1M tokens (in/out) |
|---|---|---|---|
| Muse Spark | `muse-spark-1.3` (recommended), `-1.2`, `-1.1` | Text, image, video, audio, PDF in; text out; 1,048,576-token context; tools; reasoning | $1.25 / $4.25 (cached in $0.15) |
| Muse Spark contributor | `muse-spark-1.3-contributor`, `-1.2-contributor` | Same, discounted for training use | $0.10 / $0.20. **Do not use for family data.** |
| Muse Wonder (preview) | `muse-wonder-1.0` | Long-horizon coding and agents | $4.00 / $20.00 |
| Muse Voice Transcribe | `muse-voice-transcribe-1.0` | Speech to text only | $0.18 per audio hour |
| Muse Image | `muse-image-1.0` | Generate and edit images | $0.01 per image |
| SAM 3.1 | `sam-3.1` | Boxes and masks from a text prompt, on images and video | $2.50 per 1,000 images |

Rate limits are per team: Spark 3,000 RPM and 4M TPM; Muse Image 150 RPM; Voice Transcribe 256 concurrent streams and 16,000 streams per hour; SAM 3,000 RPM. Free credits are not stated on the pricing page (the track gate doc says $50; a separate Meta hackathon reportedly says $150, a different event). [Docs, Reported]

**Image understanding** (what face ID and any scene question use): images go only in `user` messages as a public URL, base64 data URL, or Files API id. Limits: 50 images per request, 50 MB inline, JPEG, PNG, GIF, WebP, ICO. Tasks: describe, answer questions, extract text and data from documents, identify objects and relations, and localize objects as points or boxes on a 0 to 1000 normalized grid. A roughly 1280 px image costs about 1,300 to 1,500 tokens. The page has **no caveat about faces or identifying people** [Docs]. Muse Spark did compare a glasses photo with consented reference photos: 5 of 5 "match" verdicts at 0.95-0.99 (D12 dev testing); no stranger or no-face trials yet, so its false-match behavior is [Unknown]. [Verified on device, partial]

**Structured output:** `text.format` with `type: "json_schema"` (Responses) or `response_format` (Chat Completions); generation is constrained to the schema; `strict: true` enforces a stricter subset; recursion is rejected; you still parse the JSON string yourself. Use it for match verdicts and the response composer so the guard can reject malformed output. **Tool calling:** `tools` with function definitions; `tool_choice` must be `"auto"`; always validate arguments yourself. [Docs]

**Speech in:** Muse Voice Transcribe realtime at `wss://api.meta.ai/v1/asr/realtime` (raw 16-bit mono PCM, 16 or 24 kHz; modes `PUSH_TO_TALK`, `ENDPOINTING`, `DIARIZATION`; 60-minute sessions) or file upload at `POST /v1/asr/transcribe` (mono 16-bit WAV, 32 MB or 10 minutes). No word timestamps or confidence scores. Audio input to `muse-spark-1.3` is degraded; the docs recommend `muse-spark-1.2` for audio. **Speech out: none.** [Docs]

**Open-weight:** Muse Spark Open (816B MoE, self-host) and Muse Glimmer (edge-sized, Apache 2.0, text and image in, runs with llama.cpp, vLLM, SGLang or ExecuTorch). Whether self-hosting a Meta model on the phone counts as "Meta stack" is Q14. [Docs]

**Retention and training terms for requests are not covered on pages read.** Check the Model API terms before sending any frame that contains a person (Q12). [Unknown]

## 5. WhatsApp Cloud API (caregiver alerts)

Graph API: `POST /<phone_number_id>/messages` with a JSON body. A test business number and test account are created automatically, with relaxed limits and no payment method needed for template sends. Templates need approval and are the only message type allowed outside the 24-hour customer service window, so an alert we initiate (missed dose) must be an approved template. Webhooks report delivery status and incoming messages. Limits: 80 messages per second per number, about one message per 6 s to the same user. [Docs]

- A vendor blog reports free-form replies inside the 24 h window become billable from 2026-10-01 after 1,000 free per number per month. Not confirmed on Meta's page. [Reported]
- In test mode Meta normally restricts recipients to a short pre-verified list. This was not on the page read. Verify before the demo. [Inferred]
- The access token must not ship inside the APK. Sending from the phone embeds it; a small relay would be a backend and conflicts with D5's no-backend reading. Q13. [Inferred]

## 6. Policy and legal: the identity blocker

**Wearables Developer Acceptable Use Policy (last updated 2026-09-22)** [Docs]:
- Prohibits "Use device sensors for unique identification of people based on physical characteristics, such as voices and faces;" and states "exceptions are granted for specific use cases, including accessibility apps, with prior Meta approval."
- Also prohibits surreptitious recording, bypassing the capture LED, covert filming, and "analyzing people in a user's surroundings to facilitate unwanted approaches or interactions."

**Wearables Developer Terms (last updated 2025-10-06)** [Docs]:
- 5.2(c): no "Using Technology Data to perform, facilitate, or provide tools for surveillance." A separate rule from the AUP, so an AUP exception does not override it.
- 5.1: Technology Data only to run, support and maintain the integration. 5.5: delete it when a user asks. 6.1: privacy policy must say what data is processed and how. 2.8: experimental features test-only.
- Whether sending frames to Meta's Model API is a permitted "Service Provider" use is not stated. [Unknown]

**Approval path:** none documented. On 2026-10-13 a Meta account replied to a face-identification approval request on the DAT tracker (issue #334) that the tracker is for SDK bugs, so use cases "cannot be reviewed or approved there", and asked that deployment details not be posted publicly. It points to the discussions forum and the Developer Center. Practical route for us: ask the Meta judges and mentors at the event (PRD pre-build check 1), and look in the Developer Center for a use-case form. [Docs]

**Model API Acceptable Use Policy** (separate) bars identifying or tracking a specific individual by biometric data, including facial recognition, "without a lawful basis and consent where required by applicable law". It also bars inferring identity, health or biometric information about individuals without the right to do so, and relying on outputs for health or safety decisions "without a qualified person reviewing the Outputs." Consent from enrolled participants is our plan, but this clause is why medication outputs stay caregiver-verified. [Docs]

**Context:** Meta reportedly kept an inactive face-recognition feature ("NameTag") in its Meta AI app, and reportedly disabled it on 2026-06-05 after press coverage; the Texas Attorney General opened an investigation into Meta's glasses in May 2026. [Reported] Texas also has a biometric-identifier consent law; our demo uses consenting participants, but confirm handling with the organizers. [Inferred, not legal advice]

**Consequence for Myst:** there is no Meta-only, policy-clean way to identify a face today. Muse Spark can be prompted with a live frame and consented reference photos (5 of 5 match verdicts on device, section 4), but that is still identification by face under the AUP, gives no calibrated confidence (so the PRD's zero-wrong-name rule needs negative trials before it can be claimed), and is not covered by an exception. The team accepted that risk for private, consented testing only (D12, Accepted by RJ; D16 verbal, not approval). Keep identity behind one interface (D7) and never present it as Meta-approved (G05).

## 7. Capability table, answered

Mirrors the PRD table, which also records what is built on `main`. Detail and sources are in sections 3 to 6.

| Need | Finding | Proposed demo treatment |
|---|---|---|
| Glasses model and developer access | DAT lists six models; needs V128 firmware and Meta AI V292 [Docs]. Registration works on ours [Verified on device]. Marketing model and firmware are Q1. | Live |
| Camera frames | DAT stream or single photo; Bitmap or HEIC [Docs]. One photo per request reaches Myst [Verified on device]. Latency not timed. | Live; one photo per request |
| Audio to wearer | No DAT call; Android audio over A2DP [Docs]. Heard on the glasses with Meta's sample [Verified on device]. | Live |
| Speech out | No Meta TTS [Docs] | Android TextToSpeech, labeled OS (D17, Proposed) |
| Speech in | HFP mic plus Muse Voice Transcribe realtime, or DAT Speech (experimental) [Docs]. DAT Speech starts on our glasses [Verified on device, partial]. | DAT Speech, experimental (D17) |
| Voice or button trigger | Hey Meta launches the app only; Inputs gated; Speech phrase in session possible [Docs] | Speech phrase (D17) plus a quiet "Backup: Who is this?" text button on Home (D24); glasses tap via MediaSession untested |
| Person identification | Policy-gated, no Meta matcher [Docs]. Muse Spark comparison works in dev testing [Verified on device, partial]. | `MuseIdentity`, dev builds only (D12); never presented as Meta-approved |
| Memory storage | Caregiver records on the phone | Live (app logic) |
| Label reading and bottle check | Muse Spark image understanding plus structured output [Docs]. | Removed (D24, efc2270): medication sends no frame to Muse |
| Muse access | HTTPS, key from dashboard; region claims conflict (section 10). Our key works [Verified on device]. | Live |
| Dose scheduling | Android alarms (host OS) | Live; OS feature, confirm under D5 |
| Caregiver alerts | WhatsApp Cloud API, approved template, token problem (section 5) | Live or mock; see Q13 |
| Face blur before Muse | No Meta tool found | No blur; only face ID sends frames (on request, D12 dev builds); medication sends none (D24) |
| Object finding | SAM 3.1 or Muse Spark grounding exists [Docs] | Roadmap (D8) |

## 8. How Myst maps onto the suite

```text
Wearer        Glasses              Android phone (Myst)                       Meta cloud
 "Hey Meta,   mic/HFP --------->   DAT Speech or Muse Voice Transcribe ------> STT (wss)
  start Myst"  camera  --------->   DAT photo capture (one per request)
                                    identity interface (MuseIdentity, D12 dev only)
                                    caregiver records (on phone, family-written)
                                    Muse Spark: face match only (D12) --------> api.meta.ai/v1
                                    dose reminders: Android alarms, no frame (D24)
                                    guard: uncertainty, source records, 20 words
 whisper <--- speaker A2DP <-----   Android TextToSpeech (OS, D17)
                                    WhatsApp template alert ------------------> Graph API
```

Requirement impact (full list in `hub/PRD.md`):
- **R1 (3 s):** trigger plus capture plus identity plus Muse plus speech in series is unproven. Measure on device first (`hub/LOOP.md` spike).
- **R3 and R4:** `MuseIdentity` (D12) plus a "not sure" path; never present it as Meta-approved.
- **R5:** no retention is achievable on our side; never persist frames. Blur has no Meta tool.
- **R7 and R8:** removed by D24. Medication is reminders only and sends no frame to Muse.
- **R13 and R20:** WhatsApp template plus Q13.
- **Data location:** caregiver records stay on the phone; frames go only to Meta's API in Standard mode, only for face ID, only when the wearer triggered, and are not stored.

## 9. Verify on the device (extends the spike in `hub/LOOP.md`)

Done items record their result in `AGENTS.md` "Verified implementation facts".

1. Which glasses we own, firmware and Meta AI app versions. Partly: Meta AI app 295.0.0.37.164, Bluetooth name RB Meta 02WM; model and firmware open.
2. Developer Mode registration works (done); `CAPABILITY_DENIED` rate not counted.
3. One photo: size, format, request-to-bytes time over 20 runs. Partly: 76-102 KB JPEG; not timed.
4. A2DP playback of a fixed phrase: clarity and call-to-sound time; cost of switching to HFP and back. Partly: audible, not timed.
5. Voice Invocation "Hey Meta, start Myst": does it reach the app in Developer Mode or a release channel?
6. Speech: where it runs, accuracy on "Who is this?" in a noisy room; compare Muse Voice Transcribe. Partly: session starts; phrase accuracy not measured.
7. ~~Muse Spark with a bottle photo~~: superseded by D24 (bottle check removed).
8. Muse Spark behavior when shown a person and asked to compare to a reference photo (see section 6). Partly: 5 of 5 matches; strangers and no-face not run.
9. Model API request retention and training terms; our key's region eligibility.
10. WhatsApp: template approval time, test recipient list, delivery to Robert's phone.

## 10. Source conflicts

- **Model API region.** July 2026 coverage says a US-only public preview; the 2026-09-24 Connect recap says it is "generally available globally"; quickstart mentions no waitlist; one guide says waitlist. Trust the dashboard sign-up. [Reported, Docs]
- **DAT status.** The Android README still says "developer preview"; the FAQ and recap say 1.0 from 2026-09-30; the code examples use 1.0.1. Use the version we pin.
- **Voice.** The 2025 preview blog said voice and Meta AI were not in the toolkit; DAT 1.0 has Voice Invocations (launch only). `hub/LOOP.md` says custom commands are not in the toolkit; that is still true, but Inputs, Speech and Voice Invocations now exist (gated or experimental).
- **Two Meta events.** The repo targets the HackTX Meta track. Meta also runs a separate "Global AI Developer Hackathon" (applications reportedly close 2026-10-25, $150 credits reported). Do not assume its rules or credits apply to HackTX.
- **Mic count on Gen 2** differs between sources; irrelevant to us.

## 11. How to look things up and keep this current

- Search current docs through the Wearables MCP: `https://mcp.developer.meta.com/wearables`, tool `search_dat_docs`, no authentication. Install Meta's plugin for Claude Code, Codex or Muse Code from the SDK repo.
- Read `AGENTS.md` in `facebook/meta-wearables-dat-android` (and `-ios`), the API reference at `wearables.developer.meta.com/docs/reference/android/dat/latest`, and the SDK `CHANGELOG.md`.
- Model API docs: `dev.meta.ai/docs/overview`, with the section pages linked there. Policy: `wearables.developer.meta.com/acceptable-use-policy/` and `/terms/`. Re-read both before each demo; the AUP changed on 2026-09-22.
- **When you verify something on the device,** change its label here from [Docs] to verified-on-device with the date, model, firmware and app version, and add it to `AGENTS.md` "Verified implementation facts" (only the top-level agent edits hub files).

## 12. Sources (read 2026-10-24)

- DAT docs: `wearables.developer.meta.com/docs/develop/dat` (setup, Android integration, permissions, microphones and speakers, voice invocations, version dependencies); Android SDK README and `AGENTS.md`, `github.com/facebook/meta-wearables-dat-android`; FAQ `developers.meta.com/wearables/faq`; Connect recaps `developers.meta.com/blog/meta-connect-recap` and `/meta-connect-recap-ai-glasses`; preview announcement `developers.meta.com/blog/introducing-meta-wearables-device-access-toolkit`.
- Policy: `wearables.developer.meta.com/acceptable-use-policy/`, `wearables.developer.meta.com/terms/`, `dev.meta.ai/legal/acceptable-use-policy`; GitHub issue #334 on `facebook/meta-wearables-dat-ios`.
- Model API: `dev.meta.ai/docs/overview`, `/models`, `/pricing-rate-limits`, `/quickstart`, `/image-understanding`, `/structured-output`, `/tool-calling`, `/speech-to-text`; connectors page `dev.meta.ai/products/connectors`.
- WhatsApp: `developers.facebook.com/docs/whatsapp/cloud-api/overview`.
- Reported only: Malwarebytes (2026-06-09) on NameTag; biometricupdate.com on the Texas inquiry; GitHub issues #344, #355, #311; hardware spec roundups (GSMArena, Fonearena and others); respond.io on WhatsApp pricing.
