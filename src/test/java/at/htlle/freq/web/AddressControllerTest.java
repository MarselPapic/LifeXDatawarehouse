package at.htlle.freq.web;

import at.htlle.freq.application.AddressService;
import at.htlle.freq.domain.Address;
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

class AddressControllerTest {

    private AddressService service;
    private AddressController controller;

    @BeforeEach
    void setUp() {
        service = mock(AddressService.class);
        controller = new AddressController(service);
    }

    @Test
    void listDelegatesToService() {
        List<Address> addresses = List.of(new Address());
        when(service.getAllAddresses()).thenReturn(addresses);

        assertEquals(addresses, controller.list());
    }

    @Test
    void byIdReturns404ForMissing() {
        UUID id = UUID.randomUUID();
        when(service.getAddressById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.byId(id));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createSurfacesBadRequest() {
        when(service.createAddress(any())).thenThrow(new IllegalArgumentException("invalid"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.create(new Address()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateReturns404WhenServiceEmpty() {
        UUID id = UUID.randomUUID();
        when(service.updateAddress(eq(id), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.update(id, new Address()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
