import asyncio,json,sys
from playwright.async_api import async_playwright
JS='''()=>{const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let x;const o=[];while(x=w.nextNode()){if(x.parentElement.closest('svg,script,style'))continue;const s=x.nodeValue.trim();if(s&&/[A-Za-z]/.test(s))o.push(s)}return o}'''
async def main(bs):
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':1920,'height':1200})
    out={}
    for n in bs:
      await pg.goto(f'file:///tmp/w13/src/t13board.html?b={n}');await pg.wait_for_function('window.done')
      out[n]=await pg.evaluate(JS)
    await b.close();json.dump(out,open('/tmp/w13/texts.json','w'),ensure_ascii=False,indent=0)
asyncio.run(main(sys.argv[1].split(',')))
