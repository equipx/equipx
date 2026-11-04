import type { Device, Fault, Mission, Procedure, Quiz } from "./types";

/**
 * Single source of validation for the admin panel. Mirrors content/schema/*.json and
 * tools/content/validate.mjs (schema + cross-reference rules). Keep in sync with those.
 */

export const ID_RE = /^[a-z][a-z0-9]*(_[a-z0-9]+)*$/;
export const COLOR_RE = /^#[0-9A-Fa-f]{6}$/;
/** Ids that collide with fixed routes (/equipment/new, /missions/schema ...). */
export const RESERVED_IDS: readonly string[] = ["new", "index", "missions", "schema"];

const PART_TYPES = ["screen", "knob", "button", "switch", "port", "led", "display", "probe", "slot", "handle"];
const FACES = ["front", "top", "left", "right", "back"];
const TASK_TYPES = ["scan", "identify_part", "complete_procedure", "pass_quiz"];

/** snake_case slug that always matches ID_RE, or "" when nothing usable remains. */
export function toSnake(s: string): string {
  return s
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^[^a-z]+/, "")
    .replace(/_+$/g, "");
}

export function slugify(s: string): string {
  return toSnake(s) || "item";
}

export function uniqueId(base: string, taken: Iterable<string>): string {
  const set = new Set(taken);
  if (!set.has(base)) return base;
  let n = 2;
  while (set.has(`${base}_${n}`)) n++;
  return `${base}_${n}`;
}

/** Id for a new procedure/fault/quiz/mission: schema-valid, unique in `taken`, never a reserved route id. */
export function newItemId(title: string, taken: Iterable<string>): string {
  return uniqueId(slugify(title), [...taken, ...RESERVED_IDS]);
}

const dup = (arr: string[]) => [...new Set(arr.filter((x, i) => arr.indexOf(x) !== i))];
const blank = (s: unknown) => typeof s !== "string" || !s.trim();
const idErr = (what: string, id: string): string | null =>
  !ID_RE.test(id)
    ? `${what} "${id}" must be snake_case (start with a letter; a-z, 0-9, single _).`
    : null;

function routeId(id: string): string | null {
  return RESERVED_IDS.includes(id) ? `Id "${id}" is reserved. Choose another.` : null;
}

export function validateProcedure(p: Procedure, device: Device): string[] {
  const e: string[] = [];
  const ie = idErr("Id", p.id) ?? routeId(p.id);
  if (ie) e.push(ie);
  if (blank(p.title)) e.push("Title is required.");
  if (p.steps.length < 1) e.push("Add at least one step.");
  const parts = new Set(device.parts.map((x) => x.id));
  p.steps.forEach((s, i) => {
    if (blank(s.text)) e.push(`Step ${i + 1}: text is required.`);
    if (!parts.has(s.partId)) e.push(`Step ${i + 1}: choose a part of this device.`);
  });
  return e;
}

export function validateFault(f: Fault, device: Device): string[] {
  const e: string[] = [];
  const ie = idErr("Id", f.id) ?? routeId(f.id);
  if (ie) e.push(ie);
  if (blank(f.symptom)) e.push("Symptom is required.");
  if (blank(f.explanation)) e.push("Explanation is required.");
  if (f.options.length < 2) e.push("Add at least two options.");
  f.options.forEach((o, i) => {
    const oe = idErr(`Option ${i + 1}: id`, o.id);
    if (oe) e.push(oe);
    if (blank(o.text)) e.push(`Option ${i + 1}: text is required.`);
  });
  dup(f.options.map((o) => o.id)).forEach((d) => e.push(`Duplicate option id "${d}".`));
  if (!f.options.some((o) => o.id === f.correctOptionId)) e.push("Select the correct option.");
  const parts = new Set(device.parts.map((x) => x.id));
  f.relatedPartIds.forEach((id) => {
    if (!parts.has(id)) e.push(`Related part "${id}" does not exist on this device.`);
  });
  return e;
}

