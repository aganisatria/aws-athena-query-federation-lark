package com.amazonaws.glue.lark.base.crawler.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class STSServiceTest {

    @InjectMocks
    private StsClient mockStsClient;

    private STSService stsService;

    @Before
    public void setUp() {
        stsService = new STSService(mockStsClient);
    }

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
}