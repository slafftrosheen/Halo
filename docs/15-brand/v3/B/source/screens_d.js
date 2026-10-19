/* halo — Package D: Kids mode (K01–K05) + voice & reading accessibility (VA01–VA03).
   Kids rules: talk-first, ≤3 choices, 56dp targets, 72dp mic, body ≥18sp, cloud off by default, adult routes behind the grown-up credential. */
const SCREENS_D = {}, STATES_D = {};
const korb = (s, th, z = 40) => `<img src="a/orb/halo_kids_orb_${s}_${th}.svg" style="width:${z}px;height:${z}px;display:block;flex:none" alt="">`;
const ktop = (title, o = {}) => `<div class="top">${o.back ? `<span class="ib back" aria-label="${t('k.back', 'Back to my learning space')}">${ic('back')}</span>` : ''}<h1 style="font-size:calc(23px*var(--fs))">${title}</h1>${o.right !== undefined ? o.right : `<span class="ib" aria-label="${t('k.grownUps', 'Grown-up space')}" style="opacity:.7">${ic('privacy')}</span>`}</div>`;
const kdock = (o = {}) => `<div class="kdock">${o.cap ? `<div class="cap">${o.cap}</div>` : ''}<span class="ib" aria-label="${t('k.type', 'Type instead')}">${ic('ask_mode')}</span>
  <span class="kmic ${o.live ? 'stop' : ''}" aria-label="${o.live ? t('k.stop', 'Stop') : t('k.talk', 'Talk to halo')}">${o.live === 'listen' ? '<i class="mic-live"></i><i class="mic-live r2"></i>' : ''}${ic(o.live ? 'stop' : 'mic')}</span>
  <span class="ib" aria-label="${t('k.whatSay', 'What can I say?')}">${ic('guide_me')}</span></div>`;
const kc = (i, label, o = {}) => `<div class="kchoice ${o.cls || ''}"><span class="pic">${ic(i)}</span><span class="grow">${label}${o.sub ? `<div class="xs mut" style="font-family:Atk;font-weight:400;margin-top:2px">${o.sub}</div>` : ''}</span></div>`;
const kbody = (inner, o = {}) => `<div class="body" style="bottom:${o.bottom || 118}px">${inner}</div>`;
const kwords = (txt, upto, cls = 'on') => txt.split(' ').map((w, i) => `<span class="kword ${i < upto ? 'done' : i === upto ? cls : 'todo'}">${w}</span>`).join(' ');

/* ---------- K01 Kids home + grown-up gate ---------- */
STATES_D.K01 = ['home', 'listening', 'gate', 'no-lock'];
SCREENS_D.K01 = (st, th) => {
  const home = `<div class="aura ${st === 'listening' ? 'listening' : 'idle'}"></div>${sb()}${ktop(t('k.hi', 'Hi there!'))}${kbody(`
    <div style="display:grid;place-items:center;margin:0 0 12px"><span class="orbwrap" style="width:96px;height:96px">${korb(st === 'listening' ? 'listening' : 'idle', th, 92)}</span>
    <div class="ttl" style="margin-top:12px;text-align:center">${st === 'listening' ? t('k.listening', 'I’m listening…') : t('k.today', 'What shall we do today?')}</div></div>
    ${st === 'listening' ? `<div class="card" style="text-align:center;font:500 calc(21px*var(--fs)) Sora">${kwords('Why is the sky blue', 4)}</div>` :
    `${kc('explain', t('k.ask', 'Ask a question'))}${kc('study', t('k.learn', 'Learn something'), { sub: t('k.resume', 'Keep going: volcanoes'), cls: 'guide' })}${kc('sketch', t('k.create', 'Make something'))}`}`)}
    ${kdock({ live: st === 'listening' ? 'listen' : false, cap: st === 'listening' ? '' : t('k.tapTalk', 'Tap and talk') })}`;
  if (st === 'gate') return `${home.replace('<div class="aura idle"></div>', '')}<div class="scrim in"></div><div class="dlg" style="text-align:center"><div style="display:grid;place-items:center;margin-bottom:8px">${korb('waiting', th, 56)}</div>
    <h2>${t('k.gateT', 'Ask a grown-up')}</h2><div class="sm mut">${t('k.gateB', 'This part is for grown-ups. They’ll unlock it with the phone’s screen lock.')}</div>
    <div class="col" style="margin-top:14px"><span class="btn full">${ic('privacy')}${t('k.gateGo', 'Grown-up: unlock')}</span><span class="btn ghost full">${t('k.gateBack', 'Go back')}</span></div></div>`;
  if (st === 'no-lock') return `${home.replace('<div class="aura idle"></div>', '')}<div class="scrim in"></div><div class="dlg" style="text-align:center"><div style="display:grid;place-items:center;margin-bottom:8px">${korb('permission_missing', th, 56)}</div>
    <h2>${t('k.noLockT', 'This phone needs a screen lock')}</h2><div class="sm mut">${t('k.noLockB', 'Grown-up settings stay locked until a PIN, pattern or fingerprint is set in Android settings.')}</div>
    <div class="col" style="margin-top:14px"><span class="btn ghost full">${t('common.ok', 'OK')}</span></div></div>`;
  return home;
};

