# halo — Product map (Package A) · Brand Book v3.0 / DevKit v2.0 track
Revised 2026-10-19 · pinned to repo `3f217ccb4378` (HALO-SEARCH-145). Static source read — not runtime-verified.

## Read this first
- **Latest baseline:** Brand Book 2.3 (91 pp) + DevKit 1.4. The 60-page PDF used in the audit is v2.1 and is superseded.
- **Target:** Brand Book 3.0 + DevKit 2.0, published once when packages B–G are in. Until then, this pack + 2.3/1.4 are the source of truth.
- **Round 1 pages** (v2.1 pp48–60) are history. Never build from them.
- **Design status ≠ implementation status.** Every screen carries both. Source-present code is `I?` until QA confirms it on a device.

## Files
| File | What it is |
|---|---|
| `spec/halo_product_matrix.json` | Machine-readable: 56 routes, 75 design IDs, inline surfaces, nav rules, copy fixes, languages |
| `spec/halo_routes.csv` | Route sign-off sheet (one row per composable in AppShell.kt) |
| `spec/halo_designs.csv` | Design ID sheet: package, priority, audit status, host file, required states |
| `spec/spec_v3.py` | The editable source both are generated from |
| `boards/A01…A07.png` | Book chapter 14 pages: sitemap, Settings taxonomy, navigation contract, routes ×2, coverage, versions |

## Navigation contract
- **Pill (bottom):** Shown when width <600dp AND not mini_halo AND not Kids AND not editing (notebook/note/*, notebook/canvas/*) AND not guided_setup.
- **Rail (side):** Same conditions at width ≥600dp — the five destinations move to a NavigationRail.
- **Five destinations:** Home · Activity · Notebook · Devices · Settings. Study is NOT a tab — it opens from Notebook / Home capture.
- **Kids mode:** Turning Kids on returns to home and swaps it for Kids home. Child-safe routes: home, mini_halo, study, notebook/note/*, notebook/canvas/*, notebook/study/*. Every other route asks for the grown-up credential first.
- **Credential unavailable:** If the phone has no screen lock, the gate cannot verify → show 'Ask a grown-up to set a screen lock' and stay put. Never fall back to a guessable PIN.
- **Back:** Tabs: Back from a non-Home tab → Home; from Home → leave app. Nested: Back → parent in the table. Inline pages (Appearance, Language, Halo nearby, Kids) close to Settings. Editors: Back saves draft first, then returns.
- **Global layers:** Approval sheet and read-aloud bar live above navigation — they follow the user across tabs. Only one audio owner at a time.
- **Deep links:** No external deep links are declared in source today. Internal entries only (notifications, widgets, Mini halo). Any future deep link must pass the Kids gate.
- **Guided setup:** Start destination on first launch only. 'Save and leave' is durable; a 'Return to guided setup' button stays at the top until it is finished.
- **Developer tools:** Off by default. Off: Advanced shows Setup tools + System check only. On: Runtime & diagnostics + Model labs appear. Labs never appear in Everyday or search unless on.

## ID namespace
Audit voice-settings IDs V01–V06 → **VO01–VO06** (they clashed with DevKit 1.4 V01–V08, which keep their meaning).
Learning IDs (K00, K10–K17, H20–H23, C30–C34, S40–S47, G90) stay and live inside K01–K05 / S01–S05.

## Copy fixes in source (lowercase halo)
- `Choose what Halo can use` → `Choose what halo can use`
- `Review what Halo remembers` → `Review what halo remembers`
- `Use Halo from your phone` → `Use halo from your phone`
- `Actions Halo can use` → `Actions halo can use`
- `Halo nearby` → `halo nearby`
- `Mini halo, floating orb and home-screen widget` → `Mini halo, floating orb and home-screen widget ✓`

## Languages in source
values (en), values-lv, values-ru. **No values-pl.**

## Packages
- **A · Navigation + design system:** NAV01, NAV02, NAV03, NAV04, P05, P06, ST01, ST02, ST03, XS01
- **B · Home, tasks, notifications:** H01, H02, H03, H04, H05, H06, H07, A01, A02, N01, N02
- **C · Notebook, Canvas, Memory, Study:** NB01, NB02, NB03, NB04, NB05, NB06, NB07, NB08, S01, S02, S03, S04, S05
- **D · Kids + voice accessibility:** K01, K02, K03, K04, K05
- **E · Intelligence, local models, voice:** O01, O02, I01, I02, I03, I04, I05, I06, I07, I08, VO01, VO02, VO03, VO04, VO05, P01, P02, P03
- **F · Devices + automation:** N03, D01, D02, D03, D04, D05, D06, D07, D08, C01, C02, C03, C04, C05
- **G · Advanced + QA:** VO06, P04, X01, X02

## Sign-off per route
A route is signed off when: design ID frames exist (dark + light, 360 + 412dp, 1× + 200%), all required states are drawn, Back goes to the listed parent, Kids/developer visibility matches, and an acceptance journey screenshot from a real device is attached.
