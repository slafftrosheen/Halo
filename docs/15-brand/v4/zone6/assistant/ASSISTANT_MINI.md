# halo · Android assistant & mini halo — Zone 6 (pack 6.5)

Baseline repo @98aa2fb. Owners unchanged: `HaloVoiceInteractionSession` (+ native confirm/pick), `AssistantSurface` (presentation), `AssistantRuntimeBridge`, `FloatingHaloService` (bubble), `MiniHaloActivity`/`MiniHaloScreen`.

## 1. Assistant = one island over any app
- Rises from the bottom (ASSIST_INVOKE sweep, spring 0.62/380), inset 10dp, radius 32dp, max 420dp tall, max width 600dp; the host app stays visible above (soft scrim only at the bottom).
- Order identical to the dock: **Stop/Close · ring 60dp + caption · Open halo**.
- Context chips: “In Messages” (current app) and “Screen included” (only when the screenshot/structure was attached).
- Edge light breathes while Listening / Pick; Apricot static while NeedsYou; none otherwise.

| Runtime state | Island | Caption |
|---|---|---|
| Listening | “Listening” + live words + wave | Listening |
| Transcribing | “Understanding · Turning your voice into words.” | Working |
| Preparing / Generating | “Thinking” + real step chips (only if reported) | Working |
| Speaking / Completed | answer title + body + app action chips (direct actions from NativeAssistantActions only) | Tap to stop → Talk |
| InteractionSurface (inputRequestId) / VoiceInteractor confirm | **Apricot** NeedsYou: question, preview, “Messages is asking · nothing sent yet”, [Not now] [**action-named button**] | Waiting for you |
| VoiceInteractor pick option | numbered options (50dp), “Say the number or tap.” | Listening |
| Failed | problem title + what still works + up to 2 fixes; question kept | Talk |
| Cancelled / Interrupted | “Stopped · Ready whenever you need me.” then auto-hide | Talk |
Native confirm/pick no longer uses a plain AlertDialog “Voice request”; same `respond()` callbacks.

## 2. mini halo
- **Bubble** (FloatingHaloService, 60dp): it is the ring — idle breathe, listening glow, working arc (only rotation), needs-you Apricot dot. Snaps to edges as today; hidden while halo is in front (HaloHostVisibility).
- **Window** (MiniHaloActivity): compact island at the bottom — “mini halo” wordmark + status line, Open halo / Close (44dp+), suggestion chips, response card with Read aloud / Continue in halo, composer + ring 52dp. Needs-review state is Apricot with [Not now] [Review → Activity].
- Copy lowercase everywhere: replaces “Mini Halo”, “Ask Halo”, “Open Halo”, “Talk to Halo” (`res/values/assistant_mini_strings.xml`).

## 3. Accessibility
Island body is a polite live region; ring has caption as label and “Open Places”-style custom action is NOT added here (assistant has no Places). Pick options are numbered for voice and ≥50dp for touch. Bubble announces state changes via contentDescription only when the state changes.

## 4. Files
`compose/AssistantIsland.kt` (reference: island model, host, captions, bubble state notes) · `res/values/assistant_mini_strings.xml` · boards 19–21 · film `halo-assistant-mini.mp4`.
