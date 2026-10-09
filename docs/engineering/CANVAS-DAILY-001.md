# CANVAS-DAILY-001 — everyday capture and working board

Implemented directly on `main`, 2026-10-09. This is a native Android / Jetpack Compose implementation. No web canvas dependency, vendor-hosted backend, or database migration was introduced.

## Existing foundation retained
- Board cards, links, connections, selection, viewport, export-to-note / Study, and AI draft review still use the original canvas repository and Workspace object identities.
- Source documents remain Workspace objects. A canvas stores references only; removing a card does not delete its file or note.
- AI actions retain their existing provider routing and cloud-sharing opt-in; importing a file does **not** upload it to an LLM.

## New capabilities
1. **Import file** uses Android's system OpenDocument picker with all MIME types (PDF, images, DOC/DOCX, text, audio and other readable files). The existing WorkspaceFileRepository creates the file object, then creates an app-owned copy with a strict **64 MiB cap**. An import that cannot be copied is not linked and its provisional Workspace entry is moved to trash. Open an imported card through Workspace file details, which provides Android file opening and existing relink facilities. Imported file cards have a taller default height to fit actions. Imported image files are shown as sampled local thumbnails; PDF and other documents remain openable through the existing Workspace detail route. No embedded PDF rendering or document editing is claimed.
2. **Voice memo** records AAC in an MP4/M4A container from the microphone after runtime permission. Foreground-only recording is stopped when the screen goes to the background. On Stop & save, the recorder's temporary file is imported through the exact same bounded local Workspace file path; the temporary recording is removed only after a stable copy and card link have been created. The main audio-perception runtime is not modified and a busy microphone may prevent recording. Audio file cards provide opt-in local playback with MediaPlayer; playback is stopped/released when the card leaves composition.
3. **Sketch** opens a native finger/stylus pad with undo-last-stroke and clear, followed by explicit Save. Strokes use screen-independent coordinates in a bounded `sketch:v1|` payload kept in existing node text; SKETCH is an additive enum value, not a schema change. Sketch cards are visually previewed and may be edited and duplicated. Unknown/corrupt sketch content is rendered as an empty preview, not interpreted as a command.
4. New cards appear in the **center of the current viewport**, rather than unexpectedly at an off-screen/origin location.
5. Added reviewed EN/RU/LV labels and a pure codec round-trip / limits test.

## Important limitations and next validation
- This is not a full infinite whiteboard replacement. No rich PDF pages, free-placement image bitmap nodes, handwriting recognition, embedded audio waveform, export-as-PNG/SVG, or multi-selection yet.
- On a device, validate permissions (allow/deny/revoke), microphone conflict with Halo listening/HeyCyan, recording stop on Home/background/rotation, M4A open/play using available Android viewers, large/unknown file sizes, revoked URI permissions, DOCX/PDF external open, sketch input/undo/resize, 150–200% font and RU/LV labels.
- Source-only implementation: Android Gradle tests, real audio capture, provider URI lifetimes and performance **have not been executed here**. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` on the checked-out repository before shipping; no GitHub Actions were added or modified.

## Reference implementation research (patterns, not embedded code)
- [Excalidraw](https://github.com/excalidraw/excalidraw): sketch gestures, predictable undo and portable drawing serialization. It is a web editor; keep Halo's native Compose drawing and persistence.
- [tldraw](https://github.com/tldraw/tldraw): shape/tool abstraction and world-space coordinates. Production use of the tldraw SDK has license requirements; not added to Halo.
- [SimpleStorage](https://github.com/anggrayudi/SimpleStorage): SAF picker/persistable-URI lifecycle and local-copy tradeoffs. Use Halo's existing WorkspaceFileRepository instead of another library.
- [VoiceRecorder](https://github.com/KaushalVasava/VoiceRecorder): Android microphone permission and recorder lifecycle ideas; implementation here is new and self-contained.
