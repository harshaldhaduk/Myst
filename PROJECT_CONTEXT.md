# PROJECT_CONTEXT.md — Smart Glasses for Memory & Connection

> **Superseded in part (2026-10-24):** `hub/PRD.md` (HackTX PRD, D4) now sets scope: Medication Routine is a P0 feature beside Familiar Faces, object help is roadmap, and the stack is Meta only (D5). Where this file disagrees on those points, the PRD and `hub/DECISIONS.md` win. The rest (thesis, privacy, judge answers) still stands. For what Meta's platform actually provides (section 8's open questions), read `hub/META_SUITE.md`.


> **Purpose of this file:** Give coding agents and teammates a single source of truth for the product vision, hackathon storytelling, demo priorities, and constraints discussed so far. Read this before suggesting features, making architectural decisions, implementing flows, or writing the pitch.
>
> **Status:** Product direction and proposed demo, **not** a record of completed implementation. No repository, codebase, SDK integration, hardware access, or working capabilities were inspected in this conversation. Treat everything marked *planned* as unimplemented until verified in the repo.

## 1. Elevator pitch

**For people experiencing early-stage dementia or memory loss, we're building a smart-glasses-based social companion that helps them recognize loved ones and reconnect with the context of their relationships.** When helpful, that same companion can also support everyday memory tasks, such as locating familiar objects.

**The key differentiator is not generic visual Q&A. It is meaningful social context:** *who is in front of me, how do I know them, and what can help us connect right now?*

One-line narrative:

> **We're not trying to give people with dementia perfect memory. We're trying to make sure that forgetting a face doesn't have to mean losing a connection.**

The product aims to **reduce friction in relationships**, not cure dementia, restore memory, replace caregivers, or deliver medical treatment.

## 2. Context: hackathon, platform, and why this matters

- This is a **hackathon project targeting the Meta track**, with **Meta smart glasses** as the proposed wearable interface.
- A judge/team conversation emphasized Meta as a **social company**, so **social impact and human connection** should be central to the product and pitch.
- Our working assumption is that **a compelling, cohesive demo and story matter enormously** to judging. The team's informal framing was roughly “90% pitch, 10% final result”; **this is a strategic assumption, not an official judging rubric**.
- Motivation: firsthand observation of a neighbor's family dealing with the distress of a person with dementia not recognizing her husband or son. The human problem isn't just failure to identify a face; it's the disruption of connection between loved ones.
- Another genuine problem is loss of independence and increased caregiver burden around common objects, hydration, meals, medication reminders, and navigating daily routines. Those are valid but **should not compete with the central social story**.
- The team discussed a broader “ask [assistant/Muse] anything” model, but a generic AI assistant is **not enough differentiation**. Existing voice/vision assistance is an enabling layer, not the product's unique value.

## 3. Product strategy: ONE purpose, prioritized capabilities

**North star:** Help people with early-stage memory impairment maintain **confidence, dignity, independence, and connection with loved ones**.

### P0 — Core experience: person recognition **plus relationship context**

This must be the main story, the most polished demo, and the first implementation priority.

Conceptual flow:

1. A person who has **explicitly opted into enrollment** (e.g., a son or spouse) is in front of the wearer.
2. The wearer **intentionally requests help** (e.g., “Who is this?”) or another demonstrably supported trigger initiates a brief recognition interaction.
3. The system matches the person against an approved, enrolled profile (only if matching is technically supported and confidence is adequate).
4. Through discreet audio, the wearer receives a **short, respectful cue**:
   - “I think that's Daniel, your son.”
   - Optional approved memory: “You both love going to the park.”
5. The wearer may ask a follow-up such as “What did we talk about last time?” The system retrieves **actual saved, family-provided context**, if available.

**Crucial product distinction:** Basic face identification is useful, but a name **plus trusted shared memories, relationships, and conversation starters** is more valuable for the Meta/social angle.

Suggested consent-based family/caregiver setup data:

- Person's name and photo(s) for opt-in enrollment
- Relationship to the wearer
- Trusted, short shared memories and conversation starters
- Recent family updates, optionally with attribution and timestamps
- Enrollment/consent and ability to edit or revoke

**Do not fabricate family memories or confidently assert an uncertain identification.**

