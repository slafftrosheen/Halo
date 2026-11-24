/* halo 13.0 · batch 5 — Study + Kids (boards 161–166) */
const B13e={};
const lr5=(ic,t,sub,r='')=>`<div class="lr" style="min-height:40px">${I(ic)}<div class="gr">${t}${sub?`<small>${sub}</small>`:''}</div>${r}</div>`;
const pill5=(t,col,bg)=>`<span style="font:700 10.5px Atk;padding:3px 8px;border-radius:999px;color:${col};background:${bg};white-space:nowrap">${t}</span>`;
const kc=(e,t,s,o={})=>`<div style="display:flex;gap:10px;align-items:center;border-radius:20px;background:#fff;border:2px solid ${o.on?'#E0763F':'#F6DDB4'};padding:${o.pad||10}px 12px;margin-bottom:7px;color:#2a1d10;min-height:${o.h||56}px"><span style="font-size:${o.fs||30}px">${e}</span><div><b style="font:600 ${o.ts||15}px Sora">${t}</b>${s?`<div style="font:500 12px Atk;color:#6b5236">${s}</div>`:''}</div>${o.r||''}</div>`;
const bud=t=>`<div class="q-bud"><div class="f">🦊</div><div class="t">${t}</div></div>`;
const tbl5=rows=>`<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:3px 16px;font-size:12px;color:var(--t2)">${rows.map(([a,b,col])=>`<div style="padding:4px 0;border-bottom:1px solid var(--line)"><code style="font:500 10px ui-monospace,Menlo,monospace;color:var(--t3)">${a}</code><br><b style="color:${col||'var(--cream)'}">${b}</b></div>`).join('')}</div>`;

