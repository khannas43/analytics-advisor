import type { ReactNode } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { ShellProviders } from "@/components/shell/ShellProviders";

export default function ShellLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <ShellProviders>
      <AppShell>{children}</AppShell>
    </ShellProviders>
  );
}
