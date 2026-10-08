# Standalone Study and workspace AI

## FEATURE-069 — source-grounded Study (2026-10-08)

Study now chooses/imports up to four Workspace sources, resolves block-aware notes and bounded UTF-8 text, and sends one explicitly active image/PDF page only to exact verified vision targets under Workspace/cloud privacy policy. PDF support is lazy page-image rendering; PDF text extraction and OCR are not installed. Learner level, support style, independent languages and explicit source-only/general policy persist with per-source versions. Citations follow actual S1–S4 answer markers; model feedback remains unverified. Practice generation produces bounded, source-located drafts requiring Keep, and review preserves original provenance. Material/settings live in a lesson sheet; topic-only lessons remain available.

See [implementation, limits and validation](STUDY-SOURCES-002.md). Older source-only/design statements below are historical context.


Source implementation: 2026-10-01. Builds, tests, lint, emulator, device acceptance, and benchmarks were not run under the source-only instruction.

## Study as a tool

Study now has a primary navigation entry. Activity remains reachable through its existing Home links and routes. Starting a topic, question, or exercise creates a durable Study session with no notebook source. Starting from a note remains optional. Existing note/canvas/tool handoff routes remain supported and select the Study navigation item.

The Study home provides a topic field, optional explanation language, resumable active sessions, and a due-card entry that opens focused review directly. The lesson view prioritizes the explanation and learner response. Sources, review intensity, and measured progress live in a secondary dialog; complete stage strips are removed, memory curves and saved cards are optional, and card creation expands on request. Destructive session deletion has a concrete confirmation.

Learners can submit a graded attempt or ask a follow-up question without treating it as an attempt. Requests include up to four recent lesson replies and still respect the existing help-stage/full-solution rules. Up to 30 replies are kept in bounded local Storage history and restored on reopening. This local history is separate from Room attempts and is removed when session data is deleted. Exporting study notes remains available; transcript history is not a new Brain archive schema entry.

Topic-only requests use question routing. Sessions containing notebook excerpts use document routing and current workspace-sharing policy. Provider and speech selection remain owned by the existing router and narration controller.

## Canvas and Notebook AI

Canvas exposes review, expansion, and selected-card rewrite with optional user direction. Results are editable drafts that can be regenerated, discarded, or applied. Rewrites verify the original card text before replacement; review/expansion add a new card. Linked source documents stay under their own editor.

Notebook adds a Review action, editable AI proposals, regeneration, and cancellation. Selection-based replacements require an actual nonempty passage. Existing revision checks, acceptance, undo, and canonical document persistence remain in use. Notebook cloud requests now read the authoritative runtime policy rather than a stale legacy preference flow.

## Image studio

Both notes and canvases expose image creation and image editing, inspired by OneBox's separate image-provider flow. Models are chosen independently of chat. A draft supports generation, regeneration from the original prompt/reference, editing the current preview, and explicit Save. Existing images remain intact; edits save a new image. Settings provider details link to separate live image-model lists and default image selection.

Implemented image adapters:

| Provider | Discovery | Generation / edits |
| --- | --- | --- |
| OpenAI | Live GPT Image model IDs from its models endpoint | Image generations / multipart edits |
| Gemini | Paginated Gemini model inventory filtered for image GenerateContent models | GenerateContent with image output and optional inline reference |
| OpenRouter | Dedicated Image Models API with input capability metadata | Dedicated Image API with optional reference data URLs |

The image service uses the existing configured provider objects and credentials. Workspace sharing and cloud-routing policy gate requests. Sending reference pixels also requires cloud-image sharing. It does not silently fall back to another provider. Cancellation cancels the HTTP call; image responses and decoded dimensions are bounded. Supported raster bytes are saved into managed workspace assets with model/prompt attribution, rather than expiring remote URLs.

In Canvas, a saved image is backed by a regular note with an attachment and linked as an image card. Retry retains the note/asset identity if linking fails. Canvas thumbnails observe attachment changes and decode sampled previews off the main thread. Notebook images open an in-app preview. No new Room entity or migration is introduced by this pass.

## Provider setup and models audit

All 12 inference implementations have setup descriptors and model-list methods and are represented by the registry-driven provider center:

| Provider | Setup | Model source |
| --- | --- | --- |
| LiteRT | Installed model management | Local installed inventory |
| Ollama | Endpoint | Server inventory |
| OpenRouter | API key | Live/cached chat catalogue; separate image catalogue |
| OpenAI | API key | Live chat catalogue; separate image catalogue |
| Anthropic | API key | Paginated model catalogue |
| Gemini | API key or Google OAuth/project | Paginated chat/image inventory |
| Groq | API key | Configured compatible-provider discovery |
| Cerebras | API key | Configured compatible-provider discovery |
| xAI | API key | Configured compatible-provider discovery |
| OpenCode Zen | API key/token | Configured compatible-provider discovery/hints |
| OpenCode Companion | Endpoint and companion credential | Companion model inventory |
| Custom compatible endpoint | Endpoint, auth mode/key, optional model ID | Discovery plus explicit text-only model fallback |

Model catalogues retain bounded last-known-good metadata in Storage across restart. Endpoint changes invalidate visible metadata, and cache identities include the endpoint. Metadata refresh failures retain cached entries without proving runtime readiness. Explicit OpenRouter refresh bypasses its inference-time TTL cache. Image-only/audio-only outputs are kept out of chat selection, and Gemini/Anthropic discovery follows additional pages. Endpoint-based runtime location and protocol metadata come from actual provider descriptors.

Speech providers and Android assistant handoff remain in their existing Voice and Android Assistant settings: they are not inference providers and do not fabricate chat-model lists. No live credentials, provider generation, connection checks, or device runs were exercised by this implementation pass.

## API references consulted

- [OpenAI image prompting](https://developers.openai.com/api/docs/guides/image-prompting)
- [Gemini image generation](https://ai.google.dev/gemini-api/docs/generate-content/image-generation)
- [Gemini model inventory](https://ai.google.dev/api/models)
- [Anthropic model pagination](https://platform.claude.com/docs/en/api/models/list)
- [OpenRouter Image API](https://openrouter.ai/docs/guides/overview/multimodal/image-generation)

OneBox was inspected directly from `OneBox-main (1).zip`; its separate image-provider descriptors, generation manager, and settings model selector informed this flow. No vendored application code was copied.

## Validation status

Source review, dependency/route tracing, and `git diff --check` completed. Source implementation is complete. Compilation, real layouts, provider account access, image generations, frame performance, and device behavior remain unverified.
