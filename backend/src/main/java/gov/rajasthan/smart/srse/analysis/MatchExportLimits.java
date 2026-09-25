package gov.rajasthan.smart.srse.analysis;

/** AA-18 — Excel sheet row cap (OOXML spec); CSV/JSON/XML have no product row cap here. */
public final class MatchExportLimits {

    public static final int EXCEL_MAX_ROWS_PER_SHEET = 1_048_576;

    private MatchExportLimits() {
    }
}
