// Validates /content/*.json against schema + cross-references. Exit 1 on failure.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const contentRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../content');
// EQUIPX_CONTENT_DIR lets a draft folder (own index.json + device files) be validated on its own.
const root = process.env.EQUIPX_CONTENT_DIR ? path.resolve(process.env.EQUIPX_CONTENT_DIR) : contentRoot;
const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const ajv = new Ajv2020({ allErrors: true, strict: true });
addFormats(ajv);
const vDevice = ajv.compile(readJson(path.join(contentRoot, 'schema/device.schema.json')));
const vIndex = ajv.compile(readJson(path.join(contentRoot, 'schema/index.schema.json')));

const errors = [];
const err = (file, msg) => errors.push(`${file}: ${msg}`);
const fmt = (v) => v.errors.map((e) => `${e.instancePath || '/'} ${e.message}`);

const dup = (arr) => arr.filter((x, i) => arr.indexOf(x) !== i);

function checkDevice(file, d) {
  const partIds = d.parts.map((p) => p.id);
  const layoutIds = d.layout.parts.map((p) => p.id);
  for (const id of dup(partIds)) err(file, `duplicate part id ${id}`);
  for (const id of dup(layoutIds)) err(file, `duplicate layout part id ${id}`);
  for (const id of partIds) if (!layoutIds.includes(id)) err(file, `part ${id} has no layout entry`);
  for (const id of layoutIds) if (!partIds.includes(id)) err(file, `layout part ${id} has no parts entry`);
  const nums = d.parts.map((p) => p.number).sort((a, b) => a - b);
  nums.forEach((n, i) => { if (n !== i + 1) err(file, `part numbers must be unique and run 1..N (got ${nums.join(',')})`); });
  if (d.model.asset !== `models/${d.id}.glb`) err(file, `model.asset should be models/${d.id}.glb`);
  if (d.model.hotspots !== `models/${d.id}.hotspots.json`) err(file, `model.hotspots should be models/${d.id}.hotspots.json`);
  for (const p of d.procedures) {
    p.steps.forEach((s, i) => { if (!partIds.includes(s.partId)) err(file, `procedure ${p.id} step ${i + 1}: unknown partId ${s.partId}`); });
  }
  for (const f of d.faults) {
    const optIds = f.options.map((o) => o.id);
    for (const id of dup(optIds)) err(file, `fault ${f.id}: duplicate option id ${id}`);
    if (!optIds.includes(f.correctOptionId)) err(file, `fault ${f.id}: correctOptionId ${f.correctOptionId} not in options`);
    for (const id of f.relatedPartIds) if (!partIds.includes(id)) err(file, `fault ${f.id}: unknown relatedPartId ${id}`);
  }
  for (const id of dup([...d.procedures, ...d.faults, ...d.quizzes].map((x) => x.id))) err(file, `duplicate procedure/fault/quiz id ${id}`);
  for (const q of d.quizzes) {
    for (const id of dup(q.questions.map((x) => x.id))) err(file, `quiz ${q.id}: duplicate question id ${id}`);
    for (const x of q.questions) {
      if (x.correctIndex >= x.options.length) err(file, `quiz ${q.id}/${x.id}: correctIndex ${x.correctIndex} out of range`);
    }
  }
}

const indexFile = path.join(root, 'index.json');
if (!fs.existsSync(indexFile)) { console.error('content/index.json missing'); process.exit(1); }
const index = readJson(indexFile);
if (!vIndex(index)) fmt(vIndex).forEach((m) => err('index.json', m));
else {
  for (const id of dup(index.devices.map((d) => d.id))) err('index.json', `duplicate device ${id}`);
  for (const entry of index.devices) {
    const f = `${entry.id}.json`;
    const fp = path.join(root, f);
    if (!fs.existsSync(fp)) { err('index.json', `device ${entry.id} has no ${f}`); continue; }
    const d = readJson(fp);
    if (!vDevice(d)) { fmt(vDevice).forEach((m) => err(f, m)); continue; }
    if (d.id !== entry.id) err(f, `id ${d.id} != index id ${entry.id}`);
    for (const k of ['name', 'category', 'summary']) if (d[k] !== entry[k]) err(f, `${k} differs from index.json`);
    checkDevice(f, d);
  }
  const listed = new Set(index.devices.map((d) => `${d.id}.json`));
  for (const f of fs.readdirSync(root)) {
    if (f.endsWith('.json') && f !== 'index.json' && !listed.has(f)) err(f, 'not listed in index.json');
  }
}

if (errors.length) {
  console.error(`Content validation FAILED (${errors.length} problem(s)):`);
  errors.forEach((e) => console.error('  - ' + e));
  process.exit(1);
}
console.log(`Content validation OK (${index.devices.length} device(s)).`);
