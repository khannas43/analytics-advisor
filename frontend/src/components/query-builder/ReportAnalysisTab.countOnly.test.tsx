// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { I18N } from "@/lib/i18n/catalog";
import {
  EMPTY_EXTRACT_CONFIG,
  EMPTY_REPORT_CONFIG,
} from "@/lib/queryBuilderPipelineTypes";
import { buildExtractRequest } from "@/lib/queryBuilderRequestBuild";
import { useQueryBuilderPipeline } from "@/lib/queryBuilderPipelineStore";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const runRecordMatchStream = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/shell/ShellProviders", () => ({
  useShell: () => ({ t: (key: keyof typeof I18N.en) => I18N.en[key] }),
}));

vi.mock("@/lib/savedQueryApi", () => ({
  listSavedQueries: vi.fn().mockResolvedValue([]),
  createSavedQuery: vi.fn(),
  getSavedQuery: vi.fn(),
}));

vi.mock("@/lib/analysisApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/analysisApi")>();
  return {
    ...actual,
    listColumnMetadata: vi.fn().mockResolvedValue([]),
    fetchAnalysisLimits: vi.fn().mockResolvedValue({
      maxTargetSets: 5,
      multiMatchBudgetSeconds: 120,
      maxGroupColumns: 4,
      maxGroupingColumns: 4,
      maxAggregates: 4,
      maxAnyOfGroupsPerSide: 2,
      maxProbedPairs: 20,
      blockingPrefixLen: 3,
      maxEstimatedRows: 50_000_000,
      maxColumnDistinctValues: 200,
    }),
    listAnalysisColumns: vi.fn().mockResolvedValue([
      {
        name: "m_id",
        dataType: "bigint",
        businessName: null,
        fuzzyMatchable: false,
        valuePickerEnabled: false,
      },
    ]),
    listAnalysisCatalogs: vi.fn().mockResolvedValue([]),
    listAnalysisSchemas: vi.fn().mockResolvedValue([]),
    listAnalysisTables: vi.fn().mockResolvedValue([]),
    listAnalysisLayers: vi.fn().mockResolvedValue([]),
    listAnalysisSourceSystems: vi.fn().mockResolvedValue([]),
    listAnalysisTableGroups: vi.fn().mockResolvedValue([]),
    fetchMatchSql: vi.fn(),
    fetchComparisonSummary: vi.fn(),
    suggestJoinKeys: vi.fn().mockResolvedValue([]),
    downloadRecordMatchExport: vi.fn(),
    downloadMultiTargetMatchCsv: vi.fn(),
    runMultiTargetMatchStream: vi.fn(),
    runRecordMatchStream: (...args: unknown[]) => runRecordMatchStream(...args),
  };
});

vi.mock("@/components/AnalysisResultsGrid", () => ({
  AnalysisResultsGrid: () => <div data-testid="analysis-results-grid">GRID</div>,
}));

vi.mock("@/components/LakehouseCascade", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/LakehouseCascade")>();
  return {
    ...actual,
    default: () => <div data-testid="cascade" />,
  };
});

vi.mock("@/components/ComparisonPairsEditor", () => ({
  ComparisonPairsEditor: () => null,
}));

vi.mock("@/components/query-builder/ReportSourceFilters", () => ({
  ReportSourceFilters: () => null,
}));

vi.mock("@/components/MultiTargetJoinCanvas", () => ({
  MultiTargetJoinCanvas: () => null,
  createInitialJoinCanvas: () => ({ nodes: [], edges: [] }),
}));

const SOURCE = { catalog: "demo_pg", schema: "demo", table: "beneficiary" };
const TARGET = { catalog: "demo_pg", schema: "demo", table: "bank_account" };

function seedPipeline() {
  const extract = {
    ...EMPTY_EXTRACT_CONFIG,
    sourceRef: SOURCE,
    targetRef: TARGET,
    displayCols: ["m_id"],
    joinKeySource: "m_id",
    joinKeyTarget: "m_id",
  };
  const extractRequest = buildExtractRequest(true, extract)!;
  useQueryBuilderPipeline.setState({
    dualMode: true,
    extract,
    report: { ...EMPTY_REPORT_CONFIG },
    extractSucceeded: true,
    baseExtractRequest: extractRequest,
    lastAppliedRequest: extractRequest,
    extractResult: {
      columns: ["source_m_id"],
      rows: [{ source_m_id: 1 }],
      totalRows: 1,
      sql: "SELECT 1",
    },
    reportResult: null,
  });
}

describe("Report Analysis count-only", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;

  beforeEach(() => {
    useQueryBuilderPipeline.getState().resetWorkflow();
    runRecordMatchStream.mockReset();
    runRecordMatchStream.mockImplementation(
      async (
        req: { countOnly?: boolean },
        handlers: {
          onMeta: (meta: { columns: string[]; sql: string }) => void;
          onRow: (row: Record<string, unknown>) => void;
          onDone: (totalRows: number) => void;
        },
      ) => {
        expect(req.countOnly).toBe(true);
        handlers.onMeta({ columns: ["row_count"], sql: "SELECT COUNT(*) AS row_count FROM (...)" });
        handlers.onRow({ row_count: 675387 });
        handlers.onDone(1);
      },
    );
    seedPipeline();
  });

  afterEach(() => {
    act(() => root?.unmount());
    container?.remove();
    root = null;
    container = null;
    useQueryBuilderPipeline.getState().resetWorkflow();
  });

  it("shows the exact count and does not render the grid", async () => {
    const { default: ReportAnalysisTab } = await import(
      "@/components/query-builder/ReportAnalysisTab"
    );
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);

    await act(async () => {
      root?.render(<ReportAnalysisTab embedded />);
    });
    await act(async () => {
      await Promise.resolve();
    });

    const checkbox = container.querySelector(
      'input[data-testid="report-count-only"]',
    ) as HTMLInputElement | null;
    expect(checkbox).toBeTruthy();
    expect(checkbox?.checked).toBe(false);
    expect(container.querySelector('[data-testid="analysis-results-grid"]')).toBeTruthy();

    await act(async () => {
      useQueryBuilderPipeline.getState().patchReport({ countOnly: true });
    });
    expect(
      (container.querySelector('input[data-testid="report-count-only"]') as HTMLInputElement).checked,
    ).toBe(true);

    const apply = [...container.querySelectorAll("button")].find(
      (el) => el.textContent?.trim() === "Apply report options",
    );
    expect(apply).toBeTruthy();

    await act(async () => {
      apply?.click();
    });
    await act(async () => {
      await Promise.resolve();
    });

    expect(runRecordMatchStream).toHaveBeenCalled();
    const result = container.querySelector('[data-testid="report-count-only-result"]');
    expect(result?.textContent).toContain("675,387 matching rows");
    expect(container.querySelector('[data-testid="analysis-results-grid"]')).toBeNull();
    expect(container.querySelector("table")).toBeNull();
  });
});
