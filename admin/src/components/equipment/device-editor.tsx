"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Download, Info, Save, Trash2, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { ModelPreview, type HotspotOverrides } from "@/components/model/model-preview";
import { getStore, type Device } from "@/lib/data";
import { Field, StringList } from "./fields";
import { LayoutEditor } from "./layout-editor";
import { PartsEditor } from "./parts-editor";
import { missionsReferencing } from "@/lib/data/validate";
import { downloadText, toContentJson, validateDevice } from "./validate";

export function DeviceEditor({ id }: { id: string }) {
  const router = useRouter();
  const [device, setDevice] = useState<Device | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [saved, setSaved] = useState("");
  const [selectedPartId, setSelectedPartId] = useState<string | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [usedBy, setUsedBy] = useState<string[]>([]);

  useEffect(() => {
    getStore()
      .getDevice(id)
      .then((d) => {
        if (!d) return setNotFound(true);
        const clone = structuredClone(d);
        setDevice(clone);
        setSaved(JSON.stringify(clone));
      });
  }, [id]);

  // Export needs a complete device; Save only blocks on errors that are not just "still empty".
  const errors = useMemo(() => (device ? validateDevice(device) : []), [device]);
  const saveErrors = useMemo(() => (device ? validateDevice(device, { requireComplete: false }) : []), [device]);

  if (notFound) {
    return (
      <div>
        <BackLink />
        <p className="text-sm text-muted-foreground">No device with id &quot;{id}&quot;.</p>
      </div>
    );
  }
  if (!device) return null;

  const dirty = JSON.stringify(device) !== saved;
  const set = (patch: Partial<Device>) => setDevice({ ...device, ...patch });
  const overrides: HotspotOverrides = device.hotspotOverrides ?? {};

  const save = async () => {
    await getStore().saveDevice(device);
    setSaved(JSON.stringify(device));
    toast.success("Saved locally. Export JSON to /content and run npm run sync to ship it.");
  };

  const remove = async () => {
    await getStore().deleteDevice(device.id);
    toast.success(`Deleted ${device.name}`);
    router.push("/equipment");
  };

  const place = (partId: string, position: [number, number, number], normal: [number, number, number]) => {
    setDevice((d) => (d ? { ...d, hotspotOverrides: { ...(d.hotspotOverrides ?? {}), [partId]: { position, normal } } } : d));
    toast.success(`Hotspot moved for ${partId}. Save to keep it.`);
  };
  const reset = (partId: string) => {
    setDevice((d) => {
      if (!d) return d;
      const next = { ...(d.hotspotOverrides ?? {}) };
      delete next[partId];
      return { ...d, hotspotOverrides: next };
    });
  };

  return (
    <div>
      <BackLink />
      <header className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">{device.name || "Untitled device"}</h1>
          <p className="mt-1 font-mono text-xs text-muted-foreground">{device.id}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" onClick={() => downloadText(`${device.id}.json`, toContentJson(device))} disabled={errors.length > 0}>
            <Download />
            Export JSON
          </Button>
          <Button
            variant="destructive"
            onClick={async () => {
              const used = missionsReferencing(await getStore().listMissions(), { deviceId: device.id });
              setUsedBy(used.map((m) => m.title || m.id));
              setConfirmDelete(true);
            }}
          >
            <Trash2 />
            Delete device
          </Button>
          <Button onClick={save} disabled={!dirty || saveErrors.length > 0}>
            <Save />
            {dirty ? "Save" : "Saved"}
          </Button>
        </div>
      </header>

      <div className="mb-4 flex gap-2 rounded-md border-l-4 border-l-[#DFAB01] bg-[#FBF3DB] p-3 text-sm">
        <Info className="mt-0.5 size-4 shrink-0" />
        <p>
          Edits are saved locally in this browser (mock store). To ship them to the app, export the JSON into{" "}
          <code className="text-xs">/content/{device.id}.json</code> (and list it in <code className="text-xs">index.json</code>), then
          run <code className="text-xs">npm run sync</code>. Custom hotspot positions are admin-only and are not part of the export.
        </p>
      </div>

      {errors.length > 0 && (
        <div className="mb-4 rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm">
          <p className="mb-1 flex items-center gap-2 font-medium text-destructive">
            <TriangleAlert className="size-4" />
            {errors.length} validation problem{errors.length > 1 ? "s" : ""} (export is disabled; save is disabled while errors other than missing items remain)
          </p>
          <ul className="list-disc pl-5 text-destructive">
            {errors.map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Tabs defaultValue="overview">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="parts">Parts ({device.parts.length})</TabsTrigger>
            <TabsTrigger value="layout">Layout</TabsTrigger>
            <TabsTrigger value="safety">Safety rules</TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="mt-3 flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Name"><Input value={device.name} onChange={(e) => set({ name: e.target.value })} /></Field>
              <Field label="Category"><Input value={device.category} onChange={(e) => set({ category: e.target.value })} /></Field>
            </div>
            <Field label="Summary (one line)"><Input value={device.summary} onChange={(e) => set({ summary: e.target.value })} /></Field>
            <Field label="Overview"><Textarea rows={5} value={device.overview} onChange={(e) => set({ overview: e.target.value })} /></Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Camera orbit (model.cameraOrbit)">
                <Input
                  className="font-mono"
                  value={device.model.cameraOrbit}
                  onChange={(e) => set({ model: { ...device.model, cameraOrbit: e.target.value } })}
                />
              </Field>
              <Field label="Model credit (leave empty for none)">
                <Input
                  value={device.model.credit ?? ""}
                  onChange={(e) => set({ model: { ...device.model, credit: e.target.value === "" ? null : e.target.value } })}
                />
              </Field>
            </div>
          </TabsContent>

          <TabsContent value="parts" className="mt-3">
            <PartsEditor device={device} onChange={setDevice} selectedPartId={selectedPartId} onSelectPart={setSelectedPartId} />
          </TabsContent>

          <TabsContent value="layout" className="mt-3">
            <LayoutEditor device={device} onChange={setDevice} selectedPartId={selectedPartId} onSelectPart={setSelectedPartId} />
          </TabsContent>

          <TabsContent value="safety" className="mt-3">
            <StringList
              items={device.safetyRules}
              addLabel="Add rule"
              placeholder="Safety rule"
              onChange={(safetyRules) => set({ safetyRules })}
            />
          </TabsContent>
        </Tabs>

        <aside className="xl:sticky xl:top-6 xl:self-start">
          <p className="mb-2 text-sm font-medium">3D preview</p>
          <ModelPreview
            key={device.id}
            deviceId={device.id}
            parts={device.parts}
            cameraOrbit={device.model.cameraOrbit}
            selectedPartId={selectedPartId}
            onSelectPart={setSelectedPartId}
            overrides={overrides}
            editMode={editMode}
            onEditModeChange={setEditMode}
            onPlace={place}
            onResetOverride={reset}
          />
          <p className="mt-2 text-xs text-muted-foreground">
            The model shows the last generated layout; layout edits appear after export and <code>npm run sync</code>.
          </p>
        </aside>
      </div>

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {device.name}?</DialogTitle>
            <DialogDescription>
              This removes the device from the local admin store. Content in /content is not touched.
            </DialogDescription>
            {usedBy.length > 0 && (
              <p className="text-sm font-medium text-destructive">
                Used by mission{usedBy.length > 1 ? "s" : ""}: {usedBy.join(", ")}. Those missions will fail validation
                until fixed.
              </p>
            )}
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>Cancel</Button>
            <Button variant="destructive" onClick={remove}>Delete device</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/equipment" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" />
      Equipment
    </Link>
  );
}
