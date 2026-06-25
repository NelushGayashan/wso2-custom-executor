package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hermetic unit suite for {@link CustomSubscriptionExecutor}, configured for manual admin
 * approval testing.
 */
class CustomSubscriptionExecutorTest {

    private CustomSubscriptionExecutor executor;
    private SubscriptionWorkflowDTO mockWorkflowDTO;
    private UserStoreManager mockUserStoreManager;
    private MockedStatic<APIUtil> mockedApiUtil;
    private MockedStatic<ServiceReferenceHolder> mockedServiceRefHolder;
    private MockedStatic<EmailUtil> mockedEmailUtil;
    private MockedStatic<ApiMgtDAO> mockedApiMgtDAO;

    /**
     * Bootstraps mock operational environments, dependency injection interfaces, and simulated data schemas.
     *
     * @throws Exception If runtime testing constructs fail to initialize mock containers.
     */
    @BeforeEach
    void setUp() throws Exception {
        executor = new CustomSubscriptionExecutor();
        mockWorkflowDTO = mock(SubscriptionWorkflowDTO.class);

        when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.CREATED);
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
        mockUserStoreManager = mock(UserStoreManager.class);

        when(mockRealmService.getTenantUserRealm(anyInt())).thenReturn(mockUserRealm);
        when(mockUserRealm.getUserStoreManager()).thenReturn(mockUserStoreManager);

