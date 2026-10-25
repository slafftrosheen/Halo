# Study mastery metadata localization

Checkpoint22's real RU Study screen displayed English Mastery, New and memory-duration metadata. Keep source/question/answer/locator strings unchanged. Format only typed StudyMasterySnapshot fields through the existing app catalogue, preserving unmeasured versus measured-zero, existing due-date/day/month calculations and all scheduling ownership.

First expose the existing production badge internally for a rendered regression. Four EN/RU/LV/PL tests cover unmeasured, measured-zero, due-now/due-later and all duration bands at100/200% font in360dp; these are synthetic presentation checks, not learner-performance or physical evidence. Record expected RED before implementing copy. Then add reviewed catalogue rows, regenerate via scripts/generate_ui_languages.py, and reuse typed localized status in the existing mastery detail view. Do not parse or translate arbitrary domain/source strings. Preserve existing Study question/header geometry assertions using the localized host headline.

Run the new rendered cases, existing Study layout/draft/source ordering and language/catalogue checks together, then build both APKs at one integration checkpoint. No voice/user verification wait; physical UI remains blocked while the phone is securely locked. Root owns builds, deployment and publication. No runtime/model/permission changes.