/* ───────── 161 · Study home and start ───────── */
const p161a=`<div class="q-top">${I('back')}<b>Study</b>${I('search')}</div>
<p class="r-big" style="font-size:19px">What do you want to learn?</p>
${[['study_photo','A page or homework','take a photo'],['file_doc','A file or my notes','PDF, slides, a note'],['study_topic','A topic','“how volcanoes work”'],['exam_date','For a test','pick the date, I’ll plan it']].map(([ic,t,s],i)=>`<div class="opt ${i==0?'on':''}" style="padding:9px 11px"><div class="oi">${I(ic)}</div><div><b>${t}</b><small>${s}</small></div></div>`).join('')}
<div class="q-comp" style="margin-top:6px">${I('mic')}Or just say it</div>`;
const p161b=`<div class="q-top">${I('sky_day')}<b>Study · today</b></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.45)"><h4>${I('card_got_it')}12 cards to look at again</h4><p>About 8 minutes. Biology and French.</p><div class="r-bt" style="height:40px;font-size:13.5px">Start</div></div>
<div class="q-blk"><h4>${I('exam_date')}Biology test · in 12 days</h4><p>Today: “The cell”, part 2 of 5.</p></div>
<div class="lab" style="margin:8px 0 4px">Keep going</div>
${lr5('study_topic','Volcanoes','Try · 3 of 6 questions')}${lr5('file_doc','French verbs','Look · from your PDF')}`;
const p161c=`<div class="q-top">${I('back')}<b>Volcanoes</b>${I('more')}</div>
<div style="display:flex;gap:4px;margin-bottom:10px">${[['path_look','Look',1],['path_try','Try',2],['path_check','Check',0],['path_done','Done',0]].map(([ic,t,s])=>`<div style="flex:1;text-align:center;padding:7px 0;border-radius:12px;background:${s==2?'rgba(12,242,186,.16)':s==1?'rgba(250,250,244,.06)':'transparent'};border:1px solid ${s==2?'rgba(12,242,186,.55)':'var(--line)'};font:600 10.5px Atk;color:${s==2?'var(--mint)':s==1?'var(--cream)':'var(--t3)'}">${I(ic,'width:17px;height:17px;display:block;margin:0 auto 2px')}${t}</div>`).join('')}</div>
<div class="q-blk"><h4>${I('path_try')}Question 3 of 6</h4><p style="color:var(--cream);font-size:13.5px">Why do some volcanoes explode while others just flow?</p></div>
<div class="q-comp" style="color:var(--cream);height:auto;padding:9px 10px;line-height:1.35">Thick lava traps the gas…</div>
<div style="display:flex;gap:5px;margin-top:8px;flex-wrap:wrap">${chip('Check my answer','m','check_try')}${chip('Help me','','remind_me')}${chip('Say it','','mic')}</div>`;
const p161d=`<div class="q-top">${I('back')}<b>Set up</b></div>
<p class="sub">Three quick choices. You can change them later.</p>
<div class="lab" style="margin:8px 0 4px">The page is in</div><div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Latvian','m')}${chip('English')}${chip('Russian')}</div>
<div class="lab" style="margin:10px 0 4px">Explain it to me in</div><div style="display:flex;gap:5px;flex-wrap:wrap">${chip('English','m')}${chip('Latvian')}</div>
<div class="lab" style="margin:10px 0 4px">How do you like to learn?</div>
${[['path_look','Show me first'],['path_try','Let me try first'],['listen_mode','Read it to me']].map(([ic,t],i)=>`<div class="opt ${i==1?'on':''}" style="padding:7px 11px;margin-bottom:4px"><div class="oi">${I(ic)}</div><div><b>${t}</b></div></div>`).join('')}
<div class="r-bt">Start</div>`;
const p161e=`<div class="q-top">${I('back')}<b>Done · Volcanoes</b></div>
<p class="r-big" style="font-size:19px">You got through all 6.</p>
<p class="r-p" style="color:var(--cream)">Two needed a hint. That is how learning goes.</p>
<div class="q-blk"><h4>${I('card_got_it')}Keep 4 cards?</h4><p>I wrote them from your answers. You can edit them first.</p><div style="display:flex;gap:5px;margin-top:6px">${chip('Keep them','m')}${chip('Look first')}</div></div>
${lr5('new_note','Save a summary to Notes','one page, your words kept')}
${lr5('calendar','Practise again on Friday','in Planner, you choose the time')}`;
B13e['161']=()=>`<div class="boardpg">${head13('batch 5 · Study','Study starts with one question: what do you want to learn?','The repo already has a strong study engine: photo homework coach, sessions from your own files, a 7-stage learning journey, a help ladder and spaced review. 13.0 gives it one plain front door, four starts, a path everyone understands (Look → Try → Check → Done) and an ending that celebrates without points.')}
<div class="r-ph" style="justify-content:space-between">
${P(p161a,'Start','four plain ways in · or say it')}
${P(p161b,'Study today','cards, tests, things in progress')}
${P(p161c,'The path','Look · Try · Check · Done')}
${P(p161d,'Set up','page language, my language, how I like to learn')}
${P(p161e,'Done','no points, no score · keep cards')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.2"><h3>${I('plain_words')}One path over two engines</h3>${tbl5([['START · DISCOVER','Look'],['EXPLAIN · EXPLORE','Look'],['PRACTICE','Try'],['REFLECT','Check'],['CONTINUE','Done'],['StudyLearningPhase EXPLORE · TRY · REVIEW · COMPLETE','Look · Try · Check · Done']])}</div>
<div class="box" style="flex:1"><h3>${I('level_pro')}By depth</h3><p><b>Simple</b>: the four starts and the path; read aloud on. <b>Standard</b>: set-up choices, cards, test plans. <b>Pro</b>: course outline, sources and citations, your own review steps, export.</p><p style="margin-top:6px">No points, scores or streaks anywhere. The repo stores <code style="color:#B4E6D6">knownStreak</code> for scheduling only; it is never shown.</p></div>
</div></div>`;

