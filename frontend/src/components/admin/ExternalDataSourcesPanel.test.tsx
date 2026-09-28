// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { ExternalDataSourcesPanel } from "@/components/admin/ExternalDataSourcesPanel";

const listExternalSourceTypes = vi.hoisted(() => vi.fn());
const listExternalDataSources = vi.hoisted(() => vi.fn());
const updateExternalFederationCatalog = vi.hoisted(() => vi.fn());
const browseExternalCatalogs = vi.hoisted(() => vi.fn());
const browseExternalSchemas = vi.hoisted(() => vi.fn());
const browseExternalTables = vi.hoisted(() => vi.fn());
const browseExternalColumns = vi.hoisted(() => vi.fn());
const listExternalTableRegistrations = vi.hoisted(() => vi.fn());

vi.mock("@/lib/externalDataSourceApi", () => ({
  listExternalSourceTypes: (...args: unknown[]) => listExternalSourceTypes(...args),
  listExternalDataSources: (...args: unknown[]) => listExternalDataSources(...args),
  createExternalDataSource: vi.fn(),
  testExternalDataSource: vi.fn(),
  deleteExternalDataSource: vi.fn(),
  updateExternalFederationCatalog: (...args: unknown[]) => updateExternalFederationCatalog(...args),
  browseExternalCatalogs: (...args: unknown[]) => browseExternalCatalogs(...args),
  browseExternalSchemas: (...args: unknown[]) => browseExternalSchemas(...args),
  browseExternalTables: (...args: unknown[]) => browseExternalTables(...args),
  browseExternalColumns: (...args: unknown[]) => browseExternalColumns(...args),
  listExternalTableRegistrations: (...args: unknown[]) => listExternalTableRegistrations(...args),
  registerExternalTable: vi.fn(),
  unregisterExternalTable: vi.fn(),
}));

const source = {
  id: 3,
  name: "Payroll",
  databaseType: "POSTGRESQL",
  databaseTypeLabel: "PostgreSQL",
  jdbcUrl: "jdbc:postgresql://db.internal:5432/egov",
  username: "reader",
  federationCatalog: "egov",
  active: true,
  passwordConfigured: true,
  lastTestedAt: null,
  lastTestStatus: "UP",
  lastTestMessage: "Connected",
  registrationCount: 1,
};

