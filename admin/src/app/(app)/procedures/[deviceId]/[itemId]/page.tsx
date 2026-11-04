"use client";

import { useParams } from "next/navigation";
import { ProcedureEditor } from "@/components/editors/procedure-editor";

export default function Page() {
  const { deviceId, itemId } = useParams<{ deviceId: string; itemId: string }>();
  return <ProcedureEditor deviceId={deviceId} itemId={itemId} />;
}
