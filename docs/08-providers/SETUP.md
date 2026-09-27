# Provider setup

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

This page describes setup implemented by the current Android host.

## On-device LiteRT-LM — default

Provider ID: `provider.litert.local`

This is Halo's clean-install/default inference provider. It runs `.litertlm` models directly on the phone
using LiteRT-LM (GPU or CPU) and requires no server, API key or cloud account.

1. Open **Settings -> Intelligence**.
2. Keep/select **On-device (LiteRT)**.
3. Open **Browse models for this phone**.
4. Download or import a compatible `.litertlm` model (such as Gemma 3 1B IT or Qwen 3 0.6B).
5. Choose **Select**.
6. Use **System Check** or **Check connection** to verify native load.

Legacy `.gguf` files remain on disk for user cleanup via Intelligence settings but are no longer loaded.

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
