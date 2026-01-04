package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.infrastructure.search.SearchHit;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import at.htlle.freq.domain.InstalledSoftware;
import at.htlle.freq.domain.InstalledSoftwareRepository;
import at.htlle.freq.domain.InstalledSoftwareStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Integration-like tests for {@link LuceneIndexServiceImpl} that operate on a temporary
 * filesystem index to verify indexing and search behaviour without mocking Lucene classes.
 */
class LuceneIndexServiceImplTest {

    private LuceneIndexServiceImpl service;
    private Path indexPath;

    @BeforeEach
    void setUp() throws IOException {
        indexPath = Path.of("target", "test-index", UUID.randomUUID().toString());
        service = new LuceneIndexServiceImpl();
        service.setIndexPath(indexPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexPath != null && Files.exists(indexPath)) {
            Files.walk(indexPath)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Test
    void indexAndSearchRoundTrip() throws Exception {
        // Arrange: index both an account and a project so the search has relevant content
        service.indexAccount("acc-1", "Acme", "Austria", "contact@acme.test");
        service.indexProject("proj-1", "SAP-1", "HQ", null, null, "ACTIVE", null, null, null);

        // Act: execute a Lucene query that should match the stored account document
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));
        // Assert: confirm the query returns the account with the expected metadata
        assertEquals(1, hits.size());
        assertEquals("acc-1", hits.get(0).getId());
        assertEquals("account", hits.get(0).getType());
    }

    @Test
    void searchStringHandlesParseErrorsGracefully() {
        List<SearchHit> hits = service.search("\\");
        assertTrue(hits.isEmpty());
    }

    @Test
    void reindexAllClearsDocuments() throws Exception {
        // Arrange: persist an account so the subsequent query yields a hit
        service.indexAccount("acc-2", "Test", null, null);
        Query query = new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer()).parse("test");
        assertFalse(service.search(query).isEmpty());

        // Act: request a full reindex, which should clear previously indexed documents
        service.reindexAll();
        // Assert: verify the earlier query now returns no search hits
        assertTrue(service.search(query).isEmpty());
    }

    @Test
    void deleteDocumentRemovesEntriesFromIndex() throws Exception {
        service.indexAccount("acc-del", "Delete Me", null, null);
        Query query = new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer()).parse("delete");
        assertFalse(service.search(query).isEmpty());

        service.deleteDocument("acc-del");

        assertTrue(service.search(query).isEmpty());
    }

    @Test
    void safeHandlesNullValuesWhenIndexing() throws Exception {
        // Arrange: index an account where all optional fields are null
        service.indexAccount("acc-3", null, null, null);
        // Act: perform a broad match-all search against the index
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));
        // Assert: ensure the snippet collapses to an empty string instead of "null"
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getSnippet());
    }

    @Test
    void snippetIsTrimmedAndNormalized() throws Exception {
        // Arrange: index an account with a long snippet to test trimming behaviour
        String longValue = ("Austria   with   spaces   ").repeat(20);
        service.indexAccount("acc-4", "Acme", longValue, null);

        // Act: search for the account using a keyword from the document text
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));

        // Assert: verify the snippet is normalized, deduplicated, and appropriately truncated
        assertEquals(1, hits.size());
        String snippet = hits.get(0).getSnippet();
        assertNotNull(snippet);
        assertFalse(snippet.contains("  "));
        assertTrue(snippet.endsWith("…"));
        assertTrue(snippet.length() <= 160);
    }

    @Test
    void textFallsBackToTypeAndIdWhenFieldsAreBlank() throws Exception {
        // Arrange: index an account where both the text and snippet fields are blank
        service.indexAccount("acc-blank", "", null, null);

        // Act: issue a match-all search to load the indexed account
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));

        // Assert: confirm the result falls back to the type and identifier as the display text
        assertEquals(1, hits.size());
        assertEquals("account acc-blank", hits.get(0).getText());
        assertEquals("", hits.get(0).getSnippet());
    }

    @Test
    void reindexAllPassesAllInstalledSoftwareDatesToIndexing() throws Exception {
        InstalledSoftwareRepository repository = mock(InstalledSoftwareRepository.class);
        UUID installedId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID softwareId = UUID.randomUUID();
        String offered = "2024-01-01";
        String installed = "2024-02-02";
        String rejected = "2024-03-03";
        String outdated = "2024-04-04";

        InstalledSoftware record = new InstalledSoftware(
                installedId,
                siteId,
                softwareId,
                InstalledSoftwareStatus.INSTALLED.dbValue(),
                offered,
                installed,
                rejected,
                outdated
        );
        Mockito.when(repository.findAll()).thenReturn(List.of(record));

        LuceneIndexServiceImpl serviceWithRepository = new LuceneIndexServiceImpl(
                null, null, null, null, null, null, null, repository,
                null, null, null, null, null, null, null, null
        );
        serviceWithRepository.setIndexPath(Path.of("target", "test-index", UUID.randomUUID().toString()));

        LuceneIndexServiceImpl spyService = spy(serviceWithRepository);
        doNothing().when(spyService).clearIndex();
        doNothing().when(spyService).indexInstalledSoftware(any(), any(), any(), any(), any(), any(), any(), any());

        spyService.reindexAll();

        verify(spyService).indexInstalledSoftware(
                installedId.toString(),
                siteId.toString(),
                softwareId.toString(),
                InstalledSoftwareStatus.INSTALLED.dbValue(),
                offered,
                installed,
                rejected,
                outdated
        );
    }
}
