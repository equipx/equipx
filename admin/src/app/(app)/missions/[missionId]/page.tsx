"use client";

import { useParams } from "next/navigation";
import { MissionEditor } from "@/components/editors/mission-editor";

export default function Page() {
  const { missionId } = useParams<{ missionId: string }>();
  return <MissionEditor missionId={missionId} />;
}
