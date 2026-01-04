package at.htlle.freq.web;

import at.htlle.freq.application.DeploymentVariantService;
import at.htlle.freq.domain.DeploymentVariant;
import at.htlle.freq.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentVariantControllerTest {

    private DeploymentVariantService service;
    private AuditLogger audit;
    private DeploymentVariantController controller;

    @BeforeEach
    void setUp() {
        service = mock(DeploymentVariantService.class);
        audit = mock(AuditLogger.class);
        controller = new DeploymentVariantController(service, audit);
    }

    @Test
    void listReturnsAllVariants() {
        List<DeploymentVariant> variants = List.of(new DeploymentVariant());
        when(service.getAllVariants()).thenReturn(variants);

        assertEquals(variants, controller.list());
    }

    @Test
    void byIdThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(service.getVariantById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.byId(id));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createPropagatesBadRequestMessage() {
        DeploymentVariant variant = new DeploymentVariant();
        when(service.createOrUpdateVariant(any())).thenThrow(new IllegalArgumentException("invalid data"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(variant));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("invalid data"));
    }
}
