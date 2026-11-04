import type {
  BadgeId,
  FaultAttempt,
  LedgerEntry,
  MissionCompletion,
  PartView,
  ProcedureCompletion,
  QuizAttempt,
  Scan,
  StudentPoints,
} from "./types";

export interface ActivityData {
  quizAttempts: QuizAttempt[];
  faultAttempts: FaultAttempt[];
  scans: Scan[];
  partViews: PartView[];
  procedures: ProcedureCompletion[];
  missions: MissionCompletion[];
}

const byTime = <T extends { at: string }>(a: T, b: T) => a.at.localeCompare(b.at);

/**
 * Points ledger using docs/CONTRACT.md rules: scan +10 (once per device per UTC day), first view of
 * a part +2, quiz question correct +5 (first time per question), fault fixed +20 (first correct
 * diagnosis per scenario), mission complete +50 (once).
 */
export function buildLedger(d: ActivityData): LedgerEntry[] {
  const out: LedgerEntry[] = [];
  const seen = new Set<string>();
  const once = (key: string) => (seen.has(key) ? false : (seen.add(key), true));

  for (const s of [...d.scans].sort(byTime)) {
    if (once(`scan|${s.studentId}|${s.deviceId}|${s.at.slice(0, 10)}`))
      out.push({ studentId: s.studentId, deviceId: s.deviceId, at: s.at, amount: 10, reason: "scan" });
  }
  for (const p of [...d.partViews].sort(byTime)) {
    if (once(`part|${p.studentId}|${p.deviceId}|${p.partId}`))
      out.push({ studentId: p.studentId, deviceId: p.deviceId, at: p.at, amount: 2, reason: "part" });
  }
  for (const a of [...d.quizAttempts].sort(byTime)) {
    a.answers.forEach((ok, i) => {
      if (ok && once(`q|${a.studentId}|${a.quizId}|${i}`))
        out.push({ studentId: a.studentId, deviceId: a.deviceId, at: a.at, amount: 5, reason: "quiz" });
    });
  }
  for (const f of [...d.faultAttempts].sort(byTime)) {
    if (f.correct && once(`f|${f.studentId}|${f.faultId}`))
      out.push({ studentId: f.studentId, deviceId: f.deviceId, at: f.at, amount: 20, reason: "fault" });
  }
  for (const m of [...d.missions].sort(byTime)) {
    if (once(`m|${m.studentId}|${m.missionId}`))
      out.push({ studentId: m.studentId, deviceId: m.deviceId, at: m.at, amount: 50, reason: "mission" });
  }
  return out;
}

export const BADGES: { id: BadgeId; label: string; description: string }[] = [
  { id: "first_scan", label: "First scan", description: "Scanned a device" },
  { id: "part_spotter", label: "Part spotter", description: "Viewed 10 distinct parts" },
  { id: "safety_first", label: "Safety first", description: "Completed a procedure" },
  { id: "quiz_ace", label: "Quiz ace", description: "100% on a quiz" },
  { id: "fault_finder", label: "Fault finder", description: "Fixed 3 distinct faults" },
  { id: "mission_complete", label: "Mission complete", description: "Completed a mission" },
];

export function computeBadges(studentId: string, d: ActivityData): BadgeId[] {
  const mine = <T extends { studentId: string }>(xs: T[]) => xs.filter((x) => x.studentId === studentId);
  const badges: BadgeId[] = [];
  if (mine(d.scans).length >= 1) badges.push("first_scan");
  const parts = new Set(mine(d.partViews).map((p) => `${p.deviceId}|${p.partId}`));
  if (parts.size >= 10) badges.push("part_spotter");
  if (mine(d.procedures).length >= 1) badges.push("safety_first");
  if (mine(d.quizAttempts).some((a) => a.answers.length > 0 && a.answers.every(Boolean)))
    badges.push("quiz_ace");
  const faults = new Set(mine(d.faultAttempts).filter((f) => f.correct).map((f) => f.faultId));
  if (faults.size >= 3) badges.push("fault_finder");
  if (mine(d.missions).length >= 1) badges.push("mission_complete");
  return badges;
}

export function summarizePoints(
  studentIds: string[],
  ledger: LedgerEntry[],
  d: ActivityData,
): Record<string, StudentPoints> {
  const out: Record<string, StudentPoints> = {};
  for (const id of studentIds) out[id] = { total: 0, byDevice: {}, badges: computeBadges(id, d) };
  for (const l of ledger) {
    const p = out[l.studentId];
    if (!p) continue;
    p.total += l.amount;
    p.byDevice[l.deviceId] = (p.byDevice[l.deviceId] ?? 0) + l.amount;
  }
  return out;
}
