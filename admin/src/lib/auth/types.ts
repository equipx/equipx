export interface AuthUser {
  email: string;
  name: string;
  role: "teacher";
}

export interface AuthService {
  signIn(email: string, password: string): Promise<AuthUser>;
  signOut(): Promise<void>;
  getSession(): AuthUser | null;
}
