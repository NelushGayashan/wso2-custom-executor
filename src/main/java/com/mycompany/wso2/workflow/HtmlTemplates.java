package com.mycompany.wso2.workflow;

import java.util.Map;

public class HtmlTemplates {

    public static String render(String templateName, Map<String, Object> model) {
        switch (templateName) {
            case "admin_application_created":    return adminApplicationCreated(model);
            case "developer_application_created": return developerApplicationCreated(model);
            case "admin_subscription_created":   return adminSubscriptionCreated(model);
            case "developer_subscription_created": return developerSubscriptionCreated(model);
            case "publisher_subscription_created": return publisherSubscriptionCreated(model);
            default: return "<html><body><p>No template found: " + escape(templateName) + "</p></body></html>";
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private static String v(Map<String, Object> m, String key) {
        Object val = m.get(key);
        return val != null ? escape(val.toString()) : "-";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String head(String title, String gradientFrom, String gradientTo) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>" + escape(title) + "</title>"
                + "<style>"
                + "body{margin:0;padding:0;background:#f4f6f9;font-family:'Segoe UI',Arial,sans-serif;}"
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
                + ".footer{background:#f4f6f9;padding:16px 32px;text-align:center;font-size:11px;color:#888;border-top:1px solid #e8ecf0;}"
                + "</style></head><body><div class=\"wrapper\">";
    }

    private static String footer() {
        return "<div class=\"footer\">Automated notification from WSO2 API Manager &mdash; Do not reply.</div>"
                + "</div></body></html>";
    }

    private static String row(String label, String value) {
        return "<tr><td>" + escape(label) + "</td><td>" + value + "</td></tr>";
    }

    // ── Templates ───────────────────────────────────────────────────────────

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
                + row("Tier",       v(m, "tier"))
                + row("Timestamp",  v(m, "timestamp"))
                + "</table>"
                + "<div class=\"ref-box\"><strong>Workflow Reference:</strong> " + v(m, "workflowRef") + "</div>"
                + "</div>"
                + footer();
    }

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
                + row("Tier",         v(m, "tier"))
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
                + row("Tier",         v(m, "tier"))
                + row("Tenant",       v(m, "tenantDomain"))
                + row("Subscribed At", v(m, "timestamp"))
                + "</table>"
                + "<div style=\"background:#f0f9ff;border:1px solid #bae6fd;border-radius:6px;padding:14px 18px;margin-top:20px;font-size:13px;color:#0369a1;\">"
                + "&#8505; This subscription was <strong>auto-approved</strong>. Review active subscriptions in the Publisher Portal under your API's <em>Subscriptions</em> tab."
                + "</div>"
                + "</div>"
                + footer();
    }
}