# Private and Power modes — product and implementation specification

**Status:** Approved direction for implementation. This specification expands the existing Private / Cloud switch into two independently configurable processing profiles and one clear sharing choice. The current implementation is a compatibility baseline, not completion of this design.

## Product decision and language

Use **Private** and **Power** as the user-facing mode names. Power is the replacement label for Cloud: it means Halo may use its configured online providers and supported cloud features. Do not use “God mode.” That name suggests system-level or unrestricted authority Halo does not have, obscures where data goes, and cannot override Android, provider, family, or action-safety boundaries.

The central promise is simple: **choose how Halo may process requests once; tune each mode in one place; always be able to see where a request is going.** Selecting Power enables supported online Halo activities by default. It does not open a parade of Halo-made feature consent screens. The user can still tune the Power profile, and can return to Private at any time.

Mode is the top-level data-processing choice. A profile is the configuration used while that mode is active. Android permission grants, provider credentials, parental restrictions, action approvals, and spending controls remain distinct state owned by their existing systems.

## Mode behavior

| | Private | Power |
|---|---|---|
| AI processing | On-device only | Configured online provider/model, with local fallback only when the profile explicitly permits it |
| Cloud-capable Halo features | Off; no remote dispatch | All shipped and configured cloud features enabled by default |
| Context | Private profile's local context choices | Power profile's independently chosen categories; cloud categories on by default |
| Provider, model, routing | Independent Private selection | Independent Power selection |
| Android permissions | Existing grants still govern local features | Same grants govern local and device features; mode cannot grant them |
| Cost and action controls | Existing controls still apply | Existing controls still apply; mode is not spending or action approval |

Power's default-on list covers the cloud-capable functionality actually shipped in the installed build: remote conversation and model tools, hosted speech, web search/fetch, image understanding and generation, and eligible screen, audio/video, memory, and workspace context. A feature only becomes usable when its provider/model is configured and the required Android access exists. No unavailable provider is silently substituted.

Private is a hard dispatch boundary, not merely a model preference. It blocks remote AI, hosted speech, paid web operations, cloud image work, and remote context transfer, even when a stale provider or feature setting remains in storage. Local notes, files selected through Android pickers, local models, and device features continue subject to their own grants.

## One sharing choice, two tunable profiles

Keep one persistent Private / Power control in the app shell. Choosing Power is the user's broad authorization for the configured online route and the Power profile's enabled data categories. Do not require a separate Halo allowance tap for each ordinary cloud feature or each request.

Provide one **Mode profiles** destination with two visibly separate panels:

- **Private profile:** local model and routing, local context categories, and local-only feature behavior.
- **Power profile:** provider/model and routing, cloud context categories, and shipped online feature switches. Every supported online feature starts enabled when a profile is first created. Users can turn off individual categories here without affecting Private.

Mode changes save and restore the corresponding profile; editing or activating one must never rewrite the other. The shell summarizes the active profile in plain language, such as **“Private · processed on this device”** or **“Power · requests may go to OpenRouter”**. Details identify the selected provider, online capabilities, data categories, and whether costs may be billed by that provider.

Profiles do not contain API keys or other credentials. Continue using the existing secret store. Credentials, provider outages, unsupported models, disconnected network, and provider quotas get actionable setup/recovery states; none cause an undisclosed route change.

## Transparent activity without repeated approvals

Keep the processing path visible beside the composer and on any feature surface that can dispatch remotely. Before the first request after enabling Power, explain that prompts and the selected context categories can leave the device, identify the configured service, and say that provider charges may apply. This is part of the one-time mode choice, not an extra feature allowance.

For ordinary subsequent requests, show a compact, inspectable disclosure such as **“Sending text + selected workspace context to OpenRouter”**. Do not block every send with a confirmation dialog. If the active provider, destination, or data category changes, update the disclosure before dispatch and make the change obvious in the UI.

Add a Power activity history with content-free receipts: time, provider/service, operation type, data categories, result, and provider-reported cost when available. Never save prompts, message bodies, images, audio, credentials, signed URLs, or guessed costs. If the provider returns no cost, show **“cost not reported.”** Give users a clear way to clear this local history.

Power enables the features; it does not invent unlimited free service. Show known provider billing behavior and preserve existing user-selected limits and provider quotas. Do not silently add a new Halo-imposed spending approval step to every request. If a user-configured limit or provider quota blocks work, explain which limit stopped it and where that limit is controlled.

## Android access setup: one guided pass, OS-controlled grants

At first-run setup, offer one **Set up Halo capabilities** flow that inventories only Android permissions and special access required by features present in this build. Explain the feature and why it needs each permission, then let the user start setup in one pass. Where Android permits runtime prompts, launch them in a clear sequence, recording granted, declined, or not-yet-asked status. Group and order requests sensibly; do not issue a system prompt before explaining it.

Android does not expose a universal API for Halo to grant every permission or show every system prompt at once. Some grants require a feature-specific moment, an Android picker, or a trip to system settings; Android controls the final prompt and decision. Halo must not imply that selecting Power grants microphone, camera, location, Bluetooth, notifications, files, accessibility, or other system access. Respect denial and “don't ask again”; keep a useful degraded route and let users revisit the setup guide. Request a permission only when the installed app has a real feature that uses it. Prefer Android's document/media pickers over broad storage access.

This guide replaces scattered Halo allowance lists. It does not combine OS permission state with cloud-sharing authority: Android access lets a feature read/capture locally; Power determines whether eligible content may be sent to a provider.

## Runtime and storage design

The current mode owner is `PrivacyModeStore`, composed through `ManualDependencyGraph`; context settings are read through `RuntimeAiPolicy`. Extend these owners rather than adding a competing policy store.

