# Workspace entrance implementation plan — issue #49

> Execute inline with the existing execution-plans workflow. User sequence overrides test-first execution: finish all source and regression cases before build/test.

**Goal:** Make Workspace the clear user-facing home for existing local documents and tools without migrating data or replacing routes.
**Architecture:** Preserve NotebookScreen/ViewModel and `notebook` navigation as canonical owners. Add a small native presentation component that invokes existing filters, collections and Memory navigation. Studio is an honest unavailable row until #51 implements its separate editor; no fake Canvas shortcut.
**Tech stack:** Existing Kotlin/Compose, native resources, Robolectric/Roborazzi.
**Spec:** GitHub #49, `.agents/AGENTS.md`, `docs/15-brand/v4/IMPLEMENTATION.md` and `USER_FACING.md`.
**Starting main:** `023ef76013b599ee7ca3513014af184a5242e2b5`, fetched 0/0 and clean.

## Chosen approach and boundaries

A native entrance over existing owners matches the requested scope. A global route/database rename would risk compatibility; a second dashboard/store would duplicate current data. Neither is needed. This is presentation and navigation, not a new functional tool; assistant execution owners remain unchanged under #54. Existing create/import/capture/open/search/continue actions retain their implementations and permissions. Import never implies memory promotion. Memory is an explicit destination. No new server, permission, provider, action authority or GitHub Actions.

## Tasks

- [x] Preserve the existing route and object links; update AppShell and screen catalogue display titles to Workspace.
- [x] Add `WorkspaceHubDestinations.kt`: responsive Notes/Files/Canvas/Collections/Inbox/Memory entries over a bounded enum, plus a truthful non-action Studio state.
- [x] Wire entries through existing Notebook filters/collection controls/Memory owner, keep Create/Import/Continue and saved item navigation. Clarify Capture vs the Inbox content view.
- [x] Update existing EN/LV/RU Notebook-facing resources consistently while retaining resource keys and source compatibility; add native hub copy. Unsupported Polish remains honest fallback.
- [x] Add native callback, unavailable-state, localization/200%/landscape/light/dark render and route-compatibility regression sources. Retain existing repository/migration/search coverage.
- [x] Read-only review: no actionable P1/P2 findings; native wrapping and Studio non-action source verified before validation.
- [x] Source frozen; both APK assemblies and 59 tests passed, zero failures/errors/skips; five native captures inspected. User explicitly deferred phone checks to #43.
- [x] Package fresh evidence/source trees and publish implementation with `[skip ci]`; final phone checks remain deferred under #43.

## Review focus

Old Notebook deep links and assistant handoffs must still resolve. Hub filters must clear unrelated selections without changing content. Memory must not silently become a file filter. Unavailable Studio must not dispatch or claim a result. Controls and long localized labels must remain reachable at 200%, with no fixed-height text clipping. Shared resources must preserve positional placeholders and escaped Android characters.

Source completion checkpoint: native callbacks preserve the existing typed filters
and Memory route; the public skill display name/instruction and source labels say
Workspace while skill/tool/route IDs remain unchanged. Home Tools/Orb and backup,
privacy, import/export and recovery copy use the same translated name. New source
cases exercise six actual button callbacks, unavailable Studio with no click action,
Memory scope, every label overflow, actual font scale and five native viewport/locale
renders. The existing migration/search/repository suites remain the data-compatibility
gate. No build or test has run for this issue yet.

The skill regression also persists a disabled legacy `halo.notebook` preference and reloads it under the new public Workspace name, retaining exact tool ownership. Source freeze begins before the first serial gate.

Validation correction: the first five native cases failed an over-broad
TextLayoutResult.hasVisualOverflow assertion. A captured/measured diagnostic showed
Notes intrinsic width40, visible height19, but paragraph allocation108: the text
was readable and fit. Replaced the paragraph-allocation check with actual visible
line widths, vertical bounds and no-ellipsis checks. The scale and reachability
assertions remain. No production layout change was needed; final full gate rerun.
