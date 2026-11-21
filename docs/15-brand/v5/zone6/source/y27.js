/* halo 2027 · Quietly ahead — screens for boards 47–52 */
const SY={};
const ystep=(st,n,t,tag,tc)=>`<div class="ystep ${st}"><span class="n">${st=='d'?I('check'):n}</span><div>${t}</div>${tag?`<span class="tg ${tc||''}">${tag}</span>`:''}</div>`;
const yon=(t,c='')=>`<span class="yon ${c}"><i></i>${t}</span>`;
const RG=(s,c='#0CF2BA')=>`<svg viewBox="0 0 240 240" style="width:${s}px;height:${s}px"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="${c}" stroke-width="30" stroke-linecap="round" fill="none"/></svg>`;
/* ---------- 47 · Hand it to halo ---------- */
SY.plan=`<div class="crumb">${I('home')}Chat · today</div>
 <div class="yme">Book a table for 4 at Herbarium on Saturday at 7, and tell Anna.</div>
 <div class="yb"><div class="yk">${I('task')}Here’s how I’ll do it</div>
 ${ystep('',1,'Check Saturday in your calendar','reads','')}
 ${ystep('',2,'Find a table at Herbarium','web','')}
 ${ystep('',3,'Book for 4 at 19:00','asks you first','ap')}
 ${ystep('',4,'Tell Anna in Messages','asks you first','ap')}
 <div class="ytouch"><span>${I('calendar')}Calendar</span><span>${I('language')}Web</span><span>${I('send')}Messages</span></div></div>
 <div class="yrule">${I('approval')}halo always stops and asks before money, messages to people, deleting or posting.</div>`;
SY.working=`<div class="crumb">${I('task')}Table for Saturday</div><div class="ybig">On it.</div><div class="mi-s">You can leave — it keeps going and shows on your lock screen.</div>
 <div class="yb">${ystep('d',1,'Saturday is free after 18:00','','')}${ystep('c',2,'Finding a table at Herbarium','now','m')}${ystep('',3,'Book for 4 at 19:00','asks you first','ap')}${ystep('',4,'Tell Anna','asks you first','ap')}</div>
 <div class="ylive">${I('search')}19:00 and 19:30 are free for 4 people.</div>`;
SY.workIsl={cls:'work',r1:`<div class="spin"><svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="11" stroke="rgba(12,242,186,.2)" stroke-width="3" fill="none"/><path d="M14 3a11 11 0 0 1 11 11" stroke="#0CF2BA" stroke-width="3" stroke-linecap="round" fill="none"/></svg></div><div><div class="tt">Finding a table</div><div class="ts">Step 2 of 4</div></div><div class="btn stop" style="margin-left:auto;height:44px">${I('stop')}Stop</div>`,r2:`<div class="steps"><div class="s d">${I('check')}Calendar</div><div class="s c">${I('search')}Table</div><div class="s">Book</div><div class="s">Anna</div></div>`};
SY.ask=`<div class="crumb">${I('task')}Table for Saturday</div><div class="ybig">One thing to check</div>
 <div class="yb" style="border-color:rgba(255,159,107,.45)"><div class="yk" style="color:var(--ap)">${I('approval')}Waiting for you</div><div class="yt">Herbarium · Sat 23 Jan · 19:00</div><div class="ys">Table for 4 · no deposit · free to cancel until Friday 18:00</div></div>
 <div class="yb">${ystep('d',1,'Calendar checked','','')}${ystep('d',2,'Table found','','')}${ystep('c',3,'Book it — needs your OK','you','ap')}${ystep('',4,'Tell Anna','next','')}</div>`;
SY.askIsl={cls:'need',r1:`<div class="yir"><span class="apd">${I('approval')}</span><div><div class="tt">Book 19:00 for 4?</div><div class="ts">Free to cancel until Friday</div></div></div>`,r2:`<div class="btn" style="flex:1">${I('history')}Other time</div><div class="btn apb" style="flex:1.3">${I('check')}Book it</div>`};
SY.receipt=`<div class="crumb">${I('task')}Table for Saturday</div><div class="ybig">Done · 4 of 4</div><div class="mi-s">Here’s exactly what I did. Each one can be undone.</div>
 <div class="yb"><div class="yrc"><span class="ok2">${I('check')}</span><div>Booked Herbarium, Sat 19:00, 4 people<small>Cancel free until Fri 18:00</small></div><span class="u">Cancel</span></div>
 <div class="yrc"><span class="ok2">${I('check')}</span><div>Added to your calendar<small>Sat 19:00–21:30</small></div><span class="u">Remove</span></div>
 <div class="yrc"><span class="ok2">${I('check')}</span><div>Sent Anna: “Table for 4, Sat 7pm 🌿”<small>You approved it</small></div><span class="u">See</span></div>
 <div class="yrc"><span class="ok2">${I('check')}</span><div>Reminder on Saturday at 18:15<small>Leave time, with weather</small></div><span class="u">Change</span></div></div>`;
