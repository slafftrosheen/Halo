# Workspace continuity #53 — verification in progress

Base: de4b7349b6469de87327c209b415000622a78377; fetched main unchanged, 0/0 before the integrated gate. No GitHub Actions.

## Implemented source

Canonical file versions now agree across Workspace, Canvas, Studio and selected context. Explicit handoffs capture a source version and revalidate it before use. Studio source handoffs create a separate project and retain a durable UUID receipt, including the originally observed source version, so restart or a duplicate delivery returns the saved project without overwriting later edits. Originals remain intact.

A shared bounded continuity facade exposes destination availability, source history, exact-version Canvas insertion and Working Set selection. Six typed assistant actions use the existing Workspace executor, policies and skill owner. Host dispatch status is distinct from saved-result evidence. Pending presentation requests expire and are never restored or automatically replayed.

Continue controls use the same owner from notes, files, Canvas, Studio and File tools. Source-history states distinguish current, earlier, unavailable and old unverified provenance. Canvas text review records a source-node digest; existing derived note IDs are accepted by managed exports. English, Latvian and Russian have 24 matching resource keys, with no missing source references.

Chat media resolution checks source lifecycle, availability, identity and bytes; Study refuses stale explicit handoffs and cannot replace a missing selected source with a general lesson. Memory promotion remains explicit through its existing owner, requires an active source and matching note revision, and forgotten records cannot resolve as context. Imports do not promote facts.

## Review and validation

Read-only source review completed before the first build. Corrected JSON/DOCX destination discoverability, derived Canvas note export IDs and nullable source-node metadata access. Whitespace and resource parity checks passed.

The first focused integrated gate passed in 11m59s: app and instrumentation APK assembly, six new continuity test classes and affected file, Memory, capture, Studio and archive regressions. Production compilation and both APK assemblies passed. Desktop test compilation and all 70 selected cases across 13 classes passed, with zero failures, errors or skips. Runtime policy/skill ownership/provenance checks passed in 1m1s: 7 cases across 4 classes, zero failures, errors or skips. The EN 360dp, LV 200% portrait and RU dark 200% short-landscape renders were inspected: text wraps, content scrolls and Close/Done remain reachable.

Built app: 287287643 bytes, SHA-256 `6d4833b9eca70edd1d37d0ed8544f598626631c645bb88f3dba75378a1d73ed0`. Instrumentation APK: 1963558 bytes, SHA-256 `444a3066df5abc36ffbebd1a79fe64517e90da0ee815cf8b1106fb94201cbe87`. Neither is installed.

## Native acceptance

ADB inventory immediately before the gate listed no attached target. Both new isolated Android cases are authored but NOT_RUN. #52 native cases also remain pending. No APK from this source batch is claimed installed. Synthetic owner tests do not establish actual spoken recognition, provider response, remote consent routing or live user-document behavior.

Issue #53 remains OPEN until required acceptance has evidence. Release qualification #43 remains open.

Compiled production tree identities: app/src/main `9a2590265acc101b1bf51afeae2893ad61c4f95d`; runtime/cognition/src/main `fe9b271e7191837eac038a9826ef0851b97792ae`. Evidence is retained in the adjacent issue53 artifact folder.

## Current native saved-object acceptance — 2026-10-24

[Current source/installed identities and raw evidence](HALO-CANVAS-CLEAR-054-VERIFICATION-2026-10-24.md) supersede earlier NOT_RUN/install status. Five native Workspace cases passed in14.385s on f01fe673/API35, including photo -> Studio edit/save -> Workspace export -> Canvas -> Study/Chat source bytes with exact source provenance and original preservation. The continuity fixture now closes/reopens a unique actual on-disk database and reconstructs Studio/Canvas/Study/media/search readers; saved project/result/card IDs, media bytes, lexical discovery, trashed source rejection and empty Memory survive reconstruction. Literal note/file persistence is independently covered by the strengthened #52 native case. RU200% Continue/Chat/Done interaction passed with technical IDs absent.

The scoped saved-object continuity acceptance is complete against source, existing70 desktop cases, runtime policies and current native readback. Study material resolution is verified, not saved Study-session restart. Database close/open is not OS process-kill acceptance. Actual spoken/provider/remote-consent flows remain #54/#47 qualification, broad lifecycle/accessibility/performance remains #43. No automatic Memory promotion, replay, provider or user-document mutation was introduced.
