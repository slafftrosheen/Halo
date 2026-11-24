/* halo 13.0 clickable prototype · batches 1–5 · ?flow=1..7 &lang=ru &auto=1 (one flow) | auto=all &still=1 */
(()=>{
const Q=new URLSearchParams(location.search);
const st={flow:+(Q.get('flow')||1),lang:Q.get('lang')=='ru'?'ru':'en',still:Q.has('still'),auto:Q.get('auto')};
const T=k=>(S[st.lang][k]!==undefined?S[st.lang][k]:S.en[k]);
const I=n=>`<svg class="i" viewBox="0 0 24 24" fill="none">${(window.IC||{})[n]||''}</svg>`;
const RING=(c='#0CF2BA',s=40,w=30)=>`<svg class="p-ring" viewBox="0 0 240 240" style="width:${s}px;height:${s}px"><path d="M197.32 87.18A84 84 0 1 1 152.82 42.68" stroke="${c}" stroke-width="${w}" stroke-linecap="round" fill="none"/></svg>`;
const $=s=>document.querySelector(s);
const N=7;
let F={};
const reset=()=>{F={s:1,big:false,read:false,who:{wMe:1},much:'mStd',where:'pPhone',home:false,
 depth:'mSimple',offer:false,why:false,
 src:'list',typed:'',pin:false,
 pl:'today',q:'',g:'17',ft:false,
 cv:'board',tidy:0,
 sv:'start',help:false,hint:false,res:false,rate:null,
 k:'home',ka:null,hw:false,gu:false}};
reset();
const Dr={strokes:[],cur:null,per:6};
function side(){
 $('#side').innerHTML=`<div class="p-wm">hal${RING('#0CF2BA',30)}<small>13.0</small></div>
 <div style="font:700 11px Atk;letter-spacing:.12em;text-transform:uppercase;color:var(--t3)">${T('k')}</div>
 <div class="p-flows">${Array.from({length:N},(_,j)=>j+1).map(i=>`<button class="p-flow ${st.flow==i?'on':''}" data-flow="${i}"><span class="n">${i}</span><span><b>${T('f'+i)}</b><span>${T('f'+i+'s')}</span></span></button>`).join('')}</div>
 <div class="p-cap" id="cap"></div>
 <div class="p-tools"><button class="p-pill" id="bAuto">▶ ${T('auto')}</button><button class="p-pill" id="bLang">${T('lang')}</button><button class="p-pill ${st.still?'on':''}" id="bStill">${T('still')}</button></div>
 <div class="p-foot">${T('foot')}</div>`;
 document.querySelectorAll('[data-flow]').forEach(b=>b.onclick=()=>{st.flow=+b.dataset.flow;reset();Dr.strokes=[];draw()});
 $('#bLang').onclick=()=>{st.lang=st.lang=='en'?'ru':'en';draw()};
 $('#bStill').onclick=()=>{st.still=!st.still;document.body.classList.toggle('still',st.still);side()};
 $('#bAuto').onclick=()=>runAuto(st.flow);
}
function cap(k){const c=T(k);$('#cap').innerHTML=`<b>${c[0]}</b>${c[1]}`}
const SB=`<div class="p-sb"><span>9:41</span><span class="r">${I('signal')}${I('battery')}</span></div>`;
const DOCK=()=>`<div class="p-dock">${I('home')}${I('search')}${RING('#0CF2BA',40)}${I('devices')}${I('settings')}</div>`;
function toast(t){const e=$('#toast');if(!e)return;e.textContent=t;e.classList.add('on');clearTimeout(toast.t);toast.t=setTimeout(()=>e.classList.remove('on'),2400)}
const dots=n=>`<div class="q-dots">${[1,2,3,4].map(i=>`<i class="${i==n?'on':i<n?'d':''}"></i>`).join('')}</div>`;
const opt=(id,t,s,on,extra='')=>`<button class="q-opt ${on?'on':''}" id="${id}" data-act="${id}">${extra}<span><b>${t}</b>${s?`<small>${s}</small>`:''}</span><i class="q-tick">${on?'✓':''}</i></button>`;
const V={};
/* 1 first open */
V[1]=()=>{
 const top=`<div class="q-top"><span></span><button class="q-just" id="bJust" data-act="just">${T('just')}</button></div>`;
 if(F.home){cap('c1e');const sug=[F.who.wStudy?'sHw':null,'sPlan',F.who.wCreate?'sDraw':null,'sList','sWeather'].filter(Boolean).slice(0,4);
  return{html:`<div class="p-h">${T('gm')}</div><div class="p-sub">${T('mStd')} · ${T(F.where)}</div>
  ${sug.map((k,i)=>`<div class="p-card p-fade" style="animation-delay:${i*.12}s"><h4>${I(['think_with_halo','routines','draw','task','cloud'][i])}${T(k)}</h4></div>`).join('')}
  <div class="p-card q-lil p-fade" style="animation-delay:.6s"><h4>${I('guide_me')}${T('tomorrow')}</h4><p>${T('tomorrowS')}</p></div>`,
  extra:`<div class="p-in">${I('think_with_halo')}${T('gmS')}<span class="mic">${I('mic')}</span></div>`}}
 if(F.s==1){cap('c1a');return{cls:F.big?'q-big':'',html:top+dots(1)+`<div class="q-hello">${RING('#0CF2BA',76,26)}</div><div class="q-q">${T('hello')}</div><div class="p-sub">${T('helloS')}</div>
  <div class="q-tg">${[['big','big'],['read','read']].map(([k,l])=>`<button class="q-sw ${F[k]?'on':''}" id="t_${k}" data-act="t_${k}"><span>${T(l)}</span><i></i></button>`).join('')}
  <button class="q-sw"><span>${T('contrast')}</span><i></i></button><button class="q-sw"><span>${T('calm')}</span><i></i></button></div>
  <button class="q-main" id="bNext" data-act="next">${T('start')}</button><div class="q-link">${T('before')}</div>`}}
 if(F.s==2){cap('c1b');return{cls:F.big?'q-big':'',html:top+dots(2)+`<div class="q-q">${T('who')}</div><div class="p-sub">${T('whoS')}</div>
  ${['wMe','wChild','wFam','wStudy','wCreate'].map(k=>opt('w_'+k,T(k),'',F.who[k])).join('')}<button class="q-main" id="bNext" data-act="next">${T('next')}</button>`}}
 if(F.s==3){cap('c1c');const pv={mSimple:`<div class="q-pv s"><i></i><i></i><i></i><i></i></div>`,mStd:`<div class="q-pv m"><i></i><i></i><i></i><i></i><i></i><i></i></div>`,mPro:`<div class="q-pv p">${'<i></i>'.repeat(12)}</div>`};
  return{cls:F.big?'q-big':'',html:top+dots(3)+`<div class="q-q">${T('much')}</div><div style="height:8px"></div>
  ${['mSimple','mStd','mPro'].map(k=>opt('m_'+k,T(k),T(k+'S'),F.much==k,pv[k])).join('')}<button class="q-main" id="bNext" data-act="next">${T('next')}</button>`}}
 cap('c1d');return{cls:F.big?'q-big':'',html:top+dots(4)+`<div class="q-q">${T('where')}</div><div style="height:8px"></div>
  ${['pPhone','pOnline','pLater'].map(k=>opt('p_'+k,T(k),T(k+'S'),F.where==k)).join('')}
  <div class="q-priv">${I('privacy')}${T('priv')}</div><button class="q-main" id="bNext" data-act="next">${T('next')}</button>`}};
/* 2 depth */
V[2]=()=>{
 cap(F.why?'c2c':F.offer?'c2b':'c2a');
 const seg=`<div class="q-seg">${['mSimple','mStd','mPro'].map(k=>`<button class="${F.depth==k?'on':''}" id="d_${k}" data-act="d_${k}">${T(k)}</button>`).join('')}</div>`;
 const canvas=h=>`<div class="q-paper" style="height:${h}px"><svg viewBox="0 0 300 200" preserveAspectRatio="none"><path d="M30 150 C80 40 140 40 170 110 S250 170 275 60" stroke="#24412F" stroke-width="5" fill="none" stroke-linecap="round"/><circle cx="210" cy="70" r="26" fill="#C9B8FF" opacity=".7"/></svg></div>`;
 let body='';
 if(F.depth=='mSimple')body=canvas(390)+`<div class="q-four">${[['draw','dBrush'],['accent','dColour'],['eraser','dEraser'],['more','dMore']].map(([i,k])=>`<button id="s_${k}" data-act="more">${I(i)}<span>${T(k)}</span></button>`).join('')}</div>`;
 if(F.depth=='mStd')body=canvas(330)+`<div class="q-row">${['#24412F','#C9B8FF','#FF9F6B','#3a6ea5','#14102a'].map((c,i)=>`<span class="p-sw ${i==0?'on':''}" style="background:${c}"></span>`).join('')}<span class="q-val">${T('dSize')}</span></div>
  <div class="q-four">${[['draw','dBrush'],['collections','dLayers'],['select','dSelect'],['eraser','dEraser'],['more','dMore']].map(([i,k])=>`<button data-act="more">${I(i)}<span>${T(k)}</span></button>`).join('')}</div>
  <button class="q-whyb" id="bWhy" data-act="why">${I('explain')} ${T('why')}</button>`;
 if(F.depth=='mPro')body=`<div class="q-menu">${(st.lang=='ru'?['Файл','Правка','Вставка','Формат','Вид','Инструменты']:['File','Edit','Insert','Format','View','Tools']).map(m=>`<span>${m}</span>`).join('')}</div>
  <div class="q-pro"><div class="q-rail">${['draw','eraser','select','shapes','sketch','accent','zoom_in'].map(i=>I(i)).join('')}</div>${canvas(300)}</div>
  <div class="q-insp"><span>${T('dLayers')} 3</span><span>6.0 px</span><span>${T('dTilt')} 40%</span></div><div class="q-kbd">${T('dShort')}</div>`;
 return{cls:'',html:`<div class="q-tt">${I('back')}<b>${T('dTitle')}</b>${RING('#0CF2BA',22)}</div>${seg}${body}`,
 extra:`<div class="p-sheet ${F.offer?'open':''}"><div class="grab"></div><div class="q-q" style="font-size:17px">${RING('#C9B8FF',26)}</div><p class="p-why" style="margin:6px 0 12px">${T('offer')}</p>
  <button class="q-main" id="bOfferY" data-act="offerY">${T('offerY')}</button><button class="q-sec" data-act="offerN">${T('offerN')}</button><p class="p-sub" style="margin-top:10px">${T('offerS')}</p></div>
 <div class="p-sheet ${F.why?'open':''}"><div class="grab"></div><b style="font:600 17px Sora">${T('why')}</b><p class="p-why" style="margin:8px 0 14px">${T('whyT')}</p>
  <div class="p-card act" style="display:flex;gap:6px;background:none;border:0;padding:0"><button class="p-chip mint" id="bWhyK" data-act="whyK">${T('whyK')}</button><button class="p-chip" data-act="whyK">${T('whyR')}</button><button class="p-chip" data-act="whyK">${T('whyS')}</button></div></div>`}};
/* 3 sources */
const row=(id,ic,t,s,state)=>`<button class="q-src" id="${id}" data-act="${id}"><span class="q-si">${I(ic)}</span><span class="q-st"><b>${t}</b><small>${s}</small></span><i class="q-dot ${state}"></i></button>`;
V[3]=()=>{
 if(F.src=='hn'){cap('c3b');return{html:`<div class="q-tt">${I('back')}<b>${T('hn')}</b><span></span></div>
  <div class="q-state fail">${T('hnS').split('·').pop().trim()}</div><p class="p-why" style="margin:10px 0">${T('hnWhat')}</p>
  <div class="p-card"><h4>${I('history')}${T('hnDid')}</h4><p>${T('hnDidS')}</p></div>
  <div class="p-card"><h4>${I('guide_me')}${T('hnCan')}</h4><div class="act"><button class="p-chip mint" id="bTry" data-act="tryNow">${T('tryNow')}</button><button class="p-chip">${T('pause')}</button></div></div>
  <div class="p-sub" style="margin:14px 0 6px">${T('uses').toUpperCase()}</div>${['uBrief','uAns'].map((k,i)=>`<div class="q-sw on"><span>${T(k)}</span><i></i></div>`).join('')}<div class="q-sw"><span>${T('uAlert')}</span><i></i></div>`}}
 if(F.src=='add'){cap('c3c');const ok=F.typed.length>=F.addLen;return{html:`<div class="q-tt">${I('back')}<b>${T('addT')}</b><span></span></div><p class="p-sub">${T('addS')}</p>
  <div class="q-field">${I('link')}<span>${F.typed||'<i style="opacity:.5">https://…</i>'}</span></div>
  ${ok?`<div class="p-card p-fade"><h4>${I('source')}Rīgas satiksme</h4><p>${T('found')}</p><div class="q-smp">· ${T('smp1')}<br>· ${T('smp2')}</div></div>
  <div class="p-sub" style="margin:4px 0 6px">${T('uses').toUpperCase()}</div><div class="q-sw on"><span>${T('uBrief')}</span><i></i></div><div class="q-sw on"><span>${T('uAns')}</span><i></i></div>
  <button class="q-main" id="bAdd" data-act="addDone">${T('addBtn')}</button>`:''}`}}
 cap(F.pin?'c3d':'c3a');
 return{html:`<div class="q-tt"><span></span><b>${T('srcT')}</b><button class="q-plus" id="bPlus" data-act="plus">＋</button></div><div class="p-sub" style="text-align:center">${T('srcS')}</div>
  <div class="q-g">${T('gOwn')}</div>${row('r_own','notebook',T('own'),T('ownS'),'ok')}
  <div class="q-g">${T('gNews')}</div>${row('r_lsm','source',T('lsm'),T('lsmS'),'ok')}${row('r_nasa','lab',T('nasa'),T('nasaS'),'ok')}${row('r_hn','conversations',T('hn'),T('hnS'),F.fixed?'ok':'fail')}
  ${F.added?row('r_rs','source','Rīgas satiksme',T('added'),'new'):''}
  <div class="q-g">${T('gWx')}</div>${row('r_wx','cloud',T('wx'),T('wxS'),'ok')}${row('r_warn','notifications',T('warn'),T('warnS'),'ok')}
  <div class="q-g">${T('gKids')}</div>${row('r_kids','kids_mode',T('kidsRow'),T('kidsRowS'),'lil')}`,
 extra:`<div class="p-sheet ${F.pin?'open':''}"><div class="grab"></div><b style="font:600 17px Sora">${T('pinT')}</b><p class="p-why" style="margin:8px 0 14px">${T('pinS')}</p><div class="q-pin">● ● ● ●</div><button class="q-main" id="bPin" data-act="pinOk">${T('pinB')}</button></div>`}};
/* 4 planner */
V[4]=()=>{
 const seg=`<div class="q-seg">${(st.lang=='ru'?['Сегодня','День','Неделя','Месяц']:['Today','Day','Week','Month']).map((k,i)=>`<button class="${(F.pl=='week'?i==2:i==0)?'on':''}" ${i==2?'id="bWeek" data-act="week"':i==0?'data-act="today"':''}>${k}</button>`).join('')}</div>`;
 if(F.pl=='week'){cap(F.ft?'c4d':'c4c');const ev={0:[['12:30','lunch','me']],1:[['07:30',st.lang=='ru'?'Спортзал?':'Gym?','g']],3:[['17:00',T('pv'),'fam']].concat(F.booked?[['12:15',st.lang=='ru'?'Спортзал':'Gym','me']]:[]),4:[[st.lang=='ru'?'весь день':'all day',st.lang=='ru'?'Аренда':'Rent','me']],5:[['10:00',st.lang=='ru'?'Рынок':'Market','fam']]};
  return{html:`<div class="q-tt"><span></span><b>${T('wk')}</b><span></span></div>${seg}<div class="q-week">${T('days').map((d,i)=>`<div class="q-day"><span class="q-dn">${d}</span><div>${(ev[i]||[]).map(([t,n,c])=>`<div class="q-ev ${c} ${F.booked&&i==3&&t=='12:15'?'p-fade':''}"><b>${t}</b> ${n=='lunch'?T('lunch').slice(6):n}</div>`).join('')}</div></div>`).join('')}</div>
  <button class="q-main" id="bFt" data-act="ft">${I('timer')} ${T('ft')}</button>`,
  extra:`<div class="p-sheet ${F.ft?'open':''}"><div class="grab"></div><b style="font:600 17px Sora">${T('ft')}</b><p class="p-sub" style="margin:4px 0 10px">${T('ftQ')}</p>
  ${['o1','o2','o3'].map(k=>`<button class="q-opt" id="b_${k}" data-act="book_${k}"><span><b>${T(k)}</b><small>${T(k+'s')}</small></span><i class="q-tick"></i></button>`).join('')}</div>`}}
 cap(F.q?'c4b':'c4a');
 return{html:`<div class="q-tt"><span></span><b>${T('pToday')}</b><span></span></div>${seg}
  <div class="p-card q-leave"><h4>${I('timer')}${T('leave')}</h4><p>${T('leaveS')}</p></div>
  <div class="q-tl"><div class="q-ev me"><b>12:30</b> ${T('lunch').slice(6)}</div><div class="q-ev must">${T('rent')}</div><div class="q-ev fam"><b>15:30</b> ${T('mia').slice(6)}</div></div>
  ${F.q?`<div class="p-card sel p-fade"><h4>${I('calendar')}${T('pv')}</h4><p>${T('pvW')} · <span class="${F.g=='?'?'q-ap':''}">${F.g=='05'?'05:00':'17:00'}</span><br>${T('pvF')}</p>
  ${F.q.length>=T('said').length?`<div class="q-guess">${T('guess')} <button class="p-chip ${F.g=='17'?'mint':''}" data-act="g17">17:00</button><button class="p-chip" data-act="g05">05:00</button></div>
  <div class="act"><button class="p-chip mint" id="bSave" data-act="save">${T('save')}</button></div>`:''}</div>`:''}`,
 extra:`<div class="p-in" id="bIn" data-act="input" style="cursor:pointer">${I('add')}<span style="color:${F.q?'var(--cream)':''}">${F.q||T('addPh2')}</span><span class="mic">${I('mic')}</span></div>`}};
/* 5 canvas */
const NOTES=[['n1',40,30,'#FFE08A'],['n2',180,40,'#A8E6A1'],['n3',90,140,'#FFE08A'],['n4',196,160,'#A8E6A1'],['n5',30,250,'#FFE08A'],['n6',170,270,'#FFE08A'],['n7',100,360,'#9EC9FF']];
const GROUP={n1:[20,40],n3:[20,100],n5:[20,160],n6:[20,220],n2:[190,40],n4:[190,100],n7:[190,250]};
V[5]=()=>{
 const tabs=`<div class="q-seg"><button class="${F.cv=='board'?'on':''}" data-act="board">${T('bT')}</button><button class="${F.cv=='draw'?'on':''}" id="bDrawTab" data-act="drawtab">${T('dr')}</button></div>`;
 if(F.cv=='draw'){cap('c5c');const n=Dr.strokes.length,sh=Math.floor(Math.max(0,n-1)/Dr.per)+1;
  return{cls:'',html:tabs+`<div class="q-sheetc">${T('sheet')} ${sh}${sh>1?` · ${sh} ${T('sheets')}`:''}</div>`,extra:`<div class="p-cv" id="cv" style="top:132px;bottom:150px"><canvas id="cvc"></canvas></div>
  <div class="q-bar5" style="bottom:92px">${[['draw','dr'],['undo','undo']].map(([i,k])=>`<button data-act="${k=='undo'?'cvundo':''}">${I(i)}<span>${T(k)}</span></button>`).join('')}<span class="p-sub" style="margin:0 0 0 6px;font-size:11px;align-self:center">${T('demo')}</span></div>`}}
 cap(F.tidy==1?'c5b':'c5a');
 const pos=k=>{const d=NOTES.find(x=>x[0]==k);return F.tidy==2?GROUP[k]:[d[1],d[2]]};
 return{html:tabs+`<div class="q-board">${F.tidy>=1?`<div class="q-grp" style="left:10px;top:${F.tidy==2?14:6}px;width:150px;height:${F.tidy==2?290:420}px;${F.tidy==1?'border-style:dashed':''}"><span>${T('gLook')}</span></div><div class="q-grp" style="left:178px;top:${F.tidy==2?14:6}px;width:150px;height:${F.tidy==2?130:250}px;${F.tidy==1?'border-style:dashed':''}"><span>${T('gMoney')}</span></div><div class="q-grp" style="left:178px;top:${F.tidy==2?224:270}px;width:150px;height:${F.tidy==2?80:150}px;${F.tidy==1?'border-style:dashed':''}"><span>${T('gPeople')}</span></div>`:''}
  ${NOTES.map(([k,x,y,c])=>{const [px,py]=pos(k);return`<div class="q-note" style="left:${px}px;top:${py+(F.tidy==2?14:0)}px;background:${c}">${T(k)}</div>`}).join('')}</div>
  <div class="q-bar5">${[['annotate','vW'],['draw','vD'],['image','vP'],['shapes','vS'],['connection','vC']].map(([i,k])=>`<button data-act="${k=='vD'?'drawtab':''}">${I(i)}<span>${T(k)}</span></button>`).join('')}</div>
  <button class="q-tidy" id="bTidy" data-act="tidy">${I('ai_organize')} ${T('tidy')}</button>`,
 extra:`<div class="p-sheet ${F.tidy==1?'open':''}" style="padding-bottom:96px"><div class="grab"></div><p class="p-why" style="margin:0 0 12px">${T('tidyS')}</p><div class="act" style="display:flex;gap:8px"><button class="p-chip mint" id="bDo" data-act="doTidy">${T('doIt')}</button><button class="p-chip" data-act="noTidy">${T('notNow')}</button></div></div>`}};
/* 6 study */
V[6]=()=>{
 const path=n=>`<div class="q-path">${T('path').map((p,i)=>`<span class="${i<n?'d':i==n?'on':''}">${p}</span>`).join('')}</div>`;
 if(F.sv=='start'){cap('c6a');return{html:`<div class="p-h" style="margin-top:14px">${T('learn')}</div><div style="height:10px"></div>
  <div class="q-four2">${[['camera','stPhoto'],['file_doc','stFile'],['study','stTopic'],['calendar','stTest']].map(([i,k])=>`<button id="st_${k}" data-act="${k=='stTopic'?'topic':''}">${I(i)}<b>${T(k)}</b></button>`).join('')}</div>
  <div class="q-field" style="margin-top:14px">${I('mic')}<span style="opacity:.6">${T('topicV')}</span></div>`}}
 if(F.sv=='try'){cap(F.res?'c6c':F.help?'c6b':'c6a');return{html:path(F.res?2:1)+`<div class="p-sub">${T('q3')}</div><div class="p-h" style="font-size:20px">${T('qT')}</div>
  <div class="q-ans">${T('ans')}</div>
  ${F.hint?`<div class="p-card q-lil p-fade"><h4>${I('explain')}${T('h4')}</h4><p>${T('hint')}</p></div>`:''}
  ${F.res?`<div class="p-card q-almost p-fade"><h4>${I('review')}${T('res')}</h4><p>${T('resS')}</p><div class="act"><button class="p-chip mint" id="bFix" data-act="fix">${T('fix')}</button></div></div>`:
  `<div style="display:flex;gap:8px;margin-top:6px"><button class="q-main" style="flex:1;margin:0" id="bCheck" data-act="check">${T('check')}</button><button class="q-sec" style="width:auto;margin:0;padding:0 16px" id="bHelp" data-act="help">${T('help')}</button></div>`}`,
  extra:`<div class="p-sheet ${F.help?'open':''}"><div class="grab"></div><b style="font:600 17px Sora">${T('help')}</b><div style="height:8px"></div>${['h1','h2','h3','h4','h5','h6','h7'].map((k,i)=>`<button class="q-rung" id="h_${k}" data-act="rung"><span>${i+1}</span>${T(k)}</button>`).join('')}</div>`}}
 if(F.sv=='done'){cap('c6d');return{html:path(4)+`<div class="q-hello" style="margin-top:6px">${RING('#0CF2BA',64,26)}</div><div class="p-h" style="text-align:center">${T('doneT')}</div><p class="p-why" style="text-align:center;margin-bottom:14px">${T('doneS')}</p>
  <div class="p-card"><h4>${I('flashcard')}${T('keep')}</h4><p>${T('keepS')}</p><div class="act"><button class="p-chip mint" id="bKeep" data-act="cards">${T('keep')}</button></div></div>
  <div class="p-card"><h4>${I('calendar')}${T('fri')}</h4><p>${T('friS')}</p></div>`}}
 cap('c6e');return{html:`<div class="p-sub" style="text-align:center;margin-top:8px">FRANÇAIS · 3 / 12</div><div class="q-card"><b>${T('card')}</b>${F.rate?`<span class="p-fade">${T('cardB')}</span>`:''}</div>
  <div class="p-sub" style="text-align:center">${T('how')}</div><div class="q-rate">${[['again','in2'],['almost','in4'],['got','in7']].map(([k,w])=>`<button class="${F.rate==k?'on':''} r-${k}" id="rt_${k}" data-act="rate_${k}"><b>${T(k)}</b><small>${T(w)}</small></button>`).join('')}</div>
  <p class="p-sub" style="text-align:center;margin-top:14px">${T('noStreak')}</p>`}};
/* 7 kids */
const bud=t=>`<div class="p-bud"><div class="av">🦊</div><div class="bb">${t}</div></div>`;
V[7]=()=>{
 if(F.k=='safe'){cap('c7e');return{cls:'kid',html:`<div style="height:150px"></div><div class="q-hello">${RING('#FF9F6B',90,26)}</div><div class="p-h" style="text-align:center">${T('safe')}</div><p class="p-sub" style="text-align:center">${T('safeS')}</p><p class="p-sub" style="text-align:center;margin-top:30px">${T('tapOn')}</p>`}}
 if(F.k=='week'){cap('c7d');return{cls:'paper',html:`<div class="q-tt"><span></span><b>${T('wkT')}</b><span></span></div><p class="p-sub" style="text-align:center">${T('wkS')}</p>
  ${['wk1','wk2','wk3'].map((k,i)=>`<div class="p-card q-pc p-fade" style="animation-delay:${i*.1}s"><p>${T(k)}</p></div>`).join('')}<div class="p-card q-pc q-lilp"><p>${T('wkTry')}</p></div>
  <button class="q-main" id="bHand" data-act="hand">${T('hand')}</button>`}}
 if(F.k=='learn'){cap(F.ka=='a1'?'c7b':'c7a');return{cls:'kid',html:bud(F.ka=='a2'?T('kYes'):F.ka=='a1'?T('kNo'):T('kQ'))+`<div class="q-dino">🦕 🦖</div>
  <div class="p-choice"><button id="ka1" class="${F.ka=='a1'?'q-look':''}" data-act="ka1">${T('kA1')}</button><button id="ka2" class="${F.ka=='a2'?'ok':''}" data-act="ka2">${T('kA2')}</button></div>
  ${F.ka=='a2'?`<button class="p-kbtn" id="bHw" data-act="hw" style="margin-top:16px">📒 ${T('kHw')}</button>`:''}`}}
 if(F.k=='hw'){cap('c7c');return{cls:'kid',html:bud(F.hw?T('kHwOk'):T('kHwHint'))+`<div class="p-big">9 + 6 = ${F.hw?'15':'?'}</div>
  <div class="p-choice">${[13,15,16].map(n=>`<button id="hw${n}" class="${F.hw&&n==15?'ok':''}" data-act="hw${n}">${n}</button>`).join('')}</div><p class="p-sub" style="margin-top:12px">${T('kHwS')}</p>
  <button class="q-gu" id="bGu" data-act="gu">${I('privacy')} ${T('gu')}</button>`,
  extra:`<div class="p-sheet ${F.gu?'open':''}"><div class="grab"></div><b style="font:600 17px Sora">${T('guT')}</b><p class="p-why" style="margin:8px 0 14px">${T('guS')}</p><div class="q-pin">● ● ● ●</div><button class="q-main" id="bGuOk" data-act="guOk">${T('guB')}</button></div>`}}
 cap('c7a');return{cls:'kid',html:bud(T('kHi'))+[['kAsk','💬'],['kLearn','🦕'],['kMake','🎨']].map(([k,e])=>`<button class="p-stop q-door" id="door_${k}" data-act="${k=='kLearn'?'learn':''}"><span class="e">${e}</span><span><b>${T(k)}</b><span>${T(k+'S')}</span></span></button>`).join('')+
  `<div class="p-stop" style="min-height:60px;border-style:dashed"><span class="e">🗺️</span><span><b style="font-size:14px">${T('kAdv')}</b></span></div>`}};
/* actions */
const A={
 just:()=>{F.home=true},next:()=>{if(F.s<4)F.s++;else F.home=true},t_big:()=>{F.big=!F.big},t_read:()=>{F.read=!F.read},
 d_mSimple:()=>{F.depth='mSimple'},d_mStd:()=>{F.depth='mStd';F.offer=false},d_mPro:()=>{F.depth='mPro'},more:()=>{F.offer=true},offerY:()=>{F.offer=false;F.depth='mStd'},offerN:()=>{F.offer=false},why:()=>{F.why=true},whyK:()=>{F.why=false},
 r_hn:()=>{F.src='hn'},tryNow:()=>{F.src='list';F.fixed=true;setTimeout(()=>toast(st.lang=='ru'?'Снова на связи':'Back up to date'),50)},plus:()=>{F.src='add';F.typed='';F.addLen=T('addPh').length},addDone:()=>{F.src='list';F.added=true;setTimeout(()=>toast(T('added')),50)},r_kids:()=>{F.pin=true},pinOk:()=>{F.pin=false},
 input:()=>{},g17:()=>{F.g='17'},g05:()=>{F.g='05'},save:()=>{F.q='';F.pl='week';setTimeout(()=>toast(T('saved')),50)},week:()=>{F.pl='week'},today:()=>{F.pl='today'},ft:()=>{F.ft=true},book_o2:()=>{F.ft=false;F.booked=true;setTimeout(()=>toast(T('booked')),50)},book_o1:()=>{F.ft=false},book_o3:()=>{F.ft=false},
 tidy:()=>{F.tidy=1},doTidy:()=>{F.tidy=2;setTimeout(()=>toast(T('done')),50)},noTidy:()=>{F.tidy=0},drawtab:()=>{F.cv='draw'},board:()=>{F.cv='board'},cvundo:()=>{Dr.strokes.pop()},
 topic:()=>{F.sv='try'},help:()=>{F.help=true},rung:()=>{F.help=false;F.hint=true},check:()=>{F.res=true},fix:()=>{F.sv='done'},cards:()=>{F.sv='cards'},rate_again:()=>{F.rate='again'},rate_almost:()=>{F.rate='almost'},rate_got:()=>{F.rate='got'},
 learn:()=>{F.k='learn'},ka1:()=>{F.ka='a1'},ka2:()=>{F.ka='a2'},hw:()=>{F.k='hw'},hw13:()=>{},hw16:()=>{},hw15:()=>{F.hw=true},gu:()=>{F.gu=true},guOk:()=>{F.gu=false;F.k='week'},hand:()=>{F.k='safe'}
};
['wMe','wChild','wFam','wStudy','wCreate'].forEach(k=>A['w_'+k]=()=>{F.who[k]=!F.who[k]});
['mSimple','mStd','mPro'].forEach(k=>A['m_'+k]=()=>{F.much=k});
['pPhone','pOnline','pLater'].forEach(k=>A['p_'+k]=()=>{F.where=k});
document.addEventListener('click',e=>{const b=e.target.closest('[data-act]');if(!b||!b.dataset.act)return;const f=A[b.dataset.act];if(f){f();draw()}});
/* drawing */
function cvSetup(){const c=$('#cvc');if(!c)return;const r=c.getBoundingClientRect();c.width=r.width*2;c.height=r.height*2;paint();
 let cur=null;const pt=e=>{const q=c.getBoundingClientRect();return[e.clientX-q.left,e.clientY-q.top]};
 c.onpointerdown=e=>{cur=[pt(e)];Dr.cur=cur;c.setPointerCapture(e.pointerId)};c.onpointermove=e=>{if(cur){cur.push(pt(e));paint()}};
 c.onpointerup=()=>{if(cur&&cur.length>1){Dr.strokes.push(cur)}cur=null;Dr.cur=null;draw()}}
const COLS=['#24412F','#3a6ea5','#B0567A','#C98A2B','#14102a','#0a8a6a'];
function paint(){const c=$('#cvc');if(!c)return;const x=c.getContext('2d');x.setTransform(2,0,0,2,0,0);x.clearRect(0,0,c.width,c.height);x.lineCap='round';x.lineJoin='round';
 const all=Dr.cur?[...Dr.strokes,Dr.cur]:Dr.strokes;all.forEach((s,i)=>{x.strokeStyle=COLS[Math.floor(i/Dr.per)%COLS.length];x.lineWidth=5;x.beginPath();s.forEach(([a,b],j)=>j?x.lineTo(a,b):x.moveTo(a,b));x.stroke()})}
function draw(){document.documentElement.lang=st.lang;side();const v=V[st.flow]();const s=$('#sc');s.className='p-sc '+(v.cls||'');
 s.innerHTML=`${SB}<div class="p-body">${v.html}</div>${v.extra||''}${DOCK()}<div class="p-toast" id="toast"></div>`;if(st.flow==5&&F.cv=='draw')cvSetup()}
/* auto demo */
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function tap(sel,wait=1000){const el=typeof sel=='string'?$(sel):sel;if(!el){await sleep(200);return}const h=$('#hand'),r=el.getBoundingClientRect();h.style.display='block';h.style.left=(r.left+r.width/2)+'px';h.style.top=(r.top+r.height/2)+'px';await sleep(650);h.classList.add('tap');await sleep(140);h.classList.remove('tap');el.click();await sleep(wait)}
async function typeIn(key,txt,ms=60){for(const ch of txt){F[key]+=ch;draw();await sleep(ms)}}
async function autoStroke(pts){const c=$('#cvc');if(!c)return;const q=c.getBoundingClientRect(),h=$('#hand');h.style.display='block';const cur=[];Dr.cur=cur;
 for(const [a,b] of pts){cur.push([a,b]);h.style.transition='none';h.style.left=(q.left+a)+'px';h.style.top=(q.top+b)+'px';paint();await sleep(12)}
 Dr.strokes.push(cur);Dr.cur=null;h.style.transition='';draw();await sleep(260)}
const SCRIPT={
 1:async()=>{await sleep(1200);await tap('#t_big',1500);await tap('#t_big',700);await tap('#bNext',1200);await tap('#w_wStudy',700);await tap('#w_wCreate',900);await tap('#bNext',1200);await tap('#m_mSimple',1100);await tap('#m_mStd',900);await tap('#bNext',1200);await tap('#bNext',3800)},
 2:async()=>{await sleep(1300);await tap('#s_dMore',1800);await tap('#bOfferY',1800);await tap('#d_mPro',2200);await tap('#d_mStd',1200);await tap('#bWhy',2200);await tap('#bWhyK',1200)},
 3:async()=>{await sleep(1500);await tap('#r_hn',2600);await tap('#bTry',1800);await tap('#bPlus',600);await typeIn('typed',T('addPh'),70);await sleep(1600);await tap('#bAdd',2000);await tap('#r_kids',2000);await tap('#bPin',1200)},
 4:async()=>{await sleep(1300);await tap('#bIn',200);await typeIn('q',T('said'),45);await sleep(1500);await tap('[data-act="g05"]',900);await tap('[data-act="g17"]',900);await tap('#bSave',2200);await tap('#bFt',1500);await tap('#b_o2',2600)},
 5:async()=>{await sleep(1300);await tap('#bTidy',1800);await tap('#bDo',2400);await tap('#bDrawTab',800);
  for(let i=0;i<9;i++){const y=40+i*42;await autoStroke(Array.from({length:30},(_,j)=>[30+j*9,y+Math.sin(j/4+i)*12]))}await sleep(1800)},
 6:async()=>{await sleep(1200);await tap('#st_stTopic',1300);await tap('#bHelp',1400);await tap('#h_h4',2400);await tap('#bCheck',2400);await tap('#bFix',2400);await tap('#bKeep',1300);await tap('#rt_almost',2200)},
 7:async()=>{await sleep(1400);await tap('#door_kLearn',1500);await tap('#ka1',2200);await tap('#ka2',2000);await tap('#bHw',2000);await tap('#hw15',2000);await tap('#bGu',1500);await tap('#bGuOk',2600);await tap('#bHand',2200)}
};
async function runAuto(f){st.flow=f;reset();Dr.strokes=[];draw();await SCRIPT[f]();$('#hand').style.display='none'}
window.haloAuto=async list=>{for(const f of list)await runAuto(f);window.autoDone=1};
window.haloGo=(f,patch)=>{st.flow=f;reset();Object.assign(F,patch||{});draw()};
document.body.classList.toggle('still',st.still);
draw();
if(st.auto){const l=st.auto=='all'?Array.from({length:N},(_,j)=>j+1):[st.flow];setTimeout(()=>window.haloAuto(l),600)}
})();
