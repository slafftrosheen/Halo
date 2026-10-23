# Private and Full Power modes — product and implementation specification

**Status:** Approved direction for implementation. This specification expands the existing Private / Cloud switch into two independently configurable processing profiles and one clear sharing choice. The current implementation is a compatibility baseline, not completion of this design.

## Product decision and language

Use **Private** and **Full Power** as the user-facing mode names. Full Power replaces Cloud and makes the choice feel decisive while its adjacent explanation stays candid: Halo may use configured online providers and supported cloud features, and selected data may leave this phone. Do not imply system-level or unrestricted authority. Android, provider, family, cost, and action-safety boundaries remain visible and effective.

The central promise is simple: **choose how Halo may process requests once; tune each mode in one place; always be able to see where a request is going.** Selecting Full Power enables every shipped and configured cloud-capable Halo activity by default. It must not open a sequence of Halo-made feature allowance screens or ask again before each ordinary request. The user can tune the Full Power profile in one place and return to Private at any time.

Mode is the top-level data-processing choice. A profile is the configuration used while that mode is active. Android permission grants, provider credentials, parental restrictions, action approvals, and spending controls remain distinct state owned by their existing systems.

## Mode behavior

| | Private | Full Power |
|---|---|---|
| AI processing | On-device only | Configured online provider/model, with local fallback only when the profile explicitly permits it |
| Cloud-capable Halo features | Off; no remote dispatch | All shipped and configured cloud features enabled by default |
| Context | Private profile's local context choices | Full Power profile's independently chosen categories; eligible cloud categories on by default |
| Provider, model, routing | Independent Private selection | Independent Full Power selection |
| Android permissions | Existing grants still govern local features | Same grants govern local and device features; mode cannot grant them |
| Cost and action controls | Existing controls still apply | Existing controls still apply; mode is not spending or action approval |

Full Power's default-on list covers the cloud-capable functionality actually shipped in the installed build: remote conversation and model tools, hosted speech, web search/fetch, image understanding and generation, and eligible screen, audio/video, memory, and workspace context. This is a default for Halo's cloud-sharing profile, not a claim that every operation will succeed: each feature still needs an available configured provider/model and any required Android access. No unavailable provider is silently substituted. New cloud-capable features inherit the enabled default only after their destination, data categories, and cost behavior are added to the profile disclosure and activity receipt contract.

Private is a hard dispatch boundary, not merely a model preference. It blocks remote AI, hosted speech, paid web operations, cloud image work, and remote context transfer, even when a stale provider or feature setting remains in storage. Local notes, files selected through Android pickers, local models, and device features continue subject to their own grants.

## One sharing choice, two tunable profiles

Keep one persistent Private / Full Power control in the app shell. Choosing Full Power is the user's broad authorization for configured online routes and the Full Power profile's enabled data categories. Do not require a separate Halo allowance tap for each ordinary cloud feature or each request. Changing an optional category in the profile editor affects Full Power only; it never changes Private.

Provide one **Mode profiles** destination with two visibly separate panels:

- **Private profile:** local model and routing, local context categories, and local-only feature behavior.
- **Full Power profile:** provider/model and routing, cloud context categories, and shipped online feature switches. Every supported online feature starts enabled when a profile is first created. Users can turn off individual categories here without affecting Private. The editor is available as a single optional place to tune behavior, not a checklist required to start using Halo.

Mode changes save and restore the corresponding profile; editing or activating one must never rewrite the other. The shell summarizes the active profile in plain language, such as **“Private · processed on this device”** or **“Full Power · requests may go to OpenRouter”**. Details identify the selected provider, enabled online capabilities, data categories, and whether costs may be billed by that provider.

Profiles do not contain API keys or other credentials. Continue using the existing secret store. Credentials, provider outages, unsupported models, disconnected network, and provider quotas get actionable setup/recovery states; none cause an undisclosed route change.

## Transparent activity without repeated approvals

Keep the processing path visible beside the composer and on any feature surface that can dispatch remotely. Before the first request after enabling Full Power, explain that prompts and the selected context categories can leave the device, identify the configured service, and say that provider charges may apply. This is part of the one-time mode choice, not an extra feature allowance.

For ordinary subsequent requests, show a compact, inspectable disclosure such as **“Sending text + selected workspace context to OpenRouter”**. Do not block every send with a confirmation dialog. If the active provider, destination, or data category changes, update the disclosure before dispatch and make the change obvious in the UI.

