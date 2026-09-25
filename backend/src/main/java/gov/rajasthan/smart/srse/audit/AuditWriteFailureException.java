package gov.rajasthan.smart.srse.audit;

public class AuditWriteFailureException extends RuntimeException {

    public AuditWriteFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
