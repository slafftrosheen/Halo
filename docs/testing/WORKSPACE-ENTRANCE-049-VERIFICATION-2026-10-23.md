# Workspace entrance — issue #49

Workspace is now the public destination for Halo's saved documents and local tools.
The existing `notebook` route, Kotlin/Room owners, object IDs and incoming handoffs
remain compatible. The shell, screen catalogue, tool catalog/Orb, skill display name,
privacy, backup, import/export and recovery text use the same Workspace wording.
Existing resource aliases remain for callers; EN/LV/RU copy is updated together.
Polish support remains the existing honest fallback.

The native entrance opens Notes, Files, Canvas, Collections and Inbox through the
existing filters and collection controls, and Memory through its existing dedicated
owner. Create, Import, Open, Continue and actual-source search keep their current
implementations. Quick capture opens the existing capture dialog; Inbox opens saved
inbox items. Working-set/source owners and saved objects are unchanged. Import does
not imply memory promotion, and the hub says so explicitly.

Studio is visible as an unavailable graphics workshop in this build, with no click
action and no fabricated result. Its independent editor is owned by issue #51; the
hub does not relabel the existing Canvas editor as Studio. This issue adds presentation
and navigation over existing functionality, not a new editor, assistant executor,
approval owner or tool capability. Broader assistant parity remains in #54.

## Verification

All implementation and regression sources were authored before the first serial
build/test gate. Read-only review found no actionable P1/P2 issues. The source
regressions exercise all six actual button callbacks, explicit Memory scope, Studio
without a click action, every destination label without visual overflow, actual
font scale, existing Notebook detail/Study route compatibility, and a disabled legacy
skill preference restored with the same tool ownership under the new public name.

The first five hub cases failed an over-broad `hasVisualOverflow` assertion.
A native capture and measured diagnostic showed Notes occupying 40px × 19px while
the paragraph retained its 108px offered allocation. The text fit. The corrected
assertion checks each visible line width, vertical bounds and ellipsis instead;
actual font scale and scrolling/callback checks remain. No production layout change
was needed for that fixture correction. The final combined gate was rerun.

The final serial gate passed in **3m 15s** (unchanged production APK tasks cached
from the successful compile/assembly in the earlier gate):

- **55 app tests / 12 classes**, zero failures/errors/skips.
- **4 runtime skill tests / 1 class**, zero failures/errors/skips.
- **59 total tests**, with both debug APK assemblies successful.
- **5 native hub renders**, visually inspected.

Five native hub renders cover light/dark, English/Latvian/Russian, actual 200% text
and a short landscape viewport. The scroll container keeps lower controls and the
unavailable-state text reachable. Existing repository, migration and local search
suites qualify the reused data owners; no schema or data migration is introduced by
this change. Source was frozen for the final gate. The manifest records compiled
app/runtime Git trees, selected fresh XML, build log and assembled APK identity.
No GitHub Actions ran. APK binaries and personal phone captures are not committed.

Final APK: **287,170,465 bytes**, SHA256
`764d48140419a53c9d7093722d67e1fff9c4fb8cede9a418e1d8938ce9500e19`.

**Final device checks deferred by the user:** “Continue; phone later.” No final-APK
installation, read-back or physical navigation/accessibility PASS is claimed for
this issue. The pending upgrade/link, Workspace/Library, capture/Inbox, Memory and
200% landscape phone checks are retained explicitly in #43.

Evidence: [manifest](artifacts/WORKSPACE-ENTRANCE-049-2026-10-23/manifest.json),
[test XML](artifacts/WORKSPACE-ENTRANCE-049-2026-10-23/tests),
[native renders](artifacts/WORKSPACE-ENTRANCE-049-2026-10-23/renders), and
[device status](artifacts/WORKSPACE-ENTRANCE-049-2026-10-23/device/device-observations.json).

## Remaining qualification

Physical TalkBack/keyboard operation, final-APK phone navigation/upgrade journeys,
tablet/API coverage and real spoken assistant workflows are not established by native
callbacks or a local build. Unobserved cases stay explicit in #43; Studio functionality
belongs to #51 and broader cross-tool assistant execution to #54.
