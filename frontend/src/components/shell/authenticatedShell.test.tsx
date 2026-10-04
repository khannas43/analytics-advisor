// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react";
import { AuthenticatedUserBar } from "@/components/shell/AuthenticatedUserBar";
import { AuthSessionProvider } from "@/components/shell/AuthSessionProvider";
import { ShellProviders } from "@/components/shell/ShellProviders";

const performLogoutMock = vi.fn().mockResolvedValue(undefined);

vi.mock("@/lib/performLogout", () => ({
  performLogout: (...args: unknown[]) => performLogoutMock(...args),
}));

vi.mock("@/lib/authToken", () => ({
  isLocalAuthMode: () => false,
  switchActiveRole: vi.fn(),
  fetchAuthSession: vi.fn().mockResolvedValue({
    username: "superadmin",
    roles: ["SUPER_ADMIN"],
    authorities: [],
    admin: true,
    superAdmin: true,
    auditReader: false,
    mustChangePassword: false,
    active: true,
  }),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/overview",
}));

function renderBar() {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <ShellProviders>
        <AuthSessionProvider>
          <AuthenticatedUserBar />
        </AuthSessionProvider>
      </ShellProviders>,
    );
  });
  return { container, root };
}

function mockMatchMedia() {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

describe("AuthenticatedUserBar", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;

  beforeEach(() => {
    mockMatchMedia();
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container?.remove();
    root = null;
    container = null;
  });

  it("shows username and Super Admin role", async () => {
    ({ container, root } = renderBar());
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0));
    });
    expect(container!.textContent).toContain("superadmin");
    expect(container!.textContent).toContain("Super Admin");
  });

  it("invokes logout flow when logout is clicked", async () => {
    ({ container, root } = renderBar());
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0));
    });
    const btn = container!.querySelector(".app-user-bar-logout") as HTMLButtonElement;
    expect(btn).toBeTruthy();
    await act(async () => {
      btn.click();
      await new Promise((r) => setTimeout(r, 0));
    });
    expect(performLogoutMock).toHaveBeenCalled();
  });
});
