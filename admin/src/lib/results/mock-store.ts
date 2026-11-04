import seedIndex from "@/data/seed/index.json";
import seedMissions from "@/data/seed/missions.json";
import { buildLedger, summarizePoints, type ActivityData } from "./points";
import type {
  DeviceInfo,
  FaultAttempt,
  MissionCompletion,
  PartView,
  ProcedureCompletion,
  QuizAttempt,
  ResultsStore,
  Scan,
  Student,
  StudentPoints,
  StudentResultRow,
} from "./types";

/** Fixed "now" so generated data and relative dates are identical on server and client. */
export const NOW_ISO = "2026-11-05T12:00:00.000Z";
export const NOW_MS = Date.parse(NOW_ISO);
export const DAY_MS = 86_400_000;
const WINDOW_DAYS = 28;

const FIRST = [
  "Aarav", "Vivaan", "Aditya", "Arjun", "Rohan", "Karan", "Siddharth", "Ishaan", "Kabir", "Nikhil",
  "Priya", "Ananya", "Diya", "Sneha", "Pooja", "Neha", "Riya", "Kavya", "Meera", "Isha",
  "Tanvi", "Shreya", "Aditi", "Rahul", "Omkar", "Sahil", "Prathamesh", "Harsh", "Divya", "Swati",
];
const LAST = [
  "Patil", "Sharma", "Deshmukh", "Kulkarni", "Iyer", "Reddy", "Nair", "Gupta", "Joshi", "Mehta",
  "Singh", "Verma", "Pawar", "More", "Jadhav", "Shinde", "Rao", "Bhat", "Chopra", "Menon",
];
const COLLEGES = [
  "Government College of Engineering, Pune",
  "VJTI Mumbai",
  "College of Engineering, Pune",
  "PICT Pune",
  "Walchand College of Engineering, Sangli",
  "KJ Somaiya College of Engineering",
];
const GROUPS = ["SE Electronics A", "SE Electronics B", "TE Electrical"];

interface QuizMeta { id: string; deviceId: string; questions: number }
interface DeviceMeta {
  id: string;
  name: string;
  partIds: string[];
  quizzes: QuizMeta[];
  faultIds: string[];
  procedureIds: string[];
}

interface SeedDevice {
  id: string;
  name: string;
  parts: { id: string }[];
  quizzes: { id: string; questions: unknown[] }[];
  faults: { id: string }[];
  procedures: { id: string }[];
}

async function loadDeviceMeta(): Promise<DeviceMeta[]> {
  const entries = (seedIndex as { devices: DeviceInfo[] }).devices;
  const metas: DeviceMeta[] = [];
  for (const e of entries) {
    const mod = await import(`@/data/seed/${e.id}.json`);
    const d = (mod.default ?? mod) as SeedDevice;
    metas.push({
      id: d.id,
      name: d.name,
      partIds: d.parts.map((p) => p.id),
      quizzes: d.quizzes.map((q) => ({ id: q.id, deviceId: d.id, questions: q.questions.length })),
      faultIds: d.faults.map((f) => f.id),
      procedureIds: d.procedures.map((p) => p.id),
    });
  }
  return metas;
}

/** mulberry32 seeded PRNG. */
function prng(seed: number) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    int: (lo: number, hi: number) => lo + Math.floor(next() * (hi - lo + 1)),
    pick: <T,>(xs: T[]) => xs[Math.floor(next() * xs.length)],
    chance: (p: number) => next() < p,
  };
}

