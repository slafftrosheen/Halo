/* Rollout 2 — inner pages */
const sw=(c,l,on)=>`<div class="swt ${on?'on':''}"><i style="background:${c}"></i><span>${l}</span></div>`;
const tg=(on=1)=>`<span class="tgl ${on?'on':''}"></span>`;
const row=(i,t,s,right='')=>`<div class="srw"><div class="ic2">${I(i)}</div><div style="flex:1;min-width:0"><b>${t}</b><span>${s}</span></div>${right}</div>`;
const sec=(h,inner)=>`<div class="wsec">${h.toUpperCase()}</div><div class="grp">${inner}</div>`;
const S2={};
S2.appearance=`<div class="ttl">Appearance</div><div class="sub">How halo looks — the dock and ring follow it.</div>
 ${sec('Light or dark',`<div class="segw">${['Light','Dark','Like my phone'].map((x,i)=>`<span class="${i==1?'on':''}">${x}</span>`).join('')}</div>`)}
 ${sec('Accent',`<div class="sws">${sw('#0CF2BA','Mint',1)}${sw('#8EC5FF','Blue')}${sw('#C9B8FF','Lilac')}${sw('#FF9EB3','Rose')}</div>`)}
 ${sec('Tone',`<div class="segw">${['Natural','Warm','Cool'].map((x,i)=>`<span class="${i==0?'on':''}">${x}</span>`).join('')}</div>`)}
 ${sec('Comfort',row('heading','Text size','Large · 130%')+row('pause','Less motion','Off',tg(0)))}`;
S2.voice=`<div class="ttl">Voice & reading</div><div class="sub">How halo listens and speaks.</div>
 ${sec('halo’s voice',['Aria · calm','Juno · bright','Ilse · warm, slower'].map((v,i)=>`<div class="srw"><div class="ic2">${I('speaker')}</div><div style="flex:1"><b>${v.split(' · ')[0]}</b><span>${v.split(' · ')[1]}</span></div>${i==0?`<span class="chkc">${I('check')}</span>`:`<span class="play">${I('play')}</span>`}</div>`).join(''))}
 ${sec('Listening',row('mic','Hear me on this phone','Ready · works offline',`<span class="okt">${I('check')}</span>`)+row('read_mode','Read answers aloud','When I talked to halo',tg(1)))}`;
S2.privacy=`<div class="ttl">Privacy</div><div class="sub">You decide what leaves this phone.</div>
 <div class="hero2"><div class="ic3">${I('privacy')}</div><div><b>Private mode is on</b><span>Answers, voice and memory stay on this phone.</span></div>${tg(1)}</div>
 ${sec('This week',row('cloud','Nothing left this phone','Last checked 9:41',`<span class="okt">${I('check')}</span>`)+row('sources','Sources halo fetched','Weather, 3 news feeds · read only'))}
 ${sec('Your data',row('memory','What halo remembers','24 things · review')+row('trash','Delete history','Choose what to delete'))}`;
S2.brain=`<div class="ttl">Brain</div><div class="sub">Where halo thinks.</div>
 <div class="choice on"><div class="ic3">${I('on_device')}</div><div><b>On this phone</b><span>Private, works offline. Good for everyday questions.</span><em>${I('check')}Works well here</em></div></div>
 <div class="choice"><div class="ic3">${I('cloud')}</div><div><b>Online helper</b><span>Bigger answers when you allow it. Asks first, every time.</span><em class="m">Off</em></div></div>
 ${sec('Check',row('guide_me','Ask a test question','“What’s 15% of 80?”')+row('lab','Advanced','Models and servers — for tinkerers'))}`;
S2.addDevice=`<div class="ttl">Add a device</div><div class="sub">What would you like to connect?</div>
 ${[['glasses','Glasses','See and hear with halo'],['headphones','Headphones or speaker','Talk and listen hands-free'],['watch','Watch','Quick answers on your wrist'],['robot','BuddyBot','A small robot helper']].map(([i,t,s])=>`<div class="trow"><div class="ti2 memory">${I(i)}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S2.pairing=`<div class="crumb">${I('glasses')}Add glasses · step 2 of 3</div><div class="prog"><i class="d"></i><i class="c"></i><i></i></div>
 <div class="pairv"><div class="pr1"></div><div class="pr2"></div><div class="gl">${I('glasses')}</div></div>
 <div class="lq" style="text-align:center">Hold the glasses close and keep them on</div><div class="sub" style="text-align:center">This usually takes under a minute.</div>`;
S2.deviceDetail=`<div class="crumb">${I('devices')}Devices</div><div class="ttl">HeyCyan glasses</div>
 <div class="dcard" style="margin-top:10px"><div class="di">${I('glasses')}</div><div><b>Ready</b><span class="g">${I('check')}Tested 9:02 · heard and seen</span><span>Battery 76% · about 4 h</span></div></div>
 ${sec('halo can use',row('camera','Camera','Only when you ask',tg(1))+row('mic','Microphone','Wake word off',tg(1))+row('speaker','Speaker','Answers play here',tg(1)))}`;