1. **Versioned profiles.** Add a versioned schema with distinct Private and Power records for provider/model/routing, context categories, and online feature choices. Keep credentials in the current secret storage. Migrate `ai_privacy_mode_v1` and existing provider/model/routing/context keys transactionally. Preserve the selected mode; a saved Private install must never migrate into Power. Map legacy shared preferences explicitly and document every default.
2. **Safe activation.** Resolve and validate the target profile before publishing a mode change. Persist the outgoing profile, prepare the incoming route and effective context policy, then publish the new mode. A failed or corrupt profile falls back to Private with an actionable error. Do not expose a partially activated Power state.
3. **Owner-boundary gate.** Create a shared authorization decision at remote AI, speech, web, image, and remote-context dispatch owners. A UI label is not authority. Private rejects before dispatch and cancels supported cancellable requests when activated. The check must use the current mode/profile at dispatch time, not a stale UI snapshot.
4. **Routing isolation.** Remove mode switching's mutation of unrelated global routing preferences. Each profile resolves only its own provider/model/routing choice. A missing selection reports setup needed; it never silently switches to another remote service.
5. **Consent ownership cleanup.** Do not treat Power selection as Android permission, parental permission, phone-action confirmation, provider credential, or arbitrary spending-limit consent. Decouple paid-media consent from the global mode; Power may enable paid online capabilities, while configured spending controls and provider rules continue to apply.
6. **Observable receipts.** After profiles and dispatch gates are reliable, add content-free activity receipts at the actual dispatch/outcome owners. Record only metadata explicitly listed above and only provider-reported costs.
7. **Capability setup.** Add the build-aware Android capability guide after auditing the manifest, runtime requests, special app access, and feature ownership. Model each requirement as granted, declined, needs system settings, or not requested; do not mark optional features as required to use core chat.

## Implementation sequence

### A. Inventory and contract

- Enumerate every online dispatch owner and every permission request used by features in the current build.
- Establish a mode/capability matrix and map current persisted keys to Private or Power defaults.
- Add migration and owner-boundary coverage before moving screens to the new profile model.

### B. Profiles and dispatch enforcement

- Add schema, transactional migration, isolated profile reads/writes, safe activation, and dispatch gates.
- Ensure Private blocks all remote paths and mode switch cancels supported in-flight work.
- Keep the current user-facing switch while profile storage and safety paths are validated.

### C. Everyday UX

- Replace Cloud wording with Power across shell, settings, setup, recovery, and provider disclosures; preserve `PrivacyMode.CLOUD` and serialized keys for compatibility until a deliberate migration is justified.
- Add the two-section profile editor and clear default-on state for shipped Power capabilities.
- Add a first-enable explanation, persistent route/category indicator, concise capability setup guide, and content-free activity history.
- Ensure missing permissions/providers are explained at the affected feature while ordinary requests avoid repeated allowance confirmations.

### D. Migration and qualification

- Verify existing Private and Cloud users retain their selected mode and receive correct profile defaults.
- Verify independent profile edits across mode switches, process death, upgrade, corrupt profile, unavailable provider, and stale routing state.
- Verify each remote operation is blocked in Private and reported against the actual destination in Power.
- Walk the capability guide on supported Android versions, including denial, permanent denial, picker, and special-access paths.
- Keep local-model quality and inference qualification in Halo Lab/catalogue work; do not claim it from mode/profile tests.

## Acceptance criteria

- Fresh installs begin in Private. Existing installs retain Private or Cloud through migration; Cloud is shown as Power.
- Selecting Power is one clear broad sharing choice. All shipped, configured cloud activities are enabled by default without per-feature allowance lists or per-request confirmation dialogs.
- Private and Power provider/model/routing/context/feature choices are independently editable and persist through mode changes and restart.
- Private cannot dispatch remote AI, speech, web, image, screen, or workspace-context operations, including with stale Power configuration. Supported active remote operations cancel when switching to Private.
- All remote paths check mode/profile at the owner boundary. Missing provider/model/access never causes a hidden provider fallback.
- The shell and dispatch surfaces reveal the active mode and actual destination/data categories. A content-free activity receipt is available after each operation; reported costs are truthful and missing values are not shown as zero.
- Android capability setup is scoped to installed features, explains each grant, permits a guided setup pass, follows OS-controlled permission flows, and remains useful after denial. Power does not claim to grant device permissions.
- Existing Kids restrictions, explicit phone-action authority, user-configured spending limits, provider credentials, and Android permission state remain effective in both modes.
- Migration leaves conversations, provider credentials, model installations, and local workspace content unchanged.
- EN/LV/RU/PL strings and accessibility labels stay synchronized.

## Current implementation checkpoint — 2026-10-22

The current UI slice presents the legacy Cloud mode as **Power** in the persistent switch, privacy/settings summary, and mode-related recovery copy. Internal `PrivacyMode.CLOUD` and its stored key remain for compatibility. The current `PrivacyModeStore.set` still writes shared policy booleans and couples paid-media consent to the mode; provider/model/routing configuration is not yet isolated by profile. There is no complete capability setup guide or Power activity history.

This document is the target contract. The mode redesign is not complete until implementation and verification cover the acceptance criteria above. Current behavior and phone evidence: `docs/testing/HALO-CLOUD-PRIVACY-2026-10-22.md`.

## Source references

- Mode owner: `app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt`.
- Context policy: `app/src/main/java/com/halo/app/ai/RuntimeAiPolicy.kt`.
- Composition root: `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`.
- Mode UI: `app/src/main/java/com/halo/app/ui/components/PrivacyModeControl.kt` and `app/src/main/java/com/halo/app/ui/screens/settings/ContextPrivacyScreen.kt`.
- [Android runtime permission guidance](https://developer.android.com/training/permissions/requesting).
- [Google Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
