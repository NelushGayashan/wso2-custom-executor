package com.mycompany.wso2.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.SubscriptionCreationApprovalWorkflowExecutor;
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
 * Custom workflow executor for WSO2 API Manager's subscription-creation lifecycle.
 * Configured for manual administrator approval. Alerts the admin on submission,
 * but only routes terminal updates to the subscriber and API publisher.
 */
@SuppressWarnings("DuplicatedCode")
public class CustomSubscriptionExecutor extends SubscriptionCreationApprovalWorkflowExecutor {

    private static final Log log = LogFactory.getLog(CustomSubscriptionExecutor.class);

    @SuppressWarnings("HttpUrlsUsage")
    private static final String EMAIL_CLAIM_URI = "http://wso2.org/claims/emailaddress";

    private enum NotificationStage {
        SUBMITTED,
        APPROVED,
        REJECTED
    }

    private static final ConcurrentMap<String, String[]> pendingEmailCache = new ConcurrentHashMap<>();

    /**
     * Executes the base subscription registration operations and initial submission alerts.
     *
     * @param workflowDTO Core processing parameters container object.
     * @return Execution response summary data context.
     * @throws WorkflowException If tracking constraints are violated.
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("CustomSubscriptionExecutor: handling subscription creation execute() (pending admin approval)");
        WorkflowResponse response = super.execute(workflowDTO);

        try {
            notify(workflowDTO, NotificationStage.SUBMITTED);
        } catch (Exception e) {
            log.error("Failed to send subscription submission notification emails.", e);
        }
        return response;
    }

    /**
     * Finalizes structural asset allocation context updates from governance metrics.
     * Restores sparse DTO metadata before invoking WSO2 core to prevent Gateway JMS NullPointerExceptions.
     *
     * @param workflowDTO Processing parameters block metadata details.
     * @return Completed payload structural reference state.
     * @throws WorkflowException If external storage engines reject operations.
     */
    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {
        log.info("CustomSubscriptionExecutor: handling subscription creation complete()");

        SubscriptionWorkflowDTO subDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String workflowRef = subDTO.getWorkflowReference();

        if (workflowRef != null) {
            String[] cachedData = pendingEmailCache.get(workflowRef);
            if (cachedData != null && cachedData.length >= 10) {
                if (isBlank(subDTO.getSubscriber())) subDTO.setSubscriber(cachedData[3]);
                if (isBlank(subDTO.getApiName())) subDTO.setApiName(cachedData[4]);
                if (isBlank(subDTO.getApplicationName())) subDTO.setApplicationName(cachedData[5]);
                if (isBlank(subDTO.getApiVersion())) subDTO.setApiVersion(cachedData[6]);
                if (isBlank(subDTO.getApiProvider())) subDTO.setApiProvider(cachedData[7]);
                if (isBlank(subDTO.getTierName())) subDTO.setTierName(cachedData[8]);
                if (isBlank(subDTO.getTenantDomain())) subDTO.setTenantDomain(cachedData[9]);
            }
        }

        WorkflowResponse response = super.complete(workflowDTO);

        try {
            if (WorkflowStatus.APPROVED.equals(workflowDTO.getStatus())) {
                notify(workflowDTO, NotificationStage.APPROVED);
            } else if (WorkflowStatus.REJECTED.equals(workflowDTO.getStatus())) {
                notify(workflowDTO, NotificationStage.REJECTED);
            }
        } catch (Exception e) {
            log.error("Failed to send subscription decision notification emails.", e);
        } finally {
            if (workflowRef != null) {
                pendingEmailCache.remove(workflowRef);
            }
        }
        return response;
    }

