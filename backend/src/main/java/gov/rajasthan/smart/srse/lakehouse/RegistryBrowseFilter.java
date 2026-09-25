package gov.rajasthan.smart.srse.lakehouse;

/**
 * Registry-only filters for the officer cascade and Database Overview (§3.1.3).
 * Never part of {@code catalog.schema.table} or request match payloads.
 */
public record RegistryBrowseFilter(String layer, String sourceSystem, String tableGroup) {

    public boolean hasAnyTagFilter() {
        return layer != null || sourceSystem != null || tableGroup != null;
    }

    public static RegistryBrowseFilter none() {
        return new RegistryBrowseFilter(null, null, null);
    }

    public static RegistryBrowseFilter parse(String layer, String sourceSystem, String tableGroup) {
        return new RegistryBrowseFilter(
                layer == null || layer.isBlank() ? null : parseLayer(layer),
                RegistryDisplayTags.parseFilterParam(sourceSystem),
                RegistryDisplayTags.parseFilterParam(tableGroup));
    }

    private static String parseLayer(String layer) {
        return LakehouseLayers.parseFilterParam(layer);
    }
}
