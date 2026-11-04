// validate -> generate models -> copy to Android assets + admin. Run from repo root: `npm run sync`.
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const p = (...s) => path.join(repo, ...s);
const run = (cmd) => { console.log(`\n$ ${cmd}`); execSync(cmd, { cwd: repo, stdio: 'inherit' }); };

for (const dir of ['tools/content', 'tools/gen-models']) {
  if (!fs.existsSync(p(dir, 'node_modules'))) {
    console.error(`Missing dependencies in ${dir}. Run once from the repo root: npm run setup`);
    process.exit(1);
  }
}

run('npm run content:validate');
run('npm run models:gen');

const index = JSON.parse(fs.readFileSync(p('content/index.json'), 'utf8'));
const ids = index.devices.map((d) => d.id);

const contentDests = ['android/app/src/main/assets/content', 'admin/src/data/seed'];
const modelDests = ['android/app/src/main/assets/models', 'admin/public/models'];

const keepContent = new Set(['index.json', ...ids.map((id) => `${id}.json`)]);
const keepModels = new Set(ids.flatMap((id) => [`${id}.glb`, `${id}.hotspots.json`]));

function sync(destRel, files, keep, stalePattern) {
  const dest = p(destRel);
  fs.mkdirSync(dest, { recursive: true });
  for (const [name, src] of files) fs.copyFileSync(src, path.join(dest, name));
  for (const f of fs.readdirSync(dest)) {
    if (stalePattern.test(f) && !keep.has(f)) {
      fs.rmSync(path.join(dest, f));
      console.log(`removed stale ${destRel}/${f}`);
    }
  }
  console.log(`synced ${files.length} file(s) -> ${destRel}`);
}

const contentFiles = [['index.json', p('content/index.json')], ...ids.map((id) => [`${id}.json`, p(`content/${id}.json`)])];
const modelFiles = ids.flatMap((id) => [`${id}.glb`, `${id}.hotspots.json`].map((n) => [n, p('tools/gen-models/out', n)]));
for (const [, src] of modelFiles) if (!fs.existsSync(src)) { console.error(`missing generated file ${src}`); process.exit(1); }

console.log('');
for (const d of contentDests) sync(d, contentFiles, keepContent, /\.json$/);
for (const d of modelDests) sync(d, modelFiles, keepModels, /\.(glb|hotspots\.json)$/);
console.log('\nSync complete.');
