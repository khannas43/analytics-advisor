// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react";
import { ResultTableToolbar } from "@/components/results/ResultTableToolbar";
import { I18N } from "@/lib/i18n/catalog";

describe("ResultTableToolbar mobile layout", () => {
  it("uses toolbar classes that wrap without forcing page overflow", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    act(() => {
      root.render(
        <ResultTableToolbar
          rowCountLabel={null}
          columns={["a", "b"]}
          hiddenColumnIds={[]}
          onHiddenChange={() => {}}
          labelFor={(id) => id}
          pageSize={25}
          onPageSizeChange={() => {}}
          pageIndex={0}
          pageCount={2}
          totalFilteredRows={40}
          totalLoadedRows={40}
          onFirstPage={() => {}}
          onPrevPage={() => {}}
          onNextPage={() => {}}
          onLastPage={() => {}}
          canPrevious={false}
          canNext
          onDownloadCsv={() => {}}
          onDownloadExcel={() => {}}
          t={(key) => I18N.en[key]}
        />,
      );
    });
    const toolbar = container.querySelector(".result-table-toolbar");
    expect(toolbar).toBeTruthy();
    expect(toolbar!.className).toContain("result-table-toolbar");
    expect(container.querySelector(".result-table-toolbar-actions")).toBeTruthy();
    act(() => root.unmount());
    container.remove();
  });
});
