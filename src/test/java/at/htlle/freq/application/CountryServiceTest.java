package at.htlle.freq.application;

import at.htlle.freq.domain.Country;
import at.htlle.freq.domain.CountryRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static at.htlle.freq.application.TestFixtures.country;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CountryServiceTest {

    private CountryRepository repo;
    private LuceneIndexService lucene;
    private CountryService service;

    @BeforeEach
    void setUp() {
        repo = mock(CountryRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new CountryService(repo, lucene);
    }

    @Test
    void getCountryByCodeRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getCountryByCode(null));
    }

    @Test
    void createCountryRequiresCode() {
        Country value = new Country();
        value.setCountryName("Austria");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateCountry(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createCountryRequiresName() {
        Country value = new Country();
        value.setCountryCode("AT");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateCountry(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createCountryIndexesImmediately() {
        Country value = country();
        when(repo.save(value)).thenReturn(value);

        Country saved = service.createOrUpdateCountry(value);
        assertSame(value, saved);
        verify(lucene).indexCountry("AT", "Austria");
    }

    @Test
    void createCountryRegistersAfterCommit() {
        Country value = country();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateCountry(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexCountry("AT", "Austria");
    }

    @Test
    void createCountryContinuesOnLuceneFailure() {
        Country value = country();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexCountry(any(), any());

        Country saved = service.createOrUpdateCountry(value);
        assertSame(value, saved);
        verify(lucene).indexCountry("AT", "Austria");
    }

    @Test
    void updateCountryAppliesPatch() {
        Country existing = country();
        when(repo.findById("AT")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Country patch = new Country();
        patch.setCountryName("Republic of Austria");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Country> updated = service.updateCountry("AT", patch);
            assertTrue(updated.isPresent());
            assertEquals("Republic of Austria", existing.getCountryName());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexCountry("AT", "Republic of Austria");
    }

    @Test
    void updateCountryReturnsEmptyWhenUnknown() {
        when(repo.findById("AT")).thenReturn(Optional.empty());
        assertTrue(service.updateCountry("AT", country()).isEmpty());
    }

    @Test
    void deleteCountryDeletesWhenPresent() {
        when(repo.findById("AT")).thenReturn(Optional.of(country()));
        service.deleteCountry("AT");
        InOrder order = inOrder(repo);
        order.verify(repo).findById("AT");
        order.verify(repo).deleteById("AT");
    }

    @Test
    void getVariantByCodeTrimsInput() {
        when(repo.findById("AT")).thenReturn(Optional.of(country()));
        service.getCountryByCode("AT");
        verify(repo).findById("AT");
    }
}