        when(mockUserStoreManager.getUserClaimValue(eq("dev_user_sub"), anyString(), isNull()))
                .thenReturn("subscriber@finance.org");
        when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull()))
                .thenReturn("admin-audit@finance.org");
        when(mockUserStoreManager.getUserClaimValue(eq("publisher_owner"), anyString(), isNull()))
                .thenReturn("api-owner@finance.org");

        APIManagerConfigurationService mockConfigService = mock(APIManagerConfigurationService.class);
        APIManagerConfiguration mockConfig = mock(APIManagerConfiguration.class);
        when(mockConfigService.getAPIManagerConfiguration()).thenReturn(mockConfig);

        ServiceReferenceHolder mockHolder = mock(ServiceReferenceHolder.class);
        when(mockHolder.getRealmService()).thenReturn(mockRealmService);
        when(mockHolder.getAPIManagerConfigurationService()).thenReturn(mockConfigService);

        mockedServiceRefHolder = Mockito.mockStatic(ServiceReferenceHolder.class);
        mockedServiceRefHolder.when(ServiceReferenceHolder::getInstance).thenReturn(mockHolder);

        ApiMgtDAO mockDao = mock(ApiMgtDAO.class, Mockito.RETURNS_DEEP_STUBS);
        mockedApiMgtDAO = Mockito.mockStatic(ApiMgtDAO.class);
        mockedApiMgtDAO.when(ApiMgtDAO::getInstance).thenReturn(mockDao);

        mockedEmailUtil = Mockito.mockStatic(EmailUtil.class);
    }

    /**
     * Deconstructs static framework mocking to avoid interference across test methods.
     */
    @AfterEach
    void tearDown() {
        mockedApiUtil.close();
        mockedServiceRefHolder.close();
        mockedEmailUtil.close();
        mockedApiMgtDAO.close();
    }

    /**
     * Functional test suite validating initial creation triggers and message constraints.
     */
    @Nested
    class ExecuteStage {

        /**
         * Verifies that subscription requests queue messages for administrators and requesting developers.
         *
         * @throws Exception If capture mappings trigger underlying exception blocks.
         */
        @Test
        void submissionSendsAdminPendingApprovalAndSubscriberSubmittedEmails() throws Exception {
            WorkflowResponse response = executor.execute(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("admin-audit@finance.org"), anyString(), eq("admin_subscription_pending_approval"), modelCaptor.capture()));

            Map<String, Object> model = modelCaptor.getValue();
            assertEquals("dev_user_sub", model.get("subscriber"));
            assertEquals("AccountingDashboard", model.get("applicationName"));
            assertEquals("finance.wso2.local", model.get("tenantDomain"));
            assertEquals("FinancialLedgerAPI", model.get("apiName"));
            assertEquals("v3.4-beta", model.get("apiVersion"));
            assertEquals("publisher_owner", model.get("apiProvider"));
            assertEquals("GoldTierLimits", model.get("tierName"));
            assertEquals("776655", model.get("workflowRef"));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("subscriber@finance.org"), anyString(), eq("developer_subscription_submitted"), any()));
        }

        /**
         * Verifies API publishers do not receive emails during the pending approval stage.
         *
         * @throws Exception If framework engines fail verification routines.
         */
        @Test
        void submissionNeverSendsAPublisherEmail() throws Exception {
            executor.execute(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("publisher_subscription_created"), any()), Mockito.never());
        }

        /**
         * Assures the submission lifecycle state does not prematurely trigger approval layout dispatches.
         *
         * @throws Exception If configuration engines experience compilation faults.
         */
        @Test
        void submissionNeverSendsCreatedTemplates() throws Exception {
            executor.execute(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_subscription_created"), any()), Mockito.never());
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("developer_subscription_created"), any()), Mockito.never());
        }

        /**
         * Verifies that absence of a valid administrative account limits notifications but continues processing.
         *
         * @throws Exception If mock state manipulations trigger unsupported runtime conditions.
         */
        @Test
        void missingAdminClaimSkipsAdminEmailOnly() throws Exception {
            when(mockUserStoreManager.getUserClaimValue(eq("admin"), anyString(), isNull())).thenReturn(null);

            assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_subscription_pending_approval"), any()), Mockito.never());
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("subscriber@finance.org"), anyString(), eq("developer_subscription_submitted"), any()));
        }

        /**
         * Ensures that system infrastructure issues during claim lookup are swallowed cleanly.
         */
        @Test
        void exceptionsFromUserStoreAreSuppressedWithoutCrashingExecute() {
            mockedApiUtil.when(() -> APIUtil.getTenantId(anyString()))
                    .thenThrow(new RuntimeException("OSGi Registry Fail"));
            assertDoesNotThrow(() -> executor.execute(mockWorkflowDTO));
        }
    }

    /**
     * Functional test suite validating administrative decision updates across the system components.
     */
    @Nested
    class CompleteStage {

        /**
         * Validates that API approval notifications execute only against publishers and developers.
         *
         * @throws Exception If context validation routines map to invalid constraints.
         */
        @Test
        void approvalSendsOnlyPublisherAndSubscriberCreatedEmailsWithCompleteModel() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

            WorkflowResponse response = executor.complete(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("api-owner@finance.org"), anyString(), eq("publisher_subscription_created"), modelCaptor.capture()));

            Map<String, Object> model = modelCaptor.getValue();
            assertEquals("dev_user_sub", model.get("subscriber"));
            assertEquals("AccountingDashboard", model.get("applicationName"));
            assertEquals("FinancialLedgerAPI", model.get("apiName"));
            assertEquals("v3.4-beta", model.get("apiVersion"));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("subscriber@finance.org"), anyString(), eq("developer_subscription_created"), any()));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_subscription_created"), any()), Mockito.never());
        }

        /**
         * Verifies rejection actions isolate feedback strictly to requesting developers.
         *
         * @throws Exception If test execution parameters encounter unexpected values.
         */
        @Test
        void rejectionSendsOnlySubscriberEmailsWithCompleteModel() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            WorkflowResponse response = executor.complete(mockWorkflowDTO);
            assertNotNull(response);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("subscriber@finance.org"), anyString(), eq("developer_subscription_rejected"), modelCaptor.capture()));

            Map<String, Object> model = modelCaptor.getValue();
            assertEquals("dev_user_sub", model.get("subscriber"));
            assertEquals("AccountingDashboard", model.get("applicationName"));
            assertEquals("FinancialLedgerAPI", model.get("apiName"));
            assertEquals("v3.4-beta", model.get("apiVersion"));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("admin_subscription_rejected"), any()), Mockito.never());
        }

        /**
         * Confirms that subscription blockades avoid broadcasting irrelevant alerts to the API owners.
         *
         * @throws Exception If testing engine encounters parameter mapping failures.
         */
        @Test
        void rejectionDoesNotSendAPublisherEmail() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            executor.complete(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("publisher_subscription_created"), any()), Mockito.never());
            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), eq("publisher_subscription_rejected"), any()), Mockito.never());
        }

        /**
         * Validates intermediate statuses do not emit terminal confirmation messaging patterns.
         *
         * @throws Exception If dependency injection frameworks fail verification paths.
         */
        @Test
        void createdStatusAtCompleteSendsNoDecisionEmails() throws Exception {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.CREATED);

            executor.complete(mockWorkflowDTO);

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), anyString(), any()), Mockito.never());
        }

        /**
         * Assures structural connection exceptions occurring in database layers do not compromise pipeline results.
         */
        @Test
        void exceptionsFromUserStoreAreSuppressedWithoutCrashingComplete() {
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);
            mockedApiUtil.when(() -> APIUtil.getTenantId(anyString()))
                    .thenThrow(new RuntimeException("DB Connection Pool Timeout"));
            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));
        }

        /**
         * Validates email destinations are parsed from local storage indices if active directories lose matching account entries.
         *
         * @throws Exception If testing boundaries hit parsing constraint limits.
         */
        @Test
        void rejectionEmailSentFromCacheWhenSubscriberRecordAlreadyRemoved() throws Exception {
            executor.execute(mockWorkflowDTO);

            when(mockUserStoreManager.getUserClaimValue(eq("dev_user_sub"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("subscriber@finance.org"), anyString(), eq("developer_subscription_rejected"), any()));
        }

        /**
         * Validates email destinations are compiled from local indices if the publisher account is disabled or absent.
         *
         * @throws Exception If object inspection tasks fail structural comparisons.
         */
        @Test
        void approvalEmailSentFromCacheWhenPublisherRecordAlreadyRemoved() throws Exception {
            executor.execute(mockWorkflowDTO);

            when(mockUserStoreManager.getUserClaimValue(eq("publisher_owner"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.APPROVED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    eq("api-owner@finance.org"), anyString(), eq("publisher_subscription_created"), any()));
        }

        /**
         * Verifies that blank indices and crashed data streams execute operations without blocking workflows.
         *
         * @throws Exception If system configuration rules raise validation errors.
         */
        @Test
        void noSubscriberEmailWhenUserStoreThrowsAndCacheIsEmpty() throws Exception {
            when(mockUserStoreManager.getUserClaimValue(eq("dev_user_sub"), anyString(), isNull()))
                    .thenThrow(new UserStoreException("30007 - UserNotFound"));
            when(mockWorkflowDTO.getStatus()).thenReturn(WorkflowStatus.REJECTED);

            assertDoesNotThrow(() -> executor.complete(mockWorkflowDTO));

            mockedEmailUtil.verify(() -> EmailUtil.sendHtmlEmail(
                    anyString(), anyString(), anyString(), any()), Mockito.never());
        }
    }
}