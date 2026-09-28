import type { ReactNode } from "react";
import { AuthenticatedUserBar } from "@/components/shell/AuthenticatedUserBar";
import { AuthSessionProvider } from "@/components/shell/AuthSessionProvider";
import { ShellProviders } from "@/components/shell/ShellProviders";

export default function ChangePasswordLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <ShellProviders>
      <AuthSessionProvider>
        <div className="admin-standalone-shell change-password-shell">
          <header className="admin-standalone-top admin-standalone-top-end">
            <AuthenticatedUserBar />
          </header>
          {children}
        </div>
      </AuthSessionProvider>
    </ShellProviders>
  );
}