/* ---------- K02 Kids learn journey ---------- */
STATES_D.K02 = ['topic', 'know', 'focus', 'branch', 'summary'];
SCREENS_D.K02 = (st, th) => {
  const head = (title) => `${sb()}${ktop(title, { back: true })}`;
  const path = (i) => `<div style="display:flex;gap:6px;margin:0 0 12px">${[0, 1, 2, 3, 4].map(k => `<i style="flex:1;height:8px;border-radius:4px;background:${k < i ? 'color-mix(in srgb,var(--ink) 70%,transparent)' : k === i ? 'var(--fill)' : 'var(--line)'}"></i>`).join('')}</div>`;
  if (st === 'topic') return `<div class="aura idle"></div>${head(t('k.learn', 'Learn something'))}${kbody(`<div class="ttl">${t('k.whatLearn', 'What would you like to learn?')}</div>
    ${kc('connection', 'Volcanoes', { cls: 'guide' })}${kc('study', 'Space and planets')}${kc('explain', 'How plants grow')}<div class="sm mut" style="text-align:center;margin-top:12px">${t('k.orSay', 'Or say anything you wonder about')}</div>`)}${kdock()}`;
  if (st === 'know') return `<div class="aura idle"></div>${head('Volcanoes')}${kbody(`${path(0)}<div class="ttl">${t('k.knowT', 'What do you already know?')}</div>
    ${kc('check', t('k.know1', 'They have hot lava'))}${kc('think', t('k.know2', 'I’m not sure yet'))}${kc('mic', t('k.know3', 'I’ll tell you'))}<div class="sm mut" style="text-align:center;margin-top:12px">${t('k.notYet', 'You can choose Not yet. We can start together.')}</div>`)}${kdock()}`;
  if (st === 'focus') return `<div class="aura idle"></div>${head('Volcanoes')}${kbody(`${path(1)}<div class="card" style="padding:0;overflow:hidden"><div style="height:120px;background:linear-gradient(160deg,#ff9f6b,#c4452b 55%,#3b1c25)"></div>
    <div style="padding:14px 16px;font-size:calc(20px*var(--fs));line-height:1.5">${kwords('Deep under the ground it is so hot that rock melts. That melted rock is called magma.', 7)}</div></div>
    <div class="row" style="gap:10px;margin-top:12px"><span class="btn ghost" style="flex:1">${t('k.again', 'Say it again')}</span><span class="btn" style="flex:1">${t('k.next', 'Next')}</span></div>`, { bottom: 186 })}
    <div class="rbar" style="bottom:112px"><span class="ib">${ic('pause')}</span><span class="prog"><i style="width:45%"></i></span><span class="xs b" style="margin:0 6px">${t('k.reading', 'Reading')}</span><span class="ib">${ic('stop')}</span></div>${kdock()}`;
  if (st === 'branch') return `<div class="aura idle"></div>${head('Volcanoes')}${kbody(`${path(3)}<div class="ttl">${t('k.branchT', 'Where shall we go next?')}</div>
    ${kc('connection', 'Why do volcanoes erupt?', { cls: 'guide' })}${kc('image', 'Famous volcanoes')}${kc('history', 'Can I visit one?')}`)}${kdock()}`;
  return `<div class="aura success"></div>${head('Volcanoes')}${kbody(`${path(5)}<div style="display:grid;place-items:center">${korb('success', th, 88)}</div><div class="ttl" style="text-align:center;margin-top:10px">${t('k.learnedT', 'What I learned')}</div>
    <div class="card"><div class="sm" style="line-height:1.7">🌋 ${t('k.l1', 'Magma is melted rock')}<br>💨 ${t('k.l2', 'Gas pushes it up')}<br>🗺️ ${t('k.l3', 'Iceland has lots of volcanoes')}</div></div>
    <div class="row" style="gap:10px;margin-top:12px"><span class="btn ghost" style="flex:1">${t('k.saveIt', 'Save it')}</span><span class="btn" style="flex:1">${t('k.more', 'Learn more')}</span></div>`)}${kdock()}`;
};

