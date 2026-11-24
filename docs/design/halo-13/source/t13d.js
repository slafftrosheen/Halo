/* halo 13.0 · batch 4 — Canvas, drawing without walls, Studio, Notes, Translate + Scan (boards 155–160) */
const B13d={};
const lr4=(ic,t,sub,r='')=>`<div class="lr" style="min-height:40px">${I(ic)}<div class="gr">${t}${sub?`<small>${sub}</small>`:''}</div>${r}</div>`;
const card4=(x,y,w,h,bg,t,o='')=>`<div style="position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;border-radius:10px;background:${bg};color:#14102a;font:600 10.5px/1.25 Atk;padding:6px 7px;box-shadow:0 2px 6px rgba(20,16,42,.12);${o}">${t}</div>`;
const bar4=(items,on=0)=>`<div style="position:absolute;left:8px;right:8px;bottom:84px;display:flex;justify-content:space-around;background:#17125a;border:1px solid rgba(201,184,255,.25);border-radius:18px;padding:7px 4px">${items.map(([ic,t],i)=>`<span style="display:flex;flex-direction:column;align-items:center;gap:2px;font:600 9.5px Atk;color:${i==on?'var(--mint)':'var(--t2)'}">${I(ic,'width:19px;height:19px')}${t}</span>`).join('')}</div>`;
const ink4=(d,c='#7B61D9',w=3)=>`<path d="${d}" fill="none" stroke="${c}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`;

