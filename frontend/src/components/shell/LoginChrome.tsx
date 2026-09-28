"use client";

import { BrandMark } from "@/components/shell/BrandMark";
import { useShell } from "@/components/shell/ShellProviders";

export function LoginChromeHeader() {
  const { t, theme, toggleTheme, toggleLang, lang } = useShell();
  return (
    <header className="login-chrome-header">
      <BrandMark />
      <div className="login-chrome-header-actions">
        <button type="button" className="theme-switch-btn" onClick={toggleTheme}>
          <span aria-hidden>{theme === "dark" ? "☀" : "☽"}</span>
          <span className="login-chrome-action-label">
            {theme === "dark" ? t("themeLight") : t("themeDark")}
          </span>
        </button>
        <button type="button" className="lang-toggle-btn" onClick={toggleLang}>
          <span aria-hidden>🌐</span>
          <span className="login-chrome-action-label">
            {lang === "en" ? "हिन्दी (HI)" : "English (EN)"}
          </span>
        </button>
      </div>
    </header>
  );
}

export function LoginChromeFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="login-chrome-footer">
      <p className="login-footer-title">Analytics Advisor</p>
      <p className="login-footer-tagline">Secure data analysis platform</p>
      <p className="login-footer-meta">© {year} · Authorized users only · Protect credentials and sign out when finished</p>
    </footer>
  );
}
