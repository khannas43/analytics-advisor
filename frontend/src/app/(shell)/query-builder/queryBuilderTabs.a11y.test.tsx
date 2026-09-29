// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react";
import QueryBuilderPage, { queryBuilderTabIds } from "@/app/(shell)/query-builder/page";
import { ShellProviders } from "@/components/shell/ShellProviders";
import { I18N } from "@/lib/i18n/catalog";

vi.mock("@/components/query-builder/ExtractRecordsTab", () => ({
  default: ({ onGoReport }: { onGoReport: () => void }) => (
    <div data-testid="extract-tab">
      <button type="button" onClick={onGoReport}>
        Go to Report Analysis
      </button>
    </div>
  ),
}));

vi.mock("@/components/query-builder/ReportAnalysisTab", () => ({
  default: () => (
    <section role="region" aria-label="Report saved queries">
      <button type="button">Save query</button>
      <button type="button">Apply report options</button>
    </section>
  ),
}));

vi.mock("@/components/query-builder/QueryBuilderSavedQueryOpen", () => ({
  QueryBuilderSavedQueryOpen: () => (
    <section role="region" aria-label="Extract saved queries">
      <select aria-label="Open saved query">
        <option value="">Open</option>
      </select>
    </section>
  ),
}));

const pipelineState = {
  dualMode: false,
  extractSucceeded: false,
  setDualMode: vi.fn(),
};

vi.mock("@/lib/queryBuilderPipelineStore", () => ({
  useQueryBuilderPipeline: (selector: (s: typeof pipelineState) => unknown) => selector(pipelineState),
}));

vi.mock("@/lib/savedQueryApi", () => ({
  listSavedQueries: vi.fn().mockResolvedValue([]),
}));

