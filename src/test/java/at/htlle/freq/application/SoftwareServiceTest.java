package at.htlle.freq.application;

import at.htlle.freq.domain.Software;
import at.htlle.freq.domain.SoftwareRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID5;
import static at.htlle.freq.application.TestFixtures.software;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SoftwareServiceTest {

    private SoftwareRepository repo;
    private LuceneIndexService lucene;
    private SoftwareService service;

    @BeforeEach
    void setUp() {
        repo = mock(SoftwareRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new SoftwareService(repo, lucene);
    }

    @Test
    void getSoftwareByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getSoftwareById(null));
    }

    @Test
    void getSoftwareByNameReturnsEmptyForBlank() {
        assertTrue(service.getSoftwareByName(" ").isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void createSoftwareRequiresName() {
        Software value = new Software();
        value.setRelease("1.0");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateSoftware(value));
    }

    @Test
    void createSoftwareRequiresRelease() {
        Software value = new Software();
        value.setName("Software");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateSoftware(value));
    }

    @Test
    void createSoftwareIndexesImmediately() {
        Software value = software();
        when(repo.save(value)).thenReturn(value);

        Software saved = service.createOrUpdateSoftware(value);
        assertSame(value, saved);
        verify(lucene).indexSoftware(eq(UUID5.toString()), eq("Software"), eq("1.0"), eq("rev1"), eq("Production"), eq("Subscription"), eq("2024-12-31"), eq("2024-01-01"), eq("2025-12-31"));
    }

    @Test
    void createSoftwareRegistersAfterCommit() {
        Software value = software();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateSoftware(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexSoftware(eq(UUID5.toString()), eq("Software"), eq("1.0"), eq("rev1"), eq("Production"), eq("Subscription"), eq("2024-12-31"), eq("2024-01-01"), eq("2025-12-31"));
    }

    @Test
    void createSoftwareContinuesWhenLuceneFails() {
        Software value = software();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexSoftware(any(), any(), any(), any(), any(), any(), any(), any(), any());

        Software saved = service.createOrUpdateSoftware(value);
        assertSame(value, saved);
        verify(lucene).indexSoftware(eq(UUID5.toString()), eq("Software"), eq("1.0"), eq("rev1"), eq("Production"), eq("Subscription"), eq("2024-12-31"), eq("2024-01-01"), eq("2025-12-31"));
    }

    @Test
    void updateSoftwareAppliesPatch() {
        Software existing = software();
        when(repo.findById(UUID5)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Software patch = new Software();
        patch.setName("New Software");
        patch.setRelease("2.0");
        patch.setRevision("rev2");
        patch.setSupportPhase("Beta");
        patch.setLicenseModel("License");
        patch.setEndOfSalesDate("2025-01-01");
        patch.setSupportStartDate("2025-02-01");
        patch.setSupportEndDate("2026-01-01");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Software> updated = service.updateSoftware(UUID5, patch);
            assertTrue(updated.isPresent());
            assertEquals("New Software", existing.getName());
            assertEquals("2.0", existing.getRelease());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexSoftware(eq(UUID5.toString()), eq("New Software"), eq("2.0"), eq("rev2"), eq("Beta"), eq("License"), eq("2025-01-01"), eq("2025-02-01"), eq("2026-01-01"));
    }

    @Test
    void updateSoftwareReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID5)).thenReturn(Optional.empty());
        assertTrue(service.updateSoftware(UUID5, software()).isEmpty());
    }

    @Test
    void deleteSoftwareLoadsOptional() {
        when(repo.findById(UUID5)).thenReturn(Optional.of(software()));
        service.deleteSoftware(UUID5);
        verify(repo).findById(UUID5);
    }
}
