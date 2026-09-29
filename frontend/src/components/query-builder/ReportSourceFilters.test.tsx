// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { ReportSourceFilters } from "@/components/query-builder/ReportSourceFilters";
import type { PredicateSpecWire, RegisteredColumn, TableRef } from "@/lib/analysisApi";
import { EMPTY_REPORT_CONFIG, type ReportConfig } from "@/lib/queryBuilderPipelineTypes";

vi.mock("@/components/shell/ShellProviders", () => ({
  useShell: () => ({
    t: (key: string) =>
      ({
        secFilter: "Filter (Optional)",
        secFuzzy: "Fuzzy Matching (Optional)",
        qbValuePickerIn: "Pick values from list (IN rule)",
        qbFuzzyRuleScoped: "Typed-text fuzzy match (inside scoped table)",
        lblColumn: "Column",
        selNone: "— None —",
        qbFuzzyName: "Name to match",
        qbFuzzyThreshold: "Threshold %",
        qbFuzzyIgnoreSpaces: "Ignore spaces",
        qbFuzzyCaseSensitive: "Case sensitive",
      })[key] ?? key,
  }),
}));

vi.mock("@/components/ValueFilterPicker", () => ({
  default: ({
    table,
    column,
    hydratedSpec,
    onChange,
  }: {
    table: TableRef;
    column: string;
    hydratedSpec?: PredicateSpecWire | null;
    onChange: (spec: PredicateSpecWire | null) => void;
  }) => (
    <button
      type="button"
      data-testid="value-filter-picker"
      data-column={column}
      data-table={`${table.catalog}.${table.schema}.${table.table}`}
      data-spec={hydratedSpec ? "set" : "empty"}
      onClick={() =>
        onChange({
          root: {
            type: "PREDICATE",
            column: { table, column },
            operator: "IN",
            value: ["Jaipur"],
          },
        })
      }
    >
      apply spec
    </button>
  ),
}));

const table: TableRef = { catalog: "iceberg", schema: "srse", table: "beneficiary" };

const columns: RegisteredColumn[] = [
  { name: "district", dataType: "varchar", businessName: "District", fuzzyMatchable: false },
  { name: "full_name", dataType: "varchar", businessName: null, fuzzyMatchable: true },
];

const districtSpec: PredicateSpecWire = {
  root: {
    type: "PREDICATE",
    column: { table: { ...table }, column: "district" },
    operator: "IN",
    value: ["Jaipur"],
  },
};

const appliedSpec: PredicateSpecWire = {
  root: {
    type: "PREDICATE",
    column: { table: { ...table }, column: "district" },
    operator: "IN",
    value: ["Jaipur"],
  },
};

function Harness({
  initial = EMPTY_REPORT_CONFIG,
  onPatch,
}: {
  initial?: ReportConfig;
  onPatch: (patch: Partial<ReportConfig>) => void;
}) {
  const [report, setReport] = useState(initial);
  return (
    <ReportSourceFilters
      table={table}
      columns={columns}
      report={report}
      onPatch={(patch) => {
        onPatch(patch);
        setReport((current) => ({ ...current, ...patch }));
      }}
    />
  );
}

function group(container: ParentNode, label: string): HTMLElement {
  const found = container.querySelector(`[role="group"][aria-label="${label}"]`);
  if (!(found instanceof HTMLElement)) throw new Error(`Missing group ${label}`);
  return found;
}

function controlForLabel(container: ParentNode, text: string): HTMLInputElement | HTMLSelectElement {
  const label = Array.from(container.querySelectorAll("label")).find((node) => node.textContent?.trim() === text);
  if (!label) throw new Error(`Missing label ${text}`);
  const nested = label.querySelector("input, select");
  if (nested instanceof HTMLInputElement || nested instanceof HTMLSelectElement) return nested;
  const linked = label.htmlFor ? container.querySelector(`#${CSS.escape(label.htmlFor)}`) : null;
  if (linked instanceof HTMLInputElement || linked instanceof HTMLSelectElement) return linked;
  throw new Error(`Missing control ${text}`);
}

