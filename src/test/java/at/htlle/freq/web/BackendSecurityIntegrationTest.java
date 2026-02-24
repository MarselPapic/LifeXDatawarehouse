package at.htlle.freq.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "lifex.security.backend.enabled=true",
        "lifex.security.backend.username=lifex",
        "lifex.security.backend.password=12345"
})
class BackendSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void backendEndpointsRejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/search").queryParam("q", "site"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void backendEndpointsAllowConfiguredBasicAuthentication() throws Exception {
        mockMvc.perform(get("/accounts")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("lifex", "12345")))
                .andExpect(status().isOk());
    }

    @Test
    void staticUiRemainsPublicWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void h2ConsoleIsProtectedWhenBackendSecurityEnabled() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isUnauthorized());
    }

    private static String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