/* ───────── 155 · Canvas ───────── */
const p155a=`<div class="q-top">${I('back')}<b>Canvas</b>${I('search')}</div>
<p class="sub">A big table for ideas. Start with one of these.</p>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:7px;margin:6px 0 10px">
${[['board_blank','Blank','an empty table'],['mind_map','Mind map','one idea in the middle'],['moodboard','Moodboard','pictures and colours'],['tidy_up','Compare','two or three columns']].map(([ic,t,s],i)=>`<div class="q-blk" style="margin:0;padding:10px;${i==0?'border-color:rgba(12,242,186,.5)':''}"><h4 style="margin-bottom:2px">${I(ic)}${t}</h4><p style="font-size:11px">${s}</p></div>`).join('')}</div>
<div class="lab" style="margin-bottom:4px">Your boards</div>
${lr4('canvas','Trip to Tartu','Edited today · 14 things')}${lr4('canvas','Kitchen ideas','Last week · 31 things')}${lr4('canvas','Mia’s science fair','2 weeks ago · 9 things')}`;
const p155b=`<div class="q-top">${I('back')}<b>Trip to Tartu</b>${I('more')}</div>
<div class="r-cv" style="height:372px;margin-top:2px">
${card4(14,18,96,52,'#FFE08A','Train Fri 08:10<br><span style="font-weight:500">from Rīga</span>')}
${card4(130,14,100,64,'#C9B8FF','Places to see<br><span style="font-weight:500">AHHAA · old town</span>')}
${card4(22,108,90,90,'#fff',`<div style="height:58px;border-radius:6px;background:url(img/leaf.jpg) center/cover"></div>Botanic garden`)}
${card4(136,120,92,46,'#B4F5E4','Book hotel')}
<svg width="250" height="372" style="position:absolute;left:0;top:0">${ink4('M112 44 L130 44','#14102a',2)}${ink4('M182 78 L182 120','#14102a',2)}${ink4('M30 250 C50 220 70 280 92 240 S130 262 150 236','#7B61D9',3)}</svg>
${card4(150,270,80,40,'#FFD3BE','Rain Sat?<br><span style="font-weight:500">weather</span>')}
</div>
${bar4([['new_note','Write'],['draw','Draw'],['image','Picture'],['shapes','Shape'],['connect_cards','Connect']],0)}`;
const p155c=`<div class="q-top">${I('back')}<b>Kitchen ideas</b></div>
<div class="r-cv" style="height:200px">
${card4(14,14,64,36,'#C9B8FF','Oak shelf')}${card4(90,22,64,36,'#FFE08A','£ budget')}${card4(170,10,64,36,'#B4F5E4','Green tiles')}
${card4(28,80,64,36,'#FFE08A','Quotes ×3')}${card4(120,90,64,36,'#C9B8FF','Brass tap')}${card4(180,140,58,36,'#B4F5E4','Sage paint')}
${card4(40,148,64,36,'#FFE08A','Plumber?')}</div>
<div class="r-sheet" style="bottom:8px"><div class="r-grab"></div><h5>${I('tidy_up')}Tidy up?</h5>
<p class="r-p" style="margin-bottom:6px">I’d make 3 groups. Nothing moves until you say so.</p>
${[['Look','Oak shelf · Green tiles · Brass tap · Sage paint'],['Money','£ budget · Quotes ×3'],['To do','Plumber?']].map(([t,s])=>`<div class="lr" style="min-height:34px">${I('group')}<div class="gr">${t}<small>${s}</small></div></div>`).join('')}
<div style="display:flex;gap:6px"><div class="r-bt" style="flex:1">Tidy it</div><div class="r-bt s" style="flex:1;margin-top:8px">Not now</div></div></div>`;
const p155d=`<div class="q-top">${I('back')}<b>Find on this board</b></div>
<div class="q-comp" style="color:var(--cream)">${I('search')}hotel</div>
<div class="r-cv" style="height:150px;margin:8px 0;background:#E9E6DA">
${[[18,20,40,22,'#FFE08A'],[70,14,46,28,'#C9B8FF'],[22,62,36,36,'#fff'],[78,70,40,20,'#0CF2BA'],[90,110,34,18,'#FFD3BE'],[150,30,50,30,'#C9B8FF'],[170,90,40,26,'#FFE08A']].map(([x,y,w,h,c])=>`<i style="position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;border-radius:4px;background:${c};${c=='#0CF2BA'?'box-shadow:0 0 0 3px #0AA983':''}"></i>`).join('')}
<i style="position:absolute;left:56px;top:50px;width:90px;height:62px;border:2px solid #7B61D9;border-radius:6px"></i></div>
${lr4('task','Book hotel','Card · next to “Places to see”',`<span class="q-chip m">Go there</span>`)}
${lr4('notebook','Trip notes','Linked note · “hotel near the river”')}
<div class="lab" style="margin:10px 0 4px">Move around</div>
<div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Fit everything','','fit_all')}${chip('Back to start')}${chip('Next frame','','frame_slide')}</div>`;
const p155e=`<div class="q-top">${I('back')}<b>Show it</b>${I('more')}</div>
<p class="sub">Frames become pages you can swipe through.</p>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:7px;margin:8px 0">
${[['1','The trip','16:9'],['2','Getting there','16:9'],['3','Where we sleep','A4'],['4','Rain plan','Phone']].map(([n,t,f],i)=>`<div style="border-radius:12px;background:${i==0?'rgba(12,242,186,.12)':'rgba(250,250,244,.05)'};border:1px solid ${i==0?'rgba(12,242,186,.5)':'var(--line)'};padding:8px;height:76px"><b style="font:600 11.5px Sora">${n} · ${t}</b><small style="display:block;color:var(--t3);font-size:10px;margin-top:3px">${f}</small></div>`).join('')}</div>
<div class="r-bt">${I('play')}Start from frame 1</div>
<div style="display:flex;gap:5px;margin-top:8px;flex-wrap:wrap">${chip('Save as PDF','','export')}${chip('Send to a phone','','devices')}</div>`;
B13d['155']=()=>`<div class="boardpg">${head13('batch 4 · Canvas','Canvas: a big table for ideas, with five plain buttons','Today the Canvas editor has 12 kinds of thing (text, note, image, file, sketch, ink selection, group, tag, sticky, shape, frame, comment) behind dense menus. 13.0 keeps every one of them, but starts people with four plain board types, one bottom bar of five verbs, a “Tidy up” that only suggests, and frames you can show like slides.')}
<div class="r-ph" style="justify-content:space-between">
${P(p155a,'Start a board','four plain starts · recent boards')}
${P(p155b,'On the board','one bar: Write · Draw · Picture · Shape · Connect')}
${P(p155c,'Tidy up','suggests groups · nothing moves until you tap')}
${P(p155d,'Find on this board','search + map · “Go there”')}
${P(p155e,'Show it','frames become swipeable pages')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.2"><h3>${I('plain_words')}12 kinds of thing → 5 verbs</h3>
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:3px 16px;font-size:12px;color:var(--t2)">${[['TEXT · NOTE · STICKY','Write'],['SKETCH · INK_SELECTION','Draw'],['IMAGE · FILE','Picture (and files)'],['SHAPE · FRAME','Shape · frames under “More”'],['edges','Connect'],['GROUP · TAG · COMMENT','long-press menu: Group, Tag, Comment']].map(([a,b])=>`<div style="padding:4px 0;border-bottom:1px solid var(--line)"><code style="font:500 10px ui-monospace,Menlo,monospace;color:var(--t3)">${a}</code><br><b style="color:var(--cream)">${b}</b></div>`).join('')}</div></div>
<div class="box" style="flex:1"><h3>${I('level_pro')}By depth</h3><p><b>Simple</b>: Write, Draw, Picture; no lines or frames. <b>Standard</b>: all five verbs, Tidy up, Find. <b>Pro</b>: frames, review pins, image tools, PDF pages, minimap always on.</p><p style="margin-top:6px"><b>Tidy up</b> wraps the existing <code style="color:#B4E6D6">CanvasOrganizationProposal</code>: a preview, then one undo step (<code style="color:#B4E6D6">CanvasUndoSnapshot</code>).</p></div>
</div></div>`;

