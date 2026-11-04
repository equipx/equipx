"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Box, MousePointerClick, RotateCcw } from "lucide-react";
import type { ModelViewerElement } from "@google/model-viewer";
import { Button } from "@/components/ui/button";
import { cn } from "cn";
import type { HotspotsFile } from "@/lib/data";

export type Vec3 = [number, number, number];
export type HotspotOverrides = Record<string, { position: Vec3; normal: Vec3 }>;

export interface PreviewPart {
  id: string;
  number: number;
  name: string;
}

interface Props {
  deviceId: string;
  parts: PreviewPart[];
  cameraOrbit: string;
  selectedPartId: string | null;
  onSelectPart: (id: string | null) => void;
  overrides: HotspotOverrides;
  /** Hotspot editor mode: clicking the surface places the selected part's hotspot. */
  editMode: boolean;
  onEditModeChange: (on: boolean) => void;
  onPlace: (partId: string, position: Vec3, normal: Vec3) => void;
  onResetOverride: (partId: string) => void;
}

const ACCENT = "#2383E2";
const OFFSET = 0.002; // same 2 mm offset the generator uses
const r5 = (n: number) => Math.round(n * 1e5) / 1e5;

type Status = "loading" | "ready" | "missing";

export function ModelPreview({
  deviceId,
  parts,
  cameraOrbit,
  selectedPartId,
  onSelectPart,
  overrides,
  editMode,
  onEditModeChange,
  onPlace,
  onResetOverride,
}: Props) {
  const [status, setStatus] = useState<Status>("loading");
  const [generated, setGenerated] = useState<HotspotsFile["hotspots"]>([]);
  const [modelLoaded, setModelLoaded] = useState(false);
  const [picked, setPicked] = useState<string | null | undefined>(undefined);
  const viewerRef = useRef<ModelViewerElement | null>(null);
  const originals = useRef<Map<string, number[]>>(new Map());

  // Latest props for the native event listeners.
  const live = useRef({ parts, selectedPartId, editMode, onSelectPart, onPlace });
  useEffect(() => {
    live.current = { parts, selectedPartId, editMode, onSelectPart, onPlace };
  });

  // Register <model-viewer> on the client only.
  useEffect(() => {
    void import("@google/model-viewer");
  }, []);

  // Check the generated GLB + hotspots exist (they are produced by `npm run sync`).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [glb, hs] = await Promise.all([
          fetch(`/models/${deviceId}.glb`, { method: "HEAD" }),
          fetch(`/models/${deviceId}.hotspots.json`),
        ]);
        const ctype = glb.headers.get("content-type") ?? "";
        if (!glb.ok || ctype.includes("text/html") || !hs.ok) throw new Error("missing");
        const json = (await hs.json()) as HotspotsFile;
        if (cancelled) return;
        setGenerated(json.hotspots ?? []);
        setStatus("ready");
      } catch {
        if (!cancelled) setStatus("missing");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deviceId]);

  const attachViewer = useCallback((el: ModelViewerElement | null) => {
    viewerRef.current = el;
  }, []);

  // Load + click handling (native listeners; drag vs click distinguished by distance).
  useEffect(() => {
    const el = viewerRef.current;
    if (status !== "ready" || !el) return;
    originals.current = new Map();
    const onLoad = () => setModelLoaded(true);
    el.addEventListener("load", onLoad);
    if ((el as unknown as { loaded?: boolean }).loaded) onLoad();

    let down: { x: number; y: number } | null = null;
    const onDown = (e: PointerEvent) => {
      down = { x: e.clientX, y: e.clientY };
    };
    const onUp = (e: PointerEvent) => {
      if (!down) return;
      // In edit mode a click on a slotted hotspot button must not also re-place the hotspot.
      if (live.current.editMode && (e.target as Element | null)?.closest?.('[slot^="hotspot-"]')) {
        down = null;
        return;
      }
      const moved = Math.hypot(e.clientX - down.x, e.clientY - down.y);
      down = null;
      if (moved > 5) return;
      const rect = el.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      const material = el.materialFromPoint(x, y);
      const name = material?.name ?? null;
      setPicked(name);
      const cur = live.current;
      if (cur.editMode) {
        if (!cur.selectedPartId) return;
        const hit = el.positionAndNormalFromPoint(x, y);
        if (!hit) return;
        const n: Vec3 = [hit.normal.x, hit.normal.y, hit.normal.z];
        const pos: Vec3 = [
          r5(hit.position.x + n[0] * OFFSET),
          r5(hit.position.y + n[1] * OFFSET),
          r5(hit.position.z + n[2] * OFFSET),
        ];
        cur.onPlace(cur.selectedPartId, pos, [r5(n[0]), r5(n[1]), r5(n[2])]);
      } else if (name && cur.parts.some((p) => p.id === name)) {
        cur.onSelectPart(name);
      }
    };
    el.addEventListener("pointerdown", onDown);
    el.addEventListener("pointerup", onUp);
    return () => {
      el.removeEventListener("load", onLoad);
      el.removeEventListener("pointerdown", onDown);
      el.removeEventListener("pointerup", onUp);
    };
  }, [status]);

  // Highlight the selected part's material.
  useEffect(() => {
    const el = viewerRef.current;
    if (!modelLoaded || !el?.model) return;
    for (const mat of el.model.materials) {
      const pbr = mat.pbrMetallicRoughness;
      if (!originals.current.has(mat.name)) {
        originals.current.set(mat.name, [...pbr.baseColorFactor]);
      }
      if (mat.name === selectedPartId) {
        pbr.setBaseColorFactor(ACCENT);
      } else {
        const o = originals.current.get(mat.name);
        if (o) pbr.setBaseColorFactor(o as [number, number, number, number]);
      }
    }
  }, [selectedPartId, modelLoaded]);

  const genById = new Map(generated.map((h) => [h.partId, h]));
  const selected = parts.find((p) => p.id === selectedPartId) ?? null;
  const overridden = parts.filter((p) => overrides[p.id]);

  if (status === "missing") {
    return (
      <div className="flex flex-col items-center justify-center rounded-md border border-dashed px-6 py-16 text-center">
        <Box className="mb-3 size-6 text-muted-foreground" />
        <p className="text-sm font-medium">No 3D model yet</p>
        <p className="mt-1 max-w-xs text-sm text-muted-foreground">
          Run <code className="rounded bg-muted px-1 py-0.5 text-xs">npm run sync</code> to generate the model from this
          device&apos;s layout (after exporting its JSON to <code className="text-xs">/content</code>).
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="relative overflow-hidden rounded-md border bg-[#F7F7F5]">
        {status === "ready" ? (
          <model-viewer
            ref={attachViewer as unknown as React.Ref<HTMLElement>}
            src={`/models/${deviceId}.glb`}
            alt={`3D model of ${deviceId}`}
            camera-controls
            camera-orbit={cameraOrbit || undefined}
            shadow-intensity="0"
            interaction-prompt="none"
            touch-action="pan-y"
            style={{ width: "100%", height: 380, background: "#F7F7F5", cursor: editMode ? "crosshair" : "grab" }}
          >
            {parts.map((p) => {
              const o = overrides[p.id];
              const h = o ?? genById.get(p.id);
              if (!h) return null;
              const sel = p.id === selectedPartId;
              return (
                <button
                  key={p.id}
                  type="button"
                  slot={`hotspot-${p.id}`}
                  data-position={`${h.position[0]}m ${h.position[1]}m ${h.position[2]}m`}
                  data-normal={`${h.normal[0]}m ${h.normal[1]}m ${h.normal[2]}m`}
                  data-visibility-attribute="visible"
                  title={`${p.number}. ${p.name}${o ? " (custom position)" : ""}`}
                  onClick={() => onSelectPart(p.id)}
                  className={cn(
                    "flex size-6 items-center justify-center rounded-full border text-xs font-medium opacity-30 transition-opacity data-visible:opacity-100",
                    sel ? "text-white" : "bg-white text-[#37352F]",
                    o && !sel && "border-dashed",
                  )}
                  style={{
                    borderWidth: 1,
                    borderColor: sel ? ACCENT : "#37352F",
                    background: sel ? ACCENT : "#fff",
                    color: sel ? "#fff" : "#37352F",
                    padding: 0,
                  }}
                >
                  {p.number}
                </button>
              );
            })}
          </model-viewer>
        ) : (
          <div className="flex h-[380px] items-center justify-center text-sm text-muted-foreground">Loading…</div>
        )}
        {status === "ready" && !modelLoaded && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-muted-foreground">
            Loading model…
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="sm"
          variant={editMode ? "default" : "outline"}
          onClick={() => onEditModeChange(!editMode)}
        >
          <MousePointerClick />
          {editMode ? "Placing hotspots" : "Edit hotspots"}
        </Button>
        {selected && overrides[selected.id] && (
          <Button size="sm" variant="outline" onClick={() => onResetOverride(selected.id)}>
            <RotateCcw />
            Reset #{selected.number} to generated
          </Button>
        )}
      </div>

      <p className="text-xs text-muted-foreground">
        {editMode
          ? selected
            ? `Click the model surface to place the hotspot for #${selected.number} ${selected.name}.`
            : "Select a part in the Parts list (or a hotspot), then click the model surface."
          : "Drag to orbit. Click a part or hotspot to highlight it."}
      </p>
      <p className="text-xs text-muted-foreground">
        Picked material/node:{" "}
        <span className="font-mono text-foreground">
          {picked === undefined ? "click the model" : (picked ?? "none (background)")}
        </span>
      </p>
      <div className="text-xs text-muted-foreground">
        Custom hotspots:{" "}
        {overridden.length === 0 ? (
          "none (using generated positions)"
        ) : (
          <span className="inline-flex flex-wrap gap-1 align-middle">
            {overridden.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => onSelectPart(p.id)}
                className="rounded-md border px-1.5 py-0.5 text-foreground hover:bg-muted"
              >
                #{p.number} {p.id}
              </button>
            ))}
          </span>
        )}
      </div>
    </div>
  );
}
