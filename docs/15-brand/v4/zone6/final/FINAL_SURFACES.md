# halo Zone 6 · Final surfaces (pack 7.0 · boards 22–26)

Baseline repo: `98aa2fb` (2026-11-20 18:30 +0200). Presentation only — every owner stays where it is.
Not yet built or tested on a device.

## 22 · Sheets from buttons (Format, Insert, Layers, More)
- One component: `HaloButtonSheet`. Rises from the dock button that opened it (notch points at it), ends **8dp above the dock**; the halo zone (bottom 25 %) is never covered.
- Scrim over the work area only; tap scrim / Back / close all dismiss. The opening button stays filled while open.
- Notes › Format: Title / Heading / Body / Small (each set in its own size), Bold · Italic · Highlight · Quote · Link · Code, Bullet / Numbered / Checklist.
- Notes › Insert: Checklist, Table, Image, Scan text, Voice memo, Link, Quote, **Draft with halo last** (suggestion only, never replaces text).
- Studio › Layers: thumbnail, name, kind; show/hide + per-layer More; Add layer, Reorder. Selected layer = mint outline.
- More list: Export as PNG (primary, mint icon) · Use in chat · Duplicate · Rename · Versions · **Delete… last, red text, never filled, confirms**.
- Motion: Full = spring 0.62 / 380 from the button; Gentle = 220 ms slide; Still = 150 ms fade.

## 23 · Canvas connect & Help & check-up
- Connect is a mode (dock: Cancel · **Done**). Hint banner: “Tap a card to connect it to …”. Dashed mint line follows the finger; target gets a dashed outline.
- After linking: optional label sheet — **No label first**, part of, leads to, inspired by, needs, Your own. Undo in the dock.
- Help & check-up (Places › Settings › Help): status hero, “How do I…” topics, Share a check-up report (no chats included).
- Check-up runs real tests only: test question (Brain), mic level (Hearing you), test phrase (voice), device test, source refresh state, free storage. Rows: passed (mint), running, waiting, **needs you (Apricot) with one named fix**. Last good copy is kept.

## 24 · Empty states — `HaloRingEmptyState`
Ring (gap at 1–2 o'clock) + one line icon + what will appear here + up to 3 starts. The filled dock button is the most likely start.
Library · Activity “All clear” · HaloMemory “asks first” · Widgets & sources “No sources yet — widgets stay empty rather than guess”.

## 25 · Offline & loading
- Offline is blue, never red: “No internet · halo answers on this phone”. Online-only actions read “… when online”.
- Every internet copy carries its time — same words as SourcesScreen: “Cached · may be out of date”, “Retrieved %1$s”.
- 400 ms rule (`DelayedSkeleton`): nothing for quick loads, skeleton after 400 ms, a sentence after 3 s.
- Long first-time work (`RealProgressCard`): bytes from the real download, Pause in the dock, Wi-Fi only by default.

## 26 · Refreshed features (new in 98aa2fb)
- **Account sign-in** → Brain › Online helper. States map `ChatGptAccountState`: not connected / connected + access / connected without access (Apricot, halo keeps using the phone). Dock: Sign in → (browser) → Fetch models; Disconnect… asks first and repeats the code's message if remote removal couldn't be confirmed.
- **Online voices** → Voice & reading › Online voices (`OpenRouterSpeechProvider`). Model + voice only from the live catalog; Play test phrase; cost line always visible; “Add your key in AI services” when not configured.
- **Full article text** → saved source item (`SavedArticleBody`): “Saved article text · Retrieved …”, partial capture line, “This image has not been saved.” Read aloud and Ask halo in the dock.
- Keys, endpoints and raw model ids stay in Advanced.

## Decisions taken as defaults (change if you disagree)
- The hub is named **Widgets & sources** (matches Settings in the code).
- Your day does **not** show a news line by default; it's one switch in the widget's settings.

## Acceptance
- No sheet, banner or state overlaps the bottom 25 %. All targets ≥ 48dp (Kids 56dp).
- Every “works / ready / all clear” is backed by a real check or real state; no invented data in empty or offline states.
- Delete / Forget / Disconnect never filled, always confirm. Still motion = fades only.
- TalkBack: sheets announce their title (paneTitle); hints and offline line are polite live regions; progress exposes range info.
