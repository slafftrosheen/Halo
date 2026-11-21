/* halo 7.3 · the alive layer on every page and every tool */
const SP={};
const prow=(i,t,s,r='')=>`<div class="tl" style="height:54px">${I(i)}<div>${t}${s?`<small>${s}</small>`:''}</div>${r}</div>`;
const tgl=(on=1)=>`<span class="tg2 ${on?'':'off'}"></span>`;
const segs=(a,on)=>`<div class="seg3">${a.map((x,i)=>`<span class="${i==on?'on':''}">${x}</span>`).join('')}</div>`;
const val=v=>`<span style="margin-left:auto;color:var(--t2);font-size:14px">${v}</span>`;
const hdl=`<span class="hdl" style="margin-left:auto;color:var(--t3);font:700 16px Atk;letter-spacing:-2px">⋮⋮</span>`;
/* ---------- Home · Workspace · Activity · Places ---------- */
SP.homeEdit=`<div class="crumb">${I('home')}Home › Edit</div><div class="mi-t" style="font-size:23px">Your Home</div><div class="mi-s">Choose up to 4 ways to start. Drag to reorder.</div>
 ${[['calendar','What’s on today?'],['new_note','Start a note'],['study','Learn something'],['camera','What’s this?']].map(([i,t])=>`<div class="dl" style="height:54px"><div class="ti">${I(i)}</div>${t}<span class="rm">–</span>${hdl}</div>`).join('')}
 <div class="lbl3">Add instead</div><div class="chipsx">${['Call Anna','Timer','Weather','Read my brief','Draw'].map(x=>`<span>${I('add')}${x}</span>`).join('')}</div>
 <div class="lbl3">Greeting line</div>${segs(['Day + weather','Just the day','Off'],0)}`;
SP.activityEasy=`<div class="easyh" style="font-size:28px">Activity</div><div class="mi-s" style="font-size:17px">What halo did, and what’s waiting for you.</div>
 <div class="bigcard ap"><div class="k3">${I('approval')}Waiting for you</div><b>Send this to Anna?</b><p>“I’ll bring the price cards — see you at 10!”</p><div class="cbtns"><span style="height:56px;font-size:16px">Not now</span><span class="p ap" style="height:56px;font-size:16px">${I('send')}Look and send</span></div></div>
 <div class="bigcard"><div class="k3">${I('check')}Done today · 3</div><b>Timer, reminder, a note</b><p>Tap to see each one.</p></div>`;
SP.placesArrange=`<div class="crumb">${I('grid_view')}Places › Arrange</div><div class="mi-t" style="font-size:23px">Arrange Places</div><div class="mi-s">Drag to reorder. Hide what you never use — it’s still in search.</div>
 <div class="ptiles">${[['archive','Library',1],['memory','HaloMemory',1],['widget','Widgets & sources',1],['devices','Devices',1],['settings','Settings',1],['guide_me','Help & check-up',1],['appearance','Make it yours',1],['history','History',0]].map(([i,t,on])=>`<div class="pt2 ${on?'':'off'}"><span class="pg">${hdl}</span>${I(i)}<b>${t}</b><span class="eye">${I(on?'eye':'close')}</span></div>`).join('')}</div>
 <div class="lockline">${I('eye')}Easy view shows the first 4 as big buttons</div>`;
/* ---------- Places pages ---------- */
SP.libraryBig=`<div class="mi-t" style="margin-top:6px">Library</div><div class="srch">${I('search')}Search or say what you’re looking for</div>
 <div class="fch">${['All','Notes','Images','Lessons','Boards'].map((x,i)=>`<span class="${i==0?'on':''}">${x}</span>`).join('')}</div>
 <div class="vsw"><span>${I('notebook')}List</span><span class="on">${I('image')}Big pictures</span><span style="margin-left:auto">Newest ${I('chevron_down')}</span></div>
 <div class="lgrid">${[['Autumn market poster','studio','image'],['Market list','office','file_doc'],['Photosynthesis','study','flashcard'],['Shop ideas','canvas','canvas']].map(([t,c,i])=>`<div class="lg"><div class="lth ${c}">${I(i)}</div><b>${t}</b></div>`).join('')}</div>`;