    /**
     * Governs routing rules determining exactly which actors receive transactional receipts.
     *
     * @param workflowDTO Identity reference data structure tracking parameters.
     * @param stage Target transactional phase metadata categorization value.
     */
    private void notify(WorkflowDTO workflowDTO, NotificationStage stage) {
        SubscriptionWorkflowDTO subDTO = (SubscriptionWorkflowDTO) workflowDTO;

        String subscriber = isBlank(subDTO.getSubscriber()) ? "Unknown Subscriber" : subDTO.getSubscriber();
        String apiName = isBlank(subDTO.getApiName()) ? "Unknown API" : subDTO.getApiName();
        String applicationName = isBlank(subDTO.getApplicationName()) ? "Unknown Application" : subDTO.getApplicationName();
        String apiVersion = isBlank(subDTO.getApiVersion()) ? "Unknown Version" : subDTO.getApiVersion();
        String apiProvider = isBlank(subDTO.getApiProvider()) ? "Unknown Provider" : subDTO.getApiProvider();
        String tierName = isBlank(subDTO.getTierName()) ? "Unknown Tier" : subDTO.getTierName();

        subDTO.setSubscriber(subscriber);
        subDTO.setApiName(apiName);
        subDTO.setApplicationName(applicationName);
        subDTO.setApiVersion(apiVersion);
        subDTO.setApiProvider(apiProvider);
        subDTO.setTierName(tierName);

        String[] resolvedEmails = resolveEmails(subDTO, stage);
        String subscriberEmail = resolvedEmails[0];
        String adminEmail = resolvedEmails[1];
        String providerEmail = resolvedEmails[2];

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        Map<String, Object> model = new HashMap<>();
        model.put("subscriber", subscriber);
        model.put("applicationName", applicationName);
        model.put("tenantDomain", subDTO.getTenantDomain());
        model.put("apiName", apiName);
        model.put("apiVersion", apiVersion);
        model.put("apiProvider", apiProvider);
        model.put("tierName", tierName);
        model.put("timestamp", timestamp);
        model.put("workflowRef", subDTO.getWorkflowReference());

        switch (stage) {
            case SUBMITTED:
                if (adminEmail != null) {
                    EmailUtil.sendHtmlEmail(adminEmail, "Subscription awaiting your approval", "admin_subscription_pending_approval", model);
                }
                if (subscriberEmail != null) {
                    EmailUtil.sendHtmlEmail(subscriberEmail, "Subscription submitted for approval", "developer_subscription_submitted", model);
                }
                break;
            case REJECTED:
                if (subscriberEmail != null) {
                    EmailUtil.sendHtmlEmail(subscriberEmail, "Your API subscription request was declined", "developer_subscription_rejected", model);
                }
                break;
            case APPROVED:
            default:
                if (providerEmail != null) {
                    EmailUtil.sendHtmlEmail(providerEmail, "Your API has a new subscriber", "publisher_subscription_created", model);
                }
                if (subscriberEmail != null) {
                    EmailUtil.sendHtmlEmail(subscriberEmail, "Subscription confirmed", "developer_subscription_created", model);
                }
                break;
        }
    }

    /**
     * Discovers communication details using available runtime contexts or state caches.
     *
     * @param subDTO Context container storing relational tracking components.
     * @param stage Processing task configuration classification stage.
     * @return Aggregated target endpoint reference array.
     */
    private String[] resolveEmails(SubscriptionWorkflowDTO subDTO, NotificationStage stage) {
        String subscriber = subDTO.getSubscriber();
        String apiProvider = subDTO.getApiProvider();
        String tenantDomain = subDTO.getTenantDomain();
        String workflowRef = subDTO.getWorkflowReference();

        if (stage == NotificationStage.SUBMITTED) {
            String subscriberEmail = getEmailInternally(subscriber, tenantDomain);
            String adminEmail = getEmailInternally("admin", tenantDomain);
            String providerEmail = getEmailInternally(apiProvider, tenantDomain);

            if (workflowRef != null) {
                pendingEmailCache.put(workflowRef, new String[]{
                        subscriberEmail, adminEmail, providerEmail,
                        subscriber, subDTO.getApiName(), subDTO.getApplicationName(),
                        subDTO.getApiVersion(), apiProvider, subDTO.getTierName(),
                        tenantDomain
                });
            }
            return new String[]{subscriberEmail, adminEmail, providerEmail};
        }

        String subscriberEmail = getEmailInternally(subscriber, tenantDomain);
        String adminEmail = getEmailInternally("admin", tenantDomain);
        String providerEmail = getEmailInternally(apiProvider, tenantDomain);

        if (isBlank(subscriberEmail) || isBlank(adminEmail) || isBlank(providerEmail)) {
            String[] cached = workflowRef != null ? pendingEmailCache.get(workflowRef) : null;
            if (cached != null) {
                if (isBlank(subscriberEmail) && cached.length > 0) subscriberEmail = cached[0];
                if (isBlank(adminEmail) && cached.length > 1) adminEmail = cached[1];
                if (isBlank(providerEmail) && cached.length > 2) providerEmail = cached[2];
            }
        }
        return new String[]{subscriberEmail, adminEmail, providerEmail};
    }

    /**
     * Determines whether a structural character reference payload is empty.
     *
     * @param s Target evaluation content reference context.
     * @return True if length or sequence properties resolve null.
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Communicates with external operational directory domains to discover identity markers.
     *
     * @param username Target principal login descriptor label.
     * @param tenantDomain Tenant system container identifier boundary.
     * @return Fully structured destination link value or null.
     */
    private String getEmailInternally(String username, String tenantDomain) {
        if (isBlank(username) || username.startsWith("Unknown")) {
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