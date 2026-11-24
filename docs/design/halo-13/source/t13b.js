/* halo 13.0 · batch 2 — onboarding (boards 143–148) */
const dots=(n,on)=>`<div class="dots" style="margin:4px 0 10px">${Array.from({length:n},(_,i)=>`<i class="${i==on?'on':''}"></i>`).join('')}</div>`;
const skip=`<span class="sub" style="margin-left:auto">Just open halo</span>`;
const obTop=(n,back=1)=>`<div class="q-top">${back?I('back'):'<span style="width:18px"></span>'}<b></b>${skip}</div>${dots(4,n)}`;
const opt=(ic,t,s,on,col)=>`<div class="opt ${on?'on':''}" style="padding:9px 11px;margin-bottom:7px"><div class="oi" style="${col?`background:${col};color:#14102a`:''}">${I(ic)}</div><div style="flex:1;min-width:0"><b>${t}</b><small>${s}</small></div>${on?`<span style="color:var(--mint)">${I('check')}</span>`:''}</div>`;
const B13b={};

/* ───────── 143 · onboarding brief ───────── */
B13b['143']=()=>`<div class="boardpg">${head13('batch 2 · onboarding','From 10 technical stages to 4 human steps','Today halo asks about AI services, listening downloads, permissions, devices and privacy before you have tried anything. 13.0 says hello, asks who halo is for and how much to show, offers a plain choice about where halo thinks, and opens. Everything else is asked when you first use it, or offered later as a small card.')}
<div class="q-grid3" style="grid-template-columns:1.25fr 1fr 1fr">
<div class="q-col"><h2>${I('review')}Today’s 10 stages → where they go</h2>
<div class="lab" style="margin-bottom:6px">ui/onboarding/SetupProgressStore.kt · SetupStage</div>
<table class="q-tbl"><tr><th>Today</th><th>13.0</th></tr>
${[['1 Welcome to Halo · 2 How would you like to start?','<b>Hello</b> + <b>Who is halo for?</b>'],['(new)','<b>How much do you want to see?</b> Simple · Standard · Pro'],['4 How Halo answers','<b>Where should halo think?</b> On this phone · online · later'],['3 You choose the access','Asked when a feature is first used, never up front'],['5 Listening and speaking','First tap on the ring, or the card “Let halo hear you”'],['6 Your devices','When a device is near, or the card “Use halo on your glasses”'],['7 Your data, your choice','One plain line on step 4 + Settings → What halo can use'],['8 Your day · 9 Let’s check · 10 Try Halo for real','<b>The first real try on Home</b>, then one card a day']].map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td></tr>`).join('')}</table></div>
<div class="q-col"><h2>${I('guide_me')}Rules for the first ten minutes</h2>
${[['try_it','<b>Useful in under 60 seconds.</b> From the first screen to a real answer on Home.'],['not_now','<b>“Just open halo” on every step.</b> Skipping gives sensible defaults, never a lesser halo.'],['ask_access','<b>No permission before its feature.</b> halo explains first, then Android asks.'],['text_size','<b>Comfort first.</b> Larger text, read aloud, contrast and less motion on the very first screen, applied live.'],['plain_words','<b>Plain words only.</b> No “model”, “provider”, “runtime” or “diagnostics” in onboarding.'],['setup_card','<b>One card a day, at most.</b> Each is a benefit, not a chore. Dismissed twice → gone.']].map(([i,t])=>li2(i,t)).join('')}
</div>
<div class="q-col"><h2>${I('one_action')}How we’ll know it works</h2>
<div class="q-grid3" style="grid-template-columns:1fr 1fr;gap:10px">
<div class="r-stat m"><b>4</b><span>steps at most (was 10)</span></div>
<div class="r-stat m"><b>&lt; 60 s</b><span>to the first real answer</span></div>
<div class="r-stat m"><b>0</b><span>permission prompts during onboarding</span></div>
<div class="r-stat m"><b>0</b><span>tech words (ratchet test, onboarding strings)</span></div></div>
<div class="box" style="margin-top:12px"><h3>${I('helper')}Test with real people</h3><ul><li><b>A 70-year-old</b> with large text, no help.</li><li><b>A parent</b> setting up a 6-year-old.</li><li><b>A student</b> who skips everything.</li><li><b>A TalkBack user</b>, eyes closed.</li></ul><p style="margin-top:6px">Each must reach a real answer without asking anyone.</p></div>
</div></div>
<div style="margin-top:26px" class="q-pipe">${[['1','Hello','Language from the phone, comfort switches, “Let’s start”, or “I’ve used halo before”.'],['2','Who is halo for?','Me · My child · My family · Studying · Creating. Several allowed.'],['3','How much to see?','Simple · Standard · Pro, with a live preview.'],['4','Where halo thinks','On this phone · online service · decide later. One privacy line.'],['→','Home: first try','A real suggestion that fits who it’s for. One setup card from tomorrow.']].map(([n,b,p])=>`<div class="q-st"><div class="n">${n=='→'?'THEN':'STEP '+n}</div><b>${b}</b><p>${p}</p></div>`).join('')}</div>
</div>`;

/* ───────── 144 · the four steps ───────── */
const p144a=`${obTop(0,0)}<div style="display:grid;place-items:center;height:118px">${RING('#0CF2BA',100,26)}</div>
<div class="r-big" style="text-align:center">Hi, I’m halo</div><p class="r-p" style="text-align:center">I can help plan your day, explain things, draw, translate and more.</p>
<div class="lab" style="margin:12px 0 6px">Make it easier to use</div>
<div style="display:flex;flex-wrap:wrap;gap:4px">${chip('Larger text','m','text_size')}${chip('Read aloud','','speaker')}${chip('Stronger contrast','','contrast')}${chip('Less motion','','calm_motion')}</div>
<div class="r-bt" style="margin-top:12px">Let’s start</div><p class="sub" style="text-align:center;margin-top:8px">I’ve used halo before · English ▾</p>`;
const p144b=`${obTop(1)}<div class="r-big">Who is halo for?</div><p class="sub" style="margin-bottom:8px">Pick all that fit. You can change this later.</p>
${opt('for_me','Me','My day, notes, questions',1,'#0CF2BA')}${opt('for_child','My child','A safe space for ages 4–12',0,'#FFD27A')}${opt('family','My family','Shared plans, kids’ profiles',0,'#F3C0D9')}${opt('studying','Studying','School, uni, exams, languages',1,'#9EC9FF')}${opt('creating','Creating','Drawing, photos, design',0,'#C9B8FF')}
<div class="r-bt" style="margin-top:4px">Next</div>`;
const mini=(n,on)=>`<div style="flex:1;border-radius:12px;padding:6px;border:1.5px solid ${on?'var(--mint)':'rgba(250,250,244,.12)'};background:${on?'rgba(12,242,186,.07)':'rgba(250,250,244,.03)'}"><div style="height:62px;border-radius:7px;background:#F6F4EC;margin-bottom:5px;display:grid;grid-template-columns:repeat(${n},1fr);gap:3px;padding:4px">${Array.from({length:n*n},()=>`<i style="background:#d9f8ee;border-radius:3px"></i>`).join('')}</div></div>`;
const p144c=`${obTop(2)}<div class="r-big">How much do you want to see?</div>
<div style="display:flex;gap:6px;margin:6px 0 10px">${mini(1,0)}${mini(2,1)}${mini(3,0)}</div>
${opt('level_simple','Keep it simple','The main things, big and clear.',0)}${opt('level_standard','A bit of everything','The usual halo. More is one tap away.',1)}${opt('level_pro','Show me everything','All tools, menus and shortcuts.',0)}
<p class="sub">You can change this any time, for each tool too.</p><div class="r-bt">Next</div>`;
const p144d=`${obTop(3)}<div class="r-big">Where should halo think?</div>
${opt('on_device','On this phone','Private and free. One download on Wi-Fi.',1)}${opt('cloud','With an online AI service','Faster start. Needs an account with a service.',0)}${opt('finish_later','Decide later','Start with simple things now.',0)}
<div class="q-blk" style="border-color:rgba(201,184,255,.4)"><h4>${I('privacy')}Your privacy</h4><p>Nothing you say or show leaves this phone unless you choose an online service.</p></div>
<div class="r-bt">Open halo</div><p class="sub" style="text-align:center;margin-top:6px">Download starts on Wi-Fi · size shown first</p>`;
const p144e=`<div class="q-top">${I('sky_day')}<b>Good morning, Anna</b></div>
<div class="r-big" style="font-size:20px">Try it: what’s on today?</div><p class="sub" style="margin-bottom:8px">Tap a suggestion or say anything.</p>
${['Plan my day','Explain a photo of my homework','Make a shopping list','What’s the weather?'].map((t,i)=>`<div class="q-blk" style="${i==0?'border-color:rgba(12,242,186,.5)':''}"><h4>${I(i==1?'camera':'tip')}${t}</h4></div>`).join('')}
<div class="q-comp" style="margin-top:8px">${I('mic')}Say or type anything…</div>
<div class="q-blk" style="margin-top:10px;border-style:dashed;opacity:.75"><h4>${I('setup_card')}Tomorrow: “Let halo hear you”</h4><p>One small setup card a day, from day two.</p></div>`;
B13b['144']=()=>`<div class="boardpg">${head13('batch 2 · the four steps','Hello, who for, how much, where to think — then try it','Each step is one question with big answers, “Just open halo” in the corner and dots instead of “5 / 10”. Comfort settings come first and apply live. The last step lands on Home with a real suggestion that fits who halo is for.')}
<div class="r-ph" style="justify-content:space-between">
${P(p144a,'1 · Hello','comfort first, applied live',{nodock:1})}
${P(p144b,'2 · Who is halo for?','several allowed · sets Home and tools',{nodock:1})}
${P(p144c,'3 · How much to see?','live preview of the depth',{nodock:1})}
${P(p144d,'4 · Where halo thinks','plain choice, one privacy line',{nodock:1})}
${P(p144e,'Home · the first try','a real answer in under a minute')}
</div>
${notes([['Who for → what changes','Me: Today first. Studying: Study in the dock + “explain a photo”. Creating: Canvas in the dock. My child: goes to the hand-over. Family: shared Planner + Kids card.'],['Thinking, plainly','“On this phone” starts a Wi-Fi download in the background; halo works for notes, drawing, timers and Planner meanwhile. Online opens the service sign-in only after Home.'],['TalkBack and read aloud','Every step reads its question and options. Large answers are 64 dp. Swipe right = next option, double-tap = choose.'],['Back always works','Back never loses a choice. Leaving mid-way saves it and Home opens with defaults.']])}
</div>`;

/* ───────── 145 · ask when needed + setup cards ───────── */
const askSheet=(ic,t,why,no,once)=>`<div class="r-sheet"><div class="r-grab"></div><h5>${I(ic)}${t}</h5><p class="r-p">${why}</p><p class="sub" style="margin-top:6px">${I('helper','width:13px;height:13px;vertical-align:-2px')} If not: ${no}</p>
<div class="r-bt" style="height:42px">Allow</div>${once?'<div class="r-bt s" style="margin-top:6px">Only this time</div>':''}<div class="r-bt s" style="margin-top:6px;background:none">Not now</div></div>`;
const p145a=`<div class="q-top">${I('back')}<b>halo</b></div><div class="q-say">What’s on my calendar tomorrow?</div><div style="display:grid;place-items:center;height:120px">${RING('#C9B8FF',80,24)}</div>${askSheet('mic','Let halo hear you?','So halo can hear you when you tap the ring. What you say stays on this phone.','you can still type.',1)}`;
const p145b=`<div class="q-top">${I('back')}<b>Translate</b></div><div class="pic" style="height:180px;background-image:url(img/market.jpg);border-radius:16px"></div>${askSheet('camera','Use the camera?','To read signs and menus you point at. Photos stay on this phone unless you share them.','pick a photo from your gallery instead.',1)}`;
const p145c=`<div class="q-top">${I('back')}<b>Planner</b></div>
<div class="q-blk"><h4>${I('calendar')}Today</h4><p>10:00 Call with Jānis · 15:30 Pick up Mia</p></div>
<div class="q-blk" style="border-color:rgba(255,159,107,.45)"><h4 style="color:var(--ap)">${I('notifications')}Reminders are off</h4><p>Your phone won’t ask again. Turning them on takes two taps.</p><div style="display:flex;gap:5px;margin-top:7px">${chip('Open phone settings','a')}${chip('Keep off')}</div></div>
<div class="q-blk" style="opacity:.7"><h4>${I('helper')}Why it matters</h4><p>“Leave by” can’t remind you in time while halo is closed.</p></div>`;
const p145d=`<div class="q-top">${I('sky_day')}<b>Good morning</b><span class="sub">Day 2</span></div>
${blk('calendar','First today','<p>10:00 Call with Jānis</p>')}
<div class="q-blk" style="border-color:rgba(201,184,255,.5);background:rgba(201,184,255,.08)"><h4 style="color:var(--lil)">${I('mic')}Let halo hear you</h4><p>Talk instead of typing. Works without the internet.</p><div style="display:flex;gap:5px;margin-top:7px">${chip('Try it','l')}${chip('Maybe later')}</div></div>
${blk('cloud','Weather','<p>Dry until 15:00 · 2°</p>')}${blk('task','Also today','<p>Pay rent · Library book</p>')}`;
const p145e=`<div class="q-top">${I('back')}<b>What halo can use</b></div>
<p class="sub" style="margin-bottom:6px">Turn things on or off. halo explains each one.</p>
${[['mic','Hear you','When you tap the ring',1],['camera','Camera','Scan, translate, homework',1],['calendar','Your calendar','Planner',0],['notifications','Reminders','Leave by, timers',0],['location','Where you are','Only when you ask',0],['bluetooth','Nearby devices','Glasses, headphones',1]].map(([i,t,s,on])=>`<div class="lr">${I(i)}<div class="gr">${t}<small>${s}</small></div><span class="tg ${on?'on':''}"></span></div>`).join('')}
<div class="lr">${I('show_more')}<div class="gr">More for experts<small>11 more · Pro</small></div></div>`;
B13b['145']=()=>`<div class="boardpg">${head13('batch 2 · ask when needed','Permissions only when you reach for the feature','No permission is asked during onboarding. When you first tap the mic, open the camera or add a reminder, a halo sheet explains what for, what stays on the phone and what happens if you say no. Then Android asks. If Android stops asking, halo shows the two-tap way in Settings, never a nag.')}
<div class="r-ph" style="justify-content:space-between">
${P(p145a,'First tap on the ring','halo explains, then Android asks')}
${P(p145b,'First camera use','“Only this time” where Android allows')}
${P(p145c,'Said no twice','a calm two-tap way back, inside the feature')}
${P(p145d,'Setup card · day 2','one a day, a benefit, never a chore')}
${P(p145e,'Settings · What halo can use','6 plain rows; 11 more for experts')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="code" style="flex:1"><span class="c">// ui/onboarding/v13/AskWhenNeeded.kt</span>
<span class="k">fun</span> next(granted: Boolean, h: AskHistory, startedByPerson: Boolean) = <span class="k">when</span> {
  granted                          -> DONT_ASK
  !startedByPerson || h.pending    -> DONT_ASK            <span class="c">// no surprise prompts</span>
  h.androidDenials >= <span class="n">2</span>          -> SHOW_SETTINGS_PATH
  h.notNowCount >= <span class="n">3</span>             -> DONT_ASK            <span class="c">// quiet “Turn on” stays</span>
  <span class="k">else</span>                             -> SHOW_HALO_SHEET
}</div>
<div class="box" style="flex:1.1"><h3>${I('ask_access')}17 kinds of access, in plain words · ask-when-needed.json</h3><p>Microphone → <b>Talk to halo</b> · Camera → <b>Scan, translate signs, homework photos</b> · Notifications → <b>Reminders and Leave by</b> · Calendar → <b>Planner</b> · Location → <b>Weather, Leave by, maps</b> (only when you ask) · Bluetooth → <b>Glasses, headphones, watch</b> · and 11 more for Pro. Each entry has “why”, “if not”, EN + RU.</p></div>
</div></div>`;

/* ───────── 146 · kids & family ───────── */
const p146a=`<div class="q-top">${I('back')}<b>Set up for your child</b></div>${dots(3,0)}
<div style="display:grid;place-items:center;height:96px"><span style="width:72px;height:72px;border-radius:24px;background:rgba(201,184,255,.14);display:grid;place-items:center;color:var(--lil)">${I('privacy','width:36px;height:36px')}</span></div>
<div class="r-big" style="text-align:center">First, a grown-up check</div><p class="r-p" style="text-align:center">Use your phone’s PIN or fingerprint. Only a grown-up can change the kids’ settings later.</p>
<div class="r-bt" style="margin-top:16px">Confirm it’s me</div>`;
const p146b=`<div class="q-top">${I('back')}<b>About your child</b></div>${dots(3,1)}
<div class="lab" style="margin-bottom:5px">What should halo call your child?</div><div class="q-comp" style="color:var(--cream)">Mia</div>
<div class="lab" style="margin:12px 0 5px">How old?</div><div class="seg"><div>4–6</div><div class="on">7–9</div><div>10–12</div></div>
<div class="lab" style="margin:12px 0 5px">A few rules (you can change them)</div>
${[['timer','Homework time','16:00–18:00'],['camera','Homework photos','Ask me first'],['sources','Where answers come from','Kids-safe sources'],['privacy','No ads, no points, no chat with strangers','Always']].map(([i,t,v])=>`<div class="lr">${I(i)}<div class="gr">${t}</div><span class="v">${v}</span></div>`).join('')}
<div class="r-bt">Next</div>`;
const p146c=`<div style="position:absolute;inset:-40px -16px -80px;background:radial-gradient(80% 50% at 50% 30%,#FFE7A8,#FFF7E6)"></div><div style="position:relative;text-align:center;color:#2a1d10;padding-top:30px">
<div style="font:600 26px/1.15 Sora">Now hand the phone to Mia</div><p style="font:500 13px Atk;color:#6b5236;margin-top:8px">Her buddy will take it from here.</p>
<div style="display:grid;place-items:center;height:220px">${I('hand_over','width:120px;height:120px;color:#E0763F')}</div>
<div class="q-kbtn" style="margin:0 10px">${I('check')}I’m Mia!</div><p style="font:500 11.5px Atk;color:#8a6b44;margin-top:10px">Grown-ups: hold the corner for 3 s to go back</p></div>`;
const p146d=`<div class="q-bud" style="margin-top:6px"><div class="f">🦊</div><div class="t">Hi Mia! I’m Fox, your buddy. What do you like? Pick three!</div></div>
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:7px;margin-top:6px">${[['🐶','Animals',1],['🚀','Space',1],['🎨','Drawing',0],['🌳','Nature',0],['🔢','Numbers',0],['🎵','Music',1],['🦕','Dinosaurs',0],['🏰','Stories',0],['⚽','Sport',0]].map(([e,t,on])=>`<div style="border-radius:18px;background:#fff;border:2.5px solid ${on?'#E0763F':'#F6DDB4'};padding:9px 0;text-align:center;font:600 11.5px Atk;color:#2a1d10"><div style="font-size:28px;line-height:1.1">${e}</div>${t}</div>`).join('')}</div>
<div class="q-kbtn" style="margin-top:12px">${I('check')}Let’s go!</div>`;
const p146e=`<div class="q-top">${I('back')}<b>Family</b></div>
${blk('family','Who’s here','<div style="display:flex;gap:6px;margin-top:4px">'+[['Anna','for_me','#0CF2BA'],['Mia · 8','for_child','#FFD27A'],['Leo · 5','for_child','#F3C0D9']].map(([n,i,c])=>`<span class="q-chip" style="background:${c};color:#14102a">${I(i)}${n}</span>`).join('')+'</div>')}
${blk('calendar','Shared Planner','<p>Swimming Thu 17:00 · Grandma Sun</p>')}
${blk('kids_mode','Leo isn’t set up yet','<p>Takes about 2 minutes, then hand the phone over.</p><div style="margin-top:6px">'+chip('Set up Leo','m')+'</div>')}
${blk('privacy','What grown-ups see','<p>What the kids made and learned. Never their buddy chats word for word.</p>')}`;
B13b['146']=()=>`<div class="boardpg">${head13('batch 2 · kids & family','A grown-up sets it up, then hands it over','Choosing “My child” leads to a short grown-up part behind the phone’s own PIN: name, age band and a few rules shown as plain defaults. Then the phone is handed over and the buddy speaks every line, so a child who can’t read yet is fine. No points, scores or streaks, ever.')}
<div class="r-ph" style="justify-content:space-between">
${P(p146a,'Grown-up check','device PIN · KidsGrownUpGate',{nodock:1})}
${P(p146b,'About your child','name, age band, plain defaults',{nodock:1})}
${P(p146c,'Hand-over','a clear moment, a way back for grown-ups',{cls:'kid',nodock:1})}
${P(p146d,'Child’s first minute','pick three things · they become islands',{cls:'kid'})}
${P(p146e,'Family view','who’s here · shared plans · one more child',{cls:'light'})}
</div>
${notes([['Age bands shape the start','4–6: pictures only, buddy speaks everything. 7–9: short words + pictures. 10–12: words, and the child can type.'],['Topics become islands','The three picks seed KidsWorld islands from batch 4 of 12.0 (kids-topics.json). More grow as the child asks.'],['Camera and mic later','Asked the first time the child taps them, and the grown-up confirms with the PIN.'],['Russian for kids','Buddy uses «ты», is called «бадди». “Hi Mia!” → «Привет, Мия! Я твой бадди.»']])}
</div>`;

/* ───────── 147 · moving, comfort and trouble paths ───────── */
const p147a=`<div class="q-top">${I('back')}<b>Bring your halo over</b></div>
${opt('qr_code','Old phone is here','Scan a code, both phones nearby',1)}${opt('restore','Use a backup file','From Drive or a USB stick',0)}${opt('try_it','Start fresh','You can bring things over later',0)}
<div class="lab" style="margin:10px 0 6px">What to bring</div>
${[['Notes and drawings',1],['Planner',1],['Sources',1],['Looks and wallpaper',1],['Kids profiles',1],['Study',0]].map(([t,on])=>chk(t,on)).join('')}
<p class="sub" style="margin-top:6px">AI service keys and the kids’ grown-up PIN are never moved.</p>`;
const p147b=`<div class="q-top">${I('back')}<b>Check the words</b></div>
<div style="display:flex;justify-content:center;gap:16px;margin:10px 0">${['old','new'].map((x,i)=>`<div style="width:96px;height:160px;border-radius:16px;border:2px solid ${i?'var(--mint)':'rgba(250,250,244,.3)'};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;font:600 13px Sora">${['amber','harbor','pebble'].map(w=>`<span>${w}</span>`).join('')}<small style="font:500 10px Atk;color:var(--t3)">${x} phone</small></div>`).join('')}</div>
<div class="r-big" style="text-align:center;font-size:19px">Do both phones show these three words?</div>
<div class="r-bt">Yes, bring it over</div><div class="r-bt s">No, stop</div>
<p class="sub" style="text-align:center;margin-top:6px">The code works for 10 minutes · encrypted, phone to phone</p>`;
const p147c=`<div class="q-top">${I('back')}<b></b><span class="sub" style="font-size:15px">Just open halo</span></div>${dots(4,0)}
<div style="display:grid;place-items:center;height:110px">${RING('#FFE08A',96,26)}</div>
<div style="font:600 32px/1.15 Sora;text-align:center;color:#fff">Hi, I’m halo</div>
<div style="display:flex;flex-direction:column;gap:6px;margin-top:14px">${[['Larger text',1],['Read aloud',1],['Stronger contrast',1]].map(([t])=>`<div style="height:48px;border-radius:14px;border:2px solid #FFE08A;display:flex;align-items:center;gap:8px;padding:0 12px;font:600 16px Atk;color:#fff">${I('check','width:20px;height:20px;color:#FFE08A')}${t}</div>`).join('')}</div>
<div class="r-bt" style="background:#FFE08A;color:#000;height:58px;font-size:18px">Let’s start</div>`;
const p147d=`${obTop(3)}<div class="r-big">Where should halo think?</div>
${opt('on_device','On this phone','Private and free. One download on Wi-Fi.',1)}
<div class="q-blk" style="border-color:rgba(255,159,107,.45)"><h4 style="color:var(--ap)">${I('connection')}No internet right now</h4><p>That’s fine. halo still works for notes, drawing, timers and your planner. The download waits for Wi-Fi.</p></div>
<div class="q-blk"><h4>${I('download')}Download · waiting for Wi-Fi</h4><div class="slider"><i style="width:0%"></i></div><p>You’ll see it on Home. Pause or cancel any time.</p></div>
<div class="r-bt">Open halo</div>`;
const p147e=`<div class="q-top">${I('sky_day')}<b>Good morning</b></div>
<div class="q-blk" style="border-color:rgba(201,184,255,.45)"><h4 style="color:var(--lil)">${I('download')}Getting halo’s offline brain</h4><div class="slider"><i style="width:46%"></i><b style="left:46%"></b></div><p>46 % · time left shown from real speed · ${'<span style="color:var(--cream)">Pause</span>'}</p></div>
<div class="q-blk" style="border-color:rgba(255,159,107,.45)"><h4 style="color:var(--ap)">${I('storage')}Not enough space for the download</h4><p>Example: halo needs 1.2 GB, you have 0.8 GB free.</p><div style="display:flex;flex-wrap:wrap;gap:5px;margin-top:6px">${chip('Use online instead','a')}${chip('Free up space')}${chip('Later')}</div></div>
${blk('tip','Meanwhile','<p>Notes, drawing, timers, Planner and calc work now.</p>')}`;
B13b['147']=()=>`<div class="boardpg">${head13('batch 2 · other ways in','Moving phones, comfort first, and when things go wrong','“I’ve used halo before” brings everything across with a code and three matching words. People who start with larger text or read aloud get the whole flow big and spoken. No internet, no space or a slow download never block halo: they show what works now and what you can do.')}
<div class="r-ph" style="justify-content:space-between">
${P(p147a,'I’ve used halo before','choose what comes across',{nodock:1})}
${P(p147b,'Three matching words','10-minute code · encrypted · security review',{nodock:1})}
${P(p147c,'Comfort on','larger text, high contrast, read aloud',{nodock:1,cls:''})}
${P(p147d,'No internet','never a dead end',{nodock:1})}
${P(p147e,'Download and space','plain numbers, three ways forward')}
</div>
${notes([['Never moved','AI service keys must be entered again. The kids’ grown-up PIN belongs to the device. Everything else is a tick box.'],['Security review needed','Phone-to-phone transfer: one-time 16-byte code, 10 minutes, 3-word match on both screens, local network only. Must be reviewed before it ships.'],['Comfort is remembered','Large text + read aloud also set the starting depth to Simple and the look to “Clear & big”.'],['Errors follow the rule','What happened → what halo did → what you can do. No codes, no blame, no red unless something failed.']])}
</div>`;

/* ───────── 148 · icons, motion, developer ───────── */
const ICN2=['hello','for_me','for_child','family','studying','creating','try_it','ask_access','allow_once','not_now','move_phone','restore','qr_code','hand_over','age_band','setup_card','finish_later','buddy'];
const LOT2=[['hello','Hello','ring draws in, gap sparks · 90 f'],['handover','Hand-over','phone to the child, star · 90 f'],['ask_access','Ask when needed','shield, then allowed · 75 f'],['move_phone','Move phones','your things travel across · 90 f'],['ready','Ready','calm bloom, no confetti · 75 f']];
B13b['148']=()=>`<div class="boardpg">${head13('batch 2 · icons, motion, developer files','Everything a developer needs for onboarding','18 new icons, 5 Lottie animations in dark and light, the flow as code with a test, three data files and 54 strings in English and Russian. The old HaloSetupScreen stays until the new flow passes the people tests on board 143.')}
<div class="row" style="gap:24px">
<div style="flex:1.12" class="col">
<div class="lab">Icons · assets/halo/artwork/ic + res/drawable</div>
<div class="r-icg">${ICN2.map(n=>`<div class="r-ic">${I(n)}<span>${n}</span></div>`).join('')}</div>
<div class="lab" style="margin-top:14px">Lottie · res/raw/halo13_motion_*_{dark,light}.json</div>
${LOT2.map(([f,n,s])=>`<div class="r-strip"><b>${n}<small>${s}</small></b><img src="img/l2/prev_${f}_dark.png"></div>`).join('')}
</div>
<div style="flex:1" class="col">
<div class="lab">Files in this batch</div>
<table class="q-tbl"><tr><th>Path</th><th>What it is</th></tr>
${[['docs/design/halo-13/ONBOARDING_13.md','The spec: steps, mapping, asks, cards, kids, moving, errors, QA'],['ui/onboarding/v13/OnboardingFlow.kt','4 steps, events, reducer, defaults, first try'],['ui/onboarding/v13/AskWhenNeeded.kt','When to show the halo sheet, Android or the settings path'],['ui/onboarding/v13/SetupCards.kt','One card a day, gone after 2 dismissals'],['ui/onboarding/v13/KidsHandOver.kt','Grown-up gate → child profile → hand-over'],['ui/onboarding/v13/MoveFromOldPhone.kt','10-minute code, 3 words, what never moves'],['test/…/OnboardingFlowTest.kt','4 steps max, skip defaults, no surprise asks'],['assets/onboarding/onboarding-13.json','Steps, options, first try, targets'],['assets/onboarding/ask-when-needed.json','17 kinds of access · why / if not · EN + RU'],['assets/onboarding/setup-cards.json','7 cards, priorities, who they’re for'],['res/values(-ru)/strings_onboarding_13.xml','54 strings EN + RU']].map(r=>`<tr><td><code>${r[0]}</code></td><td>${r[1]}</td></tr>`).join('')}</table>
<div class="code"><span class="c">// Every step: “Just open halo” → usable defaults</span>
<span class="k">fun</span> withDefaults(s: OnboardingState) = s.copy(
  whoFor   = s.whoFor.ifEmpty { setOf(WhoFor.ME) },
  start    = s.start ?: <span class="k">if</span> (LARGER_TEXT <span class="k">in</span> s.comfort) KEEP_IT_SIMPLE
                         <span class="k">else</span> A_BIT_OF_EVERYTHING,
  thinking = s.thinking ?: Thinking.DECIDE_LATER)</div>
</div></div>
${notes([['Flag it','Ship behind onboarding_v13. New installs only at first; existing users never see onboarding again.'],['Keep the guide','CapabilityGuideProgress stays the store for access attempts, so nothing re-prompts by itself.'],['Not compiled','Reference code matched to repo 99aac2f names, for review.'],['Next: batch 3','Sources manager and the new Planner.']])}
</div>`;
Object.assign(B,B13b);
