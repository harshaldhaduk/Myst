# Myst demo operator script (draft, 2026-10-25)

Use consenting teammates only. Face ID is an experimental, unapproved developer test (D12); never describe it as Meta approved. The phone is a Samsung S23 Ultra paired to RB Meta 02WM; the glasses' marketing model and firmware still need recording.

## Before the run

1. Put the glasses on, confirm they are paired in the Meta AI app, and open Myst. Home should show the glasses connected. Keep bystanders out of frame.
2. Use the existing debug install only: `adb install -r app/app/build/outputs/apk/debug/app-debug.apk`. Never uninstall or clear app data; that deletes enrollment and the Muse key. If Home says the key is missing, provision it with `scripts/provision-muse-key.sh` from the repo root, then reopen Myst. Keep `.env` and real family data off screen.
3. In Setup > Family, confirm the consenting demo person has a glasses photo, relationship, and one short caregiver-entered memory. Do not record a fresh photo without renewed consent. Check the name and wording before filming.
4. Turn on **Start Myst**. If voice reports a problem, reconnect the glasses in Meta AI and retry. Have the phone's quiet **Backup: Who is this?** control in view for a clearly disclosed fallback.

## Run order

1. Film the wearer asking “Who is this?” with the enrolled person in view. Listen for the earcon and hedged cue. The wearer then turns to the person and starts a real conversation using the saved context. Capture the glasses, phone status, and wearer response in one run where practical.
2. Ask with an unenrolled consenting teammate in view. Myst should say “I'm not sure who that is.” Stop if it names anyone. For a no-face check, point away from people. For a network failure, expect “I can't check right now”; for camera failure, “I can't see right now.”
3. Show Setup > Family briefly: consent, memory editing, and revoke. Revoke only after the filmed recognition; it removes that person's enrollment, photo, and sighting history. Re-enrollment needs fresh consent and a new glasses photo.
4. If demonstrating a reminder, schedule it a couple of minutes ahead in Setup > Reminders. Label the dose as **reported taken** if logged. A phone notification is not a WhatsApp delivery.

## Fallback and reset

The backup text control runs the real photo and Muse path; say on camera that the phone was used as a trigger. A recorded run must be labeled **pre-recorded real run**. Never splice in a fixed model answer as live. Turn **Start Myst** off between takes. There is no one-tap demo reset; check the Family data before each run and avoid deleting the app.

Record each rehearsal's trigger, subject (enrolled/stranger/no face), spoken outcome, intervention, and elapsed time in `hub/status/mayank.md`. The required five consecutive recognitions, three stranger checks, three no-face checks, airplane-mode check, revoke check, and about twenty timed runs are still pending until recorded there.
