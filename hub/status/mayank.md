# Mayank (mayank-jain632) · Lane 2 (files shared with RJ): Familiar Faces hardening, Muse key handling

Updated 03:50 by Codex

**Shipped** (on `main`, pushed: ef9d32c, 7aeed51, efc2270)
- Glasses battery UI: Home observes DAT 1.0.1 device metadata for one connected pair and displays a positive percentage in the status pill and Glasses card; otherwise battery is unavailable. DAT 0% disagreed with Meta AI's charged indication, so 0 is now treated as unavailable.
- Hub docs brought up to date (D23); decisions D20-D23 Proposed.
- D21: `MockIdentity` removed; hedged matches ("I think that's Daniel, your son."); "I can't check right now" on Muse/network/key failure; "I can't see right now" on capture failure; "Face recognition isn't set up on this phone." when the build has no face ID; guarded fallback lines instead of silence; reference photos read off the main thread; 150 ms earcon (OS audio) after every trigger.
- D22: Muse key no longer in `BuildConfig`; `scripts/provision-muse-key.sh` + `core/secrets/MuseKeyStore.kt` (Android Keystore AES/GCM).
- Lint error in `GlassesTapTrigger.kt` fixed (SDK_INT check). `assembleDebug` and `lintDebug` pass (0 errors).
- efc2270 (D24): Reminders page replaces Medicine; bottle check, room check, "What is this?", Places and the two Home buttons removed (archived at tag `archive/bottle-check-places-home-buttons`); quiet "Backup: Who is this?" text button at the bottom of Home. 7aeed51 alone did not build (it deleted files still in use); efc2270 fixes that. Both pushed.
- D25 feasibility tooling: a debug-only 30-second consent-driven HFP recording control on Home. It keeps PCM and WAV in RAM, calls Meta's `/v1/asr/transcribe` as multipart DIARIZATION WAV and a Spark structured summary, and reports route/photo/API status. It is a spike, not a visit feature. `docs/demo-script.md` is a draft operator procedure.
- MuseClient no longer logs model-output snippets on malformed JSON; conversation text could otherwise appear in diagnostics.

**In flight**
- Conversation nudge stopped at its required device spike: DAT Speech coexistence is not established, the other speaker was not tested, and Spark returned BAD_ANSWER. Mayank accepted D25 and its batch-upload amendment D26 at 02:49; the stop rule still applies. Medication as reminders only is done (D24, efc2270).
- Rollback: `ae9d87b` reverts the unverified `f7187d2` app changes to the last device-tested D25-era app code (`686c718` over `f5a3c9f`). The restored APK rebuilt, passed lint, and installed with `adb install -r`. Mayank reports glasses-to-app communication is working again after a hardware-side recovery; the exact recovery action and paths rechecked were not recorded. Sentence-nudge WIP is preserved only in a local git stash.
- Fixed false glasses-update guidance: generic DAT session-ended errors now ask the wearer to check the connection; only DAT's typed update-required result triggers update guidance. DAT battery 0 is displayed as unavailable. `assembleDebug lintDebug` passed and the APK installed in place. Mayank reports the corrected Home shows no battery percentage and Start Myst still fails with the generic "unexpected error"; the false update warning is gone, while the DAT session root cause remains unresolved.

**Verified on device**
- 02:20: efc2270 installed on the S23 Ultra with `adb install -r`; family data intact (screenshot check). No reminder fired then. The task handoff says the Muse key was provisioned afterward; this session did not inspect or reveal it.
- 2026-10-25 ~02:38, S23 Ultra, Meta AI app 295.0.0.37.164, RB Meta 02WM, firmware not recorded: `adb install -r` of the D25 spike build succeeded. With a consenting teammate, user reported `HFP routed=true`, photo OK during HFP, and the temporary Meta ASR transcript correctly included the wearer's taco conversation. The teammate did not speak in that first clip. Spark reported `BAD_ANSWER`; exact ASR/Spark latencies were not supplied. A later "Who is this?" attempt was intermittently ignored or produced an earcon with no audible answer; when the earcon played, Home's "Last thing Myst said" stayed empty. The result line for that later run was not reported. These are user-observed outcomes, not a measured reliability rate.
- 2026-10-25 after the spike, with Start Myst on and the glasses pointed away from people, the user tapped Home's backup "Who is this?" control. Home's last-said card updated and Myst's unsure line was heard through the glasses. This separates the working backup path from the unreliable spoken trigger; it is not a timed run and does not prove a stranger negative case.
- The revised spike APK (RAM buffer wipe, debug-only microphone permission, no model-output snippet logging) passed `assembleDebug lintDebug` and installed with `adb install -r` successfully. After rebasing on f5a3c9f (battery UI), the integrated APK again passed `assembleDebug lintDebug` and installed with `adb install -r`. No second run of either revised APK is claimed.
- 2026-10-25 ~03:38, same S23 Ultra: restored D25-era APK built from code at `686c718` (battery UI from `f5a3c9f`) and installed successfully with `adb install -r`. No uninstall, clear-data or enrollment changes were made. Glasses behavior after rollback has not yet been rechecked.
- After that install, Mayank reported Start Myst briefly starts, then the app says the glasses need an update; Meta AI reports that the glasses are current. This matches the app's generic session-ended fallback wording, but the underlying DAT session error code has not been captured.
- After the targeted correction was installed, Mayank reported the battery percentage was no longer shown and Start Myst returned "Voice didn't start (An unexpected error occured)." The generic session failure remains; no typed DAT error trace was collected.
- Mayank subsequently reported that the glasses/app communication recovered through a hardware-side fix. No new capture/recognition results or exact recovery steps have been recorded yet.

**Gaps**
- DAT battery percentage disagreed with Meta AI: Myst displayed 0% while Meta AI showed charge. Treat 0 as unavailable; actual glasses charge/runtime are not known.
- Stranger, no-face and failure trials (gate A4, E2) after D21 lands.
- D25: confirm DAT Speech phrase while AudioRecord is active, hear the teammate at about 1 m, collect the ASR/Spark timings, and diagnose Spark's invalid structured answer. The debug control does not start a visit or whisper a nudge.
- Rotate the old Muse key if any APK built before D22 was shared.

**Real-glasses rehearsal log (2026-10-25; observations, not accuracy claims)**

| Case | Observed | Counts toward requested run set? |
|---|---|---|
| Consenting but unenrolled teammate, spoken “Who is this?” | User heard “I'm not sure who that is.” No model trace or conditions recorded. | One anecdotal unknown-person check; not a controlled 3/3 set. |
| No person in view, Home backup trigger after HFP spike | Last-said card updated and unsure line was audible through the glasses. | One backup/no-face observation; not a controlled 3/3 set. |
| Later spoken requests during/after HFP spike | Intermittently ignored, or earcon without a last-said update. | Failure to investigate; no successful-run count. |

Still to record with timestamps and conditions: five straight known-person recognitions, three controlled stranger checks, three controlled no-face checks, one airplane-mode check, one revoke-and-retry check, and about twenty end-to-end timed runs. No result has been invented for these open cells.

**Blocked, or need from someone**
- A human to accept or change D20-D23.

**Files I am working in right now**
- `app/` identity (`features/faces/`), `glasses/Speaker.kt`, `muse/MuseClient.kt`, `ui/EnrollViewModel.kt`, `ui/RemindersPart.kt`, `ui/HomeScreen.kt`, `features/meds/`, `app/app/build.gradle.kts`, `scripts/provision-muse-key.sh`.
