/* halo 8.0 · final upgrade screens */
const SQ={};
/* 59 · Easy & Kids versions of the new tools */
SQ.scanEasy=`<div class="easyh" style="font-size:28px;margin-top:8px">Scan</div><div class="mi-s" style="font-size:17px">What do you want to do?</div>
 <div class="qeasy"><div class="p">${I('speaker')}Read it to me</div><div>${I('language')}Translate</div><div>${I('ocr')}Keep a paper</div><div>${I('explain')}What is this?</div></div>
 <div class="yrule" style="font-size:15px">${I('guide_me')}<span>Just point the phone. halo tells you when to hold still.</span></div>`;
SQ.planEasy=`<div class="easyh" style="font-size:28px;margin-top:8px">Today</div><div class="mi-s" style="font-size:17px">Thursday · 3 things</div>
 <div class="yb" style="padding:0 16px">${[['9:00','Tablet with breakfast',1],['14:00','Pick up the frame',0],['19:00','Bins out',0]].map(([t,a,on])=>`<div class="qrow"><span class="t">${t}</span>${a}<span class="done ${on?'on':''}"></span></div>`).join('')}</div>
 <div class="qeasy"><div class="pl">${I('mic')}Add by talking</div><div>${I('speaker')}Read my day</div></div>`;
SQ.kidsScan=`<div class="qk"><div class="kq" style="margin-top:12px;font-size:24px">What is this?</div>
 <div class="qkcam"><div class="leaf"></div></div>
 <div class="qkans"><b>🍃 A leaf!</b><span>It’s from a maple tree. Leaves drink sunlight to make food for the tree.</span></div></div>`;
SQ.kidsDay=`<div class="qk"><div class="kq" style="margin-top:12px;font-size:24px">My day</div>
 <div class="qkday"><div><span class="pic" style="background:#FFE7D6">🥣</span>Breakfast<span class="tk on"></span></div><div class="now"><span class="pic" style="background:#E0F7EF">🎒</span>School<span class="tk"></span></div><div><span class="pic" style="background:#EDE7FF">🎨</span>Drawing club<span class="tk"></span></div><div><span class="pic" style="background:#FFF4CC">🛁</span>Bath & story<span class="tk"></span></div></div></div>`;
/* 60 · HaloAlbum & HaloVault */
SQ.album=`<div class="ttl" style="margin-top:4px">Album</div><div class="srch">${I('search')}“Gran’s birthday last summer”</div>
 <div class="zgrp">Found 24 · 18 July</div><div class="qalb">${[1,2,3,4,5,6,3,1,5].map(n=>`<div class="qph${n}"></div>`).join('')}</div>
 <div class="ylive" style="margin-top:12px">${I('guide_me')}People, places and words in photos are found on this phone.</div>`;
SQ.story=`<div class="crumb">${I('image')}Album › Make a story</div><div class="qstory"><div class="bars"><i class="on"></i><i class="on"></i><i></i><i></i><i></i></div><div class="tt">Gran is 80</div><div class="ts">18 July · Jūrmala · 24 photos</div></div>
 <div class="zsend"><span>${I('speaker')}Add music</span><span>${I('annotate')}Change words</span><span>${I('send')}Send to family</span></div>`;
SQ.vault=`<div class="ttl" style="margin-top:4px">Vault</div><div class="sub">Important papers, kept safe and findable.</div>
 <div class="yb" style="padding:2px 14px">
 <div class="qdoc"><span class="ic">${I('file_doc')}</span><div>Fridge warranty<small>Bought 3 Mar 2024</small></div><span class="exp ap">ends in 3 weeks</span></div>
 <div class="qdoc"><span class="ic">${I('card')}</span><div>Car insurance<small>Policy · renews yearly</small></div><span class="exp">Aug 2027</span></div>
 <div class="qdoc"><span class="ic">${I('ocr')}</span><div>Jar supplier receipt<small>From HaloScan · €46</small></div><span class="exp">kept</span></div>
 <div class="qdoc"><span class="ic">${I('privacy')}</span><div>Passport photo page<small>Locked · fingerprint</small></div><span class="exp">2031</span></div></div>
 <div class="qlock">${I('privacy')}Locked with your fingerprint. Never used to answer questions unless you open it.</div>`;
SQ.vaultAdd=`<div class="crumb">${I('privacy')}Vault › New paper</div><div class="mi-t" style="font-size:23px">I found a warranty</div>
 <div class="yb"><div class="zparse"><div>What</div><div>Fridge warranty · Bosch</div><div>Bought</div><div>3 March 2024</div><div>Ends</div><div>3 March 2027</div><div>Remind</div><div>1 month before</div></div></div>
 <div class="yrule">${I('explain')}<span>Read from your photo of the receipt. Tap any line to change it.</span></div>`;
