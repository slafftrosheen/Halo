/* halo 12.0 · batch 1 — foundations + halo builds the interface (boards 117–120) */
const I=(n,st='')=>`<svg class="i" viewBox="0 0 24 24" fill="none" style="${st}">${(window.IC||{})[n]||''}</svg>`;
const RING=(c='#0CF2BA',s=38,w=30)=>`<svg class="ring" viewBox="0 0 240 240" style="width:${s}px;height:${s}px"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="${c}" stroke-width="${w}" stroke-linecap="round" fill="none"/></svg>`;
const WM=`<div class="wm">hal${RING('#0CF2BA',30)}</div>`;
const head=(k,t,p)=>`<div class="bh"><div><div class="k">halo · 12.0 · ${k}</div><h1>${t}</h1><p>${p}</p></div>${WM}</div>`;
const notes=n=>`<div class="notes">${n.map(([h,p])=>`<div class="note"><b>${h}</b><p>${p}</p></div>`).join('')}</div>`;
const SB=`<div class="sb"><span>9:41</span><span class="isl"></span><span class="r">${I('signal')}${I('bluetooth')}${I('battery')}</span></div>`;
const DOCK=(kid)=>`<div class="dock">${I('home')}${I('search')}${RING('#0CF2BA',kid?44:38)}${I('devices')}${I('settings')}</div>`;
const phone=(inner,o={})=>`<div class="ph ${o.cls||''}"><div class="sc">${SB}<div class="body" style="${o.bst||''}">${inner}</div>${o.ctl?`<div class="q-ctl">${o.ctl}</div>`:''}${o.over||''}${o.nodock?'':DOCK(o.cls=='kid')}</div></div>`;
const P=(inner,label,small,o)=>`<div class="pcol">${phone(inner,o)}<div class="pl">${label}<small>${small||''}</small></div></div>`;
const blk=(ic,t,body,o={})=>`<div class="q-blk ${o.cls||''}" style="${o.st||''}"><h4>${I(ic)}${t}${o.r?`<span style="margin-left:auto">${o.r}</span>`:''}</h4>${body}${o.sel?HD:''}</div>`;
const HD=['left:-5px;top:-5px','right:-5px;top:-5px','left:-5px;bottom:-5px','right:-5px;bottom:-5px'].map(s=>`<i class="q-hd" style="${s}"></i>`).join('');
const chip=(t,c='',ic='')=>`<span class="q-chip ${c}">${ic?I(ic):''}${t}</span>`;
const made=`<span class="q-made">${I('ai_draft')}made for you</span>`;
const chk=(t,d)=>`<div class="q-check"><i class="${d?'d':''}"></i>${t}</div>`;
const tag=(t,st,c='')=>`<span class="q-tag ${c}" style="${st}">${t}</span>`;

const B={};

