# META_JUDGING_GATE.md — 100% Rubric-Coverage Contract

> **Read with `PROJECT_CONTEXT.md` before making any product, engineering, UX, pitch, or demo decision.** For G06 and A5 (verified platform facts, which Meta tools are real), start from `hub/META_SUITE.md`; it records what Meta's docs say and what still needs a device.
>
> **Agent directive:** This document is a release gate, not inspiration. **Do not mark the project `JUDGING-READY` until every required gate below has evidence and passes.** If a requirement is not implemented, cannot be verified, or is simulated but presented as real, mark it **FAIL / NOT VERIFIED**, report the blocker, and fix it or explicitly narrow the claim. Never invent evidence, scores, SDK capabilities, statistics, user impact, or API usage.
>
> **Meaning of “100%”:** We aim for **100/100 internal rubric coverage and zero failed hard gates**, not a guaranteed score from independent Meta judges. Only the judges determine winners.

## 0. Authority, weights, and track mission

**Track theme:** *Bringing people closer together with AI.* The product must enable a demonstrably better interaction **between humans**. Helping someone identify an object or perform a task is not, by itself, evidence of human connection.

The supplied **Winner Selection / Judging Criteria (official rules)** are our scoring source of truth:

| Criterion | Official weight | What must be visible to judges |
|---|---:|---|
| **Human Connection** | **30 points** | A real, meaningful improvement to an interaction between people |
| **AI Centrality** | **25 points** | Indispensable, live AI inside the core experience—not decorative chat |
| **Originality** | **20 points** | A defensible, differentiated insight and memorable product interaction |
| **Execution & Demo Quality** | **25 points** | A complete, credible, repeatable working flow, convincingly demonstrated |
| **Total** | **100 points** | All four categories explicitly evidenced |

**Source discrepancy:** Promotional material says execution is 20%; the supplied official judging rules say **25%**. The promo's stated weights total **95%**. Follow the official **30/25/20/25 = 100%** distribution unless organizers issue a newer controlling rules update. Record any verified change here before rescoring.

**Meta APIs:** Muse Spark, Muse Voice Transcribe, Muse Image, and Muse/connector integrations are **encouraged, not required** according to the supplied track description. Use them **only if real access, correct fit, and integration are verified**. Do not force an irrelevant model into the demo for branding. Track each model/tool actually used; $50 API ecosystem credits are an offered resource, not proof of API integration.

## 1. The one-sentence product contract — NONNEGOTIABLE

> **We help people experiencing early-stage memory loss reconnect with consenting loved ones by recognizing who is with them and privately surfacing trustworthy relationship context at the moment of connection.**

**Emotional thesis:** *Forgetting a face should not have to mean losing a connection.*

**Target:** people with early-stage dementia/memory difficulties who can choose to use glasses. **Not** an always-on monitor, medical device, dementia treatment, substitute caregiver, or reliable support for every stage of dementia.

**Hero story, in order:**
1. **Margaret** (fictional wearer) sees **Daniel** (fictional son, opt-in demo participant) and chooses to ask for help.
2. The system identifies Daniel **only if the enrolled match passes its documented confidence/verification policy** and responds discreetly: *“I think that's Daniel, your son.”*
3. It provides a **trusted, enrolled relationship memory or family update** (with source/date when relevant), never a manufactured one.
4. Margaret **speaks directly to Daniel** or reconnects around the remembered subject. **This human-to-human moment is the payoff.** The scene cannot end at an AI answer.
5. **Optional, after P0 is reliable:** find an everyday object using what the camera currently sees **or** a previously saved, verified location—clearly labeled as one or the other.

**Scope allocation:** P0 = identity + relationship + resulting human conversation. P1 = contextual object assistance (only if no P0 tradeoff). P2/roadmap = meals, water, medication reminders, caregivers, broader home assistance. No generic “ask the glasses anything” positioning. No medical adherence claims.

### Every proposed feature must pass this 4-question filter

1. **Connection:** What interaction between two people becomes easier, warmer, or more continuous?
2. **AI:** What indispensable, real inference or grounded language understanding makes it possible?
3. **Originality:** Why is this meaningfully different from a basic vision assistant, contacts app, or reminder app?
4. **Demonstration:** Can a judge *watch the complete action and outcome* in the 2–3 minute demo?

