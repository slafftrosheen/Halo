/* Rollout 3 — sheets from buttons, Canvas connect, Help & check-up, empty/offline/loading, refreshed features (repo @98aa2fb) */
const S3={},X3={};
const bsheet=(title,sub,inner,arrowX)=>`<div class="scrim"></div><div class="bs glass"><div class="grab"></div><div class="bh"><div><b>${title}</b>${sub?`<span>${sub}</span>`:''}</div><div class="x">${I('close')}</div></div>${inner}${arrowX!=null?`<div class="from" style="left:${arrowX}px"></div>`:''}</div>`;
const eRing=(icon)=>`<div class="eo"><svg class="rg" viewBox="0 0 240 240"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="rgba(12,242,186,.55)" stroke-width="14" stroke-linecap="round" fill="none"/></svg><div class="ic">${I(icon)}</div></div>`;
const empty=(icon,h,p,btns)=>`<div class="empty">${eRing(icon)}<h3>${h}</h3><p>${p}</p>${btns?`<div class="eb">${btns.map(([i,t])=>`<span>${I(i)}${t}</span>`).join('')}</div>`:''}</div>`;
const chk=(cls,icon,t,s,em='')=>`<div class="chk2"><div class="st ${cls}">${cls=='run'?'':I(icon)}</div><div><b>${t}</b><span>${s}</span></div>${em?`<em>${em}</em>`:''}</div>`;

/* ---- 22 · sheets from buttons ---- */
X3.format=bsheet('Format','Market list · selection: “Price cards”',`
 <div class="bsl">TEXT</div><div class="tsz"><span style="font:600 19px Sora">Title</span><span style="font:600 16px Sora">Heading</span><span class="on" style="font:500 15px Atk">Body</span><span style="font:500 12px Atk">Small</span></div>
 <div class="bsl">STYLE</div><div class="tbtns"><span class="on">${I('bold')}</span><span>${I('italic')}</span><span>${I('highlighter')}</span><span>${I('quote')}</span><span>${I('link')}</span><span>${I('code')}</span></div>
 <div class="bsl">LISTS</div><div class="tsz"><span>${I('bullet_list')}</span><span>${I('numbered_list')}</span><span class="on">${I('task')}</span></div>`,62);
X3.insert=bsheet('Insert','Adds at the cursor',`
 <div class="itiles">${[['task','Checklist'],['list_view','Table'],['image','Image'],['ocr','Scan text'],['file_audio','Voice memo'],['link','Link'],['quote','Quote'],['ai_draft','Draft with halo','ai']].map(([i,t,c])=>`<div class="itile ${c||''}"><div class="q">${I(i)}</div>${t}</div>`).join('')}</div>
 <div class="fine">“Draft with halo” writes a suggestion you can keep or throw away — it never replaces your text.</div>`,170);
X3.layers=bsheet('Layers','Autumn market poster · 4 layers',`
 ${[['T','Title · “Saturday market”','Text · Sora 64',1,0],['img','Pumpkins photo','Image · 80%',0,0],['sh','Leaf shapes','Drawing · 3 shapes',0,1],['bg','Background','Cream paper',0,0]].map(([t,n,s,on,hid],k)=>`<div class="lyr ${on?'on':''}"><div class="th" style="background:${['#3a2f7a','#7a4a2f','#2f6a52','#efe6d4'][k]};color:${k==3?'#060330':'#fff'}">${t=='T'?'Aa':t=='img'?I('image'):t=='sh'?I('shapes'):''}</div><div><b>${n}</b><span>${s}</span></div><div class="ey"><i class="${hid?'off':''}">${I(hid?'unavailable':'review')}</i><i>${I('more')}</i></div></div>`).join('')}
 <div class="lbl" style="margin-top:4px"><span>${I('add')}&nbsp;Add layer</span><span>${I('sort')}&nbsp;Reorder</span></div>`,62);
X3.more=bsheet('More','Autumn market poster',`<div class="ml">
 ${[['export','Export as PNG','p','1080 × 1350'],['use_in_chat','Use in chat'],['collections','Duplicate'],['heading','Rename'],['history','Versions','','6 saved'],['trash','Delete…','d']].map(([i,t,c,s])=>`<div class="mi ${c||''}">${I(i)}${t}${s?`<small>${s}</small>`:''}</div>`).join('')}</div>`,300);

/* ---- 23 · Canvas connect + Help & check-up ---- */
S3.connect=`<div class="cv"><div class="hintb">${I('link')}Tap a card to connect it to “Shop ideas”</div>
 <svg class="cvl" viewBox="0 0 350 560"><path d="M110 120 C 110 300, 120 330, 120 380"/><path class="dash" d="M120 130 C 220 150, 250 330, 250 420"/></svg>
 <div class="cc src" style="left:20px;top:80px"><b>Shop ideas</b><span>9 cards</span></div><div class="cc studio" style="left:180px;top:215px">${I('image')}<b>Poster</b></div>
 <div class="cc office" style="left:30px;top:350px">${I('file_doc')}<b>Market list</b></div><div class="cc tgt" style="left:180px;top:400px"><b>Winter candles?</b><span>new idea</span></div></div>`;
