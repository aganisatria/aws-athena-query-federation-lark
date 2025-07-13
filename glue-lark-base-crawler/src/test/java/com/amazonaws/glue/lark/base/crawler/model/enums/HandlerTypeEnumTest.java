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
package com.amazonaws.glue.lark.base.crawler.model.enums;

import org.junit.Test;
import static org.junit.Assert.*;

public class HandlerTypeEnumTest {

    @Test
    public void fromString_validLarkBase_shouldReturnLarkBaseEnum() {
        assertEquals(HandlerTypeEnum.LARK_BASE, HandlerTypeEnum.fromString("LARK_BASE"));
    }

    @Test
    public void fromString_validLarkDrive_shouldReturnLarkDriveEnum() {
        assertEquals(HandlerTypeEnum.LARK_DRIVE, HandlerTypeEnum.fromString("LARK_DRIVE"));
    }

    @Test
    public void fromString_invalidString_shouldReturnUnknownEnum() {
        assertEquals(HandlerTypeEnum.UNKNOWN, HandlerTypeEnum.fromString("INVALID_TYPE"));
    }

    @Test
    public void fromString_emptyString_shouldReturnUnknownEnum() {
        assertEquals(HandlerTypeEnum.UNKNOWN, HandlerTypeEnum.fromString(""));
    }

    @Test
    public void fromString_nullString_shouldReturnUnknownEnum() {
        assertEquals(HandlerTypeEnum.UNKNOWN, HandlerTypeEnum.fromString(null));
    }

    @Test
    public void enumValues_shouldContainExpectedValues() {
        HandlerTypeEnum[] values = HandlerTypeEnum.values();
        assertEquals(3, values.length);
        assertTrue(java.util.Arrays.asList(values).contains(HandlerTypeEnum.LARK_BASE));
        assertTrue(java.util.Arrays.asList(values).contains(HandlerTypeEnum.LARK_DRIVE));
        assertTrue(java.util.Arrays.asList(values).contains(HandlerTypeEnum.UNKNOWN));
    }
}
