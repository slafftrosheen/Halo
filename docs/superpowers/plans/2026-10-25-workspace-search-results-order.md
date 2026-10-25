# Workspace search results before secondary tools — #62

Authority: tests and plan only at12bdf94c0. Root owns builds, device actions and publication. Production remains frozen until a valid RED and explicit GREEN authorization.

Root subsequently recorded valid RED22: NotebookLibrarySearchOrderTest three cases/one rendered-order assertion failure (matching result top653dp, Tools top331dp); constrained callback/navigation and Hub guards passed. Six BrandNotebookHubTest guards also passed. Root then authorized the minimal NotebookScreen move: the mutually exclusive Hub/Library branches each emit the same existing notebook-tools key and callbacks, keeping exactly one such lazy item per composition. Source is prepared for root's GREEN; no device/build acceptance is claimed here.

Observed phone evidence: landscape2400×1080 with pending-review/footer leaves a Library viewport approximately[185,94]–[2251,739]. The owned BOARD-D query returns three results, but secondary Tools/Study/Memory precedes the matching content and requires extra scrolling. The existing NotebookScreen puts its notebook-tools lazy item before Library controls/results. Hub uses that same position intentionally.

1. RED through actual NotebookScreen and NotebookViewModel with isolated canonical Room note/Canvas/workspace/organization/context/conversation owners. Use memory-only preferences and fake only the derived-search port; actual title matching, filtering, ordering and navigation remain production owners. No live Graph, user database, providers, permissions, approval resolution or device state.
2. Prove rendered result order using geometry of all three real matching rows relative to Tools in a tall inspection viewport. Keep this distinct from a physical first-screen/scroll-count claim. Retain canonical result identities, title sort, selection and unmatched-item exclusion.
3. In a constrained landscape viewport, scroll the actual list to Study and Memory and assert callback/navigation identity, then scroll back to a matching note and assert its exact existing open navigation. Protect Hub continuation → Tools → Open Library order separately. Existing BrandNotebookHubTest remains a tool-callback guard.
4. After root records valid RED and authorizes GREEN, move only the existing secondary tools lazy item below the complete Library result/empty-state surface when showLibrary is true; keep its existing Hub position when false. Reuse the same callbacks and owners. Preserve headers, search/filter/collection controls, selection, sort, all results and empty/loading states. No new runtime executor, state owner or route.
5. Root runs focused RED/GREEN and the required integrated gate, then may repeat the owned device search without resolving arbitrary pending approvals. Desktop geometry/callback proof does not qualify actual voice recognition, provider execution or physical-device layout.

Focused command (root only):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*NotebookLibrarySearchOrderTest' --tests '*BrandNotebookHubTest' --no-parallel --max-workers=1 --no-daemon
```