/* ---------- K03 Homework ---------- */
STATES_D.K03 = ['consent', 'preview', 'check', 'exercise', 'done'];
SCREENS_D.K03 = (st, th) => {
  const head = `${sb()}${ktop(t('k.homework', 'Homework'), { back: true })}`;
  if (st === 'consent') return `${head}${kbody(`<div style="display:grid;place-items:center;margin:10px 0">${korb('waiting', th, 80)}</div><div class="ttl" style="text-align:center">${t('k.camT', 'Can halo use the camera?')}</div>
    <div class="sm mut" style="text-align:center">${t('k.camB', 'A grown-up needs to say yes once. Photos stay on this phone.')}</div>
    <div class="col" style="margin-top:18px"><span class="btn full">${t('k.camAsk', 'Ask a grown-up')}</span><span class="btn ghost full">${t('k.typeIt', 'Type the question instead')}</span></div>`)}`;
  if (st === 'preview') return `<div class="cam"></div>${sb()}<div class="body nonav" style="top:60px"><div class="photo" style="height:360px;border-radius:24px"></div><div class="sm" style="text-align:center;color:#fff;margin-top:12px">${t('k.holdStill', 'Hold still… got it!')}</div></div>
    <div style="position:absolute;left:16px;right:16px;bottom:24px;display:flex;gap:10px;z-index:3"><span class="btn ghost" style="flex:1">${t('k.retake', 'Take again')}</span><span class="btn" style="flex:1">${t('k.useIt', 'Use this')}</span></div>`;
  if (st === 'check') return `${head}${kbody(`<div class="ttl">${t('k.checkT', 'Is this right?')}</div><div class="card" style="font-size:calc(20px*var(--fs));line-height:1.6">3. A box is 12 cm long and <span class="unsure">7?</span> cm wide. How big is it?</div>
    <div class="banner att" style="margin-top:12px">${ic('unavailable')}<span class="sm">${t('k.unsure', 'halo isn’t sure about the orange part. Tap it to fix it.')}</span></div>
    <div class="row" style="gap:10px;margin-top:14px"><span class="btn ghost" style="flex:1">${t('k.fix', 'Fix it')}</span><span class="btn" style="flex:1">${t('k.right', 'It’s right')}</span></div>`)}`;
  if (st === 'exercise') return `<div class="aura idle"></div>${head}${kbody(`<div class="xs b" style="color:var(--ink);letter-spacing:.08em;text-transform:uppercase">${t('k.ex', 'Exercise 3 of 5')}</div><div class="card" style="margin-top:8px;font-size:calc(20px*var(--fs))">A box is 12 cm long and 7 cm wide. How big is it?</div>
    <div class="banner info" style="margin-top:12px">${ic('guide_me')}<span class="sm">${t('k.hint', 'Hint: count the squares in one row, then how many rows.')}</span></div>
    <div style="display:flex;gap:8px;margin-top:12px">${[['guide_me', t('k.h1', 'Hint')], ['explain', t('k.h2', 'Show me')], ['review', t('k.h3', 'Answer')]].map(([i, l], j) => `<div class="card s2" style="flex:1;min-height:76px;padding:10px 4px;text-align:center;${j === 0 ? 'background:color-mix(in srgb,var(--fill) 22%,var(--s1))' : ''}"><div style="display:grid;place-items:center;color:var(--ink)">${ic(i)}</div><div class="xs b" style="margin-top:4px">${l}</div></div>`).join('')}</div>`)}${kdock({ cap: t('k.sayAnswer', 'Say your answer') })}`;
  return `<div class="aura success"></div>${head}${kbody(`<div style="display:grid;place-items:center;margin-top:10px">${korb('success', th, 92)}</div><div class="ttl" style="text-align:center;margin-top:10px">${t('k.doneT', 'You did all 5!')}</div>
    <div class="sm mut" style="text-align:center">${t('k.doneB', '4 on your own, 1 with a hint. Saved to My learning.')}</div><span class="btn full" style="margin-top:18px">${t('k.doneBtn', 'Done')}</span>`)}`;
};

