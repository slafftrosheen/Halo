/* halo 7.2 · Make it yours, alive */
(function(){const s='stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" fill="none"';Object.assign(window.IC,{
wallpaper:`<path d="M6.5 3.5H17.5A2.5 2.5 0 0 1 20 6V18A2.5 2.5 0 0 1 17.5 20.5H6.5A2.5 2.5 0 0 1 4 18V6A2.5 2.5 0 0 1 6.5 3.5Z" ${s}/><path d="M4 16.5C7 13 10 13 12 15C14 17 17 15 20 12.5" ${s}/><path d="M15 7.2A1.6 1.6 0 1 1 15 10.4A1.6 1.6 0 1 1 15 7.2" ${s}/>`,
palette:`<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7c0-1.3-1-1.6-1-2.6c0-1 .8-1.7 1.8-1.7h2.2a3.7 3.7 0 0 0 3.7-3.7C20.5 6.8 16.7 3.5 12 3.5Z" ${s}/><path d="M7.8 11.2h.01M9.8 7.6h.01M14 7.4h.01" ${s} stroke-width="2.6"/>`,
text_size:`<path d="M3.5 18L8 6.5L12.5 18M5.2 14H10.8" ${s}/><path d="M14 18L17.25 10L20.5 18M15.2 15.2H19.3" ${s}/>`,
lock:`<path d="M7 10.5H17A2 2 0 0 1 19 12.5V18.5A2 2 0 0 1 17 20.5H7A2 2 0 0 1 5 18.5V12.5A2 2 0 0 1 7 10.5Z" ${s}/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" ${s}/>`,
sun:`<path d="M12 8.2A3.8 3.8 0 1 1 12 15.8A3.8 3.8 0 1 1 12 8.2" ${s}/><path d="M12 3V4.8M12 19.2V21M3 12H4.8M19.2 12H21M5.6 5.6L6.9 6.9M17.1 17.1L18.4 18.4M5.6 18.4L6.9 17.1M17.1 6.9L18.4 5.6" ${s}/>`,
moon:`<path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10Z" ${s}/>`,
motion:`<path d="M3.5 12c2.5-5 5-5 7.5 0s5 5 7.5 0" ${s}/><path d="M19.5 8.5V12H16" ${s}/>`,
family:`<path d="M8 4.5A2.5 2.5 0 1 1 8 9.5A2.5 2.5 0 1 1 8 4.5" ${s}/><path d="M16.5 7A2 2 0 1 1 16.5 11A2 2 0 1 1 16.5 7" ${s}/><path d="M3.5 19.5v-2a4.5 4.5 0 0 1 9 0v2M13.5 19.5v-1.5a3 3 0 0 1 6 0v1.5" ${s}/>`,
hand:`<path d="M9 11.5V5.2a1.5 1.5 0 0 1 3 0v5.3" ${s}/><path d="M12 10V8.8a1.5 1.5 0 0 1 3 0V11M15 10.2a1.5 1.5 0 0 1 3 0V15c0 3.3-2.4 5.5-5.6 5.5c-2.2 0-3.6-.9-4.8-2.6L5 14.2a1.4 1.4 0 0 1 2.2-1.7L9 14" ${s}/>`,
dock:`<path d="M5.5 3.5H18.5A2 2 0 0 1 20.5 5.5V18.5A2 2 0 0 1 18.5 20.5H5.5A2 2 0 0 1 3.5 18.5V5.5A2 2 0 0 1 5.5 3.5Z" ${s}/><path d="M3.5 15H20.5M8 17.8h.01M12 17.8h.01M16 17.8h.01" ${s}/>`,
sound:`<path d="M4 9.5H7L11 6V18L7 14.5H4Z" ${s}/><path d="M14.5 9.5a3.5 3.5 0 0 1 0 5M17 7a7 7 0 0 1 0 10" ${s}/>`,
eye:`<path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12Z" ${s}/><path d="M12 9.5A2.5 2.5 0 1 1 12 14.5A2.5 2.5 0 1 1 12 9.5" ${s}/>`});})();
const hring=(w,c='#0CF2BA',sw=22,op=1)=>`<svg viewBox="0 0 240 240" style="width:${w}px;height:${w}px;opacity:${op}"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="${c}" stroke-width="${sw}" stroke-linecap="round" fill="none"/></svg>`;
/* ---------------- deterministic noise ---------------- */
function rnd(i){const x=Math.sin(i*127.1+311.7)*43758.5453;return x-Math.floor(x)}
function hexA(h,a){const n=parseInt(h.slice(1),16);return `rgba(${n>>16&255},${n>>8&255},${n&255},${a})`}
function mix(a,b,k){const A=parseInt(a.slice(1),16),B=parseInt(b.slice(1),16);const r=Math.round((A>>16&255)*(1-k)+(B>>16&255)*k),g=Math.round((A>>8&255)*(1-k)+(B>>8&255)*k),bl=Math.round((A&255)*(1-k)+(B&255)*k);return '#'+((1<<24)+(r<<16)+(g<<8)+bl).toString(16).slice(1)}
/* time of day 0..1 → sky top/bottom */
const SKY=[[0,'#04021f','#120d4a'],[.25,'#1a1760','#ff9f6b'],[.5,'#1d3b8a','#b6dfe2'],[.75,'#2a1252','#f3c0d9'],[1,'#04021f','#120d4a']];
function sky(tod){for(let i=0;i<SKY.length-1;i++){const [a,t1,b1]=SKY[i],[b,t2,b2]=SKY[i+1];if(tod>=a&&tod<=b){const k=(tod-a)/(b-a),e=k*k*(3-2*k);return [mix(t1,t2,e),mix(b1,b2,e)]}}return [SKY[0][1],SKY[0][2]]}
/* ---------------- wallpapers ---------------- */
const WPS={
drift:{name:'Halo Drift',light:0,note:'the ring, breathing'},
aurora:{name:'Aurora',light:0,note:'slow colour ribbons'},
tide:{name:'Tide',light:0,note:'waves follow the time of day'},
dayarc:{name:'Day Arc',light:0,note:'the ring is your sun & moon'},
constellation:{name:'Constellation',light:0,note:'links glow while halo works'},
paper:{name:'Paper',light:1,note:'light, almost still'}};
function paintWP(cv,kind,t,o={}){
 const W=cv.width,H=cv.height,c=cv.getContext('2d');c.save();c.setTransform(1,0,0,1,0,0);c.globalCompositeOperation='source-over';c.filter='none';c.globalAlpha=1;
 const sc=W/390;c.scale(sc,sc);const w=390,h=H/sc;const tod=o.tod==null?.5:o.tod;const acc=o.acc||'#0CF2BA';const mot=o.motion==null?1:o.motion;t=t*mot;
 const blur=v=>{c.filter=`blur(${Math.max(.1,v*sc)}px)`};
 if(kind=='drift'){c.fillStyle='#060330';c.fillRect(0,0,w,h);c.globalCompositeOperation='lighter';blur(40);
  [[acc,.30,0],['#C9B8FF',.26,2],['#FF9F6B',.16,4],['#B6DFE2',.18,1]].forEach(([col,a,i])=>{const x=195+130*Math.cos(t*.11*(1+i*.3)+i*1.7),y=h*.4+190*Math.sin(t*.08*(1+i*.2)+i);const g=c.createRadialGradient(x,y,0,x,y,230);g.addColorStop(0,hexA(col,a));g.addColorStop(1,hexA(col,0));c.fillStyle=g;c.fillRect(0,0,w,h)});
  c.globalCompositeOperation='source-over';const br=1+.035*Math.sin(t*1.1);c.translate(195,h*.42);c.rotate(t*.06);c.scale(br,br);blur(10);c.lineCap='round';c.lineWidth=34;c.strokeStyle=hexA(acc,.42);c.beginPath();c.arc(0,0,150,-Math.PI/8,Math.PI*1.625);c.stroke();
  c.filter='none';c.lineWidth=2;c.strokeStyle=hexA('#FAFAF4',.18);c.beginPath();c.arc(0,0,150,-Math.PI/8,Math.PI*1.625);c.stroke();}
 else if(kind=='aurora'){const g0=c.createLinearGradient(0,0,0,h);g0.addColorStop(0,'#04021f');g0.addColorStop(1,'#0d0840');c.fillStyle=g0;c.fillRect(0,0,w,h);
  c.globalCompositeOperation='lighter';blur(22);[[acc,.34,150],['#C9B8FF',.30,260],['#B6DFE2',.22,370],['#F3C0D9',.16,470]].forEach(([col,a,base],b)=>{
   c.beginPath();const pts=[];for(let x=-20;x<=w+20;x+=10){const y=base+38*Math.sin(x*.012+t*.35*(1+b*.25)+b*1.3)+18*Math.sin(x*.031-t*.5+b);pts.push([x,y])}
   c.moveTo(pts[0][0],pts[0][1]);pts.forEach(p=>c.lineTo(p[0],p[1]));for(let i=pts.length-1;i>=0;i--){const p=pts[i];c.lineTo(p[0],p[1]+90+30*Math.sin(p[0]*.02+t*.4+b))}c.closePath();
   const g=c.createLinearGradient(0,base-40,0,base+140);g.addColorStop(0,hexA(col,0));g.addColorStop(.35,hexA(col,a));g.addColorStop(1,hexA(col,0));c.fillStyle=g;c.fill()});
  c.globalCompositeOperation='source-over';c.filter='none';for(let i=0;i<40;i++){const a=.25+.35*(.5+.5*Math.sin(t*1.3+i));c.fillStyle=hexA('#FAFAF4',a*.6);c.beginPath();c.arc(rnd(i)*w,rnd(i+50)*h*.55,rnd(i+9)*1.2+.3,0,7);c.fill()}}
 else if(kind=='tide'){const [a,b]=sky(tod);const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,a);g.addColorStop(.62,b);g.addColorStop(1,b);c.fillStyle=g;c.fillRect(0,0,w,h);
  const sy=h*(.62-.38*Math.sin(Math.PI*Math.min(1,Math.max(0,(tod-.2)/.6))));const sg=c.createRadialGradient(270,sy,0,270,sy,140);sg.addColorStop(0,hexA('#FFE7C9',.75));sg.addColorStop(.25,hexA('#FF9F6B',.35));sg.addColorStop(1,hexA('#FF9F6B',0));c.fillStyle=sg;c.fillRect(0,0,w,h);
  const dark=tod<.18||tod>.86;for(let L=0;L<5;L++){const base=h*.6+L*62,amp=10+L*4,sp=.5+L*.18;c.beginPath();c.moveTo(0,h);for(let x=0;x<=w;x+=6){c.lineTo(x,base+amp*Math.sin(x*(.018-L*.002)+t*sp+L*1.7)+amp*.4*Math.sin(x*.05-t*sp*1.3))}c.lineTo(w,h);c.closePath();
   c.fillStyle=mix(dark?'#16125a':'#2a2580','#060330',L/4.5);c.globalAlpha=.86;c.fill();c.globalAlpha=1;
   c.strokeStyle=hexA(L==0?acc:'#FAFAF4',L==0?.5:.08);c.lineWidth=1.5;c.stroke()}}
 else if(kind=='dayarc'){const [a,b]=sky(tod);const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,a);g.addColorStop(1,b);c.fillStyle=g;c.fillRect(0,0,w,h);
  const night=tod<.2||tod>.82;if(night)for(let i=0;i<70;i++){const tw=.4+.6*(.5+.5*Math.sin(t*2+i*1.7));c.fillStyle=hexA('#FAFAF4',.55*tw);c.beginPath();c.arc(rnd(i)*w,rnd(i+80)*h*.7,rnd(i+3)*1.3+.3,0,7);c.fill()}
  const cx=195,cy=h*.62,R=230;c.setLineDash([2,7]);c.strokeStyle=hexA('#FAFAF4',.25);c.lineWidth=1.5;c.beginPath();c.arc(cx,cy,R,Math.PI,0);c.stroke();c.setLineDash([]);
  const p=night?((tod>.5?tod-.82:tod+.18)/.38):(tod-.2)/.62;const ang=Math.PI+Math.PI*Math.min(1,Math.max(0,p));const sx=cx+R*Math.cos(ang),sy2=cy+R*Math.sin(ang);
  const col=night?'#C9B8FF':acc;const gl=c.createRadialGradient(sx,sy2,0,sx,sy2,120);gl.addColorStop(0,hexA(night?'#C9B8FF':'#FFE7C9',.55));gl.addColorStop(1,hexA('#FFE7C9',0));c.fillStyle=gl;c.fillRect(0,0,w,h);
  c.save();c.translate(sx,sy2);c.rotate(t*.3);c.lineCap='round';c.lineWidth=9;c.strokeStyle=col;c.beginPath();c.arc(0,0,26,-Math.PI/8,Math.PI*1.625);c.stroke();c.restore();
  c.fillStyle=hexA('#060330',.55);c.beginPath();c.moveTo(0,h);for(let x=0;x<=w;x+=8)c.lineTo(x,cy+40+16*Math.sin(x*.015+1)+8*Math.sin(x*.05));c.lineTo(w,h);c.fill();
  c.fillStyle=hexA('#060330',.85);c.beginPath();c.moveTo(0,h);for(let x=0;x<=w;x+=8)c.lineTo(x,cy+110+12*Math.sin(x*.02+3));c.lineTo(w,h);c.fill()}
 else if(kind=='constellation'){const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#050228');g.addColorStop(1,'#120d4a');c.fillStyle=g;c.fillRect(0,0,w,h);
  const N=48,P=[];for(let i=0;i<N;i++)P.push([rnd(i)*w+18*Math.sin(t*.25+i),rnd(i+99)*h+14*Math.cos(t*.2+i*1.3)]);const work=o.state=='work';
  for(let i=0;i<N;i++)for(let j=i+1;j<N;j++){const dx=P[i][0]-P[j][0],dy=P[i][1]-P[j][1],d=Math.hypot(dx,dy);if(d<95){let a=(1-d/95)*.32;let col='#FAFAF4';if(work){const pulse=.5+.5*Math.sin(t*3-(P[i][1]/h)*8);a=(1-d/95)*(.35+.55*pulse);col=acc}c.strokeStyle=hexA(col,a);c.lineWidth=1.2;c.beginPath();c.moveTo(P[i][0],P[i][1]);c.lineTo(P[j][0],P[j][1]);c.stroke()}}
  P.forEach((p,i)=>{c.fillStyle=hexA(work&&i%3==0?acc:'#FAFAF4',.8);c.beginPath();c.arc(p[0],p[1],1.6+rnd(i+7)*1.4,0,7);c.fill()})}
 else if(kind=='paper'){c.fillStyle='#F4F1E8';c.fillRect(0,0,w,h);blur(46);[['#8C9A7B',.30,0],['#B6DFE2',.38,2],['#F3C0D9',.28,4]].forEach(([col,a,i])=>{const x=195+120*Math.cos(t*.06+i),y=h*.45+200*Math.sin(t*.05+i*1.1);const g=c.createRadialGradient(x,y,0,x,y,220);g.addColorStop(0,hexA(col,a));g.addColorStop(1,hexA(col,0));c.fillStyle=g;c.fillRect(0,0,w,h)});
  c.filter='none';c.save();c.translate(195,h*.4);c.rotate(t*.03);c.lineCap='round';c.lineWidth=28;c.strokeStyle=hexA('#1d1f24',.05);c.beginPath();c.arc(6,10,150,-Math.PI/8,Math.PI*1.625);c.stroke();c.strokeStyle=hexA('#0a7f66',.16);c.beginPath();c.arc(0,0,150,-Math.PI/8,Math.PI*1.625);c.stroke();c.restore();
  for(let i=0;i<900;i++){c.fillStyle=hexA(rnd(i+3)>.5?'#000000':'#ffffff',.05);c.fillRect(rnd(i)*w,rnd(i+400)*h,1,1)}}
 /* halo-state overlays */
 c.filter='none';c.globalCompositeOperation='source-over';const st=o.state,amt=o.amt==null?1:o.amt;
 if(st=='listen'&&amt>0){const bx=195,by=h-150;const gg=c.createRadialGradient(bx,h,0,bx,h,340);gg.addColorStop(0,hexA(acc,.28*amt));gg.addColorStop(1,hexA(acc,0));c.fillStyle=gg;c.fillRect(0,0,w,h);
  for(let k=0;k<4;k++){const r=((o.rt==null?t:o.rt)*90+k*95)%380;c.strokeStyle=hexA(acc,(1-r/380)*.55*amt);c.lineWidth=2.5;c.beginPath();c.arc(bx,by,r,0,7);c.stroke()}}
 if(st=='need'&&amt>0){const br=.75+.25*Math.sin((o.rt==null?t:o.rt)*2);const gg=c.createRadialGradient(195,h+40,0,195,h+40,460);gg.addColorStop(0,hexA('#FF9F6B',.42*amt*br));gg.addColorStop(1,hexA('#FF9F6B',0));c.fillStyle=gg;c.fillRect(0,0,w,h)}
 if(st=='work'&&amt>0&&kind!='constellation'){const gg=c.createRadialGradient(195,h-140,0,195,h-140,260);gg.addColorStop(0,hexA(acc,.16*amt));gg.addColorStop(1,hexA(acc,0));c.fillStyle=gg;c.fillRect(0,0,w,h)}
 if(o.dim){c.fillStyle=`rgba(0,0,10,${o.dim})`;c.fillRect(0,0,w,h)}
 c.restore()}
