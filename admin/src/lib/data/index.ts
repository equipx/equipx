import { useSupabase } from "@/lib/supabase/client";
import { MockStore } from "./mock-store";
import { SupabaseStore } from "./supabase-store";
import type { DataStore } from "./store";

export type { DataStore } from "./store";
export * from "./types";

let store: DataStore | null = null;

/** Supabase when NEXT_PUBLIC_SUPABASE_URL + NEXT_PUBLIC_SUPABASE_ANON_KEY are set, otherwise the offline mock. */
export function getStore(): DataStore {
  if (!store) store = useSupabase ? new SupabaseStore() : new MockStore();
  return store;
}
