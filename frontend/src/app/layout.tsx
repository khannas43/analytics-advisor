import type { ReactNode } from "react";
import "./globals.css";
import { AppHeader } from "@/components/AppHeader";

export const metadata = {
  title: "Analytics Advisor",
  description: "Lakehouse record matching and analysis",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <AppHeader />
        {children}
      </body>
    </html>
  );
}