**If any answer is missing, do not implement or promote that feature until the gap is fixed.** Extra features do **not** substitute for gaps in P0.

## 2. Non-negotiable integrity, safety, and feasibility gates

These are **hard release gates** independent of the 100-point score. Any failure means **NOT JUDGING-READY**, even when the self-score would otherwise be 100.

- [ ] **G01 Consent / privacy:** Demo identities are enrolled by consenting participants; wearer has appropriate agency/permission; captures are intentional and privacy practices are explained. Bystanders are not covertly identified.
- [ ] **G02 Honest provenance:** Relationship, kinship, recent events, and memories are drawn only from enrolled/saved, attributable information. Every generated factual claim has a record source or clearly says it is unknown.
- [ ] **G03 Unknown / ambiguous face:** An unenrolled, ambiguous, low-confidence, or failed match never receives a confident name or relationship; an uncertainty/fallback path is demonstrated.
- [ ] **G04 Revocation / deletion:** Enrolled identity data can be removed or effectively disabled in the actual implemented scope; later matching must respect revocation. State retention limitations truthfully.
- [ ] **G05 Truth-in-demo:** Each step is labeled accurately as `LIVE`, `PRE-RECORDED REAL RUN`, `SIMULATED`, or `ROADMAP`. No prerecorded output passed off as inference, no hidden hardcoded answer passed off as generated, no fake SDK/glasses integration.
- [ ] **G06 Actual platform verification:** Exact glasses model, available camera/audio interfaces, paired-phone/backend path, and relevant Meta API/SDK access are confirmed from working implementation or verified documentation. Unsupported functionality is never claimed.
- [ ] **G07 No clinical overclaim:** No claim to cure/reverse dementia, diagnose conditions, prove a medication was taken, replace care, guarantee wearer safety, or operate continuously.
- [ ] **G08 Respect user autonomy:** On-demand, non-infantilizing, brief output; wearer can decline or remove glasses. Losing glasses, battery, connectivity, or confidence has a truthful noncritical fallback.
- [ ] **G09 Runnable demo:** A fresh operator can launch and reset the demonstrated functionality following documented steps, and all essential demos have a recorded backup with honest labeling.
- [ ] **G10 Evidence before checkmarks:** Every checked gate points to something a reviewer can inspect—file/function, test output, timestamped video segment, console/model trace, UX screen, or written operator procedure. Assertions alone are not evidence.

**Hardware honesty:** If the judge demo only runs through a phone/web simulator, it may be a valid software prototype **but is not a fully working glasses demo**. Say exactly which device inputs/outputs are real versus simulated. Do not mark actual-glasses integration as complete.

## 3. The 100-point acceptance scorecard (20 required tests × 5 points)

**Scoring mechanics:** Each of the 20 numbered items is worth **5 points if fully passed and evidenced; otherwise 0**. This is an intentionally strict **internal release checklist**, not Meta's actual hidden scoring method. No partial points; no averaging across items; no bonus points; **100/100 + G01–G10 + submission gates** is required to write `JUDGING-READY`.

### A. HUMAN CONNECTION — 30 points, 6 required passes

- [ ] **H1 — Clearly human stakes (5):** Within the first **25–30 seconds**, viewers understand that failing to recognize a loved one can disrupt a relationship, not simply create inconvenience. One relatable Margaret/Daniel story carries the video. **Proof:** script + video timestamp.
- [ ] **H2 — Actual human-to-human result (5):** The hero demo ends with Margaret directly engaging Daniel after receiving the cue. AI does not replace or monopolize the interaction. **Proof:** uninterrupted demo clip showing the human exchange.
- [ ] **H3 — Relationship depth beyond identity (5):** The device provides an accurate, **source-backed relationship/context clue**, not just a person's name, age, or generic small talk. **Proof:** enrolled source record → retrieved context → output.
- [ ] **H4 — Respectful wearer agency (5):** Help is wearer-initiated (or otherwise demonstrably consensual), brief, discreet, and useful without requiring complicated menus; wearer can ignore it. **Proof:** user flow and on-device/audio demonstration.
- [ ] **H5 — Reciprocal family participation (5):** Family member enrollment includes permission and at least one family-contributed memory/update; the person can edit or revoke it. Show how a **real person's contribution becomes the wearer's conversation context**. **Proof:** setup screen/data and deletion/correction test.
- [ ] **H6 — Social impact is explicit and bounded (5):** Demo explains the **specific moment of connection improved**, the early-stage target population, dignity/privacy limits, and why this supports rather than displaces loved ones/caregivers. **Proof:** 1–2 sentence judge-facing statement + clip. Do **not** invent clinical or real-user impact numbers.

