package gov.rajasthan.smart.srse.lakehouse;

/**
 * Optional display labels on {@link RegisteredTable} (source system, table group).
 * Same contract as {@link LakehouseLayers}: filter-only, never in addresses or SQL.
 */
public final class RegistryDisplayTags {

    /** Wire sentinel for {@code label IS NULL} rows in officer/admin filters. */
    public static final String UNTAGGED = "UNTAGGED";

    private RegistryDisplayTags() {
    }

    /** Blank → {@code null} stored tag; rejects reserved {@link #UNTAGGED}. */
    public static String normaliseOptional(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        if (UNTAGGED.equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException(
                    UNTAGGED + " is reserved for untagged registrations — choose another label");
        }
        return trimmed;
    }

    public static String normaliseForImport(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return label.trim();
    }

    public static boolean isUntaggedFilter(String param) {
        return param != null && UNTAGGED.equalsIgnoreCase(param.trim());
    }

    /** Blank HTTP param → unfiltered ({@code null}); {@link #UNTAGGED} → sentinel. */
    public static String parseFilterParam(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        if (isUntaggedFilter(param)) {
            return UNTAGGED;
        }
        return param.trim();
    }
}
