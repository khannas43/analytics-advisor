package gov.rajasthan.smart.srse.metadata.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataManifestServiceTest {

    @Mock
    private RegisteredTableRepository registrations;
    @Mock
    private TableColumnSnapshotRepository snapshots;
    @Mock
    private MetadataSyncRunRepository runs;

    private MetadataManifestService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MetadataManifestService(registrations, snapshots, runs, objectMapper);
    }

    @Test
    void manifestMatchesSnapshotRowsAndContainsNoSecrets() throws Exception {
        MetadataSyncRun latest = new MetadataSyncRun(Instant.parse("2026-10-01T00:00:00Z"),
                MetadataSyncTrigger.SCHEDULED, MetadataSyncOutcome.SUCCESS);
        org.springframework.test.util.ReflectionTestUtils.setField(latest, "id", 42L);
        when(runs.findFirstByOrderByStartedAtDesc()).thenReturn(Optional.of(latest));
        RegisteredTable reg = new RegisteredTable(1L, "iceberg", "srse", "beneficiary", "GOLD");
        when(registrations.findAll()).thenReturn(List.of(reg));
        when(snapshots.findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                "iceberg", "srse", "beneficiary")).thenReturn(List.of(
                new TableColumnSnapshot("iceberg", "srse", "beneficiary", "district", "varchar", 0,
                        Instant.now(), 42L)));

        String json = service.buildManifestJson();
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.contains("jdbc:"));
        assertFalse(json.contains("@host"));

        JsonNode root = objectMapper.readTree(json);
        assertEquals(42, root.get("syncRunId").asInt());
        JsonNode table = root.get("tables").get(0);
        assertEquals("district", table.get("columns").get(0).get("name").asText());
        assertEquals("varchar", table.get("columns").get(0).get("dataType").asText());
        assertTrue(root.has("generatedAt"));
    }
}
