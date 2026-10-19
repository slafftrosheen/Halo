# halo — User-facing contract (finished app)
Applies to every screen, sheet, notification, widget, the assistant overlay and every future package (C–G).

## 1. One place for technical things
- **Settings is one list** for everyone: halo · Privacy · Everyday · Connections · Help. No Everyday/Advanced tabs.
- **Advanced is the last row of Settings.** It always contains:
  - Model roles
  - Custom servers (MCP)
  - Technical report (safe to share: no keys, no messages)
- **Developer tools** is a switch inside Advanced. It's off by default and turning it on asks for confirmation. When it's on, *Labs & diagnostics* appear inside Advanced and nowhere else: Runtime status, Workload diagnostics, Assistant diagnostics, Vision Lab, Speech Lab, Local audio test, and the phone and automation sandboxes.
- When it's off, those routes **refuse to open**, even from old links, notifications or search.
- Settings search covers user settings only. If nothing matches, it says "Technical options live in Advanced."

## 2. Never on a user screen
Internal IDs, provider/model IDs, run IDs, hashes, MAC addresses, endpoints, URLs typed by developers, tokens/keys, log lines, timing reports, model file names, quantisation and backend names, "probe", "diagnostics", "runtime", "interactor", or raw enum names (`DIRECT_ACTION`, `UNVERIFIED`).
→ Say it in words instead. For example: "Used Messages and Photos", "Everything stayed on this phone", "English listening pack", "halo can't see photos yet".

## 3. Flow rules
1. Every screen has **one obvious next step**. It's the one accent glow, used at most once per screen.
2. Every error offers **one fix** first. Other options are secondary.
3. Approvals happen in **one sheet** that names the action, on whatever screen you're on.
4. Nothing important is **more than two taps** from where you are.
5. **Honest states:** "Delivery not confirmed" for hand-offs, and "Did you hear halo?" before marking speech as working. Connected doesn't mean ready.
6. **The cloud is never preselected.** New permissions, payments and deleting are never suggested by the guide.
7. Kids mode: adult routes ask a grown-up first (unchanged).

## 4. Light and motion
The aura and edge light follow real state only (see `B/PACKAGE_B.md`, "2027 light layer"). Developer labs use their own quiet Lilac "Lab" style, so they can never be mistaken for the everyday app.

## 5. Engineering checklist: technical text found in today's source
| Screen (file) | Today | Finished app |
|---|---|---|
| Activity (`ActivityScreen.kt`) | "Provider turn started (providerId)", "Model reflection skipped", "State Changed" | Plain step names. Detail goes in the Technical report only |
| Android assistant (`AndroidAssistantScreen.kt`) | "VoiceInteractionService Declared", "Interactor", "Actual endpoint", "Role after return" | 4 human states (AS05). Details go in Advanced › Assistant diagnostics |
| Skills (`SkillsScreen.kt`) | "Direct Actions · N", "Functions discovered · N" | "12 actions halo can use in your apps" |
| System check (`SystemCheckScreen.kt`) | "speech probe", "Copy voice latency report", "Copy local AI diagnostics" | Check-up (ST04). The report moves to Advanced |
| Glasses (`HeyCyanControlScreen.kt`) | "Diagnostics" section | Battery, storage and connection described in words. Diagnostics move to Advanced |
| Integrations (`IntegrationsScreen.kt`, `McpServerScreen.kt`) | "Endpoint", "Bearer token" | Sign in to apps. Raw servers go in Advanced › Custom servers (MCP) |
| Intelligence (`ProviderControlScreens.kt`) | "System check and diagnostics" link | Removed. Check-up lives in Help |
| Settings (`SettingsScreen.kt`) | Everyday/Advanced tabs, version line in the Advanced tab | One list, Advanced row, version in About halo |

This checklist comes from reading the source. It isn't exhaustive. Before release, grep the strings for the banned words in section 2.
