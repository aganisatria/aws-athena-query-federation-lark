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
