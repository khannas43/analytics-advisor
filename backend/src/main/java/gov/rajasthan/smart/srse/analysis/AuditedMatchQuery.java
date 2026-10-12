package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.monitoring.QueryExecutionTicket;

public record AuditedMatchQuery(RecordMatchService.MatchQuery query, QueryExecutionTicket ticket) {
}
