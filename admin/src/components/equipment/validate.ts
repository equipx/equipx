import type { Device } from "@/lib/data";

export { ID_RE, RESERVED_IDS, toSnake, validateDevice } from "@/lib/data/validate";

/** Device JSON in the /content shape (no admin-only fields, canonical key order). */
export function toContentJson(d: Device): string {
  const out = {
    id: d.id,
    name: d.name,
    category: d.category,
    summary: d.summary,
    overview: d.overview,
    model: d.model,
    layout: d.layout,
    parts: d.parts,
    safetyRules: d.safetyRules,
    procedures: d.procedures,
    faults: d.faults,
    quizzes: d.quizzes,
  };
  return JSON.stringify(out, null, 2) + "\n";
}

export function downloadText(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function newDevice(id: string, name: string, category: string, summary: string): Device {
  return {
    id,
    name,
    category,
    summary,
    overview: summary,
    model: {
      asset: `models/${id}.glb`,
      hotspots: `models/${id}.hotspots.json`,
      cameraOrbit: "0deg 75deg 0.45m",
      credit: null,
    },
    layout: {
      units: "m",
      body: { width: 0.09, height: 0.18, depth: 0.045, cornerRadius: 0.008, color: "#D9D7D2" },
      parts: [],
    },
    parts: [],
    safetyRules: [],
    procedures: [],
    faults: [],
    quizzes: [],
  };
}
