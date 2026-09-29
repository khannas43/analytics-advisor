// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { BasicRuleListEditor } from "@/components/query-builder/BasicRuleListEditor";
import type { RegisteredColumn } from "@/lib/analysisApi";
import type { ExtractRuleRow } from "@/lib/queryBuilderPipelineTypes";

vi.mock("@/components/shell/ShellProviders", () => ({
  useShell: () => ({
    t: (key: string) =>
      ({
        btnAddRule: "Add rule",
        btnRemove: "Remove",
        msgNoRule: "No rule — all rows of the table are used.",
        lblColumn: "Column",
        lblOperator: "Operator",
        lblValue: "Value",
        selNone: "— None —",
        basicRuleSideSource: "Source",
        basicRuleSideTarget: "Destination",
        ariaBasicRuleList: "{side} rules",
        ariaBasicRuleAdd: "Add {side} rule",
        ariaBasicRuleColumn: "{side} rule {n} column",
        ariaBasicRuleOperator: "{side} rule {n} operator",
        ariaBasicRuleValue: "{side} rule {n} value",
        ariaBasicRuleRemove: "Remove {side} rule {n}",
        opEq: "is equal to (=)",
        opNe: "is not equal to (≠)",
        opLt: "is less than (<)",
        opLte: "is less or equal (≤)",
        opGt: "is greater than (>)",
        opGte: "is greater or equal (≥)",
        opIsNull: "is null",
        opNotNull: "is not null",
      })[key] ?? key,
  }),
}));

const columns: RegisteredColumn[] = [
  { name: "age", dataType: "integer", businessName: "Age", fuzzyMatchable: false },
  { name: "name", dataType: "varchar", businessName: null, fuzzyMatchable: false },
];

const ADDED_ID = "00000000-0000-4000-8000-000000000001";

function Harness({ initial = [] }: { initial?: ExtractRuleRow[] }) {
  const [rows, setRows] = useState(initial);
  return <BasicRuleListEditor side="source" columns={columns} rows={rows} onChange={setRows} />;
}

function selectByLabel(container: ParentNode, label: string): HTMLSelectElement {
  const found = container.querySelector(`select[aria-label="${label}"]`);
  if (!(found instanceof HTMLSelectElement)) throw new Error(`Missing select ${label}`);
  return found;
}

function inputByLabel(container: ParentNode, label: string): HTMLInputElement {
  const found = container.querySelector(`input[aria-label="${label}"]`);
  if (!(found instanceof HTMLInputElement)) throw new Error(`Missing input ${label}`);
  return found;
}

function buttonByLabel(container: ParentNode, label: string): HTMLButtonElement {
  const found = container.querySelector(`button[aria-label="${label}"]`);
  if (!(found instanceof HTMLButtonElement)) throw new Error(`Missing button ${label}`);
  return found;
}

function changeControl(element: HTMLSelectElement | HTMLInputElement, value: string) {
  const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, "value")?.set;
  if (!setter) throw new Error("No value setter");
  setter.call(element, value);
  element.dispatchEvent(new Event("input", { bubbles: true }));
  element.dispatchEvent(new Event("change", { bubbles: true }));
}

describe("BasicRuleListEditor", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;

  afterEach(() => {
    act(() => root?.unmount());
    container?.remove();
    root = null;
    container = null;
    vi.restoreAllMocks();
  });

  async function render(initial?: ExtractRuleRow[]) {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root?.render(<Harness initial={initial} />);
    });
  }

  it("adds a rule with crypto.randomUUID()", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue(ADDED_ID);
    await render();

    expect(container?.textContent).toContain("No rule — all rows of the table are used.");
    expect(container?.querySelector("[style]")).toBeNull();

    await act(async () => {
      buttonByLabel(container!, "Add Source rule").click();
    });

    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("age");
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("EQ");
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("");
    expect(crypto.randomUUID).toHaveBeenCalledOnce();
  });

  it("updates column, operator, and value independently", async () => {
    await render([{ id: "row-1", column: "age", operator: "EQ", value: "18" }]);

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 column"), "name");
    });
    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("name");
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("EQ");
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("18");

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 operator"), "GT");
    });
    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("name");
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("GT");
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("18");

    await act(async () => {
      changeControl(inputByLabel(container!, "Source rule 1 value"), "21");
    });
    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("name");
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("GT");
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("21");
  });

  it("hides and clears the value for IS_NULL and NOT_NULL", async () => {
    await render([{ id: "row-1", column: "age", operator: "EQ", value: "18" }]);

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 operator"), "IS_NULL");
    });
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("IS_NULL");
    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("age");
    expect(container?.querySelector('input[aria-label="Source rule 1 value"]')).toBeNull();

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 operator"), "EQ");
    });
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("");

    await act(async () => {
      changeControl(inputByLabel(container!, "Source rule 1 value"), "9");
    });
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("9");

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 operator"), "NOT_NULL");
    });
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("NOT_NULL");
    expect(container?.querySelector('input[aria-label="Source rule 1 value"]')).toBeNull();

    await act(async () => {
      changeControl(selectByLabel(container!, "Source rule 1 operator"), "LTE");
    });
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("");
  });

  it("removes one row and leaves the other", async () => {
    await render([
      { id: "row-1", column: "age", operator: "EQ", value: "18" },
      { id: "row-2", column: "name", operator: "NE", value: "Ada" },
    ]);

    await act(async () => {
      buttonByLabel(container!, "Remove Source rule 1").click();
    });

    expect(container?.querySelector('button[aria-label="Remove Source rule 1"]')).toBeTruthy();
    expect(selectByLabel(container!, "Source rule 1 column").value).toBe("name");
    expect(selectByLabel(container!, "Source rule 1 operator").value).toBe("NE");
    expect(inputByLabel(container!, "Source rule 1 value").value).toBe("Ada");
    expect(container?.querySelector('button[aria-label="Remove Source rule 2"]')).toBeNull();
  });
});
