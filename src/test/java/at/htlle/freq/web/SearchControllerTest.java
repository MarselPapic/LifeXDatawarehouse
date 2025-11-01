package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import at.htlle.freq.infrastructure.search.SearchHit;
import at.htlle.freq.infrastructure.search.SmartQueryBuilder;
import at.htlle.freq.infrastructure.search.SuggestService;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        assertEquals(List.of(), controller.query("   ", false).getBody());
        verifyNoInteractions(lucene);
    }

    @Test
    void queryBypassesBuilderForRawQueries() {
        List<SearchHit> hits = List.of(new SearchHit("1", "type", "name", "snippet"));
        when(lucene.search("type:server")).thenReturn(hits);

        assertEquals(hits, controller.query("type:server", true).getBody());
        verify(lucene).search("type:server");
        verify(smart, never()).build(any());
    }

    @Test
    void queryUsesSmartBuilderOtherwise() {
        List<SearchHit> hits = List.of(new SearchHit("2", "type", "name", "snippet"));
        when(lucene.search(any(Query.class))).thenReturn(hits);

        assertEquals(hits, controller.query("Vienna", false).getBody());
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(lucene).search(captor.capture());
        assertTrue(captor.getValue().toString().contains("vienna"));
    }

    @Test
    void suggestDelegatesToServiceWithClampedMax() {
        when(suggest.suggest("ac", 8)).thenReturn(List.of("acme"));
        assertEquals(List.of("acme"), controller.suggest("ac", 8));

        controller.suggest("ac", 100); // should clamp to 25
        verify(suggest).suggest("ac", 25);

        controller.suggest("ac", 0); // should clamp to 1
        verify(suggest).suggest("ac", 1);
    }
}