/* ───────── 162 · help and checking ───────── */
const p162a=`<div class="q-top">${I('back')}<b>Help me</b></div>
<p class="sub">Take as much help as you need. Nothing is locked.</p>
${[['remind_me','Remind me','what do I already know?','ASK'],['tip','A hint','a small push','HINT'],['explain_it','Explain it','in simple words','EXPLAIN'],['another_way','Another way','an example from life','ANALOGY'],['check_try','Check my try','where am I stuck?','CHECK_ATTEMPT'],['one_step','Show one step','then I go on','PARTIAL_SOLUTION']].map(([ic,t,s,k],i)=>`<div class="opt" style="padding:6px 10px;margin-bottom:4px"><div class="oi">${I(ic)}</div><div><b>${t}</b><small>${s}</small></div></div>`).join('')}
<div class="lr" style="min-height:36px;border:0">${I('show_all')}<div class="gr" style="color:var(--t2)">Show it all<small>asks once: “You can still try first.”</small></div></div>`;
const p162b=`<div class="q-top">${I('back')}<b>Check</b></div>
${[['Checks out','var(--mint)','rgba(12,242,186,.14)','Your answer matches page 41.','CHECKS_OUT'],['One step needs a fix','var(--ap)','rgba(255,159,107,.14)','Step 2: 3 × 4 is 12, not 7.','NEEDS_WORK'],['I can’t tell from your page','var(--t2)','rgba(250,250,244,.07)','The page doesn’t say. Ask your teacher?','INSUFFICIENT_EVIDENCE'],['halo’s guess, not checked','var(--lil)','rgba(201,184,255,.14)','Looks right, but no source backs it.','UNVERIFIED']].map(([t,c,bg,s])=>`<div style="border-radius:14px;padding:9px 11px;margin-bottom:6px;background:${bg};border:1px solid ${c}"><b style="font:600 13px Sora;color:${c}">${t}</b><p class="r-p" style="font-size:12px;margin-top:2px">${s}</p></div>`).join('')}
<p class="sub">Never red. Red is only for a broken source.</p>`;
const p162c=`<div class="q-top">${I('back')}<b>Homework · page 41</b></div>
<div style="height:120px;border-radius:12px;background:#fff;color:#14102a;padding:10px;font:500 13px/1.6 Atk;margin-bottom:8px">3. 48 ÷ 4 = <span style="border-bottom:2px solid #0AA983">12</span><br>4. 7 × 6 = <span style="background:rgba(255,159,107,.3);border-radius:3px;padding:0 3px">36</span><br>5. 125 − 38 = <span style="color:#999">…</span></div>
<div class="q-blk" style="border-color:rgba(255,159,107,.5)"><h4 style="color:var(--ap)">${I('check_try')}Number 4: look again</h4><p>Try counting 7 six times. Want a hint?</p></div>
<div style="display:flex;gap:5px;flex-wrap:wrap">${chip('A hint','','tip')}${chip('I fixed it','m')}${chip('Next one')}</div>
<p class="sub" style="margin-top:8px">The photo stays on this phone.</p>`;
const p162d=`<div class="q-top">${I('back')}<b>Explain it back</b></div>
<p class="sub">Say it like you’d tell a friend. I listen and give you two tips.</p>
<div style="display:flex;justify-content:center;margin:12px 0"><span style="width:76px;height:76px;border-radius:50%;background:var(--mint);color:#04211a;display:grid;place-items:center">${I('explain_back','width:32px;height:32px')}</span></div>
<div class="q-blk"><h4>${I('quote')}You said</h4><p style="color:var(--cream)">“Thick lava traps the gas, so it builds up and bursts.”</p></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.4)"><h4>${I('tip')}Two tips</h4><p>Great link between thick lava and gas. Add <b>why</b> runny lava lets gas out.</p></div>
<p class="sub">The recording is deleted after the tips. Keep it?</p>`;
const p162e=`<div class="q-top">${I('back')}<b>Where it came from</b></div>
<div class="q-blk"><h4>${I('file_doc')}Biology book, page 41</h4><p style="color:var(--cream)">“The nucleus holds the cell’s DNA …”</p><div style="display:flex;gap:5px;margin-top:6px">${chip('Open the page','m')}${chip('Not this page')}</div></div>
<div class="q-blk"><h4>${I('src_science')}Your class slides · slide 7</h4><p>Matches what the book says.</p></div>
<p class="sub">Every explanation shows where it came from. If I used no source, I say so.</p>`;
B13e['162']=()=>`<div class="boardpg">${head13('batch 5 · Study · help and checking','Help is a ladder you climb as far as you want. Checks say where, never how badly.','The repo’s help follows TutorStage (ask, hint, explain, analogy, check attempt, part of the solution, solution) and its rule is already right: “neither points nor elapsed time gate any rung”. 13.0 shows all of it as one plain list and puts the four StudyVerdict results into kind words and meaning colours.')}
<div class="r-ph" style="justify-content:space-between">
${P(p162a,'Help me','seven rungs, nothing locked')}
${P(p162b,'Check','four results · meaning colours, no red')}
${P(p162c,'Homework photo','marks the one to look at again')}
${P(p162d,'Explain it back','spoken · two tips · deleted after')}
${P(p162e,'Where it came from','every answer shows its page')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.2"><h3>${I('plain_words')}TutorStage → plain help</h3>${tbl5([['ASK','Remind me'],['HINT','A hint'],['EXPLAIN','Explain it'],['ANALOGY','Another way'],['CHECK_ATTEMPT','Check my try'],['PARTIAL_SOLUTION · SOLUTION','Show one step · Show it all']])}</div>
<div class="box" style="flex:1"><h3>${I('path_check')}StudyVerdict → plain result</h3>${tbl5([['CHECKS_OUT','Checks out','var(--mint)'],['NEEDS_WORK','One step needs a fix','var(--ap)'],['INSUFFICIENT_EVIDENCE','I can’t tell from your page','var(--t2)'],['UNVERIFIED','halo’s guess, not checked','var(--lil)']])}</div>
</div></div>`;

