"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { isLockedResultColumn, visibleColumnIds } from "@/lib/resultTableColumns";

type Option = { value: string; label: string };

export function ResultColumnSelector({
  columns,
  hiddenColumnIds,
  onHiddenChange,
  labelFor,
  disabled,
  appearance = "prototype",
}: Readonly<{
  columns: string[];
  hiddenColumnIds: string[];
  onHiddenChange: (hidden: string[]) => void;
  labelFor: (id: string) => string;
  disabled?: boolean;
  appearance?: "legacy" | "prototype";
}>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const ref = useRef<HTMLDivElement>(null);
  const proto = appearance === "prototype";
  const visible = visibleColumnIds(columns, hiddenColumnIds);
  const options: Option[] = useMemo(
    () => columns.map((c) => ({ value: c, label: labelFor(c) })),
    [columns, labelFor],
  );
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }, [options, query]);

  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  function setVisible(columnId: string, show: boolean) {
    const hidden = new Set(hiddenColumnIds);
    if (show) {
      hidden.delete(columnId);
    } else if (!isLockedResultColumn(columnId)) {
      hidden.add(columnId);
    }
    const nextHidden = [...hidden];
    if (visibleColumnIds(columns, nextHidden).length === 0) return;
    onHiddenChange(nextHidden);
  }

  function selectAll() {
    onHiddenChange([]);
  }

  function clearAll() {
    const hide = columns.filter((c) => !isLockedResultColumn(c));
    if (visibleColumnIds(columns, hide).length === 0) return;
    onHiddenChange(hide);
  }

  function resetDefault() {
    onHiddenChange([]);
  }

  const btnClass = proto ? "btn secondary sm" : "srse-btn srse-btn-sm";

  return (
    <div className="result-column-selector" ref={ref} style={{ position: "relative" }}>
      <button
        type="button"
        className={btnClass}
        aria-expanded={open}
        aria-haspopup="dialog"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
      >
        Columns ({visible.length}/{columns.length})
      </button>
      {open && (
        <div
          className="result-column-panel"
          role="dialog"
          aria-label="Choose visible columns"
        >
          <input
            type="search"
            className="result-column-search"
            placeholder="Search columns…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search columns"
          />
          <div className="result-column-actions">
            <button type="button" className={btnClass} onClick={selectAll}>
              Select all
            </button>
            <button type="button" className={btnClass} onClick={clearAll}>
              Clear all
            </button>
            <button type="button" className={btnClass} onClick={resetDefault}>
              Reset
            </button>
          </div>
          <ul className="result-column-list">
            {filtered.map((opt) => {
              const checked = !hiddenColumnIds.includes(opt.value);
              const locked = isLockedResultColumn(opt.value);
              return (
                <li key={opt.value}>
                  <label className={proto ? "checkbox-row" : "srse-checkbox-label"}>
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={locked}
                      onChange={(e) => setVisible(opt.value, e.target.checked)}
                    />
                    {opt.label}
                    {locked ? " (required)" : ""}
                  </label>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
