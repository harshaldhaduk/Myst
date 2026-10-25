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
- Visit memory (branch `kchperla/visit-memory`, D18 Proposed): remember when each enrolled person was last recognized, and add "You saw Daniel at 1 PM." to the next whisper. Touches RJ's files `features/faces/FamiliarFaces.kt`, `core/records/HouseholdStore.kt`, `core/records/Schema.kt` (no new event kind; uses VISIT) and `ui/EnrollViewModel.kt` / `ui/EnrollScreen.kt` (one demo control). Coordinate with RJ before merging.
  - Built: `core/records/VisitMemory.kt` (encounter rule, wording, 20-word composer, 30-day expiry), `HouseholdStore.logSighting/sightings/deleteSightings`, `FamiliarFaces` (reads earlier sightings, then logs a match), revoke deletes sightings, Family tab shows "Last recognized", demo button "Demo: seen 5 h ago".
  - Checked: app compiles (assembleDebug). 23 scenario checks of `VisitMemory` ran on the JVM through a throwaway harness outside the repo (1 PM then 6 PM, repeat ask in one meeting, 7 days silent, midnight crossing, word limit, expiry): all pass. No JUnit in the repo yet (D13 Proposed), so these are not committed as tests.
  - Demo data: button "Load demo data (fictional)" adds Daniel (son) and Emily (granddaughter) with one memory and an earlier sighting each, consent "Robert (demo data)", no photos (so mock identity only). Skips names already enrolled.
  - Memory entry (branch `kchperla/memory-entry`): caregiver can add, delete and reorder memories on each person's row in the Family tab. `PeopleStore.addMemory/removeMemory/makeFirstMemory` rewrite the record through the existing atomic write; list edits are pure `Person.withMemory/withoutMemory/withMemoryFirst`. A new memory is checked with `ResponseGuard` at entry (banned phrases, questions, other family names, over 20 words are refused), max 5 per person and 140 characters, author is whoever recorded consent. The first memory is the one spoken.
  - Checked: 21 scenario checks on the JVM against the real `Schema.kt`, `VisitMemory.kt`, `ResponseGuard.kt` and a real org.json (not Android's): JSON round trip incl. quotes, newline, unicode and emoji, an old record with no memories key, remove, make-first, out-of-range indexes, guard refusals, and the final spoken line. Not checked: the file write on a phone, and the screen.
  - Not yet run on a device: store writes, revoke cleanup, and the spoken line through the glasses.
- Spike from hub/LOOP.md section 5, step 1: build the DAT Android sample and run it with Mock Device Kit, then on the real glasses.

**Blocked, or need from someone**
- Exact glasses model not yet recorded (Q1). Developer mode is reported on by the user; not yet verified in the app.
- Muse Spark API key for spike step 5: goes in ignored .env (MUSE_API_KEY, empty file created); where hackathon credits are issued is unknown.
- Human call on Q3 (trigger, Android TTS under D5) and Q10 (scope conflict).

**Files I am working in right now**
- hub/status/kchperla.md
