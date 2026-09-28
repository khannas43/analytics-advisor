import type { ReactNode } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { AuthSessionProvider } from "@/components/shell/AuthSessionProvider";
import { ShellProviders } from "@/components/shell/ShellProviders";

export default function ShellLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <ShellProviders>
      <AuthSessionProvider>
        <AppShell>{children}</AppShell>
      </AuthSessionProvider>
    </ShellProviders>
  );
}