Add a Full Power activity history with content-free receipts: time, provider/service, operation type, data categories, result, and provider-reported cost when available. Never save prompts, message bodies, images, audio, credentials, signed URLs, or guessed costs. If the provider returns no cost, show **“cost not reported.”** Give users a clear way to clear this local history.

Full Power enables cloud-capable features; it does not invent unlimited free service. Show known provider billing behavior and preserve existing user-selected limits and provider quotas. Do not silently add a new Halo-imposed spending approval step to every request. Mode selection must never set or imply paid-media, purchase, or spending consent. If a user-configured limit or provider quota blocks work, explain which limit stopped it and where that limit is controlled.

## Android access setup: one guided pass, OS-controlled grants

At first-run setup, offer one **Set up Halo capabilities** flow that inventories Android runtime permissions and special access required by features present in this build. Explain each capability and why it needs access, then let the user request all applicable access from this single guided pass. Launch supported runtime prompts in a clear sequence, recording granted, declined, or not-yet-asked status. Include settings-bound grants and Android pickers as clearly identified steps, even though the OS may require a separate screen or user-selected item. Group and order requests sensibly; never issue a system prompt before explaining it. Do not make this setup a prerequisite for typed questions or Private-mode core use.

Android does not expose a universal API for Halo to grant every permission or show every system prompt in one dialog. The guided pass should request every applicable runtime permission supported by the OS in sequence and offer a clear next step for settings-bound access; Android controls each final prompt and decision. Halo must not imply that selecting Full Power grants microphone, camera, location, Bluetooth, notifications, files, accessibility, or other system access. Respect denial and “don't ask again”; record the actual state, keep a useful degraded route, and let users revisit the guide. Request a permission only when the installed app has a real feature that uses it. Prefer Android's document/media pickers over broad storage access. If a later app update adds a permission, surface that new capability in the same guide instead of silently requesting unrelated access.

This guide replaces scattered Halo allowance lists, including the current hidden bulk request for a hard-coded skills/tools permission bundle. It does not combine OS permission state with cloud-sharing authority: Android access lets a feature read/capture locally; Full Power determines whether eligible content may be sent to a provider. A denied OS permission does not turn off Full Power or trigger a repeated Halo prompt; the affected feature reports the exact missing access and offers the guide/settings route.

## Runtime and storage design

The current mode owner is `PrivacyModeStore`, composed through `ManualDependencyGraph`; context settings are read through `RuntimeAiPolicy`. Extend these owners rather than adding a competing policy store.

1. **Versioned profiles.** Add a versioned schema with distinct Private and Full Power records for provider/model/routing, context categories, and online feature choices. Keep credentials in the current secret storage. Migrate `ai_privacy_mode_v1` and existing provider/model/routing/context keys transactionally. Preserve the selected mode; a saved Private install must never migrate into Full Power. Map legacy shared preferences explicitly and document every default.
2. **Safe activation.** Resolve and validate the target profile before publishing a mode change. Persist the outgoing profile, prepare the incoming route and effective context policy, then publish the new mode. A failed or corrupt profile falls back to Private with an actionable error. Do not expose a partially activated Full Power state.
3. **Owner-boundary gate.** Create a shared authorization decision at remote AI, speech, web, image, and remote-context dispatch owners. A UI label is not authority. Private rejects before dispatch and cancels supported cancellable requests when activated. The check must use the current mode/profile at dispatch time, not a stale UI snapshot.
4. **Routing isolation.** Remove mode switching's mutation of unrelated global routing preferences. Each profile resolves only its own provider/model/routing choice. A missing selection reports setup needed; it never silently switches to another remote service.
5. **Consent ownership cleanup.** Do not treat Full Power selection as Android permission, parental permission, phone-action confirmation, provider credential, or arbitrary spending-limit consent. Decouple paid-media consent from the global mode; Full Power may enable paid online capabilities, while configured spending controls and provider rules continue to apply.
6. **Observable receipts.** After profiles and dispatch gates are reliable, add content-free activity receipts at the actual dispatch/outcome owners. Record only metadata explicitly listed above and only provider-reported costs.
7. **Capability setup.** Replace scattered requests and the bulk skills/tools bundle with the build-aware Android capability guide after auditing the manifest, runtime requests, special app access, and feature ownership. Offer all access applicable to this build in one guided pass; model each requirement as granted, declined, needs system settings/picker, or not requested. Keep optional permissions optional for core chat and do not request broad access without a shipped feature owner.

