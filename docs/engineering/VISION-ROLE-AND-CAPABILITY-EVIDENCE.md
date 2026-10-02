# Vision role and capability evidence

AiModel stores capability evidence independently of advertised input capabilities: AUTHORITATIVE_METADATA, HALO_PROBE_SUCCESS, CATALOG_PIN, HEURISTIC and UNKNOWN. Runtime image admission requires verified IMAGE input. Heuristic model names and generic multimodal labels do not qualify. OpenRouter architecture.input_modalities supplies authoritative image metadata; openrouter/free is always text-only. OpenAI and Gemini catalogue declarations require an explicit successful probe where authoritative metadata is absent.

A user-run synthetic probe draws HALO and a random number in an ephemeral image, asks the provider to read the image without revealing the answer in the prompt, and records success only after both tokens are recognized. Merely receiving a nonempty answer does not qualify the model. Capability evidence is retained through model catalogue caching.

VisionLab compares up to eight concrete models against one acquired/prepared image. It sends no history, memory, tool definitions or private system instruction. All cloud-image, screen-context and object sharing policies are checked before acquisition and provider submission. Sources are gallery, phone camera, a fresh HeyCyan thumbnail and an active assistant screenshot. Diagnostic wearable images do not enter normal inference. Synthetic probe media has its own lifetime, independent of the selected diagnostic image.

Results record provider/model/evidence, local execution, source/prepared byte counts, dimensions, MIME, preparation/request/total durations, response length, stage and failure classification. Full responses, base64 and source images are not journalled. Request stages distinguish capability, policy, acquisition, preparation, serialization, request, HTTP and parsing failures.

Provider wire tests use actual Gemini, OpenAI and OpenRouter implementations with intercepted HTTP responses and a real prepared JPEG. They check one image, matching MIME, preserved user text, no tools and no image in system/history messages. This verifies serialization, not live provider recognition or reliability.

LiteRT runtime supports image bytes only for a descriptor with supportsVision=true and a configured vision backend. The shipped text artefacts do not qualify for VISION. No guessed multimodal artefact, hash or download was added. Local multimodal acceptance remains pending a verified compatible artefact and device probe.

## Automated result

VisionLab policy/evidence tests and the actual Gemini/OpenAI/OpenRouter HTTP wire test pass in the complete 610-test suite. The wire fixture uses Robolectric native graphics to prepare a real JPEG. Live recognition remains pending. Remote probe evidence is not applied to on-device models; LiteRT qualification remains tied to its own artifact fingerprint and runtime version.
