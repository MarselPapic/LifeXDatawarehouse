package at.htlle.freq.application;

import at.htlle.freq.domain.Site;
import at.htlle.freq.domain.SiteRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID3;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.UUID5;
import static at.htlle.freq.application.TestFixtures.site;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SiteServiceTest {

    private SiteRepository repo;
    private LuceneIndexService lucene;
    private SiteService service;

    @BeforeEach
    void setUp() {
        repo = mock(SiteRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new SiteService(repo, lucene);
    }

    @Test
    void getSiteByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getSiteById(null));
    }

    @Test
    void getSitesByProjectRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getSitesByProject(null));
    }

    @Test
    void createSiteRequiresName() {
        Site value = new Site();
        value.setProjectID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateSite(value));
    }

    @Test
    void createSiteRequiresProjectId() {
        Site value = new Site();
        value.setSiteName("Site");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateSite(value));
    }

    @Test
    void createSiteRequiresAddressId() {
        Site value = new Site();
        value.setSiteName("Site");
        value.setProjectID(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateSite(value));
    }

    @Test
    void createSiteIndexesImmediately() {
        Site value = site();
        when(repo.save(value)).thenReturn(value);

        Site saved = service.createOrUpdateSite(value);
        assertSame(value, saved);
        verify(lucene).indexSite(eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID5.toString()), eq("Site"), eq("Zone"), eq(10));
    }

    @Test
    void createSiteRegistersAfterCommit() {
        Site value = site();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateSite(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexSite(eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID5.toString()), eq("Site"), eq("Zone"), eq(10));
    }

    @Test
    void createSiteContinuesWhenLuceneFails() {
        Site value = site();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexSite(any(), any(), any(), any(), any(), any());

        Site saved = service.createOrUpdateSite(value);
        assertSame(value, saved);
        verify(lucene).indexSite(eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID5.toString()), eq("Site"), eq("Zone"), eq(10));
    }

    @Test
    void updateSiteAppliesPatch() {
        Site existing = site();
        when(repo.findById(UUID4)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Site patch = new Site();
        patch.setSiteName("NewSite");
        patch.setProjectID(UUID.randomUUID());
        patch.setAddressID(UUID.randomUUID());
        patch.setFireZone("NewZone");
        patch.setTenantCount(42);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Site> updated = service.updateSite(UUID4, patch);
            assertTrue(updated.isPresent());
            assertEquals("NewSite", existing.getSiteName());
            assertEquals("NewZone", existing.getFireZone());
            assertEquals(42, existing.getTenantCount());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexSite(eq(UUID4.toString()), eq(existing.getProjectID().toString()), eq(existing.getAddressID().toString()), eq("NewSite"), eq("NewZone"), eq(42));
    }

    @Test
    void updateSiteIgnoresNullAddressPatch() {
        Site existing = site();
        when(repo.findById(UUID4)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Site patch = new Site();
        patch.setSiteName("NewName");

        Optional<Site> updated = service.updateSite(UUID4, patch);
        assertTrue(updated.isPresent());
        assertEquals(UUID5, existing.getAddressID());

        verify(lucene).indexSite(eq(UUID4.toString()), eq(existing.getProjectID().toString()), eq(UUID5.toString()), eq("NewName"), any(), any());
    }

    @Test
    void updateSiteReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID4)).thenReturn(Optional.empty());
        assertTrue(service.updateSite(UUID4, site()).isEmpty());
    }

    @Test
    void deleteSiteDeletesWhenPresent() {
        when(repo.findById(UUID4)).thenReturn(Optional.of(site()));
        service.deleteSite(UUID4);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID4);
        order.verify(repo).deleteById(UUID4);
    }
}
