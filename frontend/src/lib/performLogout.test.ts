// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { performLogout } from "@/lib/performLogout";
import { storeAuthToken, clearAuthToken } from "@/lib/authToken";
import { useQueryResultsStore } from "@/lib/queryResultsStore";

describe("performLogout", () => {
  const fetchMock = vi.fn();
  const replaceMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockResolvedValue({ ok: true });
    replaceMock.mockReset();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { replace: replaceMock },
    });
    sessionStorage.clear();
    useQueryResultsStore.getState().clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearAuthToken();
  });

  it("posts logout, clears token and query cache, then redirects", async () => {
    storeAuthToken("test-token");
    useQueryResultsStore.getState().setLastResult({
      columns: ["id"],
      rows: [{ id: 1 }],
      totalRows: 1,
      source: "extract",
    });

    await performLogout();

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/logout"),
      expect.objectContaining({
        method: "POST",
        headers: { Authorization: "Bearer test-token" },
      }),
    );
    expect(sessionStorage.getItem("srse.auth.token")).toBeNull();
    expect(useQueryResultsStore.getState().lastResult).toBeNull();
    expect(replaceMock).toHaveBeenCalledWith("/login");
  });

  it("still clears locally when no token is stored", async () => {
    await performLogout();
    const logoutCalls = fetchMock.mock.calls.filter((c) => String(c[0]).includes("/api/auth/logout"));
    expect(logoutCalls).toHaveLength(0);
    expect(replaceMock).toHaveBeenCalledWith("/login");
  });
});