function cvs(kind,o={}){return `<canvas class="wpc" data-k="${kind}" data-o='${JSON.stringify(o)}'></canvas>`}
function paintAll(t,root=document){root.querySelectorAll('canvas.wpc').forEach(cv=>{const r=cv.getBoundingClientRect();const z=parseFloat(cv.dataset.z||1);const W=Math.max(40,Math.round(cv.offsetWidth*1.5)),H=Math.max(40,Math.round(cv.offsetHeight*1.5));if(cv.width!=W||cv.height!=H){cv.width=W;cv.height=H}const o=JSON.parse(cv.dataset.o||'{}');paintWP(cv,cv.dataset.k,(o.t||0)+t,o)})}
/* ---------------- widgets (phase-driven) ---------------- */
const SB=`<div class="sb"><span>9:41</span><span class="r"><i></i><i></i><i></i></span></div>`;
const SBL=`<div class="sb" style="color:#1d1f24"><span>9:41</span><span class="r"><i style="background:#1d1f24"></i><i style="background:#1d1f24"></i><i style="background:#1d1f24"></i></span></div>`;
const ar=`<span class="ar">${hring(18)}</span>`;
const WD={
ask:(p={})=>{const lis=p.listen;const br=1+.05*Math.sin((p.t||0)*2.2);return `<div class="w2 ask ${p.lt?'lt':''}" style="${p.pos||''};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px">
 <div style="position:relative;width:76px;height:76px;transform:scale(${lis?1.04:br})"><div style="position:absolute;inset:-26%;border-radius:50%;background:radial-gradient(closest-side,${lis?'rgba(12,242,186,.45)':'rgba(255,159,107,.25)'},rgba(12,242,186,.14) 60%,transparent)"></div><div style="position:relative;transform:rotate(${(p.t||0)*(lis?60:8)}deg)">${hring(76)}</div></div>
 ${lis?`<div class="wave2">${[...Array(9)].map((_,i)=>`<i style="height:${6+18*Math.abs(Math.sin((p.t||0)*6+i*.9))}px"></i>`).join('')}</div>`:`<div style="font:600 16px Sora">Ask halo</div>`}</div>`},
clock:(p={})=>{const sec=p.sec==null?24:p.sec;const C=2*Math.PI*54,f=sec/60;return `<div class="w2 ${p.lt?'lt':''}" style="${p.pos||''};display:grid;place-items:center;text-align:center"><div style="position:relative;width:128px;height:128px;display:grid;place-items:center">
 <svg viewBox="0 0 128 128" style="position:absolute;inset:0"><circle cx="64" cy="64" r="54" stroke="${p.lt?'rgba(0,0,0,.08)':'rgba(250,250,244,.10)'}" stroke-width="7" fill="none"/><circle cx="64" cy="64" r="54" stroke="#0CF2BA" stroke-width="7" fill="none" stroke-linecap="round" stroke-dasharray="${C*f} ${C}" transform="rotate(-90 64 64)"/></svg>
 <div><div class="bigt" style="font-size:36px">9:41</div><div class="ws">Sat 21 Nov</div></div></div></div>`},
day:(p={})=>{const ph=p.ph||0;/* 0 normal, 0..1 refresh slide */const rows=[['10:30','Coffee at Miera iela','in 49 min'],['14:00','Pick up the frame','Brīvības 40'],['16:00','Market hall','']];const nr=['17:30','Anna · call back','new'];
 const k=Math.min(1,Math.max(0,ph)),e=1-Math.pow(1-k,3);return `<div class="w2 ${p.lt?'lt':''}" style="${p.pos||''}"><div class="hd2">${I('calendar')}Your day · Saturday ${ar}</div><div style="margin-top:6px;position:relative;height:102px;overflow:hidden"><div style="transform:translateY(${-34*e}px)">
 ${rows.concat([nr]).map(([a,b,x],i)=>`<div class="row2" style="${i==3?`opacity:${e}`:''}${i==0?`;opacity:${1-e}`:''}"><span class="t">${a}</span>${b}<span class="x" ${i==3?'style="color:var(--mint)"':''}>${x}</span></div>`).join('')}</div></div></div>`},
tasks:(p={})=>{const ph=p.ph||0;const k1=Math.min(1,Math.max(0,ph/.3)),k2=Math.min(1,Math.max(0,(ph-.3)/.3)),k3=Math.min(1,Math.max(0,(ph-.62)/.38));const e3=1-Math.pow(1-k3,3);
 return `<div class="w2 ${p.need?'need':''} ${p.lt?'lt':''}" style="${p.pos||''}"><div class="hd2">${I('task')}${p.need?'<span style="color:var(--ap)">1 needs you</span>':'Tasks · today'} ${ar}</div><div style="margin-top:6px;position:relative;height:102px;overflow:hidden">
 <div style="height:${34*(1-e3)}px;opacity:${1-e3};overflow:hidden"><div class="row2"><span class="cb ${k1>.5?'d':''}" style="transform:scale(${1+.25*Math.sin(Math.PI*k1)})"></span><span style="color:${k2>0?'var(--t2)':'inherit'}">Call the framer</span><span class="strike" style="width:${118*k2}px"></span></div></div>
 <div class="row2">${p.need?`<span class="cb" style="border-color:var(--ap)"></span>Reply to Anna<span class="x" style="color:var(--ap)">review</span>`:`<span class="cb"></span>Send Anna the photos`}</div><div class="row2"><span class="cb"></span>Order clay<span class="x">+2 more</span></div><div class="row2" style="opacity:${e3}"><span class="cb"></span>Price cards</div></div></div>`},
learn:(p={})=>{const f=p.f==null?.6:p.f;const C=2*Math.PI*30;return `<div class="w2 ${p.lt?'lt':''}" style="${p.pos||''}"><div class="hd2">${I('study')}Learn ${ar}</div>
 <div style="display:flex;align-items:center;gap:8px;margin-top:10px"><div style="position:relative;width:54px;height:54px;flex:none"><svg viewBox="0 0 72 72" style="width:54px;height:54px"><circle cx="36" cy="36" r="30" stroke="rgba(250,250,244,.1)" stroke-width="7" fill="none"/><circle cx="36" cy="36" r="30" stroke="#0CF2BA" stroke-width="7" fill="none" stroke-linecap="round" stroke-dasharray="${C*f*.875} ${C}" transform="rotate(-45 36 36)"/></svg>
 <div style="position:absolute;inset:0;display:grid;place-items:center;font:600 13px Sora">${Math.round(f*5)}/5</div>${p.bloom?`<div style="position:absolute;inset:${-20*p.bloom}px;border-radius:50%;background:rgba(12,242,186,${.4*(1-p.bloom)})"></div>`:''}</div>
 <div style="min-width:0"><div style="font:600 14px Sora">Fractions</div><div class="ws">${f>=.99?'Lesson done':'6 cards due'}</div></div></div></div>`},
weather:(p={})=>{const k=p.ph||0;return `<div class="w2 ${p.lt?'lt':''}" style="${p.pos||''}"><div class="hd2">${I('cloud')}Riga ${ar}</div><div style="position:relative;height:80px;margin-top:8px">
 <div style="position:absolute;inset:0;opacity:${1-k}"><div class="bigt" style="font-size:42px">3°</div><div class="ws">cloudy · dry until 15:00</div></div>
 <div style="position:absolute;inset:0;opacity:${k}"><div class="bigt" style="font-size:42px">1°</div><div class="ws" style="color:var(--mint)">updated · frost tonight</div></div></div></div>`},
brief:(p={})=>`<div class="w2 ${p.lt?'lt':''}" style="${p.pos||''}"><div class="hd2">${I('read_mode')}Morning brief ${ar}</div><div style="font-size:14.5px;line-height:1.42;margin-top:8px">Dry until 15:00. Your 14:00 moved to Brīvības 40. Two orders shipped.</div></div>`};
/* home screen: kind = wallpaper, layout fixed */
function home(kind,o={}){const lt=WPS[kind]&&WPS[kind].light;const p=Object.assign({lt},o);const t=o.t||0;
 const L=o.layout||'std';let wg='';
 if(L=='std'){wg=WD.day(Object.assign({},p,{pos:'left:22px;right:22px;top:56px;height:168px',ph:o.dayPh}))+WD.ask(Object.assign({},p,{pos:'left:22px;width:165px;top:238px;height:170px'}))+WD.clock(Object.assign({},p,{pos:'right:22px;width:165px;top:238px;height:170px',sec:o.sec}))+WD.tasks(Object.assign({},p,{pos:'left:22px;right:22px;top:422px;height:168px',ph:o.taskPh}))}
 if(L=='learn'){wg=WD.weather(Object.assign({},p,{pos:'left:22px;width:165px;top:56px;height:150px',ph:o.wPh}))+WD.learn(Object.assign({},p,{pos:'right:22px;width:165px;top:56px;height:150px',f:o.lf,bloom:o.bloom}))+WD.day(Object.assign({},p,{pos:'left:22px;right:22px;top:220px;height:168px',ph:o.dayPh}))+WD.ask(Object.assign({},p,{pos:'left:22px;right:22px;top:402px;height:188px'}))}
 if(L=='min'){wg=WD.clock(Object.assign({},p,{pos:'left:50%;margin-left:-90px;width:180px;top:110px;height:180px',sec:o.sec}))}
 const tag=o.tag?`<div class="wtag ${o.tagc||''}"><i></i>${o.tag}</div>`:'';
 return `<div class="phone ${lt?'lt':''}" style="background:#060330">${cvs(kind,{state:o.state,tod:o.tod,acc:o.acc,t:o.t0||0,amt:o.amt,motion:o.motion,dim:o.dim})}<div class="hs2">${lt?SBL:SB}${tag}${wg}
 ${L=='min'?'':`<div class="srch2">${I('search')}Search or ask halo</div>`}<div class="apps">${['halo','call','send','camera'].map(a=>`<div class="app ${a=='halo'?'halo':''}">${a=='halo'?hring(32):I(a=='call'?'voice':a)}</div>`).join('')}</div></div>${o.over||''}<div class="gb"></div></div>`}
