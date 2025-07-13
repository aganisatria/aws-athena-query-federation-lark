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
