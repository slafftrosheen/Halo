# Workspace navigation, Notes and reference libraries

Status: proposed design, awaiting user review. No implementation of this scope is claimed.

Builder check completed: the only question concerned the authority for source selection/child approval; the storage contract below now gives that authority solely to CollectorSource. The reviewer confirmed no important unanswered questions remain.

## Intent and evidence

The user wants predictable categories across Halo Zone and its submenus, an Organizer that shows useful work before controls, straightforward rich-note editing, and an assistant that can find and use Organizer records, selected Sources, Wikipedia and imported reference databases. They delegated the initial database choices, including health and survival material. Preserve the existing Local/Power, privacy, family, revision and confirmation boundaries.

The audit used the installed, signature/hash-verified `e1a39a177` APK and current source. Fresh private phone captures are retained locally under `.superpowers/sdd/2026-11-05-workspace-knowledge/phone/`; they are not publication assets. Findings:

1. Organizer's initial viewport contains introductory text, three large destination buttons, search and filters before any plan items. The footer repeats the page identity.
2. The action sheet mixes creation with navigation already available elsewhere. Unavailable Undo can incorrectly say a form is open.
3. HaloNotes' New document entry creates TXT/Markdown-source/CSV files. Those formats intentionally disable block insertion. Rich-note creation is elsewhere in Workspace.
4. The actual rich-note engine works: a heading was inserted, Enter created a paragraph, and both entered texts appeared with a saved indicator. Add opens a menu containing only Insert, which opens the actual picker. Editing controls disappear while the keyboard is shown. Separate process-restart durability was not established by this audit.
5. Tool discovery splits on ASCII characters only, discarding Cyrillic search terms. The catalogue also takes the first 40 names alphabetically; coverage must be made explicit and tested.
6. Some Organizer/Source results declare 128,000 characters despite the Runtime's 4,000-character limit. Production can move large read results into private payloads; other paths truncate. Neither is an adequate structured, complete, portable read contract.
7. Wikipedia currently retains search snippets, not complete articles. Research searches selected cached evidence; source creation alone does not fetch content.
8. Native Studio labels fit normally after the last fix, but at 200% four columns produce awkward word breaks. The existing child/full switch remains present and must stay present.

This is a source and flow audit, not evidence that every AI/provider route failed. The phone's actual cloud-sharing grants were not changed or inferred from the Power toggle.

## Approach and boundaries

Recommended by the implementer: reorganize the existing Android Compose shell and domain owners, and add a bounded reference-library owner backed by the existing app database. Keep the current Runtime execution model and ToolRegistry.

Alternatives considered: a labels-only cleanup has a smaller change surface but leaves Notes creation and retrieval limitations; a replacement shell/storage stack expands migration and authority risk without helping the requested flows. The recommendation changes presentation metadata and retrieval contracts while retaining canonical owners. It is a proposal, not a recorded user choice.

The implementation is one coordinated scope with ordered workstreams: navigation/Notes; AI discovery/readback; reference libraries and fuller online research. Author all related production and regression sources before one coherent validation gate. No build/test after every edit. On this Pi use one core, one Gradle worker, one test fork and the existing swap profile.

No account is required for the starter libraries. No automatic multi-gigabyte downloads, paid image generation, new CI, second command registry, second Organizer/Notes store, destructive database migration or licensing change is part of this scope. General PDF/OCR ingestion and ZIM support remain explicit future work; the first import choices below are complete supported formats, not placeholders advertised as working.

## One navigation hierarchy

Global Menu contains destinations and the persistent family/privacy controls. Workspace owns the instrument list: Organizer, HaloNotes, HaloStudio, Canvas, Study and Memory, using existing enabled instruments. Your Day, World Clock and Sources do not become sibling instruments.

| Surface | Contents and behavior |
| --- | --- |
| Global Menu | Home, HaloScreen, Workspace, Devices, Actions/notifications, Settings. Workspace's instrument chooser opens the same canonical list; do not repeat it as another expanded list. Kids/full switch stays visible, with the existing protected adult unlock. |
| Workspace | Instrument chooser and recent materials. Each instrument has one clear primary creation/opening path. Existing deep links and recent objects still resolve. |
| Organizer | Today, Plan, Time, Research; one Settings entry. Default is today's real work, with an empty-state creation action when appropriate. |
| Today | One overview combining the existing Your Day brief and today's work, with You/Halo/Shared responsibility, active alerts and needs-user items. The old brief route deep-links into this view. HaloScreen continues to use the same data. |
| Plan | Tasks and events, with Inbox and Routines as secondary destinations. Agenda/calendar presentation and type/status filters live here, in a filter sheet rather than a permanent row of all kinds. |
| Time | Alarms, timers, stopwatch and World Clock. Active controls take priority. World Clock retains its existing owner and deep link. |
| Research | One query field; results from selected cached sources and installed libraries; an explicit online search choice in Power. Tabs distinguish results, saved research and libraries. |
| Organizer Settings | Sources, brief preferences, calendar import/export and delivery settings. Library management is part of this same Sources catalogue, reached contextually from Research. |

