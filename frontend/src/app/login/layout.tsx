import type { ReactNode } from "react";
import { ShellProviders } from "@/components/shell/ShellProviders";

export default function LoginLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <ShellProviders>{children}</ShellProviders>;
}
