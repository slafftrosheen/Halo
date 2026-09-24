# Provider contracts

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

Use [provider architecture](../ai/AI_PROVIDER_ARCHITECTURE.md),
[provider setup](SETUP.md) and [sdk/provider-sdk](../../sdk/provider-sdk).

Provider abstraction is not a security sandbox. Adapters own platform/native/network integration.
Validate model-specific modalities and distinguish inference from handoff.

The Android composition root designates `Providers.inference` as the clean-install inference
default. Registration order must not silently choose another local adapter. An explicitly persisted
user selection still wins.
