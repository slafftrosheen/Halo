/* halo 13.0 · batch 3 — sources manager + planner (boards 149–154) */
const stc={m:'var(--mint)',a:'var(--ap)',e:'var(--err)',n:'var(--t3)'};
const srow=(ic,t,sub,st,tone,o={})=>`<div class="lr" style="min-height:48px">${o.col?`<span style="width:30px;height:30px;border-radius:10px;background:${o.col};display:grid;place-items:center;color:#14102a;flex:none">${I(ic,'width:17px;height:17px')}</span>`:I(ic)}<div class="gr">${t}${o.kid?` <span class="q-chip" style="padding:2px 6px;background:#FFD27A;color:#3a2608;font-size:9.5px">kids</span>`:''}<small>${sub}</small></div><span style="font:600 10.5px Atk;color:${stc[tone]};display:flex;align-items:center;gap:4px;white-space:nowrap"><i style="width:7px;height:7px;border-radius:50%;background:${stc[tone]}"></i>${st}</span></div>`;
const grp=t=>`<div class="grp">${t}</div>`;
const B13c={};

/* ───────── 149 · your sources ───────── */
const p149a=`<div class="q-top">${I('back')}<b>Your sources</b>${I('add')}</div>
<p class="sub" style="margin-bottom:2px">Everything halo reads from, in one place.</p>
${grp('Your own things · 4')}${srow('src_own','Notes, Planner, files','On this phone','On phone','m',{col:'#0CF2BA'})}
${grp('News & reading · 5')}${srow('src_news','LSM · Society','Public news · every hour','12 min ago','m',{col:'#9EC9FF'})}${srow('src_science','NASA','Science · few times a day','1 h ago','m',{col:'#C9B8FF'})}${srow('src_community','Hacker News','Community · every hour','Can’t reach','e',{col:'#E6D3A8'})}
${grp('Weather & warnings · 2')}${srow('weather_mood','Rīga','Forecast · every hour','8 min ago','m',{col:'#FFE08A',kid:1})}${srow('src_official','Latvia weather warnings','Official · tells you right away','Up to date','m',{col:'#FF9F6B',kid:1})}
${grp('Study · 2 · Kids-safe · 3')}`;
const p149b=`<div class="q-top">${I('back')}<b>LSM · Society</b>${I('more')}</div>
<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px">${chip('Public news','l','src_news')}${chip('Up to date · 12 min ago','m','check')}${chip('English')}</div>
<div class="lab" style="margin-bottom:5px">Latest</div>
${['Riga trams to run later on weekends','New library opens in Pārdaugava','School holidays: what changes'].map(t=>`<div class="lr" style="min-height:36px;font-size:12px">${I('read_mode')}<div class="gr">${t}</div></div>`).join('')}
<div class="lab" style="margin:10px 0 4px">halo uses it for</div>
<div class="lr">${I('sky_day')}<div class="gr">Today’s brief</div><span class="tg on"></span></div>
<div class="lr">${I('plain_words')}<div class="gr">Answers<small>Used 6 times this week</small></div><span class="tg on"></span></div>
<div class="lr">${I('kid_safe')}<div class="gr">OK for kids<small>Grown-up PIN needed</small></div><span class="tg"></span></div>
<div class="lr">${I('fresh')}<div class="gr">How often</div><span class="v">Every hour</span></div>
<div style="display:flex;gap:5px;margin-top:8px">${chip('Keep quiet')}${chip('Pause')}${chip('Remove')}</div>`;
const p149c=`<div class="q-top">${I('back')}<b>Add a source</b></div>
${[['add_link','Paste a link','Any site or news feed'],['src_news','Browse ready sources','18 picked by halo'],['tip','Follow a topic','“Riga transport”, “Mars”'],['weather_mood','Add a city','Weather, air and pollen'],['calendar','Add a calendar','A link or a file'],['src_book','Add a book or file','For Study']].map(([i,t,s],k)=>opt(i,t,s,k==0)).join('')}`;
const p149d=`<div class="q-top">${I('back')}<b>Paste a link</b></div>
<div class="q-comp" style="color:var(--cream)">${I('add_link')}rigasatiksme.lv</div>
<div class="q-blk" style="border-color:rgba(12,242,186,.45);margin-top:8px"><h4 style="color:var(--mint)">${I('check')}Found a news feed on this site</h4><p>Rīgas satiksme · News · updates a few times a day</p></div>
<div class="lab" style="margin:8px 0 4px">Latest from this source</div>
${['Route 3 changes from 1 December','Night buses on New Year’s Eve','New e-ticket app'].map(t=>`<div class="lr" style="min-height:34px;font-size:12px">${I('read_mode')}<div class="gr">${t}</div></div>`).join('')}
<div class="lab" style="margin:8px 0 4px">halo will use it for</div>
<div style="display:flex;gap:4px;flex-wrap:wrap">${chip('Today’s brief','m','check')}${chip('Answers','m','check')}${chip('Tell me right away')}</div>
<div class="r-bt" style="height:44px">Add Rīgas satiksme</div>`;
const p149e=`<div class="q-top">${I('back')}<b>Kids-safe sources</b></div>
<div class="q-blk" style="border-color:rgba(201,184,255,.4)"><h4 style="color:var(--lil)">${I('kid_safe')}Only what a grown-up approved</h4><p>Mia and Leo’s buddy answers only from these.</p></div>
${srow('src_science','NASA · Picture of the Day','Science','Up to date','m',{col:'#C9B8FF'})}${srow('weather_mood','Rīga weather','Forecast','Up to date','m',{col:'#FFE08A'})}${srow('src_book','Mia’s school books','3 files','On phone','m',{col:'#9EC9FF'})}
<div class="r-bt s" style="margin-top:10px">${I('add')}Approve another source</div><p class="sub" style="margin-top:6px;text-align:center">Needs your phone PIN</p>`;
B13c['149']=()=>`<div class="boardpg">${head13('batch 3 · sources manager','One calm place for everything halo reads from','Today, sources live in “Widgets & sources”, Study, the Library and Kids, and their states are technical (BACKOFF, QUOTA, NOT_MODIFIED). 13.0 puts them all in one list grouped the way people think, with a plain state, who stands behind each source and exactly what halo uses it for.')}
<div class="r-ph" style="justify-content:space-between">
${P(p149a,'Your sources','6 groups · plain state on every row')}
${P(p149b,'One source','latest, what halo uses it for, quiet controls')}
${P(p149c,'Add a source','six plain ways in')}
${P(p149d,'Paste a link','checked first: feed, sample, uses')}
${P(p149e,'Kids-safe','grown-up approves with the PIN')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.3"><h3>${I('fresh')}11 technical states → 9 plain ones · SourceManager.state()</h3>
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:3px 16px;font-size:12px;color:var(--t2)">${[['CURRENT · NOT_MODIFIED','Up to date','m'],['FETCHING','Updating…','n'],['LOCAL_ONLY','On this phone','m'],['OFFLINE','Waiting for internet','n'],['BACKOFF · QUOTA','Taking a break · tries at 14:20','n'],['FAILED ×1–2','Can’t reach it right now','e'],['FAILED ×3+','Needs you','a'],['DISABLED · CANCELLED','Paused','n'],['NEVER','New · first update soon','n']].map(([a,b,t])=>`<div style="padding:4px 0;border-bottom:1px solid var(--line)"><code style="font:500 10px ui-monospace,Menlo,monospace;color:var(--t3)">${a}</code><br><b style="color:${stc[t]}">${b}</b></div>`).join('')}</div></div>
<div class="box" style="flex:1"><h3>${I('src_official')}Who stands behind it — never a score</h3><p><b>Official</b> CERT.LV, Meteoalarm, ECB, WHO, GOV.UK · <b>Public news</b> LSM, BBC, Ars, The Verge · <b>Science</b> NASA, USGS, arXiv · <b>Community</b> Hacker News: “posts by people, ranked by people, check before you trust” · <b>Yours</b> · <b>Added by you</b>.</p></div>
</div></div>`;

