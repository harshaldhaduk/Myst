# Decisions

Append-only. Agents add **Proposed** entries. Only a human marks one **Accepted** or **Rejected**, by changing the status word and adding their name. To reverse a decision, add a new entry that names the one it replaces; never rewrite an old entry.

Entry format:

```
### D<n> · <Proposed | Accepted | Rejected> · <HH:MM> · <who>
**Decision:** one sentence.
**Why:** one sentence.
**Affects:** lanes, files, or the demo.
```

## Open questions (need a human call)

When the team answers one, delete it here and log the answer below as a decision. Mirrors `PROJECT_CONTEXT.md` section 10.

- **Q1. Hardware and SDK.** Exact Ray-Ban Meta model we have, and does it work with the Wearables Device Access Toolkit? PRD says Ray-Ban Meta via DAT; not verified.
- **Q2. Recognition.** Do the Meta judges say a consented, on-request family face match qualifies for the accessibility exception (D7)? If not, what is Plan B, and is it Meta-only or a labeled mock?
- **Q3. Trigger and speech.** Docs read (`hub/LOOP.md`): no documented glasses button event or custom voice command for apps; no Muse text-to-speech found; Muse Voice Transcribe exists per third-party sources. Which trigger do we use, and is Android's built-in TTS acceptable under D5?
- **Q4. Bottle check.** On-device OCR is out (D5). What Meta-only rule counts as "agreement" for R7?
- **Q6. Name and tagline.** The PRD title reads "Myst (Now Myst)", which is ambiguous. Is "Myst" final?
- **Q7. `face-demo/`.** It lives only on branch `feature/face-recognition-demo`, not on `main`. Keep as a branch, archive or delete?
- **Q8. PRD cleanup for RJ.** Undefined priorities (P3, P4, "?"), missing R18, truncated R12, R3 at P2 although R1/R2/R4 need identity, R5 (no retention) at P3, R16 vs R13 alert overlap. See `hub/PRD.md` hub notes.
- **Q10. Conflicting scope docs.** `META_JUDGING_GATE.md` (teammate) puts identity first and medication in P2/roadmap; the PRD (RJ) makes Medication Routine P0 and the gate doc says Meta APIs are "encouraged, not required" while D5 says Meta only. Which is the controlling scope? Until a human decides, build the loop (`hub/LOOP.md`), which both need.
- **Q9. Face blur and water detection.** R5 blur and R9 water check have no known Meta tool. Drop, mock or redesign?

## Log

### D1 · Proposed · 00:00 · (seed, from PROJECT_CONTEXT.md)
**Decision:** The product is one story: a glasses companion that recognizes consenting loved ones and gives trusted relationship context. Person recognition plus context is P0, object help is P1, routines and caregiver tooling are roadmap, and medication adherence never leads the pitch.
**Why:** Generic vision Q&A is not differentiated for a social-company track, and a focused, truthful demo beats several simulated capabilities.
**Affects:** every lane; the demo; the pitch. A human should accept, edit or reject this on day one.

### D2 · Proposed · 00:00 · (seed)
**Decision:** Every coding agent coordinates through this hub: `AGENTS.md` at the root, plus `hub/DECISIONS.md`, `hub/status/`, and `hub/IDEAS.md`, following `hub/BUILDING.md`.
**Why:** Several people on different coding agents need one rulebook so stray ideas and invented facts do not derail the build.
**Affects:** every session in every tool.

### D3 · Proposed · 00:00 · Mayank, direct instruction recorded by Claude Code
**Decision:** The entire tech stack is Meta and Muse tools only. No third-party recognition service, model, backend or SDK. A capability Meta/Muse does not expose is blocked, mocked with a clear label, or moved to roadmap, never filled by an outside tool.
**Why:** The project targets Meta's track and must demonstrate what Meta's own platform enables; outside dependencies would also undercut the truthful-demo rule.
**Affects:** every lane; `AGENTS.md` stack section; the pre-existing `face-demo/` (CompreFace, MediaPipe) falls outside it pending Q7.

### D4 · Proposed · 15:12 · Claude Code, from the HackTX PRD Mayank pasted
**Decision:** Adopt the HackTX PRD (RJ, 2026-10-24) as product direction: two features, Familiar Faces and Medication Routine, both P0. This replaces D1's ordering where medication was roadmap and object help was P1.
**Why:** The human supplied the PRD and asked the hub to be updated to match.
**Affects:** `hub/PRD.md` rewritten; `PROJECT_CONTEXT.md` banner; `AGENTS.md` direction, medical and object-help lines; every lane.

### D5 · Proposed · 15:12 · Mayank, direct instruction recorded by Claude Code
**Decision:** Restate D3 concretely: the stack is Ray-Ban Meta glasses, the Wearables Device Access Toolkit, the Muse Spark API and the WhatsApp Cloud API, with Android as the DAT host OS. On-device OCR, face-blur libraries, a third-party face matcher, FCM and any outside SDK are not allowed; capabilities needing them are blocked, mocked or roadmap.
**Why:** The human said the stack is strictly Meta and everything comes directly from Meta.
**Affects:** PRD R3, R5, R7, R13; capability table; the PRD's OCR fallback and push alerts.

### D6 · Proposed · 15:12 · Claude Code
**Decision:** Replace the blanket "no medication verification" rule with: Myst may compare a held bottle's label to the caregiver's record, speak caregiver-entered counts, and log a dose as "reported taken". It never advises on dosing, never infers a count, and never treats a log as proof of ingestion.
**Why:** The PRD makes bottle checking core, and the safe wording keeps the medical boundary honest.
**Affects:** `AGENTS.md` safety section; R7, R10, R12; timeline wording ("reported taken", not "taken").

### D7 · Proposed · 15:12 · Claude Code
**Decision:** Put identity behind one interface. The demo path depends on the Meta judges' answer on the accessibility exception; until then it is a labeled mock. Plan B (the PRD's "family beacon") is not adopted until a human confirms it can be Meta-only.
**Why:** Meta's AUP restricts face identification, and no Meta matching API is known.
**Affects:** PRD R3, capability table; Q2.

### D8 · Proposed · 15:12 · Claude Code
**Decision:** Object finding moves to roadmap and passive approach whispering (R19) stays off and optional.
**Why:** The PRD has no object-help requirement, and passive scanning conflicts with the on-request consent rule.
**Affects:** `AGENTS.md`; `hub/IDEAS.md`; the demo scope.

### D9 · Proposed · 15:40 · Claude Code
**Decision:** The core loop is: one Android session per request, one DAT photo capture, HTTPS to Muse Spark, answer spoken through the phone's normal audio output to the glasses over Bluetooth A2DP. The mic uses HFP only when needed. Prove it with the spike in `hub/LOOP.md` before building any feature on top.
**Why:** Meta's docs give camera access through DAT and audio only through standard Bluetooth profiles, and DAT sessions are fragile, so short single-shot sessions are the safest path.
**Affects:** all lanes; PRD capability table; R1 latency target; Q3.
