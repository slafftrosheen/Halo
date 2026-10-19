/* halo — Package E: how halo answers · models on this phone · local search · voice & reading settings · privacy · permissions · guided setup.
   User words only: no engine names, file names or model IDs on these screens (those live in Advanced). Honest fit badges: Works well here · Not checked yet · Won't run well here. */
const SCREENS_E = {}, STATES_E = {};
const fit = (k) => ({ ok: `<span class="tag">${ic('check')}${t('fit.ok', 'Works well here')}</span>`, unk: `<span class="tag neu">${t('fit.unk', 'Not checked yet')}</span>`, bad: `<span class="tag att">${t('fit.bad', 'Too big for this phone')}</span>`,
  you: `<span class="tag">${ic('check')}${t('fit.you', 'You tested it')}</span>`, dmg: `<span class="tag err">${t('fit.dmg', 'Damaged')}</span>` }[k]);
const bar = (p, att) => `<div style="height:6px;border-radius:3px;background:var(--line);overflow:hidden;margin-top:6px"><i style="display:block;height:100%;width:${p}%;background:${att ? 'var(--att)' : 'var(--ink)'}"></i></div>`;
const pg = (title, inner, o = {}) => `${o.aura ? `<div class="aura ${o.aura}"></div>` : ''}${sb()}${topBar(title, { back: true, right: o.right })}<div class="body" style="bottom:92px">${inner}</div>${nav('settings')}${o.over || ''}`;
const radio = (on) => `<span style="width:24px;height:24px;border-radius:50%;box-shadow:inset 0 0 0 2px ${on ? 'var(--ink)' : 'var(--line)'};display:grid;place-items:center;flex:none">${on ? '<i style="width:12px;height:12px;border-radius:50%;background:var(--ink)"></i>' : ''}</span>`;
const step = (k, n = 9) => `<div style="display:flex;gap:4px;padding:0 16px">${Array.from({ length: n }, (_, i) => `<i style="flex:1;height:4px;border-radius:2px;background:${i < k ? 'var(--ink)' : 'var(--line)'}"></i>`).join('')}</div>`;