/* ---------- K04 Create ---------- */
STATES_D.K04 = ['choose', 'shape', 'making', 'review', 'project'];
SCREENS_D.K04 = (st, th) => {
  const head = (title) => `${sb()}${ktop(title, { back: true })}`;
  if (st === 'choose') return `<div class="aura idle"></div>${head(t('k.create', 'Make something'))}${kbody(`<div class="ttl">${t('k.makeT', 'What shall we make?')}</div>${kc('read_mode', t('k.story', 'A story'), { cls: 'guide' })}${kc('sketch', t('k.character', 'A character'))}${kc('shapes', t('k.project', 'A project'))}`)}${kdock()}`;
  if (st === 'shape') return `<div class="aura idle"></div>${head(t('k.story', 'A story'))}${kbody(`<div class="ttl">${t('k.whoT', 'Who is the story about?')}</div>${kc('favorite', 'A brave fox')}${kc('robot', 'A tiny robot')}${kc('mic', t('k.myIdea', 'My own idea'))}
    <div class="card s2" style="margin-top:12px"><div class="xs mut">${t('k.myStory', 'My story so far')}</div><div class="sm">${t('k.storySoFar', 'In a snowy forest…')}</div></div>`)}${kdock()}`;
  if (st === 'making') return `<div class="aura thinking"></div>${head(t('k.story', 'A story'))}${kbody(`<div style="display:grid;place-items:center;margin:20px 0 10px">${korb('thinking', th, 84)}</div><div class="ttl" style="text-align:center">${t('k.makingT', 'Making your story…')}</div>
    <div class="card" style="border:2px dashed var(--ink);box-shadow:none"><div class="shim" style="width:90%"></div><div class="shim" style="width:70%"></div><div class="shim" style="width:80%"></div></div><span class="btn ghost full" style="margin-top:14px">${t('k.stop', 'Stop')}</span>`)}`;
  if (st === 'review') return `${head(t('k.story', 'A story'))}${kbody(`<div class="card rise" style="border:2px dashed var(--ink);background:color-mix(in srgb,var(--fill) 8%,var(--s1));box-shadow:none">
    <div class="tag" style="margin-bottom:8px">${t('k.draft', 'Not saved yet')}</div><div style="font:600 calc(20px*var(--fs)) Sora">Fox and the Lost Star</div>
    <div class="sm" style="margin-top:6px;line-height:1.6">${t('k.storyText', 'In a snowy forest lived a brave fox called Pip. One night a little star fell into the snow…')}</div></div>
    <div class="sm mut" style="margin:10px 2px">${t('k.reviewHint', 'Change anything you like, then choose Keep to save it.')}</div>
    <div style="display:flex;gap:8px"><span class="btn ghost" style="flex:1">${t('k.discard', 'Discard')}</span><span class="btn ghost" style="flex:1">${t('k.change', 'Change')}</span><span class="btn" style="flex:1">${t('k.keep', 'Keep')}</span></div>`)}`;
  return `<div class="aura success"></div>${head(t('k.myProject', 'My project'))}${kbody(`<div class="card" style="padding:0;overflow:hidden"><div style="height:130px;background:linear-gradient(160deg,#1b2a5a,#3c2f7a 60%,#ffd27a)"></div><div style="padding:14px 16px"><div style="font:600 calc(20px*var(--fs)) Sora">Fox and the Lost Star</div><div class="xs mut">${t('k.pages', '4 pages · saved')}</div></div></div>
    ${kc('read_mode', t('k.readStory', 'Read my story'), { cls: 'guide' })}${kc('add', t('k.keepGoing', 'Keep going'))}${kc('privacy', t('k.share', 'Share · ask a grown-up'))}`)}`;
};

