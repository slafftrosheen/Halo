# Host UI and qualification scope copy — #36 / #39 / #43

Base main `bca90bd9ae69e7ced5f98bcad8b2b309e25c2710`, fetched0/0 clean. Shared/native resource coverage is complete for registered keys, but a raw-call audit found English host-authored startup, structured-result and notification text bypassing localization. Existing language-less legacy role measurements also omit their English fixture scope in UI/report copy.

- [x] Audit raw Text/native TextView/notification labels, renderer accessibility copy, current AppLanguageProvider root and the existing Context.nativeUi owner.
- [x] Reuse uiText/nativeUi for app-authored startup, picker, audio/attention notifications, compact comparison, save affordance and status/step/progress semantics. Preserve source/model content and existing callbacks/IDs/lifecycle.
- [x] Share the existing resource lookup/format resolver between Compose/native hosts so no-argument literal percentages are not parsed as format instructions. Guard both uiText/nativeUi static lookup keys.
- [x] Annotate legacy measured text/tool qualification UI and report as English-fixture scope, without promoting other-language exports or changing model/role selection.
- [x] Add all missing labels in EN/RU/LV/PL, author focused resource/format/structured-result semantic and callback regressions, then regenerate and review exact existing-key/value parity before one gate.
- [x] Publish verified source/evidence; keep phone notification/TalkBack, real language quality and other original acceptance pending.

Presentation-only changes, not new tool operations or authority. Runtime/user/model node text, native receipts, channel IDs, request IDs, callbacks, permission and privacy boundaries stay with their existing owners. Phone reconnected before the focused gate; assemble both matching APKs once for native validation.
