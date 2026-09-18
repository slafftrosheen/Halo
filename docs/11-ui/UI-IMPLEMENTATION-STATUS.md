# UI source inventory

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.
Route/source presence is not interaction, visual or accessibility acceptance.

[AppShell](../../app/src/main/java/com/halo/app/ui/shell/AppShell.kt) registers:

| Route | Surface |
|---|---|
| home | HomeScreen |
| activity | ActivityScreen |
| notebook | NotebookScreen |
| notebook/note/{noteId} | NoteEditorScreen |
| devices | DevicesScreen |
| device/{deviceId} | DeviceKind dispatch to HeyCyanControlScreen/BuddyBotControlScreen |
| settings | SettingsScreen |
| ai_settings | IntelligenceScreen |
| voice_settings | VoiceSettingsScreen |
| permissions | PermissionsScreen |
| discovery | PairingScreen |

Notebook now has a persistent NB-01 notes route with quick creation, local lexical search,
Inbox/archive/trash views and an autosaving plain-text/Markdown editor. Memory, Working Set,
files, conversations and Canvas remain proposed in the
[Notebook plan](../02-product/NOTEBOOK-DELIVERY.md); the initial route does not imply those flows.

Console, Protocol Lab and Runtime Inspector are not registered destinations.
HeyCyanDiscoveryScreen exists separately; the discovery route above uses PairingScreen.
A probe/diagnostic class, label or old screen is not a reachable page.

The orb has Canvas shapes, pixel-ring motion, colors and touch animation, but no measured
audio/task-progress input. Previous “complete/no placeholders” labels have been removed.

Acceptance still needs action/back behavior, empty/loading/offline/denied/error/stale states,
large text, TalkBack, reduced motion, recreation, frame timing and physical workflow checks.
