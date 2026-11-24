import asyncio,sys,json
from playwright.async_api import async_playwright
CHK='''()=>{const out=[];const bp=document.querySelector('.boardpg');const H=bp.scrollHeight;
document.querySelectorAll('.ph .body, .r-sheet, .q-blk, .box, .q-prin, .q-bt, .r-stat, .note').forEach(e=>{if(e.scrollHeight>e.clientHeight+2||e.scrollWidth>e.clientWidth+2)out.push((e.className+' '+e.textContent.trim().slice(0,40))+' '+e.scrollHeight+'/'+e.clientHeight+' w'+e.scrollWidth+'/'+e.clientWidth)});
document.querySelectorAll('.ph .body').forEach(b=>{const r=b.getBoundingClientRect();b.querySelectorAll('*').forEach(c=>{const q=c.getBoundingClientRect();if(q.height>0&&q.bottom>r.bottom+1&&!c.closest('.r-sheet')&&getComputedStyle(c).position!='absolute')out.push('CLIP '+c.className+' '+c.textContent.trim().slice(0,30))})});
return {H,out:[...new Set(out)].slice(0,20)}}'''
async def main(boards,outdir):
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':1920,'height':1200},device_scale_factor=1.25)
    pg.on('pageerror',lambda e:print('ERR',e))
    for n in boards:
      await pg.goto(f'file:///tmp/w13/src/t13board.html?b={n}');await pg.wait_for_function('window.done')
      r=await pg.evaluate(CHK);print(n,r['H'],json.dumps(r['out'],ensure_ascii=False)[:1500])
      await pg.screenshot(path=f'{outdir}/{n}.png',full_page=True)
    await b.close()
asyncio.run(main(sys.argv[1].split(','),sys.argv[2]))
