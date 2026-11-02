# Everyday pages and editing ownership

Implementation boundary: organizer, world clocks, saved briefs, sources and cached readers. This document describes source implementation; the matching host run and independent review are recorded separately. It does not certify Screen cards, launcher widgets, tooling, physical-device behavior or human usability.

## Entry and identity

All five pages are Screen subroutes in the existing AppShell, with one `ShellPageOwner` each. Existing Orb/Zone actions provide navigation and page tools. Organizer routes retain record IDs, brief routes retain document IDs, and readers retain source/item IDs. The routing owner rechecks live records and family/source visibility before following a saved fact. Public calendar links include the producing snapshot revision, original occurrence identity and display zone; an updated or removed snapshot produces an unavailable/changed outcome.

Brief notifications use `EverydayBriefEntry`: only the canonical 64-character document ID is accepted, and each document has a distinct PendingIntent data URI. The resumed-host request owner admits this narrow route in addition to the existing exact screen catalogue. Invalid query strings remain rejected. A pruned document opens a missing state; a notification never silently selects the latest brief.

## Durable editing

Organizer forms retain their original record version, recurrence scope and exception metadata in SavedState. Whole-series changes explicitly review/reset exceptions; title-only changes preserve exact imported UNTIL and existing overrides. Occurrence edits preserve floating time identity, authored elapsed duration, completion and URL overrides. Notes and parent tasks use canonical relationship candidates independently of the page's search.

An adult action captures a monotonic Kids authority revision. `EverydayWriteAuthority` propagates its validator through suspension into organizer, collector, clock and briefing-setting owners. A Kids round trip does not revive the old action. The pages synchronously hide adult content on a family transition. Existing background owners continue to enforce their separate scheduling/network policy.

Delivery history remains version-labelled. Current scheduling and terminal outcomes are distinct; zero-time timers use their receipt state. UI notification/exact/full-screen permission controls invoke the existing Android access owner and report actual delivery precision and status.

## Calendar transfer

SAF import reads at most 2 MiB and previews supported records before saving. The Room import transaction deduplicates stable calendar UIDs across filenames and Trash, never overwrites an existing record, and publishes all records/exceptions/outbox changes together. Unsupported recurrence rules remain explicit preview issues.

Export reviews exact event ID/version selections, including stale selections that must be cleared/reviewed. The canonical Workspace file owner prepares the manifest note and immutable managed calendar file in one transaction, with authority and source-version checks around publication. Failure rolls back both rows and cleans unreferenced owned bytes. The existing Workspace external-export session then owns the SAF copy, readback verification and durable receipt. A provider accepting a stream is not represented as verified external delivery.

## Saved sources and briefs

Opening pages, searching offline cities, editing source settings and reading cached content make no network request. Explicit feed preview and source refresh go through the existing governed collector owners. Deferred attempts retain the coordinator's actual reason/next-eligible time without changing successful retrieval timestamps. Source mutation invalidates a stale manual receipt.

Article reminder forms capture source ID/version, snapshot revision and the complete reviewed article. The shared Room transaction validates them before creating the attributed organizer reminder; a same-ID content change requires a fresh review. Readers use plain text, explicit publisher links, source attribution and freshness; they do not load publisher HTML/images. Weather observations use the selected city's time zone. Calendar readers label cancelled series and show saved recurrence exceptions and their own title/details/URL.

Brief history and retry retain the requested document independently of a successful read. Changes distinguish added, updated, removed, unchanged and unavailable comparison inputs. Parents can revoke every existing pinned/shared organizer ID, including unavailable records and records beyond the candidate search window. Read aloud uses the existing scoped speech owner; entering a page or receiving a proactive notice does not start speech.

## Presentation and remaining acceptance

New app-authored copy is in the generated EN/RU/LV/PL catalogue. Shared sheets preserve actual reading density and veto dismissal until their owner accepts it; dirty forms provide explicit discard decisions. Child-visible actions use 56dp minimums where added by these pages. These are source properties, not a claim that every native/rendered geometry case has passed.

The focused regression source covers recurrence restoration, import deduplication/rollback, cache-clear fencing, family authority, immutable article review, calendar occurrence links, brief PendingIntent identity, and atomic export preparation. Matching host results, restored-form/large-text renders, and later Android scheduling/launcher/speech acceptance must be recorded before broad completion claims.
