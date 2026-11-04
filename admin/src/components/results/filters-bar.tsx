"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DEFAULT_FILTERS, type DeviceInfo, type Filters } from "@/lib/results";

export function FiltersBar({
  filters, onChange, groups, devices,
}: {
  filters: Filters;
  onChange: (f: Filters) => void;
  groups: string[];
  devices: DeviceInfo[];
}) {
  const set = (patch: Partial<Filters>) => onChange({ ...filters, ...patch });
  const groupItems = [{ value: "all", label: "All groups" }, ...groups.map((g) => ({ value: g, label: g }))];
  const deviceItems = [{ value: "all", label: "All devices" }, ...devices.map((d) => ({ value: d.id, label: d.name }))];
  const dirty = JSON.stringify(filters) !== JSON.stringify(DEFAULT_FILTERS);
  return (
    <div className="mb-6 flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label>Class group</Label>
        <Select items={groupItems} value={filters.classGroup} onValueChange={(v) => set({ classGroup: v ?? "all" })}>
          <SelectTrigger className="w-48" aria-label="Class group"><SelectValue /></SelectTrigger>
          <SelectContent>
            {groupItems.map((i) => <SelectItem key={i.value} value={i.value}>{i.label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <div className="grid gap-1.5">
        <Label>Device</Label>
        <Select items={deviceItems} value={filters.deviceId} onValueChange={(v) => set({ deviceId: v ?? "all" })}>
          <SelectTrigger className="w-56" aria-label="Device"><SelectValue /></SelectTrigger>
          <SelectContent>
            {deviceItems.map((i) => <SelectItem key={i.value} value={i.value}>{i.label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="from">From</Label>
        <Input id="from" type="date" className="w-40" value={filters.from} max={filters.to || undefined} onChange={(e) => set({ from: e.target.value })} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="to">To</Label>
        <Input id="to" type="date" className="w-40" value={filters.to} min={filters.from || undefined} onChange={(e) => set({ to: e.target.value })} />
      </div>
      {dirty && <Button variant="ghost" onClick={() => onChange(DEFAULT_FILTERS)}>Reset</Button>}
    </div>
  );
}
