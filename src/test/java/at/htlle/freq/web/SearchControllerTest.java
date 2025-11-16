package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import at.htlle.freq.infrastructure.search.SearchHit;
import at.htlle.freq.infrastructure.search.SmartQueryBuilder;
import at.htlle.freq.infrastructure.search.SuggestService;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SearchControllerTest {

    private LuceneIndexService lucene;
    private SmartQueryBuilder smart;
    private SuggestService suggest;
    private SearchController controller;

    @BeforeEach
    void setUp() {
        lucene = mock(LuceneIndexService.class);
        smart = spy(new SmartQueryBuilder());
        suggest = mock(SuggestService.class);
        controller = new SearchController(lucene, smart, suggest);
    }

    @Test
    void queryReturnsEmptyListForBlankSearchTerm() {
        assertEquals(List.of(), controller.query("   ", null, false).getBody());
        verifyNoInteractions(lucene);
    }

    @Test
    void queryBypassesBuilderForRawQueries() {
        List<SearchHit> hits = List.of(new SearchHit("1", "type", "name", "snippet"));
        when(lucene.search("type:server")).thenReturn(hits);

        assertEquals(hits, controller.query("type:server", null, true).getBody());
        verify(lucene).search("type:server");
        verify(smart, never()).build(any());
    }

    @Test
    void queryUsesSmartBuilderOtherwise() {
        List<SearchHit> hits = List.of(new SearchHit("2", "type", "name", "snippet"));
        when(lucene.search(any(Query.class))).thenReturn(hits);

        assertEquals(hits, controller.query("Vienna", null, false).getBody());
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(lucene).search(captor.capture());
        assertTrue(captor.getValue().toString().contains("vienna"));
    }

    @Test
    void queryAppendsTypeFilterToRawQueries() {
        List<SearchHit> hits = List.of(new SearchHit("3", "city", "Vienna", null));
        when(lucene.search("type:city AND (status:active)")).thenReturn(hits);

        assertEquals(hits, controller.query("status:active", "City", true).getBody());
        verify(lucene).search("type:city AND (status:active)");
    }

    @Test
    void queryBuildsScopedQueryWhenTypeProvided() {
        List<SearchHit> hits = List.of(new SearchHit("4", "account", "Acme", null));
        when(lucene.search(any(Query.class))).thenReturn(hits);

        assertEquals(hits, controller.query("Integration", "account", false).getBody());
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(lucene).search(captor.capture());
        String luceneQuery = captor.getValue().toString();
        assertTrue(luceneQuery.contains("content:integration"));
        assertTrue(luceneQuery.contains("type:account"));
    }

    @Test
    void queryReturnsBadRequestWhenBuilderRejectsQuery() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid query syntax");
        doThrow(failure).when(smart).build(eq("???"), any());

        var response = controller.query("???", null, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid query syntax", response.getBody());
        verifyNoInteractions(lucene);
    }

    @Test
    void typeOnlyQueryStillExecutesWhenFilterPresent() {
        List<SearchHit> hits = List.of(new SearchHit("5", "country", "Austria", null));
        when(lucene.search(any(Query.class))).thenReturn(hits);

        assertEquals(hits, controller.query(null, "Country", false).getBody());
        verify(lucene).search(any(Query.class));
    }

    @Test
    void suggestDelegatesToServiceWithClampedMax() {
        when(suggest.suggest("ac", 8)).thenReturn(List.of("acme"));
        assertEquals(List.of("acme"), controller.suggest("ac", 8));

        controller.suggest("ac", 100); // Expect the controller to clamp the upper bound to 25 suggestions
        verify(suggest).suggest("ac", 25);

        controller.suggest("ac", 0); // Expect a minimum clamp of one suggestion even when zero is requested
        verify(suggest).suggest("ac", 1);
    }
}
