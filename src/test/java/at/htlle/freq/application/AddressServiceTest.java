package at.htlle.freq.application;

import at.htlle.freq.domain.Address;
import at.htlle.freq.domain.AddressRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.address;
import static at.htlle.freq.application.TestFixtures.UUID1;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AddressServiceTest {

    private AddressRepository repo;
    private LuceneIndexService lucene;
    private AddressService service;

    @BeforeEach
    void setUp() {
        repo = mock(AddressRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new AddressService(repo, lucene);
    }

    @Test
    void getAddressByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getAddressById(null));
    }

    @Test
    void createAddressRequiresStreet() {
        Address value = new Address();
        value.setCityID("CITY-1");
        assertThrows(IllegalArgumentException.class, () -> service.createAddress(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createAddressRequiresCity() {
        Address value = new Address();
        value.setStreet("Main Street");
        assertThrows(IllegalArgumentException.class, () -> service.createAddress(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createAddressIndexesImmediatelyWhenNoTransactionActive() {
        Address value = address();
        when(repo.save(value)).thenAnswer(invocation -> {
            value.setAddressID(UUID.randomUUID());
            return value;
        });

        Address saved = service.createAddress(value);
        assertNotNull(saved.getAddressID());
        verify(lucene).indexAddress(anyString(), eq("Main Street 1"), eq("CITY-1"));
    }

    @Test
    void createAddressRegistersAfterCommitWhenTransactionActive() {
        Address value = address();
        value.setAddressID(UUID1);
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createAddress(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexAddress(eq(UUID1.toString()), eq("Main Street 1"), eq("CITY-1"));
    }

    @Test
    void createAddressContinuesWhenLuceneFails() {
        Address value = address();
        value.setAddressID(UUID1);
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene down")).when(lucene).indexAddress(any(), any(), any());

        Address saved = service.createAddress(value);
        assertSame(value, saved);
        verify(lucene).indexAddress(eq(UUID1.toString()), eq("Main Street 1"), eq("CITY-1"));
    }

    @Test
    void updateAddressAppliesPatchValues() {
        Address existing = address();
        existing.setAddressID(UUID1);
        when(repo.findById(UUID1)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Address patch = new Address();
        patch.setStreet("Changed");
        patch.setCityID("CITY-2");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Address> updated = service.updateAddress(UUID1, patch);
            assertTrue(updated.isPresent());
            assertEquals("Changed", existing.getStreet());
            assertEquals("CITY-2", existing.getCityID());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexAddress(eq(UUID1.toString()), eq("Changed"), eq("CITY-2"));
    }

    @Test
    void updateAddressReturnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.updateAddress(id, address()).isEmpty());
        verify(repo, never()).save(any());
    }

    @Test
    void updateAddressRequiresArguments() {
        Address patch = address();
        assertThrows(NullPointerException.class, () -> service.updateAddress(null, patch));
        assertThrows(NullPointerException.class, () -> service.updateAddress(UUID1, null));
    }

    @Test
    void deleteAddressDelegatesToRepository() {
        service.deleteAddress(UUID1);
        verify(repo).deleteById(UUID1);
    }
}
