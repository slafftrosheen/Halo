function ringK(){const g='k'+(gid++);return `<svg class="ring" viewBox="0 0 240 240"><defs><linearGradient id="${g}" x1="36" y1="36" x2="204" y2="204" gradientUnits="userSpaceOnUse"><stop stop-color="#0CF2BA"/><stop offset="1" stop-color="#FF9F6B"/></linearGradient></defs><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="url(#${g})" stroke-width="24" stroke-linecap="round" fill="none"/></svg>`}
const orbK=()=>`<div class="orb"><div class="glow"></div><div class="rip"></div><div class="rip b"></div>${ringK()}</div>`;
W.draft=()=>`<div class="dim"><div class="crumb">${I('home')}Chat · Saturday</div>
 <div class="msg">Tell Anna I’ll be ten minutes late.</div>
 <div class="ans"><p>Here’s the message. I’ll only send it when you say so.</p>
 <div class="draft"><div class="h2">${I('approval')}Waiting for you · not sent</div>To Anna: Running about ten minutes late — save me a seat by the window? ☕</div></div></div>`;
W.kids=()=>`<div class="crumb" style="color:#5B4A60">${I('study')}Counting · step 2 of 4</div><div class="kq">How many apples are in the basket?</div>
 <div class="apples"><i></i><i></i><i></i><i></i><i></i></div><div class="kch"><div>4</div><div class="g">5</div><div>6</div></div>`;
const CAP={idle:'Talk',nav:'Talk',listen:'Listening',work:'Working',need:'Waiting for you',done:'Talk'};
function zoneM(scr,state,kid){
 const studio=scr=='studio';
 const placeIdle = kid?`<div class="place"><div class="pi">${I('study')}</div><div><div class="pt">Counting apples</div><div class="ps">Learn · step 2 of 4</div></div></div>`
  : studio?`<div class="place"><div class="pi">${I('draw')}</div><div><div class="pt">Poster draft</div><div class="ps">HaloStudio · <span class="ok">${I('check')}</span>Saved</div></div></div><div class="next">${I('guide_me')}Next: add a title</div>`
  :`<div class="place"><div class="pi home">${I('home')}</div><div><div class="pt">Chat</div><div class="ps">Private · on this phone</div></div></div><div class="next">${I('history')}History</div>`;
 const r2 = kid?`<div class="act p">${I('heading')}Type</div><div class="act">${I('guide_me')}Help</div>`
  : studio?`<div class="act p">${I('add')}Add</div><div class="act">${I('draw')}Draw</div><div class="act">${I('group')}Layers</div><div class="act">${I('more')}More</div>`
  :`<div class="ibtn">${I('attach')}</div><div class="comp">Message halo…<div class="send">${I('send')}</div></div>`;
 const nav = kid?`<div class="tab on"><div class="ind">${I('home')}</div>Home</div><div class="ocell">${orbK()}<div class="cap">Talk</div></div><div class="tab"><div class="ind">${I('study')}</div>Learn</div>`
  :`<div class="tab ${!studio&&state!='nav'?'on':''}"><div class="ind">${I('home')}</div>Home</div><div class="tab ${studio&&state!='nav'?'on':''}"><div class="ind">${I('notebook')}</div>Workspace</div>
 <div class="ocell">${orb()}<div class="cap">${CAP[state]}</div></div>
 <div class="tab"><div class="ind">${I('notifications')}<span class="badge">2</span></div>Activity</div><div class="tab ${state=='nav'?'on':''}"><div class="ind">${I('grid_view')}</div>Places</div>`;
 return `<div class="zone"><div class="zbg glass"></div><div class="navbg glass"></div><div class="horizon"></div><div class="shim"></div>
 <div class="pn idle"><div class="r1">${placeIdle}</div><div class="r2">${r2}</div></div>
 <div class="pn listen"><div class="r1">${wave(7)}<span class="cap" style="color:var(--mint)">Listening</span><div class="btn stop" style="margin-left:auto;height:44px">${I('stop')}Stop</div></div><div style="margin-top:6px">${words}</div></div>
 <div class="pn work"><div class="r1"><div class="spin"><svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="11" stroke="rgba(12,242,186,.2)" stroke-width="3" fill="none"/><path d="M14 3a11 11 0 0 1 11 11" stroke="#0CF2BA" stroke-width="3" stroke-linecap="round" fill="none"/></svg></div><div><div class="tt">Planning your Saturday</div><div class="ts">Step 2 of 3 · finding places</div></div><div class="btn stop" style="margin-left:auto;height:44px">${I('stop')}Stop</div></div>
  <div class="r2"><div class="steps"><div class="s d">${I('check')}Calendar</div><div class="s c">${I('search')}Places</div><div class="s">${I('calendar')}Add</div></div></div></div>
 <div class="pn need"><div class="r1"><div class="ap">${I('approval')}</div><div><div class="tt">Send this to Anna?</div><div class="ts">Message · nothing is sent yet</div></div></div>
  <div class="r2"><div class="btn">Not now</div><div class="btn ap2">${I('send')}Send to Anna</div></div></div>
 <div class="pn done"><div class="r1"><div class="ok2">${I('check')}</div><div><div class="tt">Added 3 events</div><div class="ts">Calendar · confirmed</div></div></div>
  <div class="r2"><div class="btn">${I('undo')}Undo</div><div class="btn">${I('calendar')}Open calendar</div></div></div>
 <div class="nav">${nav}</div></div>
 ${kid?'':`<div class="sheet glass"><div class="grab"></div><div class="sh">Places<small>hold the ring · or tap Places</small></div><div class="tiles">${tiles(studio?'Workspace':'Home')}</div>
 <div class="prof">${I('privacy')}Private mode — everything stays on this phone<div class="t"></div></div></div>`}`;
}
Z.M=zoneM;
function phoneM(scr,state,o={}){
 return `<div class="phone M st-${state} ${o.kid?'kid':''} ${o.split?'show-split':''}"><div class="wall"></div>${sb}<div class="work">${W[scr]()}</div>${zoneM(scr,state,o.kid)}<div class="split"></div><div class="edge"></div><div class="gb"></div></div>`;
}
