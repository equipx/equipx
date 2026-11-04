"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getStore, type Device, type Part } from "@/lib/data";
import { missionsReferencing } from "@/lib/data/validate";
import { cn } from "cn";
import { Field, NumField, StringList } from "./fields";
import { ID_RE, toSnake } from "./validate";

interface Props {
  device: Device;
  onChange: (d: Device) => void;
  selectedPartId: string | null;
  onSelectPart: (id: string | null) => void;
}

export function PartsEditor({ device, onChange, selectedPartId, onSelectPart }: Props) {
  const [newName, setNewName] = useState("");
  const [newId, setNewId] = useState("");
  const [idTouched, setIdTouched] = useState(false);

  const idError = !newId
    ? null
    : !ID_RE.test(newId)
      ? "Use snake_case (a-z, 0-9, _)"
      : device.parts.some((p) => p.id === newId)
        ? "Part id already exists"
        : null;

  const patchPart = (id: string, patch: Partial<Part>) =>
    onChange({ ...device, parts: device.parts.map((p) => (p.id === id ? { ...p, ...patch } : p)) });

  const addPart = () => {
    if (!newName.trim() || !newId || idError) return;
    const number = device.parts.reduce((m, p) => Math.max(m, p.number), 0) + 1;
    onChange({
      ...device,
      parts: [
        ...device.parts,
        {
          id: newId,
          number,
          name: newName.trim(),
          function: "Describe what this part does.",
          usage: "Describe how to use it.",
          safety: [],
        },
      ],
      layout: {
        ...device.layout,
        parts: [
          ...device.layout.parts,
          { id: newId, type: "button", face: "front", position: [0, 0], size: [0.01, 0.01, 0.003] },
        ],
      },
    });
    onSelectPart(newId);
    setNewName("");
    setNewId("");
    setIdTouched(false);
  };

  const deletePart = async (id: string) => {
    const used = device.procedures.find((p) => p.steps.some((s) => s.partId === id));
    if (used) {
      toast.error(`"${id}" is used by procedure "${used.title}". Change that step first.`);
      return;
    }
    const missions = missionsReferencing(await getStore().listMissions(), { deviceId: device.id, partId: id });
    if (missions.length) {
      toast.error(`"${id}" is used by mission "${missions[0].title || missions[0].id}". Change that task first.`);
      return;
    }
    const remaining = device.parts
      .filter((p) => p.id !== id)
      .sort((a, b) => a.number - b.number)
      .map((p, i) => ({ ...p, number: i + 1 }));
    const overrides = { ...(device.hotspotOverrides ?? {}) };
    delete overrides[id];
    onChange({
      ...device,
      parts: remaining,
      layout: { ...device.layout, parts: device.layout.parts.filter((p) => p.id !== id) },
      faults: device.faults.map((f) => ({ ...f, relatedPartIds: f.relatedPartIds.filter((x) => x !== id) })),
      hotspotOverrides: overrides,
    });
    if (selectedPartId === id) onSelectPart(null);
  };

  const sorted = [...device.parts].sort((a, b) => a.number - b.number);

  return (
    <div className="flex flex-col gap-3">
      {sorted.length === 0 && <p className="text-sm text-muted-foreground">No parts yet. Add the first one below.</p>}
      {sorted.map((p) => {
        const sel = p.id === selectedPartId;
        return (
          <div
            key={p.id}
            onClick={() => onSelectPart(p.id)}
            className={cn("rounded-md border p-3", sel && "border-[#2383E2] bg-[#2383E2]/5")}
          >
            <div className="mb-3 flex items-center gap-2">
              <NumField
                label="Part number"
                value={p.number}
                min={1}
                step="1"
                className="w-16"
                onChange={(n) => patchPart(p.id, { number: Math.round(n) })}
              />
              <Input
                aria-label="Part name"
                value={p.name}
                onChange={(e) => patchPart(p.id, { name: e.target.value })}
              />
              <span className="shrink-0 font-mono text-xs text-muted-foreground">{p.id}</span>
              {device.hotspotOverrides?.[p.id] && <Badge variant="secondary">custom hotspot</Badge>}
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${p.name}`}
                onClick={(e) => {
                  e.stopPropagation();
                  deletePart(p.id);
                }}
              >
                <Trash2 />
              </Button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Function">
                <Textarea
                  rows={3}
                  value={p.function}
                  onChange={(e) => patchPart(p.id, { function: e.target.value })}
                />
              </Field>
              <Field label="Usage">
                <Textarea rows={3} value={p.usage} onChange={(e) => patchPart(p.id, { usage: e.target.value })} />
              </Field>
            </div>
            <div className="mt-3">
              <Field label="Safety tips">
                <StringList
                  items={p.safety}
                  addLabel="Add tip"
                  onChange={(safety) => patchPart(p.id, { safety })}
                />
              </Field>
            </div>
          </div>
        );
      })}

      <div className="rounded-md border border-dashed p-3">
        <p className="mb-2 text-sm font-medium">Add part</p>
        <div className="flex flex-wrap items-start gap-2">
          <Input
            className="w-48"
            placeholder="Name, e.g. Range knob"
            value={newName}
            onChange={(e) => {
              setNewName(e.target.value);
              if (!idTouched) setNewId(toSnake(e.target.value));
            }}
          />
          <div>
            <Input
              className="w-48 font-mono"
              placeholder="part_id"
              aria-invalid={!!idError}
              value={newId}
              onChange={(e) => {
                setIdTouched(true);
                setNewId(e.target.value);
              }}
            />
            {idError && <p className="mt-1 text-xs text-destructive">{idError}</p>}
          </div>
          <Button onClick={addPart} disabled={!newName.trim() || !newId || !!idError}>
            <Plus />
            Add part
          </Button>
        </div>
        <p className="mt-2 text-xs text-muted-foreground">
          Also adds a default entry to the Layout tab (front face, 10 mm button) for you to adjust.
        </p>
      </div>
    </div>
  );
}
