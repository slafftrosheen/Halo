let gid=0;
const I=(n,cls='i')=>`<svg class="${cls}" viewBox="0 0 24 24" fill="none">${IC[n]||''}</svg>`;
function ring(){const g='g'+(gid++);return `<svg class="ring" viewBox="0 0 240 240"><defs><linearGradient id="${g}" x1="36" y1="36" x2="204" y2="204" gradientUnits="userSpaceOnUse"><stop stop-color="#0CF2BA"/><stop offset="1" stop-color="#B4E6D6"/></linearGradient></defs><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="url(#${g})" stroke-width="22" stroke-linecap="round" fill="none"/></svg>`}
const orb=()=>`<div class="orb"><div class="glow"></div><div class="rip"></div><div class="rip b"></div>${ring()}</div>`;
const sb=`<div class="sb"><span>9:41</span><span class="r"><i></i><i></i><i></i></span></div>`;
const wave=n=>`<div class="wave">${'<i></i>'.repeat(n)}</div>`;
const words=`<div class="words">Add milk and eggs <span class="w">to my</span> <span class="n">shopping list…</span></div>`;

/* ---------- work areas ---------- */
const W={
home:()=>`<div class="dim"><div class="crumb">${I('home')}Chat · Saturday</div>
 <div class="msg">Plan a quiet Saturday in Riga — nothing before ten.</div>
 <div class="ans"><p>Here is a slow one. I kept the mornings free.</p>
 <div class="card"><div class="li">${I('timer')}<b>10:30</b> Coffee at Miera iela<span>15 min walk</span></div>
 <div class="li">${I('study')}<b>12:00</b> Art Nouveau quarter<span>2 h</span></div>
 <div class="li">${I('calendar')}<b>16:00</b> Market hall, then home<span>tram 7</span></div></div>
 <div class="rc">${I('check')}Added to your calendar · confirmed</div></div></div>`,
studio:()=>`<div class="dim"><div class="board"><div class="art"><div class="sun"></div><div class="hill2"></div><div class="hill"></div><div class="tx">Autumn<br>market</div><div class="sel"><i></i><i></i><i></i><i></i></div></div></div></div>`,
};
/* ---------- places for nav sheets ---------- */
const PL=[['home','Home','Chat with halo','cur'],['notebook','Workspace','Poster draft · 3 notes today',''],['notifications','Activity','2 need you','need'],
['devices','Devices','Glasses · ready',''],['settings','Settings','Voice, privacy, look',''],['widget','Widgets','Organiser, news, clocks — on your home screen','']];
const tiles=(cur)=>PL.map(([i,n,s,c])=>`<div class="tile ${n==cur?'cur':''} ${c=='need'?'need':''}"><div class="ti">${I(i)}</div><b>${n}</b><span>${s}</span>${c=='need'?'':''}</div>`).join('');

