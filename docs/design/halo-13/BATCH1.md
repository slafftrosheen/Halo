# halo 13.0 · Batch 1 — Foundations · what's in this pack

Repo check: 99aac2f (23 Nov 2026). The pack only adds files and overwrites nothing. Its paths mirror the repo.

- **Spec:** docs/design/halo-13/FOUNDATIONS_13.md
- **Boards (2400 × 1500):** docs/design/halo-13/boards/en/137–142.png
- **Code (reference, not compiled):**
  - ui/adaptive/v13/ExperienceLevel.kt, DynamicSurface.kt
  - ui/plain/PlainWords.kt, plus test PlainWordsRatchetTest.kt
  - ui/theme/v13/LookEngine.kt
  - wallpaper/v13/SkyTimeline.kt
- **Data:** assets/plain/glossary-13.json, assets/adaptive/v13/surfaces-13.json, assets/looks/looks-13.json
- **Strings:** res/values{,-ru}/strings_foundations_13.xml (37 EN + RU)
- **Icons:** 25 × (assets/halo/artwork/ic/*.svg + res/drawable/*.xml)
- **Lottie:** 5 × dark/light in res/raw/halo13_motion_*.json, with frame previews in docs/design/halo-13/motion
- **Source for the boards:** docs/design/halo-13/source. To render, run `python3 render13.py 137,138 out` next to the 12.0 source files (t10.css, t12.css, t12.js, icons.js, fonts, img).

Not done yet: device testing, a by-eye review of the boards, and a native Russian review.
