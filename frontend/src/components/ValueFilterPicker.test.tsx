// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { createRoot, type Root } from "react-dom/client";
import { act, useState } from "react";
import ValueFilterPicker, { predicateWireSignature } from "@/components/ValueFilterPicker";
import { inPredicateHydrationKey } from "@/lib/valuePickerSemantics";
import type { PredicateSpecWire, TableRef } from "@/lib/analysisApi";
import { fetchColumnDistinctValues } from "@/lib/analysisApi";

vi.mock("@/lib/analysisApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/analysisApi")>();
  return {
    ...actual,
    fetchColumnDistinctValues: vi.fn().mockResolvedValue({
      values: ["Jaipur", "Udaipur"],
      truncated: false,
    }),
  };
});

const table: TableRef = { catalog: "iceberg", schema: "srse", table: "beneficiary" };

function savedInSpec(): PredicateSpecWire {
  return {
    root: {
      type: "PREDICATE",
      column: { table: { ...table }, column: "district" },
      operator: "IN",
      value: ["Jaipur"],
    },
  };
}

function ReopenHarness({
  onChange,
  hydratedSpec,
}: {
  onChange: (spec: PredicateSpecWire | null) => void;
  hydratedSpec: PredicateSpecWire | null;
}) {
  const [tick, setTick] = useState(0);
  const [spec, setSpec] = useState(hydratedSpec);
  return (
    <>
      <button type="button" id="hydrate" onClick={() => setSpec(savedInSpec())}>
        hydrate
      </button>
      <button type="button" id="rerender" onClick={() => setTick((t) => t + 1)}>
        rerender {tick}
      </button>
      <ValueFilterPicker
        table={{ catalog: table.catalog, schema: table.schema, table: table.table }}
        column={spec ? "district" : ""}
        hydratedSpec={spec ? { ...spec, root: { ...spec.root!, value: [...(spec.root!.value as string[])] } } : null}
        onChange={onChange}
      />
    </>
  );
}

describe("ValueFilterPicker update loop", () => {
  afterEach(() => {
    document.body.innerHTML = "";
    vi.mocked(fetchColumnDistinctValues).mockClear();
  });

  it("saved-query reopen: hydrate Jaipur without onChange loop or max-depth", async () => {
    const onChange = vi.fn();
    const host = document.createElement("div");
    document.body.appendChild(host);
    const root: Root = createRoot(host);
    const errors: string[] = [];
    const origError = console.error;
    console.error = (...args: unknown[]) => {
      errors.push(args.map(String).join(" "));
      origError(...args);
    };

    await act(async () => {
      root.render(<ReopenHarness onChange={onChange} hydratedSpec={null} />);
    });

    await act(async () => {
      document.getElementById("hydrate")?.click();
      await new Promise((r) => setTimeout(r, 20));
    });

    const callsAfterHydrate = onChange.mock.calls.length;

    for (let i = 0; i < 10; i++) {
      await act(async () => {
        document.getElementById("rerender")?.click();
        await new Promise((r) => setTimeout(r, 0));
      });
    }

    const jaipurChecked = host.querySelector<HTMLInputElement>('input[aria-label="Jaipur"]');
    expect(jaipurChecked?.checked).toBe(true);
    expect(onChange.mock.calls.length).toBeLessThanOrEqual(callsAfterHydrate + 1);
    expect(errors.some((e) => /Maximum update depth exceeded/i.test(e))).toBe(false);

    console.error = origError;
    await act(async () => root.unmount());
    host.remove();
  });

  it("stable hydration keys for equivalent IN specs", () => {
    const a: PredicateSpecWire = {
      root: {
        type: "PREDICATE",
        column: { table, column: "district" },
        operator: "IN",
        value: ["Jaipur", "Udaipur"],
      },
    };
    const b: PredicateSpecWire = {
      root: {
        type: "PREDICATE",
        column: { table, column: "district" },
        operator: "IN",
        value: ["Udaipur", "Jaipur"],
      },
    };
    expect(inPredicateHydrationKey(a)).toBe(inPredicateHydrationKey(b));
    expect(predicateWireSignature(null)).toBe("__null__");
  });
});
