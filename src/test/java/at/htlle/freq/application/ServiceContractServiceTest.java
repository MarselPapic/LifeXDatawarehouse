package at.htlle.freq.application;

import at.htlle.freq.domain.ServiceContract;
import at.htlle.freq.domain.ServiceContractRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID3;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.serviceContract;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceContractServiceTest {

    private ServiceContractRepository repo;
    private LuceneIndexService lucene;
    private ServiceContractService service;

    @BeforeEach
    void setUp() {
        repo = mock(ServiceContractRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new ServiceContractService(repo, lucene);
    }

    @Test
    void getContractByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getContractById(null));
    }

    @Test
    void getContractsByAccountRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getContractsByAccount(null));
    }

    @Test
    void createContractRequiresAccountId() {
        ServiceContract value = new ServiceContract();
        value.setProjectID(UUID.randomUUID());
        value.setContractNumber("C-1");
        value.setStatus("Active");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateContract(value));
    }

    @Test
    void createContractRequiresProjectId() {
        ServiceContract value = new ServiceContract();
        value.setAccountID(UUID.randomUUID());
        value.setContractNumber("C-1");
        value.setStatus("Active");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateContract(value));
    }

    @Test
    void createContractRequiresContractNumber() {
        ServiceContract value = new ServiceContract();
        value.setAccountID(UUID.randomUUID());
        value.setProjectID(UUID.randomUUID());
        value.setStatus("Active");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateContract(value));
    }

    @Test
    void createContractRequiresStatus() {
        ServiceContract value = new ServiceContract();
        value.setAccountID(UUID.randomUUID());
        value.setProjectID(UUID.randomUUID());
        value.setContractNumber("C-1");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateContract(value));
    }

    @Test
    void createContractIndexesImmediately() {
        ServiceContract value = serviceContract();
        when(repo.save(value)).thenReturn(value);

        ServiceContract saved = service.createOrUpdateContract(value);
        assertSame(value, saved);
        verify(lucene).indexServiceContract(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID4.toString()), eq("C-1"), eq("Active"), eq("2024-01-01"), eq("2024-12-31"));
    }

    @Test
    void createContractRegistersAfterCommit() {
        ServiceContract value = serviceContract();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateContract(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexServiceContract(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID4.toString()), eq("C-1"), eq("Active"), eq("2024-01-01"), eq("2024-12-31"));
    }

    @Test
    void createContractContinuesWhenLuceneFails() {
        ServiceContract value = serviceContract();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexServiceContract(any(), any(), any(), any(), any(), any(), any(), any());

        ServiceContract saved = service.createOrUpdateContract(value);
        assertSame(value, saved);
        verify(lucene).indexServiceContract(eq(UUID3.toString()), eq(UUID4.toString()), eq(UUID3.toString()), eq(UUID4.toString()), eq("C-1"), eq("Active"), eq("2024-01-01"), eq("2024-12-31"));
    }

    @Test
    void updateContractAppliesPatch() {
        ServiceContract existing = serviceContract();
        when(repo.findById(UUID3)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        ServiceContract patch = new ServiceContract();
        patch.setAccountID(UUID.randomUUID());
        patch.setProjectID(UUID.randomUUID());
        patch.setSiteID(UUID.randomUUID());
        patch.setContractNumber("C-2");
        patch.setStatus("Expired");
        patch.setStartDate(LocalDate.parse("2023-01-01"));
        patch.setEndDate(LocalDate.parse("2023-12-31"));

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<ServiceContract> updated = service.updateContract(UUID3, patch);
            assertTrue(updated.isPresent());
            assertEquals("C-2", existing.getContractNumber());
            assertEquals("Expired", existing.getStatus());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexServiceContract(eq(UUID3.toString()), eq(existing.getAccountID().toString()), eq(existing.getProjectID().toString()), eq(existing.getSiteID().toString()), eq("C-2"), eq("Expired"), eq("2023-01-01"), eq("2023-12-31"));
    }

    @Test
    void updateContractReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID3)).thenReturn(Optional.empty());
        assertTrue(service.updateContract(UUID3, serviceContract()).isEmpty());
    }

    @Test
    void deleteContractDeletesWhenPresent() {
        when(repo.findById(UUID3)).thenReturn(Optional.of(serviceContract()));
        service.deleteContract(UUID3);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID3);
        order.verify(repo).deleteById(UUID3);
    }
}
