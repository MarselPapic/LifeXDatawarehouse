package at.htlle.freq.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendLinkRegressionTest {

    @Test
    void detailsParentMappingUsesCurrentEntityTypes() throws IOException {
        String details = Files.readString(Path.of("src/main/resources/static/details.html"), StandardCharsets.UTF_8);

        assertTrue(details.contains("type === 'PhoneIntegration' || type === 'Phone'"),
                "Phone links should resolve via Site hierarchy for both aliases.");
        assertTrue(details.contains("type === 'AudioDevice' || type === 'Audio'"),
                "Audio links should resolve for the canonical AudioDevice type and legacy alias.");
        assertFalse(details.contains("} else if (type === 'Phone') {"),
                "Legacy phone->client parent mapping must not exist anymore.");
    }

    @Test
    void dashboardDetailsNavigationEncodesDetailId() throws IOException {
        String appJs = Files.readString(Path.of("src/main/resources/static/js/app.js"), StandardCharsets.UTF_8);
        assertTrue(appJs.contains("&id=${encodeURIComponent(id)}`"),
                "Detail navigation must URL-encode the id parameter.");
    }

    @Test
    void softwareVersionFieldIsPresentInFrontendFormConfigAndPayload() throws IOException {
        String formConfig = Files.readString(Path.of("src/main/resources/static/js/form-config.js"), StandardCharsets.UTF_8);
        String createPage = Files.readString(Path.of("src/main/resources/static/create.html"), StandardCharsets.UTF_8);
        String detailsPage = Files.readString(Path.of("src/main/resources/static/details.html"), StandardCharsets.UTF_8);

        assertTrue(formConfig.contains("id: 'swVersion'"),
                "Software form config must contain a dedicated swVersion field.");
        assertTrue(formConfig.contains("name: 'Version'"),
                "Software form config must map the swVersion field to Version.");
        assertTrue(createPage.contains("Version: v('swVersion')"),
                "Create flow must send the Version field when software is created.");
        assertTrue(detailsPage.contains("version: val(item,'Version','version')"),
                "Details view must parse Version as a dedicated software field.");
        assertTrue(detailsPage.contains("if(entry.version) versionParts.push(`Version ${entry.version}`);"),
                "Details view must render software version explicitly in overview badges.");
    }
}
