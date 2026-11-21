/* Rollout: generic Calm Dock builder + all screens */
function dock(o){
 const pl=o.place, kid=o.kid;
 const place=`<div class="place"><div class="pi ${pl.cls||''}">${I(pl.icon)}</div><div><div class="pt">${pl.t}</div><div class="ps">${pl.s}</div></div></div>${o.next?`<div class="next">${I(o.next[0])}${o.next[1]}</div>`:''}`;
 let r2='';
 if(o.composer!==undefined) r2=`<div class="ibtn">${I('attach')}</div><div class="comp">${o.composer}<div class="send">${I('send')}</div></div>`;
 else if(o.seg) r2=`<div class="seg">${o.seg.map(([i,l,on,b])=>`<div class="sg ${on?'on':''}">${I(i)}${l}${b?`<span class="sb2">${b}</span>`:''}</div>`).join('')}</div>${(o.acts||[]).map(([i,l,p])=>`<div class="act ${p?'p':''}" style="flex:0 0 ${l?'auto':'52px'};padding:0 ${l?14:0}px">${I(i)}${l||''}</div>`).join('')}`;
 else r2=o.acts.map(([i,l,p])=>`<div class="act ${p?'p':''}">${I(i)}${l}</div>`).join('');
 const tabs=kid?`<div class="tab ${o.nav=='home'?'on':''}"><div class="ind">${I('home')}</div>Home</div><div class="ocell">${orbK()}<div class="cap">${o.cap||'Talk'}</div></div><div class="tab ${o.nav=='learn'?'on':''}"><div class="ind">${I('study')}</div>Learn</div>`
 :`<div class="tab ${o.nav=='home'?'on':''}"><div class="ind">${I('home')}</div>Home</div><div class="tab ${o.nav=='work'?'on':''}"><div class="ind">${I('notebook')}</div>Workspace</div>
 <div class="ocell">${orb()}<div class="cap">${o.cap||'Talk'}</div></div><div class="tab ${o.nav=='act'?'on':''}"><div class="ind">${I('notifications')}${o.badge===0?'':`<span class="badge">${o.badge||2}</span>`}</div>Activity</div><div class="tab ${o.nav=='places'?'on':''}"><div class="ind">${I('grid_view')}</div>Places</div>`;
 const tabsXl=`<div class="tab ${o.nav=='home'?'on':''}"><div class="ind">${I('home')}</div>Home</div><div class="ocell">${orb()}<div class="cap">${o.cap||'Talk'}</div></div><div class="tab"><div class="ind">${I('grid_view')}</div>Places</div>`;
 const isl=o.island?`<div class="pn ${o.island.cls}"><div class="r1">${o.island.r1}</div><div class="r2">${o.island.r2}</div></div>`:'';
 return `<div class="zone"><div class="zbg glass"></div><div class="navbg glass"></div><div class="horizon"></div><div class="shim"></div>
 <div class="pn idle"><div class="r1">${place}</div><div class="r2">${r2}</div></div>${isl}<div class="nav">${o.cls=='xl'?tabsXl:tabs}</div></div>`;
}
function ph(work,o,extra=''){return `<div class="phone M st-${o.state||'idle'} ${o.kid?'kid':''} ${o.light?'lt':''} ${o.cls||''}"><div class="wall"></div>${sb}<div class="work">${work}</div>${dock(o)}${extra}<div class="split"></div><div class="edge"></div><div class="gb"></div></div>`}
const ok=t=>`<span class="ok">${I('check')}</span>${t}`;
/* ---------- work areas ---------- */
const S={};
S.welcome=`<div class="ttl" style="margin-top:40px">Good morning</div><div class="sub">Friday · 3° in Riga · 3 things today</div>
 <div class="sugg">${[['calendar','What’s on today?','Your day, read out in a minute'],['new_note','Start a note','Type or just talk'],['study','Learn something','One step at a time'],['camera','What’s this?','Point the camera and ask']].map(([i,t,s])=>`<div class="sgc"><div class="ti">${I(i)}</div><div><b>${t}</b><span>${s}</span></div>${I('chevron_down','i rot')}</div>`).join('')}</div>`;
S.typing=`<div class="crumb">${I('home')}Chat · new</div><div class="msg" style="opacity:.5">…</div>`;
S.library=`<div class="ttl">Library</div><div class="srch">${I('search')}Search your things</div>
 <div class="fch">${['All','Notes','Images','Lessons'].map((x,i)=>`<span class="${i==0?'on':''}">${x}</span>`).join('')}</div>
 ${[['file_doc','Market list','HaloNotes · edited 9:12','office'],['image','Autumn market poster','HaloStudio · 2 layers','studio'],['flashcard','Photosynthesis','HaloStudy · step 2 of 5','study'],['canvas','Shop ideas','HaloCanvas · 9 cards','canvas'],['file_audio','Voice memo · glasses','Recording · 1:42','office']].map(([i,t,s,c])=>`<div class="frow"><div class="fi ${c}">${I(i)}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S.tools=`<div class="ttl">Tools</div><div class="sub">Open a tool. Your creations stay in Library.</div>
 ${[['draw','HaloStudio','Images, drawing, text and layers','studio'],['notebook','HaloNotes','Notes, documents and tables','office'],['study','HaloStudy','Lessons and practice, step by step','study'],['memory','HaloMemory','What halo remembers — and why','memory'],['canvas','HaloCanvas','Bring ideas together on a board','canvas']].map(([i,t,s,c])=>`<div class="trow"><div class="ti2 ${c}">${I(i)}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S.notes=`<div class="doc"><div class="dh">Market list</div><div class="dm">Saturday · for the stall</div>
 <p><b>Bring</b></p><div class="chk on">Planters — 6 small, 2 large</div><div class="chk on">Candles in jars</div><div class="chk">Price cards<span class="caret"></span></div><div class="chk">Card reader, charged</div>
 <p style="margin-top:14px"><b>Ask Anna</b></p><p>Can she cover 12:00–13:00 so I can eat?</p></div>`;
