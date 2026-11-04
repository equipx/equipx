"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Fault } from "@/lib/data";
import { Card, Field, MoveButtons, inputCls, move, uniqueId } from "./common";
import { DeviceItemEditor } from "./item-editor";
import { validateFault } from "./validate";

export function FaultEditor({ deviceId, itemId }: { deviceId: string; itemId: string }) {
  return (
    <DeviceItemEditor<Fault>
      deviceId={deviceId}
      itemId={itemId}
      collection="faults"
      basePath="/faults"
      noun="fault"
      makeNew={() => ({
        id: "new_fault",
        symptom: "",
        options: [
          { id: "a", text: "" },
          { id: "b", text: "" },
        ],
        correctOptionId: "a",
        explanation: "",
        relatedPartIds: [],
      })}
      validate={validateFault}
      nameOf={(f) => f.symptom}
      render={(f, set, device) => (
        <div className="space-y-6">
          <Field label="Symptom">
            <Textarea
              value={f.symptom}
              rows={2}
              onChange={(e) => set({ ...f, symptom: e.target.value })}
              placeholder="The display shows OL on every range"
            />
          </Field>
          <div className="space-y-2">
            <h2 className="text-sm font-semibold">Options</h2>
            <p className="text-xs text-muted-foreground">Select the radio next to the correct diagnosis.</p>
            {f.options.map((o, i) => (
              <Card key={o.id + i} className="flex items-center gap-3">
                <input
                  type="radio"
                  name="correct"
                  aria-label={`Option ${i + 1} is correct`}
                  checked={f.correctOptionId === o.id}
                  onChange={() => set({ ...f, correctOptionId: o.id })}
                  className="size-4 accent-[#2383E2]"
                />
                <code className="w-10 shrink-0 text-xs text-muted-foreground">{o.id}</code>
                <input
                  className={inputCls}
                  aria-label={`Option ${i + 1} text`}
                  value={o.text}
                  onChange={(e) =>
                    set({
                      ...f,
                      options: f.options.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)),
                    })
                  }
                  placeholder="Diagnosis text"
                />
                <MoveButtons
                  index={i}
                  count={f.options.length}
                  removeDisabled={f.options.length <= 2}
                  onMove={(dir) => set({ ...f, options: move(f.options, i, dir) })}
                  onRemove={() => {
                    const options = f.options.filter((_, j) => j !== i);
                    set({
                      ...f,
                      options,
                      correctOptionId:
                        f.correctOptionId === o.id ? options[0].id : f.correctOptionId,
                    });
                  }}
                />
              </Card>
            ))}
            <Button
              variant="outline"
              onClick={() => {
                const id = uniqueId(
                  String.fromCharCode(97 + f.options.length),
                  f.options.map((x) => x.id),
                );
                set({ ...f, options: [...f.options, { id, text: "" }] });
              }}
            >
              <Plus /> Add option
            </Button>
          </div>
          <Field label="Explanation">
            <Textarea
              value={f.explanation}
              rows={3}
              onChange={(e) => set({ ...f, explanation: e.target.value })}
              placeholder="Why this is the right answer"
            />
          </Field>
          <div className="space-y-2">
            <h2 className="text-sm font-semibold">Related parts</h2>
            <div className="grid gap-1 rounded-md border p-3 sm:grid-cols-2">
              {device.parts.map((p) => (
                <label key={p.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-[#2383E2]"
                    checked={f.relatedPartIds.includes(p.id)}
                    onChange={(e) =>
                      set({
                        ...f,
                        relatedPartIds: e.target.checked
                          ? [...f.relatedPartIds, p.id]
                          : f.relatedPartIds.filter((x) => x !== p.id),
                      })
                    }
                  />
                  {p.number}. {p.name}
                </label>
              ))}
            </div>
          </div>
        </div>
      )}
    />
  );
}
