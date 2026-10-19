/* halo — Settings, finalised for users. One list, no tabs. Everything technical lives behind ONE row: Advanced.
   Developer tools (labs, diagnostics, sandboxes) only appear inside Advanced after an explicit switch + confirm. */
const SCREENS_ST = {}, STATES_ST = {};
const srow = (i, title, sub, o = {}) => `<div class="li" style="padding:9px 2px" ${o.go ? `data-go="${o.go}"` : ''}><div class="av" style="width:38px;height:38px;${o.dev ? 'color:#C9B8FF' : ''}">${ic(i)}</div>
  <div class="grow"><div class="b sm">${title}${o.tag || ''}</div>${sub ? `<div class="xs mut ell">${sub}</div>` : ''}</div>${o.right !== undefined ? o.right : `<span style="opacity:.5;transform:rotate(-90deg);display:inline-block">${ic('chevron_down')}</span>`}</div>`;
const sgroup = (label, rows) => `<div class="lbl">${label}</div><div class="card list" style="padding:0 14px">${rows}</div>`;
const sw = (on) => `<span style="width:46px;height:28px;border-radius:14px;background:${on ? 'var(--fill)' : 'var(--s3)'};position:relative;flex:none"><i style="position:absolute;top:3px;${on ? 'right' : 'left'}:3px;width:22px;height:22px;border-radius:50%;background:${on ? 'var(--on)' : 'var(--s1)'}"></i></span>`;
const labTag = () => ` <span class="tag" style="background:rgba(201,184,255,.18);color:#C9B8FF;height:18px;font-size:9.5px;vertical-align:1px">${t('st.lab', 'Lab')}</span>`;

STATES_ST.ST01 = ['top', 'issue', 'bottom', 'search', 'search-empty'];
SCREENS_ST.ST01 = (st, th) => {
  const head = `${sb()}${topBar(t('nav.settings', 'Settings'), { right: `<span class="ib" aria-label="${t('st.search', 'Search settings')}">${ic('search')}</span>` })}`;
  if (st === 'search' || st === 'search-empty') {
    const q = st === 'search' ? 'voice' : 'gguf';
    const res = st === 'search' ? sgroup(t('st.results', '3 results'), srow('voice', t('st.voice', 'Voice & reading'), t('st.voiceSub', 'Listening, read aloud, halo’s voice')) + srow('mini_halo', t('st.assistant', 'Android assistant'), t('st.matchVoice', 'Talk to halo from any app')) + srow('kids_mode', t('st.kids', 'Kids mode'), t('st.matchKidsVoice', 'Reading voice for kids')))
      : `<div class="empty">${orb('idle', th, 72)}<div class="t2">${t('st.noMatch', 'Nothing called that in Settings')}</div><div class="sm mut">${t('st.noMatchSub', 'Technical options live in Advanced.')}</div><span class="btn ghost sm" style="margin-top:6px">${t('st.openAdvanced', 'Open Advanced')}</span></div>`;
    return `${sb()}<div class="top"><span class="ib back">${ic('back')}</span><div class="field focus" style="flex:1;min-height:44px">${ic('search')}<span class="sm">${q}</span><span class="grow"></span>${ic('close')}</div></div><div class="body" style="bottom:92px">${res}</div>${nav('settings')}`;
  }
  const status = st === 'issue'
    ? `<div class="card guide" data-go="ST04"><div class="row"><span class="orbwrap att" style="width:36px;height:36px">${orb('permission_missing', th, 34)}</span><div class="grow"><div class="b sm">${t('st.issueT', 'halo can’t hear you from other apps')}</div><div class="xs mut">${t('st.issueB', 'One permission is off.')}</div></div><span class="btn sm">${t('st.fix', 'Fix')}</span></div></div>`
    : `<div class="card s2" data-go="ST04"><div class="row"><span class="orbwrap" style="width:36px;height:36px">${orb('success', th, 34)}</span><div class="grow"><div class="b sm">${t('st.okT', 'Everything’s working')}</div><div class="xs mut">${t('st.okB', 'Answers on this phone · voice ready')}</div></div></div></div>`;
  const g1 = sgroup(t('st.g.halo', 'halo'), srow('intelligence', t('st.intel', 'How halo answers'), t('st.intelSub', 'On this phone')) + srow('voice', t('st.voice', 'Voice & reading'), t('st.voiceSub2', 'Aria · English')) + srow('appearance', t('st.appearance', 'Appearance'), t('st.appSub', 'Dark · Mint')) + srow('language', t('st.language', 'Language'), 'English'));
  const g2 = sgroup(t('st.g.privacy', 'Privacy'), srow('privacy', t('st.use', 'What halo can use'), t('st.useSub', 'Apps, screen, files, cloud')) + srow('permission', t('st.perm', 'Permissions'), t('st.permSub', '6 of 8 allowed')) + srow('memory', t('st.memory', 'Memory'), t('st.memSub', '42 things halo remembers')) + srow('storage', t('st.storage', 'Storage & backup'), t('st.storSub', 'Last backup yesterday')));
  const g3 = sgroup(t('st.g.everyday', 'Everyday'), srow('mini_halo', t('st.nearby', 'halo nearby'), t('st.nearbySub', 'Floating orb, widget')) + srow('phone', t('st.assistant', 'Android assistant'), t('st.assistSub', 'On · hold power')) + srow('notifications', t('st.notif', 'Notifications'), t('st.notifSub', 'Digest at 20:00')) + srow('kids_mode', t('st.kids', 'Kids mode'), t('st.kidsSub', 'Off')));
  const g4 = sgroup(t('st.g.connect', 'Connections'), srow('integrations', t('st.apps', 'Apps & services'), t('st.appsSub', 'Calendar, Spotify')) + srow('skills', t('st.skills', 'What halo can do in apps'), t('st.skillsSub', '12 actions')) + srow('routines', t('st.routines', 'Routines'), t('st.routSub', '2 on')));
  const g5 = sgroup(t('st.g.help', 'Help'), srow('diagnostics', t('st.checkup', 'Check-up'), t('st.checkupSub', 'See what works, fix what doesn’t'), { go: 'ST04' }) + srow('guided_setup', t('st.setupAgain', 'Set up again'), t('st.setupAgainSub', 'Keeps your choices')) + srow('mini_halo', t('st.about', 'About halo'), 'Version 2.0'));
  const adv = `<div class="card s2" style="margin-top:14px" data-go="ST02">${srow('lab', t('st.advanced', 'Advanced'), t('st.advancedSub', 'For technical users'), { dev: true })}</div>`;
  if (st === 'bottom') return `${head}<div class="body" style="bottom:92px;display:flex;flex-direction:column;justify-content:flex-end">${g4}${g5}${adv}<div class="xs mut" style="text-align:center;margin:12px 0 4px">halo 2.0</div></div>${nav('settings')}`;
  return `${head}<div class="body" style="bottom:92px">${status}${g1}${g2}</div>${nav('settings')}`;
};

