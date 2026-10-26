# Halo Interface 4.0 — Android integration contract

Version 2 · 2026-10-25 · authority: GitHub #62. This interaction contract succeeds the tab/rail and tap-to-disclose recommendations in BrandBook 3.0 / DevKit 2.0. The existing Open Ring identity, tokens, typography, state artwork and screen specifications remain authoritative. Implementation and physical qualification are separate.

## Current shell contract

Normal portrait reserves approximately the upper three quarters for work and the lower quarter for the Halo Zone. Content measurement, keyboard, short windows and large fonts take precedence over a rigid percentage. The zone owns exactly four stationary destinations: Home, Workspace, Devices and Settings. Its bell opens Activities/Notifications; the legacy activity route and run IDs remain valid. Local/Power projects the existing Private/CLOUD profile authority, never an additional grant.

The visible Orb is enlarged to 96dp in normal portrait. A bounded, manually scrolled contextual dial exposes only the current page's action lease; release snaps, dragging never invokes an action, and an explicit full list exposes the same current-page actions. Main-shell global destinations stay on the four fixed roots, bell and their proper suites; they are not duplicated in that list. The fixed navigation, bell and profile never participate in contextual rotation. Compact and large-text layouts use the nonoverlapping Orb/list and fixed navigation fallback. If an exceptionally tall IME leaves insufficient height for original touch targets, the entire footer has a status-inset-safe vertical viewport; controls keep their order and sizes and remain scroll-accessible. Keyboard Back restores normal space. The earlier root-destination fan is superseded inside the shell; standalone hosts keep their compatibility presentation.

The upper zone utility band is a navigation-entry-scoped composition projection. Home owns its composer and Chat/Screen switch; nested tools own title/back and essential options; Activity owns its filters. Disposal and exact entry checks reject outgoing-page projections. A projection grants no context read, permission, command authority or persisted mutation. Shared ToolRegistry and domain owners remain authoritative.

Home defaults to live following, pauses when the user inspects history, retains per-conversation read position and restores following on explicit submission/return. Workspace opens Library directly, with non-destructive Office Tools and Halo Studio navigation groupings. Settings exposes Brain/Voice/Vision peers and keeps developer gating intact. Existing object IDs and data stores do not migrate.

Decorative Halo/Mist backgrounds are procedural assets, distinct from the interactive Orb. Animation stops outside RESUMED, when the screen is off, under battery saver/thermal limits, reduced motion or high contrast. Still is a persisted option. Appearance preview remains local until durable Apply; light/dark choices stay independent. New accents and surfaces extend existing saved enum values.

The paragraphs below retain compatibility and safety context; the current shell contract above supersedes the former fan, footer size and top-overflow placement. Physical touch, visual quality, speech and sustained resource acceptance remain separately evidenced.

The applicable Android shell owns one bottom-centered Orb, compact Private/Power control and accessible menu. Its content is inset above system navigation and IME. Tap starts speech; while capturing it finalizes speech for recognition; during preparation/processing/work it cancels; speaking supports interruption; pending review opens the existing Activity/interaction owners. No animation starts capture or dispatch. Long press reveals a bounded labeled fan; swipe up and the explicit adjacent menu open all destinations/actions. Reduced motion, TalkBack, large text, short screens and IME use the scrollable list. Back closes disclosure before leaving the page. Satellite entries are navigation or existing canonical catalog affordances, never hidden tool execution.

Explicit root destinations open that root rather than restoring a saved detail above it. Other existing restoration callers retain their child-stack behavior. Route changes preserve the process-owned HomeViewModel/CognitiveSession. The host stops microphone ownership when the Activity actually backgrounds, and skips configuration-change cancellation. Rotation does not replay a mutation. Mini uses one adapted local presentation backed by the same owners; onboarding retains its explicit setup controls. Kids projects/lessons retain their exact scoped draft/stop owner projected into the shell, rather than routing an answer into an unrelated conversation. Ordinary field dictation remains an editor control.

Private/Power reflects the existing durable profiles. Its compact button reuses the original sharing disclosure and Kids gates. Power grants no microphone permission, paid-media authority or destructive-action consent. Model/provider absence must not prevent independent navigation or editor controls.

High-frequency Save/Send/Stop, attachments, Canvas drawing commands and Undo/Redo stay directly reachable. Rare page actions use the existing editor menu or screen-owned Halo Zone overflow. All legacy route/data IDs remain unchanged. `screen` is an exact assistant/discovery alias for Home's existing Screen surface, not a second dashboard. Parameterized object routes still require real IDs from existing owners. The explicit “Use this page as context” action reads a fresh canonical revision on every selection and cancels with its page; mere visibility does not attach content.

Evidence and migration inventory:

- `docs/testing/INTERFACE-4-SCREEN-PARITY.md`: original controls, retained callbacks and page action migrations.
- `docs/testing/INTERFACE-4-ASSISTANT-PARITY.md`: text/STT-final routes, canonical functional tools, revision guards and protected exceptions.
- `docs/testing/INTERFACE-4-MOTION-CATALOG.md`: all active Lottie families, static fallback and lifecycle policies.
- [Integrated build and physical verification](../../testing/INTERFACE-4-VERIFICATION.md): exact fingerprints, commands, outcomes and qualification limits.
- `scripts/verification/inventory_interface4.py`: reproducible baseline/current route and callback index. An index is not evidence of semantic or visual parity.

No physical screenshots, spoken accuracy, TalkBack, measured energy or complete 360/412dp/200%/landscape/tablet acceptance can be inferred from this contract or a local compile. Record their actual status in the integrated verification report before issue closure.