### P1 — Supporting experience: everyday object assistance

Examples:

- “Where is my glasses case?”
- “Can you help me find my keys?”

This should appear as a **natural extension of the same memory-support companion**, not a second equal product pillar.

A safe demo could identify an item in the **current camera view** or retrieve a **previously saved, explicitly recorded location**. Do **not** claim continuous object tracking, accurate historical location, persistent room understanding, or 3D scanning unless those functions are actually implemented and demonstrated.

### P2 / future roadmap — routines and caregiver assistance

Ideas discussed: meal/water reminders, medication reminders, retrieving stored items, caregiver-facing configuration, more spatial awareness, and reduced interruptions for caregivers.

These may be valuable later, but **do not lead the hackathon pitch with medication adherence or clinical/medical monitoring**. They introduce safety, accuracy, and scope concerns. Never claim medication ingestion, dosage correctness, or medical compliance verification without validated evidence. Any demo reminder should be characterized as a reminder, **not** proof that a medicine was taken correctly.

### Underlying interface, not headline feature

A short voice request + glasses camera input + audio cue can connect these experiences. The team referenced Meta AI/Muse as a possible enabling assistant experience; **availability, SDK access, supported APIs, and actual integration must be verified**. Do not present a particular assistant or platform ability as implemented merely because it was discussed.

## 4. Demo / presentation: tell ONE story

Use a single fictional demo persona rather than presenting disconnected use cases. Names below are **illustrative placeholders**, not real patient data.

**Persona:** Margaret is living with early-stage dementia. Her son Daniel visits her, but lately she sometimes struggles to recognize him.

### Scene 1 — The emotional hook (the hero moment)

Daniel arrives. Margaret looks uncertain, asks for help, and hears a short, private prompt:

> “I think that's Daniel, your son. You both enjoy visiting the park.”

**Value shown:** The device makes the beginning of a human interaction easier. The goal is not to turn Margaret into a passive observer of AI, but to help her engage directly with Daniel.

### Scene 2 — More than a name (the differentiator)

Margaret asks:

> “What did we talk about last time?”

Using **saved, attributed family context**, the assistant replies, for example:

> “Daniel told you about his daughter's first day of school.”

**Value shown:** Real relationship continuity and conversational context, not a generic image-captioning demo.

### Scene 3 — Small everyday win (supporting capability)

Later, Margaret asks where her glasses case is. The device either detects it **in view** or points to a **stored, previously verified location**.

**Value shown:** The same companion also helps with small daily memory challenges; this is not a new app or a separate product story.

**Narrative thread across all scenes:** Memory loss can interfere with connection and confidence. A familiar, discreet wearable delivers context at the exact moment it helps.

### Story / effort weighting (guidance, not fixed requirements)

- **Main pitch narrative:** ~70% people + relationship context; ~20% everyday-object help; ~10% underlying assistant/glasses interaction.
- **Proposed build-time emphasis:** ~60% polished person/context flow; ~25% end-to-end demo reliability and story; ~15% object support, **only after the hero flow works**.
- Prefer **one truthful, working, emotionally resonant interaction** over several disconnected or simulated capabilities presented as real.

## 5. What to ask mentors / judges

Ask for feedback on **track fit, distinctiveness, and scope**, rather than asking “Do you like our idea?”

1. **Track fit:** “For Meta's track, would you value a focused product that deeply addresses social connection, or a broader assistant that addresses several challenges faced by people with dementia?”
2. **Why glasses / why Meta:** “What would make a smart-glasses project feel uniquely suited to Meta's platform, rather than an AI assistant that happens to run on glasses?”
3. **Demo scope:** “If our strongest demonstration is helping someone recognize their loved ones, would showing object-finding as a second capability strengthen the story, or dilute it?”
4. **Credibility:** “What would you need to see in a two-minute demo to believe this is a real product rather than just a compelling concept?”

Keep the setup short (about 20 seconds) and get candid feedback. **No actual answers from judges to these questions have been recorded here.**

## 6. Expected judge objections and our answers

### “What about battery life?”

**Design principle:** Short, intentional interactions rather than 24/7 video recording or continuous face recognition.

Suggested answer:

