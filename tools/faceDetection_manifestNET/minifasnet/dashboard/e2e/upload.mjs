// Upload a photo through the dashboard file input in headless Chrome and print the verdict.
// Usage: node upload.mjs <debugPort> <url> <image path>
const [,, port, url, file] = process.argv;
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t; for (let i = 0; i < 50; i++) { try { t = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); break; } catch { await sleep(200); } }
const ws = new WebSocket(t.find(x => x.type === 'page').webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
let id = 0; const p = new Map(); const errs = [];
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && p.has(m.id)) { p.get(m.id)(m); p.delete(m.id); }
  if (m.method === 'Runtime.exceptionThrown') errs.push(m.params.exceptionDetails.text); };
const send = (method, params = {}) => new Promise(r => { const i = ++id; p.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
await send('Runtime.enable'); await send('Page.enable'); await send('Page.navigate', { url }); await sleep(2000);
const doc = await send('DOM.getDocument'); const q = await send('DOM.querySelector', { nodeId: doc.result.root.nodeId, selector: '#file' });
await send('DOM.setFileInputFiles', { nodeId: q.result.nodeId, files: [file] }); await sleep(2500);
const v = await send('Runtime.evaluate', { expression: `JSON.stringify({verdict: document.getElementById('verdict').textContent, score: document.getElementById('scoreTxt').textContent, why: document.getElementById('why').textContent.slice(0,70), stillShown: !document.getElementById('still').hidden, videoHidden: document.getElementById('vid').hidden})`, returnByValue: true });
console.log(v.result.result.value, 'errors:', JSON.stringify(errs)); process.exit(0);
