/* halo Learning prototype — screens. One render function per screen ID; `st` = state, `th` = light|dark.
   Same code drives: clickable prototype (proto.html), screenshots for boards/book (?shot=1). */
const IC = window.HALO_ICONS || {};
const ic = (n, cls = '') => `<span class="ic ${cls}">${IC[n] || IC['more'] || ''}</span>`;
const go = (to, ev, label) => `data-go="${to}" data-ev="${ev}" title="${ev}"`;
const kidsRing = (state, th, size = 150) => `<div class="ring" style="width:${size}px;height:${size}px"><img src="a/orb/halo_kids_orb_${state}_${th}.svg" alt=""></div>`;
const orb = (state, th, size = 40, kids = false) => `<img src="a/orb/halo_${kids ? 'kids_' : ''}orb_${state}_${th}.svg" style="width:${size}px;height:${size}px;display:block" alt="">`;
const status = () => `<div class="sb"><span>9:41</span><span>●●● <i></i></span></div>`;
const where = (kind = 'local', txt) => {
  const m = { local: ['on_device', 'On this phone'], cloud_ok: ['cloud', "Needs a grown-up's OK"], cloud: ['cloud', 'In the cloud'],
    offline: ['disconnected', 'Offline'], unavailable: ['unavailable', 'Helper not set up'], reading: ['on_device', 'Reading stays on this phone'] }[kind];
  return `<span class="chip where ${kind === 'cloud_ok' || kind === 'unavailable' ? 'att' : ''}">${ic(m[0])}${txt || m[1]}</span>`;
};
const STAGES = [['start', 'home'], ['discover', 'think'], ['explain', 'explain'], ['explore', 'connection'], ['practice', 'practise'], ['reflect', 'review'], ['continue', 'history']];
const path = (now, kids = true, label) => {
  const i = STAGES.findIndex(s => s[0] === now);
  let h = '<div class="path">';
  STAGES.forEach((s, k) => {
    if (k) h += `<div class="ln ${k <= i ? 'done' : ''}"></div>`;
    const c = k < i ? 'done' : k === i ? 'now' : k === i + 1 ? 'next' : '';
    h += `<div class="st ${c}">${c === 'now' || c === 'next' ? ic(s[1]) : ''}</div>`;
  });
  return h + `</div>`;
};
const crumb = (now) => { const i = STAGES.findIndex(s => s[0] === now); const L = ['Start', 'What I know', 'Explain', 'Explore', 'Practice', 'Reflect', 'Continue']; return `<div class="crumb">Stage ${i + 1} of 7 · ${L[i]}</div>`; };

function dock(o = {}) {
  // o: {kids, mode:'idle'|'listening'|'speaking'|'stopped', intent:bool, cap:string, field}
  const live = o.mode === 'listening' || o.mode === 'speaking' || o.mode === 'thinking';
  const micCls = live ? 'mic stop' : 'mic';
  const micIc = live ? ic('stop') : ic('mic');
  const rip = o.mode === 'listening' ? '<i class="ripple"></i><i class="ripple r2"></i><i class="ripple r3"></i>' : '';
  const label = live ? (o.mode === 'listening' ? 'Stop listening' : 'Stop') : 'Talk to halo';
  if (o.kids === false) {
    return `<div class="dock">${o.cap ? `<div class="cap">${o.cap}</div>` : ''}<span class="field">${o.field || 'Ask or answer…'}</span>
      <span class="ib s2" aria-label="Attach">${ic('attach')}</span>
      <span class="${micCls} ${o.intent ? 'intent round' : ''}" aria-label="${label}" ${go('self', live ? 'runtime.stop' : 'voice.open')}>${rip}${micIc}</span></div>`;
  }
  return `<div class="dock">${o.cap ? `<div class="cap">${o.cap}</div>` : ''}
    <span class="ib" aria-label="Type instead" ${go('self', 'input.type')}>${ic('ask_mode')}</span>
    <span class="${micCls} ${o.intent ? 'intent round' : ''}" aria-label="${label}" ${go('self', live ? 'runtime.stop' : 'voice.open')}>${rip}${micIc}</span>
    <span class="ib" aria-label="What can I do here?" ${go('self', 'help.speak')}>${ic('guide_me')}</span></div>`;
}
const topBar = (title, o = {}) => `<div class="top">${o.back === false ? '' : `<span class="ib" aria-label="Back" ${go(o.backTo || 'K00', 'nav.back')}>${ic('back')}</span>`}
  <h1>${title}</h1>${o.right || ''}</div>`;

