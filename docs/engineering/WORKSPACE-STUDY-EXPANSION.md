# Canvas, Notebook, and Study expansion

Implemented in source on 2026-10-01, using the shared Halo UI surfaces and existing workspace, note, provider, and speech dependencies.

## Canvas

- Add text cards and links to active workspace items; open linked sources through the normal routes.
- Duplicate cards, create labeled directed connections, remove connections, rename the board, and fit or reset the viewport.
- Persist cards, connections, and viewport in the existing canvas tables. Content edits update workspace timestamps; deleting a card removes its edges transactionally.
- Debounce typing and dragging, then flush pending writes before Back, source navigation, duplication, or export. Failed saves retain a visible retry message.
- Export a board into a regular editable note, or export and start a Study session from that note. Export includes available local source text (up to 6,000 characters per linked item) and connection descriptions. Files use the existing context extraction behavior.
- Selected canvas context includes text cards and link labels. Linked source bodies require their own workspace context selection; links do not override sharing choices.

## Notebook

- Sort by recent update, creation date, or title.
- Select up to 100 visible items and batch add to Halo context, assign a collection, archive, trash with confirmation, or restore. Failed changes remain selected.
- Start Study directly from an active note. Existing context item and token budgets still apply.

## Study

- A session-specific due queue avoids starvation from the global review query limit.
- Focused review presents one due card, answer reveal, explicit self-assessment, temporary skip, and source navigation. Ratings persist through the existing scheduler.
- Save tutor responses, attempts, accepted cards, and source references into an editable notebook note.
- Refresh source freshness on resume and stop narration on pause or source navigation.
- Remote tutoring reads the current authoritative runtime workspace-sharing policy. Existing provider routing and speech selection remain in use.

## Validation

Source and navigation review plus `git diff --check` completed. No database entity changes or new migration are required by this pass. Build, tests, emulator, device, and frame measurements were not run under the source-only restriction. Compilation and runtime interaction remain unverified.
