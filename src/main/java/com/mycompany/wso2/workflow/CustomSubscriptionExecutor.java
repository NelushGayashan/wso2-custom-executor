package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.SubscriptionCreationSimpleWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomSubscriptionExecutor extends SubscriptionCreationSimpleWorkflowExecutor {
    private static final Log log = LogFactory.getLog(CustomSubscriptionExecutor.class);

    /**
     * Intercepts the WSO2 API subscription lifecycle, handles standard data persistence
     * mechanisms via the parent class, compiles subscription context parameters, and
     * triggers multiple asynchronous HTML email dispatches targeting the system administrator,
     * the API publisher, and the consumer developer.
     *
     * @param workflowDTO The data transfer object containing context regarding the workflow instance.
     * @return The standard workflow verification response indicating success or failure status.
     * @throws WorkflowException If a critical validation error occurs inside the core WSO2 engine.
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("Executing custom HTML interceptor for Subscription Creation Workflow...");
        WorkflowResponse response = super.execute(workflowDTO);

        try {
            SubscriptionWorkflowDTO subDTO = (SubscriptionWorkflowDTO) workflowDTO;
            String subscriber = subDTO.getSubscriber();
            String tenantDomain = subDTO.getTenantDomain();
            String apiProvider = subDTO.getApiProvider();

            String subscriberEmail = getEmailInternally(subscriber, tenantDomain);
            String adminEmail = getEmailInternally("admin", tenantDomain);
            String providerEmail = getEmailInternally(apiProvider, tenantDomain);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            Map<String, Object> model = new HashMap<>();
            model.put("subscriber", subscriber);
            model.put("applicationName", subDTO.getApplicationName());
            model.put("tenantDomain", tenantDomain);
            model.put("apiName", subDTO.getApiName());
            model.put("apiVersion", subDTO.getApiVersion());
            model.put("apiProvider", apiProvider);
            model.put("tier", subDTO.getTierName());
            model.put("timestamp", timestamp);
            model.put("workflowRef", subDTO.getWorkflowReference());

            if (adminEmail != null) {
                EmailUtil.sendHtmlEmail(adminEmail, "🔗 New API Subscription", "admin_subscription_created", model);
            }
            if (providerEmail != null) {
                EmailUtil.sendHtmlEmail(providerEmail, "🔔 New Subscriber for Your API", "publisher_subscription_created", model);
            }
            if (subscriberEmail != null) {
                EmailUtil.sendHtmlEmail(subscriberEmail, "✓ Subscription Confirmed", "developer_subscription_created", model);
            }

        } catch (Exception e) {
            log.error("Failed executing custom subscription HTML notification dispatch loops.", e);
        }
        return response;
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