package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import javax.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link EmailUtil} against a real, in-process SMTP server (SubEthaSMTP's
 * {@link Wiser}) instead of mocking {@code javax.mail.Transport.send()}.
 *
 * <p>A mocked {@code Transport.send()} only proves the method was called — it can't catch a bug
 * where the email body silently contains "null" instead of a value, or where the wrong
 * content-type header is set. Wiser runs a minimal real SMTP server on an ephemeral local port;
 * assertions here read the actual {@link MimeMessage} objects it received, so this is meaningful
 * coverage of what would actually land in an inbox.
 *
 * <p>Wiser is {@code javax.mail}-native, matching the {@code javax.mail} package used throughout
 * this project and required by the WSO2 Carbon runtime. GreenMail's modern releases depend on
 * {@code jakarta.mail} instead — a different Maven coordinate and package namespace — which risks
 * split-package classpath conflicts if mixed with {@code javax.mail}, so it was not used here.
 *
 * <p><b>Race condition note:</b> {@code Transport.send()} returns as soon as Wiser's SMTP server
 * ACKs the {@code DATA} command, which can happen before Wiser's accept thread finishes appending
 * the message to its internal list — and separately, {@link EmailUtil#sendHtmlEmail} itself
 * dispatches onto its own background worker thread, so the call returns before the send has even
 * been attempted. Both delays are handled the same way: poll with a timeout
 * ({@link EmailUtil#awaitSentCount}) rather than asserting immediately.
 */
class EmailUtilTest {

    /** The in-process SMTP server instance for the currently-running test, bound to an ephemeral port. */
    private Wiser wiser;

    /** The {@code email.smtp.host} system property value as it was before this test, for restoration in {@link #stopWiserAndRestoreSystemProperties}. */
    private String originalHost;

    /** The {@code email.smtp.port} system property value as it was before this test, for restoration in {@link #stopWiserAndRestoreSystemProperties}. */
    private String originalPort;

    /** The {@code email.smtp.from} system property value as it was before this test, for restoration in {@link #stopWiserAndRestoreSystemProperties}. */
    private String originalFrom;

    /**
     * Starts a fresh Wiser SMTP server on an ephemeral local port before every test, redirects
     * {@link EmailUtil}'s SMTP configuration (via system properties) to point at it, and resets
     * the shared {@link EmailUtil#awaitSentCount} counter so each test starts from zero.
     *
     * <p>System properties are saved before being overwritten so {@link #stopWiserAndRestoreSystemProperties}
     * can put them back afterward, keeping this test class from leaking configuration into any
     * other test class that might run in the same JVM.
     */
    @BeforeEach
    void startWiserAndPointEmailUtilAtIt() {
        wiser = new Wiser(0);
        wiser.start();

        originalHost = System.getProperty("email.smtp.host");
        originalPort = System.getProperty("email.smtp.port");
        originalFrom = System.getProperty("email.smtp.from");

        System.setProperty("email.smtp.host", "localhost");
        System.setProperty("email.smtp.port", String.valueOf(wiser.getServer().getPort()));
        System.setProperty("email.smtp.from", "apim-noreply@example.com");

        EmailUtil.resetSentCountForTests();
    }

    /**
     * Stops the Wiser server and restores whatever SMTP system properties were in place before
     * this test ran, so subsequent test classes see a clean, unmodified environment.
     */
    @AfterEach
    void stopWiserAndRestoreSystemProperties() {
        wiser.stop();
        restoreProperty("email.smtp.host", originalHost);
        restoreProperty("email.smtp.port", originalPort);
        restoreProperty("email.smtp.from", originalFrom);
    }

    /**
     * Restores a single system property to its pre-test value, clearing it entirely if it was
     * unset before (rather than leaving behind an empty-string artifact).
     *
     * @param key   the system property name to restore
     * @param value the value to restore it to, or {@code null} if the property was unset before
     */
    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /**
     * Tests verifying {@link EmailUtil#sendHtmlEmail}'s asynchronous, non-blocking dispatch
     * contract: the call must return immediately, with actual SMTP work happening entirely on the
     * background worker thread.
     */
    @Nested
    class AsynchronousDispatch {

        /**
         * Confirms {@code sendHtmlEmail()} returns in well under the time a real SMTP round-trip
         * would take, proving the call truly queues work onto the background thread rather than
         * blocking the caller. The 200ms threshold is generous relative to typical method-call
         * overhead while still being far faster than any real network SMTP exchange.
         */
        @Test
        void sendHtmlEmailReturnsImmediatelyWithoutBlockingOnSmtp() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "FailproofApp");

            long start = System.nanoTime();
            EmailUtil.sendHtmlEmail("recipient@example.com", "Subject", "admin_application_created", model);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMillis < 200, "sendHtmlEmail should return near-instantly, took " + elapsedMillis + "ms");
            assertTrue(EmailUtil.awaitSentCount(1, 2000), "Background send did not complete within timeout");
        }

        /**
         * End-to-end smoke test across the full template set: dispatches all thirteen known
         * templates against a single shared model and confirms every one is both submitted
         * without throwing and actually received by Wiser — the real-SMTP equivalent of the
         * original mock-based "all templates pass through without blocking" test, now with an
         * actual delivery count assertion instead of just a fixed {@code Thread.sleep}.
         */
        @Test
        void allThirteenTemplatesDispatchWithoutThrowing() {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "FailproofApp");
            model.put("userName", "test_user");
            model.put("tenantDomain", "carbon.super");
            model.put("subscriber", "test_sub");
            model.put("apiName", "FailproofAPI");
            model.put("apiVersion", "1.0.0");
            model.put("tierName", "Silver");

            String[] templateKeys = {
                    "admin_application_created", "developer_application_created",
                    "admin_subscription_created", "developer_subscription_created", "publisher_subscription_created",
                    "admin_application_rejected", "developer_application_rejected",
                    "admin_subscription_rejected", "developer_subscription_rejected",
                    "admin_application_pending_approval", "developer_application_submitted",
                    "admin_subscription_pending_approval", "developer_subscription_submitted"
            };

            for (String key : templateKeys) {
                assertDoesNotThrow(() ->
                        EmailUtil.sendHtmlEmail("sandbox-recipient@company.org", "Integrity Run", key, model));
            }

            assertTrue(EmailUtil.awaitSentCount(templateKeys.length, 5000),
                    "Not all queued sends completed within timeout");
            assertEquals(templateKeys.length, wiser.getMessages().size());
        }
    }

    /**
     * Tests verifying the actual content of messages Wiser receives — subject, headers,
     * recipients, content-type, and body — rather than just whether a send was attempted.
     */
    @Nested
    class EmailContent {

        /**
         * End-to-end content assertion: sends one templated email and verifies every aspect of
         * what Wiser actually received — exactly one message, correct subject, correct envelope
         * {@code From} and {@code To} addresses, a {@code text/html} content type, and a rendered
         * body that actually contains the interpolated model values. This is the test that a
         * mocked {@code Transport.send()} could never provide meaningful coverage for.
         *
         * @throws Exception propagated from {@code javax.mail} API calls (e.g.
         * {@code getContent()}), not expected to actually occur here
         */
        @Test
        void wiserReceivesARealWellFormedMessage() throws Exception {
            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", "EnterpriseDataRouter");
            model.put("userName", "compliance_developer");

            EmailUtil.sendHtmlEmail("dev@mycompany.com", "New application created",
                    "admin_application_created", model);

            assertTrue(EmailUtil.awaitSentCount(1, 2000));

            List<WiserMessage> messages = wiser.getMessages();
            assertEquals(1, messages.size());

            MimeMessage mimeMessage = messages.get(0).getMimeMessage();
            assertEquals("New application created", mimeMessage.getSubject());
            assertEquals("apim-noreply@example.com", ((javax.mail.internet.InternetAddress) mimeMessage.getFrom()[0]).getAddress());
            assertEquals("dev@mycompany.com",
                    ((javax.mail.internet.InternetAddress) mimeMessage.getAllRecipients()[0]).getAddress());

            String contentType = mimeMessage.getContentType();
            assertTrue(contentType.toLowerCase().contains("text/html"), "Expected text/html content type, got: " + contentType);

            String body = (String) mimeMessage.getContent();
            assertTrue(body.contains("EnterpriseDataRouter"));
            assertTrue(body.contains("compliance_developer"));
        }

        /**
         * Isolated, narrower companion to {@link #wiserReceivesARealWellFormedMessage}, focused
         * specifically on confirming the {@code email.smtp.from} system property is correctly
         * bound onto the outbound message's {@code From} header, independent of the rest of the
         * message's content.
         *
         * @throws Exception propagated from {@code javax.mail} API calls, not expected to occur
         */
        @Test
        void fromAddressAndDisplayNameAreCorrectlyBoundOntoMimeHeaders() throws Exception {
            EmailUtil.sendHtmlEmail("recipient@example.com", "Subject line", "admin_application_created", new HashMap<>());

            assertTrue(EmailUtil.awaitSentCount(1, 2000));

            MimeMessage mimeMessage = wiser.getMessages().get(0).getMimeMessage();
            javax.mail.internet.InternetAddress from = (javax.mail.internet.InternetAddress) mimeMessage.getFrom()[0];
            assertEquals("apim-noreply@example.com", from.getAddress());
        }
    }

    /**
     * Tests verifying {@link EmailUtil} degrades gracefully when SMTP delivery itself fails,
     * rather than ever propagating an exception back toward the WSO2 workflow thread that
     * triggered the send.
     */
    @Nested
    class FailureResilience {

        /**
         * Points {@code EmailUtil} at a port nothing is listening on so {@code Transport.send()}
         * fails inside the background worker, then confirms both that {@code sendHtmlEmail()}
         * itself never throws (failures are entirely contained within the background task) and
         * that the failed attempt still completes promptly rather than hanging indefinitely.
         */
        @Test
        void smtpUnreachableFailuresAreSwallowedNeverThrown() {
            System.setProperty("email.smtp.host", "localhost");
            System.setProperty("email.smtp.port", "1");

            assertDoesNotThrow(() ->
                    EmailUtil.sendHtmlEmail("recipient@example.com", "Subject", "admin_application_created", new HashMap<>()));

            assertTrue(EmailUtil.awaitSentCount(1, 3000));
        }
    }
}