/* mini previews for cards */
function minihome(kind,o={}){const lt=WPS[kind]&&WPS[kind].light;return `${cvs(kind,o)}<div class="mph ${lt?'lt':''}"><div class="mw" style="top:12%;height:16%"></div><div class="mw" style="top:31%;height:14%;right:52%"></div><div class="mw" style="top:31%;height:14%;left:52%"></div><div class="mw" style="top:48%;height:16%"></div></div>`}
/* ---------------- in-app screens ---------------- */
const SA={};
SA.hub=`<div class="mi-t">Make it yours</div><div class="mi-s">Change anything. You can always put it back.</div>
 <div class="withme"><div class="gi">${I('guide_me')}</div><div><b>Do it with me</b><span>3 questions with pictures · about a minute</span></div></div>
 <div class="tiles6">${[['palette','Look','Night Mint','drift'],['wallpaper','Wallpaper','Aurora · live','aurora'],['widget','Widgets','4 on home screen','constellation'],['dock','Dock & buttons','Large · labels on','tide'],['text_size','Text & size','Bigger','paper'],['motion','Motion & feel','Gentle · haptics','dayarc']].map(([i,t,s,k],n)=>`<div class="t6">${n<2?cvs(k,{tod:.3,t:n*3}):''}<div class="ic">${I(i)}</div><b>${t}</b><span>${s}</span></div>`).join('')}</div>
 <div class="putback">${I('undo')}Put back how it was · 7 days of changes kept</div>`;
