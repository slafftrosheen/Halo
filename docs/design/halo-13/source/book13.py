#!/usr/bin/env python3
"""halo brand book v8.0 — new pages: cover, Part II front matter, ch 28/29 openers, full ch 30 (halo 13.0).
Designed HTML pages 1920x1200 -> PNG (x1.25) -> JPEG -> raster PDF; merged with v7.0 + release books by merge.py."""
import html, json
E = html.escape
RING = lambda c='#0CF2BA', s=40, w=30: f'<svg viewBox="0 0 240 240" style="width:{s}px;height:{s}px;vertical-align:middle"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="{c}" stroke-width="{w}" stroke-linecap="round" fill="none"/></svg>'
START_PART2 = 348      # v8.0 page where Part II begins (v7.0 keeps pp 1–347; p1 becomes the new cover)
P28 = 350; P28N = 15   # opener + additions-book pp 43–57
P29 = P28 + 1 + P28N; P29N = 48  # opener + 12.0 book pp 3–50
P30 = P29 + 1 + P29N

B = {137:'Brief, repo check and the plan',138:'Plain words',139:'Simple, Standard, Pro',140:'Dynamic UI everywhere',141:'A wallpaper that follows the sky',142:'Icons, motion, files and code',
 143:'From 10 stages to 4 steps',144:'The four steps',145:'Ask when needed',146:'Kids and family hand-over',147:'Moving phones, comfort, when things go wrong',148:'Icons, motion, files and code',
 149:'Sources manager',150:'Sources at work',151:'Planner',152:'Adding and finding time',153:'Tablet, family and kids',154:'Icons, motion, files and code',
 155:'Canvas',156:'Drawing without a wall',157:'Studio',158:'Notes',159:'Translate and Scan',160:'Icons, motion, files and code',
 161:'Study',162:'Help and checking',163:'Cards and test plans',164:'Kids: home and learning',165:'Kids: homework, focus, grown-ups',166:'Icons, motion, files and code'}
CH = [
 ('1','Foundations: plain words, three depths, a screen that fits the moment',list(range(137,143)),
  'halo was too technical for many people. 13.0 rewrites what people read in everyday words, lets every screen show more or less (Simple, Standard, Pro), lets every screen adjust to the moment with a “Why?” behind each change, and gives the wallpaper a real sky.',
  ['A glossary of 30 words and a test that stops tech words from growing (553 today)','Simple · Standard · Pro, app-wide or per tool; halo offers, never switches','At most two changes each time a screen opens, with Keep · Reset · Stop','Six sky phases from real sunrise and sunset; the accent is checked for contrast']),
 ('2','Onboarding: four human steps',list(range(143,149)),
  'Ten technical setup stages become four questions: Hello, Who is halo for?, How much do you want to see?, Where should halo think? Every step has “Just open halo”. Permissions are asked when a feature is first used, never during onboarding.',
  ['A real answer on Home in under 60 seconds','Comfort settings first, applied live','One setup card a day from day two; dismissed twice, it is gone','A grown-up sets up a child behind the phone’s own lock, then hands it over']),
 ('3','Sources manager and Planner',list(range(149,155)),
  'Everything halo reads lives in one calm list, grouped the way people think, with a plain state and exactly what halo uses each source for. The Planner gets one switcher, a day you can drag tasks into, “Leave by”, adding in your own words and a calm “Find a time”.',
  ['11 technical states become 9 plain ones; red only when a source fails','No scores: Official · Public news · Science · Community · Yours','Only official warnings interrupt, at most 3 a day; kids-safe needs the PIN','Natural adding in English and Russian works with no AI brain; guesses in apricot']),
 ('4','The making tools: Canvas, Studio, Notes, Translate, Scan',list(range(155,161)),
  'Every making tool starts from what you are doing, names its tools by what they do, and keeps the full power one step away. Drawing no longer hits a wall: a full sheet quietly continues on the next one.',
  ['Canvas: four ways to start, five verbs, Tidy up that only suggests','Studio: “What are you making?”, plain names, smudge that never stops','Notes: five buttons, a tick line becomes a Planner task, drawings inside notes','Translate: hold to talk, face to face · Scan offers what fits the page']),
 ('5','Study and Kids mode',list(range(161,167)),
  'Study starts with one question and one path, Look → Try → Check → Done, with help as a ladder and checks that say where, never how badly. Kids mode has three big doors, a buddy who reads everything aloud and a grown-up area behind the phone’s own lock.',
  ['Seven rungs of help, nothing locked; results never in red','Again · Almost · Got it, and the day each card comes back','Age bands 4–6 · 7–9 · 10–12 size every screen','No points, scores, streaks or ranks, checked by a test in English and Russian']),
]
RU = [138,139,140,141,143,144,145,146,147,149,150,151,152,153,155,156,157,158,159,161,162,163,164,165]
STILLS = [('First open','Home after the four steps, suggestions fit “Studying” and “Creating”'),('Your depth','Simple drawing, and halo offering more tools once'),('Sources','A source that broke: what happened, what halo does, what you can do'),('Planner','“Swimming with Mia every Thursday at 5”: the guess is in apricot'),('Canvas','Tidy up suggests three groups; nothing moves yet'),('Study','A hint from the ladder, then “Almost there” in lilac'),('Kids','A wrong answer gets “look again”, never a buzzer')]

