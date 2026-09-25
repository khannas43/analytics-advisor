"use client";

import { useMemo } from "react";
import Link from "next/link";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useShell } from "@/components/shell/ShellProviders";
import { useQueryResultsStore } from "@/lib/queryResultsStore";

const PIE_COLORS = ["#38bdf8", "#6366f1", "#10b981", "#f59e0b", "#ef4444", "#a855f7"];

export default function DashboardPage() {
  const { t } = useShell();
  const lastResult = useQueryResultsStore((s) => s.lastResult);

  const { numericCols, chartData, tablePreview } = useMemo(() => {
    if (!lastResult?.rows.length) {
      return { numericCols: [] as string[], chartData: [] as { name: string; count: number }[], tablePreview: [] as Record<string, unknown>[] };
    }
    const { columns, rows } = lastResult;
    const numeric = columns.filter((c) =>
      rows.every((r) => {
        const v = r[c];
        if (v === null || v === undefined || v === "") {
          return true;
        }
        return !Number.isNaN(Number(v));
      }),
    );
    const groupCol = columns[0];
    const counts = new Map<string, number>();
    if (groupCol) {
      for (const row of rows) {
        const k = String(row[groupCol] ?? "—");
        counts.set(k, (counts.get(k) ?? 0) + 1);
      }
    }
    const chartData = [...counts.entries()]
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 12);
    return { numericCols: numeric, chartData, tablePreview: rows.slice(0, 50) };
  }, [lastResult]);

  return (
    <div className="page active">
      <div className="top-action-bar">
        <div className="page-header">
          <h2>{t("p3Title")}</h2>
          <p className="sub">{t("p3Sub")}</p>
        </div>
      </div>

      {!lastResult?.rows.length ? (
        <div className="empty">{t("msgDashEmpty")}</div>
      ) : (
        <div id="dash-body">
          <div className="stat-grid">
            <div className="stat">
              <div className="num">{lastResult.totalRows ?? lastResult.rows.length}</div>
              <div className="lbl">{t("statResultRows")}</div>
            </div>
            <div className="stat">
              <div className="num">{lastResult.columns.length}</div>
              <div className="lbl">{t("statColsSelected")}</div>
            </div>
            <div className="stat">
              <div className="num">{numericCols.length}</div>
              <div className="lbl">{t("statNumericCols")}</div>
            </div>
          </div>

          <div className="section">
            <h4>{t("secGroupCount")}</h4>
            <p className="desc">
              {lastResult.columns[0] ? (
                <>
                  <b>{lastResult.columns[0]}</b>
                </>
              ) : null}
            </p>
            <div style={{ width: "100%", height: 280 }}>
              <ResponsiveContainer>
                <BarChart data={chartData} layout="vertical" margin={{ left: 8, right: 16 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis type="number" stroke="var(--text-muted)" />
                  <YAxis type="category" dataKey="name" width={120} stroke="var(--text-muted)" tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill="var(--primary)" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {chartData.length > 0 && (
            <div className="section">
              <h4>{t("secGroupCount")} (pie)</h4>
              <div style={{ width: "100%", height: 260 }}>
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={chartData} dataKey="count" nameKey="name" outerRadius={90} label>
                      {chartData.map((_, i) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          <div className="section">
            <h4>{t("lblReportResults")}</h4>
            <div className="results-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    {lastResult.columns.map((c) => (
                      <th key={c}>{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tablePreview.map((row, i) => (
                    <tr key={i}>
                      {lastResult.columns.map((c) => (
                        <td key={c}>{String(row[c] ?? "")}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      <p className="desc" style={{ marginTop: "1rem" }}>
        <Link href="/query-builder">{t("navQueryBuilder")}</Link>
      </p>
    </div>
  );
}