S2.source=`<div class="crumb">${I('widget')}Widgets · Sources</div><div class="ttl">Weather · Riga</div>
 <div class="dcard" style="margin-top:10px"><div class="di">${I('cloud')}</div><div><b>Updated 9:38</b><span class="g">${I('check')}Fresh · every 30 min</span><span>Read only · location: Riga (chosen, not tracked)</span></div></div>
 ${sec('Feeds these widgets',row('calendar','Clock & weather','Home screen · 4×3')+row('widget','Your day in halo','Home screen · 4×4'))}
 ${sec('Settings',row('retry','Refresh every','30 minutes')+row('wifi'===''?'':'signal','Only on Wi-Fi','Off',tg(0)))}`;
S2.history=`<div class="ttl">History</div><div class="srch">${I('search')}Search conversations</div>
 ${[['Saturday plan','Today · added 3 events'],['Anna — running late','Today · sent'],['Poster colours','Yesterday'],['Photosynthesis help','Mon'],['Frame shop hours','Sun']].map(([t,s])=>`<div class="frow"><div class="fi okk">${I('conversations')}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S2.receipt=`<div class="crumb">${I('notifications')}Activity · Done</div><div class="ttl">Added 3 events</div><div class="sub">Today 8:12 · you asked by voice</div>
 <div class="card">${[['10:30','Coffee at Miera iela'],['12:00','Art Nouveau quarter'],['16:00','Market hall']].map(([t,x])=>`<div class="li"><b style="color:var(--mint);width:46px">${t}</b>${x}<span>${I('check')}</span></div>`).join('')}</div>
 <div class="rc">${I('check')}Calendar confirmed all 3</div>
 ${sec('What halo did',row('calendar','Wrote to Calendar','3 new events')+row('privacy','Stayed on this phone','No online helper used'))}`;
S2.memoryItem=`<div class="crumb">${I('memory')}Memory</div><div class="ttl" style="font-size:22px">You sell at the Saturday market</div>
 ${sec('Why halo remembers',row('conversations','From chat','12 Nov · “I have a stall on Saturdays”')+row('check','You kept it','12 Nov'))}
 ${sec('Used for',row('calendar','Planning Saturdays','3 times this month')+row('widget','Your day in halo','Saturday mornings'))}`;
S2.studyDone=`<div class="crumb">${I('study')}Photosynthesis</div><div class="prog"><i class="d"></i><i class="d"></i><i class="d"></i><i class="d"></i><i class="d"></i></div>
 <div class="donev"><div class="dr">${I('check')}</div></div><div class="lq" style="text-align:center">Lesson done — 4 of 5 right first time</div>
 <div class="card"><div class="li">${I('check')}Light gives plants energy</div><div class="li">${I('check')}They take in carbon dioxide</div><div class="li">${I('retry')}Review: where oxygen goes<span>tomorrow</span></div></div>`;
S2.setup=`<div style="text-align:center;margin-top:40px"><div class="setupring">${ring()}</div><div class="ttl" style="margin-top:22px">Hi, I’m halo</div>
 <div class="sub" style="max-width:300px;margin:6px auto 0">I live on this phone. Let’s set up three things — it takes about two minutes.</div></div>
 <div class="card" style="margin-top:26px">${[['speaker','Pick my voice','1'],['mic','Check I can hear you','2'],['privacy','Choose privacy','3']].map(([i,t,n])=>`<div class="li">${I(i)}<b>${t}</b><span>${n} of 3</span></div>`).join('')}</div>`;
S2.kidsLearn=`<div class="kq" style="margin-top:24px">What shall we learn?</div>${[['study','Counting','Step 2 of 4','#0CF2BA'],['read_mode','Read with me','The fox and the moon','#FF9F6B'],['draw','Shapes','New!','#C9B8FF']].map(([i,t,s,c])=>`<div class="kc"><div class="kci" style="background:${c}33">${I(i)}</div><div><div>${t}</div><div style="font:500 15px Atk;color:#5B4A60">${s}</div></div></div>`).join('')}`;
S2.kidsMake=`<div class="kq" style="margin-top:18px">Draw a happy sun</div><div class="kpad"><div class="ksun"></div><div class="kray r1"></div><div class="kray r2"></div><div class="kray r3"></div></div>
 <div class="kcols">${['#FF9F6B','#F4C56A','#0CF2BA','#8EC5FF','#C9B8FF'].map((c,i)=>`<i style="background:${c}" class="${i==1?'on':''}"></i>`).join('')}</div>`;
S2.parent=`<div style="text-align:center;margin-top:60px"><div class="ic3" style="margin:0 auto;width:72px;height:72px;border-radius:24px;background:rgba(255,159,107,.2);color:#9B4B19">${I('privacy')}</div>
 <div class="kq" style="font-size:24px">Grown-ups only</div><div style="font-size:18px;color:#5B4A60;max-width:300px;margin:0 auto;line-height:1.45">Settings for Mila are behind your phone’s screen lock.</div></div>`;
