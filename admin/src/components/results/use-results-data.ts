"use client";

import { useEffect, useState } from "react";
import { loadResultsData, type ResultsData } from "@/lib/results";

/** Loads the results dataset on the client only (avoids hydration mismatches). */
export function useResultsData(): ResultsData | null {
  const [data, setData] = useState<ResultsData | null>(null);
  useEffect(() => {
    let live = true;
    loadResultsData().then((d) => live && setData(d));
    return () => {
      live = false;
    };
  }, []);
  return data;
}
