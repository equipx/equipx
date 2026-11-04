"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { getStore } from "@/lib/data";
import { Field } from "./fields";
import { ID_RE, RESERVED_IDS, newDevice, toSnake } from "./validate";

export function NewDeviceButton({ existingIds, disabled }: { existingIds: string[]; disabled?: boolean }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [id, setId] = useState("");
  const [idTouched, setIdTouched] = useState(false);
  const [category, setCategory] = useState("");
  const [summary, setSummary] = useState("");

  const idError = !id ? null : !ID_RE.test(id) ? "Use snake_case (start with a letter)" : RESERVED_IDS.includes(id) ? "This id is reserved" : existingIds.includes(id) ? "Id already exists" : null;
  const ok = name.trim() && category.trim() && summary.trim() && id && !idError;

  const create = async () => {
    await getStore().saveDevice(newDevice(id, name.trim(), category.trim(), summary.trim()));
    setOpen(false);
    router.push(`/equipment/${id}`);
  };

  return (
    <>
      <Button onClick={() => setOpen(true)} disabled={disabled}>
        <Plus />
        New device
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New device</DialogTitle>
            <DialogDescription>Creates an empty device in the local store. Add parts on the next screen.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <Field label="Name">
              <Input
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (!idTouched) setId(toSnake(e.target.value));
                }}
              />
            </Field>
            <Field label="Id (snake_case)">
              <Input
                className="font-mono"
                value={id}
                aria-invalid={!!idError}
                onChange={(e) => {
                  setIdTouched(true);
                  setId(e.target.value);
                }}
              />
              {idError && <p className="mt-1 text-xs text-destructive">{idError}</p>}
            </Field>
            <Field label="Category"><Input value={category} onChange={(e) => setCategory(e.target.value)} /></Field>
            <Field label="Summary"><Input value={summary} onChange={(e) => setSummary(e.target.value)} /></Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={create} disabled={!ok}>Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
