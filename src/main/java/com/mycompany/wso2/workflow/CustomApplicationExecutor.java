package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.ApplicationWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.ApplicationCreationSimpleWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomApplicationExecutor extends ApplicationCreationSimpleWorkflowExecutor {
    private static final Log log = LogFactory.getLog(CustomApplicationExecutor.class);

    /**
     * Intercepts the WSO2 application creation lifecycle, triggers the standard automated
     * approval process via the parent class, extracts descriptive metadata, and dispatches
     * customized HTML email notifications asynchronously to both the administrator and the creator.
     *
     * @param workflowDTO The data transfer object containing context regarding the workflow instance.
     * @return The standard workflow verification response indicating success or failure status.
     * @throws WorkflowException If a critical validation error occurs inside the core WSO2 engine.
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("Executing custom HTML interceptor for Application Creation Workflow...");
        WorkflowResponse response = super.execute(workflowDTO);

        try {
            ApplicationWorkflowDTO appDTO = (ApplicationWorkflowDTO) workflowDTO;
            String creator = appDTO.getUserName();
            String tenantDomain = appDTO.getTenantDomain();

            String creatorEmail = getEmailInternally(creator, tenantDomain);
            String adminEmail = getEmailInternally("admin", tenantDomain);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            Map<String, Object> model = new HashMap<>();
            model.put("applicationName", appDTO.getApplication().getName());
            model.put("userName", creator);
            model.put("tenantDomain", tenantDomain);
            model.put("applicationTier", appDTO.getApplication().getTier());
            model.put("tokenType", appDTO.getApplication().getTokenType());
            model.put("description", appDTO.getApplication().getDescription() != null ? appDTO.getApplication().getDescription() : "No description provided.");
            model.put("timestamp", timestamp);
            model.put("workflowRef", appDTO.getWorkflowReference());

            if (adminEmail != null) {
                EmailUtil.sendHtmlEmail(adminEmail, "⚠️ New Application Created", "admin_application_created", model);
            }
            if (creatorEmail != null) {
                EmailUtil.sendHtmlEmail(creatorEmail, "✓ Application Created Successfully", "developer_application_created", model);
            }

        } catch (Exception e) {
            log.error("Failed executing custom application HTML notification dispatch loops.", e);
        }
        return response;
    }

    /**
     * Queries the active tenant user realm via OSGi services to locate the specified
     * user store manager and extract the registered email claim mapping for a user identity.
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