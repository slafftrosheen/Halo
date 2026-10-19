/* halo — Package C: Notebook · editor + halo help · Canvas · files · Memory · conversations · Study.
   Finished, user-facing. Editors hide the nav (focus). One accent glow per screen. Drafts are dashed until kept. */
const SCREENS_C = {}, STATES_C = {};
const edTop = (title, o = {}) => `<div class="top"><span class="ib back" data-go="back">${ic('back')}</span><h1 style="font-size:calc(18px*var(--fs))">${title}</h1>${o.right || `<span class="ib" aria-label="${t('ans.read', 'Read aloud')}">${ic('speaker')}</span><span class="ib">${ic('more')}</span>`}</div>`;
const saved = (txt) => `<div class="row xs mut" style="margin:-6px 2px 8px;gap:5px">${ic('check')}${txt || t('ed.saved', 'Saved')}</div>`;
const lines = (n, w = []) => `<div class="lines">${Array.from({ length: n }, (_, i) => `<i style="width:${w[i] || 92 - (i * 7) % 30}%"></i>`).join('')}</div>`;
const toolbar = (items) => `<div style="position:absolute;left:10px;right:10px;bottom:12px;height:56px;border-radius:28px;display:flex;align-items:center;justify-content:space-around;z-index:3;background:color-mix(in srgb,var(--s1) 80%,transparent);backdrop-filter:blur(16px);box-shadow:var(--shadow)">${items}</div>`;
const tb = (i, lab, on) => `<span class="ib" style="width:44px;height:44px;${on ? 'background:color-mix(in srgb,var(--fill) 18%,transparent);color:var(--ink)' : ''}" aria-label="${lab}">${ic(i)}</span>`;
const noteCard = (i, title, sub, o = {}) => `<div class="card ${o.cls || ''}" style="padding:12px 14px"><div class="row"><div class="av">${ic(i)}</div><div class="grow"><div class="b sm ell">${title}</div><div class="xs mut ell">${sub}</div></div>${o.right || ''}</div></div>`;

/* ---------- NB01 Notebook hub + Library ---------- */
STATES_C.NB01 = ['hub', 'library', 'select', 'empty', 'trash'];
SCREENS_C.NB01 = (st, th) => {
  const head = `${sb()}${topBar(t('nav.notebook', 'Notebook'), { right: `<span class="ib">${ic('search')}</span>` })}`;
  const create = `<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:2px 0 4px">${[['new_note', t('nb.note', 'Note')], ['new_canvas', t('nb.canvas', 'Canvas')], ['import', t('nb.import', 'Import')], ['inbox', t('nb.inbox', 'Inbox')]].map(([i, l], k) => `<div class="card ${k === 0 && st === 'empty' ? 'guide' : ''}" style="padding:12px 4px;text-align:center"><div style="display:grid;place-items:center;color:var(--ink)">${ic(i)}</div><div class="xs b" style="margin-top:5px">${l}${k === 3 ? ' · 3' : ''}</div></div>`).join('')}</div>`;
  if (st === 'empty') return `<div class="aura idle"></div>${head}<div class="body" style="bottom:92px">${create}<div class="empty">${orb('idle', th, 80)}<div class="t2">${t('nb.emptyT', 'Your Notebook is empty')}</div><div class="sm mut">${t('nb.emptyB', 'Write a note, start a canvas or import a file. Things you save from Home land in Inbox.')}</div></div></div>${nav('notebook')}`;
  const seg = (on) => `<div class="seg">${[['all', t('nb.all', 'All')], ['notes', t('nb.notes', 'Notes')], ['canvas', t('nb.canvases', 'Canvases')], ['files', t('nb.files', 'Files')], ['trash', t('nb.trash', 'Trash')]].map(([k, l]) => `<span class="chip ${k === on ? 'sel' : 'line'}">${l}</span>`).join('')}</div>`;
  const items = [['new_note', 'Trip to Kuldīga', 'Note · edited 10 min ago'], ['new_canvas', 'Kitchen renovation', 'Canvas · 14 cards'], ['file_doc', 'Lease 2027.pdf', 'File · 38 pages'], ['new_note', 'Spanish verbs', 'Note · in Study'], ['image', 'Whiteboard photo', 'Image · from glasses']];
  if (st === 'library' || st === 'select') {
    const sel = st === 'select';
    const list = items.map(([i, a, b], k) => noteCard(i, a, b, { right: sel ? `<span style="width:24px;height:24px;border-radius:50%;${k < 2 ? 'background:var(--fill);color:var(--on)' : 'box-shadow:inset 0 0 0 2px var(--line)'};display:grid;place-items:center">${k < 2 ? ic('check') : ''}</span>` : `<span class="ib" style="width:36px;height:36px">${ic('more')}</span>` })).join('');
    return `${sb()}${sel ? `<div class="top"><span class="ib">${ic('close')}</span><h1>${t('nb.selected', '2 selected')}</h1><span class="btn text sm">${t('nb.selectAll', 'Select all')}</span></div>` : topBar(t('nb.library', 'Library'), { back: true, right: `<span class="ib">${ic('sort')}</span><span class="ib">${ic('multi_select')}</span>` })}
     <div class="body" style="bottom:92px">${sel ? '' : `<div class="field" style="min-height:44px;margin-bottom:8px">${ic('search')}<span class="mut sm">${t('nb.search', 'Search your Notebook')}</span></div>`}${seg('all')}${list}</div>
     ${sel ? toolbar(`${tb('collections', t('nb.addTo', 'Add to collection'))}${tb('study_this', t('nb.study', 'Study'))}${tb('use_in_chat', t('nb.useChat', 'Use in conversation'))}${tb('archive', t('nb.archive', 'Archive'))}${tb('trash', t('nb.toTrash', 'Move to Trash'))}`) : nav('notebook')}`;
  }
  if (st === 'trash') return `${sb()}${topBar(t('nb.trash', 'Trash'), { back: true })}<div class="body" style="bottom:92px">${seg('trash')}<div class="sm mut" style="margin:0 2px 10px">${t('nb.trashSub', 'Items stay here for 30 days, then they’re gone for good.')}</div>
    ${noteCard('new_note', 'Old shopping list', 'Deleted 3 days ago', { right: `<span class="btn ghost sm">${t('nb.restore', 'Restore')}</span>` })}${noteCard('image', 'Blurry photo', 'Deleted 12 days ago', { right: `<span class="btn ghost sm">${t('nb.restore', 'Restore')}</span>` })}
    <div class="btns"><span class="btn text sm" style="color:var(--err)">${t('nb.emptyTrash', 'Empty Trash…')}</span></div></div>${nav('notebook')}`;
  return `<div class="aura idle"></div>${head}<div class="body" style="bottom:92px">${create}
    <div class="lbl">${t('nb.continue', 'Continue')}</div>${noteCard('new_note', 'Trip to Kuldīga', 'Note · edited 10 min ago')}${noteCard('new_canvas', 'Kitchen renovation', 'Canvas · 14 cards')}
    <div class="lbl">${t('nb.forYou', 'Waiting for you')}</div>
    <div class="card guide" style="padding:12px 14px"><div class="row"><div class="av">${ic('practise')}</div><div class="grow"><div class="b sm">${t('nb.due', '6 Spanish cards to review')}</div><div class="xs mut">${t('nb.dueSub', 'About 4 minutes')}</div></div><span class="btn sm">${t('nb.start', 'Start')}</span></div></div>
    <div class="card s2" style="padding:12px 14px;margin-top:10px"><div class="row"><div class="av">${ic('memory')}</div><div class="grow"><div class="b sm">${t('nb.memSugg', '2 things halo could remember')}</div><div class="xs mut">${t('nb.memSuggSub', 'Only if you say yes')}</div></div><span class="btn ghost sm">${t('nb.review', 'Review')}</span></div></div>
    <div class="row" style="justify-content:space-between;margin-top:12px"><span class="btn text sm" style="padding:0">${t('nb.openLibrary', 'Open Library')}</span><span class="btn text sm" style="padding:0">${t('nb.chats', 'Saved chats')}</span></div></div>${nav('notebook')}`;
};

