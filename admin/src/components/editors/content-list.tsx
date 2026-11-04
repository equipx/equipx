"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Download, Plus } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/shell/empty-state";
import type { Device } from "@/lib/data";
import {
  Callout,
  LOCAL_EDITS_NOTE,
  NativeSelect,
  deviceForExport,
  downloadJson,
  useAllDevices,
} from "./common";
import type { LucideIcon } from "lucide-react";

export interface ListRow {
  id: string;
  title: string;
  cells: React.ReactNode[];
}

export function ContentList({
  basePath,
  title,
  description,
  noun,
  icon,
  columns,
  rowsFor,
}: {
  basePath: string;
  title: string;
  description: string;
  noun: string;
  icon: LucideIcon;
  columns: string[];
  rowsFor: (d: Device) => ListRow[];
}) {
  const devices = useAllDevices();
  const [filter, setFilter] = useState("");
  const [newDevice, setNewDevice] = useState("");

  const rows = useMemo(() => {
    if (!devices) return [];
    return devices
      .filter((d) => !filter || d.id === filter)
      .flatMap((d) => rowsFor(d).map((r) => ({ ...r, device: d })));
  }, [devices, filter, rowsFor]);

  const deviceOptions = (devices ?? []).map((d) => ({ value: d.id, label: d.name }));
  const target = filter || newDevice || devices?.[0]?.id || "";
  const exportDevice = devices?.find((d) => d.id === filter);

  return (
    <>
      <PageHeader title={title} description={description} />
      <Callout>{LOCAL_EDITS_NOTE}</Callout>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <NativeSelect
          aria-label="Filter by device"
          className="w-56"
          value={filter}
          onChange={setFilter}
          placeholder="All devices"
          options={deviceOptions}
        />
        {exportDevice && (
          <Button
            variant="outline"
            onClick={() => downloadJson(`${exportDevice.id}.json`, deviceForExport(exportDevice))}
          >
            <Download /> Export {exportDevice.name} JSON
          </Button>
        )}
        <div className="ml-auto flex items-center gap-2">
          {!filter && (
            <NativeSelect
              aria-label={`Device for new ${noun}`}
              className="w-48"
              value={target}
              onChange={setNewDevice}
              options={deviceOptions}
            />
          )}
          <Link
            href={target ? `${basePath}/${target}/new` : basePath}
            className={buttonVariants({ variant: "default" })}
          >
            <Plus /> New {noun}
          </Link>
        </div>
      </div>
      {devices && rows.length === 0 ? (
        <EmptyState
          icon={icon}
          title={`No ${noun}s`}
          description={`Create a ${noun} for a device to get started.`}
        />
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                {columns.map((c) => (
                  <TableHead key={c}>{c}</TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={`${r.device.id}/${r.id}`}>
                  <TableCell className="max-w-md truncate font-medium">
                    <Link
                      href={`${basePath}/${r.device.id}/${r.id}`}
                      className="hover:underline"
                    >
                      {r.title}
                    </Link>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{r.device.name}</TableCell>
                  {r.cells.map((c, i) => (
                    <TableCell key={i} className="text-muted-foreground">
                      {c}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  );
}
