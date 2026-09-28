"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { type I18nKey, type I18nLang, translate } from "@/lib/i18n/catalog";

type ThemeMode = "light" | "dark";

type ShellContextValue = {
  theme: ThemeMode;
  lang: I18nLang;
  sidebarCollapsed: boolean;
  setSidebarCollapsed: (v: boolean) => void;
  toggleTheme: () => void;
  toggleLang: () => void;
  t: (key: I18nKey) => string;
  modeKey: (dualKey: I18nKey, singleKey: I18nKey, dual: boolean) => string;
};

const ShellContext = createContext<ShellContextValue | null>(null);

const THEME_KEY = "aa-theme";
const LANG_KEY = "aa-lang";
const SIDEBAR_KEY = "aa-sidebar-collapsed";
const MOBILE_BREAKPOINT_PX = 767;

function readStoredTheme(): ThemeMode {
  if (typeof window === "undefined") {
    return "light";
  }
  const stored = localStorage.getItem(THEME_KEY) as ThemeMode | null;
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  return "light";
}

function readStoredLang(): I18nLang {
  if (typeof window === "undefined") {
    return "en";
  }
  const stored = localStorage.getItem(LANG_KEY) as I18nLang | null;
  if (stored === "en" || stored === "hi") {
    return stored;
  }
  return "en";
}

function readInitialSidebarCollapsed(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  if (window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT_PX}px)`).matches) {
    return true;
  }
  return sessionStorage.getItem(SIDEBAR_KEY) === "1";
}

export function ShellProviders({ children }: Readonly<{ children: ReactNode }>) {
  /** SSR-safe defaults; sync from storage after mount to avoid React #418 hydration drift. */
  const [theme, setTheme] = useState<ThemeMode>("light");
  const [lang, setLang] = useState<I18nLang>("en");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  useEffect(() => {
    const id = requestAnimationFrame(() => {
      setTheme(readStoredTheme());
      setLang(readStoredLang());
      setSidebarCollapsed(readInitialSidebarCollapsed());
    });
    return () => cancelAnimationFrame(id);
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    document.documentElement.setAttribute("data-lang", lang);
    localStorage.setItem(THEME_KEY, theme);
    localStorage.setItem(LANG_KEY, lang);
  }, [theme, lang]);

  useEffect(() => {
    sessionStorage.setItem(SIDEBAR_KEY, sidebarCollapsed ? "1" : "0");
  }, [sidebarCollapsed]);

  const toggleTheme = useCallback(() => {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }, []);

  const toggleLang = useCallback(() => {
    setLang((l) => (l === "en" ? "hi" : "en"));
  }, []);

  const value = useMemo<ShellContextValue>(
    () => ({
      theme,
      lang,
      sidebarCollapsed,
      setSidebarCollapsed,
      toggleTheme,
      toggleLang,
      t: (key) => translate(lang, key),
      modeKey: (dualKey, singleKey, dual) => translate(lang, dual ? dualKey : singleKey),
    }),
    [theme, lang, sidebarCollapsed, toggleTheme, toggleLang],
  );

  return <ShellContext.Provider value={value}>{children}</ShellContext.Provider>;
}

export function useShell(): ShellContextValue {
  const ctx = useContext(ShellContext);
  if (!ctx) {
    throw new Error("useShell must be used within ShellProviders");
  }
  return ctx;
}