pages = []
def page(inner, cls=''): pages.append(f'<section class="pg {cls}">{inner}</section>')
def foot(n, label): return f'<div class="ft"><span>halo brand book v8.0 · {E(label)}</span><span>{n}</span></div>'

# ---- v8.0 cover (replaces v7.0 p1)
page(f'''<div class="cov"><div class="k">halo · brand book</div><h1>v8.0</h1><h2>Made for everyone</h2>
<p>The halo brand book, now through halo 13.0. Part I is the v7.0 book (chapters 1–27, unchanged). Part II adds halo 10.6 and 11.0, halo 12.0 and halo 13.0. Part II starts on page {START_PART2}.</p>
<div class="meta">23 November 2026 · replaces brand book v7.0 · checked against the halo repo at 99aac2f · design specification, not a build</div></div>
<div class="covring">{RING(s=760,w=26)}</div>''', 'dark')
cover = list(pages); pages.clear()

# ---- Part II front matter
n = START_PART2
toc = [('Part II · What’s new since v7.0', START_PART2), ('Chapter 28 · halo 10.6 and 11.0', P28), ('Chapter 29 · halo 12.0 · The interface comes to you', P29), ('Chapter 30 · halo 13.0 · Made for everyone', P30)]
c30 = []
q = P30 + 2
for c in CH: c30.append((f'30.{c[0]} · {c[1].split(":")[0]}', q)); q += 1 + len(c[2])
c30.append(('30.6 · Motion, prototype and films', q)); q += 3
c30.append(('30.7 · Russian boards', q)); q += 1 + len(RU)
c30.append(('30.8 · For the team · files', q))
rows = ''.join(f'<div class="tr"><span>{E(t)}</span><i></i><b>{p}</b></div>' for t, p in toc)
rows2 = ''.join(f'<div class="tr sm"><span>{E(t)}</span><i></i><b>{p}</b></div>' for t, p in c30)
page(f'<div class="hd"><div class="k">Part II · contents</div><h1>What’s new since v7.0</h1></div><div class="toc">{rows}<div style="height:26px"></div>{rows2}</div>'
     '<div class="side"><p>Part I (pages 2–347) is brand book v7.0 exactly as it was, with its own contents on page 2.</p><p>Chapters 28 and 29 reuse the pages of the 9.5–11.0 additions book and the 12.0 book. Those pages keep their original footers; the numbers on this page are the page numbers in v8.0.</p><p>Colour meanings never change: <b style="color:#0CF2BA">mint</b> for done, <b style="color:#FF9F6B">apricot</b> for needs you, <b style="color:#C9B8FF">lilac</b> for help, red only for a failed source.</p></div>' + foot(START_PART2, 'part II contents'))
