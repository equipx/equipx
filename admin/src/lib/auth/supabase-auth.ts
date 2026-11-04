import { getSupabase } from "@/lib/supabase/client";
import type { AuthService, AuthUser } from "./types";

const CACHE_KEY = "equipx.admin.supabase-user.v1";

/**
 * Email + password sign-in through Supabase Auth. Only profiles with role = 'teacher' may enter;
 * everyone else is signed out again. getSession() is synchronous (used by useSyncExternalStore), so
 * the verified user is cached in localStorage and re-validated against Supabase in the background.
 */
export class SupabaseAuth implements AuthService {
  private db = getSupabase();

  constructor() {
    if (typeof window !== "undefined") void this.revalidate();
  }

  async signIn(email: string, password: string): Promise<AuthUser> {
    const { data, error } = await this.db.auth.signInWithPassword({ email: email.trim(), password });
    if (error || !data.user) throw new Error(error?.message ?? "Invalid email or password.");

    const { data: profile, error: pErr } = await this.db
      .from("profiles")
      .select("role,display_name")
      .eq("id", data.user.id)
      .maybeSingle();
    if (pErr || profile?.role !== "teacher") {
      await this.db.auth.signOut();
      this.cache(null);
      throw new Error("This account is not a teacher. The admin panel is for teachers only.");
    }
    const user: AuthUser = {
      email: data.user.email ?? email,
      name: profile.display_name || (data.user.email ?? "Teacher").split("@")[0],
      role: "teacher",
    };
    this.cache(user);
    return user;
  }

  async signOut(): Promise<void> {
    this.cache(null);
    await this.db.auth.signOut();
  }

  getSession(): AuthUser | null {
    if (typeof window === "undefined") return null;
    try {
      const raw = window.localStorage.getItem(CACHE_KEY);
      return raw ? (JSON.parse(raw) as AuthUser) : null;
    } catch {
      return null;
    }
  }

  /** Drops the cached user if the Supabase session is gone or the account is no longer a teacher. */
  private async revalidate() {
    try {
      const { data } = await this.db.auth.getSession();
      const uid = data.session?.user.id;
      if (!uid) {
        if (this.getSession()) this.expire();
        return;
      }
      const { data: profile, error } = await this.db.from("profiles").select("role").eq("id", uid).maybeSingle();
      if (!error && profile?.role !== "teacher") {
        await this.db.auth.signOut();
        this.expire();
      }
    } catch {
      /* offline: keep the cached user, writes will fail visibly */
    }
  }

  private expire() {
    this.cache(null);
    window.location.replace("/login");
  }

  private cache(user: AuthUser | null) {
    try {
      if (user) window.localStorage.setItem(CACHE_KEY, JSON.stringify(user));
      else window.localStorage.removeItem(CACHE_KEY);
    } catch {
      /* ignore */
    }
  }
}
