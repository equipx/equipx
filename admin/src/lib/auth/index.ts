import { useSupabase } from "@/lib/supabase/client";
import { MockAuth } from "./mock-auth";
import { SupabaseAuth } from "./supabase-auth";
import type { AuthService } from "./types";

export type { AuthService, AuthUser } from "./types";

let auth: AuthService | null = null;

/** Supabase Auth when the Supabase env vars are set, otherwise the offline mock account. */
export function getAuth(): AuthService {
  if (!auth) auth = useSupabase ? new SupabaseAuth() : new MockAuth();
  return auth;
}