/* ---------- NB02 Note editor ---------- */
STATES_C.NB02 = ['edit', 'format', 'find', 'embed', 'ahead', 'restore', 'conflict'];
SCREENS_C.NB02 = (st, th) => {
  const head = `${sb()}${edTop('Trip to Kuldīga')}`;
  const body = (extra = '') => `<div class="body nonav" style="bottom:${st === 'format' || st === 'find' ? 330 : 80}px">${saved()}
    <div class="ttl" style="font-size:calc(24px*var(--fs))">Trip to Kuldīga</div>
    <div class="b" style="margin:12px 0 4px;font:600 calc(17px*var(--fs)) Sora">Saturday</div>
    <p style="margin:0 0 8px">Leave at 9:00. ${st === 'format' ? '<span style="background:color-mix(in srgb,var(--fill) 34%,transparent);border-radius:3px">Stop at the waterfall first</span>' : st === 'find' ? 'Stop at the <span style="background:color-mix(in srgb,var(--att) 45%,transparent);border-radius:3px">waterfall</span> first' : 'Stop at the waterfall first'}, then lunch in the old town.</p>
    <div class="b" style="margin:12px 0 4px;font:600 calc(17px*var(--fs)) Sora">Packing</div>
    <div class="sm" style="line-height:1.9">○ Rain jackets<br>○ Camera<br>○ Snacks for the car</div>${extra}</div>`;
  const tools = toolbar(`${tb('add_block', t('ed.addBlock', 'Add block'))}${tb('bold', t('ed.bold', 'Bold'))}${tb('bullet_list', t('ed.list', 'List'))}${tb('mic', t('ed.dictate', 'Dictate'))}${tb('think_with_halo', t('ed.ask', 'Ask halo'), true)}${tb('undo', t('common.undo', 'Undo'))}`);
  const kbd = `<div class="kbd">${[10, 9, 7, 4].map(n => `<div class="kr">${'<i></i>'.repeat(n)}</div>`).join('')}</div>`;
  if (st === 'format') return `${head}${body()}<div class="menu" style="left:30px;top:228px;display:flex;gap:2px;padding:4px;min-width:0">${['bold', 'italic', 'highlighter', 'link', 'think_with_halo'].map(i => `<span class="ib" style="width:40px;height:40px">${ic(i)}</span>`).join('')}</div>
    ${toolbar(`${tb('add_block', '')}${tb('bold', '', true)}${tb('bullet_list', '')}${tb('mic', '')}${tb('think_with_halo', '')}${tb('undo', '')}`).replace('bottom:12px', 'bottom:282px')}${kbd}`;
  if (st === 'find') return `${sb()}<div class="top"><div class="field focus" style="flex:1;min-height:44px">${ic('search')}<span class="sm grow">waterfall</span><span class="xs mut">1 of 2</span></div><span class="ib">${ic('chevron_down')}</span><span class="ib">${ic('close')}</span></div>
    <div style="padding:0 16px"><div class="field" style="min-height:44px">${ic('find_replace')}<span class="sm mut grow">${t('ed.replaceWith', 'Replace with')}</span><span class="btn text sm">${t('ed.replace', 'Replace')}</span><span class="btn text sm">${t('nb.all', 'All')}</span></div></div>${body().replace('top:84px', 'top:140px')}${kbd}`;
  if (st === 'embed') return `${head}${body(`<div class="card s2" style="margin-top:12px;padding:10px"><div class="photo" style="height:120px"></div><div class="row xs mut" style="margin-top:6px">${ic('image')}Waterfall.jpg · ${t('ed.fromGlasses', 'from your glasses')}</div></div>
    <div class="card" style="margin-top:10px;padding:10px 12px"><div class="row">${ic('file_doc')}<span class="sm grow">Route.pdf</span><span class="btn text sm">${t('ed.open', 'Open')}</span></div></div>`)}${tools}`;
  if (st === 'ahead') return `${head}${body(`<div class="suggest" style="margin-top:14px;padding:0"><span class="chip rise d1">${ic('check')}${t('ed.ahead.check', 'Make packing a checklist')}</span><span class="chip rise d2">${ic('calendar')}${t('ed.ahead.cal', 'Add Saturday 9:00 to calendar')}</span></div>`)}${tools}`;
  if (st === 'restore') return `${head}<div class="body nonav" style="bottom:80px"><div class="banner info" style="margin-bottom:10px">${ic('history')}<span class="grow"><b class="sm">${t('ed.unsaved', 'Unsaved text found')}</b><br><span class="xs mut">${t('ed.unsavedSub', 'From when halo closed yesterday at 22:14.')}</span></span></div>
    <div class="btns" style="margin:-4px 0 10px"><span class="btn text sm">${t('nb.dismiss', 'Dismiss')}</span><span class="btn sm">${t('ed.restoreIt', 'Restore it')}</span></div>${lines(6)}</div>${tools}`;
  if (st === 'conflict') return `${head}${body()}<div class="scrim in"></div><div class="dlg"><h2>${t('ed.conflictT', 'This note changed somewhere else')}</h2>
    <div class="sm mut">${t('ed.conflictB', 'It was edited from your backup while you were writing. Pick which version to keep — the other is saved as a copy.')}</div>
    <div class="col" style="margin-top:12px"><span class="btn full">${t('ed.keepMine', 'Keep mine')}</span><span class="btn ghost full">${t('ed.useOther', 'Use the other version')}</span></div></div>`;
  return `${head}${body()}${tools}`;
};

