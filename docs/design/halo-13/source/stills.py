import asyncio,json
from playwright.async_api import async_playwright
SAID={'en':'Swimming with Mia every Thursday at 5','ru':'Плавание с Mia каждый четверг в 5'}
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':1280,'height':860},device_scale_factor=2)
    for lang in ['en','ru']:
      ST=[(1,{'home':True,'who':{'wMe':1,'wStudy':1,'wCreate':1}}),(2,{'depth':'mSimple','offer':True}),(3,{'src':'hn'}),(4,{'q':SAID[lang]}),(5,{'tidy':1}),(6,{'sv':'try','hint':True,'res':True}),(7,{'k':'learn','ka':'a1'})]
      await pg.goto(f'file:///tmp/w13/proto/halo13.html?lang={lang}&still=1');await pg.wait_for_timeout(500)
      for i,(f,patch) in enumerate(ST):
        await pg.evaluate(f'()=>window.haloGo({f},{json.dumps(patch)})');await pg.wait_for_timeout(700)
        await pg.locator('.p-ph').screenshot(path=f'/tmp/w13/stills/still_{lang}_{i+1}.png')
    await b.close()
asyncio.run(main())