sup = [('Dock','Home · Search · halo · Devices · Settings (10.5)','unchanged'),('First launch','9 screens (10.5) → 10 setup stages in code','4 steps + ask when needed (13.0)'),('Learning','Choose · Look · Try · Help · Keep (7.1)','Look → Try → Check → Done (13.0)'),('Pages','Fixed screens','Built for the job, with “Why?” (12.0); on every screen (13.0)'),('Tools','Easy / Standard / Pro (9.0)','Simple · Standard · Pro, app-wide or per tool (13.0)'),('Sources','Widgets & sources page (7.0)','One sources manager, 6 groups, plain states (13.0)'),('Drawing','64 or 256 lines, then a red limit','Sheets continue silently (13.0)'),('Library','Places tab, vault (10.x)','One Library, Private lock (11.0)')]
page('<div class="hd"><div class="k">What changed</div><h1>Where a later release replaces an earlier one</h1><p class="lead">When two releases disagree, the later one wins. Earlier pages stay as they were, so Part I still shows some older screens.</p></div><div class="sup"><div class="sr h"><span>Area</span><span>Before</span><span>Now</span></div>'
     + ''.join(f'<div class="sr"><span>{E(a)}</span><span>{E(b)}</span><span>{E(c)}</span></div>' for a, b, c in sup) + '</div>' + foot(START_PART2 + 1, 'what changed'))
page(f'<div class="chn">Chapter 28</div><div class="hd"><h1 class="big">halo 10.6 and 11.0</h1><p class="lead">The clickable Kids and Study prototype with Russian text (10.6), then livelier Kids, the homework helper, the Study photo coach, one Library, halo maps, Today built from blocks and six new tools (11.0). The next {P28N} pages are taken from the 9.5–11.0 additions book, pages 43–57.</p></div>'
     '<ul class="bul"><li>Homework helper: hints, never the final answer for kids</li><li>Study photo coach: “Coach me” by default</li><li>One Library: Today · Library · Tools · Reads</li><li>halo maps: Leave by, turn by turn, sharing asks first</li></ul>' + foot(P28, 'chapter 28'), 'dark')
page(f'<div class="chn">Chapter 29</div><div class="hd"><h1 class="big">halo 12.0 · The interface comes to you</h1><p class="lead">Pages built for the job from halo’s own parts, a drawing engine with real brushes and vectors, every tool as real software, and Kids & Study 12. The next {P29N} pages are the 12.0 book, pages 3–50.</p></div>'
     '<ul class="bul"><li>24 blocks in 6 families, a “Why?” on every block</li><li>36 brushes, vector paths, live boxes, precise drawing</li><li>One shell for 17 tools: seven menus in the same order</li><li>A world that grows · live homework coach · living exam plan</li></ul>' + foot(P29, 'chapter 29'), 'dark')
front2 = list(pages); pages.clear()

# ---- Chapter 30
p = P30
page(f'<div class="chn">Chapter 30</div><div class="hd"><h1 class="big" style="font-size:120px">halo 13.0<br>Made for everyone</h1><p class="lead">Anyone should be able to use halo without help. 13.0 keeps every power feature but hides it until someone needs it: plain words, three depths, four onboarding steps, one sources manager, a real planner, gentler making tools, and Study and Kids without points.</p></div>'
     f'<div class="covring" style="top:330px;right:-60px;opacity:.55">{RING(s=640,w=26)}</div>' + foot(p, 'chapter 30'), 'dark'); p += 1
rules = [('Plain words','Everyday words on Simple and Standard. Pro keeps exact terms.'),('Your depth','Simple, Standard or Pro, for the app or per tool. halo offers at most once in 14 days.'),('Ask when needed','No permission before its feature. halo explains first, then Android asks.'),('No scores','Sources get kinds, not ratings. Study and Kids get no points, streaks or ranks.'),('Never a dead end','Every error says what happened, what halo did and what you can do.'),('Kids stay kids','Always Simple. Hints, not answers. Grown-ups behind the phone’s own lock.')]
stats = [('30','boards, 137–166'),('24','boards in Russian'),('117','new icons'),('25','Lottie animations × 2 themes'),('297','strings in English and Russian'),('7','clickable journeys')]
page('<div class="hd"><div class="k">30 · overview</div><h1>Six promises for 13.0</h1></div><div class="grid3">' + ''.join(f'<div class="card"><b>{a}</b><p>{b}</p></div>' for a, b in rules) + '</div>'
     + '<div class="stats">' + ''.join(f'<div class="stt"><b>{a}</b><span>{E(b)}</span></div>' for a, b in stats) + '</div>' + foot(p, '13.0 overview')); p += 1