/* ───────── 156 · drawing without walls ───────── */
const p156a=`<div class="q-top">${I('back')}<b>Sketch</b>${I('undo')}${I('redo')}</div>
<div class="r-cv" style="height:300px;margin-top:4px;background:#fff">
<span class="r-bad" style="top:8px;left:8px">Today</span>
<svg width="250" height="300">${ink4('M30 80 C60 40 90 120 120 70 S170 40 210 90','#14102a',2.5)}${ink4('M30 130 C70 100 100 170 140 120 S190 110 220 150','#14102a',2.5)}${ink4('M40 190 C80 160 110 220 150 180','#14102a',2.5)}</svg>
<div style="position:absolute;left:10px;right:10px;bottom:10px;padding:8px 10px;border-radius:10px;background:rgba(255,87,112,.14);color:#b3122e;font:600 11px/1.35 Atk">Drawing limit reached. Lift the pen; undo or erase strokes to make room.</div></div>
<p class="r-p" style="margin-top:8px">A wall in the middle of a drawing, shown in red, the colour halo keeps for broken sources. Older sketches stop at 64 lines.</p>`;
const p156b=`<div class="q-top">${I('back')}<b>Sketch</b>${I('undo')}${I('redo')}</div>
<div class="r-cv" style="height:300px;margin-top:4px;background:#fff">
<span class="r-good" style="top:8px;left:8px">13.0</span>
<svg width="250" height="300">${ink4('M30 80 C60 40 90 120 120 70 S170 40 210 90','#14102a',2.5)}${ink4('M30 130 C70 100 100 170 140 120 S190 110 220 150','#14102a',2.5)}${ink4('M40 190 C80 160 110 220 150 180','#14102a',2.5)}${ink4('M150 180 C170 165 190 200 215 185','#0AA983',3)}</svg>
<div style="position:absolute;right:10px;bottom:10px;display:flex;align-items:center;gap:6px;padding:5px 9px;border-radius:999px;background:rgba(10,169,131,.12);color:#076a52;font:700 10.5px Atk">${I('sheet_next','width:15px;height:15px')}Sheet 2 of this drawing</div></div>
<p class="r-p" style="margin-top:8px">The next line just keeps going. Behind the scenes it lands on a new sheet stacked under the same drawing. No message, no red.</p>`;
const p156c=`<div class="q-top">${I('back')}<b>Garden plan</b>${I('more')}</div>
<div class="r-cv" style="height:220px;background:#fff">
<svg width="250" height="220">${ink4('M20 40 H230 V200 H20 Z','#14102a',2)}${ink4('M40 60 C80 50 90 110 60 120','#0AA983',3)}${ink4('M120 70 a22 22 0 1 0 44 0 a22 22 0 1 0 -44 0','#E0763F',3)}${ink4('M60 160 L200 160','#7B61D9',3)}${ink4('M150 120 C170 110 200 140 210 120','#0AA983',3)}</svg></div>
<div class="lab" style="margin:10px 0 5px">Sheets · one drawing</div>
<div style="display:flex;gap:6px">${['1','2','3'].map((n,i)=>`<div style="width:62px;height:48px;border-radius:8px;background:#fff;border:2px solid ${i==2?'#0CF2BA':'rgba(250,250,244,.15)'};position:relative"><b style="position:absolute;right:4px;bottom:2px;font:700 10px Atk;color:#14102a">${n}</b></div>`).join('')}<div style="width:62px;height:48px;border-radius:8px;border:1.5px dashed var(--t3);display:grid;place-items:center;color:var(--t3)">${I('add')}</div></div>
<div style="display:flex;gap:5px;margin-top:9px;flex-wrap:wrap">${chip('See all as one','m')}${chip('Hide a sheet')}${chip('Flatten to picture')}</div>`;
const p156d=`<div class="q-top">${I('back')}<b>Biology notes</b>${I('more')}</div>
<p class="r-big" style="font-size:19px">The leaf</p>
<p class="r-p" style="color:var(--cream)">Light goes in, sugar comes out. The green comes from chlorophyll.</p>
<div style="margin:8px 0;border-radius:14px;background:#fff;height:140px;position:relative">
<svg width="232" height="140">${ink4('M30 110 C40 40 150 20 200 30 C190 90 120 130 30 110 Z','#0AA983',3)}${ink4('M30 110 L170 45','#0AA983',2)}${ink4('M180 70 L215 70','#E0763F',2.5)}</svg>
<span style="position:absolute;right:8px;top:62px;font:600 10px Atk;color:#14102a">sun</span>
<span style="position:absolute;left:8px;bottom:6px;font:600 9.5px Atk;color:#55536f;display:flex;align-items:center;gap:4px">${I('draw_block','width:13px;height:13px')}Drawing · tap to draw more</span></div>
<p class="r-p" style="color:var(--cream)">Draw right inside a note. It is the same drawing as on Canvas, so edits show in both.</p>
<div style="display:flex;gap:5px;margin-top:6px">${chip('Turn writing into text','l','handwriting')}</div>`;
const p156e=`<div class="q-top">${I('back')}<b>Turn writing into text</b></div>
<div style="border-radius:14px;background:#fff;height:110px;position:relative;margin:4px 0 10px">
<svg width="232" height="110">${ink4('M16 50 C22 30 30 30 30 50 S40 60 46 40 M56 32 V58 M66 42 C72 32 82 36 80 46 S70 58 66 52 M96 40 C100 30 112 34 108 48 S96 60 104 58 M124 36 C140 30 140 60 124 56','#14102a',2.5)}${ink4('M16 86 C30 70 40 100 56 80 S80 96 96 84','#14102a',2.5)}</svg></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.45)"><h4>${I('handwriting')}I read</h4><p style="color:var(--cream);font-size:13px">Buy seeds <span style="background:rgba(255,159,107,.25);border-bottom:2px solid var(--ap);border-radius:3px;padding:0 2px">Sat?</span></p></div>
<div class="q-why" style="position:relative;left:0;right:0;margin-top:6px"><b>Not sure about one word.</b>Tap it to fix it. Works on this phone, no internet.</div>
<div style="display:flex;gap:6px"><div class="r-bt" style="flex:1">Add as text</div><div class="r-bt s" style="flex:1;margin-top:8px">Keep drawing</div></div>`;
B13d['156']=()=>`<div class="boardpg">${head13('batch 4 · drawing without walls','Draw as long as you like: the 64-line wall comes down','In the repo, older sketches (sketch:v1) stop at 64 strokes, and newer ink stops at 256 strokes or 16,384 points. When you hit it, the editor shows “Drawing limit reached” in the error colour. 13.0 keeps those safe limits per sheet, but stacks new sheets under the same drawing, so you never meet a wall. Drawings also move into Notes.')}
<div class="r-ph" style="justify-content:space-between">
${P(p156a,'Today','a wall, in red, mid-drawing',{cls:''})}
${P(p156b,'13.0 · keep drawing','next line lands on sheet 2, silently')}
${P(p156c,'Sheets of one drawing','see all as one · hide · flatten')}
${P(p156d,'Drawing in a note','same drawing as on Canvas')}
${P(p156e,'Writing into text','on this phone · unsure words in apricot')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="code" style="flex:1.1"><span class="c">// ui/canvas/v13/InkSheets.kt · storage untouched</span>
<span class="k">val</span> fill = InkSheets.fullness(doc)          <span class="c">// 0.0–1.0 of InkLimits</span>
<span class="k">if</span> (fill >= <span class="n">0.9</span>) sheets.prepareNext()        <span class="c">// quiet, off the UI thread</span>
<span class="k">if</span> (!editor.begin(tool)) sheets.continueOn(next) <span class="c">// never show ink_capacity</span>
<span class="c">// each sheet = one SKETCH node, all in one GROUP,</span>
<span class="c">// edges labelled "continues" · v1 sketches open as v4</span></div>
<div class="box" style="flex:1"><h3>${I('sheet_next')}Rules</h3><ul><li><b>Limits stay per sheet</b> (256 lines · 16,384 points), so saving, sync and OCR stay fast.</li><li><b>Up to 32 sheets</b> per drawing; past 24, a calm note suggests a new drawing.</li><li><b>Undo crosses sheets.</b> Erase works on all of them.</li><li><b>Old 64-line sketches</b> are upgraded the first time they are edited. The original is kept until the save succeeds.</li></ul></div>
<div class="box" style="flex:.9"><h3>${I('notebook')}In Notes</h3><p>No new block type. A drawing is a <code style="color:#B4E6D6">REFERENCE</code> block pointing at a Canvas sketch, shown inline. Notes without Canvas boards get a hidden “Note drawings” board.</p></div>
</div></div>`;

