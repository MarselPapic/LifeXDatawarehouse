package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerIntegrationTest {

    private static final String ACME_ACCOUNT_ID = "bfacb3aa-2756-4c62-9f92-040000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LuceneIndexService luceneIndexService;

    @BeforeEach
    void setUp() {
        luceneIndexService.reindexAll();
    }

    @AfterEach
    void tearDown() {
        luceneIndexService.reindexAll();
    }

    @Test
    void plainTextQueryReturnsHits() throws Exception {
        mockMvc.perform(get("/search").param("q", "Acme Integration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ACME_ACCOUNT_ID))
                .andExpect(jsonPath("$[0].type").value("account"))
                .andExpect(jsonPath("$[0].text").value("Acme Integration"));
    }

    @Test
    void typeParameterRestrictsResults() throws Exception {
        mockMvc.perform(get("/search").param("q", "Acme").param("type", "account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("account"));

        mockMvc.perform(get("/search").param("q", "Acme").param("type", "city"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void typeOnlyQueryReturnsScopedResults() throws Exception {
        mockMvc.perform(get("/search").param("type", "account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("account"));
    }
}