/* ───────── 163 · cards and test plans ───────── */
const p163a=`<div class="q-top">${I('back')}<b>Look again · 3 of 12</b></div>
<div style="height:200px;border-radius:20px;background:rgba(201,184,255,.12);border:1px solid rgba(201,184,255,.4);display:grid;place-items:center;text-align:center;padding:16px"><div><small style="color:var(--t3);font:600 11px Atk">FRENCH</small><p style="font:600 21px/1.3 Sora;margin-top:6px">nous allons</p><p class="r-p" style="margin-top:10px;color:var(--mint)">we go · we are going</p></div></div>
<p class="sub" style="text-align:center;margin:10px 0 6px">How was that?</p>
<div style="display:flex;gap:6px">${[['card_again','Again','in 2 days'],['card_almost','Almost','in 4 days'],['card_got_it','Got it','in 7 days']].map(([ic,t,s],i)=>`<div style="flex:1;text-align:center;padding:8px 0;border-radius:14px;background:${i==2?'rgba(12,242,186,.15)':'rgba(250,250,244,.06)'};border:1px solid ${i==2?'rgba(12,242,186,.5)':'var(--line)'}">${I(ic,'width:20px;height:20px;display:block;margin:0 auto 3px')}<b style="font:600 12px Sora">${t}</b><small style="display:block;font-size:10px;color:var(--t3)">${s}</small></div>`).join('')}</div>`;
const p163b=`<div class="q-top">${I('back')}<b>That’s today’s cards</b></div>
<p class="r-big" style="font-size:19px">12 cards, about 8 minutes.</p>
<div class="q-blk"><h4>${I('calendar')}Coming back</h4><p>Tomorrow 3 · Thursday 5 · next week 4</p></div>
<div class="q-blk"><h4>${I('tip')}You’re keeping these well</h4><p>“nous allons” moves to next week.</p></div>
<p class="sub">No streaks to keep, no points to lose. Miss a day and the cards just wait.</p>`;
const p163c=`<div class="q-top">${I('back')}<b>Biology test</b>${I('more')}</div>
<div style="display:flex;gap:5px;margin-bottom:8px">${chip('Thu 4 Dec','a','exam_date')}${chip('12 days')}</div>
<div style="display:grid;grid-template-columns:repeat(6,1fr);gap:4px;margin-bottom:10px">${['M','T','W','T','F','S','S','M','T','W','T','F'].map((d,i)=>`<div style="text-align:center;padding:5px 0;border-radius:8px;font:700 10px Atk;background:${i==11?'rgba(255,159,107,.25)':i<1?'rgba(12,242,186,.25)':[5,6].includes(i)?'rgba(250,250,244,.03)':'rgba(250,250,244,.08)'};color:${i==11?'var(--ap)':'var(--cream)'}">${d}<br><span style="font-weight:500;color:var(--t3)">${i==11?'test':[5,6].includes(i)?'rest':'20m'}</span></div>`).join('')}</div>
${lr5('path_check','Today · The cell, part 2','20 min · 6 cards')}${lr5('path_look','The day before','light: a quick look, no new topics')}
<div style="display:flex;gap:5px;margin-top:6px">${chip('Fewer days','','calendar')}${chip('Put it in Planner','m')}</div>`;
const p163d=`<div class="q-top">${I('back')}<b>Practise out loud</b></div>
<p class="sub">For a talk or an oral test. I play the listener.</p>
<div class="q-blk"><h4>${I('rehearse')}“My science project”</h4><p>3 minutes · you’re at 1:40</p><div style="height:6px;border-radius:3px;background:rgba(250,250,244,.1);margin-top:6px"><div style="width:55%;height:100%;border-radius:3px;background:var(--mint)"></div></div></div>
<div class="q-blk"><h4>${I('tip')}After you finish</h4><p>Pace, the parts you skipped, one question a listener might ask.</p></div>
<div class="q-why" style="position:relative;left:0;right:0"><b>Recording stays on this phone.</b>It is deleted when you close this, unless you keep it.</div>`;
const p163e=`<div class="q-top">${I('back')}<b>Listen</b></div>
<div class="q-blk"><h4>${I('listen_mode')}The cell · part 2</h4><p style="color:var(--cream)"><span style="background:rgba(12,242,186,.22);border-radius:3px">The nucleus holds the DNA.</span> Around it, the cytoplasm …</p></div>
<div style="display:flex;justify-content:center;gap:14px;margin:12px 0">${['undo','pause','redo'].map((ic,i)=>`<span style="width:${i==1?56:44}px;height:${i==1?56:44}px;border-radius:50%;display:grid;place-items:center;background:${i==1?'var(--mint)':'rgba(250,250,244,.08)'};color:${i==1?'#04211a':'var(--cream)'}">${I(ic)}</span>`).join('')}</div>
<div style="display:flex;gap:5px;flex-wrap:wrap">${chip('Ask a question')}${chip('Slower')}${chip('Headphones only','','headphones')}</div>`;
B13e['163']=()=>`<div class="boardpg">${head13('batch 5 · Study · cards and tests','Cards come back when you’re about to forget. A test gets a calm plan.','Spaced review already schedules cards at 1, 2, 4, 7, 15 and 30 days with three ratings (AGAIN, ALMOST, KNOWN). 13.0 names them Again, Almost and Got it, tells you when each card comes back, and turns a test date into a day-by-day plan with rest days and a light last day.')}
<div class="r-ph" style="justify-content:space-between">
${P(p163a,'Cards','Again · Almost · Got it · when it comes back')}
${P(p163b,'Done for today','no streaks · missed days just wait')}
${P(p163c,'Test plan','day by day · rest days · light last day')}
${P(p163d,'Practise out loud','spoken rehearsal · deleted by default')}
${P(p163e,'Listen','read aloud, follows along')}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="code" style="flex:1"><span class="c">// ui/study/v13/ReviewPlain.kt · steps 1 2 4 7 15 30</span>
ReviewPlain.comesBack(rating = <span class="s">ALMOST</span>, intervalIndex = <span class="n">2</span>)
<span class="c">// → "in 4 days" (ALMOST holds the rung)</span>
ExamPlan.make(cards = <span class="n">48</span>, today, exam = today + <span class="n">11</span>.days,
             restDays = setOf(SAT, SUN))
<span class="c">// → 8 study days of 6 cards, rest at weekends, last day light</span></div>
<div class="box" style="flex:1"><h3>${I('rehearse')}Privacy</h3><ul><li><b>Spoken rehearsal and Explain it back</b> are recorded on the phone and deleted when the sheet closes, unless you tap Keep.</li><li><b>Homework photos</b> stay on the phone (noBackupFilesDir), as today.</li><li>A privacy review is still needed before launch.</li></ul></div>
</div></div>`;

