"use client";

import { useEffect, useState } from "react";
import { PageHeader } from "@/components/shell/page-header";
import { getStore } from "@/lib/data";
import { NOW_MS, formatDuration, loadResultsData, relativeTime, type ResultsData } from "@/lib/results";

interface ContentStats { devices: number; parts: number; procedures: number; quizzes: number }
interface Activity { at: string; text: string }

function buildActivity(d: ResultsData): Activity[] {
  const stu = new Map(d.students.map((s) => [s.id, s.displayName]));
  const dev = new Map(d.devices.map((x) => [x.id, x.name]));
  const n = (id: string) => stu.get(id) ?? id;
  const v = (id: string) => dev.get(id) ?? id;
  return [
    ...d.quizAttempts.map((a) => ({ at: a.at, text: `${n(a.studentId)} scored ${a.answers.filter(Boolean).length}/${a.answers.length} on the ${v(a.deviceId)} quiz (${formatDuration(a.durationSec)})` })),
    ...d.faultAttempts.filter((f) => f.correct).map((f) => ({ at: f.at, text: `${n(f.studentId)} fixed a fault on the ${v(f.deviceId)}` })),
    ...d.procedures.map((p) => ({ at: p.at, text: `${n(p.studentId)} completed a ${v(p.deviceId)} procedure` })),
    ...d.missions.map((m) => ({ at: m.at, text: `${n(m.studentId)} completed a ${v(m.deviceId)} mission` })),
  ].sort((a, b) => b.at.localeCompare(a.at)).slice(0, 8);
}

export default function DashboardPage() {
  const [content, setContent] = useState<ContentStats | null>(null);
  const [results, setResults] = useState<ResultsData | null>(null);

  useEffect(() => {
    (async () => {
      const store = getStore();
      const entries = await store.listDevices();
      const devices = (await Promise.all(entries.map((e) => store.getDevice(e.id)))).filter((d) => d !== null);
      setContent({
        devices: entries.length,
        parts: devices.reduce((n, d) => n + d.parts.length, 0),
        procedures: devices.reduce((n, d) => n + d.procedures.length, 0),
        quizzes: devices.reduce((n, d) => n + d.quizzes.length, 0),
      });
    })();
    loadResultsData().then(setResults);
  }, []);

  let active: number | undefined;
  let accuracy: string | undefined;
  let activity: Activity[] = [];
  if (results) {
    const weekAgo = new Date(NOW_MS - 7 * 86_400_000).toISOString();
    const ids = new Set<string>();
    for (const list of [results.quizAttempts, results.faultAttempts, results.scans, results.procedures, results.missions])
      for (const x of list) if (x.at >= weekAgo) ids.add(x.studentId);
    active = ids.size;
    const total = results.quizAttempts.reduce((n, a) => n + a.answers.length, 0);
    const right = results.quizAttempts.reduce((n, a) => n + a.answers.filter(Boolean).length, 0);
    accuracy = total ? `${Math.round((1000 * right) / total) / 10}%` : "-";
    activity = buildActivity(results);
  }

  const blocks: { label: string; value: string | number | undefined }[] = [
    { label: "Devices", value: content?.devices },
    { label: "Parts", value: content?.parts },
    { label: "Procedures", value: content?.procedures },
    { label: "Quizzes", value: content?.quizzes },
    { label: "Active students this week", value: active },
    { label: "Avg quiz accuracy", value: accuracy },
  ];

  return (
    <>
      <PageHeader title="Dashboard" description="An overview of your EquipX content and student activity." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        {blocks.map((b) => (
          <div key={b.label} className="rounded-md border bg-secondary px-4 py-3">
            <p className="text-sm text-muted-foreground">{b.label}</p>
            <p className="mt-1 text-2xl font-semibold">{b.value ?? "-"}</p>
          </div>
        ))}
      </div>
      <section className="mt-8">
        <h2 className="mb-3 text-lg font-semibold">Recent activity</h2>
        <ul className="divide-y rounded-md border">
          {activity.length === 0 && <li className="px-4 py-6 text-sm text-muted-foreground">{results ? "No recent activity." : "Loading..."}</li>}
          {activity.map((a, i) => (
            <li key={i} className="flex items-baseline justify-between gap-4 px-4 py-2.5 text-sm">
              <span>{a.text}</span>
              <span className="shrink-0 text-xs text-muted-foreground">{relativeTime(a.at)}</span>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