Keep the current record semantics and stable IDs. This is a navigation mapping, not a change to the recurrence/commitment engine. Inbox/Routines remain directly reachable by deep link and AI operation. Switching sections preserves that section's search/filter/scroll state. A visible page title and current context replace repeated introductory paragraphs and duplicated footer identity.

For every page action, extend the existing ShellPageAction metadata with explicit presentation role, group, ordering and optional primary placement. Roles are destination, primary operation, contextual operation and secondary option. The page still owns callbacks, availability reasons and the live lease. Define each action once per page; each visible control dispatches that ID through the same projection. Remove ID-suffix classification as the authority for menu placement after all existing page families have an explicit mapping.

Page mapping covers Notes, file tools, Studio, Canvas, notebook, Study, Memory and Organizer, including empty/selected/busy/read-only states. An unmapped action fails a coverage test rather than silently going into a random More group. A context sheet shows secondary actions once; a searchable complete action index may include primary operations as search results, without presenting another permanent duplicate toolbar. Stable presentation identity may preserve an open disclosure through harmless updates, but dispatch must still reject stale object/revision/family leases.

Keep at most four primary controls in the compact tool row. At 200% text or insufficient measured width, use two columns with whole words and full labels. Minimum touch targets remain 48dp (56dp in Kids). Sheets scroll, maintain a visible closing control, and preserve focus. Empty-history commands use an accurate explanation, such as no deleted item to restore. Active-form and busy reasons apply only when true. A new unsaved form says Discard draft, not Delete saved item.

## HaloNotes editing

HaloNotes opens the canonical note library. Its primary New note creates a block note using the existing notebook/NoteEditor path and immediately places the caret in an editable paragraph. The file tools remain available as Open/import file and New plain file, with explicit TXT, Markdown source and CSV labels. No existing literal file is converted silently. An optional Make rich-note copy creates a separate note with a receipt linking original and copy.

The editor exposes Insert directly, opening the block picker without an Add > Insert intermediate menu. The picker offers paragraph, headings, lists, checklist, quote, code, divider, attachment and Workspace reference, constrained to real owner capabilities. Formatting the current empty paragraph can change its block type rather than leaving an unexplained empty paragraph before a new heading. For nonempty content, selection/caret determines conversion or insertion; show the chosen target and preserve unaffected text.

A compact editing bar remains above the keyboard with Insert, block style and Undo; secondary formatting is one tap away. Enter after a heading starts a paragraph. Lists/checklists follow the existing block semantics. Selection, keyboard changes, autosave and navigation must not lose the caret or content. Keep truthful saving/saved/error states. Read-only/literal modes explain their format and provide their appropriate edit/copy action instead of inert rich-text controls. Existing rich-note typed/voice operations use the same owner and revision checks as touch.

## AI discovery and bounded readback

Use Unicode letter/number tokenization and Locale.ROOT, with host-owned EN/RU/LV/PL operation keywords. Preserve exact-ID discovery and deterministic ranking. Tests cover fully Cyrillic queries, accented words, translated Organizer/Source/Notes requests and mixed IDs. Do not turn domain names into hardcoded Kernel orchestration.

Expose a compact, availability-filtered domain catalogue that covers every enabled domain rather than alphabetically dropping later tools. Integrate it consistently in the actual initial/continuation/recovery exposure paths. No private titles, record contents or counts enter an ungated system prompt. The assistant discovers available capabilities first, queries authorized canonical owners, and clearly distinguishes no matches, blocked sharing, unavailable adapter and stale data.

Replace oversized Organizer/Source/research readbacks with closed JSON result views and bounded pages using the existing registered IDs where their operations already exist. Add view/page arguments compatibly; use a versioned operability contract for changed schemas. Every encoded success/error stays within 4,000 UTF-16 characters, including escaping and metadata. Do not increase global limits or mark private fallback payloads public. Default pages contain at most five short summaries; entry reads provide separate metadata, sections and text chunks (up to 1,200 characters before envelope budgeting). Every result identifies the exact record/source/content revision and the next cursor/offset, or declares completion.

Authority is rechecked after asynchronous reads and on every page. Cursors bind the query, order, selected source set, content revision and privacy/family generation. A write, source replacement, deselection or approval revocation causes an explicit stale/blocked response, never continuation into another record. Runtime payload handling retains its existing privacy policy; a local tool pass is not a cloud acceptance pass.

