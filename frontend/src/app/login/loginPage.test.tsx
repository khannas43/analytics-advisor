// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react";
import LoginPage from "@/app/login/page";
import { ShellProviders } from "@/components/shell/ShellProviders";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/authToken", () => ({
  isLocalAuthMode: () => true,
  storeAuthToken: vi.fn(),
}));

function mockMatchMedia() {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("767"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

describe("LoginPage", () => {
  let root: Root | null = null;
  let container: HTMLDivElement;

  beforeEach(() => {
    mockMatchMedia();
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    container.remove();
    root = null;
  });

  it("renders header, login form and footer without authenticated user controls", () => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => {
      root!.render(
        <ShellProviders>
          <LoginPage />
        </ShellProviders>,
      );
    });

    expect(document.querySelector(".login-chrome-header")).toBeTruthy();
    expect(document.querySelector(".login-card")).toBeTruthy();
    expect(document.querySelector(".login-chrome-footer")).toBeTruthy();
    expect(document.querySelector("#login-username")).toBeTruthy();
    expect(document.querySelector("#login-password")).toBeTruthy();
    expect(document.querySelector(".app-user-bar")).toBeNull();
    expect(document.querySelector(".app-user-bar-logout")).toBeNull();
    expect(container.textContent).toContain("Sign in to Analytics Advisor");
    expect(container.textContent).toContain("access the data analysis workspace");
    expect(container.textContent).toContain("Secure data analysis platform");
    expect(container.textContent).not.toContain("Welcome back");
    expect(container.textContent?.toLowerCase()).not.toContain("lakehouse");
  });

  it("uses responsive login chrome structure for narrow layouts", () => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => {
      root!.render(
        <ShellProviders>
          <LoginPage />
        </ShellProviders>,
      );
    });
    const page = document.querySelector(".login-page") as HTMLElement;
    expect(page).toBeTruthy();
    expect(document.querySelector(".login-chrome-header-actions")).toBeTruthy();
    expect(document.querySelector(".login-card")?.getAttribute("class")).toContain("login-card");
    expect(page.scrollWidth).toBeLessThanOrEqual(Math.max(document.documentElement.clientWidth, 390) + 1);
  });
});
