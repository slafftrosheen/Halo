# AppFunctions consumer architecture

Native → AppFunction → DirectAction → Intent → Accessibility → User.
Model reasoning never owns platform authority.

`host-android/AndroidAppFunctionConsumer` owns AndroidX alpha12 feature detection, search, state checks, observation, metadata conversion and platform execution. AndroidX/platform classes do not enter runtime or provider SDK contracts. AppFunctionManager.getInstance determines device/profile support, including extension-enabled devices; manifest permission declaration does not imply authorization.

Observation starts before explicit initial search/state reads. Searches target the visible package, or Halo when no foreground app is known. Discovery, state and execution have deadlines. This is event-driven invalidation and on-demand refresh, not background accessibility monitoring.

On API 37, VoiceInteractionSession translates its ActivityId using getAppFunctionActivityId; activity-scoped metadata is filtered by getAppFunctionActivityStates. Opaque activity IDs remain transient. Activity change, hide, function state change and package revision checks invalidate or reject stale execution.

`AppFunctionCapabilityIndex` stores immutable bounded generations. `ExternalSemanticToolCatalogue` supplies a removable ToolRegistry overlay with hashed generation-bound names and forced confirmation. It does not permanently register external functions in HostToolRegistry. Known Halo-owned semantic aliases permit deterministic native interpretation; unfamiliar functions remain sensitive external capabilities.

Execution rechecks enabled state, package revision and activity-scoped membership before dispatch. It captures the activity handle and rejects an identity change rather than attaching an old approved action to a new activity. Failed observation can be re-established by the next explicit refresh. AppFunctionData uses the discovered metadata schema. Int range is checked before conversion. Success means DISPATCHED / UNCONFIRMED; platform errors remain typed and cannot erase a dispatch claim. External returned data is not effect evidence or authorization.

The composition root connects Native media, AppFunctions, existing DirectAction dispatch and token-based accessibility to one broker. Shared ToolTurn claims and approvals remain the only execution authority. A possible dispatch prevents fallback and replay.

Flat scalar argument types are supported. Nested, array and resource/URI schemas remain excluded. Unsupported platforms and denied callers retain the normal native tool path.

Official API references: [AppFunctionManager](https://developer.android.com/reference/kotlin/androidx/appfunctions/AppFunctionManager), [AndroidX releases](https://developer.android.com/jetpack/androidx/releases/appfunctions), [AppFunctions guide](https://developer.android.com/ai/appfunctions). APIs were verified against alpha12 sources and the local Android 37 SDK.
