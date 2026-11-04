import { MockAuth } from "./mock-auth";
import type { AuthService } from "./types";

export type { AuthService, AuthUser } from "./types";

let auth: AuthService | null = null;

export function getAuth(): AuthService {
  if (!auth) auth = new MockAuth();
  return auth;
}
