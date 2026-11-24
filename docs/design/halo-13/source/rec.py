import asyncio,sys,glob,os,subprocess
from playwright.async_api import async_playwright
lang=sys.argv[1]
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch()
    ctx=await b.new_context(viewport={'width':1280,'height':860},record_video_dir=f'/tmp/w13/films/raw_{lang}',record_video_size={'width':1280,'height':860},device_scale_factor=1)
    pg=await ctx.new_page()
    await pg.goto(f'file:///tmp/w13/proto/halo13.html?flow=1&lang={lang}');await pg.wait_for_timeout(1200)
    await pg.evaluate('()=>{window.autoDone=0;setTimeout(()=>window.haloAuto([1,2,3,4,5,6,7]),10)}')
    for i in range(400):
      await pg.wait_for_timeout(500)
      if await pg.evaluate('window.autoDone'):break
    await pg.wait_for_timeout(1500)
    await ctx.close();await b.close()
asyncio.run(main())
v=glob.glob(f'/tmp/w13/films/raw_{lang}/*.webm')[0]
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',v,'-c:v','libx264','-pix_fmt','yuv420p','-crf','23','-preset','veryfast','-movflags','+faststart',f'/tmp/w13/films/halo13-prototype-{lang}.mp4'])
print('ok',lang)