/* ---------- O01 Guided setup (9 real stages: WELCOME USE PERMISSIONS AI SPEECH DEVICES PRIVACY READINESS FIRST) ---------- */
STATES_E.O01 = ['welcome', 'use', 'permissions', 'answers', 'speech', 'devices', 'privacy', 'ready', 'first'];
SCREENS_E.O01 = (st, th) => {
  const k = STATES_E.O01.indexOf(st) + 1;
  const head = `${sb()}<div class="top"><span class="ib back">${ic('back')}</span><h1 style="font-size:16px" class="mut">${t('o.setup', 'Setup')} · ${k} / 9</h1><span class="btn text sm">${t('o.saveLeave', 'Save and leave')}</span></div>${step(k)}`;
  const foot = (main, sec, g = true) => `<div style="position:absolute;left:16px;right:16px;bottom:16px;display:flex;gap:10px;z-index:3">${sec ? `<span class="btn ghost" style="flex:1">${sec}</span>` : ''}<span class="btn ${g ? 'guide' : ''}" style="flex:2">${main}</span></div>`;
  const B = (inner) => `<div class="body nonav" style="top:100px;bottom:84px">${inner}</div>`;
  if (st === 'welcome') return `<div class="aura idle"></div>${sb()}<div class="body nonav" style="top:60px;bottom:84px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">
    <span class="orbwrap" style="width:120px;height:120px">${orb('idle', th, 116)}</span><img class="wm" src="a/wm/halo_wordmark_${th}.svg" style="height:40px;margin:22px 0 10px"><div class="ttl">${t('o.welcomeT', 'Your assistant, on your phone first')}</div>
    <div class="sm mut">${t('o.welcomeB', 'A few choices and you’re ready. Nothing leaves this phone unless you decide it should.')}</div></div>${foot(t('o.start', 'Let’s start'))}`;
  if (st === 'use') return `${head}${B(`<div class="ttl">${t('o.useT', 'How would you like to start?')}</div>${[[t('o.u1', 'Everyday help'), t('o.u1b', 'Questions, notes, reminders'), 1], [t('o.u2', 'Learning'), t('o.u2b', 'Study and practice'), 0], [t('o.u3', 'For my child'), t('o.u3b', 'Starts in Kids mode'), 0]].map(([a, b, on]) => `<div class="card" style="padding:12px 14px;margin-top:10px;${on ? 'box-shadow:0 0 0 2px var(--ink)' : ''}"><div class="row">${radio(on)}<div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div></div></div>`).join('')}<div class="xs mut" style="margin-top:10px">${t('o.useNote', 'You can change this any time.')}</div>`)}${foot(t('common.next', 'Next'))}`;
  if (st === 'permissions') return `${head}${B(`<div class="ttl">${t('o.permT', 'You choose the access')}</div><div class="sm mut" style="margin-bottom:10px">${t('o.permB', 'Only the microphone now. Everything else is asked when you first use it.')}</div>
    <div class="card list" style="padding:0 14px">${srow('mic', t('o.mic', 'Microphone'), t('o.micSub', 'To hear you. Listening stays on this phone.'), { right: `<span class="btn sm" style="height:36px">${t('o.allow', 'Allow')}</span>` })}${srow('notifications', t('o.notif', 'Notifications'), t('o.later', 'Asked later, when needed'), { right: '' })}${srow('camera', t('o.camera', 'Camera'), t('o.later', 'Asked later, when needed'), { right: '' })}</div>`)}${foot(t('common.next', 'Next'), t('o.skip', 'Skip'), false)}`;
  if (st === 'answers') return `${head}${B(`<div class="ttl">${t('o.ansT', 'Where should halo think?')}</div><div class="sm mut" style="margin-bottom:10px">${t('o.ansB', 'Pick one. You can add more later.')}</div>
    ${[['on_device', t('o.a1', 'On this phone'), t('o.a1b', 'Private. Needs a download (about 400 MB).'), fit('ok')], ['lan', t('o.a2', 'A computer at home'), t('o.a2b', 'Faster. Uses your Wi-Fi.'), ''], ['cloud', t('o.a3', 'A cloud service'), t('o.a3b', 'Fastest. Your questions go to that service.'), '']].map(([i, a, b, f]) => `<div class="card" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div></div>${f ? `<div style="margin-top:6px">${f}</div>` : ''}</div>`).join('')}
    <div class="xs mut" style="margin-top:10px">${t('o.noneSel', 'Nothing is chosen for you.')}</div>`)}${foot(t('common.next', 'Next'), t('o.later2', 'Decide later'), false)}`;
  if (st === 'speech') return `<div class="aura listening"></div>${head}${B(`<div style="display:grid;place-items:center;margin:8px 0">${orb('speaking', th, 84)}</div><div class="ttl" style="text-align:center">${t('o.hearT', 'Did you hear halo?')}</div>
    <div class="card s2" style="text-align:center;font:500 18px Sora">“${t('o.hearPhrase', 'This is halo. Can you hear me?')}”</div><div class="xs mut" style="text-align:center;margin-top:8px">${t('o.hearNote', 'Played on the phone speaker. Only your yes marks speaking as working.')}</div>`)}${foot(t('o.yesHeard', 'Yes, I heard it'), t('o.noHeard', 'No'))}`;
  if (st === 'devices') return `${head}${B(`<div class="ttl">${t('o.devT', 'Your devices')}</div><div class="sm mut" style="margin-bottom:10px">${t('o.devB', 'Optional. Connect glasses, a robot or headphones now or later.')}</div>
    ${kc('glasses', t('o.glasses', 'Smart glasses'))}${kc('robot', 'BuddyBot')}${kc('headphones', t('o.headphones', 'Headphones'))}`)}${foot(t('o.skipNow', 'Not now'), '', false)}`;
  if (st === 'privacy') return `${head}${B(`<div class="ttl">${t('o.privT', 'Your data, your choice')}</div><div class="card list" style="padding:0 14px">
    ${srow('cloud', t('o.askCloud', 'Ask before anything goes to the cloud'), t('o.recommended', 'Recommended'), { right: sw(true) })}${srow('memory', t('o.remember', 'Let halo suggest things to remember'), t('o.askFirst', 'Always asks first'), { right: sw(true) })}${srow('read_mode', t('o.screenRead', 'Use what’s on screen when I call halo'), t('o.androidSetting', 'Android asks separately'), { right: sw(false) })}</div>`)}${foot(t('common.next', 'Next'))}`;
  if (st === 'ready') return `<div class="aura success"></div>${head}${B(`<div class="ttl">${t('o.readyT', 'Let’s check what works')}</div><div class="card"><ul class="steps">
    <li class="done"><span class="dot">${ic('check')}</span><span class="grow"><b class="sm">${t('ck.answers', 'Answers')}</b><div class="xs mut">${t('o.r1', 'On this phone · tested')}</div></span></li>
    <li class="done"><span class="dot">${ic('check')}</span><span class="grow"><b class="sm">${t('ck.listen', 'Listening')}</b><div class="xs mut">${t('o.r2', 'English · tested')}</div></span></li>
    <li class="done"><span class="dot">${ic('check')}</span><span class="grow"><b class="sm">${t('ck.speak', 'Speaking')}</b><div class="xs mut">${t('ck.heard', 'You heard it')}</div></span></li>
    <li class="skip"><span class="dot"></span><span class="grow"><b class="sm">${t('o.r4', 'Devices')}</b><div class="xs mut">${t('o.r4b', 'Skipped · add any time')}</div></span></li></ul></div>`)}${foot(t('o.tryIt', 'Try halo'))}`;
  return `<div class="aura idle"></div>${sb()}<div class="top"><h1>${t('o.firstT', 'Your first request')}</h1></div><div class="body" style="top:84px">
    <div class="sm mut" style="margin-bottom:10px">${t('o.firstB', 'Say it or tap one. This is a real answer, not a demo.')}</div><div class="col">${[t('o.f1', 'What time is it?'), t('o.f2', 'Remind me to call Anna at 18:00'), t('o.f3', 'Explain black holes simply')].map((x, i) => `<span class="chip ${i === 0 ? 'guide' : ''}" style="height:44px;justify-content:flex-start">${x}</span>`).join('')}</div></div>${composer()}${nav('home')}`;
};

