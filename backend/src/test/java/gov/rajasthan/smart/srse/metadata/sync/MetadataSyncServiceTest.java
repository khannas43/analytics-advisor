package gov.rajasthan.smart.srse.metadata.sync;

import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataSyncServiceTest {

    @Mock
    private RegisteredTableRepository registrations;
    @Mock
    private LakehouseBrowseService browse;
    @Mock
    private MetadataSyncRunRepository runs;
    @Mock
    private MetadataSyncTableResultRepository tableResults;
    @Mock
    private TableColumnSnapshotRepository snapshots;

    private MetadataSyncService service;

    @BeforeEach
    void setUp() {
        service = new MetadataSyncService(
                registrations, browse, null, runs, tableResults, snapshots, new SyncTaskExecutor());
    }

    @Test
    void perTableFailureRetainsPriorSnapshotRows() {
        RegisteredTable good = new RegisteredTable(1L, "c1", "s", "t1", "GOLD");
        RegisteredTable bad = new RegisteredTable(2L, "c2", "s", "t2", "GOLD");
        when(registrations.findAll()).thenReturn(List.of(good, bad));
        MetadataSyncRun run = new MetadataSyncRun(Instant.now(), MetadataSyncTrigger.MANUAL, MetadataSyncOutcome.RUNNING);
        org.mockito.Mockito.doAnswer(inv -> {
            MetadataSyncRun saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        }).when(runs).save(any(MetadataSyncRun.class));
        when(browse.listColumns("c1", "s", "t1")).thenReturn(List.of(
                new LakehouseBrowseService.ColumnInfo("a", "varchar")));
        when(browse.listColumns("c2", "s", "t2")).thenThrow(new RuntimeException("jdbc:secret@host fail"));
        when(runs.findById(9L)).thenReturn(Optional.of(run));

        service.executeRun(9L);

        verify(snapshots).deleteAllForTable("c1", "s", "t1");
        verify(snapshots, never()).deleteAllForTable("c2", "s", "t2");
        verify(tableResults, atLeastOnce()).save(any(MetadataSyncTableResult.class));
    }

    @Test
    void degradedOutcomeWhenAnyTableFails() {
        RegisteredTable good = new RegisteredTable(1L, "c1", "s", "t1", "GOLD");
        when(registrations.findAll()).thenReturn(List.of(good));
        MetadataSyncRun run = new MetadataSyncRun(Instant.now(), MetadataSyncTrigger.MANUAL, MetadataSyncOutcome.RUNNING);
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 11L);
        org.mockito.Mockito.doAnswer(inv -> {
            MetadataSyncRun saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        }).when(runs).save(any(MetadataSyncRun.class));
        when(browse.listColumns("c1", "s", "t1")).thenThrow(new RuntimeException("timeout"));
        when(runs.findById(11L)).thenReturn(Optional.of(run));

        service.executeRun(11L);

        ArgumentCaptor<MetadataSyncRun> finished = ArgumentCaptor.forClass(MetadataSyncRun.class);
        verify(runs, org.mockito.Mockito.atLeastOnce()).save(finished.capture());
        assertTrue(finished.getAllValues().stream()
                .anyMatch(r -> r.getOverallOutcome() == MetadataSyncOutcome.DEGRADED));
    }

    @Test
    void registrationSyncFailureDoesNotThrowToCaller() {
        RegisteredTable table = new RegisteredTable(1L, "c1", "s", "t1", "GOLD");
        org.mockito.Mockito.doAnswer(inv -> {
            MetadataSyncRun saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        }).when(runs).save(any(MetadataSyncRun.class));
        when(browse.listColumns("c1", "s", "t1")).thenThrow(new RuntimeException("engine down"));
        when(runs.findById(3L)).thenReturn(Optional.of(
                new MetadataSyncRun(Instant.now(), MetadataSyncTrigger.REGISTRATION, MetadataSyncOutcome.RUNNING)));

        assertDoesNotThrow(() -> service.syncOneTableBestEffort(table, MetadataSyncTrigger.REGISTRATION));
    }

    @Test
    void overlappingRunSkipped() {
        MetadataSyncRun running = new MetadataSyncRun(Instant.now(), MetadataSyncTrigger.MANUAL, MetadataSyncOutcome.RUNNING);
        org.springframework.test.util.ReflectionTestUtils.setField(running, "id", 5L);
        when(runs.findFirstByOverallOutcomeOrderByStartedAtDesc(MetadataSyncOutcome.RUNNING))
                .thenReturn(Optional.of(running));

        MetadataSyncService.SyncStartResult result = service.startSyncAll(MetadataSyncTrigger.MANUAL);

        assertTrue(result.skippedOverlapping());
        assertEquals(5L, result.runId());
        verify(registrations, never()).findAll();
    }
}
