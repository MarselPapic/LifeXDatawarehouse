package at.htlle.freq.application;

import at.htlle.freq.domain.City;
import at.htlle.freq.domain.CityRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;

import static at.htlle.freq.application.TestFixtures.city;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CityServiceTest {

    private CityRepository repo;
    private LuceneIndexService lucene;
    private CityService service;

    @BeforeEach
    void setUp() {
        repo = mock(CityRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new CityService(repo, lucene);
    }

    @Test
    void getCityByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getCityById(null));
    }

    @Test
    void getCitiesByCountryRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getCitiesByCountry(null));
    }

    @Test
    void createCityRequiresId() {
        City value = new City();
        value.setCityName("Vienna");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateCity(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createCityRequiresName() {
        City value = new City();
        value.setCityID("CITY-1");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateCity(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createCityIndexesImmediately() {
        City value = city();
        when(repo.save(value)).thenReturn(value);

        City saved = service.createOrUpdateCity(value);
        assertSame(value, saved);
        verify(lucene).indexCity("CITY-1", "Vienna", "AT");
    }

    @Test
    void createCityRegistersAfterCommit() {
        City value = city();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateCity(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexCity("CITY-1", "Vienna", "AT");
    }

    @Test
    void createCityContinuesWhenLuceneFails() {
        City value = city();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexCity(any(), any(), any());

        City saved = service.createOrUpdateCity(value);
        assertSame(value, saved);
        verify(lucene).indexCity("CITY-1", "Vienna", "AT");
    }

    @Test
    void updateCityAppliesPatch() {
        City existing = city();
        when(repo.findById("CITY-1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        City patch = new City();
        patch.setCityName("Wien");
        patch.setCountryCode("AUT");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<City> updated = service.updateCity("CITY-1", patch);
            assertTrue(updated.isPresent());
            assertEquals("Wien", existing.getCityName());
            assertEquals("AUT", existing.getCountryCode());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexCity("CITY-1", "Wien", "AUT");
    }

    @Test
    void updateCityReturnsEmptyWhenUnknown() {
        when(repo.findById("CITY-1")).thenReturn(Optional.empty());
        assertTrue(service.updateCity("CITY-1", city()).isEmpty());
    }

    @Test
    void deleteCityDelegatesToRepository() {
        when(repo.findById("CITY-1")).thenReturn(Optional.of(city()));
        service.deleteCity("CITY-1");
        verify(repo).findById("CITY-1");
        verify(repo).deleteById("CITY-1");
    }
}
