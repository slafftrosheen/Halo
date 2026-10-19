/* halo — native Android assistant (VoiceInteractionSession overlay) + Android assistant settings.
   Source: assistant/HaloVoiceInteractionSession.kt · assistant/ui/AssistantSurface.kt · ui/screens/settings/AndroidAssistantScreen.kt · nativeactions/HaloTalkTileService.kt */
const SCREENS_AS = {}, STATES_AS = {};
const hostMaps = (th) => {
  const d = th === 'dark';
  const bg = d ? '#1b2230' : '#e9efe6', road = d ? '#2d3850' : '#ffffff', water = d ? '#16304a' : '#bcd9ef', park = d ? '#1d3a2e' : '#cfe6c4', txt = d ? '#e8ecf4' : '#1d2433';
  return `<div class="host" style="background:${bg};font-family:Roboto,Atk,sans-serif">
   <div style="position:absolute;left:-40px;top:300px;width:520px;height:120px;background:${water};transform:rotate(-12deg)"></div>
   <div style="position:absolute;left:30px;top:120px;width:120px;height:90px;border-radius:14px;background:${park}"></div>
   <div style="position:absolute;left:0;right:0;top:230px;height:16px;background:${road}"></div><div style="position:absolute;left:170px;top:0;bottom:0;width:14px;background:${road}"></div>
   <div style="position:absolute;left:0;right:0;top:520px;height:12px;background:${road};transform:rotate(8deg)"></div><div style="position:absolute;left:60px;top:0;bottom:0;width:10px;background:${road};transform:rotate(-14deg)"></div>
   <div style="position:absolute;left:162px;top:206px;width:30px;height:30px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);background:#e5484d;box-shadow:0 4px 10px rgba(0,0,0,.3)"></div>
   <div style="position:absolute;left:12px;right:12px;top:40px;height:50px;border-radius:25px;background:${d ? '#262f42' : '#fff'};box-shadow:0 2px 8px rgba(0,0,0,.2);display:flex;align-items:center;padding:0 18px;color:${txt};font-size:15px">Central Market</div>
   <div style="position:absolute;left:0;right:0;bottom:0;height:250px;border-radius:22px 22px 0 0;background:${d ? '#232b3c' : '#fff'};padding:20px;color:${txt}">
     <div style="font:600 21px Roboto,Atk">Central Market</div><div style="opacity:.7;font-size:14px;margin-top:4px">Market · Open until 18:00 · 1.2 km</div>
     <div style="display:flex;gap:8px;margin-top:16px">${['Directions', 'Start', 'Save', 'Share'].map((x, i) => `<span style="height:38px;border-radius:19px;padding:0 14px;display:grid;place-items:center;font-size:14px;${i ? `border:1px solid ${d ? '#45506a' : '#cfd5df'}` : 'background:#2f6fe4;color:#fff'}">${x}</span>`).join('')}</div></div></div>`;
};
const hostRecipe = (th) => {
  const d = th === 'dark', bg = d ? '#14161a' : '#ffffff', txt = d ? '#eceef2' : '#1b1d22';
  return `<div class="host" style="background:${bg};color:${txt};font-family:Roboto,Atk,sans-serif"><div style="height:56px;margin-top:28px;display:flex;align-items:center;padding:0 16px;font-size:14px;opacity:.7">recipes.example · Lemon chicken</div>
   <div style="height:200px;background:linear-gradient(135deg,#d9a441,#9c5b22 60%,#5a3416)"></div><div style="padding:16px"><div style="font:600 24px Roboto,Atk">Lemon chicken traybake</div>
   <div style="opacity:.7;margin:6px 0 14px;font-size:14px">Serves 4 · 50 min</div>${'<div style="height:9px;border-radius:5px;background:' + (d ? '#2a2d34' : '#eceef1') + ';margin:10px 0"></div>'.repeat(9)}</div></div>`;
};
const asChips = (o = {}) => `<div class="row" style="gap:6px;flex-wrap:wrap">${o.app !== false ? `<span class="where">${ic('open_app')}${t('as.in', 'In')} · ${o.app || 'Maps'}</span>` : ''}${o.screen === 'on' ? `<span class="where">${ic('read_mode')}${t('as.screen', 'Current screen included')}<span style="opacity:.6;margin-left:2px">✕</span></span>` : o.screen === 'off' ? `<span class="where att">${ic('read_mode')}${t('as.noScreen', 'Screen not shared')}</span>` : ''}</div>`;
const asCard = (th, o) => `<div class="asc ${o.cls || ''}">
  <div class="row"><span class="orbwrap ${o.att ? 'att' : ''}" style="width:34px;height:34px">${orb(o.orb || 'listening', th, 32)}</span><span class="b sm grow">${o.label}</span>${o.right || `<span class="ib" style="width:40px;height:40px" aria-label="${t('as.openHalo', 'Open halo')}">${ic('mini_halo')}</span>`}</div>
  ${o.say ? `<div class="say">${o.say}</div>` : ''}${o.body || ''}${o.chips !== undefined ? o.chips : asChips()}
  ${o.acts !== false ? `<div class="acts2">${o.acts || `<span class="ib s2" aria-label="${t('as.type', 'Type instead')}">${ic('ask_mode')}</span><span class="sp"></span><span class="go stop" aria-label="${t('comp.stop', 'Stop')}">${ic('stop')}</span>`}</div>` : ''}</div>`;
