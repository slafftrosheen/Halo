import asyncio,sys,os
from playwright.async_api import async_playwright
from PIL import Image
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch();pg=await b.new_page(viewport={'width':1920,'height':1200},device_scale_factor=1.25)
    for name in ['cover','front2','ch30']:
      await pg.goto(f'file:///tmp/w13/book/{name}.html',wait_until='networkidle');await pg.evaluate('document.fonts.ready');await pg.wait_for_timeout(400)
      chk=await pg.evaluate('''()=>[...document.querySelectorAll('.pg')].map((s,i)=>{const r=s.getBoundingClientRect();const o=[];s.querySelectorAll('*').forEach(e=>{const q=e.getBoundingClientRect();if(q.width&&e.children.length==0&&(q.bottom>r.bottom-30||q.right>r.right+1))o.push(e.textContent.trim().slice(0,30)||e.tagName)});const im=[...s.querySelectorAll('img')].filter(x=>!x.naturalWidth).length;return [i,o.slice(0,3),im]}).filter(x=>x[1].length||x[2])''')
      print(name,chk[:12])
      els=await pg.query_selector_all('.pg')
      os.makedirs(f'/tmp/w13/book/out/{name}',exist_ok=True)
      for i,e in enumerate(els):
        f=f'/tmp/w13/book/out/{name}/{i+1:03d}.png';await e.screenshot(path=f)
        Image.open(f).convert('RGB').save(f[:-4]+'.jpg',quality=86,optimize=True);os.remove(f)
    await b.close()
asyncio.run(main())
