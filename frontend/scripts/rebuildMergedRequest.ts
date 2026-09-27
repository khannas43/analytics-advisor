import { readFileSync } from "node:fs";
import type { RecordMatchRequest } from "../src/lib/analysisApi";
import { buildMergedRequest } from "../src/lib/queryBuilderRequestBuild";
import {
  dualModeFromRequest,
  hydrateExtractConfigFromRequest,
  hydrateReportConfigFromRequest,
} from "../src/lib/savedQueryHydrate";

const req = JSON.parse(readFileSync(0, "utf8")) as RecordMatchRequest;
const dual = dualModeFromRequest(req);
const merged = buildMergedRequest(
  dual,
  hydrateExtractConfigFromRequest(req),
  hydrateReportConfigFromRequest(req),
);
process.stdout.write(JSON.stringify(merged));