/* ---------- K05 Story reader ---------- */
STATES_D.K05 = ['listen', 'read-myself', 'heard', 'big-text', 'no-voice'];
SCREENS_D.K05 = (st, th) => {
  const head = `${sb()}${ktop('Fox and the Lost Star', { back: true, right: `<span class="xs mut" style="margin-right:10px">2 / 4</span>` })}`;
  const txt = 'One night a little star fell into the snow. Pip ran to help it.';
  const fs = st === 'big-text' ? 26 : 22, lh = st === 'big-text' ? 1.9 : 1.6;
  const pg = (inner) => `<div class="card" style="padding:0;overflow:hidden"><div style="height:${st === 'big-text' ? 90 : 150}px;background:linear-gradient(160deg,#1b2a5a,#3c2f7a 60%,#ffd27a)"></div><div style="padding:16px;font:500 calc(${fs}px*var(--fs))/${lh} Atk;letter-spacing:${st === 'big-text' ? '.03em' : '0'};word-spacing:${st === 'big-text' ? '.12em' : '0'}">${inner}</div></div>`;
  const ctrls = (a, b) => `<div style="position:absolute;left:16px;right:16px;bottom:18px;display:flex;gap:10px;z-index:3">${a}${b}</div>`;
  if (st === 'listen') return `<div class="aura idle"></div>${head}${kbody(pg(kwords(txt, 5)), { bottom: 160 })}<div class="rbar" style="bottom:88px"><span class="ib">${ic('pause')}</span><span class="prog"><i style="width:40%"></i></span><span class="ib">${ic('chevron_down')}</span><span class="ib">${ic('stop')}</span></div>
    ${ctrls(`<span class="btn ghost" style="flex:1">${ic('back')}</span>`, `<span class="btn ghost" style="flex:2">${t('k.readMyself', 'Read it myself')}</span><span class="btn" style="flex:1">${ic('chevron_down').replace('<svg', '<svg style="transform:rotate(-90deg)"')}</span>`)}`;
  if (st === 'read-myself') return `<div class="aura listening"></div>${head}${kbody(pg(kwords(txt, 4, 'on')), { bottom: 120 })}${kdock({ live: 'listen', cap: t('k.youRead', 'You read, halo listens') })}`;
  if (st === 'heard') return `${head}${kbody(`${pg(txt)}<div class="card s2" style="margin-top:12px"><div class="b sm">${t('k.heardT', 'What halo heard')}</div><div class="sm" style="margin-top:4px">“One night a little star fell in the snow. Pip ran to help it.”</div>
    <div class="xs mut" style="margin-top:6px">${t('k.noScore', 'No score — just for you to compare.')}</div></div>`, { bottom: 90 })}
    ${ctrls(`<span class="btn ghost" style="flex:1">${t('k.tryAgain', 'Read again')}</span>`, `<span class="btn" style="flex:1">${t('k.nextPage', 'Next page')}</span>`)}`;
  if (st === 'big-text') return `${head}${kbody(pg(txt), { bottom: 90 })}${ctrls(`<span class="btn sel ghost" style="flex:1;box-shadow:inset 0 0 0 2px var(--ink)">Aa ${t('k.larger', 'Larger text')}</span>`, `<span class="btn ghost" style="flex:1">${t('k.spacing', 'More spacing')}</span>`)}`;
  return `${head}${kbody(`${pg(txt)}<div class="banner att" style="margin-top:12px">${ic('speaker')}<span class="sm">${t('k.noVoice', 'The reading voice isn’t ready on this phone. Read it together with a grown-up, or read it yourself.')}</span></div>`, { bottom: 90 })}
    ${ctrls(`<span class="btn" style="flex:1">${t('k.readMyself', 'Read it myself')}</span>`, '')}`;
};

