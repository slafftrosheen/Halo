# MINING-P3-001: multimodal perception and capability packs

Source implementation: 2026-09-30. Baseline: `faca1ae45cb1fc3ac8d8f7dee5836244e6deebc6`. No commit made. The checkout already contained extensive P0/P1/P2 changes; those are preserved and are not claimed as new P3 implementation.

## Architecture and behavior

Typed media identity lives in runtime-sdk. MediaAssetRef contains identity, kind, MIME, size/hash, timestamps, provenance, lifetime and interaction/device/Workspace ownership; it contains no bytes, filesystem path or URI. Host resolvers provide content at execution time. Android cache uses opaque IDs, exact reference/hash verification, a 64 MiB total bound, 15-minute maximum age, startup orphan cleanup and terminal-interaction cleanup. Existing explicit Save/import remains the route into Notebook/Workspace; model context acquisition does not persist captures there.

Image preparation accepts JPEG/PNG/WebP with a 20 MiB source bound, validates decode MIME/dimensions, sample-decodes, applies EXIF orientation using AndroidX ExifInterface 1.4.2, caps output at 1,048,576 pixels and 1280×1280 dimensions, strips metadata through JPEG/PNG re-encoding and reduces quality/dimensions until output fits 4 MiB. Original cached bytes remain available for explicit Save. The same preparer runs at the cognition boundary for local and remote image inference.

Explicit Home/shared/Notebook references take precedence and use REFERENCE freshness. Wearable-origin visual acquisition resolves the connected HeyCyan camera, preserving its resource lease and transfer coordinator; it cannot silently switch to a phone camera. Phone-camera inputs are explicit Home captures. Automatic phone world-view acquisition is not introduced. Assistant semantic accessibility content remains preferred; screenshot fallback is opt-in, requires a safe known package and no sensitive omissions, and requires a local vision target.

Required IMAGE/TEXT capabilities, target execution location, global image policy and object AI access are checked before media materialization/inference. Visual capture is not started when no allowed target resolves. Text-only models cannot silently consume an attached image. Local-only includes a rejection of LAN inference. Object policy restrictions remain in the prepared workload used by retries/fallbacks. Image-derived instructions are identified as untrusted evidence. Media context does not authorize tools or memory writes.

HeyCyan visual questions use the existing cognitive session, workload planner, target router, context acquisition and wearable response route. Existing audio affinity and DISALLOW_PHONE rules remain authoritative; P3 does not introduce a parallel wearable assistant.

## Provider qualification and lifecycle

ModelInputCapabilityReporting prevents provider-wide capabilities from substituting for a selected artifact's capabilities. AiModel carries explicit executionLocation. LiteRT now loads request.modelId, enables its shared image engine configuration only for a declared vision artifact and retains the existing single resident runtime/session and resource coordinator. Binding the same artifact to chat and vision does not create a second engine.

Vision qualification distinguishes DECLARED, PROBE_REQUIRED, PROVEN and FAILED. Evidence keys include artifact fingerprint, runtime and image contract. The explicit synthetic probe draws HALO and 42 in memory, requests bounded generation through the normal provider and checks expected content without tool calls. It accesses no camera. Evidence is process-scoped and invalidates when identity/contract changes; persistent probe evidence is not implemented. No built-in catalog flag was upgraded to vision, and no model was downloaded.

OpenAI, compatible transport, Gemini, Anthropic and OpenRouter serializers use the shared request image MIME selector. No new remote provider or upload subsystem is created. Ollama models identify LAN execution explicitly.

Audio preparation provides AudioAssetMetadata, PCM16 frame/rate/channel/byte/duration validation and WAV framing through the existing PCM utility. AudioModelTranscriber is an optional bounded, transcription-only first pass with empty history/tools, explicit remote-audio permission and rejection of tool calls; its result is text for the normal speech-result path. SpeechInterpretationStrategy keeps DEDICATED_STT as default. Normal Sherpa voice capture is unchanged. No audio-model adapter is enabled, and LiteRT does not advertise operational AUDIO without a backend. Reference-based audio preparation requires a host resolver that supplies audio metadata; the current image cache does not synthesize that metadata.

RuntimeCapabilityRegistry covers local vision, semantic memory, smart interruption and audio transcription with UNAVAILABLE, AVAILABLE, INSTALL_REQUIRED, INSTALLING, INSTALLED, QUALIFICATION_REQUIRED, READY and FAILED. READY requires actual compatible runtime and keyed qualification evidence. Only local vision currently has an evidence adapter; absent optional adapters remain unavailable. No optional artifact is installed automatically.

## UI and diagnostics

Home has one image attachment, source/thumbnail/budget display, remove, explicit Save and Vision setup access. Shared single images and Notebook image detail actions attach explicit references. Notebook exposes local-only, configured-cloud and excluded object AI policies. Intelligence exposes Default/Fast/Reasoning/Agentic/Vision bindings, capability inspection and screen fallback consent. Model Browser shows readable supported input chips and evidence-based Vision status; raw flags/access/maturity/revision remain in Details. HeyCyan offers connection/setup status and an explicit visual question. System Check offers an explicit synthetic vision probe and reports dedicated-STT availability rather than assuming it is loaded.