export function validateQuiz(q: Quiz): string[] {
  const e: string[] = [];
  const ie = idErr("Id", q.id) ?? routeId(q.id);
  if (ie) e.push(ie);
  if (blank(q.title)) e.push("Title is required.");
  if (q.timeLimitSec !== null && (!Number.isInteger(q.timeLimitSec) || q.timeLimitSec < 1))
    e.push("Time limit must be a positive whole number of seconds (or empty).");
  if (q.questions.length < 1) e.push("Add at least one question.");
  dup(q.questions.map((x) => x.id)).forEach((d) => e.push(`Duplicate question id "${d}".`));
  q.questions.forEach((x, i) => {
    const n = `Question ${i + 1}`;
    const qe = idErr(`${n}: id`, x.id);
    if (qe) e.push(qe);
    if (blank(x.prompt)) e.push(`${n}: prompt is required.`);
    if (x.options.length < 2) e.push(`${n}: add at least two options.`);
    if (x.options.some((o) => blank(o))) e.push(`${n}: all options need text.`);
    if (!Number.isInteger(x.correctIndex) || x.correctIndex < 0 || x.correctIndex >= x.options.length)
      e.push(`${n}: correct answer is out of range.`);
    if (blank(x.explanation)) e.push(`${n}: explanation is required.`);
  });
  return e;
}

/**
 * Whole-device validation. `requireComplete: false` skips the "at least one" rules so a work-in-progress
 * device can still be saved locally; export always uses the default (full) check.
 */
export function validateDevice(d: Device, opts: { requireComplete?: boolean } = {}): string[] {
  const full = opts.requireComplete ?? true;
  const e: string[] = [];
  const ie = idErr("Device id", d.id);
  if (ie) e.push(ie);
  else if (RESERVED_IDS.includes(d.id)) e.push(`Device id "${d.id}" is reserved. Choose another.`);
  for (const k of ["name", "category", "summary", "overview"] as const) {
    if (blank(d[k])) e.push(`${k} must not be empty`);
  }
  if (d.model.asset !== `models/${d.id}.glb`) e.push(`model.asset should be models/${d.id}.glb`);
  if (d.model.hotspots !== `models/${d.id}.hotspots.json`) e.push(`model.hotspots should be models/${d.id}.hotspots.json`);
  if (typeof d.model.cameraOrbit !== "string") e.push("model.cameraOrbit must be a string");

  if (full) {
    if (d.parts.length < 1) e.push("Add at least one part (parts must not be empty)");
    if (d.layout.parts.length < 1) e.push("layout.parts must have at least one entry");
    if (d.safetyRules.length < 1) e.push("Add at least one safety rule");
  }
  if (d.layout.units !== "m") e.push('layout.units must be "m"');

  const partIds = d.parts.map((p) => p.id);
  const layoutIds = d.layout.parts.map((p) => p.id);
  for (const id of dup(partIds)) e.push(`duplicate part id ${id}`);
  for (const id of dup(layoutIds)) e.push(`duplicate layout part id ${id}`);
  for (const id of partIds) if (!layoutIds.includes(id)) e.push(`part ${id} has no layout entry`);
  for (const id of layoutIds) if (!partIds.includes(id)) e.push(`layout part ${id} has no parts entry`);
  for (const p of d.parts) {
    const pe = idErr("part id", p.id);
    if (pe) e.push(pe);
    if (!Number.isInteger(p.number) || p.number < 1) e.push(`part ${p.id}: number must be an integer >= 1`);
    if (blank(p.name)) e.push(`part ${p.id}: name must not be empty`);
    if (blank(p.function)) e.push(`part ${p.id}: function must not be empty`);
    if (blank(p.usage)) e.push(`part ${p.id}: usage must not be empty`);
    if (p.safety.some((s) => blank(s))) e.push(`part ${p.id}: safety tips must not be empty`);
  }
  const nums = d.parts.map((p) => p.number).sort((a, b) => a - b);
  if (nums.some((n, i) => n !== i + 1)) e.push(`part numbers must be unique and run 1..N (got ${nums.join(",")})`);
  for (const lp of d.layout.parts) {
    const le = idErr("layout part id", lp.id);
    if (le) e.push(le);
    if (!PART_TYPES.includes(lp.type)) e.push(`layout ${lp.id}: unknown type "${lp.type}"`);
    if (!FACES.includes(lp.face)) e.push(`layout ${lp.id}: unknown face "${lp.face}"`);
    if (lp.position.length !== 2 || lp.position.some((n) => !Number.isFinite(n))) e.push(`layout ${lp.id}: position must be two numbers`);
    if (lp.size.length !== 3 || lp.size.some((n) => !(n > 0))) e.push(`layout ${lp.id}: size values must be > 0`);
    if (lp.color !== undefined && !COLOR_RE.test(lp.color)) e.push(`layout ${lp.id}: color must be #RRGGBB`);
  }
  const b = d.layout.body;
  if (![b.width, b.height, b.depth].every((n) => n > 0)) e.push("layout body width/height/depth must be > 0");
  if (!(b.cornerRadius >= 0)) e.push("layout body cornerRadius must be >= 0");
  if (!COLOR_RE.test(b.color)) e.push("layout body color must be #RRGGBB");
  if (d.safetyRules.some((s) => blank(s))) e.push("safety rules must not be empty");

  for (const p of d.procedures) validateProcedure(p, d).forEach((m) => e.push(`procedure ${p.id}: ${m}`));
  for (const f of d.faults) validateFault(f, d).forEach((m) => e.push(`fault ${f.id}: ${m}`));
  for (const q of d.quizzes) validateQuiz(q).forEach((m) => e.push(`quiz ${q.id}: ${m}`));
  for (const id of dup([...d.procedures, ...d.faults, ...d.quizzes].map((x) => x.id)))
    e.push(`duplicate procedure/fault/quiz id ${id}`);
  return e;
}

