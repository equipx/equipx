import type { StudentRow } from "./analytics";

/** RFC 4180 escaping (plus formula-injection guard): quote fields containing comma, quote, CR or LF; double inner quotes. */
export function csvEscape(v: string | number | null | undefined): string {
  let s = v == null ? "" : String(v);
  // Spreadsheet formula injection: neutralise leading = + - @ tab CR in text fields.
  if (typeof v === "string" && /^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export const CSV_HEADER = [
  "Name", "Class group", "College", "Quiz attempts", "Accuracy %", "Avg time (s)", "Points", "Badges", "Last active (UTC)",
];

export function studentsToCsv(rows: StudentRow[]): string {
  const lines = [CSV_HEADER.map(csvEscape).join(",")];
  for (const r of rows) {
    lines.push(
      [r.name, r.classGroup, r.college, r.quizAttempts, r.accuracy, r.avgSec, r.points, r.badges.length, r.lastActive]
        .map(csvEscape)
        .join(","),
    );
  }
  return lines.join("\r\n") + "\r\n";
}

export function downloadCsv(filename: string, csv: string) {
  const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
