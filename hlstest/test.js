const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const data = JSON.parse(fs.readFileSync('../hls.json', 'utf8'));
  const hlsSrc = fs.readFileSync(require.resolve('hls.js/dist/hls.min.js'), 'utf8');
  console.log('hls.js version', require('hls.js/package.json').version);
  const browser = await chromium.launch({ channel: 'chrome', args: ['--disable-web-security', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await browser.newContext({ userAgent: 'Mozilla/5.0 (Linux; Android 14; 23117RA68G Build/UKQ1.230804.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.107 Mobile Safari/537.36' });
  for (const [url, d] of Object.entries(data)) {
    if (!d.hls) { console.log('NOHLS', url); continue; }
    const page = await ctx.newPage();
    page.on('console', m => console.log('   [console]', m.text().slice(0, 300)));
    await page.setContent('<video id=v muted playsinline style="width:640px"></video>');
    await page.addScriptTag({ content: hlsSrc });
    const r = await page.evaluate(async ({ src, vonly, audio }) => {
      const out = { url: src.slice(0, 60) };
      const v = document.getElementById('v');
      out.native = v.canPlayType('application/vnd.apple.mpegurl');
      out.mse = !!window.MediaSource && MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d401f"');
      // CORS check for adaptive url fetch (without disable-web-security this would matter)
      try { const res = await fetch(vonly[0].url + '&range=0-1000'); out.fetchVonly = res.status; } catch (e) { out.fetchVonly = 'ERR ' + e; }
      const hls = new Hls({ enableWorker: true });
      const t0 = performance.now();
      const errs = [];
      hls.on(Hls.Events.ERROR, (e, d) => errs.push(d.type + ':' + d.details + (d.fatal ? ':FATAL' : '')));
      await new Promise(res => { hls.on(Hls.Events.MANIFEST_PARSED, res); hls.loadSource(src); hls.attachMedia(v); setTimeout(res, 15000); });
      out.levels = hls.levels.map(l => l.height + 'p' + (l.frameRate ? Math.round(l.frameRate) : '') + ' ' + (l.videoCodec || '') + '/' + (l.audioCodec || ''));
      out.audioTracks = hls.audioTracks.map(t => t.name + ' ' + t.lang + (t.default ? ' default' : ''));
      try { await v.play() } catch (e) { out.playErr = String(e) }
      let firstFrame = null;
      for (let i = 0; i < 80; i++) { await new Promise(r => setTimeout(r, 250)); if (v.currentTime > 0 && firstFrame === null) firstFrame = Math.round(performance.now() - t0); if (v.currentTime > 4) break; }
      out.firstFrameMs = firstFrame; out.t = v.currentTime; out.level = hls.currentLevel >= 0 ? hls.levels[hls.currentLevel].height : -1;
      out.vw = v.videoWidth; out.vh = v.videoHeight;
      // switch to highest level and seek
      hls.currentLevel = hls.levels.length - 1; v.currentTime = 60;
      for (let i = 0; i < 60; i++) { await new Promise(r => setTimeout(r, 250)); if (v.currentTime > 62 && v.videoHeight >= 700) break; }
      out.afterSwitch = { t: v.currentTime, vh: v.videoHeight };
      out.errs = errs.slice(0, 10);
      hls.destroy();
      return out;
    }, { src: d.hls, vonly: d.vonly || [], audio: d.audio });
    console.log('RESULT', JSON.stringify(r));
    await page.close();
  }
  await browser.close();
})().catch(e => { console.error('TESTFAIL', e); process.exit(1) });
