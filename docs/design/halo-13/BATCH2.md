# halo 13.0 · Batch 2 — Onboarding · what's in this pack

Repo check: 99aac2f (23 Nov 2026), unchanged since batch 1. The pack only adds files and overwrites nothing. Its paths mirror the repo.

- **Spec:** docs/design/halo-13/ONBOARDING_13.md
- **Boards:** boards/en/143–148.png. Board 138 is updated: its "before" mock no longer quotes a download size the repo doesn't state.
- **Code (reference, not compiled):** ui/onboarding/v13/OnboardingFlow.kt, AskWhenNeeded.kt, SetupCards.kt, KidsHandOver.kt, MoveFromOldPhone.kt, plus test OnboardingFlowTest.kt
- **Data:** assets/onboarding/onboarding-13.json, ask-when-needed.json (17 kinds of access), setup-cards.json
- **Strings:** res/values{,-ru}/strings_onboarding_13.xml (54)
- **Icons:** 18 × SVG + vector drawable
- **Lottie:** 5 × dark/light (res/raw/halo13_motion_{hello,handover,ask_access,move_phone,ready}_*.json)

Not done yet: device testing, the people tests, a security review of phone-to-phone moving, and a native Russian review.
