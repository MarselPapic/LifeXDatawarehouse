package at.htlle.freq.infrastructure.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;

/**
 * Hilfsklasse, die Nutzereingaben in Lucene-Queries übersetzt und damit den
 * {@link at.htlle.freq.web.SearchController SearchController} entlastet.
 *
 * <p>Verwendet einen {@link StandardAnalyzer}, damit Query-Parsing und Indexierung die gleiche
 * Tokenisierung nutzen. Der {@code SearchController} delegiert freie Texteingaben an diese
 * Komponente, wenn {@link #looksLikeLucene(String)} keine explizite Lucene-Syntax erkennt.</p>
 */
@Component
public class SmartQueryBuilder {

    /**
     * Analyzer der Query-Eingaben identisch wie beim Indexieren tokenisiert, um
     * Suchanfragen konsistent zu interpretieren.
     */
    private static final StandardAnalyzer ANALYZER = new StandardAnalyzer();

    /**
     * Fällt für freie Texteingaben auf das Standardfeld des Volltextindex zurück.
     */
    private static final String DEFAULT_FIELD = "content";

    /**
     * Prüft heuristisch, ob der Nutzer-String bereits eine vollständige Lucene-Query ist.
     *
     * <p>Die Heuristiken erkennen Doppelpunkte für Feldangaben, Anführungszeichen für
     * Phrasen, logische Operatoren ({@code AND}/{@code OR}) sowie ein abschließendes
     * {@code *} für Präfixsuchen.</p>
     *
     * @param q Roh-Eingabe des Nutzers.
     * @return {@code true}, wenn die Heuristiken Lucene-Syntax vermuten; sonst {@code false}.
     */
    public static boolean looksLikeLucene(String q) {
        if (q == null) return false;
        String s = q.trim();
        return s.contains(":") || s.contains("\"") || s.contains(" AND ")
                || s.contains(" OR ") || s.endsWith("*");
    }

    /**
     * Baut aus einer nutzerfreundlichen Texteingabe eine Lucene-Query für den
     * {@link at.htlle.freq.web.SearchController}, falls nicht bereits
     * {@link #looksLikeLucene(String)} greift.
     *
     * <p>Die Eingabe wird mit dem {@link #ANALYZER} auf das {@link #DEFAULT_FIELD}
     * projiziert, der Standardoperator auf {@code AND} gesetzt und bei leerer Eingabe eine
     * Match-All-Query ({@code *:*}) erzeugt.</p>
     *
     * @param userInput Roh-Eingabe des Nutzers, typischerweise aus dem HTTP-Parameter {@code q}.
     * @return Geparste Lucene-Query, die an den {@code LuceneIndexService} weitergegeben werden
     * kann.
     * @throws IllegalArgumentException wenn die Eingabe nicht in eine gültige Lucene-Query
     *                                  übersetzt werden kann.
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
            throw new IllegalArgumentException("Ungültige Suchanfrage: " + userInput, e);
        }
    }
}