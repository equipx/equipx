import seedIndex from "@/data/seed/index.json";
import seedMissions from "@/data/seed/missions.json";
import type { DataStore } from "./store";
import type { Device, DeviceIndexEntry, Mission, MissionsFile } from "./types";

const STORAGE_KEY = "equipx.admin.store.v1";

interface Overrides {
  devices: Record<string, Device>;
  deleted: string[];
  missions: Record<string, Mission>;
  deletedMissions: string[];
}

function readOverrides(): Overrides {
  const empty: Overrides = { devices: {}, deleted: [], missions: {}, deletedMissions: [] };
  if (typeof window === "undefined") return empty;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return empty;
    const parsed = JSON.parse(raw) as Partial<Overrides>;
    return {
      devices: parsed.devices ?? {},
      deleted: parsed.deleted ?? [],
      missions: parsed.missions ?? {},
      deletedMissions: parsed.deletedMissions ?? [],
    };
  } catch {
    return empty;
  }
}

function writeOverrides(o: Overrides) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(o));
  } catch {
    /* storage unavailable: edits live only for this page load */
  }
}

function seedEntries(): DeviceIndexEntry[] {
  return (seedIndex as { devices: DeviceIndexEntry[] }).devices ?? [];
}

async function loadSeedDevice(id: string): Promise<Device | null> {
  if (!/^[a-z0-9_]+$/.test(id) || id === "index") return null;
  try {
    // The bundler indexes every JSON file in the seed folder, so this works
    // whether only index.json or all device files exist.
    const mod = await import(`@/data/seed/${id}.json`);
    return (mod.default ?? mod) as Device;
  } catch {
    return null;
  }
}

function toEntry(d: Device): DeviceIndexEntry {
  return { id: d.id, name: d.name, category: d.category, summary: d.summary };
}

export class MockStore implements DataStore {
  async listDevices(): Promise<DeviceIndexEntry[]> {
    const o = readOverrides();
    const byId = new Map<string, DeviceIndexEntry>();
    for (const e of seedEntries()) {
      if (!o.deleted.includes(e.id)) byId.set(e.id, e);
    }
    for (const d of Object.values(o.devices)) byId.set(d.id, toEntry(d));
    return [...byId.values()];
  }

  async getDevice(id: string): Promise<Device | null> {
    const o = readOverrides();
    if (o.deleted.includes(id)) return null;
    if (o.devices[id]) return o.devices[id];
    return loadSeedDevice(id);
  }

  async saveDevice(device: Device): Promise<void> {
    const o = readOverrides();
    o.devices[device.id] = device;
    o.deleted = o.deleted.filter((x) => x !== device.id);
    writeOverrides(o);
  }

  async deleteDevice(id: string): Promise<void> {
    const o = readOverrides();
    delete o.devices[id];
    if (!o.deleted.includes(id)) o.deleted.push(id);
    writeOverrides(o);
  }

  async listMissions(): Promise<Mission[]> {
    const o = readOverrides();
    const byId = new Map<string, Mission>();
    for (const m of (seedMissions as unknown as MissionsFile).missions ?? []) {
      if (!o.deletedMissions.includes(m.id)) byId.set(m.id, m);
    }
    for (const m of Object.values(o.missions)) byId.set(m.id, m);
    return [...byId.values()];
  }

  async saveMission(mission: Mission): Promise<void> {
    const o = readOverrides();
    o.missions[mission.id] = mission;
    o.deletedMissions = o.deletedMissions.filter((x) => x !== mission.id);
    writeOverrides(o);
  }

  async deleteMission(id: string): Promise<void> {
    const o = readOverrides();
    delete o.missions[id];
    if (!o.deletedMissions.includes(id)) o.deletedMissions.push(id);
    writeOverrides(o);
  }

  async reset(): Promise<void> {
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }
}
