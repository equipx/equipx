"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Download, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { getStore, type Device } from "@/lib/data";
import { missionsReferencing, validateDevice } from "@/lib/data/validate";
import {
  ConfirmDelete,
  EditorShell,
  ErrorList,
  deviceForExport,
  downloadJson,
  newItemId,
} from "./common";
import { useDevice } from "./use-device-item";

type Collection = "procedures" | "faults" | "quizzes";

/** Shared load/save/delete/export shell for items stored inside a device. */
export function DeviceItemEditor<T extends { id: string }>({
  deviceId,
  itemId,
  collection,
  basePath,
  noun,
  makeNew,
  validate,
  nameOf,
  render,
}: {
  deviceId: string;
  itemId: string;
  collection: Collection;
  basePath: string;
  noun: string;
  makeNew: (device: Device) => T;
  validate: (item: T, device: Device) => string[];
  nameOf: (item: T) => string;
  render: (item: T, set: (next: T) => void, device: Device) => React.ReactNode;
}) {
  const router = useRouter();
  const loaded = useDevice(deviceId);
  // After a save the store has newer data than the hook loaded; keep the saved copy locally.
  const [saved, setSaved] = useState<Device | null>(null);
  const device = saved && saved.id === deviceId ? saved : loaded;
  const isNew = itemId === "new";
  const [draft, setDraft] = useState<T | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [usedBy, setUsedBy] = useState<string[]>([]);

  useEffect(() => {
    if (!device) return;
    const list = device[collection] as unknown as T[];
    const found = isNew ? makeNew(device) : list.find((x) => x.id === itemId);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDraft(found ? structuredClone(found) : null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [device, itemId]);

  const back = { backHref: basePath, backLabel: `All ${noun === "quiz" ? "quizzes" : noun + "s"}` };

  if (device === undefined) return <p className="text-sm text-muted-foreground">Loading...</p>;
  if (device === null)
    return (
      <EditorShell {...back} title="Device not found">
        <p className="text-sm text-muted-foreground">No device with id &quot;{deviceId}&quot;.</p>
      </EditorShell>
    );
  if (!draft)
    return (
      <EditorShell {...back} title={`${noun[0].toUpperCase()}${noun.slice(1)} not found`}>
        <p className="text-sm text-muted-foreground">
          &quot;{itemId}&quot; does not exist on {device.name}.
        </p>
      </EditorShell>
    );

  const list = device[collection] as unknown as T[];

  async function save() {
    if (!device || !draft) return;
    const item = { ...draft };
    if (isNew) {
      // Ids are derived from the title / symptom on first save.
      // Unique across procedures + faults + quizzes (the validator checks them together).
      const taken = [...device.procedures, ...device.faults, ...device.quizzes].map((x) => x.id);
      item.id = newItemId(nameOf(item), taken);
    }
    const errs = validate(item, device);
    setErrors(errs);
    if (errs.length) return;
    setSaving(true);
    const next = isNew
      ? [...list, item]
      : list.map((x) => (x.id === item.id ? item : x));
    const nextDevice = { ...device, [collection]: next } as Device;
    await getStore().saveDevice(nextDevice);
    setSaved(nextDevice);
    toast.success(`${noun[0].toUpperCase()}${noun.slice(1)} saved`);
    setSaving(false);
    if (isNew) router.replace(`${basePath}/${deviceId}/${item.id}`);
    else setDraft(item);
  }

  async function openDelete() {
    if (!device || !draft) return;
    const ref = { deviceId: device.id } as Parameters<typeof missionsReferencing>[1];
    if (collection === "procedures") ref.procedureId = draft.id;
    else if (collection === "quizzes") ref.quizId = draft.id;
    const used = ref.procedureId || ref.quizId ? missionsReferencing(await getStore().listMissions(), ref) : [];
    setUsedBy(used.map((m) => m.title || m.id));
    setConfirmOpen(true);
  }

  function exportDevice() {
    if (!device) return;
    const errs = validateDevice(device);
    if (errs.length) {
      toast.error(`Export blocked: ${errs[0]}${errs.length > 1 ? ` (+${errs.length - 1} more)` : ""}. Fix it in the Equipment editor.`);
      return;
    }
    downloadJson(`${device.id}.json`, deviceForExport(device));
  }

  async function remove() {
    if (!device) return;
    const nextDevice = { ...device, [collection]: list.filter((x) => x.id !== draft!.id) } as Device;
    await getStore().saveDevice(nextDevice);
    setSaved(nextDevice);
    toast.success(`${noun[0].toUpperCase()}${noun.slice(1)} deleted`);
    router.push(basePath);
  }

  return (
    <EditorShell
      {...back}
      title={isNew ? `New ${noun}` : nameOf(draft) || `Untitled ${noun}`}
      actions={
        <>
          <Button variant="outline" onClick={exportDevice}>
            <Download /> Export device JSON
          </Button>
          {!isNew && (
            <Button variant="destructive" onClick={openDelete}>
              <Trash2 /> Delete
            </Button>
          )}
          <Button onClick={save} disabled={saving}>
            <Save /> Save
          </Button>
        </>
      }
    >
      <p className="-mt-3 mb-5 text-sm text-muted-foreground">
        Device: <span className="font-medium text-foreground">{device.name}</span>
        {!isNew && (
          <>
            {" "}
            · id <code className="rounded bg-muted px-1">{draft.id}</code>
          </>
        )}
      </p>
      <ErrorList errors={errors} />
      {render(draft, (n) => setDraft(n), device)}
      <ConfirmDelete
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={`"${nameOf(draft)}"`}
        onConfirm={remove}
        warning={usedBy.length ? `Used by mission${usedBy.length > 1 ? "s" : ""}: ${usedBy.join(", ")}. Those missions will fail validation until fixed.` : undefined}
      />
    </EditorShell>
  );
}
