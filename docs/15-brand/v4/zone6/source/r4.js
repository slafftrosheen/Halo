/* Rollout 4 — Learn: one path, three sizes. Grounded in repo @98aa2fb:
   StudyLearningPhase EXPLORE/TRY/REVIEW/COMPLETE · LearningHelpLadder Hint / Help me think / Full solution (confirm)
   StudyExplanationDepth SIMPLER/BALANCED/DEEPER · review ratings Again / Almost / I knew it · drafts → Keep card
   KidsDestination HOME/ASK/LEARN/CREATE · Kids create: My idea → More about it → Let’s make it / Write it myself → Read my story
   KidsSpokenChoice “first tap reads, tap again to choose” · KidsGrownUpGate = phone screen lock */
const S4={};
const pr=(f,l,c='#0CF2BA',bg='rgba(250,250,244,.12)')=>{const C=2*Math.PI*20,gap=C*0.125,len=(C-gap)*f;return `<div class="pring"><svg viewBox="0 0 54 54"><g transform="rotate(-67.5 27 27)"><circle cx="27" cy="27" r="20" fill="none" stroke="${bg}" stroke-width="6" stroke-linecap="round" stroke-dasharray="${C-gap} ${gap}"/><circle cx="27" cy="27" r="20" fill="none" stroke="${c}" stroke-width="6" stroke-linecap="round" stroke-dasharray="${Math.max(len,0.01)} ${C}"/></g></svg><b>${l}</b></div>`};
const st5=(cur,names=['Choose','Look','Try','Help','Keep'])=>`<div class="steps5">${names.map((n,i)=>`<div class="${i<cur?'d':i==cur?'c':''}"><i></i>${n}</div>`).join('')}</div>`;
const lh=(t,s,f,l)=>`<div class="lhead">${pr(f,l)}<div class="t"><b>${t}</b><span>${s}</span></div></div>`;

/* ---------- Study ---------- */
S4.start=`<div class="ttl" style="margin-top:14px">What shall we learn?</div><div class="sub">Pick a way in. You can switch any time.</div>
 <div class="door hi"><div class="di2" style="background:rgba(12,242,186,.14);color:var(--mint)">${I('practise')}</div><div><b>Keep practising</b><span>Fractions · 6 cards due today</span></div><span class="due">6</span></div>
 <div class="door"><div class="di2" style="background:rgba(201,184,255,.16);color:#C9B8FF">${I('guide_me')}</div><div><b>Learn a topic</b><span>Say or type anything — “how do volcanoes work?”</span></div></div>
 <div class="door"><div class="di2" style="background:rgba(255,159,107,.16);color:var(--ap)">${I('camera')}</div><div><b>My homework or notes</b><span>Photo, PDF or file — halo checks what it says first</span></div></div>
 <div class="wsec">CONTINUE</div><div class="grp">${row('study','Photosynthesis','Try · step 3 of 5 · yesterday')}</div>`;
S4.setup=`<div class="crumb">${I('study')}New topic · Fractions</div><div class="ttl" style="font-size:22px">How should halo explain?</div><div class="sub">Pick the one that sounds right. Change it any time.</div>
 <div class="prev"><div class="ph"><span class="radio"></span>SIMPLER</div><p>A fraction is a part of something. ½ means one of two equal pieces.</p></div>
 <div class="prev on"><div class="ph"><span class="radio on"></span>BALANCED</div><p>The bottom number says how many equal parts; the top says how many you have.</p></div>
 <div class="prev"><div class="ph"><span class="radio"></span>DEEPER</div><p>A fraction a⁄b is a ÷ b — a point on the number line between whole numbers.</p></div>
 <div class="fine">This changes future explanations, not your level or results.</div>`;
S4.photo=`<div class="crumb">${I('camera')}Homework · page 1</div><div class="ttl" style="font-size:22px">Is this what it says?</div>
 <div class="photo"><span class="q">Ex. 4</span>1. ¾ + ⅛ = ?<br><span class="ex on">2. Which is bigger: ⅔ or ⁵⁄₈ ?</span><br>3. Write 0.75 as a fraction.<br>4. Share 12 sweets in a ratio of 1 : 3.</div>
 <div class="word"><small>HALO READ EXERCISE 2 AS</small>Which is bigger: <b>2/3</b> or <b>5/8</b>?</div>
 <div class="fine">halo may misread symbols — fix the words before you start.</div>`;
S4.path=lh('Which is bigger: ⅔ or ⅝?','Homework · exercise 2','.4','2/5')+`<div class="path">
 <div class="pst d"><div class="dot">${I('check')}</div><b>Choose</b><span>Exercise 2 from your photo</span></div>
 <div class="pst d"><div class="dot">${I('check')}</div><b>Look</b><span>Same-size pieces make fractions easy to compare</span></div>
 <div class="pst c"><div class="dot"></div><b>Try</b><span>Your answer, in your words</span><div class="tag">YOU ARE HERE</div></div>
 <div class="pst"><div class="dot"></div><b>Help</b><span>Only if you want it · 3 levels</span></div>
 <div class="pst"><div class="dot"></div><b>Keep</b><span>Save what you learned as practice</span></div></div>`;