const sbHost = (th) => `<div class="sb" style="position:absolute;top:0;left:0;right:0;z-index:2;color:${th === 'dark' ? '#fff' : '#111'}"><span>9:41</span><span>●●● <i></i></span></div>`;

STATES_AS.AS01 = ['invoke', 'listening', 'understanding', 'answer', 'speaking'];
SCREENS_AS.AS01 = (st, th) => {
  const H = hostMaps(th) + sbHost(th);
  if (st === 'invoke') return `${H}<div class="edge invoke"></div><div class="cornerglow l"></div><div class="cornerglow r"></div>
    ${asCard(th, { cls: 'in', orb: 'listening', label: t('as.hello', 'halo'), say: `<span class="mut">${t('as.go', 'Go ahead, I’m listening.')}</span>`, chips: asChips({ screen: 'on' }) })}`;
  if (st === 'listening') return `${H}<div class="edge"></div>${asCard(th, { orb: 'capturing_voice', label: t('as.listening', 'Listening'),
    say: `<span class="word on">How</span> <span class="word on">long</span> <span class="word on">does</span> <span class="word on">it</span> <span class="word on">take</span> <span class="word">to</span> <span class="word">walk</span> <span class="word">there?</span>`,
    body: `<div class="wave" style="height:22px;margin:-2px 2px 10px">${[8, 14, 20, 11, 22, 16, 9, 18, 12, 21, 7, 15, 19, 10, 13, 17, 8, 20].map(h => `<i style="height:${h}px"></i>`).join('')}</div>`, chips: asChips({ screen: 'on' }) })}`;
  if (st === 'understanding') return `${H}<div class="edge"></div>${asCard(th, { orb: 'thinking', label: t('as.understanding', 'Understanding'), say: `How long does it take to walk there?`,
    body: `<div class="row sm mut" style="margin:-4px 2px 10px"><span class="typing-dots"><i></i><i></i><i></i></span>${t('as.usingScreen', 'Using the place on your screen')}</div>`, chips: asChips({ screen: 'on' }) })}`;
  if (st === 'answer' || st === 'speaking') return `${H}${st === 'speaking' ? '<div class="edge"></div>' : ''}${asCard(th, { orb: st === 'speaking' ? 'speaking' : 'idle', label: st === 'speaking' ? t('as.speaking', 'Speaking') : t('as.found', 'Here’s what I found.'),
    say: st === 'speaking' ? `<span class="word on">About</span> <span class="word on">15</span> <span class="word on">minutes</span> <span class="word">on</span> <span class="word">foot.</span> <span class="word">It</span> <span class="word">closes</span> <span class="word">at</span> <span class="word">18:00.</span>` : `About <b>15 minutes</b> on foot. It closes at 18:00, so you have time.`,
    body: `<div class="src" style="margin:-4px 0 10px"><span>${ic('read_mode')}${t('as.fromScreen', 'From your screen')}</span><span>${ic('on_device')}${t('where.phone', 'On this phone')}</span></div>`,
    chips: '', acts: `<span class="btn ghost sm">${ic('mic')}${t('as.followUp', 'Ask more')}</span><span class="btn ghost sm">${ic('phone_action')}${t('as.startWalk', 'Start walking route')}</span><span class="sp"></span><span class="go ${st === 'speaking' ? 'stop' : 'off'}" style="width:40px;height:40px">${ic(st === 'speaking' ? 'stop' : 'close')}</span>` })}`;
};