/* ───────── 117 · halo 12 rethought for 2027 + repo sync ───────── */
B['117']=()=>`<div class="boardpg">${head('the 2027 rethink','halo 12 — the interface comes to you','In 2027, people won’t go looking for the right screen. They’ll say what they want, and halo will put together a screen for the job. That screen uses halo’s own parts, follows halo’s rules, and stays fully under your control. This release also turns every tool into proper software: real brushes, vector drawing, precise drawing and boxes you can resize. It arrives in five batches, each checked against the halo repo.')}
<div class="q-grid3" style="grid-template-columns:1.05fr 1fr 1fr">
<div class="q-col"><h2>${I('code')}What the repo has today</h2>
<div class="lab" style="margin-bottom:6px">github · slafftrosheen/Halo · c267a2e · 22 Nov 2026</div>
${[['check','<b>8.0 design applied.</b> Album with on-device faces, Record, Global Pause, Follow Me, planner parts, wallpapers and Kids sky picker.'],
['check','<b>Drawing exists.</b> A canvas drawing surface supports draw, erase, pan and select, with pen pressure, tilt and remote halo ink.'],
['check','<b>halo studio has layers.</b> Image, drawing, text, rectangle and ellipse layers, with opacity, rotation, mask and 16-step undo.'],
['check','<b>Study tutor ladder.</b> ask → hint → explain → analogy → check → part → solution, and the solution only on request.'],
['check','<b>Kids.</b> Grown-up gate, homework photo review, story reader, voice dock and create flow.']].map(([i,t])=>`<div class="q-li2">${I(i)}<div>${t}</div></div>`).join('')}
</div>
<div class="q-col"><h2>${I('review')}Gaps 12.0 closes</h2>
<div class="lab" style="margin-bottom:6px">found in the code</div>
${[['A stroke stores only the input device (finger, pen, mouse).','<b>No brush, colour or width per stroke.</b> Every line looks the same.'],
['A drawing is capped at 256 strokes and 16,384 points.','<b>Too small for illustration.</b> One hair study can use more than that.'],
['Shapes are just rectangles and ellipses inside a box.','<b>No paths, corners, nodes or exact sizes.</b> You can’t draw a vector logo or a floor plan.'],
['Layer sizes are thousandths of the page, with no units.','<b>No mm, cm, px or pt.</b> Precise drawing needs real units and snapping.'],
['Screens are fixed layouts.','<b>Nothing adapts to the task.</b> The design for 9.0–11.0 is still waiting in the backlog.']].map(([a,b])=>`<div class="q-li2"><i class="q-dot" style="background:var(--ap)"></i><div>${b}<br><span style="color:var(--t3)">${a}</span></div></div>`).join('')}
</div>
<div class="q-col"><h2>${I('guide_me')}Six rules for 2027</h2>
<div class="q-grid3" style="grid-template-columns:1fr 1fr;gap:10px">
${[['Intent first','You say what you’re doing. halo brings the tools for it.'],
['halo suggests, you decide','Every screen halo makes can be edited, pinned or undone, and a “Why?” shows its reasons.'],
['Anchors never move','The dock, back button, title and halo ring stay in the same place on every screen halo makes.'],
['On the phone first','halo puts layouts together on the phone. Nothing goes to the cloud to design a screen.'],
['Pen, voice, touch, glasses','Every tool works with each of them. None of them is the “real” way.'],
['Calm by default','No flashing. The Gentle and Still versions stay. Colour meanings stay the same.']].map(([b,p],i)=>`<div class="q-prin"><div class="n">0${i+1}</div><b>${b}</b><p>${p}</p></div>`).join('')}
</div></div></div>
<div style="margin-top:30px"><div class="lab" style="margin-bottom:10px">halo 12.0 in five batches · each one is checked against the repo before it ships</div>
<div class="q-batch">${[['Batch 1','Foundations + halo builds the interface','Repo check, 2027 rules, “made for you” pages, the parts list and the rules halo builds with, plus Kids and Study.',1],
['Batch 2','Drawing engine','Real brushes (pressure, tilt, texture), vector paths with nodes, resizable boxes with exact values, precise drawing with units and snapping.'],
['Batch 3','Every tool as real software','A full workspace for each of the 17 tools: menus, panels, shortcuts and files, plus about 120 new instruments.'],
['Batch 4','Kids & Study 12','Adventure pages that change with the child, live homework coach and drawing lessons, plus adult study plans.'],
['Batch 5','Book + one pack','The 12.0 book, Russian text, a clickable prototype, films and a repo-shaped folder of developer files.']].map(([n,b,p,on])=>`<div class="q-bt ${on?'on':''}"><div class="n">${n}${on?' · now':''}</div><b>${b}</b><p>${p}</p></div>`).join('')}</div></div>
<div style="margin-top:26px"><div class="lab" style="margin-bottom:8px">Where 12.0 lands in the repo · the pack mirrors these paths</div>
<table class="q-tbl"><tr><th>Area</th><th>Owner in the code today</th><th>12.0 adds</th><th>Batch</th></tr>
${[['Pages halo builds','<code>ui/screens/home/HomeStartLayout.kt</code> · <code>HomeNextForYou.kt</code>','<code>ui/adaptive/</code> page plan, block catalogue, check, renderer','1'],
['Drawing + brushes','<code>sdk/runtime-sdk/…/ink/InkDocument.kt</code> · <code>CanvasInkSurface.kt</code>','brush on each stroke, colour, width, texture; higher limits via ink schema 3','2'],
['Vector, boxes, precise drawing','<code>data/studio/StudioDocument.kt</code> (<code>StudioLayerKind</code>)','PATH, FRAME, LINE and DIMENSION layers, real units, constraints, snapping','2'],
['Tool workspaces','<code>ui/workspace/Studio*.kt</code> · <code>ui/toolkit/Halo*Screen.kt</code>','menus, panels, shortcuts, file formats per tool','3'],
['Kids + Study','<code>ui/screens/kids/*</code> · <code>ui/screens/study/*</code> · TutorStage','adventure pages, live coach, drawing lessons','4']].map(r=>`<tr><td><b>${r[0]}</b></td><td>${r[1]}</td><td>${r[2]}</td><td>${r[3]}</td></tr>`).join('')}</table></div>
</div>`;