**HUMAN-CONNECTION KILL TEST:** Remove the line where the AI says the name. Does the film still visibly show a strengthened relationship from trustworthy context? If not, the project is still only face identification: improve P0 before adding features.

### B. AI CENTRALITY — 25 points, 5 required passes

- [ ] **A1 — AI is necessary (5):** At least one core inference (e.g., visual identity matching against enrolled profiles) is genuinely model-based, not an `if (Daniel)` branch or manual selection. **Proof:** model invocation, actual input, response/score, mapping to UI/audio, plus test using a different enrolled participant/input.
- [ ] **A2 — AI is connected end-to-end (5):** A fresh, wearer-initiated request flows through **real input → AI processing → opt-in profile/context retrieval → guarded response → actual user-facing output**. **Proof:** timestamped trace + live video + architecture diagram matching deployed code.
- [ ] **A3 — Grounded contextual intelligence (5):** A natural follow-up such as “What did we talk about last time?” resolves to **actual approved family context**. AI helps interpret/retrieve/compose the answer where appropriate; it is not a manually scripted response. Distinguish database lookup from model inference. **Proof:** two different saved contexts produce correctly different answers; inspect source attribution.
- [ ] **A4 — Model behavior is safe and demonstrated (5):** Unknown person, no relevant memory, low confidence, or conflicting context produces *“I'm not sure”* / clarification instead of guessing. **Proof:** reproducible negative tests; prompt/threshold/validation mechanism identified.
- [ ] **A5 — Precisely explained AI architecture (5):** Name actual models and roles (identity, speech recognition, reasoning, retrieval, speech output), inputs/outputs, locations of inference, and what is deterministic logic. If Meta Muse tools were used, show their real invocation; if not, explicitly say so. **Proof:** architecture walkthrough + source/runtime evidence + video narration.

**AI-CENTRALITY KILL TEST:** Replace every AI invocation with a fixed response. If the experience still works equally well with new faces, new memories, and new questions, AI is decorative: **FAIL**. Conversely, do not add gratuitous AI when simple deterministic privacy rules are safer.

### C. ORIGINALITY — 20 points, 4 required passes

- [ ] **O1 — Specific differentiator (5):** Clearly articulate **opt-in identity + private, family-curated relationship memories + an immediate human reunion** as the product's distinct combined experience. **Proof:** one-sentence differentiator and matching video scene.
- [ ] **O2 — Against obvious alternatives (5):** Prepare honest, concise comparisons to (a) a generic camera chatbot, (b) a contacts/family album, and (c) standard reminders. Describe what each already does and the **specific missing connection workflow** this adds. **Proof:** 3-row comparison; do not claim no competitors exist.
- [ ] **O3 — Form-factor insight (5):** Demonstrate why hands-free, in-the-moment, discreet wearable support is meaningfully better for this specific social interaction than unlocking a phone mid-conversation. **Proof:** wearer POV and clear input/output story; if glasses output is simulated, label it.
- [ ] **O4 — Distinctive interaction, not a feature pile (5):** The family memory is surfaced **contextually when the person is present** and used to initiate conversation; optional object finding does not dilute the story. **Proof:** one unified product walkthrough, no scattered feature montage or generic assistant pitch.

**ORIGINALITY KILL TEST:** If the pitch is simply “AI glasses recognize faces and remind people of things,” **FAIL**. The defensible insight is **turning consenting family's real context into reconnection in the moment**. Do not claim novelty as a patent, scientific breakthrough, or world first without evidence.

### D. EXECUTION & DEMO QUALITY — 25 points, 5 required passes

