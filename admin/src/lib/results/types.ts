// Result-tracking types. Shapes mirror the tables behind supabase `student_results` (see
// supabase/migrations/20261104000300_views.sql) so a Supabase-backed store can replace the mock.

export interface Student {
  id: string;
  displayName: string;
  classGroup: string;
  college: string;
}

export interface QuizAttempt {
  id: string;
  studentId: string;
  deviceId: string;
  quizId: string;
  /** ISO timestamp (UTC). */
  at: string;
  durationSec: number;
  /** One entry per question (index = question order): was the answer correct. */
  answers: boolean[];
}

export interface FaultAttempt {
  id: string;
  studentId: string;
  deviceId: string;
  faultId: string;
  at: string;
  correct: boolean;
}

export interface Scan {
  id: string;
  studentId: string;
  deviceId: string;
  at: string;
}

export interface PartView {
  id: string;
  studentId: string;
  deviceId: string;
  partId: string;
  at: string;
}

export interface ProcedureCompletion {
  id: string;
  studentId: string;
  deviceId: string;
  procedureId: string;
  at: string;
}

export interface MissionCompletion {
  id: string;
  studentId: string;
  deviceId: string;
  missionId: string;
  at: string;
}

export type PointReason = "scan" | "part" | "quiz" | "fault" | "mission";

export interface LedgerEntry {
  studentId: string;
  deviceId: string;
  at: string;
  amount: number;
  reason: PointReason;
}

export type BadgeId =
  | "first_scan"
  | "part_spotter"
  | "safety_first"
  | "quiz_ace"
  | "fault_finder"
  | "mission_complete";

export interface StudentPoints {
  total: number;
  byDevice: Record<string, number>;
  badges: BadgeId[];
}

export interface DeviceInfo {
  id: string;
  name: string;
}

/** One row per (student, device) with activity - same columns as the `student_results` view. */
export interface StudentResultRow {
  userId: string;
  displayName: string;
  classGroup: string;
  equipmentId: string;
  equipmentName: string;
  quizAttempts: number;
  questionsAnswered: number;
  correctAnswers: number;
  accuracyPct: number | null;
  avgAttemptSec: number | null;
  points: number;
}

export interface ResultsStore {
  listDevices(): Promise<DeviceInfo[]>;
  listStudents(): Promise<Student[]>;
  listQuizAttempts(): Promise<QuizAttempt[]>;
  listFaultAttempts(): Promise<FaultAttempt[]>;
  listScans(): Promise<Scan[]>;
  listPartViews(): Promise<PartView[]>;
  listProcedureCompletions(): Promise<ProcedureCompletion[]>;
  listMissionCompletions(): Promise<MissionCompletion[]>;
  pointsByStudent(): Promise<Record<string, StudentPoints>>;
  studentResults(): Promise<StudentResultRow[]>;
}
