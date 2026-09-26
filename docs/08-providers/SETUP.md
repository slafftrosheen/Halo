# Provider setup

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

This page describes setup implemented by the current Android host.

## Native Local — default

Provider ID: `provider.llamacpp.native`

The provider ID is intentionally unchanged for settings/recovery compatibility, but it no longer means every local model is forced through one native engine.

Halo now supports two on-device runtime formats:

- **LiteRT-LM (`.litertlm`) — recommended Android path.** Uses Google LiteRT-LM 0.17.1, structured conversation history, a persistent inference cache, GPU initialization with CPU fallback, and no Ollama/API key/cloud account.
- **GGUF (`.gguf`) — compatibility / power-user path.** Uses the existing llama.cpp Android wrapper. GGUF artifact classification still filters projectors, imatrix/MTP auxiliaries and split shards before selection.

For the first phone acceptance run:

1. Open **Settings -> Intelligence**.
2. Keep/select **Native Local (LiteRT / GGUF)**.
3. Download **Qwen3 0.6B · LiteRT** from the built-in list.
4. Select it.
5. Open **System Check** and run the local text probe.
6. Record whether LiteRT initialized on GPU or fell back to CPU.

The curated Qwen3 artifact is the public LiteRT Community `Qwen3-0.6B.litertlm` model. Gemma LiteRT artifacts may require accepting the Gemma license on Hugging Face first, so Halo does not make a gated Gemma URL the zero-config download.

The current GGUF browser/import path remains available. A valid GGUF file may still be rejected by its native wrapper at load time; that no longer blocks Halo's default Android local-AI path.

The runtime may reduce context size when Android memory headroom is insufficient. LiteRT cache data is stored under app `filesDir/litertlm_cache` rather than Android's disposable cache directory.

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

- Clean install -> Native Local provider; curated Android recommendation -> LiteRT-LM.
- Persisted explicit user provider selection wins after restart.
- Default routing policy -> local-only.
- Explicit provider selection -> explicit routing.
- Android Assistant never enters inference candidates.
- Provider diagnostics are on-demand; no background Ollama/OpenRouter polling.

## Troubleshooting

**No model selected:** download and explicitly select a local model. For Android acceptance, start with Qwen3 0.6B · LiteRT.

**Download used to fail with “GGUF is missing general.architecture metadata”:** that was an overly
strict Halo download gate. Complete readable GGUF files are now retained; native llama.cpp decides
actual load compatibility.

**Ollama offline:** verify host process, host IP/port, endpoint scheme and that the selected model is
pulled on the Ollama host.

**OpenRouter unconfigured:** save a valid key, refresh models, select a model/router, then Check connection.

**Android assistant unavailable:** configure the system assistant in Android settings; normal Halo
Native Local inference does not depend on it.