- [ ] **E1 — Real, visible P0 product (5):** One complete **recognize → retrieve relationship context → private cue → human conversation** flow works from a reproducible start state. Human-connection payoff is on film. **Proof:** uncut or clearly annotated live run, code path and start instructions.
- [ ] **E2 — Reliability and negative cases (5):** P0 succeeds **5 consecutive dry runs** from a resettable starting state; unknown/ambiguous person safely fails **3/3 tests**; absent-memory response safely fails **3/3 tests**; one revoke/edit test succeeds. These are **hackathon demo readiness thresholds, not general accuracy claims**. **Proof:** run log with conditions, outcomes, and any manual intervention.
- [ ] **E3 — Complete 2–3 minute video (5):** A **120–180 second** video includes: problem + bringing people closer (~30s), working key demo (~90s), real AI integration (~30s), and actual tools/harnesses/models used in the build (~30s). Adjust overlap to remain within 180 seconds. **Proof:** final video duration and timecoded outline.
- [ ] **E4 — Honest, credible build (5):** Demo shows real interaction (not only slides/Figma), makes backend/phone/glasses division understandable, has no broken critical path, and explicitly labels mocks. **Proof:** runtime/architecture evidence, clear model/tool inventory, demo checklist.
- [ ] **E5 — Submission and resilience (5):** A fresh operator can run, reset, and record the demo; audio/text is intelligible; permissions/network/assets are stable; live and clearly labeled recorded fallback are ready; submission is checked against contest instructions. **Proof:** rehearsal record, operator commands, video link/file, team review.

**EXECUTION KILL TEST:** If the video depends on a hidden operator entering the correct person, on a prerecorded model answer advertised as live, or on a demo that only works once and cannot reset, **FAIL**.

## 4. Exact demonstration to build and film

**Only claim these events if they really happen.** Fictional personas are fine; demo participants must consent to capture/enrollment.

| Time (target 2:40) | What the judge sees | Rubric evidence to capture, not yet passed |
|---|---|---|
| **0:00–0:25** | A consenting teammate plays Daniel; show a greeting interrupted by uncertainty. Narration names the early-stage memory problem and the goal of reconnecting people. | H1, H6, O1 |
| **0:25–0:45** | Brief phone capture of Daniel's consented Family profile and one caregiver-entered memory. Hide real personal data and the API key. | H5, G01, G02 |
| **0:45–1:25** | Uncut wearer request, earcon, one real DAT photo, Muse match, hedged relationship cue and source-backed memory, then the wearer directly resumes talking with Daniel. Show phone state or a separate time-aligned trace. If the backup phone trigger is needed, show and label it. | A1, A2, H2, H3, H4, O4, E1 |
| **1:25–1:50** | One consenting unenrolled-person or no-face request gives an unsure line. Film only after the required negative rehearsals pass; do not call one check an accuracy study. | A4, E2, G03 |
| **1:50–2:20** | Simple diagram and source view: DAT photo → Android app → Muse Spark Standard-mode match → enrolled family record → ResponseGuard → Android TTS over Bluetooth. Distinguish model inference from stored facts and OS speech. | A5, E4, O3 |
| **2:20–2:35** | Show the actual build tools used: DAT 1.0.1, Meta Model API, Android app, and honest live/experimental labels. Do not show D25's unpassed conversation nudge or describe WhatsApp as delivered. | A5, E3, S02, S03 |
| **2:35–2:40** | Return to the two people speaking and finish with the connection thesis. | H6, E3 |

The follow-up “What did we talk about last time?” and the D25 nudge are unbuilt. This plan uses the memory already included in the initial cue. A3 remains FAIL until a separate, grounded follow-up works on the device. Film the hero clip only after its trigger, face result, audible cue and resulting conversation succeed in one run.

**Tradeoff:** If the video must cut something, remove object finding, flashy UI, and extra features **before** removing the Margaret–Daniel conversation, the live AI trace, or truthful limitations. The official suggested breakdown totals ~3 minutes; our outline compresses it to ~2:45 without omitting a required section.

### Minimum UI/flow for the hero experience

