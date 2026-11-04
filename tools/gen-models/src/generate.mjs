// Reads /content/index.json + /content/<device>.json and writes out/<id>.glb + out/<id>.hotspots.json
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Document, NodeIO } from '@gltf-transform/core';
import validator from 'gltf-validator';
import { box, cylinder, roundedBox, transform } from './geometry.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const contentDir = process.env.EQUIPX_CONTENT_DIR ? path.resolve(process.env.EQUIPX_CONTENT_DIR) : path.resolve(here, '../../../content');
const outDir = process.env.EQUIPX_OUT_DIR ? path.resolve(process.env.EQUIPX_OUT_DIR) : path.resolve(here, '../out');

const MATERIAL = { metallic: 0, roughness: 0.7 };
const DEFAULT_COLOR = {
  screen: '#9FB3A5', display: '#9FB3A5', knob: '#4A4A48', button: '#8C8B87', switch: '#8C8B87',
  port: '#2B2B2B', led: '#B5524A', probe: '#A34A4A', slot: '#3A3A3A', handle: '#8C8B87',
};
const EMBED = 0.0005; // parts sink slightly into the body so no gaps show
const HOTSPOT_OFFSET = 0.002;

// Per face: outward normal n, in-face axes u (right) and v (up) as seen from outside. u x v = n.
const FACES = {
  front: { n: [0, 0, 1], u: [1, 0, 0], v: [0, 1, 0] },
  back: { n: [0, 0, -1], u: [-1, 0, 0], v: [0, 1, 0] },
  right: { n: [1, 0, 0], u: [0, 0, -1], v: [0, 1, 0] },
  left: { n: [-1, 0, 0], u: [0, 0, 1], v: [0, 1, 0] },
  top: { n: [0, 1, 0], u: [1, 0, 0], v: [0, 0, -1] },
};

const r6 = (x) => Math.round(x * 1e6) / 1e6;

function partMesh(type, size) {
  const [w, h, d] = size;
  const z0 = -EMBED, z1 = d;
  switch (type) {
    case 'knob': return cylinder(Math.min(w, h) / 2, z0, z1, 32);
    case 'port': return cylinder(Math.min(w, h) / 2, z0, z1, 20);
    case 'led': return cylinder(Math.min(w, h) / 2, z0, z1, 12);
    case 'probe': return cylinder(Math.min(w, h) / 2, z0, z1, 16);
    default: {
      const m = box(w / 2, h / 2, (z1 - z0) / 2);
      return transform(m, [0, 0, (z0 + z1) / 2], [1, 0, 0], [0, 1, 0], [0, 0, 1]);
    }
  }
}

function faceCentreOffset(body, face) {
  return face === 'front' || face === 'back' ? body.depth / 2 : face === 'left' || face === 'right' ? body.width / 2 : body.height / 2;
}

function hexToLinear(hex) {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const lin = c.map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return [...lin, 1];
}

function addMesh(doc, buffer, name, mesh, colorHex) {
  const mat = doc.createMaterial(name).setBaseColorFactor(hexToLinear(colorHex))
    .setMetallicFactor(MATERIAL.metallic).setRoughnessFactor(MATERIAL.roughness);
  const prim = doc.createPrimitive()
    .setAttribute('POSITION', doc.createAccessor().setType('VEC3').setArray(new Float32Array(mesh.positions)).setBuffer(buffer))
    .setAttribute('NORMAL', doc.createAccessor().setType('VEC3').setArray(new Float32Array(mesh.normals)).setBuffer(buffer))
    .setIndices(doc.createAccessor().setType('SCALAR').setArray(new Uint16Array(mesh.indices)).setBuffer(buffer))
    .setMaterial(mat);
  const m = doc.createMesh(name).addPrimitive(prim);
  return doc.createNode(name).setMesh(m);
}

async function build(device) {
  const { body, parts: layoutParts } = device.layout;
  const doc = new Document();
  doc.getRoot().getAsset().generator = 'equipx gen-models';
  const buffer = doc.createBuffer();
  const scene = doc.createScene(device.id);
  let tris = 0;

  const bodyMesh = roundedBox(body.width, body.height, body.depth, body.cornerRadius);
  tris += bodyMesh.indices.length / 3;
  scene.addChild(addMesh(doc, buffer, 'body', bodyMesh, body.color));

  const numbers = new Map(device.parts.map((p) => [p.id, p.number]));
  const hotspots = [];
  for (const lp of layoutParts) {
    const f = FACES[lp.face];
    const origin = f.n.map((c, i) => c * faceCentreOffset(body, lp.face) + f.u[i] * lp.position[0] + f.v[i] * lp.position[1]);
    const mesh = transform(partMesh(lp.type, lp.size), origin, f.u, f.v, f.n);
    tris += mesh.indices.length / 3;
    scene.addChild(addMesh(doc, buffer, lp.id, mesh, lp.color ?? DEFAULT_COLOR[lp.type]));
    const d = lp.size[2];
    hotspots.push({
      partId: lp.id,
      number: numbers.get(lp.id),
      position: origin.map((c, i) => r6(c + f.n[i] * (d + HOTSPOT_OFFSET))),
      normal: f.n.slice(),
    });
  }
  hotspots.sort((a, b) => a.number - b.number);
  const glb = await new NodeIO().writeBinary(doc);
  return { glb, hotspots, tris };
}

async function main() {
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });
  const index = JSON.parse(fs.readFileSync(path.join(contentDir, 'index.json'), 'utf8'));
  let failed = false;
  for (const entry of index.devices) {
    const device = JSON.parse(fs.readFileSync(path.join(contentDir, `${entry.id}.json`), 'utf8'));
    const { glb, hotspots, tris } = await build(device);
    fs.writeFileSync(path.join(outDir, `${device.id}.glb`), glb);
    fs.writeFileSync(path.join(outDir, `${device.id}.hotspots.json`), JSON.stringify({ deviceId: device.id, hotspots }, null, 2) + '\n');
    const report = await validator.validateBytes(new Uint8Array(glb), { uri: `${device.id}.glb` });
    const { numErrors, numWarnings, numInfos, numHints } = report.issues;
    if (numErrors > 0) {
      failed = true;
      report.issues.messages.filter((m) => m.severity === 0).forEach((m) => console.error('  ERROR', m.code, m.message, m.pointer));
    }
    console.log(`${device.id}: ${tris} triangles, ${(glb.length / 1024).toFixed(1)} KB, validator errors=${numErrors} warnings=${numWarnings} infos=${numInfos} hints=${numHints}`);
    if (tris >= 20000 || glb.length >= 500 * 1024) { failed = true; console.error('  budget exceeded (<20k tris, <500KB)'); }
  }
  if (failed) process.exit(1);
}

main().catch((e) => { console.error(e); process.exit(1); });
