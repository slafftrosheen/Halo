const {chromium}=require('C:/Users/slavd/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 for(const [width,height] of [[360,800],[768,1024],[1440,1000]]){
  const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:1});
  const failures=[];page.on('pageerror',e=>failures.push(e.message));page.on('response',r=>{if(r.status()>=400)failures.push(r.status()+' '+r.url())});
  await page.goto('http://127.0.0.1:8766',{waitUntil:'networkidle'});
  await page.locator('img').evaluateAll(xs=>xs.forEach(x=>x.loading='eager'));
  await page.waitForFunction(()=>Array.from(document.images).every(x=>x.complete));
  const result=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,images:Array.from(document.images).map(x=>({src:x.getAttribute('src'),width:x.naturalWidth,height:x.naturalHeight})),title:document.title}));
  await page.screenshot({path:'C:/Users/slavd/HaloEco/Halo-main/.artifacts/haloweb-'+width+'.png',fullPage:true});
  console.log(JSON.stringify({width,...result,failures}));
  if(result.overflow||result.images.some(x=>!x.width)||failures.length) process.exitCode=1;
  await page.close();
 }
 await browser.close();
})();
