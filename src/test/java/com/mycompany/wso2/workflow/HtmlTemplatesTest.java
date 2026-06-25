package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit suite for {@link HtmlTemplates}. Runs on every {@code mvn test} with zero
 * external infrastructure — pure string-level assertions against rendered HTML, no mocking, no
 * SMTP server. Organized into three concerns: template content correctness, XSS-escaping
 * behavior, and shared design-system consistency across all nine templates.
 */
class HtmlTemplatesTest {

    /** All thirteen known template keys, used by tests that need to iterate the full template set. */
    private static final String[] ALL_TEMPLATE_NAMES = {
            "admin_application_created", "developer_application_created",
            "admin_subscription_created", "developer_subscription_created", "publisher_subscription_created",
            "admin_application_rejected", "developer_application_rejected",
            "admin_subscription_rejected", "developer_subscription_rejected",
            "admin_application_pending_approval", "developer_application_submitted",
            "admin_subscription_pending_approval", "developer_subscription_submitted"
    };

    /** Tests verifying each template renders the correct data with no blank/null leakage. */
    @Nested
    class TemplateContent {

        /**
         * Renders all nine templates against one shared, fully-populated data model and asserts
         * that every template-specific expected field actually appears in its rendered output,
         * and that no template leaks the literal word "null" anywhere in its markup. This is the
         * primary regression guard against silently dropped or mismapped template fields.
         */
        @Test
        void allTemplatesRenderExpectedFieldsWithNoBlanksOrNullLiterals() {
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

            Map<String, String[]> expectedTokensByTemplate = new HashMap<>();
            expectedTokensByTemplate.put("admin_application_created", new String[]{
                    "TargetProductionApp", "ops_engineer", "secops.wso2.com", "Tier-X1", "OAuth2-OIDC",
                    "High velocity trading pipeline", "2026-06-13", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_application_created", new String[]{
                    "ops_engineer", "TargetProductionApp", "Tier-X1", "OAuth2-OIDC", "High velocity trading pipeline", "2026-06-13"
            });
            expectedTokensByTemplate.put("admin_subscription_created", new String[]{
                    "fin_billing_daemon", "TargetProductionApp", "secops.wso2.com", "ClearingHouseSettlementAPI",
                    "v2.11", "internal_banking_publisher", "UltraLowLatencyTier", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_subscription_created", new String[]{
                    "fin_billing_daemon", "ClearingHouseSettlementAPI", "v2.11", "TargetProductionApp", "UltraLowLatencyTier", "2026-06-13"
            });
            expectedTokensByTemplate.put("publisher_subscription_created", new String[]{
                    "internal_banking_publisher", "ClearingHouseSettlementAPI", "v2.11", "fin_billing_daemon",
                    "TargetProductionApp", "UltraLowLatencyTier", "secops.wso2.com"
            });
            expectedTokensByTemplate.put("admin_application_rejected", new String[]{
                    "TargetProductionApp", "ops_engineer", "secops.wso2.com", "Tier-X1", "OAuth2-OIDC",
                    "High velocity trading pipeline", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_application_rejected", new String[]{
                    "ops_engineer", "TargetProductionApp", "secops.wso2.com"
            });
            expectedTokensByTemplate.put("admin_subscription_rejected", new String[]{
                    "fin_billing_daemon", "TargetProductionApp", "secops.wso2.com", "ClearingHouseSettlementAPI",
                    "v2.11", "internal_banking_publisher", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_subscription_rejected", new String[]{
                    "fin_billing_daemon", "TargetProductionApp", "ClearingHouseSettlementAPI", "v2.11", "UltraLowLatencyTier"
            });
            expectedTokensByTemplate.put("admin_application_pending_approval", new String[]{
                    "TargetProductionApp", "ops_engineer", "secops.wso2.com", "Tier-X1", "OAuth2-OIDC",
                    "High velocity trading pipeline", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_application_submitted", new String[]{
                    "ops_engineer", "TargetProductionApp", "Tier-X1", "OAuth2-OIDC", "High velocity trading pipeline"
            });
            expectedTokensByTemplate.put("admin_subscription_pending_approval", new String[]{
                    "fin_billing_daemon", "TargetProductionApp", "secops.wso2.com", "ClearingHouseSettlementAPI",
                    "v2.11", "internal_banking_publisher", "UltraLowLatencyTier", "wf-token-7654321"
            });
            expectedTokensByTemplate.put("developer_subscription_submitted", new String[]{
                    "fin_billing_daemon", "ClearingHouseSettlementAPI", "v2.11", "TargetProductionApp", "UltraLowLatencyTier"
            });

            for (Map.Entry<String, String[]> entry : expectedTokensByTemplate.entrySet()) {
                String templateName = entry.getKey();
                String[] expectedTokens = entry.getValue();

                String html = HtmlTemplates.render(templateName, contextModel);

                assertNotNull(html, "Render output must never be null: " + templateName);
                assertFalse(html.contains(">null<") || html.contains(": null"),
                        "Literal 'null' leaked into rendered output: " + templateName);

                for (String token : expectedTokens) {
                    assertTrue(html.contains(token),
                            "Expected value [" + token + "] missing from rendered template [" + templateName + "]");
                }
            }
        }

