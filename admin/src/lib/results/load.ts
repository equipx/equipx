import { getResultsStore } from "./mock-store";
import type { ResultsData } from "./analytics";

export async function loadResultsData(): Promise<ResultsData> {
  const s = getResultsStore();
  const [devices, students, quizAttempts, faultAttempts, scans, partViews, procedures, missions, points] =
    await Promise.all([
      s.listDevices(), s.listStudents(), s.listQuizAttempts(), s.listFaultAttempts(), s.listScans(),
      s.listPartViews(), s.listProcedureCompletions(), s.listMissionCompletions(), s.pointsByStudent(),
    ]);
  return { devices, students, quizAttempts, faultAttempts, scans, partViews, procedures, missions, points };
}
