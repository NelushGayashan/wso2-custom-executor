package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.ApplicationWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.ApplicationCreationApprovalWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.user.core.UserStoreException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Custom workflow executor for WSO2 API Manager's application-creation lifecycle.
 * Configured for manual administrator approval. Alerts the admin on submission,
 * but only notifies the requesting developer on final approval or rejection.
 */
public class CustomApplicationExecutor extends ApplicationCreationApprovalWorkflowExecutor {

    private static final Log log = LogFactory.getLog(CustomApplicationExecutor.class);

    @SuppressWarnings("HttpUrlsUsage")
    private static final String EMAIL_CLAIM_URI = "http://wso2.org/claims/emailaddress";

    private enum NotificationStage {
        SUBMITTED,
        APPROVED,
        REJECTED
    }

    private static final ConcurrentMap<String, String[]> pendingEmailCache = new ConcurrentHashMap<>();

    /**
     * Executes the workflow process upon initial application submission.
     *
     * @param workflowDTO The workflow data transfer object.
     * @return The response payload of the executed workflow.
     * @throws WorkflowException If a management exception occurs.
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("CustomApplicationExecutor: handling application creation execute() (pending admin approval)");
        WorkflowResponse response = super.execute(workflowDTO);

        try {
            notify(workflowDTO, NotificationStage.SUBMITTED);
        } catch (Exception e) {
            log.error("Failed to send application submission notification emails.", e);
        }
        return response;
    }

    /**
     * Completes the workflow cycle upon administrative approval or rejection.
     *
     * @param workflowDTO The workflow data transfer object.
     * @return The execution response payload.
     * @throws WorkflowException If an implementation error occurs.
     */
    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("CustomApplicationExecutor: handling application creation complete()");
        WorkflowResponse response = super.complete(workflowDTO);

