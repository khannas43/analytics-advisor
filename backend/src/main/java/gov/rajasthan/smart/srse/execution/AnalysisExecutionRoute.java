package gov.rajasthan.smart.srse.execution;

/** Where one validated analysis statement will execute. */
public record AnalysisExecutionRoute(Mode mode, Long externalDataSourceId) {
    public enum Mode { PRESTO, DIRECT_JDBC, FEDERATED_PRESTO }

    public static AnalysisExecutionRoute presto() {
        return new AnalysisExecutionRoute(Mode.PRESTO, null);
    }
}
