/**
 * Display labels for deployment environments. Wire values stay {@code SYNTHETIC} /
 * {@code LIVE} ({@code DATA_MODE}).
 */

const ENVIRONMENT_LABELS = {
  SYNTHETIC: "Development",
  LIVE: "Production (Live)",
} as const;

/** Label for the connections panel (this running deployment). */
export function runningEnvironmentLabel(
  environmentLabel: string | undefined,
  dataMode: string,
): string {
  const trimmed = environmentLabel?.trim();
  if (trimmed) {
    return trimmed;
  }
  const mode = dataMode.trim().toUpperCase();
  if (mode === "LIVE") {
    return ENVIRONMENT_LABELS.LIVE;
  }
  if (mode === "SYNTHETIC") {
    return ENVIRONMENT_LABELS.SYNTHETIC;
  }
  return dataMode;
}
