> Status: DOCUMENTATION INDEX. Reviewed 2026-09-24.
>
> Index of the provider domain. Implementation claims require current source and wiring.

# 08-providers

- [Setup and troubleshooting](SETUP.md)
- [Provider contracts](SPEC.md)
- [AI provider architecture](../ai/AI_PROVIDER_ARCHITECTURE.md)

Native llama.cpp is the Android host's designated clean-install inference provider. Ollama and
OpenRouter are optional configured inference backends; Android Assistant is a handoff boundary,
not an inference backend.
