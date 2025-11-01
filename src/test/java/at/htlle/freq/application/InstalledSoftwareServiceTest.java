package at.htlle.freq.application;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID2;
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
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()));
    }

    @Test
    void createInstalledSoftwareRegistersAfterCommit() {
        InstalledSoftware value = installedSoftware();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateInstalledSoftware(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()));
    }

    @Test
    void createInstalledSoftwareContinuesWhenLuceneFails() {
        InstalledSoftware value = installedSoftware();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexInstalledSoftware(any(), any(), any());

        InstalledSoftware saved = service.createOrUpdateInstalledSoftware(value);
        assertSame(value, saved);
        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID5.toString()));
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

        verify(lucene).indexInstalledSoftware(eq(UUID2.toString()), eq(existing.getSiteID().toString()), eq(existing.getSoftwareID().toString()));
    }

    @Test
    void updateInstalledSoftwareReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updateInstalledSoftware(UUID2, installedSoftware()).isEmpty());
    }

    @Test
    void deleteInstalledSoftwareLoadsOptional() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(installedSoftware()));
        service.deleteInstalledSoftware(UUID2);
        verify(repo).findById(UUID2);
    }
}
