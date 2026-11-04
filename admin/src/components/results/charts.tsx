"use client";

import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import type { DeviceRow, WeekBucket } from "@/lib/results";

const ACCENT = "#2383E2";
const GRID = "#E9E9E7";
const TICK = { fill: "#787774", fontSize: 12 };
const tooltipStyle = {
  contentStyle: { border: "1px solid #E9E9E7", borderRadius: 6, boxShadow: "none", fontSize: 12 },
  cursor: { fill: "#F7F7F5" },
};

export function AccuracyByDeviceChart({ rows }: { rows: DeviceRow[] }) {
  const data = rows.filter((r) => r.accuracy != null).map((r) => ({ name: r.name, accuracy: r.accuracy }));
  if (!data.length) return <ChartEmpty />;
  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid vertical={false} stroke={GRID} />
          <XAxis dataKey="name" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} interval={0} angle={-30} textAnchor="end" height={80} />
          <YAxis domain={[0, 100]} tick={TICK} tickLine={false} axisLine={false} unit="%" />
          <Tooltip {...tooltipStyle} formatter={(v) => [`${v}%`, "Avg accuracy"]} />
          <Bar dataKey="accuracy" fill={ACCENT} radius={[2, 2, 0, 0]} maxBarSize={36} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function AttemptsPerWeekChart({ weeks }: { weeks: WeekBucket[] }) {
  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={weeks} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
          <CartesianGrid vertical={false} stroke={GRID} />
          <XAxis dataKey="label" tick={TICK} tickLine={false} axisLine={{ stroke: GRID }} />
          <YAxis allowDecimals={false} tick={TICK} tickLine={false} axisLine={false} />
          <Tooltip {...tooltipStyle} cursor={{ stroke: GRID }} formatter={(v) => [v, "Quiz attempts"]} />
          <Line type="monotone" dataKey="attempts" stroke={ACCENT} strokeWidth={2} dot={{ r: 3, fill: ACCENT, stroke: ACCENT }} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function ChartEmpty() {
  return <p className="py-16 text-center text-sm text-muted-foreground">No quiz data for these filters.</p>;
}