        try {
            if (WorkflowStatus.APPROVED.equals(workflowDTO.getStatus())) {
                notify(workflowDTO, NotificationStage.APPROVED);
            } else if (WorkflowStatus.REJECTED.equals(workflowDTO.getStatus())) {
                notify(workflowDTO, NotificationStage.REJECTED);
            }
        } catch (Exception e) {
            log.error("Failed to send application decision notification emails.", e);
        } finally {
            String workflowRef = workflowDTO.getWorkflowReference();
            if (workflowRef != null) {
                pendingEmailCache.remove(workflowRef);
            }
        }
        return response;
    }

    /**
     * Handles contextual generation and dispatch of dynamic HTML notifications.
     *
     * @param workflowDTO The active workflow context data payload.
     * @param stage The lifecycle state step triggering notifications.
     */
    private void notify(WorkflowDTO workflowDTO, NotificationStage stage) {
        ApplicationWorkflowDTO appDTO = (ApplicationWorkflowDTO) workflowDTO;
        String workflowRef = appDTO.getWorkflowReference();
        String tenantDomain = appDTO.getTenantDomain();

        String creator = appDTO.getUserName();
        String appName = (appDTO.getApplication() != null) ? appDTO.getApplication().getName() : null;

        if (workflowRef != null) {
            String[] cachedData = pendingEmailCache.get(workflowRef);
            if (cachedData != null) {
                if ((creator == null || creator.trim().isEmpty()) && cachedData.length > 2 && cachedData[2] != null) {
                    creator = cachedData[2];
                }
                if ((appName == null || appName.trim().isEmpty()) && cachedData.length > 3 && cachedData[3] != null) {
                    appName = cachedData[3];
                }
            }
        }

        if (creator == null || creator.trim().isEmpty()) creator = "Unknown Developer";
        if (appName == null || appName.trim().isEmpty()) appName = "Unknown Application";

        String[] resolvedEmails = resolveEmails(creator, tenantDomain, workflowRef, appName, stage);
        String creatorEmail = resolvedEmails[0];
        String adminEmail = resolvedEmails[1];

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> model = new HashMap<>();
        model.put("userName", creator);
        model.put("tenantDomain", tenantDomain);
        model.put("timestamp", timestamp);
        model.put("workflowRef", workflowRef);
        model.put("applicationName", appName);

        if (appDTO.getApplication() != null) {
            model.put("applicationTier", appDTO.getApplication().getTier());
            model.put("tokenType", appDTO.getApplication().getTokenType());
            model.put("description", appDTO.getApplication().getDescription());
        } else {
            model.put("applicationTier", "Default");
            model.put("tokenType", "Default");
            model.put("description", "No description available during completion step.");
        }

        switch (stage) {
            case SUBMITTED:
                if (adminEmail != null) {
                    EmailUtil.sendHtmlEmail(adminEmail, "Application awaiting your approval", "admin_application_pending_approval", model);
                }
                if (creatorEmail != null) {
                    EmailUtil.sendHtmlEmail(creatorEmail, "Application submitted for approval", "developer_application_submitted", model);
                }
                break;
            case REJECTED:
                if (creatorEmail != null) {
                    EmailUtil.sendHtmlEmail(creatorEmail, "Your application request was declined", "developer_application_rejected", model);
                }
                break;
            case APPROVED:
            default:
                if (creatorEmail != null) {
                    EmailUtil.sendHtmlEmail(creatorEmail, "Application created successfully", "developer_application_created", model);
                }
                break;
        }
    }

    /**
     * Extracts and validates user-store notification endpoints across system boundaries.
     *
     * @param creator Identity of the requesting principal.
     * @param tenantDomain The domain of the tenant system context.
     * @param workflowRef The tracking string identifier.
     * @param appName The structural entity configuration label.
     * @param stage The target state metadata flag.
     * @return A parsed array of resolved destination addresses.
     */
    private String[] resolveEmails(String creator, String tenantDomain, String workflowRef, String appName, NotificationStage stage) {
        if (stage == NotificationStage.SUBMITTED) {
            String creatorEmail = getEmailInternally(creator, tenantDomain);
            String adminEmail = getEmailInternally("admin", tenantDomain);

            if (workflowRef != null) {
                pendingEmailCache.put(workflowRef, new String[]{creatorEmail, adminEmail, creator, appName});
            }
            return new String[]{creatorEmail, adminEmail};
        }

        String creatorEmail = getEmailInternally(creator, tenantDomain);
        String adminEmail = getEmailInternally("admin", tenantDomain);

        if (creatorEmail == null || creatorEmail.isEmpty() || adminEmail == null || adminEmail.isEmpty()) {
            String[] cached = workflowRef != null ? pendingEmailCache.get(workflowRef) : null;
            if (cached != null && cached.length > 1) {
                if (creatorEmail == null || creatorEmail.isEmpty()) creatorEmail = cached[0];
                if (adminEmail == null || adminEmail.isEmpty()) adminEmail = cached[1];
            }
        }
        return new String[]{creatorEmail, adminEmail};
    }

    /**
     * Queries internal carbon directory managers for live user claim attributes.
     *
     * @param username Target unique identity reference.
     * @param tenantDomain Context system domain configuration.
     * @return Fully resolved string destination or null.
     */
    private String getEmailInternally(String username, String tenantDomain) {
        if (username == null || username.trim().isEmpty() || "Unknown Developer".equals(username)) {
            return null;
        }
        try {
            int tenantId = APIUtil.getTenantId(tenantDomain);
            return ServiceReferenceHolder.getInstance().getRealmService()
                    .getTenantUserRealm(tenantId).getUserStoreManager()
                    .getUserClaimValue(username, EMAIL_CLAIM_URI, null);
        } catch (UserStoreException e) {
            log.warn("Could not resolve live email claim for user: " + username);
            return null;
        } catch (Exception e) {
            log.error("Failed to resolve email for user: " + username, e);
            return null;
        }
    }
}