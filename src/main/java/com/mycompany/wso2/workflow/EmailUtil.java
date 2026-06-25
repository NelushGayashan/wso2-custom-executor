package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispatches rendered HTML email asynchronously over SMTP via {@code javax.mail}.
 * All sends are queued onto a single background thread so that SMTP latency or an
 * unreachable mail server can never block the calling workflow thread.
 */
public class EmailUtil {

    private static final Log log = LogFactory.getLog(EmailUtil.class);

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static final AtomicInteger completedSendCount = new AtomicInteger(0);

    /**
     * Submits an email task onto an internal single-threaded background queue handler.
     *
     * @param toAddress    Destination communication endpoint.
     * @param subject      The header subject line string data.
     * @param templateName Identifier map mapping to targeted layout engines.
     * @param model        Variable context payload parameter map.
     */
    public static void sendHtmlEmail(final String toAddress, final String subject,
                                     final String templateName, final Map<String, Object> model) {

        if (toAddress == null || toAddress.trim().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping email dispatch: destination address is null or empty for template: " + templateName);
            }
            return;
        }

        executorService.submit(() -> {
            try {
                String smtpHost = System.getProperty("email.smtp.host", "localhost");
                String smtpPort = System.getProperty("email.smtp.port", "1025");
                String fromAddress = System.getProperty("email.smtp.from", "noreply@wso2.local");

                String htmlContent = HtmlTemplates.render(templateName, model);

                Properties properties = new Properties();
                properties.put("mail.smtp.host", smtpHost);
                properties.put("mail.smtp.port", smtpPort);

                Session session = Session.getInstance(properties);
                Message message = new MimeMessage(session);

                message.setFrom(new InternetAddress(fromAddress));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
                message.setSubject(subject);
                message.setContent(htmlContent, "text/html; charset=utf-8");

                Transport.send(message);

                if (log.isDebugEnabled()) {
                    log.debug("Sent HTML email to " + toAddress + " using template " + templateName);
                }
            } catch (Exception e) {
                log.error("Failed to send HTML email to: " + toAddress, e);
            } finally {
                completedSendCount.incrementAndGet();
            }
        });
    }

    /**
     * Synchronization validation testing utility tracking queue drain progress states.
     *
     * @param expectedCount The total historical boundary counter threshold to check against.
     * @param timeoutMillis Maximum duration permitted before a timeout verification failure.
     * @return True if internal indices meet the parameters within thresholds, false otherwise.
     */
    static boolean awaitSentCount(int expectedCount, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (completedSendCount.get() >= expectedCount) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return completedSendCount.get() >= expectedCount;
    }

    /**
     * Resets structural counter registers ensuring completely independent testing isolations.
     */
    static void resetSentCountForTests() {
        completedSendCount.set(0);
    }

    /**
     * Implements thread tracking synchronization via sentinel submission block operations.
     *
     * @param timeoutMillis Absolute tracking limit in structural milliseconds.
     * @throws InterruptedException If a processing queue interrupt notification is caught.
     */
    static void awaitWorkerIdleForTests(long timeoutMillis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        executorService.submit(latch::countDown);
        latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}