SP.memoryEasy=`<div class="mi-t">What halo remembers</div><div class="mi-s">Only what you keep. Forget anything, any time.</div>
 <div class="coachcard" style="position:relative;left:0;right:0;margin-top:12px;padding:12px 14px"><div class="k2">First time here</div><b style="font-size:16px">halo suggests, you decide</b><p style="font-size:13.5px">New things wait here as “Suggested” until you tap Remember.</p></div>
 ${[['You sell at the Saturday market','You told halo · 12 Nov'],['Anna is your stall partner','You told halo · 3 Nov'],['You like the window seat at Miera iela','Suggested · not saved']].map(([t,s],i)=>`<div class="memrow ${i==2?'sug':''}"><div><b>${t}</b><span>${s}</span></div><span class="fg">${i==2?'Remember':'Forget'}</span></div>`).join('')}`;
SP.widgetAdd=`<div class="crumb">${I('widget')}Widgets & sources › Add</div><div class="mi-t" style="font-size:23px">Your day</div><div class="mi-s">Upcoming events and tasks. Shown on your own wallpaper.</div>
 <div class="wpbig" style="width:210px;height:250px;border-radius:22px">${cvs('aurora',{tod:.8})}<div style="position:absolute;left:12px;right:12px;top:40px;transform:scale(.55);transform-origin:0 0;width:342px">${WD.day({pos:'position:relative;height:168px'})}</div></div>
 <div class="lbl3">Size</div>${segs(['Small','Wide','Tall'],1)}
 <div class="tl">${I('motion')}<div>Moves when something changes<small>slides new items in, in mint</small></div>${tgl(1)}</div>
 <div class="tl">${I('palette')}<div>Match my look<small>Night Mint · follows light/dark</small></div>${tgl(1)}</div>`;
SP.devicesEasy=`<div class="mi-t">Add a device</div><div class="mi-s">Three steps. halo shows each one.</div>
 ${[['1','Turn on your speaker or glasses','Hold its power button until the light blinks.','on'],['2','Keep it close to your phone','About one arm’s length.',''],['3','Tap “Find it”','halo looks for it and names it for you.','']].map(([n,t,s,c])=>`<div class="stp ${c}"><span class="sn">${n}</span><div><b>${t}</b><p>${s}</p></div></div>`).join('')}
 <div class="lockline">${I('speaker')}Every step can be read aloud</div>`;
/* ---------- Settings · Help ---------- */
SP.settingsFind=`<div class="mi-t">Settings</div><div class="srch big">${I('mic')}<span>“bigger text”</span></div>
 <div class="lbl3">Found 2</div>
 <div class="res on"><div class="ti2b">${I('text_size')}</div><div><b>Text size</b><span>Make it yours › Text & size</span></div><span class="go">Go</span></div>
 <div class="res"><div class="ti2b">${I('eye')}</div><div><b>Easy view</b><span>Bigger buttons and text everywhere</span></div><span class="go">Go</span></div>
 <div class="lbl3">Or browse</div>${[['appearance','Make it yours','Look, wallpaper, dock, motion'],['voice','Voice & reading','Calm · on this phone'],['privacy','Privacy','What halo can see']].map(([i,t,s])=>prow(i,t,s,`<span style="margin-left:auto">${I('chevron_down','i rot')}</span>`)).join('')}`;
SP.settingsEasy=`<div class="easyh" style="font-size:28px">Settings</div><div class="mi-s" style="font-size:17px">The things people change most.</div>
 <div class="easyg">${[['text_size','Text size'],['speaker','Read aloud'],['sound','Volume'],['family','Who helps me'],['moon','Night'],['settings','All settings']].map(([i,t],k)=>`<div class="eb2" style="height:118px;font-size:17px">${I(i)}${t}</div>`).join('')}</div>`;