for num, title, boards, lead, bul in CH:
    thumbs = ''.join(f'<div class="th"><img src="a/{b}.png"><span>{b} · {E(B[b])}</span></div>' for b in boards)
    page(f'<div class="chn">30.{num} · Batch {num}</div><div class="hd"><h1 class="big">{E(title)}</h1><p class="lead">{E(lead)}</p></div>'
         '<ul class="bul">' + ''.join(f'<li>{E(x)}</li>' for x in bul) + f'</ul><div class="thumbs">{thumbs}</div>' + foot(p, f'13.0 · batch {num}'), 'dark'); p += 1
    for b in boards:
        page(f'<img class="full" src="a/{b}.png"><div class="pn">{p}</div>', 'img'); p += 1
# 30.6
sheets = [('onboarding_dark_sheet','Onboarding'),('sources_planner_dark_sheet','Sources + Planner'),('making_tools_dark_sheet','Making tools'),('study_kids_dark_sheet','Study + Kids'),('lottie_dark_sheet','Foundations')]
page('<div class="hd"><div class="k">30.6 · motion</div><h1>25 small animations, one rule: motion explains a change</h1><p class="lead">Each batch adds five Lottie animations in dark and light, 256 px at 30 fps, in res/raw. Still mode shows the last frame.</p></div><div class="msh">'
     + ''.join(f'<div><img src="a/{f}.png"><span>{E(t)}</span></div>' for f, t in sheets) + '</div>' + foot(p, 'motion')); p += 1
page('<div class="chn">30.6 · prototype and films</div><div class="hd"><h1 class="big">Tap through 13.0</h1><p class="lead">A clickable prototype of seven 13.0 journeys, in English and Russian, with a Still mode and a demo that plays itself. Two films record the demo in each language. The drawing journey draws for real.</p></div>'
     '<ul class="bul"><li>Open prototype/halo13.html in any browser · add ?lang=ru for Russian</li><li>?flow=1…7 opens one journey · ?auto=all plays them all</li><li>Films: halo13-prototype-en.mp4 and halo13-prototype-ru.mp4</li><li>Not a build: nothing here was tested on a phone</li></ul>' + foot(p, 'prototype'), 'dark'); p += 1
st = ''.join(f'<div class="st"><img src="a/still_en_{i+1}.png"><b>{i+1} · {E(a)}</b><span>{E(b)}</span></div>' for i, (a, b) in enumerate(STILLS))
page(f'<div class="hd"><div class="k">prototype · seven journeys</div><h1>What each journey shows</h1></div><div class="stills">{st}</div>' + foot(p, 'prototype')); p += 1
# 30.7
stru = ''.join(f'<div class="st"><img src="a/still_ru_{i+1}.png"></div>' for i in range(7))
page(f'<div class="chn">30.7 · Russian</div><div class="hd"><h1 class="big">Russian boards</h1><p class="lead">The 24 boards people see, with every label in Russian. Adults are addressed with «вы», children with «ты», “buddy” is «бадди», and halo and tool names stay in Latin letters. “Points” is «баллы», never «очки», which also means glasses. Code, names and the five developer boards stay in English.</p></div>'
     f'<div class="stills ru">{stru}</div><p class="warn">Not yet reviewed by a native Russian speaker.</p>' + foot(p, 'russian'), 'dark'); p += 1
for b in RU:
    page(f'<img class="full" src="a/ru{b}.png"><div class="pn">{p}</div>', 'img'); p += 1
