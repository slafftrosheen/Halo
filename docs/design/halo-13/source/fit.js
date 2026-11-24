(ru)=>{
const path=e=>{const p=[];while(e&&e!==document.body){p.push([...e.parentNode.children].indexOf(e));e=e.parentNode}return p.join('.')};
const bad=()=>{const r=new Map();
 for(const e of document.querySelectorAll('body *')){if(e.closest('svg'))continue;const cs=getComputedStyle(e);
  if((e.scrollWidth>e.clientWidth+2&&cs.overflowX!='visible')||(e.scrollHeight>e.clientHeight+2&&cs.overflowY!='visible'))r.set(path(e),e)}
 for(const sc of document.querySelectorAll('.sc,.tb')){const b=sc.getBoundingClientRect();for(const e of sc.querySelectorAll('*')){if(e.closest('svg'))continue;const q=e.getBoundingClientRect();
  if(q.width&&(q.right>b.right+1||q.bottom>b.bottom+1)&&e.children.length==0&&e.parentElement)r.set('E'+path(e),e.parentElement)}}
 // leaf text wider than its parent box (chips/buttons with nowrap)
 for(const e of document.querySelectorAll('body *')){if(e.closest('svg')||!e.firstChild||e.children.length)continue;const p=e.parentElement;if(!p)continue;const q=e.getBoundingClientRect(),pb=p.getBoundingClientRect();
  if(q.width&&pb.width&&(q.right>pb.right+2||q.left<pb.left-2)&&getComputedStyle(p).overflow!='visible')r.set('W'+path(e),p)}
 return r};
const base=new Set(bad().keys());
document.documentElement.lang='ru';
const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let x;const ns=[],missing=[];while(x=w.nextNode())ns.push(x);
for(const n of ns){const s=n.nodeValue.trim();if(!s)continue;if(ru[s]!==undefined)n.nodeValue=n.nodeValue.replace(s,ru[s]);else if(/[A-Za-z]{3}/.test(s))missing.push(s)}
let it=0,shrunk=0,left=[];
for(;it<10;it++){const m=bad();const off=[...m.entries()].filter(([k])=>!base.has(k)&&!base.has(k.replace(/^[EW]/,'')));left=off.map(([k,e])=>(e.innerText||'').slice(0,40).replace(/\n/g,' '));if(!off.length)break;
 const seen=new Set();for(const [k,e] of off){if(seen.has(e))continue;seen.add(e);
  for(const d of [e,...e.querySelectorAll('*')]){if(d.closest('svg'))continue;const fs=parseFloat(getComputedStyle(d).fontSize);d.style.fontSize=(fs*0.94)+'px'}shrunk++}}
return {iters:it,shrunk,left,missing:[...new Set(missing)]}}
