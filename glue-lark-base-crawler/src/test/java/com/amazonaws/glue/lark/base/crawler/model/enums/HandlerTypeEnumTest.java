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