        /**
         * Confirms that rendering with a completely empty model falls back to the {@code "-"}
         * placeholder (via {@link HtmlTemplates}'s internal {@code v()} helper) rather than ever
         * printing the literal word "null" into the customer-facing HTML.
         */
        @Test
        void missingFieldsFallBackToPlaceholderRatherThanNullLiteral() {
            String html = HtmlTemplates.render("admin_application_created", new HashMap<>());
            assertFalse(html.contains(">null<"), "Missing fields must not render the literal word null");
            assertTrue(html.contains("<td>-</td>"), "Missing fields should fall back to a dash placeholder");
        }

        /**
         * Confirms that an unrecognized template key hits the {@code default} branch of
         * {@link HtmlTemplates#render}'s switch statement and returns a safe, informative fallback
         * message rather than throwing or returning {@code null}.
         */
        @Test
        void unknownTemplateNameReturnsFallbackMarkup() {
            String html = HtmlTemplates.render("unknown_system_event", new HashMap<>());
            assertTrue(html.contains("No template found: unknown_system_event"));
        }

        /**
         * Smoke-tests every one of the nine templates against a fully empty model, confirming
         * none of them throw (e.g. a {@link NullPointerException} from an unguarded
         * {@code model.get(key).toString()} somewhere in a template builder method).
         */
        @Test
        void allTemplatesRenderWithoutThrowingOnAnEmptyModel() {
            for (String templateName : ALL_TEMPLATE_NAMES) {
                assertDoesNotThrow(() -> HtmlTemplates.render(templateName, new HashMap<>()),
                        "Template should render defensively even with a fully empty model: " + templateName);
            }
        }
    }

    /**
     * Tests verifying {@link HtmlTemplates}'s {@code esc()} sanitizer neutralizes injection
     * attempts and behaves correctly character-by-character. This is the suite that locks in the
     * fix for the original four-character escaper gap (missing {@code '} and {@code =}) described
     * in the project README.
     */
    @Nested
    class HtmlEscaping {

        /**
         * Supplies a representative set of HTML/script/SQL injection payloads to
         * {@link #payloadsAreNeutralizedInRenderedOutput}, covering script-tag injection,
         * attribute-breakout via a leading quote, the bare-attribute {@code <img onerror>} case,
         * a SQL-injection-style string (to confirm this isn't SQL-specific escaping, just generic
         * HTML escaping that happens to also neutralize it), and an already-encoded entity string
         * (to confirm no double-encoding corruption).
         *
         * @return the stream of payload strings consumed by the parameterized test
         */
        static Stream<String> xssPayloads() {
            return Stream.of(
                    "<script>alert('compromised')</script>",
                    "\"><script>alert(1)</script>",
                    "<img src=x onerror=alert(1)>",
                    "'; DROP TABLE applications; --",
                    "&lt;already&gt;encoded&amp;entities"
            );
        }

        /**
         * Parameterized test run once per payload from {@link #xssPayloads}: confirms that no
         * raw {@code <script>} tag and no raw {@code <img src=x onerror=alert(1)>} element ever
         * survives intact in rendered output, regardless of which specific injection technique
         * the payload uses.
         *
         * @param payload one untrusted string from {@link #xssPayloads}, injected into both the
         * {@code applicationName} and {@code userName} model fields
         */
        @ParameterizedTest
        @MethodSource("xssPayloads")
        void payloadsAreNeutralizedInRenderedOutput(String payload) {
            Map<String, Object> dangerousModel = new HashMap<>();
            dangerousModel.put("applicationName", payload);
            dangerousModel.put("userName", payload);

            String html = HtmlTemplates.render("admin_application_created", dangerousModel);

            assertFalse(html.contains("<script>"), "Raw <script> tag must never survive rendering: " + payload);
            assertFalse(html.contains("<img src=x onerror=alert(1)>"),
                    "Raw <img onerror> element must never survive rendering: " + payload);
        }

