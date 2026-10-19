/* halo — Package B screens (Home · tasks · notifications). One renderer per design ID; st = state.
   Every visible string goes through t(key, en) → collected into strings/en.json (translation-ready keys). */
const IC = window.HALO_ICONS || {};
const STR = window.STR = window.STR || {};
const t = (k, en) => { STR[k] = en; return en; };
const ic = (n, cls = '') => `<span class="ic ${cls}">${IC[n] || IC['more'] || ''}</span>`;
const orb = (s, th, z = 26) => `<img src="a/orb/halo_orb_${s}_${th}.svg" style="width:${z}px;height:${z}px;display:block;flex:none" alt="">`;
const sb = () => `<div class="sb"><span>9:41</span><span>●●● <i></i></span></div>`;
const where = (k) => {
  const m = { phone: ['on_device', t('where.phone', 'On this phone')], lan: ['lan', t('where.lan', 'Your network')], cloud: ['cloud', t('where.cloud', 'Cloud · you chose it')],
    offline: ['disconnected', t('where.offline', 'Offline')], none: ['unavailable', t('where.none', 'Not set up')] }[k];
  return `<span class="where ${k === 'none' ? 'att' : k === 'offline' ? 'err' : ''}">${ic(m[0])}${m[1]}</span>`;
};
const NAV = [['home', 'nav.home', 'Home'], ['activity', 'nav.activity', 'Activity'], ['notebook', 'nav.notebook', 'Notebook'], ['devices', 'nav.devices', 'Devices'], ['settings', 'nav.settings', 'Settings']];
const nav = (on = 'home', badge = '') => `<div class="nav" role="navigation">${NAV.map(([i, k, en]) => `<div class="it ${i === on ? 'on' : ''}" data-go="${i}">${ic(i)}${t(k, en)}${badge === i ? '<span class="badge"></span>' : ''}</div>`).join('')}</div>`;
const topBar = (title, o = {}) => `<div class="top">${o.back ? `<span class="ib back" aria-label="${t('a11y.back', 'Back')}" data-go="back">${ic('back')}</span>` : ''}<h1>${title}</h1>${o.right || ''}</div>`;
const homeTop = (th, o = {}) => `<div class="top"><img class="wm" src="a/wm/halo_wordmark_${th}.svg" alt="halo"><span class="grow"></span>${o.where !== undefined ? o.where : where('phone')}
  <span class="ib" aria-label="${t('home.updates', 'Updates')}" data-go="N01">${ic('notifications')}${o.badge === false ? '' : '<span class="badge"></span>'}</span></div>`;

/* ---------- composer (H02) ---------- */
function composer(o = {}) {
  // o.mode: idle|typing|listening|busy|disabled ; o.n sources ; o.text ; o.nonav ; o.modeLabel
  const m = o.mode || 'idle';
  let main;
  if (m === 'listening') main = `<div class="wave" aria-label="${t('comp.listening', 'Listening…')}">${[8, 14, 20, 11, 22, 16, 9, 18, 12, 21, 7, 15, 19, 10].map(h => `<i style="height:${h}px"></i>`).join('')}</div><span class="sm mut">${t('comp.listening', 'Listening…')}</span>`;
  else if (m === 'typing') main = `<span class="typed">${o.text || 'Plan dinner for four, nothing spicy'}<i></i></span>`;
  else if (m === 'disabled') main = `<span class="ph-t">${t('comp.noProvider', 'Set up how halo answers first')}</span>`;
  else if (o.ghostText) main = `<span class="ph-t ghost">${o.ghostText}<kbd>${t('comp.tabHint', 'tap ↗')}</kbd></span>`;
  else main = `<span class="ph-t">${o.ph || t('comp.placeholder', 'Ask halo…')}</span>`;
  const btn = m === 'typing' ? `<span class="go" aria-label="${t('comp.send', 'Send')}" data-ev="composer.send">${ic('send')}</span>`
    : m === 'listening' ? `<span class="go stop" aria-label="${t('comp.stopListening', 'Stop listening')}" data-ev="voice.stop"><i class="mic-live"></i><i class="mic-live r2"></i>${ic('stop')}</span>`
    : m === 'busy' ? `<span class="go stop" aria-label="${t('comp.stop', 'Stop')}" data-ev="runtime.stop">${ic('stop')}</span>`
    : m === 'disabled' ? `<span class="go ${o.guide ? 'guide' : ''}" aria-label="${t('comp.setUp', 'Set up answers')}" data-go="I01" style="width:auto;padding:0 14px;border-radius:24px;font:700 13.5px Atk">${t('comp.setUp', 'Set up answers')}</span>`
    : `<span class="go" aria-label="${t('comp.talk', 'Talk to halo')}" data-ev="voice.open">${ic('mic')}</span>`;
  const n = o.n || 0;
  return `<div class="comp ${o.nonav ? 'nonav' : ''}" style="${o.style || ''}">
 ${o.suggest ? `<div class="suggest">${o.suggest.map((x, i) => `<span class="chip rise d${i + 1}">${ic(i ? (i === 1 ? 'timer' : 'send') : 'new_note')}${x}</span>`).join('')}</div>` : ''}
   ${o.chips ? `<div class="row" style="gap:6px;padding:4px 4px 6px;flex-wrap:wrap">${o.chips}</div>` : ''}
   <div class="in">${main}${btn}</div>
   <div class="bar"><span class="pill" data-go="H03">${ic('attach')}<span class="hide-big">${t('comp.attach', 'Attach')}</span>${n ? `<span class="n">${n}</span>` : ''}</span>
     <span class="pill" data-go="H04">${ic('tool')}<span class="hide-big">${t('comp.tools', 'Tools')}</span></span><span class="sp"></span>
     <span class="pill" data-go="H02:mode">${ic('think_with_halo')}${o.modeLabel || t('mode.auto', 'Automatic')}${ic('chevron_down')}</span></div></div>`;
}
const userMsg = (txt) => `<div class="msg me"><div class="bub">${txt}</div></div>`;
const haloMsg = (th, inner, s = 'idle') => `<div class="msg"><div class="who">${orb(s, th, 26)}</div><div class="ans">${inner}</div></div>`;
const ansActs = () => `<div class="acts"><span class="ib" aria-label="${t('ans.read', 'Read aloud')}">${ic('speaker')}</span><span class="ib" aria-label="${t('ans.copy', 'Copy reply')}">${ic('card')}</span><span class="ib" aria-label="${t('ans.save', 'Save to Notebook')}">${ic('new_note')}</span><span class="ib" aria-label="${t('ans.retry', 'Try again')}">${ic('retry')}</span></div>`;