SP.helpTours=`<div class="mi-t">Help & check-up</div><div class="srch">${I('guide_me')}Ask how to do something…</div>
 <div class="lbl3">Show me around · short tours</div>
 ${[['home','Home and the dock','1 min',1],['voice','Talking to halo','1 min',1],['draw','HaloStudio','2 min',0],['notebook','HaloNotes','1 min',0],['canvas','HaloCanvas','2 min',0],['study','Learn','2 min',0]].map(([i,t,d,dn])=>`<div class="tour">${I(i)}<div><b>${t}</b><span>${d}</span></div>${dn?`<span class="dn">${I('check')}seen</span>`:`<span class="go">${I('play')}Start</span>`}</div>`).join('')}
 <div class="lockline">${I('diagnostics')}Check-up · all good · 9:40</div>`;
SP.tourStep=S.welcome;
/* ---------- HaloStudio ---------- */
SP.studioEasy=`<div class="dim"><div class="board" style="padding-top:6px"><div class="art" style="width:290px;height:340px"><div class="sun"></div><div class="hill2"></div><div class="hill"></div><div class="tx">Autumn<br>market</div></div></div></div>
 <div class="easyg" style="grid-template-columns:repeat(4,1fr);gap:8px;margin-top:12px">${[['draw','Draw',1],['heading','Words'],['image','Picture'],['undo','Undo']].map(([i,t,p])=>`<div class="eb2 ${p?'p':''}" style="height:84px;font-size:14px;border-radius:20px;gap:6px">${I(i)}${t}</div>`).join('')}</div>`;
SP.studioPrefs=`<div class="crumb">${I('draw')}HaloStudio › Make it yours</div><div class="mi-t" style="font-size:23px">Your Studio</div>
 <div class="lbl3">Favourite tools · in the dock</div><div class="favs">${[['draw','Draw',1],['heading','Text',1],['image','Picture',1],['shapes','Shapes',1],['annotate','Pen'],['fit_all','Crop']].map(([i,t,on])=>`<span class="${on?'on':''}">${I(i)}${t}</span>`).join('')}</div>
 <div class="lbl3">Artboard background</div>${segs(['Plain','Dots','Grid'],1)}
 <div class="tl">${I('hand')}<div>Left-handed<small>tools on the left, Undo on the right</small></div>${tgl(0)}</div>
 <div class="tl">${I('motion')}<div>Snap with a little bounce<small>Gentle: soft snap · Still: none</small></div>${tgl(1)}</div>
 <div class="tl">${I('guide_me')}<div>Name tools while I draw<small>short labels under each tool</small></div>${tgl(1)}</div>`;
/* ---------- HaloNotes ---------- */
SP.notesEasy=`<div class="doc easyd"><div class="dh" style="font-size:30px">Market list</div><div class="dm" style="font-size:16px">Saturday · for the stall</div>
 <div class="chk on" style="font-size:21px">Planters</div><div class="chk on" style="font-size:21px">Candles in jars</div><div class="chk" style="font-size:21px"><span class="kara2">Price <span class="now">cards</span></span></div><div class="chk" style="font-size:21px">Card reader</div></div>`;
SP.notesPrefs=`<div class="crumb">${I('notebook')}HaloNotes › Make it yours</div><div class="mi-t" style="font-size:23px">Your notes</div>
 <div class="papers">${[['Plain',''],['Lined','ln'],['Grid','gd']].map(([t,c],i)=>`<div class="pp ${i==1?'on':''}"><div class="pps ${c}"><i></i><i></i><i></i></div>${t}</div>`).join('')}</div>
 <div class="lbl3">Letters</div>${segs(['Easy-read','Book','Round'],0)}
 <div class="lbl3">Space between lines</div>${segs(['Normal','Roomy','Extra'],1)}
 <div class="tl">${I('mic')}<div>Hold the ring to dictate<small>your words appear as you speak</small></div>${tgl(1)}</div>
 <div class="tl">${I('speaker')}<div>Read back with highlight<small>words light up as halo reads</small></div>${tgl(1)}</div>`;