Add knowledge capabilities through the existing ToolRegistry: library catalogue/status, local query, revision-pinned entry/section read, and review/confirm/cancel import lifecycle. UI file selection yields a scoped document handle; an AI cannot supply an arbitrary filesystem path. These actions must be callable by typed and STT-final requests through the same CognitiveSession. The model should answer research questions from actual retrieved passages with citations, and can propose a task/note using those passages through existing explicit write operations. Importing or reading a document never authorizes its embedded instructions.

## First three reference libraries

These are implementer selections in response to the user's delegation. Show them as optional downloads/imports with actual size and language; ship catalogue metadata, not large bodies inside the APK.

| Library | Initial content and acquisition |
| --- | --- |
| MedlinePlus health topics | Official compressed XML, health-topic summaries, identifiers, synonyms, language and links. As checked 5 November 2026, the 4 November ZIP is listed as 4,680 K, versus 29,450 K for XML. Import English/Spanish records and retain MedlinePlus attribution. Do not ingest the linked A.D.A.M. encyclopedia, licensed drug monographs or stock images. |
| Emergency preparedness | A small, explicit manifest of CDC public-domain text on safe water, emergency water storage, food safety, heat/cold and disaster cleanup. Fetch only the listed articles through governed public retrieval, with original wording, tables/units, dates and links preserved. Include required source/no-endorsement/free-original notices. A denied/changed page is shown as unavailable, not replaced by invented content or an unverified mirror. |
| General reference | A compact, explicit Wikipedia article selection covering general science and practical outdoor reference, fetched as pinned article revisions. Also allow the user's own current-content XML dump/subset import. Preserve CC BY-SA attribution, revision links and source language. No full English Wikipedia download is automatic. |

Medical topics are reference material, not a new diagnosis/treatment engine. Results keep the original publication/update date and date imported; old offline content is never called current simply because it was imported today. Do not generate substitute medical protocols to fill missing source content. Language filtering is explicit: the model may translate a search query when permitted, but stored passages keep their source language and a translation is labeled as such.