STATES_ST.ST02 = ['default', 'confirm', 'developer'];
SCREENS_ST.ST02 = (st, th) => {
  const head = `${sb()}${topBar(t('st.advanced', 'Advanced'), { back: true })}`;
  const intro = `<div class="sm mut" style="margin:-2px 2px 4px">${t('adv.intro', 'Technical settings. You don’t need any of these for everyday use.')}</div>`;
  const tech = sgroup(t('adv.g.tech', 'Technical'), srow('model', t('adv.roles', 'Model roles'), t('adv.rolesSub', 'Pick a model per kind of task')) + srow('integrations', t('adv.mcp', 'Custom servers (MCP)'), t('adv.mcpSub', 'Address, token, tools')) + srow('export', t('adv.report', 'Technical report'), t('adv.reportSub', 'Safe to share · no keys or messages')));
  const devRow = `<div class="card" style="margin-top:12px;box-shadow:0 0 0 1px rgba(201,184,255,.35)">${srow('lab', t('adv.dev', 'Developer tools'), t('adv.devSub', 'Labs, diagnostics and test sandboxes'), { dev: true, right: sw(st === 'developer') })}</div>`;
  const labs = st === 'developer' ? sgroup(t('adv.g.labs', 'Labs & diagnostics'),
    srow('runtime_status', t('adv.runtime', 'Runtime status'), '', { dev: true, tag: labTag() }) + srow('activity', t('adv.workload', 'Workload diagnostics'), '', { dev: true, tag: labTag() }) + srow('phone', t('adv.assistDiag', 'Assistant diagnostics'), '', { dev: true, tag: labTag() }) +
    srow('image', t('adv.vision', 'Vision Lab'), '', { dev: true, tag: labTag() }) + srow('mic', t('adv.speech', 'Speech Lab'), '', { dev: true, tag: labTag() }) + srow('speaker', t('adv.audio', 'Local audio test'), '', { dev: true, tag: labTag() }) + srow('phone_action', t('adv.sandboxes', 'Phone & automation sandboxes'), '', { dev: true, tag: labTag() })) : '';
  const body = `${head}<div class="body" style="bottom:92px">${intro}${tech}${devRow}${labs}</div>${nav('settings')}`;
  if (st === 'confirm') return `${body}<div class="scrim in"></div><div class="dlg"><h2>${t('adv.confirmT', 'Show developer tools?')}</h2>
    <div class="sm mut">${t('adv.confirmB', 'These are for testing halo. They can use more battery and show unfinished features. Everyday screens don’t change.')}</div>
    <div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn">${t('adv.show', 'Show developer tools')}</span></div></div>`;
  return body;
};