/* ---------- HaloCanvas ---------- */
SP.canvasList=`<div class="crumb">${I('canvas')}Shop ideas · as a list</div><div class="vsw" style="margin-top:8px"><span>${I('canvas')}Board</span><span class="on">${I('notebook')}List</span></div>
 ${[['Shop ideas','9 cards','',''],['Poster','image · HaloStudio','→ Shop ideas','studio'],['Market list','note · HaloNotes','→ Shop ideas','office'],['Winter candles?','idea','→ Poster','canvas']].map(([t,s,l,c])=>`<div class="frow"><div class="fi ${c}">${I(c=='studio'?'image':c=='office'?'file_doc':'canvas')}</div><div><b>${t}</b><span>${s}</span></div>${l?`<span class="lnk">${l}</span>`:''}</div>`).join('')}
 <div class="lockline">${I('connection')}Links read as “linked to …” — same board, no dragging</div>`;
SP.canvasPrefs=`<div class="crumb">${I('canvas')}HaloCanvas › Make it yours</div><div class="mi-t" style="font-size:23px">Your boards</div>
 <div class="lbl3">Card colours</div><div class="favs">${[['By kind',1],['By me',0],['One colour',0]].map(([t,on])=>`<span class="${on?'on':''}">${t}</span>`).join('')}</div>
 <div class="kinds">${[['Images','#F3C0D9'],['Notes','#FF9F6B'],['Lessons','#B4E6D6'],['Ideas','#C9B8FF']].map(([t,c])=>`<span><i style="background:${c}"></i>${t}</span>`).join('')}</div>
 <div class="lbl3">Links</div>${segs(['Curved','Straight','Dotted'],0)}
 <div class="tl">${I('grid_view')}<div>Snap cards to a grid<small>easier to keep tidy</small></div>${tgl(1)}</div>
 <div class="tl">${I('notebook')}<div>Open boards as a list<small>good for Easy view and TalkBack</small></div>${tgl(0)}</div>`;
/* ---------- HaloStudy / HaloMemory tool prefs ---------- */
SP.studyPrefs=`<div class="crumb">${I('study')}HaloStudy › Make it yours</div><div class="mi-t" style="font-size:23px">How you learn</div>
 <div class="lbl3">Size</div>${segs(['Pictures','Simple','Full'],2)}
 <div class="lbl3">Highlight while reading</div><div class="kinds">${[['Mint','#0CF2BA',1],['Lilac','#C9B8FF'],['Sun','#FFE07A'],['Underline','#FAFAF4']].map(([t,c,on])=>`<span class="${on?'on':''}"><i style="background:${c}"></i>${t}</span>`).join('')}</div>
 <div class="tl">${I('text_size')}<div>Easy-read spacing<small>wider letters and lines</small></div>${tgl(1)}</div>
 <div class="tl">${I('reminder')}<div>Practice reminder<small>a quiet note at 18:00 when cards are due</small></div>${tgl(1)}</div>
 <div class="tl">${I('voice')}<div>Reading voice<small>Calm · normal speed</small></div>${val('Change')}</div>`;
/* ---------- Assistant · mini halo · Kids ---------- */
SP.miniPrefs=`<div class="crumb">${I('mini_halo')}mini halo › Make it yours</div><div class="mi-t" style="font-size:23px">your mini halo</div>
 <div class="wpbig" style="width:190px;height:250px;border-radius:22px">${cvs('drift',{})}<div class="bub" style="right:10px;top:120px"></div></div>
 <div class="lbl3">Bubble size</div>${segs(['Small','Medium','Large'],1)}
 <div class="lbl3">Sits on the</div>${segs(['Left','Right'],1)}
 <div class="tl">${I('play')}<div>Hide during videos & games<small>comes back when you stop</small></div>${tgl(1)}</div>`;
SP.kidsWall=`<div class="kq" style="margin-top:14px;font-size:22px">Pick your sky</div>
 <div class="kwall">${[['Stars','dayarc',.95,1],['Sea','tide',.5],['Garden','paper',.5],['Moon','aurora',.8]].map(([t,k,tod,on])=>`<div class="kw ${on?'on':''}"><div class="kwc">${cvs(k,{tod,motion:.5})}</div><b>${t}</b><small>${I('speaker')}hear</small></div>`).join('')}</div>`;