/* ---------- O02 Starter downloads (never automatic) ---------- */
STATES_E.O02 = ['picks', 'downloading', 'low-storage'];
SCREENS_E.O02 = (st, th) => {
  const item = (i, a, b, size, f, o = '') => `<div class="card" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div>${o || `<span class="xs mut">${size}</span>`}</div><div class="row" style="gap:6px;margin-top:6px">${f}</div>${st === 'downloading' && i === 'model' ? bar(64) + `<div class="row xs mut" style="margin-top:4px"><span class="grow">${t('o.dl', '260 of 400 MB · about 2 min on Wi-Fi')}</span><span class="btn text sm" style="height:28px">${t('o.pause', 'Pause')}</span></div>` : ''}</div>`;
  return `${sb()}${topBar(t('o.picksT', 'Downloads to start'), { back: true })}<div class="body nonav" style="bottom:84px">
    <div class="sm mut">${t('o.picksB', 'Recommended for this phone. Nothing downloads until you tap.')}</div>
    ${st === 'low-storage' ? `<div class="banner att" style="margin-top:10px">${ic('storage')}<span class="sm">${t('o.lowT', 'Only 520 MB free. Pick fewer items or free up space first.')}</span></div>` : ''}
    ${item('model', t('o.p1', 'Everyday answers'), t('o.p1b', 'Quick, private answers on this phone'), '400 MB', `${fit('ok')}<span class="tag neu">EN · LV · RU</span>`)}
    ${item('mic', t('o.p2', 'Listening · accurate'), t('o.p2b', 'Understands you without internet'), '480 MB', `${fit('ok')}<span class="tag neu">${t('o.lang99', '90+ languages')}</span>`)}
    ${item('speaker', t('o.p3', 'Natural voices'), t('o.p3b', 'halo’s voice, on this phone'), '310 MB', `${fit('unk')}<span class="tag neu">EN · ${t('o.more', '+4')}</span>`)}
    ${item('search', t('o.p4', 'Search your Notebook'), t('o.p4b', 'Find notes by meaning, offline'), '200 MB', `${st === 'low-storage' ? fit('bad') : fit('ok')}`)}</div>
    <div style="position:absolute;left:16px;right:16px;bottom:16px;display:flex;gap:10px;z-index:3"><span class="btn ghost" style="flex:1">${t('o.skipNow', 'Not now')}</span><span class="btn" style="flex:2">${st === 'downloading' ? t('o.downloading', 'Downloading 1 of 3…') : t('o.dl3', 'Download 3 · 1.2 GB')}</span></div>`;
};

/* ---------- I01 How halo answers ---------- */
STATES_E.I01 = ['ok', 'none', 'cloud'];
SCREENS_E.I01 = (st, th) => {
  const hero = st === 'none' ? `<div class="card guide"><div class="row">${orb('permission_missing', th, 36)}<div class="grow"><div class="b">${t('i.noneT', 'halo can’t answer yet')}</div><div class="xs mut">${t('i.noneB', 'Choose where answers come from.')}</div></div></div><div class="btns"><span class="btn sm">${t('i.setUp', 'Set up answers')}</span></div></div>`
    : `<div class="card"><div class="row">${orb('success', th, 36)}<div class="grow"><div class="b">${st === 'cloud' ? t('i.cloudT', 'Your cloud service') : t('i.okT', 'On this phone')}</div><div class="xs mut">${st === 'cloud' ? t('i.cloudB', 'Answers come from the service you connected · asks before sending files') : t('i.okB', 'Everyday answers · tested today')}</div></div></div>
      <div class="row" style="gap:6px;margin-top:8px">${st === 'cloud' ? `<span class="where att">${ic('cloud')}${t('i.leaves', 'Questions leave this phone')}</span>` : `<span class="where">${ic('on_device')}${t('i.stays', 'Nothing leaves this phone')}</span>`}</div></div>`;
  return pg(t('st.intel', 'How halo answers'), `${hero}${sgroup(t('i.g', 'Answers'), srow('lan', t('i.sources', 'Where answers come from'), st === 'none' ? t('i.nothing', 'Nothing connected') : t('i.sourcesSub', 'This phone · 1 cloud service')) + srow('model', t('i.choose', 'Choose a model'), t('i.chooseSub', 'Everyday answers')) + srow('download', t('i.downloads', 'Downloads on this phone'), '2 · 880 MB') + srow('search', t('i.search', 'Search your Notebook'), t('i.searchSub', 'Ready · 412 notes')))}
    ${sgroup(t('i.g2', 'Photos & privacy'), srow('image', t('i.images', 'Photos and images'), t('i.imagesSub', 'Seeing photos: on · Making images: off')) + srow('privacy', t('i.what', 'What left this phone'), t('i.whatSub', '3 questions this week')))}`, { aura: st === 'none' ? 'needs' : 'idle' });
};