const SCREENS_B = {};
const STATES_B = {};

/* ---------- H01 Home conversation feed ---------- */
STATES_B.H01 = ['empty', 'conversation', 'ahead', 'live', 'earlier', 'edit', 'ime'];
SCREENS_B.H01 = (st, th) => {
  if (st === 'empty') return `${sb()}${homeTop(th)}<div class="body">
    <div style="display:flex;flex-direction:column;align-items:center;margin:18px 0 18px">${orb('idle', th, 92)}
    <div class="ttl" style="margin-top:14px;text-align:center">${t('home.greet', 'Good evening, Anna')}</div>
    <div class="sub" style="text-align:center">${t('home.greetSub', 'Think it through, find the words, or learn something new.')}</div></div>
    <div class="card guide" data-go="H06"><div class="row">${ic('guide_me')}<div class="grow"><div class="t2">${t('next.title1', 'Finish setting up voice')}</div>
      <div class="sm mut">${t('next.why1', 'So you can ask hands-free. Takes about a minute.')}</div></div></div>
      <div class="btns" style="margin-top:10px"><span class="btn text sm">${t('next.notNow', 'Not now')}</span><span class="btn sm">${t('next.do1', 'Set up voice')}</span></div></div>
    <div class="lbl">${t('home.starters', 'Starting points')}</div>
    <div class="row" style="flex-wrap:wrap;gap:8px"><span class="chip">${ic('task')}${t('home.s.plan', 'Make a plan')}</span><span class="chip">${ic('explain')}${t('home.s.words', 'Find the words')}</span><span class="chip">${ic('study')}${t('home.s.learn', 'Learn something')}</span></div>
    </div>${composer()}${nav('home')}`;
  if (st === 'ime') return `${sb()}${homeTop(th)}<div class="body" style="bottom:390px">
    ${userMsg('What’s the weather in Riga tomorrow?')}${haloMsg(th, `<p>Cloudy, 9° to 13°, light rain after 15:00.</p><div class="src"><span>${ic('cloud')}Weather · you allowed it</span></div>`)}
    </div>${composer({ mode: 'typing', text: 'And on Saturday?', nonav: true, style: 'bottom:282px' })}<div class="kbd">${[10, 9, 7, 4].map(n => `<div class="kr">${'<i></i>'.repeat(n)}</div>`).join('')}</div>`;
  const feed = `${st === 'earlier' ? '' : `<div class="earlier" data-ev="feed.expand">${ic('history')}${t('feed.earlier', 'Show 6 earlier messages')}</div>`}
    ${st === 'earlier' ? `${userMsg('Remind me what we planned for Sunday')}${haloMsg(th, `<p>Lunch at 13:00 with your parents, then the market.</p>`)}
      ${userMsg('Add flowers to the list')}${haloMsg(th, `<p>Added “flowers” to your Sunday list.</p><div class="src"><span>${ic('check')}${t('receipt.saved', 'Saved')}</span></div>`)}` : ''}
    ${userMsg('Plan dinner for four, nothing spicy')}
    ${haloMsg(th, `<p><b>Lemon chicken traybake</b> with potatoes and green beans. 50 minutes, one tray.</p><p class="sm mut">Want the shopping list in your Notebook?</p>
      <div class="src"><span>${ic('on_device')}${t('where.phone', 'On this phone')}</span></div>${ansActs()}`)}`;
  if (st === 'edit') return `${sb()}${homeTop(th)}<div class="body">${feed}</div>${composer()}${nav('home')}
    <div class="scrim"></div><div class="menu" style="right:16px;top:${300}px">
     <div class="mi">${ic('undo')}<span>${t('feed.useEarlier', 'Use earlier question')}<small>${t('feed.useEarlierSub', 'Copies it into the message box. Nothing is sent yet.')}</small></span></div>
     <div class="mi">${ic('card')}${t('feed.copy', 'Copy')}</div><div class="mi">${ic('speaker')}${t('ans.read', 'Read aloud')}</div></div>
    <div class="msg me" style="position:absolute;right:16px;top:244px"><div class="bub" style="box-shadow:0 0 0 2px var(--ink)">Plan dinner for four, nothing spicy</div></div>`;
  if (st === 'ahead') return `${sb()}${homeTop(th)}<div class="body">${feed}</div>${composer({ suggest: [t('ahead.list', 'Add the shopping list to Notebook'), t('ahead.timer', 'Set a 50-minute timer'), t('ahead.share', 'Send to Anna')], ghostText: t('ahead.ghost', 'Add the shopping list to Notebook') })}${nav('home')}`;
  if (st === 'live') return `${sb()}${homeTop(th)}<div class="body">${feed}</div>${composer()}<div class="live rise" data-go="A02"><span class="orbwrap" style="width:24px;height:24px">${orb('acting_phone', th, 22)}</span><span>${t('live.sending', 'Sending photos to Anna')}</span><span class="pr"><i style="width:66%"></i></span><span class="x" aria-label="${t('comp.stop', 'Stop')}">${ic('stop')}</span></div>${nav('home')}`;
  return `${sb()}${homeTop(th)}<div class="body">${feed}</div>${composer()}${nav('home')}`;
};

