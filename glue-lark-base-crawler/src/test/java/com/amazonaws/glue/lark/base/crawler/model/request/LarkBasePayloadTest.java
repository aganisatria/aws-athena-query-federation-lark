package com.amazonaws.glue.lark.base.crawler.model.request;

import org.junit.Test;
import static org.junit.Assert.*;

public class LarkBasePayloadTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        String baseDsId = "baseDs123";
        String tableDsId = "tableDs456";
        LarkBasePayload payload = new LarkBasePayload(baseDsId, tableDsId);

        assertEquals(baseDsId, payload.larkBaseDataSourceId());
        assertEquals(tableDsId, payload.larkTableDataSourceId());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        LarkBasePayload payload1a = new LarkBasePayload("b1", "t1");
        LarkBasePayload payload1b = new LarkBasePayload("b1", "t1");
        LarkBasePayload payload2 = new LarkBasePayload("b2", "t2");
        LarkBasePayload payload3 = new LarkBasePayload("b1", "t2");

        assertEquals(payload1a, payload1b);
        assertNotEquals(payload1a, payload2);
        assertNotEquals(payload1a, payload3);

        assertEquals(payload1a.hashCode(), payload1b.hashCode());
    }
}