/* ---------- I02 Where answers come from ---------- */
STATES_E.I02 = ['list', 'add'];
SCREENS_E.I02 = (st, th) => {
  const list = `${sgroup(t('i.phone', 'On this phone'), srow('on_device', t('o.p1', 'Everyday answers'), t('i.tested', 'Tested today'), { right: fit('ok') }))}
    ${sgroup(t('i.network', 'Your network'), srow('lan', t('i.homePc', 'Home computer'), t('i.notChecked', 'Saved · not checked'), { right: fit('unk') }))}
    ${sgroup(t('i.cloudG', 'Cloud'), srow('cloud', 'OpenRouter', t('i.connectedTested', 'Connected · tested yesterday'), { right: '' }) + srow('cloud', 'Anthropic', t('i.keyRejected', 'Key not accepted'), { right: `<span class="tag err">${t('i.fix', 'Fix')}</span>` }))}`;
  if (st === 'add') return pg(t('i.sources', 'Where answers come from'), list, { over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('i.addT', 'Add a source')}</h2>
    ${kc('on_device', t('i.addPhone', 'On this phone'), { sub: t('i.addPhoneB', 'Download a model') })}${kc('lan', t('i.addLan', 'A computer at home'), { sub: t('i.addLanB', 'halo looks for it on your Wi-Fi') })}${kc('cloud', t('i.addCloud', 'A cloud service'), { sub: t('i.addCloudB', 'Sign in or paste a key') })}</div>` });
  return pg(t('i.sources', 'Where answers come from'), list + `<span class="btn ghost full" style="margin-top:12px">${ic('add')}${t('i.addT', 'Add a source')}</span>`);
};

/* ---------- I03 Source detail (cloud key / home computer) ---------- */
STATES_E.I03 = ['cloud-key', 'tested', 'rejected', 'home-pc'];
SCREENS_E.I03 = (st, th) => {
  if (st === 'home-pc') return pg(t('i.homePc', 'Home computer'), `<div class="card"><div class="row">${orb('waiting', th, 30)}<div class="grow"><div class="b sm">${t('i.looking', 'Looking on your Wi-Fi…')}</div><div class="xs mut">${t('i.lookingB', 'Make sure the computer is on and on the same network.')}</div></div></div></div>
    ${sgroup(t('i.found', 'Found'), srow('lan', 'Desktop · Living room', t('i.foundSub', 'Ready to connect'), { right: `<span class="btn sm" style="height:36px">${t('i.connect', 'Connect')}</span>` }))}
    <div class="btn text sm" style="padding:0;margin-top:10px">${t('i.manual', 'Enter address yourself')}</div>`);
  const field = `<div class="lbl">${t('i.key', 'Your key')}</div><div class="field ${st === 'rejected' ? 'bad' : 'focus'}">${ic('privacy')}<span class="grow">••••••••••••3f9a</span><span class="btn text sm">${t('i.paste', 'Paste')}</span></div>
    ${st === 'rejected' ? `<div class="help bad">${t('i.rejectedB', 'OpenRouter didn’t accept this key. Check it was copied fully, or create a new one.')}</div>` : `<div class="help">${t('i.keyHelp', 'Stored encrypted on this phone. Never shown again.')}</div>`}`;
  const test = st === 'tested' ? `<div class="card s2" style="margin-top:12px"><div class="row">${fit('ok')}<span class="xs mut">${t('i.realQ', 'Real test question · 1.2 s')}</span></div><div class="sm" style="margin-top:6px">“${t('i.realA', 'Riga is the capital of Latvia.')}”</div></div>` : '';
  return pg('OpenRouter', `<div class="sm mut">${t('i.cloudNote', 'Questions you send here leave this phone. halo asks before sending files or photos.')}</div>${field}
    <div class="lbl">${t('i.modelL', 'Model')}</div>${srow('model', t('i.modelPick', 'Balanced'), t('i.modelPickSub', 'Good for most questions'), { right: '' })}${test}
    <div class="btns">${st === 'rejected' ? `<span class="btn text sm">${t('i.getKey', 'How to get a key')}</span>` : ''}<span class="btn ${st === 'tested' ? 'ghost' : 'guide'}">${t('i.testReal', 'Test with a real question')}</span>${st === 'tested' ? `<span class="btn">${t('i.useIt', 'Use for answers')}</span>` : ''}</div>
    <div class="btn text sm" style="padding:0;color:var(--err);margin-top:6px">${t('i.removeKey', 'Remove key')}</div>`);
};

/* ---------- I04 Choose a model ---------- */
STATES_E.I04 = ['list', 'detail'];
SCREENS_E.I04 = (st, th) => {
  const m = (a, b, where, f) => `<div class="card" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div>${f}</div><div class="row" style="gap:6px;margin-top:6px">${where}</div></div>`;
  const list = `<div class="seg">${[t('m.all', 'All'), t('m.fast', 'Fast'), t('m.careful', 'Careful'), t('m.photos', 'Photos'), t('m.phone', 'On phone')].map((x, i) => `<span class="chip ${i === 0 ? 'sel' : 'line'}">${x}</span>`).join('')}</div>
    ${m(t('m.m1', 'Everyday answers'), t('m.m1b', 'Quick and private'), `<span class="where">${ic('on_device')}${t('where.phone', 'On this phone')}</span>`, `${fit('ok')}`)}
    ${m(t('m.m2', 'Careful thinker'), t('m.m2b', 'Slower, better at hard problems'), `<span class="where">${ic('on_device')}${t('where.phone', 'On this phone')}</span>`, fit('bad'))}
    ${m(t('m.m3', 'Balanced'), t('m.m3b', 'Good for most questions'), `<span class="where att">${ic('cloud')}OpenRouter</span>`, `<span class="tag">${t('m.inUse', 'In use')}</span>`)}
    ${m(t('m.m4', 'Sees photos'), t('m.m4b', 'Understands pictures and pages'), `<span class="where att">${ic('cloud')}OpenRouter</span>`, fit('unk'))}`;
  if (st === 'detail') return pg(t('i.choose', 'Choose a model'), list, { over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('m.m2', 'Careful thinker')}</h2><div class="sm mut">${t('m.m2b', 'Slower, better at hard problems')}</div>
    <div class="banner att" style="margin-top:10px">${ic('storage')}<span class="sm">${t('m.tooBig', 'Needs more memory than this phone has. It would be very slow or stop.')}</span></div>
    <div class="list sm" style="margin-top:8px"><div class="li" style="padding:6px 0">${ic('download')}<span class="grow">2.1 GB</span></div><div class="li" style="padding:6px 0">${ic('language')}<span class="grow">EN · LV · RU · PL</span></div></div>
    <div class="btns"><span class="btn text sm">${t('m.about', 'About this model')}</span><span class="btn ghost">${t('m.useCloud', 'Use a cloud one instead')}</span></div></div>` });
  return pg(t('i.choose', 'Choose a model'), list);
};