/* ───────── 157 · Studio ───────── */
const p157a=`<div class="q-top">${I('back')}<b>Studio</b></div>
<p class="r-big" style="font-size:19px">What are you making?</p>
${[['studio_square','A square picture','for a post or a profile'],['postcard','A postcard','wide, with room to write'],['image','A tall picture','for a phone screen or story'],['board_blank','Blank','any size'],['camera','Start from a photo','take one or pick one']].map(([ic,t,s],i)=>`<div class="opt ${i==0?'on':''}" style="padding:9px 11px"><div class="oi">${I(ic)}</div><div><b>${t}</b><small>${s}</small></div></div>`).join('')}`;
const p157b=`<div class="q-top">${I('back')}<b>Birthday card</b>${I('undo')}</div>
<div style="height:250px;border-radius:14px;background:linear-gradient(160deg,#FFE08A,#FFB38A);position:relative;overflow:hidden;margin-top:2px">
<div style="position:absolute;left:30px;top:40px;width:110px;height:110px;border-radius:50%;background:#C9B8FF;opacity:.9"></div>
<div style="position:absolute;left:100px;top:90px;width:110px;height:110px;border-radius:50%;background:#0CF2BA;opacity:.85"></div>
<b style="position:absolute;left:18px;bottom:18px;font:700 26px Sora;color:#14102a">Happy 7th, Leo!</b></div>
<div style="display:flex;gap:5px;margin-top:9px;overflow:hidden">${chip('Draw','m','draw')}${chip('Shapes','','shapes')}${chip('Text','','heading')}${chip('Photo','','image')}${chip('Colour','','colour_pick')}</div>
${bar4([['draw','Brush'],['eraser','Eraser'],['smudge','Smudge'],['colour_pick','Pick colour'],['layers_plain','Layers']],0)}`;
const p157c=`<div class="q-top">${I('back')}<b>Things on this picture</b></div>
<p class="sub">Top of the list is in front. Drag to reorder.</p>
${[['heading','“Happy 7th, Leo!”','Text',1],['shapes','Green circle','Shape',1],['shapes','Lilac circle','Shape',1],['draw','Your drawing','Drawing · 2 sheets',0],['image','Background','Colour fill',1]].map(([ic,t,s,v])=>`<div class="lr" style="min-height:42px">${I(ic)}<div class="gr">${t}<small>${s}</small></div>${I(v?'read_mode':'unavailable','width:17px;height:17px;opacity:.7')}${I('drag_block','width:17px;height:17px;opacity:.6')}</div>`).join('')}
<div class="q-blk" style="margin-top:8px"><h4>${I('join_shapes')}Two shapes chosen</h4><div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Join','m','join_shapes')}${chip('Cut out','','cut_out')}${chip('Keep the overlap')}</div></div>`;
const p157d=`<div class="q-top">${I('back')}<b>Smudge</b></div>
<div style="height:210px;border-radius:14px;background:linear-gradient(90deg,#9EC9FF,#C9B8FF 45%,#FFB38A);position:relative">
<svg width="232" height="210" style="position:absolute;left:0;top:0">${ink4('M30 150 C70 120 120 170 170 120','rgba(255,255,255,.55)',14)}</svg></div>
<div class="lr" style="margin-top:8px">${I('smudge')}<div class="gr">Strength<small>Soft</small></div><span class="v">40%</span></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.4)"><h4>${I('check')}Smudge as much as you like</h4><p>Today you must stop and “apply” every 12 strokes. 13.0 applies them for you, quietly, and one undo still takes back one stroke.</p></div>`;
const p157e=`<div class="q-top">${I('back')}<b>Share or save</b></div>
<div style="height:120px;border-radius:14px;background:linear-gradient(160deg,#FFE08A,#FFB38A);margin-bottom:10px;position:relative"><b style="position:absolute;left:12px;bottom:10px;font:700 15px Sora;color:#14102a">Happy 7th, Leo!</b></div>
${lr4('image','Picture','for messages and posts')}${lr4('file_doc','PDF','for printing')}${lr4('shapes','Shapes file (SVG)','to keep editing elsewhere · Pro')}${lr4('archive','halo project','everything, to open on another phone')}
<div class="q-why" style="position:relative;left:0;right:0;margin-top:8px"><b>Your picture is kept as you go.</b>If halo closes, you pick up where you stopped.</div>`;
B13d['157']=()=>`<div class="boardpg">${head13('batch 4 · Studio','Studio asks what you are making, then gets out of the way','Studio already has layers (up to 32), brushes, smudge, symmetry, fills, shape joins (WELD, TRIM, INTERSECT), DXF import and export to SVG, DXF, PDF and .halo. 13.0 starts from the thing you are making, names tools by what they do, lists layers as “things on this picture” and removes the 12-stroke smudge stop.')}
<div class="r-ph" style="justify-content:space-between">
${P(p157a,'Start','what are you making? · 5 plain starts')}
${P(p157b,'Making','5 tool groups · one brush bar')}
${P(p157c,'Things on this picture','layers in plain words · join and cut out')}
${P(p157d,'Smudge, no stopping','auto-apply every 12 strokes')}
${P(p157e,'Share or save','by use, not by file type')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.1"><h3>${I('plain_words')}Plain names</h3><div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:3px 16px;font-size:12px;color:var(--t2)">${[['SQUARE · POSTCARD · PORTRAIT · BLANK','A square picture · A postcard · A tall picture · Blank'],['Layers','Things on this picture'],['WELD · TRIM · INTERSECT','Join · Cut out · Keep the overlap'],['LINEAR · RADIAL gradient','Fade across · Fade from the middle'],['VERTICAL · HORIZONTAL · RADIAL symmetry','Mirror left–right · top–bottom · Kaleidoscope'],['SVG · DXF · PDF · HALO','Shapes file · Cutter file (Pro) · PDF · halo project']].map(([a,b])=>`<div style="padding:4px 0;border-bottom:1px solid var(--line)"><code style="font:500 10px ui-monospace,Menlo,monospace;color:var(--t3)">${a}</code><br><b style="color:var(--cream)">${b}</b></div>`).join('')}</div></div>
<div class="box" style="flex:.9"><h3>${I('level_pro')}By depth</h3><p><b>Simple</b>: Draw, Text, Photo, Colour; no layers list. <b>Standard</b>: shapes, layers, smudge, symmetry. <b>Pro</b>: path points, pixel selection, combine shapes, DXF, colour wheel with numbers.</p><p style="margin-top:6px"><b>Kids</b>: big brushes, stickers from Kids mode, no file export, auto-save to their gallery.</p></div>
</div></div>`;

