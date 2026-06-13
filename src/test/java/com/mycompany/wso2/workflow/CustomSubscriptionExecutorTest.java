package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfigurationService;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomSubscriptionExecutorTest {

    private CustomSubscriptionExecutor executor;
    private SubscriptionWorkflowDTO mockWorkflowDTO;

    private MockedStatic<APIUtil> mockedApiUtil;
    private MockedStatic<ServiceReferenceHolder> mockedServiceRefHolder;
    private MockedStatic<EmailUtil> mockedEmailUtil;
    private MockedStatic<ApiMgtDAO> mockedApiMgtDao;

    /**
     * Initializes the testing runtime environment prior to each test case execution by building the required
     * mock objects, registering stubbed subscription data parameters, and configuring scoped static simulation
     * boundaries for core WSO2 Carbon subsystem hooks.
     *
     * @throws Exception If an unexpected error scenario occurs during initial mock subsystem initialization phases.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomSubscriptionExecutor();
        mockWorkflowDTO = mock(SubscriptionWorkflowDTO.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);
        when(mockWorkflowDTO.getSubscriber()).thenReturn("dev_user_sub");
        when(mockWorkflowDTO.getApiProvider()).thenReturn("publisher_owner");
        when(mockWorkflowDTO.getApiName()).thenReturn("FinancialLedgerAPI");
        when(mockWorkflowDTO.getApiVersion()).thenReturn("v3.4-beta");
        when(mockWorkflowDTO.getTierName()).thenReturn("GoldTierLimits");
        when(mockWorkflowDTO.getApplicationName()).thenReturn("AccountingDashboard");
        when(mockWorkflowDTO.getTenantDomain()).thenReturn("finance.wso2.local");

        when(mockWorkflowDTO.getWorkflowReference()).thenReturn("776655");

        mockedApiUtil = Mockito.mockStatic(APIUtil.class);
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenReturn(505);

        RealmService mockRealmService = mock(RealmService.class);
        UserRealm mockUserRealm = mock(UserRealm.class);
        UserStoreManager mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("dev_user_sub"), anyString(), isNull())).thenReturn("subscriber@finance.org");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn("admin-audit@finance.org");
        when(mockUserStoreManager.getUserClaimValue(eq("publisher_owner"), anyString(), isNull())).thenReturn("api-owner@finance.org");

        ServiceReferenceHolder mockHolder = mock(ServiceReferenceHolder.class);
        when(mockHolder.getRealmService()).thenReturn(mockRealmService);

        APIManagerConfigurationService mockConfigService = mock(APIManagerConfigurationService.class);
        APIManagerConfiguration mockConfig = mock(APIManagerConfiguration.class);
        when(mockConfigService.getAPIManagerConfiguration()).thenReturn(mockConfig);
        when(mockHolder.getAPIManagerConfigurationService()).thenReturn(mockConfigService);

        mockedServiceRefHolder = Mockito.mockStatic(ServiceReferenceHolder.class);
        mockedServiceRefHolder.when(ServiceReferenceHolder::getInstance).thenReturn(mockHolder);

        mockedEmailUtil = Mockito.mockStatic(EmailUtil.class);

        ApiMgtDAO mockDao = mock(ApiMgtDAO.class);
        mockedApiMgtDao = Mockito.mockStatic(ApiMgtDAO.class);
        mockedApiMgtDao.when(ApiMgtDAO::getInstance).thenReturn(mockDao);
    }

    /**
     * Standard teardown lifecycle routine designed to release static simulation contexts, closed mocks,
     * and thread-local testing reference locks to preserve isolation boundaries between test runs.
     */
    @AfterEach
    void tearDown() {
        mockedApiUtil.close();
        mockedServiceRefHolder.close();
        mockedEmailUtil.close();
        if (mockedApiMgtDao != null) {
            mockedApiMgtDao.close();
        }
    }

    /**
     * Validates that the custom initialization execution process triggers completely, handles mapping rules
     * flawlessly, and successfully generates matching notification profiles containing complete subscription data
     * elements for administrative, publisher, and developer entities.
     *
     * @throws Exception If an unhandled workflow context error bubbles out during the evaluation process.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testExecuteApprovalEnsuresZeroDetailDrop() throws Exception {
        WorkflowResponse response = executor.execute(mockWorkflowDTO);
        assertNotNull(response);

        ArgumentCaptor<Map> modelCaptor = ArgumentCaptor.forClass(Map.class);

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("admin-audit@finance.org"), anyString(), eq("admin_subscription_created"), modelCaptor.capture()), Mockito.times(1));

        Map<String, Object> model = modelCaptor.getValue();
        assertEquals("dev_user_sub", model.get("subscriber"));
        assertEquals("AccountingDashboard", model.get("applicationName"));
        assertEquals("finance.wso2.local", model.get("tenantDomain"));
        assertEquals("FinancialLedgerAPI", model.get("apiName"));
        assertEquals("v3.4-beta", model.get("apiVersion"));
        assertEquals("publisher_owner", model.get("apiProvider"));
        assertEquals("GoldTierLimits", model.get("tierName"));
        assertEquals("776655", model.get("workflowRef"));

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(eq("api-owner@finance.org"), anyString(), eq("publisher_subscription_created"), any(Map.class)), Mockito.times(1));
        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(eq("subscriber@finance.org"), anyString(), eq("developer_subscription_created"), any(Map.class)), Mockito.times(1));
    }

    /**
     * Verifies that the workflow completion path correctly acts upon transactional rejection updates, mapping
     * defensive and contextual template properties completely to alert system audit pools and developer mail accounts.
     *
     * @throws Exception If data mapping errors occur within the validation pipelines.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCompleteRejectionEnsuresZeroDetailDrop() throws Exception {
        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

        WorkflowResponse response = executor.complete(mockWorkflowDTO);
        assertNotNull(response);

        ArgumentCaptor<Map> modelCaptor = ArgumentCaptor.forClass(Map.class);

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("admin-audit@finance.org"), anyString(), eq("admin_subscription_rejected"), modelCaptor.capture()), Mockito.times(1));

        Map<String, Object> model = modelCaptor.getValue();
        assertEquals("dev_user_sub", model.get("subscriber"));
        assertEquals("AccountingDashboard", model.get("applicationName"));
        assertEquals("FinancialLedgerAPI", model.get("apiName"));
        assertEquals("v3.4-beta", model.get("apiVersion"));

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("subscriber@finance.org"), anyString(), eq("developer_subscription_rejected"), any(Map.class)), Mockito.times(1));
    }
}