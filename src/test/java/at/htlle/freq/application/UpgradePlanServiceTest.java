package at.htlle.freq.application;

import at.htlle.freq.domain.UpgradePlan;
import at.htlle.freq.domain.UpgradePlanRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID3;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.UUID5;
import static at.htlle.freq.application.TestFixtures.upgradePlan;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpgradePlanServiceTest {

    private UpgradePlanRepository repo;
    private LuceneIndexService lucene;
    private UpgradePlanService service;

    @BeforeEach
    void setUp() {
        repo = mock(UpgradePlanRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new UpgradePlanService(repo, lucene);
    }

    @Test
    void getUpgradePlanByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getUpgradePlanById(null));
    }

    @Test
    void getUpgradePlansBySiteRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getUpgradePlansBySite(null));
    }

    @Test
    void createUpgradePlanRequiresSiteId() {
        UpgradePlan value = new UpgradePlan();
        value.setSoftwareID(UUID.randomUUID());
        value.setStatus("Status");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateUpgradePlan(value));
    }

    @Test
    void createUpgradePlanRequiresSoftwareId() {
        UpgradePlan value = new UpgradePlan();
        value.setSiteID(UUID.randomUUID());
        value.setStatus("Status");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateUpgradePlan(value));
    }

    @Test
    void createUpgradePlanRequiresStatus() {
        UpgradePlan value = new UpgradePlan();
        value.setSiteID(UUID.randomUUID());
        value.setSoftwareID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateUpgradePlan(value));
    }

    @Test
    void createUpgradePlanIndexesImmediately() {
        UpgradePlan value = upgradePlan();
        when(repo.save(value)).thenReturn(value);

        UpgradePlan saved = service.createOrUpdateUpgradePlan(value);
        assertSame(value, saved);
        verify(lucene).indexUpgradePlan(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID5.toString()), eq("2024-03-01"), eq("2024-03-02"), eq("Planned"), eq("2024-01-01"), eq("Alice"));
    }

    @Test
    void createUpgradePlanRegistersAfterCommit() {
        UpgradePlan value = upgradePlan();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateUpgradePlan(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexUpgradePlan(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID5.toString()), eq("2024-03-01"), eq("2024-03-02"), eq("Planned"), eq("2024-01-01"), eq("Alice"));
    }

    @Test
    void createUpgradePlanContinuesWhenLuceneFails() {
        UpgradePlan value = upgradePlan();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexUpgradePlan(any(), any(), any(), any(), any(), any(), any(), any());

        UpgradePlan saved = service.createOrUpdateUpgradePlan(value);
        assertSame(value, saved);
        verify(lucene).indexUpgradePlan(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID5.toString()), eq("2024-03-01"), eq("2024-03-02"), eq("Planned"), eq("2024-01-01"), eq("Alice"));
    }

    @Test
    void updateUpgradePlanAppliesPatch() {
        UpgradePlan existing = upgradePlan();
        when(repo.findById(UUID3)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        UpgradePlan patch = new UpgradePlan();
        patch.setSiteID(UUID.randomUUID());
        patch.setSoftwareID(UUID.randomUUID());
        patch.setPlannedWindowStart("2025-01-01");
        patch.setPlannedWindowEnd("2025-01-02");
        patch.setStatus("Approved");
        patch.setCreatedAt("2024-02-01");
        patch.setCreatedBy("Bob");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<UpgradePlan> updated = service.updateUpgradePlan(UUID3, patch);
            assertTrue(updated.isPresent());
            assertEquals("Approved", existing.getStatus());
            assertEquals("Bob", existing.getCreatedBy());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexUpgradePlan(eq(UUID3.toString()), eq(existing.getSiteID().toString()), eq(existing.getSoftwareID().toString()), eq("2025-01-01"), eq("2025-01-02"), eq("Approved"), eq("2024-02-01"), eq("Bob"));
    }

    @Test
    void updateUpgradePlanReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID3)).thenReturn(Optional.empty());
        assertTrue(service.updateUpgradePlan(UUID3, upgradePlan()).isEmpty());
    }

    @Test
    void deleteUpgradePlanLoadsOptional() {
        when(repo.findById(UUID3)).thenReturn(Optional.of(upgradePlan()));
        service.deleteUpgradePlan(UUID3);
        verify(repo).findById(UUID3);
    }
}
