package gov.rajasthan.smart.srse.monitoring;

/**
 * Correlates an in-flight analytical execution with its QUERY_EXECUTED audit row
 * ({@code queryId=} in {@code detail}) and the eventual QUERY_FINISHED row.
 */
public record QueryExecutionTicket(
        String queryId,
        ActiveQueryKind kind,
        String qualifiedTables,
        Long actorUserId,
        String actorUsername,
        String scopeSummary,
        boolean writeQueryFinishedAudit,
        QueryOutcomeProbe probe) {

    public QueryExecutionTicket {
        probe = probe == null ? new QueryOutcomeProbe() : probe;
    }

    public QueryExecutionTicket(
            String queryId,
            ActiveQueryKind kind,
            String qualifiedTables,
            Long actorUserId,
            String actorUsername,
            String scopeSummary,
            boolean writeQueryFinishedAudit) {
        this(queryId, kind, qualifiedTables, actorUserId, actorUsername, scopeSummary,
                writeQueryFinishedAudit, new QueryOutcomeProbe());
    }

    public QueryExecutionTicket withKind(ActiveQueryKind newKind) {
        return new QueryExecutionTicket(
                queryId, newKind, qualifiedTables, actorUserId, actorUsername, scopeSummary,
                writeQueryFinishedAudit, probe);
    }
}
