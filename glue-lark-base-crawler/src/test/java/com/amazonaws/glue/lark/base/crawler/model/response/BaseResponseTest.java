package com.amazonaws.glue.lark.base.crawler.model.response;

import org.junit.Test;
import static org.junit.Assert.*;

public class BaseResponseTest {

    @Test
    public void builder_allFieldsSet_shouldCreateObjectCorrectly() {
        int code = 0;
        String msg = "success";
        String data = "Test Data";

        BaseResponse<String> response = BaseResponse.<String>builder()
                .code(code)
                .msg(msg)
                .data(data)
                .build();

        assertEquals(code, response.getCode());
        assertEquals(msg, response.getMsg());
        assertEquals(data, response.getData());
    }

    @Test
    public void builder_defaultValues_whenFieldsNotSet() {
        BaseResponse<Object> response = BaseResponse.builder().build();

        assertEquals(0, response.getCode());
        assertNull(response.getMsg());
        assertNull(response.getData());
    }
}