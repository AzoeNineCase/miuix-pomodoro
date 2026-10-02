/**
 * 导出网页版各元素的精确几何/样式数据（作为与原生版对齐的基准）。
 *
 * 用法: node tools/web_metrics.js <page> <theme> [out.json]
 *   page: timer|stats|todos|settings   theme: light|dark|aurora
 *
 * 输出 JSON：每个元素的 rect（CSS px，即原生侧 dp）、字号/字重/行高/字距、
 * padding/圆角/gap，以及文本 Range 盒（墨水盒，用于对比文字基线）。
 * 注意：原生截图 @density=2 时，图片 px = 2 × dp；网页截图 @DPR=2 时同理。
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const http = require('http');

const REPO = 'e:/ke/cook';
const SHOT_DIR = path.join(os.tmpdir(), 'shot');
const FONTS_DIR = path.join(REPO, 'android/app/src/main/assets/fonts');
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

const page = process.argv[2] || 'timer';
const theme = process.argv[3] || 'light';
const outJson = process.argv[4] || path.join(SHOT_DIR, `metrics-${page}-${theme}.json`);

function prepareHtml() {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const css = fs
    .readFileSync(path.join(FONTS_DIR, 'fonts.css'), 'utf8')
    .replace(/https:\/\/appassets\.androidplatform\.net\/fonts\//g, '/fonts/');
  fs.writeFileSync(path.join(SHOT_DIR, 'fonts.css'), css, 'utf8');
  let html = fs.readFileSync(path.join(REPO, 'index.html'), 'utf8');
  html = html.replace(/<link rel="preconnect"[^>]*>\s*/g, '');
  html = html.replace(/<link[^>]*fonts\.googleapis\.com[^>]*>\s*/g, '');
  html = html.replace('</head>', `  <link rel="stylesheet" href="/fonts.css" />\n</head>`);
  fs.writeFileSync(path.join(SHOT_DIR, 'shot.html'), html, 'utf8');
}