/* ST04 — Check-up (user-facing System check). One fix at a time; audible test needs a human yes. Report lives in Advanced. */
STATES_ST.ST04 = ['checking', 'one-fix', 'hear', 'all-good'];
SCREENS_ST.ST04 = (st, th) => {
  const item = (s, a, b) => `<li class="${s}"><span class="dot">${s === 'done' ? ic('check') : s === 'fail' ? ic('close') : ''}</span><span class="grow"><b class="sm">${a}</b><div class="xs mut">${b}</div></span></li>`;
  const list = {
    checking: [['done', t('ck.answers', 'Answers'), t('ck.answersOk', 'On this phone')], ['done', t('ck.listen', 'Listening'), t('ck.listenOk', 'English')], ['now', t('ck.speak', 'Speaking'), t('ck.checking', 'Checking…')], ['', t('ck.assist', 'Android assistant'), ''], ['', t('ck.notif', 'Notifications'), '']],
    'one-fix': [['done', t('ck.answers', 'Answers'), t('ck.answersOk', 'On this phone')], ['done', t('ck.listen', 'Listening'), t('ck.listenOk', 'English')], ['done', t('ck.speak', 'Speaking'), t('ck.heard', 'You heard it')], ['fail', t('ck.assist', 'Android assistant'), t('ck.assistBad', 'Speech permission is off')], ['done', t('ck.notif', 'Notifications'), t('ck.notifOk', 'Connected')]],
    hear: [['done', t('ck.answers', 'Answers'), t('ck.answersOk', 'On this phone')], ['done', t('ck.listen', 'Listening'), t('ck.listenOk', 'English')], ['wait', t('ck.speak', 'Speaking'), t('ck.played', 'Played a test sentence')], ['', t('ck.assist', 'Android assistant'), ''], ['', t('ck.notif', 'Notifications'), '']],
    'all-good': [['done', t('ck.answers', 'Answers'), t('ck.answersOk', 'On this phone')], ['done', t('ck.listen', 'Listening'), t('ck.listenOk', 'English')], ['done', t('ck.speak', 'Speaking'), t('ck.heard', 'You heard it')], ['done', t('ck.assist', 'Android assistant'), t('ck.assistOk', 'Hold power to talk')], ['done', t('ck.notif', 'Notifications'), t('ck.notifOk', 'Connected')]],
  }[st];
  const o = { checking: 'thinking', 'one-fix': 'permission_missing', hear: 'speaking', 'all-good': 'success' }[st];
  const headline = { checking: t('ck.t1', 'Checking halo…'), 'one-fix': t('ck.t2', 'One thing to fix'), hear: t('ck.t3', 'Did you hear halo?'), 'all-good': t('ck.t4', 'Everything’s working') }[st];
  const actions = { checking: `<span class="btn ghost">${t('comp.stop', 'Stop')}</span>`, 'one-fix': `<span class="btn guide full">${t('ck.fixAssist', 'Allow speech permission')}</span>`,
    hear: `<span class="btn ghost">${t('ck.no', 'No, I didn’t')}</span><span class="btn">${t('ck.yes', 'Yes, I heard it')}</span>`, 'all-good': `<span class="btn ghost">${t('ck.again', 'Check again')}</span>` }[st];
  return `<div class="aura ${st === 'one-fix' ? 'needs' : st === 'all-good' ? 'success' : 'thinking'}"></div>${sb()}${topBar(t('st.checkup', 'Check-up'), { back: true })}<div class="body" style="bottom:92px">
   <div style="display:grid;place-items:center;margin:6px 0 10px"><span class="orbwrap ${st === 'one-fix' ? 'att' : ''}" style="width:84px;height:84px">${orb(o, th, 80)}</span><div class="ttl" style="margin-top:12px;text-align:center">${headline}</div>
   ${st === 'hear' ? `<div class="sm mut" style="text-align:center">${t('ck.hearSub', 'halo only marks speaking as working when you say so.')}</div>` : ''}</div>
   <div class="card"><ul class="steps">${list.map(x => item(...x)).join('')}</ul></div><div class="btns" style="justify-content:center">${actions}</div></div>${nav('settings')}`;
};
window.SCREENS_ST = SCREENS_ST; window.STATES_ST = STATES_ST;