/* ---------- illustrations (inline, theme-aware via currentColor / vars) ---------- */
const ILL = {
  blackhole: (th) => `<svg viewBox="0 0 328 150" width="100%" style="display:block"><rect width="328" height="150" fill="${th === 'dark' ? '#0D0818' : '#1A1030'}"/>
    ${Array.from({ length: 34 }, (_, i) => `<circle cx="${(i * 97) % 328}" cy="${(i * 53) % 150}" r="${i % 3 ? .9 : 1.5}" fill="#FBF6F0" opacity="${.35 + (i % 4) * .15}"/>`).join('')}
    <ellipse cx="164" cy="78" rx="96" ry="22" fill="none" stroke="#FF9F6B" stroke-width="7" opacity=".55"/>
    <ellipse cx="164" cy="78" rx="78" ry="16" fill="none" stroke="#FFD2B0" stroke-width="3" opacity=".7"/>
    <circle cx="164" cy="74" r="34" fill="#050208"/><circle cx="164" cy="74" r="36" fill="none" stroke="#FFB27E" stroke-width="2" opacity=".7"/>
    <path d="M70 78 Q164 112 258 78" fill="none" stroke="#FF9F6B" stroke-width="7" opacity=".75"/></svg>`,
  worksheet: (o = {}) => `<svg viewBox="0 0 300 380" width="100%" style="display:block">
    <rect x="18" y="14" width="264" height="352" rx="6" fill="#FBF8F1" transform="rotate(-1.6 150 190)"/>
    <g transform="rotate(-1.6 150 190)" font-family="Atk, sans-serif" fill="#2A2238">
      <text x="40" y="52" font-size="15" font-weight="700">Maths · Week 6</text><line x1="40" y1="62" x2="262" y2="62" stroke="#D9CFC0"/>
      <text x="40" y="96" font-size="13.5">1) 37 + 25 = ____</text>
      <text x="40" y="140" font-size="13.5">2) 64 − 28 = ____</text>
      <text x="40" y="184" font-size="13.5">3) A bus has 46 seats. 18 people</text><text x="58" y="202" font-size="13.5">get on, then 9 get off. How many</text><text x="58" y="220" font-size="13.5">seats are empty now?</text>
      <text x="40" y="262" font-size="13.5">4) 9 × 7 = ____   8 × 6 = ___</text>
      <path d="M40 300 q30 -8 60 0 t60 0" stroke="#B7AFA4" fill="none"/><text x="40" y="330" font-size="11" fill="#8A8173">Name: ________</text>
    </g>
    ${o.boxes ? `<g fill="none" stroke-width="3" stroke-linejoin="round">
      <rect x="32" y="76" width="160" height="32" rx="8" stroke="#0CF2BA"/><text x="198" y="98" font-family="Atk" font-weight="800" font-size="13" fill="#0CF2BA">1</text>
      <rect x="32" y="120" width="160" height="32" rx="8" stroke="#0CF2BA"/><text x="198" y="142" font-family="Atk" font-weight="800" font-size="13" fill="#0CF2BA">2</text>
      <rect x="32" y="164" width="240" height="66" rx="8" stroke="#0CF2BA" ${o.pick === 3 ? 'stroke-width="5"' : ''}/><text x="258" y="184" font-family="Atk" font-weight="800" font-size="13" fill="#0CF2BA">3</text>
      <rect x="32" y="242" width="240" height="32" rx="8" stroke="#FF9F6B" stroke-dasharray="7 6"/><circle cx="272" cy="242" r="7" fill="#FF9F6B"/><text x="252" y="292" font-family="Atk" font-weight="800" font-size="13" fill="#FF9F6B">4</text></g>` : ''}
    ${o.frame ? `<g stroke="${o.frame === 'locked' ? '#0CF2BA' : '#FBF6F0'}" stroke-width="5" stroke-linecap="round" fill="none" ${o.frame === 'searching' ? 'opacity=".7"' : ''}>
      <path d="M12 46 V10 H48"/><path d="M252 10 H288 V46"/><path d="M288 334 V370 H252"/><path d="M48 370 H12 V334"/></g>` : ''}</svg>`,
  ex3: () => `<svg viewBox="0 0 300 96" width="100%" style="display:block"><rect width="300" height="96" fill="#FBF8F1"/>
    <g font-family="Atk, sans-serif" fill="#2A2238" font-size="15"><text x="14" y="28">3) A bus has 46 seats. 18 people</text><text x="32" y="50">get on, then 9 get off. How many</text><text x="32" y="72">seats are empty now?</text></g></svg>`,
  fox: (th) => `<svg viewBox="0 0 120 120" width="100%" style="display:block"><rect width="120" height="120" rx="18" fill="${th === 'dark' ? '#2E2238' : '#F7E9DD'}"/>
    <path d="M30 86 L42 42 L56 62 L64 62 L78 42 L90 86 Z" fill="#FF9F6B"/><path d="M44 80 Q60 96 76 80 L70 70 L50 70 Z" fill="#FBF6F0"/>
    <circle cx="52" cy="68" r="3" fill="#1A0F14"/><circle cx="68" cy="68" r="3" fill="#1A0F14"/><circle cx="60" cy="78" r="3.2" fill="#1A0F14"/>
    <path d="M86 30 a10 10 0 1 1 -6 -6" fill="none" stroke="#0CF2BA" stroke-width="3" stroke-linecap="round"/></svg>`,
  leaf: (th) => `<svg viewBox="0 0 328 110" width="100%" style="display:block"><rect width="328" height="110" fill="${th === 'dark' ? '#110E45' : '#EFF0E8'}"/>
    <circle cx="276" cy="30" r="16" fill="#FF9F6B" opacity=".9"/><path d="M60 92 C70 30 150 18 200 40 C160 50 120 70 60 92Z" fill="#0CF2BA" opacity=".85"/>
    <path d="M60 92 C110 64 150 50 200 40" stroke="${th === 'dark' ? '#060330' : '#047A5E'}" stroke-width="2" fill="none"/>
    <g font-family="Atk" font-size="11" font-weight="700" fill="${th === 'dark' ? '#B7B9D3' : '#4A4F63'}"><text x="226" y="64">light</text><text x="20" y="40">CO₂ + H₂O</text><text x="214" y="96">glucose + O₂</text></g></svg>`,
};

