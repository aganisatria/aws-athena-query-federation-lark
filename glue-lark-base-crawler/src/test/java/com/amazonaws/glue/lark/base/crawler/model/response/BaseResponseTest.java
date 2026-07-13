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
package com.amazonaws.glue.lark.base.crawler.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaseResponseTest {

    @Test
    public void deserialize_withUnrecognizedTopLevelField_doesNotThrow() throws Exception {
        // Reproduces a real production failure: Lark's API sometimes returns a response with an unexpected
        // top-level "error" key (observed on the fields-list endpoint) that isn't part of the normal
        // code/msg/data shape. Without ignoreUnknown on the Builder that @JsonDeserialize(builder=...)
        // actually deserializes into, Jackson throws UnrecognizedPropertyException instead of just ignoring
        // the extra field - crashing lookup-type resolution for every field on the affected table.
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "{\"code\":0,\"msg\":\"success\",\"data\":{\"foo\":\"bar\"},\"error\":{\"log_id\":\"abc123\"}}";

        BaseResponse<?> response = objectMapper.readValue(json, BaseResponse.class);

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
    }

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
