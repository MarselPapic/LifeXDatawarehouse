package at.htlle.freq.application;

import at.htlle.freq.domain.Radio;
import at.htlle.freq.domain.RadioRepository;
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
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.radio;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RadioServiceTest {

    private RadioRepository repo;
    private LuceneIndexService lucene;
    private RadioService service;

    @BeforeEach
    void setUp() {
        repo = mock(RadioRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new RadioService(repo, lucene);
    }

    @Test
    void getRadioByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getRadioById(null));
    }

    @Test
    void getRadiosBySiteRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getRadiosBySite(null));
    }

    @Test
    void createRadioRequiresSiteId() {
        Radio value = new Radio();
        value.setRadioBrand("Brand");
        value.setRadioSerialNr("Serial");
        value.setMode("MODE");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateRadio(value));
    }

    @Test
    void createRadioRequiresBrand() {
        Radio value = new Radio();
        value.setSiteID(UUID.randomUUID());
        value.setRadioSerialNr("Serial");
        value.setMode("MODE");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateRadio(value));
    }

    @Test
    void createRadioRequiresSerial() {
        Radio value = new Radio();
        value.setSiteID(UUID.randomUUID());
        value.setRadioBrand("Brand");
        value.setMode("MODE");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateRadio(value));
    }

    @Test
    void createRadioRequiresMode() {
        Radio value = new Radio();
        value.setSiteID(UUID.randomUUID());
        value.setRadioBrand("Brand");
        value.setRadioSerialNr("Serial");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateRadio(value));
    }

    @Test
    void createRadioIndexesImmediately() {
        Radio value = radio();
        when(repo.save(value)).thenReturn(value);

        Radio saved = service.createOrUpdateRadio(value);
        assertSame(value, saved);
        verify(lucene).indexRadio(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID1.toString()), eq("Brand"), eq("SERIAL"), eq("MODE"), eq("STANDARD"));
    }

    @Test
    void createRadioRegistersAfterCommit() {
        Radio value = radio();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateRadio(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexRadio(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID1.toString()), eq("Brand"), eq("SERIAL"), eq("MODE"), eq("STANDARD"));
    }

    @Test
    void createRadioContinuesWhenLuceneFails() {
        Radio value = radio();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexRadio(any(), any(), any(), any(), any(), any(), any());

        Radio saved = service.createOrUpdateRadio(value);
        assertSame(value, saved);
        verify(lucene).indexRadio(eq(UUID2.toString()), eq(UUID4.toString()), eq(UUID1.toString()), eq("Brand"), eq("SERIAL"), eq("MODE"), eq("STANDARD"));
    }

    @Test
    void updateRadioAppliesPatch() {
        Radio existing = radio();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Radio patch = new Radio();
        patch.setSiteID(UUID.randomUUID());
        patch.setAssignedClientID(UUID.randomUUID());
        patch.setRadioBrand("NewBrand");
        patch.setRadioSerialNr("NewSerial");
        patch.setMode("NewMode");
        patch.setDigitalStandard("NewStandard");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Radio> updated = service.updateRadio(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals("NewBrand", existing.getRadioBrand());
            assertEquals("NewSerial", existing.getRadioSerialNr());
            assertEquals("NewMode", existing.getMode());
            assertEquals("NewStandard", existing.getDigitalStandard());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexRadio(eq(UUID2.toString()), eq(existing.getSiteID().toString()), eq(existing.getAssignedClientID().toString()), eq("NewBrand"), eq("NewSerial"), eq("NewMode"), eq("NewStandard"));
    }

    @Test
    void updateRadioReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updateRadio(UUID2, radio()).isEmpty());
    }

    @Test
    void deleteRadioDeletesWhenPresent() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(radio()));
        service.deleteRadio(UUID2);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID2);
        order.verify(repo).deleteById(UUID2);
    }
}
