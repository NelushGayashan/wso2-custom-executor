package com.mycompany.wso2.workflow;

import java.util.Map;

/**
 * Zero-dependency HTML email template renderer.
 *
 * <p>Every template is built with plain Java string concatenation rather than a templating
 * engine (Thymeleaf, FreeMarker, Velocity). This is a deliberate constraint, not a missing
 * feature: WSO2 API Manager runs on Eclipse Equinox, an OSGi container with strict classloader
 * isolation between bundles. A templating library's classes are not visible across the OSGi
 * module boundary unless extensively (and fragile-ly) wired via {@code Import-Package} /
 * {@code Export-Package} directives, which risks {@code NoClassDefFoundError} at runtime for a
 * bundle deployed into {@code dropins/}. Trading templating ergonomics for deployment
 * reliability is the right trade for a handful of fixed templates.
 */
public class HtmlTemplates {

    /**
     * Renders the named HTML email template against the given data model.
     *
     * <p>This is the sole public entry point into the rendering engine; {@link EmailUtil} calls
     * this once per dispatched email to obtain the HTML body before handing it to
     * {@code javax.mail}. Template selection is a plain {@code switch} over known string keys
     * rather than reflection or a registry, since the full set of thirteen templates is fixed and
     * known at compile time.
     *
     * @param templateName one of the thirteen known template keys (e.g. {@code "admin_application_created"});
     * an unrecognized key falls through to the {@code default} case
     * @param model         the data to interpolate into the template; missing keys render as a
     * {@code "-"} placeholder via {@link #v}, never as the literal word "null"
     * @return the fully rendered HTML document as a string, ready to be set as a message body
     */
    public static String render(String templateName, Map<String, Object> model) {
        switch (templateName) {
            case "admin_application_created":         return adminApplicationCreated(model);
            case "developer_application_created":      return developerApplicationCreated(model);
            case "admin_subscription_created":         return adminSubscriptionCreated(model);
            case "developer_subscription_created":     return developerSubscriptionCreated(model);
            case "publisher_subscription_created":     return publisherSubscriptionCreated(model);
            case "admin_application_rejected":         return adminApplicationRejected(model);
            case "developer_application_rejected":     return developerApplicationRejected(model);
            case "admin_subscription_rejected":        return adminSubscriptionRejected(model);
            case "developer_subscription_rejected":    return developerSubscriptionRejected(model);
            case "admin_application_pending_approval":   return adminApplicationPendingApproval(model);
            case "developer_application_submitted":      return developerApplicationSubmitted(model);
            case "admin_subscription_pending_approval":  return adminSubscriptionPendingApproval(model);
            case "developer_subscription_submitted":     return developerSubscriptionSubmitted(model);
            default:
                return "<html><body><p>No template found: " + esc(templateName) + "</p></body></html>";
        }
    }

    /**
     * Reads a value out of the model map, escaping it for safe HTML interpolation.
     * Falls back to "-" when the key is absent, so a missing field renders as a visible
     * placeholder rather than the literal string "null".
     *
     * @param m   the data model for this render call
     * @param key the model key to read
     * @return the escaped string value, or {@code "-"} if {@code key} is absent or its value is null
     */
    private static String v(Map<String, Object> m, String key) {
        Object val = m.get(key);
        return val != null ? esc(val.toString()) : "-";
    }

