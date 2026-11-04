"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Download, Flag, Plus } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/shell/empty-state";
import { Callout, ErrorList, LOCAL_EDITS_NOTE, exportMissions, useAllDevices } from "@/components/editors/common";
import { getStore, type Mission } from "@/lib/data";

export default function Page() {
  const devices = useAllDevices();
  const [exportErrors, setExportErrors] = useState<string[]>([]);
  const [missions, setMissions] = useState<Mission[] | null>(null);
  const load = useCallback(() => getStore().listMissions().then(setMissions), []);
  useEffect(() => {
    load();
  }, [load]);
  const name = (id?: string) => (id ? (devices?.find((d) => d.id === id)?.name ?? id) : "Any device");

  return (
    <>
      <PageHeader title="Missions" description="Guided task lists that award points on completion." />
      <Callout>{LOCAL_EDITS_NOTE}</Callout>
      <div className="mb-4 flex justify-end gap-2">
        <Button
          variant="outline"
          disabled={!missions || !devices}
          onClick={() => setExportErrors(exportMissions(missions!, devices!))}
        >
          <Download /> Export JSON
        </Button>
        <Link href="/missions/new" className={buttonVariants()}>
          <Plus /> New mission
        </Link>
      </div>
      <ErrorList errors={exportErrors} heading="Export blocked" />
      {missions && missions.length === 0 ? (
        <EmptyState icon={Flag} title="No missions" description="Create a mission to get started." />
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Device</TableHead>
                <TableHead>Tasks</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(missions ?? []).map((m) => (
                <TableRow key={m.id}>
                  <TableCell className="font-medium">
                    <Link href={`/missions/${m.id}`} className="hover:underline">
                      {m.title}
                    </Link>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{name(m.deviceId)}</TableCell>
                  <TableCell className="text-muted-foreground">{m.tasks.length}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  );
}
