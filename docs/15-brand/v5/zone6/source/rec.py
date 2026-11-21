import asyncio,os,glob
from playwright.async_api import async_playwright
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch()
        ctx=await b.new_context(viewport={'width':1600,'height':1000},record_video_dir='/tmp/z6/vid',record_video_size={'width':1600,'height':1000})
        pg=await ctx.new_page(); errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
        await pg.goto('file:///tmp/z6/proto.html'); await pg.wait_for_timeout(1800)
        async def click(sel,wait=1400):
            await pg.locator(sel).first.click(); await pg.wait_for_timeout(wait)
        await click('.ocell',3000)           # listen
        await pg.wait_for_timeout(3200)       # work -> need
        await click('.btn:has-text("Send to Anna")',3600)
        await pg.mouse.move(0,0)
        o=pg.locator('.ocell').first; bb=await o.bounding_box()
        await pg.mouse.move(bb['x']+bb['width']/2,bb['y']+bb['height']/2); await pg.mouse.down(); await pg.wait_for_timeout(800); await pg.mouse.up(); await pg.wait_for_timeout(1500)
        await click('.tile:has-text("Workspace")',1500)
        await click('.sg:has-text("Tools")',1500)
        await click('.trow:has-text("HaloStudio")',1800)
        await click('.act:has-text("More")',2600)
        await click('.btn:has-text("Export PNG")',2000)
        await click('.tab:has-text("Activity")',1600)
        await click('.sg:has-text("Running")',1200)
        await click('.tab:has-text("Places")',1500)
        await click('.tile:has-text("Widgets")',2200)
        print('errors',errs)
        await ctx.close(); await b.close()
asyncio.run(main())
