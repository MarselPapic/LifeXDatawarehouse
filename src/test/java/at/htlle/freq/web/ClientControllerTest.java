package at.htlle.freq.web;

import at.htlle.freq.application.ClientsService;
import at.htlle.freq.domain.Clients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClientControllerTest {

    private ClientsService service;
    private ClientController controller;

    @BeforeEach
    void setUp() {
        service = mock(ClientsService.class);
        controller = new ClientController(service);
    }

    @Test
    void findBySiteReturnsAllWhenSiteIdMissing() {
        List<Clients> expected = List.of(new Clients());
        when(service.findAll()).thenReturn(expected);

        List<Clients> result = controller.findBySite(null);

        assertEquals(expected, result);
        verify(service).findAll();
        verify(service, never()).findBySite(any());
    }

    @Test
    void findBySiteFiltersWhenSiteIdProvided() {
        UUID siteId = UUID.randomUUID();
        List<Clients> expected = List.of(new Clients());
        when(service.findBySite(siteId)).thenReturn(expected);

        List<Clients> result = controller.findBySite(siteId);

        assertEquals(expected, result);
        verify(service).findBySite(siteId);
        verify(service, never()).findAll();
    }
}
