"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Download, Plus, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { getStore, type Mission, type MissionTask, type MissionTaskType } from "@/lib/data";
import {
  Card,
  ConfirmDelete,
  EditorShell,
  ErrorList,
  Field,
  MoveButtons,
  NativeSelect,
  inputCls,
  move,
  exportMissions,
  newItemId,
  uniqueId,
  useAllDevices,
} from "./common";
import { validateMission } from "./validate";

const TYPES: { value: MissionTaskType; label: string }[] = [
  { value: "scan", label: "Scan device" },
  { value: "identify_part", label: "Identify part" },
  { value: "complete_procedure", label: "Complete procedure" },
  { value: "pass_quiz", label: "Pass quiz" },
];

export function MissionEditor({ missionId }: { missionId: string }) {
  const router = useRouter();
  const devices = useAllDevices();
  const isNew = missionId === "new";
  const [all, setAll] = useState<Mission[] | null>(null);
  const [draft, setDraft] = useState<Mission | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    getStore()
      .listMissions()
      .then((list) => {
        setAll(list);
        const found = isNew
          ? { id: "new_mission", title: "", description: "", tasks: [] as MissionTask[] }
          : list.find((m) => m.id === missionId);
        setDraft(found ? structuredClone(found) : null);
      });
  }, [missionId, isNew]);

  const back = { backHref: "/missions", backLabel: "All missions" };
  if (!devices || !all) return <p className="text-sm text-muted-foreground">Loading...</p>;
  if (!draft)
    return (
      <EditorShell {...back} title="Mission not found">
        <p className="text-sm text-muted-foreground">No mission with id &quot;{missionId}&quot;.</p>
      </EditorShell>
    );
  const m = draft;
  const set = (n: Mission) => setDraft(n);
  const setTask = (i: number, patch: Partial<MissionTask>) =>
    set({ ...m, tasks: m.tasks.map((t, j) => (j === i ? { ...t, ...patch } : t)) });
  const deviceOpts = devices.map((d) => ({ value: d.id, label: d.name }));

  function changeType(i: number, type: MissionTaskType) {
    const t = m.tasks[i];
    const next: MissionTask = { id: t.id, type, title: t.title, deviceId: t.deviceId };
    if (type === "pass_quiz") next.minScorePct = 70;
    set({ ...m, tasks: m.tasks.map((x, j) => (j === i ? next : x)) });
  }

  async function save() {
    const item = { ...m };
    if (isNew)
      item.id = newItemId(item.title, all!.map((x) => x.id));
    if (!item.deviceId) delete item.deviceId;
    const errs = validateMission(item, devices!);
    setErrors(errs);
    if (errs.length) return;
    await getStore().saveMission(item);
    setAll(isNew ? [...all!, item] : all!.map((x) => (x.id === item.id ? item : x)));
    toast.success("Mission saved");
    if (isNew) router.replace(`/missions/${item.id}`);
    else setDraft(item);
  }

  async function remove() {
    await getStore().deleteMission(m.id);
    toast.success("Mission deleted");
    router.push("/missions");
  }

  return (
    <EditorShell
      {...back}
      title={isNew ? "New mission" : m.title || "Untitled mission"}
      actions={
        <>
          <Button
            variant="outline"
            onClick={async () => setErrors(exportMissions(await getStore().listMissions(), devices))}
          >
            <Download /> Export missions JSON
          </Button>
          {!isNew && (
            <Button variant="destructive" onClick={() => setConfirmOpen(true)}>
              <Trash2 /> Delete
            </Button>
          )}
          <Button onClick={save}>
            <Save /> Save
          </Button>
        </>
      }
    >
      <ErrorList errors={errors} />
      <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-[1fr_16rem]">
          <Field label="Title">
            <input
              className={inputCls}
              value={m.title}
              onChange={(e) => set({ ...m, title: e.target.value })}
              placeholder="Multimeter first steps"
            />
          </Field>
          <Field label="Device (optional)">
            <NativeSelect
              value={m.deviceId ?? ""}
              placeholder="Cross-device"
              options={deviceOpts}
              onChange={(v) => set({ ...m, deviceId: v || undefined })}
            />
          </Field>
        </div>
        <Field label="Description">
          <Textarea
            rows={2}
            value={m.description}
            onChange={(e) => set({ ...m, description: e.target.value })}
          />
        </Field>
        <div className="space-y-2">
          <h2 className="text-sm font-semibold">Tasks</h2>
          {m.tasks.map((t, i) => {
            const dev = devices.find((d) => d.id === t.deviceId);
            return (
              <Card key={i} className="space-y-2">
                <div className="flex items-start gap-3">
                  <span className="mt-1.5 w-6 shrink-0 text-sm text-muted-foreground">{i + 1}.</span>
                  <div className="grid min-w-0 flex-1 gap-2 md:grid-cols-[1fr_12rem]">
                    <input
                      className={inputCls}
                      aria-label={`Task ${i + 1} title`}
                      value={t.title}
                      placeholder="Task title"
                      onChange={(e) => setTask(i, { title: e.target.value })}
                    />
                    <NativeSelect
                      aria-label={`Task ${i + 1} type`}
                      value={t.type}
                      options={TYPES}
                      onChange={(v) => changeType(i, v as MissionTaskType)}
                    />
                  </div>
                  <MoveButtons
                    index={i}
                    count={m.tasks.length}
                    onMove={(dir) => set({ ...m, tasks: move(m.tasks, i, dir) })}
                    onRemove={() => set({ ...m, tasks: m.tasks.filter((_, j) => j !== i) })}
                  />
                </div>
                <div className="grid gap-2 pl-9 md:grid-cols-3">
                  <NativeSelect
                    aria-label={`Task ${i + 1} device`}
                    value={t.deviceId}
                    placeholder="Select device"
                    options={deviceOpts}
                    onChange={(v) =>
                      setTask(i, { deviceId: v, partId: undefined, procedureId: undefined, quizId: undefined })
                    }
                  />
                  {t.type === "identify_part" && (
                    <NativeSelect
                      aria-label={`Task ${i + 1} part`}
                      value={t.partId ?? ""}
                      placeholder="Select part"
                      options={(dev?.parts ?? []).map((p) => ({ value: p.id, label: p.name }))}
                      onChange={(v) => setTask(i, { partId: v })}
                    />
                  )}
                  {t.type === "complete_procedure" && (
                    <NativeSelect
                      aria-label={`Task ${i + 1} procedure`}
                      value={t.procedureId ?? ""}
                      placeholder="Select procedure"
                      options={(dev?.procedures ?? []).map((p) => ({ value: p.id, label: p.title }))}
                      onChange={(v) => setTask(i, { procedureId: v })}
                    />
                  )}
                  {t.type === "pass_quiz" && (
                    <>
                      <NativeSelect
                        aria-label={`Task ${i + 1} quiz`}
                        value={t.quizId ?? ""}
                        placeholder="Select quiz"
                        options={(dev?.quizzes ?? []).map((q) => ({ value: q.id, label: q.title }))}
                        onChange={(v) => setTask(i, { quizId: v })}
                      />
                      <label className="flex items-center gap-2 text-sm text-muted-foreground">
                        Min score %
                        <input
                          type="number"
                          min={1}
                          max={100}
                          className={inputCls}
                          aria-label={`Task ${i + 1} min score`}
                          value={t.minScorePct ?? ""}
                          placeholder="70"
                          step={1}
                          onChange={(e) =>
                            setTask(i, { minScorePct: e.target.value === "" ? undefined : Number(e.target.value) })
                          }
                        />
                      </label>
                    </>
                  )}
                </div>
              </Card>
            );
          })}
          <Button
            variant="outline"
            onClick={() =>
              set({
                ...m,
                tasks: [
                  ...m.tasks,
                  {
                    id: uniqueId(`t${m.tasks.length + 1}`, m.tasks.map((x) => x.id)),
                    type: "scan",
                    title: "",
                    deviceId: m.deviceId ?? "",
                  },
                ],
              })
            }
          >
            <Plus /> Add task
          </Button>
        </div>
      </div>
      <ConfirmDelete open={confirmOpen} onOpenChange={setConfirmOpen} title={`"${m.title}"`} onConfirm={remove} />
    </EditorShell>
  );
}
