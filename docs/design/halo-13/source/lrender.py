import asyncio,json,glob,os
from playwright.async_api import async_playwright
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':1250,'height':220})
    pg.on('pageerror',lambda e:print('ERR',e))
    for f in sorted(glob.glob('out/raw/*.json')):
      n=os.path.basename(f)[14:-5]; v=n.rsplit('_',1)[1]; k=n.rsplit('_',1)[0]
      await pg.goto(f'file:///tmp/w/lprev.html?v={v}'); await pg.evaluate('d=>window.run(d)',json.load(open(f)))
      await pg.wait_for_function('window.done'); await pg.screenshot(path=f'img/l4/prev_{k}_{v}.png',clip={'x':0,'y':0,'width':1270,'height':220})
      print(k,v)
    await b.close()
asyncio.run(main())
