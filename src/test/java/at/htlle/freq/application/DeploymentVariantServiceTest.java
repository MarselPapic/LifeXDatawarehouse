package at.htlle.freq.application;

import at.htlle.freq.domain.DeploymentVariant;
import at.htlle.freq.domain.DeploymentVariantRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.deploymentVariant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentVariantServiceTest {

    private DeploymentVariantRepository repo;
    private LuceneIndexService lucene;
    private DeploymentVariantService service;

    @BeforeEach
    void setUp() {
        repo = mock(DeploymentVariantRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new DeploymentVariantService(repo, lucene);
    }

    @Test
    void getVariantByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getVariantById(null));
    }

    @Test
    void getVariantByCodeReturnsEmptyForBlank() {
        assertTrue(service.getVariantByCode("   ").isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void getVariantByNameReturnsEmptyForBlank() {
        assertTrue(service.getVariantByName("" ).isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void createVariantRequiresCode() {
        DeploymentVariant value = new DeploymentVariant();
        value.setVariantName("Variant");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateVariant(value));
    }

    @Test
    void createVariantRequiresName() {
        DeploymentVariant value = new DeploymentVariant();
        value.setVariantCode("CODE");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateVariant(value));
    }

    @Test
    void createVariantIndexesImmediately() {
        DeploymentVariant value = deploymentVariant();
        when(repo.save(value)).thenReturn(value);

        DeploymentVariant saved = service.createOrUpdateVariant(value);
        assertSame(value, saved);
        verify(lucene).indexDeploymentVariant(eq(UUID2.toString()), eq("CODE"), eq("Variant"), eq("Description"), eq(true));
    }

    @Test
    void createVariantRegistersAfterCommit() {
        DeploymentVariant value = deploymentVariant();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateVariant(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexDeploymentVariant(eq(UUID2.toString()), eq("CODE"), eq("Variant"), eq("Description"), eq(true));
    }

    @Test
    void createVariantContinuesWhenLuceneFails() {
        DeploymentVariant value = deploymentVariant();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexDeploymentVariant(any(), any(), any(), any(), anyBoolean());

        DeploymentVariant saved = service.createOrUpdateVariant(value);
        assertSame(value, saved);
        verify(lucene).indexDeploymentVariant(eq(UUID2.toString()), eq("CODE"), eq("Variant"), eq("Description"), eq(true));
    }

    @Test
    void createVariantDefaultsInactiveWhenNull() {
        DeploymentVariant value = new DeploymentVariant();
        value.setVariantCode("CODE");
        value.setVariantName("Variant");
        when(repo.save(any(DeploymentVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        DeploymentVariant saved = service.createOrUpdateVariant(value);

        assertSame(value, saved);
        assertEquals(Boolean.FALSE, value.getActive());
        verify(repo).save(argThat(v -> Boolean.FALSE.equals(v.getActive())));
        verify(lucene).indexDeploymentVariant(isNull(), eq("CODE"), eq("Variant"), isNull(), eq(false));
    }

    @Test
    void updateVariantAppliesPatch() {
        DeploymentVariant existing = deploymentVariant();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        DeploymentVariant patch = new DeploymentVariant();
        patch.setVariantCode("NEW");
        patch.setVariantName("New Variant");
        patch.setDescription("New Description");
        patch.setActive(Boolean.FALSE);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<DeploymentVariant> updated = service.updateVariant(UUID2, patch);
            assertTrue(updated.isPresent());
            assertEquals("NEW", existing.getVariantCode());
            assertEquals("New Variant", existing.getVariantName());
            assertFalse(existing.isActive());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexDeploymentVariant(eq(UUID2.toString()), eq("NEW"), eq("New Variant"), eq("New Description"), eq(false));
    }

    @Test
    void updateVariantKeepsActiveWhenPatchOmitted() {
        DeploymentVariant existing = deploymentVariant();
        when(repo.findById(UUID2)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        DeploymentVariant patch = new DeploymentVariant();
        patch.setVariantName("Updated Variant");
        patch.setDescription("Updated Description");

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<DeploymentVariant> updated = service.updateVariant(UUID2, patch);
            assertTrue(updated.isPresent());
            assertTrue(existing.isActive());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexDeploymentVariant(eq(UUID2.toString()), eq(existing.getVariantCode()), eq("Updated Variant"), eq("Updated Description"), eq(true));
    }

    @Test
    void updateVariantReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID2)).thenReturn(Optional.empty());
        assertTrue(service.updateVariant(UUID2, deploymentVariant()).isEmpty());
    }

    @Test
    void deleteVariantDeletesWhenPresent() {
        when(repo.findById(UUID2)).thenReturn(Optional.of(deploymentVariant()));
        service.deleteVariant(UUID2);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID2);
        order.verify(repo).deleteById(UUID2);
    }
}