/* ---------- NB03 Note + halo assistance ---------- */
STATES_C.NB03 = ['ask', 'writing', 'draft', 'applied', 'stale', 'dictate'];
SCREENS_C.NB03 = (st, th) => {
  const head = `${sb()}${edTop('Trip to Kuldīga')}`;
  const page = `<div class="body nonav" style="bottom:80px">${saved()}<div class="ttl">Trip to Kuldīga</div><p style="margin:10px 0">Leave at 9:00. Stop at the waterfall first, then lunch in the old town.</p>${lines(3)}</div>`;
  if (st === 'ask') return `${head}${page}<div class="scrim in"></div><div class="sheet in"><div class="grab"></div><div class="row">${orb('idle', th, 28)}<h2 style="margin:0">${t('nh.title', 'Think with halo')}</h2></div>
    <div class="sm mut" style="margin:4px 0 10px">${t('nh.sub', 'halo writes a draft next to your note. Nothing changes until you apply it.')}</div>
    <div class="row" style="flex-wrap:wrap;gap:8px">${[['explain', t('nh.rewrite', 'Rewrite clearer')], ['review', t('nh.summary', 'Summarise')], ['add', t('nh.expand', 'Expand')], ['task', t('nh.plan', 'Turn into a plan')], ['study_this', t('nh.study', 'Make study cards')]].map(([i, l]) => `<span class="chip">${ic(i)}${l}</span>`).join('')}</div>
    <div class="field" style="margin-top:12px">${ic('think_with_halo')}<span class="sm mut grow">${t('nh.own', 'Or tell halo what you want…')}</span><span class="ib s2" style="width:40px;height:40px">${ic('mic')}</span></div></div>`;
  const draftCard = (o = {}) => `<div class="card draft-c ${o.stale ? '' : 'rise'}" style="border:1.5px dashed var(--ink);background:color-mix(in srgb,var(--fill) 7%,var(--s1));box-shadow:none;margin-top:10px">
    <div class="row" style="margin-bottom:6px"><span class="tag">${ic('ai_draft')}${t('nh.proposed', 'Draft by halo · not saved')}</span></div>
    <div class="sm"><b>Saturday plan</b><br>09:00 Leave Riga · 11:15 Ventas Rumba waterfall · 12:30 Lunch in the old town · 15:00 Walk by the river · 17:30 Head home</div>
    ${o.stale ? `<div class="banner att" style="margin-top:8px">${ic('history')}<span class="xs">${t('nh.staleB', 'Your note changed after halo wrote this. Applying would replace your newer text.')}</span></div>` : ''}
    <div class="btns" style="margin-top:10px">${o.stale ? `<span class="btn text sm">${t('nh.discard', 'Discard')}</span><span class="btn ghost sm">${t('nh.saveNew', 'Save as new note')}</span><span class="btn sm guide">${t('nh.redo', 'Write it again')}</span>` : `<span class="btn text sm">${t('nh.discard', 'Discard')}</span><span class="btn ghost sm">${t('nh.again', 'Try again')}</span><span class="btn ghost sm">${t('nh.saveNew', 'Save as new note')}</span><span class="btn sm">${t('nh.apply', 'Apply to note')}</span>`}</div></div>`;
  if (st === 'writing') return `<div class="aura thinking"></div>${head}<div class="body nonav" style="bottom:80px">${saved()}<div class="ttl">Trip to Kuldīga</div><p style="margin:10px 0">Leave at 9:00. Stop at the waterfall first, then lunch in the old town.</p>
    <div class="card" style="border:1.5px dashed var(--ink);box-shadow:none;margin-top:10px"><div class="row sm">${orb('thinking', th, 24)}<span class="grow">${t('nh.writing', 'halo is writing…')}</span><span class="btn text sm">${t('comp.stop', 'Stop')}</span></div><div class="shim" style="width:92%"></div><div class="shim" style="width:76%"></div><div class="shim" style="width:58%"></div></div></div>`;
  if (st === 'applied') return `<div class="aura success"></div>${head}<div class="body nonav" style="bottom:80px">${saved(t('nh.appliedSaved', 'Saved · halo’s draft applied'))}<div class="ttl">Trip to Kuldīga</div>
    <div class="card rise" style="margin-top:10px;box-shadow:none;background:color-mix(in srgb,var(--fill) 6%,var(--s1))"><div class="b sm">Saturday plan</div><div class="sm" style="line-height:1.8">09:00 Leave Riga<br>11:15 Ventas Rumba waterfall<br>12:30 Lunch in the old town<br>15:00 Walk by the river<br>17:30 Head home</div></div></div>
    <div class="toast" style="bottom:20px"><span class="grow">${t('nh.applied', 'Applied to your note')}</span><span class="btn text">${t('common.undo', 'Undo')}</span></div>`;
  if (st === 'draft' || st === 'stale') return `${head}<div class="body nonav" style="bottom:20px">${saved()}<div class="ttl">Trip to Kuldīga</div><p style="margin:10px 0;opacity:.75">Leave at 9:00. Stop at the waterfall first, then lunch in the old town.</p>${draftCard({ stale: st === 'stale' })}</div>`;
  return `<div class="aura listening"></div>${head}${page}<div class="sheet in" style="padding-bottom:16px"><div class="grab"></div><div class="row"><span class="go stop" style="width:44px;height:44px"><i class="mic-live"></i>${ic('stop')}</span>
    <div class="grow"><div class="b sm">${t('nh.dictating', 'Listening…')}</div><div class="wave" style="height:20px">${[8, 14, 20, 11, 22, 16, 9, 18, 12, 21, 7, 15].map(h => `<i style="height:${h}px"></i>`).join('')}</div></div></div>
    <div class="card s2" style="margin-top:10px;border:1.5px dashed var(--ink)"><span class="sm">and bring the blue thermos for coffee</span></div>
    <div class="btns"><span class="btn text sm">${t('nh.discard', 'Discard')}</span><span class="btn ghost sm">${t('nh.editFirst', 'Edit first')}</span><span class="btn sm">${t('nh.useSpoken', 'Add to note')}</span></div></div>`;
};

