/* halo Screen — "Chat is where you talk to halo. Screen is where halo works for you."
   Entry: a Chat | Screen switch at the top of the Home tab (5 tabs unchanged). One responsive surface: phone → tablet portrait/landscape → ambient.
   Cards come from a fixed widget registry; halo may PROPOSE layout changes, never generate UI. Honest states for every provider. */
const SCREENS_SC = {}, STATES_SC = {};
const fresh = (k, txt) => `<span class="fresh ${k || ''}"><i></i>${txt}</span>`;
const scHead = (th, o = {}) => `<div class="top" style="gap:8px"><span class="segm" role="tablist"><span ${o.chat ? 'class="on"' : ''}>${t('scr.chat', 'Chat')}</span><span ${o.chat ? '' : 'class="on"'}>${t('scr.screen', 'Screen')}</span></span>
  ${o.chat ? '<span class="grow"></span>' : `<span class="chip line" style="height:32px;padding:0 10px">${o.profile || t('scr.morning', 'Morning')}${ic('chevron_down')}</span><span class="grow"></span>
  <span class="ib" style="width:42px;height:42px" aria-label="${t('scr.ask', 'Ask halo to change this screen')}">${ic('mic')}</span><span class="ib" style="width:42px;height:42px" aria-label="${t('scr.customize', 'Customize')}">${ic('draw')}</span><span class="ib" style="width:42px;height:42px" aria-label="${t('scr.full', 'Full screen')}">${ic('fit_all')}</span>`}
  ${o.chat ? `<span class="ib" aria-label="${t('home.updates', 'Updates')}">${ic('notifications')}<span class="badge"></span></span>` : ''}</div>`;