SA.looks=`<div class="crumb">${I('palette')}Make it yours › Look</div><div class="mi-t" style="font-size:23px">Pick a look</div><div class="mi-s">Colours + a matching live wallpaper. Tap to try it on the whole phone.</div>
 <div class="looks">${[['Night Mint','drift','#0CF2BA',1,.5],['Paper Sage','tide','#8C9A7B',0,.27],['Slate Blue','constellation','#B6DFE2',0,.5],['Cool Lilac','aurora','#C9B8FF',0,.8],['Coral Day','dayarc','#FF9F6B',0,.3],['Warm Rose','paper','#F3C0D9',0,.5]].map(([n,k,c,on,tod])=>`<div class="lk ${on?'on':''} ${WPS[k].light?'lt':''}">${cvs(k,{tod,acc:k=='paper'?'#0CF2BA':c})}<span class="nm">${n}</span><span class="dot" style="background:${c}"></span></div>`).join('')}</div>`;
SA.dock=`<div class="crumb">${I('dock')}Make it yours › Dock & buttons</div><div class="mi-t" style="font-size:23px">Your dock</div><div class="mi-s">Swap one place in. Home, the ring and Places stay.</div>
 <div class="dprev"><div class="dt"><div class="bx">${I('home')}</div>Home</div><div class="dt sw"><div class="bx">${I('study')}</div>Learn</div><div class="or">${hring(52)}</div><div class="dt"><div class="bx">${I('notifications')}</div>Activity</div><div class="dt"><div class="bx">${I('grid_view')}</div>Places</div></div>
 ${[['notebook','Workspace','swap'],['study','Learn','in dock'],['archive','Library','swap'],['notifications','Activity','in dock']].map(([i,t,s])=>`<div class="dl"><div class="ti">${I(i)}</div>${t}<span class="fx" style="${s=='in dock'?'color:var(--mint)':''}">${s}</span><span class="hdl" style="margin-left:10px">⋮⋮</span></div>`).join('')}
 <div class="lbl3">Button size</div><div class="seg3"><span>Regular</span><span class="on">Large</span><span>Extra large</span></div>`;