/* ---------- NB04 Canvas board ---------- */
const cnode = (x, y, w, txt, o = {}) => `<div style="position:absolute;left:${x}px;top:${y}px;width:${w}px;border-radius:14px;padding:9px 10px;font-size:calc(12.5px*var(--fs));line-height:1.3;background:${o.bg || 'var(--s1)'};box-shadow:${o.sel ? '0 0 0 2px var(--ink),' : ''}var(--shadow);${o.draft ? 'border:1.5px dashed var(--ink);background:color-mix(in srgb,var(--fill) 8%,var(--s1));box-shadow:none;' : ''}" class="${o.cls || ''}">${o.icon ? `<div style="color:var(--ink);margin-bottom:3px">${ic(o.icon)}</div>` : ''}${txt}</div>`;
const conn = (x1, y1, x2, y2, lab) => `<svg style="position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;pointer-events:none"><path d="M${x1} ${y1} C ${(x1 + x2) / 2} ${y1}, ${(x1 + x2) / 2} ${y2}, ${x2} ${y2}" stroke="color-mix(in srgb,var(--ink) 60%,transparent)" stroke-width="1.6" fill="none"/></svg>${lab ? `<span class="xs mut" style="position:absolute;left:${(x1 + x2) / 2 - 20}px;top:${(y1 + y2) / 2 - 18}px">${lab}</span>` : ''}`;
const grid = `<div style="position:absolute;inset:0;background-image:radial-gradient(color-mix(in srgb,var(--text) 14%,transparent) 1px,transparent 1.2px);background-size:22px 22px"></div>`;
STATES_C.NB04 = ['board', 'add', 'select', 'ahead', 'tidy', 'empty'];
SCREENS_C.NB04 = (st, th) => {
  const head = `${sb()}${edTop('Kitchen renovation', { right: `<span class="ib">${ic('search')}</span><span class="ib">${ic('fit_all')}</span><span class="ib">${ic('more')}</span>` })}`;
  const tidy = st === 'tidy';
  const board = `<div style="position:absolute;left:0;right:0;top:84px;bottom:0;overflow:hidden">${grid}
    ${conn(150, 90, 196, 150, tidy ? '' : 'budget')}${conn(110, 300, 200, 330)}
    ${cnode(tidy ? 16 : 20, tidy ? 30 : 40, 130, '<b>Budget</b><br>€ 8 000 max', { icon: 'task', sel: st === 'select', cls: tidy ? 'rise' : '' })}
    ${cnode(tidy ? 186 : 196, tidy ? 30 : 120, 140, '<b>Worktop</b><br>Oak or quartz?', { sel: st === 'select', cls: tidy ? 'rise d1' : '' })}
    <div style="position:absolute;left:${tidy ? 16 : 30}px;top:${tidy ? 150 : 210}px;width:120px;height:${tidy ? 96 : 110}px;border-radius:14px;background:linear-gradient(135deg,#cfc6b4,#8d7f68);box-shadow:var(--shadow)" class="${tidy ? 'rise d2' : ''}"></div>
    ${cnode(tidy ? 186 : 200, tidy ? 150 : 290, 140, `<div class="row" style="gap:6px">${ic('file_audio')}<b>Voice memo</b></div><span class="mut">0:42 · “tiles from the shop on…”</span>`, { cls: tidy ? 'rise d3' : '' })}
    ${cnode(tidy ? 16 : 60, tidy ? 266 : 420, 150, '<b>Tiles</b><br>Green, matte, 10×10', { cls: tidy ? 'rise d4' : '' })}
    ${tidy ? `<div style="position:absolute;left:6px;top:20px;width:348px;height:330px;border-radius:20px;box-shadow:inset 0 0 0 1.5px color-mix(in srgb,var(--ink) 50%,transparent)" class="rise"><span class="tag" style="position:absolute;left:12px;top:-11px">Kitchen · 5</span></div>` : ''}
  </div>`;
  const tools = toolbar(`${tb('add', t('cv.add', 'Add'), st === 'add')}${tb('select', t('cv.select', 'Select'), st === 'select')}${tb('connection', t('cv.connect', 'Connect'))}${tb('think_with_halo', t('ed.ask', 'Ask halo'))}${tb('undo', t('common.undo', 'Undo'))}`);
  if (st === 'empty') return `${head}<div style="position:absolute;left:0;right:0;top:84px;bottom:0">${grid}<div class="empty" style="position:absolute;inset:0;justify-content:center">${orb('idle', th, 72)}<div class="t2">${t('cv.emptyT', 'Make room for your ideas')}</div><div class="sm mut">${t('cv.emptyB', 'Add cards, link your notes and connect what belongs together.')}</div><span class="btn guide" style="margin-top:6px">${ic('add')}${t('cv.addFirst', 'Add a card')}</span></div></div>`;
  if (st === 'add') return `${head}${board}${tools}<div class="menu" style="left:10px;bottom:78px;width:240px;z-index:7">${[['card', t('cv.text', 'Text card')], ['new_note', t('cv.linkNote', 'A note from Notebook')], ['image', t('cv.photo', 'Photo')], ['file_doc', t('cv.file', 'File or PDF')], ['sketch', t('cv.sketch', 'Sketch')], ['file_audio', t('cv.voice', 'Voice memo')], ['create_image', t('cv.image', 'Create an image')]].map(([i, l]) => `<div class="mi">${ic(i)}${l}</div>`).join('')}</div>`;
  if (st === 'select') return `${head}${board}${toolbar(`${tb('group', t('cv.group', 'Group'))}${tb('tag', t('cv.tag', 'Tag'))}${tb('ai_organize', t('cv.tidy', 'Tidy up'))}${tb('think_with_halo', t('ed.ask', 'Ask halo'))}${tb('trash', t('cv.remove', 'Remove'))}`)}<div class="live" style="bottom:78px;box-shadow:var(--shadow)"><span>${t('cv.selected', '2 cards selected')}</span></div>`;
  if (st === 'ahead') return `${head}${board}${tools}<div class="live rise" style="bottom:78px"><span class="orbwrap" style="width:22px;height:22px">${orb('idle', th, 20)}</span><span>${t('cv.ahead', 'Group the 4 kitchen cards?')}</span><span class="btn sm" style="height:30px;padding:0 10px">${t('cv.group', 'Group')}</span><span class="x">${ic('close')}</span></div>`;
  return `${head}${board}${tools}`;
};

/* ---------- NB05 Canvas: halo drafts, voice memo, PDF pages ---------- */
STATES_C.NB05 = ['draft', 'voice', 'transcript', 'pdf', 'failed'];
SCREENS_C.NB05 = (st, th) => {
  const head = `${sb()}${edTop('Kitchen renovation', { right: `<span class="ib">${ic('fit_all')}</span><span class="ib">${ic('more')}</span>` })}`;
  const base = `<div style="position:absolute;left:0;right:0;top:84px;bottom:0;overflow:hidden">${grid}${cnode(20, 40, 130, '<b>Budget</b><br>€ 8 000 max', { icon: 'task' })}${cnode(196, 40, 140, '<b>Worktop</b><br>Oak or quartz?')}`;
  if (st === 'draft') return `<div class="aura idle"></div>${head}${base}${cnode(30, 170, 300, `<span class="tag" style="margin-bottom:6px">${ic('ai_draft')}${t('cv.proposed', 'Proposed by halo · not saved')}</span><div style="margin-top:6px"><b>Worktop: quartz</b><br>Within budget at about €1 900, easier to clean than oak, matches the green tiles.</div>
    <div class="btns" style="margin-top:8px"><span class="btn text sm">${t('nh.discard', 'Discard')}</span><span class="btn ghost sm">${t('nh.again', 'Try again')}</span><span class="btn sm">${t('cv.keep', 'Keep')}</span></div>`, { draft: true, cls: 'rise' })}</div>`;
  if (st === 'voice') return `<div class="aura listening"></div>${head}${base}</div><div class="sheet in"><div class="grab"></div><h2>${ic('file_audio')} ${t('cv.voice', 'Voice memo')}</h2>
    <div class="row" style="margin:8px 0"><span class="go stop"><i class="mic-live"></i><i class="mic-live r2"></i>${ic('stop')}</span><div class="wave" style="height:28px">${[8, 14, 20, 11, 22, 16, 9, 18, 12, 21, 7, 15, 19, 10, 13, 17].map(h => `<i style="height:${h}px"></i>`).join('')}</div><b>0:18</b></div>
    <div class="xs mut">${t('cv.voiceNote', 'Recorded on this phone. You choose whether to turn it into text.')}</div></div>`;
  if (st === 'transcript') return `${head}${base}</div><div class="sheet in"><div class="grab"></div><h2>${t('cv.transcriptT', 'Check the text')}</h2>
    <div class="field focus" style="align-items:flex-start;min-height:90px;padding-top:10px"><span class="sm">The tiles from the shop on Brīvības iela are <span style="background:color-mix(in srgb,var(--att) 25%,transparent);border-radius:3px">€34</span> per square metre, green ones are in stock.</span></div>
    <div class="help">${t('cv.transcriptHelp', 'Transcribed on this phone. Highlighted words were hard to hear.')}</div>
    <div class="btns"><span class="btn text sm">${t('cv.audioOnly', 'Keep audio only')}</span><span class="btn sm">${t('cv.addCard', 'Add as card')}</span></div></div>`;
  if (st === 'pdf') return `${head}${base}</div><div class="scrim in"></div><div class="sheet in"><div class="grab"></div><h2>${t('cv.pdfT', 'Which pages?')}</h2><div class="sm mut">Kitchen catalogue.pdf · 24 pages</div>
    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:12px 0">${Array.from({ length: 8 }, (_, i) => `<div style="aspect-ratio:3/4;border-radius:8px;background:var(--bg);position:relative;box-shadow:${[2, 3, 6].includes(i) ? '0 0 0 2px var(--ink)' : 'inset 0 0 0 1px var(--line)'}"><span class="xs" style="position:absolute;left:5px;bottom:3px">${i + 1}</span>${[2, 3, 6].includes(i) ? `<span style="position:absolute;right:4px;top:4px;width:18px;height:18px;border-radius:50%;background:var(--fill);color:var(--on);display:grid;place-items:center">${ic('check')}</span>` : ''}</div>`).join('')}</div>
    <div class="btns"><span class="btn text sm">${t('common.cancel', 'Cancel')}</span><span class="btn">${t('cv.addPages', 'Add 3 pages')}</span></div></div>`;
  return `<div class="aura error"></div>${head}${base}${cnode(30, 170, 300, `<div class="row" style="gap:8px">${orb('error', th, 22)}<b>${t('cv.failT', 'halo couldn’t finish this draft')}</b></div><div class="sm mut" style="margin-top:4px">${t('cv.failB', 'The connection dropped halfway. Your board is unchanged.')}</div><div class="btns" style="margin-top:8px"><span class="btn text sm">${t('nb.dismiss', 'Dismiss')}</span><span class="btn sm guide">${t('nh.again', 'Try again')}</span></div>`, { draft: true })}</div>`;
};