/* --- card library (registry ids in comments) --- */
const C_ = {
  clock: (o = {}) => `<div class="sc ${o.cls || ''}"><div class="sh">${ic('timer')}${t('w.clock', 'Now')}${o.pin ? `<span class="pin">${ic('tag')}</span>` : ''}</div><div class="big">07:42</div><div class="sm mut">${t('w.date', 'Monday 20 October')}</div></div>`, // widget.clock
  weatherSetup: (o = {}) => `<div class="sc setup ${o.cls || ''}"><div class="sh">${ic('cloud')}${t('w.weather', 'Weather')}</div><div class="sm">${t('w.weatherSetup', 'Pick a city or allow location to see the forecast.')}</div><div class="foot"><span class="btn sm ghost" style="height:36px">${t('w.setUp', 'Set up')}</span></div></div>`, // widget.weather (no provider yet)
  weather: (o = {}) => `<div class="sc ${o.cls || ''}"><div class="sh">${ic('cloud')}Riga</div><div class="row"><span class="big" style="font-size:calc(34px*var(--fs))">11°</span><span class="sm mut">${t('w.cloudy', 'Cloudy · rain after 15:00')}</span></div><div class="foot">${fresh('', t('w.upd0715', 'Updated 07:15'))}</div></div>`,
  tasks: (o = {}) => `<div class="sc ${o.w || 'w2'} ${o.cls || ''}"><div class="sh">${ic('task')}${t('w.tasks', 'Next up')}${o.pin ? `<span class="pin">${ic('tag')}</span>` : ''}</div>
    <div class="list sm ${o.priv ? 'mask' : ''}"><div class="li" style="padding:5px 0">${ic('reminder')}<span class="grow">Dentist</span><span class="xs mut">10:30</span></div><div class="li" style="padding:5px 0">${ic('saved_reply')}<span class="grow">Reply to Pēteris</span><span class="xs mut">${t('w.today', 'today')}</span></div><div class="li" style="padding:5px 0">${ic('practise')}<span class="grow">Review 6 Spanish cards</span><span class="xs mut">4 min</span></div></div></div>`, // widget.tasks
  calendar: (o = {}) => `<div class="sc ${o.cls || ''}"><div class="sh">${ic('calendar')}${t('w.cal', 'Calendar')}</div><div class="${o.priv ? 'mask' : ''}"><div class="t2">Mārtiņš · call</div><div class="sm mut">16:00 – 16:30</div></div><div class="foot">${fresh('', t('w.device', 'From your phone'))}</div></div>`, // widget.calendar
  mail: (o = {}) => `<div class="sc w2 ${o.cls || ''}"><div class="sh">${ic('routines')}${t('w.mail', 'Morning email summary')}<span class="pin" style="opacity:1">${o.state === 'failed' ? `<span class="tag att">${t('w.lastFailed', 'Last update failed')}</span>` : o.state === 'updated' ? `<span class="tag">${t('w.new3', '3 new')}</span>` : ''}</span></div>
    <div class="sm ${o.priv ? 'mask' : ''}" style="line-height:1.45">${o.state === 'updated' ? '<b>Bank:</b> statement ready. <b>Anna:</b> Sunday at 13:00 confirmed. <b>School:</b> trip form due Friday.' : '<b>Anna:</b> Sunday lunch at 13:00. <b>Landlord:</b> heating check Thursday. 4 newsletters skipped.'}</div>
    <div class="foot">${o.state === 'failed' ? fresh('stale', t('w.lastGood', 'Showing yesterday 07:00')) : fresh('', o.state === 'updated' ? t('w.upd0702', 'Updated 07:02 · Gmail') : t('w.yest', 'Yesterday 07:01 · Gmail'))}<span class="grow"></span><span class="ib" style="width:36px;height:36px">${ic('speaker')}</span></div></div>`, // widget.automation_result (stable id = routine id)
  mailSetup: () => `<div class="sc w2 setup"><div class="sh">${ic('routines')}${t('w.mail', 'Morning email summary')}</div><div class="sm">${t('w.mailSetup', 'Connect Gmail and halo will put a short summary here every morning.')}</div><div class="foot"><span class="btn sm ghost" style="height:36px">${t('w.connect', 'Connect Gmail')}</span><span class="xs mut">${t('w.optional', 'Optional')}</span></div></div>`,
  interests: (o = {}) => `<div class="sc w2 ${o.cls || ''}"><div class="sh">${ic('explain')}${t('w.interest', 'Black holes')}</div>
    ${o.state === 'nosource' ? `<div class="sm">${t('w.noSource', 'halo needs a reading source to find new articles. Meanwhile, here’s what you saved.')}</div><div class="list sm"><div class="li" style="padding:5px 0">${ic('new_note')}<span class="grow">Event horizons — my notes</span></div></div><div class="foot"><span class="btn sm ghost" style="height:36px">${t('w.chooseSource', 'Choose a source')}</span></div>`
    : `<div class="list sm"><div class="li" style="padding:6px 0"><div style="width:44px;height:44px;border-radius:10px;background:linear-gradient(135deg,#221a4a,#ff9f6b);flex:none"></div><div class="grow"><div class="b sm">Webb spots a black hole from the early universe</div><div class="xs mut">NASA · 2 days ago</div></div></div>
      <div class="li" style="padding:6px 0"><div style="width:44px;height:44px;border-radius:10px;background:linear-gradient(135deg,#0c2a4a,#8ec5ff);flex:none"></div><div class="grow"><div class="b sm">How black holes “sing”</div><div class="xs mut">ESA · last week</div></div></div></div>
      <div class="foot">${o.state === 'offline' ? fresh('off', t('w.offlineCached', 'Offline · saved 8 h ago')) : fresh('', t('w.upd0600', 'Updated 06:00'))}</div>`}</div>`, // widget.interests
  notebook: (o = {}) => `<div class="sc ${o.cls || ''}"><div class="sh">${ic('notebook')}${t('w.continue', 'Continue')}</div><div class="t2">Trip to Kuldīga</div><div class="xs mut">Note · 10 min ago</div></div>`, // widget.notebook
  activity: (o = {}) => `<div class="sc ${o.cls || ''}"><div class="sh">${ic('check')}${t('w.done', 'halo finished')}</div><div class="sm">${t('w.doneSum', '2 tasks overnight')}</div><div class="xs mut">${t('w.doneSub', 'Backup · voice pack')}</div></div>`, // widget.halo_activity
};
const screenPage = (th, inner, o = {}) => `<div class="aura idle"></div>${sb()}${scHead(th, o)}<div class="body" style="bottom:92px;top:88px">${inner}</div>${o.nonav ? '' : nav('home')}`;
const dash = (o = {}) => `<div class="sgrid" style="--cols:2"><div class="slbl">${t('scr.now', 'Now')}</div>${C_.clock({ pin: true })}${o.weatherReady ? C_.weather() : C_.weatherSetup()}
  <div class="slbl">${t('scr.attention', 'Needs attention')}</div>${C_.tasks({ pin: true, priv: o.priv })}${o.mail === 'setup' ? C_.mailSetup() : C_.mail({ state: o.mail, priv: o.priv, cls: o.mail === 'updated' ? 'cardupd' : '' })}
  ${o.more ? `<div class="slbl">${t('scr.forYou', 'For you')}</div>${C_.interests({ state: o.interest })}<div class="slbl">${t('scr.cont', 'Continue')}</div>${C_.notebook()}${C_.activity()}` : ''}</div>`;