function mockMatchMedia() {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("767"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

function savedQueryRegionsInDom() {
  return [...document.querySelectorAll('[role="region"]')].filter((el) =>
    /saved queries/i.test(el.getAttribute("aria-label") ?? ""),
  );
}

function visibleTabPanels() {
  return [...document.querySelectorAll('[role="tabpanel"]')];
}

function buttonByText(text: string) {
  return [...document.querySelectorAll("button")].find((el) => el.textContent?.trim() === text);
}

describe("Query Builder tab accessibility", () => {
  let root: Root | null = null;
  let container: HTMLDivElement;

  beforeEach(() => {
    mockMatchMedia();
    pipelineState.extractSucceeded = false;
    pipelineState.dualMode = false;
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container.remove();
  });

  function renderPage() {
    act(() => {
      root?.render(
        <ShellProviders>
          <QueryBuilderPage />
        </ShellProviders>,
      );
    });
  }

  it("exposes only the extract panel and one extract saved-query region by default", () => {
    renderPage();
    expect(document.querySelectorAll('[role="tabpanel"]')).toHaveLength(1);
    expect(document.getElementById(queryBuilderTabIds.PANEL_EXTRACT_ID)).toBeTruthy();
    expect(document.getElementById(queryBuilderTabIds.PANEL_REPORT_ID)).toBeNull();
    const regions = savedQueryRegionsInDom();
    expect(regions).toHaveLength(1);
    expect(regions[0]?.getAttribute("aria-label")).toBe("Extract saved queries");
    expect(document.querySelector("#qb2")).toBeNull();
  });

  it("wires tab aria-controls to tabpanel ids without duplicates", () => {
    renderPage();
    const extractTab = document.getElementById(queryBuilderTabIds.TAB_EXTRACT_ID);
    const reportTab = document.getElementById(queryBuilderTabIds.TAB_REPORT_ID);
    expect(extractTab?.getAttribute("aria-controls")).toBe(queryBuilderTabIds.PANEL_EXTRACT_ID);
    expect(reportTab?.getAttribute("aria-controls")).toBe(queryBuilderTabIds.PANEL_REPORT_ID);
    expect(extractTab?.getAttribute("aria-selected")).toBe("true");
    expect(reportTab?.getAttribute("aria-selected")).toBe("false");
    expect(reportTab?.hasAttribute("aria-disabled")).toBe(false);
    expect((reportTab as HTMLButtonElement).disabled).toBe(false);
    expect(reportTab?.getAttribute("title")).toBe(I18N.en.msgNoExtractYet);
  });

  it("does not expose report controls while extract tab is selected", () => {
    renderPage();
    expect(document.querySelector('[aria-label="Report saved queries"]')).toBeNull();
    expect(document.querySelector('button[aria-label="Save query"]')).toBeNull();
    expect(buttonByText("Apply report options")).toBeUndefined();
  });

  it("opens Report Analysis before extract and shows the prerequisite empty state", () => {
    renderPage();
    const reportTab = document.getElementById(queryBuilderTabIds.TAB_REPORT_ID);
    act(() => {
      reportTab?.click();
    });
    expect(reportTab?.getAttribute("aria-selected")).toBe("true");
    expect(document.getElementById(queryBuilderTabIds.TAB_EXTRACT_ID)?.getAttribute("aria-selected")).toBe("false");
    expect(reportTab?.hasAttribute("aria-disabled")).toBe(false);
    expect(reportTab?.getAttribute("aria-describedby")).toBe(queryBuilderTabIds.REPORT_PREREQ_ID);
    expect(reportTab?.getAttribute("title")).toBe(I18N.en.msgNoExtractYet);
    expect(visibleTabPanels()).toHaveLength(1);
    const panel = document.getElementById(queryBuilderTabIds.PANEL_REPORT_ID);
    expect(panel?.getAttribute("role")).toBe("tabpanel");
    expect(panel?.getAttribute("aria-labelledby")).toBe(queryBuilderTabIds.TAB_REPORT_ID);
    expect(document.getElementById(queryBuilderTabIds.PANEL_EXTRACT_ID)).toBeNull();
    expect(document.getElementById(queryBuilderTabIds.REPORT_PREREQ_ID)?.textContent).toBe(I18N.en.msgNoExtractYet);
    expect(buttonByText(I18N.en.btnBackExtract)).toBeTruthy();
    expect(savedQueryRegionsInDom()).toHaveLength(0);
    expect(document.querySelector('[aria-label="Report saved queries"]')).toBeNull();
    expect(buttonByText("Apply report options")).toBeUndefined();
  });

  it("returns to Extract Records from the report prerequisite empty state", () => {
    renderPage();
    act(() => {
      document.getElementById(queryBuilderTabIds.TAB_REPORT_ID)?.click();
    });
    act(() => {
      buttonByText(I18N.en.btnBackExtract)?.click();
    });
    expect(visibleTabPanels()).toHaveLength(1);
    expect(document.getElementById(queryBuilderTabIds.PANEL_EXTRACT_ID)).toBeTruthy();
    expect(document.getElementById(queryBuilderTabIds.PANEL_REPORT_ID)).toBeNull();
    expect(document.getElementById(queryBuilderTabIds.TAB_EXTRACT_ID)?.getAttribute("aria-selected")).toBe("true");
    expect(document.getElementById(queryBuilderTabIds.TAB_REPORT_ID)?.getAttribute("aria-selected")).toBe("false");
    const regions = savedQueryRegionsInDom();
    expect(regions).toHaveLength(1);
    expect(regions[0]?.getAttribute("aria-label")).toBe("Extract saved queries");
    expect(document.querySelector('[aria-label="Report saved queries"]')).toBeNull();
    expect(buttonByText("Apply report options")).toBeUndefined();
    expect(document.querySelector("#qb2")).toBeNull();
  });

  it("shows report panel with one report saved-query region after extract succeeds", () => {
    pipelineState.extractSucceeded = true;
    renderPage();
    const reportTab = document.getElementById(queryBuilderTabIds.TAB_REPORT_ID);
    expect(reportTab?.hasAttribute("aria-disabled")).toBe(false);
    expect(reportTab?.hasAttribute("title")).toBe(false);
    act(() => {
      reportTab?.click();
    });
    expect(reportTab?.getAttribute("aria-selected")).toBe("true");
    expect(reportTab?.hasAttribute("aria-describedby")).toBe(false);
    expect(visibleTabPanels()).toHaveLength(1);
    expect(document.getElementById(queryBuilderTabIds.PANEL_REPORT_ID)).toBeTruthy();
    expect(document.getElementById(queryBuilderTabIds.PANEL_EXTRACT_ID)).toBeNull();
    expect(document.getElementById(queryBuilderTabIds.REPORT_PREREQ_ID)).toBeNull();
    expect(document.body.textContent).not.toContain(I18N.en.msgNoExtractYet);
    const regions = savedQueryRegionsInDom();
    expect(regions).toHaveLength(1);
    expect(regions[0]?.getAttribute("aria-label")).toBe("Report saved queries");
    expect(buttonByText("Apply report options")).toBeTruthy();
  });

  it("restores extract saved-query region when switching back from report", () => {
    pipelineState.extractSucceeded = true;
    renderPage();
    act(() => {
      document.getElementById(queryBuilderTabIds.TAB_REPORT_ID)?.click();
    });
    act(() => {
      document.getElementById(queryBuilderTabIds.TAB_EXTRACT_ID)?.click();
    });
    const regions = savedQueryRegionsInDom();
    expect(regions).toHaveLength(1);
    expect(regions[0]?.getAttribute("aria-label")).toBe("Extract saved queries");
    expect(document.querySelector('[aria-label="Report saved queries"]')).toBeNull();
  });
});
