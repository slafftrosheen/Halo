# Tasks: PR-025 End-to-End Realization & Connectivity Fixes

- [x] **Network & Security**
    - [x] Update `network_security_config.xml` to permit cleartext for `10.0.2.2` and `127.0.0.1`
- [x] **Functional Pairing Flow**
    - [x] Create `PairingViewModel.kt` for production device management
    - [x] Create `PairingScreen.kt` for a user-friendly pairing experience
    - [x] Update `AppShell.kt` to use `PairingScreen` for the `"discovery"` route
- [x] **Settings Feedback & Navigation**
    - [x] Update `SettingsScreen.kt` to wire "Glasses" to Devices
    - [x] Implement "Coming Soon" dialog for dead settings (Privacy, Appearance, Voice)
- [x] **Home Screen Interaction**
    - [x] Wire State Orb click to `CognitiveSession.submit`
    - [x] Make status labels clickable for navigation (Intelligence, Wearable)
- [x] **AI Provider Cleanup**
    - [x] Verify `OllamaProvider.getStatus` and `health` logic
- [x] **Verification**
    - [x] Verify local Ollama connectivity
    - [x] Verify end-to-end pairing and interaction flow
