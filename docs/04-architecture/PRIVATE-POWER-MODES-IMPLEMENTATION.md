# Private and Power modes — implementation brief

**Status:** Product direction for implementation. Existing `Private / Cloud` mode is the baseline; this document describes its replacement and the behavior the implementation must preserve.

## Product model

Halo has two first-class operating modes, visible in the app shell and in every place that can send data to a provider:

- **Private** keeps AI inference on this device and blocks remote AI, hosted speech, paid web lookup, image generation, and remote context transfer. Local files, notes, and device features continue to work within Android access already granted.
- **Power** is the former Cloud mode. It enables every supported online Halo capability and the full configured provider route without asking the user to approve each feature separately. Halo clearly names the provider or service receiving a request, shows the data categories being sent, and reports known provider charges or limits.

“Power” is a sharing and routing choice. It does not mean that Halo can grant Android permissions, ignore a child profile, execute sensitive phone actions without their existing approval, or promise unlimited third-party service. These boundaries remain enforced by their existing owners.

## Mode profiles

Each mode owns a separate, durable profile. Switching modes saves the profile being left and restores the one being entered. At minimum, profiles contain:

- Default provider and model, plus routing policy.
- Context categories and online capability preferences.
- Mode-specific feature choices (for example, web lookup and image generation).

Power starts with supported online capabilities enabled. Its profile can be tuned in one place; changes are reflected in a concise summary beside the mode switch. Private remains network-blocked even if its stored provider or feature selections are invalid or stale. Returning to either mode restores that mode's choices rather than rewriting the other profile. A missing or corrupt profile fails closed for Private and uses an explicit setup state for Power.

Do not persist credentials inside a mode profile. Continue to use the current secret storage. Provider/model availability is revalidated when the profile is activated; a missing provider or model produces an actionable status and never silently switches to an unannounced remote provider.

## User experience

1. Replace the visible **Cloud** label with **Power**. Keep internal identifiers and stored enum values stable during the first migration so existing installs keep their current mode.
2. Keep the mode control persistent. It states the active route in plain language: “Private · on this device” or “Power · sent to [provider/service]”. The compact shell may use a short label, but Settings and dispatch surfaces show the details.
3. Provide one **Mode profiles** destination with separate Private and Power sections. Do not scatter required allowances across feature screens. Power's defaults enable supported online activities; its profile summary lists enabled categories and known cost exposure.
4. Before a Power request leaves the device, show a lightweight, request-specific disclosure when the service, destination, data category, or cost behavior is not already clear from the current screen. Do not add a confirmation for every ordinary request after the user has enabled Power.
5. Keep a content-free activity history: time, service/provider, operation type, data categories, outcome, and charge information only when returned by the provider. Never store prompts, message bodies, images, audio, credentials, full URLs containing secrets, or fabricated cost estimates. If a provider does not return cost, show “cost not reported” rather than zero.

## Android permissions

Mode activation cannot grant system permissions. During first-run setup, offer one guided **Enable Halo capabilities** step that presents the permissions required by the currently shipped core features and can launch their OS requests in one session. Explain each permission and its feature before opening the OS prompt; let the user skip or decline any request and preserve a useful degraded path. Do not request permissions for features the installed build does not expose. Use Android's system picker when it can replace broad file/media access. Special permissions continue through their OS settings pages and require the user to grant them in Settings.

This removes repeated trips through Halo's own allowance lists. It does not imply that Halo can batch-grant system access or bypass Android's separate dialogs. The app must never claim that Power itself grants microphone, camera, location, Bluetooth, notification, file, or accessibility access. Android displays and controls those grants independently.

## Runtime and storage implementation

The current owner is `PrivacyModeStore` and `ManualDependencyGraph.setPrivacyMode`. The first implementation slice should:

1. Introduce a versioned mode-profile schema in app storage, with migration from `ai_privacy_mode_v1` and existing provider/model/routing/context keys. Migration preserves the currently selected mode and never changes a saved Private installation into Power.
2. Make profile activation atomic: snapshot the outgoing route/settings, apply the incoming route/settings, update effective context policy, then publish the new mode to observers. If applying the incoming profile fails, remain in Private and report the failure.
3. Add a dispatch-time mode gate shared by all remote provider and paid online operations. Private rejects before network dispatch and cancels cancellable in-flight remote operations when selected.
4. Remove mode switching's implicit mutation of an unrelated global routing preference. Power chooses its own provider/model/routing profile; Private cannot fall back to a remote provider.
5. Replace the user-facing “Cloud” wording with “Power” in the persistent control, settings, recovery copy, and disclosures. Preserve API/serialized identifiers until a deliberate migration is justified.
6. Add the profile editor and truthful, content-free Power activity receipt/history only after the mode gate and storage migration are in place.

## Existing behavior to retire carefully

The current `PrivacyModeStore.set` writes many `policy_*` booleans to `true` and ties paid-media consent to the global mode. `ContextPrivacyScreen` still describes the old one-switch contract, and switching to Cloud may reset `LOCAL_ONLY` routing. Replace these behaviors through the profile migration; do not create a second, competing policy store. Keep Kids restrictions, action confirmation, provider credentials, user-chosen cost limits, and Android permission state owned by their existing components.

## Acceptance criteria

- Fresh install starts in Private; an existing install retains its stored Private or Cloud mode through migration (Cloud is presented as Power).
- Private makes no remote AI, speech, web, image, screen, or workspace-context request, including with a stale Cloud provider/model selection. Switching to Private cancels supported cancellable requests.
- Power enables supported configured online activities by default without per-feature consent lists. Its provider/model/routing and category settings remain independently editable and survive app restart and mode switching.
- Switching modes preserves each profile and does not silently change the other profile. Unavailable selections are surfaced without hidden routing to a different remote service.
- Every online dispatch has a mode decision at the owner boundary. UI labels alone cannot authorize transmission.
- User can see the current processing path and inspect content-free activity records with provider/service, data categories, result and provider-reported cost where available.
- Existing Kids restrictions, explicit action authority, spending safeguards, and Android permission denials remain effective in both modes.
- Android permission requests are explained, optional, feature-relevant, denial-safe, and compatible with platform pickers and special-permission flows.
- EN/LV/RU/PL strings stay synchronized. Existing conversations, provider credentials, model installations, and local workspace content are unchanged by migration.

## Current source checkpoint — 2026-10-22

The first UI slice renames the visible Cloud mode to Power in the persistent switch, privacy/settings summary, and mode-related recovery copy. Internal `PrivacyMode.CLOUD` and its saved key remain unchanged for compatibility. EN/RU/LV/PL catalog entries were added and generated.

This slice does **not** implement separate provider/context profiles, the upfront capability permission guide, or the Power activity history. The current runtime remains the existing shared-profile implementation described above; do not claim the full mode redesign is complete until those items and the acceptance criteria are verified.

## Source and policy references

- Current behavior and verification baseline: `docs/testing/HALO-CLOUD-PRIVACY-2026-10-22.md`.
- Current mode owner: `app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt`.
- Current UI: `app/src/main/java/com/halo/app/ui/components/PrivacyModeControl.kt` and `app/src/main/java/com/halo/app/ui/screens/settings/ContextPrivacyScreen.kt`.
- Android runtime permission requests should be explained, tied to the feature, optional, and resilient to denial: [Android runtime permission guidance](https://developer.android.com/training/permissions/requesting).
- Google Play limits sensitive permissions and broad storage access to what the app needs for core functionality: [Play permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