/* ---------- NB06 File / object detail ---------- */
STATES_C.NB06 = ['pdf', 'image', 'missing', 'cant-read'];
SCREENS_C.NB06 = (st, th) => {
  const head = `${sb()}${topBar(st === 'image' ? 'Whiteboard photo' : 'Lease 2027.pdf', { back: true, right: `<span class="ib">${ic('speaker')}</span><span class="ib">${ic('more')}</span>` })}`;
  const acts = `<div class="row" style="gap:8px;margin-top:12px;flex-wrap:wrap"><span class="btn sm guide">${ic('think_with_halo')}${t('fd.ask', 'Ask about this')}</span><span class="btn ghost sm">${ic('study_this')}${t('fd.study', 'Study this')}</span><span class="btn ghost sm">${ic('export')}${t('fd.share', 'Share')}</span></div>`;
  if (st === 'missing') return `${head}<div class="body" style="bottom:92px"><div class="empty">${orb('warning', th, 76)}<div class="t2">${t('fd.missingT', 'The original file has moved')}</div><div class="sm mut">${t('fd.missingB', 'Your notes and highlights are safe. Show halo where the file is now.')}</div><span class="btn guide" style="margin-top:8px">${t('fd.relink', 'Find the file')}</span><span class="btn text sm">${t('fd.keepCopy', 'Keep halo’s copy')}</span></div></div>${nav('notebook')}`;
  if (st === 'cant-read') return `${head}<div class="body" style="bottom:92px"><div class="card s2" style="height:240px;display:grid;place-items:center"><div class="col" style="align-items:center">${ic('file_doc')}<span class="xs mut">Scanned pages</span></div></div>
    <div class="banner att" style="margin-top:12px">${ic('image')}<span class="sm"><b>${t('fd.cantT', 'These pages are pictures of text')}</b><br>${t('fd.cantB', 'halo needs to read photos to use them. You can let it, or type the parts you need.')}</span></div>
    <div class="btns"><span class="btn text sm">${t('next.notNow', 'Not now')}</span><span class="btn sm">${t('att.setupVision', 'Let halo see photos')}</span></div></div>${nav('notebook')}`;
  const viewer = st === 'image' ? `<div class="photo" style="height:260px"></div><div class="xs mut" style="margin-top:6px">${t('fd.fromGlasses', 'From your glasses · Thursday 14:02')}</div>`
    : `<div class="card s2" style="height:250px;padding:18px 22px;position:relative">${lines(9)}<span class="tag neu" style="position:absolute;right:10px;bottom:10px">p. 4 / 38</span></div>`;
  return `${head}<div class="body" style="bottom:92px">${viewer}${acts}<div class="lbl">${t('fd.used', 'Used in')}</div>${noteCard('new_note', 'Moving checklist', 'Note · cites page 4')}${noteCard('history', t('fd.chat', 'Chat · “When can I give notice?”'), 'Yesterday')}</div>${nav('notebook')}`;
};

