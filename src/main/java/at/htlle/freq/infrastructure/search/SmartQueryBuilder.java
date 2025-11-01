package at.htlle.freq.infrastructure.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;

@Component
public class SmartQueryBuilder {

    private static final StandardAnalyzer ANALYZER = new StandardAnalyzer();
    private static final String DEFAULT_FIELD = "content";

    /** Heuristik: sieht der String nach Lucene-Syntax aus? */
    public static boolean looksLikeLucene(String q) {
        if (q == null) return false;
        String s = q.trim();
        return s.contains(":") || s.contains("\"") || s.contains(" AND ")
                || s.contains(" OR ") || s.endsWith("*");
    }

    /** Baut aus einer normalen Nutzereingabe eine Multi-Field-Lucene-Query. */
    public Query build(String userInput) {
        try {
            QueryParser p = new QueryParser(DEFAULT_FIELD, ANALYZER);
            p.setDefaultOperator(QueryParser.Operator.AND);
            if (userInput == null || userInput.isBlank()) {
                return p.parse("*:*");
            }
            return p.parse(userInput.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültige Suchanfrage: " + userInput, e);
        }
    }
}