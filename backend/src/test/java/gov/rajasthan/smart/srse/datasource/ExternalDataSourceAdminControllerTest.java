package gov.rajasthan.smart.srse.datasource;

import gov.rajasthan.smart.srse.identity.AdminAccessDeniedException;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalDataSourceAdminControllerTest {

    @Mock
    private ExternalDataSourceService dataSourceService;
    @Mock
    private ExternalDataSourceMetadataService metadataService;
    @Mock
    private LakehouseRegistryService registry;
    @InjectMocks
    private ExternalDataSourceAdminController controller;

    @Test
    void unregisterRequiresSuperAdminBeforeItDeletes() {
        doThrow(new AdminAccessDeniedException("SuperAdmin only"))
                .when(dataSourceService).requireForBrowse(3L);

        assertThrows(AdminAccessDeniedException.class, () -> controller.unregister(3L, 9L));

        verify(registry, never()).unregister(9L);
        verify(registry, never()).listExternalRegistrations(3L);
    }

    @Test
    void unregisterRejectsARegistrationFromAnotherSource() {
        RegisteredTable other = new RegisteredTable(4L, "jdbc_3", "public", "other", null, false, "Payroll", null);
        when(registry.listExternalRegistrations(3L)).thenReturn(List.of(other));

        assertThrows(IllegalArgumentException.class, () -> controller.unregister(3L, 9L));

        verify(dataSourceService).requireForBrowse(3L);
        verify(registry, never()).unregister(9L);
    }
}