/* ---------- A · CALM DOCK ---------- */
function zoneA(scr,state){
 const studio=scr=='studio';
 const r1=studio?`<div class="place"><div class="pi">${I('draw')}</div><div><div class="pt">Poster draft</div><div class="ps">HaloStudio · <span class="ok">${I('check')}</span>Saved</div></div></div><div class="next">${I('guide_me')}Next: add a title</div>`
 :`<div class="place"><div class="pi home">${I('home')}</div><div><div class="pt">Chat</div><div class="ps">Private · on this phone</div></div></div><div class="next">${I('history')}History</div>`;
 const r2=studio?`<div class="act p">${I('add')}Add</div><div class="act">${I('draw')}Draw</div><div class="act">${I('group')}Layers</div><div class="act">${I('more')}More</div>`
 :`<div class="ibtn">${I('attach')}</div><div class="comp">Message halo…<div class="send">${I('send')}</div></div>`;
 return `<div class="zone glass"><div class="horizon"></div>
 <div class="zrow1">${r1}</div><div class="zrow2">${r2}</div>
 <div class="listen"><div class="top">${wave(9)}<div class="cap" style="margin-left:auto;color:var(--mint)">Listening</div></div><div style="margin-top:8px">${words}</div></div>
 <div class="nav"><div class="tab ${!studio&&state!='nav'?'on':''}"><div class="ind">${I('home')}</div>Home</div><div class="tab ${studio&&state!='nav'?'on':''}"><div class="ind">${I('notebook')}</div>Workspace</div>
 <div class="ocell">${orb()}<div class="cap">Talk</div></div>
 <div class="tab"><div class="ind">${I('notifications')}<span class="badge">2</span></div>Activity</div><div class="tab ${state=='nav'?'on':''}"><div class="ind">${I('more')}</div>More</div></div></div>
 <div class="sheet glass"><div class="grab"></div><div class="sh">Go to<small>Home is open</small></div><div class="tiles">${tiles(studio?'Workspace':'Home')}</div>
 <div class="prof">${I('privacy')}Private mode — everything stays on this phone<div class="t"></div></div></div>`;
}
/* ---------- B · ORBIT ---------- */
function zoneB(scr){
 const studio=scr=='studio';
 const sats=studio?[['add','Add',1],['draw','Draw'],['group','Layers'],['more','More']]:[['attach','Attach'],['new_chat','New chat',1],['history','History'],['heading','Type']];
 return `<div class="zone"><div class="ztop"><div class="place"><div class="pi ${studio?'':'home'}">${I(studio?'draw':'home')}</div><div><div class="pt">${studio?'Poster draft':'Chat'}</div><div class="ps">${studio?`HaloStudio · <span class="ok">${I('check')}</span>Saved`:'Private · on this phone'}</div></div></div>
 <div class="places">${I('grid_view')}Places</div></div>
 ${sats.map(([i,l,p],k)=>`<div class="sat s${k+1} ${p?'p':''}"><div class="c">${I(i)}</div><span>${l}</span></div>`).join('')}
 <div class="listen">${words}<div style="display:flex;justify-content:center;margin-top:10px">${wave(11)}</div></div>
 <div class="lside l"><div class="c">${I('stop')}</div>Stop</div><div class="lside r"><div class="c">${I('heading')}</div>Type</div>
 <div class="ocell">${orb()}<div class="cap">Tap to talk</div></div></div>
 <div class="psheet glass"><div class="grab"></div><div class="sh">Places<small>hold the ring · or swipe up</small></div><div class="tiles">${tiles(studio?'Workspace':'Home')}</div></div>`;
}
/* ---------- C · ISLAND ---------- */
function zoneC(scr){
 const studio=scr=='studio';
 return `<div class="zone"><div class="island glass">
 <div class="itop"><div class="place"><div class="pi ${studio?'':'home'}">${I(studio?'draw':'home')}</div><div><div class="pt">${studio?'Poster draft':'Chat'}</div><div class="ps">${studio?`HaloStudio · <span class="ok">${I('check')}</span>Saved`:'Private · on this phone'}</div></div></div>
 <div class="orbw">${orb()}<div class="cap">Talk</div></div></div>
 <div class="chips">${studio?`<div class="chip p">${I('add')}Add</div><div class="chip">${I('draw')}Draw</div><div class="chip">${I('group')}Layers</div><div class="chip">${I('more')}</div>`
 :`<div class="chip">${I('attach')}Attach</div><div class="chip p">${I('heading')}Type</div><div class="chip">${I('history')}History</div>`}</div>
 <div class="lv"><div class="row">${wave(10)}<span class="cap" style="color:var(--mint)">Listening</span><div style="margin-left:auto">${orb()}</div></div><div style="margin-top:10px">${words}</div>
 <div class="row" style="margin-top:12px"><div class="btn stop" style="height:44px;flex:1">${I('stop')}Stop</div><div class="btn" style="height:44px;flex:1">Type instead</div></div></div>
 <div class="nv"><div class="sh">Places<small>swipe the island to switch</small></div><div class="tiles">${tiles(studio?'Workspace':'Home')}</div>
 <div class="prof">${I('privacy')}Private mode — on this phone<div class="t"></div></div></div>
 </div>
 <div class="switch"><span>Screen</span><b>${studio?'Workspace':'Home'}</b><span>${studio?'Activity':'Workspace'}</span></div></div>`;
}
const Z={A:zoneA,B:zoneB,C:zoneC};
function phone(dir,scr,state,split){
 return `<div class="phone ${dir} st-${state} ${split?'show-split':''}" data-dir="${dir}"><div class="wall"></div>${sb}<div class="work">${W[scr]()}</div>${Z[dir](scr,state)}<div class="split"></div><div class="edge"></div><div class="gb"></div></div>`;
}
