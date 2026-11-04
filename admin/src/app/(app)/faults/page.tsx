"use client";

import { Wrench } from "lucide-react";
import { ContentList } from "@/components/editors/content-list";
import type { Device } from "@/lib/data";

const rowsFor = (d: Device) =>
  d.faults.map((f) => ({
    id: f.id,
    title: f.symptom,
    cells: [f.options.length, f.relatedPartIds.length],
  }));

export default function Page() {
  return (
    <ContentList
      basePath="/faults"
      title="Fault scenarios"
      description="Diagnose-the-fault scenarios for each device."
      noun="fault"
      icon={Wrench}
      columns={["Symptom", "Device", "Options", "Related parts"]}
      rowsFor={rowsFor}
    />
  );
}