## Implementation sequence

### A. Inventory and contract

- Enumerate every online dispatch owner and every permission request used by features in the current build.
- Establish a mode/capability matrix and map current persisted keys to Private or Full Power defaults.
- Add migration and owner-boundary coverage before moving screens to the new profile model.

### B. Profiles and dispatch enforcement

- Add schema, transactional migration, isolated profile reads/writes, safe activation, and dispatch gates.
- Ensure Private blocks all remote paths and mode switch cancels supported in-flight work.
- Keep the current user-facing switch while profile storage and safety paths are validated.

### C. Everyday UX

- Replace Cloud/Power wording with Full Power across shell, settings, setup, recovery, and provider disclosures; preserve `PrivacyMode.CLOUD` and serialized keys for compatibility until a deliberate migration is justified.
- Add the two-section profile editor and clear default-on state for shipped Full Power capabilities.
- Add a first-enable explanation, persistent route/category indicator, one-pass build-aware capability setup guide, and content-free activity history.
- Ensure missing permissions/providers are explained at the affected feature while ordinary requests avoid repeated allowance confirmations.

### D. Migration and qualification

- Verify existing Private and Cloud users retain their selected mode and receive correct profile defaults.
- Verify independent profile edits across mode switches, process death, upgrade, corrupt profile, unavailable provider, and stale routing state.
- Verify each remote operation is blocked in Private and reported against the actual destination in Full Power.
- Walk the capability guide on supported Android versions, including denial, permanent denial, picker, and special-access paths.
- Keep local-model quality and inference qualification in Halo Lab/catalogue work; do not claim it from mode/profile tests.

## Acceptance criteria

- Fresh installs begin in Private. Existing installs retain Private or Cloud through migration; Cloud is shown as Full Power.
- Selecting Full Power is one clear broad sharing choice. All shipped, configured cloud activities are enabled by default without per-feature allowance lists or per-request confirmation dialogs.
- Private and Full Power provider/model/routing/context/feature choices are independently editable and persist through mode changes and restart.
- Private cannot dispatch remote AI, speech, web, image, screen, or workspace-context operations, including with stale Full Power configuration. Supported active remote operations cancel when switching to Private.
- All remote paths check mode/profile at the owner boundary. Missing provider/model/access never causes a hidden provider fallback.
- The shell and dispatch surfaces reveal the active mode and actual destination/data categories. A content-free activity receipt is available after each operation; reported costs are truthful and missing values are not shown as zero.
- Android capability setup inventories the installed build, explains every grant, lets users request all applicable runtime access in one guided pass, identifies settings-bound steps, follows OS-controlled permission flows, and remains useful after denial. Full Power does not claim to grant device permissions.
- Existing Kids restrictions, explicit phone-action authority, user-configured spending limits, provider credentials, and Android permission state remain effective in both modes.
- Migration leaves conversations, provider credentials, model installations, and local workspace content unchanged.
- EN/LV/RU/PL strings and accessibility labels stay synchronized.

## Current implementation checkpoint — 2026-10-23

Issue #47 implements independent versioned profiles, atomic migration and activation,
current-policy HTTP/WebView dispatch leases, first-enable sharing disclosure, profile
editing, distinct spending consent and bounded content-free activity receipts. Turn
provenance retains source categories and the original revision through inference,
tools, approval recovery, native account handoff and ordinary/streaming speech.
Legacy `PrivacyMode.CLOUD` and stored keys remain compatible; user-facing text says
**Full Power**. Issue #48 delivered the separate build-aware Android capability guide.

This specification remains the target contract. Verification and limitations are
recorded in [the issue #47 report](../testing/PRIVATE-POWER-047-VERIFICATION-2026-10-23.md).
Final device reruns, live-provider qualification and other hardware-dependent
acceptance stay explicit in issue #43; source completion does not establish them.
Explicit public model installation and OS account authorization retain their setup
owners; Private is a Halo processing-dispatch boundary, not a system-wide firewall.

## Source references

- Mode owner: `app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt`.
- Context policy: `app/src/main/java/com/halo/app/ai/RuntimeAiPolicy.kt`.
- Composition root: `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`.
- Mode UI: `app/src/main/java/com/halo/app/ui/components/PrivacyModeControl.kt` and `app/src/main/java/com/halo/app/ui/screens/settings/ContextPrivacyScreen.kt`.
- [Android runtime permission guidance](https://developer.android.com/training/permissions/requesting).
- [Google Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
