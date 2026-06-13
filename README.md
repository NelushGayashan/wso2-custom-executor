# WSO2 APIM Custom Workflow Executor — HTML Email Notifications

> A production-grade OSGi bundle for WSO2 API Manager 4.2.0 that intercepts application creation and API subscription workflow events and dispatches rich, styled HTML email notifications to all relevant stakeholders — admin, API publisher, and developer — using only the Java standard library and WSO2's built-in runtime services. Zero external dependencies. Zero OSGi classloader conflicts.

---

## Table of Contents

1. [Project Background](#1-project-background)
2. [What This Project Does](#2-what-this-project-does)
3. [Architecture Overview](#3-architecture-overview)
4. [Project Structure](#4-project-structure)
5. [How WSO2 Workflows Work](#5-how-wso2-workflows-work)
6. [Component Deep Dive](#6-component-deep-dive)
   - 6.1 [CustomApplicationExecutor](#61-customapplicationexecutor)
   - 6.2 [CustomSubscriptionExecutor](#62-customsubscriptionexecutor)
   - 6.3 [EmailUtil](#63-emailutil)
   - 6.4 [HtmlTemplates](#64-htmltemplates)
7. [WSO2 API and DTO Reference](#7-wso2-api-and-dto-reference)
8. [OSGi Bundle Mechanics](#8-osgi-bundle-mechanics)
9. [Why No External Dependencies](#9-why-no-external-dependencies)
10. [Prerequisites](#10-prerequisites)
11. [Build Instructions](#11-build-instructions)
12. [Deployment Instructions](#12-deployment-instructions)
13. [WSO2 Configuration](#13-wso2-configuration)
14. [Email Notifications Reference](#14-email-notifications-reference)
15. [SMTP / MailHog Setup](#15-smtp--mailhog-setup)
16. [Switching to Production SMTP](#16-switching-to-production-smtp)
17. [User Email Claim Setup](#17-user-email-claim-setup)
18. [Extending the Project](#18-extending-the-project)
19. [Troubleshooting](#19-troubleshooting)
20. [Key Classes Quick Reference](#20-key-classes-quick-reference)

---

## 1. Project Background

WSO2 API Manager provides a **workflow extension mechanism** that allows custom Java code to intercept lifecycle events such as application creation, API subscription, user signup, API state changes, and more. By default, WSO2 ships simple auto-approving executors for each event type. These do nothing more than immediately approve the action and return.

This project replaces those defaults with custom executors that:

1. Still auto-approve immediately (by delegating to `super.execute()`)
2. Additionally send rich HTML email notifications to all relevant parties

The primary engineering challenge here is the **OSGi deployment model**. WSO2 runs on Eclipse Equinox — a strict OSGi R4 container where every component is an isolated bundle with its own classloader. This prevents naive approaches like embedding Thymeleaf or other third-party libraries, which fail with `NoClassDefFoundError` at runtime due to Equinox's nested JAR classloading limitations. This project solves that problem definitively by using zero external dependencies — all HTML rendering is done with pure Java string building.

---

## 2. What This Project Does

**Application Creation Workflow:**
- Triggered when a developer creates a new application in the WSO2 Developer Portal
- Sends an admin notification email with full application details
- Sends a confirmation email to the developer with next-steps guidance

**Subscription Creation Workflow:**
- Triggered when a developer subscribes an application to an API
- Sends an admin notification with subscriber and API details
- Sends a notification to the API publisher (their API got a new subscriber)
- Sends a confirmation to the subscribing developer with next-steps guidance

**What this project does NOT change:**
- Application creation still auto-approves immediately
- Subscription creation still auto-approves immediately
- No existing WSO2 behavior is altered — notifications are purely additive
- Email failures are caught silently and logged; they never affect the workflow outcome

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     WSO2 API Manager 4.2.0                          │
│                    (Eclipse Equinox OSGi)                           │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Developer Portal — REST API Call                │   │
│  └──────────────────────┬───────────────────────────────────────┘   │
│                         │                                           │
│           ┌─────────────▼──────────────┐                            │
│           │    APIConsumerImpl         │                            │
│           │  .addApplication()         │  (Application Creation)    │
│           │  .addSubscription()        │  (Subscription Creation)   │
│           └─────────────┬──────────────┘                            │
│                         │  calls WorkflowExecutor.execute(DTO)      │
│           ┌─────────────▼──────────────────────────────────────┐    │
│           │        YOUR OSGi BUNDLE (dropins/)                 │    │
│           │                                                    │    │
│           │  CustomApplicationExecutor.execute()               │    │
│           │    ├─ super.execute()          → APPROVED          │    │
│           │    ├─ HtmlTemplates.render()   → HTML string       │    │
│           │    └─ EmailUtil.sendHtmlEmail() → async SMTP       │    │
│           │                                                    │    │
│           │  CustomSubscriptionExecutor.execute()              │    │
│           │    ├─ super.execute()          → APPROVED          │    │
│           │    ├─ HtmlTemplates.render() ×3 → HTML strings     │    │
│           │    └─ EmailUtil.sendHtmlEmail() ×3 → async SMTP    │    │
│           └────────────────────────────────────────────────────┘    │
│                         │                                           │
│           ┌─────────────▼──────────────┐                            │
│           │  Carbon Identity Stack     │                            │
│           │  UserStoreManager          │  email claim lookup        │
│           │  .getUserClaimValue()      │                            │
│           └────────────────────────────┘                            │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ SMTP (localhost:1025)
                          ▼
                   ┌──────────────┐
                   │   MailHog    │  (dev) or real SMTP (prod)
                   │  :8025 UI   │
                   └──────────────┘
```

**Async email dispatch:** A `SingleThreadExecutor` in `EmailUtil` handles all SMTP operations in the background. The WSO2 workflow response is returned to the caller immediately — email delivery time never adds latency to the API response.

---

## 4. Project Structure

```
wso2-custom-executor/
│
├── pom.xml                                          # Maven OSGi bundle descriptor
│
└── src/
    └── main/
        └── java/
            └── com/
                └── mycompany/
                    └── wso2/
                        └── workflow/
                            │
                            ├── CustomApplicationExecutor.java
                            │   # Workflow executor for Application Creation events
                            │   # Extends ApplicationCreationSimpleWorkflowExecutor
                            │
                            ├── CustomSubscriptionExecutor.java
                            │   # Workflow executor for Subscription Creation events
                            │   # Extends SubscriptionCreationSimpleWorkflowExecutor
                            │
                            ├── EmailUtil.java
                            │   # Async SMTP email dispatcher
                            │   # Uses SingleThreadExecutor for non-blocking dispatch
                            │
                            └── HtmlTemplates.java
                                # Pure-Java HTML email renderer
                                # Replaces Thymeleaf — zero external dependencies
```

**Output artifact:** `target/com.mycompany.wso2.workflow-1.0.0.jar`

This JAR is deployed to `<APIM_HOME>/repository/components/dropins/`.

---

## 5. How WSO2 Workflows Work

### 5.1 The Workflow Extension Point

WSO2 API Manager uses a workflow engine to intercept and control lifecycle events. Each event type has a corresponding **workflow executor** class. These are configured in `workflow-extensions.xml` (or via `deployment.toml`).

When an event occurs, WSO2 calls `executor.execute(WorkflowDTO)`. The executor must return a `WorkflowResponse` with one of three statuses:

| Status | Meaning |
|--------|---------|
| `APPROVED` | Event proceeds immediately |
| `REJECTED` | Event is denied |
| `CREATED` | Async — waiting for external callback to approve/reject |

This project uses `APPROVED` (via `super.execute()`) — the simple synchronous auto-approve behavior.

### 5.2 Application Creation Flow

```
1.  Developer opens Dev Portal → Applications → Add New Application
2.  Browser sends POST /api/am/devportal/v2/applications
3.  ApplicationsApiServiceImpl.applicationsPost() is called
4.  → ApplicationsApiServiceImpl.preProcessAndAddApplication()
5.  → APIConsumerImpl.addApplication(Application, subscriber)
6.  → WorkflowExecutorFactory resolves executor from workflow-extensions.xml
7.  → CustomApplicationExecutor.execute(ApplicationWorkflowDTO) ← YOUR CODE
8.      super.execute() → sets WorkflowStatus = APPROVED, persists to DB
9.      getEmailInternally() × 2 → fetches admin + creator email from user store
10.     EmailUtil.sendHtmlEmail() × 2 → submits to background thread
11.     returns WorkflowResponse(APPROVED)
12. Application is created and visible in Dev Portal
```

### 5.3 Subscription Creation Flow

```
1.  Developer opens Dev Portal → Subscriptions → Subscribe to API
2.  Browser sends POST /api/am/devportal/v2/subscriptions
3.  SubscriptionsApiServiceImpl is called
4.  → APIConsumerImpl.addSubscription()
5.  → WorkflowExecutorFactory resolves executor
6.  → CustomSubscriptionExecutor.execute(SubscriptionWorkflowDTO) ← YOUR CODE
7.      super.execute() → sets WorkflowStatus = APPROVED
8.      getEmailInternally() × 3 → admin, provider, subscriber emails
9.      EmailUtil.sendHtmlEmail() × 3 → submitted to background thread
10.     returns WorkflowResponse(APPROVED)
11. Subscription is active — developer can now generate keys
```

### 5.4 WorkflowDTO Inheritance

```
WorkflowDTO  (base — common fields)
    ├── ApplicationWorkflowDTO  (adds: userName, Application object)
    ├── SubscriptionWorkflowDTO (adds: subscriber, apiName, apiVersion, tier, ...)
    ├── UserSignUpWorkflowDTO
    ├── APIStateChangeWorkflowDTO
    └── ... (other event types)
```

---

## 6. Component Deep Dive

### 6.1 CustomApplicationExecutor

```java
public class CustomApplicationExecutor extends ApplicationCreationSimpleWorkflowExecutor
```

**What `ApplicationCreationSimpleWorkflowExecutor` does (the parent):**
- Persists the application to the APIM database
- Sets `WorkflowStatus = APPROVED`
- Returns a `WorkflowResponse` with `APPROVED` status
- Completes synchronously — no external callbacks

**What your override adds:**

```java
@Override
public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
    // 1. Call parent — auto-approves, persists app to DB
    WorkflowResponse response = super.execute(workflowDTO);

    try {
        // 2. Downcast to access application-specific fields
        ApplicationWorkflowDTO appDTO = (ApplicationWorkflowDTO) workflowDTO;

        // 3. Extract identity context
        String creator = appDTO.getUserName();
        String tenantDomain = appDTO.getTenantDomain();

        // 4. Resolve email addresses from WSO2 user store
        String creatorEmail = getEmailInternally(creator, tenantDomain);
        String adminEmail   = getEmailInternally("admin", tenantDomain);

        // 5. Build template variable map
        Map<String, Object> model = new HashMap<>();
        model.put("applicationName", appDTO.getApplication().getName());
        model.put("userName",        creator);
        model.put("tenantDomain",    tenantDomain);
        model.put("applicationTier", appDTO.getApplication().getTier());
        model.put("tokenType",       appDTO.getApplication().getTokenType());
        model.put("description",     appDTO.getApplication().getDescription() != null
                                     ? appDTO.getApplication().getDescription()
                                     : "No description provided.");
        model.put("timestamp",       new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        model.put("workflowRef",     appDTO.getWorkflowReference());

        // 6. Dispatch emails asynchronously
        if (adminEmail != null) {
            EmailUtil.sendHtmlEmail(adminEmail,
                "⚠️ New Application Created",
                "admin_application_created", model);
        }
        if (creatorEmail != null) {
            EmailUtil.sendHtmlEmail(creatorEmail,
                "✓ Application Created Successfully",
                "developer_application_created", model);
        }

    } catch (Exception e) {
        // Email failure must NEVER propagate — workflow approval already happened
        log.error("Failed executing custom application HTML notification dispatch.", e);
    }

    // 7. Return the already-APPROVED response from super
    return response;
}
```

**Why the try-catch wraps everything after `super.execute()`:**
If email lookup or dispatch throws any exception, it is caught and logged. The `response` variable already holds `APPROVED` from `super.execute()`. Returning it regardless means the application is never accidentally blocked due to a notification failure.

---

### 6.2 CustomSubscriptionExecutor

```java
public class CustomSubscriptionExecutor extends SubscriptionCreationSimpleWorkflowExecutor
```

Same pattern as the application executor, with three key differences:

**Three-party notification:**

```java
String subscriberEmail = getEmailInternally(subscriber, tenantDomain);
String adminEmail      = getEmailInternally("admin", tenantDomain);
String providerEmail   = getEmailInternally(apiProvider, tenantDomain);
```

A subscription event is interesting to three parties:
- **Admin** — audit trail, usage monitoring
- **API Provider** — their API gained a new consumer
- **Subscriber** — confirmation they can now generate keys and call the API

**`SubscriptionWorkflowDTO` fields used:**

```java
subDTO.getSubscriber()       // "john" — the developer
subDTO.getTenantDomain()     // "carbon.super"
subDTO.getApiProvider()      // "alice" — the API publisher
subDTO.getApplicationName()  // "MyWeatherApp"
subDTO.getApiName()          // "WeatherAPI"
subDTO.getApiVersion()       // "1.0"
subDTO.getTierName()         // "Gold"
subDTO.getWorkflowReference()// "wf-uuid-..."
```

Note there is no `.getApplication()` object on `SubscriptionWorkflowDTO` — unlike `ApplicationWorkflowDTO`, this DTO only carries the application name as a string, not the full Application model object.

---

### 6.3 EmailUtil

```java
public class EmailUtil {
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    ...
}
```

**Design decisions explained:**

**`newSingleThreadExecutor()` vs `newFixedThreadPool(n)`:**
A single background thread is used intentionally. Emails are sent sequentially, which prevents concurrent SMTP connections and keeps resource usage minimal. In high-load scenarios where many applications/subscriptions are created rapidly, emails queue up and are delivered in order without overwhelming the SMTP server.

**`static final ExecutorService`:**
The executor is a static singleton initialized once per JVM lifetime (per bundle classloader). This means the background thread persists across multiple workflow executions — there's no thread creation overhead per email.

**Anonymous `Runnable` vs lambda:**
Java 8 anonymous inner class syntax is used because the OSGi bundle is compiled with `source/target = 1.8`. Lambda syntax would also work but the anonymous class makes the threading intent explicit.

**The full SMTP dispatch sequence:**

```java
executorService.submit(new Runnable() {
    @Override
    public void run() {
        try {
            // 1. Render the HTML body
            String htmlContent = HtmlTemplates.render(templateName, model);

            // 2. Configure SMTP
            Properties properties = new Properties();
            properties.put("mail.smtp.host", SMTP_HOST);  // "localhost"
            properties.put("mail.smtp.port", SMTP_PORT);  // "1025"

            // 3. Build the message
            Session session = Session.getInstance(properties);
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("noreply@wso2.local"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
            message.setSubject(subject);

            // 4. Set HTML content type — critical for email clients to render HTML
            message.setContent(htmlContent, "text/html; charset=utf-8");

            // 5. Send
            Transport.send(message);
            log.info("HTML email sent to: " + toAddress + " | template: " + templateName);

        } catch (Exception e) {
            log.error("Failed to send HTML email to: " + toAddress, e);
        }
    }
});
```

**`javax.mail` availability in WSO2:**
WSO2 API Manager ships with the JavaMail API (`javax.mail`) in its OSGi runtime. This is declared as `provided` scope in `pom.xml` and listed in `Import-Package` so the bundle uses WSO2's copy rather than trying to embed its own.

---

### 6.4 HtmlTemplates

This class is the most architecturally significant component. It replaces what would typically be a Thymeleaf or FreeMarker template engine with pure Java string concatenation.

**Why pure Java instead of a template engine:**

WSO2 API Manager 4.2.0 runs on Equinox OSGi 3.14. When you deploy a bundle to `dropins/`, Equinox gives it an isolated classloader. Any classes your bundle needs that aren't already in the WSO2 runtime must either be:

1. Exported by another bundle (via `Import-Package`) — works for WSO2's own classes
2. Physically embedded in your JAR with the classloader configured via `Bundle-ClassPath` — theoretically works but Equinox 3.14 has known issues with this for `dropins/` bundles
3. Inlined into your bundle's root (via `Embed-Dependency;inline=true`) — still fails because Equinox's `BundleLoader` does not correctly resolve inlined classes from `dropins/` bundles in this version

The only reliable solution is to have zero third-party dependencies — which is exactly what `HtmlTemplates` achieves.

**Core helper methods:**

```java
// Safe value extractor — returns "-" if key missing or null
private static String v(Map<String, Object> m, String key) {
    Object val = m.get(key);
    return val != null ? escape(val.toString()) : "-";
}

// HTML escaping — prevents XSS from user-supplied data in email clients
private static String escape(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
}

// Shared CSS stylesheet + HTML header — parameterized by gradient colors
private static String head(String title, String gradientFrom, String gradientTo) { ... }

// Shared footer
private static String footer() { ... }

// Table row helper
private static String row(String label, String value) {
    return "<tr><td>" + escape(label) + "</td><td>" + value + "</td></tr>";
}
```

Note that `row(label, value)` escapes the label but NOT the value — this is intentional, because values are already processed through `v()` which calls `escape()`. Double-escaping would corrupt HTML entities.

**Template routing:**

```java
public static String render(String templateName, Map<String, Object> model) {
    switch (templateName) {
        case "admin_application_created":      return adminApplicationCreated(model);
        case "developer_application_created":  return developerApplicationCreated(model);
        case "admin_subscription_created":     return adminSubscriptionCreated(model);
        case "developer_subscription_created": return developerSubscriptionCreated(model);
        case "publisher_subscription_created": return publisherSubscriptionCreated(model);
        default:
            return "<html><body><p>No template found: " + escape(templateName) + "</p></body></html>";
    }
}
```

**Visual design system:** All five templates share a common CSS design system defined in `head()`. Each template is differentiated by its header gradient:

| Template | Gradient | Audience |
|----------|----------|----------|
| `admin_application_created` | Navy → Blue | Admin |
| `developer_application_created` | Blue → Sky | Developer |
| `admin_subscription_created` | Navy → Purple | Admin |
| `developer_subscription_created` | Green → Emerald | Developer |
| `publisher_subscription_created` | Blue → Light Blue | Publisher |

---

## 7. WSO2 API and DTO Reference

### WorkflowDTO (base)
`org.wso2.carbon.apimgt.impl.dto.WorkflowDTO`

| Method | Type | Description |
|--------|------|-------------|
| `getWorkflowReference()` | `String` | Unique UUID for this workflow instance |
| `getTenantDomain()` | `String` | e.g. `"carbon.super"` for default tenant |
| `getWorkflowStatus()` | `WorkflowStatus` | `APPROVED`, `REJECTED`, or `CREATED` |
| `getExternalWorkflowReference()` | `String` | For async workflows — external callback ref |
| `setStatus(WorkflowStatus)` | `void` | Set by executor to signal outcome |
| `getCallbackUrl()` | `String` | Callback URL for async workflows |

### ApplicationWorkflowDTO
`org.wso2.carbon.apimgt.impl.dto.ApplicationWorkflowDTO` extends `WorkflowDTO`

| Method | Type | Description |
|--------|------|-------------|
| `getUserName()` | `String` | Developer username who created the app |
| `getApplication()` | `Application` | Full Application model object |

**`Application` object** (`org.wso2.carbon.apimgt.api.model.Application`):

| Method | Type | Description |
|--------|------|-------------|
| `getName()` | `String` | e.g. `"MyWeatherApp"` |
| `getTier()` | `String` | Throttling tier e.g. `"Unlimited"`, `"Gold"` |
| `getTokenType()` | `String` | `"JWT"` or `"OAUTH"` |
| `getDescription()` | `String` | Optional — may be `null` |
| `getId()` | `int` | Internal DB application ID |
| `getOwner()` | `String` | Application owner username |
| `getGroupId()` | `String` | Application group (for organization-level access) |

### SubscriptionWorkflowDTO
`org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO` extends `WorkflowDTO`

| Method | Type | Description |
|--------|------|-------------|
| `getSubscriber()` | `String` | Subscribing developer's username |
| `getApiProvider()` | `String` | API publisher/owner username |
| `getApplicationName()` | `String` | Application name (string, not object) |
| `getApiName()` | `String` | API name e.g. `"WeatherAPI"` |
| `getApiVersion()` | `String` | e.g. `"1.0"`, `"2.0.0"` |
| `getTierName()` | `String` | Subscription tier e.g. `"Gold"` |
| `getApiContext()` | `String` | API context path e.g. `"/weather/v1"` |

### WorkflowResponse
`org.wso2.carbon.apimgt.api.WorkflowResponse`

Returned by `execute()`. The simple executor sets status to `APPROVED` synchronously. You return it unchanged from `super.execute()`.

### WorkflowException
`org.wso2.carbon.apimgt.impl.workflow.WorkflowException`

Thrown by executors on failure. If your `execute()` throws this, the operation is rolled back by APIM. This is why all notification logic is wrapped in a try-catch — email failures must never cause a `WorkflowException`.

### APIUtil
`org.wso2.carbon.apimgt.impl.utils.APIUtil`

Static utility class. Used here for tenant resolution:

```java
// "carbon.super" → -1234 (super tenant integer ID)
// "myorg.com"    → integer ID for that tenant
int tenantId = APIUtil.getTenantId(String tenantDomain);
```

### ServiceReferenceHolder
`org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder`

OSGi service locator singleton — the central registry for all Carbon platform services. Used to reach the Identity subsystem:

```java
ServiceReferenceHolder.getInstance()
    .getRealmService()                         // org.wso2.carbon.user.core.service.RealmService
    .getTenantUserRealm(tenantId)              // org.wso2.carbon.user.api.UserRealm
    .getUserStoreManager()                     // org.wso2.carbon.user.api.UserStoreManager
    .getUserClaimValue(
        "john",                                // username
        "http://wso2.org/claims/emailaddress", // claim URI
        null                                   // profile — null = default
    );
// Returns: "john@example.com" or null if not configured
```

**Claim URI reference:**
The URI `http://wso2.org/claims/emailaddress` is WSO2's standard dialect for the email attribute. It maps to:
- `mail` attribute in LDAP/Active Directory user stores
- `UM_EMAIL` column in JDBC user stores
- `emails[0].value` in SCIM2

---

## 8. OSGi Bundle Mechanics

### What is an OSGi Bundle?

An OSGi bundle is a regular JAR file with additional metadata in `META-INF/MANIFEST.MF` that tells the OSGi container (Equinox) how to wire it to other bundles.

The `maven-bundle-plugin` generates this manifest automatically from the `<instructions>` block in `pom.xml`.

### Generated MANIFEST.MF

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-SymbolicName: com.mycompany.wso2.workflow
Bundle-Name: WSO2 Custom Workflow Extensions
Bundle-Version: 1.0.0
Export-Package: com.mycompany.wso2.workflow;version="1.0.0"
Import-Package:
 org.wso2.carbon.apimgt.impl.workflow;version="[9.28,10)",
 org.wso2.carbon.apimgt.impl;version="[9.28,10)",
 org.wso2.carbon.apimgt.api;version="[9.28,10)",
 org.wso2.carbon.user.core;version="[4.6,5)",
 org.wso2.carbon.utils;version="[4.6,5)",
 org.apache.commons.logging;version="[1.2,2)",
 javax.mail;version="[1.6,2)",
 ...
```

**`Export-Package`:** Makes `com.mycompany.wso2.workflow.*` visible to other bundles. This is required so WSO2's workflow engine (in a different bundle) can instantiate your executor classes via reflection.

**`Import-Package`:** Declares which packages your bundle requires from the OSGi runtime. Equinox resolves these at bundle activation time. If any required package can't be found, the bundle fails to start.

The `*;resolution:=optional` catch-all handles standard Java packages (`java.util`, `java.text`, etc.) which are always available but not explicitly exported by any bundle.

### dropins/ vs components/lib/

WSO2 provides two ways to add JARs:

| Location | Behavior |
|----------|---------|
| `repository/components/dropins/` | OSGi bundle — manifest is respected, proper classloader isolation |
| `repository/components/lib/` | Plain JAR added to system classloader — no OSGi isolation |

This project deploys to `dropins/` because it needs to extend WSO2's bundle classes (`ApplicationCreationSimpleWorkflowExecutor`), which requires proper OSGi class wiring.

### Bundle Cache

Equinox persists bundle state — including wiring and classloader configuration — in `<APIM_HOME>/work/osgi/`. This cache allows fast startup by not re-resolving bundle dependencies every time. However, it also means that replacing a JAR in `dropins/` without clearing this cache may result in Equinox continuing to use the old bundle's wiring.

**Always clear `work/osgi/*` when redeploying a new bundle version.**

---

## 9. Why No External Dependencies

This section explains the technical root cause of the Thymeleaf classloading failures that were encountered during development, and why pure Java was the correct solution.

**The problem with embedding JARs in OSGi bundles:**

When you use `maven-bundle-plugin` with `Embed-Dependency`, the plugin places the dependency JARs inside your bundle JAR (e.g. `OSGI-INF/lib/thymeleaf.jar`). For the OSGi classloader to find classes inside those nested JARs, two conditions must be met:

1. `Bundle-ClassPath` in the manifest must list the nested JAR path
2. The Equinox classloader must actually scan that path at runtime

Equinox 3.14 (shipped with WSO2 APIM 4.2.0) has a known limitation where it does not reliably scan nested JAR entries for bundles loaded from `dropins/`. The `BundleLoader.findClassInternal()` method at line 512 (as seen in the stack trace) fails to locate `org.thymeleaf.templateresolver.ITemplateResolver` even when the JAR is physically present and `Bundle-ClassPath` lists it.

**The problem with the shade plugin approach:**

An earlier attempt used `maven-shade-plugin` to create a fat JAR with all Thymeleaf classes inlined at the root. This produces a valid fat JAR — but the shade plugin also replaces the OSGi manifest, losing the `Import-Package` declarations needed for WSO2 class resolution. The result is a JAR that either fails OSGi resolution or causes `ClassCastException` when trying to cast to WSO2 types.

**The solution:**

Zero external dependencies. `HtmlTemplates.java` replaces Thymeleaf entirely using Java string building. The resulting bundle has no `Embed-Dependency`, no `Bundle-ClassPath` complications, and no manifest issues. It is a pure OSGi bundle that imports only packages already present in the WSO2 runtime.

---

## 10. Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| WSO2 API Manager | 4.2.0 | Target runtime |
| Java JDK | 11 – 17 | Build and runtime (JDK 17 used in dev) |
| Maven | 3.6+ | Build tool |
| MailHog | Latest | Development SMTP server |

**Install MailHog (Windows):**

Download `MailHog_windows_amd64.exe` from [github.com/mailhog/MailHog/releases](https://github.com/mailhog/MailHog/releases) and run it. It starts automatically on:
- SMTP: `localhost:1025`
- Web UI: `http://localhost:8025`

---

## 11. Build Instructions

```powershell
# Navigate to project root
cd "D:\Education\Programming\Java\wso2-custom-executor"

# Full clean build
mvn clean package

# Verify the JAR contains NO Thymeleaf classes (must print nothing)
jar tf target\com.mycompany.wso2.workflow-1.0.0.jar | findstr thymeleaf

# Verify the OSGi manifest was generated correctly
unzip -p target\com.mycompany.wso2.workflow-1.0.0.jar META-INF/MANIFEST.MF

# Verify your four classes are present
jar tf target\com.mycompany.wso2.workflow-1.0.0.jar | findstr "\.class"
```

**Expected `jar tf | findstr .class` output:**
```
com/mycompany/wso2/workflow/CustomApplicationExecutor.class
com/mycompany/wso2/workflow/CustomSubscriptionExecutor.class
com/mycompany/wso2/workflow/EmailUtil.class
com/mycompany/wso2/workflow/HtmlTemplates.class
```

Nothing else. No `org/thymeleaf/`, no `ognl/`, no `org/attoparser/`.

---

## 12. Deployment Instructions

> **Stop WSO2 completely before running these steps.**

```powershell
# Step 1: Remove old JAR from dropins
Remove-Item "C:\wso2am-4.2.0\repository\components\dropins\com.mycompany.wso2.workflow-1.0.0.jar" `
            -ErrorAction SilentlyContinue

# Step 2: Wipe OSGi bundle cache (CRITICAL — forces full re-scan on next startup)
Remove-Item -Recurse -Force "C:\wso2am-4.2.0\work\osgi\*" -ErrorAction SilentlyContinue

# Step 3: Clear temp directory
Remove-Item -Recurse -Force "C:\wso2am-4.2.0\tmp\*" -ErrorAction SilentlyContinue

# Step 4: Build fresh JAR
cd "D:\Education\Programming\Java\wso2-custom-executor"
mvn clean package

# Step 5: Sanity check — must print nothing
jar tf "target\com.mycompany.wso2.workflow-1.0.0.jar" | findstr thymeleaf

# Step 6: Deploy
Copy-Item "target\com.mycompany.wso2.workflow-1.0.0.jar" `
          "C:\wso2am-4.2.0\repository\components\dropins\"

# Step 7: Start MailHog (separate terminal)
# .\MailHog_windows_amd64.exe

# Step 8: Start WSO2
cd "C:\wso2am-4.2.0\bin"
.\api-manager.bat --clean
```

**Verifying successful bundle activation:**

After WSO2 starts, check `<APIM_HOME>/repository/logs/wso2carbon.log` for:
```
INFO  - CustomApplicationExecutor Executing custom HTML interceptor for Application Creation Workflow...
```

This log line appears the first time an application is created after deployment. If you see it, the bundle loaded and your executor is running.

---

## 13. WSO2 Configuration

### 13.1 Register Custom Executors via deployment.toml

Edit `C:\wso2am-4.2.0\repository\conf\deployment.toml`:

```toml
[apim.workflow_extensions]
application_creation   = "com.mycompany.wso2.workflow.CustomApplicationExecutor"
subscription_creation  = "com.mycompany.wso2.workflow.CustomSubscriptionExecutor"
```

### 13.2 Register via workflow-extensions.xml (alternative)

Edit `C:\wso2am-4.2.0\repository\deployment\server\synapse-configs\default\sequences\workflow-extensions.xml`:

```xml
<workFlowExtensions>
    <applicationCreation
        executor="com.mycompany.wso2.workflow.CustomApplicationExecutor"/>
    <subscriptionCreation
        executor="com.mycompany.wso2.workflow.CustomSubscriptionExecutor"/>

    <!-- Leave other workflow types at their defaults -->
    <userSignUp
        executor="org.wso2.carbon.apimgt.impl.workflow.UserSignUpSimpleWorkflowExecutor"/>
    <applicationRegistration
        executor="org.wso2.carbon.apimgt.impl.workflow.ApplicationRegistrationSimpleWorkflowExecutor"/>
    <apiStateChange
        executor="org.wso2.carbon.apimgt.impl.workflow.APIStateChangeSimpleWorkflowExecutor"/>
    <applicationDeletion
        executor="org.wso2.carbon.apimgt.impl.workflow.ApplicationDeletionSimpleWorkflowExecutor"/>
    <subscriptionDeletion
        executor="org.wso2.carbon.apimgt.impl.workflow.SubscriptionDeletionSimpleWorkflowExecutor"/>
</workFlowExtensions>
```

---

## 14. Email Notifications Reference

### 14.1 admin_application_created

**Trigger:** Any application creation  
**Recipient:** Admin user  
**Subject:** `⚠️ New Application Created`  
**Header color:** Navy → Blue gradient  
**Content:**

| Field | Source DTO method |
|-------|------------------|
| Application Name | `appDTO.getApplication().getName()` |
| Created By | `appDTO.getUserName()` |
| Tenant Domain | `appDTO.getTenantDomain()` |
| Throttling Tier | `appDTO.getApplication().getTier()` |
| Token Type | `appDTO.getApplication().getTokenType()` |
| Description | `appDTO.getApplication().getDescription()` |
| Timestamp | `new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())` |
| Workflow Reference | `appDTO.getWorkflowReference()` |

---

### 14.2 developer_application_created

**Trigger:** Any application creation  
**Recipient:** Developer who created the application  
**Subject:** `✓ Application Created Successfully`  
**Header color:** Blue → Sky gradient  
**Content:** Personalized greeting + application details + next-steps guide

**Next steps included:**
1. Browse available APIs in the Developer Portal
2. Subscribe your application to the APIs you need
3. Generate OAuth 2.0 tokens under your application
4. Start making API calls with your credentials

---

### 14.3 admin_subscription_created

**Trigger:** Any API subscription  
**Recipient:** Admin user  
**Subject:** `🔗 New API Subscription`  
**Header color:** Navy → Purple gradient  
**Content:** Two-section layout — Subscriber Details + API Details

| Section | Fields |
|---------|--------|
| Subscriber Details | Subscriber username, Application name, Tenant domain |
| API Details | API name, Version, Provider, Tier, Timestamp |
| Footer box | Workflow reference UUID |

---

### 14.4 developer_subscription_created

**Trigger:** Any API subscription  
**Recipient:** Subscribing developer  
**Subject:** `✓ Subscription Confirmed`  
**Header color:** Green → Emerald gradient  
**Content:** Personalized confirmation + subscription table + next-steps guide

**Next steps included:**
1. Go to your application in the Developer Portal
2. Generate OAuth 2.0 production / sandbox keys
3. Copy your Consumer Key and Secret
4. Start calling the API using your access token

---

### 14.5 publisher_subscription_created

**Trigger:** Any API subscription  
**Recipient:** API provider (publisher/owner of the subscribed API)  
**Subject:** `🔔 New Subscriber for Your API`  
**Header color:** Blue → Light Blue gradient  
**Content:** Personalized notification with subscriber details + pointer to Publisher Portal Subscriptions tab

---

## 15. SMTP / MailHog Setup

MailHog is a developer SMTP trap — it accepts all emails but delivers none. All captured emails appear in its web UI at `http://localhost:8025`.

**Start MailHog:**
```powershell
.\MailHog_windows_amd64.exe
```

**Verify it's running:**
```
http://localhost:8025     ← Web UI (inbox view)
localhost:1025            ← SMTP endpoint (used by EmailUtil)
```

**Testing the workflow:**
1. Start MailHog
2. Deploy the bundle and start WSO2
3. Ensure test users have email claims configured (see Section 17)
4. Create an application in the Developer Portal
5. Open `http://localhost:8025` — you should see the admin and developer emails arrive

---

## 16. Switching to Production SMTP

To use a real SMTP server, modify `EmailUtil.java`:

**For Gmail (with App Password):**
```java
private static final String SMTP_HOST = "smtp.gmail.com";
private static final String SMTP_PORT = "587";
private static final String SMTP_USER = "yourapp@gmail.com";
private static final String SMTP_PASS = "your-app-password"; // Not your account password

// Replace Session.getInstance(properties) with:
Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
    protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
        return new javax.mail.PasswordAuthentication(SMTP_USER, SMTP_PASS);
    }
});

// Add these properties:
properties.put("mail.smtp.auth", "true");
properties.put("mail.smtp.starttls.enable", "true");
```

**For SendGrid:**
```java
private static final String SMTP_HOST = "smtp.sendgrid.net";
private static final String SMTP_PORT = "587";
private static final String SMTP_USER = "apikey";
private static final String SMTP_PASS = "your-sendgrid-api-key";
// Same Authenticator pattern as above
```

**For internal corporate relay (no auth):**
```java
private static final String SMTP_HOST = "mail.yourcompany.internal";
private static final String SMTP_PORT = "25";
// No auth needed — keep Session.getInstance(properties) without Authenticator
```

---

## 17. User Email Claim Setup

The `getEmailInternally()` method fetches the `http://wso2.org/claims/emailaddress` claim for each user. If this claim is not set, the method returns `null` and no email is sent for that recipient.

**Option 1 — Via Carbon Management Console:**
1. Navigate to `https://localhost:9443/carbon`
2. Go to `Home → Identity → Users and Roles → Users`
3. Click on a user → `User Profile`
4. Set the `Email` field
5. Save

**Option 2 — Via SCIM2 REST API:**
```bash
curl -X PUT https://localhost:9443/scim2/Users/{userId} \
  -H "Authorization: Basic YWRtaW46YWRtaW4=" \
  -H "Content-Type: application/json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "emails": [{"value": "john@example.com", "primary": true}]
  }'
```

**Option 3 — During user creation:**
Include the email when creating users via the Admin REST API or Management Console.

**For the admin user specifically:**
The admin email is fetched with `getEmailInternally("admin", tenantDomain)`. If the admin user in WSO2 has no email claim, admin notifications are silently skipped. Make sure the admin profile has an email set.

---

## 18. Extending the Project

### Adding a New Workflow Event Type

To add notifications for another event type (e.g. API State Change):

1. Create a new executor class:
```java
public class CustomApiStateChangeExecutor extends APIStateChangeSimpleWorkflowExecutor {
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        WorkflowResponse response = super.execute(workflowDTO);
        try {
            // cast to APIStateChangeWorkflowDTO and send notifications
        } catch (Exception e) {
            log.error("...", e);
        }
        return response;
    }
}
```

2. Add a new template method to `HtmlTemplates.java`
3. Add the template name to the `switch` in `HtmlTemplates.render()`
4. Register in `workflow-extensions.xml`

### Adding CC / BCC Recipients

In `EmailUtil.java`, add before `Transport.send(message)`:
```java
message.addRecipients(Message.RecipientType.CC,
    InternetAddress.parse("compliance@yourcompany.com"));
```

### Adding Attachments

Replace `message.setContent(htmlContent, "text/html")` with a `MimeMultipart`:
```java
MimeMultipart multipart = new MimeMultipart();
MimeBodyPart htmlPart = new MimeBodyPart();
htmlPart.setContent(htmlContent, "text/html; charset=utf-8");
multipart.addBodyPart(htmlPart);
// Add attachment parts...
message.setContent(multipart);
```

### Making SMTP Config External

Instead of hardcoding, read from WSO2's configuration system:
```java
// In EmailUtil static initializer:
String smtpHost = System.getProperty("email.smtp.host", "localhost");
String smtpPort = System.getProperty("email.smtp.port", "1025");
```

Then set JVM args in `api-manager.bat`:
```
-Demail.smtp.host=mail.yourcompany.com
-Demail.smtp.port=25
```

---

## 19. Troubleshooting

### Bundle fails to load — `NoClassDefFoundError`

**Symptom:** `java.lang.NoClassDefFoundError: some/Class` in `wso2carbon.log`

**Cause:** A class your bundle depends on couldn't be resolved by Equinox.

**Fix:**
1. Run `jar tf target\com.mycompany.wso2.workflow-1.0.0.jar | findstr thymeleaf` — must print nothing
2. Check `MANIFEST.MF` `Import-Package` lists the failing package
3. Verify `work\osgi\*` was cleared before restart
4. Verify the new JAR was copied to `dropins/` after clearing the cache

---

### Emails not arriving in MailHog

**Checks:**
1. MailHog is running: `http://localhost:8025` opens
2. Check `wso2carbon.log` for `ERROR - EmailUtil` lines
3. Users have email claims set (Section 17)
4. Verify the workflow was triggered: look for `INFO - CustomApplicationExecutor Executing...` in logs

---

### `ClassCastException` at workflowDTO cast

**Symptom:** `java.lang.ClassCastException: WorkflowDTO cannot be cast to ApplicationWorkflowDTO`

**Cause:** Wrong executor registered for the wrong event type in `workflow-extensions.xml`

**Fix:** Verify `applicationCreation` maps to `CustomApplicationExecutor` and `subscriptionCreation` maps to `CustomSubscriptionExecutor` — not swapped.

---

### Old code still running after redeployment

**Symptom:** Log messages still show old behavior after deploying new JAR

**Cause:** Equinox loaded the bundle from its `work/osgi/` cache, not the new JAR

**Fix:** Always delete `work\osgi\*` and `tmp\*` before restarting after a redeployment.

---

### `javax.mail.NoSuchProviderException` or SMTP connection refused

**Cause:** MailHog not running, or wrong SMTP host/port

**Fix:**
1. Start MailHog: `.\MailHog_windows_amd64.exe`
2. Verify `SMTP_HOST = "localhost"` and `SMTP_PORT = "1025"` in `EmailUtil.java`
3. Confirm MailHog is listening: `netstat -an | findstr 1025`

---

### Bundle activates but executor never called

**Symptom:** Bundle loads (no errors at startup) but `CustomApplicationExecutor.execute()` is never called

**Cause:** `workflow-extensions.xml` or `deployment.toml` not updated to point to your class

**Fix:** Double-check the fully qualified class name in the workflow configuration matches exactly:
```
com.mycompany.wso2.workflow.CustomApplicationExecutor
```

---

## 20. Key Classes Quick Reference

| Class | Package | Role in this project |
|-------|---------|---------------------|
| `ApplicationCreationSimpleWorkflowExecutor` | `apimgt.impl.workflow` | Parent class — provides auto-approve logic |
| `SubscriptionCreationSimpleWorkflowExecutor` | `apimgt.impl.workflow` | Parent class — provides auto-approve logic |
| `WorkflowDTO` | `apimgt.impl.dto` | Base DTO carrying common workflow fields |
| `ApplicationWorkflowDTO` | `apimgt.impl.dto` | DTO with app-creation-specific fields |
| `SubscriptionWorkflowDTO` | `apimgt.impl.dto` | DTO with subscription-specific fields |
| `WorkflowResponse` | `apimgt.api` | Return type — carries APPROVED/REJECTED status |
| `WorkflowException` | `apimgt.impl.workflow` | Thrown on executor failure (rolls back event) |
| `Application` | `apimgt.api.model` | Application model — name, tier, token type, etc. |
| `APIUtil` | `apimgt.impl.utils` | Static utility — `getTenantId()` used here |
| `ServiceReferenceHolder` | `apimgt.impl.internal` | OSGi service locator — access to realm service |
| `RealmService` | `carbon.user.core.service` | Identity realm factory — per-tenant user realms |
| `UserRealm` | `carbon.user.api` | Tenant-scoped user management context |
| `UserStoreManager` | `carbon.user.api` | Reads/writes user attributes and claims |

---

*Built for WSO2 API Manager 4.2.0 — Equinox OSGi 3.14 — Java 17*
