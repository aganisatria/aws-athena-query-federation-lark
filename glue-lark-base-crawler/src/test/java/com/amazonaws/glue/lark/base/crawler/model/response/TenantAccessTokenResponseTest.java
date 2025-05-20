package com.amazonaws.glue.lark.base.crawler.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class TenantAccessTokenResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void recordCreation_shouldHoldValuesCorrectly() {
        int code = 0;
        String msg = "success";
        int expire = 7200;
        String token = "test_tenant_access_token";

        TenantAccessTokenResponse response = new TenantAccessTokenResponse(code, msg, expire, token);

        assertEquals(code, response.code());
        assertEquals(msg, response.msg());
        assertEquals(expire, response.expire());
        assertEquals(token, response.tenantAccessToken());
    }

    @Test
    public void jacksonDeserialization_shouldWork() throws Exception {
        String json = "{\"code\":0,\"msg\":\"Operation Succeeded\",\"expire\":7199,\"tenant_access_token\":\"t-g10348b31dkcfe74356157000000000b043f07cc\"}";
        TenantAccessTokenResponse response = objectMapper.readValue(json, TenantAccessTokenResponse.class);

        assertEquals(0, response.code());
        assertEquals("Operation Succeeded", response.msg());
        assertEquals(7199, response.expire());
        assertEquals("t-g10348b31dkcfe74356157000000000b043f07cc", response.tenantAccessToken());
    }

    @Test
    public void jacksonSerialization_shouldProduceCorrectJson() throws Exception {
        TenantAccessTokenResponse response = new TenantAccessTokenResponse(0, "OK", 3600, "a_token");
        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"code\":0"));
        assertTrue(json.contains("\"msg\":\"OK\""));
        assertTrue(json.contains("\"expire\":3600"));
        assertTrue(json.contains("\"tenant_access_token\":\"a_token\""));
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        TenantAccessTokenResponse response1a = new TenantAccessTokenResponse(0, "msg1", 100, "token1");
        TenantAccessTokenResponse response1b = new TenantAccessTokenResponse(0, "msg1", 100, "token1");
        TenantAccessTokenResponse response2 = new TenantAccessTokenResponse(1, "msg2", 200, "token2");

        assertEquals(response1a, response1b);
        assertNotEquals(response1a, response2);
        assertEquals(response1a.hashCode(), response1b.hashCode());
    }

    @Test
    public void recordCreation_withDifferentValues() {
        TenantAccessTokenResponse response = new TenantAccessTokenResponse(9999, "error msg", 0, "");
        assertEquals(9999, response.code());
        assertEquals("error msg", response.msg());
        assertEquals(0, response.expire());
        assertEquals("", response.tenantAccessToken());
    }
}