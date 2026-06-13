package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HtmlTemplatesTest {

    /**
     * Iterates through a defined array of structural template notification keys,
     * rendering each layout with a common mock parameter map to verify successful html
     * synthesis, data inclusion, and the exclusion of fallback null strings.
     */
    @Test
    void testAllTemplatesRenderSuccessfully() {
        Map<String, Object> model = new HashMap<>();
        model.put("applicationName", "TestApp");
        model.put("userName", "dev_user");
        model.put("tenantDomain", "carbon.super");
        model.put("applicationTier", "Unlimited");
        model.put("tokenType", "JWT");
        model.put("description", "A test app");
        model.put("timestamp", "2023-01-01 12:00:00");
        model.put("workflowRef", "uuid-1234");
        model.put("subscriber", "sub_user");
        model.put("apiName", "TestAPI");
        model.put("apiVersion", "v1");
        model.put("apiProvider", "admin");
        model.put("tier", "Gold");

        String[] templates = {
                "admin_application_created",
                "developer_application_created",
                "admin_subscription_created",
                "developer_subscription_created",
                "publisher_subscription_created"
        };

        for (String template : templates) {
            String html = HtmlTemplates.render(template, model);
            assertNotNull(html, "Template " + template + " should render");
            assertTrue(html.contains("TestApp") || html.contains("TestAPI"), "Should contain data");
            assertFalse(html.contains("null"), "Should not contain literal null");
        }
    }

    /**
     * Assures that when an unrecognized layout key identifier is supplied,
     * the rendering routine handles it by fallback to a safe missing-template
     * html message string.
     */
    @Test
    void testInvalidTemplateReturnsFallback() {
        String html = HtmlTemplates.render("invalid_template_name", new HashMap<>());
        assertTrue(html.contains("No template found: invalid_template_name"));
    }

    /**
     * Validates that empty or incomplete datasets are handled cleanly by confirming
     * that unpopulated model keys default to a placeholder dash character inside
     * the table cell row markup layers.
     */
    @Test
    void testMissingValuesRenderAsDash() {
        Map<String, Object> emptyModel = new HashMap<>();
        String html = HtmlTemplates.render("admin_application_created", emptyModel);
        assertTrue(html.contains("<td>-</td>"), "Null values should map to a dash");
    }

    /**
     * Verifies string input sanitization behavior by testing that special character blocks,
     * tags, and scripts are correctly transformed into safe escaped text entities inside
     * the compiled page block.
     */
    @Test
    void testXssEscaping() {
        Map<String, Object> model = new HashMap<>();
        model.put("applicationName", "<script>alert(\"XSS\" & hack)</script>");

        String html = HtmlTemplates.render("admin_application_created", model);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(&quot;XSS&quot; &amp; hack)&lt;/script&gt;"));
    }
}