/* ───────── 158 · Notes ───────── */
const p158a=`<div class="q-top">${I('back')}<b>New note</b></div>
<p class="sub">Start blank, or pick a shape for it.</p>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:7px;margin:8px 0">
${[['new_note','Blank'],['bullet_list','Shopping list'],['calendar','Meeting'],['note_template','Recipe'],['favorite','Diary'],['study','Class notes']].map(([ic,t],i)=>`<div class="q-blk" style="margin:0;padding:11px;${i==0?'border-color:rgba(12,242,186,.5)':''}"><h4 style="margin:0">${I(ic)}${t}</h4></div>`).join('')}</div>
<div class="lab" style="margin:8px 0 4px">Or just say it</div>
<div class="q-comp">${I('mic')}“Note: ideas for Mia’s party”</div>`;
const p158b=`<div class="q-top">${I('back')}<b>Party ideas</b>${I('more')}</div>
<p class="r-big" style="font-size:19px">Mia’s party · 14 Dec</p>
${chk('Book the pool room',1)}${chk('Ask Leo’s mum about the cake',0)}${chk('Invites by Friday',0)}
<p class="r-p" style="color:var(--cream);margin-top:6px">Theme: ocean. Blue and green balloons.</p>
<div style="position:absolute;left:8px;right:8px;bottom:84px;display:flex;justify-content:space-around;background:#17125a;border:1px solid rgba(201,184,255,.25);border-radius:16px;padding:8px 4px">${[['heading','Title'],['bullet_list','List'],['task','Tick list'],['draw_block','Drawing'],['more','More']].map(([ic,t],i)=>`<span style="display:flex;flex-direction:column;align-items:center;gap:2px;font:600 9.5px Atk;color:var(--t2)">${I(ic,'width:18px;height:18px')}${t}</span>`).join('')}</div>`;
const p158c=`<div class="q-top">${I('back')}<b>Party ideas</b></div>
${chk('Invites by Friday',0)}
<div class="r-sheet" style="bottom:8px"><div class="r-grab"></div><h5>${I('calendar')}Put it in Planner?</h5>
<div class="lr" style="min-height:36px">${I('task')}<div class="gr">Invites<small>Task · due Fri 28 Nov</small></div></div>
<div class="lr" style="min-height:36px">${I('link')}<div class="gr">Stays linked to this note<small>tick it in either place</small></div></div>
<div class="r-bt">Add to Planner</div><div class="r-bt s">Just keep it here</div></div>`;
const p158d=`<div class="q-top">${I('back')}<b>Club newsletter</b>${I('more')}</div>
<div style="display:flex;gap:5px;margin-bottom:8px">${chip('Suggesting','l','suggest_edit')}${chip('2 comments','','annotate')}</div>
<p class="r-p" style="color:var(--cream);line-height:1.55">Training moves to <span style="text-decoration:line-through;color:var(--t3)">Tuesday</span> <span style="background:rgba(201,184,255,.22);border-bottom:2px solid var(--lil)">Wednesday</span> from January. Bring <span style="background:rgba(201,184,255,.22);border-bottom:2px solid var(--lil)">your own</span> water.</p>
<div class="q-blk" style="margin-top:8px"><h4>${I('annotate')}Anna</h4><p>Is the hall free on Wednesdays?</p></div>
<div style="display:flex;gap:6px"><div class="r-bt" style="flex:1">Accept all</div><div class="r-bt s" style="flex:1;margin-top:8px">Go one by one</div></div>`;
const p158e=`<div class="q-top">${I('back')}<b>Read it to me</b></div>
<div class="q-blk"><h4>${I('speaker')}Party ideas</h4><p style="color:var(--cream)"><span style="background:rgba(12,242,186,.22);border-radius:3px">Book the pool room.</span> Ask Leo’s mum about the cake. Invites by Friday.</p></div>
<div style="display:flex;justify-content:center;gap:14px;margin:12px 0">${['undo','pause','redo'].map((ic,i)=>`<span style="width:${i==1?56:44}px;height:${i==1?56:44}px;border-radius:50%;display:grid;place-items:center;background:${i==1?'var(--mint)':'rgba(250,250,244,.08)'};color:${i==1?'#04211a':'var(--cream)'}">${I(ic)}</span>`).join('')}</div>
<div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Slower')}${chip('Brand voice','','voice')}${chip('Keep screen on')}</div>`;
B13d['158']=()=>`<div class="boardpg">${head13('batch 4 · Notes','Notes: five buttons for writing, the rest when you need it','The note editor knows 11 block types (paragraph, three headings, three lists, quote, code, divider, reference), fonts, page sizes, comments, tracked changes and read-back. 13.0 shows five buttons, offers shapes to start from, turns a tick line into a Planner task in one tap and makes “Suggesting” plain.')}
<div class="r-ph" style="justify-content:space-between">
${P(p158a,'New note','blank or a shape · or say it')}
${P(p158b,'Writing','Title · List · Tick list · Drawing · More')}
${P(p158c,'Tick line to Planner','linked both ways, preview first')}
${P(p158d,'Suggesting','tracked changes in plain words')}
${P(p158e,'Read it to me','follows along · slower · brand voice')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.1"><h3>${I('plain_words')}11 block types, plain names</h3><div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:3px 16px;font-size:12px;color:var(--t2)">${[['HEADING · HEADING_2 · HEADING_3','Title · Heading · Small heading'],['BULLET_LIST · NUMBERED_LIST','List · Numbered list'],['CHECKLIST','Tick list'],['QUOTE · DIVIDER','Quote · Line'],['CODE','Code (Pro)'],['REFERENCE','Link to something · Drawing']].map(([a,b])=>`<div style="padding:4px 0;border-bottom:1px solid var(--line)"><code style="font:500 10px ui-monospace,Menlo,monospace;color:var(--t3)">${a}</code><br><b style="color:var(--cream)">${b}</b></div>`).join('')}</div></div>
<div class="box" style="flex:.9"><h3>${I('level_pro')}By depth</h3><p><b>Simple</b>: Title, List, Tick list, Drawing; read aloud. <b>Standard</b>: all blocks, shapes to start from, comments, Planner link. <b>Pro</b>: Suggesting (NoteChange), fonts, page size, page numbers, code, PDF export.</p></div>
</div></div>`;