/* ───────── 164 · Kids home ───────── */
const p164a=`${bud('Hi Leo! What shall we do?')}
${kc('❓','Ask Fox','“why is the sky blue?”',{on:1,h:72,fs:34})}${kc('🌱','Learn','today: animals',{h:72,fs:34})}${kc('🎨','Make','draw, stories, stickers',{h:72,fs:34})}
<div class="q-kbtn">${I('mic')}Talk to Fox</div>`;
const p164b=`${bud('Today’s adventure: dinosaurs!')}
<div style="position:relative;height:300px;border-radius:22px;background:#fff;border:2px solid #F6DDB4;margin-top:4px;overflow:hidden">
<svg width="232" height="300" style="position:absolute;left:0;top:0"><path d="M40 250 C80 220 60 170 110 160 S170 120 150 80 S120 40 190 40" fill="none" stroke="#F6C58F" stroke-width="6" stroke-dasharray="2 12" stroke-linecap="round"/></svg>
${[['📖','Learn',40,250,1],['🖍️','Make',110,160,1],['🔎','Find',150,80,0],['💬','Tell',190,40,0]].map(([e,t,x,y,d])=>`<div style="position:absolute;left:${x-26}px;top:${y-26}px;width:52px;height:52px;border-radius:50%;background:${d?'#B4F5E4':'#FFF7E6'};border:2px solid ${d?'#0AA983':'#F6C58F'};display:grid;place-items:center;font-size:24px">${e}</div><span style="position:absolute;left:${x+30}px;top:${y-8}px;font:700 12px Atk;color:#2a1d10">${t}</span>`).join('')}
</div>
<p style="font:500 12px Atk;color:#6b5236;margin-top:8px;text-align:center">Two done. No rush, no points.</p>`;
const p164c=`${bud('Which one is a plant-eater?')}
<div style="display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:6px">${[['🦕','Long neck',1],['🦖','Big teeth',0]].map(([e,t,on])=>`<div style="height:150px;border-radius:24px;background:#fff;border:3px solid ${on?'#0AA983':'#F6DDB4'};display:grid;place-items:center;text-align:center;color:#2a1d10"><div><div style="font-size:56px">${e}</div><b style="font:600 15px Sora">${t}</b></div></div>`).join('')}</div>
<div style="margin-top:10px;border-radius:18px;background:#E6FBF4;border:2px solid #0AA983;padding:10px 12px;color:#04211a;font:600 14px/1.35 Sora">Yes! Long necks reach the leaves high up. 🌿</div>
<p style="font:500 12px Atk;color:#6b5236;margin-top:8px;text-align:center">Ages 4–6: two big choices, read aloud</p>`;
const p164d=`${bud('Let’s make a story together.')}
${kc('🐉','A dragon who is scared of the dark','you picked this',{on:1})}
<div style="border-radius:20px;background:#fff;border:2px solid #F6DDB4;padding:10px 12px;color:#2a1d10;font:500 13.5px/1.5 Atk;margin-bottom:8px">Pip the dragon lived in a cave. Every night he lit a tiny flame, just to see. <b style="color:#E0763F">What happens next?</b></div>
<div style="display:flex;gap:6px">${['🌟 A star visits','🦉 An owl helps'].map(t=>`<span style="flex:1;text-align:center;padding:12px 4px;border-radius:16px;background:#fff;border:2px solid #F6C58F;font:600 12.5px Atk;color:#2a1d10">${t}</span>`).join('')}</div>
<div class="q-kbtn">${I('mic')}Or say your idea</div>`;
const p164e=`${bud('Pick your sky!')}
<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:7px;margin-top:4px">${[['⭐','Stars','#1b1760'],['🌊','Sea','#2B6CB0'],['🌷','Garden','#7FB77E'],['🌙','Moon','#3a2f7a'],['✨','Glow garden','#0AA983'],['🍂','Seasons','#E0763F']].map(([e,t,c],i)=>`<div style="height:84px;border-radius:18px;background:${c};border:3px solid ${i==1?'#FFD27A':'transparent'};display:grid;place-items:center;text-align:center;color:#fff"><div><div style="font-size:26px">${e}</div><b style="font:600 11px Atk">${t}</b></div></div>`).join('')}</div>
<div style="margin-top:10px">${kc('🦊','Fox','your buddy · tap to hear',{h:52,fs:26})}</div>`;
B13e['164']=()=>`<div class="boardpg">${head13('batch 5 · Kids mode · home and learning','A child’s halo: three big doors, an adventure a day, and a buddy who reads everything aloud','Kids mode already has age bands (4–6, 7–9, 10–12), four daily adventure stops (learn, make, find, tell), card topics, six skies, stories and a fox buddy. 13.0 sizes everything by age, keeps the home to three doors, and stays free of points, scores and streaks.')}
<div class="r-ph" style="justify-content:space-between">
${P(p164a,'Home','three doors · talk to Fox',{cls:'kid'})}
${P(p164b,'Today’s adventure','stops by age · no points',{cls:'kid'})}
${P(p164c,'Learn · ages 4–6','two big choices, read aloud',{cls:'kid'})}
${P(p164d,'Make a story','child picks, Fox helps',{cls:'kid'})}
${P(p164e,'My sky','six skies · buddy',{cls:'kid'})}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="box" style="flex:1.2"><h3>${I('kids_mode')}Sized by age · KidsAgeBand</h3>${tbl5([['EARLY 4–6','72 dp buttons · 2 choices · 2 stops · always read aloud'],['PRIMARY 7–9','56 dp · 3 choices · 3 stops · read aloud on tap'],['OLDER 10–12','48 dp · 3 choices · 4 stops · reads text, Fox on tap'],['Home','Ask · Learn · Make (KidsDestination)'],['Adventure','LEARN · MAKE · FIND · TELL'],['Topics','animals · counting · nature · dinosaurs']])}</div>
<div class="box" style="flex:1"><h3>${I('kid_safe')}Always</h3><ul><li><b>No points, scores, streaks or timers that punish.</b></li><li><b>Always Simple depth.</b> No settings without a grown-up.</li><li><b>Wrong answers</b> get a gentle “look again” and a hint, never a buzzer.</li><li><b>Online look-up</b> only from grown-up-approved kids-safe sources.</li></ul></div>
</div></div>`;

