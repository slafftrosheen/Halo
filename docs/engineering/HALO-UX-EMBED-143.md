# HALO-UX-EMBED-143 — Product Home, developer isolation and truthful local retrieval

Date: 2026-10-19
Target: Android `main`
Status: source implementation committed; compilation, physical UX validation and cross-modal native acceptance still pending. GitHub is storage only; no Actions, builds or tests were run in this batch.

## 1. Home product surface

- Home still uses the existing pinned `HaloComposer`, voice runtime, draft-state owner and submission gate.
- **Attach** and **Tools** are now beside the composer (available without scrolling the feed). Attachments open in a capped-height Material 3 bottom sheet. Existing camera/import, Notebook/Study context selection, privacy and vision links are preserved.
- The separate `ContextContinuityControls` save-review owner remains composed outside the sheet, while its sheet instance does not duplicate that review. Do not regress "Save result to Notebook?" after AI completion.
- The conversation feed displays the most recent four stored turns by default, rather than requiring the user to open hidden history. Earlier turns are available using Conversation history. Preserve edit-as-new-message, clipboard copy and current response card identity.
- Only practical tools (Inbox, note, timer, open app, new/clear conversation) remain in the composer menu; connection diagnostics were removed from that menu. Action admission remains governed by the real dashboard capability list.

## 2. Developer surface

- Advanced Settings now contains an explicit persistent **Developer tools** switch (`halo_developer_tools_enabled`). Without it, the Advanced tab exposes System check and Setup, not benchmark/model labs/operator diagnostics.
- On-device Models shows available downloads, install/readiness, active choices and model roles first. Raw Hub sync, file import and temporary Hugging Face token controls stay accessible under **Other model sources**, rather than dominating the landing page.
- Per-model benchmark, role matrix, probe, CPU/GPU/context tuning and trace content are hidden unless Developer tools is enabled and the specific Developer diagnostics panel is opened. Existing stored developer filters cannot inadvertently hide models after developer mode is disabled.
- Model tests remain source-supported; only their consumer exposure changed. No test runs are initiated on screen load.

## 3. EmbeddingGemma capability truth

Upstream:
- EmbeddingGemma (original 300M): **text embeddings**. Do not infer visual/audio inputs from Gemma-family naming.
- EmbeddingGemma **2 740M**: publisher supports text, images, audio and video in shared 768-dimensional space. Different encoder subsets and backend support affect realistic device fit. Upstream references:
  - https://ai.google.dev/gemma/docs/embeddinggemma/model_card
  - https://ai.google.dev/gemma/docs/embeddinggemma/model_card_2
  - https://developers.google.com/edge/litert-lm/overview

Halo implementation:
- `LiteRtEmbeddingProvider` currently calls `EmbeddingEngine.computeEmbedding(listOf(InputData.Text(...)))`; it supports only text input and requires exact pinned SHA, output dimension, runtime metadata, CPU TEXT support, a native resource lease, memory budget, and explicit source consent upstream.
- `LocalEmbeddingModalityPolicy` separates **publisher modality**, **wired adapter modality**, and **per-device proof**. Current wired set is TEXT, not IMAGE/AUDIO/VIDEO. Do **not** turn on a generative Vision/VLM flag on an embedding-only artifact.
- The catalog labels EmbeddingGemma 2 as **Local search** and its card explains that a semantic retrieval model cannot chat or generate a visual description on its own.
- Onboarding keeps generation candidates separate from the EmbeddingGemma 2 card. EmbeddingGemma download is explicitly requested, role configuration is a separate step, and selecting workspace sources/consent stays separate.

## 4. Remaining product acceptance

1. Build Android serially (maintainer/alpha tester), verify Kotlin/Compose and model regression tests; test font scale, keyboard IME, nav bar inset and reduced-motion on small phones.
2. Home: attach image, capture camera, reattach unavailable workspace source, select a note, save result to Notebook from a turn with attachment sheet closed, cancel an attachment picker, Stop while voice is active, switch conversation without losing the draft.
3. Check four-language UI copy: new product text is currently written in English where no reviewed EN/RU/LV catalog key exists. Finish translation source `app/src/main/localization/ui.tsv` and regenerate using `scripts/generate_ui_languages.py` before claiming localization acceptance. Polish UI language also requires separate product coverage work.
4. Settings: navigate Everyday → Advanced; verify developer probes are hidden until enabled; enable then navigate to models and disable; verify persistence and no automatic test activity.
5. Embedding: download pinned artifact; verify physical SHA and CPU TEXT embedding, select EMBED role, explicitly consent one note, index, search, revoke source and confirm no excerpts leak.
6. Native multimodal: separate work item; validate actual LiteRT native IMAGE/AUDIO/VIDEO APIs, per-modality encoder/backends, memory lease, input size/decoding, cross-modal index identity and no index mixing. Do not claim this is implemented.

## 5. Issue status

- Issue #34 was already closed before this batch. No change to its state.
- Issue #33 remains open: concurrent-stage latency evidence, CPU/GPU device matrix, cancellation, memory/thermal and full voice-to-voice acceptance are not complete.
- Issue #32 and Ink/wearable roadmap issues were not in scope; do not close them merely because product UI work progressed.
