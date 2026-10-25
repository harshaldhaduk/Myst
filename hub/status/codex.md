# Codex · Platform and Conversation work

Updated 2026-10-25.

**Historical D29 slice (superseded by D33)**
- `GlassesTapTrigger` handled Bluetooth media events. D33 removed it after music playback interference and switched the live double-tap recap to DAT Inputs CAPTOUCH Select.
- Home and start confirmation described double-tap as experimental. The Home backup button remains.
- Recorded D29 and updated the PRD, platform reference, app README and H4 ledger. No new dependency, capture mode, or direct DAT Inputs implementation.
- Included earlier user-authorized documentation changes: WhatsApp excluded (D27), no remote delivery channel selected, and reply-prefix requirements removed (D28). Historical decisions remain intact.

**Verified on device / evidence limits**
- On the connected Samsung S23 Ultra, Myst owned the active Android media-button session. Event history recorded Bluetooth `KEYCODE_MEDIA_NEXT` down/up at 04:02:11 on 2026-10-25; the old handler did not handle next/previous. App logs showed listening but no accepted tap. The exact physical gesture producing those historical events was not isolated.
- Current Meta Wearables MCP `search_dat_docs` confirms single tap during a DAT session without interactive display content pauses/resumes the session and is not delivered to the app. Double-tap is a candidate application gesture; mappings depend on the device.
- `:app:assembleDebug :app:lintDebug` passed with a complete JDK 17. The resulting APK passed apksigner verification.
- Physical double-tap through capture, Muse and audible recognition has not been verified. H4 remains PENDING.

**Historical deployment handoff (superseded by the current device install)**
- User requested pushing the changes so the computer with the original signing key can build and install them.
- This computer's debug signer differs from the installed app. No APK was installed, no app was uninstalled, and no device data was cleared. The previous APK is backed up outside the repository; no complete app-data backup was made.
- On the original build computer, retain its existing debug keystore and `app/local.properties` settings, including the authorized face-ID build setting. From the repository root:

