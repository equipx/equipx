"use client";

import type { Device, LayoutBody, LayoutFace, LayoutPart, PartType } from "@/lib/data";
import { Input } from "@/components/ui/input";
import { cn } from "cn";
import { Field, NumField, Pick } from "./fields";

const TYPES: readonly PartType[] = ["screen", "knob", "button", "switch", "port", "led", "display", "probe", "slot", "handle"];
const FACES: readonly LayoutFace[] = ["front", "top", "left", "right", "back"];

function ColorField({ value, onChange, label }: { value: string; onChange: (c: string) => void; label: string }) {
  const hex = /^#[0-9A-Fa-f]{6}$/.test(value);
  const valid = value === "" || hex;
  return (
    <div className="flex items-center gap-2">
      <input
        type="color"
        aria-label={label}
        value={hex ? value : "#cccccc"}
        onChange={(e) => onChange(e.target.value.toUpperCase())}
        className="size-8 shrink-0 cursor-pointer rounded-md border bg-transparent p-0.5"
      />
      <Input className="font-mono" value={value} aria-invalid={!valid} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

export function LayoutEditor({
  device,
  onChange,
  selectedPartId,
  onSelectPart,
}: {
  device: Device;
  onChange: (d: Device) => void;
  selectedPartId: string | null;
  onSelectPart: (id: string | null) => void;
}) {
  const body = device.layout.body;
  const setBody = (patch: Partial<LayoutBody>) =>
    onChange({ ...device, layout: { ...device.layout, body: { ...body, ...patch } } });
  const setPart = (id: string, patch: Partial<LayoutPart>) =>
    onChange({
      ...device,
      layout: { ...device.layout, parts: device.layout.parts.map((p) => (p.id === id ? { ...p, ...patch } : p)) },
    });

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-md border p-3">
        <p className="mb-2 text-sm font-medium">Body (metres)</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          <Field label="Width"><NumField value={body.width} onChange={(n) => setBody({ width: n })} /></Field>
          <Field label="Height"><NumField value={body.height} onChange={(n) => setBody({ height: n })} /></Field>
          <Field label="Depth"><NumField value={body.depth} onChange={(n) => setBody({ depth: n })} /></Field>
          <Field label="Corner radius"><NumField value={body.cornerRadius} onChange={(n) => setBody({ cornerRadius: n })} /></Field>
          <Field label="Color"><ColorField label="Body color" value={body.color} onChange={(c) => setBody({ color: c })} /></Field>
        </div>
      </div>

      {device.layout.parts.length === 0 && (
        <p className="text-sm text-muted-foreground">No layout parts. Add parts on the Parts tab.</p>
      )}
      {device.layout.parts.map((lp) => {
        const part = device.parts.find((p) => p.id === lp.id);
        return (
          <div
            key={lp.id}
            onClick={() => onSelectPart(lp.id)}
            className={cn("rounded-md border p-3", lp.id === selectedPartId && "border-[#2383E2] bg-[#2383E2]/5")}
          >
            <p className="mb-2 text-sm font-medium">
              {part ? `${part.number}. ${part.name}` : lp.id}{" "}
              <span className="font-mono text-xs font-normal text-muted-foreground">{lp.id}</span>
            </p>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Field label="Type"><Pick label="Type" value={lp.type} options={TYPES} onChange={(type) => setPart(lp.id, { type })} /></Field>
              <Field label="Face"><Pick label="Face" value={lp.face} options={FACES} onChange={(face) => setPart(lp.id, { face })} /></Field>
              <Field label="Position u"><NumField value={lp.position[0]} onChange={(n) => setPart(lp.id, { position: [n, lp.position[1]] })} /></Field>
              <Field label="Position v"><NumField value={lp.position[1]} onChange={(n) => setPart(lp.id, { position: [lp.position[0], n] })} /></Field>
              <Field label="Size w"><NumField value={lp.size[0]} onChange={(n) => setPart(lp.id, { size: [n, lp.size[1], lp.size[2]] })} /></Field>
              <Field label="Size h"><NumField value={lp.size[1]} onChange={(n) => setPart(lp.id, { size: [lp.size[0], n, lp.size[2]] })} /></Field>
              <Field label="Size d (protrusion)"><NumField value={lp.size[2]} onChange={(n) => setPart(lp.id, { size: [lp.size[0], lp.size[1], n] })} /></Field>
              <Field label="Color (optional)">
                <ColorField
                  label="Part color"
                  value={lp.color ?? ""}
                  onChange={(c) => setPart(lp.id, { color: c === "" ? undefined : c })}
                />
              </Field>
            </div>
          </div>
        );
      })}
    </div>
  );
}
