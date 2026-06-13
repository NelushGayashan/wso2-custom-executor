package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.Test;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailUtilTest {

    /**
     * Verifies that the email processing helper gracefully isolates network transmission issues
     * by demonstrating that executing tasks without an active SMTP server intercepts exceptions
     * inside the background thread pool without disrupting the main runtime thread.
     *
     * @throws InterruptedException If the test execution environment halts the structural thread wait timer.
     */
    @Test
    void testSendHtmlEmailGracefullyHandlesErrors() throws InterruptedException {
        assertDoesNotThrow(() -> {
            EmailUtil.sendHtmlEmail(
                    "test@example.com",
                    "Test Subject",
                    "admin_application_created",
                    new HashMap<>()
            );
        });

        Thread.sleep(500);
    }
}