```text
[Consenting family setup]
    Daniel profile: name + relationship + permitted photos
    Approved memory/update: content + author + date + permissions
                   |
[Margaret intentionally asks for help]
                   |
[Authorized visual/audio input from VERIFIED platform]
                   |
[Actual recognition/inference + match threshold]
      |                         |
   confident                uncertain/none
      |                         |
[Retrieve enrolled         [Say unsure; don't
 context by profile ID]     fabricate identity]
      |
[Grounded response with source checks]
      |
[Discreet output to wearer]
      |
[Margaret resumes conversation with Daniel]
```

**Source of truth:** AI may phrase a response, but **may not create relationship facts**. Approved records, not a language model's memory, determine names, relationships, and recent family events. Unknown = unknown.

## 5. Required test scenarios (show negative cases, don't hide them)

| Scenario | Expected result | Minimum proof |
|---|---|---|
| Known enrolled Daniel, clear conditions | Appropriate qualified identity cue + correct relationship | Live invocation and saved profile reference |
| Second enrolled person / changed input | A genuinely different identity or safe uncertainty | Second real invocation; no constant output |
| Stranger / no matching consented profile | No invented identity or relationship | 3/3 negative test results |
| Low confidence / poor camera conditions | Calibrated uncertainty, request clarification or stop | Threshold and recorded result |
| Two overlapping/conflicting family facts | Ask or disclose uncertainty; never silently pick fiction | Record set + output |
| Relevant family memory exists | Answer from right record with provenance | Exact record ID/author/date mapped to answer |
| Family memory does not exist | Say no saved information; don't improvise | 3/3 negative results |
| Family edits/revokes Daniel's enrollment | Edits reflected; revoked data no longer used | Before/after test + deletion/disable evidence |
| Network/API/authentication failure | Gentle, truthful unavailable state; no fabricated data | Forced failure test |
| No glasses / powered off / removed | Assistance stops; no care-critical dependence | UX behavior and honest explanation |
| Optional object lookup (if built) | Only describe current-view detection OR a previously saved, verified location | Input/frame or stored record, clearly identified |

**Do not publish these dry-run results as face-recognition accuracy or clinical validation.** The data set is too small; it only proves demo repeatability in stated conditions.

## 6. Likely judge objections — required 1–2 sentence defenses

| Objection | Defensible answer / demonstration requirement |
|---|---|
| **“Why Meta? Why social?”** | Because the intervention isn't identifying an object; it helps Margaret **resume a conversation with Daniel** using their real shared context. The product strengthens the relationship rather than replacing it. |
| **“Isn't this a generic visual AI assistant?”** | A general assistant describes what's visible. This experience uses **consenting identity enrollment, private relationship data, trust/provenance, and a conversation-ready cue at the right social moment**. Demonstrate all four. |
| **“Why glasses rather than a phone?”** | Hands-free, in-the-moment, discreet assistance avoids interrupting a greeting to unlock and point a phone. Demonstrate this benefit; disclose if glasses integration is simulated. |
| **“What about battery life?”** | Capture/inference is **intentional and brief**, not promised 24/7 scanning. Any battery or latency claim needs actual measured hardware evidence; don't make up hours saved. |
| **“What if the person takes the glasses off?”** | Assistance stops. This is intended for people with early-stage needs who choose to use glasses; it isn't a safety-critical wearable or replacement caregiver. |
| **“What if it recognizes the wrong person?”** | Use permissioned matching, a documented uncertainty threshold, and abstain/clarify for unknown or ambiguous results. Show the negative test, not a claim of perfection. |
| **“Could it invent memories?”** | Identity/relationship facts come from approved stored records, with provenance. On missing/conflicting information, it explicitly says it doesn't know. |
| **“Is this medical treatment?”** | No. This is a prototype for social assistance, not diagnosis, therapy, medication compliance, or clinical care. |
| **“What really runs on Meta hardware/models?”** | Show and name **only verified integrations** and exactly which actions run on glasses, phone, local backend, cloud, or a simulator. |

## 7. Agent implementation protocol — treat these as system-level project rules