/* ───────── 118 · made for you — halo builds the screen ───────── */
const p118a=`<div class="q-top">${I('back')}<b>halo</b>${I('more')}</div>
<div class="q-say">Plan Saturday with the kids. It might rain and we have about 40 euros.</div>
<div class="q-think">
<div><i class="d"></i>Checked the weather · rain 11:00–15:00</div>
<div><i class="d"></i>Found 6 indoor places within 25 min</div>
<div><i class="d"></i>Matched to Mia (7) and Leo (4)</div>
<div><i class="w"></i>Building your page…</div></div>
<div class="q-skel" style="height:34px"></div><div class="q-row"><div class="q-skel"></div><div class="q-skel"></div></div><div class="q-skel" style="height:70px"></div>`;
const p118b=`<div class="q-top">${I('back')}<b>Rainy Saturday</b>${made}</div>
${blk('cloud','Weather','<p>Rain 11–15 · dry morning · 9°</p>',{r:'<span style="font:600 11px Atk;color:var(--sky)">☂ 80%</span>'})}
<div class="q-row">${blk('kids_mode','Science museum','<div class="q-mini" style="background-image:url(img/leaf.jpg)"></div><p style="margin-top:4px">18 min · €24 family</p>')}${blk('kids_mode','Climbing hall','<div class="q-mini" style="background-image:url(img/market.jpg)"></div><p style="margin-top:4px">12 min · Leo 4+ ok</p>')}</div>
${blk('calendar','Your day','<p>09:30 park (dry) · 11:00 museum · 13:00 lunch · 15:30 home</p>')}
${blk('task','Bring','<div style="display:flex;gap:10px">'+chk('Raincoats')+chk('Snacks',1)+chk('Leo’s socks')+'</div>')}`;
const p118c=`<div class="q-top">${I('back')}<b>Rainy Saturday</b>${made}</div>
${blk('cloud','Weather','<p>Rain 11–15 · dry morning · 9°</p>')}
${blk('kids_mode','Climbing hall','<div class="q-mini" style="background-image:url(img/market.jpg);height:72px"></div><p style="margin-top:4px">12 min · Leo 4+ ok · €16</p>',{cls:'sel',sel:1,st:'margin-top:12px'})}
<div style="display:flex;flex-wrap:wrap;gap:2px;margin:2px 0 8px">${chip('Bigger','','zoom_in')}${chip('Swap','','retry')}${chip('Less like this','l')}${chip('Why?','l')}</div>
${blk('calendar','Your day','<p>09:30 park · 11:00 climbing · 13:00 lunch</p>',{cls:'ghost'})}`;
const p118d=`<div class="q-top">${I('back')}<b>Library</b>${I('search')}</div>
<div style="display:flex;gap:5px;margin-bottom:8px">${chip('Today')}${chip('Library','m')}${chip('Tools')}${chip('Reads')}</div>
<div class="lab" style="margin:4px 0 6px">Pages you made</div>
${blk('grid_view','Rainy Saturday','<p>Weekend · 5 blocks · checks the weather again on Saturday morning</p>',{r:chip('Pinned','m')})}
${blk('grid_view','Leo’s bedtime','<p>Every day 19:30 · story, teeth timer, light</p>')}
${blk('grid_view','Flat move checklist','<p>Project · 9 blocks · 3 people</p>')}
<div class="lab" style="margin:8px 0 6px">Save as</div>
<div style="display:flex;flex-wrap:wrap">${chip('Page','m','check')}${chip('Widget','','widget')}${chip('Routine','','routines')}</div>`;
B['118']=()=>`<div class="boardpg">${head('made for you','halo builds the screen for the job','Say what you need and halo builds a page for it in the top three quarters of the screen. It uses blocks from halo’s own parts list. You can move, resize, swap or delete any of them. A page you like becomes yours: you can pin it, keep it in the Library, make it a widget or turn it into a routine. The dock, back button, title and halo ring stay where they always are.')}
<div class="row" style="justify-content:space-between">
${P(p118a,'1 · Ask','halo says out loud what it is checking. Grey placeholders show where the page will go.',{ctl:`<div class="q-comp">${I('mic')}Listening… <span style="margin-left:auto">${I('stop')}</span></div>`})}
${P(p118b,'2 · The page appears','The blocks fade in one at a time, top to bottom, with no flashing. A “made for you” badge sits by the title.',{ctl:`<div style="display:flex;gap:5px">${chip('Pin page','m','favorite')}${chip('Edit','','select')}${chip('Share','','send')}</div><div class="q-comp">${I('mic')}Change anything…</div>`})}
${P(p118c,'3 · Make it yours','Long-press a block to get its handles, then drag or resize it. You can also just say “bigger”, “swap it” or “not this”.',{ctl:`<div style="display:flex;gap:5px">${chip('Undo','','undo')}${chip('Done','m','check')}${chip('Reset page','')}</div><div class="q-comp">${I('mic')}“Swap climbing for the pool”</div>`,over:tag('drag · resize · swap','left:150px;top:168px')})}
${P(p118d,'4 · It lives in your Library','A saved page keeps itself up to date: it checks the weather again on the day, and halo asks before booking or paying.',{ctl:`<div class="q-comp">${I('search')}Find a page…</div>`})}
</div>
${notes([['What halo can change','Only the work area: the blocks, their order and their size. It never touches the dock, the back button, permissions or the menu for paying, sending or deleting.'],
['What halo learns','Pins, “Less like this” and resizes are kept on the phone, per person. You can see and clear them under Settings → Privacy → Pages halo made.'],
['Why?','Every block can explain itself: “Climbing hall: 12 min away, open in the rain, fine for age 4+.” The sources are listed and can be tapped.'],
['When halo is unsure','It shows two layouts side by side and asks “Which helps more?” It never guesses silently with money, times or people.']])}
</div>`;

