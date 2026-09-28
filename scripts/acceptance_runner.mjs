/**
 * Testable automated-gate runner + browser checkpoint helpers for aa_final_acceptance.mjs.
 */
import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export const APP_FINGERPRINT_PATHS = [
  "frontend/src",
  "frontend/package.json",
  "frontend/package-lock.json",
  "backend/src",
  "backend/pom.xml",
];

export const DEFAULT_COMMAND_TIMEOUTS_MS = {
  frontendTests: 5 * 60 * 1000,
  frontendLint: 10 * 60 * 1000,
  backendTests: 15 * 60 * 1000,
  productionBuild: 20 * 60 * 1000,
  diffCheck: 60 * 1000,
  nextEnvDiff: 30 * 1000,
};

const TAIL_CHARS = 4000;

export function sha256Hex(text) {
  return crypto.createHash("sha256").update(text).digest("hex");
}

/** Application source fingerprint (excludes acceptance scripts). */
export function computeApplicationSourceFingerprint(root) {
  const commit = spawnGit(root, ["rev-parse", "HEAD"]).stdout.trim();
  const scopedStatus = spawnGit(root, ["status", "--porcelain", "--", ...APP_FINGERPRINT_PATHS]).stdout.trim();
  const tracked = spawnGit(root, ["ls-files", "--", ...APP_FINGERPRINT_PATHS]).stdout
    .trim()
    .split("\n")
    .filter(Boolean);
  const blobLines = tracked.map((f) => {
    const r = spawnSync("git", ["-C", root, "hash-object", "--", f], {
      encoding: "utf8",
      maxBuffer: 8 * 1024 * 1024,
    });
    const h = (r.stdout ?? "").trim();
    return `${f}:${h || "error"}`;
  });
  const untracked = spawnGit(root, ["ls-files", "-o", "--exclude-standard", "--", ...APP_FINGERPRINT_PATHS]).stdout
    .trim()
    .split("\n")
    .filter(Boolean);
  const untrackedLines = untracked.map((f) => {
    const abs = path.join(root, f);
    const buf = fs.readFileSync(abs);
    return `${f}:${sha256Hex(buf)}`;
  });
  const payload = [commit, scopedStatus, ...blobLines, ...untrackedLines].join("\n");
  return {
    fingerprint: sha256Hex(payload),
    commit,
    scopedStatus,
    trackedFileCount: tracked.length,
    untrackedFileCount: untracked.length,
    scope: APP_FINGERPRINT_PATHS,
  };
}

function spawnGit(root, args) {
  const r = spawnSync("git", ["-C", root, ...args], { encoding: "utf8", maxBuffer: 32 * 1024 * 1024 });
  if (r.error) throw r.error;
  if (r.status !== 0) {
    throw new Error(`git ${args.join(" ")} failed: ${r.stderr || r.stdout}`);
  }
  return r;
}

export function tailText(text, max = TAIL_CHARS) {
  if (!text) return "";
  return text.length <= max ? text : text.slice(-max);
}

/**
 * @param {object} opts
 * @param {string} opts.cwd
 * @param {string} opts.command
 * @param {string[]} [opts.args]
 * @param {Record<string,string>} [opts.env]
 * @param {number} opts.timeoutMs
 * @param {string} opts.name
 */
export function runAutomatedCommand({ cwd, command, args = [], env = process.env, timeoutMs, name }) {
  const started = Date.now();
  const result = spawnSync(command, args, {
    cwd,
    env,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    timeout: timeoutMs,
  });
  const durationMs = Date.now() - started;
  const timedOut = result.error?.code === "ETIMEDOUT";
  const signal = result.signal ?? null;
  let exitCode = result.status;
  if (timedOut) exitCode = exitCode ?? 124;
  if (signal) exitCode = exitCode ?? 128;

  return {
    name,
    command: [command, ...args].join(" "),
    exitCode,
    durationMs,
    timedOut,
    signal,
    stdoutTail: tailText(result.stdout ?? ""),
    stderrTail: tailText(result.stderr ?? ""),
    combinedTail: tailText(`${result.stdout ?? ""}${result.stderr ?? ""}`),
  };
}