SA.withme=(sel)=>`<div class="qn">Do it with me · 2 of 3</div><div class="qq">Which feels more like you?</div>
 <div class="ab"><div class="abc ${sel==0?'on':''}"><div class="mp">${minihome('aurora',{tod:.8})}</div><b>Calm night</b><span>dark, slow colour</span><div class="spk">${I('speaker')}hear it</div></div>
 <div class="abc ${sel==1?'on':''}"><div class="mp">${minihome('paper',{})}</div><b>Bright paper</b><span>light, easy to read</span><div class="spk">${I('speaker')}hear it</div></div></div>
 <div class="dotsq"><i></i><i class="on"></i><i></i></div>`;
SA.picker=(k='aurora',tod=.8)=>`<div class="crumb">${I('wallpaper')}Make it yours › Wallpaper</div>
 <div class="wpbig">${minihome(k,{tod})}</div>
 <div class="thumbs">${Object.keys(WPS).map(x=>`<div class="wth ${x==k?'on':''}">${cvs(x,{tod:.3})}</div>`).join('')}</div>
 <div class="tod">${[['sun','Morning',.27],['sun','Day',.5],['moon','Evening',.74],['moon','Night',.95]].map(([i,l,v])=>`<span class="${Math.abs(v-tod)<.08?'on':''}">${I(i)}${l}</span>`).join('')}</div>
 <div class="tl">${I('intelligence')}<div>Reacts to halo<small>glows when listening or when something needs you</small></div><span class="tg2"></span></div>
 <div class="tl">${I('moon')}<div>Calmer at night<small>dims and slows after 22:00</small></div><span class="tg2"></span></div>`;