HALO_VISUAL_SOURCE_RESOLVED, HALO_VISION_TARGET_RESOLVED, HALO_MEDIA_CONTEXT_SELECTED, HALO_MEDIA_PREPARED and HALO_AUDIO_TRANSCRIPTION_STRATEGY report bounded metadata. Activity receives prepared/blocked media receipts. Diagnostic export includes target/evidence, image dimensions/bytes/MIME/provenance, retention, default speech strategy and capability states. It does not export media bytes, paths, OCR text or audio content.

## Source manifest

New P3 files:
- sdk/runtime-sdk: multimodal/MediaAsset.kt, AudioInputPreparer.kt, VisualSourceResolver.kt; capability/RuntimeCapabilityRegistry.kt.
- sdk/provider-sdk: ModelInputCapabilityReporting.kt.
- app/capture: EphemeralMediaAssetStore.kt, AndroidImageInputPreparer.kt, VisualAttachmentStore.kt, VisualAttachmentThumbnail.kt, WorkspaceMediaAssetResolver.kt, MultimodalStatus.kt.
- app/data/workspace: WorkspaceMediaAiPolicy.kt.
- providers/litert: LiteRtVisionQualification.kt.
- runtime/cognition/workload: AudioModelTranscriber.kt.
- docs/engineering: MULTIMODAL-PERCEPTION-MINING.md.

P3 changes to existing files or files already introduced by earlier uncommitted batches:
- SDK: AI.kt, LocalInferenceRuntime.kt, ConfiguredOpenAiCompatibleProvider.kt, OpenAiCompatibleTransport.kt, Observation.kt, LeasedCapabilities.kt, multimodal/PcmAudio.kt.
- Cognition: ContextAssembler.kt, DefaultContextAssembler.kt, ContextAcquisitionCoordinator.kt, ContextAcquisitionModels.kt, AssistantContextRepository.kt, ContextBudget.kt, AiWorkloadPlanner.kt, CognitiveTurnPreparer.kt.
- Providers/engines: AiProviderRouter.kt, LiteRtLocalProvider.kt, LiteRtRuntime.kt, LiteRtEngineConfigFactory.kt, LiteRtControlProbe.kt, OllamaProvider.kt, OpenAiProvider.kt, GeminiProvider.kt, AnthropicProvider.kt, OpenRouterProvider.kt.
- App: ManualDependencyGraph.kt, RuntimeVisualContextSource.kt, AssistantRuntimeBridge.kt, LocalCaptureStore.kt, HeyCyanCaptureCoordinator.kt, SendIntentHandler.kt, HomeViewModel.kt, HomeScreen.kt, WorkspaceObjectDetailScreen.kt, HeyCyanControlScreen.kt, IntelligenceViewModel.kt, IntelligenceScreen.kt, ModelBrowserScreen.kt, SystemCheckViewModel.kt, SystemCheckScreen.kt, CapabilityStatusViewModel.kt, DiagnosticsExport.kt.
- Driver: HeyCyanThumbnailCaptureCoordinator.kt.
- Dependency declaration: app/build.gradle.kts and gradle/libs.versions.toml (ExifInterface only).
- Documentation: CURRENT-STATE.md; docs/04-architecture/ARCHITECTURE.md; docs/08-providers/ARCHITECTURE.md; docs/10-features/FEATURES.md; docs/11-ui/UI-IMPLEMENTATION-STATUS.md; docs/12-security/THREAT_MODEL.md; docs/14-roadmap/ROADMAP.md; docs/engineering/REFERENCE-HARVEST.md.

## Harvest and uncertainties

See [reference harvest](REFERENCE-HARVEST.md) for upstream URLs. The voice-agent image/WAV/backend patterns are adapted into Halo's typed input machinery; no ADK runtime dependency, second conversation engine or upstream app was imported. The Android-Agent harness-improve/core-engine path was not reliably inspected and is not claimed as copied evidence.

Basic source review and git diff whitespace checks only. No Gradle, build, compilation, tests, lint, emulator, adb, CI or physical-device acceptance was performed. Dependency resolution was not performed. Stabilization must confirm the pinned LiteRT-LM 0.17.1 constructor/image contract and native behavior, imported artifact metadata correctness, probe reliability/timeout cancellation, image EXIF and decoder behavior, resource eviction/cleanup, HeyCyan transfer and output affinity, role fallback privacy and Compose UX. Assistant screenshots fail closed if the safe semantic snapshot has not arrived first; callback ordering and a future bounded pending-screen design need device evaluation.

Deferred: video inference/streaming, continuous/background capture, surveillance, always-on visual memory, blanket model OCR, replacing Sherpa, arbitrary audio reasoning or audio-to-tools, emotion/speaker/face/people identification, unrestricted screenshots, cloud bypass, unverified downloads and prior-batch A2UI changes.

SOURCE COMPLETE.
BUILD NOT RUN.
TESTS NOT RUN.
DEVICE ACCEPTANCE NOT RUN.
