# Provider setup

Status: CURRENT SOURCE MAP. Reviewed 2026-09-27 (LiteRT model acquisition + verified inference path).

This page describes setup implemented by the current Android host.

## On-device LiteRT-LM — default

Provider ID: `provider.litert.local`

This is Halo's clean-install/default inference provider. It runs `.litertlm` models directly on the phone
using LiteRT-LM (GPU or CPU) and requires no server, API key or cloud account.

Runtime: **LiteRT-LM 0.17.1** (`com.google.ai.edge.litertlm:litertlm-android:0.17.1`).

### Built-in downloadable models

Halo currently ships **exactly one** zero-configuration downloadable artifact:

| Field | Value |
| --- | --- |
| id | `qwen3-0.6b-dynamic-int4` |
| Name | Qwen 3 0.6B Dynamic INT4 |
| Source | `litert-community/Qwen3-0.6B` (official LiteRT Community repository) |
| Revision | `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76` (immutable) |
| File | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` |
| Bytes | `344671744` |
| SHA-256 | `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856` |
| Context | 4096 tokens |
| Capabilities | Text only (no vision, no tools) |
| Minimum runtime | LiteRT-LM 0.17.1 |

The download URL is derived from `repoId` + `revision` + `filename`; `main`, `latest` and other mutable
refs are never used. Integrity is checked on the streamed bytes, never on the HTTP `Content-Length`
header (which may be absent or wrong behind a CDN redirect).

**Gemma is withheld.** The real upstream (`litert-community/Gemma3-1B-IT`) is gated by Gemma terms and
Halo has no Hugging Face authentication or gated-license acceptance flow, so it is not a valid
zero-config built-in. Halo will not use a mirror to bypass licensing or authentication; Gemma can
return later through a proper gated-source flow.

### Setup

1. Open **Settings -> Intelligence**.
2. Keep/select **On-device (LiteRT)**.
3. Open **Browse models for this phone**.
4. Press **Download** on *Qwen 3 0.6B Dynamic INT4* and wait for **Verifying file** to finish.
5. Press **Verify** to qualify the artifact (loads the engine, then generates a bounded probe).
6. Press **Select** — this atomically sets **On-device (LiteRT)** + the Qwen model.

### Model states and what they mean

- **installed** != **verified**: a file that finished downloading is only *installed*. Only a completed
  qualification run reports **Verified on GPU/CPU**.
- **selected** != **resident**: selecting a model does not load it into memory.
- **resident** != **generation-proven**: a loaded engine is not proof that generation works.
- Only a catalog artifact whose physical bytes **and** SHA-256 match the pinned entry is offered as
  **Select**-able. Anything else renders as **Corrupt/stale — re-download**.
- A model imported through SAF is **UNVERIFIED** until it is qualified. A byte-identical import of the
  pinned Qwen artifact is canonicalized to `qwen3-0.6b-dynamic-int4` instead of creating a duplicate copy.
- An imported model with unknown metadata uses the model's own default context window; Halo does not
  force a guessed 4096 tokens.

Legacy `.gguf` files remain on disk for user cleanup via Intelligence settings but are no longer loaded.

### Troubleshooting

- **"could not be loaded"** — open **Details** on the model card for bounded technical diagnostics
  (stage, backend, bytes, SHA, context, exception type/message). Pair it with logcat
  (`HALO_LITERT_LOAD_FAILED`, `HALO_LITERT_GPU_FAILED`, `HALO_LITERT_CPU_FALLBACK`, `HALO_LITERT_LOAD_READY`).
- **Corrupt/stale after upgrading** — older builds installed artifacts with fabricated fingerprints.
  Press **Re-download**.
- **No arm64-v8a** — LiteRT local inference requires the `arm64-v8a` ABI; the app fails early with a
  diagnostic instead of entering native initialization.

## Ollama — optional local-network server

Provider ID: `provider.ollama`

Ollama is not required for Halo's default path. It is an optional HTTP server, commonly running on
another computer reachable from the phone.

1. Install/start Ollama on the host.
2. Pull at least one model on that host.
3. Make the Ollama API reachable from Android.
4. Select **Ollama** in Intelligence.
5. Enter a reachable endpoint such as `http://192.168.1.10:11434`.
6. Save/refresh and select a model returned by Ollama.
7. Use **Check connection**.

On a physical phone, `localhost`/`127.0.0.1` points to the phone, not your PC. The Android emulator
may use `http://10.0.2.2:11434` as a development-only host alias.

Halo's current Ollama adapter advertises TEXT and STREAMING, not TOOLS.

## OpenRouter — optional cloud inference

Provider ID: `openrouter`

1. Create an OpenRouter API key.
2. Select **OpenRouter** in Intelligence.
3. Save the key.
4. Select an advertised model, or keep Halo's default `openrouter/free` router.
5. Use **Check connection**.

OpenRouter is cloud inference. Context included by Halo's current policy can leave the device when
this provider is explicitly selected.

## Android system assistant — handoff only

Provider ID: `android.system.assistant`

This is not an inference backend.

1. Configure a system assistant in Android settings.
2. In Intelligence, use the **External assistant** section.
3. Halo dispatches an Android assistant handoff.

A successful handoff does not establish that Halo received a model answer.

## Sherpa speech recognition

Halo's production recognition path is local-first.

1. Open **Settings -> Voice**.
2. Install the supported Sherpa/Whisper model.
3. Return to Home; the provider refreshes without app restart.
4. Grant microphone access when voice input is requested.

Missing local STT does not silently fall back to Android/OEM SpeechRecognizer.

## Android TTS

Android TTS is the current synthesis provider and needs no Halo API key. Installed voices/languages
depend on the phone's TTS engine. Synthesis and output routing are separate: wearable-origin speech
with phone fallback disabled must never leak to the phone speaker.

## Routing rules

- Clean install -> on-device LiteRT-LM.
- Persisted explicit user provider selection wins after restart.
- Default routing policy -> local-only.
- Explicit provider selection -> explicit routing.
- Android Assistant never enters inference candidates.
- Provider diagnostics are on-demand; no background Ollama/OpenRouter polling.

## Troubleshooting

**No model selected:** download or import a `.litertlm` model in Model Browser.

**Legacy GGUF files present:** GGUF files are no longer loaded by the LiteRT runtime. Use Intelligence Settings -> "Delete legacy GGUF files" to clean them up.

**Ollama offline:** verify host process, host IP/port, endpoint scheme and that the selected model is
pulled on the Ollama host.

**OpenRouter unconfigured:** save a valid key, refresh models, select a model/router, then Check connection.

**Android assistant unavailable:** configure Halo as the default assistant in Settings -> Android Assistant.