/* ---------- 48 · Outside the app ---------- */
const liveN=(top,big)=>`<div class="yntf" style="top:${top}px"><div class="hd"><span class="rg">${RG(20)}</span><b>halo</b>· Table for Saturday<span style="margin-left:auto">now</span></div>
 <div class="tl2">Finding a table · step 2 of 4</div><div class="sl">19:00 and 19:30 are free</div>
 <div class="yseg"><i class="d"></i><i class="c"></i><b class="ap"></b><i class="a"></i><b class="ap"></b><i class="a"></i></div>
 ${big?`<div class="sl" style="font-size:12.5px;margin-top:6px">Orange dots = I’ll ask you first</div><div class="ynb"><span>${'Stop'}</span><span class="p">Open</span></div>`:''}</div>`;
SY.lock=()=>home('drift',{layout:'none',state:'work',amt:.5,over:`<div class="yov ylockwrap"><div class="ychip"><i></i>Step 2/4</div><div class="ylock"><div class="tm">17:42</div><div class="dt">Thursday 21 January</div></div>${liveN(420,0)}<div class="yntf" style="top:560px;padding:10px 16px"><div class="hd"><span class="rg">${RG(20)}</span><b>halo</b>· nothing private shown on the lock screen</div></div></div>`});
SY.shade=()=>home('drift',{layout:'std',amt:.4,over:`<div class="yov"><div class="yshade"></div><div class="yqs"><span class="on">${I('mic')}</span><span>${I('bluetooth')}</span><span>${I('signal')}</span><span>${I('reminder')}</span></div>${liveN(120,1)}
 <div class="yntf" style="top:330px"><div class="hd"><span class="rg">${RG(20)}</span><b>halo</b>· While you were away</div><div class="ydg" style="margin-top:6px">
 <div class="row ap"><i></i><div>Anna asked if 7 still works<small>Waiting for you</small></div></div><div class="row"><i></i><div>Library book renewed<small>Done</small></div></div><div class="row"><i></i><div>Market list shared with Anna<small>Done</small></div></div></div></div></div>`});
SY.circle=`<div class="yweb"><div class="url">kerami.lv/planters/terra-14</div><div class="pimg"></div><h3>Terra planter, 14 cm</h3><div class="pr">€18.00</div><div class="tx">Hand-thrown stoneware, drainage hole, fits most small house plants.</div></div>
 <svg class="ycircle" viewBox="0 0 334 300"><path d="M40 60C80 10 260 4 305 50S330 250 250 285 30 280 14 190 8 100 40 60Z" stroke="#0CF2BA" stroke-width="5" fill="rgba(12,242,186,.07)" stroke-linecap="round" stroke-dasharray="0" /></svg>
 <div class="yisl"><div class="cx"><span>${I('language')}In Chrome</span><span>${I('select')}What you circled</span></div><div class="q">A stoneware planter, 14 cm</div><div class="a">Fits the small Monstera from your market list. Two stalls near you sell similar ones.</div>
 <div class="ch"><span class="p">${I('add')}Add to market list</span><span>${I('search')}Find it nearby</span><span>${I('explain')}Will it fit?</span></div>
 <div class="bar"><span class="stp">${I('stop')}Stop</span>${yon('Screen used once, not kept')}<span class="op">Open halo</span></div></div>`;
