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
