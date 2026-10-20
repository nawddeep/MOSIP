// Drive headless Chrome over the DevTools protocol: open the dashboard, start the (fake) camera, read the UI, screenshot.
// Usage: node live.mjs <debugPort> <url> <screenshot.png> <clickCamera 0|1> [readTimesMs,comma,separated]
import fs from 'node:fs';
const [,, debugPort, url, shot, clickCam] = process.argv;
const sleep = ms => new Promise(r => setTimeout(r, ms));
let targets;
for (let i = 0; i < 50; i++) { try { targets = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json(); break; } catch { await sleep(200); } }
const page = targets.find(t => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(r => ws.onopen = r);
let id = 0; const pending = new Map(); const logs = [];
ws.onmessage = ev => { const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  if (m.method === 'Runtime.exceptionThrown') logs.push('EXCEPTION: ' + m.params.exceptionDetails.text + ' ' + (m.params.exceptionDetails.exception?.description || ''));
  if (m.method === 'Runtime.consoleAPICalled' && ['error','warning'].includes(m.params.type)) logs.push('console.' + m.params.type + ': ' + m.params.args.map(a => a.value ?? a.description).join(' '));
};
const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async expr => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result.result.value;
await send('Runtime.enable'); await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1360, height: 1000, deviceScaleFactor: 1, mobile: false });
await send('Page.navigate', { url }); await sleep(2500);
const reads = (process.argv[6] || '').split(',').filter(Boolean).map(Number);
const read = () => ev(`JSON.stringify({conn: document.getElementById('connPill').textContent, verdict: document.getElementById('verdict').textContent, why: document.getElementById('why').textContent.slice(0,60), score: document.getElementById('scoreTxt').textContent, perf: document.getElementById('perf').textContent, alert: document.getElementById('alert').innerText.slice(0,70)})`);
if (clickCam === '1') { await ev(`document.getElementById('camBtn').click()`); let t = 0; for (const at of reads) { await sleep(at - t); t = at; console.log('t=' + at + 'ms', await read()); } if (!reads.length) await sleep(7000); }
const ui = await ev(`JSON.stringify({conn: document.getElementById('connPill').textContent, health: document.getElementById('healthPill').textContent,
  alert: document.getElementById('alert').innerText, verdict: document.getElementById('verdict').textContent, why: document.getElementById('why').textContent,
  score: document.getElementById('scoreTxt').textContent, perf: document.getElementById('perf').textContent,
  checks: [...document.querySelectorAll('#checks .tag')].map(t=>t.textContent).join(','), badge: document.getElementById('badge').textContent,
  camBtn: document.getElementById('camBtn').textContent, proof: document.getElementById('proof').innerText.slice(0,160)})`);
const png = await send('Page.captureScreenshot', { format: 'png' });
fs.writeFileSync(shot, Buffer.from(png.result.data, 'base64'));
console.log(ui); console.log('logs:', JSON.stringify(logs));
ws.close(); process.exit(0);
