package com.amazonaws.glue.lark.base.crawler.service;

import com.amazonaws.glue.lark.base.crawler.model.request.TenantAccessTokenRequest;
import com.amazonaws.glue.lark.base.crawler.model.response.TenantAccessTokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CommonLarkServiceTest {

    private static final String TEST_APP_ID = "testAppId";
    private static final String TEST_APP_SECRET = "testAppSecret";

    @Spy
    private CommonLarkService commonLarkService = new CommonLarkService(TEST_APP_ID, TEST_APP_SECRET);

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private ObjectMapper mockObjectMapper;

    @Mock
    private HttpResponse mockHttpResponse;

    @Mock
    private HttpEntity mockHttpEntity;

    @Before
    public void setUp() {
        commonLarkService.httpClient = mockHttpClient;
        commonLarkService.objectMapper = mockObjectMapper;
        commonLarkService.tenantAccessToken = null;
        commonLarkService.tokenExpiry = 0;
    }

    @Test
    public void refreshTenantAccessToken_noNeedToRefresh_tokenIsValid() {
        commonLarkService.tenantAccessToken = "validToken";
        commonLarkService.tokenExpiry = System.currentTimeMillis() + (60 * 1000);

        assertDoesNotThrow(() -> commonLarkService.refreshTenantAccessToken());

        try {
            verify(mockHttpClient, never()).execute(any(HttpPost.class));
            verify(mockObjectMapper, never()).writeValueAsString(any());
            verify(mockObjectMapper, never()).readValue(anyString(), eq(TenantAccessTokenResponse.class));
        } catch (IOException e) {
            fail("Exception tidak diharapkan: " + e.getMessage());
        }
    }

    @Test
    public void refreshTenantAccessToken_success() throws IOException {
        String mockJsonResponse = "{\"code\":0,\"msg\":\"success\",\"tenant_access_token\":\"mock_token\",\"expire\":7200}";
        TenantAccessTokenResponse mockTokenResponse = new TenantAccessTokenResponse(0, "success", 7200, "mock_token");

        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{\"app_id\":\"testAppId\",\"app_secret\":\"testAppSecret\"}");
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockTokenResponse);

        commonLarkService.refreshTenantAccessToken();

        assertNotNull(commonLarkService.tenantAccessToken);
        assertEquals("mock_token", commonLarkService.tenantAccessToken);
        assertTrue(commonLarkService.tokenExpiry > System.currentTimeMillis());

        verify(mockHttpClient, times(1)).execute(any(HttpPost.class));
        verify(mockObjectMapper, times(1)).writeValueAsString(any(TenantAccessTokenRequest.class));
        verify(mockObjectMapper, times(1)).readValue(mockJsonResponse, TenantAccessTokenResponse.class);
    }

    @Test
    public void refreshTenantAccessToken_forceRefresh_whenTokenIsNull() throws IOException {
        commonLarkService.tenantAccessToken = null;
        String mockJsonResponse = "{\"code\":0,\"msg\":\"success\",\"tenant_access_token\":\"refreshed_token\",\"expire\":3600}";
        TenantAccessTokenResponse mockTokenResponse = new TenantAccessTokenResponse(0, "success", 3600, "refreshed_token");
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockTokenResponse);

        commonLarkService.refreshTenantAccessToken();

        assertEquals("refreshed_token", commonLarkService.tenantAccessToken);
        verify(mockHttpClient, times(1)).execute(any(HttpPost.class));
    }

    @Test
    public void refreshTenantAccessToken_forceRefresh_whenTokenIsExpired() throws IOException {
        commonLarkService.tenantAccessToken = "expiredToken";
        commonLarkService.tokenExpiry = System.currentTimeMillis() - 1000;
        String mockJsonResponse = "{\"code\":0,\"msg\":\"success\",\"tenant_access_token\":\"new_refreshed_token\",\"expire\":3600}";
        TenantAccessTokenResponse mockTokenResponse = new TenantAccessTokenResponse(0, "success", 3600, "new_refreshed_token");
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockTokenResponse);

        commonLarkService.refreshTenantAccessToken();

        assertEquals("new_refreshed_token", commonLarkService.tenantAccessToken);
        verify(mockHttpClient, times(1)).execute(any(HttpPost.class));
    }


    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_httpClientThrowsIOException() throws IOException {
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new IOException("HTTP execute failed"));

        commonLarkService.refreshTenantAccessToken();
    }

    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_objectMapperWriteValueThrowsException() throws IOException {
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class)))
                .thenThrow(new JsonProcessingException("Jackson write error"){});

        commonLarkService.refreshTenantAccessToken();
    }

    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_entityUtilsToStringThrowsException() throws IOException {
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenThrow(new IOException("Error reading entity content"));

        commonLarkService.refreshTenantAccessToken();
    }


    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_objectMapperReadValueThrowsException() throws IOException {
        String mockJsonResponse = "{\"code\":0,...}";
        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class)))
                .thenThrow(new JsonProcessingException("Jackson read error"){});

        commonLarkService.refreshTenantAccessToken();
    }

    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_larkApiReturnsErrorCode() throws IOException {
        String mockErrorJsonResponse = "{\"code\":10001,\"msg\":\"app ticket invalid\",\"tenant_access_token\":\"\",\"expire\":0}";
        TenantAccessTokenResponse mockErrorTokenResponse = new TenantAccessTokenResponse(10001, "app ticket invalid", 0, "");

        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockErrorJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockErrorTokenResponse);

        commonLarkService.refreshTenantAccessToken();
    }

    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_larkApiReturnsSuccessCodeButEmptyToken() throws IOException {
        String mockEmptyTokenJsonResponse = "{\"code\":0,\"msg\":\"success\",\"tenant_access_token\":\"\",\"expire\":7200}";
        TenantAccessTokenResponse mockEmptyTokenResponse = new TenantAccessTokenResponse(0, "success", 7200, ""); // Token kosong

        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockEmptyTokenJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockEmptyTokenResponse);

        commonLarkService.refreshTenantAccessToken();
    }

    @Test(expected = IOException.class)
    public void refreshTenantAccessToken_larkApiReturnsSuccessCodeButNullToken() throws IOException {
        TenantAccessTokenResponse mockNullTokenResponse = new TenantAccessTokenResponse(0, "success", 7200, null);

        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class))).thenReturn("{}");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        String mockJsonResponseForNullToken = "{\"code\":0,\"msg\":\"success\",\"expire\":7200}";
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponseForNullToken.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockNullTokenResponse);

        commonLarkService.refreshTenantAccessToken();
    }

    @Test
    public void refreshTenantAccessToken_httpClientIsNull_shouldThrowIllegalStateException() throws IOException {
        commonLarkService.httpClient = null;
        commonLarkService.tenantAccessToken = null;
        commonLarkService.tokenExpiry = 0;

        when(mockObjectMapper.writeValueAsString(any(TenantAccessTokenRequest.class)))
                .thenReturn("{\"app_id\":\"dummyAppId\",\"app_secret\":\"dummyAppSecret\"}");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> commonLarkService.refreshTenantAccessToken());
        assertEquals("HTTP client not yet initialized", exception.getMessage());

        verify(mockObjectMapper).writeValueAsString(any(TenantAccessTokenRequest.class));
        verify(mockHttpClient, never()).execute(any());
    }

    @Test
    public void refreshTenantAccessToken_requestBodyConstruction() throws IOException {
        String mockJsonResponse = "{\"code\":0,\"msg\":\"success\",\"tenant_access_token\":\"mock_token\",\"expire\":7200}";
        TenantAccessTokenResponse mockTokenResponse = new TenantAccessTokenResponse(0, "success", 7200, "mock_token");
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(anyString(), eq(TenantAccessTokenResponse.class))).thenReturn(mockTokenResponse);

        ArgumentCaptor<TenantAccessTokenRequest> requestCaptor = ArgumentCaptor.forClass(TenantAccessTokenRequest.class);
        when(mockObjectMapper.writeValueAsString(requestCaptor.capture())).thenReturn("{\"app_id\":\"testAppId\",\"app_secret\":\"testAppSecret\"}");

        commonLarkService.refreshTenantAccessToken();

        TenantAccessTokenRequest capturedRequest = requestCaptor.getValue();
        assertEquals(TEST_APP_ID, capturedRequest.appId());
        assertEquals(TEST_APP_SECRET, capturedRequest.appSecret());
    }
}