package at.htlle.freq.web;

import at.htlle.freq.application.AccountService;
import at.htlle.freq.domain.Account;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountControllerTest {

    private AccountService service;
    private AuditLogger audit;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        audit = mock(AuditLogger.class);
        controller = new AccountController(service, audit);
    }

    @Test
    void findAllDelegatesToService() {
        List<Account> accounts = List.of(new Account());
        when(service.getAllAccounts()).thenReturn(accounts);
        assertEquals(accounts, controller.findAll());
    }

    @Test
    void findByIdReturns404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(service.getAccountById(id)).thenReturn(Optional.empty());
        ResponseEntity<Account> response = controller.findById(id);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void findByIdReturnsAccount() {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setAccountID(id);
        when(service.getAccountById(id)).thenReturn(Optional.of(account));

        ResponseEntity<Account> response = controller.findById(id);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(account, response.getBody());
    }

    @Test
    void createReturns201WithLocationHeader() {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setAccountID(id);

        when(service.createAccount(account)).thenReturn(account);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/accounts");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ResponseEntity<Account> response = controller.create(account);
        RequestContextHolder.resetRequestAttributes();

        assertEquals(201, response.getStatusCode().value());
        assertEquals(account, response.getBody());
        assertEquals("http://localhost/accounts/" + id, response.getHeaders().getLocation().toString());
    }
}
