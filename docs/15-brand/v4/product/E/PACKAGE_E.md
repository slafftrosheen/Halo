# halo · Package E — how halo answers · models on the phone · local search · voice settings · privacy · permissions · guided setup
Brand Book v3.0 track · chapter 20 · repo pinned `3f217ccb` · 2026-10-19 · follows `../USER_FACING.md` and the light layer.
Covers **O01–O02, I01–I05, I07–I08, VO01–VO05, P01–P03** (17 screens). Model roles (I06), developer labs and the Speech Lab (VO06) stay in **Advanced** only.
Design status: designed. Implementation status: **not verified on a device**.

## Rules this package locks
1. **Plain names, honest fit.** Users see "Everyday answers", "Listening · accurate" and "Natural voices", each with size, languages and one fit badge: **Works well here · Not checked yet · Too big for this phone · You tested it · Damaged**. Engine and model names live in "About this model" or in Advanced.
2. **A test means a real result.** "Test with a real question" shows the actual answer and how long it took. "Connected" never counts as working. Speaking counts as working only after **"Did you hear halo?" → Yes**.
3. **Nothing downloads by itself.** Every download shows its size and Wi-Fi time, pauses when you leave Wi-Fi, and only shows a check after it has been verified. Removing a download says what halo will use instead.
4. **The cloud is never preselected.** Setup offers On this phone / A computer at home / A cloud service with nothing chosen, plus "Decide later" (limited mode). "Ask before sending to the cloud" is on by default.
5. **What left this phone** is a plain 30-day log: what was sent, to which service, and when. It can be cleared, with a confirm.
6. **Seeing photos and making images are separate switches,** because a service that understands photos may not make them.
7. **Permissions:** halo explains before Android asks, never changes a permission by itself, and sends blocked permissions to Android settings, re-checking on return.
8. **Search your Notebook works offline.** It's off until you turn it on, and it prepares notes only while the phone is idle or charging. Chats and memories are off by default. Phones that can't run it keep normal search.
9. **Setup follows the app's real 9 stages** (Welcome → Use → Permissions → Answers → Speech → Devices → Privacy → Ready → First). The microphone is the only permission asked during setup. "Save and leave" works anywhere. The last step is a real request, not a demo.

## Plain words (from today's source)
| Screen | Today | Finished app |
|---|---|---|
| Guided setup | Download Whisper Small · Download and select Supertonic 3 · Save setup diagnostics on this phone | Listening · accurate · Natural voices · (diagnostics → Advanced) |
| Voice settings | Install Silero VAD · Speech detector · Interruption timing · Automatic prefers installed Base, then Tiny. | Hands-free (included in the listening download) · Interrupt halo when I speak · Accurate / Light |
| Speech output | TTS model · Synthesis fallback keeps the selected audio output route. · Not verified during playback | halo's voice · "uses the phone's voice and tells you once" · Did you hear halo? |
| Model library | Model identifier · Model files and license · Routing | About this model (detail sheet) · routing → Advanced › Model roles |
| Intelligence | Where halo thinks · Conversation model | How halo answers · Choose a model |

## Folder
`screens/` 122 PNGs · `boards/` chapter 20 (21 pages) · `motion/` films: setup (dark + light), where halo thinks, voice settings · `spec/package_e.json` · `strings/` Package E keys (English final, LV/RU/PL keys ready).
