package at.htlle.freq.application;

import at.htlle.freq.domain.Account;
import at.htlle.freq.domain.AccountRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private AccountRepository repo;
    private LuceneIndexService lucene;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repo = mock(AccountRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new AccountService(repo, lucene);
    }

    @Test
    void getAccountByNameValidatesInput() {
        assertTrue(service.getAccountByName(null).isEmpty());
        assertTrue(service.getAccountByName("   ").isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void getAccountByNameTrimsInputBeforeQuery() {
        service.getAccountByName("  Acme  ");
        verify(repo).findByName("Acme");
    }

    @Test
    void createAccountValidatesAndIndexesImmediatelyWithoutTransaction() {
        Account incoming = new Account(null, "Acme", "", "mail", "", "", "");
        when(repo.save(any(Account.class))).thenAnswer(invocation -> {
            Account stored = invocation.getArgument(0);
            stored.setAccountID(UUID.randomUUID());
            return stored;
        });

        Account saved = service.createAccount(incoming);
        assertNotNull(saved.getAccountID());
        verify(lucene).indexAccount(anyString(), eq("Acme"), anyString(), anyString());
    }

    @Test
    void createAccountContinuesWhenLuceneFails() {
        Account incoming = new Account(UUID.randomUUID(), "Acme", "", "mail", "", "", "");
        when(repo.save(incoming)).thenReturn(incoming);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexAccount(any(), any(), any(), any());

        Account saved = service.createAccount(incoming);
        assertSame(incoming, saved);
        verify(lucene).indexAccount(eq(incoming.getAccountID().toString()), eq("Acme"), anyString(), anyString());
    }

    @Test
    void createAccountRegistersAfterCommitWhenTransactionActive() {
        Account incoming = new Account(UUID.randomUUID(), "Acme", "", "mail", "", "", "");
        when(repo.save(incoming)).thenReturn(incoming);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createAccount(incoming);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(lucene).indexAccount(eq(incoming.getAccountID().toString()), eq("Acme"), anyString(), anyString());
    }

    @Test
    void updateAccountReplacesFields() {
        UUID id = UUID.randomUUID();
        Account existing = new Account(id, "Old", "Contact", "old@mail", "123", "vat", "AT");
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Account patch = new Account(null, "New", null, "new@mail", null, null, null);
        Optional<Account> result = service.updateAccount(id, patch);
        assertTrue(result.isPresent());
        assertEquals("New", existing.getAccountName());
        assertEquals("new@mail", existing.getContactEmail());
        verify(lucene).indexAccount(eq(id.toString()), eq("New"), eq("AT"), eq("new@mail"));
    }

    @Test
    void updateAccountReturnsEmptyWhenUnknown() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.updateAccount(id, new Account()).isEmpty());
    }

    @Test
    void updateAccountRequiresArguments() {
        Account patch = new Account();
        assertThrows(NullPointerException.class, () -> service.updateAccount(null, patch));
        assertThrows(NullPointerException.class, () -> service.updateAccount(UUID.randomUUID(), null));
    }

    @Test
    void deleteAccountDelegatesToRepository() {
        UUID id = UUID.randomUUID();
        service.deleteAccount(id);
        verify(repo).deleteById(id);
    }

    @Test
    void getAccountByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getAccountById(null));
    }

    @Test
    void createAccountRequiresName() {
        Account invalid = new Account();
        assertThrows(IllegalArgumentException.class, () -> service.createAccount(invalid));
    }
}
