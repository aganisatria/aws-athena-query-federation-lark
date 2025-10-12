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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchRecordsRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void builder_withAllFields_shouldCreateObjectCorrectly() throws Exception {
        Integer pageSize = 100;
        String pageToken = "token123";

        SearchRecordsRequest request = SearchRecordsRequest.builder()
                .pageSize(pageSize)
                .pageToken(pageToken)
                .build();

        assertEquals(pageSize, request.getPageSize());
        assertEquals(pageToken, request.getPageToken());

        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"page_size\":100"));
        assertTrue(json.contains("\"page_token\":\"token123\""));
    }
}