function startServer() {
  const server = http.createServer((req, res) => {
    const url = req.url.split('?')[0];
    const send = (file, type) => {
      try {
        const buf = fs.readFileSync(file);
        res.writeHead(200, { 'Content-Type': type });
        res.end(buf);
      } catch {
        res.writeHead(404); res.end('not found');
      }
    };
    if (url === '/' || url === '/shot.html') return send(path.join(SHOT_DIR, 'shot.html'), 'text/html; charset=utf-8');
    if (url === '/fonts.css') return send(path.join(SHOT_DIR, 'fonts.css'), 'text/css; charset=utf-8');
    if (url.startsWith('/fonts/')) return send(path.join(FONTS_DIR, path.basename(url)), 'font/woff2');
    res.writeHead(404); res.end('not found');
  });
  return new Promise((resolve) => server.listen(8124, '127.0.0.1', () => resolve(server)));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const fetchJson = async (url) => (await fetch(url)).json();

const MEASURE_JS = `(() => {
  const sels = [
    '.rail', '.rail-item', '.rail-brand',
    '.topbar', '.topbar h1', '.topbar p', '.icon-btn',
    '.content', '.page.active .card', '.card',
    '.card-title', '.card-title strong',
    '.segment', '.segment button', '.segment button.active',
    '.timer-wrap', '.timer-ring', '.ring-track', '.timer-center', '.timer-time', '.timer-mode-label', '.timer-center .dot',
    '.controls', '.btn', '.btn-primary', '.btn-ghost', '.session-dots', '.session-dots i',
    '.stat-cell', '.stat-cell .val', '.stat-cell .lbl',
    '.stepper', '.stepper button', '.stepper .val',
    '.switch',
    '.todo-item', '.todo-item .t', '.todo-check',
    '.setting-row', '.setting-row .name', '.setting-row .sub',
    '.tone-chip', '.upload-btn',
    '.week-bars', '.week-bars .bar',
    '.about', '.about-head', '.about-back', '.about-main', '.about-logo', '.about-name', '.about-ver',
    '.about-card', '.about-item', '.about-item-label', '.about-item-value'
  ];
  const out = [];
  const textInfo = (el) => {
    try {
      const r = document.createRange();
      r.selectNodeContents(el);
      const b = r.getBoundingClientRect();
      return { tx: +b.x.toFixed(2), ty: +b.y.toFixed(2), tw: +b.width.toFixed(2), th: +b.height.toFixed(2) };
    } catch (e) { return null; }
  };
  for (const sel of sels) {
    document.querySelectorAll(sel).forEach((el, i) => {
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      const rec = { sel, i, x: +r.x.toFixed(2), y: +r.y.toFixed(2), w: +r.width.toFixed(2), h: +r.height.toFixed(2),
        fs: cs.fontSize, fw: cs.fontWeight, lh: cs.lineHeight, ls: cs.letterSpacing,
        pad: cs.padding, rad: cs.borderRadius, gap: cs.gap, disp: cs.display };
      if (['.topbar h1','.timer-time','.card-title','.segment button','.stepper .val','.stat-cell .val'].includes(sel)) {
        rec.text = textInfo(el);
      }
      out.push(rec);
    });
  }
  // 关键主体的墨水盒（含中文回退字体的行盒）
  const ink = {};
  const pick = { title: '.topbar h1', subtitle: '.topbar p', time: '.timer-time', ready: '.segment button', val: '.stepper .val' };
  for (const [k, sel] of Object.entries(pick)) {
    const el = document.querySelector(sel);
    ink[k] = el ? textInfo(el) : null;
  }
  // 行盒探针：CSS line-height: normal 在真实字体栈下的实测行高（区分纯拉丁 / 中文）
  const lines = { latin: {}, cjk: {} };
  const stack = getComputedStyle(document.body).fontFamily;
  const probe = (text) => {
    const out = {};
    for (const s of [12, 13, 14, 15, 16, 18, 20, 22, 24, 26, 28, 44, 56, 64]) {
      const d = document.createElement('div');
      d.style.cssText = 'position:absolute;visibility:hidden;font-family:' + stack + ';font-size:' + s + 'px;line-height:normal;white-space:nowrap';
      d.textContent = text;
      document.body.appendChild(d);
      out[s] = d.getBoundingClientRect().height;
      d.remove();
    }
    return out;
  };
  lines.latin = probe('Hg25:00');
  lines.cjk = probe('专注测试');
  return JSON.stringify({ bands: out, ink, lines });
})()`;

async function main() {
  prepareHtml();
  const server = await startServer();
  const profile = path.join(os.tmpdir(), 'edge-shot-profile');
  const edge = cp.spawn(EDGE, [
    '--headless=new', '--remote-debugging-port=9333', `--user-data-dir=${profile}`,
    '--no-first-run', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files',
    '--disable-web-security', 'about:blank',
  ], { stdio: 'ignore' });

  let version = null;
  for (let i = 0; i < 60 && !version; i++) {
    try { version = await fetchJson('http://127.0.0.1:9333/json/version'); } catch { await sleep(300); }
  }
  if (!version) { edge.kill(); throw new Error('CDP 未就绪'); }

  const targets = await fetchJson('http://127.0.0.1:9333/json/list');
  const ws = new WebSocket(targets[0].webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0;
  const pending = new Map();
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
  };
  const send = (method, params = {}, sessionId) =>
    new Promise((resolve) => { const mid = ++id; pending.set(mid, resolve); ws.send(JSON.stringify({ id: mid, method, params, sessionId })); });

  const { result: { targetId } } = await send('Target.createTarget', { url: 'about:blank' });
  const { result: { sessionId } } = await send('Target.attachToTarget', { targetId, flatten: true });
  await send('Page.enable', {}, sessionId);
  await send('Runtime.enable', {}, sessionId);
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 800, deviceScaleFactor: 2, mobile: false }, sessionId);
  const settings = JSON.stringify({ theme, focusMinutes: 25, shortMinutes: 5, longMinutes: 15 });
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `try{localStorage.clear();localStorage.setItem('pomodoro.miuix.settings', ${JSON.stringify(settings)});}catch(e){}`,
  }, sessionId);
  await send('Page.navigate', { url: 'http://127.0.0.1:8124/shot.html' }, sessionId);
  await sleep(600);
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }, sessionId);
    return r.result?.result?.value;
  };
  for (let i = 0; i < 60; i++) {
    const ok = await evalJs(`(document.getElementById('loading')===null || document.getElementById('loading').classList.contains('hidden')) && document.fonts.status==='loaded'`);
    if (ok) break;
    await sleep(300);
  }
  // 特殊状态页（与 webshot.js 保持一致：running / about / todos-items）
  const basePage = page === 'running' ? 'timer' : page === 'todos-items' ? 'todos' : page === 'about' ? 'settings' : page;
  if (basePage !== 'timer') {
    await evalJs(`document.querySelector('.rail-item[data-page="${basePage}"]')?.click()`);
    await sleep(700);
  }
  if (page === 'about') {
    await evalJs(`document.getElementById('aboutEntry')?.click()`);
    await sleep(900);
  } else if (page === 'running') {
    await evalJs(`(() => {
      remaining = 687; total = 1500; running = true; endAt = Date.now() + remaining * 1000;
      completedSessions = 2;
      document.getElementById('timerRing').classList.add('running');
      document.getElementById('startText').textContent = '暂停';
      document.getElementById('startIcon').textContent = 'pause';
      document.getElementById('startBtn').classList.add('running');
      updateTime(); updateRing(); updateDots();
    })()`);
    await sleep(400);
  } else {
    await sleep(1400);
  }
  const raw = await evalJs(MEASURE_JS);
  fs.writeFileSync(outJson, raw, 'utf8');
  const parsed = JSON.parse(raw);
  console.log(`saved ${outJson} (${parsed.bands.length} elements)`);
  ws.close(); edge.kill(); server.close(); await sleep(300);
}

main().catch((e) => { console.error('FAILED', e); process.exit(1); });