S3.connected=`<div class="cv"><svg class="cvl" viewBox="0 0 350 560"><path d="M110 120 C 110 300, 120 330, 120 380"/><path d="M120 130 C 220 150, 250 330, 250 420" style="stroke:var(--mint);stroke-width:2.5"/></svg>
 <div class="cc" style="left:20px;top:80px"><b>Shop ideas</b><span>9 cards</span></div><div class="cc studio" style="left:180px;top:215px">${I('image')}<b>Poster</b></div>
 <div class="cc office" style="left:30px;top:350px">${I('file_doc')}<b>Market list</b></div><div class="cc sel" style="left:180px;top:400px"><b>Winter candles?</b><span>new idea</span></div>
 <div class="lchip" style="left:196px;top:262px">part of</div></div>`;
X3.label=bsheet('Name the link','Shop ideas → Winter candles?',`<div class="lbl">${['No label','part of','leads to','inspired by','needs','+ Your own'].map((x,i)=>`<span class="${i==1?'on':''}">${x}</span>`).join('')}</div>`,190);
S3.help=`<div class="ttl">Help & check-up</div><div class="sub">See that halo works — and get help in plain words.</div>
 <div class="hero2"><div class="ic3">${I('diagnostics')}</div><div><b>Everything works</b><span>Checked today 9:41 · 6 of 6</span></div></div>
 <div class="wsec">HOW DO I…</div><div class="grp">${[['mic','Talk to halo hands-free'],['widget','Put halo on my home screen'],['privacy','Keep everything on this phone'],['glasses','Connect my glasses']].map(([i,t])=>`<div class="topic">${I(i)}${t}</div>`).join('')}</div>
 <div class="wsec">STILL STUCK</div><div class="grp"><div class="topic">${I('export')}Share a check-up report<small style="margin-left:auto;font:500 12.5px Atk;color:var(--t3)">no chats included</small></div></div>`;
S3.checkup=`<div class="crumb">${I('diagnostics')}Help · check-up</div><div class="ttl">1 thing needs a look</div>
 <div class="grp ck" style="margin-top:10px">${chk('','check','Brain','On this phone · answered a test question')+chk('','check','Hearing you','Mic works · wake word off')+chk('','check','halo’s voice','Played the test phrase')+chk('','check','Devices','Glasses ready · tested 9:02')+chk('need','failure','Sources','1 of 7 could not refresh')+chk('','check','Storage','2.1 GB free · enough')}</div>
 <div class="fixc"><div class="h3">${I('approval')}NEEDS YOU</div><b>LSM news can’t be reached</b><p>Last good copy (7:10) is kept and marked “may be out of date”.</p><div class="r"><span class="p">Try again</span><span>Open source</span></div></div>`;