/* ---------- H02 composer states ---------- */
STATES_B.H02 = ['idle', 'typing', 'listening', 'busy', 'mode', 'disabled'];
SCREENS_B.H02 = (st, th) => {
  const head = `${sb()}${homeTop(th, st === 'disabled' ? { where: where('none') } : {})}`;
  const short = `<div class="body">${userMsg('What’s on my calendar tomorrow?')}${st === 'busy' ? haloMsg(th, `<div class="row sm mut">${t('state.thinking', 'Thinking…')}</div><div class="shim" style="width:90%"></div><div class="shim" style="width:64%"></div>`, 'thinking') : haloMsg(th, `<p>Two things: dentist at 10:30 and Mārtiņš’ call at 16:00.</p><div class="src"><span>${ic('calendar')}Calendar</span></div>`)}</div>`;
  if (st === 'disabled') return `${head}<div class="body"><div class="empty">${orb('permission_missing', th, 84)}<div class="t2">${t('home.noProvider', 'halo can’t answer yet')}</div>
    <div class="sm mut">${t('home.noProviderSub', 'Choose where answers happen — on this phone, on your network, or a cloud service you pick.')}</div></div></div>${composer({ mode: 'disabled', guide: true })}${nav('home')}`;
  if (st === 'mode') return `${head}${short}${composer()}${nav('home')}<div class="scrim"></div>
    <div class="menu" style="right:12px;bottom:150px;width:290px"><div class="lbl" style="margin:8px 12px 4px">${t('mode.title', 'How halo helps')}</div>
    <div class="mi sel">${ic('think_with_halo')}<span class="grow">${t('mode.auto', 'Automatic')}<small>${t('mode.autoSub', 'halo picks the right way for each request')}</small></span>${ic('check')}</div>
    <div class="mi">${ic('ask_mode')}<span>${t('mode.ask', 'Ask')}<small>${t('mode.askSub', 'Answers only. Never acts.')}</small></span></div>
    <div class="mi">${ic('task')}<span>${t('mode.plan', 'Plan')}<small>${t('mode.planSub', 'Shows the steps first. You decide.')}</small></span></div>
    <div class="mi">${ic('action_mode')}<span>${t('mode.act', 'Act')}<small>${t('mode.actSub', 'Can use apps. Asks before anything that matters.')}</small></span></div></div>`;
  return `${head}${short}${composer({ mode: st })}${nav('home')}`;
};

/* ---------- H03 Attach / context sheet ---------- */
STATES_B.H03 = ['sheet', 'chips', 'review', 'vision-missing', 'camera-denied'];
SCREENS_B.H03 = (st, th) => {
  const base = `${sb()}${homeTop(th)}<div class="body"></div>`;
  if (st === 'chips') return `${base}${composer({ n: 2, mode: 'typing', text: 'What should I reply to this?', chips: `<span class="chip x">${ic('image')}${t('chip.photo', 'Photo')}${ic('close')}</span><span class="chip x">${ic('new_note')}Trip notes${ic('close')}</span><span class="where">${ic('on_device')}${t('chip.scope', 'Stays on this phone')}</span>` })}${nav('home')}`;
  if (st === 'review') return `${sb()}${topBar(t('cap.review', 'Review captured image'), { back: true })}<div class="body nonav">
    <div class="photo" style="height:250px"></div>
    <div class="banner info" style="margin-top:12px">${ic('ocr')}<span>${t('cap.draft', 'halo read this from the photo. Check the highlighted parts before using it.')}</span></div>
    <div class="card s2" style="margin-top:10px"><div class="sm">Invoice 2026-114 · Total <span style="background:color-mix(in srgb,var(--att) 25%,transparent);border-radius:4px;padding:0 3px">€ 1?8.40</span> · due 30 Oct</div><div class="xs mut" style="margin-top:4px">${t('cap.unsure', 'Highlighted values were hard to read.')}</div></div>
    <div class="btns" style="position:absolute;left:16px;right:16px;bottom:16px"><span class="btn ghost">${t('cap.retake', 'Retake')}</span><span class="btn ghost">${t('cap.edit', 'Edit text')}</span><span class="btn">${t('cap.use', 'Use in message')}</span></div></div>`;
  const sheet = `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('att.title', 'Attachments and context')}</h2>
    <div class="sm mut">${t('att.sub', 'Pick what halo can look at for this message only.')}</div>
    <div class="grid" style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:14px 0 6px;text-align:center">
     ${[['camera', 'att.camera', 'Camera'], ['image', 'att.photo', 'Photo'], ['file_doc', 'att.file', 'File'], ['notebook', 'att.notebook', 'Notebook']].map(([i, k, en]) => `<div><div class="av" style="width:56px;height:56px;border-radius:18px;margin:0 auto 6px">${ic(i)}</div><div class="xs b">${t(k, en)}</div></div>`).join('')}</div>
    ${st === 'vision-missing' ? `<div class="banner att" style="margin-top:8px">${ic('image')}<span><b>${t('att.visionTitle', 'halo can’t see photos yet')}</b><br>${t('att.visionSub', 'halo can’t look at photos on this phone yet. You can still attach it and describe it.')}</span></div>
      <div class="btns"><span class="btn text sm">${t('att.attachAnyway', 'Attach as file')}</span><span class="btn sm">${t('att.setupVision', 'Let halo see photos')}</span></div>`
    : st === 'camera-denied' ? `<div class="banner att" style="margin-top:8px">${ic('permission')}<span><b>${t('att.camDenied', 'Camera permission was not granted')}</b><br>${t('att.camDeniedSub', 'You can allow it in Android settings, or pick a photo instead.')}</span></div>
      <div class="btns"><span class="btn text sm">${t('att.pickPhoto', 'Pick a photo')}</span><span class="btn sm">${t('att.openSettings', 'Open Android settings')}</span></div>`
    : `<div class="lbl">${t('att.selected', 'Selected')}</div><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip x">${ic('image')}IMG_2041${ic('close')}</span><span class="chip x">${ic('new_note')}Trip notes${ic('close')}</span></div>
      <div class="hr"></div><div class="row sm">${ic('on_device')}<span class="grow">${t('att.scope', 'Used for this message · stays on this phone')}</span></div>
      <div class="btns"><span class="btn">${t('att.done', 'Done · 2 selected')}</span></div>`}</div>`;
  return `${base}${composer({ n: st === 'sheet' ? 2 : 0 })}${nav('home')}${sheet}`;
};