> “We intentionally designed this around meaningful moments, not continuous surveillance. When the wearer asks for help, the system briefly captures visual context, looks up the relevant information, and responds through audio. We would use caching and battery-aware behavior where technically supported. It's an assistive layer, not a life-critical monitoring system.”

Potential optimizations, **subject to platform support**:

- On-demand capture instead of continuous camera streaming
- Cache approved identity profiles / relationship context
- Offload expensive inference to paired phone or backend when feasible
- Battery-aware feature prioritization and charging routines

**Do not promise specific runtimes or low-energy performance without measuring the actual device and app.** Manufacturer specs, if used in the pitch, should be freshly verified for the specific model.

### “What if the patient forgets the glasses or takes them off?”

**Product decision:** Focus the first version on **people with early-stage dementia or memory difficulties** who can generally put on, tolerate, and use glasses. We are not promising reliable wear for every dementia stage.

Suggested answer:

> “That's why we designed this for people who can still independently use glasses but sometimes struggle with recognizing people or recalling context. It's not a replacement for a caregiver or something that has to work 24/7. If they take the glasses off, the assistance stops—and that's okay. The value is in the moments they choose to use it.”

Product implications:

- Favor integration into an **existing glasses-wearing routine**, if compatible with the hardware
- Simple, repeatable voice interactions; minimal navigation
- Caregiver-supported setup / reminders, not intrusive forced compliance
- Respect refusal/removal: never imply wear can or should be enforced
- Do not position as a replacement for essential care or emergency monitoring

### “Isn't this just Meta AI / Muse / a generic assistant?”

Our distinction is **private, consent-based family enrollment + trusted relationship memories + conversational context at the moment of meeting someone**. Generic vision Q&A is infrastructure, not the value proposition.

### “How accurate and safe is face recognition?”

The demo must not suggest that probabilistic recognition is infallible. Use enrolled, consenting participants; allow uncertainty and correction; do not hallucinate names, roles, or memories. If confidence is insufficient, say the identity isn't certain, ask for clarification, or refrain from identifying.

## 7. Privacy, safety, and accessibility requirements

This product is for a **vulnerable population**, so these are core design constraints, not polish:

- **Consent:** Only recognize people who have opted into the relevant enrollment/use. Obtain appropriate permission and respect the wearer's preferences and decision-making capacity.
- **Minimize capture:** Prefer brief, intentional capture over continuous bystander scanning or surveillance. Respect applicable privacy and recording rules.
- **Limit storage:** Store only necessary face/profile/memory information; protect it, and support revocation and deletion.
- **Truthfulness:** Use sourced, family-entered memories. Clearly distinguish stored facts from AI guesses. Handle “not sure” gracefully.
- **Dignity:** Avoid infantilizing prompts. Use calm, concise, optional audio and let the wearer continue the conversation naturally.
- **Accessibility:** Favor simple prompts, low cognitive load, adjustable repetition/volume where possible, and caregiver-supported setup.
- **Medical boundary:** Not a diagnosis, cure, clinical memory therapy, medication verification, fall detector, emergency response system, or substitute for human care.
- **Graceful degradation:** If the battery dies, a face is not recognized, the service is offline, or the glasses are removed, do not promise continuous support or allow critical care to depend on the system.

## 8. Technical feasibility: verify before implementing or pitching

**No tech stack or codebase has been verified in this conversation.** A coding agent must inspect the actual repo, connected hardware, SDK docs, API permissions, and existing functionality before making technical claims or edits.

High-priority questions:

1. What **exact Meta smart glasses model** do we have?
2. Can our app legally and technically get **camera frames** and deliver **audio output** through the supported developer interfaces?
3. Are face detection, embedding/matching, or scene-understanding APIs available? If not, what authorized processing path is feasible?
4. What triggers capture (explicit speech, button, supported event)? **Do not assume passive approach detection** exists.
5. Can the device/SDK support paired-phone or backend inference? What are the latency and network costs?
6. How do consenting family members enroll, edit, and delete profiles and memories?
7. Where are images/embeddings/relationship records stored? How are access and deletion handled?
8. What can realistically be demonstrated **live**, and what must be explicitly labeled a mock or simulation?
9. For object assistance, is the system seeing an object **now** or retrieving its **previously stored location**? These must not be conflated.
10. What are the actual battery/runtime and connectivity characteristics **under our implementation**?