/* ---------- I05 Downloads on this phone ---------- */
STATES_E.I05 = ['list', 'downloading', 'damaged', 'remove'];
SCREENS_E.I05 = (st, th) => {
  const d = (i, a, size, f, extra = '') => `<div class="card" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${size}</div></div>${f}</div>${extra}</div>`;
  const body = `<div class="card s2"><div class="row sm"><span class="grow">${t('dl.space', 'halo uses 1.1 GB · 9.4 GB free')}</span></div>${bar(12)}</div>
    ${d('model', t('o.p1', 'Everyday answers'), '400 MB', fit('you'))}
    ${d('mic', t('o.p2', 'Listening · accurate'), '480 MB', st === 'damaged' ? fit('dmg') : fit('ok'), st === 'damaged' ? `<div class="sm" style="margin-top:6px">${t('dl.dmgB', 'Part of this download is missing, so listening may fail.')}</div><div class="btns" style="margin-top:6px"><span class="btn sm guide">${t('dl.repair', 'Download again')}</span></div>` : '')}
    ${d('speaker', t('o.p3', 'Natural voices'), st === 'downloading' ? '190 of 310 MB' : '310 MB', st === 'downloading' ? `<span class="btn text sm">${t('dl.cancel', 'Cancel')}</span>` : fit('unk'), st === 'downloading' ? bar(61) + `<div class="xs mut" style="margin-top:4px">${t('dl.wifi', 'Paused automatically if you leave Wi-Fi')}</div>` : '')}
    <div class="row" style="gap:10px;margin-top:12px"><span class="btn ghost sm">${ic('add')}${t('dl.more', 'More downloads')}</span><span class="btn text sm">${t('dl.import', 'Add from a file')}</span></div>`;
  if (st === 'remove') return pg(t('i.downloads', 'Downloads on this phone'), body, { over: `<div class="scrim in"></div><div class="dlg"><h2>${t('dl.removeT', 'Remove Natural voices?')}</h2><div class="sm mut">${t('dl.removeB', 'Frees 310 MB. halo will use the phone’s built-in voice until you choose another.')}</div><div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn danger">${t('dl.remove', 'Remove')}</span></div></div>` });
  return pg(t('i.downloads', 'Downloads on this phone'), body);
};

/* ---------- I07 Search your Notebook (local search) ---------- */
STATES_E.I07 = ['off', 'indexing', 'ready', 'unsupported'];
SCREENS_E.I07 = (st, th) => {
  if (st === 'off') return pg(t('i.search', 'Search your Notebook'), `<div class="empty">${orb('idle', th, 76)}<div class="t2">${t('ls.offT', 'Find notes by what they mean')}</div><div class="sm mut">${t('ls.offB', 'Search “trip ideas” and find your Kuldīga note, even without those words. Works offline. 200 MB.')}</div><span class="btn guide" style="margin-top:8px">${t('ls.turnOn', 'Turn on')}</span></div>`);
  if (st === 'unsupported') return pg(t('i.search', 'Search your Notebook'), `<div class="empty">${orb('warning', th, 76)}<div class="t2">${t('ls.unsT', 'This phone can’t run it well')}</div><div class="sm mut">${t('ls.unsB', 'Normal word search still works everywhere in Notebook.')}</div></div>`);
  const src = sgroup(t('ls.what', 'What it searches'), srow('new_note', t('ls.notes', 'Notes'), '412', { right: sw(true) }) + srow('file_doc', t('ls.files', 'Files and PDFs'), '38', { right: sw(true) }) + srow('saved_chat', t('ls.chats', 'Saved chats'), '', { right: sw(false) }) + srow('memory', t('ls.mem', 'Memories'), '', { right: sw(false) }));
  if (st === 'indexing') return pg(t('i.search', 'Search your Notebook'), `<div class="card"><div class="row">${orb('thinking', th, 30)}<div class="grow"><div class="b sm">${t('ls.indexing', 'Getting your notes ready… 186 of 450')}</div><div class="xs mut">${t('ls.indexingB', 'Only while charging or idle. You can keep using halo.')}</div></div></div>${bar(41)}</div>${src}`, { aura: 'thinking' });
  return pg(t('i.search', 'Search your Notebook'), `<div class="card"><div class="row">${fit('ok')}<span class="sm grow" style="margin-left:6px">${t('ls.ready', 'Ready · stays on this phone')}</span></div><div class="xs mut" style="margin-top:4px">${t('ls.readyB', 'New notes are added automatically.')}</div></div>${src}`);
};

