package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hermetic unit suite for {@link CustomApplicationExecutor}, configured for manual admin
 * approval. Runs on every {@code mvn test} with zero external infrastructure.
 */
class CustomApplicationExecutorTest {

    private CustomApplicationExecutor executor;
    private ApplicationWorkflowDTO mockWorkflowDTO;
    private UserStoreManager mockUserStoreManager;
    private MockedStatic<APIUtil> mockedApiUtil;
    private MockedStatic<ServiceReferenceHolder> mockedServiceRefHolder;
    private MockedStatic<EmailUtil> mockedEmailUtil;
    private MockedStatic<ApiMgtDAO> mockedApiMgtDAO;

    /**
     * Initializes the mock operational runtime environment before every test run execution context.
     *
     * @throws Exception If context initialization variables fail to resolve.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomApplicationExecutor();
        mockWorkflowDTO = mock(ApplicationWorkflowDTO.class);
        Application mockApp = mock(Application.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.CREATED);
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
        mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull()))
                .thenReturn("dev@mycompany.com");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull()))
                .thenReturn("security-audit@mycompany.com");

        APIManagerConfigurationService mockConfigService = mock(APIManagerConfigurationService.class);
        APIManagerConfiguration mockConfig = mock(APIManagerConfiguration.class);
        when(mockConfigService.getAPIManagerConfiguration()).thenReturn(mockConfig);

        ServiceReferenceHolder mockHolder = mock(ServiceReferenceHolder.class);
        when(mockHolder.getRealmService()).thenReturn(mockRealmService);
        when(mockHolder.getAPIManagerConfigurationService()).thenReturn(mockConfigService);

        mockedServiceRefHolder = Mockito.mockStatic(ServiceReferenceHolder.class);
        mockedServiceRefHolder.when(ServiceReferenceHolder::getInstance).thenReturn(mockHolder);

        ApiMgtDAO mockDao = mock(ApiMgtDAO.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockDao.getApplicationById(anyInt())).thenReturn(mockApp);
        when(mockDao.getApplicationByUUID(anyString())).thenReturn(mockApp);

        mockedApiMgtDAO = Mockito.mockStatic(ApiMgtDAO.class);
        mockedApiMgtDAO.when(ApiMgtDAO::getInstance).thenReturn(mockDao);

        mockedEmailUtil = Mockito.mockStatic(EmailUtil.class);
    }

    /**
     * Closes and cleans all static mockery registers to isolate successive test executions.
     */
    @AfterEach
    void tearDown() {
        mockedApiUtil.close();
        mockedServiceRefHolder.close();
        mockedEmailUtil.close();
        mockedApiMgtDAO.close();
    }

    /**
     * Nested verification unit suite isolating structural logic triggered inside workflow executions.
     */
    @Nested
    class ExecuteStage {

        /**
         * Assures that application registration submissions notify both administrators and developers.
         *
         * @throws Exception If message transport definitions fail validation parameters.
         */
        @Test
        void submissionSendsAdminPendingApprovalAndDeveloperSubmittedEmails() throws Exception {
            WorkflowResponse response = executor.execute(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("security-audit@mycompany.com"), anyString(), eq("admin_application_pending_approval"), modelCaptor.capture()));

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
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_submitted"), any()));
        }

