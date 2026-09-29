// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { AttributeMultiSelect } from "@/components/query-builder/ExtractRecordsTab";
import type { RegisteredColumn } from "@/lib/analysisApi";

vi.mock("@/components/shell/ShellProviders", () => ({
  useShell: () => ({
    t: (key: string) =>
      ({
        attrSummaryNone: "Select attributes (multiple)",
        attrSummaryAll: "All ({count})",
        attrSummaryCount: "{selected} of {total} selected",
        ariaAttrSummary: "{label}, {summary}",
        ariaSelectAllAttrs: "Select all {label}",
        ariaClearAttrs: "Clear {label}",
        btnSelectAll: "Select all",
        btnClearSelection: "Clear",
        ariaAttrColumn: "{label} {caption}",
      })[key] ?? key,
  }),
}));

const columns: RegisteredColumn[] = [
  { name: "age", dataType: "integer", businessName: "Age", fuzzyMatchable: false },
  { name: "name", dataType: "varchar", businessName: null, fuzzyMatchable: false },
];

function Harness() {
  const [selected, setSelected] = useState<string[]>([]);
  return (
    <AttributeMultiSelect
      id="source-attrs"
      label="Source attributes"
      columns={columns}
      selected={selected}
      onChange={setSelected}
    />
  );
}

function checkbox(container: ParentNode, label: string): HTMLInputElement {
  const found = container.querySelector(`input[type="checkbox"][aria-label="${label}"]`);
  if (!(found instanceof HTMLInputElement)) throw new Error(`Missing checkbox ${label}`);
  return found;
}

function button(container: ParentNode, label: string): HTMLButtonElement {
  const found = container.querySelector(`button[aria-label="${label}"]`);
  if (!(found instanceof HTMLButtonElement)) throw new Error(`Missing button ${label}`);
  return found;
}

describe("AttributeMultiSelect", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;

  afterEach(() => {
    act(() => root?.unmount());
    container?.remove();
    root = null;
    container = null;
  });

  it("checks one column, selects all, and clears", async () => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root?.render(<Harness />);
    });

    const age = checkbox(container, "Source attributes Age");
    const name = checkbox(container, "Source attributes name");
    expect(age.checked).toBe(false);
    expect(name.checked).toBe(false);

    await act(async () => {
      age.click();
    });
    expect(checkbox(container, "Source attributes Age").checked).toBe(true);
    expect(checkbox(container, "Source attributes name").checked).toBe(false);

    await act(async () => {
      button(container, "Select all Source attributes").click();
    });
    expect(checkbox(container, "Source attributes Age").checked).toBe(true);
    expect(checkbox(container, "Source attributes name").checked).toBe(true);

    await act(async () => {
      button(container, "Clear Source attributes").click();
    });
    expect(checkbox(container, "Source attributes Age").checked).toBe(false);
    expect(checkbox(container, "Source attributes name").checked).toBe(false);
  });
});
