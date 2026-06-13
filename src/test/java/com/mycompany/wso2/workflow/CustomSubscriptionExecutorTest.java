package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     * Reinitializes the subscription workflow executor instance and builds a synthetic test execution
     * matrix by intercepting static utility ecosystems, database singletons, and OSGi identity registry services.
     *
     * @throws Exception If mock environment generation or claim variable allocation triggers an issue.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomSubscriptionExecutor();

        mockWorkflowDTO = mock(SubscriptionWorkflowDTO.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

        when(mockWorkflowDTO.getSubscriber()).thenReturn("dev_user");
        when(mockWorkflowDTO.getApiProvider()).thenReturn("publisher_user");
        when(mockWorkflowDTO.getTenantDomain()).thenReturn("carbon.super");
        when(mockWorkflowDTO.getWorkflowReference()).thenReturn("1234");

        mockedApiUtil = Mockito.mockStatic(APIUtil.class);
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenReturn(-1234);

        RealmService mockRealmService = mock(RealmService.class);
        UserRealm mockUserRealm = mock(UserRealm.class);
        UserStoreManager mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("dev_user"), anyString(), isNull())).thenReturn("dev@example.com");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn("admin@example.com");
        when(mockUserStoreManager.getUserClaimValue(eq("publisher_user"), anyString(), isNull())).thenReturn("pub@example.com");

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
     * Systematically releases and dissolves active scoped static thread handles following
     * the completion of each test verification loop to keep the build runtime isolated.
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
     * Validates subscription intercept routines under clear operational rules, checking that
     * the base activation logic triggers three distinct template notification calls (Admin,
     * Publisher, and Developer).
     *
     * @throws Exception If parameter population patterns fail during the process.
     */
    @Test
    void testExecuteSuccess() throws Exception {
        WorkflowResponse response = executor.execute(mockWorkflowDTO);

        assertNotNull(response, "Workflow response should not be null");

        mockedEmailUtil.verify(
                () -> EmailUtil.sendHtmlEmail(anyString(), anyString(), eq("admin_subscription_created"), any(Map.class)),
                Mockito.times(1)
        );
        mockedEmailUtil.verify(
                () -> EmailUtil.sendHtmlEmail(anyString(), anyString(), eq("publisher_subscription_created"), any(Map.class)),
                Mockito.times(1)
        );
        mockedEmailUtil.verify(
                () -> EmailUtil.sendHtmlEmail(anyString(), anyString(), eq("developer_subscription_created"), any(Map.class)),
                Mockito.times(1)
        );
    }

    /**
     * Assures processing robustness by forcing an runtime error during the tenant tracking steps,
     * verifying that interceptor blocks trap the exception and safely return structural verification tokens.
     *
     * @throws Exception If an unhandled system anomaly breaks the execution scope.
     */
    @Test
    void testExecuteWithInternalException() throws Exception {
        mockedApiUtil.when(() -> APIUtil.getTenantId(anyString())).thenThrow(new RuntimeException("Simulated error"));

        WorkflowResponse response = executor.execute(mockWorkflowDTO);

        assertNotNull(response, "Workflow response should not be null even when emails fail");
    }
}