/* ---------- NB07 Memory ---------- */
STATES_C.NB07 = ['memories', 'suggestion', 'detail', 'forget'];
SCREENS_C.NB07 = (st, th) => {
  const head = `${sb()}${topBar(t('mem.title', 'Memory'), { back: true, right: `<span class="ib">${ic('search')}</span><span class="ib">${ic('add')}</span>` })}`;
  const seg = (on) => `<div class="seg"><span class="chip ${on === 'm' ? 'sel' : 'line'}">${t('mem.remembered', 'Remembered · 42')}</span><span class="chip ${on === 's' ? 'sel' : 'line'}">${t('mem.suggestions', 'Suggestions · 2')}</span></div>`;
  const mrow = (txt, sub, o = '') => `<div class="li"><div class="av">${ic('memory')}</div><div class="grow"><div class="sm">${txt}</div><div class="xs mut">${sub}</div></div>${o}</div>`;
  if (st === 'suggestion') return `<div class="aura idle"></div>${head}<div class="body" style="bottom:92px">${seg('s')}<div class="sm mut" style="margin:0 2px 10px">${t('mem.sugSub', 'halo only remembers what you approve. The original stays in Notebook.')}</div>
    <div class="card guide"><div class="tag" style="margin-bottom:8px">${t('mem.new', 'New')}</div><div class="t2">Anna is your sister</div><div class="xs mut" style="margin-top:4px">${t('mem.from', 'From your chat on Sunday')}</div>
    <div class="btns"><span class="btn text sm">${t('mem.notTrue', 'Not true')}</span><span class="btn ghost sm">${t('mem.edit', 'Edit')}</span><span class="btn sm">${t('mem.remember', 'Remember')}</span></div></div>
    <div class="card" style="margin-top:10px"><div class="tag att" style="margin-bottom:8px">${t('mem.update', 'Update')}</div><div class="sm"><s class="mut">You work at Rimi</s><br><b>You work at Latvijas Banka</b></div>
    <div class="btns"><span class="btn text sm">${t('mem.keepOld', 'Keep current')}</span><span class="btn sm">${t('mem.useNew', 'Use new')}</span></div></div></div>${nav('notebook')}`;
  if (st === 'detail' || st === 'forget') return `${head}<div class="body" style="bottom:92px"><div class="card"><div class="t2">You prefer short answers in the morning</div>
    <div class="list" style="margin-top:8px"><div class="li" style="padding:7px 0">${ic('history')}<span class="sm grow">${t('mem.why', 'Why: you asked twice for “just the short version” before 9:00')}</span></div>
    <div class="li" style="padding:7px 0">${ic('privacy')}<span class="sm grow">${t('mem.where', 'Used in: conversations · stays on this phone')}</span></div></div>
    <div class="btns"><span class="btn ghost sm">${t('mem.correct', 'Correct')}</span><span class="btn line sm" style="color:var(--err)">${t('mem.forget', 'Forget')}</span></div></div></div>${nav('notebook')}
    ${st === 'forget' ? `<div class="scrim in"></div><div class="dlg"><h2>${t('mem.forgetT', 'Forget this?')}</h2><div class="sm mut">${t('mem.forgetB', 'halo stops using it right away. Your notes and chats aren’t deleted.')}</div><div class="btns"><span class="btn text">${t('common.cancel', 'Cancel')}</span><span class="btn danger">${t('mem.forget', 'Forget')}</span></div></div>` : ''}`;
  return `${head}<div class="body" style="bottom:92px">${seg('m')}<div class="card list" style="padding:2px 14px">${mrow('Anna is your sister', 'People · from a chat')}${mrow('You prefer short answers in the morning', 'Habits · learned')}${mrow('Allergic to peanuts', 'Health · you told halo')}
    ${mrow('Kitchen budget is €8 000', 'Projects · from a canvas', `<span class="tag att">${t('mem.check', 'Check')}</span>`)}${mrow('Learning Spanish', 'Learning · from Study')}</div>
    <div class="row" style="margin-top:10px">${ic('settings')}<span class="sm grow">${t('mem.useIt', 'Use memory in conversations')}</span>${sw(true)}</div></div>${nav('notebook')}`;
};

/* ---------- NB08 Saved chats ---------- */
STATES_C.NB08 = ['list', 'detail', 'deleted', 'empty'];
SCREENS_C.NB08 = (st, th) => {
  const head = `${sb()}${topBar(t('chats.title', 'Saved chats'), { back: true, right: `<span class="ib">${ic('search')}</span>` })}`;
  const row = (a, b, c) => `<div class="li"><div class="av">${ic('saved_chat')}</div><div class="grow"><div class="b sm ell">${a}</div><div class="xs mut ell">${b}</div></div><span class="xs mut">${c}</span></div>`;
  if (st === 'empty') return `${head}<div class="body" style="bottom:92px"><div class="empty">${orb('idle', th, 76)}<div class="t2">${t('chats.emptyT', 'No saved chats yet')}</div><div class="sm mut">${t('chats.emptyB', 'Conversations from Home are kept here so you can pick them up later.')}</div></div></div>${nav('notebook')}`;
  if (st === 'detail') return `${sb()}${topBar('Sunday lunch', { back: true, right: `<span class="ib">${ic('more')}</span>` })}<div class="body" style="bottom:160px">${userMsg('Plan Sunday lunch for 6')}${haloMsg(th, '<p>Roast chicken, baked potatoes, cucumber salad, and Anna’s dessert.</p>')}${userMsg('Add flowers to the list')}${haloMsg(th, `<p>Added.</p>`)}</div>
    <div style="position:absolute;left:16px;right:16px;bottom:92px"><span class="btn full guide">${ic('use_in_chat')}${t('chats.continue', 'Continue in Home')}</span></div>${nav('notebook')}`;
  const list = `<div class="card list" style="padding:2px 14px">${row('Sunday lunch', 'Added flowers to the list', 'Sun')}${row('Repot basil', 'Pot one size bigger…', 'Fri')}${row('Lease notice period', 'Two months, in writing', 'Wed')}${row('Spanish practice', '12 new words', 'Mon')}</div>`;
  return `${head}<div class="body" style="bottom:92px">${list}</div>${nav('notebook')}${st === 'deleted' ? `<div class="toast" style="bottom:96px"><span class="grow">${t('chats.deleted', 'Chat deleted')}</span><span class="btn text">${t('common.undo', 'Undo')}</span></div>` : ''}`;
};

/* ---------- S01 Study home ---------- */
STATES_C.S01 = ['home', 'empty'];
SCREENS_C.S01 = (st, th) => {
  const head = `<div class="aura idle"></div>${sb()}${topBar(t('study.title', 'Study'), { back: true })}`;
  if (st === 'empty') return `${head}<div class="body" style="bottom:92px"><div class="empty">${orb('idle', th, 84)}<div class="t2">${t('study.emptyT', 'Learn anything, at your pace')}</div><div class="sm mut">${t('study.emptyB', 'Choose a topic, add a photo, a PDF or a note, and halo guides you step by step.')}</div><span class="btn guide" style="margin-top:8px">${t('study.new', 'Start a new lesson')}</span></div></div>${nav('notebook')}`;
  return `${head}<div class="body" style="bottom:92px"><div class="card guide"><div class="row"><div class="av">${ic('practise')}</div><div class="grow"><div class="b">${t('study.review', 'Ready to remember')}</div><div class="xs mut">6 Spanish cards · about 4 min</div></div><span class="btn sm">${t('nb.start', 'Start')}</span></div></div>
    <div class="lbl">${t('study.continue', 'Pick up where you left off')}</div>
    ${noteCard('study', 'Photosynthesis', 'Stage 4 of 7 · Explore', { right: `<span class="btn ghost sm">${t('study.resume', 'Continue')}</span>` })}${noteCard('file_doc', 'Lease 2027 · key terms', 'Stage 2 of 7 · from a PDF', { right: `<span class="btn ghost sm">${t('study.resume', 'Continue')}</span>` })}
    <span class="btn ghost full" style="margin-top:14px">${ic('add')}${t('study.new', 'Start a new lesson')}</span></div>${nav('notebook')}`;
};

