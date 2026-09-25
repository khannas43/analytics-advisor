package gov.rajasthan.smart.srse.audit.admin;

import java.util.List;

public record AuditPageResponse(
        List<AuditEventView> entries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int retentionDays,
        String retentionNote) {
}