/* ---------- VA01 Read-aloud bar (global, every screen) ---------- */
STATES_D.VA01 = ['playing', 'follow', 'options', 'route'];
SCREENS_D.VA01 = (st, th) => {
  const page = `<div class="aura idle"></div>${sb()}${edTop('Trip to Kuldīga')}<div class="body nonav" style="bottom:150px">${saved()}<div class="ttl">Trip to Kuldīga</div>
    <p style="margin:10px 0;line-height:1.6">${st === 'follow' ? `Leave at 9:00. <span style="background:color-mix(in srgb,var(--fill) 32%,transparent);border-radius:4px;padding:0 3px">Stop at the waterfall first, then lunch in the old town.</span> Bring rain jackets.` : 'Leave at 9:00. Stop at the waterfall first, then lunch in the old town. Bring rain jackets.'}</p>${lines(5)}</div>`;
  const bar = `<div class="rbar" style="bottom:20px;height:64px"><span class="ib" aria-label="${t('va.prev', 'Previous sentence')}">${ic('undo')}</span><span class="ib" aria-label="${t('va.pause', 'Pause')}" style="background:var(--fill);color:var(--on)">${ic('pause')}</span><span class="ib" aria-label="${t('va.next', 'Next sentence')}">${ic('redo')}</span>
    <span class="prog"><i style="width:38%"></i></span><span class="chip" style="height:32px;padding:0 10px">1.0×</span><span class="ib" aria-label="${t('va.stop', 'Stop')}">${ic('stop')}</span></div>`;
  if (st === 'options') return `${page}${bar}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('va.optT', 'Reading')}</h2>
    <div class="lbl">${t('va.speed', 'Speed')}</div><div class="row" style="gap:6px">${['0.75×', '1.0×', '1.25×', '1.5×'].map((x, i) => `<span class="chip ${i === 1 ? 'sel' : 'line'}">${x}</span>`).join('')}</div>
    <div class="lbl">${t('va.voice', 'Voice')}</div>${srow('voice', 'Aria', t('va.voiceSub', 'English · on this phone'), { right: `<span class="ib" style="width:40px;height:40px">${ic('play')}</span>` })}
    <div class="row" style="margin-top:8px">${ic('read_mode')}<span class="sm grow">${t('va.follow', 'Highlight as it reads')}</span>${sw(true)}</div></div>`;
  if (st === 'route') return `${page}${bar}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('va.routeT', 'Play on')}</h2>
    <div class="card list" style="padding:0 14px">${srow('phone', t('va.phone', 'This phone'), t('va.phoneSub', 'Speaker'), { right: '' })}${srow('headphones', 'Pixel Buds', t('va.connected', 'Connected'), { right: `<span style="color:var(--ink)">${ic('check')}</span>` })}${srow('glasses', 'HeyCyan glasses', t('va.notConnected', 'Not connected'), { right: '' })}</div>
    <div class="xs mut" style="margin-top:8px">${t('va.routeNote', 'If the sound stops, halo pauses and shows where it tried to play.')}</div></div>`;
  return `${page}${bar}`;
};