/* ───────── 119 · the grammar ───────── */
const FAM=[['Read','var(--sky)',[['text','Text','Plain or rich text, read aloud'],['source','Source card','Article, link, file, with where it came from'],['image','Gallery','1–12 pictures, swipe'],['explain','Explainer','Step by step, can be folded']]],
['Do','var(--mint)',[['task','Checklist','Ticks, owner, due'],['timer','Timer','Countdown or stopwatch'],['approval','Ask-first action','Pay · send · book · post, always asks first'],['routines','Steps','Ordered steps with progress']]],
['Choose','var(--lil)',[['multi_select','Options','2–4 cards (Kids max 3)'],['filter','Compare','Side by side, same facts'],['sort','Rank','Drag to order'],['quiz','Question','One question, coach mode']]],
['Make','var(--sun)',[['draw','Sketch','Drawing area with real brushes (batch 2)'],['new_note','Note','Write, voice to text'],['shapes','Board','Boxes, arrows, sticky notes'],['create_image','Image','Generate or edit, asks first']]],
['See','var(--rose)',[['calendar','Timeline','Day, week or plan'],['grid_view','Table','Rows and columns, sum, sort'],['study','Chart','Bar, line, pie, from the data'],['signal','Map','Place, route, leave-by']]],
['Talk','var(--sand)',[['mic','Ask','Composer with voice'],['saved_chat','Thread','Short chat inside the page'],['headphones','Listen','Read aloud with chapters'],['devices','Device','Glasses, buds, buddy, ink controls']]]];
B['119']=()=>`<div class="boardpg">${head('the parts halo builds with','24 blocks, one set of rules, a check before anything shows','halo doesn’t draw screens from nothing. It picks from a closed set of 24 blocks in six families, lays them out on a fixed grid, and every page goes through a check before you see it. The same rules apply on a phone, a tablet, glasses and a watch.')}
<div class="q-grid6">${FAM.map(([n,c,bl])=>`<div class="q-fam"><h3><i style="background:${c}"></i>${n}</h3>${bl.map(([ic,b,p])=>`<div class="q-bk">${I(ic)}<div><b style="display:block">${b}</b>${p}</div></div>`).join('')}</div>`).join('')}</div>
<div class="row" style="margin-top:26px;gap:22px">
<div style="flex:1.35"><div class="lab" style="margin-bottom:10px">How a page is made · on the phone, under a second for the layout</div>
<div class="q-pipe">${[['1','Understand','Turn the request into an intent, people, place, time and limits (budget, age, mode).'],
['2','Plan','Pick 3–9 blocks and order them by importance, as a page plan.'],
['3','Check','Schema, Kids rules, permissions, ask-first actions, contrast, text size and no more than 7 blocks in view.'],
['4','Show','Built from halo’s real components. Blocks fade in top to bottom, or appear at once in Still mode.'],
['5','Learn','Pins, swaps and “less like this” are stored on the phone. Nothing leaves it.']].map(([n,b,p])=>`<div class="q-st"><div class="n">${n}</div><b>${b}</b><p>${p}</p></div>`).join('')}</div>
<div class="code" style="margin-top:14px"><span class="c">// page plan · what halo hands to the renderer (see dev/halo-adaptive-ui.schema.json)</span>
{ <span class="k">"page"</span>: <span class="s">"rainy-saturday"</span>, <span class="k">"mode"</span>: <span class="s">"adult"</span>, <span class="k">"density"</span>: <span class="s">"standard"</span>, <span class="k">"refresh"</span>: <span class="s">"sat 07:00"</span>,
  <span class="k">"blocks"</span>: [
    { <span class="k">"type"</span>: <span class="s">"chart"</span>, <span class="k">"source"</span>: <span class="s">"weather.hourly"</span>, <span class="k">"why"</span>: <span class="s">"Rain decides the plan"</span> },
    { <span class="k">"type"</span>: <span class="s">"options"</span>, <span class="k">"span"</span>: <span class="n">4</span>, <span class="k">"max"</span>: <span class="n">2</span>, <span class="k">"source"</span>: <span class="s">"places.nearby"</span> },
    { <span class="k">"type"</span>: <span class="s">"timeline"</span>, <span class="k">"span"</span>: <span class="n">4</span> },
    { <span class="k">"type"</span>: <span class="s">"askFirstAction"</span>, <span class="k">"action"</span>: <span class="s">"book"</span>, <span class="k">"limit"</span>: { <span class="k">"eur"</span>: <span class="n">40</span> } } ] }</div></div>
<div style="flex:1"><div class="lab" style="margin-bottom:10px">One grid, every screen</div>
<div class="q-grid4" style="grid-template-columns:repeat(4,1fr);gap:12px">
${[['Phone','4 columns',4,[4,2,2,4]],['Tablet','8 columns',8,[5,3,4,4]],['Glasses','1 column, 2 blocks',1,[1,1]],['Watch','1 block',1,[1]]].map(([n,s,c,sp])=>`<div><div class="q-gridvis" style="grid-template-columns:repeat(${c},1fr)">${sp.map(x=>`<div style="grid-column:span ${x}"></div>`).join('')}</div><div style="font:600 13px Sora;margin-top:6px">${n}</div><div style="font-size:11.5px;color:var(--t3)">${s}</div></div>`).join('')}</div>
<div class="box" style="margin-top:16px"><h3>${I('privacy')}The check never lets through</h3><ul>
<li>a block outside the 24, or <b>code</b> that halo wrote itself</li>
<li>paying, sending, deleting or posting <b>without asking you first</b></li>
<li>more than 3 choices, <b>points, streaks or ads</b> in Kids</li>
<li>a final answer for a child’s <b>homework</b></li>
<li>red used for anything except <b>errors</b>, or Apricot for anything except “needs you”</li>
<li>text under 14 sp (Kids 18 sp), or targets under 48 dp (Kids 56 dp)</li></ul></div></div></div>
<div style="margin-top:24px"><div class="lab" style="margin-bottom:10px">Every block has the same parts and the same five states</div>
<div class="q-grid6" style="grid-template-columns:1.3fr repeat(5,1fr)">
<div class="box" style="padding:12px 14px"><h3>${I('card')}Anatomy</h3><p><b style="color:var(--cream)">Header</b> (icon, name, ⋯) · <b style="color:var(--cream)">Body</b> · <b style="color:var(--cream)">Why?</b> · up to 2 <b style="color:var(--cream)">actions</b> · handles when you edit</p></div>
${[['Loading','grey placeholder the size of the block, no spinner','var(--t3)'],['Ready','normal look','var(--cream)'],['Working','thin mint line along the top while it updates','var(--mint)'],['Needs you','Apricot edge plus one plain question','var(--ap)'],['Offline / error','“Last checked 08:12”, red only for a real error','var(--err)']].map(([n,p,c])=>`<div class="box" style="padding:12px 14px;border-top:3px solid ${c}"><h3 style="font-size:15px">${n}</h3><p>${p}</p></div>`).join('')}
</div></div>
</div>`;