### Suggested conceptual architecture (NOT a claim about the repo)

```text
Wearer intentionally requests help
             |
       Smart-glasses input
       (audio / permitted image)
             |
     Input handler / coordinator
        /                 \
  Person recognition     Object assistance
  (opt-in matches)       (current view OR saved location)
        |                     |
  Approved family        Grounded object info
  context / memories             |
        \                     /
         Safe response composer
                 |
         Short audio response
```

Architectural principles: clear separation between identity matching and memory retrieval, sourced family context, uncertainty handling, data minimization, easy demo reset, and an auditable distinction between actual sensor output and mocked demo data.

## 9. MVP acceptance criteria (proposed)

**Hero demo is ready when:**

- [ ] A consenting demo family member can be enrolled with a name, relation, photo(s), and one or two real/clearly fictional demo memories.
- [ ] A supported and **intentional** interaction initiates a recognition attempt.
- [ ] The wearer gets a **short, contextually useful audio cue** for a confidently matched enrolled person.
- [ ] The wearer can ask one natural follow-up and receive an answer grounded in stored family context.
- [ ] Unknown / uncertain faces are handled gracefully; names and memories are not invented.
- [ ] The experience can be repeated reliably in the live hackathon environment.
- [ ] Demo operators can clearly explain which steps run on glasses, phone, server, or mock.

**Optional enhancement after the hero flow is reliable:**

- [ ] One concrete object-finding flow works with a truthful description of whether it detects an item in view or uses a previously recorded location.

**Pitch is ready when:**

- [ ] The first 20–30 seconds communicate the relationship problem and human stakes.
- [ ] The Meta/social fit and necessity of a discreet hands-free wearable are clear.
- [ ] The demo centers on one wearer and one loved one, rather than a menu of disconnected features.
- [ ] We can answer battery life, forgotten glasses, reliability, privacy, and “why not generic AI?” honestly.
- [ ] We distinguish **working product**, **prototype mock**, and **future roadmap** explicitly.

## 10. Open decisions — don't silently assume answers

- Final project name, branding, and tagline
- Exact supported hardware / SDK / platform integration
- Recognition model and inference location
- Voice UX and on-demand trigger
- Actual data model and storage/security implementation
- Whether caregiver/family profile setup exists or needs building
- Whether object finding makes the live demo or remains roadmap
- Real measured recognition accuracy/latency/battery performance
- Scope and content of any judges' actual feedback
- Which pieces are already implemented in the current repo

## 11. Instructions for coding agents

1. **Inspect the repo first.** Report what exists, what is missing, and what is mocked. Do not assume the conceptual architecture matches the code.
2. **Preserve the product thesis:** person recognition + relationship context is P0; object help is P1; medication/caregiver systems are roadmap unless explicitly prioritized.
3. **Prioritize a truthful, repeatable end-to-end hero demo** over speculative breadth or impressive-sounding but unsupported functionality.
4. **Flag hardware/API limitations immediately** with grounded alternatives. Never assert Meta/Muse capabilities that haven't been verified.
5. Build around **opt-in identity data, sourced memories, uncertainty handling, and privacy controls**.
6. For each proposed feature, explain how it improves the **one social-connection story**. If it doesn't, defer it.
7. **Separate facts from aspirations** in code comments, README text, slides, and judge-facing claims.
8. When making changes, state which files/functions changed, how to run/test them, any mocks or hardcoded data, and what was actually verified.

---

### Short summary for any agent with limited context

We're building a **Meta smart-glasses social companion for people with early-stage memory impairment**, especially to help them recognize consenting loved ones and retrieve **trusted relationship memories**. The product is **about preserving human connection**, not generalized “ask AI anything” functionality. A secondary feature may help locate everyday objects, but it should support rather than dilute the story. The pitch uses a fictional wearer (Margaret) and son (Daniel), moving from recognizing him to recalling shared context, then optionally finding an object. Focus on a privacy-respecting, voice-triggered, reliable, truthful demo. Our main judge defenses are: **on-demand interactions for battery**, **early-stage users + optional use for forgotten glasses**, and **family-contributed social context as differentiation**. Everything technical must be checked against the actual repository and Meta hardware/SDK before being claimed as implemented.
