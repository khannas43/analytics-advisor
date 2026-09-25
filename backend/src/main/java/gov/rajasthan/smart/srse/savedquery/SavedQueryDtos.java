package gov.rajasthan.smart.srse.savedquery;

import gov.rajasthan.smart.srse.analysis.RecordMatchRequest;

import java.time.Instant;

public final class SavedQueryDtos {

    private SavedQueryDtos() {
    }

    public record SavedQuerySummary(
            long id,
            long ownerUserId,
            String name,
            String description,
            Instant updatedAt,
            boolean ownedByMe) {
    }

    public record SavedQueryDetail(
            long id,
            long ownerUserId,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            RecordMatchRequest request) {
    }

    public record CreateSavedQueryRequest(
            String name,
            String description,
            RecordMatchRequest request) {
    }

    public record UpdateSavedQueryRequest(
            String name,
            String description,
            RecordMatchRequest request) {
    }

    public record ShareSavedQueryRequest(long granteeUserId) {
    }
}