SP.kidsGrown=`<div class="crumb">${I('lock')}Grown-up · Mila’s halo</div><div class="mi-t" style="font-size:23px">Mila’s look & help</div><div class="mi-s">Opened with your phone lock. Mila can pick a sky; everything else stays.</div>
 ${prow('wallpaper','Wallpapers Mila can pick','Stars, Sea, Garden, Moon · gentle only',val('4'))}
 ${prow('motion','Motion','Gentle — never fast or flashing',val('Gentle'))}
 ${prow('text_size','Learn size','Pictures',val('Size 1'))}
 ${prow('guide_me','Hand demos','first 3 times for each gesture',tgl(1))}
 ${prow('speaker','Read everything aloud','buttons, choices, stories',tgl(1))}`;
/* ---------- tagging / hand helpers (run after render) ---------- */
function placeTags(phone,tags){const R=phone.getBoundingClientRect(),z=R.width/390;const used=[];
 tags.forEach(([sel,label,n],k)=>{const e=phone.querySelector(sel);if(!e)return;const r=e.getBoundingClientRect();const d=document.createElement('div');d.className='glc dn';d.innerHTML=`<i>${n||k+1}</i>${label}`;phone.appendChild(d);
  const w=d.offsetWidth,h=d.offsetHeight;let x=(r.left-R.left)/z+Math.min(24,(r.width/z)/2-10),y=(r.top-R.top)/z-h-6;x=Math.max(8,Math.min(390-w-8,x));
  for(let g=0;g<6;g++){if(used.some(u=>x<u[0]+u[2]&&u[0]<x+w&&y<u[1]+u[3]&&u[1]<y+h))y-=h+4;else break}used.push([x,y,w,h]);
  const ax=Math.max(10,Math.min(w-20,(r.left-R.left)/z+(r.width/z)/2-x-5));d.style.cssText=`left:${x}px;top:${y}px;--ax:${ax}px`})}
function placeHand(phone,sel,card,cardTop,drag){const R=phone.getBoundingClientRect(),z=R.width/390;const e=phone.querySelector(sel);if(!e)return;const r=e.getBoundingClientRect();
 const cx=(r.left-R.left+r.width/2)/z,cy=(r.top-R.top+r.height/2)/z;const h=document.createElement('div');
 h.innerHTML=`<div class="hole" style="left:${cx-r.width/z/2-8}px;top:${cy-r.height/z/2-8}px;width:${r.width/z+16}px;height:${r.height/z+16}px;border-radius:18px"></div>${drag?`<svg style="position:absolute;left:0;top:0;width:390px;height:844px;z-index:44;pointer-events:none"><path d="M${cx} ${cy} Q ${cx+drag[0]/2+40} ${cy+drag[1]/2-60} ${cx+drag[0]} ${cy+drag[1]}" stroke="#C9B8FF" stroke-width="3" stroke-dasharray="6 7" fill="none"/><circle cx="${cx+drag[0]}" cy="${cy+drag[1]}" r="9" fill="none" stroke="#C9B8FF" stroke-width="3"/></svg>`:''}
 <svg class="hand" viewBox="0 0 54 54" style="left:${cx-6}px;top:${cy-10}px"><circle cx="20" cy="14" r="12" fill="rgba(201,184,255,.45)"/><path d="M17 30V13a3 3 0 0 1 6 0v12m0-3a3 3 0 0 1 6 0v3m0-1a3 3 0 0 1 6 0v3m0-1a3 3 0 0 1 6 0v8c0 7-5 12-12 12h-2c-5 0-8-2-11-6l-6-8a3 3 0 0 1 4.5-4L17 34" fill="#FAFAF4" stroke="#1b1446" stroke-width="2" stroke-linejoin="round"/></svg>
 <div class="coachcard" style="top:${cardTop}px"><div class="k2">${card[0]}</div><b>${card[1]}</b><p>${card[2]}</p><div class="cbtns"><span>${I('retry')}Again</span><span>${I('play')}Do it for me</span><span class="p">${I('check')}Got it</span></div></div>`;
 while(h.firstChild)phone.appendChild(h.firstChild)}
