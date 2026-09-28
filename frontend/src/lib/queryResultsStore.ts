import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { RecordMatchRequest } from "@/lib/analysisApi";

export type QueryResultRow = Record<string, unknown>;

export type QueryResultsPayload = {
  columns: string[];
  rows: QueryResultRow[];
  totalRows: number | null;
  capturedAt: string;
  source: "extract" | "report";
  /** When set, dashboard can re-run server CSV/Excel for the full analytical result. */
  matchExportRequest?: RecordMatchRequest | null;
};

type QueryResultsState = {
  lastResult: QueryResultsPayload | null;
  setLastResult: (payload: Omit<QueryResultsPayload, "capturedAt">) => void;
  clear: () => void;
};

/** Carried client state for page 3 — no second /api/analysis/match call. */
export const useQueryResultsStore = create<QueryResultsState>()(
  persist(
    (set) => ({
      lastResult: null,
      setLastResult: (payload) =>
        set({
          lastResult: { ...payload, capturedAt: new Date().toISOString() },
        }),
      clear: () => set({ lastResult: null }),
    }),
    { name: "aa-query-results-v1", partialize: (s) => ({ lastResult: s.lastResult }) },
  ),
);
