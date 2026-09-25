package gov.rajasthan.smart.srse.savedquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.analysis.RecordMatchRequest;
import org.springframework.stereotype.Component;

/**
 * Versioned saved-query envelope (AA-18). TEXT column in DB — parsed here, not JSONB.
 */
@Component
public class SavedQueryPayloadCodec {

    public static final int CURRENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public SavedQueryPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(RecordMatchRequest request) {
        Envelope envelope = new Envelope(CURRENT_VERSION, "record_match", request);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new SavedQueryPayloadException("Could not serialise saved query", e);
        }
    }

    public RecordMatchRequest decode(String payloadText) {
        try {
            JsonNode root = objectMapper.readTree(payloadText);
            int version = root.path("version").asInt(-1);
            if (version != CURRENT_VERSION) {
                throw new SavedQueryPayloadException(
                        "Saved query payload version " + version + " is not supported (expected "
                                + CURRENT_VERSION + "). Re-save the query or contact an administrator.");
            }
            String kind = root.path("kind").asText("");
            if (!"record_match".equals(kind)) {
                throw new SavedQueryPayloadException("Unsupported saved query kind: " + kind);
            }
            JsonNode requestNode = root.get("request");
            if (requestNode == null || requestNode.isNull()) {
                throw new SavedQueryPayloadException("Saved query payload is missing its request body");
            }
            return objectMapper.treeToValue(requestNode, RecordMatchRequest.class);
        } catch (SavedQueryPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new SavedQueryPayloadException("Saved query payload could not be read", e);
        }
    }

    private record Envelope(int version, String kind, RecordMatchRequest request) {
    }
}
