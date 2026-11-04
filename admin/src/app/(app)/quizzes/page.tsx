"use client";

import { CircleHelp } from "lucide-react";
import { ContentList } from "@/components/editors/content-list";
import type { Device } from "@/lib/data";

const rowsFor = (d: Device) =>
  d.quizzes.map((q) => ({
    id: q.id,
    title: q.title,
    cells: [q.questions.length, q.timeLimitSec ? `${q.timeLimitSec}s` : "None"],
  }));

export default function Page() {
  return (
    <ContentList
      basePath="/quizzes"
      title="Quizzes"
      description="Quizzes that test knowledge of each device."
      noun="quiz"
      icon={CircleHelp}
      columns={["Title", "Device", "Questions", "Time limit"]}
      rowsFor={rowsFor}
    />
  );
}
