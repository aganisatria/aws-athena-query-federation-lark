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
package com.amazonaws.glue.lark.base.crawler.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class STSServiceTest {

    @Mock
    private StsClient mockStsClient;

    @InjectMocks
    private STSService stsService;

    @Test
    public void getAccountId_success() {
        GetCallerIdentityResponse mockResponse = GetCallerIdentityResponse.builder()
                .account("123456789012")
                .build();
        when(mockStsClient.getCallerIdentity()).thenReturn(mockResponse);

        String accountId = stsService.getAccountId();

        assertEquals("123456789012", accountId);
        verify(mockStsClient).getCallerIdentity();
    }

    @Test(expected = RuntimeException.class)
    public void getAccountId_sdkThrowsException() {
        when(mockStsClient.getCallerIdentity()).thenThrow(new RuntimeException("AWS SDK Error"));
        stsService.getAccountId();
    }

    @Test
    public void testConstructorWithStsClient() {
        StsClient customClient = mockStsClient;
        STSService service = new STSService(customClient);
        assertNotNull("STSService should be instantiated with custom StsClient", service);
    }

    @Test
    public void testDefaultConstructor() {
        // This test covers the default constructor which calls StsClient.create()
        // It will pass if AWS credentials are properly configured in the environment
        try {
            STSService service = new STSService();
            assertNotNull("STSService should be instantiated with default constructor", service);
        } catch (Exception e) {
            // If AWS credentials are not configured, this test will fail
            // In CI/CD or AWS environment with proper credentials, this should pass
            throw new AssertionError(
                "Default constructor test requires AWS credentials to be configured. " +
                "Error: " + e.getMessage(), e
            );
        }
    }
}
