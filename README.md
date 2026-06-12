# WSO2 APIM Custom Workflow Executor with HTML Email Notifications

A production-grade OSGi bundle that intercepts **WSO2 API Manager 4.2.0** application creation and subscription creation workflow events, then dispatches rich HTML email notifications to all relevant stakeholders (Admin, API Provider, and Developer) using only the Java standard library and WSO2's built-in runtime services.

---

# Table of Contents

* [Overview](#overview)
* [Architecture](#architecture)
* [Project Structure](#project-structure)
* [How WSO2 Workflows Work](#how-wso2-workflows-work)
* [Component Deep Dive](#component-deep-dive)
* [WSO2 API and DTO Reference](#wso2-api-and-dto-reference)
* [OSGi Bundle Mechanics](#osgi-bundle-mechanics)
* [Prerequisites](#prerequisites)
* [Build Instructions](#build-instructions)
* [Deployment Instructions](#deployment-instructions)
* [WSO2 Configuration](#wso2-configuration)
* [Email Notifications Reference](#email-notifications-reference)
* [SMTP / MailHog Setup](#smtp--mailhog-setup)
* [Troubleshooting](#troubleshooting)
* [Logging](#logging)
* [License](#license)

---

# Overview

WSO2 API Manager exposes a workflow extension point that allows custom Java code to be executed during lifecycle events such as application creation and API subscription.

By default, WSO2's built-in simple workflow executors automatically approve these operations. This project extends those executors to send rich HTML email notifications while preserving the existing auto-approval behavior.

## Features

### Application Creation Notifications

When a developer creates an application:

* Sends notification to the Administrator.
* Sends confirmation email to the Developer.
* Includes application metadata and tenant information.

### Subscription Creation Notifications

When a developer subscribes an application to an API:

* Sends notification to the Administrator.
* Sends notification to the API Publisher.
* Sends confirmation email to the Subscriber.

### Technical Characteristics

* Pure Java implementation.
* No Spring Framework.
* No Thymeleaf.
* No external runtime dependencies.
* OSGi-compatible.
* Fully asynchronous email dispatch.
* Safe HTML rendering with escaping.
* Production-ready deployment model.

---

# Architecture

```text
WSO2 APIM Runtime (Equinox OSGi)
│
├── Application Creation Request (Dev Portal)
│   └── APIConsumerImpl.addApplication()
│       └── CustomApplicationExecutor.execute()
│           ├── super.execute()
│           ├── HtmlTemplates.render()
│           └── EmailUtil.sendHtmlEmail() ×2
│
├── Subscription Creation Request (Dev Portal)
│   └── APIConsumerImpl.addSubscription()
│       └── CustomSubscriptionExecutor.execute()
│           ├── super.execute()
│           ├── HtmlTemplates.render()
│           └── EmailUtil.sendHtmlEmail() ×3
│
└── Identity / User Store (Carbon Realm)
    └── UserStoreManager.getUserClaimValue()
```

Email dispatch is performed asynchronously through a background executor thread so that workflow processing remains fast and non-blocking.

---

# Project Structure

```text
wso2-custom-executor/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/mycompany/wso2/workflow/
                ├── CustomApplicationExecutor.java
                ├── CustomSubscriptionExecutor.java
                ├── EmailUtil.java
                └── HtmlTemplates.java
```

| File                            | Purpose                                 |
| ------------------------------- | --------------------------------------- |
| CustomApplicationExecutor.java  | Handles application creation workflows  |
| CustomSubscriptionExecutor.java | Handles subscription creation workflows |
| EmailUtil.java                  | Asynchronous SMTP email sender          |
| HtmlTemplates.java              | Pure Java HTML template renderer        |

---

# How WSO2 Workflows Work

WSO2 API Manager uses workflow executors to control approval processes.

Each workflow type is mapped to a Java class configured through:

```xml
workflow-extensions.xml
```

or

```toml
deployment.toml
```

When an event occurs:

```java
WorkflowResponse execute(WorkflowDTO dto)
```

is invoked.

The executor returns one of:

* APPROVED
* REJECTED
* CREATED / PENDING

The built-in Simple Workflow Executors immediately approve requests.

This project subclasses those executors and adds email notification behavior after approval.

## Application Creation Flow

```text
Developer Creates Application
          │
          ▼
Applications API
          │
          ▼
APIConsumerImpl.addApplication()
          │
          ▼
CustomApplicationExecutor.execute()
          │
          ├─ super.execute()
          ├─ Build Email Model
          ├─ Render HTML
          └─ Send Emails
          │
          ▼
WorkflowResponse(APPROVED)
```

## Subscription Creation Flow

```text
Developer Creates Subscription
          │
          ▼
APIConsumerImpl.addSubscription()
          │
          ▼
CustomSubscriptionExecutor.execute()
          │
          ├─ super.execute()
          ├─ Build Email Model
          ├─ Render HTML
          └─ Send Emails
          │
          ▼
WorkflowResponse(APPROVED)
```

---

# Component Deep Dive

## CustomApplicationExecutor

**Extends**

```java
ApplicationCreationSimpleWorkflowExecutor
```

### Responsibilities

* Cast WorkflowDTO → ApplicationWorkflowDTO
* Extract application metadata
* Resolve developer email
* Resolve admin email
* Build template model
* Trigger email notifications

### Notification Targets

| Recipient | Template                      |
| --------- | ----------------------------- |
| Admin     | admin_application_created     |
| Developer | developer_application_created |

---

## CustomSubscriptionExecutor

**Extends**

```java
SubscriptionCreationSimpleWorkflowExecutor
```

### Responsibilities

* Cast WorkflowDTO → SubscriptionWorkflowDTO
* Resolve subscriber email
* Resolve publisher email
* Resolve administrator email
* Build email model
* Trigger notifications

### Notification Targets

| Recipient  | Template                       |
| ---------- | ------------------------------ |
| Admin      | admin_subscription_created     |
| Publisher  | publisher_subscription_created |
| Subscriber | developer_subscription_created |

---

## EmailUtil

Handles SMTP email delivery.

### Design Goals

* Non-blocking
* Sequential delivery
* Failure isolation

### Internal Architecture

```text
Caller Thread
     │
     ▼
SingleThreadExecutor
     │
     ▼
SMTP Server
```

### SMTP Configuration

```java
private static final String SMTP_HOST = "localhost";
private static final String SMTP_PORT = "1025";
```

For production, replace with:

* Gmail SMTP
* SendGrid
* Office365
* Internal relay

and add authentication settings.

---

## HtmlTemplates

A lightweight HTML rendering engine implemented entirely in Java.

### Why Not Thymeleaf?

WSO2 runs on Equinox OSGi, where embedded template engines often create classloader issues.

Benefits of a pure Java renderer:

* Zero dependencies
* No OSGi wiring problems
* Faster deployment
* Easier maintenance

### Utility Methods

| Method   | Purpose                |
| -------- | ---------------------- |
| escape() | HTML escaping          |
| v()      | Safe value lookup      |
| head()   | Shared HTML header     |
| row()    | Standardized table row |

---

# WSO2 API and DTO Reference

## WorkflowDTO

Package:

```java
org.wso2.carbon.apimgt.impl.dto
```

| Method                         | Return Type    | Description                 |
| ------------------------------ | -------------- | --------------------------- |
| getWorkflowReference()         | String         | Workflow UUID               |
| getTenantDomain()              | String         | Tenant Domain               |
| getWorkflowStatus()            | WorkflowStatus | Workflow State              |
| getExternalWorkflowReference() | String         | External Workflow Reference |

---

## ApplicationWorkflowDTO

| Method           | Return Type |
| ---------------- | ----------- |
| getUserName()    | String      |
| getApplication() | Application |

---

## SubscriptionWorkflowDTO

| Method               | Return Type |
| -------------------- | ----------- |
| getSubscriber()      | String      |
| getApiProvider()     | String      |
| getApplicationName() | String      |
| getApiName()         | String      |
| getApiVersion()      | String      |
| getTierName()        | String      |

---

# Internal Services Used

## APIUtil

Used for:

* Tenant ID resolution
* Tenant metadata access

## ServiceReferenceHolder

Used for:

* Accessing Carbon Realm services
* Resolving user email claims

Example:

```java
getUserClaimValue()
```

---

# OSGi Bundle Mechanics

WSO2 API Manager runs on:

```text
Eclipse Equinox OSGi
```

Every deployable component is an OSGi bundle.

The generated JAR contains:

```text
META-INF/MANIFEST.MF
```

which declares:

* Import-Package
* Export-Package
* Bundle metadata

## Why No Embedded Dependencies?

Embedded libraries frequently cause classloader visibility problems inside Equinox.

Keeping the bundle dependency-free guarantees smooth deployment from:

```text
repository/components/dropins/
```

---

# Prerequisites

| Requirement      | Version |
| ---------------- | ------- |
| WSO2 API Manager | 4.2.0   |
| Java             | 11–17   |
| Maven            | 3.6+    |
| MailHog          | Latest  |

---

# Build Instructions

## Build Bundle

```powershell
cd "D:\Education\Programming\Java\wso2-custom-executor"

mvn clean package
```

## Verify No External Dependencies

```powershell
jar tf target\com.mycompany.wso2.workflow-1.0.0.jar | findstr thymeleaf
```

Expected:

```text
(no output)
```

## Inspect OSGi Manifest

```powershell
unzip -p target\com.mycompany.wso2.workflow-1.0.0.jar META-INF/MANIFEST.MF
```

---

# Deployment Instructions

Perform deployment with WSO2 stopped.

```powershell
# Stop WSO2
Stop-Process -Name "java" -Force -ErrorAction Ignore

# Remove old bundle
Remove-Item `
"C:\wso2am-4.2.0\repository\components\dropins\com.mycompany.wso2.workflow-*.jar" `
-ErrorAction Ignore

# Clear OSGi cache
Remove-Item `
-Recurse `
-Force `
"C:\wso2am-4.2.0\repository\components\configuration" `
-ErrorAction Ignore

# Copy new bundle
Copy-Item `
"D:\Education\Programming\Java\wso2-custom-executor\target\com.mycompany.wso2.workflow-1.0.0.jar" `
"C:\wso2am-4.2.0\repository\components\dropins\"

# Start WSO2
$env:ALLOW_UNSUPPORTED_SKIP_JDK_VERSION_CHECK="true"

cd "C:\wso2am-4.2.0\bin"

.\api-manager.bat --clean
```

---

# WSO2 Configuration

Edit:

```text
C:\wso2am-4.2.0\repository\conf\deployment.toml
```

Add:

```toml
[apim.workflow_extensions]

application_creation =
"com.mycompany.wso2.workflow.CustomApplicationExecutor"

subscription_creation =
"com.mycompany.wso2.workflow.CustomSubscriptionExecutor"
```

---

# User Email Configuration

Email lookup requires the Email claim to be populated.

Navigate to:

```text
Home
 └─ Identity
     └─ Users and Roles
         └─ Users
             └─ User Profile
                 └─ Email
```

---

# Email Notifications Reference

| Event                 | Recipient | Subject                            |
| --------------------- | --------- | ---------------------------------- |
| Application Creation  | Admin     | ⚠️ New Application Created         |
| Application Creation  | Developer | ✓ Application Created Successfully |
| Subscription Creation | Admin     | 🔗 New API Subscription            |
| Subscription Creation | Publisher | 🔔 New Subscriber for Your API     |
| Subscription Creation | Developer | ✓ Subscription Confirmed           |

---

# SMTP / MailHog Setup

Start MailHog:

```powershell
.\MailHog.exe
```

MailHog UI:

```text
http://localhost:8025
```

SMTP endpoint:

```text
localhost:1025
```

All emails will be captured locally and displayed in the MailHog dashboard.

---

# Troubleshooting

## NoClassDefFoundError

Possible causes:

* Missing Import-Package resolution.
* Old OSGi cache.
* Wrong JAR deployed.

Verify:

```text
repository/components/configuration
```

was removed before restart.

---

## Emails Not Being Sent

Verify:

* MailHog is running.
* SMTP host and port are correct.
* User email claim is populated.
* WSO2 logs contain no EmailUtil errors.

---

## ClassCastException on WorkflowDTO

Occurs when an executor receives an unexpected DTO type.

Verify:

```toml
deployment.toml
```

maps workflow types correctly.

---

## Old Code Still Running

Equinox caches bundle wiring.

Always delete:

```text
repository/components/configuration
```

before redeployment.

---

# Logging

To enable debug logging:

File:

```text
repository/conf/log4j2.properties
```

Add:

```properties
logger.custom_executor.name = com.mycompany.wso2.workflow
logger.custom_executor.level = DEBUG
```

---

# License

MIT License

Copyright (c) 2026

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.
