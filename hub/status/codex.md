# Codex · Platform trigger correction and documentation

Updated 2026-10-25.

**Implemented**
- `GlassesTapTrigger` now handles Bluetooth next/previous-track key events and transport callbacks as well as play/pause, advertises those playback actions, and ignores repeated key-downs.
- Home and start confirmation describe double-tap as experimental. Removed the claim that a glasses tap definitely works when voice fails; the Home backup button remains.
- Recorded D29 and updated the PRD, platform reference, app README and H4 ledger. No new dependency, capture mode, or direct DAT Inputs implementation.
- Included earlier user-authorized documentation changes: WhatsApp excluded (D27), no remote delivery channel selected, and reply-prefix requirements removed (D28). Historical decisions remain intact.

**Verified on device / evidence limits**
- On the connected Samsung S23 Ultra, Myst owned the active Android media-button session. Event history recorded Bluetooth `KEYCODE_MEDIA_NEXT` down/up at 04:02:11 on 2026-10-25; the old handler did not handle next/previous. App logs showed listening but no accepted tap. The exact physical gesture producing those historical events was not isolated.
- Current Meta Wearables MCP `search_dat_docs` confirms single tap during a DAT session without interactive display content pauses/resumes the session and is not delivered to the app. Double-tap is a candidate application gesture; mappings depend on the device.
- `:app:assembleDebug :app:lintDebug` passed with a complete JDK 17. The resulting APK passed apksigner verification.
- Physical double-tap through capture, Muse and audible recognition has not been verified. H4 remains PENDING.

**Deployment handoff**
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
- Family and the last-answer note label scripted context; deleting the seed restores normal memory behavior. Five caregiver-memory slots remain independent of the seed.
- Cross-lane code: `core/records/{DemoMemories,PeopleStore,Schema}.kt`, `features/faces/FamiliarFaces.kt`, `ui/{EnrollViewModel,SetupScreen}.kt`. Docs: AGENTS, PRD, META_SUITE, app README, H3 ledger, DECISIONS and this status.
- `assembleDebug` and `lintDebug` passed. 24 pure-JVM checks passed against the actual Schema, DemoMemories, VisitMemory and ResponseGuard code: old JSON migration, stored seed-marker round trip, idempotence, persistent deletion, preservation of five caregiver memories and identity data, name aliases, both complete guarded answers, long-relationship fallback and cross-subject guard refusal. Checks ran in a throwaway harness outside the repo; no new app/test dependency. No device installation or recognition run from this computer; deployment remains with the original signing computer above.

**Word-limit update (D31)**
- User raised the shared response limit to 40 words. Visit composition and memory validation already read ResponseGuard.MAX_WORDS; the saved-memory hint now does too. Active README/PRD/platform text matches 40. Earlier decision/status descriptions of 20 refer to previous builds.
- Exactly 40 words passes, 41 words is blocked, and visit composition can include a full line longer than 20 words. All 24 seed/migration/guard checks and a fresh assembleDebug/lintDebug run passed.