        /**
         * Verifies that execution invocation phases never dispatch finished confirmation templates.
         *
         * @throws Exception If testing framework engine encounters execution failures.
         */
        @Test
        void submissionNeverSendsCreatedTemplates() throws Exception {
            executor.execute(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_application_created"), any()), Mockito.never());
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("developer_application_created"), any()), Mockito.never());
        }

        /**
         * Ensures missing administrative claims do not crash workflow initialization routines.
         *
         * @throws Exception If underlying mock configuration layers fail to parse definitions.
         */
        @Test
        void missingAdminClaimSkipsAdminEmailButStillSendsDeveloperEmail() throws Exception {
            when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn(null);

            assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_submitted"), any()));
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_application_pending_approval"), any()), Mockito.never());
        }

        /**
         * Confirms that missing developer identity endpoints do not break administrative notifications.
         *
         * @throws Exception If database query layers raise unexpected access errors.
         */
        @Test
        void missingCreatorClaimSkipsDeveloperEmailButStillSendsAdminEmail() throws Exception {
            when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull())).thenReturn(null);

            assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("security-audit@mycompany.com"), anyString(), eq("admin_application_pending_approval"), any()));
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("developer_application_submitted"), any()), Mockito.never());
        }

        /**
         * Assures system infrastructure connection drops during user verification are caught cleanly.
         */
        @Test
        void exceptionsFromUserStoreAreSuppressedWithoutCrashingExecute() {
            mockedApiUtil.when(() -> APIUtil.getTenantId(anyString()))
                    .thenThrow(new RuntimeException("OSGi Registry Fail"));
            assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));
        }
    }

    /**
     * Nested evaluation group analyzing logic paths executed when processing terminal answers.
     */
    @Nested
    class CompleteStage {

        /**
         * Confirms approval results route exclusively to requesting users, bypassing the admin.
         *
         * @throws Exception If testing engine layers encounter underlying exceptions.
         */
        @Test
        void approvalSendsOnlyDeveloperCreatedEmailWithCompleteModel() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

            WorkflowResponse response = executor.complete(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_created"), modelCaptor.capture()));

            Map<String, Object> capturedModel = modelCaptor.getValue();
            assertEquals("EnterpriseDataRouter", capturedModel.get("applicationName"));
            assertEquals("compliance_developer", capturedModel.get("userName"));
            assertEquals("production.tenant.org", capturedModel.get("tenantDomain"));
            assertEquals("PlatinumHighThroughput", capturedModel.get("applicationTier"));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_application_created"), any()), Mockito.never());
        }

        /**
         * Validates rejection results route exclusively to requesting users, bypassing the admin.
         *
         * @throws Exception If processing execution operations fail unexpectedly.
         */
        @Test
        void rejectionSendsOnlyDeveloperEmailWithCompleteModel() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            WorkflowResponse response = executor.complete(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_rejected"), modelCaptor.capture()));

            Map<String, Object> capturedModel = modelCaptor.getValue();
            assertEquals("EnterpriseDataRouter", capturedModel.get("applicationName"));
            assertEquals("compliance_developer", capturedModel.get("userName"));
            assertEquals("production.tenant.org", capturedModel.get("tenantDomain"));
            assertEquals("PlatinumHighThroughput", capturedModel.get("applicationTier"));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_application_rejected"), any()), Mockito.never());
        }

        /**
         * Assures non-terminal states encountered inside processing endpoints generate zero mail outbox calls.
         *
         * @throws Exception If underlying test structures throw compilation validation blocks.
         */
        @Test
        void createdStatusAtCompleteSendsNoDecisionEmails() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.CREATED);

            executor.complete(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), anyString(), any()), Mockito.never());
        }

        /**
         * Confirms data platform query errors during resolution steps do not block terminal completions.
         */
        @Test
        void exceptionsFromUserStoreAreSuppressedWithoutCrashingComplete() {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);
            mockedApiUtil.when(() -> APIUtil.getTenantId(anyString()))
                    .thenThrow(new RuntimeException("DB Connection Pool Timeout"));
            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));
        }

        /**
         * Assures user address fallback cache resolves destination properties if records are deleted prior to rejections.
         *
         * @throws Exception If mapping variables encounter parsing boundaries.
         */
        @Test
        void rejectionEmailSentFromCacheWhenCreatorRecordAlreadyRemoved() throws Exception {
            executor.execute(mockWorkflowDTO);

            when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_rejected"), any()));
        }

        /**
         * Assures user address fallback cache resolves destination properties if records are deleted prior to approvals.
         *
         * @throws Exception If internal state registers encounter structural alignment errors.
         */
        @Test
        void approvalEmailSentFromCacheWhenCreatorRecordAlreadyRemoved() throws Exception {
            executor.execute(mockWorkflowDTO);

            when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_created"), any()));
        }

        /**
         * Verifies that if the target user directory throws exceptions and the data cache is empty, no messages are sent.
         *
         * @throws Exception If configuration frameworks experience startup synchronization glitches.
         */
        @Test
        void noDeveloperEmailWhenUserStoreThrowsAndCacheIsEmpty() throws Exception {
            when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), anyString(), any()), Mockito.never());
        }

        /**
         * Verifies that transient tracking logs are evacuated completely when execution tasks conclude.
         *
         * @throws Exception If operational synchronization layers fail execution thresholds.
         */
        @Test
        void cacheEntryIsRemovedAfterTerminalStatus() throws Exception {
            executor.execute(mockWorkflowDTO);
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);
            executor.complete(mockWorkflowDTO);

            when(mockUserStoreManager.getUserClaimValue(eq("compliance_developer"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("dev@mycompany.com"), anyString(), eq("developer_application_rejected"), any()), Mockito.times(1));
        }
    }
}