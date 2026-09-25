package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.audit.admin.AuditEventView;
import gov.rajasthan.smart.srse.audit.admin.AuditPageResponse;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Export must run through the same scoped criteria as the paged viewer. */
@ExtendWith(MockitoExtension.class)
class AuditCsvMatchesViewerTest {

    @Mock
    private AuditEventQueryDao queryDao;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private AdminAuthorizationService authorization;

    private AuditViewerService service;
    private AppUser reader;
    private AuditEvent sample;

    @BeforeEach
    void setUp() {
        service = new AuditViewerService(queryDao, userRepository, authorization, new AuditProperties(List.of(), 90));
        reader = new AppUser("reader", "hash");
        sample = new AuditEvent(
                Instant.parse("2026-01-01T00:00:00Z"),
                5L,
                null,
                AuditActionType.QUERY_EXECUTED,
                AuditOutcome.SUCCESS,
                "d",
                "127.0.0.1",
                "t",
                "SELECT ?",
                "BYPASS");
        try {
            var idField = AuditEvent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(sample, 99L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        when(authorization.isSuperAdmin(reader)).thenReturn(true);
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void exportUsesSameCriteriaAsPagedSearch() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        when(queryDao.findPage(any(), any())).thenReturn(new PageImpl<>(
                List.of(sample), PageRequest.of(0, 50), 1));
        when(queryDao.findAll(any(), eq(AuditViewerService.MAX_EXPORT_ROWS))).thenReturn(List.of(sample));

        AuditPageResponse page = service.search(reader, from, null, null, null, null, 0, 50);
        List<AuditEventView> exported = service.searchForExport(reader, from, null, null, null, null);

        ArgumentCaptor<AuditSearchCriteria> pageCriteria = ArgumentCaptor.forClass(AuditSearchCriteria.class);
        verify(queryDao).findPage(pageCriteria.capture(), any());
        ArgumentCaptor<AuditSearchCriteria> exportCriteria = ArgumentCaptor.forClass(AuditSearchCriteria.class);
        verify(queryDao).findAll(exportCriteria.capture(), eq(AuditViewerService.MAX_EXPORT_ROWS));

        assertEquals(pageCriteria.getValue(), exportCriteria.getValue());
        assertEquals(1, page.entries().size());
        assertEquals(page.entries().get(0).queryShape(), exported.get(0).queryShape());
    }
}
