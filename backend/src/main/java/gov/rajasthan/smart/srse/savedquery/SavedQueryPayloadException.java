package gov.rajasthan.smart.srse.savedquery;

public class SavedQueryPayloadException extends RuntimeException {

    public SavedQueryPayloadException(String message) {
        super(message);
    }

    public SavedQueryPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
