"use client";

import { useMemo, useSyncExternalStore } from "react";
import { getAuth, type AuthUser } from "./index";

const subscribe = () => () => {};
const PENDING = "pending";

/** undefined = still checking (server / hydration), null = signed out. */
export function useSession(): AuthUser | null | undefined {
  const raw = useSyncExternalStore(
    subscribe,
    () => JSON.stringify(getAuth().getSession()),
    () => PENDING,
  );
  return useMemo(
    () => (raw === PENDING ? undefined : (JSON.parse(raw) as AuthUser | null)),
    [raw],
  );
}
