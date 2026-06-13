package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HtmlTemplatesTest {

    @Test
    void testTemplatesEnforceZeroBlankOrMissingFields() {
        Map<String, Object> contextModel = new HashMap<>();
        contextModel.put("applicationName", "TargetProductionApp");
        contextModel.put("userName", "ops_engineer");
        contextModel.put("tenantDomain", "secops.wso2.com");
        contextModel.put("applicationTier", "Tier-X1");
        contextModel.put("tokenType", "OAuth2-OIDC");
        contextModel.put("description", "High velocity trading pipeline interface descriptor.");
        contextModel.put("timestamp", "2026-06-13 14:45:00 UTC");
        contextModel.put("workflowRef", "wf-token-7654321");
        contextModel.put("subscriber", "fin_billing_daemon");
        contextModel.put("apiName", "ClearingHouseSettlementAPI");
        contextModel.put("apiVersion", "v2.11");
        contextModel.put("apiProvider", "internal_banking_publisher");
        contextModel.put("tierName", "UltraLowLatencyTier");

        Map<String, String[]> templateExpectedContentMap = new HashMap<>();

        templateExpectedContentMap.put("admin_application_created", new String[]{
                "TargetProductionApp", "ops_engineer", "secops.wso2.com", "Tier-X1", "OAuth2-OIDC", "High velocity trading pipeline", "2026-06-13", "wf-token-7654321"
        });
        templateExpectedContentMap.put("developer_application_created", new String[]{
                "ops_engineer", "TargetProductionApp", "Tier-X1", "OAuth2-OIDC", "High velocity trading pipeline", "2026-06-13"
        });
        templateExpectedContentMap.put("admin_subscription_created", new String[]{
                "fin_billing_daemon", "TargetProductionApp", "secops.wso2.com", "ClearingHouseSettlementAPI", "v2.11", "internal_banking_publisher", "UltraLowLatencyTier", "wf-token-7654321"
        });
        templateExpectedContentMap.put("developer_subscription_created", new String[]{
                "fin_billing_daemon", "ClearingHouseSettlementAPI", "v2.11", "TargetProductionApp", "UltraLowLatencyTier", "2026-06-13"
        });
        templateExpectedContentMap.put("publisher_subscription_created", new String[]{
                "internal_banking_publisher", "ClearingHouseSettlementAPI", "v2.11", "fin_billing_daemon", "TargetProductionApp", "UltraLowLatencyTier", "secops.wso2.com"
        });
        templateExpectedContentMap.put("admin_application_rejected", new String[]{
                "TargetProductionApp", "ops_engineer", "secops.wso2.com", "Tier-X1", "OAuth2-OIDC", "High velocity trading pipeline", "wf-token-7654321"
        });
        templateExpectedContentMap.put("developer_application_rejected", new String[]{
                "ops_engineer", "TargetProductionApp", "secops.wso2.com"
        });
        templateExpectedContentMap.put("admin_subscription_rejected", new String[]{
                "fin_billing_daemon", "TargetProductionApp", "secops.wso2.com", "ClearingHouseSettlementAPI", "v2.11", "internal_banking_publisher", "wf-token-7654321"
        });
        templateExpectedContentMap.put("developer_subscription_rejected", new String[]{
                "fin_billing_daemon", "TargetProductionApp", "ClearingHouseSettlementAPI", "v2.11", "UltraLowLatencyTier"
        });

        for (Map.Entry<String, String[]> testTarget : templateExpectedContentMap.entrySet()) {
            String layoutName = testTarget.getKey();
            String[] expectedTokens = testTarget.getValue();

            String generatedHtml = HtmlTemplates.render(layoutName, contextModel);

            assertNotNull(generatedHtml, "Layout output must never be null: " + layoutName);
            assertFalse(generatedHtml.contains("null"), "Literal 'null' segment intercepted inside compiled HTML payload: " + layoutName);
            assertFalse(generatedHtml.contains("<td>-</td>"), "A variable was dropped, falling back to a dash element inside layout table cells: " + layoutName);

            for (String lookupToken : expectedTokens) {
                assertTrue(generatedHtml.contains(lookupToken),
                        "Data gap identified! Expected value [" + lookupToken + "] missing from rendered template [" + layoutName + "]");
            }
        }
    }

    @Test
    void testInvalidTemplateReturnsFallback() {
        String html = HtmlTemplates.render("unknown_system_event", new HashMap<>());
        assertTrue(html.contains("No template found: unknown_system_event"));
    }

    @Test
    void testXssEscapingEnsuresLayoutSanitization() {
        Map<String, Object> dangerousModel = new HashMap<>();
        dangerousModel.put("applicationName", "\"><script>alert('compromised')</script>");

        String cleanHtml = HtmlTemplates.render("admin_application_created", dangerousModel);

        assertFalse(cleanHtml.contains("<script>"), "Raw unsanitized script injection vectors detected inside final page markup");
        assertTrue(cleanHtml.contains("&lt;script&gt;"), "Special character patterns must resolve to safe entity representations");
    }
}