/* ───────── 165 · Kids homework, focus, grown-ups ───────── */
const p165a=`${bud('Homework time! Show me your page.')}
<div style="height:150px;border-radius:20px;background:#fff;border:2px solid #F6DDB4;padding:12px;color:#2a1d10;font:600 15px/1.7 Atk">7 + 5 = <span style="border-bottom:3px solid #0AA983">12</span><br>9 + 6 = <span style="background:#FFE3D3;border-radius:6px;padding:0 4px">14</span><br>8 + 8 = …</div>
<div style="margin-top:9px;border-radius:18px;background:#FFF0E6;border:2px solid #E0763F;padding:10px 12px;color:#2a1d10;font:600 13.5px/1.4 Sora">Look again at 9 + 6. Try counting up from 9. 🖐️</div>
<p style="font:500 11.5px Atk;color:#6b5236;margin-top:7px;text-align:center">Fox gives hints, not answers. Open 16:00–18:00.</p>`;
const p165b=`${bud('How long shall we focus?')}
<div style="display:flex;gap:8px;margin:6px 0 12px">${[5,10,25].map((m,i)=>`<div style="flex:1;height:76px;border-radius:20px;background:#fff;border:3px solid ${i==1?'#0AA983':'#F6DDB4'};display:grid;place-items:center;color:#2a1d10;text-align:center"><div><b style="font:700 24px Sora">${m}</b><div style="font:600 11px Atk">minutes</div></div></div>`).join('')}</div>
<div style="height:150px;display:grid;place-items:center"><div style="width:140px;height:140px;border-radius:50%;background:conic-gradient(#0AA983 0 62%,#F6DDB4 0);display:grid;place-items:center"><div style="width:112px;height:112px;border-radius:50%;background:#FFF7E6;display:grid;place-items:center;font:700 26px Sora;color:#2a1d10">6:12</div></div></div>
<p style="font:500 12px Atk;color:#6b5236;text-align:center">When it ends, a soft chime. Stopping early is fine.</p>`;
const p165c=`<div class="q-top">${I('back')}<b>Grown-up space</b></div>
<div class="q-blk" style="border-color:rgba(12,242,186,.4)"><h4>${I('kid_safe')}Unlocked with your phone’s PIN</h4><p>Locks again when you leave or after 2 minutes.</p></div>
${lr5('grown_up_week','Leo’s week','what he explored and made')}${lr5('homework_window','Homework time','weekdays 16:00–18:00')}${lr5('src_book','Kids-safe sources','3 approved')}${lr5('focus_timer','Time in halo','up to 45 min a day')}${lr5('settings','Age and reading','7–9 · read aloud on tap')}
<div class="r-bt">${I('hand_back')}Hand back to Leo</div>`;
const p165d=`<div class="q-top">${I('back')}<b>Leo’s week</b></div>
<p class="sub">What he explored. No comparisons.</p>
${[['🦕','Dinosaurs','3 adventures · asked “why did they die out?”'],['➕','Adding to 20','homework 4 days · needed hints on +6 and +7'],['🐉','Pip the dragon','a story, 6 pages · he drew 2'],['🌊','Sea sky','his new home screen']].map(([e,t,s])=>`<div class="lr" style="min-height:46px"><span style="font-size:22px;width:24px;text-align:center">${e}</span><div class="gr">${t}<small>${s}</small></div></div>`).join('')}
<div class="q-blk" style="margin-top:8px"><h4>${I('tip')}Something to try together</h4><p>He likes dinosaurs and needs +6. “How many legs on 3 raptors?”</p></div>`;
const p165e=`${bud('A grown-up is checking something. Back in a moment!')}
<div style="height:220px;display:grid;place-items:center"><div style="text-align:center"><div style="font-size:76px">🦊</div><p style="font:600 15px Sora;color:#2a1d10;margin-top:6px">Leo’s halo is safe</p><p style="font:500 12px Atk;color:#6b5236;margin-top:4px">Grown-up settings are locked again.</p></div></div>
${kc('👋','I’m back!','tap to carry on',{on:1,h:64})}`;
B13e['165']=()=>`<div class="boardpg">${head13('batch 5 · Kids mode · homework, focus and grown-ups','Homework with hints, a focus timer that never scolds, and a grown-up space behind the phone’s own lock','The repo already has a homework window (16:00–18:00 by default), a photo homework check, a focus timer (5, 10 or 25 minutes) and a grown-up gate that uses the device’s screen lock. 13.0 makes the child’s side warmer and gives the grown-up a weekly picture of what was explored, never a score.')}
<div class="r-ph" style="justify-content:space-between">
${P(p165a,'Homework','hints, not answers · only in the window',{cls:'kid'})}
${P(p165b,'Focus','5 · 10 · 25 min · no scolding',{cls:'kid'})}
${P(p165c,'Grown-up space','phone PIN · locks after 2 min')}
${P(p165d,'Leo’s week','what he explored · something to try',{})}
${P(p165e,'Handed back','child sees a calm waiting screen',{cls:'kid'})}
</div>
<div class="row" style="margin-top:20px;gap:22px">
<div class="code" style="flex:1"><span class="c">// ui/kids/v13/KidsWeek.kt · words a summary may never use</span>
KidsWeek.summary(events, child = <span class="s">"Leo"</span>)
<span class="c">// topics, things made, where hints helped</span>
KidsWeek.FORBIDDEN <span class="c">// score, points, streak, rank, behind, failed, wrong …</span>
KidsWeek.check(text) <span class="c">// false if any forbidden word appears (EN + RU)</span></div>
<div class="box" style="flex:1"><h3>${I('kid_safe')}Rules</h3><ul><li><b>Grown-up space</b> uses the phone’s screen lock, as today (KidsGrownUpGate). Locks after 2 minutes idle.</li><li><b>Homework help</b> gives hints and “look again”, never the answer, and only inside the homework window.</li><li><b>Leo’s week</b> stays on the phone. Nothing is sent anywhere.</li></ul></div>
</div></div>`;