/* ---------- H04 Tools menu + forms ---------- */
STATES_B.H04 = ['menu', 'timer', 'open-app', 'clear'];
SCREENS_B.H04 = (st, th) => {
  const base = `${sb()}${homeTop(th)}<div class="body">${userMsg('Plan dinner for four')}${haloMsg(th, '<p>Lemon chicken traybake, 50 minutes.</p>')}</div>${composer()}${nav('home')}`;
  if (st === 'menu') return `${base}<div class="scrim"></div><div class="menu" style="left:12px;bottom:150px;width:270px">
    <div class="mi">${ic('inbox')}${t('tools.inbox', 'Save to Inbox')}</div><div class="mi">${ic('new_note')}${t('tools.note', 'New note')}</div>
    <div class="mi">${ic('timer')}${t('tools.timer', 'Timer')}</div><div class="mi">${ic('open_app')}${t('tools.openApp', 'Open app')}</div><div class="sep"></div>
    <div class="mi">${ic('think_with_halo')}${t('mode.title', 'How halo helps')}</div><div class="mi">${ic('new_chat')}${t('tools.newConv', 'New conversation')}</div>
    <div class="mi danger">${ic('trash')}${t('tools.clear', 'Clear conversation')}</div></div>`;
  if (st === 'timer') return `${base}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${ic('timer')} ${t('tools.timer', 'Timer')}</h2>
    <div class="field focus"><span><span class="fl">${t('timer.label', 'Minutes')}</span><span class="fv">2</span></span><span class="grow"></span><span class="ib s2" style="width:40px;height:40px">${ic('mic')}</span></div>
    <div class="help">${t('timer.help', 'Runs in your Clock app. halo confirms once it starts.')}</div>
    <div class="row" style="gap:6px;margin-top:10px">${['1', '5', '10', '25'].map(m => `<span class="chip">${m} min</span>`).join('')}</div>
    <div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn">${t('timer.start', 'Start 2-minute timer')}</span></div></div>`;
  if (st === 'open-app') return `${base}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${ic('open_app')} ${t('tools.openApp', 'Open app')}</h2>
    <div class="field bad"><span><span class="fl">${t('openApp.label', 'App name')}</span><span class="fv">Spotfy</span></span></div>
    <div class="help bad">${t('openApp.notFound', 'No app called “Spotfy”. Did you mean Spotify?')}</div>
    <div class="row" style="gap:6px;margin-top:10px"><span class="chip sel">Spotify</span><span class="chip">Maps</span><span class="chip">Camera</span></div>
    <div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn">${t('openApp.go', 'Open Spotify')}</span></div></div>`;
  return `${base}<div class="scrim"></div><div class="dlg"><h2>${t('clear.title', 'Clear this conversation?')}</h2>
    <div class="sm mut">${t('clear.body', 'This removes its messages. Your other conversations and Notebook are kept.')}</div>
    <div class="btns"><span class="btn text">${t('clear.keep', 'Keep messages')}</span><span class="btn danger">${t('clear.confirm', 'Clear conversation')}</span></div></div>`;
};

/* ---------- H05 response states ---------- */
STATES_B.H05 = ['streaming', 'tool', 'answer', 'partial', 'stopped', 'refusal', 'offline', 'cloud-ask'];
SCREENS_B.H05 = (st, th) => {
  const q = userMsg(st === 'tool' || st === 'partial' ? 'Send Anna the photos from Saturday and set a reminder to call her' : st === 'refusal' ? 'Read my partner’s messages' : st === 'cloud-ask' ? 'Summarise this 40-page PDF' : 'How do I repot a basil plant?');
  let a = '', s = 'idle', comp = {}, wh;
  if (st === 'streaming') { s = 'streaming_text'; comp.mode = 'busy'; a = `<p>Pick a pot one size bigger with drainage holes. Water the basil an hour before so the roots slide out<span style="display:inline-block;width:7px;height:15px;background:var(--ink);margin-left:2px;vertical-align:-2px"></span></p>`; }
  if (st === 'answer') a = `<p><b>1.</b> Pot one size bigger, with holes.<br><b>2.</b> Water an hour before.<br><b>3.</b> Loosen roots gently, plant at the same depth.<br><b>4.</b> Water well, keep out of strong sun for 2 days.</p>
    <div class="src"><span>${ic('source')}Notebook · Balcony herbs</span><span>${ic('on_device')}${t('where.phone', 'On this phone')}</span></div>${ansActs()}`;
  if (st === 'tool') { s = 'acting'; comp.mode = 'busy'; a = `<div class="row"><span class="t2 grow">${t('task.title', 'Doing 2 things')}</span><span class="tag">${t('task.running', 'Running')}</span></div>
    <ul class="steps"><li class="done"><span class="dot">${ic('check')}</span>Found 6 photos from Saturday</li><li class="now"><span class="dot"></span>Opening Messages to send to Anna…</li><li><span class="dot"></span>Set reminder “Call Anna”</li></ul>
    <div class="xs mut">${t('task.stopHint', 'You can stop at any step.')}</div>`; }
  if (st === 'partial') { s = 'warning'; a = `<div class="row"><span class="t2 grow">${t('task.partial', '1 of 2 done')}</span><span class="tag att">${t('task.needsLook', 'Needs a look')}</span></div>
    <ul class="steps"><li class="done"><span class="dot">${ic('check')}</span>Reminder set · Tomorrow 18:00</li><li class="fail"><span class="dot">${ic('close')}</span>Photos not sent — Messages closed before sending</li></ul>
    <div class="btns" style="justify-content:flex-start"><span class="btn sm guide">${t('task.retryStep', 'Try sending again')}</span><span class="btn text sm">${t('task.openActivity', 'Open Activity')}</span></div>`; }
  if (st === 'stopped') { s = 'stopped'; a = `<div class="row"><span class="t2 grow">${t('task.stopped', 'Stopped')}</span><span class="tag neu">${t('task.byYou', 'You stopped it')}</span></div>
    <p class="sm" style="margin-top:6px">${t('task.stoppedBody', 'Nothing more will happen. What was already done stays done:')}</p><ul class="steps"><li class="done"><span class="dot">${ic('check')}</span>Found 6 photos</li><li class="skip"><span class="dot"></span>Not sent</li></ul>
    <div class="btns" style="justify-content:flex-start"><span class="btn ghost sm">${t('task.resume', 'Continue from here')}</span></div>`; }
  if (st === 'refusal') a = `<p>${t('ans.refuse', 'I can’t read someone else’s messages. If you’re worried about something, I can help you think through how to talk about it.')}</p>`;
  if (st === 'offline') { s = 'offline'; wh = where('offline'); a = `<div class="banner err">${ic('disconnected')}<span><b>${t('ans.offlineT', 'Your cloud service can’t be reached')}</b><br>${t('ans.offlineB', 'You’re offline. Your question is kept — send it again when you’re back, or answer it on this phone.')}</span></div>
    <div class="btns" style="justify-content:flex-start"><span class="btn sm">${t('ans.usePhone', 'Answer on this phone')}</span><span class="btn text sm">${t('ans.retryLater', 'Try again')}</span></div>`; }
  if (st === 'cloud-ask') { s = 'approval_required'; a = `<div class="t2">${t('cloud.title', 'This PDF is long for this phone')}</div><p class="sm" style="margin-top:6px">${t('cloud.body', 'On this phone it may take several minutes. Your cloud service is faster, but the PDF would leave this phone.')}</p>
    <div class="col" style="margin-top:8px"><span class="btn ghost full">${ic('on_device')}${t('cloud.local', 'Keep it on this phone')}</span><span class="btn line full">${ic('cloud')}${t('cloud.send', 'Send PDF to cloud')}</span></div>
    <div class="xs mut" style="margin-top:6px">${t('cloud.note', 'Neither is preselected. You can change the default in Context & privacy.')}</div>`; }
  return `${sb()}${homeTop(th, wh ? { where: wh } : {})}<div class="body">${q}${haloMsg(th, a, s)}</div>${composer(comp)}${nav('home')}`;
};