/* ---------- I08 Photos & what left this phone ---------- */
STATES_E.I08 = ['log', 'images', 'ask-cloud'];
SCREENS_E.I08 = (st, th) => {
  if (st === 'images') return pg(t('i.images', 'Photos and images'), `${sgroup(t('img.see', 'Seeing photos'), srow('image', t('img.seeT', 'Let halo look at photos you share'), t('img.seeSub', 'OpenRouter · asks each time'), { right: sw(true) }))}
    ${sgroup(t('img.make', 'Making images'), srow('create_image', t('img.makeT', 'Let halo create images'), t('img.makeSub', 'Needs a cloud service that makes images'), { right: sw(false) }))}
    <div class="xs mut" style="margin-top:8px">${t('img.note', 'Seeing and making are separate. A service that understands photos may not make them.')}</div>`);
  if (st === 'ask-cloud') return pg(t('i.what', 'What left this phone'), `<div class="card list" style="padding:0 14px">${srow('cloud', t('pol.ask', 'Ask before sending to the cloud'), t('o.recommended', 'Recommended'), { right: sw(true) })}${srow('file_doc', t('pol.files', 'Files and photos'), t('pol.always', 'Always ask'), { right: '' })}${srow('lan', t('pol.home', 'Home computer'), t('pol.noAsk', 'Doesn’t ask · stays on your Wi-Fi'), { right: '' })}</div>`);
  const r = (i, a, b, c) => `<div class="li"><div class="av">${ic(i)}</div><div class="grow"><div class="sm">${a}</div><div class="xs mut">${b}</div></div><span class="xs mut">${c}</span></div>`;
  return pg(t('i.what', 'What left this phone'), `<div class="sm mut" style="margin-bottom:10px">${t('log.sub', 'Everything halo sent to another service. Kept for 30 days on this phone.')}</div>
    <div class="card list" style="padding:0 14px">${r('cloud', t('log.q', 'A question'), 'OpenRouter · “Summarise this 40-page PDF”', t('log.y', 'Yesterday'))}${r('image', t('log.photo', 'A photo you chose'), 'OpenRouter · receipt', 'Fri')}${r('cloud', t('log.q', 'A question'), 'OpenRouter · weather', 'Thu')}</div>
    <div class="row" style="gap:10px;margin-top:12px"><span class="btn ghost sm">${t('log.policy', 'When to ask me')}</span><span class="btn text sm" style="color:var(--err)">${t('log.clear', 'Clear history')}</span></div>`);
};

/* ---------- VO01 Voice & reading ---------- */
STATES_E.VO01 = ['hub'];
SCREENS_E.VO01 = (st, th) => pg(t('st.voice', 'Voice & reading'), `${sgroup(t('v.listen', 'Listening'), srow('mic', t('v.listenL', 'How halo hears you'), t('v.listenSub', 'English · accurate · on this phone')) + srow('voice', t('v.hands', 'Hands-free'), t('v.handsSub', 'Answer without tapping after halo asks'), { right: sw(true) }) + srow('stop', t('v.interrupt', 'Interrupt halo when I speak'), '', { right: sw(true) }))}
  ${sgroup(t('v.speak', 'Speaking'), srow('speaker', t('v.voiceL', 'halo’s voice'), 'Aria · English') + srow('read_mode', t('v.readNew', 'Read new answers aloud'), t('v.readNewSub', 'Only when you spoke first'), { right: sw(true) }) + srow('audio_route', t('v.where', 'Play on'), t('v.whereSub', 'Wherever you spoke')))}
  ${sgroup(t('v.cloudG', 'Cloud voices'), srow('cloud', 'ElevenLabs', t('v.notConnected', 'Not connected · optional')))}`);

/* ---------- VO02 Listening ---------- */
STATES_E.VO02 = ['settings', 'mic-test', 'download'];
SCREENS_E.VO02 = (st, th) => {
  if (st === 'mic-test') return pg(t('v.listenL', 'How halo hears you'), `<div style="display:grid;place-items:center;margin:10px 0">${orb('capturing_voice', th, 80)}</div><div class="card" style="text-align:center;font:500 20px Sora">${kwords('The quick brown fox jumps over the lazy dog', 6)}</div>
    <div class="xs mut" style="text-align:center;margin-top:8px">${t('v.testB', 'Say anything. The words appear as halo understands them, on this phone.')}</div><div class="btns" style="justify-content:center"><span class="btn ghost">${ic('stop')}${t('comp.stop', 'Stop')}</span></div>`, { aura: 'listening' });
  const lang = (l, s, on) => `<div class="li" style="padding:9px 2px">${radio(on)}<div class="grow"><div class="b sm">${l}</div><div class="xs mut">${s}</div></div></div>`;
  return pg(t('v.listenL', 'How halo hears you'), `<div class="row xs mut" style="margin-bottom:8px">${ic('privacy')}${t('v.never', 'Listening never leaves this phone.')}</div>
    <div class="lbl">${t('v.lang', 'Language')}</div><div class="card list" style="padding:0 14px">${lang('English', t('v.ready', 'Ready'), 1)}${lang('Latviešu', t('v.ready', 'Ready'), 0)}${lang('Русский', t('v.ready', 'Ready'), 0)}</div>
    <div class="lbl">${t('v.quality', 'Quality')}</div><div class="card list" style="padding:0 14px">${lang(t('v.accurate', 'Accurate'), '480 MB · ' + t('v.inUse', 'in use'), 1)}${st === 'download' ? `<div class="li" style="padding:9px 2px">${radio(0)}<div class="grow"><div class="b sm">${t('v.light', 'Light')}</div><div class="xs mut">${t('v.dlg', 'Downloading 92 of 150 MB')}</div>${bar(61)}</div></div>` : lang(t('v.light', 'Light'), '150 MB · ' + t('v.faster', 'faster, less accurate'), 0)}</div>
    <span class="btn ghost full" style="margin-top:12px">${ic('mic')}${t('v.try', 'Try listening')}</span>`);
};