# 30.8
page('<div class="chn">30.8 · For the team</div><div class="hd"><h1 class="big">Decisions, reviews and checks</h1><p class="lead">Nothing in 13.0 has been built as Android screens or tested on a device. The pure Kotlin rules compile on their own and their tests pass. These are the questions to answer before it ships.</p></div>' + foot(p, 'for the team'), 'dark'); p += 1
dec = ['Which sources are safe for kids’ topics','Age bands 4–6 · 7–9 · 10–12, tried with real families','Which graphics system draws brushes (from 12.0)','Whether brushes can be shared, including in Kids','Tablet menus at the top or the bottom','Monitor mode · RAW decoder','Latvian and Polish text for 13.0 (13.0 is English and Russian only)']
rev = ['Security: moving from an old phone (code, 3 words, local network)','Privacy: homework photos and the camera on glasses','Privacy: spoken rehearsal recordings','Privacy: Safe places']
chk = ['Android build of all five batches; screens are not built yet','Tests: planner 6/6, making tools 6/6, Study + Kids 7/7 (pure Kotlin)','Russian needs a native speaker; check the label for “points”','Boards were checked automatically for cut-off text only','Text halo writes on the fly needs checking by eye','Speeds, sizes and times on boards are examples']
col = lambda t, c, xs: f'<div class="tcol"><div class="lab" style="color:{c}">{t}</div>' + ''.join(f'<div class="ti" style="border-color:{c}">{E(x)}</div>' for x in xs) + '</div>'
page('<div class="hd"><div class="k">open items</div><h1>What the team decides</h1></div><div class="cols3">' + col('Decisions', '#C0612B', dec) + col('Reviews', '#7B62C9', rev) + col('Builds and checks', '#0a8a6a', chk) + '</div>' + foot(p, 'open items')); p += 1
gl = [('Simple · Standard · Pro','Просто · Стандарт · Профи'),('Just open halo','Просто открыть halo'),('How much to show','Сколько показывать'),('Why?','Почему?'),('Keep · Reset · Stop changes','Оставить · Сбросить · Хватит'),('Needs you','Нужны вы'),('Sources','Источники'),('Kids-safe','Для детей'),('Leave by','Выйти в'),('Find a time','Найти время'),('Tidy up','Навести порядок'),('Sheet','Лист'),('Look · Try · Check · Done','Смотрю · Пробую · Проверяю · Готово'),('Again · Almost · Got it','Ещё раз · Почти · Знаю'),('points','баллы (never «очки»)'),('buddy · Fox','бадди · Лис'),('grown-up','взрослый'),('Hand back','Вернуть телефон')]
page('<div class="hd"><div class="k">Russian glossary · 13.0</div><h1>The words that must stay the same</h1></div><div class="gl">' + ''.join(f'<div class="gr"><span>{E(a)}</span><b>{E(b)}</b></div>' for a, b in gl) + '</div>' + foot(p, 'glossary')); p += 1
tree = '''START_HERE.md              (brand book v8.0 PDF is a separate download)
docs/design/halo-13/        FOUNDATIONS_13 · ONBOARDING_13 · SOURCES_PLANNER_13 · MAKING_TOOLS_13 · STUDY_KIDS_13 · BATCH1–6
  boards/en/ · boards/ru/   137–166 (30) · 24 boards in Russian
  motion/                   Lottie sheets and previews
  prototype/                halo13.html + css/js/strings · fonts · icons
  films/                    halo13-prototype-en.mp4 · halo13-prototype-ru.mp4
  source/                   board source (t13*.js) · ru13.json · fit.js · book13.py
app/src/main/java/com/halo/app/ui/{plain,adaptive/v13,onboarding/v13,sources/v13,planner/v13,
  canvas/v13,studio/v13,notes/v13,translate/v13,scan/v13,study/v13,kids/v13,theme/v13}/ · wallpaper/v13/
app/src/test/…/v13/        5 test files (pure Kotlin)
app/src/main/assets/       glossary · surfaces · looks · ask-when-needed · learning-13 …
app/src/main/res/          values{,-ru}/strings_*_13.xml (297 each) · drawable/ 117 icons · raw/ 50 Lottie files'''
page(f'<div class="hd"><div class="k">the pack</div><h1>Where everything lands in the repo</h1><p class="lead">One folder shaped like the halo repo. Copy it over the repo root: new files only, nothing existing is overwritten. The Kotlin files are reference code to review, not drop-in screens.</p></div><pre class="tree">{E(tree)}</pre>' + foot(p, 'files')); p += 1
page(f'<div class="cov" style="padding-top:330px;text-align:center"><div style="display:flex;justify-content:center">{RING(s=220,w=26)}</div><h2 style="margin-top:40px">halo · made for everyone</h2><p style="margin:24px auto 0">Brand book v8.0 · {p} pages · 23 November 2026</p></div>', 'dark')
ch30 = list(pages)

CSS = open('/tmp/w13/bookcss.css').read()
for name, pp in [('cover', cover), ('front2', front2), ('ch30', ch30)]:
    open(f'/tmp/w13/book/{name}.html', 'w').write(f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body>{"".join(pp)}</body></html>')
json.dump({'P28': P28, 'P29': P29, 'P30': P30, 'last': p, 'cover': len(cover), 'front2': len(front2), 'ch30': len(ch30)}, open('/tmp/w13/book/plan.json', 'w'))
print('cover', len(cover), 'front2', len(front2), 'ch30', len(ch30), 'P30', P30, 'last page', p)
