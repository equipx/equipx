// Types matching docs/CONTRACT.md (content/<deviceId>.json).

export interface DeviceIndexEntry {
  id: string;
  name: string;
  category: string;
  summary: string;
}

export interface DeviceIndex {
  devices: DeviceIndexEntry[];
}

export interface ModelRef {
  asset: string;
  hotspots: string;
  cameraOrbit: string;
  credit: string | null;
}

export type PartType =
  | "screen"
  | "knob"
  | "button"
  | "switch"
  | "port"
  | "led"
  | "display"
  | "probe"
  | "slot"
  | "handle";

export type LayoutFace = "front" | "top" | "left" | "right" | "back";

export interface LayoutBody {
  width: number;
  height: number;
  depth: number;
  cornerRadius: number;
  color: string;
}

export interface LayoutPart {
  id: string;
  type: PartType;
  face: LayoutFace;
  position: [number, number];
  size: [number, number, number];
  color?: string;
}

export interface Layout {
  units: string;
  body: LayoutBody;
  parts: LayoutPart[];
}

export interface Part {
  id: string;
  number: number;
  name: string;
  function: string;
  usage: string;
  safety: string[];
}

export interface ProcedureStep {
  text: string;
  partId: string;
}

export interface Procedure {
  id: string;
  title: string;
  steps: ProcedureStep[];
}

export interface FaultOption {
  id: string;
  text: string;
}

export interface Fault {
  id: string;
  symptom: string;
  options: FaultOption[];
  correctOptionId: string;
  explanation: string;
  relatedPartIds: string[];
}

export interface QuizQuestion {
  id: string;
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

export interface Quiz {
  id: string;
  title: string;
  timeLimitSec: number | null;
  questions: QuizQuestion[];
}

export interface Device extends DeviceIndexEntry {
  overview: string;
  model: ModelRef;
  layout: Layout;
  parts: Part[];
  safetyRules: string[];
  procedures: Procedure[];
  faults: Fault[];
  quizzes: Quiz[];
}

export interface Hotspot {
  partId: string;
  number: number;
  position: [number, number, number];
  normal: [number, number, number];
}

export interface HotspotsFile {
  deviceId: string;
  hotspots: Hotspot[];
}

export type MissionTaskType = "scan" | "identify_part" | "complete_procedure" | "pass_quiz";

export interface MissionTask {
  id: string;
  type: MissionTaskType;
  title: string;
  deviceId: string;
  partId?: string;
  procedureId?: string;
  quizId?: string;
  /** pass_quiz only; defaults to 70 */
  minScorePct?: number;
}

export interface Mission {
  id: string;
  title: string;
  description: string;
  /** Absent for cross-device missions. */
  deviceId?: string;
  tasks: MissionTask[];
}

export interface MissionsFile {
  missions: Mission[];
}
