# halo 7.4 · fixes

| # | Problem | Fix | Files |
|---|---------|-----|-------|
| 1 | The app may not open: no enabled launcher after the 6.4 icon aliases | MainActivity stays enabled, no LAUNCHER; Classic alias is the manifest launcher; switcher enables new → disables old; `repair()` on start | `manifest/AndroidManifest.launch-fix.xml`, `java/HaloAppIconSwitcherSafe.kt` |
| 2 | Repo Classic icon ring gap centred ~29° (near 2:30) | Gap centred 45° (1–2 o'clock), arc start 22.5°, sweep 315°, round caps | `res/drawable/ic_launcher_foreground.xml`, `res/drawable/ic_halo_brand_halo.xml` |
| 3 | Code says "Mini Halo / Ask Halo / Open Halo" | lowercase everywhere (EN + RU) | `res/values/strings_names_fix*.xml` |
| 4 | Brand book contents stopped at p 254 | p 2 updated; Chapter 23 opening page (p 225) is now a full index to p 281 | Brand book v4.4 |

## Check on a phone (not yet done)
1. Clean install → icon appears → it opens.
2. Make it yours › App icon → switch 3 times → exactly one icon each time, and it opens.
3. Force-disable all aliases with adb → restart → Classic comes back.
4. Search the APK strings for "Halo" with a capital H in user-facing text → none (the class names don't count).