/* ---------- H06 Today / Next step ---------- */
STATES_B.H06 = ['one', 'more', 'dismissed', 'none'];
SCREENS_B.H06 = (st, th) => {
  const card = (g) => `<div class="card ${g ? 'guide' : ''}"><div class="row"><div class="av">${ic('study')}</div><div class="grow"><div class="t2">${t('next.title2', 'Review 6 Spanish cards')}</div>
    <div class="sm mut">${t('next.why2', 'Due today · about 4 minutes')}</div></div></div><div class="btns" style="margin-top:10px"><span class="btn text sm">${t('next.notNow', 'Not now')}</span><span class="btn sm">${t('next.do2', 'Start review')}</span></div></div>`;
  const more = `<div class="lbl">${t('today.also', 'Also today')}</div><div class="card s2 list">
    <div class="li"><div class="av">${ic('inbox')}</div><div class="grow"><div class="b sm">3 items in Inbox</div><div class="xs mut">Saved from Home and glasses</div></div>${ic('chevron_down')}</div>
    <div class="li"><div class="av">${ic('routines')}</div><div class="grow"><div class="b sm">Evening digest at 20:00</div><div class="xs mut">Routine · on</div></div></div>
    <div class="li"><div class="av">${ic('glasses')}</div><div class="grow"><div class="b sm">Glasses battery 18%</div><div class="xs mut">Checked 12 min ago</div></div></div></div>`;
  let b = '';
  if (st === 'one') b = `<div class="lbl">${t('today.title', 'A little help for your day')}</div>${card(true)}<div class="earlier" style="margin-top:12px">${t('today.seeMore', 'See 3 more')}</div>`;
  if (st === 'more') b = `<div class="lbl">${t('today.title', 'A little help for your day')}</div>${card(true)}${more}`;
  if (st === 'dismissed') b = `<div class="lbl">${t('today.title', 'A little help for your day')}</div>${more}`;
  if (st === 'none') b = `<div class="empty">${orb('idle', th, 80)}<div class="t2">${t('today.noneT', 'Nothing needs you right now')}</div><div class="sm mut">${t('today.noneB', 'Next steps show up here when halo has a useful one. It won’t invent busywork.')}</div></div>`;
  return `${sb()}${homeTop(th, { badge: false })}<div class="body">${b}</div>${composer()}${nav('home')}${st === 'dismissed' ? `<div class="toast"><span class="grow">${t('today.hidden', 'Hidden until tomorrow')}</span><span class="btn text">${t('common.undo', 'Undo')}</span></div>` : ''}`;
};

/* ---------- H07 Approval / recovery sheet (global layer) ---------- */
STATES_B.H07 = ['request', 'inspect', 'running', 'done', 'failed', 'expired'];
SCREENS_B.H07 = (st, th) => {
  const base = `${sb()}${homeTop(th)}<div class="body">${userMsg('Send Anna the photos from Saturday')}${haloMsg(th, `<div class="row"><span class="tag att">${ic('approval')}${t('appr.needs', 'Needs your OK')}</span></div>`, 'approval_required')}</div>${composer({ mode: 'busy' })}${nav('home')}`;
  const head = (o, tag) => `<div class="row" style="margin-bottom:6px">${orb(o, th, 30)}<span class="grow"></span>${tag}</div>`;
  let s;
  if (st === 'request' || st === 'inspect') s = `${head('approval_required', `<span class="tag att">${t('appr.needs', 'Needs your OK')}</span>`)}
    <h2>${t('appr.title', 'Send 6 photos to Anna in Messages?')}</h2>
    <div class="card s2" style="padding:10px 12px;margin:8px 0"><div class="row sm">${ic('phone_action')}<span class="grow"><b>Messages</b> · Anna Bērziņa</span></div><div class="row" style="gap:6px;margin-top:8px">${'<div style="width:44px;height:44px;border-radius:8px;background:linear-gradient(135deg,#c9b38a,#6d5a40)"></div>'.repeat(6)}</div></div>
    ${st === 'inspect' ? `<div class="lbl" style="margin-top:8px">${t('appr.plan', 'What will happen')}</div><ul class="steps"><li class="done"><span class="dot">${ic('check')}</span>Find photos from Sat 18 Oct · done</li><li class="wait"><span class="dot"></span>Open Messages, attach 6 photos, send to Anna</li><li><span class="dot"></span>Show you the sent message</li></ul>
      <div class="xs mut">${t('appr.affects', 'Uses: Messages, Photos. Nothing else is touched.')}</div>` : `<div class="btn text sm" style="padding:0;height:32px">${ic('chevron_down')}${t('appr.inspect', 'See the steps')}</div>`}
    <div class="xs mut" style="margin:6px 0 0">${t('appr.leave', 'Closing leaves it waiting in Activity.')}</div>
    <div class="btns"><span class="btn text">${t('appr.decline', 'Don’t send')}</span><span class="btn">${t('appr.approve', 'Send photos')}</span></div>`;
  if (st === 'running') s = `${head('acting_phone', `<span class="tag">${t('task.running', 'Running')}</span>`)}<h2>${t('appr.sending', 'Sending to Anna…')}</h2>
    <ul class="steps"><li class="done"><span class="dot">${ic('check')}</span>Photos found</li><li class="done"><span class="dot">${ic('check')}</span>You approved · 21:04</li><li class="now"><span class="dot"></span>Attaching in Messages (4 of 6)</li></ul>
    <div class="btns"><span class="btn ghost">${ic('stop')}${t('comp.stop', 'Stop')}</span></div>`;
  if (st === 'done') s = `${head('success', `<span class="tag">${ic('check')}${t('appr.confirmed', 'Confirmed')}</span>`)}<h2>${t('appr.sent', 'Sent to Anna')}</h2>
    <div class="sm mut">${t('appr.sentBody', 'Messages reported the 6 photos as sent at 21:05.')}</div>
    <div class="btns"><span class="btn text">${t('appr.seeActivity', 'See in Activity')}</span><span class="btn">${t('common.done', 'Done')}</span></div>`;
  if (st === 'failed') s = `${head('error', `<span class="tag err">${t('task.failed', 'Couldn’t finish')}</span>`)}<h2>${t('appr.failT', 'The photos weren’t sent')}</h2>
    <div class="banner err">${ic('failure')}<span>${t('appr.failB', 'Messages closed before sending. Nothing was sent to Anna.')}</span></div>
    <div class="btns"><span class="btn text">${t('appr.leaveIt', 'Leave it')}</span><span class="btn guide">${t('appr.retry', 'Try again')}</span></div>`;
  if (st === 'expired') s = `${head('permission_missing', `<span class="tag att">${t('appr.permT', 'Permission needed')}</span>`)}<h2>${t('appr.expT', 'halo can no longer control Messages')}</h2>
    <div class="sm">${t('appr.expB', 'Android turned off halo’s accessibility access after an update. Turn it back on, then try again — your request is kept.')}</div>
    <div class="btns"><span class="btn text">${t('next.notNow', 'Not now')}</span><span class="btn">${t('appr.openA11y', 'Open Android settings')}</span></div>`;
  return `${base}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div>${s}</div>`;
};