/* SC01 — entry + first run */
STATES_SC.SC01 = ['home-switch', 'intro', 'starter-ready'];
SCREENS_SC.SC01 = (st, th) => {
  if (st === 'home-switch') return `<div class="aura idle"></div>${sb()}${scHead(th, { chat: true })}<div class="body">${userMsg('Plan dinner for four, nothing spicy')}${haloMsg(th, '<p><b>Lemon chicken traybake</b> with potatoes and green beans. 50 minutes, one tray.</p>')}</div>${composer()}${nav('home')}
    <div class="live rise" style="bottom:200px"><span class="orbwrap" style="width:22px;height:22px">${orb('idle', th, 20)}</span><span>${t('scr.discover', 'New: your Screen')}</span><span class="btn sm" style="height:30px;padding:0 10px">${t('scr.look', 'Open')}</span><span class="x">${ic('close')}</span></div>`;
  if (st === 'intro') return `<div class="aura idle"></div>${sb()}${scHead(th, { profile: '—' }).replace(/<span class="ib"[^]*?<\/span>/g, '')}<div class="body" style="bottom:92px;top:88px">
    <div class="ttl">${t('scr.introT', 'Your space for updates, tasks and things you care about')}</div><div class="sm mut" style="margin-bottom:12px">${t('scr.introB', 'Pick a starting point. You can change everything later, and nothing needs an account.')}</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">${[[t('scr.essentials', 'Essentials'), t('scr.essSub', 'Clock · next up · calendar'), 1], [t('scr.brief', 'Daily brief'), t('scr.briefSub', 'Summaries · next up'), 0], [t('scr.interests', 'My interests'), t('scr.intSub', 'Topics · saved reading'), 0], [t('scr.simple', 'Start simple'), t('scr.simpleSub', 'Just the clock'), 0]].map(([a, b, g], k) => `<div class="card ${g ? 'guide' : ''}" style="padding:10px"><div class="miniprev">${k === 3 ? '<i style="grid-column:span 2"></i>' : '<i></i><i></i><i style="grid-column:span 2"></i>'}</div><div class="b sm" style="margin-top:8px">${a}</div><div class="xs mut">${b}</div></div>`).join('')}</div></div>${nav('home')}`;
  return screenPage(th, `<div class="banner info" style="margin-bottom:10px">${ic('guide_me')}<span class="sm grow">${t('scr.readyB', 'Essentials is ready. Tap Customize or just ask halo to change it.')}</span>${ic('close')}</div>
    <div class="sgrid" style="--cols:2">${C_.clock({ cls: 'rise' })}${C_.calendar({ cls: 'rise d1' })}${C_.tasks({ cls: 'rise d2' })}${C_.weatherSetup({ cls: 'rise d3' })}</div>`, { profile: t('scr.essentials', 'Essentials') });
};

/* SC02 — populated dashboard (phone) + states */
STATES_SC.SC02 = ['top', 'scrolled', 'private', 'offline'];
SCREENS_SC.SC02 = (st, th) => {
  if (st === 'scrolled') return screenPage(th, `<div class="sgrid" style="--cols:2"><div class="slbl">${t('scr.forYou', 'For you')}</div>${C_.interests({})}<div class="slbl">${t('scr.cont', 'Continue')}</div>${C_.notebook()}${C_.activity()}</div>`);
  if (st === 'offline') return screenPage(th, `<div class="banner info" style="margin-bottom:8px">${ic('disconnected')}<span class="sm">${t('scr.offline', 'You’re offline. Saved cards stay; live ones show when they last updated.')}</span></div>
    <div class="sgrid" style="--cols:2">${C_.clock({ pin: true })}<div class="sc"><div class="sh">${ic('cloud')}Riga</div><div class="row"><span class="big" style="font-size:calc(34px*var(--fs));opacity:.6">11°</span></div><div class="foot">${fresh('stale', t('w.stale3h', 'Stale · 3 h ago'))}</div></div>${C_.tasks({ pin: true })}${C_.interests({ state: 'offline' })}</div>`);
  return screenPage(th, dash({ mail: 'ready', weatherReady: true, priv: st === 'private' }) + (st === 'private' ? `<div class="xs mut" style="text-align:center;margin-top:8px">${ic('privacy')} ${t('scr.privHint', 'Private details hidden · unlock to show')}</div>` : ''));
};

