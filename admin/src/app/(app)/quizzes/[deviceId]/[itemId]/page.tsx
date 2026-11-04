"use client";

import { useParams } from "next/navigation";
import { QuizEditor } from "@/components/editors/quiz-editor";

export default function Page() {
  const { deviceId, itemId } = useParams<{ deviceId: string; itemId: string }>();
  return <QuizEditor deviceId={deviceId} itemId={itemId} />;
}
