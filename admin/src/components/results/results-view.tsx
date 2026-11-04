"use client";

import { useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  DEFAULT_FILTERS, compute, downloadCsv, formatDuration, relativeTime, studentsToCsv,
  type Filters, type StudentRow,
} from "@/lib/results";
import { AccuracyByDeviceChart, AttemptsPerWeekChart } from "./charts";
import { FiltersBar } from "./filters-bar";
import { StudentSheet } from "./student-sheet";
import { useResultsData } from "./use-results-data";

type SortKey = "name" | "classGroup" | "quizAttempts" | "accuracy" | "avgSec" | "points" | "badges" | "lastActive";

const COLUMNS: { key: SortKey; label: string; numeric?: boolean }[] = [
  { key: "name", label: "Student" },
  { key: "classGroup", label: "Group" },
  { key: "quizAttempts", label: "Quiz attempts", numeric: true },
  { key: "accuracy", label: "Accuracy", numeric: true },
  { key: "avgSec", label: "Avg time", numeric: true },
  { key: "points", label: "Points", numeric: true },
  { key: "badges", label: "Badges", numeric: true },
  { key: "lastActive", label: "Last active" },
];

function sortValue(r: StudentRow, k: SortKey): string | number | null {
  return k === "badges" ? r.badges.length : r[k];
}

export function ResultsView() {
  const data = useResultsData();
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: "points", dir: -1 });
  const [openId, setOpenId] = useState<string | null>(null);

  const result = useMemo(() => (data ? compute(data, filters) : null), [data, filters]);
  const rows = useMemo(() => {
    if (!result) return [];
    return [...result.students].sort((a, b) => {
      const x = sortValue(a, sort.key);
      const y = sortValue(b, sort.key);
      if (x == null && y == null) return 0;
      if (x == null) return 1; // empty values always last
      if (y == null) return -1;
      const c = typeof x === "string" ? x.localeCompare(y as string) : x - (y as number);
      return c * sort.dir || a.name.localeCompare(b.name);
    });
  }, [result, sort]);

  if (!data || !result) return <p className="text-sm text-muted-foreground">Loading results...</p>;
  const groups = [...new Set(data.students.map((s) => s.classGroup))].sort();
  const { summary, devices, weekly } = result;
  const toggle = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 1 ? -1 : 1 } : { key, dir: key === "name" || key === "classGroup" ? 1 : -1 }));

  return (
    <>
      <FiltersBar filters={filters} onChange={setFilters} groups={groups} devices={data.devices} />

      <div className="mb-8 grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Stat label="Students" value={`${summary.activeStudents}/${summary.students}`} hint="active / in filter" />
        <Stat label="Quiz attempts" value={String(summary.quizAttempts)} />
        <Stat label="Avg accuracy" value={summary.accuracy == null ? "-" : `${summary.accuracy}%`} />
        <Stat label="Avg time / attempt" value={formatDuration(summary.avgSec)} />
        <Stat label="Points earned" value={summary.totalPoints.toLocaleString("en-US")} />
      </div>

      <div className="mb-8 grid gap-6 lg:grid-cols-2">
        <Panel title="Average accuracy by device"><AccuracyByDeviceChart rows={devices} /></Panel>
        <Panel title="Quiz attempts per week"><AttemptsPerWeekChart weeks={weekly} /></Panel>
      </div>

      <section className="mb-8">
        <h2 className="mb-3 text-lg font-semibold">By device</h2>
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Device</TableHead>
                <TableHead className="text-right">Students</TableHead>
                <TableHead className="text-right">Quiz attempts</TableHead>
                <TableHead className="text-right">Accuracy</TableHead>
                <TableHead className="text-right">Avg time</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {devices.map((d) => (
                <TableRow key={d.deviceId}>
                  <TableCell>{d.name}</TableCell>
                  <TableCell className="text-right">{d.students}</TableCell>
                  <TableCell className="text-right">{d.quizAttempts}</TableCell>
                  <TableCell className="text-right">{d.accuracy == null ? "-" : `${d.accuracy}%`}</TableCell>
                  <TableCell className="text-right">{formatDuration(d.avgSec)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Students <span className="text-sm font-normal text-muted-foreground">({rows.length})</span></h2>
          <Button variant="outline" disabled={!rows.length}
            onClick={() => downloadCsv("equipx-student-results.csv", studentsToCsv(rows))}>
            <Download /> Export CSV
          </Button>
        </div>
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                {COLUMNS.map((c) => (
                  <TableHead key={c.key} className={c.numeric ? "text-right" : ""} aria-sort={sort.key === c.key ? (sort.dir === 1 ? "ascending" : "descending") : "none"}>
                    <button type="button" onClick={() => toggle(c.key)} className="inline-flex items-center gap-1 hover:text-foreground">
                      {c.label}
                      {sort.key === c.key && (sort.dir === 1 ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}
                    </button>
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length === 0 && (
                <TableRow><TableCell colSpan={COLUMNS.length} className="py-8 text-center text-muted-foreground">No students match these filters.</TableCell></TableRow>
              )}
              {rows.map((r) => (
                <TableRow key={r.id} className="cursor-pointer" onClick={() => setOpenId(r.id)}>
                  <TableCell className="font-medium">{r.name}</TableCell>
                  <TableCell>{r.classGroup}</TableCell>
                  <TableCell className="text-right">{r.quizAttempts}</TableCell>
                  <TableCell className="text-right">{r.accuracy == null ? "-" : `${r.accuracy}%`}</TableCell>
                  <TableCell className="text-right">{formatDuration(r.avgSec)}</TableCell>
                  <TableCell className="text-right">{r.points}</TableCell>
                  <TableCell className="text-right">{r.badges.length}</TableCell>
                  <TableCell className="whitespace-nowrap">{r.lastActive ? relativeTime(r.lastActive) : "-"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <StudentSheet student={rows.find((r) => r.id === openId) ?? null} data={data} filters={filters} onClose={() => setOpenId(null)} />
    </>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-md border bg-secondary px-4 py-3">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-md border p-4">
      <h2 className="mb-3 text-sm font-medium">{title}</h2>
      {children}
    </div>
  );
}
