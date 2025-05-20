package com.amazonaws.glue.lark.base.crawler.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class SecretValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void recordCreation_shouldHoldValues() {
        String appId = "testAppId";
        String appSecret = "testAppSecret";
        SecretValue secretValue = new SecretValue(appId, appSecret);

        assertEquals(appId, secretValue.larkAppId());
        assertEquals(appSecret, secretValue.larkAppSecret());
    }

    @Test
    public void jacksonDeserialization_shouldWork() throws Exception {
        String json = "{\"lark_app_id\":\"jsonAppId\",\"lark_app_secret\":\"jsonAppSecret\"}";
        SecretValue secretValue = objectMapper.readValue(json, SecretValue.class);

        assertEquals("jsonAppId", secretValue.larkAppId());
        assertEquals("jsonAppSecret", secretValue.larkAppSecret());
    }

    @Test
    public void jacksonSerialization_shouldWork() throws Exception {
        SecretValue secretValue = new SecretValue("serializeId", "serializeSecret");
        String json = objectMapper.writeValueAsString(secretValue);

        assertTrue(json.contains("\"lark_app_id\":\"serializeId\""));
        assertTrue(json.contains("\"lark_app_secret\":\"serializeSecret\""));
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        SecretValue secret1a = new SecretValue("id1", "secret1");
        SecretValue secret1b = new SecretValue("id1", "secret1");
        SecretValue secret2 = new SecretValue("id2", "secret2");

        assertEquals(secret1a, secret1b);
        assertNotEquals(secret1a, secret2);
        assertEquals(secret1a.hashCode(), secret1b.hashCode());
    }
}