function button(container: ParentNode, label: string): HTMLButtonElement {
  const found = Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.trim() === label);
  if (!found) throw new Error(`Missing button ${label}`);
  return found;
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe("External data sources panel", () => {
  let root: Root | null = null;
  let container: HTMLDivElement | null = null;
  let prompt: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    prompt = vi.spyOn(window, "prompt").mockImplementation(() => {
      throw new Error("prompt should not be used");
    });
    listExternalSourceTypes.mockResolvedValue([{ code: "POSTGRESQL", label: "PostgreSQL", defaultPort: 5432 }]);
    listExternalDataSources.mockResolvedValue([source]);
    browseExternalCatalogs.mockResolvedValue(["egov"]);
    browseExternalSchemas.mockResolvedValue(["public", "audit"]);
    browseExternalTables.mockResolvedValue({
      items: [
        { catalog: "egov", schema: "public", name: "people", type: "TABLE", remarks: null },
        { catalog: "egov", schema: "audit", name: "people", type: "TABLE", remarks: null },
      ],
      page: 0,
      size: 25,
      total: 2,
    });
    browseExternalColumns.mockResolvedValue([]);
    listExternalTableRegistrations.mockResolvedValue([
      {
        registrationId: 9,
        logicalCatalog: "jdbc_3",
        schema: "public",
        table: "people",
        sourceName: "Payroll",
        physicalCatalog: "egov",
        layer: null,
        sourceSystem: "Payroll",
        tableGroup: null,
      },
    ]);
    updateExternalFederationCatalog.mockReset();
  });

  afterEach(() => {
    act(() => root?.unmount());
    container?.remove();
    root = null;
    container = null;
    prompt.mockRestore();
  });

  async function renderPanel() {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => {
      root?.render(<ExternalDataSourcesPanel />);
    });
    await flush();
    await flush();
    await flush();
  }

  it("edits the federation alias in a dialog and never prompts", async () => {
    await renderPanel();
    expect(container?.textContent).toContain("Remove its 1 registered table");
    expect(button(container!, "Remove").disabled).toBe(true);

    act(() => button(container!, "Federation").click());
    const dialog = container!.querySelector("[role='dialog']");
    expect(dialog).toBeTruthy();
    expect(dialog?.textContent).toContain("Presto catalog alias");
    expect(dialog?.textContent).toContain("Leave the alias blank");
    expect(dialog?.querySelector("input[type='password']")).toBeNull();
    expect(dialog?.querySelector("input")?.getAttribute("value") ?? (dialog?.querySelector("input") as HTMLInputElement).value).toBe("egov");
    expect(prompt).not.toHaveBeenCalled();

    act(() => button(dialog!, "Cancel").click());
    expect(container!.querySelector("[role='dialog']")).toBeNull();
    expect(updateExternalFederationCatalog).not.toHaveBeenCalled();

    act(() => button(container!, "Federation").click());
    const open = container!.querySelector("[role='dialog']") as HTMLElement;
    await act(async () => {
      open.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    expect(container!.querySelector("[role='dialog']")).toBeNull();

    act(() => button(container!, "Federation").click());
    const form = container!.querySelector("[role='dialog']") as HTMLFormElement;
    const input = form.querySelector("input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    await act(async () => {
      nativeSetter?.call(input, "  finance_catalog  ");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    updateExternalFederationCatalog.mockResolvedValue({ ...source, federationCatalog: "finance_catalog" });
    await act(async () => {
      button(form, "Save").click();
    });
    await flush();
    expect(updateExternalFederationCatalog).toHaveBeenCalledWith(3, "finance_catalog");
    expect(prompt).not.toHaveBeenCalled();
  });

  it("keeps the dialog open and shows the save error", async () => {
    updateExternalFederationCatalog.mockRejectedValue(new Error("Alias was rejected"));
    await renderPanel();
    act(() => button(container!, "Federation").click());
    const form = container!.querySelector("[role='dialog']") as HTMLFormElement;
    await act(async () => {
      button(form, "Save").click();
    });
    await flush();
    expect(form.getAttribute("role")).toBe("dialog");
    expect(container!.querySelector("[role='alert']")?.textContent).toContain("Alias was rejected");
    expect(container!.querySelector("input[type='password']")).toBeNull();
  });

  it("shows saving state and rejects an unsafe alias locally", async () => {
    let resolveSave: (value: unknown) => void = () => {};
    updateExternalFederationCatalog.mockReturnValue(new Promise((resolve) => {
      resolveSave = resolve;
    }));
    await renderPanel();
    act(() => button(container!, "Federation").click());
    const form = container!.querySelector("[role='dialog']") as HTMLFormElement;
    const input = form.querySelector("input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    await act(async () => {
      nativeSetter?.call(input, "egov catalog");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      button(form, "Save").click();
    });
    expect(container!.querySelector("[role='alert']")?.textContent).toContain("underscores");
    expect(updateExternalFederationCatalog).not.toHaveBeenCalled();

    await act(async () => {
      nativeSetter?.call(input, "finance");
      input.dispatchEvent(new Event("input", { bubbles: true }));
      button(form, "Save").click();
    });
    expect(button(form, "Saving…").disabled).toBe(true);
    expect(button(form, "Cancel").disabled).toBe(true);
    await act(async () => {
      resolveSave({ ...source, federationCatalog: "finance" });
    });
    await flush();
    expect(container!.querySelector("[role='dialog']")).toBeNull();
  });

  it("shows registration state per schema, not per table name", async () => {
    await renderPanel();
    const people = Array.from(container!.querySelectorAll(".external-table-browser ul button"));
    expect(people).toHaveLength(2);
    expect(people[0].textContent).toContain("Registered");
    expect(people[1].textContent).toContain("TABLE");

    await act(async () => {
      people[0].click();
    });
    await flush();
    expect(button(container!, "Remove from Query Builder")).toBeTruthy();

    await act(async () => {
      people[1].click();
    });
    await flush();
    expect(button(container!, "Register for Query Builder")).toBeTruthy();
    expect(people[1].classList.contains("active")).toBe(true);
    expect(people[0].classList.contains("active")).toBe(false);
  });
});
