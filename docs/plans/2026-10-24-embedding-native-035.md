# Exact-artifact synthetic embedding phone diagnostic (#35)

Source base: main `767abe6ca105b4195e9c00d00129915a10e74861` (fetched before editing).

Implement an opt-in Android test and existing host-runner mode. Reuse LiteRtEmbeddingProvider, its pinned inventory/native factory/coordinator, existing fresh fingerprint verification and footprint sampler. No independent SDK handles, production executor changes, saved preference setters, index writes, downloads, provider traffic or capability promotion.

Before execution, freeze `synthetic-embedding-cpu-v1` scoring: three English source documents, one cat-care query in EN/LV/RU/PL, all four correct-document cosine margins >0.01. Separately compare two generated 256px PNG shapes, two English text queries and one duplicated image. Both text-to-image margins and image-to-image discrimination margin >0.01; duplicate cosine >=0.999. Provider validates finite normalized 768D vectors. Images are synthetic; this is not a representative media accuracy benchmark or reviewed language qualification.

Write per-stage STARTED, PASSED/QUALITY_FAILED/UNAVAILABLE/INTERRUPTED, timing and scalar scores before the final assertion. Persist no vectors/private media. Keep unsupported backend/native execution separate from semantic quality failure. Record sampled PSS/native heap, not exact RSS/peak/energy. Cooperative stage deadline 180s; host process timeout is authoritative for blocked JNI. An incomplete stage never passes.

Complete source/review first. Then one test-APK-only compile/assembly gate; production APK must retain its hash. Install/read back test APK, execute CPU suite on selected exact installed artifact, preserve failures/partial evidence, resume MainActivity. No broad unchanged test reruns. Publish source and honest evidence to main; #35 stays open for representative media/audio/video, consumer/permissions/low-RAM/cancellation/model-switch and language acceptance.
