package at.htlle.freq.web;

import at.htlle.freq.infrastructure.search.SearchHit;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import at.htlle.freq.infrastructure.search.SmartQueryBuilder;
import at.htlle.freq.infrastructure.search.SuggestService;
import org.apache.lucene.search.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for search and suggestion queries.
 *
 * <p>Uses {@link LuceneIndexService}, {@link SmartQueryBuilder}, and {@link SuggestService}.</p>
 */
@RestController
public class SearchController {

    private final LuceneIndexService lucene;
    private final SmartQueryBuilder smart;
    private final SuggestService suggest;

    public SearchController(LuceneIndexService lucene,
                            SmartQueryBuilder smart,
                            SuggestService suggest) {
        this.lucene = lucene;
        this.smart = smart;
        this.suggest = suggest;
    }

    /**
     * Executes a full-text search.
     *
     * <p>Path: {@code GET /search}</p>
     * <p>Query parameters: {@code q} (optional search term), {@code raw} (optional boolean).</p>
     *
     * @param q   search expression.
     * @param raw when {@code true}, interprets the expression as a Lucene query.
     * @return 200 OK with a list of {@link SearchHit search hits}.
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchHit>> query(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "raw", defaultValue = "false") boolean raw
    ) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        // Run the Lucene query verbatim when 'raw' is true or the input already uses Lucene syntax.
        if (raw || SmartQueryBuilder.looksLikeLucene(q)) {
            return ResponseEntity.ok(lucene.search(q));
        }

        // Otherwise build a Lucene query from the user-friendly input and use the Query overload.
        Query built = smart.build(q);
        return ResponseEntity.ok(lucene.search(built));
    }

    // Autocomplete/Suggest
    /**
     * Provides autocomplete suggestions.
     *
     * <p>Path: {@code GET /search/suggest}</p>
     * <p>Query parameters: {@code q} (required), {@code max} (optional, 1-25).</p>
     *
     * @param q   current input text.
     * @param max maximum number of results.
     * @return 200 OK with a list of suggestion strings.
     */
    @GetMapping("/search/suggest")
    public List<String> suggest(
            @RequestParam("q") String q,
            @RequestParam(name = "max", defaultValue = "8") int max
    ) {
        return suggest.suggest(q, Math.max(1, Math.min(max, 25)));
    }
}