/* ---------- S02 New lesson ---------- */
STATES_C.S02 = ['goal', 'material', 'style', 'where', 'unavailable'];
SCREENS_C.S02 = (st, th) => {
  const k = { goal: 1, material: 2, style: 3, where: 4, unavailable: 2 }[st];
  const head = `${sb()}${topBar(t('study.new', 'Start a new lesson'), { back: true, right: `<span class="xs mut" style="margin-right:8px">${k} / 4</span>` })}<div style="display:flex;gap:4px;padding:0 16px">${[1, 2, 3, 4].map(i => `<i style="flex:1;height:4px;border-radius:2px;background:${i <= k ? 'var(--ink)' : 'var(--line)'}"></i>`).join('')}</div>`;
  const next = (lab, g = true) => `<div style="position:absolute;left:16px;right:16px;bottom:16px;display:flex;gap:10px"><span class="btn ghost">${t('common.back', 'Back')}</span><span class="btn grow ${g ? 'guide' : ''}" style="flex:1">${lab}</span></div>`;
  let b = '';
  if (st === 'goal') b = `<div class="ttl">${t('sw.goalT', 'What would you like to learn?')}</div><div class="field focus" style="min-height:64px">${ic('study')}<span class="grow">How photosynthesis works</span><span class="ib s2" style="width:40px;height:40px">${ic('mic')}</span></div>
    <div class="row" style="flex-wrap:wrap;gap:8px;margin-top:12px"><span class="chip">${t('sw.ex1', 'Spanish past tense')}</span><span class="chip">${t('sw.ex2', 'My lease, in plain words')}</span><span class="chip">${t('sw.ex3', 'Fractions for Mia')}</span></div>`;
  if (st === 'material' || st === 'unavailable') b = `<div class="ttl">${t('sw.matT', 'Add something to learn from')}</div><div class="sm mut" style="margin-bottom:10px">${t('sw.matSub', 'Optional. Without it, halo uses what it already knows.')}</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">${[['camera', t('sw.photo', 'Photo of a page')], ['file_doc', t('sw.pdf', 'PDF or document')], ['new_note', t('sw.note', 'A note')], ['link', t('sw.link', 'A web page')]].map(([i, l], j) => `<div class="card" style="padding:14px;${st === 'material' && j === 1 ? 'box-shadow:0 0 0 2px var(--ink)' : ''}"><div style="color:var(--ink)">${ic(i)}</div><div class="b sm" style="margin-top:6px">${l}</div></div>`).join('')}</div>
    ${st === 'material' ? `<div class="card s2" style="margin-top:12px"><div class="row">${ic('file_doc')}<div class="grow"><div class="b sm">Biology ch. 4.pdf</div><div class="xs mut">${t('sw.ready', 'Text ready · 12 pages')}</div></div>${ic('check')}</div></div>`
    : `<div class="banner att" style="margin-top:12px">${ic('image')}<span class="sm"><b>${t('sw.unavT', 'halo can’t read photos yet')}</b><br>${t('sw.unavB', 'Pick a PDF with text, a note, or let halo see photos.')}</span></div>`}`;
  if (st === 'style') b = `<div class="ttl">${t('sw.styleT', 'How do you like to learn?')}</div><div class="col">${[[t('sw.s1', 'Explain it simply'), t('sw.s1b', 'Short steps, everyday examples')], [t('sw.s2', 'Quiz me'), t('sw.s2b', 'Questions first, help when stuck')], [t('sw.s3', 'Talk it through'), t('sw.s3b', 'Mostly voice, hands-free')]].map(([a, c], j) => `<div class="card" style="padding:12px 14px;${j === 0 ? 'box-shadow:0 0 0 2px var(--ink)' : ''}"><div class="b sm">${a}</div><div class="xs mut">${c}</div></div>`).join('')}</div>
    <div class="xs mut" style="margin-top:10px">${t('sw.styleNote', 'You can change this during the lesson.')}</div>`;
  if (st === 'where') b = `<div class="ttl">${t('sw.whereT', 'Where should halo think?')}</div><div class="sm mut" style="margin-bottom:10px">${t('sw.whereSub', 'Your PDF stays on this phone unless you choose the cloud.')}</div>
    <div class="col"><div class="card" style="padding:12px 14px"><div class="row">${ic('on_device')}<div class="grow"><div class="b sm">${t('sw.phone', 'On this phone')}</div><div class="xs mut">${t('sw.phoneSub', 'Private · a little slower')}</div></div></div></div>
    <div class="card" style="padding:12px 14px"><div class="row">${ic('cloud')}<div class="grow"><div class="b sm">${t('sw.cloud', 'Your cloud service')}</div><div class="xs mut">${t('sw.cloudSub', 'Faster · the PDF is sent to it')}</div></div></div></div></div>
    <div class="xs mut" style="margin-top:10px">${t('sw.noneSelected', 'Pick one to continue — nothing is chosen for you.')}</div>`;
  return `${head}<div class="body nonav" style="top:100px;bottom:80px">${b}</div>${next(st === 'where' ? t('sw.start', 'Start lesson') : t('common.next', 'Next'), st !== 'where')}`;
};

/* ---------- S03 Active lesson ---------- */
const STG = [['home', 'Start'], ['think', 'Discover'], ['explain', 'Explain'], ['connection', 'Explore'], ['practise', 'Practice'], ['review', 'Reflect'], ['history', 'Continue']];
const stagePath = (i) => `<div style="display:flex;align-items:center;margin:2px 0 10px">${STG.map((s, k) => `${k ? `<i style="flex:1;height:2px;background:${k <= i ? 'color-mix(in srgb,var(--ink) 60%,transparent)' : 'var(--line)'}"></i>` : ''}<span style="${k === i ? 'width:32px;height:32px;background:var(--fill);color:var(--on)' : k < i ? 'width:10px;height:10px;background:color-mix(in srgb,var(--ink) 70%,transparent)' : 'width:10px;height:10px;background:var(--line)'};border-radius:50%;display:grid;place-items:center;flex:none">${k === i ? ic(s[0]) : ''}</span>`).join('')}</div><div class="xs b" style="color:var(--ink);letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px">${t('st3.stage', 'Step')} ${i + 1} ${t('st3.of', 'of 7')} · ${STG[i][1]}</div>`;
STATES_C.S03 = ['explain', 'practice', 'hint', 'paused'];
SCREENS_C.S03 = (st, th) => {
  const head = `<div class="aura ${st === 'paused' ? 'idle' : 'idle'}"></div>${sb()}${topBar('Photosynthesis', { back: true, right: `<span class="ib">${ic('speaker')}</span><span class="ib">${ic('more')}</span>` })}`;
  const dock = `<div class="comp" style="bottom:12px"><div class="in"><span class="ph-t">${st === 'practice' || st === 'hint' ? t('st3.answerPh', 'I think the answer is… because…') : t('st3.askPh', 'Ask a question…')}</span><span class="go">${ic('mic')}</span></div></div>`;
  if (st === 'explain') return `${head}<div class="body" style="bottom:90px">${stagePath(2)}<div class="card"><div class="t2">${t('st3.q1', 'Plants make food from light')}</div>
    <p class="sm" style="margin:8px 0">Leaves catch sunlight and use it to turn <span class="hl" style="background:color-mix(in srgb,var(--fill) 32%,transparent);border-radius:4px;padding:0 3px">water and carbon dioxide</span> into sugar. Oxygen is what’s left over.</p>
    <div class="src"><span>${ic('file_doc')}Biology ch. 4 · p. 3</span></div></div>
    <div class="row" style="gap:8px;margin-top:12px"><span class="btn ghost sm">${t('st3.example', 'Show an example')}</span><span class="btn sm guide">${t('st3.next', 'Got it')}</span></div></div>${dock}`;
  if (st === 'practice' || st === 'hint') return `${head}<div class="body" style="bottom:90px">${stagePath(4)}<div class="card"><div class="t2">${t('st3.q2', 'Why do plants need light?')}</div>
    ${st === 'hint' ? `<div class="banner info" style="margin-top:10px">${ic('guide_me')}<span class="sm">${t('st3.hint1', 'Hint: think about what light gives the leaf — it isn’t food itself.')}</span></div>` : ''}
    <div style="display:flex;gap:6px;margin-top:12px">${[['guide_me', t('st3.hint', 'Hint')], ['explain', t('st3.ex', 'Example')], ['review', t('st3.reveal', 'Show answer')]].map(([i, l], j) => `<div class="card s2" style="flex:1;padding:8px 4px;text-align:center;${st === 'hint' && j === 0 ? 'background:color-mix(in srgb,var(--fill) 22%,var(--s1))' : ''}"><div style="display:grid;place-items:center;color:var(--ink)">${ic(i)}</div><div class="xs b">${l}</div></div>`).join('')}</div>
    <div class="xs mut" style="margin-top:8px">${t('st3.revealNote', 'Show answer asks first — trying is how it sticks.')}</div></div></div>${dock}`;
  return `${head}<div class="body" style="bottom:90px">${stagePath(3)}<div class="empty" style="padding-top:20px">${orb('stopped', th, 70)}<div class="t2">${t('st3.paused', 'Lesson paused')}</div><div class="sm mut">${t('st3.pausedB', 'Your place is saved. Pick up here any time.')}</div><span class="btn guide" style="margin-top:8px">${t('study.resume', 'Continue')}</span></div></div>`;
};

