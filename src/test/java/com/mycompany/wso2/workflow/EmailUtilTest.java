package com.mycompany.wso2.workflow;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailUtilTest {

    @Test
    void testAllTemplatesPassThroughAsynchronousMailWorkerWithoutBlocking() throws InterruptedException {
        Map<String, Object> baselineModel = new HashMap<>();
        baselineModel.put("applicationName", "FailproofApp");
        baselineModel.put("userName", "test_user");
        baselineModel.put("tenantDomain", "carbon.super");
        baselineModel.put("subscriber", "test_sub");
        baselineModel.put("apiName", "FailproofAPI");
        baselineModel.put("apiVersion", "1.0.0");
        baselineModel.put("tierName", "Silver");

        String[] testLayoutKeys = {
                "admin_application_created", "developer_application_created",
                "admin_subscription_created", "developer_subscription_created", "publisher_subscription_created",
                "admin_application_rejected", "developer_application_rejected",
                "admin_subscription_rejected", "developer_subscription_rejected"
        };

        for (String activeKey : testLayoutKeys) {
            assertDoesNotThrow(() -> {
                EmailUtil.sendHtmlEmail("sandbox-recipient@company.org", "Asynchronous Integrity Run", activeKey, baselineModel);
            });
        }

        Thread.sleep(600);
    }
}