SA.showme=`${S.welcome}`;
SA.levels=`<div class="mi-t" style="margin-top:22px">How much help would you like?</div><div class="mi-s">Pick what feels right. Change it any time in Make it yours.</div>
 ${[['Show me everything','Every button has a name. halo shows each new thing once, step by step.',1,'lots'],['A little help','Tips the first time you meet something new.',0,'some'],['I know my way','No tips. Help is still one tap away.',0,'none']].map(([t,s,on,k])=>`<div class="lvl ${on?'on':''}"><div class="lex">${k=='lots'?'<div class="leb" style="top:10px;height:18px"></div><div class="leb" style="top:34px;height:18px"></div><div class="ec" style="left:6px;top:58px">1 Talk</div><div class="ec" style="left:30px;top:76px">2 Home</div>':k=='some'?'<div class="leb" style="top:10px;height:18px"></div><div class="leb" style="top:34px;height:18px"></div><div class="ec" style="left:14px;top:66px">Tip</div>':'<div class="leb" style="top:10px;height:18px"></div><div class="leb" style="top:34px;height:18px"></div><div class="leb" style="top:58px;height:18px"></div>'}</div><div><b>${t}</b><span>${s}</span></div></div>`).join('')}
 <div class="lockline">${I('family')}Setting up for someone else? <b style="color:var(--lil);margin-left:4px">Set up for them</b></div>`;
SA.easy=`<div class="easyh">Good morning, Rita</div><div class="mi-s" style="font-size:17px">Saturday · 3° · dry until 3 pm</div>
 <div class="easyg">${[['voice','Talk to halo',1],['calendar','My day'],['send','Messages'],['camera','What’s this?']].map(([i,t,p])=>`<div class="eb2 ${p?'p':''}">${I(i)}${t}</div>`).join('')}</div>
 <div class="lockline">${I('lock')}Layout kept by Mark · unlock with phone lock</div>`;
SA.stuck=`<div class="crumb">${I('settings')}Settings › Voice & reading</div><div class="mi-t" style="font-size:23px">Voice & reading</div>
 ${[['Reading voice','Calm · on this phone'],['Speed','Normal'],['Read answers aloud','Off']].map(([a,b])=>`<div class="tl" style="height:52px">${a}<span style="margin-left:auto;color:var(--t2);font-size:14px">${b}</span></div>`).join('')}
 <div class="stuck"><b>Looking for something?</b><p>You’ve been back and forth a few times. Tell me what you want to change and I’ll take you there.</p><div class="cbtns"><span class="p">${I('voice')}Tell halo</span><span>${I('guide_me')}Show me around</span><span>${I('close')}</span></div></div>`;
