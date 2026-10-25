# Mayank (mayank-jain632) · Lane 2 (files shared with RJ): Familiar Faces hardening, Muse key handling

Updated 02:20 by Claude Code

**Shipped** (on `main`, ef9d32c, not pushed)
- Hub docs brought up to date (D23); decisions D20-D23 Proposed.
- D21: `MockIdentity` removed; hedged matches ("I think that's Daniel, your son."); "I can't check right now" on Muse/network/key failure; "I can't see right now" on capture failure; "Face recognition isn't set up on this phone." when the build has no face ID; guarded fallback lines instead of silence; reference photos read off the main thread; 150 ms earcon (OS audio) after every trigger.
- D22: Muse key no longer in `BuildConfig`; `scripts/provision-muse-key.sh` + `core/secrets/MuseKeyStore.kt` (Android Keystore AES/GCM).
- Lint error in `GlassesTapTrigger.kt` fixed (SDK_INT check). `assembleDebug` and `lintDebug` pass (0 errors).

**In flight**
- Proposed next: conversation nudge (in-memory transcript) and medication as reminders only; awaiting the team.

**Verified on device**
- Nothing yet for this work.

**Gaps**
- Stranger, no-face and failure trials (gate A4, E2) after D21 lands.
- Rotate the old Muse key if any APK built before D22 was shared.

**Blocked, or need from someone**
- A human to accept or change D20-D23.

**Files I am working in right now**
- `app/` identity (`features/faces/`), `glasses/Speaker.kt`, `muse/MuseClient.kt`, `ui/EnrollViewModel.kt`, `app/app/build.gradle.kts`, `scripts/provision-muse-key.sh`.
