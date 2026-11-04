import type { AuthService, AuthUser } from "./types";

const SESSION_KEY = "equipx.admin.session.v1";

// Mock teacher account from docs/CONTRACT.md. The admin panel accepts this account only.
const TEACHER = {
  email: "teacher@equipx.app",
  password: "teacher123",
  user: { email: "teacher@equipx.app", name: "Teacher", role: "teacher" } as AuthUser,
};

export class MockAuth implements AuthService {
  async signIn(email: string, password: string): Promise<AuthUser> {
    if (
      email.trim().toLowerCase() !== TEACHER.email ||
      password !== TEACHER.password
    ) {
      throw new Error("Invalid email or password.");
    }
    try {
      window.localStorage.setItem(SESSION_KEY, JSON.stringify(TEACHER.user));
    } catch {
      /* ignore */
    }
    return TEACHER.user;
  }

  async signOut(): Promise<void> {
    try {
      window.localStorage.removeItem(SESSION_KEY);
    } catch {
      /* ignore */
    }
  }

  getSession(): AuthUser | null {
    if (typeof window === "undefined") return null;
    try {
      const raw = window.localStorage.getItem(SESSION_KEY);
      if (!raw) return null;
      const u = JSON.parse(raw) as AuthUser;
      return u.email === TEACHER.email ? u : null;
    } catch {
      return null;
    }
  }
}