/* SC03 — customize */
STATES_SC.SC03 = ['editing', 'resize', 'removed'];
SCREENS_SC.SC03 = (st, th) => {
  const head = `${sb()}<div class="top"><span class="btn text sm">${t('scr.reset', 'Reset…')}</span><h1 style="text-align:center">${t('scr.customize', 'Customize')}</h1><span class="btn sm">${t('common.done', 'Done')}</span></div>`;
  const hd = (extra = '') => `<div class="handle"><span class="ib" aria-label="${t('scr.moveUp', 'Move up')}">${ic('chevron_down').replace('<svg', '<svg style="transform:rotate(180deg)"')}</span><span class="ib" aria-label="${t('scr.moveDown', 'Move down')}">${ic('chevron_down')}</span>${extra}<span class="ib" aria-label="${t('scr.remove', 'Remove from Screen')}">${ic('close')}</span></div>`;
  const ed = (html, extra) => html.replace('class="sc ', 'class="sc edit ').replace(/<\/div>$/, hd(extra) + '</div>');
  const grid = st === 'removed' ? `${ed(C_.clock())}${ed(C_.weatherSetup())}${ed(C_.tasks())}` : `${ed(C_.clock())}${ed(C_.weatherSetup())}${ed(C_.tasks({}), `<span class="ib" aria-label="${t('scr.size', 'Change size')}">${ic('fit_all')}</span>`)}${ed(C_.mail({ state: 'ready' }))}`;
  return `${head}<div class="body" style="top:88px;bottom:${st === 'resize' ? 250 : 100}px"><div class="sgrid" style="--cols:2">${grid}</div></div>
    ${st === 'resize' ? `<div class="sheet in"><div class="grab"></div><h2>${t('scr.sizeT', 'Size of “Next up”')}</h2><div class="row" style="gap:8px">${[[t('scr.compact', 'Compact'), '1 item'], [t('scr.standard', 'Standard'), '3 items'], [t('scr.expanded', 'Expanded'), '6 items']].map(([a, b], k) => `<div class="card ${k === 1 ? '' : 's2'}" style="flex:1;padding:10px;text-align:center;${k === 1 ? 'box-shadow:0 0 0 2px var(--ink)' : ''}"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div>`).join('')}</div></div>`
    : `<div style="position:absolute;left:16px;right:16px;bottom:16px;z-index:3"><span class="btn full guide">${ic('add')}${t('scr.addCard', 'Add a card')}</span></div>`}
    ${st === 'removed' ? `<div class="toast" style="bottom:84px"><span class="grow">${t('scr.removedT', 'Email summary removed from Screen. The automation keeps running.')}</span><span class="btn text">${t('common.undo', 'Undo')}</span></div>` : ''}`;
};