STATES_AS.AS02 = ['offer', 'approve', 'running', 'done'];
SCREENS_AS.AS02 = (st, th) => {
  const H = hostMaps(th) + sbHost(th);
  if (st === 'offer') return `${H}${asCard(th, { orb: 'idle', label: t('as.canDo', 'Maps can do this directly'), say: `Start walking directions to <b>Central Market</b>?`,
    body: `<div class="card s2" style="padding:10px 12px;margin-bottom:10px"><div class="row sm">${ic('phone_action')}<span class="grow">${t('as.appAction', 'Uses Maps’ own action')} · <b>Start navigation</b></span></div></div>`, chips: '',
    acts: `<span class="btn text sm">${t('as.notNow', 'Not now')}</span><span class="sp"></span><span class="btn guide">${t('as.startNav', 'Start navigation')}</span>` })}`;
  if (st === 'approve') return `${H}<div class="edge att"></div>${asCard(th, { orb: 'approval_required', att: true, label: t('as.needsOk', 'Your input is needed'), say: `Share your live location with Anna for 1 hour?`,
    body: `<div class="banner att" style="margin-bottom:10px">${ic('privacy')}<span class="sm">${t('as.shareWhy', 'Anna will see where you are until 10:41. You can stop sharing in Maps.')}</span></div>`, chips: '',
    acts: `<span class="btn text sm">${t('as.dontShare', 'Don’t share')}</span><span class="sp"></span><span class="btn">${t('as.share', 'Share for 1 hour')}</span>` })}`;
  if (st === 'running') return `${H}<div class="edge"></div>${asCard(th, { orb: 'acting_phone', label: t('as.doing', 'Working in Maps'), say: `Starting walking directions…`,
    body: `<ul class="steps" style="margin:-6px 0 8px"><li class="done"><span class="dot">${ic('check')}</span>Place found on your screen</li><li class="now"><span class="dot"></span>Maps · Start navigation</li></ul>`, chips: '' })}`;
  return `${H}${asCard(th, { cls: 'in', orb: 'success', label: t('as.allDone', 'All done'), say: `Walking directions started. <span class="mut">15 min.</span>`,
    body: `<div class="src" style="margin:-4px 0 6px"><span>${ic('check')}${t('as.mapsConfirmed', 'Maps confirmed')}</span></div>`, chips: '', acts: `<span class="sp"></span><span class="btn ghost sm">${t('common.done', 'Done')}</span>` })}`;
};

STATES_AS.AS03 = ['app-asks', 'pick'];
SCREENS_AS.AS03 = (st, th) => {
  const H = hostRecipe(th) + sbHost(th);
  if (st === 'app-asks') return `${H}${asCard(th, { orb: 'approval_required', att: true, label: t('as.voiceRequest', 'Voice request') + ' · Recipes', say: `“Add 7 ingredients to your shopping list?”`,
    body: `<div class="xs mut" style="margin:-4px 2px 10px">${t('as.appAskedYou', 'The Recipes app is asking you. halo only passes on your answer.')}</div>`, chips: asChips({ app: 'Recipes' }),
    acts: `<span class="btn text sm">${t('common.cancel', 'Cancel')}</span><span class="sp"></span><span class="btn ghost sm">${t('as.no', 'No')}</span><span class="btn sm">${t('as.yesAdd', 'Yes, add them')}</span>` })}`;
  return `${H}${asCard(th, { orb: 'waiting', label: t('as.chooseOption', 'Choose an option') + ' · Recipes', say: `Which list?`,
    body: `<div class="col" style="margin-bottom:10px">${['Weekly shop', 'Sunday lunch', 'New list'].map((x, i) => `<span class="btn ${i ? 'ghost' : 'line'} full" style="justify-content:flex-start">${x}</span>`).join('')}</div>`, chips: '',
    acts: `<span class="ib s2">${ic('mic')}</span><span class="xs mut">${t('as.sayIt', 'Or say it')}</span><span class="sp"></span><span class="btn text sm">${t('common.cancel', 'Cancel')}</span>` })}`;
};

