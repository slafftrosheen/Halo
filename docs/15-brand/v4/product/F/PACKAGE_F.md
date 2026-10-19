# halo · Package F — devices · glasses · BuddyBot · pairing · this phone · audio · halo nearby · skills · apps & services · routines · notification settings
Brand Book v3.0 track · chapter 21 · repo pinned `3f217ccb` · 2026-10-19 · follows `../USER_FACING.md` and the light layer.
Covers **D01–D08, C01–C03, C05, N03** (13 screens). C04 Android assistant is AS05 (Package B). Custom MCP servers live in Advanced.
Design status: designed. Implementation status: **not verified on a device**.

## Rules this package locks
1. **Hardware status ladder:** Saved → Connecting… → Connected · getting ready → **Ready** (sound and camera actually checked) → In use → Needs a look. A Bluetooth link, a saved profile or a discovered device is never shown as Ready.
2. **Pairing:** nothing connects until you tap. You choose by signal strength and the code printed on the arm. The code check appears only if the glasses ask for it. Steps: Found → Linked → Checking sound and camera → Ready. Every failure gives one concrete fix and "Search again".
3. **Glasses:** capture stays disabled until the glasses are Ready. Photos stay on the glasses until they're copied, with real progress shown. Forget asks for confirmation and keeps copied photos.
4. **BuddyBot safety:** "Hold to start driving" arms it on purpose. Letting go stops it. A large high-contrast **STOP** is always visible and overrides everything. If something is in the way, BuddyBot stops by itself (Apricot). On a fault, driving turns off and the cause is explained in plain words (red).
5. **Audio never surprises.** If headphones or glasses disconnect, halo pauses instead of jumping to the loudspeaker. Outputs you've tested say "You heard halo here".
6. **This phone** shows what really works right now, tested where possible. Anything that's off has one fix.
7. **halo nearby:**
   - Mini halo (a small window), the floating orb (needs "Display over other apps", asked in context) and the home-screen widget. All three are hidden on the lock screen by default.
   - If the launcher won't let halo add the widget, the screen explains how to add it by hand.
8. **Apps & services:** you sign in on the service's own page, and halo never sees the password. Every action has its own switch, and **connected never means approved**: anything that sends, buys or deletes always asks first.
9. **Routines:**
   - **Builder:** When · Only if · Do · Tell me (Screen / notification / nothing). "Try it once" shows the result before you save.
   - **Suggested routines** explain why they were suggested and need you to turn them on.
   - **History** lists every run. A failed run keeps the previous result.
   - **On/off without deleting.**

## Plain words (from today's source)
| Screen | Today | Finished app |
|---|---|---|
| Glasses | AI thumbnail detail · Capture retention · Ephemeral · Diagnostics · Reset audio association | Plain capture buttons · "stays on glasses until copied" · diagnostics → Advanced · Repair sound |
| BuddyBot | Local WebSocket endpoint · WebSocket URL · Safety Supervisor remains authoritative… | Find on Wi-Fi (address secondary) · "STOP always works" |
| Devices | Communication route · Identity available for this connection only | Status ladder: Saved → Connected → Ready |
| Skills | Risk · … · Source · Custom · schema N · MCP tools · N · Import SKILL.md | Actions for each app, each with its ask rule · MCP and imports → Advanced |
| Integrations | Endpoint · Bearer token · MCP · Trust | Sign in with the service · custom servers → Advanced |

## Folder
`screens/` 100 PNGs · `boards/` chapter 21 (17 pages) · `motion/` films: glasses pairing (dark + light), BuddyBot and audio, routines and apps · `spec/package_f.json` · `strings/` Package F keys (English final, other languages ready).
