import type { Device, DeviceIndexEntry, Mission } from "./types";

/**
 * Data access boundary for the admin panel. MockStore (localStorage + seed JSON)
 * is used now; a Supabase implementation can replace it behind getStore().
 */
export interface DataStore {
  listDevices(): Promise<DeviceIndexEntry[]>;
  getDevice(id: string): Promise<Device | null>;
  saveDevice(device: Device): Promise<void>;
  deleteDevice(id: string): Promise<void>;
  listMissions(): Promise<Mission[]>;
  saveMission(mission: Mission): Promise<void>;
  deleteMission(id: string): Promise<void>;
  /** Discard local edits and fall back to the seed data. */
  reset(): Promise<void>;
}