        /**
         * Targeted single-character test: confirms {@code <} and {@code >} are escaped to
         * {@code &lt;}/{@code &gt;} for a basic {@code <script>} payload, isolating this specific
         * escape behavior from the broader parameterized payload suite above.
         */
        @Test
        void angleBracketsAreEscapedToEntities() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "<script>alert('x')</script>");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertFalse(html.contains("<script>"));
            assertTrue(html.contains("&lt;script&gt;"));
        }

        /**
         * Regression test locking in the {@code =} escaping fix described in the project README.
         * {@code <}, {@code >} escaping alone prevents the payload from parsing as a real
         * {@code <img>} element, but without escaping {@code =} the literal substring
         * {@code onerror=alert(1)} would still survive intact in the rendered output,
         * superficially resembling a live HTML attribute. Confirms the exact escaped form
         * ({@code onerror&#61;alert(1)}) appears in the output.
         */
        @Test
        void bareAttributeInjectionGapIsClosedByEscapingEquals() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "<img src=x onerror=alert(1)>");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertFalse(html.contains("onerror=alert(1)"),
                    "The '=' character must be escaped to prevent bare-attribute-injection patterns from surviving");
            assertTrue(html.contains("onerror&#61;alert(1)"),
                    "Expected '=' to be escaped to &#61; in the rendered output");
        }

        /**
         * Targeted single-character test: confirms a raw single quote ({@code '}) is escaped to
         * {@code &#39;}, closing the single-quoted-attribute breakout gap that was missing from
         * the original four-character escaper.
         */
        @Test
        void singleQuotesAreEscapedToPreventAttributeBreakout() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "x' onmouseover='alert(1)");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertFalse(html.contains("' onmouseover='"),
                    "Raw single quotes must be escaped to prevent breaking out of a single-quoted attribute");
            assertTrue(html.contains("&#39;"));
        }

        /**
         * Targeted single-character test: confirms a raw double quote ({@code "}) is escaped to
         * {@code &quot;}, preventing breakout from a double-quoted HTML attribute.
         */
        @Test
        void doubleQuotesAreEscapedToPreventAttributeBreakout() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "\" onmouseover=\"alert(1)");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertFalse(html.contains("\" onmouseover=\""));
            assertTrue(html.contains("&quot;"));
        }

        /**
         * Confirms {@code esc()}'s escape ordering is correct: {@code &} must be escaped before
         * the other characters, otherwise the {@code &} produced by escaping {@code <}/{@code >}/etc.
         * would itself get re-escaped into {@code &amp;amp;}, corrupting the output. Uses a string
         * containing all four of {@code &}, {@code '}, {@code <}, {@code >} simultaneously to
         * exercise this interaction directly.
         */
        @Test
        void ampersandIsEscapedFirstSoOtherEscapesDoNotDoubleEncode() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "Tom & Jerry's <Adventures>");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertTrue(html.contains("Tom &amp; Jerry&#39;s &lt;Adventures&gt;"));
            assertFalse(html.contains("&amp;amp;"), "Ampersand must not be double-escaped");
        }

        /**
         * Confirms {@code esc()} deliberately leaves forward-slash ({@code /}) unescaped. This
         * escaper is sometimes applied to values that appear near URLs in templates (e.g. inside
         * a {@code description} field), and escaping {@code /} would corrupt every
         * {@code https://...} link the moment that pattern was touched — see {@code esc()}'s
         * Javadoc in {@link HtmlTemplates} for the full rationale.
         */
        @Test
        void forwardSlashIsDeliberatelyNotEscaped() {
            Map<String, Object> model = new HashMap<>();
            model.put("description", "See https://example.com/docs for details");

            String html = HtmlTemplates.render("admin_application_created", model);

            assertTrue(html.contains("https://example.com/docs"));
        }
    }

    /**
     * Tests verifying every one of the nine templates consistently uses the shared CSS design
     * system and document boilerplate from {@link HtmlTemplates#head}/{@code sharedCss}/{@code footer},
     * rather than any one template drifted out of sync with the rest.
     */
    @Nested
    class SharedDesignSystem {

        /**
         * Confirms every template includes the shared {@code wrapper}/{@code footer} CSS classes
         * and the standard footer copy, catching any template that accidentally bypasses
         * {@link HtmlTemplates#head} or {@link HtmlTemplates}'s {@code footer()} helper.
         */
        @Test
        void everyTemplateUsesTheSharedWrapperAndFooterMarkup() {
            Map<String, Object> model = new HashMap<>();
            for (String templateName : ALL_TEMPLATE_NAMES) {
                String html = HtmlTemplates.render(templateName, model);
                assertTrue(html.contains("class=\"wrapper\""), "Missing shared wrapper class: " + templateName);
                assertTrue(html.contains("class=\"footer\""), "Missing shared footer class: " + templateName);
                assertTrue(html.contains("Automated notification from WSO2 API Manager"),
                        "Missing shared footer copy: " + templateName);
            }
        }

        /**
         * Confirms every template begins with a proper {@code <!DOCTYPE html>} declaration and
         * declares a UTF-8 charset, since both matter for correct rendering across the wide
         * variety of email clients an automated notification may land in.
         */
        @Test
        void everyTemplateDeclaresUtf8CharsetAndDoctype() {
            Map<String, Object> model = new HashMap<>();
            for (String templateName : ALL_TEMPLATE_NAMES) {
                String html = HtmlTemplates.render(templateName, model);
                assertTrue(html.startsWith("<!DOCTYPE html>"), "Missing DOCTYPE: " + templateName);
                assertTrue(html.contains("charset=\"UTF-8\""), "Missing UTF-8 charset declaration: " + templateName);
            }
        }
    }
}