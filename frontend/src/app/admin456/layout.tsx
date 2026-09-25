"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  fetchAuthSession,
  isLocalAuthMode,
  type AuthSession,
} from "@/lib/authToken";

export default function AdminLayout({ children }: Readonly<{ children: ReactNode }>) {
  const router = useRouter();
  const [session, setSession] = useState<AuthSession | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const s = await fetchAuthSession();
        if (cancelled) return;
        if (!s) {
          if (isLocalAuthMode()) {
            router.replace("/login?returnTo=/admin456");
          }
          setSession(null);
          return;
        }
        if (s.mustChangePassword) {
          router.replace("/change-password");
          return;
        }
        if (!s.admin) {
          setSession(null);
          return;
        }
        setSession(s);
      } catch {
        if (!cancelled) setSession(null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [router]);

  if (session === undefined) {
    return (
      <main className="srse-page">
        <p className="srse-muted">Checking access…</p>
      </main>
    );
  }

  if (!session?.admin) {
    return (
      <main className="srse-page">
        <p className="srse-error-text">
          {session === null && !isLocalAuthMode()
            ? "This account is not an SRSE administrator."
            : "Admin access required."}
        </p>
      </main>
    );
  }

  return children;
}
