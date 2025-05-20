package com.amazonaws.glue.lark.base.crawler.model.request;

import org.junit.Test;
import static org.junit.Assert.*;

public class MainLarkBasePayloadTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        String handlerType = "LARK_DRIVE_HANDLER";
        Object payloadObject = new LarkDrivePayload("drivePayloadToken");
        MainLarkBasePayload payload = new MainLarkBasePayload(handlerType, payloadObject);

        assertEquals(handlerType, payload.handlerType());
        assertEquals(payloadObject, payload.payload());
    }

    @Test
    public void recordCreation_withDifferentPayloadType() {
        String handlerType = "LARK_BASE_HANDLER";
        LarkBasePayload larkBasePl = new LarkBasePayload("base1", "table1");
        MainLarkBasePayload payload = new MainLarkBasePayload(handlerType, larkBasePl);

        assertEquals(handlerType, payload.handlerType());
        assertTrue(payload.payload() instanceof LarkBasePayload);
        assertEquals(larkBasePl, payload.payload());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        LarkDrivePayload drivePayload1 = new LarkDrivePayload("token1");
        LarkBasePayload basePayload1 = new LarkBasePayload("b1", "t1");

        MainLarkBasePayload payload1a = new MainLarkBasePayload("DRIVE", drivePayload1);
        MainLarkBasePayload payload1b = new MainLarkBasePayload("DRIVE", drivePayload1);
        MainLarkBasePayload payload2 = new MainLarkBasePayload("BASE", basePayload1);
        MainLarkBasePayload payload3 = new MainLarkBasePayload("DRIVE", new LarkDrivePayload("token2"));

        assertEquals(payload1a, payload1b);
        assertNotEquals(payload1a, payload2);
        assertNotEquals(payload1a, payload3);

        assertEquals(payload1a.hashCode(), payload1b.hashCode());
    }
}