SY.widget27=()=>home('drift',{layout:'std',amt:.4,over:`<div class="yov"><div class="yntf" style="top:56px;height:168px;border-radius:28px;background:rgba(17,14,69,.82)"><div class="hd"><span class="rg">${RG(20)}</span><b>halo · Working</b><span style="margin-left:auto;color:var(--mint)">2 of 4</span></div><div class="tl2">Table for Saturday</div><div class="sl">Finding a table at Herbarium</div><div class="yseg"><i class="d"></i><i class="c"></i><b class="ap"></b><i class="a"></i><b class="ap"></b><i class="a"></i></div><div class="ynb" style="margin-top:8px"><span style="height:36px">Stop</span><span class="p" style="height:36px">Open</span></div></div></div>`});
/* ---------- 49 · Next for you ---------- */
SY.nextHome=`<div class="ttl" style="margin-top:30px">Good morning</div><div class="sub">Saturday · 4° in Riga · market day</div>
 <div class="ynext"><div class="yk">${I('guide_me')}Next for you</div><div class="yt">Leave by 9:20 for the market</div><div class="ys">Rain starts around 10 — the planters need the covers.</div>
 <div class="ybtns"><span>Not today</span><span class="p">${I('reminder')}Remind me 9:10</span></div><span class="why">${I('explain')}Why this?</span></div>
 <div class="sugg" style="margin-top:12px">${[['calendar','What’s on today?','Market 10:00 · dinner 19:00'],['new_note','Market list','6 of 8 ticked']].map(([i,t,s])=>`<div class="sgc"><div class="ti">${I(i)}</div><div><b>${t}</b><span>${s}</span></div>${I('chevron_down','i rot')}</div>`).join('')}</div>`;
SY.why=`<div class="crumb">${I('guide_me')}Next for you › Why this?</div><div class="mi-t" style="font-size:23px">Why I suggested this</div><div class="mi-s">Three things, all on this phone. Nothing was sent anywhere.</div>
 <div class="yb"><div class="yreason"><div class="ic">${I('calendar')}</div><div><b>Market stall at 10:00</b><span>From your calendar</span></div></div>
 <div class="yreason"><div class="ic">${I('cloud')}</div><div><b>Rain from about 10:00</b><span>Weather for Riga · updated 7:40</span></div></div>
 <div class="yreason"><div class="ic">${I('history')}</div><div><b>You usually leave 40 min early</b><span>Learned from the last 4 Saturdays · <span style="color:#C9B8FF">forget this</span></span></div></div></div>
 <div class="ybtns"><span>${I('close')}Fewer like this</span><span>${I('settings')}Change how helpful</span></div>`;
SY.wrap=`<div class="ttl" style="margin-top:30px">Today, done</div><div class="sub">Saturday · 21:48</div>
 <div class="yb"><div class="yk">${I('check')}Done today · 5</div><div class="ys" style="margin-top:4px">Market stall, dinner booked, Anna told, 2 reminders.</div></div>
 <div class="yb"><div class="yk">${I('history')}Left for tomorrow · 1</div><div class="yt">Price cards for next week</div><div class="ybtns"><span>Drop it</span><span class="p">${I('calendar')}Move to tomorrow</span></div></div>
 <div class="yb"><div class="yk">${I('cloud')}Tomorrow</div><div class="ys" style="margin-top:4px">Dry, 2°. Nothing booked before 11.</div></div>`;
SY.helpLevel=`<div class="crumb">${I('settings')}Settings › Next for you</div><div class="mi-t" style="font-size:23px">How helpful should halo be?</div>
 <div class="ylev"><span class="rd"></span><div><b>Only when I ask</b><span>No suggestions. halo waits for you.</span></div></div>
 <div class="ylev on"><span class="rd"></span><div><b>Suggest, I decide</b><span>One card at a time, at most 3 a day. Like “Leave by 9:20 — rain at 10”.</span></div></div>
 <div class="ylev"><span class="rd"></span><div><b>Quiet helper</b><span>Also does small safe things for you — renew a library book, set a leave-time reminder — and tells you after.</span></div></div>
 <div class="lbl3">Never suggest while</div><div class="ytouch"><span>${I('reminder')}Sleeping</span><span>${I('signal')}Driving</span><span>${I('calendar')}In a meeting</span><span>${I('add')}Add</span></div>`;
/* ---------- 50 · Trust you can see ---------- */
SY.answers=`<div class="crumb">${I('home')}Chat · today</div>
 <div class="yme">What did I spend at the market last month?</div>
 <div class="yans">€142 across 4 Saturdays — most on stoneware pots (€64).<div class="ft">${yon('On this phone')}<span class="wy">How did you know?</span></div></div>
 <div class="yme">Will it rain at 10?</div>
 <div class="yans">Light rain from about 10:00, about 2 hours.<div class="ft">${yon('Used online · weather','web')}<span class="wy">What was sent?</span></div></div>
 <div class="yb" style="border-color:rgba(201,184,255,.4)"><div class="yk" style="color:#C9B8FF">${I('explain')}What was sent?</div><div class="ys" style="margin-top:4px">Only “Riga, Saturday 10:00” to the weather service. Not your name, not your messages.</div></div>`;
