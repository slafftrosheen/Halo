# Workspace continuity #53

Base main de4b7349b6469de87327c209b415000622a78377, fetched 0/0 clean. #52 code is published; its native phone acceptance remains open after ADB disconnected. Continue coding independently as authorized; no #53 build until all production/regression sources are complete.

Extend existing WorkspaceObject, file, Notebook, Studio, Canvas, Study and Working Set owners. No universal database, duplicate Memory store, provider/backend or Actions. Current DevKit2/BrandBook3/user-facing contract applies; stable identifiers and revisions stay out of ordinary UI.

- [x] One canonical file version calculation for repository, Canvas annotations/selection and Working Set; unchanged owned SHA identities, relink-sensitive external identities.
- [x] Media resolution checks active object lifecycle, current source/attachment identity and readable availability before and after bytes; stale/trashed sources cannot silently enter Chat/Study. Respect existing remote-data consent and media boundaries.
- [x] Explicit exact-source Studio imports and restart-safe source handoffs; source bytes remain intact and layer provenance survives edits/export/reopen.
- [x] Shared bounded continuity description/lineage and typed exact-version Open with/Send to/Insert/Working Set actions through existing owners. Dispatch receipts distinguish presentation from effect; no auto replay or hidden content extraction.
- [x] Consistent manual handoffs from relevant Workspace/Studio/File tools screens to Canvas/Study/Chat, and stable-version source/result links. Localized useful states for missing/revoked/trashed/stale sources. Existing current-item grounding reused, no implicit broad context.
- [x] Memory remains explicit and reversible through existing review/policy; imports never promote facts. Keep source citations/scope and block forgotten/deleted retrieval.
- [x] Regression sources for photo→Studio→managed output→Canvas→Study/Chat, restart/reopen/search/source links, stale versions/revoked/trash/forgetting/privacy, assistant/manual parity, localized native UI and isolated device workflow. Read-only review/fixes.
- [x] One focused integrated gate and runtime checks passed; exact APK identities recorded. Device unavailable; native acceptance remains explicitly pending.

Initial source audit: WorkspaceContextStore and NotebookCanvas still derive timestamp-only external versions, unlike the hardened file owner. WorkspaceMediaAssetResolver checks MIME/hash but not Workspace lifecycle or availability; durable image references can reopen trashed inputs. Studio import pins without comparing the originally observed source version under the shared lock. These boundaries are first to repair; existing stores remain authoritative.

Source review complete before first gate: corrected derived Canvas-note managed export IDs and nullable source-digest access. Added null-resolver exact-handoff regression. Resource parity and whitespace checks pass; fetched main remains de4b7349, 0/0. The integrated gate and native acceptance below remain pending.
