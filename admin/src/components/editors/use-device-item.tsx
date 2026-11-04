"use client";

import { useEffect, useState } from "react";
import { getStore, type Device } from "@/lib/data";

/** Loads one device; `device === undefined` while loading, `null` when missing. */
export function useDevice(deviceId: string) {
  const [device, setDevice] = useState<Device | null | undefined>(undefined);
  useEffect(() => {
    let alive = true;
    getStore()
      .getDevice(deviceId)
      .then((d) => alive && setDevice(d));
    return () => {
      alive = false;
    };
  }, [deviceId]);
  return device;
}
