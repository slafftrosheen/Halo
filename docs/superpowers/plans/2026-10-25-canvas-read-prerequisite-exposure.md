# Canvas read prerequisite exposure

Checkpoint 18's exact selected-card request now reaches ToolActionIntent/AGENTIC/REQUIRED. Discovery exposes `halo_canvas_edit_text` without `halo_canvas_current` or `halo_canvas_get`; the model attempts a write without either read and receives RESOURCE_UNAVAILABLE. The allocator granted its lease. A revision mismatch is a candidate, not yet a proven phone root cause: WorkspaceCanvasActions discards the original exception behind a generic rejection.

Confirmed metadata omission: the real edit descriptor has empty dependencies even though its operation requires an observed revision and its description directs selection-relative requests to read current selection. ToolExposurePlanner.expand already follows descriptor dependencies, respecting capability availability, per-tool exposure/privacy gates and local model policy. ToolRegistry validation requires dependency registration only, not a successful read or attached UI view.

1. RED: actual `HaloToolDescriptors.workspace` → real ToolRegistry/ToolExposurePlanner expansion should expose exactly edit/current/get when all are available. Verify no operation executes during exposure. Preserve USER_POLICY and NEVER_REPLAY; current/get remain read-only workspace.read operations. Verify read-capability unavailability, a denied current query and a restrictive local policy remain respected without suppressing the valid edit descriptor.
2. Preserve remote Workspace privacy, tools-disabled and ASK-mode denials. Preserve independent create exposure and explicit-ID/revision editing when no native Canvas view is attached. Adding metadata dependencies must never require executing current() for an explicit-ID write or invent selection/source authority.
3. After root verifies RED, add only the bounded descriptor prerequisites for `halo_canvas_edit_text`. Do not modify executor guards, auto-read/auto-write, authorizer, provider configuration or generic dependency planner semantics. Current and get stay separate: current resolves metadata-only active selection, get reads exact saved board content.
4. Retain the strengthened stale-version regression: RESOURCE_UNAVAILABLE and exact equality of board header, document revision, node rows and edge rows after rejection. Root owns build/phone publication and confirms the next live attempt actually reads current/get before editing and reopens the saved result.

Root RED command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.halo.app.data.workspace.CanvasReadPrerequisiteExposureTest --tests com.halo.app.data.workspace.WorkspaceCanvasActionsTest
```

Tests and this plan only until root authorizes GREEN. Passing exposure tests would prove read reachability and preserved gates, not model compliance or a successful phone edit.
