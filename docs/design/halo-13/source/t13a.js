/* halo 13.0 · batch 1 — foundations: plain words, dynamic UI, style & wallpaper (boards 137–142) */
const head13=(k,t,p)=>`<div class="bh"><div><div class="k">halo · 13.0 · ${k}</div><h1>${t}</h1><p>${p}</p></div>${WM}</div>`;
const li2=(ic,t)=>`<div class="q-li2">${I(ic)}<div>${t}</div></div>`;
const gap2=(b,a)=>`<div class="q-li2"><i class="q-dot" style="background:var(--ap)"></i><div>${b}<br><span style="color:var(--t3)">${a}</span></div></div>`;
const lvlbar=on=>`<div class="r-lvl">${['Simple','Standard','Pro'].map((x,i)=>`<span class="${i==on?'on':''}">${x}</span>`).join('')}</div>`;
const B13={};

/* ───────── 137 · halo 13 — anyone can use it ───────── */
B13['137']=()=>`<div class="boardpg">${head13('the brief','halo 13 — anyone can use it','halo can do a great deal, but today it talks like an engineer and asks for set-up before it helps. 13.0 keeps every power feature and makes the first ten minutes, the words and the screens work for anyone: a grandparent, a 10-year-old, a busy parent, a pro illustrator. It arrives in six batches, each checked against the halo repo and each shipping page mocks, icons, Lottie motion, a written spec and code.')}
<div class="q-grid3" style="grid-template-columns:1fr 1.05fr 1.15fr">
<div class="q-col"><h2>${I('code')}What the repo has today</h2>
<div class="lab" style="margin-bottom:6px">github · slafftrosheen/Halo · 99aac2f · 23 Nov 2026, 11:46</div>
${li2('check','<b>12.0 brushes shipped.</b> brushes.json, InkBrushPresets and the <code style="color:#B4E6D6">sketch:v4</code> codec: every stroke keeps its brush.')}
${li2('check','<b>Studio grew up.</b> Bézier node editing, shape operations, DXF import, named project files.')}
${li2('check','<b>Adaptive Today.</b> Blocks from blocks.json; pages export to launcher widgets and routines.')}
${li2('check','<b>Learning.</b> Live worksheet camera, spoken rehearsal, the Kids fox lesson and topic islands.')}
${gap2('Notes sketches still stop at 64 strokes.','CanvasSketchCodec.MAX_STROKES = 64 → batch 4.')}
${gap2('Onboarding is 10 technical stages.','SetupStage: Welcome → Use → Permissions → AI → Speech → Devices → Privacy → Everyday → Readiness → First → batch 2.')}
</div>
<div class="q-col"><h2>${I('review')}Why halo feels “expert only”</h2>
<div class="lab" style="margin-bottom:8px">measured in app/src/main/res/values · English</div>
<div class="q-grid3" style="grid-template-columns:1fr 1fr;gap:10px;margin-bottom:12px">
<div class="r-stat a"><b>553</b><span>of 8,299 strings use a tech word (6.7 %)</span></div>
<div class="r-stat a"><b>239</b><span>strings say “model”, 88 say “provider”</span></div>
<div class="r-stat a"><b>112</b><span>strings are longer than 140 characters</span></div>
<div class="r-stat a"><b>10</b><span>set-up stages before you can try anything</span></div></div>
${[['“Provider address could not be reached”','Error with no way forward, in tech words.'],['“Runtime policy” · “Endpoint”','Settings rows mean nothing to most people.'],['Everything at once','Studio, Canvas and Settings show every instrument on the first open.'],['Hidden gestures','Long-press, two-finger and edge swipes do things with no hint.']].map(([a,b])=>`<div class="q-li2"><i class="q-dot" style="background:var(--err)"></i><div><b>${a}</b><br><span style="color:var(--t3)">${b}</span></div></div>`).join('')}
</div>
<div class="q-col"><h2>${I('guide_me')}Six rules for 13</h2>
<div class="q-grid3" style="grid-template-columns:1fr 1fr;gap:10px">
${[['Try first, set up later','halo works in the first minute. Each permission is asked when its feature is used.'],
['Everyday words','No tech word on Simple or Standard screens. Pro keeps them, with a plain meaning on long-press.'],
['One main thing per screen','One big action. The rest is one tap away under “Show more”, never deleted.'],
['Three depths, your choice','Simple, Standard, Pro, for the whole app or per tool. halo can suggest a change; it never switches by itself.'],
['The screen fits the moment','Time, pen, keyboard, travel and device reshape a screen, at most two changes, always with “Why?”.'],
['Beautiful and calm','A wallpaper that follows the sky, looks that match it, motion you can turn down. Colours keep their meaning.']].map(([b,p],i)=>`<div class="q-prin"><div class="n">0${i+1}</div><b>${b}</b><p>${p}</p></div>`).join('')}
</div></div></div>
<div style="margin-top:28px"><div class="lab" style="margin-bottom:10px">halo 13.0 in six batches · each: page mocks · icons · Lottie · spec · code · strings EN/RU</div>
<div class="q-batch">${[['Batch 1','Foundations','Plain words, three depths, dynamic UI everywhere, style & wallpaper 13.',1],
['Batch 2','Onboarding','About 4 human steps, try first, paths for me / my child / study / create.'],
['Batch 3','Sources + Planner','One calm sources manager; a planner with day, week, month and timeline.'],
['Batch 4','Five tools grow','Canvas, Studio, Notes, Translate, Scan.'],
['Batch 5','Study + Kids','Calmer Study, deeper Kids worlds, a simple grown-up view.'],
['Batch 6','Book + pack','13.0 book, Russian boards, prototype, films, repo-shaped pack.']].map(([n,b,p,on])=>`<div class="q-bt ${on?'on':''}"><div class="n">${n}${on?' · now':''}</div><b>${b}</b><p>${p}</p></div>`).join('')}</div></div>
<div style="margin-top:24px"><div class="lab" style="margin-bottom:8px">Where batch 1 lands in the repo</div>
<table class="q-tbl"><tr><th>Area</th><th>Owner in the code today</th><th>13.0 adds</th></tr>
${[['Three depths','<code>ui/adaptive/AdaptiveTodayPage.kt</code> · <code>assets/adaptive/blocks.json</code>','<code>ui/adaptive/v13/ExperienceLevel.kt</code> · LevelAdvisor (offers, never switches)'],
['Dynamic UI everywhere','Today only','<code>ui/adaptive/v13/DynamicSurface.kt</code> · <code>assets/adaptive/v13/surfaces-13.json</code> for 9 screens'],
['Plain words','8,299 strings, no rules','<code>ui/plain/PlainWords.kt</code> · <code>assets/plain/glossary-13.json</code> · a ratchet test that only lets the count fall'],
['Looks + wallpaper','<code>ui/theme/HaloLook.kt</code> (6 presets) · <code>wallpaper/WallpaperPreferences.kt</code> (3 periods)','<code>ui/theme/v13/LookEngine.kt</code> · <code>wallpaper/v13/SkyTimeline.kt</code> · <code>assets/looks/looks-13.json</code>']].map(r=>`<tr><td><b>${r[0]}</b></td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</table></div>
</div>`;

/* ───────── 138 · plain words ───────── */
const p138a=`<div class="q-top">${I('back')}<b>Setup</b><span class="sub">5 of 10</span></div>
<div class="r-big">Listening and speaking</div>
<p class="r-p">Choose or download a <span class="r-tech">listening model</span>. <span class="r-tech">On-device</span> <span class="r-tech">speech-to-text</span> keeps audio local. Cloud <span class="r-tech">providers</span> may offer lower <span class="r-tech">latency</span>.</p>
<div class="opt" style="margin-top:12px"><div class="oi">${I('model')}</div><div><b>Sherpa listening model</b><small>Download required · <span class="r-tech">runtime</span> check</small></div></div>
<div class="opt"><div class="oi">${I('cloud')}</div><div><b>Bring my own <span class="r-tech">provider</span></b><small><span class="r-tech">Endpoint</span> + <span class="r-tech">API key</span></small></div></div>
<div class="r-bt s">Test microphone and listening</div>
<p class="sub" style="margin-top:8px;text-align:center">Configure later / limited mode</p>`;
const p138b=`<div class="q-top">${I('back')}<b></b><span class="sub">Skip</span></div>
<div class="r-big">Want to talk to halo?</div>
<p class="r-p">halo can hear you and answer out loud. It works without the internet, and what you say stays on this phone.</p>
<div style="height:120px;display:grid;place-items:center;margin:8px 0">${RING('#0CF2BA',96,26)}</div>
<div class="r-bt">${I('mic')}Yes, let halo hear me</div>
<div class="r-bt s">Not now — I’ll type</div>
<p class="sub" style="margin-top:10px;text-align:center">Needs one free download. You can do it later.</p>`;
const p138c=`<div class="q-top">${I('back')}<b>AI provider</b>${I('more')}</div>
<div class="opt" style="border-color:var(--err);background:rgba(255,143,163,.07)"><div class="oi" style="color:var(--err)">${I('failure')}</div><div><b>Provider address could not be reached</b><small>HTTP timeout · <span class="r-tech">fallback</span> disabled</small></div></div>
<div class="lr">${I('model')}<div class="gr"><span class="r-tech">Runtime policy</span><small>Automatic within the selected <span class="r-tech">provider</span></small></div></div>
<div class="lr">${I('lan')}<div class="gr"><span class="r-tech">Endpoint</span><small>https://…/v1</small></div></div>
<div class="lr">${I('privacy')}<div class="gr">Sharing permissions<small>never select a cloud <span class="r-tech">provider</span></small></div></div>
<div class="lr">${I('diagnostics')}<div class="gr">Save setup <span class="r-tech">diagnostics</span></div></div>`;
const p138d=`<div class="q-top">${I('back')}<b>How halo answers</b></div>
<div class="opt" style="border-color:var(--ap);background:rgba(255,159,107,.07)"><div class="oi" style="color:var(--ap)">${I('connection')}</div><div><b>Your online AI service isn’t answering</b><small>halo is using the one on this phone for now.</small></div></div>
<div style="display:flex;gap:6px;margin:-2px 0 10px">${chip('Try again','m','retry')}${chip('Choose another','')}</div>
<div class="lr">${I('on_device')}<div class="gr">Answers come from<small>This phone, or online if it can’t</small></div><span class="v">Auto</span></div>
<div class="lr">${I('privacy')}<div class="gr">Sharing<small>Nothing is sent online unless you say so</small></div><span class="tg"></span></div>
<div class="lr">${I('show_more')}<div class="gr">More for experts<small>Address, service key, check-up</small></div></div>
<div class="q-why" style="top:252px"><b>What’s this? · “AI service”</b>The company or app that writes halo’s answers when it isn’t done on this phone. <span style="color:var(--lil)">Say it simpler</span></div>`;
B13['138']=()=>`<div class="boardpg">${head13('batch 1 · plain words','Say it like a person would','The same screens, rewritten. A tech word becomes an everyday one; a sentence says what happens next; an error always comes with a way forward. Pro keeps exact terms, and a long-press on any word explains it.')}
<div class="r-two"><div class="r-ph">
<div class="pcol">${phone(p138a,{over:'<span class="r-bad">Today · repo</span>'})}<div class="pl">Setup · listening<small>7 tech words in one screen</small></div></div>
<div class="pcol">${phone(p138b,{over:'<span class="r-good">13.0</span>'})}<div class="pl">The same step, plain<small>one question, one big answer</small></div></div>
<div class="pcol">${phone(p138c,{over:'<span class="r-bad">Today · repo</span>'})}<div class="pl">Settings · AI provider<small>an error with no way forward</small></div></div>
<div class="pcol">${phone(p138d,{over:'<span class="r-good">13.0</span>'})}<div class="pl">How halo answers<small>what happened, what halo did, what you can do</small></div></div>
</div>
<div class="col" style="gap:16px">
<div class="box"><h3>${I('plain_words')}Glossary · 30 words · EN + RU</h3>
<table class="tbl"><tr><th>Tech word</th><th>Plain</th><th>По-русски</th></tr>
${[['provider','AI service','ИИ-сервис'],['model','brain','«мозг» halo'],['on-device','on this phone','на этом телефоне'],['diagnostics','check-up','проверка'],['receipt','what halo used','что использовал halo'],['context','what halo knows right now','что halo знает сейчас'],['grounded','based on your sources','по вашим источникам'],['listening model','voice download','загрузка голоса'],['fallback','backup plan','запасной вариант'],['vector','shapes you can edit','редактируемые фигуры']].map(r=>`<tr><td class="k">${r[0]}</td><td class="n">${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</table></div>
<div class="box"><h3>${I('check')}Seven writing rules</h3><ol>
<li><b>Everyday words</b> on Simple and Standard. The glossary is the list.</li>
<li><b>22 words at most</b> in a sentence. More goes to “Learn more”.</li>
<li><b>Buttons are verbs</b> that say the result: “Let halo hear me”, not “Continue”.</li>
<li><b>Errors: what happened, what halo did, what you can do</b>, never a code first.</li>
<li><b>No blame</b>: “isn’t answering”, not “you entered an invalid address”.</li>
<li><b>Numbers with units</b> people know: “about 1 minute”, “48 MB”.</li>
<li><b>Russian</b>: «вы» for adults, «ты» for kids; halo and tool names stay in Latin.</li></ol></div>
</div></div>
${notes([['Long-press any word','Every screen supports “What’s this?”. The glossary explains, and “Say it simpler” rewrites the whole sentence on this phone.'],['A ratchet, not a rewrite','A test counts tech words in strings. Today 553; the build fails if it grows. Teams lower it screen by screen.'],['Pro keeps precision','Keys starting pro_, dev_, diag_ are exempt. Experts still see Endpoint, tokens and model names.'],['Kids stay kids','Kids text is checked against the same list plus a shorter limit: 12 words a sentence.']])}
</div>`;

/* ───────── 139 · three depths ───────── */
const cvs=(h,extra='')=>`<div class="r-cv" style="height:${h}px"><svg viewBox="0 0 250 ${h}" style="position:absolute;inset:0;width:100%;height:100%"><path d="M40 ${h*.7}C70 ${h*.3} 110 ${h*.25} 140 ${h*.5}S200 ${h*.8} 215 ${h*.35}" stroke="#24412F" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M60 ${h*.82}C95 ${h*.62} 150 ${h*.7} 190 ${h*.6}" stroke="#FF9F6B" stroke-width="9" fill="none" stroke-linecap="round" opacity=".8"/><circle cx="190" cy="${h*.28}" r="16" fill="#9EC9FF"/></svg>${extra}</div>`;
const p139s=`<div class="q-top">${I('back')}<b>My drawing</b></div>${lvlbar(0)}${cvs(300)}
<div style="display:flex;justify-content:space-around;margin-top:14px">
<div class="r-tbtn on"><i>${I('draw')}</i>Draw</div><div class="r-tbtn"><i>${I('undo')}</i>Undo</div><div class="r-tbtn"><i style="background:#FF9F6B"></i>Colour</div><div class="r-tbtn"><i>${I('show_more')}</i>More</div></div>`;
const p139m=`<div class="q-top">${I('back')}<b>My drawing</b>${I('undo')}${I('redo')}</div>${lvlbar(1)}${cvs(268)}
<div style="display:flex;gap:5px;margin:10px 0 6px">${['Pencil','Ink','Marker','Brush','Eraser'].map((b,i)=>`<span class="q-chip ${i==1?'m':''}">${b}</span>`).join('')}</div>
<div style="display:flex;gap:6px;align-items:center">${['#24412F','#FF9F6B','#9EC9FF','#C9B8FF','#060330'].map(c=>`<i style="width:22px;height:22px;border-radius:50%;background:${c};display:inline-block;border:2px solid rgba(250,250,244,.2)"></i>`).join('')}<span class="sub" style="margin-left:auto">Size 6</span></div>
<div class="lr" style="margin-top:4px">${I('collections')}<div class="gr">Layers<small>3 · tap to open</small></div></div>`;
const p139p=`<div class="q-top">${I('back')}<b>My drawing.halo</b>${I('undo')}${I('redo')}${I('more')}</div>${lvlbar(2)}
<div style="display:flex;gap:10px;font:600 10.5px Atk;color:var(--t2);margin-bottom:6px">${['File','Edit','Brush','Layer','Select','View'].map(x=>`<span>${x}</span>`).join('')}</div>
<div style="display:flex;gap:6px">${'<div style="display:flex;flex-direction:column;gap:5px">'+['draw','eraser','select','shapes','sketch','zoom_in'].map((x,i)=>`<span style="width:28px;height:28px;border-radius:8px;display:grid;place-items:center;background:${i==0?'rgba(12,242,186,.18)':'rgba(250,250,244,.05)'};color:${i==0?'var(--mint)':'var(--t2)'}">${I(x)}</span>`).join('')+'</div>'}<div style="flex:1">${cvs(222)}</div></div>
<div class="box" style="padding:8px 10px;margin-top:8px;border-radius:14px"><div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px;font:600 10.5px Atk;color:var(--t2)"><span>Size <b style="color:var(--cream)">6.0 px</b></span><span>Flow <b style="color:var(--cream)">80 %</b></span><span>Tilt <b style="color:var(--cream)">on</b></span><span>Smooth <b style="color:var(--cream)">35</b></span><span>Snap <b style="color:var(--cream)">off</b></span><span>Layer <b style="color:var(--cream)">2/3</b></span></div></div>
<div style="font:600 10px ui-monospace,Menlo,monospace;color:var(--t3);margin-top:6px">⌘Z undo · B brush · E eraser · [ ] size</div>`;
const p139o=`<div class="q-top">${I('back')}<b>My drawing</b></div>${lvlbar(0)}${cvs(300)}
<div class="r-sheet"><div class="r-grab"></div><h5>${I('tip')}Want more tools here?</h5><p class="r-p">You often open brushes and layers from “More”. I can keep them out on this drawing screen.</p>
<div class="r-bt" style="height:44px">Yes, show more tools</div><div class="r-bt s">Not now</div>
<p class="sub" style="margin-top:8px">Only for drawing. Change any time in Settings → How much to show.</p></div>`;
B13['139']=()=>`<div class="boardpg">${head13('batch 1 · three depths','Simple, Standard, Pro — one app, your depth','Every screen exists in three depths. Simple shows the main thing, big. Standard is today’s halo in plain words. Pro shows every instrument, menu and shortcut. You choose for the whole app or per tool. halo can offer a change; it never switches on its own, and a lower depth never deletes anything.')}
<div class="r-two"><div class="r-ph">
${P(p139s,'Simple','big canvas, four big buttons')}
${P(p139m,'Standard','brushes, colours, layers one tap away')}
${P(p139p,'Pro','menus, tool rail, exact values, shortcuts')}
${P(p139o,'halo offers, you decide','at most once in 14 days per tool')}
</div>
<div class="col" style="gap:14px">
<div class="box"><h3>${I('level_pro')}How depth is chosen</h3><ul>
<li><b>First run</b>: “How much do you want to see?” Large text or TalkBack starts on Simple.</li>
<li><b>Per tool</b>: Studio on Pro, Settings on Simple is normal.</li>
<li><b>Offer up</b>: 7+ days, 12+ “More” uses in 14 days, little help needed.</li>
<li><b>Offer down</b>: 6+ help opens in 14 days.</li>
<li><b>Undo 3× in a week</b>: halo stops adapting that screen.</li>
<li><b>Kids</b>: always Simple. No offers.</li></ul></div>
<div class="code"><span class="c">// ui/adaptive/v13/ExperienceLevel.kt</span>
<span class="k">fun</span> decide(cur: ExperienceLevel, s: LevelSignals,
           daysSinceLastOffer: <span class="k">Int</span>): LevelDecision {
  <span class="k">if</span> (s.kidsProfile) <span class="k">return</span> Keep(SIMPLE)
  <span class="k">if</span> (s.chosenByPerson != <span class="k">null</span> ||
      daysSinceLastOffer < <span class="n">14</span>) <span class="k">return</span> Keep(cur)
  <span class="k">return when</span> {
    cur != PRO && s.daysUsed >= <span class="n">7</span> &&
      s.advancedActions14d >= <span class="n">12</span> -> Offer(cur, up, USES_ADVANCED_TOOLS)
    cur != SIMPLE && s.helpOpens14d >= <span class="n">6</span>
                          -> Offer(cur, down, OFTEN_ASKS_FOR_HELP)
    <span class="k">else</span> -> Keep(cur)
  }
}</div>
<div class="box"><h3>${I('anchor')}Never changes with depth</h3><p>Back, title, the halo ring, the dock and the one main action stay in the same place. Search and voice find every tool at every depth.</p></div>
</div></div>
${notes([['Nothing disappears','A lower depth moves tools behind “Show more”. Files made in Pro open fine in Simple.'],['Same words, fewer of them','Simple is not a different app: same icons, same colours, same names.'],['One setting, many places','Settings → How much to show lists every tool with its depth.'],['Accessibility first','Simple uses 56 dp targets and 17 sp body text by default.']])}
</div>`;

/* ───────── 140 · dynamic UI across the app ───────── */
const tg=(t,st,c='l')=>`<span class="q-tag ${c}" style="${st}">${t}</span>`;
const p140a=`<div class="q-top">${I('sky_day')}<b>Good morning</b><span class="sub">07:40</span></div>
${blk('calendar','First today','<p style="font-size:13px;color:var(--cream)"><b>08:30 · Dentist, Brīvības 40</b></p><p>Leave by 08:05 · bus 3 in 6 min</p>',{st:'border-color:rgba(12,242,186,.5)'})}
<div class="r-bt" style="margin:2px 0 8px">${I('play')}Start my morning</div>
${blk('cloud','Weather','<p>Dry until 15:00 · 2°</p>')}
${blk('task','Also today','<p>Pay rent · Call Mum · Library book</p>')}
${tg('morning · promoted','left:12px;top:46px')}`;
const p140b=`<div class="q-top">${I('sky_night')}<b>Good evening</b><span class="sub">21:10</span></div>
${blk('calendar','Tomorrow','<p style="font-size:13px;color:var(--cream)"><b>Quiet day · first at 10:00</b></p><p>Rain in the morning: take the umbrella</p>',{st:'border-color:rgba(201,184,255,.5)'})}
<div class="r-bt" style="background:var(--lil);color:#1d1240;margin:2px 0 8px">${I('calm_motion')}Wind down</div>
${blk('check','Done today','<p>4 of 5 · rent paid</p>')}
${blk('read_mode','To read','<p>2 saved articles · 9 min</p>')}
${tg('evening · tomorrow first','left:12px;top:46px')}`;
const p140c=`<div class="q-top">${I('back')}<b>Lecture notes</b>${I('more')}</div>
<p class="r-p" style="color:var(--cream)">Photosynthesis happens in the chloroplast. Light reaction → ATP + NADPH.</p>
<div class="r-cv" style="height:200px;margin:10px 0"><svg viewBox="0 0 250 200" style="position:absolute;inset:0"><ellipse cx="125" cy="100" rx="90" ry="55" stroke="#24412F" stroke-width="3" fill="none"/><path d="M60 100C90 80 110 120 140 95S180 90 190 105" stroke="#0aa983" stroke-width="3" fill="none"/><path d="M150 40l30-20" stroke="#E0763F" stroke-width="3"/></svg></div>
<div style="display:flex;gap:5px">${chip('Pen','m','draw')}${chip('Marker')}${chip('Eraser')}${chip('Shapes')}</div>
${tg('pen out · sketch is the main action','left:12px;top:232px')}`;
const p140d=`<div class="q-top">${I('back')}<b>Translate</b><span class="sub">🇮🇹 Rome</span></div>
<div style="display:grid;place-items:center;height:150px">${RING('#0CF2BA',110,24)}</div>
<div class="r-bt">${I('mic')}Talk · English ↔ Italiano</div>
<div class="q-row" style="margin-top:8px"><div class="r-bt s">${I('camera')}Signs & menus</div><div class="r-bt s">${I('plain_words')}Phrases</div></div>
<p class="sub" style="text-align:center;margin-top:10px">Works offline · Italian pack on this phone</p>
${tg('abroad · conversation first','left:12px;top:46px')}`;
const p140e=`<div class="q-top">${I('sky_day')}<b>Good morning</b><span class="sub">07:40</span></div>
${blk('calendar','First today','<p>08:30 · Dentist · leave by 08:05</p>')}${blk('cloud','Weather','<p>Dry until 15:00</p>')}
<div class="r-sheet"><div class="r-grab"></div><h5>${I('why')}Why does it look like this?</h5>
<div class="r-why">${I('sky_day')}<div><b>It’s morning</b>, so your first thing today is at the top.</div></div>
<div class="r-why">${I('calendar')}<div><b>You have a trip at 08:30</b>, so “Leave by” is the main button.</div></div>
<div style="display:flex;flex-direction:column;gap:6px;margin-top:10px"><div class="r-bt" style="height:40px;margin:0">${I('pin_layout')}Keep it like this</div><div class="q-row"><div class="r-bt s" style="margin:0">${I('reset_layout')}Back to normal</div><div class="r-bt s" style="margin:0">Stop changing</div></div></div></div>`;
B13['140']=()=>`<div class="boardpg">${head13('batch 1 · dynamic UI everywhere','Every screen fits the moment — and tells you why','12.0 made Today adaptive. 13.0 gives every screen the same engine: fixed anchors, plus blocks that time, pen, keyboard, travel, device and depth can promote or tuck away. At most two changes at a time, and “Why?” always explains.')}
<div class="r-ph" style="justify-content:space-between">
${P(p140a,'Today · morning','what’s first, when to leave')}
${P(p140b,'Today · evening','tomorrow first, then wind down')}
${P(p140c,'Notes · pen out','sketching becomes the main action')}
${P(p140d,'Translate · abroad','conversation first, offline')}
${P(p140e,'“Why?” sheet','keep it, reset it, or stop changes')}
</div>
<div class="row" style="margin-top:22px;gap:22px">
<div class="box" style="flex:1.5"><h3>${I('adapts')}Which signal reshapes which screen · surfaces-13.json</h3>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:4px 18px">${[['Today','time of day · on the move'],['Canvas','pen · tablet'],['Studio','keyboard'],['Notes','pen · studying'],['Planner','evening'],['Translate','abroad · glasses'],['Study','evening'],['Scan · Settings','depth only']].map(([a,b])=>`<div class="q-bk" style="border-top:1px solid var(--line)"><b>${a}</b>${b}</div>`).join('')}</div></div>
<div class="code" style="flex:1"><span class="c">// ui/adaptive/v13/DynamicSurface.kt</span>
<span class="k">object</span> SurfaceResolver {
  <span class="k">const val</span> MAX_CHANGES_PER_OPEN = <span class="n">2</span>
  <span class="k">fun</span> resolve(spec, s: Signals, pins: Pins) {
    <span class="k">val</span> applied = <span class="k">if</span> (pins.frozen) emptyList()
      <span class="k">else</span> spec.rules.filter { it.whenAll.all { c -> c.holds(s) } }
                      .take(MAX_CHANGES_PER_OPEN)
    <span class="c">// pins win · anchors can't move · one primary</span>
  }
}</div>
<div class="box" style="flex:1"><h3>${I('anchor')}Four promises</h3><ul>
<li><b>Anchors never move</b>: back, title, ring, dock.</li>
<li><b>Two changes at most</b> each time you open a screen.</li>
<li><b>Your pins win</b> over any rule.</li>
<li><b>Every change has a reason</b> in plain words, with Keep · Reset · Stop.</li></ul></div>
</div></div>`;

/* ───────── 141 · style & wallpaper 13 ───────── */
const SKY={NIGHT:['#0a0738','#1b1760','#FAFAF4',null,'21:10'],DAWN:['#3b2f7a','#FF9F6B','#FFE08A',[70,78],'07:05'],MORNING:['#9EC9FF','#FFE7C2','#FFE08A',[30,60],'08:30'],DAY:['#6FB2FF','#CFE6FF','#FFE08A',[50,18],'12:40'],GOLDEN:['#FFB36B','#FFE08A','#FFF3C4',[78,52],'15:20'],DUSK:['#5b3d8f','#FF8FA3','#FFD0A8',[84,82],'16:55']};
const skyT=(k,h=96)=>{const [a,b,s,pos,t]=SKY[k];return `<div class="r-sky" style="height:${h}px;background:linear-gradient(180deg,${a},${b})">${pos?`<i class="s" style="left:${pos[0]}%;top:${pos[1]}%;background:${s};box-shadow:0 0 24px ${s}"></i>`:`<i class="s" style="left:72%;top:16%;background:${s};box-shadow:-7px 3px 0 0 ${a} inset"></i>`}<i class="h"></i><b>${k.toLowerCase()}</b><em>${t}</em></div>`};
const p141a=`<div style="position:absolute;inset:-40px -16px -80px;background:linear-gradient(180deg,#5b3d8f,#FF8FA3 70%,#FFD0A8)"></div>
<div style="position:absolute;left:-16px;right:-16px;bottom:-80px;height:220px;background:#2a1d4a;border-radius:60% 40% 0 0"></div>
<div style="position:absolute;left:-16px;right:-16px;bottom:-80px;height:150px;background:#1b1238;border-radius:40% 60% 0 0"></div>
<div style="position:relative;text-align:center;margin-top:40px;color:#fff"><div style="font:300 64px Sora;letter-spacing:-.03em">16:55</div><div style="font:600 13px Atk;opacity:.9">Sunday · sunset 16:58 · 1°</div></div>
<div style="position:relative;display:grid;place-items:center;margin-top:120px">${RING('#FAFAF4',64,22)}</div>
<div style="position:relative;margin:26px 8px 0;border-radius:18px;background:rgba(10,7,56,.45);backdrop-filter:blur(10px);padding:10px 12px;color:#fff;font:500 12px Atk"><b style="font:600 13px Sora">Tomorrow</b> · quiet day, first at 10:00</div>`;
const lk=(n,on,bg,sw)=>`<div class="r-look ${on?'on':''}"><div class="pv" style="background:${bg}"></div><b>${n}</b><div class="sw">${sw.map(c=>`<i style="background:${c}"></i>`).join('')}</div></div>`;
const p141b=`<div class="q-top">${I('back')}<b>Look & wallpaper</b></div>
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px">
${lk('Calm night',0,'linear-gradient(160deg,#0a0738,#1b1760)',['#0CF2BA','#C9B8FF'])}
${lk('Paper morning',0,'linear-gradient(160deg,#F6F4EC,#E6D3A8)',['#8C9A7B','#24412F'])}
${lk('Match wallpaper',1,'linear-gradient(180deg,#5b3d8f,#FF8FA3)',['#E9849A','#FFD0A8'])}
${lk('Lively',0,'linear-gradient(160deg,#2a1240,#FF9F6B)',['#FF9F6B','#FFE08A'])}
${lk('World',0,'linear-gradient(160deg,#CFE6FF,#9EC9FF)',['#4C7BD9','#0aa983'])}
${lk('Clear & big',0,'#ffffff',['#0B3D91','#060330'])}</div>
<div class="lr" style="margin-top:6px">${I('sky_day')}<div class="gr">Follow the sky<small>Dawn to night, season and weather</small></div><span class="tg on"></span></div>
<div class="lr">${I('calm_motion')}<div class="gr">Motion</div></div>
<div class="seg" style="margin:2px 0 6px"><div>Moves</div><div class="on">Gentle</div><div>Still</div></div>
<div class="lr">${I('text_size')}<div class="gr">Text size</div><span class="v">Large</span></div>`;
const p141c=`<div style="position:absolute;inset:-40px -16px -80px;background:linear-gradient(180deg,rgba(91,61,143,.55),rgba(10,7,56,1) 45%)"></div><div style="position:relative">
<div class="q-top">${I('look')}<b>Matched to your sunset</b></div>
<div class="q-blk" style="border-color:rgba(255,179,193,.45)"><h4 style="color:#E9849A">${I('calendar')}Tomorrow</h4><p>Quiet day · first at 10:00</p></div>
<div class="r-bt" style="background:#E9849A;color:#2a0b18">${I('play')}Wind down</div>
<div class="q-blk" style="margin-top:8px"><h4>${I('tint')}Accent from wallpaper</h4><p>Seed <b style="color:#E9849A">#E9849A</b> from the sunset · 7.5 : 1 on this background, so no change needed. Button text picked dark: 7.1 : 1.</p></div>
<div class="q-blk"><h4>${I('anchor')}Meaning colours stay</h4><div style="display:flex;gap:4px;flex-wrap:wrap">${chip('Done','m','check')}${chip('Needs you','a')}${chip('Help','l')}<span class="q-chip" style="background:rgba(255,143,163,.18);color:var(--err)">Error</span></div></div>
<div class="r-sheet" style="bottom:-6px;padding:10px 12px"><p class="r-p" style="color:var(--cream)">${I('tip','width:15px;height:15px;color:var(--lil);vertical-align:-3px')} Colours now come from your wallpaper. Text stays easy to read.</p></div></div>`;
const p141d=`<div style="text-align:center"><div class="q-top" style="justify-content:center"><b style="flex:none">Pick your sky</b></div></div>
<div class="q-bud"><div class="f">☀️</div><div class="t">Which sky do you like? It changes when the real sky does!</div></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
${['DAWN','DAY','GOLDEN','NIGHT'].map((k,i)=>`<div style="border-radius:18px;padding:4px;background:${i==2?'#FFD27A':'#fff'};border:2px solid #F6DDB4">${skyT(k,86)}</div>`).join('')}</div>
<div class="q-kbtn" style="margin-top:12px">${I('check')}This one!</div>`;
B13['141']=()=>`<div class="boardpg">${head13('batch 1 · style & wallpaper 13','A wallpaper that follows the sky, looks that match it','The 3-step wallpaper (morning, day, night) becomes six phases timed by today’s sunrise and sunset, with the season and the weather. “Match my wallpaper” builds the accent from it and checks contrast, while the meaning colours never change. Eight looks, three motion levels and a Kids sky picker.')}
<div class="r-two"><div class="r-ph">
${P(p141a,'Lock screen · dusk 16:55','real sunset time, weather, tomorrow',{nodock:1})}
${P(p141b,'Look & wallpaper','8 looks · follow the sky · motion · text')}
${P(p141c,'Match my wallpaper','accent from the photo, contrast checked')}
${P(p141d,'Kids · pick your sky','big choices, no settings words',{cls:'kid'})}
</div>
<div class="col" style="gap:14px">
<div class="box"><h3>${I('sky_day')}Six sky phases · SkyTimeline.kt</h3>
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:7px">${['DAWN','MORNING','DAY','GOLDEN','DUSK','NIGHT'].map(k=>skyT(k,74)).join('')}</div>
<p style="margin-top:8px">Times from today’s sunrise and sunset (weather source if allowed). No location? Riga times; halo never asks for location just for a wallpaper.</p></div>
<div class="box"><h3>${I('weather_mood')}Season + weather layers</h3><ul>
<li><b>Season</b>: winter frost, spring buds, summer haze, autumn leaves (southern hemisphere flips).</li>
<li><b>Weather</b>: clear, clouds, rain, snow, fog drawn on top, softly.</li>
<li><b>Motion</b>: Moves = slow drift · Gentle = crossfade on unlock · Still = changes only on unlock.</li></ul></div>
<div class="box"><h3>${I('contrast')}Contrast guard</h3><p>The accent is lightened (dark) or darkened (light) in 12 % steps until it reaches 3 : 1 against the surface. Its text colour is picked to reach 4.5 : 1. Red-like seeds are pulled toward apricot so nothing looks like an error.</p></div>
</div></div>
</div>`;

/* ───────── 142 · icons, motion, developer ───────── */
const ICN=['level_simple','level_standard','level_pro','plain_words','explain_this','adapts','one_action','why','pin_layout','reset_layout','wallpaper','sky_day','sky_night','season','weather_mood','look','text_size','contrast','calm_motion','tint','helper','show_more','show_less','tip','anchor'];
const LOT=[['level_shift','Level shift','Simple → Standard → Pro · 90 f'],['page_assembles','Page assembles','made for you · 75 f'],['explain_bloom','Explain this','plain words bubble · 60 f'],['sky_cycle','Sky follows the day','dawn → dusk → night · 120 f'],['tint_from_wallpaper','Colours from wallpaper','contrast checked · 75 f']];
B13['142']=()=>`<div class="boardpg">${head13('batch 1 · icons, motion, developer files','Everything a developer needs for batch 1','25 new icons on halo’s 24-px grid with a 1.75 stroke, as SVG and Android vector drawables. Five Lottie animations in dark and light at 256 × 256, 30 fps, named like the existing res/raw files. Code, data and strings sit in the repo’s own folders.')}
<div class="row" style="gap:24px">
<div style="flex:1.12" class="col">
<div class="lab">Icons · assets/halo/artwork/ic/halo_ic_*.svg · res/drawable/halo_ic_*.xml</div>
<div class="r-icg">${ICN.map(n=>`<div class="r-ic">${I(n)}<span>${n}</span></div>`).join('')}<div class="r-ic" style="border-style:dashed"><span style="margin-top:10px;font:600 11px Atk;color:var(--t2)">+ 140 existing</span></div><div class="r-ic" style="background:#F6F4EC">${I(ICN[2],'color:#14102a')}<span>light</span></div></div>
<div class="lab" style="margin-top:14px">Lottie · res/raw/halo13_motion_*_{dark,light}.json · frames shown left → right</div>
${LOT.map(([f,n,s])=>`<div class="r-strip"><b>${n}<small>${s}</small></b><img src="img/l/prev_${f}_dark.png"></div>`).join('')}
</div>
<div style="flex:1" class="col">
<div class="lab">Files in this batch</div>
<table class="q-tbl"><tr><th>Path</th><th>What it is</th></tr>
${[['docs/design/halo-13/FOUNDATIONS_13.md','The spec: rules, depths, dynamic UI, words, looks, sky, motion, QA'],['ui/adaptive/v13/ExperienceLevel.kt','Depths, signals, LevelAdvisor (offers, never switches)'],['ui/adaptive/v13/DynamicSurface.kt','Anchors, blocks, rules, SurfaceResolver (max 2 changes)'],['ui/plain/PlainWords.kt','Glossary lint + explain for “What’s this?”'],['test/…/PlainWordsRatchetTest.kt','Fails the build if tech words grow past 553'],['ui/theme/v13/LookEngine.kt','Seed from wallpaper, contrast guard, locked meaning colours'],['wallpaper/v13/SkyTimeline.kt','Six sky phases, season, weather'],['assets/plain/glossary-13.json','30 tech words → plain EN + RU, with explanations'],['assets/adaptive/v13/surfaces-13.json','9 screens × 3 depths + rules'],['assets/looks/looks-13.json','8 looks'],['res/values(-ru)/strings_foundations_13.xml','37 strings EN + RU'],['res/raw/halo13_motion_* · 10 files','5 animations × dark/light'],['…/ic + drawable · 50 files','25 icons × SVG/vector']].map(r=>`<tr><td><code>${r[0]}</code></td><td>${r[1]}</td></tr>`).join('')}</table>
<div class="code"><span class="c">// Compose: a screen asks for its surface once per open</span>
<span class="k">val</span> surface = remember(signals, pins) {
  SurfaceResolver.resolve(Surfaces.canvas, signals, pins)
}
surface.primary?.let { PrimaryAction(it) }        <span class="c">// exactly one</span>
surface.visible.forEach { Block(it) }
<span class="k">if</span> (surface.tucked.isNotEmpty()) ShowMore(surface.tucked)
<span class="k">if</span> (surface.applied.isNotEmpty()) WhyChip(surface.applied)</div>
</div></div>
${notes([['Icon rules','24 × 24, 1.75 stroke, round caps and joins, no fills. Light theme recolours by tint only.'],['Motion rules','Ease 0.33/0.67, nothing faster than 200 ms, Still motion shows the last frame.'],['Not compiled','The Kotlin is reference code for review, matched to repo 99aac2f names.'],['Next: batch 2','Onboarding rebuilt with these parts: depth question, plain words, try first.']])}
</div>`;
Object.assign(B,B13);