/* ---------- VO03 halo's voice ---------- */
STATES_E.VO03 = ['voices', 'hear-test', 'fallback'];
SCREENS_E.VO03 = (st, th) => {
  const v = (n, s, on, f) => `<div class="li" style="padding:9px 2px">${radio(on)}<div class="grow"><div class="b sm">${n}</div><div class="xs mut">${s}</div></div>${f || ''}<span class="ib s2" style="width:40px;height:40px" aria-label="${t('v.preview', 'Preview')}">${ic('play')}</span></div>`;
  const list = `<div class="lbl">English</div><div class="card list" style="padding:0 14px">${v('Aria', t('v.natural', 'Natural · on this phone'), 1, fit('you'))}${v('Leo', t('v.natural', 'Natural · on this phone'), 0, fit('unk'))}${v(t('v.phoneVoice', 'Phone’s voice'), t('v.builtIn', 'Built in · always available'), 0)}</div>
    <div class="lbl">Latviešu</div><div class="card list" style="padding:0 14px">${v(t('v.phoneVoice', 'Phone’s voice'), t('v.onlyOption', 'The only Latvian voice right now'), 0)}</div>
    ${st === 'fallback' ? `<div class="banner info" style="margin-top:10px">${ic('speaker')}<span class="sm">${t('v.fbB', 'If Aria isn’t available, halo uses the phone’s voice and tells you once.')}</span></div>` : ''}`;
  if (st === 'hear-test') return pg(t('v.voiceL', 'halo’s voice'), list, { aura: 'listening', over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><div class="row">${orb('speaking', th, 34)}<h2 style="margin:0">${t('o.hearT', 'Did you hear halo?')}</h2></div><div class="sm mut" style="margin:6px 0">${t('v.hearB', 'Playing Leo on Pixel Buds.')}</div><div class="btns"><span class="btn ghost">${t('o.noHeard', 'No')}</span><span class="btn">${t('o.yesHeard', 'Yes, I heard it')}</span></div></div>` });
  return pg(t('v.voiceL', 'halo’s voice'), list);
};

/* ---------- VO04 Voice packs ---------- */
STATES_E.VO04 = ['packs', 'downloading'];
SCREENS_E.VO04 = (st, th) => pg(t('v.packs', 'Voice packs'), `<div class="sm mut" style="margin-bottom:6px">${t('v.packsB', 'Voices that work without internet.')}</div>
  <div class="card" style="padding:12px 14px;margin-top:8px"><div class="row"><div class="av">${ic('speaker')}</div><div class="grow"><div class="b sm">${t('o.p3', 'Natural voices')}</div><div class="xs mut">EN · DE · FR · ES · IT · 310 MB</div></div>${fit('you')}</div><div class="xs mut" style="margin-top:6px">${t('v.youTested', 'You heard Aria on this phone on 19 Oct.')}</div></div>
  <div class="card" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="av">${ic('speaker')}</div><div class="grow"><div class="b sm">${t('v.compact', 'Compact voices')}</div><div class="xs mut">EN · ZH · 120 MB</div></div>${st === 'downloading' ? `<span class="btn text sm">${t('dl.cancel', 'Cancel')}</span>` : `<span class="btn sm ghost" style="height:36px">${t('v.get', 'Download')}</span>`}</div>${st === 'downloading' ? bar(35) : ''}</div>
  <div class="btn text sm" style="padding:0;margin-top:12px">${t('dl.import', 'Add from a file')}</div>`);

/* ---------- VO05 Cloud voice (ElevenLabs) ---------- */
STATES_E.VO05 = ['connect', 'tested', 'limit'];
SCREENS_E.VO05 = (st, th) => pg('ElevenLabs', `<div class="sm mut">${t('el.note', 'Optional. Text halo reads aloud is sent to ElevenLabs. Off until you turn it on.')}</div>
  <div class="lbl">${t('i.key', 'Your key')}</div><div class="field ${st === 'connect' ? 'focus' : ''}">${ic('privacy')}<span class="grow">${st === 'connect' ? '' : '••••••••••c21e'}</span><span class="btn text sm">${t('i.paste', 'Paste')}</span></div><div class="help">${t('i.keyHelp', 'Stored encrypted on this phone. Never shown again.')}</div>
  ${st === 'tested' ? `<div class="card s2" style="margin-top:12px"><div class="row">${fit('you')}<span class="xs mut grow" style="margin-left:6px">${t('el.played', 'Played “Rachel” · you heard it')}</span></div></div>` : ''}
  ${st === 'limit' ? `<div class="banner att" style="margin-top:12px">${ic('unavailable')}<span class="sm">${t('el.limit', 'Your ElevenLabs plan has no characters left this month. halo is using Aria until it resets.')}</span></div>` : ''}
  <div class="btns">${st === 'connect' ? `<span class="btn guide">${t('el.test', 'Play a sample')}</span>` : `<span class="btn ghost">${t('el.test', 'Play a sample')}</span><span class="btn">${st === 'limit' ? t('el.plan', 'Open my plan') : t('el.use', 'Use for reading aloud')}</span>`}</div>`);

/* ---------- P01 What halo can use ---------- */
STATES_E.P01 = ['categories', 'detail'];
SCREENS_E.P01 = (st, th) => {
  const c = (i, a, mode) => srow(i, a, '', { right: `<span class="chip ${mode === 'Never' ? 'line' : 'sel'}" style="height:30px;font-size:12px">${mode}</span>` });
  const body = `<div class="sm mut" style="margin-bottom:6px">${t('ctx.sub', 'What halo may look at to help. Used for the request, not stored unless you save it.')}</div>
    ${sgroup(t('ctx.phone', 'On your phone'), c('calendar', t('ctx.cal', 'Calendar'), t('ctx.allow', 'Allow')) + c('notifications', t('ctx.notif', 'Notifications'), t('ctx.ask', 'Ask')) + c('read_mode', t('ctx.screen', 'Screen when you call halo'), t('ctx.ask', 'Ask')) + c('image', t('ctx.photos', 'Photos'), t('ctx.ask', 'Ask')))}
    ${sgroup(t('ctx.halo', 'In halo'), c('notebook', 'Notebook', t('ctx.allow', 'Allow')) + c('memory', t('st.memory', 'Memory'), t('ctx.allow', 'Allow')) + c('saved_chat', t('ctx.chats', 'Earlier chats'), t('ctx.never', 'Never')))}`;
  if (st === 'detail') return pg(t('st.use', 'What halo can use'), body, { over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('ctx.notif', 'Notifications')}</h2><div class="col" style="margin-top:8px">${[[t('ctx.allow', 'Allow'), t('ctx.allowB', 'halo can read them when it helps')], [t('ctx.ask', 'Ask'), t('ctx.askB', 'halo asks each time')], [t('ctx.never', 'Never'), t('ctx.neverB', 'halo never reads them')]].map(([a, b], i) => `<div class="li">${radio(i === 1)}<div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div></div>`).join('')}</div><div class="xs mut" style="margin-top:6px">${t('ctx.cloudNote', 'Sending anything to a cloud service still asks first.')}</div></div>` });
  return pg(t('st.use', 'What halo can use'), body);
};

