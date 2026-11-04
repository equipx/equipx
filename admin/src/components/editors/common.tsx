"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowDown, ArrowLeft, ArrowUp, Info, Trash2 } from "lucide-react";
import { cn } from "cn";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { toast } from "sonner";
import { getStore, type Device, type Mission } from "@/lib/data";
import { validateMissions } from "@/lib/data/validate";

export { ID_RE, slugify, uniqueId, newItemId } from "@/lib/data/validate";

/** Content shape: strips admin-only fields. */
export function deviceForExport(d: Device): Omit<Device, "hotspotOverrides"> {
  const copy = { ...d };
  delete copy.hotspotOverrides;
  return copy;
}

export function downloadJson(filename: string, data: unknown) {
  const blob = new Blob([JSON.stringify(data, null, 2) + "\n"], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** Validates every mission against the current devices; downloads missions.json only when clean. Returns the errors. */
export function exportMissions(missions: Mission[], devices: Device[]): string[] {
  const errs = validateMissions(missions, devices);
  if (errs.length) {
    toast.error(`Export blocked: ${errs.length} validation problem${errs.length > 1 ? "s" : ""}.`);
    return errs;
  }
  downloadJson("missions.json", { missions });
  return [];
}

export function move<T>(arr: T[], i: number, dir: -1 | 1): T[] {
  const j = i + dir;
  if (j < 0 || j >= arr.length) return arr;
  const next = [...arr];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

/** Load every device (full content). */
export function useAllDevices() {
  const [devices, setDevices] = useState<Device[] | null>(null);
  useEffect(() => {
    let alive = true;
    (async () => {
      const store = getStore();
      const entries = await store.listDevices();
      const all = (await Promise.all(entries.map((e) => store.getDevice(e.id)))).filter(
        (d): d is Device => !!d,
      );
      all.sort((a, b) => a.name.localeCompare(b.name));
      if (alive) setDevices(all);
    })();
    return () => {
      alive = false;
    };
  }, []);
  return devices;
}

export const inputCls =
  "h-8 w-full rounded-md border border-input bg-transparent px-2.5 text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50";

export function NativeSelect({
  value,
  onChange,
  options,
  placeholder,
  className,
  ...rest
}: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
  className?: string;
} & Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "onChange" | "value">) {
  return (
    <select
      {...rest}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={cn(inputCls, "pr-6", className)}
    >
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

export function Field({
  label,
  children,
  hint,
  className,
}: {
  label: string;
  children: React.ReactNode;
  hint?: string;
  className?: string;
}) {
  return (
    <label className={cn("block space-y-1", className)}>
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      {children}
      {hint && <span className="block text-xs text-muted-foreground">{hint}</span>}
    </label>
  );
}

export function Callout({ children }: { children: React.ReactNode }) {
  return (
    <div className="mb-6 flex gap-2 rounded-md border border-l-4 border-l-amber-400 bg-[#FBF3DB] px-3 py-2 text-sm">
      <Info className="mt-0.5 size-4 shrink-0 text-amber-700" />
      <div>{children}</div>
    </div>
  );
}

export const LOCAL_EDITS_NOTE = (
  <>
    Edits are stored locally in this browser only. To publish them, use <b>Export JSON</b>, copy the
    result into <code className="rounded bg-black/5 px-1">/content</code> and run{" "}
    <code className="rounded bg-black/5 px-1">npm run sync</code>.
  </>
);

export function MoveButtons({
  index,
  count,
  onMove,
  onRemove,
  removeDisabled,
}: {
  index: number;
  count: number;
  onMove: (dir: -1 | 1) => void;
  onRemove: () => void;
  removeDisabled?: boolean;
}) {
  return (
    <div className="flex shrink-0 items-center gap-0.5">
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Move up"
        disabled={index === 0}
        onClick={() => onMove(-1)}
      >
        <ArrowUp />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Move down"
        disabled={index === count - 1}
        onClick={() => onMove(1)}
      >
        <ArrowDown />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label="Remove"
        disabled={removeDisabled}
        onClick={onRemove}
      >
        <Trash2 />
      </Button>
    </div>
  );
}

export function ConfirmDelete({
  open,
  onOpenChange,
  title,
  onConfirm,
  warning,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  onConfirm: () => void;
  /** Shown in red, e.g. missions that reference the item. */
  warning?: string;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete {title}?</DialogTitle>
          <DialogDescription>
            This removes it from the local store. Missions referencing it will fail validation until
            fixed.
          </DialogDescription>
          {warning && <p className="text-sm font-medium text-destructive">{warning}</p>}
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={onConfirm}>
            Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function ErrorList({ errors, heading }: { errors: string[]; heading?: string }) {
  if (!errors.length) return null;
  return (
    <div
      role="alert"
      className="mb-4 rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive"
    >
      <p className="mb-1 font-medium">
        {heading ? `${heading}: ` : "Fix "}
        {errors.length} issue{errors.length > 1 ? "s" : ""}
        {heading ? "" : " before saving"}
      </p>
      <ul className="list-disc pl-5">
        {errors.map((e, i) => (
          <li key={i}>{e}</li>
        ))}
      </ul>
    </div>
  );
}

export function EditorShell({
  backHref,
  backLabel,
  title,
  actions,
  children,
}: {
  backHref: string;
  backLabel: string;
  title: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div>
      <Link
        href={backHref}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" /> {backLabel}
      </Link>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <div className="flex items-center gap-2">{actions}</div>
      </div>
      <Callout>{LOCAL_EDITS_NOTE}</Callout>
      {children}
    </div>
  );
}

export function Card({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("rounded-md border bg-background p-3", className)}>{children}</div>;
}