/* ───────── 159 · Translate + Scan ───────── */
const p159a=`<div class="q-top">${I('back')}<b>Translate</b>${I('more')}</div>
<div style="display:flex;align-items:center;gap:6px;margin-bottom:8px">${chip('Latvian','m')}${I('chevron_down','width:14px;height:14px;transform:rotate(-90deg)')}${chip('English','m')}</div><div style="margin:-2px 0 8px">${chip('No internet needed','','offline_pack')}</div>
<div class="q-blk"><h4>${I('mic')}You said</h4><p style="color:var(--cream)">Kur ir tuvākā aptieka?</p></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.45)"><h4>${I('speaker')}English</h4><p style="color:var(--cream);font-size:15px">Where is the nearest pharmacy?</p></div>
<div style="display:flex;justify-content:center;margin:12px 0 6px"><span style="width:84px;height:84px;border-radius:50%;background:var(--mint);color:#04211a;display:grid;place-items:center">${I('hold_talk','width:34px;height:34px')}</span></div>
<p class="sub" style="text-align:center">Hold to talk · let go to translate</p>
<div style="display:flex;gap:5px;justify-content:center;margin-top:6px">${chip('Type','','heading')}${chip('Camera','','camera')}${chip('Face to face','','face_to_face')}</div>`;
const p159b=`<div style="height:100%;display:flex;flex-direction:column;gap:8px">
<div style="flex:1;border-radius:16px;background:rgba(201,184,255,.14);padding:12px;transform:rotate(180deg);display:flex;flex-direction:column;justify-content:space-between"><p style="font:600 15px/1.35 Sora;color:var(--cream)">Two to Tartu, please. Return.</p><span class="q-chip l" style="align-self:flex-start">${I('hold_talk')}Hold to talk · English</span></div>
<div style="flex:1;border-radius:16px;background:rgba(12,242,186,.12);padding:12px;display:flex;flex-direction:column;justify-content:space-between"><p style="font:600 15px/1.35 Sora;color:var(--cream)">Divas biļetes uz Tartu, lūdzu. Turp un atpakaļ.</p><span class="q-chip m" style="align-self:flex-start">${I('hold_talk')}Hold to talk · Latvian</span></div></div>`;
const p159c=`<div style="margin:-6px -8px 0;height:270px;background:url(img/recipe.jpg) center/cover;position:relative;border-radius:0 0 18px 18px">
${[[30,40],[200,30],[210,230],[24,240]].map(([x,y],i)=>`<i style="position:absolute;left:${x}px;top:${y}px;width:18px;height:18px;border:4px solid #0CF2BA;border-${i<2?'bottom':'top'}:0;border-${i==0||i==3?'right':'left'}:0;border-radius:3px"></i>`).join('')}
<span style="position:absolute;left:50%;bottom:12px;transform:translateX(-50%);padding:5px 10px;border-radius:999px;background:rgba(4,2,31,.7);font:700 11px Atk;color:#0CF2BA;white-space:nowrap">Page found · hold still</span></div>
<p class="sub" style="margin-top:10px;text-align:center">Just point the phone. halo takes it when it’s sharp.</p>
<div style="display:flex;justify-content:center;gap:6px;margin-top:4px">${chip('Many pages','','add')}${chip('Magnifier','','zoom_in')}</div>`;
const p159d=`<div class="q-top">${I('back')}<b>A recipe</b>${I('more')}</div>
<div style="height:110px;border-radius:12px;background:url(img/recipe.jpg) center/cover;margin-bottom:8px"></div>
<p class="sub" style="margin-bottom:5px">What would you like to do?</p>
${[['speaker','Read it to me',1],['language','Translate to English',0],['new_note','Save as a note',0],['file_doc','Save as PDF',0],['ocr','Copy the text',0]].map(([ic,t,on])=>`<div class="opt ${on?'on':''}" style="padding:8px 11px;margin-bottom:5px"><div class="oi">${I(ic)}</div><div><b>${t}</b></div></div>`).join('')}`;
const p159e=`<div class="q-top">${I('back')}<b>A form</b></div>
<div style="background:#fff;border-radius:12px;padding:10px;color:#14102a;font:500 11px Atk;margin-bottom:8px">
${[['Name:','Anna Bērziņa',1],['Date of birth:','',0],['Phone:','',0]].map(([l,v,d])=>`<div style="display:flex;gap:6px;padding:5px 0;border-bottom:1px solid #ddd"><b>${l}</b><span style="flex:1;${d?'color:#0AA983':'background:rgba(255,159,107,.2);border-radius:3px'}">${v||'&nbsp;'}</span></div>`).join('')}</div>
<div class="q-blk"><h4>${I('form_fill')}3 boxes found · 1 filled</h4><p>I only fill what you type or say. I never guess a date or a number.</p></div>
<div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Next box','m')}${chip('Sign it','','signature')}${chip('Save as PDF','','file_doc')}</div>`;
B13d['159']=()=>`<div class="boardpg">${head13('batch 4 · Translate + Scan','Translate is one big button. Scan asks what you want once it sees the page.','Translate today has five tabs (type, talk, camera, phrasebook, offline packs). Scan has a dozen buttons around the camera. Both share the same 8 languages. 13.0 puts talking first, adds a face-to-face screen and lets Scan offer what fits the page after it is taken.')}
<div class="r-ph" style="justify-content:space-between">
${P(p159a,'Translate','hold to talk · works without internet')}
${P(p159b,'Face to face','phone flat on the table, each side reads its own',{nodock:1})}
${P(p159c,'Scan','find the edges, take it when sharp',{nodock:1})}
${P(p159d,'Then: what to do','choices that fit a recipe')}
${P(p159e,'A form','fills only what you give it')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1"><h3>${I('language')}Translate rules</h3><ul><li><b>8 languages</b> (English, Latvian, Polish, Russian, German, Spanish, French, Ukrainian), each marked “No internet needed” once its pack is on the phone.</li><li><b>Phrasebook</b> becomes “Saved phrases”, under More.</li><li><b>Kids</b>: talk and camera only, no online look-up.</li></ul></div>
<div class="box" style="flex:1.1"><h3>${I('scan_auto')}Scan: what it offers, by page</h3><p><b>Text page</b>: Read it to me · Translate · Save as a note · PDF · Copy. <b>Form</b> (labels ending in a colon): Fill · Sign · PDF. <b>Receipt</b>: Save to Inbox · Add the date to Planner. <b>Object</b>: “What is this?” only with a grown-up-approved source in Kids. Filter, rotate, edges: “Fix the page”.</p></div>
</div></div>`;