/* ───────── 120 · smart workspaces + Kids & Study ───────── */
const p120a=`<div class="q-top">${I('back')}<b>portrait.jpg</b><span class="q-made">${I('ai_draft')}retouch set</span></div>
<div class="q-mini" style="height:210px;background-image:url(img/sunset.jpg);border-radius:14px"></div>
<div style="display:flex;gap:6px;margin-top:8px;flex-wrap:wrap">${chip('Heal','m','eraser')}${chip('Soften skin','','highlighter')}${chip('Dodge / burn','','accent')}${chip('Eyes','','select')}${chip('Red eye','','fit_all')}</div>
<div style="font-size:10.5px;color:var(--t3);margin-top:8px">You’re retouching a face, so halo moved the face tools forward. The rest are in “All tools”.</div>`;
const p120b=`<div class="q-top">${I('back')}<b>Dinosaur day</b><span class="q-made" style="color:#8a5a00;background:#FFE7A8">${I('ai_draft')}made for Mia</span></div>
<div class="q-bud"><div class="f">${RING('#E59A2E',22,34)}</div><div class="t">You love T-rex! Want to draw one, count its teeth, or hear its story?</div></div>
${blk('draw','Draw a T-rex','<p>Big crayon brushes · tracing dots</p>')}
${blk('numbered_list','Count the teeth','<p>Tap each tooth · 1, 2, 3…</p>')}
${blk('headphones','Story: Rex gets lost','<p>4 minutes · read to me</p>')}`;
const p120c=`<div class="q-top">${I('back')}<b>Biology · cells</b>${made}</div>
${blk('camera','From your photo','<p>Syllabus p.12 · exam in 9 days</p>')}
${blk('grid_view','Concept map','<p>Cell → membrane · nucleus · mitochondria</p>')}
<div class="q-row">${blk('quiz','Try 3','<p>Coach me</p>')}${blk('flashcard','Cards','<p>12 cards</p>')}</div>
${blk('calendar','Plan to exam','<p>9 short sessions · 20 min</p>')}`;
B['120']=()=>`<div class="boardpg">${head('smart workspaces · Kids · Study','The same idea inside tools, for children and for learning','Tools reorganise themselves around the task you’re doing, and the standard layout is always one tap away. In Kids, halo builds pages around what the child loves, inside the grown-up’s rules. In Study, a photo of a syllabus or homework becomes a study page that coaches with questions.')}
<div class="row" style="justify-content:space-between">
${P(p120a,'Smart workspace · halo studio','halo spots the task (retouching a face) and moves the right instruments forward. “Standard layout” always puts things back.',{ctl:`<div style="display:flex;gap:5px">${chip('Standard layout','','grid_view')}${chip('All tools','','tool')}${chip('Why?','l')}</div><div class="q-comp">${I('mic')}“Make the eyes brighter”</div>`})}
${P(p120b,'Kids · made for Mia','Three choices at most, big targets, buddy’s voice. Blocks come only from the list a grown-up approved. No points.',{cls:'kid',ctl:`<div class="q-kbtn">${I('mic','color:#3a2608')}Tell buddy</div>`})}
${P(p120c,'Study · from a photo','Photo of the syllabus → map, practice, cards and a plan to the exam. “Coach me” is the default, and adults can always tap “Show answer”.',{ctl:`<div style="display:flex;gap:5px">${chip('Coach me','m','study')}${chip('Show answer','')}${chip('Easier','l')}</div><div class="q-comp">${I('camera')}Add a photo…</div>`})}
<div class="col" style="width:470px;gap:14px">
<div class="box"><h3>${I('settings')}Smart workspace rules</h3><ul>
<li>Panels can <b>reorder</b>, but menus never change their names or order.</li>
<li>A moved instrument shows a small <b>mint dot</b> for 3 uses so you can find it again.</li>
<li>“<b>Standard layout</b>” and “<b>Keep this layout</b>” are always in the bottom zone.</li>
<li>Pro level: the smart workspace is off by default and can be turned on per tool.</li></ul></div>
<div class="box"><h3>${I('kids_mode')}Kids pages · grown-up controls</h3><ul>
<li>The grown-up picks the allowed block types once: <b>Draw, Count, Story, Ask buddy, Music</b>.</li>
<li>New topics come from the child’s own words, filtered through <b>kids-safe sources</b> (still open).</li>
<li>The page summary goes to the grown-up’s phone: what was made and asked, with no scores.</li></ul></div>
<div class="box"><h3>${I('study')}Study pages</h3><ul>
<li>Built with the existing tutor ladder: <b>ask → hint → explain → analogy → check</b>.</li>
<li>Kids study: never the final answer. Adults: <b>Show answer</b> is always one tap.</li>
<li>No grades or streaks, just “what you can do now” in plain words.</li></ul></div></div>
</div>
${notes([['Same page, three sizes','Every page halo builds comes in Easy (fewer, bigger blocks), Standard and Pro (denser, with extra numbers). The level you pick in Settings applies to it.'],
['Hand-off between tools','A block can open its full tool: Sketch → halo canvas, Table → halo sheets, Map → halo maps. Your work comes along.'],
['Offline','Pages are built on the phone. Without a connection, blocks that need live data show “Last checked 08:12” and never invent numbers.'],
['Next batch','Batch 2 is the drawing engine: real brushes, vector paths, resizable boxes with exact values, and precise drawing with units.']])}
</div>`;
window.B=B;