    /**
     * Narrow, purpose-built escaper for plain-text values (usernames, application names, API
     * names, etc.) interpolated into a controlled, known-safe template. This is not a
     * general-purpose HTML sanitizer and should not be reused for arbitrary rich-text input.
     *
     * <p>Escapes six characters:
     * <ul>
     * <li>{@code &} → {@code &amp;} — prevents breaking other entity references</li>
     * <li>{@code <} → {@code &lt;} — prevents opening a new HTML tag</li>
     * <li>{@code >} → {@code &gt;} — prevents closing into an unintended tag boundary</li>
     * <li>{@code "} → {@code &quot;} — prevents breaking out of a double-quoted attribute</li>
     * <li>{@code '} → {@code &#39;} — prevents breaking out of a single-quoted attribute</li>
     * <li>{@code =} → {@code &#61;} — closes the bare-attribute-injection gap below</li>
     * </ul>
     *
     * <p><b>Why {@code =} matters:</b> a value such as {@code <img src=x onerror=alert(1)>} has
     * its {@code <} and {@code >} escaped, which prevents it from being parsed as a real
     * {@code <img>} element — but without escaping {@code =}, the literal substring
     * {@code onerror=alert(1)} survives intact in the rendered output. Escaping {@code =} ensures
     * it can never even superficially resemble a live HTML attribute.
     *
     * <p>Deliberately <b>not</b> escaped: backtick ({@code `}) and forward-slash ({@code /}).
     * Backtick escaping matters for JavaScript template-literal injection, not HTML rendering —
     * irrelevant here. Forward-slash escaping is actively harmful, since {@code esc()} is applied
     * to values that may appear near URLs in templates; escaping {@code /} would corrupt every
     * {@code https://...} link the moment that path was touched.
     *
     * @param s the raw, untrusted string to escape; {@code null} is treated as an empty string
     * @return the escaped string, safe for interpolation into a controlled HTML template
     */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("=", "&#61;");
    }

    /**
     * Builds the opening boilerplate shared by every template: {@code DOCTYPE}, {@code <head>}
     * with UTF-8 charset and escaped title, the inlined shared stylesheet (with this template's
     * gradient colors substituted in), and the opening {@code <div class="wrapper">}.
     *
     * <p>Inlined {@code <style>} is used rather than a linked stylesheet because most email
     * clients strip or ignore external CSS references entirely; inlining is the only reliable way
     * to guarantee styling renders consistently across inboxes.
     *
     * @param title         the document {@code <title>}, escaped before interpolation
     * @param gradientFrom  CSS color (hex) for the header gradient's starting color
     * @param gradientTo    CSS color (hex) for the header gradient's ending color
     * @return the opening HTML fragment, to be followed by template-specific body content and
     * closed with {@link #footer()}
     */
    private static String head(String title, String gradientFrom, String gradientTo) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>" + esc(title) + "</title>"
                + "<style>" + sharedCss(gradientFrom, gradientTo) + "</style></head><body><div class=\"wrapper\">";
    }

    /**
     * One shared CSS design system for every template, so a single style tweak propagates
     * everywhere instead of needing to be hunted down across nine separate methods. Header
     * gradient colors are the only per-template variable; everything else (spacing, type scale,
     * table styling, badges, footer) is held constant for visual consistency across the whole
     * notification set.
     *
     * @param gradientFrom CSS color (hex) for the header gradient's starting color
     * @param gradientTo   CSS color (hex) for the header gradient's ending color
     * @return the complete inline stylesheet as a raw CSS string, ready to be wrapped in a
     * {@code <style>} tag by {@link #head}
     */
    private static String sharedCss(String gradientFrom, String gradientTo) {
        return "body{margin:0;padding:0;background:#f4f6f9;font-family:'Segoe UI',Arial,sans-serif;}"
                + ".wrapper{max-width:620px;margin:30px auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1);}"
                + ".header{background:linear-gradient(135deg," + gradientFrom + "," + gradientTo + ");padding:28px 32px;}"
                + ".header h1{color:#fff;margin:0;font-size:20px;}"
                + ".header p{color:rgba(255,255,255,.75);margin:6px 0 0;font-size:13px;}"
                + ".body{padding:28px 32px;}"
                + ".body p{color:#444;font-size:14px;line-height:1.6;margin:0 0 16px;}"
                + "table{width:100%;border-collapse:collapse;margin:16px 0;}"
                + "td{padding:10px 14px;font-size:13px;color:#333;border-bottom:1px solid #e8ecf0;}"
                + "tr:nth-child(even) td{background:#f8fafc;}"
                + "td:first-child{font-weight:600;width:40%;}"
                + ".badge{display:inline-block;border-radius:4px;padding:3px 10px;font-size:12px;font-weight:600;margin-bottom:20px;}"
                + ".ref-box{background:#fff8e1;border-left:4px solid #f59e0b;padding:10px 16px;border-radius:4px;font-size:12px;color:#7c6000;margin-top:20px;}"
                + ".steps{border-radius:6px;padding:16px 20px;margin-top:20px;}"
                + ".steps h3{margin:0 0 10px;font-size:14px;}"
                + ".steps ul{margin:0;padding-left:18px;font-size:13px;line-height:1.8;}"
                + ".footer{background:#f4f6f9;padding:16px 32px;text-align:center;font-size:11px;color:#888;border-top:1px solid #e8ecf0;}";
    }

    /**
     * Builds the closing boilerplate shared by every template: the standard "automated
     * notification, do not reply" footer text, plus the closing tags that pair with
     * {@link #head}'s opening {@code <div class="wrapper">}, {@code <body>}, and {@code <html>}.
     *
     * @return the closing HTML fragment that must terminate every rendered template
     */
    private static String footer() {
        return "<div class=\"footer\">Automated notification from WSO2 API Manager &mdash; Do not reply.</div>"
                + "</div></body></html>";
    }

    /**
     * Builds a single two-column {@code <tr>} for the details tables used throughout every
     * template (e.g. "Application Name" / "EnterpriseDataRouter").
     *
     * <p>Only {@code label} is escaped by this method; {@code value} is expected to already be
     * pre-escaped by the caller via {@link #v}, since some call sites compose {@code value} from
     * multiple already-escaped fragments where double-escaping would corrupt the entities.
     *
     * @param label the row's left-column label (e.g. {@code "Application Name"}); escaped here
     * @param value the row's right-column value; must already be escaped/sanitized by the caller
     * @return a single {@code <tr><td>...</td><td>...</td></tr>} HTML fragment
     */
    private static String row(String label, String value) {
        return "<tr><td>" + esc(label) + "</td><td>" + value + "</td></tr>";
    }

    /**
     * Renders the {@code admin_application_created} template: an audit notification sent to the
     * administrator whenever a new application is auto-approved, listing the application's name,
     * creator, tenant, tier, token type, description, timestamp, and workflow reference.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminApplicationCreated(Map<String, Object> m) {
        return head("New Application Created", "#1a3c5e", "#0f6cbd")
                + "<div class=\"header\"><h1>&#9888; New Application Created</h1>"
                + "<p>WSO2 API Manager &mdash; Admin Notification</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#e8f4fd;color:#0f6cbd;border:1px solid #b3d9f7;\">APPLICATION CREATION</span>"
                + "<p>A new application has been registered and <strong>auto-approved</strong>.</p>"
                + "<table>"
                + row("Application Name", v(m, "applicationName"))
                + row("Created By",       v(m, "userName"))
                + row("Tenant Domain",    v(m, "tenantDomain"))
                + row("Throttling Tier",  v(m, "applicationTier"))
                + row("Token Type",       v(m, "tokenType"))
                + row("Description",      v(m, "description"))
                + row("Timestamp",        v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_application_created} template: a confirmation sent to the
     * application's creator, including a "Next Steps" checklist guiding them toward subscribing
     * to APIs and generating OAuth 2.0 tokens.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerApplicationCreated(Map<String, Object> m) {
        return head("Application Created Successfully", "#0f6cbd", "#38bdf8")
                + "<div class=\"header\"><h1>&#10003; Application Created Successfully</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#ecfdf5;color:#059669;border:1px solid #a7f3d0;\">&#10003; APPROVED</span>"
                + "<p>Hi <strong>" + v(m, "userName") + "</strong>,</p>"
                + "<p>Your application <strong style=\"color:#0f6cbd;\">" + v(m, "applicationName") + "</strong> has been successfully created.</p>"
                + "<table>"
                + row("Application Name",  v(m, "applicationName"))
                + row("Throttling Policy", v(m, "applicationTier"))
                + row("Token Type",        v(m, "tokenType"))
                + row("Description",       v(m, "description"))
                + row("Created At",        v(m, "timestamp"))
                + "</table>"
                + "<div class=\"steps\" style=\"background:#f0f9ff;border:1px solid #bae6fd;\">"
                + "<h3 style=\"color:#0369a1;\">&#8594; Next Steps</h3>"
                + "<ul style=\"color:#444;\">"
                + "<li>Browse available APIs in the Developer Portal</li>"
                + "<li>Subscribe your application to the APIs you need</li>"
                + "<li>Generate OAuth 2.0 tokens under your application</li>"
                + "<li>Start making API calls with your credentials</li>"
                + "</ul></div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code admin_subscription_created} template: an audit notification sent to the
     * administrator whenever a new API subscription is auto-approved, with subscriber details and
     * API details split into two separate detail tables for readability.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminSubscriptionCreated(Map<String, Object> m) {
        return head("New API Subscription", "#1a3c5e", "#7c3aed")
                + "<div class=\"header\"><h1>&#128279; New API Subscription</h1>"
                + "<p>WSO2 API Manager &mdash; Admin Notification</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#f5f3ff;color:#7c3aed;border:1px solid #ddd6fe;\">SUBSCRIPTION CREATION</span>"
                + "<p>A new subscription has been registered and <strong>auto-approved</strong>.</p>"
                + "<p style=\"font-size:12px;font-weight:700;color:#7c3aed;text-transform:uppercase;letter-spacing:.8px;\">Subscriber Details</p>"
                + "<table>"
                + row("Subscriber",  v(m, "subscriber"))
                + row("Application", v(m, "applicationName"))
                + row("Tenant",      v(m, "tenantDomain"))
                + "</table>"
                + "<p style=\"font-size:12px;font-weight:700;color:#7c3aed;text-transform:uppercase;letter-spacing:.8px;\">API Details</p>"
                + "<table>"
                + row("API Name",   v(m, "apiName"))
                + row("Version",    v(m, "apiVersion"))
                + row("Provider",   v(m, "apiProvider"))
                + row("Tier",       v(m, "tierName"))
                + row("Timestamp",  v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_subscription_created} template: a confirmation sent to the
     * subscribing developer, including a "Next Steps" checklist guiding them toward generating
     * keys and making their first API call.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerSubscriptionCreated(Map<String, Object> m) {
        return head("Subscription Confirmed", "#059669", "#10b981")
                + "<div class=\"header\"><h1>&#10003; Subscription Confirmed</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#ecfdf5;color:#059669;border:1px solid #a7f3d0;\">&#10003; APPROVED</span>"
                + "<p>Hi <strong>" + v(m, "subscriber") + "</strong>,</p>"
                + "<p>Your subscription to <strong style=\"color:#059669;\">" + v(m, "apiName") + "</strong> v"
                + v(m, "apiVersion") + " via <strong style=\"color:#059669;\">" + v(m, "applicationName") + "</strong> is confirmed.</p>"
                + "<table>"
                + row("API Name",     v(m, "apiName"))
                + row("Version",      v(m, "apiVersion"))
                + row("Provider",     v(m, "apiProvider"))
                + row("Application",  v(m, "applicationName"))
                + row("Tier",         v(m, "tierName"))
                + row("Subscribed At", v(m, "timestamp"))
                + "</table>"
                + "<div class=\"steps\" style=\"background:#f0fdf4;border:1px solid #bbf7d0;\">"
                + "<h3 style=\"color:#15803d;\">&#8594; Next Steps</h3>"
                + "<ul style=\"color:#444;\">"
                + "<li>Go to your application in the Developer Portal</li>"
                + "<li>Generate OAuth 2.0 production / sandbox keys</li>"
                + "<li>Copy your Consumer Key and Secret</li>"
                + "<li>Start calling the API using your access token</li>"
                + "</ul></div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code publisher_subscription_created} template: notifies the API's publisher
     * that a new consumer has subscribed to their API, with a pointer to the Publisher Portal's
     * Subscriptions tab for further review.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String publisherSubscriptionCreated(Map<String, Object> m) {
        return head("New Subscriber for Your API", "#0f6cbd", "#0ea5e9")
                + "<div class=\"header\"><h1>&#128276; New Subscriber for Your API</h1>"
                + "<p>WSO2 API Manager &mdash; Publisher Notification</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#eff6ff;color:#0f6cbd;border:1px solid #bfdbfe;\">NEW SUBSCRIPTION</span>"
                + "<p>Hi <strong>" + v(m, "apiProvider") + "</strong>,</p>"
                + "<p>Your API <strong style=\"color:#0f6cbd;\">" + v(m, "apiName") + "</strong> v"
                + v(m, "apiVersion") + " has a new subscriber.</p>"
                + "<table>"
                + row("Subscriber",   v(m, "subscriber"))
                + row("Application",  v(m, "applicationName"))
                + row("Tier",         v(m, "tierName"))
                + row("Tenant",       v(m, "tenantDomain"))
                + row("Subscribed At", v(m, "timestamp"))
                + "</table>"
                + "<div style=\"background:#f0f9ff;border:1px solid #bae6fd;border-radius:6px;padding:14px 18px;margin-top:20px;font-size:13px;color:#0369a1;\">"
                + "&#8505; This subscription was <strong>auto-approved</strong>. Review active subscriptions in the Publisher Portal under your API's <em>Subscriptions</em> tab."
                + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code admin_application_rejected} template: an audit record sent to the
     * administrator confirming that an application creation request was manually reviewed and
     * declined, with full request details and the workflow reference for traceability.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminApplicationRejected(Map<String, Object> m) {
        return head("Application Request Rejected", "#450a0a", "#b91c1c")
                + "<div class=\"header\"><h1>&#10060; Application Request Rejected</h1>"
                + "<p>WSO2 API Manager &mdash; System Audit Log</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fef2f2;color:#b91c1c;border:1px solid #fca5a5;\">APPLICATION DECLINED</span>"
                + "<p>An application creation request has been manually reviewed and <strong>declined</strong> by an administrator.</p>"
                + "<table>"
                + row("Application Name", v(m, "applicationName"))
                + row("Requested By",     v(m, "userName"))
                + row("Tenant Domain",    v(m, "tenantDomain"))
                + row("Throttling Tier",  v(m, "applicationTier"))
                + row("Token Type",       v(m, "tokenType"))
                + row("Description",      v(m, "description"))
                + row("Decision Time",    v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\" style=\"background:#fef2f2;border-left:4px solid #ef4444;color:#991b1b;\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_application_rejected} template: informs the application's
     * creator that their request was reviewed and declined, with measured, non-alarming copy and
     * a pointer to follow up with the platform administrator.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerApplicationRejected(Map<String, Object> m) {
        return head("Application Request Declined", "#b91c1c", "#ef4444")
                + "<div class=\"header\"><h1>&#128721; Application Request Declined</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fef2f2;color:#b91c1c;border:1px solid #fca5a5;\">&#10060; DECLINED</span>"
                + "<p>Hi <strong>" + v(m, "userName") + "</strong>,</p>"
                + "<p>Your request to create the application <strong>" + v(m, "applicationName") + "</strong> was reviewed and declined.</p>"
                + "<table>"
                + row("Application Name", v(m, "applicationName"))
                + row("Tenant Domain",    v(m, "tenantDomain"))
                + row("Timestamp",        v(m, "timestamp"))
                + "</table>"
                + "<p style=\"margin-top:20px;font-size:13px;color:#666;\">If you believe this was a mistake, please get in touch with your platform administrator.</p>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code admin_subscription_rejected} template: an audit record sent to the
     * administrator confirming that a subscription request was manually reviewed and declined,
     * with subscriber and requested-API details split into two separate detail tables.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminSubscriptionRejected(Map<String, Object> m) {
        return head("API Subscription Denied", "#450a0a", "#b91c1c")
                + "<div class=\"header\"><h1>&#10060; API Subscription Denied</h1>"
                + "<p>WSO2 API Manager &mdash; System Audit Log</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fef2f2;color:#b91c1c;border:1px solid #fca5a5;\">SUBSCRIPTION BLOCKED</span>"
                + "<p>An API subscription request has been manually reviewed and <strong>declined</strong> by an administrator.</p>"
                + "<p style=\"font-size:12px;font-weight:700;color:#b91c1c;text-transform:uppercase;letter-spacing:.8px;\">Subscriber Details</p>"
                + "<table>"
                + row("Subscriber",  v(m, "subscriber"))
                + row("Application", v(m, "applicationName"))
                + row("Tenant",      v(m, "tenantDomain"))
                + "</table>"
                + "<p style=\"font-size:12px;font-weight:700;color:#b91c1c;text-transform:uppercase;letter-spacing:.8px;\">Requested API Details</p>"
                + "<table>"
                + row("API Name",      v(m, "apiName"))
                + row("Version",       v(m, "apiVersion"))
                + row("Provider",      v(m, "apiProvider"))
                + row("Decision Time", v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\" style=\"background:#fef2f2;border-left:4px solid #ef4444;color:#991b1b;\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_subscription_rejected} template: informs the subscribing
     * developer that their subscription request was reviewed and declined, with an invitation to
     * re-apply with additional context if continued access is needed.
     *
     * <p>Note there is no corresponding {@code publisher_subscription_rejected} template — see
     * {@link CustomSubscriptionExecutor#notify} for why the API publisher is intentionally not
     * notified of rejections.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerSubscriptionRejected(Map<String, Object> m) {
        return head("API Access Request Declined", "#b91c1c", "#ef4444")
                + "<div class=\"header\"><h1>&#128721; API Access Request Declined</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fef2f2;color:#b91c1c;border:1px solid #fca5a5;\">&#10060; DECLINED</span>"
                + "<p>Hi <strong>" + v(m, "subscriber") + "</strong>,</p>"
                + "<p>Your request to subscribe <strong>" + v(m, "applicationName") + "</strong> to <strong>" + v(m, "apiName") + " (v" + v(m, "apiVersion") + ")</strong> was reviewed and declined.</p>"
                + "<table>"
                + row("API Name",          v(m, "apiName"))
                + row("Version",           v(m, "apiVersion"))
                + row("Application",       v(m, "applicationName"))
                + row("Requested Tier",    v(m, "tierName"))
                + row("Decision Time",     v(m, "timestamp"))
                + "</table>"
                + "<p style=\"margin-top:20px;font-size:13px;color:#666;\">If continued access to this API is important for your use case, you're welcome to re-apply with additional context in your request notes.</p>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code admin_application_pending_approval} template: alerts the administrator
     * that a new application creation request needs manual review, with a direct link to the
     * Admin Portal's pending-tasks queue.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminApplicationPendingApproval(Map<String, Object> m) {
        return head("Application Awaiting Your Approval", "#92400e", "#f59e0b")
                + "<div class=\"header\"><h1>&#128221; Application Awaiting Your Approval</h1>"
                + "<p>WSO2 API Manager &mdash; Admin Action Required</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fffbeb;color:#92400e;border:1px solid #fde68a;\">PENDING APPROVAL</span>"
                + "<p>A new application creation request requires your review before it can be approved.</p>"
                + "<table>"
                + row("Application Name", v(m, "applicationName"))
                + row("Requested By",     v(m, "userName"))
                + row("Tenant Domain",    v(m, "tenantDomain"))
                + row("Throttling Tier",  v(m, "applicationTier"))
                + row("Token Type",       v(m, "tokenType"))
                + row("Description",      v(m, "description"))
                + row("Submitted At",     v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:6px;padding:14px 18px;margin-top:20px;font-size:13px;color:#92400e;\">"
                + "&#8505; Review and approve or reject this request from the Admin Portal's Application approval task list."
                + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_application_submitted} template: confirms to the requester
     * that their application creation request was received and is now awaiting administrator
     * review, with measured expectations rather than a "created" confirmation.
     *
     * @param m the data model; see {@link CustomApplicationExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerApplicationSubmitted(Map<String, Object> m) {
        return head("Application Submitted for Approval", "#92400e", "#f59e0b")
                + "<div class=\"header\"><h1>&#128203; Application Submitted for Approval</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fffbeb;color:#92400e;border:1px solid #fde68a;\">&#8987; AWAITING REVIEW</span>"
                + "<p>Hi <strong>" + v(m, "userName") + "</strong>,</p>"
                + "<p>Your request to create the application <strong>" + v(m, "applicationName") + "</strong> has been submitted and is now awaiting administrator approval.</p>"
                + "<table>"
                + row("Application Name",  v(m, "applicationName"))
                + row("Throttling Policy", v(m, "applicationTier"))
                + row("Token Type",        v(m, "tokenType"))
                + row("Description",       v(m, "description"))
                + row("Submitted At",      v(m, "timestamp"))
                + "</table>"
                + "<p style=\"margin-top:20px;font-size:13px;color:#666;\">You'll receive a follow-up email once an administrator has reviewed this request. No further action is needed from you right now.</p>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code admin_subscription_pending_approval} template: alerts the administrator
     * that a new API subscription request needs manual review.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String adminSubscriptionPendingApproval(Map<String, Object> m) {
        return head("Subscription Awaiting Your Approval", "#92400e", "#f59e0b")
                + "<div class=\"header\"><h1>&#128221; Subscription Awaiting Your Approval</h1>"
                + "<p>WSO2 API Manager &mdash; Admin Action Required</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fffbeb;color:#92400e;border:1px solid #fde68a;\">PENDING APPROVAL</span>"
                + "<p>A new API subscription request requires your review before it can be approved.</p>"
                + "<p style=\"font-size:12px;font-weight:700;color:#92400e;text-transform:uppercase;letter-spacing:.8px;\">Subscriber Details</p>"
                + "<table>"
                + row("Subscriber",  v(m, "subscriber"))
                + row("Application", v(m, "applicationName"))
                + row("Tenant",      v(m, "tenantDomain"))
                + "</table>"
                + "<p style=\"font-size:12px;font-weight:700;color:#92400e;text-transform:uppercase;letter-spacing:.8px;\">Requested API Details</p>"
                + "<table>"
                + row("API Name",     v(m, "apiName"))
                + row("Version",      v(m, "apiVersion"))
                + row("Provider",     v(m, "apiProvider"))
                + row("Tier",         v(m, "tierName"))
                + row("Submitted At", v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "<div style=\"background:#fffbeb;border:1px solid #fde68a;border-radius:6px;padding:14px 18px;margin-top:20px;font-size:13px;color:#92400e;\">"
                + "&#8505; Review and approve or reject this request from the Admin Portal's Subscription approval task list."
                + "</div>"
                + "</div>"
                + footer();
    }

    /**
     * Renders the {@code developer_subscription_submitted} template: confirms to the subscribing
     * developer that their subscription request was received and is now awaiting administrator
     * review.
     *
     * <p>Note the API publisher does not receive a "submitted" notification — consistent with the
     * existing rejection-path rule documented on {@link CustomSubscriptionExecutor#notify}, a
     * subscription that hasn't been approved yet has not resulted in a live consumer connecting
     * to the publisher's API.
     *
     * @param m the data model; see {@link CustomSubscriptionExecutor#notify} for the keys populated
     * @return the rendered HTML email body
     */
    private static String developerSubscriptionSubmitted(Map<String, Object> m) {
        return head("Subscription Submitted for Approval", "#92400e", "#f59e0b")
                + "<div class=\"header\"><h1>&#128203; Subscription Submitted for Approval</h1>"
                + "<p>WSO2 API Manager &mdash; Developer Portal</p></div>"
                + "<div class=\"body\">"
                + "<span class=\"badge\" style=\"background:#fffbeb;color:#92400e;border:1px solid #fde68a;\">&#8987; AWAITING REVIEW</span>"
                + "<p>Hi <strong>" + v(m, "subscriber") + "</strong>,</p>"
                + "<p>Your request to subscribe <strong>" + v(m, "applicationName") + "</strong> to <strong>" + v(m, "apiName") + " (v" + v(m, "apiVersion") + ")</strong> has been submitted and is now awaiting administrator approval.</p>"
                + "<table>"
                + row("API Name",      v(m, "apiName"))
                + row("Version",       v(m, "apiVersion"))
                + row("Application",   v(m, "applicationName"))
                + row("Requested Tier", v(m, "tierName"))
                + row("Submitted At",  v(m, "timestamp"))
                + "</table>"
                + "<p style=\"margin-top:20px;font-size:13px;color:#666;\">You'll receive a follow-up email once an administrator has reviewed this request. No further action is needed from you right now.</p>"
                + "</div>"
                + footer();
    }
}