/* ---------- P02 Permissions ---------- */
STATES_E.P02 = ['list', 'explain', 'blocked'];
SCREENS_E.P02 = (st, th) => {
  const p = (i, a, s, k) => srow(i, a, s, { right: k === 'on' ? `<span class="tag">${ic('check')}${t('perm.on', 'Allowed')}</span>` : k === 'off' ? `<span class="tag neu">${t('perm.off', 'Not allowed')}</span>` : `<span class="tag att">${t('perm.blocked', 'Blocked')}</span>` });
  const body = `<div class="sm mut" style="margin-bottom:6px">${t('perm.sub', 'What Android lets halo do. halo never changes these by itself.')}</div><div class="card list" style="padding:0 14px">
    ${p('mic', t('o.mic', 'Microphone'), t('perm.micSub', 'Hear you'), 'on')}${p('notifications', t('o.notif', 'Notifications'), t('perm.notifSub', 'Show reminders and results'), 'on')}${p('camera', t('o.camera', 'Camera'), t('perm.camSub', 'Photos you take for halo'), 'off')}
    ${p('calendar', t('ctx.cal', 'Calendar'), t('perm.calSub', 'See and add events'), 'on')}${p('bluetooth', 'Bluetooth', t('perm.btSub', 'Connect glasses and robots'), st === 'blocked' ? 'blocked' : 'off')}${p('phone_action', t('perm.a11y', 'Act in other apps'), t('perm.a11ySub', 'Tap and type for you, with your OK'), 'off')}</div>`;
  if (st === 'explain') return pg(t('st.perm', 'Permissions'), body, { over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><div class="row"><div class="av">${ic('camera')}</div><h2 style="margin:0">${t('perm.camT', 'Let halo use the camera?')}</h2></div><div class="sm mut" style="margin:8px 0">${t('perm.camB', 'Only when you take a photo for halo — homework, a receipt, a whiteboard. halo never opens the camera by itself.')}</div><div class="btns"><span class="btn text">${t('next.notNow', 'Not now')}</span><span class="btn">${t('perm.continue', 'Continue')}</span></div><div class="xs mut">${t('perm.androidNext', 'Android will ask you next.')}</div></div>` });
  if (st === 'blocked') return pg(t('st.perm', 'Permissions'), body, { over: `<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('perm.btT', 'Bluetooth is blocked')}</h2><div class="sm mut" style="margin:6px 0">${t('perm.btB', 'You chose “Don’t ask again” earlier, so halo can’t ask. You can allow it in Android settings, then come back.')}</div><div class="btns"><span class="btn text">${t('next.notNow', 'Not now')}</span><span class="btn">${t('att.openSettings', 'Open Android settings')}</span></div></div>` });
  return pg(t('st.perm', 'Permissions'), body);
};

/* ---------- P03 Memory settings ---------- */
STATES_E.P03 = ['settings', 'forget-all'];
SCREENS_E.P03 = (st, th) => {
  const body = `${sgroup(t('st.memory', 'Memory'), srow('memory', t('mem.useIt', 'Use memory in conversations'), t('mem.useSub', '42 things'), { right: sw(true) }) + srow('remember', t('mem.suggest', 'Suggest things to remember'), t('o.askFirst', 'Always asks first'), { right: sw(true) }) + srow('history', t('mem.keep', 'Keep task history'), t('mem.keepSub', '90 days'), {}))}
    ${sgroup(t('mem.review', 'Review'), srow('notebook', t('mem.open', 'See what halo remembers'), t('mem.openSub', 'In Notebook › Memory')))}
    <div class="btn text sm" style="padding:0;color:var(--err);margin-top:12px">${t('mem.forgetAll', 'Forget everything…')}</div>`;
  if (st === 'forget-all') return pg(t('st.memory', 'Memory'), body, { over: `<div class="scrim in"></div><div class="dlg"><h2>${t('mem.forgetAllT', 'Forget all 42 memories?')}</h2><div class="sm mut">${t('mem.forgetAllB', 'halo starts fresh. This can’t be undone. Your notes, chats and files are not deleted.')}</div><div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn danger">${t('mem.forgetAllGo', 'Forget all')}</span></div></div>` });
  return pg(t('st.memory', 'Memory'), body);
};
window.SCREENS_E = SCREENS_E; window.STATES_E = STATES_E;
