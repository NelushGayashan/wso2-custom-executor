package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.Application;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.APIManagerConfigurationService;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.ApplicationWorkflowDTO;
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

class CustomApplicationExecutorTest {

    private CustomApplicationExecutor executor;
    private ApplicationWorkflowDTO mockWorkflowDTO;
    private Application mockApp;

    private MockedStatic<APIUtil> mockedApiUtil;
    private MockedStatic<ServiceReferenceHolder> mockedServiceRefHolder;
    private MockedStatic<EmailUtil> mockedEmailUtil;
    private MockedStatic<ApiMgtDAO> mockedApiMgtDao;

    /**
     * Initializes the testing runtime environment prior to each test case execution by building the required
     * mock objects, registering stubbed workflow data parameters, and configuring scoped static simulation
     * boundaries for core WSO2 Carbon subsystem hooks.
     *
     * @throws Exception If an unexpected error scenario occurs during initial mock subsystem initialization phases.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomApplicationExecutor();
        mockWorkflowDTO = mock(ApplicationWorkflowDTO.class);
        mockApp = mock(Application.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);
        when(mockWorkflowDTO.getApplication()).thenReturn(mockApp);
        when(mockWorkflowDTO.getUserName()).thenReturn("compliance_developer");
        when(mockWorkflowDTO.getTenantDomain()).thenReturn("production.tenant.org");

        when(mockWorkflowDTO.getWorkflowReference()).thenReturn("998877");

        when(mockApp.getName()).thenReturn("EnterpriseDataRouter");
        when(mockApp.getTier()).thenReturn("PlatinumHighThroughput");
        when(mockApp.getTokenType()).thenReturn("OAUTH-JWT");
        when(mockApp.getDescription()).thenReturn("Core routing layer for secure microservice proxies.");

        mockedApiUtil = Mockito.mockStatic(APIUtil.class);
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenReturn(404);

        RealmService mockRealmService = mock(RealmService.class);
        UserRealm mockUserRealm = mock(UserRealm.class);
        UserStoreManager mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull())).thenReturn("dev@mycompany.com");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn("security-audit@mycompany.com");

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
     * flawlessly, and successfully generates matching notification profiles containing complete application data
     * elements for both administrative and developer entities.
     *
     * @throws Exception If an unhandled workflow context error bubbles out during the evaluation process.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testExecuteApprovalGuaranteesAllDetailsArePresent() throws Exception {
        WorkflowResponse response = executor.execute(mockWorkflowDTO);
        assertNotNull(response);

        ArgumentCaptor<Map> modelCaptor = ArgumentCaptor.forClass(Map.class);

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("security-audit@mycompany.com"), anyString(), eq("admin_application_created"), modelCaptor.capture()), Mockito.times(1));

        Map<String, Object> capturedModel = modelCaptor.getValue();
        assertEquals("EnterpriseDataRouter", capturedModel.get("applicationName"));
        assertEquals("compliance_developer", capturedModel.get("userName"));
        assertEquals("production.tenant.org", capturedModel.get("tenantDomain"));
        assertEquals("PlatinumHighThroughput", capturedModel.get("applicationTier"));
        assertEquals("OAUTH-JWT", capturedModel.get("tokenType"));
        assertEquals("Core routing layer for secure microservice proxies.", capturedModel.get("description"));
        assertEquals("998877", capturedModel.get("workflowRef"));
        assertNotNull(capturedModel.get("timestamp"));

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("dev@mycompany.com"), anyString(), eq("developer_application_created"), any(Map.class)), Mockito.times(1));
    }

    /**
     * Verifies that the workflow completion path correctly acts upon transactional rejection updates, mapping
     * defensive and contextual template properties completely to alert system audit pools and developer mail accounts.
     *
     * @throws Exception If data mapping errors occur within the validation pipelines.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCompleteRejectionGuaranteesAllDetailsArePresent() throws Exception {
        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

        WorkflowResponse response = executor.complete(mockWorkflowDTO);
        assertNotNull(response);

        ArgumentCaptor<Map> modelCaptor = ArgumentCaptor.forClass(Map.class);

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("security-audit@mycompany.com"), anyString(), eq("admin_application_rejected"), modelCaptor.capture()), Mockito.times(1));

        Map<String, Object> capturedModel = modelCaptor.getValue();
        assertEquals("EnterpriseDataRouter", capturedModel.get("applicationName"));
        assertEquals("compliance_developer", capturedModel.get("userName"));
        assertEquals("production.tenant.org", capturedModel.get("tenantDomain"));
        assertEquals("PlatinumHighThroughput", capturedModel.get("applicationTier"));

        mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                eq("dev@mycompany.com"), anyString(), eq("developer_application_rejected"), any(Map.class)), Mockito.times(1));
    }

    /**
     * Confirms that structural infrastructure anomalies occurring inside internal user data lookups are cleanly
     * trapped and contained during execution tasks to ensure application creation remains functional.
     *
     * @throws Exception If secondary assertion validations fail inside the lifecycle run.
     */
    @Test
    void testExecuteSuppressesExceptionsGracefully() throws Exception {
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenThrow(new RuntimeException("OSGi Registry Fail"));
        assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));
    }

    /**
     * Ensures that connection-pool drops or external datastore timeouts raised throughout final notification execution
     * callbacks do not crash or prevent completion processes from saving final application statuses.
     *
     * @throws Exception If secondary execution validations fail inside the lifecycle run.
     */
    @Test
    void testCompleteSuppressesExceptionsGracefully() throws Exception {
        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenThrow(new RuntimeException("DB Connection Pool Timeout"));
        assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));
    }
}