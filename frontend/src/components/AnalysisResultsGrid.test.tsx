// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AnalysisResultsGrid } from "@/components/AnalysisResultsGrid";
import { I18N } from "@/lib/i18n/catalog";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("@/components/shell/ShellProviders", () => ({
  useShell: () => ({ t: (key: keyof typeof I18N.en) => I18N.en[key] }),
}));

describe("AnalysisResultsGrid large result preview", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;

  afterEach(() => {
    act(() => root?.unmount());
    container?.remove();
    root = null;
    container = null;
  });

  it("keeps the paginated table visible when only the first rows are buffered", () => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => {
      root?.render(
        <AnalysisResultsGrid
          appearance="prototype"
          columns={["district"]}
          rows={[{ district: "Jaipur" }, { district: "Ajmer" }]}
          sql="select district"
          totalRows={64000}
          tooManyToDisplay
          displayLimit={10000}
          highlightDuplicates={false}
          dedupAvailable={false}
          dedupEnabled={false}
          onDedupToggle={() => {}}
          showCharts={false}
        />,
      );
    });

    expect(container.textContent).toContain("Large result — showing a preview");
    expect(container.textContent).toContain("Showing first 2 of 64,000 matching rows");
    expect(container.querySelector("table")?.textContent).toContain("Jaipur");
    expect(container.querySelector("table")?.textContent).toContain("Ajmer");
    expect(container.textContent).toContain("CSV");
    expect(container.textContent).toContain("Excel");
  });
});
