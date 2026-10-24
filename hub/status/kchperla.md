# kchperla · Lane 1: Platform / device

Updated 16:38 by Claude Code

Lane choice is provisional: taken because this person has the phone and glasses. Confirm with the team that no one else owns Lane 1.

**Shipped**
- Nothing in the product yet.
- Phone reachable over adb (USB debugging authorized). Samsung Galaxy S23 Ultra (SM-S918U1), Android 16 (API 36). Meta AI app (com.facebook.stella) version 295.0.0.37.164. Facts about the phone only; checked with adb on 2026-10-24, not on the glasses.

**Verified on the real glasses (2026-10-24, Meta's CameraAccess sample, not our app)**
- DAT sample builds (Android Studio JBR, mwdat 1.0.1), installs on the S23 Ultra, registers through the Meta AI app in Developer Mode with blank app id/token.
- First run sat on "put on your glasses / waiting for active device" while the glasses worked for voice; logcat showed Meta AI app channel open failures. It connected after the user wore the glasses and retried. Cause not isolated.
- Glasses Bluetooth name: RB Meta 02WM (marketing model still to confirm).
- Video recording works. Glasses mic over HFP (setCommunicationDevice bt_sco) gave a silent clip once, with no AudioRecord start in the log, then a clip with sound on the next try. One pair of runs; unexplained. Wait for the recording-started signal before listening.
- Photo capture: about 0.5 s from tap to capture and 1 to 2 s more to display it in the sample (user's estimate by feel, not a timed median). Phone audio plays out of the glasses (A2DP), user-confirmed, not timed.
- Muse Spark via plain HTTPS works (POST https://api.meta.ai/v1/chat/completions, model muse-spark-1.3, bearer key from .env, text and image_url data URI). Glasses photo (ceiling fan, no people) downscaled to 720px JPEG, 59 KB, "describe in six words": correct answer. Default reasoning used up the whole token budget and returned empty content in 2 of 3 runs (1.9 to 3.4 s). `"reasoning_effort":"minimal"` returned a filled answer in 0.98 s (one run each, not a median); `"low"` still spent about 470 reasoning tokens. A `reasoning` object is rejected (400). Always set reasoning_effort minimal and max_tokens of a few hundred for the glasses loop.
- Photos can be pulled with adb from the debug sample's cache (run-as ... cache/images), so no manual transfer is needed for tests.
- Not yet measured: timed capture median, end-to-end latency over 20 runs.

**In flight**
- Spike from hub/LOOP.md section 5, step 1: build the DAT Android sample and run it with Mock Device Kit, then on the real glasses.

**Blocked, or need from someone**
- Exact glasses model not yet recorded (Q1). Developer mode is reported on by the user; not yet verified in the app.
- Muse Spark API key for spike step 5: goes in ignored .env (MUSE_API_KEY, empty file created); where hackathon credits are issued is unknown.
- Human call on Q3 (trigger, Android TTS under D5) and Q10 (scope conflict).

**Files I am working in right now**
- hub/status/kchperla.md
