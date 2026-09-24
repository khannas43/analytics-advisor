"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [{ href: "/analysis", label: "Analysis" }];

export function AppHeader() {
  const pathname = usePathname();

  return (
    <header className="srse-header">
      <div className="srse-header-inner">
        <Link href="/analysis" className="srse-brand">
          <span className="srse-brand-mark">Analytics Advisor</span>
          <span className="srse-brand-sub">Lakehouse record matching</span>
        </Link>
        <nav className="srse-nav">
          {TABS.map((tab) => {
            const active = pathname === tab.href || pathname?.startsWith(`${tab.href}/`);
            return (
              <Link
                key={tab.href}
                href={tab.href}
                className={active ? "srse-nav-link active" : "srse-nav-link"}
              >
                {tab.label}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
