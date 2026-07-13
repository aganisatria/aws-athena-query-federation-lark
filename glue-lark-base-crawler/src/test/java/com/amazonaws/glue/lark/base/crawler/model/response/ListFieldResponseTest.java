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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ListFieldResponseTest {

    @Test
    public void listFieldResponse_builderAndGetters_shouldWork() {
        ListFieldResponse.FieldItem fieldItem1 = ListFieldResponse.FieldItem.builder()
                .fieldId("f1").fieldName("Field One").uiType("Text").build();
        List<ListFieldResponse.FieldItem> items = Collections.singletonList(fieldItem1);
        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder()
                .items(items)
                .pageToken("nextPage")
                .hasMore(true)
                .total(10)
                .build();

        ListFieldResponse response = (ListFieldResponse) ListFieldResponse.builder()
                .code(0)
                .msg("success")
                .data(listData)
                .build();

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertNotNull(response.getData());
        assertEquals(items, response.getItems());
        assertEquals("nextPage", response.getPageToken());
        assertTrue(response.hasMore());
    }

    @Test
    public void deserialize_withUnrecognizedErrorField_doesNotThrow() throws Exception {
        // Reproduces a real production crash (glue-lark-base-crawler, LarkBaseService.getTableFields):
        // Lark's fields-list endpoint sometimes returns an unexpected top-level "error" key, which
        // Jackson rejected with UnrecognizedPropertyException since ListFieldResponse.Builder (the class
        // actually deserialized into, per @JsonDeserialize(builder=...)) didn't ignore unknown properties.
        // This silently failed lookup-type resolution for every field on the affected table (92 times in a
        // single crawl run observed in production) instead of just ignoring the extra field.
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[]},"
                + "\"error\":{\"log_id\":\"abc123\"}}";

        ListFieldResponse response = objectMapper.readValue(json, ListFieldResponse.class);

        assertEquals(0, response.getCode());
        assertNotNull(response.getItems());
    }

    @Test
    public void fieldItem_property_whenValuesAreNull_shouldFilterThemOut() {
        Map<String, Object> propertyWithNull = new HashMap<>();
        propertyWithNull.put("formatter", "0.00");
        propertyWithNull.put("date_formatter", null);
        propertyWithNull.put("auto_fill", true);

        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .fieldId("f1")
                .fieldName("Field")
                .uiType("Number")
                .property(propertyWithNull)
                .build();

        assertEquals(2, item.getProperty().size());
        assertTrue(item.getProperty().containsKey("formatter"));
        assertTrue(item.getProperty().containsKey("auto_fill"));
        assertFalse(item.getProperty().containsKey("date_formatter"));
    }

    @Test
    public void listFieldResponse_getItems_whenDataIsNull_shouldReturnEmptyList() {
        ListFieldResponse response = (ListFieldResponse) ListFieldResponse.builder().data(null).build();
        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    public void listFieldResponse_getItems_whenDataItemsIsNull_shouldReturnEmptyList() {
        ListFieldResponse.ListData listDataWithNullItems = ListFieldResponse.ListData.builder().items(null).build();
        ListFieldResponse response = (ListFieldResponse) ListFieldResponse.builder().data(listDataWithNullItems).build();

        assertNotNull(response.getItems());
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    public void listFieldResponse_getPageToken_whenDataIsNull_shouldReturnNull() {
        ListFieldResponse response = (ListFieldResponse) ListFieldResponse.builder().data(null).build();
        assertNull(response.getPageToken());
    }

    @Test
    public void listFieldResponse_hasMore_whenDataIsNull_shouldReturnFalse() {
        ListFieldResponse response = (ListFieldResponse) ListFieldResponse.builder().data(null).build();
        assertFalse(response.hasMore());
    }

    @Test
    public void listData_builderAndGetters_shouldWork() {
        ListFieldResponse.FieldItem fieldItem1 = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        ListFieldResponse.FieldItem fieldItemButton = ListFieldResponse.FieldItem.builder().uiType("Button").build();
        List<ListFieldResponse.FieldItem> allItems = Arrays.asList(fieldItem1, fieldItemButton);

        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder()
                .items(allItems)
                .pageToken("token123")
                .hasMore(true)
                .total(5)
                .build();

        assertEquals(1, listData.getItems().size());
        assertEquals(fieldItem1, listData.getItems().get(0));
        assertEquals("token123", listData.getPageToken());
        assertTrue(listData.hasMore());
        assertEquals(Integer.valueOf(5), listData.getTotal());
    }

    @Test
    public void listData_getItems_filtersBlacklistedFields() {
        ListFieldResponse.FieldItem textItem = ListFieldResponse.FieldItem.builder().fieldId("f_text").uiType("Text").build();
        ListFieldResponse.FieldItem buttonItem = ListFieldResponse.FieldItem.builder().fieldId("f_button").uiType("Button").build();
        ListFieldResponse.FieldItem stageItem = ListFieldResponse.FieldItem.builder().fieldId("f_stage").uiType("Stage").build();

        Map<String, Object> formulaPropertyButton = Map.of("type", Map.of("ui_type", "Button"));
        ListFieldResponse.FieldItem formulaButtonItem = ListFieldResponse.FieldItem.builder().fieldId("f_formula_button").uiType("Formula").property(formulaPropertyButton).build();

        Map<String, Object> formulaPropertyText = Map.of("type", Map.of("ui_type", "Text"));
        ListFieldResponse.FieldItem formulaTextItem = ListFieldResponse.FieldItem.builder().fieldId("f_formula_text").uiType("Formula").property(formulaPropertyText).build();

        List<ListFieldResponse.FieldItem> allOriginalItems = Arrays.asList(textItem, buttonItem, stageItem, formulaButtonItem, formulaTextItem);
        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder().items(allOriginalItems).build();

        List<ListFieldResponse.FieldItem> filteredItems = listData.getItems();
        assertEquals(2, filteredItems.size());
        assertTrue(filteredItems.contains(textItem));
        assertTrue(filteredItems.contains(formulaTextItem));
        assertFalse(filteredItems.contains(buttonItem));
        assertFalse(filteredItems.contains(stageItem));
        assertFalse(filteredItems.contains(formulaButtonItem));
    }

    @Test
    public void listData_getItems_whenOriginalItemsIsNull_shouldReturnEmptyList() {
        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder().items(null).build();
        assertNotNull(listData.getItems());
        assertTrue(listData.getItems().isEmpty());
    }

    @Test
    public void listData_getItems_whenOriginalItemsIsEmpty_shouldReturnEmptyList() {
        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder().items(Collections.emptyList()).build();
        assertNotNull(listData.getItems());
        assertTrue(listData.getItems().isEmpty());
    }
}
