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
Inbox/archive/trash views and an autosaving plain-text/Markdown editor with recovery drafts.
Its hierarchy, pastel action signals, compact item rows, editor toolbar and status treatment use
the root `HaloDesignMockup.png` as visual direction without exposing its unsupported controls.
Memory, Working Set,
files, conversations and Canvas remain proposed in the
[Notebook plan](../02-product/NOTEBOOK-DELIVERY.md); the initial route does not imply those flows.

Console, Protocol Lab and Runtime Inspector are not registered destinations.
HeyCyanDiscoveryScreen exists separately; the discovery route above uses PairingScreen.
A probe/diagnostic class, label or old screen is not a reachable page.

Halo Presence now uses an asymmetric rounded-cell renderer keyed to real HaloRuntimeState transitions.\nIt intentionally avoids mechanical rings, radial gauges, faces and permanent idle animation. Activity,\nNotebook and primary navigation reuse compact cell-signal marks. Measured audio/output envelope input is\nnot wired yet, and the component's reduced-motion boundary is not yet connected to Android accessibility.

Acceptance still needs action/back behavior, empty/loading/offline/denied/error/stale states,
large text, TalkBack, reduced motion, recreation, frame timing and physical workflow checks.


## 2026-09-18 workspace correction

The latest main-line correction restores Home keyboard submission, streaming/completed response
presentation, voice start/stop/interruption and session cancellation under the new workspace
visual language. Notebook exposes only implemented NB-01 controls: create note, lexical search,
Inbox/archive/trash filters and persisted note editing. Devices no longer shows sample saved
hardware or dead quick actions. Settings exposes routed configuration pages plus a working persisted System/Light/Dark appearance selector.

Light and dark color schemes are implemented through HaloTheme. AppearancePreferences persists
System/Light/Dark selection and MainActivity applies it at the app root; System remains the default.


## Secondary-surface migration

Intelligence, Voice, Permissions and Pairing now use shared workspace primitives rather than
legacy Halo Glass page/card components. Startup error/retry surfaces also use the new workspace
button/typography treatment. HeyCyanControlScreen and BuddyBotControlScreen remain the main
reachable legacy-styled surfaces and are the next migration target.
