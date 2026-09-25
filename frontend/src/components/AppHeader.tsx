"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/extract", label: "Extract" },
  { href: "/analysis", label: "Analysis" },
  { href: "/admin456", label: "Admin" },
];

export function AppHeader() {
  const pathname = usePathname();

  return (
    <header className="srse-header">
      <div className="srse-header-inner">
        <Link href="/extract" className="srse-brand">
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
