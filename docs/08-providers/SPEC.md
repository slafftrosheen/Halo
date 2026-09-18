# Provider contracts

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

Use [provider architecture](../ai/AI_PROVIDER_ARCHITECTURE.md) and
[sdk/provider-sdk](../../sdk/provider-sdk). The former Prompt/Options/Attachments/
Cancellation Token -> Result sketch was not the actual Kotlin API.

Provider abstraction is not a security sandbox. Adapters own platform/native/network
integration. Validate model-specific modalities and distinguish inference from handoff.
