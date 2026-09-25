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

export function ShellProviders({ children }: Readonly<{ children: ReactNode }>) {
  const [theme, setTheme] = useState<ThemeMode>("dark");
  const [lang, setLang] = useState<I18nLang>("en");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    const storedTheme = localStorage.getItem(THEME_KEY) as ThemeMode | null;
    const storedLang = localStorage.getItem(LANG_KEY) as I18nLang | null;
    if (storedTheme === "light" || storedTheme === "dark") {
      setTheme(storedTheme);
    }
    if (storedLang === "en" || storedLang === "hi") {
      setLang(storedLang);
    }
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) {
      return;
    }
    document.documentElement.setAttribute("data-theme", theme);
    document.documentElement.setAttribute("data-lang", lang);
    localStorage.setItem(THEME_KEY, theme);
    localStorage.setItem(LANG_KEY, lang);
  }, [theme, lang, hydrated]);

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
