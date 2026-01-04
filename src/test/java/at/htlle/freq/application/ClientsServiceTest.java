package at.htlle.freq.application;

import at.htlle.freq.domain.Clients;
import at.htlle.freq.domain.ClientsRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID1;
import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.client;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientsServiceTest {

    private ClientsRepository repo;
    private LuceneIndexService lucene;
    private ClientsService service;

    @BeforeEach
    void setUp() {
        repo = mock(ClientsRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new ClientsService(repo, lucene);
    }

    @Test
    void findBySiteRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.findBySite(null));
    }

    @Test
    void createClientRequiresSiteId() {
        Clients value = new Clients();
        value.setClientName("Client");
        value.setInstallType("LOCAL");
        assertThrows(IllegalArgumentException.class, () -> service.create(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createClientRequiresName() {
        Clients value = new Clients();
        value.setSiteID(UUID.randomUUID());
        value.setInstallType("LOCAL");
        assertThrows(IllegalArgumentException.class, () -> service.create(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createClientRequiresInstallType() {
        Clients value = new Clients();
        value.setSiteID(UUID.randomUUID());
        value.setClientName("Client");
        assertThrows(IllegalArgumentException.class, () -> service.create(value));
        verifyNoInteractions(repo);
    }

    @Test
    void createClientIndexesImmediately() {
        Clients value = client();
        when(repo.save(value)).thenAnswer(invocation -> {
            value.setClientID(UUID.randomUUID());
            return value;
        });

        Clients saved = service.create(value);
        assertNotNull(saved.getClientID());
        verify(lucene).indexClient(
                anyString(),
                anyString(),
                eq("Client"),
                eq("Brand"),
                eq("OS"),
                eq("LOCAL"),
                eq("Dispatcher"),
                eq("Office Suite")
        );
    }

    @Test
    void createClientRegistersAfterCommit() {
        Clients value = client();
        value.setClientID(UUID1);
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.create(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexClient(
                eq(UUID1.toString()),
                eq(UUID2.toString()),
                eq("Client"),
                eq("Brand"),
                eq("OS"),
                eq("LOCAL"),
                eq("Dispatcher"),
                eq("Office Suite")
        );
    }

    @Test
    void createClientContinuesWhenLuceneFails() {
        Clients value = client();
        value.setClientID(UUID1);
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error"))
                .when(lucene)
                .indexClient(any(), any(), any(), any(), any(), any(), any(), any());

        Clients saved = service.create(value);
        assertSame(value, saved);
        verify(lucene).indexClient(
                eq(UUID1.toString()),
                eq(UUID2.toString()),
                eq("Client"),
                eq("Brand"),
                eq("OS"),
                eq("LOCAL"),
                eq("Dispatcher"),
                eq("Office Suite")
        );
    }

    @Test
    void updateClientAppliesPatch() {
        Clients existing = client();
        existing.setClientID(UUID1);
        when(repo.findById(UUID1)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Clients patch = new Clients();
        patch.setClientName("Changed");
        patch.setClientBrand("NewBrand");
        patch.setInstallType("BROWSER");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Clients updated = service.update(UUID1, patch);
            assertEquals("Changed", updated.getClientName());
            assertEquals("NewBrand", updated.getClientBrand());
            assertEquals("BROWSER", updated.getInstallType());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexClient(
                eq(UUID1.toString()),
                eq(UUID2.toString()),
                eq("Changed"),
                eq("NewBrand"),
                eq("OS"),
                eq("BROWSER"),
                eq("Dispatcher"),
                eq("Office Suite")
        );
    }

    @Test
    void updateClientThrowsWhenMissing() {
        when(repo.findById(UUID1)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.update(UUID1, client()));
    }

    @Test
    void updateClientRequiresArguments() {
        Clients patch = client();
        assertThrows(NullPointerException.class, () -> service.update(null, patch));
        assertThrows(NullPointerException.class, () -> service.update(UUID1, null));
    }
}
