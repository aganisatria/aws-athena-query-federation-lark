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

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SearchRecordsResponseTest {

    @Test
    public void recordItem_builderAndGetters_shouldWork() {
        String recordId = "rec_123xyz";
        Map<String, Object> fields = Map.of("fieldName1", "value1", "fieldName2", 100);

        SearchRecordsResponse.RecordItem item = SearchRecordsResponse.RecordItem.builder()
                .recordId(recordId)
                .fields(fields)
                .build();

        assertEquals(recordId, item.getRecordId());
        assertEquals(fields, item.getFields());
    }

    @Test
    public void recordItem_fields_whenValuesAreNull_shouldFilterThemOut() {
        Map<String, Object> fieldsWithNull = new HashMap<>();
        fieldsWithNull.put("name", "Alice");
        fieldsWithNull.put("email", null);
        fieldsWithNull.put("age", 25);

        SearchRecordsResponse.RecordItem item = SearchRecordsResponse.RecordItem.builder()
                .recordId("rec_with_null")
                .fields(fieldsWithNull)
                .build();

        assertEquals(2, item.getFields().size());
        assertTrue(item.getFields().containsKey("name"));
        assertTrue(item.getFields().containsKey("age"));
        assertFalse(item.getFields().containsKey("email"));
    }

    @Test
    public void recordItem_fields_whenBuilderFieldsIsNull_shouldReturnEmptyMap() {
        SearchRecordsResponse.RecordItem item = SearchRecordsResponse.RecordItem.builder()
                .recordId("rec_no_fields")
                .fields(null)
                .build();
        assertNotNull(item.getFields());
        assertTrue(item.getFields().isEmpty());
    }

    @Test
    public void listData_builderAndGetters_shouldWork() {
        SearchRecordsResponse.RecordItem item1 = SearchRecordsResponse.RecordItem.builder()
                .recordId("rec_a").fields(Map.of("key", "valA")).build();
        List<SearchRecordsResponse.RecordItem> items = Collections.singletonList(item1);
        String pageToken = "pageToken789";
        boolean hasMore = false;
        int total = 1;

        SearchRecordsResponse.ListData listData = SearchRecordsResponse.ListData.builder()
                .items(items)
                .pageToken(pageToken)
                .hasMore(hasMore)
                .total(total)
                .build();

        assertEquals(items, listData.items());
        assertEquals(pageToken, listData.pageToken());
        assertEquals(hasMore, listData.hasMore());
        assertEquals(total, listData.total());
    }

    @Test
    public void listData_items_whenBuilderItemsIsNull_shouldReturnEmptyList() {
        SearchRecordsResponse.ListData listData = SearchRecordsResponse.ListData.builder()
                .items(null)
                .build();
        assertNotNull(listData.items());
        assertTrue(listData.items().isEmpty());
    }

    @Test
    public void listRecordsResponse_builderAndGetters_shouldWork() {
        SearchRecordsResponse.RecordItem item1 = SearchRecordsResponse.RecordItem.builder().recordId("rec_b").build();
        List<SearchRecordsResponse.RecordItem> items = Collections.singletonList(item1);
        SearchRecordsResponse.ListData listData = SearchRecordsResponse.ListData.builder()
                .items(items)
                .pageToken("nextPageData")
                .hasMore(true)
                .total(15)
                .build();

        SearchRecordsResponse response = (SearchRecordsResponse) SearchRecordsResponse.builder()
                .code(0)
                .msg("Fetched")
                .data(listData)
                .build();

        assertEquals(0, response.getCode());
        assertEquals("Fetched", response.getMsg());
        assertNotNull(response.getData());
        assertEquals(items, response.getItems());
        assertEquals("nextPageData", response.getPageToken());
        assertTrue(response.hasMore());
    }

    @Test
    public void listRecordsResponse_getters_whenDataIsNull_shouldReturnDefaults() {
        SearchRecordsResponse response = (SearchRecordsResponse) SearchRecordsResponse.builder().data(null).build();

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
        assertNull(response.getPageToken());
        assertFalse(response.hasMore());
    }
}
