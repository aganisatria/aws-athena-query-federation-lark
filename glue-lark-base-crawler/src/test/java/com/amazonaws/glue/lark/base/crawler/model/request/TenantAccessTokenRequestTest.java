/*-
 * #%L
 * glue-lark-base-crawler
 * %%
 * Copyright (C) 2019 - 2025 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.amazonaws.glue.lark.base.crawler.model.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class TenantAccessTokenRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void recordCreation_shouldHoldValues() {
        String appId = "app123";
        String appSecret = "secret456";
        TenantAccessTokenRequest request = new TenantAccessTokenRequest(appId, appSecret);

        assertEquals(appId, request.appId());
        assertEquals(appSecret, request.appSecret());
    }

    @Test
    public void jacksonSerialization_shouldProduceCorrectJson() throws Exception {
        TenantAccessTokenRequest request = new TenantAccessTokenRequest("idTest", "secretTest");
        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"app_id\":\"idTest\""));
        assertTrue(json.contains("\"app_secret\":\"secretTest\""));
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        TenantAccessTokenRequest request1a = new TenantAccessTokenRequest("id1", "s1");
        TenantAccessTokenRequest request1b = new TenantAccessTokenRequest("id1", "s1");
        TenantAccessTokenRequest request2 = new TenantAccessTokenRequest("id2", "s2");

        assertEquals(request1a, request1b);
        assertNotEquals(request1a, request2);
        assertEquals(request1a.hashCode(), request1b.hashCode());
    }
}