**At the start of EVERY implementation task:**
1. Read `PROJECT_CONTEXT.md` and this file. Inspect actual source files, setup, running services, APIs, permissions, tests, and device availability.
2. State current implementation status: `WORKING / PARTIAL / MOCKED / ABSENT / UNKNOWN` for **identity matching**, **family context**, **voice I/O**, **glasses input/output**, **privacy/revocation**, and **demo tooling**. `UNKNOWN` is never equal to `WORKING`.
3. Find the **highest-priority failed gate**. Prioritize G01–G10; then H/A/O/E gates, focusing on P0 first. Do not expand P1/P2 to avoid P0 failures.
4. Before writing code, name **which numbered acceptance item(s)** the change targets and exactly **what observable proof** would count as a pass.
5. Implement the smallest end-to-end vertical slice that establishes proof. Do not silently replace hardware/model calls with canned data. If a simulator is required, isolate it behind an explicitly labeled adapter.
6. Run the exact proof test or visibly document why it could not be run. Include conditions, failure logs, and manual steps; don't claim testing you didn't execute.
7. Update the evidence ledger below with concrete artifact references. Recalculate internal score **only for truly passed items**.
8. End each agent response with: **Changed files → Tests actually run → Real vs simulated components → Newly passed/failed gates → Next blocker**.

**Blocking instruction:** If anyone says “we're at 100%,” “ready to submit,” or “winning Meta,” but even **one** of the 20 scored items, G01–G10, or S01–S06 below is unevidenced, respond: **`NOT JUDGING-READY — missing: [exact IDs]`**. Do not rubber-stamp, manufacture screenshots, or lower thresholds retroactively to make the number reach 100.

**Override policy:** Team members may deliberately change priorities, scope, or internal tests, but they must record a dated change, reason, and impact on the judging categories. An agent may not unilaterally remove a hard gate, declare a mock real, or redefine the product as generic help to boost its self-score.

## 8. Submission gates

- [ ] **S01** Final demo video is **2–3 minutes (120–180 seconds)**; exact duration checked.
- [ ] **S02** Video visibly covers human-connection problem/solution, working features, central AI role/integration, and tools/harnesses/models used to build.
- [ ] **S03** All claims in demo, README, pitch, and captions match what was **actually implemented and tested**; mocks, simulations, and future ideas labeled.
- [ ] **S04** Submission information is complete according to the current official portal/rules; team eligibility confirmed; do not assume video alone is all that's required.
- [ ] **S05** Audio intelligible, screen/interface legible, participant consent confirmed, links playable, no private personal data/keys exposed.
- [ ] **S06** Primary run and backup demonstrated with at least one teammate not operating the original build; commands/assets/credentials safely provisioned.

## 9. Audit ledger — MUST stay updated in the repo

> Keep `PENDING` until verified. Do not fill with guesses. One item per 5-point gate; attach **real** file paths, test IDs, timestamps, outputs, and notes. This document may be the only ledger needed—do not create empty evidence docs and claim they count.

