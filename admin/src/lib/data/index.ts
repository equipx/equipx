import { MockStore } from "./mock-store";
import type { DataStore } from "./store";

export type { DataStore } from "./store";
export * from "./types";

let store: DataStore | null = null;

/** Single entry point; swap the implementation here for Supabase later. */
export function getStore(): DataStore {
  if (!store) store = new MockStore();
  return store;
}