/* ───────── 150 · sources at work ───────── */
const p150a=`<div class="q-top">${I('back')}<b>halo</b></div><div class="q-say">Are trams running late tonight?</div>
<p class="r-p" style="color:var(--cream)">From 1 December, weekend trams run until 01:00. Tonight is a weekday, so the last tram 11 leaves at 23:40.</p>
<div class="r-sheet" style="bottom:8px"><div class="r-grab"></div><h5>${I('sources')}What halo used</h5>
${srow('src_news','Rīgas satiksme','“Route changes from 1 Dec” · 2 h ago','Open','n',{col:'#9EC9FF'})}${srow('src_own','Your Planner','Tonight: dinner until 22:30','Open','n',{col:'#0CF2BA'})}
<p class="sub" style="margin-top:6px">Tap a source to see it, or keep it out of answers.</p></div>`;
const p150b=`<div class="q-top">${I('back')}<b>Hacker News</b></div>
<div class="q-blk" style="border-color:rgba(255,143,163,.45)"><h4 style="color:var(--err)">${I('failure')}Can’t reach it right now</h4><p>Three tries today didn’t work. The site may be down or may have moved its feed.</p></div>
<div class="q-blk"><h4>${I('retry')}What halo does</h4><p>It keeps the last 40 stories and tries again in an hour.</p></div>
<div class="lab" style="margin:8px 0 6px">What you can do</div>
<div class="r-bt" style="height:44px">${I('search')}Find the new address</div><div class="r-bt s">Pause it for now</div><div class="r-bt s" style="background:none">Remove</div>`;
const p150c=`<div class="q-top">${I('back')}<b>Today’s brief</b></div>
<p class="sub" style="margin-bottom:6px">What comes up in your morning brief.</p>
${[['src_news','LSM · Society',1],['src_news','BBC World',1],['src_science','NASA',0],['src_community','Hacker News',0],['weather_mood','Rīga weather',1],['src_official','Weather warnings',1]].map(([i,t,on])=>`<div class="lr">${I(i)}<div class="gr">${t}</div><span class="tg ${on?'on':''}"></span></div>`).join('')}
<div class="lab" style="margin:10px 0 4px">Tell me right away</div>
<div class="lr">${I('notifications')}<div class="gr">Weather warnings<small>Official only · max 3 a day</small></div><span class="tg on"></span></div>`;
const bar=(n,v,c)=>`<div style="display:flex;align-items:center;gap:8px;margin:5px 0;font-size:11.5px;color:var(--t2)"><span style="width:74px">${n}</span><i style="height:12px;border-radius:6px;background:${c};width:${v*28}px"></i><b style="color:var(--cream)">${v}</b></div>`;
const p150d=`<div class="q-top">${I('back')}<b>Your mix</b></div>
<p class="r-p">What your news comes from. Just so you can see it.</p>
<div class="q-blk" style="margin-top:8px">${bar('Latvia',3,'#9EC9FF')}${bar('World',1,'#C9B8FF')}${bar('Technology',4,'#E6D3A8')}${bar('Science',3,'#0CF2BA')}${bar('Official',2,'#FF9F6B')}</div>
<div class="q-blk"><h4>${I('your_mix')}Languages</h4><p>English 9 · Latvian 2 · Russian 1</p></div>
<div class="q-blk" style="opacity:.8"><h4>${I('tip')}Want more world news?</h4><p>Shown only if you open this page. halo never nudges you here.</p></div>`;
const t150=`<div class="tb"><div class="tsc"><div class="t-top"><b>Sources · health</b><span class="t-tab on">All 17</span><span class="t-tab">Needs you 1</span><span class="t-tab">Paused 2</span><span style="margin-left:auto" class="kbd">Pro</span></div>
<div style="padding:10px 14px;overflow:hidden"><table class="q-tbl"><tr><th>Source</th><th>State</th><th>Last update</th><th>Checks</th><th>Used · 7 days</th><th>Kids</th></tr>
${[['LSM · Society','Up to date','m','12 min','every hour','6','—'],['BBC World','Up to date','m','40 min','every hour','3','—'],['NASA','Up to date','m','1 h','few a day','1','yes'],['Hacker News','Can’t reach','e','6 h','every hour','0','—'],['arXiv · AI','Taking a break · 14:20','n','3 h','few a day','2','—'],['Latvia warnings','Up to date','m','5 min','every hour','—','yes'],['Rīga weather','Up to date','m','8 min','every hour','12','yes'],['Course PDFs','On this phone','m','—','—','9','—']].map(r=>`<tr><td><b>${r[0]}</b></td><td style="color:${stc[r[2]]};font-weight:700">${r[1]}</td><td>${r[3]}</td><td>${r[4]}</td><td>${r[5]}</td><td>${r[6]}</td></tr>`).join('')}</table></div></div></div>`;
B13c['150']=()=>`<div class="boardpg">${head13('batch 3 · sources at work','Every answer shows its sources, every source shows its use','“What halo used” on any answer leads back to the source, where you can keep it out of answers. When a source breaks, halo says what happened, what it did and what you can do. A gentle “Your mix” page shows where your news comes from, and Pro gets a health table on the tablet.')}
<div class="row" style="gap:22px;align-items:flex-start">
${P(p150a,'What halo used','answer → sources, one tap')}
${P(p150b,'When a source breaks','what happened · what halo did · what you can do')}
${P(p150c,'Today’s brief','pick what comes up, and what can alert you')}
${P(p150d,'Your mix','shown, never judged')}
<div class="col" style="flex:1;gap:12px;min-width:0">
<div class="lab">Pro · tablet · health table</div>
<div style="width:516px;height:324px"><div style="transform:scale(.6);transform-origin:0 0">${t150}</div></div>
<div class="code"><span class="c">// ui/sources/v13/SourceManager.kt</span>
<span class="k">fun</span> uses(s, alerts) = Uses(todayBrief = s.selected && !s.muted,
  answers = s.selected, tellMeRightAway = alerts && s.kind == ALERTS,
  kids = s.childApproved)   <span class="c">// grown-up PIN</span></div>
<div class="box"><h3>${I('anchor')}Source promises</h3><ul><li><b>Removing never breaks answers</b>: old answers keep their links.</li><li><b>Only official warnings</b> alert right away, max 3 a day.</li><li><b>https only</b>, feeds up to 2 MB, checked before adding.</li><li><b>Kids-safe needs the grown-up PIN.</b></li></ul></div>
</div></div></div>`;

