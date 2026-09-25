package gov.rajasthan.smart.srse.audit;

/** Settled A7 event types (§7.3). Metadata browsing is intentionally absent. */
public enum AuditActionType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    ACCOUNT_LOCKED,
    MFA_CHALLENGE_ISSUED,
    MFA_VERIFIED,
    MFA_FAILED,
    QUERY_EXECUTED,
    QUERY_PREVIEWED,
    QUERY_REFUSED,
    EXPORT,
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,
    PASSWORD_RESET,
    ROLE_GRANTED,
    SCOPE_GRANTED,
    CONTACT_CHANGED,
    SCOPE_BINDING_CHANGED,
    SHARED_REFERENCE_SET,
    TABLE_REGISTERED,
    TABLE_UNREGISTERED,
    /** Bulk rename of a registry display label (source system or table group). */
    REGISTRY_LABEL_RENAMED,
    SAVED_QUERY_CREATED,
    SAVED_QUERY_UPDATED,
    SAVED_QUERY_DELETED,
    SAVED_QUERY_SHARED
}
