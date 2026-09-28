import assert from "node:assert/strict";
import fs from "node:fs";
import { describe, it } from "node:test";
import {
  evaluateAutomatedGates,
  evaluateProductionBuildResult,
  assertCheckpointFingerprintMatches,
  materializeCheckpointFromBrowserReport,
  computeApplicationSourceFingerprint,
} from "./acceptance_runner.mjs";

function cmd(overrides) {
  return {
    name: "test",
    command: "npm run build",
    exitCode: 0,
    durationMs: 1,
    timedOut: false,
    signal: null,
    stdoutTail: "",
    stderrTail: "",
    combinedTail: "",
    ...overrides,
  };
}

describe("evaluateProductionBuildResult", () => {
  it("PASS on successful build markers and exit 0", () => {
    const r = cmd({
      combinedTail: "✓ Compiled successfully in 1s\nRoute (app)\n○ /",
      stdoutTail: "✓ Compiled successfully in 1s\nRoute (app)",
    });
    assert.equal(evaluateProductionBuildResult(r).pass, true);
  });

  it("FAIL on non-zero exit", () => {
    const r = cmd({ exitCode: 1, combinedTail: "Failed to compile" });
    assert.equal(evaluateProductionBuildResult(r).pass, false);
  });

  it("FAIL on SIGTERM/SIGKILL", () => {
    assert.equal(evaluateProductionBuildResult(cmd({ signal: "SIGKILL", exitCode: null })).pass, false);
    assert.equal(evaluateProductionBuildResult(cmd({ signal: "SIGTERM", exitCode: null })).pass, false);
  });

  it("FAIL on timeout", () => {
    assert.equal(evaluateProductionBuildResult(cmd({ timedOut: true, exitCode: 124 })).pass, false);
  });

  it("FAIL when output is only > next build", () => {
    const r = cmd({ combinedTail: "> next build" });
    assert.equal(evaluateProductionBuildResult(r).pass, false);
  });
});

describe("evaluateAutomatedGates", () => {
  const ok = {
    frontendTests: cmd({
      combinedTail: "Tests  78 passed (78)",
      stdoutTail: "Test Files  22 passed (22)\nTests  78 passed (78)",
    }),
    frontendLint: cmd({
      combinedTail: "✖ 17 problems (0 errors, 17 warnings)",
      stdoutTail: "✖ 17 problems (0 errors, 17 warnings)",
    }),
    backendTests: cmd({
      combinedTail: "[INFO] Tests run: 528, Failures: 0, Errors: 0, Skipped: 0",
      stdoutTail: "[INFO] Tests run: 528, Failures: 0, Errors: 0, Skipped: 0",
    }),
    productionBuild: cmd({
      combinedTail: "✓ Compiled successfully\nRoute (app)\n○ /login",
      stdoutTail: "✓ Compiled successfully\nRoute (app)",
    }),
    diffCheck: cmd({ exitCode: 0, combinedTail: "" }),
  };

  it("PASS when all gates succeed", () => {
    const ev = evaluateAutomatedGates(ok, { nextEnvClean: true });
    assert.equal(ev.pass, true);
    assert.equal(ev.lintSummary.errors, 0);
    assert.equal(ev.lintSummary.warnings, 17);
  });

  it("PASS frontendTests when summary is in stdout despite stderr tail noise", () => {
    const noisy = {
      ...ok,
      frontendTests: cmd({
        exitCode: 0,
        stdoutTail: "Test Files  22 passed (22)\nTests  78 passed (78)",
        stderrTail: "The current testing environment is not configured to support act(...)",
        combinedTail: "The current testing environment is not configured to support act(...)",
      }),
    };
    assert.equal(evaluateAutomatedGates(noisy, { nextEnvClean: true }).pass, true);
  });

  it("FAIL when build truncated", () => {
    const ev = evaluateAutomatedGates(
      { ...ok, productionBuild: cmd({ combinedTail: "> next build" }) },
      { nextEnvClean: true },
    );
    assert.equal(ev.pass, false);
    assert.ok(ev.failures.some((f) => f.startsWith("productionBuild:")));
  });
});

describe("checkpoint fingerprint", () => {
  it("mismatch prevents resume", () => {
    const checkpoint = {
      version: 1,
      applicationSourceFingerprint: "deadbeef",
    };
    const root = "/Users/sameerkhanna/Documents/Projects/analytics-advisor";
    const { match } = assertCheckpointFingerprintMatches(checkpoint, root);
    assert.equal(match, false);
  });

  it("materialize checkpoint preserves report reference", () => {
    const root = "/Users/sameerkhanna/Documents/Projects/analytics-advisor";
    const reportPath = "/tmp/aa-acceptance-stdout.json";
    if (!fs.existsSync(reportPath)) {
      return;
    }
    const cp = materializeCheckpointFromBrowserReport(reportPath, root, {
      browserEvidenceProducedAt: "2026-09-27T10:22:02.532Z",
    });
    assert.equal(cp.browserEvidenceProducedAt, "2026-09-27T10:22:02.532Z");
    assert.ok(cp.report.sections.savedQuery === "PASS" || cp.report.sections?.savedQuery);
    assert.ok(cp.applicationSourceFingerprint.length === 64);
  });
});
