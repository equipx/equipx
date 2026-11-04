import { Suspense } from "react";
import { DeviceEditor } from "@/components/equipment/device-editor";

async function Editor({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <DeviceEditor id={id} />;
}

export default function EquipmentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <Editor params={params} />
    </Suspense>
  );
}