S4.try=lh('Which is bigger: ⅔ or ⅝?','Try · your answer','.5','3/5')+`<div class="lq" style="margin-top:14px;font-size:19px">Make the pieces the same size, then compare.</div>
 <div class="ans">I think <b>⅔</b> is bigger because ⅔ = 16⁄24 and ⅝ = 15⁄24<span class="cur"></span></div>
 <div class="because"><span>I think the answer is…</span><span>because…</span><span>${'I’m not sure'}</span></div>
 <div class="fine">Type, talk (hold the ring) or snap your working. Wrong first tries are part of learning.</div>`;
S4.help=lh('Which is bigger: ⅔ or ⅝?','Help · you choose how much','.6','3/5')+`<div class="ladder">
 <div class="rung used"><div class="n">1</div><div><b>Hint</b><span>Find a number both 3 and 8 go into.</span></div></div>
 <div class="rung on"><div class="n">2</div><div><b>Help me think</b><span>3 × 8 = 24. How many 24ths is ⅔? Multiply top and bottom by 8…</span></div></div>
 <div class="rung lock"><div class="n">3</div><div><b>Full solution</b><span>Every step, worked out.</span></div><em>asks first</em></div></div>
 <div class="fine">Help is always there. Using it never lowers your level.</div>`;
S4.almost=lh('Which is bigger: ⅔ or ⅝?','Try again · nearly there','.6','3/5')+`<div class="fb almost"><div class="fh">${I('guide_me')}ALMOST</div><p>Your method is right. One number slipped: ⅝ is <b>15</b>⁄24, not 14⁄24.</p></div>
 <div class="mine"><small>YOUR WORKING</small>⅔ = <span class="ok2">16⁄24</span> · ⅝ = <span class="fix">14⁄24</span> → ⅔ is bigger</div>
 <div class="fine">Halo shows <b>what</b> slipped and keeps what was right. No red crosses.</div>`;
S4.right=lh('Which is bigger: ⅔ or ⅝?','Done · 1 hint used','1','✓')+`<div class="fb right"><div class="fh">${I('check')}YOU GOT IT</div><p>⅔ = 16⁄24 is bigger than ⅝ = 15⁄24. You found the common size yourself.</p></div>
 <div class="wsec">KEEP WHAT YOU LEARNED?</div><div class="grp drafts">
 <div class="dc"><div><b>Compare by making same-size pieces</b><span>Draft card</span></div><div class="k"><i class="on">Keep</i><i>Skip</i></div></div>
 <div class="dc"><div><b>3 and 8 → 24ths</b><span>Draft card</span></div><div class="k"><i>Keep</i><i>Skip</i></div></div></div>
 <div class="fine">Nothing goes into practice until you tap Keep.</div>`;
S4.practice=`<div class="crumb">${I('practise')}Practice · Fractions</div>${st5(2,['1','2','3','4','5','6'].slice(0,5))}
 <div class="card3"><small>CARD 3 OF 6</small><b>Which is bigger: ¾ or ⅗?</b><p>¾ = 15⁄20 · ⅗ = 12⁄20 → ¾</p></div>
 <div class="lq" style="font-size:17px;margin:14px 0 0">How well did you remember?</div>
 <div class="rate"><span>Again<small>soon</small></span><span>Almost<small>tomorrow</small></span><span class="on">I knew it<small>in 4 days</small></span></div>`;
S4.listen=`<div class="crumb">${I('read_mode')}Look · read aloud</div>${st5(1)}<div class="kara"><span class="r">A fraction tells you</span> <span class="now">how many equal parts</span> you have out of the whole.</div>
 <div class="fine" style="margin-top:14px">Words light up as halo reads. Tap any sentence to hear it again; turn on “continue automatically” for hands-free.</div>`;

/* ---------- three sizes of the same step ---------- */
S4.size1=`<div class="kq" style="margin-top:10px">Which is bigger?</div>
 <div class="picopt" style="grid-template-columns:1fr 1fr"><div class="hear">⅔<small>tap to hear</small></div><div>⅝<small>tap to hear</small></div></div>
 <div style="display:flex;gap:10px;margin-top:12px"><div style="flex:1;height:58px;border-radius:14px;background:linear-gradient(90deg,#0CF2BA 66%,#fff 66%);border:2px solid rgba(22,14,28,.1)"></div><div style="flex:1;height:58px;border-radius:14px;background:linear-gradient(90deg,#FF9F6B 62.5%,#fff 62.5%);border:2px solid rgba(22,14,28,.1)"></div></div>
 <div style="font:600 16px Atk;color:#5B4A60;margin-top:10px;text-align:center">See the bars? Tap one to answer.</div>`;
S4.size2=lh('Which is bigger?','Try · step 3 of 5','.5','3/5')+`<div class="lq" style="margin-top:14px;font-size:21px">Which is bigger: ⅔ or ⅝?</div>
 ${['⅔','⅝','They’re the same'].map((x,i)=>`<div class="opt ${i==0?'sel':''}">${x}</div>`).join('')}<div class="fine">Tip: make the pieces the same size.</div>`;