SY.glance=`<div class="crumb">${I('privacy')}Places › Privacy</div><div class="mi-t" style="font-size:23px">Privacy at a glance</div><div class="mi-s">This week, in plain numbers.</div>
 <div class="ystat"><div><b>41</b><span>answers made on this phone</span></div><div><b>6</b><span>used online (weather, web)</span></div><div><b>0</b><span>shared with people or apps</span></div></div>
 <div class="lbl3">What halo can use</div><div class="yb" style="padding:2px 14px">
 <div class="ysw">${I('calendar')}<div>Calendar<small>Used today 9:02</small></div><span class="yt2"></span></div>
 <div class="ysw">${I('send')}<div>Messages<small>Always asks before sending</small></div><span class="yt2"></span></div>
 <div class="ysw">${I('camera')}<div>Camera & screen<small>Only when you point or circle</small></div><span class="yt2"></span></div>
 <div class="ysw">${I('history')}<div>Learn my habits<small>4 things learned · see them</small></div><span class="yt2 off"></span></div></div>`;
SY.pause=`<div class="mi-t" style="margin-top:14px">Pause halo</div><div class="mi-s">Nothing listens, learns or runs. Widgets say “Paused”.</div>
 <div class="ypause"><div class="rg">${RG(96,'#B7B9D3')}</div><b>halo is paused</b><span>Back on at 19:00 — or tap Resume</span>
 <div class="ychips"><span>30 min</span><span class="on">1 hour</span><span>Until tomorrow</span></div></div>
 <div class="yrule" style="justify-content:center">${I('reminder')}Alarms and timers you already set still ring.</div>`;
SY.priv=`<div class="crumb">${I('privacy')}Private chat</div>
 <div class="yb" style="border-style:dashed;border-color:rgba(250,250,244,.35);text-align:center"><div style="display:flex;justify-content:center;margin-bottom:6px">${yon('Private · nothing kept','priv')}</div><div class="ys">No memory, no history, no suggestions from this chat. It disappears when you close it.</div></div>
 <div class="yme">How do I tell my sister I can’t lend her money?</div>
 <div class="yans">Be kind and clear, and keep it short. For example: “I love you and I wish I could, but I can’t lend money right now.”<div class="ft">${yon('On this phone')}</div></div>`;
/* ---------- 51 · Across your things ---------- */
SY.cont=`<div class="crumb">${I('task')}Table for Saturday</div><div class="mi-t" style="font-size:23px">Keep going on another device</div><div class="mi-s">halo follows you. The answer goes where you are.</div>
 <div class="ycont"><span class="on">${I('watch')}Watch<br>on your wrist</span><span>${I('glasses')}Glasses</span><span>${I('devices')}Tablet</span></div>
 <div class="yb"><div class="ysw" style="border:0">${I('routines')}<div>Follow me<small>Questions move to the device you’re using</small></div><span class="yt2"></span></div>
 <div class="ysw">${I('approval')}<div>Approve on watch<small>Small OKs only — never payments</small></div><span class="yt2"></span></div></div>`;
SY.watch=`<div class="ylab" style="margin-top:14px">On your watch</div><div class="ywatch"><span class="rg">${RG(30,'#FF9F6B')}</span><b>Tell Anna<br>“Table for 4, Sat 7pm”?</b><div class="wb"><span>Edit</span><span class="ap">Send</span></div></div>
 <div class="ylab" style="margin-top:14px">Through your glasses</div><div class="yglass"><div class="hud"><b>Herbarium · 300 m</b>Turn left on Ģertrūdes iela. Table at 19:00.</div></div>`;
SY.devices27=`<div class="crumb">${I('devices')}Places › Devices</div><div class="mi-t" style="font-size:23px">Your things</div>
 <div class="yb" style="padding:2px 14px">
 <div class="ysw">${I('watch')}<div>Watch<small>Ready · shows asks & timers</small></div>${yon('Ready')}</div>
 <div class="ysw">${I('glasses')}<div>Glasses<small>Ready · directions, captions</small></div>${yon('Ready')}</div>
 <div class="ysw">${I('headphones')}<div>Earbuds<small>halo talks here first</small></div>${yon('In use')}</div>
 <div class="ysw">${I('devices')}<div>Tablet<small>Same Library, bigger Studio</small></div><span class="yon web"><i></i>Asleep</span></div></div>
 <div class="ybtns"><span>${I('add_device')}Add a device</span></div>`;
const ySBD=`<div class="sb" style="color:#1d1f24"><span>9:41</span><span class="r"><i style="background:#1d1f24"></i><i style="background:#1d1f24"></i><i style="background:#1d1f24"></i></span></div>`;