STATES_AS.AS04 = ['no-screen', 'mic', 'failed', 'handoff'];
SCREENS_AS.AS04 = (st, th) => {
  const H = hostMaps(th) + sbHost(th);
  if (st === 'no-screen') return `${H}${asCard(th, { orb: 'listening', label: t('as.listening', 'Listening'), say: `<span class="mut">${t('as.go', 'Go ahead, I’m listening.')}</span>`, chips: asChips({ screen: 'off' }),
    body: `<div class="xs mut" style="margin:-4px 2px 8px">${t('as.noScreenWhy', 'Android isn’t sharing this screen with halo. Turn on “Use text from screen” in assistant settings if you want it to.')}</div>` })}`;
  if (st === 'mic') return `${H}${asCard(th, { orb: 'permission_missing', att: true, label: t('as.micUnavailable', 'Microphone unavailable'), say: `${t('as.micBody', 'Another app is using the microphone.')}`,
    body: `<div class="xs mut" style="margin:-4px 2px 10px">${t('as.micSub', 'You can type instead, or try again when the call ends.')}</div>`, chips: '',
    acts: `<span class="btn ghost sm">${ic('ask_mode')}${t('as.type', 'Type instead')}</span><span class="sp"></span><span class="btn sm">${t('task.retry', 'Retry')}</span>` })}`;
  if (st === 'failed') return `${H}${asCard(th, { orb: 'error', label: t('as.wrong', 'Something went wrong'), say: t('as.wrongBody', 'Maps closed before the directions started.'),
    body: `<div class="xs mut" style="margin:-4px 2px 10px">${t('as.wrongSub', 'Nothing was changed. Open halo to review this and try again.')}</div>`, chips: '',
    acts: `<span class="btn ghost sm">${t('as.openHalo', 'Open halo')}</span><span class="sp"></span><span class="btn sm">${t('task.retry', 'Retry')}</span>` })}`;
  return `${H}<div class="host dim" style="background:transparent"></div>${asCard(th, { cls: 'in', orb: 'idle', label: t('as.continues', 'Your conversation continues in halo.'), say: `${t('as.handoffSay', 'Opening the full conversation…')}`, chips: '', acts: false,
    body: `<div style="height:4px;border-radius:2px;background:var(--line);overflow:hidden;margin:2px 0 4px"><i style="display:block;width:70%;height:100%;background:var(--ink)"></i></div>` })}`;
};

