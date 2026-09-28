"use client";

import { Suspense } from "react";
import { AdminWorkspace } from "@/components/admin/AdminWorkspace";

export default function AdminPage() {
  return (
    <Suspense
      fallback={
        <main className="admin-workspace">
          <p className="admin-workspace-status">Loading admin…</p>
        </main>
      }
    >
      <AdminWorkspace />
    </Suspense>
  );
}