/* ---------- S04 Lesson source & details ---------- */
STATES_C.S04 = ['material', 'check-text', 'failed', 'change'];
SCREENS_C.S04 = (st, th) => {
  const head = `${sb()}${topBar(t('s4.title', 'Lesson material'), { back: true })}`;
  if (st === 'check-text') return `${head}<div class="body nonav"><div class="photo" style="height:170px"></div><div class="ttl" style="margin-top:12px;font-size:19px">${t('s4.checkT', 'Check what halo read')}</div>
    <div class="field" style="align-items:flex-start;min-height:110px;padding-top:10px"><span class="sm">Exercise 3: A rectangle is 12 cm long and <span style="background:color-mix(in srgb,var(--att) 28%,transparent);border-radius:3px">7?</span> cm wide. What is its area?</span></div>
    <div class="help">${t('s4.checkHelp', 'Highlighted parts were hard to read. Fix them before halo uses this.')}</div>
    <div class="btns" style="position:absolute;left:16px;right:16px;bottom:16px"><span class="btn ghost">${t('cap.retake', 'Retake')}</span><span class="btn">${t('s4.looksRight', 'Looks right')}</span></div></div>`;
  if (st === 'failed') return `${head}<div class="body" style="bottom:92px"><div class="empty">${orb('warning', th, 76)}<div class="t2">${t('s4.failT', 'halo couldn’t read this page')}</div><div class="sm mut">${t('s4.failB', 'It may be too dark or blurry. Your lesson is unchanged.')}</div><span class="btn guide" style="margin-top:8px">${t('s4.retake', 'Take another photo')}</span><span class="btn text sm">${t('s4.type', 'Type it instead')}</span></div></div>${nav('notebook')}`;
  const base = `${head}<div class="body" style="bottom:92px"><div class="seg"><span class="chip sel">${t('s4.mat', 'Material')}</span><span class="chip line">${t('s4.prog', 'Progress')}</span><span class="chip line">${t('s4.lang', 'Languages')}</span></div>
    <div class="card s2" style="height:200px;padding:16px 20px;position:relative">${lines(7)}<span class="tag neu" style="position:absolute;right:10px;bottom:10px">p. 3 / 12</span></div>
    <div class="row" style="gap:8px;margin-top:10px"><span class="btn ghost sm">${ic('speaker')}${t('s4.read', 'Read page aloud')}</span><span class="btn ghost sm">${ic('search')}${t('s4.find', 'Find')}</span></div>
    <div class="btn text sm" style="padding:0;margin-top:10px">${t('s4.changeSrc', 'Use different material…')}</div></div>${nav('notebook')}`;
  if (st === 'change') return `${base}<div class="scrim in"></div><div class="dlg"><h2>${t('s4.changeT', 'Use different material?')}</h2><div class="sm mut">${t('s4.changeB', 'Your notes and cards so far are kept. halo will restart the lesson from step 1 with the new material.')}</div>
    <div class="btns"><span class="btn text">${t('s4.keepCur', 'Keep current')}</span><span class="btn">${t('s4.changeIt', 'Choose new material')}</span></div></div>`;
  return base;
};

/* ---------- S05 Practice & review ---------- */
STATES_C.S05 = ['front', 'back', 'done'];
SCREENS_C.S05 = (st, th) => {
  const head = `<div class="aura ${st === 'done' ? 'success' : 'idle'}"></div>${sb()}${topBar(t('s5.title', 'Review'), { back: true, right: `<span class="xs mut" style="margin-right:12px">${st === 'done' ? '6 / 6' : '3 / 6'}</span>` })}`;
  if (st === 'done') return `${head}<div class="body nonav"><div class="empty" style="padding-top:10px"><span class="orbwrap" style="width:90px;height:90px">${orb('success', th, 86)}</span><div class="ttl" style="margin-top:10px">${t('s5.doneT', 'All 6 reviewed')}</div><div class="sm mut">${t('s5.doneB', '5 you knew, 1 to see again tomorrow.')}</div></div>
    <div class="card s2"><div class="row">${ic('history')}<span class="sm grow">${t('s5.next', 'Next review: tomorrow · 2 cards')}</span></div></div>
    <div class="col" style="position:absolute;left:16px;right:16px;bottom:16px"><span class="btn ghost full">${ic('new_note')}${t('s5.save', 'Save a summary to Notebook')}</span><span class="btn full">${t('common.done', 'Done')}</span></div></div>`;
  const card = `<div class="card" style="height:300px;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;padding:24px">${st === 'front' ? `<div class="xs mut">${t('s5.es', 'Spanish → English')}</div><div style="font:600 calc(34px*var(--fs)) Sora;margin-top:12px">comí</div><span class="ib s2" style="margin-top:12px">${ic('speaker')}</span>`
    : `<div class="xs mut">comí</div><div style="font:600 calc(28px*var(--fs)) Sora;margin-top:10px">I ate</div><div class="sm mut" style="margin-top:8px">comer · past, “yo”</div>`}</div>`;
  const acts = st === 'front' ? `<span class="btn full guide">${t('s5.show', 'Show answer')}</span>` : `<div style="display:flex;gap:8px;width:100%">${[[t('s5.again', 'Again'), 'line'], [t('s5.almost', 'Almost'), 'ghost'], [t('s5.knew', 'I knew it'), '']].map(([l, c]) => `<span class="btn ${c}" style="flex:1">${l}</span>`).join('')}</div>`;
  return `${head}<div class="body nonav" style="bottom:90px">${card}<div class="xs mut" style="text-align:center;margin-top:10px">${st === 'back' ? t('s5.how', 'How well did you remember?') : t('s5.sayIt', 'Say it out loud, then check.')}</div></div><div style="position:absolute;left:16px;right:16px;bottom:16px">${acts}</div>`;
};
window.SCREENS_C = SCREENS_C; window.STATES_C = STATES_C;