/* ───────── 160 · icons, motion, developer ───────── */
const ICN4=['board_blank','mind_map','moodboard','connect_cards','tidy_up','frame_slide','sheet_next','handwriting','draw_block','studio_square','postcard','layers_plain','join_shapes','cut_out','smudge','colour_pick','note_template','suggest_edit','face_to_face','hold_talk','phrasebook','offline_pack','scan_auto','form_fill','signature','what_is_this'];
const LOT4=[['sheet_next','Keep drawing','sheet 1 tucks away, sheet 2 carries on · 90 f'],['tidy_up','Tidy up','cards settle into two groups · 90 f'],['join_shapes','Join shapes','two circles become one · 90 f'],['face_to_face','Face to face','listening dots, then both sides · 90 f'],['scan_edges','Scan edges','corners find the page · 90 f']];
B13d['160']=()=>`<div class="boardpg">${head13('batch 4 · icons, motion, developer files','Everything a developer needs for the making tools','26 new icons, 5 Lottie animations in dark and light, Kotlin for drawing sheets, plain Studio and Canvas labels, note drawings, face-to-face and Scan suggestions, with 6 passing tests, one data file and 79 strings in English and Russian.')}
<div class="row" style="gap:24px">
<div style="flex:1.12" class="col">
<div class="lab">Icons · assets/halo/artwork/ic + res/drawable</div>
<div class="r-icg">${ICN4.map(n=>`<div class="r-ic">${I(n)}<span>${n}</span></div>`).join('')}</div>
<div class="lab" style="margin-top:14px">Lottie · res/raw/halo13_motion_*_{dark,light}.json</div>
${LOT4.map(([f,n,s])=>`<div class="r-strip"><b>${n}<small>${s}</small></b><img src="img/l4/prev_${f}_dark.png"></div>`).join('')}
</div>
<div style="flex:1" class="col">
<div class="lab">Files in this batch</div>
<table class="q-tbl"><tr><th>Path</th><th>What it is</th></tr>
${FILES4.map(r=>`<tr><td><code>${r[0]}</code></td><td>${r[1]}</td></tr>`).join('')}</table>
<div class="box"><h3>${I('code')}Repo ties</h3><p>Canvas wraps <code style="color:#B4E6D6">data/notebook</code> (CanvasNodeType, CanvasOrganizationProposal, CanvasSketchCodec) and the SDK’s <code style="color:#B4E6D6">InkEditor / InkLimits</code>. Studio wraps <code style="color:#B4E6D6">data/studio</code>. Translate and Scan wrap <code style="color:#B4E6D6">ui/toolkit/HaloScan*</code>. No new storage.</p></div>
</div></div>
${notes([['No new database','Sheets are SKETCH nodes in a GROUP; note drawings are REFERENCE blocks.'],['Compiled and tested','The new logic compiles against the repo’s own ink classes; 6 of 6 tests pass. Screens are not built.'],['Safe limits kept','Each sheet still obeys InkLimits; only the wall in the UI goes.'],['Next: batch 5','Study and Kids mode grow.']])}
</div>`;
Object.assign(B,B13d);