/* ───────── 166 · icons, motion, files ───────── */
const ICN5=Object.keys(window.IC).filter(k=>['study_photo','study_topic','exam_date','path_look','path_try','path_check','path_done','remind_me','explain_it','another_way','check_try','one_step','show_all','explain_back','card_again','card_almost','card_got_it','rehearse','listen_mode','kids_ask','kids_make','kids_find','kids_tell','homework_window','focus_timer','grown_up_week','hand_back'].includes(k));
const LOT5=[['help_ladder','Help ladder','rungs light up as you climb · 90 f'],['card_flip','Review card','flips, then waits for its day · 90 f'],['exam_plan','Test plan','days fill up to the test · 90 f'],['kids_adventure','Kids adventure','buddy hops stop to stop · 90 f'],['hand_back','Hand back','phone passes over, grown-up part locks · 90 f']];
B13e['166']=()=>`<div class="boardpg">${head13('batch 5 · icons, motion, developer files','Everything a developer needs for Study and Kids mode',`${ICN5.length} new icons, 5 Lottie animations in dark and light, Kotlin for the plain help ladder, results, review timing, test plans, age sizing and the grown-up week, with passing tests, one data file and strings in English and Russian.`)}
<div class="row" style="gap:24px">
<div style="flex:1.12" class="col">
<div class="lab">Icons · assets/halo/artwork/ic + res/drawable</div>
<div class="r-icg">${ICN5.map(n=>`<div class="r-ic">${I(n)}<span>${n}</span></div>`).join('')}</div>
<div class="lab" style="margin-top:14px">Lottie · res/raw/halo13_motion_*_{dark,light}.json</div>
${LOT5.map(([f,n,s])=>`<div class="r-strip"><b>${n}<small>${s}</small></b><img src="img/l5/prev_${f}_dark.png"></div>`).join('')}
</div>
<div style="flex:1" class="col">
<div class="lab">Files in this batch</div>
<table class="q-tbl"><tr><th>Path</th><th>What it is</th></tr>
${FILES5.map(r=>`<tr><td><code>${r[0]}</code></td><td>${r[1]}</td></tr>`).join('')}</table>
<div class="box"><h3>${I('code')}Repo ties</h3><p>Study wraps <code style="color:#B4E6D6">TutorStage</code>, <code style="color:#B4E6D6">StudyVerdict</code>, <code style="color:#B4E6D6">StudyReviewPolicy</code> and <code style="color:#B4E6D6">LearningStage</code>. Kids wraps <code style="color:#B4E6D6">KidsAgeBand</code>, <code style="color:#B4E6D6">KidsHomeworkWindow</code> and <code style="color:#B4E6D6">KidsGrownUpGate</code>. No new storage.</p></div>
</div></div>
${notes([['No new database','Everything maps onto study sessions, practice items and kids preferences.'],['Compiled and tested','The new logic compiles in a plain Kotlin project; all tests pass. Screens are not built.'],['No points anywhere','A test fails if a kids or study summary uses score, points, streak or rank words.'],['Next: batch 6','The 13.0 book, Russian boards, prototype, films and the full pack.']])}
</div>`;
Object.assign(B,B13e);
