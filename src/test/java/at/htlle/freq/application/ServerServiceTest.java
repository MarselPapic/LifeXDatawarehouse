package at.htlle.freq.application;

import at.htlle.freq.domain.Server;
import at.htlle.freq.domain.ServerRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.server;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerServiceTest {

    private ServerRepository repo;
    private LuceneIndexService lucene;
    private ServerService service;

    @BeforeEach
    void setUp() {
        repo = mock(ServerRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new ServerService(repo, lucene);
    }

    @Test
    void getServerByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getServerById(null));
    }

    @Test
    void getServersBySiteRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getServersBySite(null));
    }

    @Test
    void createServerRequiresSiteId() {
        Server value = new Server();
        value.setServerName("Server");
        value.setServerBrand("Brand");
        value.setServerSerialNr("Serial");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateServer(value));
    }

    @Test
    void createServerRequiresName() {
        Server value = new Server();
        value.setSiteID(UUID.randomUUID());
        value.setServerBrand("Brand");
        value.setServerSerialNr("Serial");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateServer(value));
    }

    @Test
    void createServerRequiresBrand() {
        Server value = new Server();
        value.setSiteID(UUID.randomUUID());
        value.setServerName("Server");
        value.setServerSerialNr("Serial");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateServer(value));
    }

    @Test
    void createServerRequiresSerial() {
        Server value = new Server();
        value.setSiteID(UUID.randomUUID());
        value.setServerName("Server");
        value.setServerBrand("Brand");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateServer(value));
    }

    @Test
    void createServerIndexesImmediately() {
        Server value = server();
        when(repo.save(value)).thenReturn(value);

        Server saved = service.createOrUpdateServer(value);
        assertSame(value, saved);
        verify(lucene).indexServer(eq(UUID2.toString()), eq(UUID4.toString()), eq("Server"), eq("Brand"), eq("SERIAL"), eq("Linux"), eq("Patch"), eq("Platform"), eq("1.0"), eq(true));
    }

    @Test
    void createServerRegistersAfterCommit() {
        Server value = server();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateServer(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexServer(eq(UUID2.toString()), eq(UUID4.toString()), eq("Server"), eq("Brand"), eq("SERIAL"), eq("Linux"), eq("Patch"), eq("Platform"), eq("1.0"), eq(true));
    }

    @Test
    void createServerContinuesWhenLuceneFails() {
        Server value = server();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexServer(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());

        Server saved = service.createOrUpdateServer(value);
        assertSame(value, saved);
        verify(lucene).indexServer(eq(UUID2.toString()), eq(UUID4.toString()), eq("Server"), eq("Brand"), eq("SERIAL"), eq("Linux"), eq("Patch"), eq("Platform"), eq("1.0"), eq(true));
    }

    @Test
    void updateServerAppliesPatch() {
        Server existing = server();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Server patch = new Server();
        patch.setSiteID(UUID.randomUUID());
        patch.setServerName("NewServer");
        patch.setServerBrand("NewBrand");
        patch.setServerSerialNr("NewSerial");
        patch.setServerOS("NewOS");
        patch.setPatchLevel("NewPatch");
        patch.setVirtualPlatform("NewPlatform");
        patch.setVirtualVersion("NewVersion");
        patch.setHighAvailability(false);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Server> updated = service.updateServer(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals("NewServer", existing.getServerName());
            assertEquals("NewBrand", existing.getServerBrand());
            assertEquals("NewSerial", existing.getServerSerialNr());
            assertEquals("NewOS", existing.getServerOS());
            assertFalse(existing.isHighAvailability());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexServer(eq(UUID2.toString()), eq(existing.getSiteID().toString()), eq("NewServer"), eq("NewBrand"), eq("NewSerial"), eq("NewOS"), eq("NewPatch"), eq("NewPlatform"), eq("NewVersion"), eq(false));
    }

    @Test
    void updateServerReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updateServer(UUID2, server()).isEmpty());
    }

    @Test
    void deleteServerLoadsOptional() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(server()));
        service.deleteServer(UUID2);
        verify(repo).findById(UUID2);
    }
}
