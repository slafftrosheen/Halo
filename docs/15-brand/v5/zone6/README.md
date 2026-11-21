# halo Zone 6 · Calm Dock, alive — FINAL UPGRADE PACK 8.0 (21 Nov 2026) · start with docs/FINAL_UPGRADE_8.0.md

Drop-in add-on for DevKit 2.0: unzip as `zone6/` next to `product/`. Brand Book v4.0 (final) = v3.0 (224 pp, unchanged) + Chapter 23 (pp 225–260).

- `docs/ZONE6_HANDOFF.md` — rules, anatomy, states, pages, motion, widgets, build order, acceptance, open decisions
- `tokens/halo_z6_tokens.json` — layout, semantics, ring captions, places, motion, haptics, reduced motion
- `compose/HaloDock.kt` — reference Compose (HaloDock, island states, visible nav, Places sheet, edge light) mapped to Zone 5 owners
- `lottie/` — 8 motions × dark/light (validated in lottie-web; ring gap verified at 45°) · `static/` end-frame SVGs for reduced motion
- `boards/` — 00 audit … 09 widget sizes & states
- `films/` — halo-z6-tour.mp4 (41 s, every page) · halo-z6-motion-states.mp4 (26 s, one task start → finish)
- `source/` — editable HTML/CSS/JS of every board, film and the Lottie generator (lot.py); open rboard.html?b=1..4, mboard.html?b=1..2, tour.html

Screen is removed: its organiser, sources, news, clocks and brief live in home-screen widgets; in-app hub = Places › Widgets.

6.1 adds: boards 10 first-time guide + 11 bigger screens, `prototype/proto.html` (clickable), films/halo-z6-prototype-walkthrough.mp4, handoff §13–15.

6.2 adds: `widgets-build/` — drop-in RemoteViews layouts (all ids kept), drawables, colours, strings, Zone6WidgetFace.kt + 2 small patches, WIDGETS_BUILD.md. Book v3.3: contents page now lists Chapter 23.

6.3 adds: boards 12–15 inner pages (Settings inside, devices & sources, Workspace & Activity inner, setup & Kids), handoff §16. Book v3.4 pp 240–243 + contents updated.

6.4 adds: `look/` — Make it yours (6 looks, colours, icon styles Line/Spot/Tile, app icon follows the look: 12 adaptive icons + Classic alias + manifest + HaloAppIconSwitcher, motion Full/Gentle/Still, haptics, edge light, labels), HaloLook.kt, 33 strings, LOOK_BUILD.md; boards 16–18; films/halo-make-it-yours.mp4. Book v3.5 pp 244–246.

6.5 adds: `assistant/` — Android assistant island over any app (all runtime states, Apricot app confirmations, numbered pick options, problem state) + mini halo (ring bubble states, compact window), AssistantIsland.kt, 19 lowercase strings, ASSISTANT_MINI.md; boards 19–21; films/halo-assistant-mini.mp4. Book v3.6 pp 247–249. Baseline repo @98aa2fb.

7.0 (final) adds: `final/` — sheets from buttons (Format, Insert, Layers, More), Canvas connect + link labels, Help & check-up, empty / offline / loading states, refreshed-repo features (account sign-in in Brain, online voices in Voice & reading, full article text); HaloFinalSurfaces.kt, 51 strings, FINAL_SURFACES.md; boards 22–26; handoff §17. Book v4.0 pp 250–254. Baseline repo @98aa2fb.

Start here: docs/ZONE6_HANDOFF.md → boards/ (00–26) → prototype/proto.html → compose/, look/, assistant/, widgets-build/, final/.

7.1 adds: `learn/` — Study + Kids rethought as one path (Choose · Look · Try · Help · Keep) in three sizes (Pictures, Simple, Full) for any age; LEARN.md (every step, motion table, acceptance), LearnPath.kt, strings; boards 27–32; films/halo-learn-one-path.mp4. Book v4.1 pp 255–260.

7.2 adds: `alive/` — Make it yours, alive: guided customisation (“Do it with me”, six plain-word tiles, Dock & buttons with one swappable slot, Put back how it was), six live wallpapers (Halo Drift, Aurora, Tide, Day Arc, Constellation, Paper) that follow your look, the time of day and halo’s state, widget motion with one meaning per change, a lilac guidance layer (Show me, Watch then try, one-time tips, Looking for something?), Easy / Standard / Compact views and “Set up for someone else” + Keep this layout. ALIVE.md, HaloLiveWallpaper.kt (WallpaperService), HaloWidgetMotion.kt, MakeItYours.kt, HaloGuide.kt, anims, wallpaper xml + manifest snippet, 87 strings; boards 33–38; films/halo-make-it-yours-alive.mp4 (52 s); source/aboard.html?b=33..38, alivefilm.html, alive.js (wallpaper painters, same maths as the Kotlin). Book v4.2 pp 261–266. Not built or tested on a phone.

7.3 adds: `alive/pages/` — the alive layer on every page and tool: Edit this page, Show me, Watch-then-try, Easy view and tours for Home, Workspace, Activity, Places, Library, HaloMemory, Widgets & sources, Devices, Settings (find a setting by saying it), Help & check-up (Show me around tours), HaloStudio, HaloNotes, HaloCanvas (board as a list), HaloStudy, assistant island, mini halo and Kids (sky picker + grown-up settings). PAGES.md, HaloPageLayer.kt, strings_pages.xml; boards 39–46 (39 = the page map); source/pboard.html?b=39..46 + pages.js. Book v4.3 pp 267–274. Not built or tested on a phone.

7.4 adds (halo 2027, quietly ahead): `fixes/` — app always opens (Classic launcher alias + safe icon switcher with repair), Classic icon ring gap moved to 45°, lowercase names (EN/RU), FIXES.md. `y2027/` — HaloTaskPlan (plans, hard stops, receipts), HaloLiveUpdate (status-bar chip, lock screen, segmented progress), HaloNextForYou (one suggestion with reasons, 3 levels), HaloTrust (origin chips, pause, private chat), HaloFollowMe (watch/glasses routing), strings_2027.xml, Y2027.md. Boards 47–52. Source: source/y27board.html?b=47..52. Brand book v4.4 (281 pp): contents updated, p 225 = full Chapter 23 index, pp 275–281 new. Still not built or tested on a phone.

7.5 adds (design & UX only, no code): 4 new tools — HaloScan, HaloPlanner, HaloRecord, HaloSheets — and tools working together + your tool shelf; 13 new widgets in 4 sets; 6 more live wallpapers (12 total); 4 functions — Focus, Routines, Family space, Magnifier. Boards 53–58, explained in `docs/TOOLKIT_7.5_UX.md`. Source: source/t5board.html?b=53..58. Brand book v4.5 (288 pp): pp 282–288 new, contents + p 225 index updated.

8.0 adds: boards 59–64 (Easy & Kids versions of the new tools, HaloAlbum, HaloVault, smarter widgets, 18 wallpapers, real-life functions, halo at a glance), docs/FINAL_UPGRADE_8.0.md, UX spec §12–17. Source: source/t6board.html?b=59..64. Brand book v5.0 (295 pp).
