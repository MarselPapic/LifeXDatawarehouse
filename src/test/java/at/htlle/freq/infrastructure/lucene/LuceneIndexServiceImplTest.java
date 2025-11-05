package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.infrastructure.search.SearchHit;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
        // Arrange: index an account and project document
        service.indexAccount("acc-1", "Acme", "Austria", "contact@acme.test");
        service.indexProject("proj-1", "SAP-1", "HQ", null, null, "ACTIVE", null, null);

        // Act: search for the indexed account
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));
        // Assert
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
        // Arrange
        service.indexAccount("acc-2", "Test", null, null);
        Query query = new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer()).parse("test");
        assertFalse(service.search(query).isEmpty());

        // Act
        service.reindexAll();
        // Assert
        assertTrue(service.search(query).isEmpty());
    }

    @Test
    void safeHandlesNullValuesWhenIndexing() throws Exception {
        // Arrange
        service.indexAccount("acc-3", null, null, null);
        // Act
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));
        // Assert
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getSnippet());
    }

    @Test
    void snippetIsTrimmedAndNormalized() throws Exception {
        // Arrange
        String longValue = ("Austria   with   spaces   ").repeat(20);
        service.indexAccount("acc-4", "Acme", longValue, null);

        // Act
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));

        // Assert
        assertEquals(1, hits.size());
        String snippet = hits.get(0).getSnippet();
        assertNotNull(snippet);
        assertFalse(snippet.contains("  "));
        assertTrue(snippet.endsWith("…"));
        assertTrue(snippet.length() <= 160);
    }

    @Test
    void textFallsBackToTypeAndIdWhenFieldsAreBlank() throws Exception {
        // Arrange
        service.indexAccount("acc-blank", "", null, null);

        // Act
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));

        // Assert
        assertEquals(1, hits.size());
        assertEquals("account acc-blank", hits.get(0).getText());
        assertEquals("", hits.get(0).getSnippet());
    }
}
