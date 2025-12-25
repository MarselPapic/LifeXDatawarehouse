package at.htlle.freq.infrastructure.search;

import org.apache.lucene.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmartQueryBuilderTest {

    private final SmartQueryBuilder builder = new SmartQueryBuilder();

    @Test
    void looksLikeLuceneDetectsSyntaxIndicators() {
        assertTrue(SmartQueryBuilder.looksLikeLucene("type:server"));
        assertTrue(SmartQueryBuilder.looksLikeLucene("\"quoted phrase\""));
        assertTrue(SmartQueryBuilder.looksLikeLucene("foo AND bar"));
        assertTrue(SmartQueryBuilder.looksLikeLucene("foo and bar"));
        assertTrue(SmartQueryBuilder.looksLikeLucene("foo AnD bar"));
        assertTrue(SmartQueryBuilder.looksLikeLucene("name*"));
        assertTrue(SmartQueryBuilder.looksLikeLucene("*name"));
    }

    @Test
    void looksLikeLuceneRejectsPlainTerms() {
        assertFalse(SmartQueryBuilder.looksLikeLucene(null));
        assertFalse(SmartQueryBuilder.looksLikeLucene("   "));
        assertFalse(SmartQueryBuilder.looksLikeLucene("just words"));
    }

    @Test
    void buildReturnsMatchAllForBlankInput() {
        Query query = builder.build("   ");
        assertEquals("*:*", query.toString());
    }

    @Test
    void buildParsesUserInputAgainstContentField() {
        Query query = builder.build("Vienna Server");
        String lucene = query.toString();
        assertTrue(lucene.contains("content:vienna"));
        assertTrue(lucene.contains("content:server"));
    }

    @Test
    void buildAllowsLeadingWildcardQueries() {
        Query query = builder.build("*anna");
        assertTrue(query.toString().contains("*anna"));
    }

    @Test
    void buildWithTypeAddsMandatoryFilterClause() {
        Query query = builder.build("Vienna", "account");
        String lucene = query.toString();
        assertTrue(lucene.contains("content:vienna"));
        assertTrue(lucene.contains("type:account"));
    }

    @Test
    void buildWithTypeAndBlankQueryStillScopesByType() {
        Query query = builder.build("   ", "city");
        String lucene = query.toString();
        assertTrue(lucene.contains("type:city"));
        assertTrue(lucene.contains("*:*"));
    }

    @Test
    void buildWrapsParserExceptions() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("\"unterminated"));
        assertTrue(ex.getMessage().contains("Invalid search query"));
    }
}