function toggleChecked(element: HTMLInputElement) {
  element.click();
}

function changeControl(element: HTMLSelectElement | HTMLInputElement, value: string) {
  const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, "value")?.set;
  if (!setter) throw new Error("No value setter");
  setter.call(element, value);
  element.dispatchEvent(new Event("input", { bubbles: true }));
  element.dispatchEvent(new Event("change", { bubbles: true }));
}

describe("ReportSourceFilters", () => {
  let root: Root | null = null;
  let host: HTMLDivElement | null = null;

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    host?.remove();
    root = null;
    host = null;
  });

  function render(initial?: ReportConfig) {
    const onPatch = vi.fn();
    host = document.createElement("div");
    document.body.appendChild(host);
    root = createRoot(host);
    act(() => {
      root?.render(<Harness initial={initial} onPatch={onPatch} />);
    });
    return onPatch;
  }

  it("enables the value filter and patches the column and spec", () => {
    const onPatch = render();
    const valueGroup = group(host!, "Filter (Optional)");
    expect(valueGroup.querySelector("select")).toBeNull();
    expect(valueGroup.querySelector("[data-testid='value-filter-picker']")).toBeNull();

    const enable = controlForLabel(valueGroup, "Pick values from list (IN rule)");
    if (!(enable instanceof HTMLInputElement)) throw new Error("Expected value-filter checkbox");
    act(() => {
      toggleChecked(enable);
    });

    expect(onPatch).toHaveBeenCalledWith({ sourceValueFilterEnabled: true });
    const column = controlForLabel(valueGroup, "Column");
    expect(column).toBeInstanceOf(HTMLSelectElement);
    expect(Array.from((column as HTMLSelectElement).options).map((option) => option.text)).toEqual([
      "— None —",
      "District",
      "full_name",
    ]);
    const picker = valueGroup.querySelector("[data-testid='value-filter-picker']");
    if (!(picker instanceof HTMLButtonElement)) throw new Error("Missing value filter picker");
    expect(picker.dataset.table).toBe("iceberg.srse.beneficiary");
    expect(picker.dataset.column).toBe("");
    expect(picker.dataset.spec).toBe("empty");

    act(() => {
      changeControl(column as HTMLSelectElement, "district");
    });
    expect(onPatch).toHaveBeenCalledWith({
      sourceValueFilterColumn: "district",
      sourceValueFilterSpec: null,
    });
    const columnPicker = valueGroup.querySelector("[data-testid='value-filter-picker']");
    if (!(columnPicker instanceof HTMLButtonElement)) throw new Error("Missing value filter picker");
    expect(columnPicker.dataset.column).toBe("district");
    expect(columnPicker.dataset.spec).toBe("empty");

    act(() => {
      columnPicker.click();
    });
    expect(onPatch).toHaveBeenCalledWith({ sourceValueFilterSpec: appliedSpec });
    expect(columnPicker.dataset.spec).toBe("set");
  });

  it("clears the value-filter spec when the source column changes", () => {
    const onPatch = render({
      ...EMPTY_REPORT_CONFIG,
      sourceValueFilterEnabled: true,
      sourceValueFilterColumn: "district",
      sourceValueFilterSpec: districtSpec,
    });
    const valueGroup = group(host!, "Filter (Optional)");
    const picker = valueGroup.querySelector("[data-testid='value-filter-picker']");
    expect(picker).toBeInstanceOf(HTMLButtonElement);
    expect((picker as HTMLButtonElement).dataset.spec).toBe("set");

    const column = controlForLabel(valueGroup, "Column");
    act(() => {
      changeControl(column as HTMLSelectElement, "full_name");
    });

    expect(onPatch).toHaveBeenCalledWith({
      sourceValueFilterColumn: "full_name",
      sourceValueFilterSpec: null,
    });
    const nextPicker = valueGroup.querySelector("[data-testid='value-filter-picker']");
    if (!(nextPicker instanceof HTMLButtonElement)) throw new Error("Missing value filter picker");
    expect(nextPicker.dataset.column).toBe("full_name");
    expect(nextPicker.dataset.spec).toBe("empty");
  });

  it("enables the fuzzy filter and patches each fuzzy field", () => {
    const onPatch = render();
    const fuzzyGroup = group(host!, "Fuzzy Matching (Optional)");
    expect(fuzzyGroup.querySelector("select")).toBeNull();
    expect(controlForLabel(fuzzyGroup, "Typed-text fuzzy match (inside scoped table)")).toBeInstanceOf(HTMLInputElement);

    const enable = controlForLabel(fuzzyGroup, "Typed-text fuzzy match (inside scoped table)");
    act(() => {
      toggleChecked(enable as HTMLInputElement);
    });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyEnabled: true });

    const column = controlForLabel(fuzzyGroup, "Column");
    const name = controlForLabel(fuzzyGroup, "Name to match");
    const threshold = controlForLabel(fuzzyGroup, "Threshold %");
    const ignoreSpaces = controlForLabel(fuzzyGroup, "Ignore spaces");
    const caseSensitive = controlForLabel(fuzzyGroup, "Case sensitive");
    expect(threshold).toBeInstanceOf(HTMLInputElement);
    expect((threshold as HTMLInputElement).min).toBe("0");
    expect((threshold as HTMLInputElement).max).toBe("100");

    act(() => {
      changeControl(column as HTMLSelectElement, "full_name");
      changeControl(name as HTMLInputElement, "Geeta Kumari");
      changeControl(threshold as HTMLInputElement, "150");
      toggleChecked(ignoreSpaces as HTMLInputElement);
      toggleChecked(caseSensitive as HTMLInputElement);
    });

    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyColumn: "full_name" });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyName: "Geeta Kumari" });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyThreshold: 100 });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyIgnoreSpaces: true });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyCaseSensitive: true });
    expect((threshold as HTMLInputElement).value).toBe("100");
    expect((ignoreSpaces as HTMLInputElement).checked).toBe(true);
    expect((caseSensitive as HTMLInputElement).checked).toBe(true);
  });

  it("keeps the value filter and fuzzy filter enabled together", () => {
    const onPatch = render();
    const valueGroup = group(host!, "Filter (Optional)");
    const fuzzyGroup = group(host!, "Fuzzy Matching (Optional)");
    const valueEnable = controlForLabel(valueGroup, "Pick values from list (IN rule)");
    const fuzzyEnable = controlForLabel(fuzzyGroup, "Typed-text fuzzy match (inside scoped table)");

    act(() => {
      toggleChecked(valueEnable as HTMLInputElement);
      toggleChecked(fuzzyEnable as HTMLInputElement);
    });

    expect(valueEnable).toBeInstanceOf(HTMLInputElement);
    expect((valueEnable as HTMLInputElement).checked).toBe(true);
    expect((fuzzyEnable as HTMLInputElement).checked).toBe(true);
    expect(valueGroup.querySelector("[data-testid='value-filter-picker']")).toBeInstanceOf(HTMLButtonElement);
    expect(controlForLabel(fuzzyGroup, "Name to match")).toBeInstanceOf(HTMLInputElement);
    expect(onPatch).toHaveBeenCalledWith({ sourceValueFilterEnabled: true });
    expect(onPatch).toHaveBeenCalledWith({ sourceFuzzyEnabled: true });
    expect(onPatch).not.toHaveBeenCalledWith(expect.objectContaining({ sourceValueFilterEnabled: false }));
    expect(onPatch).not.toHaveBeenCalledWith(expect.objectContaining({ sourceFuzzyEnabled: false }));
  });
});
