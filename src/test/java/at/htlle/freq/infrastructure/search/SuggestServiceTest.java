package at.htlle.freq.infrastructure.search;

import at.htlle.freq.infrastructure.lucene.LuceneIndexServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SuggestServiceTest {

    private LuceneIndexServiceImpl lucene;
    private SuggestService service;
    private Path indexPath;

    @BeforeEach
    void setUp() throws IOException {
        indexPath = Paths.get("target", "test-index", UUID.randomUUID().toString());
        lucene = new LuceneIndexServiceImpl();
        lucene.setIndexPath(indexPath);
        service = new SuggestService(lucene);
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
    void suggestReturnsTermsFromIndex() {
        lucene.indexAccount("acc-5", "Acme", "Austria", "contact@acme.test");
        lucene.indexServer("srv-1", null, null, "Dell", null, null, null, null, null);

        List<String> suggestions = service.suggest("ac", 5);
        assertTrue(suggestions.stream().anyMatch(s -> s.equalsIgnoreCase("acme")));
    }

    @Test
    void suggestHonoursPrefixLengthAndMax() {
        lucene.indexAccount("acc-6", "FooBar", null, null);
        assertTrue(service.suggest("f", 5).isEmpty(), "prefix shorter than 2 characters should yield no results");
        assertTrue(service.suggest(null, 5).isEmpty());
        assertTrue(service.suggest("fo", 0).isEmpty());

        lucene.indexAccount("acc-7", "FooBaz", null, null);
        List<String> suggestions = service.suggest("fo", 1);
        assertEquals(1, suggestions.size());
    }

    @Test
    void suggestIsCaseInsensitiveAndUnique() {
        lucene.indexAccount("acc-8", "Alpha", null, null);
        lucene.indexAccount("acc-9", "alpha", null, null);

        List<String> suggestions = service.suggest("al", 5);
        assertEquals(1, suggestions.size());
        assertEquals("alpha", suggestions.get(0));
    }

    @Test
    void suggestIsIndependentOfDefaultLocale() {
        lucene.indexAccount("acc-10", "Indigo", null, null);

        Locale previous = Locale.getDefault();
        Locale problematic = new Locale("tr");
        Locale.setDefault(problematic);
        try {
            List<String> suggestions = service.suggest("in", 5);
            assertFalse(suggestions.isEmpty(), "suggestions should still be returned under Turkish locale");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