/** Seeded Fisher-Yates shuffle; returns a new array. */
function shuffle<T>(xs: readonly T[], r: { int: (lo: number, hi: number) => number }): T[] {
  const a = [...xs];
  for (let i = a.length - 1; i > 0; i--) {
    const j = r.int(0, i);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

interface Dataset extends ActivityData {
  devices: DeviceInfo[];
  students: Student[];
  points: Record<string, StudentPoints>;
}

function generate(metas: DeviceMeta[]): Dataset {
  const r = prng(20261105);
  const students: Student[] = [];
  const data: ActivityData = {
    quizAttempts: [], faultAttempts: [], scans: [], partViews: [], procedures: [], missions: [],
  };
  const missions = (seedMissions as { missions: { id: string; deviceId: string }[] }).missions;
  let seq = 0;
  const id = (p: string) => `${p}_${String(++seq).padStart(5, "0")}`;

  /** Random moment in the last 28 days, between 09:00 and 17:30 IST (03:30-12:00 UTC). */
  const moment = (dayOffset: number) => {
    const day = Math.floor(NOW_MS / DAY_MS) - dayOffset;
    let ms = day * DAY_MS + (3.5 + r.next() * 8.5) * 3_600_000;
    if (ms > NOW_MS) ms = NOW_MS - r.int(1, 90) * 60_000;
    return new Date(Math.round(ms / 1000) * 1000).toISOString();
  };

  for (let i = 0; i < 30; i++) {
    const first = FIRST[i];
    const last = LAST[(i * 7 + 3) % LAST.length];
    const group = GROUPS[i % 3];
    const student: Student = {
      id: `stu_${String(i + 1).padStart(3, "0")}`,
      displayName: `${first} ${last}`,
      classGroup: group,
      college: COLLEGES[(i * 5 + 1) % COLLEGES.length],
    };
    students.push(student);

    // Skill spread roughly 0.45..0.95, golden-ratio shuffled so groups are mixed.
    const skill = 0.45 + 0.5 * ((i * 0.6180339887) % 1);
    // A few students have gone quiet (last active days ago); the rest are active recently.
    const quiet = i % 7 === 3;
    const newestDay = quiet ? r.int(8, 16) : r.int(0, 3);
    const nDevices = r.int(2, Math.min(8, metas.length));
    const myDevices = shuffle(metas, r).slice(0, nDevices);

    myDevices.forEach((dev, di) => {
      const sessions = r.int(1, 3);
      const diff = (dev.id.length % 5) * 0.025; // small stable per-device difficulty
      for (let s = 0; s < sessions; s++) {
        const dayOffset = Math.min(WINDOW_DAYS - 1, newestDay + r.int(0, 24 - di) + s);
        const at = moment(dayOffset);
        data.scans.push({ id: id("scan"), studentId: student.id, deviceId: dev.id, at });
        const nParts = r.int(2, 6);
        for (const pid of shuffle(dev.partIds, r).slice(0, nParts))
          data.partViews.push({ id: id("pv"), studentId: student.id, deviceId: dev.id, partId: pid, at });
      }
      // Quiz attempts: improving over time.
      const attempts = r.int(1, 3);
      for (let k = 0; k < attempts; k++) {
        const quiz = r.pick(dev.quizzes);
        const p = Math.min(0.98, Math.max(0.2, skill - diff + 0.04 * k + (r.next() - 0.5) * 0.18));
        const answers = Array.from({ length: quiz.questions }, () => r.chance(p));
        const perQ = (20 + r.next() * 25) * (1.35 - skill * 0.6);
        const dayOffset = Math.min(WINDOW_DAYS - 1, newestDay + r.int(0, 24) - k);
        data.quizAttempts.push({
          id: id("qa"),
          studentId: student.id,
          deviceId: dev.id,
          quizId: quiz.id,
          at: moment(Math.max(0, dayOffset)),
          durationSec: Math.round(quiz.questions * perQ),
          answers,
        });
      }
      for (const fid of dev.faultIds) {
        if (!r.chance(0.45)) continue;
        const tries = r.int(1, 2);
        for (let t = 0; t < tries; t++)
          data.faultAttempts.push({
            id: id("fa"),
            studentId: student.id,
            deviceId: dev.id,
            faultId: fid,
            at: moment(Math.min(WINDOW_DAYS - 1, newestDay + r.int(0, 20))),
            correct: r.chance(skill - 0.05 + t * 0.2),
          });
      }
      for (const pid of dev.procedureIds)
        if (r.chance(0.4))
          data.procedures.push({
            id: id("pc"),
            studentId: student.id,
            deviceId: dev.id,
            procedureId: pid,
            at: moment(Math.min(WINDOW_DAYS - 1, newestDay + r.int(0, 20))),
          });
      for (const m of missions)
        if (m.deviceId === dev.id && r.chance(skill * 0.5))
          data.missions.push({
            id: id("mc"),
            studentId: student.id,
            deviceId: dev.id,
            missionId: m.id,
            at: moment(Math.min(WINDOW_DAYS - 1, newestDay + r.int(0, 20))),
          });
    });
  }

  const ledger = buildLedger(data);
  const points = summarizePoints(students.map((s) => s.id), ledger, data);
  return { ...data, devices: metas.map((m) => ({ id: m.id, name: m.name })), students, points };
}

export class MockResultsStore implements ResultsStore {
  private cache: Promise<Dataset> | null = null;
  private data() {
    return (this.cache ??= loadDeviceMeta().then(generate));
  }
  async listDevices() { return (await this.data()).devices; }
  async listStudents() { return (await this.data()).students; }
  async listQuizAttempts(): Promise<QuizAttempt[]> { return (await this.data()).quizAttempts; }
  async listFaultAttempts(): Promise<FaultAttempt[]> { return (await this.data()).faultAttempts; }
  async listScans(): Promise<Scan[]> { return (await this.data()).scans; }
  async listPartViews(): Promise<PartView[]> { return (await this.data()).partViews; }
  async listProcedureCompletions(): Promise<ProcedureCompletion[]> { return (await this.data()).procedures; }
  async listMissionCompletions(): Promise<MissionCompletion[]> { return (await this.data()).missions; }
  async pointsByStudent() { return (await this.data()).points; }

  /** One row per (student, device) with any activity, like the `student_results` view. */
  async studentResults(): Promise<StudentResultRow[]> {
    const d = await this.data();
    const names = new Map(d.devices.map((x) => [x.id, x.name]));
    const stu = new Map(d.students.map((s) => [s.id, s]));
    const rows = new Map<string, StudentResultRow>();
    const row = (sid: string, did: string) => {
      const key = `${sid}|${did}`;
      let x = rows.get(key);
      if (!x) {
        const s = stu.get(sid)!;
        x = {
          userId: sid, displayName: s.displayName, classGroup: s.classGroup,
          equipmentId: did, equipmentName: names.get(did) ?? did,
          quizAttempts: 0, questionsAnswered: 0, correctAnswers: 0,
          accuracyPct: null, avgAttemptSec: null, points: d.points[sid]?.byDevice[did] ?? 0,
        };
        rows.set(key, x);
      }
      return x;
    };
    const secs = new Map<string, number>();
    for (const a of d.quizAttempts) {
      const x = row(a.studentId, a.deviceId);
      x.quizAttempts++;
      x.questionsAnswered += a.answers.length;
      x.correctAnswers += a.answers.filter(Boolean).length;
      secs.set(`${a.studentId}|${a.deviceId}`, (secs.get(`${a.studentId}|${a.deviceId}`) ?? 0) + a.durationSec);
    }
    for (const s of d.scans) row(s.studentId, s.deviceId);
    for (const [key, x] of rows) {
      if (x.questionsAnswered) x.accuracyPct = Math.round((1000 * x.correctAnswers) / x.questionsAnswered) / 10;
      if (x.quizAttempts) x.avgAttemptSec = Math.round(((secs.get(key) ?? 0) / x.quizAttempts) * 10) / 10;
    }
    return [...rows.values()];
  }
}

let singleton: ResultsStore | null = null;
export function getResultsStore(): ResultsStore {
  return (singleton ??= new MockResultsStore());
}
