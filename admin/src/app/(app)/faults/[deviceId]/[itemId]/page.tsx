"use client";

import { useParams } from "next/navigation";
import { FaultEditor } from "@/components/editors/fault-editor";

export default function Page() {
  const { deviceId, itemId } = useParams<{ deviceId: string; itemId: string }>();
  return <FaultEditor deviceId={deviceId} itemId={itemId} />;
}
