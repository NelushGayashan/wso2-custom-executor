package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.SubscriptionCreationSimpleWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomSubscriptionExecutor extends SubscriptionCreationSimpleWorkflowExecutor {
    private static final Log log = LogFactory.getLog(CustomSubscriptionExecutor.class);

    /**
     * Intercepts the WSO2 API subscription initiation lifecycle, handles standard data persistence
     * mechanisms via the parent class, compiles subscription context parameters, and
     * triggers multiple asynchronous approval HTML email dispatches targeting the system administrator,
     * the API publisher, and the consumer developer.
     *
     * @param workflowDTO The data transfer object containing context regarding the workflow instance.
     * @return The standard workflow verification response indicating success or failure status.
     * @throws WorkflowException If a critical validation error occurs inside the core WSO2 engine.
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("Executing custom HTML interceptor for Subscription Creation Workflow initiation...");
        WorkflowResponse response = super.execute(workflowDTO);

        try {
            // Safe execution context wrapper for automated creation/approval notification sequences
            executeNotificationSequence(workflowDTO, "APPROVED");
        } catch (Exception e) {
            log.error("Failed executing custom subscription approval HTML notification dispatch loops.", e);
        }
        return response;
    }

    /**
     * Intercepts the WSO2 subscription workflow completion phase callback, evaluates if the administrative
     * action represents an explicit task rejection, and maps descriptive metadata fields to dispatch targeted
     * rejection HTML alerts asynchronously to both the reviewing administrator and the requesting subscriber.
     *
     * @param workflowDTO The data transfer object updated with final operational states by the administrative entity.
     * @return The standard workflow verification response confirming state progression persistence.
     * @throws WorkflowException If an internal transactional error occurs within the baseline execution framework.
     */
    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("Executing custom HTML interceptor for Subscription Creation Workflow completion evaluation...");
        WorkflowResponse response = super.complete(workflowDTO);

        try {
            // Evaluate if the task state has transitioned explicitly into a rejected system category
            if (WorkflowStatus.REJECTED.equals(workflowDTO.getStatus())) {
                executeNotificationSequence(workflowDTO, "REJECTED");
            }
        } catch (Exception e) {
            log.error("Failed executing custom subscription rejection HTML notification dispatch loops.", e);
        }
        return response;
    }

    /**
     * Aggregates common metadata parameters from the runtime subscription payload, sanitizes property fields,
     * resolves structural routing rules, and dispatches targeted transactional mail templates based on lifecycle state changes.
     *
     * @param workflowDTO The generic data abstraction envelope populated by the calling gateway context.
     * @param targetStatus A string status representation flag dividing approval notifications from rejection workflows.
     */
    private void executeNotificationSequence(WorkflowDTO workflowDTO, String targetStatus) {
        SubscriptionWorkflowDTO subDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String subscriber = subDTO.getSubscriber();
        String tenantDomain = subDTO.getTenantDomain();
        String apiProvider = subDTO.getApiProvider();

        String subscriberEmail = getEmailInternally(subscriber, tenantDomain);
        String adminEmail = getEmailInternally("admin", tenantDomain);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        Map<String, Object> model = new HashMap<>();
        model.put("subscriber", subscriber);
        model.put("applicationName", subDTO.getApplicationName());
        model.put("tenantDomain", tenantDomain);
        model.put("apiName", subDTO.getApiName());
        model.put("apiVersion", subDTO.getApiVersion());
        model.put("apiProvider", apiProvider);
        model.put("tierName", subDTO.getTierName()); // Aligned to support template property references
        model.put("timestamp", timestamp);
        model.put("workflowRef", subDTO.getWorkflowReference());

        // Select template strategies based on the current lifecycle state mapping parameter
        if ("REJECTED".equals(targetStatus)) {
            if (adminEmail != null) {
                EmailUtil.sendHtmlEmail(adminEmail, "❌ Subscription Request Rejected (Audit Log)", "admin_subscription_rejected", model);
            }
            if (subscriberEmail != null) {
                EmailUtil.sendHtmlEmail(subscriberEmail, "🛑 Notice: Your API Subscription Request Was Declined", "developer_subscription_rejected", model);
            }
        } else {
            String providerEmail = getEmailInternally(apiProvider, tenantDomain);
            if (adminEmail != null) {
                EmailUtil.sendHtmlEmail(adminEmail, "📢 Global Log: New API Subscription Bound", "admin_subscription_created", model);
            }
            if (providerEmail != null) {
                EmailUtil.sendHtmlEmail(providerEmail, "🔥 Alert: Your API gained a new Consumer", "publisher_subscription_created", model);
            }
            if (subscriberEmail != null) {
                EmailUtil.sendHtmlEmail(subscriberEmail, "✓ Connected: API Subscription Active", "developer_subscription_created", model);
            }
        }
    }

    /**
     * Connects to the primary tenant registry domain via active OSGi runtime hooks,
     * initializes the matching User Store manager instance, and extracts the explicit
     * email claim URI mapping associated with the queried target username.
     *
     * @param username The identifier of the specific system user whose email is requested.
     * @param tenantDomain The domain boundary string separating the multi-tenant system space.
     * @return The text value of the email claim mapping if resolved, or null if an error occurs.
     */
    private String getEmailInternally(String username, String tenantDomain) {
        try {
            int tenantId = APIUtil.getTenantId(tenantDomain);
            return ServiceReferenceHolder.getInstance().getRealmService()
                    .getTenantUserRealm(tenantId).getUserStoreManager()
                    .getUserClaimValue(username, "http://wso2.org/claims/emailaddress", null);
        } catch (Exception e) {
            log.error("Failed to fetch internal email identity context for: " + username, e);
            return null;
        }
    }
}