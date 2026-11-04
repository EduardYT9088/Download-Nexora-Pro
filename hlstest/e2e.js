const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const D = JSON.parse(fs.readFileSync('../data.json', 'utf8'));
  const html = fs.readFileSync('index.html', 'utf8');
  const hlsSrc = fs.readFileSync(require.resolve('hls.js/dist/hls.min.js'), 'utf8');
  const bridge = `(function(){const D=${JSON.stringify(D)};const emit=(o,ms)=>setTimeout(()=>window.nxEvent&&window.nxEvent(o),ms||50);
    window.NexoraAndroid={hasNp:()=>true,ytEngine:()=>'{"status":"ready","version":"x"}',account:()=>'{"logged":false}',version:()=>'1.0.99',ytActive:()=>'[]',takeShared:()=>'',scanLibrary:()=>'[]',mediaPerm:()=>true,clipboard:()=>'',status:()=>'{}',
      setBarColor(){},setBars(){},setNow(){},suggest(){},httpGet(req){emit({type:'http',req,ok:false})},
      ytSearch(req,q,c,s){emit(Object.assign({type:'search',req},s>1?{ok:true,items:[]}:D.search))},
      ytInfoFast(req){emit(Object.assign({type:'info',req},D.info),300)},ytInfo(req){emit(Object.assign({type:'info',req},D.info),300)},ytInfoFull(req){emit({type:'info',req,ok:false,error:'x'})},ytCancelInfo(){},
      npComments(req){emit(Object.assign({type:'comments',req},D.comments),200)},
      ytList(req){emit(Object.assign({type:'list',req},D.channel||{ok:false}))}};})();`;
  const browser = await chromium.launch({ channel: 'chrome', args: ['--disable-web-security', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, userAgent: 'Mozilla/5.0 (Linux; Android 14; 23117RA68G; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36', locale: 'es-CO' });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push('PAGEERROR ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 200)) });
  await page.route('http://app.local/**', r => {
    const u = r.request().url();
    if (u.endsWith('/index.html')) return r.fulfill({ contentType: 'text/html', body: html.replace('<script src="hls.min.js"></script>', '<script src="bridge.js"></script><script src="hls.min.js"></script>') });
    if (u.endsWith('/bridge.js')) return r.fulfill({ contentType: 'application/javascript', body: bridge });
    if (u.endsWith('/hls.min.js')) return r.fulfill({ contentType: 'application/javascript', body: hlsSrc });
    return r.fulfill({ status: 404, body: '' });
  });
  await page.goto('http://app.local/index.html');
  await page.evaluate(() => localStorage.setItem('nexora.v1', JSON.stringify({ accepted: true, items: [], lang: 'es' })));
  await page.reload(); await page.waitForTimeout(800);
  await page.fill('#url', 'rick astley'); await page.press('#url', 'Enter'); await page.waitForTimeout(2500);
  await page.screenshot({ path: '../shot-search.png' });
  const t0 = Date.now();
  await page.click('.card[data-res]');
  let first = null;
  for (let i = 0; i < 60; i++) { await page.waitForTimeout(250); const ct = await page.evaluate(() => document.getElementById('wv').currentTime); if (ct > 0 && first === null) { first = Date.now() - t0; } if (ct > 3) break; }
  const st = await page.evaluate(() => ({ t: wv().currentTime, vh: wv().videoHeight, hls: !!(W && W.hls), levels: W && W.hls ? W.hls.levels.map(l => l.height + '/' + (l.videoCodec || '')) : [], curH: W.curH, com: W.com && W.com.items.length, rel: W.related.length, state: document.getElementById('wstate').textContent }));
  console.log('PLAY firstFrameMs=' + first, JSON.stringify(st));
  await page.waitForTimeout(1500); await page.screenshot({ path: '../shot-watch.png' });
  // calidad 1080 manual
  await page.evaluate(() => { saved.qPref = 1080; applyQ(); });
  for (let i = 0; i < 60; i++) { await page.waitForTimeout(250); if (await page.evaluate(() => wv().videoHeight >= 1000)) break; }
  console.log('Q1080', JSON.stringify(await page.evaluate(() => ({ vh: wv().videoHeight, t: wv().currentTime, curH: W.curH }))));
  // subtítulos
  await page.evaluate(() => { const s = W.info.subtitles.find(x => x.lang.startsWith('en')) || W.info.subtitles[0]; setSubs(s); });
  await page.waitForTimeout(3000);
  console.log('SUBS', JSON.stringify(await page.evaluate(() => { const tt = wv().textTracks[0]; return { tracks: wv().textTracks.length, mode: tt && tt.mode, cues: tt && tt.cues ? tt.cues.length : -1 } })));
  // menú y paneles
  await page.click('#wMenu'); await page.waitForTimeout(400); await page.click('#mQ'); await page.waitForTimeout(400); await page.screenshot({ path: '../shot-quality.png' });
  await page.evaluate(() => closeSheet()); await page.waitForTimeout(300);
  await page.click('#wCom'); await page.waitForTimeout(600); await page.screenshot({ path: '../shot-comments.png' }); await page.click('#wpClose'); await page.waitForTimeout(300);
  await page.click('#wHead'); await page.waitForTimeout(600); await page.screenshot({ path: '../shot-about.png' }); await page.click('#wpClose');
  // seek por minuto en la descripción
  await page.evaluate(() => seekTo(60)); await page.waitForTimeout(2500);
  console.log('SEEK', await page.evaluate(() => wv().currentTime));
  // canal
  await page.evaluate(() => { closeWatch(); openChannel(D_CH = 'https://www.youtube.com/@RickAstleyYT', 'Rick Astley') }).catch(() => {});
  await page.waitForTimeout(1500); await page.screenshot({ path: '../shot-channel.png' });
  console.log(errs.length ? errs.join('\n') : 'NO_ERRORS');
  await browser.close();
})().catch(e => { console.error('TESTFAIL', e); process.exit(1) });
