package at.htlle.freq.infrastructure.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;

/**
 * Utility that translates user input into Lucene queries and offloads the
 * {@link at.htlle.freq.web.SearchController SearchController}.
 *
 * <p>Uses a {@link StandardAnalyzer} so parsing and indexing share the same tokenization. The
 * {@code SearchController} delegates free-text input to this component whenever
 * {@link #looksLikeLucene(String)} does not detect explicit Lucene syntax.</p>
 */
@Component
public class SmartQueryBuilder {

    /**
     * Analyzer that tokenizes query input exactly as during indexing to interpret searches
     * consistently.
     */
    private static final StandardAnalyzer ANALYZER = new StandardAnalyzer();

    /**
     * Default field used when free-text queries are projected onto the full-text index.
     */
    private static final String DEFAULT_FIELD = "content";

    /**
     * Heuristically checks whether the user string already represents a complete Lucene query.
     *
     * <p>The heuristics look for colons to denote field specifications, quotation marks for
     * phrases, logical operators ({@code AND}/{@code OR}), and trailing {@code *} characters for
     * prefix searches.</p>
     *
     * @param q raw user input.
     * @return {@code true} when the heuristics indicate Lucene syntax; otherwise {@code false}.
     */
    public static boolean looksLikeLucene(String q) {
        if (q == null) return false;
        String s = q.trim();
        return s.contains(":") || s.contains("\"") || s.contains(" AND ")
                || s.contains(" OR ") || s.endsWith("*");
    }

    /**
     * Builds a Lucene query from user-friendly text input for the
     * {@link at.htlle.freq.web.SearchController}, provided {@link #looksLikeLucene(String)} does
     * not already apply.
     *
     * <p>The input is projected onto the {@link #DEFAULT_FIELD} using the {@link #ANALYZER}, the
     * default operator is set to {@code AND}, and empty input results in a match-all query
     * ({@code *:*}).</p>
     *
     * @param userInput raw user input, typically sourced from the HTTP parameter {@code q}.
     * @return parsed Lucene query that can be forwarded to the {@code LuceneIndexService}.
     * @throws IllegalArgumentException if the input cannot be transformed into a valid Lucene
     *                                  query.
     */
    public Query build(String userInput) {
        try {
            QueryParser p = new QueryParser(DEFAULT_FIELD, ANALYZER);
            p.setDefaultOperator(QueryParser.Operator.AND);
            if (userInput == null || userInput.isBlank()) {
                return p.parse("*:*");
            }
            return p.parse(userInput.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid search query: " + userInput, e);
        }
    }
}
