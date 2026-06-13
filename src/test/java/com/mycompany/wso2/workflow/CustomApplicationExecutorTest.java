package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomApplicationExecutorTest {

    private CustomApplicationExecutor executor;
    private ApplicationWorkflowDTO mockWorkflowDTO;

    private MockedStatic<APIUtil> mockedApiUtil;
    private MockedStatic<ServiceReferenceHolder> mockedServiceRefHolder;
    private MockedStatic<EmailUtil> mockedEmailUtil;
    private MockedStatic<ApiMgtDAO> mockedApiMgtDao;

    /**
     * Reinitializes the target workflow executor class and configures mock environments,
     * intercepting structural static dependencies, OSGi services, and underlying database
     * singletons to guarantee isolation before running each test condition.
     *
     * @throws Exception If an error occurs during runtime mock object allocations.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomApplicationExecutor();

        mockWorkflowDTO = mock(ApplicationWorkflowDTO.class);
        Application mockApp = mock(Application.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

        when(mockWorkflowDTO.getApplication()).thenReturn(mockApp);
        when(mockWorkflowDTO.getUserName()).thenReturn("dev_user");
        when(mockWorkflowDTO.getTenantDomain()).thenReturn("carbon.super");
        when(mockWorkflowDTO.getWorkflowReference()).thenReturn("1234");
        when(mockApp.getName()).thenReturn("TestApp");
        when(mockApp.getDescription()).thenReturn("My app description");

        mockedApiUtil = Mockito.mockStatic(APIUtil.class);
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenReturn(-1234);

        RealmService mockRealmService = mock(RealmService.class);
        UserRealm mockUserRealm = mock(UserRealm.class);
        UserStoreManager mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("dev_user"), anyString(), isNull())).thenReturn("dev@example.com");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn("admin@example.com");

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
     * Explicitly releases and terminates all initialized dynamic static mock resource scopes
     * following every test verification cycle to prevent thread context leaks.
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
     * Verifies that the custom executor runs smoothly under optimal operational states,
     * successfully invoking base interceptors and triggering one email payload to the administrator
     * and another to the creator.
     *
     * @throws Exception If an unexpected error arises during data compilation steps.
     */
    @Test
    void testExecuteSuccess() throws Exception {
        WorkflowResponse response = executor.execute(mockWorkflowDTO);

        assertNotNull(response, "Workflow response should not be null");

        mockedEmailUtil.verify(
                () -> EmailUtil.sendHtmlEmail(eq("admin@example.com"), anyString(), eq("admin_application_created"), any(Map.class)),
                Mockito.times(1)
        );
        mockedEmailUtil.verify(
                () -> EmailUtil.sendHtmlEmail(eq("dev@example.com"), anyString(), eq("developer_application_created"), any(Map.class)),
                Mockito.times(1)
        );
    }

    /**
     * Assures structural resilience by forcing a runtime exception inside the user lookup sequence,
     * verifying that interceptor routines gracefully suppress inner faults and return valid tokens.
     *
     * @throws Exception If an unhandled validation exception triggers outside expected thresholds.
     */
    @Test
    void testExecuteWithInternalException() throws Exception {
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenThrow(new RuntimeException("Simulated error"));

        WorkflowResponse response = executor.execute(mockWorkflowDTO);

        assertNotNull(response, "Workflow response should not be null even when emails fail");
    }
}