/* ---------- VA02 Voice can't be used right now — always a way forward ---------- */
STATES_D.VA02 = ['mic-busy', 'no-listening', 'no-reading', 'secret-field'];
SCREENS_D.VA02 = (st, th) => {
  if (st === 'secret-field') return `${sb()}${topBar(t('va.signIn', 'Connect Spotify'), { back: true })}<div class="body nonav"><div class="lbl">${t('va.password', 'Password')}</div>
    <div class="field focus">${ic('privacy')}<span class="grow">••••••••</span><span class="ib s2" style="width:40px;height:40px;opacity:.35">${ic('mic')}</span></div>
    <div class="help">${t('va.noDictate', 'Voice typing is off for passwords so nobody nearby hears them.')}</div><span class="btn full" style="margin-top:16px">${t('va.connect', 'Connect')}</span></div>`;
  const msg = { 'mic-busy': [t('va.micT', 'The microphone is busy'), t('va.micB', 'Another app is using it, maybe a call. Type instead, or try again when it’s free.'), t('va.typeInstead', 'Type instead'), 'permission_missing'],
    'no-listening': [t('va.listenT', 'Listening isn’t set up yet'), t('va.listenB', 'halo needs a listening pack for English (about 150 MB, stays on this phone).'), t('va.getPack', 'Download listening pack'), 'model_loading'],
    'no-reading': [t('va.readT', 'Reading aloud isn’t ready'), t('va.readB', 'Choose a voice to hear answers. Until then, everything is shown as text.'), t('va.chooseVoice', 'Choose a voice'), 'permission_missing'] }[st];
  return `<div class="aura needs"></div>${sb()}${homeTop(th)}<div class="body">${userMsg('What’s the weather tomorrow?')}${haloMsg(th, '<p>Cloudy, 9° to 13°, light rain after 15:00.</p>')}</div>
    <div class="sheet in" style="bottom:0"><div class="grab"></div><div class="row">${orb(msg[3], th, 34)}<h2 style="margin:0">${msg[0]}</h2></div><div class="sm mut" style="margin:6px 0 4px">${msg[1]}</div>
    <div class="btns"><span class="btn text sm">${t('next.notNow', 'Not now')}</span><span class="btn">${msg[2]}</span></div></div>`;
};

/* ---------- VA03 Screen reader order + captions (annotated) ---------- */
STATES_D.VA03 = ['home-order', 'kids-order', 'captions'];
SCREENS_D.VA03 = (st, th) => {
  if (st === 'captions') return `<div class="aura listening"></div>${sb()}${homeTop(th)}<div class="body">${userMsg('Read my notification from Anna')}${haloMsg(th, `<p>Anna says: “Are we still on for Sunday? I can bring dessert.”</p>`, 'speaking')}</div>
    <div style="position:absolute;left:12px;right:12px;bottom:200px;z-index:4;border-radius:16px;padding:10px 14px;background:rgba(0,0,0,.78);color:#fff;font-size:calc(17px*var(--fs))">${kwords('Anna says: Are we still on for Sunday? I can bring dessert.', 6)}</div>${composer({ mode: 'busy' })}${nav('home')}`;
  if (st === 'kids-order') return SCREENS_D.K01('home', th);
  return decorate('H01', 'conversation', th, SCREENS_B.H01('conversation', th));
};
/* Screen-reader focus order, drawn after render from real element positions. */
const FOCUS_ORDER = { 'VA03_home-order': ['.top .wm', '.top .ib', '.msg.me .bub', '.ans', '.comp .in .ph-t', '.comp .go', '.comp .bar .pill', '.nav .it.on'],
  'VA03_kids-order': ['.top h1', '.kchoice@0', '.kchoice@1', '.kchoice@2', '.kmic', '.kdock .ib:first-child', '.kdock .ib:last-child', '.top .ib'] };
function drawFocus(root, id, st) {
  const sel = FOCUS_ORDER[id + '_' + st]; if (!sel) return;
  const pr = root.getBoundingClientRect(); let n = 1;
  const lay = document.createElement('div'); lay.style.cssText = 'position:absolute;inset:0;z-index:20;pointer-events:none'; root.appendChild(lay);
  sel.forEach(q => { const [qq, ix] = q.split('@'); const e = ix ? root.querySelectorAll(qq)[+ix] : root.querySelector(qq); if (!e) return; const r = e.getBoundingClientRect();
    const d = document.createElement('div'); d.className = 'focusring'; d.style.cssText = `position:absolute;left:${r.left - pr.left - 3}px;top:${r.top - pr.top - 3}px;width:${r.width + 6}px;height:${r.height + 6}px`;
    d.innerHTML = `<span class="fnum">${n++}</span>`; lay.appendChild(d); });
}
window.drawFocus = drawFocus;
window.SCREENS_D = SCREENS_D; window.STATES_D = STATES_D;
