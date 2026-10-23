# Halo Studio — issue #51

Standalone Studio projects are local Workspace ARTIFACT objects, separate from Canvas boards. Room schema 28 persists their committed document, current draft, bounded undo/redo session and saved versions. Shared revision-checked commands serve touch and assistant tools. PNG/JPEG outputs are new managed Workspace files with exact project revision and document/source links; Canvas inserts references to those outputs. Originals are retained.

The local workshop supports image/Workspace/camera import, crop/rotate/resize/mirror, text, rectangles/ellipses, portable Halo Ink drawing/erasure/selection, layer order/visibility/locking/opacity, rectangular masks, brightness/contrast/saturation and editable presets. Bounds are 32 layers, 4096 px per side and 12 MP per document, 20 MB imported/exported image bytes; a document also has a 65,536 Ink-point budget. Undo/redo keeps at most 16 entries in each direction within shared character/point budgets. Serial composition holds one output and one source layer; previews use bounded source decodes. These are enforced implementation limits, not measured memory/performance qualification.

Studio opens from shared Home Tools, Workspace, image file details, image attachments and Canvas. Exported files reuse existing Workspace attachment/Study/chat routes. Eleven Studio tools share the same owners and runtime approval policies. Mutations and presentation requests never automatically replay after revision conflicts or uncertain dispatch. UI receipts report presentation/proposal disposition, not provider billing or OS foreground truth.

AI review reuses WorkspaceImageStudio, the existing provider/image owner and one-shot spend review. Preview/Keep/Discard preserve the original project; Keep creates a separate project and managed image. Deferred assistant proposals carry content-free source categories and the original privacy-policy revision through review, spend preparation and generation; they cannot obtain fresh policy authority from a later UI coroutine. Prompt/reference bytes stay ephemeral and are discarded on dismissal. No real provider request is part of the local qualification fixtures.

Archive format 3 includes project/session/version rows, validates the closed Studio schema before applying, and remaps exact image source IDs throughout current/undo/redo/saved versions and recognized source metadata. Old archives without Studio remain supported; older application versions reject format 3 rather than silently lose projects.

## Source review and gate

Base main 64202f92e325d6a7f090762c1c71d5b8c1a92d74, fetched 0/0 before validation. Production source review corrected a preview decode flag and deferred-AI provenance loss. Follow-up read-only review found no further concrete P1/P2. Existing Workspace destination test callback was updated for the new optional Studio entry. Static resources have 93 matching EN/LV/RU keys and 88 resolved Studio references. No upstream code/assets/dependencies copied; official cropper/editor pattern research is linked in the implementation plan.

The integrated gate successfully assembled both APKs and compiled all production and regression sources. It includes both APK assemblies and focused desktop regression classes for documents, commands, persistence, migration, recovery, pixels, exports, Canvas insertion, provenance, backup remapping, receipts, Ink conflicts and native localized layouts. A separate focused runtime check covers tool ownership and result privacy categories. Phone fixtures use only isolated synthetic cache files/in-memory databases and a hostless large-text layer dialog.

## Qualification still separate

Tablet/stylus pressure, physical eraser behavior, touch latency, sustained memory/frame times, TalkBack interaction, camera hardware and real providers/billing require direct evidence. The API35 phone connection alone establishes none of those. Track release qualification under #43 and Ink device work under #28–#30. Advanced brushes and color-managed printing are outside this workshop and are shown as unsupported in the editor.

## Measured checks

The initial combined run took 15 minutes: both APKs assembled, 36 of 44 desktop cases passed, and eight failed in test fixtures. Three used a Robolectric file URI whose path did not resolve; those now use an explicit fixture content URI and registered stream backed by the owned file row. Four new/existing image tests omitted the existing Full Power sharing acknowledgement; fixtures now explicitly acknowledge in their in-memory policy store. One Ink case advanced virtual timeout before the real Room worker returned; it now uses a bounded real coroutine worker, matching its disk-backed asynchronous boundary. No production change followed these fixture failures.

The focused desktop retry passed **44 cases / 12 classes**, zero failures/errors/skips, in **3m 20s**. The three native light/dark EN/LV/RU dialog renders were visually inspected, including 200% text on Latvian portrait and Russian short landscape. Footers stay visible while extended content scrolls. The focused runtime gate passed **5 cases / 2 classes**, zero failures/errors/skips, in **1m 35s**.

Real Android **API35 / 23124RA7EO / f01fe673**: **OK (2 tests)**. Local create/import/edit/export/reopen rendered real PNG pixels, preserved the original bytes and source-linked draft, rejected a stale export and repeatedly released bounded previews. Russian 200% layer review kept Apply and Close reachable. Synthetic cache and in-memory database fixtures were cleaned up; no user Workspace or OS preference changes. This is focused workflow evidence, not full app navigation, camera, interruption/hardware or performance acceptance.

Installed APK: **287,221,991 bytes**, SHA256 `9c9a451575a572f661aaeec96bad448c2121f0914566244527f11d9a55e9a951`, matching installed base.apk read-back. Only regression fixtures changed after assembly; the production app binary was not rebuilt or reinstalled. The manifest pins source trees and final verification totals. Main was re-fetched at 64202f92 with 0/0 divergence before publication.

Production phone smoke: MainActivity launched and remained running. An existing Home Tools sheet initially obscured the requested destination; after dismissing that sheet, the exact Studio screen request showed the Studio title and New project control in the real application. Only known static label booleans were retained; temporary UI dumps were removed. No production project was created for this smoke check.