/* ---------- A01 Activity list ---------- */
STATES_B.A01 = ['all', 'needs', 'empty', 'failed'];
const runCard = (o) => `<div class="card" data-go="A02"><div class="row"><div class="av">${ic(o.ic)}</div><div class="grow"><div class="b ell">${o.title}</div><div class="xs mut">${o.meta}</div></div>${o.tag}</div>${o.extra || ''}</div>`;
SCREENS_B.A01 = (st, th) => {
  const seg = (on) => `<div class="seg">${[['all', t('act.all', 'All')], ['needs', t('act.needs', 'Needs you') + ' · 1'], ['run', t('act.running', 'Running')], ['done', t('act.done', 'Done')]].map(([k, l]) => `<span class="chip ${k === on ? 'sel' : 'line'}">${l}</span>`).join('')}</div>`;
  const need = runCard({ ic: 'approval', title: 'Send 6 photos to Anna', meta: 'Messages · waiting since 21:04', tag: `<span class="tag att">${t('act.waiting', 'Waiting for you')}</span>`, extra: `<div class="btns" style="margin-top:8px"><span class="btn sm">${t('act.review', 'Review')}</span></div>` });
  const run = runCard({ ic: 'download', title: 'Downloading English listening pack', meta: 'On this phone · 62%', tag: `<span class="tag">${t('task.running', 'Running')}</span>` });
  const done = runCard({ ic: 'timer', title: '2-minute timer', meta: 'Clock · 20:41 · confirmed', tag: `<span class="tag neu">${ic('check')}${t('act.doneOne', 'Done')}</span>` });
  const unk = runCard({ ic: 'phone_action', title: 'Reply to Pēteris on WhatsApp', meta: 'Handed to WhatsApp · 19:58', tag: `<span class="tag neu">${t('act.unconfirmed', 'Not confirmed')}</span>` });
  const fail = runCard({ ic: 'failure', title: 'Summarise “Lease 2027.pdf”', meta: 'On this phone · 18:30', tag: `<span class="tag err">${t('task.failed', 'Couldn’t finish')}</span>`, extra: `<div class="sm mut" style="margin-top:6px">${t('act.failWhy', 'The model ran out of memory on page 31.')}</div><div class="btns" style="margin-top:6px"><span class="btn ghost sm">${t('act.details', 'Details')}</span><span class="btn sm">${t('task.retry', 'Retry')}</span></div>` });
  const head = `${sb()}${topBar(t('nav.activity', 'Activity'), { right: `<span class="ib">${ic('search')}</span>` })}`;
  if (st === 'empty') return `${head}<div class="body" style="bottom:92px">${seg('all')}<div class="empty">${orb('idle', th, 84)}<div class="t2">${t('act.emptyT', 'No activity yet')}</div><div class="sm mut">${t('act.emptyB', 'Ask halo something on Home to get started.')}</div><span class="btn ghost sm" style="margin-top:6px">${t('act.openHome', 'Open Home')}</span></div></div>${nav('activity')}`;
  if (st === 'needs') return `${head}<div class="body" style="bottom:92px">${seg('needs')}<div class="sm mut" style="margin:0 2px 10px">${t('act.needsSub', 'Approvals and answers wait here until you choose what happens next.')}</div>${need}</div>${nav('activity', 'activity')}`;
  return `${head}<div class="body" style="bottom:92px"><div class="sm mut" style="margin:-4px 2px 8px">${t('act.sub', 'Everything halo did, in plain words.')}</div>${seg('all')}${st === 'failed' ? fail + done : need + run}<div class="lbl">${t('act.earlier', 'Earlier today')}</div>${done}${unk}</div>${nav('activity', 'activity')}`;
};

