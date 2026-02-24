package at.htlle.freq.web;

import at.htlle.freq.domain.ArchiveState;
import at.htlle.freq.infrastructure.search.SearchHit;
import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import at.htlle.freq.infrastructure.search.SmartQueryBuilder;
import at.htlle.freq.infrastructure.search.SuggestService;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller for search and suggestion queries.
 *
 * <p>Uses {@link LuceneIndexService}, {@link SmartQueryBuilder}, and {@link SuggestService}.</p>
 */
@RestController
public class SearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);

    private final LuceneIndexService lucene;
    private final SmartQueryBuilder smart;
    private final SuggestService suggest;

    /**
     * Creates a controller that delegates search and suggestion operations to Lucene services.
     *
     * @param lucene index service used for query execution.
     * @param smart query builder for user-friendly search input.
     * @param suggest suggestion service for autocomplete results.
     */
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
    public ResponseEntity<?> query(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "archiveState", required = false) String archiveStateRaw,
            @RequestParam(name = "raw", defaultValue = "false") boolean raw
    ) {
        String normalizedType = normalizeType(type);
        ArchiveState archiveState = parseArchiveState(archiveStateRaw);
        boolean hasQuery = q != null && !q.isBlank();
        if (!hasQuery && normalizedType == null) {
            return ResponseEntity.ok(List.of());
        }

        try {
            // Run the Lucene query verbatim when 'raw' is true or the input already uses Lucene syntax.
            if (raw || SmartQueryBuilder.looksLikeLucene(q)) {
                String luceneQuery = appendTypeFilter(q, normalizedType);
                luceneQuery = appendArchiveFilter(luceneQuery, archiveState);
                return ResponseEntity.ok(lucene.search(luceneQuery));
            }

            // Otherwise build a Lucene query from the user-friendly input and use the Query overload.
            Query built = smart.build(q, normalizedType, archiveState);
            return ResponseEntity.ok(lucene.search(built));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Failed to execute search for query='{}', type='{}', archiveState='{}', raw={}",
                    q, normalizedType, archiveState, raw, ex);
            String message = ex.getMessage() != null ? ex.getMessage() : "Invalid search query";
            return ResponseEntity.badRequest().body(message);
        }
    }

    /**
     * Normalizes a type filter to lower-case or returns null when empty.
     *
     * @param type raw type filter value.
     * @return normalized type or null when absent.
     */
    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase();
    }

    /**
     * Appends a type filter to the query string when provided.
     *
     * @param query original query string.
     * @param normalizedType normalized type filter.
     * @return query string with an appended type clause.
     */
    private static String appendTypeFilter(String query, String normalizedType) {
        if (normalizedType == null) {
            return query;
        }
        String typeClause = "type:" + normalizedType;
        if (query == null || query.isBlank()) {
            return typeClause;
        }
        return typeClause + " AND (" + query.trim() + ")";
    }

    private static String appendArchiveFilter(String query, ArchiveState archiveState) {
        if (archiveState == ArchiveState.ALL) {
            return query;
        }
        String clause = archiveState == ArchiveState.ARCHIVED ? "archived:true" : "archived:false";
        if (query == null || query.isBlank()) {
            return clause;
        }
        return clause + " AND (" + query.trim() + ")";
    }

    private static ArchiveState parseArchiveState(String raw) {
        try {
            return ArchiveState.from(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public ResponseEntity<?> query(String q, String type, boolean raw) {
        String normalizedType = normalizeType(type);
        boolean hasQuery = q != null && !q.isBlank();
        if (!hasQuery && normalizedType == null) {
            return ResponseEntity.ok(List.of());
        }
        try {
            if (raw || SmartQueryBuilder.looksLikeLucene(q)) {
                String luceneQuery = appendTypeFilter(q, normalizedType);
                return ResponseEntity.ok(lucene.search(luceneQuery));
            }
            Query built = smart.build(q, normalizedType);
            return ResponseEntity.ok(lucene.search(built));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Failed to execute search for query='{}', type='{}', raw={}", q, normalizedType, raw, ex);
            String message = ex.getMessage() != null ? ex.getMessage() : "Invalid search query";
            return ResponseEntity.badRequest().body(message);
        }
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
            @RequestParam(name = "archiveState", required = false) String archiveStateRaw,
            @RequestParam(name = "max", defaultValue = "8") int max
    ) {
        return suggest.suggest(q, Math.max(1, Math.min(max, 25)), parseArchiveState(archiveStateRaw));
    }

    /**
     * Backwards-compatible overload without archive-state parameter.
     */
    public List<String> suggest(String q, int max) {
        return suggest.suggest(q, Math.max(1, Math.min(max, 25)));
    }
}