```sh
git pull --ff-only origin main
cd app
./gradlew :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- An update signed with the same key preserves registration, records and the stored Muse key. Then turn Start Myst on and test double-tap with a consenting enrolled teammate; verify the app accepts the trigger, captures one photo and speaks an answer. Use Home's backup button to compare paths.

**Files / coordination**
- Cross-lane application files: `glasses/GlassesTapTrigger.kt`, `ui/HomeScreen.kt`, `ui/EnrollViewModel.kt`.
- Documentation: AGENTS, CLAUDE, GEMINI, PROJECT_CONTEXT, app README, PRD, META_SUITE, DECISIONS, IDEAS, META_JUDGING_GATE, and this status.

**Current slice: seeded recognition context (D30)**
- Added fictional demo bundles for existing Krishna/RJ enrollments, applied once in debug builds on load/enrollment refresh. Names, relationships, photos, consent and all caregiver memories remain. A backward-compatible Person JSON marker prevents duplicates and respects seed deletion across restarts.
- After a confident match, the guarded whisper (now up to 40 words, D31) keeps the interaction and personal detail together ahead of the generated visit timestamp. Long relationships can fall back to name plus the full bundle. Other people retain visit/memory rotation. Real VISIT logging is unchanged; no synthetic visit records or identities are created.
- Family and the last-answer note use neutral saved-context wording (D32); deleting the seed restores normal memory behavior. Five caregiver-memory slots remain independent of the seed.
- Cross-lane code: `core/records/{DemoMemories,PeopleStore,Schema}.kt`, `features/faces/FamiliarFaces.kt`, `ui/{EnrollViewModel,SetupScreen}.kt`. Docs: AGENTS, PRD, META_SUITE, app README, H3 ledger, DECISIONS and this status.
- `assembleDebug` and `lintDebug` passed. 24 pure-JVM checks passed against the actual Schema, DemoMemories, VisitMemory and ResponseGuard code: old JSON migration, stored seed-marker round trip, idempotence, persistent deletion, preservation of five caregiver memories and identity data, name aliases, both complete guarded answers, long-relationship fallback and cross-subject guard refusal. Checks ran in a throwaway harness outside the repo; no new app/test dependency. No device installation or recognition run from this computer; deployment remains with the original signing computer above.

**Word-limit update (D31)**
- User raised the shared response limit to 40 words. Visit composition and memory validation already read ResponseGuard.MAX_WORDS; the saved-memory hint now does too. Active README/PRD/platform text matches 40. Earlier decision/status descriptions of 20 refer to previous builds.
- Exactly 40 words passes, 41 words is blocked, and visit composition can include a full line longer than 20 words. All 24 seed/migration/guard checks and a fresh assembleDebug/lintDebug run passed.

**Current slice: visible memory wording (D32)**
- Removed demo/fictional/scripted wording from Family’s memory hint, badge, displayed seed attribution, and Home’s last-answer note. Uses Shared context / Saved context / Saved relationship context. Existing stored author metadata stays internal, so already seeded records also show the new labels without migration.
- No changes to recognition, memory text, seeding/deletion, visit history or the 40-word guard. Documentation retains the stories’ scripted provenance.
- Cross-lane code: `ui/SetupScreen.kt` and `core/records/DemoMemories.kt`. Docs: AGENTS, README, PRD, H3 ledger, D32 and this status. assembleDebug and lintDebug passed. A scan of UI/resource string literals found no demo/fictional/scripted wording; existing stored seed authors are displayed through the neutral saved-context branch. Device install remains with the original signing computer.

## Shipped in the current D33 slice

- Added explicitly started live Conversation transcription using the glasses HFP mic and Muse Voice Transcribe realtime, plus a Muse Spark structured one-sentence recap through ResponseGuard and Android TTS. DAT Inputs CAPTOUCH Select is the glasses recap trigger; phone recap is the backup. “Stop recording” is recognized in the realtime transcript. The old 30-second spike control and Android MediaSession tap fallback were removed.
- Conversation Home card is compact when idle and expands for live text, recap progress and result. No visible checkbox, experimental title, or saved-transcript list. New sessions do not write audio or transcript files and clear transcript state at end. Existing encrypted files from earlier test builds were left untouched.
- Latest user-requested wording/layout: “Start recording” replaces “Record this”; separate Glasses card removed; Connect/Disconnect/Update appears below the top-right battery pill as applicable; the face card is titled “Last person Myst saw.”
- Cross-lane code: `ui/{EnrollViewModel,HomeScreen,MystApp}.kt`, `glasses/{DatGlassesGateway,Speaker,GlassesTapTrigger}.kt`, `features/{VoiceCommands,conversation/ConversationSpike,conversation/LiveConversationRecorder}.kt`, `muse/MuseRealtimeClient.kt`, manifest and Gradle. Docs: AGENTS, DECISIONS D33, PRD, META_SUITE, LOOP, app README, META_JUDGING_GATE and this status.

## In flight

- The new “Start recording” wording and relocated top-right connection controls are built but await a real-glasses/UI check. No automatic visit recording or past-conversation follow-up is built.

## Verified on device

- 2026-10-25, Samsung S23 Ultra (SM-S918U1, Android 16), Meta AI 295.0.0.37.164, RB Meta 02WM glasses (firmware unrecorded): Mayank reported the earlier “Record this” start and “Stop recording” end worked; a glasses double-tap produced a spoken recap. Android's Myst-only log showed `conversation recap ok 2511ms` on one run; the transcript and audio were not logged or retained for inspection.
- A first recap build failed to return JSON with `finish=length` at 120 output tokens; raising to 600 gave `conversation recap ok` in logs. An audio-focus build crashed because Android required a listener for `setWillPauseWhenDucked(true)`; the trace identified `LiveConversationRecorder.kt:79`. Removing that flag, rebuilding and installing with `adb install -r` led Mayank to report “Great works now.” This confirms the crash was resolved by user report; Spotify behavior was not separately described after that build.
- `assembleDebug` and `lintDebug` passed on the original-signing-key computer. All installations were in place (`adb install -r`), preserving enrollment and the Muse key.

## Gaps

- New phrase and small Home layout pending device check; second-speaker transcription, physical face-trigger double-tap, end-to-end latency and a filmed demo remain unverified. No unit tests were added. Earlier encrypted test transcripts remain in app-private storage but this build has no history UI or new writes.
