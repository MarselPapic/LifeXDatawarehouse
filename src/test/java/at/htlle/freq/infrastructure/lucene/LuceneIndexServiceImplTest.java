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
        service.indexAccount("acc-1", "Acme", "Austria", "contact@acme.test");
        service.indexProject("proj-1", "SAP-1", "HQ", null, null, "ACTIVE", null, null);

        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));
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
        service.indexAccount("acc-2", "Test", null, null);
        Query query = new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer()).parse("test");
        assertFalse(service.search(query).isEmpty());

        service.reindexAll();
        assertTrue(service.search(query).isEmpty());
    }

    @Test
    void safeHandlesNullValuesWhenIndexing() throws Exception {
        service.indexAccount("acc-3", null, null, null);
        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));
        assertEquals(1, hits.size());
        assertEquals("", hits.get(0).getSnippet());
    }

    @Test
    void snippetIsTrimmedAndNormalized() throws Exception {
        String longValue = ("Austria   with   spaces   ").repeat(20);
        service.indexAccount("acc-4", "Acme", longValue, null);

        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("acme"));

        assertEquals(1, hits.size());
        String snippet = hits.get(0).getSnippet();
        assertNotNull(snippet);
        assertFalse(snippet.contains("  "));
        assertTrue(snippet.endsWith("…"));
        assertTrue(snippet.length() <= 160);
    }

    @Test
    void textFallsBackToTypeAndIdWhenFieldsAreBlank() throws Exception {
        service.indexAccount("acc-blank", "", null, null);

        List<SearchHit> hits = service.search(new QueryParser("content", new org.apache.lucene.analysis.standard.StandardAnalyzer())
                .parse("*:*"));

        assertEquals(1, hits.size());
        assertEquals("account acc-blank", hits.get(0).getText());
        assertEquals("", hits.get(0).getSnippet());
    }
}
