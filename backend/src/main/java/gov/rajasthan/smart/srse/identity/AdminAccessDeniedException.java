package gov.rajasthan.smart.srse.identity;

/** Scoped-admin or self-service guard — maps to HTTP 403. */
public class AdminAccessDeniedException extends RuntimeException {

    public AdminAccessDeniedException(String message) {
        super(message);
    }
}