/* SC04 — widget picker */
STATES_SC.SC04 = ['picker', 'preview'];
SCREENS_SC.SC04 = (st, th) => {
  const head = `${sb()}${topBar(t('scr.addCard', 'Add a card'), { back: true, right: `<span class="ib">${ic('search')}</span>` })}`;
  const item = (i, a, b, tag) => `<div class="li"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div>${tag}</div>`;
  const ready = `<span class="tag neu">${t('scr.ready', 'Ready')}</span>`, setup = `<span class="tag att">${t('scr.needsSetup', 'Needs setup')}</span>`, added = `<span class="tag">${ic('check')}${t('scr.added', 'Added')}</span>`;
  if (st === 'preview') return `${head}<div class="body nonav"><div class="lbl">${t('scr.previewL', 'Preview')}</div><div class="sgrid" style="--cols:2">${C_.calendar({})}</div>
    <div class="card s2" style="margin-top:12px"><div class="b sm">${t('w.cal', 'Calendar')}</div><div class="xs mut">${t('scr.calWhat', 'Your next event from the phone’s calendar. Needs calendar permission — halo asks when you add it.')}</div></div>
    <div class="lbl">${t('scr.sizeL', 'Size')}</div><div class="row" style="gap:6px"><span class="chip sel">${t('scr.compact', 'Compact')}</span><span class="chip line">${t('scr.standard', 'Standard')}</span></div>
    <span class="btn full" style="position:absolute;left:16px;right:16px;bottom:16px;width:auto">${t('scr.addIt', 'Add to Screen')}</span></div>`;
  return `${head}<div class="body nonav"><div class="lbl">${t('scr.g.ess', 'Essentials')}</div><div class="card list" style="padding:0 14px">${item('timer', t('w.clockN', 'Clock & date'), t('scr.offlineOk', 'Works offline'), added)}${item('cloud', t('w.weather', 'Weather'), t('scr.weatherNeeds', 'City or location'), setup)}${item('calendar', t('w.cal', 'Calendar'), t('scr.calNeeds', 'Phone calendar'), ready)}</div>
    <div class="lbl">${t('scr.g.work', 'My work')}</div><div class="card list" style="padding:0 14px">${item('task', t('w.tasks', 'Next up'), t('scr.tasksSub', 'Tasks and follow-ups from halo'), added)}${item('notebook', t('w.continue', 'Continue'), t('scr.nbSub', 'Recent notes and lessons'), ready)}</div>
    <div class="lbl">${t('scr.g.halo', 'From halo')}</div><div class="card list" style="padding:0 14px">${item('routines', t('w.autoRes', 'Routine results'), t('scr.autoSub', 'Latest result of a routine you choose'), ready)}${item('check', t('w.done', 'halo finished'), t('scr.actSub', 'What halo completed'), ready)}</div>
    <div class="lbl">${t('scr.g.int', 'Interests')}</div><div class="card list" style="padding:0 14px">${item('explain', t('w.topic', 'A topic'), t('scr.topicSub', 'Articles on a topic you pick'), setup)}</div></div>`;
};

/* SC05 — card detail */
STATES_SC.SC05 = ['detail', 'article'];
SCREENS_SC.SC05 = (st, th) => {
  const base = screenPage(th, dash({ mail: 'updated', weatherReady: true }));
  if (st === 'article') return `${base}<div class="scrim in"></div><div class="sheet in" style="max-height:88%"><div class="grab"></div><div style="height:120px;border-radius:16px;background:linear-gradient(135deg,#221a4a,#ff9f6b)"></div>
    <h2 style="margin-top:10px">Webb spots a black hole from the early universe</h2><div class="xs mut">NASA · 18 Oct 2026 · ${t('scr.extLink', 'opens nasa.gov')}</div>
    <div class="sm" style="margin-top:8px">${t('scr.artSum', 'A short summary from halo, with the source linked. Tap Open to read the full article on the publisher’s site.')}</div>
    <div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap"><span class="btn sm">${t('scr.open', 'Open article')}</span><span class="btn ghost sm">${ic('new_note')}${t('ans.save', 'Save to Notebook')}</span><span class="btn ghost sm">${ic('speaker')}</span></div>
    <div class="btn text sm" style="padding:0;margin-top:6px">${t('scr.notInterested', 'Fewer like this')}</div></div>`;
  return `${base}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><div class="row"><div class="av">${ic('routines')}</div><div class="grow"><h2 style="margin:0">${t('w.mail', 'Morning email summary')}</h2><div class="xs mut">${t('scr.mailMeta', 'Routine · Gmail · updated 07:02 · 3 new since yesterday')}</div></div></div>
    <div class="sm" style="margin:10px 0;line-height:1.55"><b>Bank:</b> October statement is ready.<br><b>Anna:</b> Sunday at 13:00 confirmed, she brings dessert.<br><b>School:</b> trip form due Friday — needs a signature.</div>
    <div class="row" style="gap:8px;flex-wrap:wrap"><span class="btn sm">${ic('speaker')}${t('ans.read', 'Read aloud')}</span><span class="btn ghost sm">${t('scr.fullResult', 'Full result')}</span><span class="btn ghost sm">${t('scr.openRoutine', 'Open routine')}</span></div>
    <div class="row" style="gap:14px;margin-top:8px"><span class="btn text sm" style="padding:0">${t('scr.pin', 'Pin')}</span><span class="btn text sm" style="padding:0">${t('scr.hide', 'Hide from Screen')}</span></div></div>`;
};

