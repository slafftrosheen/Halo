/* ===================== Voice & Read everywhere screens (DevKit v1.4) ===================== */
const progRing = (pct) => `<svg class="prog" viewBox="0 0 46 46"><circle cx="23" cy="23" r="21" fill="none" stroke="var(--line)" stroke-width="2.5"/><circle cx="23" cy="23" r="21" fill="none" stroke="var(--ink)" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="${(pct / 100 * 132).toFixed(0)} 200" transform="rotate(-90 23 23)"/></svg>`;
const readBtn = (st = 'idle', pct = 40) => `<span class="rbtn ${st !== 'idle' ? 'on' : ''}" aria-label="${st === 'reading' ? 'Pause reading' : 'Read aloud'}" ${go('self', st === 'reading' ? 'tts.pause' : 'tts.request')}>${st === 'reading' ? progRing(pct) + ic('pause') : ic('speaker')}</span>`;
const readBar = (title, sub = 'Reading · sentence 2 of 5', paused = false) => `<div class="rbar"><div class="tt">${title}<i>${sub}</i></div>
  <span class="b" aria-label="Previous sentence">${ic('back')}</span><span class="b pp" aria-label="${paused ? 'Resume' : 'Pause reading'}">${ic(paused ? 'play' : 'pause')}</span><span class="b" aria-label="Next sentence" style="transform:scaleX(-1)">${ic('back')}</span>
  <span class="sp">1.0×</span><span class="b" aria-label="Stop" ${go('self', 'tts.stop')}>${ic('close')}</span></div>`;
