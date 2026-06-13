package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailUtil {
    private static final Log log = LogFactory.getLog(EmailUtil.class);

    private static final String SMTP_HOST = "localhost";
    private static final String SMTP_PORT = "1025";

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * Submits an email compilation and transmission task to an asynchronous
     * single-threaded background worker pool, which processes the template rendering
     * and manages SMTP network communications without blocking the primary request path.
     *
     * @param toAddress    The destination email address of the message recipient.
     * @param subject      The text content to populate into the email subject line.
     * @param templateName The key identifier corresponding to the chosen layout design mapping.
     * @param model        The dataset dictionary containing parameters to bind into the HTML content variables.
     */
    public static void sendHtmlEmail(final String toAddress, final String subject,
                                     final String templateName, final Map<String, Object> model) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String htmlContent = HtmlTemplates.render(templateName, model);

                    Properties properties = new Properties();
                    properties.put("mail.smtp.host", SMTP_HOST);
                    properties.put("mail.smtp.port", SMTP_PORT);

                    Session session = Session.getInstance(properties);
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress("noreply@wso2.local"));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
                    message.setSubject(subject);
                    message.setContent(htmlContent, "text/html; charset=utf-8");

                    Transport.send(message);
                    log.info("HTML email sent to: " + toAddress + " | template: " + templateName);
                } catch (Exception e) {
                    log.error("Failed to send HTML email to: " + toAddress, e);
                }
            }
        });
    }
}