S.study=`<div class="crumb">${I('study')}Photosynthesis</div><div class="prog"><i class="d"></i><i class="c"></i><i></i><i></i><i></i></div>
 <div class="lq">Plants make food from light. What do they take in from the air?</div>
 ${['Oxygen','Carbon dioxide','Nitrogen'].map((x,i)=>`<div class="opt ${i==1?'sel':''}">${x}</div>`).join('')}
 <div class="hint">${I('guide_me')}<div><b>Hint</b><span>It’s the gas we breathe out.</span></div></div>`;
S.memory=`<div class="ttl">HaloMemory</div><div class="sub">What halo remembers — you decide.</div>
 <div class="mcard sug"><div class="mh">${I('remember')}Suggested · not saved yet</div>You prefer the window seat at Miera iela.<div class="mr"><span class="ghost">Not this</span><span class="keep">Remember</span></div></div>
 ${[['You sell at the Saturday market','From chat · 12 Nov'],['Anna is your stall partner','You told halo · 3 Nov'],['Weather: Riga','Settings']].map(([t,s])=>`<div class="frow"><div class="fi memory">${I('memory')}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S.canvas=`<div class="cv"><svg class="cvl" viewBox="0 0 350 560"><path d="M110 120 C 180 120, 160 250, 230 250" /><path d="M110 120 C 110 300, 120 330, 120 380"/><path d="M230 250 C 260 330, 230 380, 240 430"/></svg>
 <div class="cc" style="left:20px;top:80px"><b>Shop ideas</b><span>9 cards</span></div><div class="cc studio" style="left:180px;top:215px">${I('image')}<b>Poster</b></div>
 <div class="cc office" style="left:30px;top:350px">${I('file_doc')}<b>Market list</b></div><div class="cc sel" style="left:180px;top:400px"><b>Winter candles?</b><span>new idea</span></div></div>`;
S.activity=`<div class="ttl">Activity</div><div class="sub">What halo did, and what’s waiting for you.</div>
 <div class="acard need"><div class="ah">${I('approval')}Needs you</div><b>Send the photos to Anna?</b><span>Message · drafted 9:20 · not sent</span><div class="mr"><span class="ghost">Not now</span><span class="apb">Send to Anna</span></div></div>
 <div class="acard"><div class="ah run">${I('retry')}Running</div><b>Exporting poster as PNG</b><div class="bar"><i style="width:62%"></i></div></div>
 ${[['check','Added 3 events to Calendar','Confirmed · 8:12'],['check','Saved “Market list”','HaloNotes · 9:12'],['failure','Couldn’t reach the news source','Will try again at 10:00 · Widgets']].map(([i,t,s],k)=>`<div class="frow"><div class="fi ${k==2?'bad':'okk'}">${I(i)}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}`;
S.devices=`<div class="ttl">Devices</div><div class="sub">What halo can use right now.</div>
 <div class="dcard"><div class="di">${I('glasses')}</div><div><b>HeyCyan glasses</b><span class="g">${I('check')}Ready · tested 9:02</span><span>Battery 76% · camera, mic, speaker</span></div></div>
 <div class="dcard"><div class="di">${I('robot')}</div><div><b>BuddyBot</b><span class="m">Saved · not connected</span><span>Turn it on to connect</span></div></div>
 <div class="dcard"><div class="di">${I('phone')}</div><div><b>This phone</b><span class="g">${I('check')}Ready</span><span>Mic, camera, speaker</span></div></div>`;
S.settings=`<div class="ttl">Settings</div>${[['halo',[['intelligence','Brain','On this phone · works offline'],['voice','Voice & reading','Ready · halo’s voice']]],['You',[['appearance','Appearance','Dark · Mint · Natural'],['privacy','Privacy','Private mode on'],['widget','Widgets & sources','6 widgets · 7 sources']]],['Help',[['guide_me','Help & check-up','Everything works'],['lab','Advanced','For tinkerers']]]].map(([h,rs])=>`<div class="wsec">${h.toUpperCase()}</div><div class="grp">${rs.map(([i,t,s])=>`<div class="srw"><div class="ic2">${I(i)}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}</div>`).join('')}`;
S.kidsHome=`<div class="kq" style="margin-top:30px">Hi Mila! What shall we do?</div>${[['guide_me','Ask a question','#FF9F6B'],['study','Learn something','#0CF2BA'],['draw','Make something','#C9B8FF']].map(([i,t,c])=>`<div class="kc"><div class="kci" style="background:${c}33;color:#160E1C">${I(i)}</div>${t}</div>`).join('')}`;
S.err=W.studio();
S.large=`<div class="ttl" style="font-size:34px">Poster draft</div><div class="sub" style="font-size:20px">The artboard is above; text is at 200%.</div>`+`<div style="transform:scale(.62);transform-origin:top left;width:160%;height:420px;position:relative">${W.studio()}</div>`;