export function validateMission(m: Mission, devices: Device[]): string[] {
  const e: string[] = [];
  const byId = new Map(devices.map((d) => [d.id, d]));
  const ie = idErr("Id", m.id) ?? routeId(m.id);
  if (ie) e.push(ie);
  if (blank(m.title)) e.push("Title is required.");
  if (blank(m.description)) e.push("Description is required.");
  if (m.deviceId !== undefined && m.deviceId !== "" && !byId.has(m.deviceId)) e.push(`Device "${m.deviceId}" does not exist.`);
  if (m.tasks.length < 1) e.push("Add at least one task.");
  dup(m.tasks.map((t) => t.id)).forEach((d) => e.push(`Duplicate task id "${d}".`));
  m.tasks.forEach((t, i) => {
    const n = `Task ${i + 1}`;
    const te = idErr(`${n}: id`, t.id);
    if (te) e.push(te);
    if (!TASK_TYPES.includes(t.type)) e.push(`${n}: unknown type "${t.type}".`);
    if (blank(t.title)) e.push(`${n}: title is required.`);
    const d = byId.get(t.deviceId);
    if (!d) {
      e.push(`${n}: choose a device.`);
      return;
    }
    if (t.type === "identify_part" && !d.parts.some((p) => p.id === t.partId)) e.push(`${n}: choose a part.`);
    if (t.type === "complete_procedure" && !d.procedures.some((p) => p.id === t.procedureId))
      e.push(`${n}: choose a procedure.`);
    if (t.type === "pass_quiz") {
      if (!d.quizzes.some((q) => q.id === t.quizId)) e.push(`${n}: choose a quiz.`);
      const s = t.minScorePct;
      if (s !== undefined && (!Number.isInteger(s) || s < 1 || s > 100))
        e.push(`${n}: min score must be a whole number from 1 to 100 (leave empty for 70).`);
    }
  });
  return e;
}

/** Validates the whole missions.json payload (what Export writes). Messages are prefixed with the mission. */
export function validateMissions(missions: Mission[], devices: Device[]): string[] {
  const e: string[] = [];
  if (missions.length < 1) e.push("missions.json needs at least one mission.");
  dup(missions.map((m) => m.id)).forEach((id) => e.push(`Duplicate mission id "${id}".`));
  for (const m of missions) validateMission(m, devices).forEach((msg) => e.push(`Mission "${m.title || m.id}": ${msg}`));
  return e;
}

/** Titles of missions whose tasks (or scope) reference the given thing. */
export function missionsReferencing(
  missions: Mission[],
  ref: { deviceId: string; partId?: string; procedureId?: string; quizId?: string },
): Mission[] {
  return missions.filter((m) => {
    if (ref.partId === undefined && ref.procedureId === undefined && ref.quizId === undefined && m.deviceId === ref.deviceId)
      return true;
    return m.tasks.some(
      (t) =>
        t.deviceId === ref.deviceId &&
        ((ref.partId !== undefined && t.type === "identify_part" && t.partId === ref.partId) ||
          (ref.procedureId !== undefined && t.type === "complete_procedure" && t.procedureId === ref.procedureId) ||
          (ref.quizId !== undefined && t.type === "pass_quiz" && t.quizId === ref.quizId) ||
          (ref.partId === undefined && ref.procedureId === undefined && ref.quizId === undefined)),
    );
  });
}
