# Mayank (mayank-jain632) · Lane 2 (files shared with RJ): Familiar Faces hardening, Muse key handling

Updated 02:35 by Claude Code

**Shipped** (on `main`, pushed: ef9d32c, 7aeed51, efc2270)
- Glasses battery UI: Home observes DAT 1.0.1 device metadata for one connected pair and displays a valid percentage in the status pill and Glasses card; otherwise the card says battery unavailable. Built in an isolated worktree so the D25 conversation spike changes in the shared checkout are untouched.
- Hub docs brought up to date (D23); decisions D20-D23 Proposed.
- D21: `MockIdentity` removed; hedged matches ("I think that's Daniel, your son."); "I can't check right now" on Muse/network/key failure; "I can't see right now" on capture failure; "Face recognition isn't set up on this phone." when the build has no face ID; guarded fallback lines instead of silence; reference photos read off the main thread; 150 ms earcon (OS audio) after every trigger.
- D22: Muse key no longer in `BuildConfig`; `scripts/provision-muse-key.sh` + `core/secrets/MuseKeyStore.kt` (Android Keystore AES/GCM).
- Lint error in `GlassesTapTrigger.kt` fixed (SDK_INT check). `assembleDebug` and `lintDebug` pass (0 errors).
- efc2270 (D24): Reminders page replaces Medicine; bottle check, room check, "What is this?", Places and the two Home buttons removed (archived at tag `archive/bottle-check-places-home-buttons`); quiet "Backup: Who is this?" text button at the bottom of Home. 7aeed51 alone did not build (it deleted files still in use); efc2270 fixes that. Both pushed.

**In flight**
- Proposed next: conversation nudge (in-memory transcript); awaiting the team. Medication as reminders only is done (D24, efc2270).

**Verified on device**
- 02:20: efc2270 installed on the S23 Ultra with `adb install -r`; family data intact (screenshot check). Nothing else run on the device yet: no reminder fired, no trigger, no Muse key provisioning.

**Gaps**
- Battery percentage has not been checked against the real glasses; the UI reading is not a battery-life measurement.
- Stranger, no-face and failure trials (gate A4, E2) after D21 lands.
- Rotate the old Muse key if any APK built before D22 was shared.

**Blocked, or need from someone**
- A human to accept or change D20-D23.

**Files I am working in right now**
- `app/` identity (`features/faces/`), `glasses/Speaker.kt`, `muse/MuseClient.kt`, `ui/EnrollViewModel.kt`, `ui/RemindersPart.kt`, `ui/HomeScreen.kt`, `features/meds/`, `app/app/build.gradle.kts`, `scripts/provision-muse-key.sh`.
