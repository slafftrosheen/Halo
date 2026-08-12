# AI Provider Abstraction and Android Assistant Integration

This plan introduces a formal `AiProvider` abstraction to the Halo Cognitive Runtime and implements the first integration with the system's default Android Assistant. This allows Halo to leverage on-device or cloud AI capabilities (like Gemini or GPT) without hardcoding specific providers, while maintaining a clear privacy boundary.

## User Review Required

> [!IMPORTANT]
> This change introduces a new module `:providers:android-assistant`. Ensure that your environment supports adding new Gradle modules.
> The Android Assistant integration relies on `ACTION_ASSIST` and contextual data sharing. Users will need to have a default assistant app configured on their device for this to work effectively.

## Proposed Changes

### [Component] AI Abstraction Layer

We will define the core types for multimodal AI requests and responses in the `provider-sdk`.

#### [MODIFY] [AI.kt](file:///C:/Users/slavd/Halo/sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AI.kt)
- Add `AiCapability` enum.
- Add `AiRequest` and `AiResponse` (multimodal).
- Add `AiProvider` interface.

#### [NEW] [AiProviderRouter.kt](file:///C:/Users/slavd/Halo/engines/ai/src/main/java/com/halo/engines/ai/AiProviderRouter.kt)
- Implement a router that selects the appropriate provider based on requested capabilities and user configuration.

---

### [Component] Android Assistant Provider

Implement the `AndroidAssistantProvider` which uses Android system APIs to interact with the configured assistant.

#### [NEW] [:providers:android-assistant]
- Create a new module for the Android Assistant provider.
- Implement `AndroidAssistantProvider` using `Context` and Android Intents.

---

### [Component] AI Engine Integration

Update the `AIEngine` to use the new `AiProvider` system.

#### [MODIFY] [AIEngine.kt](file:///C:/Users/slavd/Halo/engines/ai/src/main/java/com/halo/engines/ai/AIEngine.kt)
- Update `AIEngine` to hold a reference to `AiProviderRouter`.
- Implement `reason()`, `summarize()`, and `generate()` using the router.

---

### [Component] UI: AI Settings & Status

Add a dedicated settings screen for AI providers and update the home screen status.

#### [NEW] [AiSettingsScreen.kt](file:///C:/Users/slavd/Halo/app/src/main/java/com/halo/app/ui/screens/settings/AiSettingsScreen.kt)
- A screen to list available AI providers and their capabilities.
- Allow users to select the primary AI provider.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/slavd/Halo/app/src/main/java/com/halo/app/ui/screens/settings/SettingsScreen.kt)
- Make the "Assistant" item clickable.
- Navigate to the `AiSettingsScreen`.

#### [MODIFY] [AppShell.kt](file:///C:/Users/slavd/Halo/app/src/main/java/com/halo/app/ui/shell/AppShell.kt)
- Register the new `AiSettings` route.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/slavd/Halo/app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt)
- Update the status area to show the current AI provider's state.

## Verification Plan

### Automated Tests
- Unit tests for `AiProviderRouter` to verify correct provider selection.
- Mock tests for `AndroidAssistantProvider` to verify intent generation.

### Manual Verification
- Deploy to an Android device.
- Open Settings -> Assistant.
- Verify that "Phone Assistant" is listed and selectable.
- Trigger a reasoning task (e.g., through a debug button or voice) and verify that it invokes the Android Assistant or reports the result correctly.
- Verify that the Home screen correctly displays the selected AI provider.
