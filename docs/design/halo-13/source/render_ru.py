import json,asyncio,sys
from playwright.async_api import async_playwright
RU=json.load(open('/tmp/w13/src/ru13.json'))
FIT=open('/tmp/w13/src/fit.js').read()
async def main(boards,outdir):
    async with async_playwright() as p:
        b=await p.chromium.launch(); pg=await b.new_page(viewport={'width':1920,'height':1200},device_scale_factor=1.25)
        rep={}
        for n in boards:
            await pg.goto(f'file:///tmp/w13/src/t13board.html?b={n}'); await pg.wait_for_function('window.done')
            r=await pg.evaluate(FIT,RU); rep[n]=r
            await pg.wait_for_timeout(100)
            await pg.screenshot(path=f'{outdir}/{n}.png',full_page=True)
            print(n,r['iters'],r['shrunk'],r['left'][:4],len(r['missing']))
        await b.close(); return rep
r=asyncio.run(main(sys.argv[1].split(','),sys.argv[2])); json.dump(r,open('/tmp/w13/fitrep.json','w'),ensure_ascii=False,indent=1)