/* SC06 — automation result card lifecycle (stable card, updates in place) */
STATES_SC.SC06 = ['not-set-up', 'ready', 'updated', 'failed'];
SCREENS_SC.SC06 = (st, th) => screenPage(th, `<div class="sgrid" style="--cols:2">${C_.clock()}${C_.calendar({})}${st === 'not-set-up' ? C_.mailSetup() : C_.mail({ state: st, cls: st === 'updated' ? 'cardupd' : '' })}${C_.tasks({})}</div>
  ${st === 'failed' ? `<div class="xs mut" style="margin:8px 4px">${t('scr.failNote', 'The 07:00 run couldn’t reach Gmail. Yesterday’s summary stays until a new one works.')}</div>` : ''}`);

/* SC07 — interests */
STATES_SC.SC07 = ['feed', 'no-source', 'topics'];
SCREENS_SC.SC07 = (st, th) => {
  if (st === 'topics') return `${sb()}${topBar(t('scr.topicsT', 'Your topics'), { back: true })}<div class="body nonav"><div class="sm mut" style="margin-bottom:10px">${t('scr.topicsSub', 'Only topics you add. halo never picks topics from your chats.')}</div>
    <div class="card list" style="padding:0 14px">${['Black holes', 'Gardening', 'Spanish'].map(x => `<div class="li"><div class="av">${ic('explain')}</div><span class="b sm grow">${x}</span><span class="ib" style="width:40px;height:40px">${ic('trash')}</span></div>`).join('')}</div>
    <div class="field" style="margin-top:12px">${ic('add')}<span class="sm mut grow">${t('scr.addTopic', 'Add a topic…')}</span><span class="ib s2" style="width:40px;height:40px">${ic('mic')}</span></div>
    <div class="row" style="margin-top:14px">${ic('privacy')}<span class="sm grow">${t('scr.suggestTopics', 'Suggest topics from my notes')}</span>${sw(false)}</div><div class="xs mut">${t('scr.suggestNote', 'Off by default. Suggestions always ask before they’re added.')}</div></div>`;
  return screenPage(th, `<div class="sgrid" style="--cols:2"><div class="slbl">${t('scr.forYou', 'For you')}</div>${C_.interests({ state: st === 'no-source' ? 'nosource' : '' })}${C_.notebook()}${C_.activity()}</div>`);
};

/* SC08 — ask halo to change Screen */
STATES_SC.SC08 = ['listening', 'preview', 'applied', 'unsupported'];
SCREENS_SC.SC08 = (st, th) => {
  const base = screenPage(th, st === 'applied' ? `<div class="sgrid" style="--cols:2">${C_.tasks({ cls: 'rise' })}${C_.clock()}${C_.weather()}${C_.mail({ state: 'ready' })}</div>` : dash({ mail: 'ready', weatherReady: true }));
  if (st === 'applied') return base.replace('<div class="aura idle"></div>', '<div class="aura success"></div>') + `<div class="toast" style="bottom:96px"><span class="grow">${t('scr.moved', 'Moved “Next up” to the top')}</span><span class="btn text">${t('common.undo', 'Undo')}</span></div>`;
  if (st === 'listening') return base.replace('<div class="aura idle"></div>', '<div class="aura listening"></div>') + `<div class="sheet in"><div class="grab"></div><div class="row"><span class="go stop"><i class="mic-live"></i><i class="mic-live r2"></i>${ic('stop')}</span><div class="say grow" style="font:500 calc(18px*var(--fs))/1.3 Sora">${kwords('Put my next three tasks above the weather', 5)}</div></div></div>`;
  if (st === 'unsupported') return base + `<div class="sheet in"><div class="grab"></div><div class="row">${orb('warning', th, 30)}<h2 style="margin:0">${t('scr.cantT', 'halo can’t add a stock ticker yet')}</h2></div>
    <div class="sm mut" style="margin:6px 0">${t('scr.cantB', 'There’s no card for stocks. You could follow “markets” as a topic instead.')}</div><div class="btns"><span class="btn text sm">${t('next.notNow', 'Not now')}</span><span class="btn sm">${t('scr.addTopicBtn', 'Add “markets” topic')}</span></div></div>`;
  return base + `<div class="sheet in"><div class="grab"></div><div class="row">${orb('idle', th, 30)}<h2 style="margin:0">${t('scr.changeT', 'Here’s the change')}</h2></div>
    <div class="card s2" style="margin:10px 0"><div class="row sm">${ic('task')}<span class="grow">${t('scr.change1', 'Move “Next up” to the top, show 3 tasks')}</span></div><div class="row sm" style="margin-top:6px">${ic('cloud')}<span class="grow">${t('scr.change2', 'Weather moves below it')}</span></div></div>
    <div class="btns"><span class="btn text sm">${t('common.cancel', 'Cancel')}</span><span class="btn ghost sm">${t('scr.editMyself', 'Edit myself')}</span><span class="btn sm">${t('scr.apply', 'Apply')}</span></div></div>`;
};