/* AS05 — Android assistant settings (route android_assistant). Real states from AndroidAssistantScreen.kt */
STATES_AS.AS05 = ['active', 'selected', 'not-selected', 'repair'];
SCREENS_AS.AS05 = (st, th) => {
  const S = { active: ['success', t('aa.active', 'halo is your Android assistant'), t('aa.activeSub', 'Long-press the power button or swipe up from a bottom corner.'), ''],
    selected: ['warning', t('aa.selected', 'halo is selected, but not active'), t('aa.selectedSub', 'Android hasn’t switched halo on yet. Restarting the phone usually fixes it.'), t('aa.recheck', 'Check again')],
    'not-selected': ['idle', t('aa.notSelected', 'Use halo from anywhere on your phone'), t('aa.notSelectedSub', 'Make halo your assistant. You can switch back at any time in Android settings.'), t('aa.setAs', 'Set as assistant')],
    repair: ['error', t('aa.repair', 'Assistant needs a repair'), t('aa.repairSub', 'Speech recognition permission is missing, so halo can’t hear you from other apps.'), t('aa.fix', 'Allow speech recognition')] }[st];
  const row = (i, a, b, on) => `<div class="li"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm">${a}</div><div class="xs mut">${b}</div></div>${on === undefined ? '' : `<span style="width:44px;height:26px;border-radius:13px;background:${on ? 'var(--fill)' : 'var(--s3)'};position:relative;flex:none"><i style="position:absolute;top:3px;${on ? 'right' : 'left'}:3px;width:20px;height:20px;border-radius:50%;background:${on ? 'var(--on)' : 'var(--s1)'}"></i></span>`}</div>`;
  return `<div class="aura ${st === 'active' ? 'idle' : st === 'repair' ? 'error' : 'needs'}"></div>${sb()}${topBar(t('aa.title', 'Android assistant'), { back: true })}<div class="body" style="bottom:92px">
   <div class="card" style="text-align:center;padding:14px 14px">
     <div style="display:grid;place-items:center;margin-bottom:8px"><div style="position:relative;width:150px;height:92px;border-radius:18px;background:var(--s2);overflow:hidden;box-shadow:inset 0 0 0 2px ${st === 'active' ? 'var(--fill)' : 'var(--line)'}">
       <div style="position:absolute;left:8px;right:8px;bottom:10px;height:40px;border-radius:14px;background:var(--s1);display:flex;align-items:center;gap:6px;padding:0 8px">${orb(S[0] === 'warning' ? 'waiting' : S[0], th, 22)}<i style="flex:1;height:6px;border-radius:3px;background:var(--line)"></i></div></div></div>
     <div class="t2">${S[1]}</div><div class="sm mut" style="margin-top:4px">${S[2]}</div>
     ${S[3] ? `<div class="btns" style="justify-content:center"><span class="btn ${st === 'not-selected' ? 'guide' : ''}">${S[3]}</span></div>` : ''}</div>
   <div class="lbl">${t('aa.howTitle', 'Ways to call halo')}</div><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">${[['phone', t('aa.power', 'Hold power')], ['mini_halo', t('aa.corner', 'Corner swipe')], ['widget', t('aa.tile', 'Quick tile')]].map(([i, l]) => `<div class="card s2" style="padding:10px 6px;text-align:center"><div style="display:grid;place-items:center;color:var(--ink)">${ic(i)}</div><div class="xs b" style="margin-top:4px">${l}</div></div>`).join('')}</div>
   <div class="lbl">${t('aa.privacy', 'What halo can see')}</div><div class="card list" style="padding:2px 14px">
     ${row('read_mode', t('aa.screenText', 'Use text from screen'), t('aa.screenTextSub', 'Set in Android. Off: halo can’t read the app you’re in.'), st === 'active')}${row('privacy', t('aa.screenshot', 'Screenshot'), t('aa.screenshotSub', 'Only when Android shares one. Never saved.'), false)}</div>

</div>${nav('settings')}`;
};

/* AS06 — other entry points: Quick Settings tile + lock screen (lock screen = proposal, needs engineering) */
STATES_AS.AS06 = ['tile', 'lockscreen'];
SCREENS_AS.AS06 = (st, th) => {
  if (st === 'tile') return `<div class="host" style="background:${th === 'dark' ? '#0b0d14' : '#dfe3ea'}"></div>${sbHost(th)}<div class="body nonav" style="top:40px">
    <div class="grid" style="display:grid;grid-template-columns:1fr 1fr;gap:10px">${[['bluetooth', 'Bluetooth', 0], ['signal', 'Wi-Fi', 0], ['mini_halo', t('aa.tileLabel', 'Talk to halo'), 1], ['phone', 'Do not disturb', 0]].map(([i, l, h]) => `<div style="height:72px;border-radius:24px;display:flex;align-items:center;gap:10px;padding:0 16px;${h ? 'background:var(--fill);color:var(--on);box-shadow:0 0 24px color-mix(in srgb,var(--fill) 50%,transparent)' : 'background:var(--s2)'}">${ic(i)}<span class="b sm">${l}</span></div>`).join('')}</div></div>`;
  return `<div class="host" style="background:linear-gradient(180deg,#141a33,#060330)"></div><div class="body nonav" style="top:60px;color:#FAFAF4;text-align:center"><div style="font:300 64px Sora">9:41</div><div class="sm" style="opacity:.8">Sunday 19 October</div></div>
    <div class="edge"></div>${asCard('dark', { orb: 'idle', label: t('as.locked', 'Phone is locked'), say: `Sunrise tomorrow is at 08:12.`,
    body: `<div class="banner info" style="margin-bottom:10px">${ic('privacy')}<span class="sm">${t('as.lockedNote', 'Messages, calendar and notes stay hidden until you unlock.')}</span></div>`, chips: '',
    acts: `<span class="btn ghost sm">${t('as.unlockMore', 'Unlock for more')}</span><span class="sp"></span><span class="go off" style="width:40px;height:40px">${ic('close')}</span>` })}`;
};
window.SCREENS_AS = SCREENS_AS; window.STATES_AS = STATES_AS;
