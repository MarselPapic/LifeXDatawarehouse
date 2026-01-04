package at.htlle.freq.application;

import at.htlle.freq.domain.Project;
import at.htlle.freq.domain.ProjectLifecycleStatus;
import at.htlle.freq.domain.ProjectRepository;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.htlle.freq.application.TestFixtures.UUID2;
import static at.htlle.freq.application.TestFixtures.UUID3;
import static at.htlle.freq.application.TestFixtures.UUID4;
import static at.htlle.freq.application.TestFixtures.UUID5;
import static at.htlle.freq.application.TestFixtures.project;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectServiceTest {

    private ProjectRepository repo;
    private LuceneIndexService lucene;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProjectRepository.class);
        lucene = mock(LuceneIndexService.class);
        service = new ProjectService(repo, lucene);
        when(repo.findBySapId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void getProjectByIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> service.getProjectById(null));
    }

    @Test
    void getProjectBySapIdReturnsEmptyForBlank() {
        assertTrue(service.getProjectBySapId("   ").isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void createProjectRequiresName() {
        Project value = new Project();
        value.setProjectSAPID("SAP");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateProject(value));
    }

    @Test
    void createProjectRequiresSapId() {
        Project value = new Project();
        value.setProjectName("Project");
        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateProject(value));
    }

    @Test
    void createProjectIndexesImmediately() {
        Project value = project();
        when(repo.save(value)).thenReturn(value);

        Project saved = service.createOrUpdateProject(value);
        assertSame(value, saved);
        verify(lucene).indexProject(eq(UUID3.toString()), eq("SAP-1"), eq("Project"), eq(UUID2.toString()), eq("Bundle"), eq("ACTIVE"), eq(UUID4.toString()), eq(UUID5.toString()), eq("Requires on-site coordination"));
    }

    @Test
    void createProjectSetsCreationDateWhenMissing() {
        Project value = project();
        value.setCreateDateTime("   ");
        when(repo.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String today = LocalDate.now().toString();

        Project saved = service.createOrUpdateProject(value);
        assertEquals(today, saved.getCreateDateTime());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(repo).save(captor.capture());
        assertEquals(today, captor.getValue().getCreateDateTime());
    }

    @Test
    void createProjectRejectsDuplicateSapId() {
        Project value = project();
        value.setProjectID(null);
        when(repo.findBySapId("SAP-1")).thenReturn(Optional.of(project()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateProject(value));
        assertTrue(ex.getMessage().contains("ProjectSAPID already exists"));
        verify(repo, never()).save(any());
    }

    @Test
    void createProjectRegistersAfterCommit() {
        Project value = project();
        when(repo.save(value)).thenReturn(value);

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> service.createOrUpdateProject(value));
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexProject(eq(UUID3.toString()), eq("SAP-1"), eq("Project"), eq(UUID2.toString()), eq("Bundle"), eq("ACTIVE"), eq(UUID4.toString()), eq(UUID5.toString()), eq("Requires on-site coordination"));
    }

    @Test
    void createProjectContinuesWhenLuceneFails() {
        Project value = project();
        when(repo.save(value)).thenReturn(value);
        doThrow(new RuntimeException("Lucene error")).when(lucene).indexProject(any(), any(), any(), any(), any(), any(), any(), any(), any());

        Project saved = service.createOrUpdateProject(value);
        assertSame(value, saved);
        verify(lucene).indexProject(eq(UUID3.toString()), eq("SAP-1"), eq("Project"), eq(UUID2.toString()), eq("Bundle"), eq("ACTIVE"), eq(UUID4.toString()), eq(UUID5.toString()), eq("Requires on-site coordination"));
    }

    @Test
    void updateProjectAppliesPatch() {
        Project existing = project();
        when(repo.findById(UUID3)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        Project patch = new Project();
        patch.setProjectSAPID("SAP-NEW");
        patch.setProjectName("New Project");
        patch.setDeploymentVariantID(UUID.randomUUID());
        patch.setBundleType("New Bundle");
        patch.setCreateDateTime("2024-02-01");
        patch.setLifecycleStatus(ProjectLifecycleStatus.EOL);
        patch.setAccountID(UUID.randomUUID());
        patch.setAddressID(UUID.randomUUID());

        List<TransactionSynchronization> synchronizations = TransactionTestUtils.executeWithinTransaction(() -> {
            Optional<Project> updated = service.updateProject(UUID3, patch);
            assertTrue(updated.isPresent());
            assertEquals("SAP-NEW", existing.getProjectSAPID());
            assertEquals("New Project", existing.getProjectName());
            assertEquals(ProjectLifecycleStatus.EOL, existing.getLifecycleStatus());
        });
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(lucene).indexProject(eq(UUID3.toString()), eq("SAP-NEW"), eq("New Project"), eq(existing.getDeploymentVariantID().toString()), eq("New Bundle"), eq("EOL"), eq(existing.getAccountID().toString()), eq(existing.getAddressID().toString()), eq(existing.getSpecialNotes()));
    }

    @Test
    void updateProjectRejectsDuplicateSapId() {
        Project existing = project();
        when(repo.findById(UUID3)).thenReturn(Optional.of(existing));

        Project duplicate = project();
        duplicate.setProjectID(UUID.randomUUID());
        when(repo.findBySapId("SAP-NEW")).thenReturn(Optional.of(duplicate));

        Project patch = new Project();
        patch.setProjectSAPID("SAP-NEW");

        assertThrows(IllegalArgumentException.class, () -> service.updateProject(UUID3, patch));
        verify(repo, never()).save(any());
    }

    @Test
    void updateProjectReturnsEmptyWhenUnknown() {
        when(repo.findById(UUID3)).thenReturn(Optional.empty());
        assertTrue(service.updateProject(UUID3, project()).isEmpty());
    }

    @Test
    void deleteProjectDeletesWhenPresent() {
        when(repo.findById(UUID3)).thenReturn(Optional.of(project()));
        service.deleteProject(UUID3);
        InOrder order = inOrder(repo);
        order.verify(repo).findById(UUID3);
        order.verify(repo).deleteById(UUID3);
    }
}