export function parseEslintSummary(combinedOutput) {
  const text = combinedOutput ?? "";
  let errors = null;
  let warnings = null;
  const paren = text.match(/✖\s*(\d+)\s+problems?\s*\((\d+)\s+errors?,\s*(\d+)\s+warnings?\)/i);
  if (paren) {
    errors = Number(paren[2]);
    warnings = Number(paren[3]);
  }
  const loose = text.match(/(\d+)\s+errors?\s+and\s+(\d+)\s+warnings?/i);
  if (errors == null && loose) {
    errors = Number(loose[1]);
    warnings = Number(loose[2]);
  }
  if (errors == null && /no problems found|0 errors/i.test(text)) {
    errors = 0;
    warnings = warnings ?? 0;
  }
  return { errors, warnings };
}

const BUILD_SUCCESS_MARKERS = [
  "Compiled successfully",
  "✓ Compiled",
  "Generating static pages",
  "Route (app)",
  "Finalizing page optimization",
];

/** @param {ReturnType<typeof runAutomatedCommand>} result */
export function evaluateProductionBuildResult(result) {
  if (result.timedOut) {
    return { pass: false, reason: "build timed out" };
  }
  if (result.signal) {
    return { pass: false, reason: `build terminated by signal ${result.signal}` };
  }
  if (result.exitCode !== 0) {
    return { pass: false, reason: `build exit code ${result.exitCode}` };
  }
  const out = `${result.stdoutTail}${result.stderrTail}`.trim();
  if (!out || /^>\s*next build\s*$/i.test(out)) {
    return { pass: false, reason: "build output truncated or only npm script banner" };
  }
  const hasMarker = BUILD_SUCCESS_MARKERS.some((m) => out.includes(m));
  if (!hasMarker) {
    return { pass: false, reason: "missing successful Next.js build completion markers" };
  }
  return { pass: true, reason: "ok" };
}

/** @param {Record<string, ReturnType<typeof runAutomatedCommand>>} commands */
export function evaluateAutomatedGates(commands, repoHygiene = {}) {
  const failures = [];

  const ft = commands.frontendTests;
  const ftOut = `${ft?.stdoutTail ?? ""}${ft?.stderrTail ?? ""}`;
  const frontendTestsOk =
    ft &&
    !ft.timedOut &&
    !ft.signal &&
    ft.exitCode === 0 &&
    (/(\d+)\s+passed/i.test(ftOut) || /Test Files\s+.*passed/i.test(ftOut));
  if (!frontendTestsOk) {
    failures.push("frontendTests");
  }

  const lint = commands.frontendLint;
  const lintSummary = parseEslintSummary(lint?.combinedTail ?? "");
  if (!lint || lint.timedOut || lint.signal || lint.exitCode !== 0) {
    failures.push("frontendLint");
  } else if (lintSummary.errors == null || lintSummary.errors > 0) {
    failures.push("frontendLintErrors");
  }

  const be = commands.backendTests;
  const beOut = `${be?.stdoutTail ?? ""}${be?.stderrTail ?? ""}`;
  if (!be || be.timedOut || be.signal || be.exitCode !== 0 || !/Failures:\s*0/.test(beOut)) {
    failures.push("backendTests");
  }

  const buildEval = evaluateProductionBuildResult(commands.productionBuild);
  if (!buildEval.pass) {
    failures.push(`productionBuild:${buildEval.reason}`);
  }

  const dc = commands.diffCheck;
  if (!dc || dc.timedOut || dc.signal || dc.exitCode !== 0) {
    failures.push("diffCheck");
  }

  if (!repoHygiene.nextEnvClean) {
    failures.push("nextEnvDirty");
  }

  return {
    pass: failures.length === 0,
    failures,
    lintSummary,
    buildEval,
  };
}

export const BROWSER_CHECKPOINT_FILENAME = "acceptance-browser-checkpoint.json";

export function browserSectionsFromReport(report) {
  return {
    sections: report.sections,
    matchLedger: report.matchLedger,
    pipelineChecks: report.pipelineChecks,
    savedQuery: report.savedQuery,
    dualGroupByDiagnosis: report.dualGroupByDiagnosis,
    optionalFilters: report.optionalFilters,
    exports: report.exports,
    auth: report.auth,
    screenshots: report.screenshots,
    visualError: report.visualError,
    runtime: report.runtime,
    git: report.git,
    limitations: report.limitations,
  };
}

/**
 * Build checkpoint wrapper from a persisted full browser report (does not re-run browser).
 */
