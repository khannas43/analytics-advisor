"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode } from "react";
import { AuthenticatedUserBar } from "@/components/shell/AuthenticatedUserBar";
import { useAuthSession } from "@/components/shell/AuthSessionProvider";
import { BrandMark } from "@/components/shell/BrandMark";
import { useShell } from "@/components/shell/ShellProviders";

const NAV = [
  { href: "/overview", labelKey: "navOverview" as const, icon: "folder" },
  { href: "/query-builder", labelKey: "navQueryBuilder" as const, icon: "wrench" },
  { href: "/dashboard", labelKey: "navDashboard" as const, icon: "chart" },
];

function NavIcon({ kind }: Readonly<{ kind: string }>) {
  if (kind === "folder") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden>
        <path d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
      </svg>
    );
  }
  if (kind === "wrench") {
    return (
      <svg viewBox="0 0 24 24" aria-hidden>
        <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path d="M18 20V10M12 20V4M6 20v-6" />
    </svg>
  );
}

export function AppShell({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const { t, theme, lang, sidebarCollapsed, setSidebarCollapsed, toggleTheme, toggleLang } = useShell();
  const { session } = useAuthSession();

  const showAdmin = Boolean(session?.admin);

  return (
    <div className="app">
      <aside className={`sidebar${sidebarCollapsed ? " collapsed" : ""}`} id="app-sidebar">
        <div className="brand">
          <div className="brand-left">
            <BrandMark />
          </div>
          <button
            type="button"
            className="panel-toggle-btn"
            title="Collapse sidebar"
            aria-label="Collapse sidebar"
            onClick={() => setSidebarCollapsed(true)}
          >
            ‹
          </button>
        </div>

        <nav className="nav">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={active ? "active nav-link" : "nav-link"}
              >
                <NavIcon kind={item.icon} />
                <span>{t(item.labelKey)}</span>
              </Link>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          {showAdmin && (
            <div className="control-group">
              <Link href="/admin456" className="theme-switch-btn" style={{ textDecoration: "none" }}>
                <span>⚙</span>
                <span>{t("navAdmin")}</span>
              </Link>
            </div>
          )}
          <div className="control-group">
            <span>{t("lblTheme")}</span>
            <button type="button" className="theme-switch-btn" onClick={toggleTheme}>
              <span>{theme === "dark" ? "☀️" : "🌙"}</span>
              <span>{theme === "dark" ? t("themeLight") : t("themeDark")}</span>
            </button>
          </div>
          <div className="control-group">
            <span>{t("lblLanguage")}</span>
            <button type="button" className="lang-toggle-btn" onClick={toggleLang}>
              <span>🌐</span>
              <span>{lang === "en" ? "हिन्दी (HI)" : "English (EN)"}</span>
            </button>
          </div>
        </div>
      </aside>

      {sidebarCollapsed && (
        <button
          type="button"
          className="sidebar-reopen-btn"
          title="Expand Sidebar"
          aria-label="Expand sidebar"
          onClick={() => setSidebarCollapsed(false)}
        >
          ›
        </button>
      )}

      {!sidebarCollapsed && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label="Close sidebar"
          onClick={() => setSidebarCollapsed(true)}
        />
      )}

      <main className="main main-with-user-bar" id="main-content">
        <div className="main-user-bar-row">
          <AuthenticatedUserBar />
        </div>
        <div className="main-body">{children}</div>
      </main>
    </div>
  );
}
