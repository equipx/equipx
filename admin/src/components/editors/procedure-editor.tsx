"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Procedure } from "@/lib/data";
import { Card, Field, MoveButtons, NativeSelect, inputCls, move } from "./common";
import { DeviceItemEditor } from "./item-editor";
import { validateProcedure } from "./validate";

export function ProcedureEditor({ deviceId, itemId }: { deviceId: string; itemId: string }) {
  return (
    <DeviceItemEditor<Procedure>
      deviceId={deviceId}
      itemId={itemId}
      collection="procedures"
      basePath="/procedures"
      noun="procedure"
      makeNew={(d) => ({
        id: "new_procedure",
        title: "",
        steps: [{ text: "", partId: d.parts[0]?.id ?? "" }],
      })}
      validate={validateProcedure}
      nameOf={(p) => p.title}
      render={(p, set, device) => {
        const partOpts = device.parts.map((x) => ({ value: x.id, label: `${x.number}. ${x.name}` }));
        return (
          <div className="space-y-6">
            <Field label="Title">
              <input
                className={inputCls}
                value={p.title}
                onChange={(e) => set({ ...p, title: e.target.value })}
                placeholder="Measure DC voltage"
              />
            </Field>
            <div className="space-y-2">
              <h2 className="text-sm font-semibold">Steps</h2>
              {p.steps.map((s, i) => (
                <Card key={i} className="flex gap-3">
                  <span className="mt-1 w-6 shrink-0 text-sm text-muted-foreground">{i + 1}.</span>
                  <div className="grid min-w-0 flex-1 gap-2 md:grid-cols-[1fr_14rem]">
                    <Textarea
                      aria-label={`Step ${i + 1} text`}
                      value={s.text}
                      rows={2}
                      onChange={(e) =>
                        set({
                          ...p,
                          steps: p.steps.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)),
                        })
                      }
                      placeholder="What the student should do"
                    />
                    <NativeSelect
                      aria-label={`Step ${i + 1} part`}
                      value={s.partId}
                      placeholder="Select part"
                      options={partOpts}
                      onChange={(v) =>
                        set({
                          ...p,
                          steps: p.steps.map((x, j) => (j === i ? { ...x, partId: v } : x)),
                        })
                      }
                    />
                  </div>
                  <MoveButtons
                    index={i}
                    count={p.steps.length}
                    onMove={(dir) => set({ ...p, steps: move(p.steps, i, dir) })}
                    onRemove={() => set({ ...p, steps: p.steps.filter((_, j) => j !== i) })}
                  />
                </Card>
              ))}
              <Button
                variant="outline"
                onClick={() =>
                  set({ ...p, steps: [...p.steps, { text: "", partId: device.parts[0]?.id ?? "" }] })
                }
              >
                <Plus /> Add step
              </Button>
            </div>
          </div>
        );
      }}
    />
  );
}