export function materializeCheckpointFromBrowserReport(reportPath, root, meta = {}) {
  const report = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  const stat = fs.statSync(reportPath);
  const fp = computeApplicationSourceFingerprint(root);
  return {
    version: 1,
    browserEvidenceProducedAt:
      meta.browserEvidenceProducedAt ?? "2026-09-27T10:22:02.532Z",
    browserReportWrittenAt: meta.browserReportWrittenAt ?? stat.mtime.toISOString(),
    browserReportSourcePath: path.resolve(reportPath),
    applicationSourceFingerprint: fp.fingerprint,
    fingerprintDetails: fp,
    fingerprintCapturedAt: new Date().toISOString(),
    browserReportGit: {
      commit: report.git?.commit ?? null,
      finalStatus: report.git?.finalStatus ?? report.git?.dirty ?? null,
    },
    note:
      "Checkpoint materialized from persisted browser report; timestamps preserved from original run unless overridden in meta.",
    report,
  };
}

export function writeBrowserCheckpoint(checkpointPath, report, root, browserEvidenceProducedAt) {
  const fp = computeApplicationSourceFingerprint(root);
  const payload = {
    version: 1,
    browserEvidenceProducedAt,
    browserReportWrittenAt: new Date().toISOString(),
    browserReportSourcePath: null,
    applicationSourceFingerprint: fp.fingerprint,
    fingerprintDetails: fp,
    fingerprintCapturedAt: new Date().toISOString(),
    note: "Live browser checkpoint written immediately before automated gates.",
    report: browserSectionsFromReport(report),
  };
  fs.mkdirSync(path.dirname(checkpointPath), { recursive: true });
  fs.writeFileSync(checkpointPath, JSON.stringify(payload, null, 2));
  return payload;
}

export function loadBrowserCheckpoint(checkpointPath) {
  const raw = JSON.parse(fs.readFileSync(checkpointPath, "utf8"));
  if (raw.version !== 1 || !raw.applicationSourceFingerprint) {
    throw new Error(`Unsupported checkpoint format: ${checkpointPath}`);
  }
  return raw;
}

export function assertCheckpointFingerprintMatches(checkpoint, root) {
  const current = computeApplicationSourceFingerprint(root);
  const currentCommit = spawnGit(root, ["rev-parse", "HEAD"]).stdout.trim();
  const commitMatch =
    !checkpoint.browserReportGit?.commit || checkpoint.browserReportGit.commit === currentCommit;
  const fingerprintMatch = current.fingerprint === checkpoint.applicationSourceFingerprint;
  const match = commitMatch && fingerprintMatch;
  return {
    match,
    commitMatch,
    fingerprintMatch,
    current,
    currentCommit,
    checkpointFingerprint: checkpoint.applicationSourceFingerprint,
    checkpointCommit: checkpoint.browserReportGit?.commit ?? null,
  };
}

export function runAutomatedSuite(root, apiBase, timeouts = DEFAULT_COMMAND_TIMEOUTS_MS) {
  const commands = {
    frontendTests: runAutomatedCommand({
      name: "frontendTests",
      cwd: path.join(root, "frontend"),
      command: "npm",
      args: ["test"],
      timeoutMs: timeouts.frontendTests,
    }),
    frontendLint: runAutomatedCommand({
      name: "frontendLint",
      cwd: path.join(root, "frontend"),
      command: "npm",
      args: ["run", "lint"],
      timeoutMs: timeouts.frontendLint,
    }),
    backendTests: runAutomatedCommand({
      name: "backendTests",
      cwd: path.join(root, "backend"),
      command: "bash",
      args: ["-lc", "JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test"],
      timeoutMs: timeouts.backendTests,
    }),
    productionBuild: runAutomatedCommand({
      name: "productionBuild",
      cwd: path.join(root, "frontend"),
      command: "npm",
      args: ["run", "build"],
      env: {
        ...process.env,
        NEXT_PUBLIC_AUTH_MODE: "local",
        NEXT_PUBLIC_API_BASE: apiBase,
      },
      timeoutMs: timeouts.productionBuild,
    }),
    diffCheck: runAutomatedCommand({
      name: "diffCheck",
      cwd: root,
      command: "git",
      args: ["diff", "--check"],
      timeoutMs: timeouts.diffCheck,
    }),
  };

  const nextEnvDiff = spawnSync("git", ["-C", root, "diff", "--", "frontend/next-env.d.ts"], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  const nextEnvDiffText = (nextEnvDiff.stdout ?? "").trim();
  const repoHygiene = {
    nextEnvDiff: nextEnvDiffText,
    nextEnvClean: nextEnvDiffText === "",
    nextEnvCheckExitCode: nextEnvDiff.status ?? 0,
  };

  const evaluation = evaluateAutomatedGates(commands, repoHygiene);

  return { commands, repoHygiene, evaluation };
}
