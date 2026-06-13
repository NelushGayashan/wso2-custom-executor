# 🚀 WSO2 APIM Custom Workflow Executor – HTML Email Notifications

<p align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![WSO2](https://img.shields.io/badge/WSO2-API_Manager_4.2.0-red)
![Maven](https://img.shields.io/badge/Maven-3.6+-blue)
![OSGi](https://img.shields.io/badge/OSGi-Equinox_3.14-green)
![License](https://img.shields.io/badge/License-MIT-success)

</p>

Production-grade custom workflow executors for **WSO2 API Manager 4.2.0** that automatically intercept API lifecycle events and send rich, professional HTML email notifications whenever:

* ✅ Applications are created inside the Developer Portal.
* ✅ API subscriptions are created by consumers.

This project is engineered specifically for the **WSO2 Carbon / Eclipse Equinox OSGi runtime**. By maintaining **zero external runtime dependencies** (such as Thymeleaf or FreeMarker), it avoids classloader isolation failures (`NoClassDefFoundError`) and guarantees stable deployment.

---

# 📑 Table of Contents

* [Overview](#-overview)
* [Features](#-features)
* [Architecture](#-architecture)
* [Project Structure](#-project-structure)
* [Data Layer & DTO Asymmetry](#-data-layer--dto-asymmetry)
* [Workflow Execution Flow](#-workflow-execution-flow)
* [Technology Stack](#-technology-stack)
* [Prerequisites](#-prerequisites)
* [Quick Start](#-quick-start)
* [Deployment](#-deployment)
* [Configuration](#-configuration)
* [Email Templates](#-email-templates)
* [SMTP Development Setup (MailHog)](#-smtp-development-setup-mailhog)
* [Testing](#-testing)
* [Troubleshooting](#-troubleshooting)
* [Extending the Solution](#-extending-the-solution)
* [License](#-license)
* [Author](#-author)

---

# 📖 Overview

WSO2 API Manager provides a robust workflow extension mechanism allowing custom Java code to hook into lifecycle events. The default out-of-the-box executors simply auto-approve requests silently without auditing or alerting stakeholders.

This project replaces those defaults with custom executors that:
1. **Intercept and Auto-Approve:** Instantly complete transactions using native underlying persistence pathways (`super.execute()`).
2. **Compile Metadata Safely:** Lookup user profiles and resolve target recipient claims using native Carbon UserStore services.
3. **Render Native HTML:** Generate styling and layout structural components safely via zero-dependency pure-Java template string engines.
4. **Dispatch Asynchronously:** Offload mail transfer handling to background workers to completely decouple response times from mail network latencies.

---

# ✨ Features

### 📱 Application Creation Notifications
When a developer sets up an application within the Dev Portal:
* **Admin Auditing:** High-level details are dispatched directly to the infrastructure operations team.
* **Developer Confirmation:** Receipt notification with contextual next-steps links sent directly to the creator.

### 🔗 Subscription Creation Notifications
When a developer binds an application to a published API:
* **Admin Auditing:** Real-time visibility into usage and resource binding paths.
* **API Publisher Alerts:** Notifies the API owner that a new consumer has connected to their resource.
* **Developer Guidance:** Immediate confirmation layout detailing API endpoints, version contexts, and authorization instructions.

### 🛡️ Enterprise Engineering Realities
* **Modernized Template Core:** Built entirely using native Java 17 multi-line Text Blocks (`"""..."""`), eliminating messy multi-line string concatenations for readable HTML maintenance.
* **Asynchronous Execution:** Non-blocking design patterns ensure that SMTP server bottlenecks or connection blackouts cannot impact WSO2 Dev Portal user operations.
* **XSS-Safe Injection:** Internal escaping functions sanitize runtime variables prior to rendering, neutralizing cross-site scripting vulnerabilities within admin mail interfaces.
* **Zero Dependency Tailoring:** Operates natively inside isolated Equinox container runtimes, ensuring deterministic startup profiles across clustered nodes.

---

# 🏗 Architecture

The custom bundle integrates directly with the core WSO2 API Manager engine. When a developer triggers a lifecycle action, WSO2 invokes the corresponding executor using class declarations defined in the server's extension tree configuration.

```text
WSO2 API Manager Runtime Core
│
├── Developer Portal User Action (Application / Subscription Creation)
│   ▼
├── APIConsumerImpl (Processes request state lifecycle)
│   ▼
├── WorkflowExecutorFactory (Resolves configured executor mappings)
│   └── [ YOUR BUNDLE: deployed within repository/components/dropins ]
│       │
│       ├── CustomApplicationExecutor -> Extends ApplicationCreationSimpleWorkflowExecutor
│       └── CustomSubscriptionExecutor -> Extends SubscriptionCreationSimpleWorkflowExecutor
│           │
│           ├── 1. Invoke super.execute() -> Instantly commits state variables to Database
│           ├── 2. Resolve User Stores -> Extracts recipient claim: [http://wso2.org/claims/emailaddress](http://wso2.org/claims/emailaddress)
│           ├── 3. Build Model Contexts -> Maps variables to key-value maps
│           ├── 4. HtmlTemplates.render() -> Builds XSS-sanitized HTML layout string
│           └── 5. EmailUtil.sendHtmlEmail() -> Dispatches asynchronously to background worker
│
└── SMTP Server / MailHog (Receives non-blocking mail delivery)
```

## 🧩 Component Responsibilities

| Component | Primary Responsibility |
|----------|------------------------|
| Developer Portal | Interacts with user behaviors and returns sub-second execution responses. |
| WorkflowExecutorFactory | Dynamically loads and switches routing targets toward custom bundle classes. |
| Custom Executors | Enforce transaction ordering by executing core database commits before entering notification cycles. |
| HtmlTemplates | Sanitizes user inputs and concatenates variables into responsive HTML layouts without external libraries. |
| EmailUtil | Manages background workers to handle connection setup and asynchronous SMTP transfers. |

# 📂 Project Structure

```text
wso2-custom-executor
│
├── pom.xml                        # Strict OSGi manifest generation instructions
├── README.md                      # Comprehensive deployment guide
├── combine_code.py                # Source consolidation code utility
│
├── src
│   ├── main
│   │   └── java
│   │       └── com/mycompany/wso2/workflow
│   │           ├── CustomApplicationExecutor.java  # App creation hook
│   │           ├── CustomSubscriptionExecutor.java # Subscription hook
│   │           ├── EmailUtil.java                 # Async mail transfer layer
│   │           └── HtmlTemplates.java             # Zero-dependency rendering engine
│   │
│   └── test
│       └── java
│           └── com/mycompany/wso2/workflow
│               ├── CustomApplicationExecutorTest.java
│               ├── CustomSubscriptionExecutorTest.java
│               ├── EmailUtilTest.java
│               └── HtmlTemplatesTest.java
│
└── target
    └── com.mycompany.wso2.workflow-1.0.0.jar     # Compiled deployable OSGi bundle
```

# 📊 Data Layer & DTO Asymmetry

When building out new layout components or data parsers, maintain awareness regarding the data structure differences between WSO2's native workflow Data Transfer Objects:

1. Application Creation Mappings (ApplicationWorkflowDTO)
   The application payload encapsulates information inside a nested complex model tree layer. You must navigate into the parent object model to pull primitive tracking fields:

```Java
ApplicationWorkflowDTO appDTO = (ApplicationWorkflowDTO) workflowDTO;
model.put("applicationName", appDTO.getApplication().getName());
model.put("tier", appDTO.getApplication().getTier());
model.put("callbackUrl", appDTO.getApplication().getCallbackUrl());
```

2. Subscription Creation Mappings (SubscriptionWorkflowDTO)
   The subscription payload exposes structural entity keys flattened directly out of the base metadata instance. Attempting to traverse an active .getApplication() layer here will fail:

```Java
SubscriptionWorkflowDTO subDTO = (SubscriptionWorkflowDTO) workflowDTO;
model.put("applicationName", subDTO.getApplicationName());
model.put("apiName", subDTO.getApiName());
model.put("apiVersion", subDTO.getApiVersion());
model.put("tier", subDTO.getTier());
```

# 🔄 Workflow Execution Flow
The sequence diagrams below present the structural ordering logic embedded across both notification variants. The key architectural invariant is: Database commits occur before notification resolution blocks are evaluated.

## Application Creation Chain

```text
DevPortal          CustomApplicationExecutor      WSO2 Core Engine        EmailUtil Worker
    │                          │                          │                      │
    │─── Create App ──────────>│                          │                      │
    │                          │─── super.execute() ─────>│                      │
    │                          │    (Persist & Approve)   │                      │
    │                          │<── Return Status APPROVED│                      │
    │                          │                          │                      │
    │                          │─── Resolve User Claims ─>│                      │
    │                          │─── Render HTML Template ─│                      │
    │                          │                          │                      │
    │                          │─── Submit Task Asynchronously ─────────────────>│
    │<── Return HTTP 201 ──────│                                                 │── [SMTP Tx]
```

## Subscription Creation Chain

```text
DevPortal         CustomSubscriptionExecutor     WSO2 Core Engine        EmailUtil Worker
    │                          │                          │                      │
    │─── Create Subscription ─>│                          │                      │
    │                          │─── super.execute() ─────>│                      │
    │                          │    (Persist & Approve)   │                      │
    │                          │<── Return Status APPROVED│                      │
    │                          │                          │                      │
    │                          │─── Resolve User Claims ─>│                      │
    │                          │─── Render 3 HTML Views ──│                      │
    │                          │                          │                      │
    │                          │─── Submit 3 Tasks Asynchronously -------------->│
    │<── Return HTTP 201 ──────│                                                 │── Admin Mail
    │                          │                                                 │── Publisher Mail
    │                          │                                                 │── Subscriber Mail
```

# 🛠 Technology Stack

- **Compiler Engine Target:** Java SE 17 (Native Java 17 bytecode compliance utilizing modern language features like native Text Blocks).
- **Runtime Environments:** Verified on Java 11 & Java 17 under production WSO2 APIM 4.2.0 servers.
- **Build Architecture:** Maven 3.6+ using the Apache Felix `maven-bundle-plugin` for OSGi packaging.
- **Underlying Mail Provider:** Uses the runtime’s native `javax.mail` package exported by the core application server.
- **Test Platform:** JUnit 5 framework with Mockito Core for simulation and mocking.

# 📋 Prerequisites
Before running compilation sequences, ensure your environment meets the following specifications:

- Java Development Kit (JDK) 17 installed with standard environment paths configured.
- Apache Maven 3.6.3 or newer available on your execution path.
- Access to a target instance of WSO2 API Manager 4.2.0.
- Docker Engine or an alternative mechanism to deploy a MailHog utility instance for verification cycles.  

# ⚡ Quick Start

1. Clone & Access Repository
````bash
git clone [https://github.com/your-org/wso2-custom-executor.git](https://github.com/your-org/wso2-custom-executor.git)
cd wso2-custom-executor
````

2. Run Compilation Pipelines 
   Execute standard verification and packaging clean steps to build out your binary:
````bash
mvn clean package
````

3. Verify Isolation Compliance
   Confirm that no external template parsing engine dependencies have leaked into your compiled archive:
````bash
# Windows
jar tf target/com.mycompany.wso2.workflow-1.0.0.jar | findstr thymeleaf

# Linux / macOS
jar tf target/com.mycompany.wso2.workflow-1.0.0.jar | grep thymeleaf
````

> Expected Outcome: The pipeline should pass cleanly with zero terminal output, confirming that only native company classes populate the artifact.

# 🚀 Deployment

Follow this exact structural ordering to deploy update binaries cleanly onto active cluster instances:

## 1. Purge Active Artifact Mappings

### PowerShell
```powershell
# Windows PowerShell
Remove-Item "${ENV:APIM_HOME}/repository/components/dropins/com.mycompany.wso2.workflow-1.0.0.jar" -ErrorAction SilentlyContinue

# Linux / macOS Bash Systems
rm -f $APIM_HOME/repository/components/dropins/com.mycompany.wso2.workflow-1.0.0.jar
```

## 2. Wipe Active Container Runtime Caches
To prevent class tracking discrepancies or stale metadata issues across Equinox runtimes, explicitly purge transient file trees before firing up node updates:

### PowerShell
```powershell
# Windows PowerShell
Remove-Item -Recurse -Force "${ENV:APIM_HOME}/work/osgi/*"
Remove-Item -Recurse -Force "${ENV:APIM_HOME}/tmp/*"

# Linux / macOS Terminal
rm -rf $APIM_HOME/work/osgi/*
rm -rf $APIM_HOME/tmp/*
```

## 3. Inject New Distribution Target Binaries

### PowerShell
```powershell
# Windows PowerShell
Copy-Item "target/com.mycompany.wso2.workflow-1.0.0.jar" "${ENV:APIM_HOME}/repository/components/dropins/"

# Linux / macOS Terminal
cp target/com.mycompany.wso2.workflow-1.0.0.jar $APIM_HOME/repository/components/dropins/
```

## 4. Execute Clean Node Restoration Sequences

### PowerShell
```powershell
# Windows Server Command Core
cd %APIM_HOME%\bin
api-manager.bat --clean

# Linux / macOS Bash Systems
cd $APIM_HOME/bin
./api-manager.sh --clean
```

# ⚙ Configuration

## 1. Production Integration Declarations (deployment.toml)

Append the following namespace structure directly onto your deployment definition tree file (`<APIM_HOME>/repository/conf/deployment.toml`) to register class injection hooks:
```ini
[apim.workflow_extensions]
application_creation = "com.mycompany.wso2.workflow.CustomApplicationExecutor"
subscription_creation = "com.mycompany.wso2.workflow.CustomSubscriptionExecutor"
```

## 2. Underlying XML Fallback Registries (workflow-extensions.xml)

If your structural environment references older explicit file configurations directly, alter the execution node attributes within `<APIM_HOME>/repository/conf/workflow-extensions.xml`:
```xml
<workFlowExtensions>
   <applicationCreation
           executor="com.mycompany.wso2.workflow.CustomApplicationExecutor"/>

   <subscriptionCreation
           executor="com.mycompany.wso2.workflow.CustomSubscriptionExecutor"/>
</workFlowExtensions>
```

# 📧 Email Templates

The execution bundle targets five distinct contextual templates based on the specific interaction path:

| Template Unique Name | Intended Target Recipient | Contextual Layout Objective |
|---------------------|---------------------------|-----------------------------|
| `admin_application_created` | Operations / Administrator | Full platform audit tracker detailing application names, creator tokens, and account domains. |
| `developer_application_created` | Requesting Developer | Structural confirmation containing links to generation tooling guidelines. |
| `admin_subscription_created` | Operations / Administrator | Platform connection logs detailing explicit application access to published APIs. |
| `publisher_subscription_created` | API Resource Owner | Updates product engineering managers when consumers mount applications to their specific endpoints. |
| `developer_subscription_created` | Requesting Developer | Explains technical token usage patterns and endpoint addressing configurations. |

---

# 📮 SMTP Development Setup (MailHog)

To simplify developer verification runs without routing notifications through live enterprise relays, leverage an isolated containerized MailHog deployment.

## 1. Fire Up Local Interceptor Services

### Bash

```bash
docker run -d \
  -p 1025:1025 \
  -p 8025:8025 \
  --name alert-mailhog \
  mailhog/mailhog
```

## 2. Network Boundary Coordinates

**Inbound Transfer Endpoint (SMTP):**
```text
localhost:1025
```

**Management Inspection Interface (HTTP Portal):**
```text
http://localhost:8025
```

---

# 🧪 Testing

The solution provides a decoupled layout and pipeline test verification tree. The execution tests include advanced static system mocking sequences to simulate WSO2 identity claim resolutions and validate string-building layers safely.

## 1. Run Complete Verification Frameworks

### Bash

```bash
mvn clean test
```

## 2. Extract Comprehensive JaCoCo Reporting Matrices

### Bash
```bash
mvn verify
```

Open the generated visualization tree to evaluate edge coverage profiles:

```text
target/site/jacoco/index.html
```

---

# 🛠 Troubleshooting

## 🛑 NoClassDefFoundError: org/thymeleaf/...

### Root Cause
An engineer embedded third-party compile dependencies inside the project POM tree structure. Eclipse Equinox cannot track nested class registries within raw `dropins/` structures without intricate bundle bridging definitions.

### Remediation
Remove any external layout engine declarations. Revert to using the native variable concatenation methods built into `HtmlTemplates.java`.

---

## 🛑 Custom Code Execution Does Not Trigger

### Root Cause
Typographical class name mismatches inside configuration files, or failure to flush old compilation profiles out of the native Equinox runtime memory space.

### Remediation
Double-check package routing paths in `deployment.toml`. Follow the Purge Cache deployment guidelines to clear server cache directories completely, then start up with the `--clean` flag.

---

## 🛑 E-mails Drop Silently / Missing Fields

### Root Cause
Target platform developer profiles are missing primary system email claim mappings inside the user registry database.

### Remediation
Connect to the Carbon Admin Interface:

```text
https://localhost:9443/carbon
```

Navigate to **Users and Roles**, open user profiles, and verify that the Email Address attributes are properly populated.

---

# 🔧 Extending the Solution

## 1. Adding Alternative Lifecycle Events (e.g., API State Modification)

To extend alternative event contexts (such as notifying subscribers when an API updates from `PUBLISHED` to `DEPRECATED`), replicate the core pattern:

```java
public class CustomAPIStateExecutor extends APIStateChangeSimpleWorkflowExecutor {
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        WorkflowResponse resp = super.execute(workflowDTO);
        // Insert async background notification tasks here
        return resp;
    }
}
```

Append a new string signature mapping layout blocks inside `HtmlTemplates.java`.

Update server mapping tree records using `deployment.toml`.

---

## 2. Externalizing Variables to Production Systems

The design structure uses system property fallback architecture inside its execution core to prevent rebuilding code artifacts across deployment phases:

```java
String smtpHost = System.getProperty("email.smtp.host", "localhost");
String adminUser = System.getProperty("workflow.admin.username", "admin");
```

To adjust these variables at server startup, inject properties into the runtime environment file (`api-manager.sh` or `api-manager.bat`) within the global `JVM_ARGS` collection:

```text
-Demail.smtp.host=smtp.internal-corporate-relay.com -Demail.smtp.port=25 -Dworkflow.admin.username=apim-sys-admin
```

---

# 📜 License

Distributed under the MIT License. For deep structural modifications, reference the `LICENSE` file in the repository root.

---

# 👨‍💻 Author

Engineered to run inside WSO2 API Manager 4.2.0 container execution pools. Designed to mitigate classloader conflicts while delivering asynchronous notification workflows across the platform.