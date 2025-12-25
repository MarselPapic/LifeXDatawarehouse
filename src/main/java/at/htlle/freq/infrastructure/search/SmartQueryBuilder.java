package at.htlle.freq.infrastructure.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
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
        if (s.isEmpty()) {
            return false;
        }

        String normalized = s.toLowerCase();
        return s.contains(":") || s.contains("\"") || normalized.contains(" and ")
                || normalized.contains(" or ") || s.endsWith("*") || s.startsWith("*");
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
        return build(userInput, null);
    }

    public Query build(String userInput, String typeFilter) {
        try {
            QueryParser p = new QueryParser(DEFAULT_FIELD, ANALYZER);
            p.setAllowLeadingWildcard(true);
            p.setDefaultOperator(QueryParser.Operator.AND);
            Query baseQuery;
            if (userInput == null || userInput.isBlank()) {
                baseQuery = p.parse("*:*");
            } else {
                baseQuery = p.parse(userInput.trim());
            }

            String normalizedType = normalizeType(typeFilter);
            if (normalizedType == null) {
                return baseQuery;
            }

            TermQuery typeQuery = new TermQuery(new Term("type", normalizedType));
            return new BooleanQuery.Builder()
                    .add(baseQuery, BooleanClause.Occur.MUST)
                    .add(typeQuery, BooleanClause.Occur.MUST)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid search query: " + userInput, e);
        }
    }

    private static String normalizeType(String typeFilter) {
        if (typeFilter == null) {
            return null;
        }
        String trimmed = typeFilter.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase();
    }
}
