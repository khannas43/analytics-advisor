package gov.rajasthan.smart.srse.monitoring;

/**
 * Lets a stream that reports engine failures IN-BAND (an {@code error} line, then a normal return)
 * tell the monitor the query did not succeed. Without it the monitor, which only sees exceptions,
 * would record a query that hit the engine timeout as a SUCCESS.
 */
public final class QueryOutcomeProbe {

    private volatile Throwable failure;

    /** Safe to call from the streaming thread; first failure wins. */
    public void failed(Throwable cause) {
        if (failure == null) {
            failure = cause == null ? new IllegalStateException("query failed") : cause;
        }
    }

    public boolean hasFailed() {
        return failure != null;
    }
}
