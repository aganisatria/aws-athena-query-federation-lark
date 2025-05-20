package com.amazonaws.glue.lark.base.crawler.model.request;

import org.junit.Test;
import static org.junit.Assert.*;

public class LarkDrivePayloadTest {

    @Test
    public void recordCreation_shouldHoldValue() {
        String folderToken = "folderTokenXYZ123";
        LarkDrivePayload payload = new LarkDrivePayload(folderToken);

        assertEquals(folderToken, payload.larkDriveFolderToken());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        LarkDrivePayload payload1a = new LarkDrivePayload("token1");
        LarkDrivePayload payload1b = new LarkDrivePayload("token1");
        LarkDrivePayload payload2 = new LarkDrivePayload("token2");

        assertEquals(payload1a, payload1b);
        assertNotEquals(payload1a, payload2);
        assertEquals(payload1a.hashCode(), payload1b.hashCode());
    }
}