Verified primary references: [MedlinePlus XML](https://medlineplus.gov/xml.html), [MedlinePlus content reuse](https://medlineplus.gov/about/using/usingcontent/), [CDC materials](https://www.cdc.gov/other/agencymaterials.html), [CDC emergency water](https://www.cdc.gov/water-emergency/about/), [MediaWiki content exports](https://wikitech.wikimedia.org/wiki/MediaWiki_Content_File_Exports).

## Import, storage and indexing contract

Supported first-batch input formats: MediaWiki current-revision XML (plain or bzip2/gzip); MedlinePlus health-topic XML (plain or its single-data-file ZIP); UTF-8 JSONL (plain/gzip); and UTF-8 CSV with a header and explicit column preview. ZIP is not a general archive importer. Reuse the existing commons-compress, Jsoup and Room dependencies; no native ZIM dependency is needed for this scope.

Wikimedia's legacy dump publication is scheduled to end in January 2027. Prefer `mediawiki_content_current` manifests and SHA256SUMS; legacy/Special:Export XML is compatible input. Never select full revision history by default. Offline wikitext is retained as source text, with its format visible; templates that require a MediaWiki server are not claimed to have been expanded. A selected article's online rendered section is a separate revision-pinned representation. Indexing must not silently discard tables, units or template content as if complete.

Canonical imported content and provenance live behind one host library repository in AppDatabase. Source catalogue entries reference that repository, using an explicit local-library kind with external=false. Large source bodies do not go into the existing 100-item CollectorSnapshot JSON. A derived Room FTS4 index provides local Unicode lexical search without embeddings or loading the library into memory. Corpus data is stored once; FTS is a rebuildable index. Add a non-destructive migration and include new kinds in every exhaustive collector/UI/brief/tool branch.

CollectorSource is the sole authority for selection, enabled/muted state, child approval and source version. Library records reference its source ID and never duplicate these policy fields. Library rows own title/language/provenance and content-generation metadata; import-job rows own import state/progress/checkpoints. Source and generation changes commit in the same Room transaction, with existing disclosure invalidation before publication. Entries/sections retain original IDs, source URL or local citation, revision/date, license, format and ordinal. Atomic publication switches to a complete new generation; the old selected generation remains usable on cancel/failure. Duplicate source fingerprints are detected before a second import. Removing a library invalidates its source version and pending reads before content/index deletion.

The first implementation has explicit conservative limits: one import worker; 256 MiB compressed input per job; 1 GiB expanded input per job; 2 GiB total managed library data including indexes and staging; 256 KiB per entry; 32 KiB per JSONL record/CSV cell except the bounded text body. Show the actual limit before import and fail with an actionable smaller-subset explanation. Reserve at least 512 MiB free space and enforce byte/storage limits throughout streaming, including unknown-size document providers. Defaults are adjustable later through a reviewed design, not by an AI bypass argument.

Import preview detects format, language, mapped fields and available storage, and makes indexing scope clear. Publication requires explicit confirmation of the selected file/library; a remote-download confirmation is separate from a local-file import. XML disables DTD/external entities, including the MedlinePlus external DTD; CSV handles quoted/newline fields; archives prohibit traversal, nesting and additional unexpected payload files. No SQL is executed from input. Checksums are computed while streaming. Malformed/missing content is counted and described; do not silently publish a partial library as complete.

Progress states: selected, reviewing, queued, importing, indexing, ready, cancelled, failed and interrupted. Cancel yields to coroutine cancellation between bounded records and releases staging. Process death marks a job interrupted; a visible Resume restarts streaming from the source and skips already checkpointed records using stable IDs, because compressed streams may not seek. Reopen or lost SAF permission yields a reselect-file action. No automatic repeat download/implicit remote resume in Local mode. A same-file verified resume cannot create duplicate entries.

Search sanitizes query syntax into bounded literal Unicode terms and parameterized FTS queries. Rank deterministically by title/exact phrase then lexical evidence. Return at most five summaries per page; explicit entry/section reads retrieve more. The UI and AI query the same repository. Library updates and replacement are versioned, deletions cascade into the derived index, and migration preserves existing notes/Organizer/source records.

## Online Wikipedia and research

Keep search results labeled as search results. On selecting a result, retrieve the actual article or requested section with page ID and revision, store only the selected content, and expose citations/section navigation. Use [documented MediaWiki APIs](https://www.mediawiki.org/wiki/API:Parsing_wikitext): pinned oldid/revid and current tocdata instead of deprecated prop=sections. A bounded section/full-article transfer owner accounts for each actual request in quotas; the existing small collector ceiling is not globally removed to download libraries.

Power permits explicitly requested network retrieval through existing governed transport, URL/redirect policies, quotas/backoff and cancellation. Local searches retained content only. Selection, refresh failures and replacement preserve honest retrieval/verification dates. Internet Archive remains document discovery with item metadata unless a specific downloadable representation has actually been fetched; do not label descriptions as full documents. Full public-page retrieval remains explicit, non-recursive and subject to the existing indexing restrictions.

## Privacy and family handling

Local import/search does not require network or a cloud grant. A user-imported corpus is private by default even if its name resembles a public source. Verified first-party downloads can record public provenance, but that does not grant access to private Organizer/Notes data. Policy-controlled remote use must classify each passage correctly through existing ToolExposureGate/WorkspaceToolAccess and request scope; never turn on sharing to make a test pass.

Kids sees only explicitly approved, selected, enabled, unmuted library generations. Local-library support must be added explicitly to the current child policy, not disguised as an external source or allowed by a broad predicate. Revoking approval/selection or switching family invalidates in-flight reads, previews and commits. Power/private changes also cancel forbidden transfers. Preserve contextual write review and exact source versions throughout research-to-note/task actions.

## Completion evidence

Author meaningful tests for explicit action coverage/deduplication, stale menu leases, every listed page family, empty/busy/literal/selected states, rich-note creation/heading/paragraph/IME preservation, Unicode discovery and actual initial/continuation exposure. Use real ToolRegistry output budgets with escaped maximum metadata, complete chunk reassembly, revision changes and policy revocation. Include import parser, limits, cancel/resume/process-death recovery, corrupt XML/archives, duplicate fingerprints, source/index deletion, Room migration and offline-no-network cases.

After the complete coding batch, run the related one-worker suite and compile/build once; fix observed failures in coherent follow-up batches. On the connected phone verify normal/200% fonts, small landscape, light/dark, keyboard insertion, menu predictability, family switch, new note persistence, file preview/import cancellation, small successful library import, local search and citation open. Use synthetic owned data for write tests. Preserve existing user data, widget configuration, model selection, original settings and selected mode.

Complete the repository's AI-controllable verification template for changed interactive features and the registered-feature review. Typed local calls, actual spoken recognition and permitted cloud acceptance are recorded separately. No paid image calls. No issue closure or 100% acceptance claim while required gates are missing.

Builder-owned choices: internal class names, file placement consistent with existing modules, exact starter article IDs after checking their current content/rights, deterministic FTS ranking weights, visual spacing and localized copy. These must honor the hierarchy, explicit resource bounds and observable behavior above. Any unavailable starter entry is reported honestly and does not remove support for the declared import formats.