/* SC09 — full screen / ambient (phone portrait, phone landscape, tablet landscape, tablet portrait) */
STATES_SC.SC09 = ['phone', 'phone-land', 'tablet-land', 'tablet-port', 'night'];
SCREENS_SC.SC09 = (st, th) => {
  const exitBar = `<div style="position:absolute;right:14px;top:12px;display:flex;gap:6px;z-index:3;opacity:.75"><span class="ib s2" style="width:44px;height:44px" aria-label="${t('scr.voice', 'Talk to halo')}">${ic('mic')}</span><span class="ib s2" style="width:44px;height:44px" aria-label="${t('scr.exit', 'Exit full screen')}">${ic('close')}</span></div>`;
  const cards = (cols, priv) => `<div class="sgrid" style="--cols:${cols};gap:14px">${C_.clock({ cls: cols > 2 ? 'w2' : 'w2' })}${C_.weather()}${C_.calendar({ priv })}${C_.tasks({ w: cols > 2 ? 'w2' : 'w2', priv })}${cols > 2 ? C_.mail({ state: 'ready', priv }) : ''}</div>`;
  const big = (s) => `<style>#ph .sc .big{font-size:${s}px}</style>`;
  if (st === 'phone-land') return `<div class="aura idle"></div>${exitBar}<div class="body nonav" style="top:16px;padding:0 20px"><div class="sgrid" style="--cols:4;gap:12px">${C_.clock({ cls: 'w2' })}${C_.weather()}${C_.calendar({ priv: true })}${C_.tasks({ priv: true })}${C_.notebook()}</div></div>${big(54)}`;
  if (st === 'tablet-land' || st === 'night') return `<div class="aura idle"></div>${exitBar}<div class="body nonav ${st === 'night' ? 'nightwrap' : ''}" style="top:22px;padding:0 28px;${st === 'night' ? 'filter:brightness(.55) saturate(.7)' : ''}">
    <div class="sgrid" style="--cols:4;gap:16px">${C_.clock({ cls: 'w2 h2' })}${C_.weather()}${C_.calendar({ priv: st === 'night' })}${C_.tasks({ priv: st === 'night' })}${C_.mail({ state: 'ready', priv: st === 'night' })}${C_.interests({})}</div>
    <div class="xs mut" style="text-align:center;margin-top:12px">${st === 'night' ? `${ic('privacy')} ${t('scr.nightNote', 'Night: dimmed · private details hidden · layout shifts slightly every few minutes')}` : t('scr.ambNote', 'Stays on while charging · dims at 22:00 · tap anywhere to show controls')}</div></div>${big(st === 'night' ? 96 : 96)}`;
  if (st === 'tablet-port') return `<div class="aura idle"></div>${exitBar}<div class="body nonav" style="top:70px;padding:0 24px"><div class="sgrid" style="--cols:3;gap:14px">${C_.clock({ cls: 'w3' })}${C_.weather()}${C_.calendar({})}${C_.notebook()}${C_.tasks({ w: 'w3' })}${C_.mail({ state: 'ready', cls: 'w3' }).replace('sc w2', 'sc')}${C_.interests({}).replace('sc w2', 'sc w3')}</div></div>${big(88)}`;
  return `<div class="aura idle"></div>${exitBar}<div class="body nonav" style="top:70px">${cards(2, true)}</div>${big(64)}`;
};

