"use client";

import { ListChecks } from "lucide-react";
import { ContentList } from "@/components/editors/content-list";
import type { Device } from "@/lib/data";

const rowsFor = (d: Device) =>
  d.procedures.map((p) => ({ id: p.id, title: p.title, cells: [p.steps.length] }));

export default function Page() {
  return (
    <ContentList
      basePath="/procedures"
      title="Procedures"
      description="Step-by-step procedures students follow on each device."
      noun="procedure"
      icon={ListChecks}
      columns={["Title", "Device", "Steps"]}
      rowsFor={rowsFor}
    />
  );
}
