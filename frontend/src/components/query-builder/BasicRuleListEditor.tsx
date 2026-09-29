"use client";

import { useShell } from "@/components/shell/ShellProviders";
import type { I18nKey } from "@/lib/i18n/catalog";
import type { RegisteredColumn, RuleOperator } from "@/lib/analysisApi";
import type { ExtractRuleRow } from "@/lib/queryBuilderPipelineTypes";

const BASIC_RULE_OPS: { value: RuleOperator; labelKey: I18nKey; needsValue: boolean }[] = [
  { value: "EQ", labelKey: "opEq", needsValue: true },
  { value: "NE", labelKey: "opNe", needsValue: true },
  { value: "LT", labelKey: "opLt", needsValue: true },
  { value: "LTE", labelKey: "opLte", needsValue: true },
  { value: "GT", labelKey: "opGt", needsValue: true },
  { value: "GTE", labelKey: "opGte", needsValue: true },
  { value: "IS_NULL", labelKey: "opIsNull", needsValue: false },
  { value: "NOT_NULL", labelKey: "opNotNull", needsValue: false },
];

function fillCatalog(template: string, values: Record<string, string | number>): string {
  return template.replaceAll(/\{(\w+)\}/g, (_match, name: string) => {
    const value = values[name];
    return value === undefined ? "" : String(value);
  });
}

export function BasicRuleListEditor({
  side,
  columns,
  rows,
  onChange,
}: Readonly<{
  side: "source" | "target";
  columns: RegisteredColumn[];
  rows: ExtractRuleRow[];
  onChange: (next: ExtractRuleRow[]) => void;
}>) {
  const { t } = useShell();
  const sideLabel = side === "source" ? t("basicRuleSideSource") : t("basicRuleSideTarget");
  const canAddRule = columns.length > 0;

  function addRule() {
    onChange([
      ...rows,
      {
        id: crypto.randomUUID(),
        column: columns[0]?.name ?? "",
        operator: "EQ",
        value: "",
      },
    ]);
  }

  function updateColumn(rowId: string, column: string) {
    onChange(rows.map((row) => (row.id === rowId ? { ...row, column } : row)));
  }

  function updateOperator(rowId: string, next: string) {
    const op = BASIC_RULE_OPS.find((item) => item.value === next);
    if (!op) return;
    onChange(
      rows.map((row) =>
        row.id === rowId
          ? { ...row, operator: op.value, value: op.needsValue ? row.value : "" }
          : row,
      ),
    );
  }

  function updateValue(rowId: string, value: string) {
    onChange(rows.map((row) => (row.id === rowId ? { ...row, value } : row)));
  }

  function removeRule(rowId: string) {
    onChange(rows.filter((row) => row.id !== rowId));
  }

  return (
    <div className="basic-rule-list" role="group" aria-label={fillCatalog(t("ariaBasicRuleList"), { side: sideLabel })}>
      {rows.length === 0 ? (
        <div className="empty basic-rule-list-empty">{t("msgNoRule")}</div>
      ) : (
        rows.map((row, index) => {
          const n = index + 1;
          const needsValue = BASIC_RULE_OPS.find((item) => item.value === row.operator)?.needsValue ?? true;
          const columnId = `basic-rule-${side}-${row.id}-column`;
          const operatorId = `basic-rule-${side}-${row.id}-operator`;
          const valueId = `basic-rule-${side}-${row.id}-value`;
          return (
            <div
              key={row.id}
              className={needsValue ? "basic-rule-row" : "basic-rule-row basic-rule-row-nullary"}
            >
              <div className="field">
                <label htmlFor={columnId}>{t("lblColumn")}</label>
                <select
                  id={columnId}
                  aria-label={fillCatalog(t("ariaBasicRuleColumn"), { side: sideLabel, n })}
                  value={row.column}
                  onChange={(event) => updateColumn(row.id, event.target.value)}
                >
                  <option value="">{t("selNone")}</option>
                  {columns.map((column) => (
                    <option key={column.name} value={column.name}>
                      {column.businessName ?? column.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor={operatorId}>{t("lblOperator")}</label>
                <select
                  id={operatorId}
                  aria-label={fillCatalog(t("ariaBasicRuleOperator"), { side: sideLabel, n })}
                  value={row.operator}
                  onChange={(event) => updateOperator(row.id, event.target.value)}
                >
                  {BASIC_RULE_OPS.map((op) => (
                    <option key={op.value} value={op.value}>
                      {t(op.labelKey)}
                    </option>
                  ))}
                </select>
              </div>
              {needsValue ? (
                <div className="field">
                  <label htmlFor={valueId}>{t("lblValue")}</label>
                  <input
                    id={valueId}
                    type="text"
                    aria-label={fillCatalog(t("ariaBasicRuleValue"), { side: sideLabel, n })}
                    value={row.value}
                    onChange={(event) => updateValue(row.id, event.target.value)}
                  />
                </div>
              ) : null}
              <button
                type="button"
                className="btn danger sm basic-rule-remove"
                aria-label={fillCatalog(t("ariaBasicRuleRemove"), { side: sideLabel, n })}
                onClick={() => removeRule(row.id)}
              >
                {t("btnRemove")}
              </button>
            </div>
          );
        })
      )}
      <button
        type="button"
        className="btn secondary sm basic-rule-list-add"
        aria-label={fillCatalog(t("ariaBasicRuleAdd"), { side: sideLabel })}
        disabled={!canAddRule}
        title={!canAddRule ? t("msgChooseTableBeforeRule") : undefined}
        onClick={addRule}
      >
        {t("btnAddRule")}
      </button>
      {!canAddRule ? <p className="text-muted basic-rule-list-hint">{t("msgChooseTableBeforeRule")}</p> : null}
    </div>
  );
}
