import { fetchAll, getSupabase } from "@/lib/supabase/client";
import type {
  BadgeId,
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

type Row = Record<string, unknown>;

interface Dataset {
  devices: DeviceInfo[];
  students: Student[];
  quizAttempts: QuizAttempt[];
  faultAttempts: FaultAttempt[];
  scans: Scan[];
  partViews: PartView[];
  procedures: ProcedureCompletion[];
  missions: MissionCompletion[];
  points: Record<string, StudentPoints>;
}

/** Reads student activity (teacher-only via RLS) and maps database uuids to contract slugs. */
export class SupabaseResultsStore implements ResultsStore {
  private db = getSupabase();
  private cache: Promise<Dataset> | null = null;

  private all(table: string, columns: string, order = "created_at"): Promise<Row[]> {
    return fetchAll<Row>(
      (from, to) => this.db.from(table).select(columns).order(order).order("id").range(from, to) as never,
      `Load ${table}`,
    );
  }

  private data(): Promise<Dataset> {
    // Drop the cache on failure so a retry (e.g. after signing in) can succeed.
    const p = (this.cache ??= this.load());
    p.catch(() => {
      if (this.cache === p) this.cache = null;
    });
    return p;
  }

  private async load(): Promise<Dataset> {
    const [
      equipment, profiles, quizzes, questions, scenarios, procedures, missions, tasks,
      attempts, answers, faultAttempts, scans, partViews, completions, progress, ledger, userBadges,
    ] = await Promise.all([
      this.all("equipment", "id,name", "name"),
      this.all("profiles", "id,display_name,class_group,role"),
      this.all("quizzes", "id,slug,equipment_id"),
      this.all("quiz_questions", "id,quiz_id,sort_order"),
      this.all("fault_scenarios", "id,slug,equipment_id"),
      this.all("procedures", "id,slug,equipment_id"),
      this.all("missions", "id,slug,equipment_id"),
      this.all("mission_tasks", "id,mission_id,equipment_id"),
      this.all("quiz_attempts", "id,user_id,quiz_id,duration_sec,created_at"),
      this.all("quiz_answers", "id,attempt_id,question_id,is_correct"),
      this.all("fault_attempts", "id,user_id,scenario_id,is_correct,created_at"),
      this.all("scans", "id,user_id,equipment_id,created_at"),
      this.all("part_views", "id,user_id,equipment_id,part_id,created_at"),
      this.all("procedure_completions", "id,user_id,procedure_id,created_at"),
      this.all("mission_progress", "id,user_id,mission_id,task_id,completed_at", "completed_at"),
      this.all("points_ledger", "id,user_id,reason,amount,equipment_id,created_at"),
      this.all("user_badges", "user_id,badge_code", "awarded_at"),
    ]);

    const byId = (rows: Row[]) => new Map(rows.map((r) => [r.id as string, r]));
    const quizBy = byId(quizzes);
    const scenarioBy = byId(scenarios);
    const procBy = byId(procedures);
    const missionBy = byId(missions);
    const questionOrder = new Map(questions.map((q) => [q.id as string, q.sort_order as number]));

    const studentRows = profiles.filter((p) => p.role === "student");
    const students: Student[] = studentRows.map((p) => ({
      id: p.id as string,
      displayName: (p.display_name as string) || "Student",
      classGroup: (p.class_group as string | null) ?? "",
      college: "", // not stored in the schema
    }));
    const studentIds = new Set(students.map((s) => s.id));
    const mine = (r: Row) => studentIds.has(r.user_id as string);

    // quiz attempts: answers ordered by question sort_order
    const answersByAttempt = new Map<string, Row[]>();
    for (const a of answers) {
      const list = answersByAttempt.get(a.attempt_id as string) ?? [];
      list.push(a);
      answersByAttempt.set(a.attempt_id as string, list);
    }
    const quizAttempts: QuizAttempt[] = attempts.filter(mine).map((a) => {
      const quiz = quizBy.get(a.quiz_id as string);
      const list = (answersByAttempt.get(a.id as string) ?? []).sort(
        (x, y) => (questionOrder.get(x.question_id as string) ?? 0) - (questionOrder.get(y.question_id as string) ?? 0),
      );
      return {
        id: a.id as string,
        studentId: a.user_id as string,
        deviceId: (quiz?.equipment_id as string) ?? "",
        quizId: (quiz?.slug as string) ?? "",
        at: a.created_at as string,
        durationSec: (a.duration_sec as number | null) ?? 0,
        answers: list.map((x) => x.is_correct === true),
      };
    });

    const faults: FaultAttempt[] = faultAttempts.filter(mine).map((f) => {
      const sc = scenarioBy.get(f.scenario_id as string);
      return {
        id: f.id as string,
        studentId: f.user_id as string,
        deviceId: (sc?.equipment_id as string) ?? "",
        faultId: (sc?.slug as string) ?? "",
        at: f.created_at as string,
        correct: f.is_correct === true,
      };
    });

    const scanOut: Scan[] = scans.filter(mine).map((s) => ({
      id: s.id as string, studentId: s.user_id as string, deviceId: s.equipment_id as string, at: s.created_at as string,
    }));
    const partViewOut: PartView[] = partViews.filter(mine).map((p) => ({
      id: p.id as string, studentId: p.user_id as string, deviceId: p.equipment_id as string,
      partId: p.part_id as string, at: p.created_at as string,
    }));
    const procOut: ProcedureCompletion[] = completions.filter(mine).map((c) => {
      const pr = procBy.get(c.procedure_id as string);
      return {
        id: c.id as string, studentId: c.user_id as string, deviceId: (pr?.equipment_id as string) ?? "",
        procedureId: (pr?.slug as string) ?? "", at: c.created_at as string,
      };
    });

    // A mission is complete for a student when every task has a progress row.
    const taskCount = new Map<string, number>();
    const firstTaskDevice = new Map<string, string>();
    for (const t of tasks) {
      const m = t.mission_id as string;
      taskCount.set(m, (taskCount.get(m) ?? 0) + 1);
      if (!firstTaskDevice.has(m) && t.equipment_id) firstTaskDevice.set(m, t.equipment_id as string);
    }
    const doneBy = new Map<string, { n: number; last: string; user: string; mission: string }>();
    for (const p of progress.filter(mine)) {
      const key = `${p.user_id}|${p.mission_id}`;
      const cur = doneBy.get(key) ?? { n: 0, last: "", user: p.user_id as string, mission: p.mission_id as string };
      cur.n++;
      if ((p.completed_at as string) > cur.last) cur.last = p.completed_at as string;
      doneBy.set(key, cur);
    }
    const missionOut: MissionCompletion[] = [];
    for (const [key, d] of doneBy) {
      if (d.n < (taskCount.get(d.mission) ?? Infinity)) continue;
      const m = missionBy.get(d.mission);
      missionOut.push({
        id: key,
        studentId: d.user,
        deviceId: (m?.equipment_id as string | null) ?? firstTaskDevice.get(d.mission) ?? "",
        missionId: (m?.slug as string) ?? "",
        at: d.last,
      });
    }

    // points + badges straight from the server-side ledger
    const points: Record<string, StudentPoints> = {};
    for (const id of studentIds) points[id] = { total: 0, byDevice: {}, badges: [] };
    for (const l of ledger) {
      const p = points[l.user_id as string];
      if (!p) continue;
      const amount = l.amount as number;
      p.total += amount;
      const dev = l.equipment_id as string | null;
      if (dev) p.byDevice[dev] = (p.byDevice[dev] ?? 0) + amount;
    }
    for (const b of userBadges) points[b.user_id as string]?.badges.push(b.badge_code as BadgeId);

    return {
      devices: equipment.map((e) => ({ id: e.id as string, name: e.name as string })),
      students,
      quizAttempts,
      faultAttempts: faults,
      scans: scanOut,
      partViews: partViewOut,
      procedures: procOut,
      missions: missionOut,
      points,
    };
  }

  async listDevices() { return (await this.data()).devices; }
  async listStudents() { return (await this.data()).students; }
  async listQuizAttempts() { return (await this.data()).quizAttempts; }
  async listFaultAttempts() { return (await this.data()).faultAttempts; }
  async listScans() { return (await this.data()).scans; }
  async listPartViews() { return (await this.data()).partViews; }
  async listProcedureCompletions() { return (await this.data()).procedures; }
  async listMissionCompletions() { return (await this.data()).missions; }
  async pointsByStudent() { return (await this.data()).points; }

  /** Straight from the `student_results` view. */
  async studentResults(): Promise<StudentResultRow[]> {
    const rows = await fetchAll<Row>(
      (from, to) => this.db.from("student_results").select("*").range(from, to) as never,
      "Load student_results",
    );
    const num = (v: unknown) => (v === null || v === undefined ? null : Number(v));
    return rows.map((r) => ({
      userId: r.user_id as string,
      displayName: r.display_name as string,
      classGroup: (r.class_group as string | null) ?? "",
      equipmentId: r.equipment_id as string,
      equipmentName: r.equipment_name as string,
      quizAttempts: Number(r.quiz_attempts),
      questionsAnswered: Number(r.questions_answered),
      correctAnswers: Number(r.correct_answers),
      accuracyPct: num(r.accuracy_pct),
      avgAttemptSec: num(r.avg_attempt_sec),
      points: Number(r.points),
    }));
  }
}