/* ───────── 151 · planner views ───────── */
const ev=(t,s,c,top,h,o='')=>`<div style="position:absolute;left:${o||44}px;right:6px;top:${top}px;height:${h}px;border-radius:10px;background:${c};padding:5px 8px;font:600 11px Atk;color:#14102a;overflow:hidden">${t}<div style="font:500 10px Atk;opacity:.75">${s}</div></div>`;
const hours=(from,n,step=46)=>Array.from({length:n},(_,i)=>`<div style="position:absolute;left:0;right:0;top:${i*step}px;border-top:1px solid rgba(250,250,244,.07);font:600 9.5px Atk;color:var(--t3);padding-top:2px">${String(from+i).padStart(2,'0')}:00</div>`).join('');
const p151a=`<div class="q-top">${I('back')}<b>Today</b><span class="sub">Mon 23 Nov</span></div>
<div class="seg" style="margin-bottom:8px"><div class="on">Today</div><div>Day</div><div>Week</div><div>Month</div></div>
<div class="q-blk" style="border-color:rgba(255,159,107,.5)"><h4 style="color:var(--ap)">${I('leave_by')}Leave by 09:35</h4><p>Bus 3 in 6 min · Dentist, Brīvības 40</p></div>
${[['10:00','Dentist','calendar',0],['12:30','Lunch with Jānis','calendar',0],['','Pay rent · must do today','task',1],['','Library book','task',0],['15:30','Pick up Mia','family',0]].map(([t,n,i,m])=>`<div class="lr">${I(i)}<div class="gr">${n}${t?`<small>${t}</small>`:''}</div>${m?'<span class="chip a">today</span>':`<i style="width:16px;height:16px;border-radius:5px;border:1.5px solid var(--t3)"></i>`}</div>`).join('')}
<div class="q-comp" style="margin-top:8px">${I('mic')}Add: “Call Mum tomorrow at 10”</div>`;
const p151b=`<div class="q-top">${I('back')}<b>Mon 23 Nov</b>${I('find_time')}</div>
<div class="seg" style="margin-bottom:8px"><div>Today</div><div class="on">Day</div><div>Week</div><div>Month</div></div>
<div style="position:relative;height:410px">${hours(9,9)}
<div style="position:absolute;left:44px;right:6px;top:18px;height:14px;border-radius:7px;background:rgba(255,159,107,.22);font:700 9px/14px Atk;color:var(--ap);padding-left:8px">Leave by 09:35 · bus 3</div>
${ev('Dentist','10:00–10:45 · Brīvības 40','#9EC9FF',46,34)}${ev('Lunch with Jānis','12:30–13:30','#9EC9FF',161,46)}
${ev('Pay rent','time-block · 14:00','#0CF2BA',230,23)}${ev('Pick up Mia','15:30 · Family','#F3C0D9',299,30)}
<div style="position:absolute;left:44px;right:6px;top:92px;height:46px;border-radius:10px;border:2px dashed rgba(12,242,186,.55);font:600 10px Atk;color:var(--mint);display:grid;place-items:center">Free · 11:00–12:15</div>
<div style="position:absolute;left:0;right:0;top:118px;border-top:2px solid var(--ap)"><i style="position:absolute;left:38px;top:-5px;width:8px;height:8px;border-radius:50%;background:var(--ap)"></i></div></div>
<div style="position:absolute;left:16px;right:16px;bottom:8px;display:flex;gap:5px">${chip('Library book','','drag_block')}${chip('Call bank','','drag_block')}</div>`;
const wk=['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
const p151c=`<div class="q-top">${I('back')}<b>Week 48</b>${I('family_lane')}</div>
<div class="seg" style="margin-bottom:8px"><div>Today</div><div>Day</div><div class="on">Week</div><div>Month</div></div>
${wk.map((d,i)=>`<div style="display:flex;gap:8px;align-items:flex-start;padding:6px 0;border-bottom:1px solid var(--line)"><b style="width:30px;font:600 12px Sora;color:${i==0?'var(--mint)':'var(--cream)'}">${d}<br><span style="font:500 10px Atk;color:var(--t3)">${23+i>30?23+i-30:23+i}</span></b><div style="flex:1;display:flex;flex-wrap:wrap;gap:3px">${[[['Dentist','#9EC9FF'],['Mia','#F3C0D9']],[['Gym?','rgba(12,242,186,.25)']],[['Work trip','#9EC9FF']],[['Swimming 17:00','#F3C0D9']],[['Rent','#0CF2BA']],[['Market','#FFE08A'],['Grandma','#F3C0D9']],[]][i].map(([t,c])=>`<span style="font:600 10px Atk;padding:3px 7px;border-radius:7px;background:${c};color:#14102a">${t}</span>`).join('')||'<span style="font:500 10.5px Atk;color:var(--t3)">Free day</span>'}</div></div>`).join('')}
<div class="legend" style="margin-top:8px;font-size:10.5px"><span><i style="background:#9EC9FF;border-color:#9EC9FF"></i>Me</span><span><i style="background:#F3C0D9;border-color:#F3C0D9"></i>Family</span><span><i style="background:#0CF2BA;border-color:#0CF2BA"></i>Tasks</span></div>`;
const p151d=`<div class="q-top">${I('back')}<b>November</b></div>
<div class="seg" style="margin-bottom:8px"><div>Today</div><div>Day</div><div>Week</div><div class="on">Month</div></div>
<div style="display:grid;grid-template-columns:repeat(7,1fr);gap:3px;font:600 10px Atk;color:var(--t3);text-align:center">${wk.map(d=>`<span>${d[0]}</span>`).join('')}
${Array.from({length:35},(_,i)=>{const d=i-5;const on=d==23;const dots=[[2,4,9,23],[11,18,26],[23,27,30]];return `<div style="height:38px;border-radius:8px;background:${on?'rgba(12,242,186,.16)':'rgba(250,250,244,.04)'};color:${d<1||d>30?'transparent':on?'var(--mint)':'var(--cream)'};padding-top:4px">${d<1||d>30?'·':d}<div style="display:flex;gap:2px;justify-content:center;margin-top:3px">${dots.map((a,k)=>a.includes(d)?`<i style="width:5px;height:5px;border-radius:50%;background:${['#9EC9FF','#F3C0D9','#0CF2BA'][k]}"></i>`:'').join('')}</div></div>`}).join('')}</div>
<div class="q-blk" style="margin-top:10px"><h4>${I('calendar')}Mon 23 · 4 things</h4><p>Dentist · Lunch · Pay rent · Pick up Mia</p></div>`;
B13c['151']=()=>`<div class="boardpg">${head13('batch 3 · planner','One planner: today, a day you can drag, the week, the month','The repo already has Day, Week, Month, Agenda and Easy views over one organizer (events, tasks, reminders, alarms, timers). 13.0 gives them one clear switcher, a real timeline you can drag tasks into, “Leave by” ribbons, free time you can see, and Family next to Me.')}
<div class="r-ph" style="justify-content:space-between">
${P(p151a,'Today','the Easy view, grown up · Simple depth')}
${P(p151b,'Day · timeline','drag tasks in · free time · now line')}
${P(p151c,'Week','Me + Family in colour · free days')}
${P(p151d,'Month','dots, not text · tap a day to peek')}
<div class="col" style="width:300px;gap:12px">
<div class="box"><h3>${I('timeline')}Timeline rules</h3><ul><li><b>Snap to 15 min.</b></li><li><b>Tasks dropped in</b> become time-blocks, still tasks.</li><li><b>Other calendars are fixed</b>: a drag springs back and says why.</li><li><b>Overlaps</b> sit side by side.</li><li><b>Leave by</b> ribbon above anything with a place.</li></ul></div>
<div class="box"><h3>${I('level_pro')}By depth</h3><p><b>Simple</b>: Today + add by voice. <b>Standard</b>: Day, Week, Month. <b>Pro</b>: Agenda, calendars, time zones, ICS import and export, repeat rules.</p></div>
<div class="box"><h3>${I('plain_words')}Plain priorities</h3><p>URGENT → <b>Must do today</b> · HIGH → <b>Important</b> · NORMAL → <b>Normal</b> · LOW → <b>Some day</b>.</p></div>
</div></div></div>`;

/* ───────── 152 · add, find a time, too much ───────── */
const p152a=`<div class="q-top">${I('back')}<b>Add to Planner</b></div>
<div class="q-comp" style="color:var(--cream);height:auto;padding:9px 10px;line-height:1.35">Swimming with Mia every Thursday at 5</div>
<div class="q-blk" style="margin-top:10px;border-color:rgba(12,242,186,.45)"><h4>${I('calendar')}Swimming with Mia</h4>
<div class="lr" style="min-height:34px">${I('repeat')}<div class="gr">Every Thursday</div></div>
<div class="lr" style="min-height:34px">${I('fresh')}<div class="gr" style="color:var(--ap)">17:00 – 18:00</div><span class="chip a">check</span></div>
<div class="lr" style="min-height:34px">${I('family')}<div class="gr">Family calendar<small>Mia is in it</small></div></div>
<div class="lr" style="min-height:34px;border:0">${I('notifications')}<div class="gr">Remind 30 min before</div></div></div>
<div class="q-why" style="position:relative;left:0;right:0;margin-top:6px"><b>I guessed 5 pm. Change it?</b>“at 5” could be morning. <span style="color:var(--lil)">5:00 · 17:00</span></div>
<div class="r-bt">Check and save</div>`;
const p152b=`<div class="q-top">${I('back')}<b>Find a time</b></div>
<div class="q-say">1 hour for the gym this week</div>
<p class="sub" style="margin-bottom:6px">Pick one. Nothing is booked until you do.</p>
${[['Tue 24','07:30 – 08:30','before work'],['Thu 26','12:15 – 13:15','free lunch hour'],['Sat 28','10:00 – 11:00','quiet morning']].map(([d,t,w],i)=>`<div class="opt ${i==1?'on':''}" style="padding:9px 11px"><div class="oi">${I('find_time')}</div><div><b>${d} · ${t}</b><small>${w}</small></div></div>`).join('')}
<div style="display:flex;gap:5px;margin-top:4px">${chip('Make it weekly','','repeat')}${chip('Other times')}</div>
<div class="r-bt">Book Thursday</div>`;
const p152c=`<div class="q-top">${I('sky_day')}<b>Today</b></div>
<div class="q-blk" style="border-color:rgba(255,159,107,.5);background:rgba(255,159,107,.06)"><h4 style="color:var(--ap)">${I('too_much')}Today looks full</h4><p>About 7 h planned and 5 h free. Move 2 things to tomorrow? You choose which.</p>
${chk('Library book · 30 min',1)}${chk('Call bank · 20 min',1)}${chk('Pay rent · must do today',0)}
<div style="display:flex;gap:5px;margin-top:7px">${chip('Move 2 to tomorrow','a')}${chip('Keep it')}</div></div>
${blk('calendar','Next','<p>10:00 Dentist · 12:30 Lunch</p>')}`;
const p152d=`<div class="q-top">${I('back')}<b>Plan the trip</b>${I('more')}</div>
<div style="display:flex;gap:5px;margin-bottom:8px">${chip('Important','l')}${chip('Fri 27','','calendar')}</div>
<div class="lab" style="margin-bottom:4px">Steps · 2 of 5</div>
${[['Book train',1],['Ask Jānis about the car',1],['Pack Mia’s swim bag',0],['Print tickets',0],['Water the plants',0]].map(([t,d])=>chk(t,d)).join('')}
<div class="lab" style="margin:10px 0 4px">Linked</div>
<div class="lr">${I('notebook')}<div class="gr">Trip notes<small>Notes</small></div></div><div class="lr">${I('src_news')}<div class="gr">Train timetable<small>Rīgas satiksme</small></div></div>
<div class="q-comp" style="margin-top:8px">${I('add')}Add a step</div>`;
const p152e=`<div class="q-top">${I('back')}<b>Some day</b></div><p class="sub" style="margin-bottom:6px">Ideas with no date. halo won’t remind you.</p>
${['Paint the hallway','Learn 20 Italian phrases','Try the new pool','Fix Leo’s bike'].map(t=>`<div class="lr">${I('someday')}<div class="gr">${t}</div>${I('drag_block')}</div>`).join('')}
<div class="q-blk" style="margin-top:10px"><h4>${I('tip')}Free this Saturday morning</h4><p>Want to give one of these a time?</p></div>`;
B13c['152']=()=>`<div class="boardpg">${head13('batch 3 · planner · adding and help','Say it, check it, save it — and let halo find the time','Type or say it naturally. halo shows a card with what it understood, marks anything it guessed in apricot, and saves only when you tap. “Find a time” offers three calm options across the week. When a day is too full, halo suggests what could move, and you choose.')}
<div class="r-ph" style="justify-content:space-between">
${P(p152a,'Add in your own words','preview first · guesses in apricot')}
${P(p152b,'Find a time','three options, one per day, never auto-booked')}
${P(p152c,'Too much today?','suggests, never moves “must do”')}
${P(p152d,'A task with steps','steps, links to notes and sources')}
${P(p152e,'Some day','ideas without a date, no reminders')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="code" style="flex:1"><span class="c">// ui/planner/v13/NaturalAdd.kt · works with no AI brain</span>
NaturalAdd.parse(<span class="s">"Swimming with Mia every Thursday at 5"</span>, today)
<span class="c">// → Draft(title="Swimming with Mia", weekly=THURSDAY,</span>
<span class="c">//          time=17:00, family=true, unsure=["at 5"])</span>
NaturalAdd.parse(<span class="s">"Позвонить маме завтра в 10"</span>, today)
<span class="c">// → date=tomorrow, time=10:00</span></div>
<div class="code" style="flex:1"><span class="c">// ui/planner/v13/FindTime.kt</span>
FindTime.options(busy, now, days = <span class="n">7</span>, length = 1.hours,
  dayStart = <span class="n">08:00</span>, dayEnd = <span class="n">21:00</span>, buffer = <span class="n">15</span>.min)
<span class="c">// one option per day · 3 options · nothing booked</span>
TooMuchToday.suggest(DayLoad(planned, free), tasks)
<span class="c">// ≥ 90 % full → suggest; "must do today" never moves</span></div>
</div></div>`;

/* ───────── 153 · tablet, family, kids ───────── */
const t153=`<div class="tb"><div class="tsc"><div class="t-top"><b>Planner</b><span class="t-tab">Today</span><span class="t-tab">Day</span><span class="t-tab on">Week</span><span class="t-tab">Month</span><span style="margin-left:auto;display:flex;gap:8px">${I('find_time')}${I('family_lane')}${I('add')}</span></div>
<div class="t-main"><div class="t-work" style="padding:10px 12px"><div style="display:grid;grid-template-columns:40px repeat(7,1fr);gap:4px;height:100%">
<div></div>${wk.map((d,i)=>`<div style="font:600 11px Sora;text-align:center;color:${i==0?'var(--mint)':'var(--cream)'}">${d} ${23+i>30?23+i-30:23+i}</div>`).join('')}
${[9,11,13,15,17,19].map(h=>`<div style="font:600 9.5px Atk;color:var(--t3);height:58px;border-top:1px solid var(--line)">${h}:00</div>${wk.map((d,i)=>{const m={'0-9':['Dentist','#9EC9FF'],'0-15':['Pick up Mia','#F3C0D9'],'1-13':['Gym','#0CF2BA'],'2-9':['Work trip','#9EC9FF'],'2-11':['Work trip','#9EC9FF'],'3-17':['Swimming','#F3C0D9'],'4-13':['Pay rent','#0CF2BA'],'5-11':['Market','#FFE08A'],'6-13':['Grandma','#F3C0D9']}[i+'-'+h];return `<div style="border-top:1px solid var(--line);padding:3px">${m?`<div style="border-radius:7px;background:${m[1]};color:#14102a;font:600 10px Atk;padding:4px 5px">${m[0]}</div>`:''}</div>`}).join('')}`).join('')}
</div></div>
<div class="t-side"><h5>To do</h5>${['Library book','Call bank','Paint hallway','Book train'].map(t=>`<div class="t-lay">${I('drag_block','width:13px;height:13px;color:var(--t3);margin:0')}<span style="font-size:11px">${t}</span></div>`).join('')}<h5>Some day</h5><div class="t-lay"><span style="font-size:11px;color:var(--t3)">4 ideas</span></div><h5>Drag onto the week</h5><p style="color:var(--t3);font-size:10.5px;line-height:1.4">A task dropped on a day gets that time. Other calendars stay fixed.</p></div></div></div></div>`;
const p153b=`<div class="q-top">${I('back')}<b>Family · this week</b></div>
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin-bottom:8px">${[['Anna','#9EC9FF'],['Mia · 8','#F3C0D9'],['Leo · 5','#FFE08A']].map(([n,c])=>`<div style="text-align:center;font:600 11px Atk;padding:5px;border-radius:9px;background:${c};color:#14102a">${n}</div>`).join('')}</div>
${[['Thu',['Work 9–17','Swimming 17:00','Kindergarten']],['Fri',['Pay rent','School trip','—']],['Sat',['Market','Football 10:00','Football 10:00']]].map(([d,c])=>`<div style="display:grid;grid-template-columns:30px repeat(3,1fr);gap:5px;align-items:center;padding:6px 0;border-bottom:1px solid var(--line)"><b style="font:600 11px Sora">${d}</b>${c.map(x=>`<span style="font:500 10.5px Atk;color:var(--t2)">${x}</span>`).join('')}</div>`).join('')}
<div class="q-blk" style="margin-top:10px"><h4>${I('leave_by')}Saturday: two kids, one car</h4><p>Football 10:00 for both. Leave by 09:35.</p></div>
<div class="q-blk"><h4>${I('privacy')}What the kids see</h4><p>Only their own day, in pictures.</p></div>`;
const p153c=`<div class="q-bud"><div class="f">🦊</div><div class="t">Good morning, Mia! Here’s your day.</div></div>
${[['🏫','School','08:15'],['🏊','Swimming','17:00'],['📚','Homework time','16:00 – 18:00'],['🌙','Bedtime story','20:00']].map(([e,t,s],i)=>`<div style="display:flex;gap:10px;align-items:center;border-radius:18px;background:#fff;border:2px solid ${i==0?'#E0763F':'#F6DDB4'};padding:9px 12px;margin-bottom:7px;color:#2a1d10"><span style="font-size:30px">${e}</span><div><b style="font:600 15px Sora">${t}</b><div style="font:500 12px Atk;color:#6b5236">${s}</div></div>${i==0?'<span style="margin-left:auto;font:700 11px Atk;color:#E0763F">now</span>':''}</div>`).join('')}
<div class="q-kbtn">${I('mic')}Tell Fox something</div>`;
B13c['153']=()=>`<div class="boardpg">${head13('batch 3 · planner on tablet, for the family, for kids','A week on the tablet, a family at a glance, a child’s day in pictures','On the tablet the week sits beside a to-do list you drag from. The Family view puts everyone in columns and spots clashes. Kids see only their own day, in pictures, read by their buddy. Homework time comes from KidsHomeworkSchedule.')}
<div class="row" style="gap:24px;align-items:flex-start">${t153}
${P(p153b,'Family · this week','columns per person · clashes spotted')}
${P(p153c,'Kids · My day','pictures, read aloud, no points',{cls:'kid'})}
</div>
${notes([['Tablet','Week + to-do side panel, drag between them. Keyboard: T today, D day, W week, M month, N new, F find a time.'],['Family','Uses OrganizerCalendar.FAMILY. A clash card appears only for the same time and one grown-up.'],['Kids','A child’s day reads OrganizerCalendar.FAMILY items tagged with the child, plus their homework window. They can’t edit grown-up plans.'],['Glasses','The next thing + Leave by only, one line, from 12.0 devices.']])}
</div>`;

/* ───────── 154 · icons, motion, developer ───────── */
const ICN3=['src_news','src_official','src_science','src_community','src_own','src_book','kid_safe','fresh','paused_src','add_link','your_mix','timeline','week_view','month_view','drag_block','find_time','leave_by','too_much','someday','repeat','family_lane'];
const LOT3=[['source_refresh','Source refresh','arrows turn, dot goes mint · 75 f'],['add_source','Add a source','a new row drops in · 75 f'],['timeline_drag','Drag into your day','task finds the free slot · 90 f'],['find_time','Find a time','scan the week, 3 options · 90 f'],['leave_by','Leave by','countdown turns apricot · 90 f']];
B13c['154']=()=>`<div class="boardpg">${head13('batch 3 · icons, motion, developer files','Everything a developer needs for sources and Planner','21 new icons, 5 Lottie animations in dark and light, the sources manager and planner logic in Kotlin with tests, two data files and 51 strings in English and Russian.')}
<div class="row" style="gap:24px">
<div style="flex:1.12" class="col">
<div class="lab">Icons · assets/halo/artwork/ic + res/drawable</div>
<div class="r-icg">${ICN3.map(n=>`<div class="r-ic">${I(n)}<span>${n}</span></div>`).join('')}</div>
<div class="lab" style="margin-top:14px">Lottie · res/raw/halo13_motion_*_{dark,light}.json</div>
${LOT3.map(([f,n,s])=>`<div class="r-strip"><b>${n}<small>${s}</small></b><img src="img/l3/prev_${f}_dark.png"></div>`).join('')}
</div>
<div style="flex:1" class="col">
<div class="lab">Files in this batch</div>
<table class="q-tbl"><tr><th>Path</th><th>What it is</th></tr>
${[['docs/design/halo-13/SOURCES_PLANNER_13.md','The spec: groups, states, uses, adding, planner views, timeline, help, QA'],['ui/sources/v13/SourceManager.kt','Groups, plain states, kinds, uses, how often, mix'],['ui/sources/v13/AddSource.kt','Link check, feed discovery, kids-safe rule'],['ui/planner/v13/NaturalAdd.kt','EN + RU phrases → draft, guesses marked'],['ui/planner/v13/FindTime.kt','Three free slots across the week'],['ui/planner/v13/DayTimeline.kt','Overlap columns, snap, fixed events'],['ui/planner/v13/TooMuchToday.kt','Load, suggestions, never “must do”'],['test/…/PlannerV13Test.kt','5 tests: parse EN/RU, fixed, find, too much'],['assets/sources/sources-13.json','Groups, states, kinds, starter mapping'],['assets/planner/planner-13.json','Views, timeline, find, priorities, calendars'],['res/values(-ru)/strings_sources_planner_13.xml','51 strings EN + RU']].map(r=>`<tr><td><code>${r[0]}</code></td><td>${r[1]}</td></tr>`).join('')}</table>
<div class="box"><h3>${I('code')}Repo ties</h3><p>Sources wrap <code style="color:#B4E6D6">everyday/collectors</code> (CollectorSource, StarterSourceCatalogue, 18 starters). Planner wraps <code style="color:#B4E6D6">everyday/organizer</code> (OrganizerRecord, PlannerView DAY WEEK MONTH AGENDA EASY, leaveBeforeMinutes, OrganizerCalendar). No new storage.</p></div>
</div></div>
${notes([['No new database','Everything maps onto the existing collectors and organizer models.'],['Not compiled','Reference code matched to repo 99aac2f names, for review.'],['Natural add is a floor','Rule-based EN + RU works offline; an AI brain may refine, but the preview card always shows.'],['Next: batch 4','Canvas, Studio, Notes, Translate and Scan grow.']])}
</div>`;
Object.assign(B,B13c);