/* 62 · wallpapers extension */
Object.assign(WP2,{rooftops:{name:'Riga Rooftops'},deepsea:{name:'Deep Sea'},papercut:{name:'Paper Cut'},photo:{name:'Your Photo'},meadow:{name:'Meadow Wind'},inkwash:{name:'Ink Wash'}});
const _paintZ=paintZ;
paintZ=function(cv,kind,o={}){if(!['rooftops','deepsea','papercut','photo','meadow','inkwash'].includes(kind))return _paintZ(cv,kind,o);
 const W=cv.width,H=cv.height,c=cv.getContext('2d');const sc=W/390;c.setTransform(sc,0,0,sc,0,0);const w=390,h=H/sc;const st=o.state||'idle';
 const ring=(x,y,r,col,lw)=>{c.save();c.lineCap='round';c.lineWidth=lw;c.strokeStyle=col;c.beginPath();c.arc(x,y,r,-Math.PI/8,Math.PI*1.625);c.stroke();c.restore()};
 const glow=(x,y,r,col)=>{const g=c.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,col);g.addColorStop(1,'rgba(0,0,0,0)');c.fillStyle=g;c.fillRect(x-r,y-r,2*r,2*r)};
 const tod=o.tod==null?.8:o.tod;
 if(kind=='rooftops'){const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,tod>.6?'#0b0a2e':'#7fb0d8');g.addColorStop(1,tod>.6?'#3a2a5a':'#f3d9b8');c.fillStyle=g;c.fillRect(0,0,w,h);
  glow(290,h*.2,140,st=='need'?'rgba(255,159,107,.3)':'rgba(12,242,186,.18)');ring(290,h*.2,36,st=='need'?'#FF9F6B':'rgba(220,255,240,.9)',8);
  c.fillStyle='#07061c';c.beginPath();c.moveTo(0,h);const sky=[[0,.68],[30,.66],[30,.6],[60,.6],[62,.48],[66,.4],[70,.48],[72,.6],[110,.6],[110,.64],[150,.64],[152,.5],[158,.32],[164,.5],[166,.58],[200,.58],[200,.62],[240,.62],[242,.55],[262,.55],[264,.45],[268,.38],[272,.45],[274,.56],[310,.56],[310,.63],[350,.63],[352,.59],[390,.59]];sky.forEach(([x,y])=>c.lineTo(x,h*y));c.lineTo(w,h);c.fill();
  for(let i=0;i<70;i++){const x=rr(i)*w,y=h*(.62+rr(i+1)*.3);c.fillStyle=rr(i+2)>.3?'rgba(255,214,140,.85)':'rgba(12,242,186,.7)';c.fillRect(x,y,4,6)}}
 if(kind=='deepsea'){const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#0e5a78');g.addColorStop(.5,'#06304a');g.addColorStop(1,'#020c1c');c.fillStyle=g;c.fillRect(0,0,w,h);
  c.globalCompositeOperation='lighter';for(let i=0;i<6;i++){c.fillStyle='rgba(180,240,255,.05)';c.beginPath();const x=60+i*60;c.moveTo(x,0);c.lineTo(x+40,0);c.lineTo(x+110,h*.7);c.lineTo(x+30,h*.7);c.fill()}
  for(let i=0;i<80;i++){c.fillStyle='rgba(200,255,240,'+(.15+rr(i)*.4)+')';c.beginPath();c.arc(rr(i+3)*w,rr(i+4)*h,.8+rr(i+5)*1.8,0,7);c.fill()}
  c.globalCompositeOperation='source-over';glow(195,h*.45,120,st=='need'?'rgba(255,159,107,.35)':'rgba(12,242,186,.25)');ring(195,h*.42,56,st=='need'?'rgba(255,159,107,.9)':'rgba(12,242,186,.85)',10);
  c.strokeStyle='rgba(12,242,186,.35)';c.lineWidth=2;for(let k=0;k<6;k++){c.beginPath();const x=165+k*12;c.moveTo(x,h*.42+50);for(let y=0;y<120;y+=10)c.lineTo(x+Math.sin(y*.06+k)*8,h*.42+50+y);c.stroke()}}
 if(kind=='papercut'){const cols=['#f7efe2','#efd9c3','#e6c3a8','#cfa58a','#a97a66','#6b4a4a'];c.fillStyle=cols[0];c.fillRect(0,0,w,h);ring(110,h*.2,38,st=='need'?'#E07A3F':'#24412F',9);
  for(let L=1;L<6;L++){const base=h*(.35+L*.11);c.save();c.shadowColor='rgba(60,30,20,.25)';c.shadowBlur=18;c.shadowOffsetY=-4;c.fillStyle=cols[L];c.beginPath();c.moveTo(0,h);c.lineTo(0,base);for(let x=0;x<=w;x+=10)c.lineTo(x,base+Math.sin(x*.012+L*1.3)*30+Math.sin(x*.035+L)*8);c.lineTo(w,h);c.fill();c.restore()}}
 if(kind=='photo'){const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#f6c58f');g.addColorStop(.55,'#c96b4a');g.addColorStop(1,'#3a1f2a');c.fillStyle=g;c.fillRect(0,0,w,h);
  c.filter=`blur(${18*sc}px)`;[['#ffe8c2',120,200,90],['#8a3a3a',280,420,120],['#3d6f8a',90,560,100],['#f3c0d9',300,180,70]].forEach(([col,x,y,r])=>{c.fillStyle=col;c.beginPath();c.arc(x,y,r,0,7);c.fill()});c.filter='none';
  c.fillStyle='rgba(6,3,48,.18)';c.fillRect(0,0,w,h);ring(195,h*.36,96,st=='need'?'rgba(255,159,107,.95)':'rgba(255,255,255,.85)',14)}
 if(kind=='meadow'){const g=c.createLinearGradient(0,0,0,h);g.addColorStop(0,'#fde2c4');g.addColorStop(.5,'#f8cfa8');g.addColorStop(1,'#7a9a5a');c.fillStyle=g;c.fillRect(0,0,w,h);glow(195,h*.45,200,'rgba(255,240,210,.9)');ring(195,h*.45,44,st=='need'?'#E07A3F':'rgba(36,65,47,.75)',9);
  for(let i=0;i<260;i++){const x=rr(i)*w,b=h*(.62+rr(i+1)*.4),len=40+rr(i+2)*90,bend=12+rr(i+3)*16;c.strokeStyle=`rgba(${50+rr(i)*40|0},${90+rr(i+1)*50|0},${40},.8)`;c.lineWidth=1.5+rr(i+4)*1.5;c.beginPath();c.moveTo(x,b);c.quadraticCurveTo(x+bend*.4,b-len*.6,x+bend,b-len);c.stroke()}}
 if(kind=='inkwash'){c.fillStyle='#f4f1ea';c.fillRect(0,0,w,h);ring(270,h*.18,30,st=='need'?'#E07A3F':'#B89150',7);
  [[.42,'rgba(40,50,60,.12)'],[.5,'rgba(40,50,60,.2)'],[.6,'rgba(30,40,50,.32)'],[.72,'rgba(20,30,40,.5)']].forEach(([y0,col],L)=>{c.filter=`blur(${(4-L)*sc}px)`;c.fillStyle=col;c.beginPath();c.moveTo(0,h);for(let x=0;x<=w;x+=8)c.lineTo(x,h*y0-Math.abs(Math.sin(x*.01+L*2))*70-Math.sin(x*.05+L)*10);c.lineTo(w,h);c.fill()});c.filter='none';
  c.fillStyle='rgba(30,40,50,.6)';c.beginPath();c.moveTo(140,h*.3);c.lineTo(150,h*.305);c.lineTo(160,h*.3);c.lineTo(150,h*.302);c.fill()}
};
/* 63 · functions */
SQ.check=`<div class="mi-t" style="margin-top:16px">Morning check-in</div><div class="mi-s">Set up together with Gran. Only her family sees it.</div>
 <div class="qcheck"><b>Good morning, Gran.<br>How are you today?</b><span class="ys">One tap tells Anna and Māris you’re fine.</span><div class="bb"><span class="p">I’m fine</span><span>Call me, please</span></div></div>
 <div class="yrule">${I('privacy')}<span>If no answer by 11:00, halo asks again once — then lets Anna know. Gran can turn this off any time.</span></div>`;