/* SC10 — Screen settings & privacy */
STATES_SC.SC10 = ['settings', 'reset'];
SCREENS_SC.SC10 = (st, th) => {
  const page = `${sb()}${topBar(t('scr.settingsT', 'Screen settings'), { back: true })}<div class="body" style="bottom:92px">
    ${sgroup(t('scr.g.layouts', 'Layouts'), srow('fit_all', t('scr.morning', 'Morning'), t('scr.inUse', 'In use')) + srow('fit_all', t('scr.essentials', 'Essentials'), '') + srow('add', t('scr.newLayout', 'New layout'), ''))}
    ${sgroup(t('scr.g.privacy', 'Privacy'), srow('privacy', t('scr.hidePriv', 'Hide private details in full screen'), t('scr.hidePrivSub', 'Email, calendar and tasks show after unlock'), { right: sw(true) }) + srow('explain', t('scr.topicsT', 'Your topics'), '3') + srow('memory', t('scr.personal', 'Personalise from my notes'), t('common.off', 'Off'), { right: sw(false) }))}
    ${sgroup(t('scr.g.full', 'Full screen'), srow('timer', t('scr.keepOn', 'Stay on while charging'), '', { right: sw(true) }) + srow('read_mode', t('scr.dim', 'Dim at night'), '22:00 – 07:00'))}
    <div class="btn text sm" style="color:var(--err);padding:0;margin-top:10px">${t('scr.resetAll', 'Reset Screen…')}</div></div>${nav('home')}`;
  if (st === 'reset') return `${page}<div class="scrim in"></div><div class="dlg"><h2>${t('scr.resetT', 'Reset Screen?')}</h2><div class="sm mut">${t('scr.resetB', 'Your layouts go back to Essentials. Routines, notes and topics are not deleted.')}</div><div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn danger">${t('scr.resetGo', 'Reset')}</span></div></div>`;
  return page;
};

/* SC11 — card states (one card, every state) */
STATES_SC.SC11 = ['states'];
SCREENS_SC.SC11 = (st, th) => {
  const s = (title, body, f) => `<div class="sc"><div class="sh">${ic('cloud')}${title}</div>${body}<div class="foot">${f}</div></div>`;
  return `${sb()}${topBar(t('scr.statesT', 'Every card, every state'), {})}<div class="body nonav"><div class="sgrid" style="--cols:2">
    ${s(t('st.ready', 'Ready'), `<div class="big" style="font-size:30px">11°</div>`, fresh('', 'Updated 07:15'))}
    ${s(t('st.loading', 'Loading'), `<div class="shim" style="width:70%"></div><div class="shim" style="width:45%"></div>`, '')}
    ${s(t('st.refreshing', 'Refreshing'), `<div class="big" style="font-size:30px;opacity:.6">11°</div>`, `<span class="fresh"><span class="typing-dots"><i></i><i></i><i></i></span>${t('st.updating', 'Updating')}</span>`)}
    ${s(t('st.stale', 'Stale'), `<div class="big" style="font-size:30px;opacity:.6">11°</div>`, fresh('stale', 'Stale · 3 h ago'))}
    ${s(t('st.offline', 'Offline'), `<div class="big" style="font-size:30px;opacity:.6">11°</div>`, fresh('off', 'Offline · cached'))}
    ${s(t('st.error', 'Couldn’t update'), `<div class="sm">${t('st.errB', 'The weather service didn’t answer.')}</div>`, `<span class="btn text sm" style="padding:0">${t('task.retry', 'Retry')}</span>`)}
    ${s(t('st.setup', 'Needs setup'), `<div class="sm">${t('w.weatherSetup', 'Pick a city or allow location to see the forecast.')}</div>`, `<span class="btn text sm" style="padding:0">${t('w.setUp', 'Set up')}</span>`)}
    ${s(t('st.perm', 'Permission off'), `<div class="sm">${t('st.permB', 'Location is off for halo.')}</div>`, `<span class="btn text sm" style="padding:0">${t('st.useCity', 'Use a city instead')}</span>`)}
    ${s(t('st.empty', 'Nothing yet'), `<div class="sm mut">${t('st.emptyB', 'No events today.')}</div>`, '')}
    ${s(t('st.private', 'Private'), `<div class="sm mask" style="filter:blur(6px)">Mārtiņš · call 16:00</div>`, `<span class="fresh">${ic('privacy')}${t('st.unlock', 'Unlock to show')}</span>`)}
  </div></div>`;
};
window.SCREENS_SC = SCREENS_SC; window.STATES_SC = STATES_SC;
