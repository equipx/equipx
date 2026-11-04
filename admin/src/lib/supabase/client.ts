import { createClient, type SupabaseClient } from "@supabase/supabase-js";

/** Both must be set (publishable/anon key, never the service-role key) to use Supabase instead of the mocks. */
export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";
export const useSupabase = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY);

let client: SupabaseClient | null = null;

export function getSupabase(): SupabaseClient {
  if (!useSupabase) throw new Error("Supabase is not configured (NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY).");
  return (client ??= createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: { persistSession: true, autoRefreshToken: true },
  }));
}

/** Throws a readable Error for a PostgREST error, otherwise returns data. */
export function unwrap<T>(res: { data: T | null; error: { message: string } | null }, what: string): T {
  if (res.error) throw new Error(`${what}: ${res.error.message}`);
  return (res.data ?? ([] as unknown)) as T;
}

/** Reads every row of a query, paging past PostgREST's row cap. */
export async function fetchAll<T>(
  build: (from: number, to: number) => PromiseLike<{ data: T[] | null; error: { message: string } | null }>,
  what: string,
): Promise<T[]> {
  const page = 1000;
  const all: T[] = [];
  for (let from = 0; ; from += page) {
    const rows = unwrap(await build(from, from + page - 1), what);
    all.push(...rows);
    if (rows.length < page) return all;
  }
}