/* ---------- A02 Run detail ---------- */
STATES_B.A02 = ['running', 'done', 'failed', 'what-happened'];
SCREENS_B.A02 = (st, th) => {
  const tag = { running: `<span class="tag">${t('task.running', 'Running')}</span>`, done: `<span class="tag neu">${ic('check')}${t('appr.confirmed', 'Confirmed')}</span>`, failed: `<span class="tag err">${t('task.failed', 'Couldn’t finish')}</span>`, 'what-happened': `<span class="tag neu">${ic('check')}${t('appr.confirmed', 'Confirmed')}</span>` }[st];
  const o = { running: 'acting_phone', done: 'success', failed: 'error', 'what-happened': 'success' }[st];
  const steps = st === 'running' ? `<li class="done"><span class="dot">${ic('check')}</span><span>Found 6 photos<div class="xs mut">21:03 · Photos</div></span></li><li class="done"><span class="dot">${ic('check')}</span><span>You approved “Send photos”<div class="xs mut">21:04</div></span></li><li class="now"><span class="dot"></span><span>Attaching in Messages · 4 of 6</span></li><li><span class="dot"></span><span>Confirm sent</span></li>`
    : st === 'failed' ? `<li class="done"><span class="dot">${ic('check')}</span><span>Found 6 photos</span></li><li class="done"><span class="dot">${ic('check')}</span><span>You approved · 21:04</span></li><li class="fail"><span class="dot">${ic('close')}</span><span>Messages closed before sending<div class="xs mut">21:05 · nothing was sent</div></span></li>`
    : `<li class="done"><span class="dot">${ic('check')}</span><span>Found 6 photos</span></li><li class="done"><span class="dot">${ic('check')}</span><span>You approved · 21:04</span></li><li class="done"><span class="dot">${ic('check')}</span><span>Sent to Anna<div class="xs mut">21:05 · Messages confirmed</div></span></li>`;
  const tech = st === 'what-happened' ? `<div class="card s2" style="margin-top:10px"><div class="b sm" style="margin-bottom:6px">${t('run.what', 'What halo did')}</div>
    <div class="list sm"><div class="li" style="padding:6px 0">${ic('phone_action')}<span class="grow">${t('run.usedApps', 'Used Messages and Photos')}</span></div>
    <div class="li" style="padding:6px 0">${ic('on_device')}<span class="grow">${t('run.stayed', 'Everything stayed on this phone')}</span></div>
    <div class="li" style="padding:6px 0">${ic('timer')}<span class="grow">${t('run.took', 'Took under 2 minutes')}</span></div></div></div>` : `<div class="btn text sm" style="padding:0;margin-top:6px">${t('run.showWhat', 'What halo did')}</div>`;
  const acts = st === 'running' ? `<span class="btn ghost">${ic('stop')}${t('comp.stop', 'Stop')}</span>` : st === 'failed' ? `<span class="btn ghost">${t('run.copyHome', 'Edit in Home')}</span><span class="btn guide">${t('task.retry', 'Retry')}</span>` : `<span class="btn ghost">${t('run.again', 'Do it again')}</span>`;
  return `${sb()}${topBar(t('run.title', 'Task'), { back: true, right: `<span class="ib">${ic('more')}</span>` })}<div class="body" style="bottom:92px">
    <div class="row">${orb(o, th, 40)}<div class="grow"><div class="t2">Send 6 photos to Anna</div><div class="xs mut">${t('run.from', 'From Home · today 21:03')}</div></div></div>
    <div class="row" style="gap:6px;margin:10px 0">${tag}<span class="where">${ic('on_device')}${t('where.phone', 'On this phone')}</span></div>
    <div class="card"><div class="lbl" style="margin-top:0">${t('run.steps', 'Recorded steps')}</div><ul class="steps tl">${steps}</ul></div>${tech}
    <div class="btns">${acts}</div></div>${nav('activity')}`;
};

/* ---------- N01 Notification inbox ---------- */
STATES_B.N01 = ['list', 'filter', 'empty', 'no-access'];
SCREENS_B.N01 = (st, th) => {
  const head = `${sb()}${topBar(t('notif.title', 'Notifications'), { back: true, right: `<span class="ib">${ic('settings')}</span>` })}`;
  const row = (a, app, txt, time) => `<div class="li" data-go="N02"><div class="av">${a}</div><div class="grow"><div class="row"><span class="b sm grow">${app}</span><span class="xs mut">${time}</span></div><div class="sm ell">${txt}</div></div></div>`;
  const list = `<div class="card list" style="padding:2px 14px">${row('M', 'Messages · Anna', 'Are we still on for Sunday?', '21:10')}${row('W', 'WhatsApp · Pēteris', 'Sent you the address', '19:52')}${row(ic('calendar'), 'Calendar', 'Dentist tomorrow 10:30', '18:00')}${row('B', 'Bank', 'Card payment €12.40', '16:21')}</div>`;
  const pv = `<div class="row xs mut" style="margin:-2px 2px 10px">${ic('privacy')}${t('notif.private', 'Private · last 24 hours')}</div>`;
  if (st === 'no-access') return `${head}<div class="body nonav">${pv}<div class="empty">${orb('permission_missing', th, 84)}<div class="t2">${t('notif.noAccessT', 'Notification access is not currently connected')}</div>
    <div class="sm mut">${t('notif.noAccessB', 'Allow notification access to see permitted notifications in halo. History remains available while disconnected.')}</div>
    <span class="btn" style="margin-top:8px">${t('notif.openAccess', 'Open notification access')}</span><span class="btn text sm">${t('notif.privacy', 'Manage app notification privacy')}</span></div></div>`;
  const bar = `<div class="row" style="gap:6px;margin-bottom:10px"><div class="field" style="flex:1;min-height:44px">${ic('search')}<span class="mut sm">${st === 'filter' ? 'Sunday' : t('notif.search', 'Search notifications')}</span></div><span class="chip ${st === 'filter' ? 'sel' : 'line'}">${ic('filter')}${st === 'filter' ? 'Messages' : t('notif.allApps', 'All apps')}</span></div>`;
  if (st === 'empty') return `${head}<div class="body nonav">${pv}${bar}<div class="empty">${orb('idle', th, 76)}<div class="t2">${t('notif.emptyT', 'Nothing in the last 24 hours')}</div><div class="sm mut">${t('notif.emptyB', 'Notifications from apps you’ve allowed will appear here.')}</div></div></div>`;
  if (st === 'filter') return `${head}<div class="body nonav">${pv}${bar}<div class="card list" style="padding:2px 14px">${row('M', 'Messages · Anna', 'Are we still on for <b>Sunday</b>?', '21:10')}</div>
    <div class="sm mut" style="text-align:center;margin-top:14px">${t('notif.filterHint', 'Clear the search and app filter to see more notifications.')}</div><div style="text-align:center"><span class="btn text sm">${t('notif.clear', 'Clear filters')}</span></div></div>`;
  return `${head}<div class="body nonav">${pv}${bar}<div class="card s2" style="margin-bottom:10px"><div class="row">${ic('read_mode')}<div class="grow"><div class="b sm">${t('notif.digest', 'Notification digest')}</div><div class="xs mut">${t('notif.digestSub', 'Local summary · no AI · no cloud')}</div></div><span class="ib" style="width:40px;height:40px">${ic('speaker')}</span></div>
    <div class="sm" style="margin-top:6px">4 apps · 2 messages need a reply · 1 payment · 1 reminder.</div></div>${list}</div>`;
};

