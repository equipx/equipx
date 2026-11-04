import { buildLedger, type ActivityData } from "./points";
import { DAY_MS, NOW_MS } from "./mock-store";
import type { BadgeId, DeviceInfo, Student, StudentPoints } from "./types";

export interface ResultsData extends ActivityData {
  devices: DeviceInfo[];
  students: Student[];
  points: Record<string, StudentPoints>;
}

export interface Filters {
  /** "all" or a class group. */
  classGroup: string;
  /** "all" or a device id. */
  deviceId: string;
  /** Inclusive YYYY-MM-DD (UTC), or "". */
  from: string;
  to: string;
}

export const DEFAULT_FILTERS: Filters = { classGroup: "all", deviceId: "all", from: "", to: "" };

export interface StudentRow {
  id: string;
  name: string;
  classGroup: string;
  college: string;
  quizAttempts: number;
  accuracy: number | null;
  avgSec: number | null;
  points: number;
  badges: BadgeId[];
  lastActive: string | null;
}

export interface DeviceRow {
  deviceId: string;
  name: string;
  students: number;
  quizAttempts: number;
  accuracy: number | null;
  avgSec: number | null;
}

export interface Summary {
  students: number;
  activeStudents: number;
  quizAttempts: number;
  accuracy: number | null;
  avgSec: number | null;
  totalPoints: number;
}

export interface WeekBucket { label: string; attempts: number }

const fmtDay = (ms: number) => {
  const d = new Date(ms);
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}`;
};
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function formatDate(iso: string): string {
  const d = new Date(iso);
  const h = String(d.getUTCHours()).padStart(2, "0");
  const m = String(d.getUTCMinutes()).padStart(2, "0");
  return `${fmtDay(d.getTime())}, ${h}:${m} UTC`;
}

export function formatDuration(sec: number | null): string {
  if (sec == null) return "-";
  const total = Math.round(sec);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m ? `${m}m ${String(s).padStart(2, "0")}s` : `${s}s`;
}

/** Relative to the fixed "now" so output is deterministic. */
export function relativeTime(iso: string): string {
  const diff = NOW_MS - Date.parse(iso);
  const h = Math.floor(diff / 3_600_000);
  if (h < 1) return "just now";
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return d === 1 ? "yesterday" : `${d}d ago`;
}

export function inRange(iso: string, f: Pick<Filters, "from" | "to">): boolean {
  const day = iso.slice(0, 10);
  if (f.from && day < f.from) return false;
  if (f.to && day > f.to) return false;
  return true;
}

const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : null);
const round1 = (n: number | null) => (n == null ? null : Math.round(n * 10) / 10);

/** Weeks are 7-day windows ending at the fixed "now"; index 0 is the oldest. */
export function weekBuckets(): { label: string; start: number; end: number }[] {
  return [3, 2, 1, 0].map((w) => {
    const end = NOW_MS - w * 7 * DAY_MS;
    const start = end - 7 * DAY_MS;
    return { label: `${fmtDay(start)} - ${fmtDay(end - 1)}`, start, end };
  });
}

export function applyFilters(data: ResultsData, f: Filters) {
  const students = data.students.filter((s) => f.classGroup === "all" || s.classGroup === f.classGroup);
  const ids = new Set(students.map((s) => s.id));
  const keep = <T extends { studentId: string; deviceId: string; at: string }>(xs: T[]) =>
    xs.filter(
      (x) => ids.has(x.studentId) && (f.deviceId === "all" || x.deviceId === f.deviceId) && inRange(x.at, f),
    );
  return {
    students,
    quizAttempts: keep(data.quizAttempts),
    faultAttempts: keep(data.faultAttempts),
    scans: keep(data.scans),
    partViews: keep(data.partViews),
    procedures: keep(data.procedures),
    missions: keep(data.missions),
    ledger: keep(buildLedger(data)),
  };
}

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0);
const acc = (attempts: { answers: boolean[] }[]) => {
  const total = sum(attempts.map((a) => a.answers.length));
  return total ? round1((100 * sum(attempts.map((a) => a.answers.filter(Boolean).length))) / total) : null;
};

export function compute(data: ResultsData, f: Filters) {
  const v = applyFilters(data, f);
  const students: StudentRow[] = v.students.map((s) => {
    const attempts = v.quizAttempts.filter((a) => a.studentId === s.id);
    const times = [
      ...attempts, ...v.faultAttempts, ...v.scans, ...v.procedures, ...v.missions,
    ].filter((x) => x.studentId === s.id).map((x) => x.at);
    return {
      id: s.id,
      name: s.displayName,
      classGroup: s.classGroup,
      college: s.college,
      quizAttempts: attempts.length,
      accuracy: acc(attempts),
      avgSec: round1(avg(attempts.map((a) => a.durationSec))),
      points: sum(v.ledger.filter((l) => l.studentId === s.id).map((l) => l.amount)),
      badges: data.points[s.id]?.badges ?? [],
      lastActive: times.length ? times.reduce((a, b) => (a > b ? a : b)) : null,
    };
  });

  const devices: DeviceRow[] = data.devices
    .filter((d) => f.deviceId === "all" || d.id === f.deviceId)
    .map((d) => {
      const at = v.quizAttempts.filter((a) => a.deviceId === d.id);
      return {
        deviceId: d.id,
        name: d.name,
        students: new Set(at.map((a) => a.studentId)).size,
        quizAttempts: at.length,
        accuracy: acc(at),
        avgSec: round1(avg(at.map((a) => a.durationSec))),
      };
    });

  const weekly: WeekBucket[] = weekBuckets().map((w) => ({
    label: w.label,
    attempts: v.quizAttempts.filter((a) => {
      const t = Date.parse(a.at);
      return t >= w.start && t < w.end;
    }).length,
  }));

  const summary: Summary = {
    students: students.length,
    activeStudents: students.filter((s) => s.lastActive).length,
    quizAttempts: v.quizAttempts.length,
    accuracy: acc(v.quizAttempts),
    avgSec: round1(avg(v.quizAttempts.map((a) => a.durationSec))),
    totalPoints: sum(students.map((s) => s.points)),
  };
  return { students, devices, weekly, summary };
}

export interface DeviceBreakdown {
  deviceId: string;
  name: string;
  attempts: number;
  accuracy: number | null;
  avgSec: number | null;
  points: number;
}

export function studentDetail(data: ResultsData, studentId: string, f: Filters) {
  const v = applyFilters(data, { ...f, classGroup: "all" });
  const attempts = v.quizAttempts.filter((a) => a.studentId === studentId);
  const names = new Map(data.devices.map((d) => [d.id, d.name]));
  const perDevice: DeviceBreakdown[] = data.devices
    .map((d) => {
      const at = attempts.filter((a) => a.deviceId === d.id);
      return {
        deviceId: d.id,
        name: d.name,
        attempts: at.length,
        accuracy: acc(at),
        avgSec: round1(avg(at.map((a) => a.durationSec))),
        points: sum(v.ledger.filter((l) => l.studentId === studentId && l.deviceId === d.id).map((l) => l.amount)),
      };
    })
    .filter((d) => d.attempts > 0 || d.points > 0);
  const history = [...attempts]
    .sort((a, b) => b.at.localeCompare(a.at))
    .map((a) => ({
      id: a.id,
      at: a.at,
      device: names.get(a.deviceId) ?? a.deviceId,
      quizId: a.quizId,
      correct: a.answers.filter(Boolean).length,
      total: a.answers.length,
      durationSec: a.durationSec,
    }));
  return { perDevice, history };
}