SQ.vaultIsl={cls:'need',r1:`<div class="yir"><span class="apd">${I('reminder')}</span><div><div class="tt">Fridge warranty ends in 3 weeks</div><div class="ts">Any problems worth reporting now?</div></div></div>`,r2:`<div class="btn" style="flex:1">Later</div><div class="btn apb" style="flex:1.3">${I('file_doc')}Open it</div>`};
SQ.wind=`<div class="qwind"><div class="moon"></div><b>Winding down</b><div class="ln">Screen warms and dims. Notifications wait until 7:00.<br>Alarm 7:00 · it’s raining tomorrow, leave 5 min earlier.</div></div>
 <div class="zsend" style="justify-content:center;margin-top:18px"><span>${I('speaker')}Read me a story</span><span>${I('timer')}Sleep sounds 30 min</span></div>`;
SQ.medical=`<div class="crumb">${I('privacy')}Emergency info</div><div class="mi-s" style="margin-top:6px">Shown on the lock screen to anyone who helps you. You choose each line.</div>
 <div class="qmed"><div class="hd">${I('notifications')}Emergency info · Gran</div>
 <div class="r"><span>Allergies</span><span>Penicillin</span></div><div class="r"><span>Medicine</span><span>Blood pressure tablet, 2× a day</span></div><div class="r"><span>Call first</span><span>Anna (daughter)</span></div><div class="r"><span>Language</span><span>Latvian, some English</span></div></div>`;
