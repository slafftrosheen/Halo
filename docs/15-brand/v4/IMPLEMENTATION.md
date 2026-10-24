# halo DevKit 2.0 — implementation guide (read this first)
**Interface 4.0 update:** [INTERFACE_4_0.md](INTERFACE_4_0.md) supersedes the persistent tab/rail and Orb gesture recommendations below. Keep this guide's visual tokens, artwork, user-facing contract and screen references.
Brand Book **3.0** (224 pages) · DevKit **2.0** · 2026-10-19 · design pinned to repo `slafftrosheen/Halo @ 3f217ccb`
This replaces DevKit 1.4 / Brand Book 2.3 and everything older. Design status and implementation status are tracked **separately**: everything designed here is "not verified on a device" until QA attaches a real screenshot.

## 0. The contract (`USER_FACING.md`)
- **Finished, user-facing app.** Settings is one list. **Everything technical lives in Settings › Advanced.** Developer tools is a switch inside Advanced (with a confirm). When it's off, lab routes refuse to open.
- **No IDs, endpoints, tokens, logs, model file names or diagnostics on user screens.** Each package lists the strings in today's code that must change.
- **Honest states:**
  - Connected ≠ Ready.
  - "Delivery not confirmed".
  - Speech counts as working only after "Did you hear halo?" → Yes.
  - A test means a real answer.
  - The cloud is never preselected.
- **Lowercase "halo"** in all copy. Apricot = halo needs you, red = error, Mint ring = logo (never re-coloured).

## 1. Where everything is
| You need | Go to |
|---|---|
| Every route → screen → Back → visibility | `product/A/spec/halo_product_matrix.json` · `halo_routes.csv` (56 routes + proposed `screen`) |
| Every design ID with states, host file, status | `product/A/spec/halo_designs.csv` (95 IDs, all designed) |
| Screens, per package | `product/<pkg>/screens/<ID>_<state>_<dark|light>[_w412|_fs200|_<accent>_<tone>].png` (2×) |
| Interaction tables (input → event → result → needs) | `product/<pkg>/spec/package_<pkg>.json` + each board |
| What a package locks + plain-words fixes | `product/<pkg>/PACKAGE_*.md` |
| Clickable prototype (33 journeys) and editable source | `product/B/source/prototype.html` (open in a browser) · `shot.html?s=ID&st=state&t=dark&acc=mint&tone=warm&w=412&fs=2` |
| Motion films (31) + reel | `product/<pkg>/motion/*.mp4` · `product/B/motion/halo-reel-2027.mp4` |
| Lottie (v1.4 + 9 new) + still SVGs for reduced motion | `fx/lottie/` · `fx/static/` |
| Compose references | `fx/HaloFx.kt` (1.4) · `fx/HaloTaskFx.kt` (receipts, Stop-safe queue) · `fx/HaloLightFx.kt` (aura, screen edge, `lightFor(phase)`) |
| halo Screen data contract | `screen/ScreenContract.kt` · `widget_registry.json` · `layout_example.json` |
| All strings (1,464 keys, English final, lv/ru/pl slots empty) | `product/strings/halo_strings_all.json` · `values/strings_halo_v3.xml` |
| Tokens, themes, icons, orb, wordmark, learning, voice (unchanged from 1.4) | `tokens.json`, `theme/`, `icons/`, `orb/`, `wordmark/`, `learning/`, `voice/` |

## 2. Build order
1. **Theme + tokens.** 24 themes (`theme/halo_themes.json`) and text scale. Verify on the G11 matrix.
2. **Shared components** (`product/B/source/halo-app.css` is the visual reference): nav pill/rail, composer, sheets, dialogs, chips, cards, fields, toasts, the status/freshness chips, the Lab style.
3. **The light layer.** `HaloLightFx.kt` aura and screen edge, the live task pill, and `lightFor(phase)` wired to the real runtime phase. Then the Stop-safe `HaloFxQueue`.
4. **Settings restructure** (ST01/ST02/ST04). Move every technical row behind Advanced, add the developer switch, and fix the plain-words strings.
5. **Home, tasks, approvals, Activity, notifications** (B) and the **native assistant overlay** (AS).
6. **Notebook, editor + drafts, Canvas, Memory, Study** (C).
7. **Kids + voice & reading** (D).
8. **How halo answers, voice settings, privacy, setup** (E).
9. **Devices, glasses, BuddyBot, routines, apps, skills** (F).
10. **halo Screen** (SCREEN). Start with the alpha slice in `PACKAGE_SCREEN.md` §7. The Chat | Screen entry needs product sign-off first.
11. **Appearance, language, app-wide states, storage & backup, Advanced pages** (G).

## 3. Decisions waiting for sign-off
- **halo Screen entry:** a Chat | Screen switch at the top of Home (no sixth tab).
- **Lock-screen assistant frame (AS06):** a proposal only, which needs keyguard handling to be confirmed.
- **Weather and interest-article providers:** none exist in source yet, so they ship as honest "Set up" cards.
- **Polish (`values-pl`):** missing in source, so it's marked "coming soon" in the language picker.

## 4. Acceptance per screen
Dark + light match at 360 and 412dp · nothing clipped at 200% text · every interaction row fires its event · Back goes where the board says · motion only on the listed real event, with the still SVG under reduced motion · no hard-coded strings · no banned technical words · a device screenshot attached to the route row.