/* ---------- screens ---------- */
const SCREENS = {
K00(st, th) {
  const resume = st !== 'first';
  const listening = st === 'listening';
  return `${status()}${topBar('', { back: false, right: `${where(st === 'offline' ? 'offline' : 'local')}<span class="ib" aria-label="Grown-ups" ${go('G90', 'gate.request')}>${ic('privacy')}</span>` })}
  <div class="body" style="top:84px">
    ${kidsRing(listening ? 'listening' : st === 'offline' ? 'offline' : 'idle', th, resume ? 132 : 168)}
    <div class="ttl" style="text-align:center;margin-top:10px">${listening ? 'Listening…' : 'Hi there!<br>What shall we do today?'}</div>
    ${resume ? `<div class="tile wide intent" style="margin-bottom:12px" ${go('K12', 'learning.resume')}><div class="pic">${ic('study')}</div><div style="flex:1"><b>Keep going</b><br><span>Black holes · Explain</span></div>${ic('chevron_right')}</div>` : ''}
    <div class="grid2"><div class="tile big" ${go('K10', 'learning.intent(explore)')}><div class="pic">${ic('search')}</div><b>Explore</b><span>Find out something new</span></div>
    <div class="tile big" ${go('C30', 'create.intent')}><div class="pic" style="background:color-mix(in srgb,var(--apricot) 26%,transparent)">${ic('sketch')}</div><b>Create</b><span>Make a story or a project</span></div></div>
  </div>${dock({ mode: listening ? 'listening' : 'idle', intent: !resume, cap: listening ? '“Let’s learn about…”' : 'Say “let’s learn” or tap a picture' })}`;
},
K10(st, th) {
  const L = st === 'listening';
  return `${status()}${topBar('Explore', { right: where('local') })}<div class="body">
  <div class="ttl">What do you want<br>to find out?</div>
  ${L ? `<div class="bubble me" style="max-width:85%;margin-bottom:14px">What is a black hole?</div>` : ''}
  <div class="grid2">
    <div class="tile" ${go('K11', 'voice.transcript → learning.start')}><div class="pic">${ic('mic')}</div><b>Say it</b><span>Ask out loud</span></div>
    <div class="tile" ${go('H20', 'homework.start')}><div class="pic" style="background:color-mix(in srgb,var(--apricot) 26%,transparent)">${ic('camera')}</div><b>Homework</b><span>Take a photo</span></div>
    <div class="tile" ${go('K11', 'source.pick(notebook|file)')}><div class="pic">${ic('notebook')}</div><b>My notes</b><span>A note or file</span></div>
    <div class="tile" ${go('K12', 'learning.resume')}><div class="pic">${ic('history')}</div><b>Keep going</b><span>Black holes</span></div>
  </div>
  <div class="lbl">Recently</div><div class="row" style="flex-wrap:wrap;gap:8px"><span class="chip">${ic('study')}Black holes · 3/7</span><span class="chip">${ic('practise')}Times tables</span></div>
  </div>${dock({ mode: L ? 'listening' : 'idle', intent: !L, cap: L ? 'Listening…' : 'Tap the microphone and ask' })}`;
},
K11(st, th) {
  const L = st === 'listening';
  return `${status()}${topBar('Black holes', { backTo: 'K10', right: where('local') })}<div class="body">
  ${path('discover')}
  <div style="display:flex;gap:12px;align-items:center;margin:4px 0 14px">${orb(L ? 'listening' : st === 'speaking' ? 'speaking' : 'waiting', th, 64, true)}
    <div class="q" style="font:600 calc(21px*var(--fs))/1.28 Sora">What do you already know about black holes?</div></div>
  <div style="display:grid;gap:10px">
   ${[['I know a lot', 3], ['A little', 2], ['Not yet', 1]].map(([t, n]) => `<div class="tile wide" style="min-height:64px" ${go('K12', 'learning.priorKnowledge(' + n + ')')}>
      <svg width="44" height="44" viewBox="0 0 44 44"><g fill="none" stroke-width="5" stroke-linecap="round">${[0, 1, 2].map(k => `<path d="${['M22 6 a16 16 0 0 1 14 8', 'M37 22 a16 16 0 0 1 -11 15', 'M14 36 a16 16 0 0 1 -6 -21'][k]}" stroke="${k < n ? 'var(--ink)' : 'var(--line)'}"/>`).join('')}</g></svg><b style="font-size:18px">${t}</b></div>`).join('')}
  </div>
  <div style="text-align:center;margin-top:12px" ${go('K12', 'learning.priorKnowledge(unknown)')}><span class="btn line">I don't know — that's fine</span></div>
  </div>${dock({ mode: L ? 'listening' : st === 'speaking' ? 'speaking' : 'idle', intent: st === 'waiting' || !st, cap: L ? '“Something about space…”' : st === 'speaking' ? 'halo is asking…' : 'Say it, or tap an answer' })}`;
},
K12(st, th) {
  const L = st === 'listening', ok = st === 'correct', no = st === 'notyet', hes = st === 'hesitate', stop = st === 'stopped';
  const body = ok ? `<div class="row" style="gap:8px;margin-bottom:10px"><span class="tag">${ic('check')}You worked it out</span></div>
      <p>Yes — light is fast, but the pull near a black hole is even stronger, so light can’t climb out.</p>`
    : no ? `<div class="row" style="gap:8px;margin-bottom:10px"><span class="tag" style="background:var(--s2);color:var(--text)">Not quite yet</span></div>
      <p>Black holes aren’t empty holes. Let’s look at it another way: imagine a slide so steep that nothing can climb back up.</p>`
    : `<p><span class="hl">A black hole</span> is a place where gravity pulls so hard that not even light can get out.</p>`;
  return `${status()}${topBar('Black holes', { backTo: 'K00', right: where('local') })}<div class="body">
  ${path('explain')}
  <div class="focus ${ok ? '' : ''}"><div class="ill">${ILL.blackhole(th)}</div>${body}
    ${!ok && !no ? `<div class="q" style="font-size:calc(17px*var(--fs));margin-top:12px">Why do you think light can’t get out?</div>` : ''}</div>
  <div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap">
    <span class="chip" ${go('K12', 'tutor.request(simpler)')}>${ic('explain')}Simpler</span>
    <span class="chip" ${go('K12', 'tutor.request(example)')}>${ic('card')}Example</span>
    <span class="chip ${hes ? 'intent' : ''}" ${go('K12', 'tutor.request(hint)')}>${ic('guide_me')}Hint</span>
    <span class="chip ${ok ? 'intent' : ''}" ${go('K13', 'learning.branches')}>${ic('connection')}Show me more</span>
  </div>
  ${L ? `<div class="bubble me" style="max-width:86%;margin-top:12px">because it pulls it back?</div>` : ''}
  ${stop ? `<div class="toast" style="bottom:4px;position:absolute">${ic('stop')}Stopped. Tap the microphone when you’re ready.</div>` : ''}
  </div>${dock({ mode: L ? 'listening' : st === 'speaking' ? 'speaking' : 'idle', intent: !hes && !ok && !no && !stop && !L && st !== 'speaking', cap: L ? 'Listening…' : st === 'speaking' ? '“…not even light can get out.”' : no ? 'Try again, or tap Hint' : ok ? 'Want to go deeper?' : 'Tell me what you think' })}`;
},
K13(st, th) {
  const loading = st === 'loading';
  const br = [['Gravity', 'connection', 'Why things fall'], ['Event horizon', 'explore', 'The point of no return'], ['Stars', 'favorite', 'How black holes are born']];
  return `${status()}${topBar('Where next?', { backTo: 'K12', right: where('local') })}<div class="body">
  ${path('explore')}
  <svg width="328" height="150" viewBox="0 0 328 150" style="display:block;margin:0 auto">
    <g stroke="var(--ink)" stroke-width="2.5" fill="none" stroke-linecap="round" opacity="${loading ? .25 : .7}"><path d="M164 66 C120 100 70 104 56 140"/><path d="M164 66 L164 140"/><path d="M164 66 C208 100 258 104 272 140"/></g>
    <circle cx="164" cy="56" r="44" fill="var(--s1)"/><path d="M196.5 42.5A35 35 0 1 1 177.5 23.5" fill="none" stroke="url(#kg)" stroke-width="8" stroke-linecap="round"/>
    <defs><linearGradient id="kg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#0CF2BA"/><stop offset="1" stop-color="#FF9F6B"/></linearGradient></defs>
    <text x="164" y="61" text-anchor="middle" font-family="Sora" font-weight="600" font-size="12.5" fill="var(--text)">Black holes</text></svg>
  <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:-4px">
   ${br.map(([t, i, s]) => loading ? `<div class="tile" style="min-height:132px"><div class="shim" style="width:56px;height:56px;border-radius:50%"></div><div class="shim" style="width:80%"></div><div class="shim" style="width:60%"></div></div>`
     : `<div class="tile" style="padding:12px 10px;min-height:132px" ${go('K12', 'learning.branch(' + t + ')')}><div class="pic" style="width:48px;height:48px">${ic(i)}</div><b style="font-size:calc(15.5px*var(--fs))">${t}</b><span style="font-size:12.5px">${s}</span></div>`).join('')}
  </div>
  <div class="row" style="justify-content:center;gap:8px;margin-top:14px"><span class="chip" ${go('K12', 'learning.branch(main)')}>${ic('back')}Back to black holes</span></div>
  </div>${dock({ mode: 'idle', cap: loading ? 'Finding ideas…' : 'Pick one, or ask something else' })}`;
},
K14(st, th) {
  const saved = st === 'saved';
  return `${status()}${topBar('What I learned', { backTo: 'K12', right: where('local') })}<div class="body">
  ${path('reflect')}
  <div class="lbl" style="margin-top:4px">I learned</div>
  <div style="display:grid;gap:8px"><div class="bubble me" style="margin:0;max-width:none">Gravity can pull so hard that light can’t escape.</div>
  <div class="bubble me" style="margin:0;max-width:none">The edge is called the event horizon.</div></div>
  <div class="lbl">Still wondering</div><div class="bubble" style="max-width:none">What happens inside?</div>
  <div class="card s2" style="margin-top:14px;display:flex;gap:12px;align-items:center"><svg width="42" height="42" viewBox="0 0 42 42"><path d="M35 14A16 16 0 1 1 28 7" fill="none" stroke="var(--ink)" stroke-width="5" stroke-linecap="round"/><text x="21" y="26" text-anchor="middle" font:700 font-family="Sora" font-weight="700" font-size="13" fill="var(--text)">4</text></svg>
    <div class="sm">You tried <b>4 steps</b> and explained <b>2 ideas</b>.</div></div>
  <div style="display:grid;gap:10px;margin-top:14px"><span class="btn full ${saved ? 'ghost' : 'intent'}" ${go('K14', 'notebook.save(summary)')}>${ic(saved ? 'check' : 'remember')}${saved ? 'Saved in My notes' : 'Save to my notes'}</span>
  <span class="btn full line ${saved ? 'intent' : ''}" ${go('K00', 'learning.pause')}>${ic('history')}Keep going later</span></div>
  </div>${dock({ mode: 'idle', cap: 'Or tell me what you’d like next' })}`;
},
H20(st, th) {
  const f = st === 'searching' ? 'searching' : st === 'perm' ? null : 'locked';
  if (st === 'perm') return `${status()}${topBar('Homework', { backTo: 'K10' })}<div class="body"><div style="text-align:center;margin-top:40px">${kidsRing('permission_missing', th, 120)}
    <div class="ttl" style="margin-top:16px">I need the camera to see your page</div><p class="sub">Android will ask. The photo stays on this phone.</p>
    <div style="display:grid;gap:10px;margin-top:18px"><span class="btn full" ${go('H20', 'permission.request(camera)')}>${ic('camera')}Use the camera</span><span class="btn full line" ${go('H21', 'source.pick(gallery)')}>${ic('image')}Pick a photo instead</span></div></div></div>${dock({ cap: 'Or say “use a photo”' })}`;
  return `<div class="cam"></div><div style="position:absolute;inset:0;color:#FBF6F0">${status().replace('class="sb"', 'class="sb" style="color:#FBF6F0"')}
   <div class="top" style="color:#FBF6F0"><span class="ib" style="color:#FBF6F0" ${go('K10', 'nav.back')}>${ic('back')}</span><h1 style="color:#FBF6F0">Show me your homework</h1></div>
   <div style="padding:6px 16px 0"><span class="chip where" style="color:#E9DFD8;border-color:rgba(255,255,255,.25)">${ic('on_device')}Reading stays on this phone</span></div>
   <div style="margin:14px 30px 0">${ILL.worksheet({ frame: f })}</div>
   <div style="text-align:center;margin-top:12px;font:600 16px Atk">${f === 'locked' ? 'Page found — hold still' : 'Point at your page'}</div>
   <div style="position:absolute;left:0;right:0;bottom:26px;display:flex;justify-content:space-around;align-items:center;padding:0 20px">
     <span class="ib" style="background:rgba(255,255,255,.14);color:#FBF6F0" ${go('H21', 'source.pick(gallery)')}>${ic('image')}</span>
     <span class="ib" style="background:rgba(255,255,255,.14);color:#FBF6F0" ${go('H21', 'source.pick(file)')}>${ic('file_doc')}</span>
     <span class="${f === 'locked' ? 'intent round' : ''}" style="width:78px;height:78px;border-radius:50%;background:#FBF6F0;box-shadow:inset 0 0 0 5px #160E1C;display:block;--ink:#0CF2BA" aria-label="Take photo" ${go('H21', 'capture.still → extraction.start')}></span>
     <span class="ib" style="background:rgba(255,255,255,.14);color:#FBF6F0" ${go('H21', 'source.pick(notebook)')}>${ic('notebook')}</span>
     <span class="ib" style="background:rgba(255,255,255,.14);color:#FBF6F0" ${go('self', 'voice.open')}>${ic('mic')}</span></div></div>`;
},
H21(st, th) {
  if (st === 'reading') return `${status()}${topBar('Reading your page', { backTo: 'H20', right: where('reading', 'On this phone') })}<div class="body">
    <div class="card" style="padding:10px;opacity:.85">${ILL.worksheet({})}</div><div class="shim" style="width:70%;margin-top:14px"></div><div class="shim" style="width:45%"></div>
    </div>${dock({ mode: 'thinking', cap: 'Reading… tap Stop to cancel' })}`;
  return `${status()}${topBar('I found 4 exercises', { backTo: 'H20', right: where('local') })}<div class="body">
  <div class="card" style="padding:8px;height:252px;overflow:hidden"><div style="margin-top:-30px">${ILL.worksheet({ boxes: true })}</div></div>
  <div style="display:grid;gap:8px;margin-top:12px">
   ${[1, 2, 3].map(n => `<div class="tile wide" style="min-height:56px;padding:8px 12px" ${go('H22', 'homework.select(' + n + ')')}><b style="font-size:16px;flex:1">Exercise ${n}</b><span class="tag">${ic('check')}Sure</span>${ic('chevron_right')}</div>`).join('')}
   <div class="tile wide intent" style="min-height:64px;padding:8px 12px" ${go('H21', 'extraction.correct(4)')}><div style="flex:1"><b style="font-size:16px">Exercise 4</b><br><span class="sm">I couldn’t read the last number</span></div><span class="tag att"><i class="attdot"></i>Please check</span></div>
  </div></div>${dock({ cap: 'Say “exercise three” or tap one' })}`;
},
H22(st, th) {
  const L = st === 'listening', hint = st === 'hint', chk = st === 'checking', stop = st === 'stopped';
  const rungs = [['Remember', 'memory'], ['Hint', 'guide_me'], ['Worked step', 'practise'], ['Another way', 'explain'], ['Full solution', 'review']];
  const nowR = hint ? 1 : 0;
  return `${status()}${topBar('Exercise 3', { backTo: 'H21', right: `<div class="dots" style="margin-right:6px"><i class="on"></i><i class="on"></i><i class="on"></i><i></i></div>` })}<div class="body">
  <div class="focus" style="padding:12px"><div class="ill" style="margin:0 0 12px">${ILL.ex3()}</div>
    <div class="saidby">${orb(L ? 'listening' : chk ? 'thinking' : st === 'speaking' ? 'speaking' : 'waiting', th, 30, true)}<div class="q" style="font-size:calc(17.5px*var(--fs))">${hint ? 'Hint: first work out how many people are on the bus.' : 'What did you do with problems like this in class?'}</div></div>
    ${L || chk ? `<div class="bubble me" style="margin-top:10px;max-width:90%">18 get on and 9 get off so 9 people are on it</div>` : ''}
    ${chk ? `<div class="shim" style="width:70%;margin-top:12px"></div>` : ''}
  </div>
  <div class="lbl">Help, one step at a time</div>
  <div class="ladder">${rungs.map(([t, i], k) => `<div class="rung ${k < nowR ? 'used' : k === nowR ? 'now' : ''} ${(st === 'hesitate' && k === 1) ? 'intent' : ''}" ${go('H22', k === 4 ? 'confirm → tutor.request(SOLUTION)' : 'tutor.request(' + t + ')')}>${ic(i)}${t}</div>`).join('')}</div>
  <div style="margin-top:14px"><span class="btn full ${L ? 'intent' : 'ghost'}" ${go('H23', 'attempt.submit')}>${ic('check')}Check my thinking</span></div>
  ${stop ? `<div class="toast" style="bottom:4px;position:absolute">${ic('stop')}Stopped — your work is saved.</div>` : ''}
  </div>${dock({ mode: L ? 'listening' : chk ? 'thinking' : 'idle', intent: !L && !chk && st !== 'hesitate' && !stop, cap: L ? 'Listening…' : chk ? 'Checking your thinking…' : 'Tell me how you’d start' })}`;
},
H23(st, th) {
  const no = st === 'notyet', sol = st === 'solution';
  return `${status()}${topBar(no ? 'Let’s look again' : 'Why it works', { backTo: 'H22', right: `<div class="dots" style="margin-right:6px"><i class="on"></i><i class="on"></i><i class="on"></i><i></i></div>` })}<div class="body">
  <div class="focus">
   ${no ? `<span class="tag" style="background:var(--s2);color:var(--text)">Not quite yet</span><p style="margin-top:10px">You took 9 away from 46. But first, <span class="hl">18 people got on</span>. How many are on the bus after the second stop?</p>`
     : `<span class="tag">${ic('check')}Your thinking works</span>
   <p style="margin-top:10px">You said <span class="hl">18 get on and 9 get off, so 9 people are on the bus</span>. Then 46 − 9 = <b>37 empty seats</b>.</p>`}
   ${sol ? `<div class="card s2" style="margin-top:12px"><b class="sm">The method</b><ol class="sm" style="margin:6px 0 0;padding-left:18px"><li>People on the bus: 18 − 9 = 9</li><li>Empty seats: 46 − 9 = 37</li></ol></div>`
     : `<div class="row" style="margin-top:12px;justify-content:space-between"><span class="sm mut">${no ? 'Want a hint?' : 'See the method'}</span>${ic('chevron_down')}</div>`}
  </div>
  <div class="card s2" style="margin-top:12px;display:flex;align-items:center;gap:12px"><div style="flex:1"><div class="sm mut">Next</div><b>Exercise 4 · 9 × 7</b></div><span class="tag att"><i class="attdot"></i>Check the photo first</span></div>
  <div style="display:grid;gap:10px;margin-top:14px">
   ${no ? `<span class="btn full intent" ${go('H22', 'homework.retry')}>${ic('retry')}Try again</span><span class="btn full line" ${go('H22', 'tutor.request(hint)')}>${ic('guide_me')}Get a hint</span>`
     : `<span class="btn full intent" ${go('H22', 'homework.next')}>${ic('chevron_right')}Next exercise</span><span class="btn full line" ${go('K14', 'learning.checkpoint')}>${ic('remember')}Save my place</span>`}
  </div></div>${dock({ cap: no ? 'Or tell me how you’d count it' : 'Say “next” when you’re ready' })}`;
},
C30(st, th) {
  return `${status()}${topBar('Create', { right: where('local') })}<div class="body">
  <div class="ttl">What shall we make?</div>
  <div style="display:grid;gap:10px">
    ${[['Story', 'notebook', 'Your hero, your choices', 'C31'], ['Character', 'kids_mode', 'Name, looks and superpowers', 'C31'], ['Project', 'lab', 'Build or invent something', 'C31']].map(([t, i, s, to], k) =>
      `<div class="tile wide" ${go(to, 'create.start(' + t.toLowerCase() + ')')}><div class="pic" style="${k === 1 ? 'background:color-mix(in srgb,var(--apricot) 26%,transparent)' : ''}">${ic(i)}</div><div style="flex:1"><b>${t}</b><br><span>${s}</span></div>${ic('chevron_right')}</div>`).join('')}
  </div>
  <div class="card s2" style="margin-top:12px;display:flex;gap:10px;align-items:center">${ic('draw')}<div class="sm" style="flex:1"><b>Drawing</b> needs a grown-up to set up a picture helper.</div><span class="chip" ${go('C32', 'canvas.open(draw)')}>Draw it myself</span></div>
  </div>${dock({ intent: true, cap: 'Or just tell me your idea' })}`;
},
C31(st, th) {
  const L = st === 'listening';
  return `${status()}${topBar('My story', { backTo: 'C30', right: where('local') })}<div class="body">
  ${path('discover')}
  <div class="card" style="display:flex;gap:12px;align-items:center"><div style="width:64px;flex:none">${ILL.fox(th)}</div><div style="flex:1"><div class="sm mut">My idea</div><b style="font:600 calc(18px*var(--fs))/1.25 Sora">A fox who is afraid of the dark</b></div><span class="ib s2" aria-label="Edit idea">${ic('annotate')}</span></div>
  <div class="saidby" style="margin-top:14px">${orb(L ? 'listening' : 'waiting', th, 30, true)}<div class="bubble">Who is the fox’s best friend?</div></div>
  ${L ? `<div class="bubble me" style="margin-top:10px;max-width:80%">a little owl called Pip</div>` : `<div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap"><span class="chip">An owl</span><span class="chip">A firefly</span><span class="chip">No friend yet</span></div>`}
  <div style="margin-top:18px"><span class="btn full intent" ${go('C32', 'create.draft')}>${ic('sketch')}That’s it, let’s make it</span></div>
  </div>${dock({ mode: L ? 'listening' : 'idle', cap: L ? 'Listening…' : 'Answer, or change your idea' })}`;
},
C32(st, th) {
  const np = st === 'noprovider';
  return `${status()}${topBar('The fox and the lantern', { backTo: 'C31', right: where('local') })}<div class="body">
  <div class="card" style="padding:14px"><div class="sm mut">Chapter 1 · my idea</div><b>A fox who is afraid of the dark meets Pip the owl.</b></div>
  <div class="card draft" style="margin-top:12px"><div class="row" style="justify-content:space-between"><span class="tag">${ic('ai_draft')}Draft · not saved</span><span class="sm mut">halo is writing</span></div>
    <p style="margin-top:10px;font-size:calc(16.5px*var(--fs))">Every evening, when the sun slipped behind the hill, Fen the fox hid under the big fern…</p><div class="shim" style="width:92%"></div><div class="shim" style="width:64%"></div></div>
  ${np ? `<div class="card s2" style="margin-top:12px;display:flex;gap:10px">${ic('unavailable')}<div class="sm"><b>Pictures need a helper that isn’t set up.</b><br>You can draw Fen yourself in Canvas, or ask a grown-up.</div></div>`
    : `<div class="row" style="gap:8px;margin-top:12px"><span class="chip">${ic('undo')}Undo</span><span class="chip">${ic('draw')}Draw in Canvas</span></div>`}
  </div>${dock({ mode: 'thinking', cap: 'Writing a draft… Stop keeps nothing' })}`;
},
C33(st, th) {
  const conf = st === 'confirm';
  return `${status()}${topBar('Keep this?', { backTo: 'C32', right: where('local') })}<div class="body">
  <div class="card" style="padding:14px"><div class="sm mut">My idea</div><b>A fox who is afraid of the dark meets Pip the owl.</b></div>
  <div class="card draft" style="margin-top:12px"><span class="tag">${ic('ai_draft')}Draft</span>
   <p style="margin-top:10px;font-size:calc(16.5px*var(--fs))">Every evening, when the sun slipped behind the hill, Fen the fox hid under the big fern. “Too dark,” he whispered. Then two round eyes blinked above him. “I’m Pip,” said the owl, “and I can see in the dark.”</p></div>
  <div class="grid2" style="grid-template-columns:1fr 1fr 1fr;margin-top:14px;gap:8px">
   <span class="btn" style="padding:0 8px" ${go('C34', 'create.keep(draft)')}>${ic('check')}Keep</span>
   <span class="btn ghost" style="padding:0 8px" ${go('C32', 'create.change')}>${ic('annotate')}Change</span>
   <span class="btn line" style="padding:0 8px" ${go('C33', 'confirm.discard')}>${ic('trash')}Discard</span></div>
  ${conf ? `<div class="scrim" style="margin:-84px -16px -112px"></div><div class="sheet" style="position:fixed;position:absolute;bottom:-112px;left:-16px;right:-16px"><div class="grab"></div><h2>Throw away this draft?</h2><p class="sub">Your idea stays. Only halo’s draft goes.</p>
     <div style="display:grid;gap:10px;margin-top:14px"><span class="btn full line" ${go('C32', 'create.discard')}>${ic('trash')}Throw it away</span><span class="btn full ghost" ${go('C33', 'confirm.cancel')}>Keep looking</span></div></div>` : ''}
  </div>${conf ? '' : dock({ cap: 'Say “keep it” or “change the ending”' })}`;
},
C34(st, th) {
  return `${status()}${topBar('My project', { backTo: 'K00', right: where('local') })}<div class="body">
  <div class="card" style="display:flex;gap:14px;align-items:center"><div style="width:84px;flex:none">${ILL.fox(th)}</div><div><b style="font:600 calc(18px*var(--fs))/1.25 Sora">The fox and the lantern</b><div class="sm mut" style="margin-top:4px">${ic('notebook', '').replace('class="ic', 'style="width:16px;height:16px;vertical-align:-3px" class="ic')} Saved in My notes · Stories</div><div class="sm mut">Chapter 1 of ?</div></div></div>
  <div class="lbl">Next</div><div class="card s2"><b>What happens in chapter 2?</b><div class="sm mut">Pip shows Fen something in the forest.</div></div>
  <div style="display:grid;gap:10px;margin-top:16px"><span class="btn full intent" ${go('C32', 'create.resume')}>${ic('sketch')}Keep making</span>
  <span class="btn full line" ${go('G90', 'gate.request → share')}>${ic('privacy')}Show a grown-up</span></div>
  </div>${dock({ cap: 'Or tell me what happens next' })}`;
},
S40(st, th) {
  return `${status()}${topBar('Study', { back: false, right: `${where('local')}<span class="ib">${ic('more')}</span>` })}<div class="body">
  <div class="card intent" style="border-radius:24px;display:flex;align-items:center;gap:12px;padding:14px 14px 14px 18px" ${go('S41', 'learning.start(voice)')}><div style="flex:1"><b style="font:600 18px Sora">What do you want to learn?</b><div class="sm mut">Say it, or start from your own material</div></div><span class="mic" style="width:52px;height:52px;border-radius:50%;background:var(--fill);color:var(--on);display:grid;place-items:center">${ic('mic')}</span></div>
  <div class="lbl">Continue</div>
  ${[['Photosynthesis', 'explain', 'Biology notes.pdf · 2 cards due'], ['Spanish past tense', 'practise', 'From a conversation']].map(([t, s, m]) => `<div class="card" style="margin-bottom:8px;display:flex;gap:12px;align-items:center;padding:12px 14px" ${go('S42', 'learning.resume')}><div style="flex:1"><b>${t}</b><div class="sm mut">${m}</div><div style="margin-top:6px;transform:scale(.8);transform-origin:left">${path(s, false)}</div></div>${ic('chevron_right')}</div>`).join('')}
  <div class="card s2" style="display:flex;align-items:center;gap:12px;padding:12px 14px" ${go('S44', 'review.start')}>${ic('flashcard')}<div style="flex:1"><b>Ready to review: 6</b><div class="sm mut">About 4 minutes</div></div>${ic('chevron_right')}</div>
  <div class="lbl">Start from</div><div class="row" style="flex-wrap:wrap;gap:8px"><span class="chip">${ic('conversations')}A conversation</span><span class="chip">${ic('file_doc')}File</span><span class="chip">${ic('notebook')}Note</span><span class="chip">${ic('camera')}Homework photo</span></div>
  </div>${dock({ kids: false, field: 'Ask or start a lesson…' })}`;
},
S41(st, th) {
  return `${status()}${topBar('New lesson', { backTo: 'S40', right: where('local') })}<div class="body">
  <div class="lbl" style="margin-top:4px">Goal</div><div class="card" style="padding:14px"><b style="font:600 17px/1.3 Sora">Understand photosynthesis for Thursday’s test</b></div>
  <div class="lbl">Material</div><div class="row" style="flex-wrap:wrap;gap:8px"><span class="chip sel">${ic('file_doc')}Biology notes.pdf · p.12–18</span><span class="chip">${ic('add')}Add</span></div>
  <p class="sm mut" style="margin:8px 2px 0">halo explains from your pages first and says when it uses general knowledge.</p>
  <div class="lbl">Level</div><div class="row" style="gap:8px"><span class="chip">New to it</span><span class="chip sel">${ic('check')}Some basics</span><span class="chip">Confident</span></div>
  <div class="card s2" style="margin-top:14px;display:flex;align-items:center;gap:10px;padding:12px 14px" ${go('S43', 'sheet.open(advanced)')}>${ic('settings')}<div style="flex:1"><b class="sm">More options</b><div class="sm mut">Language · style · schedule</div></div>${ic('chevron_down')}</div>
  <div style="margin-top:16px"><span class="btn full intent" ${go('S42', 'learning.start(config)')}>${ic('study')}Start</span></div>
  </div>${dock({ kids: false, field: 'Or describe your goal…' })}`;
},
S42(st, th) {
  const pr = st === 'practice';
  return `${status()}${topBar('Photosynthesis', { backTo: 'S40', right: `<span class="ib" aria-label="Lesson details" ${go('S43', 'sheet.open')}>${ic('list_view')}</span>` })}<div class="body">
  ${crumb(pr ? 'practice' : 'explain')}${path(pr ? 'practice' : 'explain', false)}
  <div class="focus"><div class="ill">${ILL.leaf(th)}</div>
   ${pr ? `<div class="q" style="font-size:18px">In your own words: why does a plant in a dark cupboard stop making glucose?</div><div class="ladder" style="margin-top:12px">${['Remember', 'Hint', 'Worked step', 'Another way', 'Solution'].map((t, k) => `<div class="rung ${k === 0 ? 'now' : ''}" style="min-height:40px">${t}</div>`).join('')}</div>`
     : `<p>Plants capture <span class="hl">light energy</span> in chlorophyll and use it to turn carbon dioxide and water into glucose, releasing oxygen.</p>`}
   <div class="row" style="gap:6px;margin-top:12px;flex-wrap:wrap"><span class="chip src">${ic('source')}Biology notes p.14</span><span class="chip src">${ic('source')}p.15 diagram</span></div></div>
  <div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap"><span class="chip" ${go('S42', 'learning.branch(deeper)')}>${ic('connection')}Go deeper</span><span class="chip">${ic('card')}Example</span><span class="chip ${pr ? '' : 'intent'}" ${go('S42', 'learning.practice')}>${ic('practise')}Practice</span><span class="chip" ${go('S44', 'notebook.save')}>${ic('remember')}Save summary</span></div>
  </div>${dock({ kids: false, intent: pr, field: pr ? 'Type or say your answer…' : 'Ask about this…' })}`;
},
S43(st, th) {
  return SCREENS.S42('', th) + `<div class="scrim"></div><div class="sheet" style="height:560px"><div class="grab"></div><h2>Lesson details</h2><p class="sm mut" style="margin:0 0 10px">The lesson is paused while you’re here.</p>
  <div class="row" style="gap:6px;margin-bottom:12px"><span class="chip sel">Sources</span><span class="chip">Language</span><span class="chip">Schedule</span><span class="chip">Progress</span></div>
  ${[['file_doc', 'Biology notes.pdf', 'Pages 12–18 · read on this phone · used 9 times'], ['image', 'p.15 diagram', 'Image · read by on-device vision'], ['intelligence', 'General knowledge', 'Used once, marked in the lesson']].map(([i, t, s]) => `<div class="row" style="padding:12px 0;border-bottom:1px solid var(--line)">${ic(i)}<div style="flex:1"><b class="sm">${t}</b><div class="sm mut">${s}</div></div>${ic('chevron_right')}</div>`).join('')}
  <div style="margin-top:16px"><span class="btn full ghost" ${go('S42', 'sheet.close')}>Back to the lesson</span></div></div>`;
},
S44(st, th) {
  const rev = st === 'revealed';
  return `${status()}${topBar('Review', { backTo: 'S40', right: `<span class="sm mut" style="margin-right:8px">3 of 6</span>` })}<div class="body">
  <div class="focus" style="min-height:220px"><div class="crumb">Recall first</div><div class="q">What are the two stages of photosynthesis?</div>
   ${rev ? `<div class="card s2" style="margin-top:14px"><b>Light-dependent reactions</b> and <b>the Calvin cycle</b> (light-independent).</div><div class="row" style="margin-top:10px"><span class="chip src">${ic('source')}Biology notes p.16</span></div>` : `<div class="card s2" style="margin-top:14px;color:var(--muted)">Say or type your answer…</div>`}</div>
  ${rev ? `<div class="lbl">How did it go?</div><div class="grid2" style="grid-template-columns:repeat(3,1fr);gap:8px"><span class="btn ghost">Again</span><span class="btn ghost">Hard</span><span class="btn intent">Got it</span></div>`
    : `<div style="margin-top:14px"><span class="btn full intent" ${go('S44', 'review.reveal')}>${ic('read_mode')}Show answer</span></div>`}
  <div class="row" style="gap:8px;margin-top:14px;flex-wrap:wrap"><span class="chip">${ic('notebook')}Open note</span><span class="chip">${ic('canvas')}Open in Canvas</span><span class="chip" ${go('S42', 'learning.resume')}>${ic('study')}Continue lesson</span></div>
  </div>${dock({ kids: false, field: 'Answer…' })}`;
},
G90(st, th) {
  if (st === 'offline') return SCREENS.K12('', th).replace(where('local'), where('offline')) + `<div class="toast" style="top:92px">${ic('disconnected')}Offline — I can still help with what’s on this phone.</div>`;
  if (st === 'unavailable') return SCREENS.C32('noprovider', th);
  // consent (default)
  return SCREENS.H21('', th) + `<div class="scrim"></div><div class="sheet"><div class="grab"></div>
   <div style="display:flex;gap:12px;align-items:center">${orb('approval_required', th, 48, true)}<h2 style="margin:0">This needs the cloud</h2></div>
   <p style="margin:12px 0 6px">The phone can’t read exercise 4 well. A cloud helper could read it better.</p>
   <div class="card s2 sm" style="margin:8px 0 14px">${ic('cloud', '').replace('class="ic', 'style="width:16px;height:16px;vertical-align:-3px;color:var(--att)" class="ic')} The photo of exercise 4 would be sent to <b>your cloud provider</b>. A grown-up needs to say OK.</div>
   <div style="display:grid;gap:10px"><span class="btn full ghost" ${go('H21', 'consent.local')}>${ic('on_device')}Try on this phone again</span>
   <span class="btn full line" ${go('G90', 'gate.pin → consent')}>${ic('privacy')}Ask a grown-up</span>
   <span class="btn full line" style="box-shadow:none" ${go('H21', 'consent.notNow')}>Not now</span></div></div>`;
},
};
