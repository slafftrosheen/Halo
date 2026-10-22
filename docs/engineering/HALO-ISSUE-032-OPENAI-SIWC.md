# HALO Issue 32 — Sign in with ChatGPT research

**Research checked:** 2026-10-22  
**Decision:** The official integration route exists, but Halo should not ship or prototype a user-facing connection until OpenAI confirms Halo's eligibility and the Android client flow. Keep `openai_subscription` separate from `openai_api` and all existing providers.

## Findings

### Official route and eligibility

OpenAI publishes Sign in with ChatGPT (SIWC) terms for applications that connect a user's ChatGPT plan to AI features. The terms require using OpenAI's supported sign-in flow, keeping persistent tokens local and under the user's control, limiting requests to the connected app and authenticated user's activity, obtaining consent for background use, and offering the plan connection without charging for or requiring a paid version of the app. The terms prohibit pooling/reselling tokens or using one user's subscription for another user's requests.

OpenAI's SIWC client-ID page says that the program is currently offered to a select group of commercial partners and points interested developers to a waitlist. Separately, OpenAI publishes a token-sharing flow for open-source agent applications, including dynamic registration and PKCE. These are distinct published paths; the documentation does not establish that Halo is approved for either path or that the open-source agent flow is approved for a native Android assistant. Do not reuse another app's client ID or undocumented endpoints.

### Authentication and request path

The open-source agent flow documents a system-browser OAuth authorization-code flow with PKCE, a loopback callback on `127.0.0.1`, random `state` and OIDC `nonce`, and token exchange without a client secret. It documents refresh-token handling and user revocation. The exact callback URI must match between authorization and code exchange. The Codex app-server guide documents passing the user's OAuth access token to a local app-server child process, which makes Responses API requests using that token.

This documents an official path for compatible agent applications. It does not establish Android app-server packaging, lifecycle compatibility, supported redirect handling for Halo, Google Play qualification, or Halo's registration/approval. Those remain explicit gates.

### Capability and entitlement limits

The app-server guide says `model/list` can return a bundled catalog and must not be treated as proof of entitlement; a successful inference turn verifies access to a model for that request. ChatGPT plan eligibility and usage limits apply. SIWC does not grant extra usage or other OpenAI services. Therefore, Halo must not infer model availability or vision, image generation, audio, speech, tools, or agent support from account sign-in or a catalog entry. Each feature needs official documentation and live, account-scoped qualification before being enabled. Unsupported operations must stay disabled with a clear explanation; they must not silently fall back to paid API billing.

## Decision and architecture

1. Keep the proposed provider ID `openai_subscription` distinct from `openai_api`; do not alter existing provider forms or routing in this research-only change.
2. Do not implement or expose Connect ChatGPT yet. First obtain written confirmation of Halo's eligible integration path and Android client registration/redirect requirements from OpenAI, and confirm whether the intended distribution is allowed.
3. If approved, prototype a local-only account connection with the official flow, Android Keystore-backed local token storage, explicit connect/disconnect/revoke, cancellation/expiry/rate-limit/offline handling, and no token or prompt logging. Keep all requests user initiated; background activity needs express consent.
4. Start with text conversation only if the approved interface and a successful request prove it. Add per-capability model/feature flags only after qualifying each operation on the exact account and flow. Keep LiteRT, OpenRouter, and direct API-key providers independent, with explicit user-selected fallback and no surprise API charges.
5. Treat ChatGPT account identity sign-in and use of plan allowance as separate consents. Show usage/eligibility limits and explain that Halo is not endorsed by OpenAI unless written permission permits that claim.

## Acceptance and remaining work

- [x] Official route and terms documented with primary-source links.
- [x] Supported public auth flow and its limits documented; no third-party OAuth client or undocumented endpoint proposed.
- [x] Architecture decision recorded: integration is technically documented for supported apps, but Halo-specific approval and Android feasibility are unconfirmed, so no app integration is authorized by this research.
- [ ] Prototype: blocked until OpenAI confirms an eligible Halo client path and Android use. No tokens, client registration, or account-level tests were available for this research.
- [x] No CI, build, test, or GitHub Actions dependency.

This closes the **research issue** with a decision and concrete gates. It does not claim that Halo has OpenAI approval or that an integration is implemented, tested, or ready to ship. Reopen or create an implementation issue only after the two approval/Android gates are answered.

## Primary sources

- [Sign in with ChatGPT Terms](https://openai.com/policies/sign-in-with-chatgpt-terms/) (2026-09-29)
- [Request a client ID](https://developers.openai.com/siwc/request-client-id)
- [Open-source agent registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Open-source agent models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Codex app-server token-sharing configuration](https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server)
