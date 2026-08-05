# HALO ALPHA CONTRACT

This document serves as the temporary constitution for the Halo Cognitive Operating System until the Alpha Gate is formally exited.

## Mission
Halo is no longer trying to prove that a cognitive architecture can be designed.
Halo is now proving that such an architecture can become a usable product.

## The Rule
Before proposing or implementing any feature, pull request, or architectural change, you must ask:

> **Does this improve one of: Stability, Reliability, User Experience, Observability, or Performance?**

If the answer is **No**, the feature belongs in the Post-Alpha Backlog. Do not implement it.

## Exit Criteria
Halo Alpha is considered complete when:
- [x] Installs on a physical Android device.
- [x] Starts without crashes.
- [x] Initializes every core engine.
- [ ] Supports passive wake-word interaction.
- [x] Supports manual in-app microphone interaction.
- [x] Supports developer command injection.
- [x] All three interaction methods converge into one cognitive pipeline.
- [x] Speech → Interpretation → Reasoning → Planning → Execution → Response works end-to-end.
- [ ] The user can observe every stage through the cognitive timeline.
- [ ] Runtime diagnostics expose engine state, Blackboard state, provider state, and logs.
- [ ] Around 10–20 polished commands execute reliably.
- [ ] The application feels responsive and stable during continuous use.

*Feature creep is the enemy of Alpha. Stick to the contract.*
