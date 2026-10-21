# BrandBook implementation and device evidence — 2026-10-20

## Source

Pulled main with fast-forward to `b9c1408cc76ade76429d42c6715f457bc3e638af`. The attached PDF matches the canonical v4 PDF (SHA-256 `d278643c3b28d82f9251a1f054c4f23c2187ef512f5846fcc35d11c8c84bcd7c`). Canonical implementation contract and exported designs remain the visual authority. Changes below are local, uncommitted and unpushed.

## Implemented

- Shared 56dp headers, 21sp titles, navigation dimensions/colors, Settings grouping and typography, Lilac Advanced styling, wrapping controls and enlarged-text handling.
- Home header integrates the surface switch; translated composer controls measure available space. Task progress uses localized journal evidence and belongs to the active conversation. Expired capture notices can be dismissed through the existing discard owner.
- Notebook editing uses quieter block presentation, localized controls and explicit block insertion. Search text binds directly to its input state, preventing dropped characters during debounced searches.
- Study learning-path labels wrap naturally. Kids surfaces and small body typography follow the warm palette and size floor.
- Activity preserves dispatched/unknown action truth across subsequent answers and durable history reloads. Only the same action's confirmed receipt can resolve uncertainty; dispatch alone never becomes completion.
- Repaired malformed adaptive-icon background and corrupted legacy launcher images using the canonical Open Ring SVG. Added a reproducible renderer and icon inflation regression check.
- Regenerated EN/RU/LV resources from 3341 TSV keys. Repaired missing imports and stale test expectations present in the pulled baseline.

## Latest build and checks

APK: `releases/checkpoints/halo-brandbook3-receipts-2026-10-20-debug.apk`.

SHA-256: `f613089cd431444c21381e01a13ace52aba98814d02a5497ef8acaedb8e02989`.

Final assembly and 26 selected checks passed: Activity mapper 11, Home progress 5, Notebook search 3, Study guide 6, launcher icon 1; zero failures/errors/skips. Log: `C:/Users/slavd/HaloEco/brand-review/receipt-parent-final.log`. Localization consistency and diff whitespace checks pass. Earlier Settings/Advanced host renders are retained under `docs/testing/artifacts/HALO-BRANDBOOK3-2026-10-20/`; these are layout evidence, not device or full pixel-diff certification.

Installed successfully over the original configured Android package. Provider credentials and data retained. The temporary capture installation was removed. The Open Ring icon and durable unconfirmed Calculator receipt were physically checked. Russian interface and Dark theme were restored at the end of the connected session. A later final read-only check found the phone disconnected; no additional device-state claim is made.

## Website assets and remaining acceptance

Ten reviewed full-screen 1080 × 2400 English PNGs are saved in `C:/Users/slavd/HaloWeb/assets/screens/captures-2026-10-20/`. Its manifest records per-image state, APK hash, PNG hash, build and date. README and CSV index describe the same ten files. Private raw captures were not copied into either repository.

Captured: Home dark/light (existing paused task preserved), Notebook library and sample note, Study topic and imported material, model catalogue and role assignment, dispatched/unverified Activity receipt, and supplemental Notebook-save approval.

Still missing: SR-03 recognized speech while listening; SR-04 exact phone-action approval; SR-06 genuinely confirmed external action receipt; SR-13 AI Canvas draft with Keep/Discard. Notebook-save approval does not substitute for SR-04. Real Study and Canvas AI attempts failed under current access/provider configuration; successful chat generation does not prove those flows. No credentials or access policies were changed to force a screenshot. Later P1/P2/hardware-dependent captures remain open.

Full BrandBook pixel-perfect parity is not complete or certified. Website assets are delivered as files; no HTML integration is claimed.

## Code-only continuation: shared surfaces

Repository fetch confirmed the same base SHA; earlier local work was preserved. Matched additional shared components to `product/B/source/halo-app.css`:

- Removed inherited card padding and default elevation from glass chips; use 34dp minimum height, 12dp horizontal inset and 13sp semibold text. Status chips and tags use pill geometry and the same size floor.
- Standard cards use 14dp vertical / 16dp horizontal padding. Section heading/action rows reflow without overlapping at 200% text and expose heading semantics.
- Standard and glass dialogs use 20dp side margins/insets, 19sp semibold titles with 24sp line height, and scrolling so long content and final actions remain reachable.
- Glass-sheet primitive uses 26dp top corners, 18dp side / 10dp top / 20dp bottom padding, and a 36 × 4dp themed handle. This primitive is not a claim that every Material sheet has been migrated.

Debug assembly passed. Eight focused checks passed (four shared-layout/accessibility checks and four Canvas draft-control checks). Native font rendering is required for metric assertions; Canvas host tests use the existing test-only thermal shadow and hostless theme. No production motion or provider behavior was changed to satisfy the host environment.

New checkpoint: `releases/checkpoints/halo-brandbook3-shared-surfaces-2026-10-20-debug.apk`.
APK SHA-256: `fd93bebc8caecdeb1b277e5eadfd64072e17483e3c78c6ad1a7d5d7670720fee`.

This checkpoint has not been installed or tested on a phone. The earlier screenshot manifest still correctly points to its earlier APK hashes. Device visual comparison remains pending; full pixel parity is not certified. Changes remain local and unpushed.

## Latest source checkpoint: Study and Kids

See [focused implementation and validation report](ANDROID-STUDY-KIDS-2026-10-20.md). The new Study/Kids checkpoint passed assembly and 41 selected checks. It includes the variable-font weight-axis repair and child primary-action styling. It has not been installed on a phone; prior installed APK and website capture hashes remain unchanged.