/* ---------- N02 Notification detail / reply ---------- */
STATES_B.N02 = ['detail', 'reply', 'confirm', 'handed', 'expired'];
SCREENS_B.N02 = (st, th) => {
  const head = `${sb()}${topBar(t('notif.one', 'Notification'), { back: true })}`;
  const card = `<div class="card"><div class="row"><div class="av">M</div><div class="grow"><div class="b">Anna Bērziņa</div><div class="xs mut">Messages · 21:10</div></div><span class="ib" style="width:44px;height:44px" aria-label="${t('ans.read', 'Read aloud')}">${ic('speaker')}</span></div><div style="margin-top:10px">Are we still on for Sunday? I can bring dessert 🍰</div></div>`;
  if (st === 'expired') return `${head}<div class="body nonav">${card}<div class="banner att" style="margin-top:12px">${ic('unavailable')}<span>${t('notif.replyGone', 'This reply action is no longer available')}<br><span class="sm mut">${t('notif.replyGoneB', 'Messages removed it. Open the app to answer.')}</span></span></div><div class="btns"><span class="btn">${t('notif.openApp', 'Open Messages')}</span></div></div>`;
  if (st === 'handed') return `${head}<div class="body nonav">${card}<div class="card s2" style="margin-top:12px"><div class="row">${orb('waiting', th, 28)}<div class="grow"><div class="b sm">${t('notif.handed', 'Reply handed to Messages')}</div><div class="xs mut">${t('notif.notConfirmed', 'Delivery not confirmed')}</div></div></div><div class="sm" style="margin-top:8px">“Yes! See you at 13:00. Dessert sounds perfect.”</div></div>
    <div class="xs mut" style="margin-top:8px">${t('notif.handedNote', 'halo can’t see whether Anna received it. Check Messages to be sure.')}</div></div>`;
  const reply = `<div class="lbl">${t('notif.yourReply', 'Your reply')}</div><div class="field focus" style="align-items:flex-start;min-height:96px;padding-top:12px"><span class="grow">Yes! See you at 13:00. Dessert sounds perfect.</span><span class="ib s2" style="width:40px;height:40px">${ic('mic')}</span></div>
    <div class="row help"><span class="grow">${t('notif.onlyAfter', 'Your reply is sent only after you confirm.')}</span><span>47/500</span></div>`;
  if (st === 'confirm') return `${head}<div class="body nonav">${card}${reply}</div><div class="scrim"></div><div class="dlg"><h2>${t('notif.confirmT', 'Reply via Messages?')}</h2><div class="card s2 sm">“Yes! See you at 13:00. Dessert sounds perfect.”</div>
    <div class="xs mut" style="margin-top:8px">${t('notif.toAnna', 'To Anna Bērziņa. halo hands it to Messages; it can’t confirm delivery.')}</div><div class="btns"><span class="btn text">${t('notif.keepEditing', 'Keep editing')}</span><span class="btn">${t('notif.sendReply', 'Send reply')}</span></div></div>`;
  if (st === 'reply') return `${head}<div class="body nonav">${card}${reply}<div class="btns"><span class="btn text">${t('notif.cancelReply', 'Cancel reply')}</span><span class="btn">${t('notif.sendDots', 'Send reply…')}</span></div></div>`;
  return `${head}<div class="body nonav">${card}<div class="col" style="margin-top:14px"><span class="btn full">${ic('saved_reply')}${t('notif.reply', 'Reply')}</span><span class="btn ghost full">${ic('new_note')}${t('ans.save', 'Save to Notebook')}</span><span class="btn line full">${t('notif.dismiss', 'Dismiss')}</span></div>
    <div class="xs mut" style="margin-top:10px">${t('notif.dismissNote', 'Dismiss asks Messages to remove it from your phone.')}</div></div>`;
};

/* 2027 light layer: which aura / edge each state gets. Bound to REAL state only. */
const FX_B = {
  'H01_empty': { aura: 'idle' }, 'H01_conversation': { aura: 'idle' }, 'H01_ahead': { aura: 'idle' }, 'H01_live': { aura: 'acting' }, 'H01_earlier': { aura: 'idle' }, 'H01_edit': { aura: 'idle' }, 'H01_ime': { aura: 'idle' },
  'H02_listening': { aura: 'listening' }, 'H02_busy': { aura: 'thinking' }, 'H02_disabled': { aura: 'needs' }, 'H02_idle': { aura: 'idle' }, 'H02_typing': { aura: 'idle' }, 'H02_mode': { aura: 'idle' },
  'H05_streaming': { aura: 'thinking' }, 'H05_tool': { aura: 'acting', edge: '' }, 'H05_answer': { aura: 'idle' }, 'H05_partial': { aura: 'needs' }, 'H05_stopped': {}, 'H05_offline': { aura: 'error' }, 'H05_cloud-ask': { aura: 'needs' }, 'H05_refusal': { aura: 'idle' },
  'H06_one': { aura: 'idle' }, 'H06_more': { aura: 'idle' }, 'H06_none': { aura: 'idle' },
  'H07_request': { aura: 'needs' }, 'H07_inspect': { aura: 'needs' }, 'H07_running': { aura: 'acting', edge: '' }, 'H07_done': { aura: 'success' }, 'H07_failed': { aura: 'error' }, 'H07_expired': { aura: 'needs' },
  'A02_running': { aura: 'acting' }, 'A02_done': { aura: 'success' }, 'A02_failed': { aura: 'error' },
  'N01_list': { aura: 'idle' }, 'N02_handed': { aura: 'idle' },
};
function decorate(id, st, th, html) {
  const f = FX_B[id + '_' + st] || {};
  return (f.aura ? `<div class="aura ${f.aura}"></div>` : '') + html + (f.edge !== undefined ? `<div class="edge ${f.edge}"></div>` : '');
}
window.decorate = decorate; window.FX_B = FX_B;
window.SCREENS_B = SCREENS_B; window.STATES_B = STATES_B;
