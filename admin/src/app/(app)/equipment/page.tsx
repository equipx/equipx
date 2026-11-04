"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Cpu } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/shell/empty-state";
import { NewDeviceButton } from "@/components/equipment/new-device-dialog";
import { PageHeader } from "@/components/shell/page-header";
import { getStore, type DeviceIndexEntry } from "@/lib/data";

export default function EquipmentPage() {
  const router = useRouter();
  const [devices, setDevices] = useState<DeviceIndexEntry[] | null>(null);

  useEffect(() => {
    getStore().listDevices().then(setDevices);
  }, []);

  return (
    <>
      <div className="flex items-start justify-between gap-4">
        <PageHeader title="Equipment" description="Lab devices, their parts and 3D models." />
        <NewDeviceButton existingIds={(devices ?? []).map((d) => d.id)} disabled={devices === null} />
      </div>
      {devices === null ? null : devices.length === 0 ? (
        <EmptyState
          icon={Cpu}
          title="No equipment yet"
          description="Run the sync script to import devices from /content, or add one with New device."
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Summary</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {devices.map((d) => (
              <TableRow key={d.id} className="cursor-pointer" onClick={() => router.push(`/equipment/${d.id}`)}>
                <TableCell className="font-medium">{d.name}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{d.category}</Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">{d.summary}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </>
  );
}
