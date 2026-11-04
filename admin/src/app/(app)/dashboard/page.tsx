"use client";

import { useEffect, useState } from "react";
import { PageHeader } from "@/components/shell/page-header";
import { getStore } from "@/lib/data";

interface Stats {
  devices: number;
  parts: number;
  procedures: number;
  quizzes: number;
}

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    (async () => {
      const store = getStore();
      const entries = await store.listDevices();
      const devices = (await Promise.all(entries.map((e) => store.getDevice(e.id)))).filter(
        (d) => d !== null,
      );
      setStats({
        devices: entries.length,
        parts: devices.reduce((n, d) => n + d.parts.length, 0),
        procedures: devices.reduce((n, d) => n + d.procedures.length, 0),
        quizzes: devices.reduce((n, d) => n + d.quizzes.length, 0),
      });
    })();
  }, []);

  const blocks: { label: string; value: number | undefined }[] = [
    { label: "Devices", value: stats?.devices },
    { label: "Parts", value: stats?.parts },
    { label: "Procedures", value: stats?.procedures },
    { label: "Quizzes", value: stats?.quizzes },
  ];

  return (
    <>
      <PageHeader title="Dashboard" description="An overview of your EquipX content." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {blocks.map((b) => (
          <div key={b.label} className="rounded-md border bg-secondary px-4 py-3">
            <p className="text-sm text-muted-foreground">{b.label}</p>
            <p className="mt-1 text-2xl font-semibold">{b.value ?? "-"}</p>
          </div>
        ))}
      </div>
    </>
  );
}
