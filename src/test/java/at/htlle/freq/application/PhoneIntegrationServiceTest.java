package at.htlle.freq.application;

import at.htlle.freq.domain.PhoneIntegration;
import at.htlle.freq.domain.PhoneIntegrationRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID1;
import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.phoneIntegration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhoneIntegrationServiceTest {

    private PhoneIntegrationRepository repo;
    private LuceneIndexService lucene;
    private PhoneIntegrationService service;

    @BeforeEach
    void setUp() {
        repo = mock(PhoneIntegrationRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new PhoneIntegrationService(repo, lucene);
    }

    @Test
    void getPhoneIntegrationByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getPhoneIntegrationById(null));
    }

    @Test
    void getPhoneIntegrationsByClientRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getPhoneIntegrationsByClient(null));
    }

    @Test
    void createPhoneIntegrationRequiresClientId() {
        PhoneIntegration value = new PhoneIntegration();
        value.setPhoneType("TYPE");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdatePhoneIntegration(value));
    }

    @Test
    void createPhoneIntegrationRequiresType() {
        PhoneIntegration value = new PhoneIntegration();
        value.setClientID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdatePhoneIntegration(value));
    }

    @Test
    void createPhoneIntegrationIndexesImmediately() {
        PhoneIntegration value = phoneIntegration();
        when(repo.save(value)).thenReturn(value);

        PhoneIntegration saved = service.createOrUpdatePhoneIntegration(value);
        assertSame(value, saved);
        verify(lucene).indexPhoneIntegration(eq(UUID2.toString()), eq(UUID1.toString()), eq("TYPE"), eq("Brand"), eq("SERIAL"), eq("FW"));
    }

    @Test
    void createPhoneIntegrationRegistersAfterCommit() {
        PhoneIntegration value = phoneIntegration();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdatePhoneIntegration(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexPhoneIntegration(eq(UUID2.toString()), eq(UUID1.toString()), eq("TYPE"), eq("Brand"), eq("SERIAL"), eq("FW"));
    }

    @Test
    void createPhoneIntegrationContinuesWhenLuceneFails() {
        PhoneIntegration value = phoneIntegration();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexPhoneIntegration(any(), any(), any(), any(), any(), any());

        PhoneIntegration saved = service.createOrUpdatePhoneIntegration(value);
        assertSame(value, saved);
        verify(lucene).indexPhoneIntegration(eq(UUID2.toString()), eq(UUID1.toString()), eq("TYPE"), eq("Brand"), eq("SERIAL"), eq("FW"));
    }

    @Test
    void updatePhoneIntegrationAppliesPatch() {
        PhoneIntegration existing = phoneIntegration();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        PhoneIntegration patch = new PhoneIntegration();
        patch.setClientID(UUID.randomUUID());
        patch.setPhoneType("NEW");
        patch.setPhoneBrand("NewBrand");
        patch.setPhoneSerialNr("NewSerial");
        patch.setPhoneFirmware("NewFW");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<PhoneIntegration> updated = service.updatePhoneIntegration(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals("NEW", existing.getPhoneType());
            assertEquals("NewBrand", existing.getPhoneBrand());
            assertEquals("NewSerial", existing.getPhoneSerialNr());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexPhoneIntegration(eq(UUID2.toString()), eq(existing.getClientID().toString()), eq("NEW"), eq("NewBrand"), eq("NewSerial"), eq("NewFW"));
    }

    @Test
    void updatePhoneIntegrationReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updatePhoneIntegration(UUID2, phoneIntegration()).isEmpty());
    }

    @Test
    void deletePhoneIntegrationDeletesWhenPresent() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(phoneIntegration()));
        service.deletePhoneIntegration(UUID2);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID2);
        order.verify(repo).deleteById(UUID2);
    }
}
