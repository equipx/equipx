// Tiny mesh builders. All meshes: { positions:number[], normals:number[], indices:number[] }.
// Winding is counter-clockwise when seen from outside (glTF convention).

export function newMesh() { return { positions: [], normals: [], indices: [] }; }

function addQuad(m, p0, p1, p2, p3, n) {
  const base = m.positions.length / 3;
  for (const p of [p0, p1, p2, p3]) { m.positions.push(...p); m.normals.push(...n); }
  m.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
}

const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (v) => { const l = Math.hypot(...v) || 1; return [v[0] / l, v[1] / l, v[2] / l]; };

/** Axis-aligned box centred on origin with half extents [hx,hy,hz]. */
export function box(hx, hy, hz) {
  const m = newMesh();
  const c = (sx, sy, sz) => [sx * hx, sy * hy, sz * hz];
  addQuad(m, c(1, -1, 1), c(1, -1, -1), c(1, 1, -1), c(1, 1, 1), [1, 0, 0]);
  addQuad(m, c(-1, -1, -1), c(-1, -1, 1), c(-1, 1, 1), c(-1, 1, -1), [-1, 0, 0]);
  addQuad(m, c(-1, 1, 1), c(1, 1, 1), c(1, 1, -1), c(-1, 1, -1), [0, 1, 0]);
  addQuad(m, c(-1, -1, -1), c(1, -1, -1), c(1, -1, 1), c(-1, -1, 1), [0, -1, 0]);
  addQuad(m, c(-1, -1, 1), c(1, -1, 1), c(1, 1, 1), c(-1, 1, 1), [0, 0, 1]);
  addQuad(m, c(1, -1, -1), c(-1, -1, -1), c(-1, 1, -1), c(1, 1, -1), [0, 0, -1]);
  return m;
}

/**
 * Extrude a convex 2D outline (CCW, in XY) along Z from z0 to z1, smooth side normals.
 * `outline` is an array of [x,y]; `sideNormals` are the outward 2D normals per point.
 */
export function extrudeConvex(outline, sideNormals, z0, z1) {
  const m = newMesh();
  const n = outline.length;
  // Sides
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    const a = outline[i], b = outline[j];
    const na = sideNormals[i], nb = sideNormals[j];
    const base = m.positions.length / 3;
    m.positions.push(a[0], a[1], z0, b[0], b[1], z0, b[0], b[1], z1, a[0], a[1], z1);
    m.normals.push(na[0], na[1], 0, nb[0], nb[1], 0, nb[0], nb[1], 0, na[0], na[1], 0);
    m.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
  // Caps (fan around centroid)
  const cx = outline.reduce((s, p) => s + p[0], 0) / n;
  const cy = outline.reduce((s, p) => s + p[1], 0) / n;
  for (const [z, nz] of [[z1, 1], [z0, -1]]) {
    const base = m.positions.length / 3;
    m.positions.push(cx, cy, z); m.normals.push(0, 0, nz);
    for (const p of outline) { m.positions.push(p[0], p[1], z); m.normals.push(0, 0, nz); }
    for (let i = 0; i < n; i++) {
      const a = base + 1 + i, b = base + 1 + ((i + 1) % n);
      if (nz > 0) m.indices.push(base, a, b); else m.indices.push(base, b, a);
    }
  }
  return m;
}

/** Rounded rectangle prism: width (x) x height (y) x depth (z), corner radius r on the 4 edges parallel to Z. */
export function roundedBox(w, h, d, r, segs = 5) {
  r = Math.min(r, w / 2, h / 2);
  if (r <= 0) return box(w / 2, h / 2, d / 2);
  const outline = [], normals = [];
  const corners = [[w / 2 - r, h / 2 - r, 0], [-(w / 2 - r), h / 2 - r, 90], [-(w / 2 - r), -(h / 2 - r), 180], [w / 2 - r, -(h / 2 - r), 270]];
  for (const [cx, cy, a0] of corners) {
    for (let s = 0; s <= segs; s++) {
      const a = ((a0 + (90 * s) / segs) * Math.PI) / 180;
      const nx = Math.cos(a), ny = Math.sin(a);
      outline.push([cx + r * nx, cy + r * ny]);
      normals.push([nx, ny]);
    }
  }
  return extrudeConvex(outline, normals, -d / 2, d / 2);
}

/** Cylinder along +Z from z0 to z1 with radius r. */
export function cylinder(r, z0, z1, segs = 24) {
  const outline = [], normals = [];
  for (let i = 0; i < segs; i++) {
    const a = (i / segs) * Math.PI * 2;
    outline.push([r * Math.cos(a), r * Math.sin(a)]);
    normals.push([Math.cos(a), Math.sin(a)]);
  }
  return extrudeConvex(outline, normals, z0, z1);
}

/** Apply an affine transform given an orthonormal basis (u,v,n) and origin to a local mesh. */
export function transform(m, origin, u, v, n) {
  const out = newMesh();
  for (let i = 0; i < m.positions.length; i += 3) {
    const [x, y, z] = [m.positions[i], m.positions[i + 1], m.positions[i + 2]];
    out.positions.push(origin[0] + u[0] * x + v[0] * y + n[0] * z, origin[1] + u[1] * x + v[1] * y + n[1] * z, origin[2] + u[2] * x + v[2] * y + n[2] * z);
    const [a, b, c] = [m.normals[i], m.normals[i + 1], m.normals[i + 2]];
    out.normals.push(u[0] * a + v[0] * b + n[0] * c, u[1] * a + v[1] * b + n[1] * c, u[2] * a + v[2] * b + n[2] * c);
  }
  out.indices = m.indices.slice();
  return out;
}

export { sub, cross, norm };
