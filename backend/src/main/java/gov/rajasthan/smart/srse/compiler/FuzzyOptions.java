package gov.rajasthan.smart.srse.compiler;

/**
 * Per-group fuzzy normalisation (§5.3). Defaults match pre-AA-16 SQL ({@code lower()} only).
 */
public record FuzzyOptions(Boolean caseSensitive, Boolean ignoreSpaces) {

    public static final FuzzyOptions DEFAULTS = new FuzzyOptions(false, false);

    public static FuzzyOptions resolve(FuzzyOptions options) {
        if (options == null) {
            return DEFAULTS;
        }
        boolean cs = options.caseSensitive != null && options.caseSensitive;
        boolean is = options.ignoreSpaces != null && options.ignoreSpaces;
        return new FuzzyOptions(cs, is);
    }
}
