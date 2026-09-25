package gov.rajasthan.smart.srse.config;

import gov.rajasthan.smart.srse.compiler.FieldResolver;
import gov.rajasthan.smart.srse.identity.AdminAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain validation and configuration errors to HTTP status codes for the
 * surviving API surface (Analysis, lakehouse, connections).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FieldResolver.UnknownFieldException.class)
    public ResponseEntity<String> badRequest(FieldResolver.UnknownFieldException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    /**
     * Malformed predicate values (e.g. BETWEEN with != 2 bounds, a
     * FUZZY_MATCH threshold outside 0..100) — compiler-level validation
     * errors, not server bugs. Previously unmapped and fell through to a
     * generic 500; found via live testing of the FUZZY_MATCH threshold guard.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    /**
     * The environment is not finished being configured — the field exists but
     * nobody has bound it to a real column. 503 rather than 400: the officer's
     * request was fine, the deployment is not ready to answer it, and retrying
     * after an admin fixes the mapping is exactly the right thing to do.
     */
    @ExceptionHandler(FieldResolver.UnconfiguredFieldException.class)
    public ResponseEntity<String> notConfigured(FieldResolver.UnconfiguredFieldException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(AdminAccessDeniedException.class)
    public ResponseEntity<String> forbidden(AdminAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