| Item | State (`PASS`/`FAIL`/`PENDING`) | Evidence (file/test/video timestamp) | Owner / next action |
|---|---|---|---|
| H1 | FAIL | No script or video yet. | Lane 5: write the 25-30 s opening. |
| H2 | FAIL | No demo clip of Margaret turning to Daniel. | Lane 5: film after E2 runs. |
| H3 | PENDING | Built: `Person.memories` -> `FamiliarFaces.whisperFor` -> `ResponseGuard`. No recorded record-to-output trace. | Lane 2: log one run with record id and spoken line. |
| H4 | PENDING | Built: DAT Speech phrase, experimental double-tap via Bluetooth media events (next/previous now handled; physical flow not yet verified; single tap is DAT pause/resume), quiet Home backup control. On 2026-10-25 the backup produced an audible unsure line through the glasses (user report, `hub/status/mayank.md`); spoken trigger remained intermittent. | Lane 2: record an uninterrupted wearer-initiated glasses-triggered run. |
| H5 | PENDING | Built: Setup > Family (consent, memories add/delete/reorder, revoke deletes record, photo, visits). Revoke not re-checked on a phone with the D19 build. | Lane 4: revoke-then-ask test on the phone. |
| H6 | FAIL | No judge-facing statement or clip. | Lane 5. |
| A1 | PENDING | Built: `MuseIdentity` (D12). Status file: 5 of 5 match verdicts, 2 with two enrolled; log holds Muse's verdict, not ground truth. | Lane 2: logged run with a second enrolled person. |
| A2 | PENDING | Path built end to end; no timestamped trace or video. | Lane 2: capture one trace. |
| A3 | FAIL | Follow-up "What did we talk about last time?" not built. D25 nudge remains a stopped spike: wearer ASR and photo during HFP observed, second speaker/DAT Speech sharing not proved, Spark BAD_ANSWER (`hub/status/mayank.md`). | Lane 2: keep follow-up out of the video until a live run passes. |
| A4 | FAIL | No stranger, no-face or no-memory trials. D21 is on `main` (a Muse failure says "I can't check right now"). | Lane 2: 3/3 negatives. |
| A5 | FAIL | No architecture walkthrough or narration. Inputs: `hub/META_SUITE.md` section 8, `hub/PRD.md` section 2. | Lane 5. |
| O1 | FAIL | Differentiator sentence exists (section 1); no matching video scene. | Lane 5. |
| O2 | FAIL | 3-row comparison not written. | Lane 5. |
| O3 | FAIL | No wearer POV footage. | Lane 5. |
| O4 | FAIL | No walkthrough yet. Scope narrowed to Familiar Faces for the demo (D20); medication is reminders only, no bottle check (D24). | Lane 5. |
| E1 | FAIL | No filmed run; no demo reset. | Lanes 2, 5. |
| E2 | FAIL | No 5-run log, no negative tests, no reset, no unit tests (D13 not adopted). | Lane 2. |
| E3 | FAIL | Section 4 now has a truthful 2:40 shot plan; video not recorded or timed. | Lane 5: film and check final duration. |
| E4 | PENDING | Real glasses capture and Muse calls (status files); mocks and experimental parts labeled in `core/Capability.kt` and on Home. MockIdentity removed and key out of the APK (D21, D22, ef9d32c); lint 0 errors. | Lane 2: run D21/D22 on the device. |
| E5 | FAIL | Draft `docs/demo-script.md` covers setup, run order and labeled fallback; no reset or recorded fallback, and no fresh-operator rehearsal. | Lane 5: rehearse and record fallback. |

**Hard-gate status:** G01–G10 = `NOT VERIFIED` until every item is checked against evidence.

**Submission status:** S01–S06 = `NOT VERIFIED` until every item is checked against evidence.

**Self-score:** `5 × count(PASS among H1–H6, A1–A5, O1–O4, E1–E5) = 0/100` (2026-10-25).

**Ready-state formula:**

```text
JUDGING-READY =
    (internal_score == 100)
    AND all(G01..G10 have reproducible evidence and PASS)
    AND all(S01..S06 have reproducible evidence and PASS)
    AND no critical execution path is mislabeled or unverified

ELSE: NOT JUDGING-READY; name the exact blockers.
```

**Last audit:** `2026-10-25 ~02:50, D25 spike report, rehearsal log and 2:40 shot plan; no scored gate newly passed`
**Verified models/API calls:** `PARTIAL: muse-spark-1.3 calls recorded in hub/status/ (no inspectable trace)`  
**Verified glasses hardware flow:** `PARTIAL: capture, Speech start, A2DP, MuseIdentity matches recorded in hub/status/; see AGENTS.md`  
**Verified end-to-end live demo:** `NOT VERIFIED`  
**Final submission video:** `NOT RECORDED / NOT VERIFIED`

## 10. The final quality bar

Judges should be able to say **yes** to all four in one viewing:

1. **Human connection (30%):** *I watched one person reconnect with someone they care about—not merely receive an AI answer.*
2. **AI centrality (25%):** *This could not work in the demonstrated form without real, appropriately grounded AI.*
3. **Originality (20%):** *Its private, family-informed, in-the-moment reconnection experience is distinctly more than basic face recognition or a reminder bot.*
4. **Execution (25%):** *I saw a real, legible, repeatable product, understood what actually ran, and could defend the demonstration against obvious failures.*

**Final rule for all agents:** Optimize for **a believable human reunion caused by real AI**. Never trade the reunion, consent, grounded truth, or honest working-demo evidence for feature count, visual polish, or unverified technical spectacle.