const nav = (on) => `<div class="nav">${[['home', 'Home'], ['notebook', 'Notebook'], ['study', 'Study'], ['activity', 'Activity'], ['settings', 'Settings']].map(([i, t]) => `<span class="${t === on ? 'on' : ''}">${ic(i)}${t}</span>`).join('')}</div>`;
/* HaloTextField: kind + state */
function hfield(o) {
  const k = o.kind || 'TEXT', s = o.st || 'idle';
  let trail = '';
  if (k === 'SECRET') trail = `<span class="act lock" aria-label="Voice input is off for secrets">${ic('privacy')}</span>`;
  else if (k === 'URL_ID') trail = `<span class="act" aria-label="Spell it">${ic('speaker')}</span>`;
  else if (k === 'STRUCTURED') trail = `<span class="act mic" aria-label="Describe it">${ic('ai_draft')}</span>`;
  else {
    if (o.value && s === 'idle' && k !== 'SEARCH') trail += `<span class="act" aria-label="Read aloud">${ic('speaker')}</span>`;
    trail += s === 'listening' || s === 'recognizing' ? `<span class="act on" aria-label="Stop listening" ${go('self', 'voice.stop')}><i class="lring"></i>${ic('stop')}</span>`
      : `<span class="act mic" aria-label="Speak" ${go('self', 'voice.open')}>${ic('mic')}</span>`;
  }
  const val = s === 'listening' ? `<span class="val">${o.value || ''}<span class="partial">${o.partial || ' listening…'}</span></span>`
    : s === 'recognizing' ? `<span class="val partial">Recognising…</span>`
    : o.value ? `<span class="val">${k === 'SECRET' ? '•••••••••••••••• 3f9a' : o.value}</span>` : `<span class="val phd">${o.ph || ''}</span>`;
  let help = '';
  if (s === 'preview') help = `<div class="prev"><div class="sm mut" style="font-weight:700;margin-bottom:4px">${o.kind === 'NUMBER' ? 'I heard “' + o.heard + '” → ' + o.num : 'Review spoken text'}</div>${o.kind === 'NUMBER' ? '' : `<div class="t">${o.prev}</div>`}
    <div class="r"><span ${go('self', 'dictation.commit')}>${ic('check')}Use spoken text</span><span>${ic('annotate')}Edit before using</span><span>${ic('close')}Discard</span></div></div>`;
  else if (s === 'model') help = `<div class="help att">${ic('unavailable')}Install a local speech model in Voice settings.<a>Get speech model</a></div>`;
  else if (s === 'perm') help = `<div class="help att">${ic('mic')}halo needs the microphone to hear you.<a>Allow microphone</a></div>`;
  else if (k === 'SECRET') help = `<div class="help">${ic('privacy')}Voice input is off for secrets. Never read aloud.</div>`;
  else if (k === 'URL_ID') help = `<div class="help">${ic('speaker')}No mic for addresses — paste it. Read aloud spells it out.</div>`;
  else if (o.help) help = `<div class="help">${ic('on_device')}${o.help}</div>`;
  return `<div class="hf"><div class="lb">${o.label || ''}</div><div class="box ${s === 'listening' || s === 'preview' ? 'focus' : ''}">${val}${trail}</div>${help}</div>`;
}
const ANS = (hl) => {
  const w = ['Try', 'a', 'standing', 'calf', 'stretch:', 'lean', 'on', 'a', 'wall,', 'back', 'leg', 'straight,', 'heel', 'down.', 'Hold', 'it', 'for', '30', 'seconds', 'on', 'each', 'side.'];
  return w.map((x, i) => hl == null ? x : i < hl ? `<span class="read-done">${x}</span>` : i === hl ? `<span class="word">${x}</span>` : `<span class="read-todo">${x}</span>`).join(' ');
};
Object.assign(SCREENS, {
V01(st, th) {
  const L = st === 'listening', P = st === 'preview', R = st === '' || st === 'reading', M = st === 'model';
  return `${status()}<div class="top"><img src="a/orb/halo_orb_${L ? 'listening' : 'idle'}_${th}.svg" style="width:34px;height:34px"><h1>halo</h1>${where('local')}</div><div class="body" style="bottom:170px">
  <div class="bubble me" style="max-width:80%">What’s a good stretch after running?</div>
  <div class="card" style="margin-top:12px"><p style="margin:0;font-size:calc(16.5px*var(--fs));line-height:1.55">${ANS(R ? 6 : null)}</p>
   <div class="row" style="margin-top:12px;justify-content:space-between"><span class="sm mut">${ic('on_device', '').replace('class="ic', 'style="width:15px;height:15px;vertical-align:-3px" class="ic')} Answered on this phone · 2 s</span>
   <div class="row" style="gap:6px">${readBtn(R ? 'reading' : 'idle', 30)}<span class="rbtn">${ic('more')}</span></div></div></div>
  </div>${R ? readBar('Calf stretch answer', 'Reading · sentence 1 of 2') : ''}
  <div class="composer" style="${R ? 'bottom:156px' : ''}">${hfield({ kind: 'TEXT', st: L ? 'listening' : P ? 'preview' : M ? 'model' : 'idle', ph: 'Ask halo anything', value: L ? 'and how long ' : P ? '' : '', partial: 'should I hold it…', prev: 'And how long should I hold it if my <u>calves</u> are really tight?' })}</div>
  ${nav('Home')}`;
},
V02(st, th) {
  const sheet = st === 'sheet', ins = st === 'inserted', R = st === '';
  return `${status()}${topBar('Trip to Riga', { backTo: 'V01', right: `${readBtn(R ? 'reading' : 'idle', 55)}<span class="ib">${ic('more')}</span>` })}
  <div class="body" style="bottom:160px"><div class="row" style="gap:8px;margin-bottom:12px"><span class="chip">${ic('mic')}Speak</span><span class="chip ${R ? 'sel' : ''}">${ic('speaker')}Read</span><span class="chip">${ic('bold')}</span><span class="chip">${ic('bullet_list')}</span></div>
  <div style="font:600 calc(22px*var(--fs))/1.25 Sora;margin-bottom:10px">Trip to Riga</div>
  <p style="font-size:calc(16px*var(--fs));line-height:1.6;margin:0">${R ? '<span class="read-done">Saturday: Central Market in the morning, then the old town.</span> <span class="word">Sunday:</span> <span class="read-todo">ride to Jūrmala and walk on the beach.</span>' : 'Saturday: Central Market in the morning, then the old town. Sunday: ride to Jūrmala and walk on the beach.'}
   ${ins ? '<span class="wash"> Book the train back for Sunday at six. Ask Anna about dinner.</span>' : ''}</p>
  ${ins ? `<div class="toast" style="bottom:4px;position:absolute">${ic('check')}Added 2 sentences</div>` : ''}
  </div>${R ? readBar('Trip to Riga', 'Reading · sentence 2 of 3') : ''}${nav('Notebook')}
  ${sheet ? `<div class="scrim"></div><div class="sheet" style="height:470px"><div class="grab"></div><h2>Speak your note</h2>
   <div style="display:grid;place-items:center;margin:6px 0"><img src="a/vr/halo_fx_voice_wave_${th}.svg" style="width:170px;height:170px"></div>
   <p class="sub" style="text-align:center;margin:0 0 10px">Book the train back for Sunday at six. Ask Anna about <span class="partial">dinner…</span></p>
   <div class="row" style="gap:10px"><span class="btn ghost" style="flex:1">${ic('pause')}Pause</span><span class="btn" style="flex:1" ${go('V02', 'dictation.preview')}>${ic('check')}Done</span></div>
   <p class="sm mut" style="text-align:center;margin-top:10px">${ic('on_device', '').replace('class="ic', 'style="width:14px;height:14px;vertical-align:-2px" class="ic')} Local speech · nothing leaves the phone</p></div>` : ''}`;
},
V03(st, th) {
  const R = st === 'reading', C = st === 'cmds';
  return `${status()}${topBar('Riga weekend', { backTo: 'V02', right: `<span class="chip" ${go('V03', 'tts.request(canvas)')}>${ic('speaker')}Read canvas</span>` })}
  <div class="body grid-bg" style="left:0;right:0;padding:0;bottom:84px;top:84px">
   <svg style="position:absolute;inset:0" width="360" height="600"><g stroke="var(--ink)" stroke-width="2" fill="none" opacity=".6"><path d="M95 120 C 120 180, 160 180, 190 230"/><path d="M95 120 C 60 220, 80 300, 110 360"/></g><path d="M260 290 C 300 330, 300 380, 250 420" stroke="var(--ink)" stroke-width="2" fill="none" stroke-dasharray="6 5"/></svg>
   <div class="ccard ${R ? '' : ''}" style="left:20px;top:60px"><b>Saturday</b>Central Market, old town, Rīgas Balzams bar</div>
   <div class="ccard ${R ? 'reading' : ''}" style="left:180px;top:200px"><b>Sunday</b>${R ? '<span class="read-done">Train to</span> <span class="word">Jūrmala,</span> <span class="read-todo">beach walk</span>' : 'Train to Jūrmala, beach walk'}</div>
   <div class="ccard" style="left:40px;top:340px"><b>Food</b>Grey peas, rye bread soup</div>
   <div class="ccard draft" style="left:180px;top:410px"><span class="tag" style="height:20px;font-size:10px">${ic('ai_draft')}From your voice</span><b style="margin-top:6px">Packing list</b>Rain jacket, walking shoes<div class="row" style="gap:6px;margin-top:8px"><span class="chip" style="height:30px">${ic('check')}Keep</span><span class="chip" style="height:30px">${ic('undo')}</span></div></div>
   <div style="position:absolute;left:16px;top:12px"><span class="said">${ic('mic')}“Add a card about packing”</span></div>
  </div>${R ? readBar('Riga weekend · canvas', 'Card 2 of 4 · Sunday') : ''}
  ${dock({ kids: false, field: 'Say a command or ask…' })}
  ${C ? `<div class="scrim"></div><div class="sheet"><div class="grab"></div><h2>What can I say?</h2>${['“Add a card about …”', '“Connect it to Sunday”', '“Read this card”', '“Read the whole canvas”', '“Tidy up the cards”'].map(x => `<div class="row" style="padding:10px 0;border-bottom:1px solid var(--line)">${ic('mic')}<span style="flex:1">${x}</span></div>`).join('')}<p class="sm mut" style="margin-top:10px">Every command also has a button.</p></div>` : ''}`;
},
V04(st, th) {
  const T = st === 'tap', F = st === 'form';
  if (F) return SCREENS.V01('', th).replace(/<div class="rbar">[\s\S]*?<\/div><\/div>/, '') + `<div class="scrim"></div><div class="sheet"><div class="grab"></div>
    <div class="row" style="gap:10px">${orb('waiting', th, 36)}<h2 style="margin:0;flex:1">Who should get the message?</h2>${readBtn('idle')}</div>
    <div style="margin-top:12px">${hfield({ kind: 'SHORT', st: 'listening', label: 'Contact', value: '', partial: 'Anna Bērziņa…' })}</div>
    <div class="row" style="gap:8px;flex-wrap:wrap"><span class="chip">Anna Bērziņa</span><span class="chip">Anna (work)</span></div></div>`;
  return SCREENS.V01('', th).replace(/<div class="rbar">[\s\S]*?<\/div><\/div>/, '') + `<div class="scrim"></div><div class="sheet"><div class="grab"></div>
   <div class="row" style="gap:10px">${orb('approval_required', th, 36)}<h2 style="margin:0;flex:1">I’ll do exactly this</h2>${readBtn(T ? 'idle' : 'reading', 70)}</div>
   <ol style="margin:12px 0;padding-left:22px;font-size:15.5px;line-height:1.55"><li class="${T ? '' : 'read-done'}">Open Messages</li><li>${T ? 'Write to Anna Bērziņa:' : '<span class="word">Write</span> to Anna Bērziņa:'} “Running 10 minutes late”</li><li>Send it</li></ol>
   <div class="card s2 sm" style="margin-bottom:12px">${ic('privacy', '').replace('class="ic', 'style="width:15px;height:15px;vertical-align:-3px" class="ic')} Uses: Contacts, Messages</div>
   ${T ? `<div class="row" style="gap:8px;margin-bottom:10px"><span class="said">${ic('mic')}“Do it”</span></div><div class="card sm" style="margin-bottom:12px;box-shadow:inset 0 0 0 1.5px var(--att);background:color-mix(in srgb,var(--att) 10%,var(--s1))">Sending can’t be undone, so please tap <b>Send message</b> to confirm.</div>` : ''}
   <div class="row" style="gap:10px"><span class="btn ghost" style="flex:1">Not now</span><span class="btn" style="flex:1.3">${ic('send')}Send message</span></div></div>`;
},
V05(st, th) {
  if (st === 'secret') return `${status()}${topBar('Cloud provider', { backTo: 'V05' })}<div class="body">
    ${hfield({ kind: 'SHORT', label: 'Name', value: 'My provider' })}
    ${hfield({ kind: 'URL_ID', label: 'Server address', value: 'https://api.example.org/v1' })}
    ${hfield({ kind: 'SECRET', label: 'API key', value: 'x' })}
    ${hfield({ kind: 'STRUCTURED', label: 'Extra headers (JSON)', ph: 'Describe it — halo drafts it' })}
    </div>${nav('Settings')}`;
  return `${status()}${topBar('Morning routine', { backTo: 'V08' })}<div class="body">
   ${hfield({ kind: 'SHORT', label: 'Name', value: 'Morning routine' })}
   ${hfield({ kind: 'NUMBER', st: 'preview', label: 'Time', value: '07:00', heard: 'half past seven', num: '07:30' })}
   ${hfield({ kind: 'TEXT', st: 'listening', label: 'What should halo say?', value: 'Good morning! Today: ', partial: 'weather, first meeting…' })}
   ${hfield({ kind: 'SEARCH', label: 'Add a step', ph: 'Search actions' })}
  </div>${nav('Settings')}`;
},
V06(st, th) {
  const P = st === 'paused';
  return `${status()}${topBar('Notification', { backTo: 'V01', right: readBtn(P ? 'idle' : 'reading', 45) })}<div class="body" style="bottom:170px">
  <div class="card"><div class="row" style="gap:10px">${ic('calendar')}<b style="flex:1">Calendar · Dentist tomorrow</b><span class="sm mut">08:12</span></div>
   <p style="margin:12px 0 0;font-size:calc(16px*var(--fs));line-height:1.6"><span class="read-done">Reminder: dental check-up tomorrow at 10:30 at Smaida Clinic, Brīvības iela 40.</span> ${P ? 'Bring your insurance card.' : '<span class="word">Bring</span> <span class="read-todo">your insurance card.</span>'}</p></div>
  <div class="lbl">Reply</div>${hfield({ kind: 'TEXT', ph: 'Write or say a reply' })}
  </div>${readBar('Dentist tomorrow', P ? 'Paused · sentence 2 of 2' : 'Reading · sentence 2 of 2', P)}${nav('Activity')}`;
},
V07(st, th) {
  if (st === 'cloud') return SCREENS.V06('paused', th) + `<div class="scrim"></div><div class="sheet"><div class="grab"></div><div class="row" style="gap:10px">${orb('approval_required', th, 36)}<h2 style="margin:0">Cloud voice confirmation</h2></div>
    <p style="margin:12px 0">The selected cloud voice will receive this text. Do you want to send it?</p>
    <div class="card s2 sm" style="margin-bottom:14px">${ic('cloud', '').replace('class="ic', 'style="width:15px;height:15px;vertical-align:-3px;color:var(--att)" class="ic')} 1 notification · 96 characters</div>
    <div style="display:grid;gap:10px"><span class="btn full line">${ic('privacy')}Keep text private</span><span class="btn full ghost">${ic('cloud')}Send and read</span></div>
    <p class="sm mut" style="margin-top:10px">Or pick a voice on this phone in Voice &amp; reading.</p></div>`;
  return `${status()}${topBar('New note', { backTo: 'V02' })}<div class="body">
   ${hfield({ kind: 'TEXT', st: 'model', label: 'Note', ph: 'Write or speak' })}
   ${hfield({ kind: 'SHORT', st: 'perm', label: 'Title', ph: 'Title' })}
   <div class="hf"><div class="lb">After speaking</div><div class="box"><span class="val phd">Shopping list</span><span class="act mic">${ic('mic')}</span></div><div class="help">${ic('guide_me')}I didn’t hear anything. Try again closer to the phone.</div></div>
  </div>${nav('Notebook')}`;
},
V08(st, th) {
  const row = (t, s, r) => `<div class="row" style="padding:12px 0;border-bottom:1px solid var(--line)"><div style="flex:1"><b class="sm" style="font-size:15px">${t}</b>${s ? `<div class="sm mut">${s}</div>` : ''}</div>${r}</div>`;
  const sw = (on) => `<span style="width:46px;height:28px;border-radius:14px;background:${on ? 'var(--fill)' : 'var(--s2)'};position:relative;display:inline-block"><i style="position:absolute;top:3px;${on ? 'right' : 'left'}:3px;width:22px;height:22px;border-radius:50%;background:${on ? 'var(--on)' : 'var(--muted)'}"></i></span>`;
  return `${status()}${topBar('Voice & reading', { backTo: 'V05' })}<div class="body" style="bottom:80px">
  <div class="lbl" style="margin-top:0">Dictation</div>${row('Language', 'Same as the app · English', ic('chevron_right'))}${row('Show what I said before using it', 'Recommended', sw(true))}${row('Speech model', 'On this phone · ready', `<span class="tag">${ic('check')}Ready</span>`)}
  <div class="lbl">Reading</div>${row('Voice', 'On this phone · Ilze', readBtn('reading', 60))}${row('Speed', '', `<div class="row" style="gap:4px">${['0.75', '1', '1.25', '1.5'].map(x => `<span class="chip ${x === '1' ? 'sel' : ''}" style="height:32px;padding:0 9px">${x}×</span>`).join('')}</div>`)}
  ${row('Follow words while reading', '', sw(true))}${row('Read answers aloud automatically', 'Only when I spoke the question', sw(true))}
  <div class="lbl">Kids</div>${row('Read-along', 'Words light up; tap a word to hear it', sw(true))}${row('Pre-reader mode', 'Touch once to hear, twice to choose', sw(false))}
  </div>${nav('Settings')}`;
},
K15(st, th) {
  const pop = st === 'pop';
  const words = ['Fen', 'the', 'fox', 'hid', 'under', 'the', 'big', 'fern.', '“Too', 'dark,”', 'he', 'whispered.'];
  const cur = 6;
  return `${status()}${topBar('The fox and the lantern', { backTo: 'C34', right: `<span class="ib">${ic('read_mode')}</span>` })}<div class="body">
  <div class="card" style="padding:10px;margin-bottom:14px"><div style="height:150px;border-radius:14px;overflow:hidden;display:grid;place-items:center;background:var(--s2)"><div style="width:130px">${ILL.fox(th)}</div></div></div>
  <div class="storyp">${words.map((w, i) => pop && i === 2 ? `<span class="pop">${w}</span>` : !pop && i === cur ? `<span class="word">${w}</span>` : `<span class="w ${i < cur ? 'read-done' : pop ? '' : 'read-todo'}">${w}</span>`).join(' ')}</div>
  <div class="row" style="justify-content:space-between;margin-top:16px"><div class="dots"><i class="on"></i><i class="on"></i><i></i><i></i></div><span class="sm mut">Page 2 of 4</span></div>
  <div class="row" style="gap:10px;margin-top:14px"><span class="btn ghost" style="flex:1">${ic('pause')}Pause</span><span class="btn line" style="flex:1.3" ${go('K16', 'mode.echo')}>${ic('mic')}Read it myself</span></div>
  </div>${dock({ mode: pop ? 'speaking' : 'speaking', cap: pop ? '“fox”' : 'Tap any word to hear it again' })}`;
},
K16(st, th) {
  const L = st === 'listening', H = st === '' || st === 'heard';
  return `${status()}${topBar('Your turn', { backTo: 'K15' })}<div class="body">
  <div class="sm mut" style="margin-bottom:8px">Read it to me</div>
  <div class="storyp" style="font-size:calc(28px*var(--fs))"><span class="wash">“Too</span> <span class="wash">dark,”</span> ${L ? '<span class="w">he</span> <span class="w">whispered.</span>' : '<span class="wash">he</span> <span class="w" style="box-shadow:inset 0 -3px 0 var(--att)">whispered.</span>'}</div>
  ${H ? `<div class="saidby" style="margin-top:18px">${orb('speaking', th, 30, true)}<div class="bubble">I heard: “Too dark, he whis-pered.” Let’s try <b>whispered</b> together: whis · pered.</div></div>
    <div class="row" style="gap:8px;margin-top:12px"><span class="chip">${ic('speaker')}Hear “whispered”</span><span class="chip intent">${ic('chevron_right')}Next sentence</span></div>` : ''}
  </div>${dock({ mode: L ? 'listening' : 'idle', intent: L ? false : false, cap: L ? 'Listening… read at your own speed' : 'No scores — just practice' })}`;
},
K17(st, th) {
  return SCREENS.K00('first', th).replace('class="tile big" data-go="K10"', 'class="tile big" style="box-shadow:0 0 0 3px var(--ink),var(--shadow);transform:translateY(-4px)" data-go="K10"')
    .replace('Say “let’s learn” or tap a picture', 'Touch once to hear, twice to choose')
    + `<div style="position:absolute;left:22px;top:470px;z-index:9"><span class="said">${ic('speaker')}“Explore — find out something new”</span></div>`;
},
S45(st, th) {
  return `${status()}${topBar('Photosynthesis', { backTo: 'S42', right: `<span class="chip">${ic('read_mode')}Read</span>` })}<div class="body" style="bottom:100px">
  <div style="display:grid;place-items:center;position:relative;margin-top:4px"><img src="a/vr/halo_fx_listen_chapters_${th}.svg" style="width:220px;height:220px">
   <div style="position:absolute;text-align:center"><div class="sm mut">Chapter 3 of 5</div><b style="font:600 18px Sora">The Calvin cycle</b><div class="sm mut">4:12 left</div></div></div>
  <div class="row" style="justify-content:center;gap:14px;margin:6px 0 14px"><span class="ib s2">${ic('back')}</span><span class="mic" style="width:64px;height:64px;border-radius:50%;background:var(--fill);color:var(--on);display:grid;place-items:center">${ic('pause')}</span><span class="ib s2" style="transform:scaleX(-1)">${ic('back')}</span><span class="chip" style="height:36px">1.25×</span></div>
  <div class="card s2 sm" style="line-height:1.5"><b>Now:</b> “…the plant uses ATP and NADPH from the light reactions to <span class="word">fix</span> carbon dioxide into sugar.”</div>
  <div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap"><span class="chip intent">${ic('mic')}Ask about this</span><span class="chip">${ic('remember')}Save this part</span></div>
  </div>${dock({ kids: false, field: 'Answer questions by voice…' })}`;
},
S46(st, th) {
  const C = st === 'compared';
  return `${status()}${topBar('Review', { backTo: 'S44', right: `<span class="sm mut" style="margin-right:8px">4 of 6</span>` })}<div class="body">
  <div class="focus"><div class="row" style="justify-content:space-between"><div class="crumb" style="margin:0">Recall first</div>${readBtn(C ? 'idle' : 'reading', 80)}</div>
   <div class="q" style="margin-top:8px">What does the Calvin cycle produce?</div>
   ${hfield({ kind: 'TEXT', st: C ? 'idle' : 'preview', label: 'Your answer', value: C ? 'sugar, glucose from CO2' : '', prev: 'Sugar — glucose — from <u>CO two</u>' })}
   ${C ? `<div class="card s2" style="margin-top:4px"><span class="tag">${ic('check')}Close — you’ve got the main idea</span><p style="margin-top:8px;font-size:15px">Card: <b>G3P sugar</b> (used to build glucose), from CO₂.</p></div>` : ''}</div>
  ${C ? `<div class="lbl">How did it go?</div><div class="grid2" style="grid-template-columns:repeat(3,1fr);gap:8px"><span class="btn ghost">Again</span><span class="btn ghost">Hard</span><span class="btn intent">Got it</span></div>` : ''}
  </div>${dock({ kids: false, field: 'Say your answer…' })}`;
},
S47(st, th) {
  const L = st === 'listening';
  if (L) return SCREENS.S42('', th) + `<div class="scrim"></div><div class="sheet" style="height:480px"><div class="grab"></div><h2>Explain photosynthesis as if to a friend</h2>
    <div style="display:grid;place-items:center;margin:4px 0"><img src="a/vr/halo_fx_voice_wave_${th}.svg" style="width:160px;height:160px"></div>
    <p class="sub" style="text-align:center;margin:0 0 10px">“So plants take light, water and CO₂ and make sugar, and oxygen comes out as a <span class="partial">byproduct…</span>”</p>
    <div class="row" style="gap:10px"><span class="btn ghost" style="flex:1">${ic('pause')}Pause</span><span class="btn" style="flex:1">${ic('check')}Done</span></div><p class="sm mut" style="text-align:center;margin-top:8px">0:48 of up to 2:00 · local speech</p></div>`;
  return `${status()}${topBar('Explain it back', { backTo: 'S42' })}<div class="body">
  ${path('reflect', false)}
  <div class="focus"><div style="display:flex;gap:14px;align-items:center"><img src="a/vr/halo_fx_explain_back_${th}.svg" style="width:96px;height:96px"><div><div class="q" style="font-size:18px">You explained it well</div><div class="sm mut">0:48 · 3 key ideas</div></div></div>
   <div class="lbl">You covered</div><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip">${ic('check')}Light is the energy</span><span class="chip">${ic('check')}CO₂ + water → sugar</span><span class="chip">${ic('check')}Oxygen is released</span></div>
   <div class="lbl">Worth another look</div>
   <div class="row" style="padding:8px 0"><i class="attdot"></i><span style="flex:1">Where it happens (chloroplasts)</span></div>
   <div class="row" style="padding:8px 0"><i class="attdot"></i><span style="flex:1">The two stages</span></div></div>
  <div style="margin-top:14px"><span class="btn full intent" ${go('S42', 'learning.branch(gap)')}>${ic('connection')}Go deeper: the two stages</span></div>
  </div>${dock({ kids: false, field: 'Ask or explain again…' })}`;
},
});
