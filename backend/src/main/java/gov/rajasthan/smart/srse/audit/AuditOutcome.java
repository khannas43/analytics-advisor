package gov.rajasthan.smart.srse.audit;

public enum AuditOutcome {
    SUCCESS,
    FAILURE,
    REFUSED,
    /** Client disconnect or aborted stream — QUERY_FINISHED only. */
    CANCELLED
}
