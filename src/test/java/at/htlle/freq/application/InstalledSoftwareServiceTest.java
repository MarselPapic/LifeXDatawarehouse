package at.htlle.freq.application;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.application.dto.SiteSoftwareOverviewEntry;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.InstalledSoftwareStatus;
import at.htlle.freq.domain.SiteSoftwareOverview;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.UUID3;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.UUID5;
import static at.htlle.freq.application.TestFixtures.installedSoftware;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstalledSoftwareServiceTest {

    private InstalledSoftwareRepository repo;
    private LuceneIndexService lucene;
    private InstalledSoftwareService service;

    @BeforeEach
    void setUp() {
        repo = mock(InstalledSoftwareRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new InstalledSoftwareService(repo, lucene);
    }

    @Test
    void getInstalledSoftwareByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getInstalledSoftwareById(null));
    }

    @Test
    void getInstalledSoftwareBySiteRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getInstalledSoftwareBySite(null));
    }

    @Test
    void getSiteSoftwareOverviewRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getSiteSoftwareOverview(null));
    }

    @Test
    void getSiteSoftwareOverviewNormalizesStatus() {
        SiteSoftwareOverview row = new SiteSoftwareOverview(
                UUID.randomUUID(),
                UUID4,
                "Site",
                UUID5,
                "CRM",
                "1.0",
                "rev1",
                "installed",
                "2024-01-01",
                "2024-02-02",
                null,
                null
        );
        when(repo.findOverviewBySite(UUID4)).thenReturn(List.of(row));

        List<SiteSoftwareOverviewEntry> result = service.getSiteSoftwareOverview(UUID4);
        assertEquals(1, result.size());
        SiteSoftwareOverviewEntry entry = result.get(0);
        assertEquals("Installed", entry.status());
        assertEquals("Installed", entry.statusLabel());
        assertEquals("2024-02-02", entry.installedAt());
        verify(repo).findOverviewBySite(UUID4);
    }

    @Test
    void getInstalledSoftwareBySoftwareRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getInstalledSoftwareBySoftware(null));
    }

    @Test
    void createInstalledSoftwareRequiresSiteId() {
        InstalledSoftware value = new InstalledSoftware();
        value.setSoftwareID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateInstalledSoftware(value));
    }

    @Test
    void createInstalledSoftwareRequiresSoftwareId() {
        InstalledSoftware value = new InstalledSoftware();
        value.setSiteID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateInstalledSoftware(value));
    }

    @Test
    void createInstalledSoftwareIndexesImmediately() {
        InstalledSoftware value = installedSoftware();
        when(repo.save(value)).thenReturn(value);

        InstalledSoftware saved = service.createOrUpdateInstalledSoftware(value);
        assertSame(value, saved);
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()),
                eq(InstalledSoftwareStatus.OFFERED.dbValue()), eq("2024-01-10"), isNull(), isNull(), isNull());
    }

    @Test
    void createInstalledSoftwareRegistersAfterCommit() {
        InstalledSoftware value = installedSoftware();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateInstalledSoftware(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()),
                eq(InstalledSoftwareStatus.OFFERED.dbValue()), eq("2024-01-10"), isNull(), isNull(), isNull());
    }

    @Test
    void createInstalledSoftwareContinuesWhenLuceneFails() {
        InstalledSoftware value = installedSoftware();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene)
                .indexInstalledSoftware(any(), any(), any(), any(), any(), any(), any(), any());

        InstalledSoftware saved = service.createOrUpdateInstalledSoftware(value);
        assertSame(value, saved);
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()),
                eq(InstalledSoftwareStatus.OFFERED.dbValue()), eq("2024-01-10"), isNull(), isNull(), isNull());
    }

    @Test
    void createInstalledSoftwareDefaultsStatusWhenMissing() {
        InstalledSoftware value = new InstalledSoftware();
        value.setSiteID(UUID4);
        value.setSoftwareID(UUID5);
        when(repo.save(any())).thenAnswer(invocation -> {
            InstalledSoftware arg = invocation.getArgument(0);
            arg.setInstalledSoftwareID(UUID2);
            return arg;
        });

        InstalledSoftware saved = service.createOrUpdateInstalledSoftware(value);
        assertEquals(InstalledSoftwareStatus.OFFERED.dbValue(), saved.getStatus());
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()),
                eq(InstalledSoftwareStatus.OFFERED.dbValue()), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void createInstalledSoftwareRejectsInvalidStatus() {
        InstalledSoftware value = installedSoftware();
        value.setStatus("Invalid");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateInstalledSoftware(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createInstalledSoftwareRejectsInvalidDate() {
        InstalledSoftware value = installedSoftware();
        value.setOfferedDate("2024-13-40");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateInstalledSoftware(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createInstalledSoftwareSupportsOutdatedStatusAndDates() {
        InstalledSoftware value = installedSoftware();
        value.setStatus("outdated");
        value.setInstalledDate("2024-02-01");
        value.setOutdatedDate("2024-03-01");
        when(repo.save(value)).thenReturn(value);

        InstalledSoftware saved = service.createOrUpdateInstalledSoftware(value);
        assertEquals(InstalledSoftwareStatus.OUTDATED.dbValue(), saved.getStatus());
        assertEquals("2024-02-01", saved.getInstalledDate());
        assertEquals("2024-03-01", saved.getOutdatedDate());
        assertNull(saved.getRejectedDate());
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()),
                eq(InstalledSoftwareStatus.OUTDATED.dbValue()), eq("2024-01-10"), eq("2024-02-01"), isNull(), eq("2024-03-01"));
    }

    @Test
    void updateInstalledSoftwareAppliesPatch() {
        InstalledSoftware existing = installedSoftware();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        InstalledSoftware patch = new InstalledSoftware();
        patch.setSiteID(UUID.randomUUID());
        patch.setSoftwareID(UUID.randomUUID());

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<InstalledSoftware> updated = service.updateInstalledSoftware(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals(patch.getSiteID(), existing.getSiteID());
            assertEquals(patch.getSoftwareID(), existing.getSoftwareID());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(existing.getSiteID().toString()),
                eq(existing.getSoftwareID().toString()), eq(InstalledSoftwareStatus.OFFERED.dbValue()),
                eq("2024-01-10"), isNull(), isNull(), isNull());
    }

    @Test
    void updateInstalledSoftwareUpdatesStatusWhenProvided() {
        InstalledSoftware existing = installedSoftware();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        InstalledSoftware patch = new InstalledSoftware();
        patch.setStatus("rejected");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<InstalledSoftware> updated = service.updateInstalledSoftware(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals(InstalledSoftwareStatus.REJECTED.dbValue(), updated.get().getStatus());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(existing.getSiteID().toString()),
                eq(existing.getSoftwareID().toString()), eq(InstalledSoftwareStatus.REJECTED.dbValue()),
                eq("2024-01-10"), isNull(), isNull(), isNull());
    }

    @Test
    void updateInstalledSoftwareReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updateInstalledSoftware(UUID2, installedSoftware()).isEmpty());
    }

    @Test
    void deleteInstalledSoftwareDeletesWhenPresent() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(installedSoftware()));
        service.deleteInstalledSoftware(UUID2);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID2);
        order.verify(repo).deleteById(UUID2);
    }

    @Test
    void replaceAssignmentsForSiteSynchronizesRecords() {
        InstalledSoftware existing = installedSoftware();
        InstalledSoftware stale = new InstalledSoftware(UUID3, UUID4, UUID.randomUUID(),
                InstalledSoftwareStatus.INSTALLED.dbValue(), "2024-01-01", "2024-01-15", null, null);

        when(repo.findBySite(UUID4)).thenReturn(List.of(existing, stale));
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.findById(UUID3)).thenReturn(Optional.of(stale));
        when(repo.save(any())).thenAnswer(invocation -> {
            InstalledSoftware arg = invocation.getArgument(0);
            if (arg.getInstalledSoftwareID() == null) {
                arg.setInstalledSoftwareID(UUID.randomUUID());
            }
            return arg;
        });

        InstalledSoftware updated = new InstalledSoftware();
        updated.setInstalledSoftwareID(UUID2);
        updated.setSoftwareID(existing.getSoftwareID());
        updated.setStatus("Installed");
        updated.setOfferedDate("2024-01-05");
        updated.setInstalledDate("2024-02-02");
        updated.setRejectedDate("2024-03-03");

        InstalledSoftware created = new InstalledSoftware();
        created.setSoftwareID(UUID.randomUUID());
        created.setStatus("Rejected");
        created.setOfferedDate("2024-04-01");
        created.setInstalledDate("2024-04-05");
        created.setRejectedDate("2024-04-10");

        List<InstalledSoftware> result = service.replaceAssignmentsForSite(UUID4, List.of(updated, created));

        assertEquals(2, result.size());
        InstalledSoftware updatedResult = result.stream()
                .filter(isw -> UUID2.equals(isw.getInstalledSoftwareID()))
                .findFirst()
                .orElseThrow();
        assertEquals(InstalledSoftwareStatus.INSTALLED.dbValue(), updatedResult.getStatus());
        assertEquals("2024-01-05", updatedResult.getOfferedDate());
        assertEquals("2024-02-02", updatedResult.getInstalledDate());
        assertNull(updatedResult.getRejectedDate());

        verify(repo).deleteById(stale.getInstalledSoftwareID());
        verify(lucene, atLeastOnce()).indexInstalledSoftware(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