S4.size3=S4.try;

/* ---------- Kids ---------- */
S4.kHome=`<div class="kq" style="margin-top:18px">Hi Mila! What shall we do?</div>
 <div class="kc" style="box-shadow:0 0 0 4px rgba(255,159,107,.5)"><div class="kci" style="background:#FF9F6B33">${I('guide_me')}</div><div>Ask<div style="font:500 15px Atk;color:#5B4A60">“Why is the sky blue?”</div></div></div>
 <div class="kc"><div class="kci" style="background:#0CF2BA33">${I('study')}</div><div>Learn<div style="font:500 15px Atk;color:#5B4A60">Counting · 2 of 4</div></div></div>
 <div class="kc"><div class="kci" style="background:#C9B8FF44">${I('draw')}</div><div>Make<div style="font:500 15px Atk;color:#5B4A60">A story or a picture</div></div></div>
 <div style="font:600 15px Atk;color:#5B4A60;margin-top:14px;display:flex;gap:8px;align-items:center">${I('speaker')}Tap once to hear · tap again to go</div>`;
S4.kPath=`<div class="kq" style="margin-top:10px">Counting adventure</div><div class="stones">
 <svg class="trail" viewBox="0 0 350 380"><path d="M70 330 C 160 330, 250 300, 270 250 S 120 170, 90 130 S 220 40, 270 50" stroke="rgba(22,14,28,.15)" stroke-width="10" stroke-dasharray="2 18" stroke-linecap="round" fill="none"/></svg>
 <div class="stone d" style="left:22px;top:282px">${I('check')}Look</div><div class="stone c" style="left:222px;top:200px">${I('study')}Try</div>
 <div class="stone" style="left:42px;top:82px">${I('guide_me')}Help</div><div class="stone" style="left:222px;top:2px">${I('favorite')}Done</div></div>`;
S4.kTry=`<div class="kq" style="margin-top:6px">How many apples?</div><div class="apples">${[1,2,3,4,5].map(n=>`<i class="n" data-n="${n<=3?n:''}"></i>`).join('')}</div>
 <div class="picopt">${[4,5,6].map((n,i)=>`<div class="${i==1?'hear':''}">${n}<small>${i==1?'listening…':'tap to hear'}</small></div>`).join('')}</div>`;
S4.kHelp=`<div class="kq" style="margin-top:6px">How many apples?</div><div class="apples">${[1,2,3,4,5].map(n=>`<i class="n" data-n="${n}"></i>`).join('')}</div>
 <div class="together"><div class="th2">${I('guide_me')}LET’S COUNT TOGETHER</div><p>Touch each apple with me: 1, 2, 3, 4… and one more is…</p></div>`;
S4.kRight=`<div class="kq" style="margin-top:6px">Yes — 5 apples!</div><div class="apples">${[1,2,3,4,5].map(n=>`<i class="n" data-n="${n}"></i>`).join('')}</div>
 <div class="saved"><div class="ck">${I('check')}</div><div>You counted all 5 yourself<span>Next: count the stars</span></div></div>`;
S4.kIdea=`<div class="kq" style="margin-top:10px">What’s your story about?</div><div class="kbub"><div class="r0">${ringK()}</div><p>Tell me your idea! Hold the ring and talk.</p></div>
 <div class="msg" style="background:#fff;color:#160E1C;font:600 18px Sora;box-shadow:0 4px 12px rgba(22,14,28,.06)">A fox who wants to touch the moon</div>
 <div style="font:600 15px Atk;color:#5B4A60;margin-top:14px">or tap <b>Write it myself</b></div>`;
S4.kMore=`<div class="kq" style="margin-top:10px">Where does the fox live?</div><div class="kchips">
 <div class="on"><div class="em" style="background:#0CF2BA33">${I('home')}</div>In a forest</div><div><div class="em" style="background:#8EC5FF44">${I('cloud')}</div>By the sea</div><div><div class="em" style="background:#F4C56A44">${I('favorite')}</div>On a hill</div></div>
 <div style="font:600 15px Atk;color:#5B4A60;margin-top:14px">Pick one, or say your own. 2 more questions.</div>`;
S4.kRead=`<div class="story"><div class="art"><div class="moon"></div><div class="fox"></div></div><p><span class="r">Every night, Fox climbed the hill</span> <span class="now">to wave</span> at the moon.</p></div>
 <div style="font:600 15px Atk;color:#5B4A60;margin-top:12px">Page 2 of 5 · words light up as halo reads</div>`;
S4.kSaved=`<div class="kq" style="margin-top:14px">Your story is saved!</div><div class="saved"><div class="ck">${I('check')}</div><div>Fox and the Moon<span>5 pages · in My projects</span></div></div>
 <div class="gu">${I('privacy')}Sharing needs a grown-up — they unlock with the phone’s lock.</div>`;