S3.checkRun=`<div class="crumb">${I('diagnostics')}Help · check-up</div><div class="ttl">Checking halo…</div><div class="sub">About 20 seconds · nothing is sent anywhere</div>
 <div class="grp" style="margin-top:12px">${chk('','check','Brain','Answered a test question')+chk('','check','Hearing you','Mic works')+chk('run','','halo’s voice','Playing the test phrase…','now')+chk('need','more','Devices','Waiting','')+chk('need','more','Sources','Waiting','')+chk('need','more','Storage','Waiting','')}</div>`.replace(/st need"><svg/g,'st" style="background:rgba(250,250,244,.06);color:var(--t3)"><svg');

/* ---- 24 · empty states ---- */
S3.emptyLib=`<div class="ttl">Library</div><div class="srch">${I('search')}Search your things</div>`+empty('collections','Nothing here yet','Notes, images, lessons and boards you make will live here.',[['new_note','Start a note'],['draw','Make an image'],['study','Learn something']]);
S3.emptyAct=`<div class="ttl">Activity</div><div class="sub">What halo did, and what’s waiting for you.</div>`+empty('check','All clear','Nothing is waiting for you and nothing is running. When halo does something for you, you’ll see it — and the proof — here.');
S3.emptyMem=`<div class="ttl">HaloMemory</div><div class="sub">What halo remembers — you decide.</div>`+empty('memory','halo doesn’t remember anything yet','When something seems worth keeping, halo will ask first. Nothing is saved without your OK.',[['remember','Tell halo something to remember']]);
S3.emptyWid=`<div class="ttl">Widgets & sources</div><div class="sub">halo on your home screen.</div>`+empty('widget','No sources yet','Widgets stay empty rather than guess. Add a news feed or a weather city and they fill in.',[['integrations','Add a feed'],['watch','Add weather city']]);

/* ---- 25 · offline & loading ---- */
S3.offline=`<div class="crumb">${I('home')}Chat · Saturday</div><div class="offb">${I('disconnected')}No internet · halo answers on this phone</div>
 <div class="msg me">What should I bring if it rains on Saturday?</div>
 <div class="msg">Bring the canopy and the clear covers for the candles, and keep the price cards in a sleeve. <span class="stale">${I('cloud')}Forecast from 7:10 · may be out of date</span></div>`;
S3.srcStale=`<div class="crumb">${I('widget')}Widgets · Sources</div><div class="ttl">LSM news</div>
 <div class="dcard" style="margin-top:10px"><div class="di">${I('sources')}</div><div><b>Cached · may be out of date</b><span class="m">Retrieved 7:10 · Last verified 7:10</span><span>Next refresh when you’re online</span></div></div>
 <div class="offb">${I('disconnected')}Local mode · no internet refresh</div>
 ${sec('Saved items',['Riga opens winter market on Saturday','Tram 7 detour this weekend','Cold snap: −5° by Sunday'].map(t=>row('file_doc',t,'Saved 7:10 · readable offline')).join(''))}`;
S3.loadLib=`<div class="ttl">Library</div><div class="srch">${I('search')}Search your things</div><div class="fch"><span class="on">All</span><span>Notes</span><span>Images</span><span>Lessons</span></div>
 ${[1,2,3,4,5].map(k=>`<div class="skr"><div class="sk a"></div><div style="flex:1"><div class="sk l1" style="width:${[62,48,70,40,55][k-1]}%"></div><div class="sk l2"></div></div></div>`).join('')}
 <div class="fine" style="text-align:center">Loading appears only after 400 ms — fast loads show nothing.</div>`;
S3.loadBrain=`<div class="crumb">${I('intelligence')}Brain</div><div class="ttl">Getting the brain ready</div><div class="sub">First time only · works offline after this</div>
 <div class="loadc"><b>Unpacking on this phone</b><span>1.3 of 2.1 GB · keep halo open</span><div class="bar"><i style="width:62%"></i></div></div>
 <div class="grp" style="margin-top:14px">${row('wifi'?'signal':'signal','Downloading on Wi-Fi','Paused automatically on mobile data')+row('battery','Battery 64%','Enough to finish')}</div>
 <div class="fine">You can keep using halo — answers use the online helper only if you allowed it, otherwise halo says it’s still getting ready.</div>`;

/* ---- 26 · refreshed repo features ---- */
S3.acct=`<div class="crumb">${I('intelligence')}Brain · Online helper</div><div class="ttl">Use your ChatGPT plan</div><div class="sub">Eligible subscription models, without API credits.</div>
 <div class="acct"><div class="av">${I('cloud')}</div><div><b>No account connected</b><span>Sign in in your browser, then come back to halo. OpenAI controls your account limits.</span></div></div>
 <div class="grp" style="margin-top:12px">${row('privacy','Stored safely','Only in this phone’s protected store')+row('approval','Asks first, every time','Online answers need your OK')}</div>
 <div class="fine">This is a sign-in, not a key. You can disconnect at any time in Brain.</div>`;
S3.acctOk=`<div class="crumb">${I('intelligence')}Brain · Online helper</div><div class="ttl">ChatGPT plan</div>
 <div class="acct okb"><div class="av">AK</div><div><b>Connected · Anna K.</b><span class="pill2">${I('check')}Subscription access granted</span></div></div>
 <div class="wsec">ANSWER MODEL</div><div class="grp">${['Model from your plan · fetched 9:40','Another model · fetched 9:40'].map((m,i)=>`<div class="vrow"><div class="radio ${i==0?'on':''}"></div><div><b>${m.split(' · ')[0]}</b><span>${m.split(' · ')[1]}</span></div></div>`).join('')}</div>
 <div class="acct wb" style="margin-top:12px"><div class="av" style="background:rgba(255,159,107,.2);color:var(--ap)">${I('approval')}</div><div><b>Other case: signed in, no access</b><span>ChatGPT didn’t grant subscription access to this account. halo keeps using this phone.</span></div></div>`;
S3.speech=`<div class="crumb">${I('voice')}Voice & reading</div><div class="ttl">Online voices</div><div class="sub">OpenRouter speech · uses your saved key</div>
 <div class="wsec">SPEECH MODEL</div><div class="grp">${[['Speech model from your list','30 voices · refreshed 9:40'],['Another model','11 voices']].map(([t,s],i)=>`<div class="vrow"><div class="radio ${i==0?'on':''}"></div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}</div>
 <div class="wsec">VOICE</div><div class="grp">${['Voice 1 · as named by the model','Voice 2','Voice 3'].map((v,i)=>`<div class="vrow"><div class="radio ${i==0?'on':''}"></div><div><b>${v.split(' · ')[0]}</b><span>${v.split(' · ')[1]}</span></div><span class="r play">${I('play')}</span></div>`).join('')}</div>
 <div class="fine">Models and voices come from your key’s live list — halo never guesses a voice. Charged to your OpenRouter account. Hearing you can use it too — choose in “Hear me”.</div>`;
S3.article=`<div class="art"><div class="src"><i>L</i>LSM news · saved 9:38</div><h2>Riga opens winter market on Saturday</h2>
 <div class="meta"><span class="stale" style="background:rgba(12,242,186,.12);color:var(--mint)">${I('check')}Saved article text</span>Retrieved 9:38 · 3 min read</div>
 <div class="hero"></div><p>The Dome Square market opens on Saturday with 80 stalls, longer hours on weekends and a new area for local makers…</p>
 <div class="miss">${I('image')}This image has not been saved.</div></div>`;
