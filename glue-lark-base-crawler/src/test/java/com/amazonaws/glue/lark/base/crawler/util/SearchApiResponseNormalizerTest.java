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
package com.amazonaws.glue.lark.base.crawler.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class SearchApiResponseNormalizerTest {

    @Test
    public void normalizeRecordFields_nullInput_returnsEmptyMap() {
        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void normalizeRecordFields_emptyInput_returnsEmptyMap() {
        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(new HashMap<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void normalizeFieldValue_nullValue_returnsNull() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("null_field", null);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertTrue(result.containsKey("null_field"));
        assertNull(result.get("null_field"));
    }

    @Test
    public void normalizeFieldValue_textField_extractsTextFromArray() {
        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", "Sample text");
        textObject.put("type", "text");
        List<Map<String, Object>> searchApiValue = Collections.singletonList(textObject);

        Map<String, Object> fields = new HashMap<>();
        fields.put("field_text", searchApiValue);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals("Sample text", result.get("field_text"));
    }

    @Test
    public void normalizeFieldValue_wrappedFormulaValue_unwrapsValue() {
        // Create wrapped formula value: {"type": 1, "value": "some result"}
        Map<String, Object> wrappedValue = new HashMap<>();
        wrappedValue.put("type", 1);
        wrappedValue.put("value", "formula result");

        Map<String, Object> fields = new HashMap<>();
        fields.put("formula_field", wrappedValue);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals("formula result", result.get("formula_field"));
    }

    @Test
    public void normalizeFieldValue_wrappedLookupValue_unwrapsNestedArray() {
        // Create wrapped lookup value with nested array: {"type": 2, "value": [{"text": "lookup text", "type": "text"}]}
        Map<String, Object> textObject = new HashMap<>();
        textObject.put("text", "lookup text");
        textObject.put("type", "text");

        Map<String, Object> wrappedValue = new HashMap<>();
        wrappedValue.put("type", 2);
        wrappedValue.put("value", Collections.singletonList(textObject));

        Map<String, Object> fields = new HashMap<>();
        fields.put("lookup_field", wrappedValue);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals("lookup text", result.get("lookup_field"));
    }

    @Test
    public void normalizeFieldValue_emptyList_returnsEmptyList() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("empty_list_field", new ArrayList<>());

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertTrue(result.get("empty_list_field") instanceof List);
        assertTrue(((List<?>) result.get("empty_list_field")).isEmpty());
    }

    @Test
    public void normalizeFieldValue_createdUserField_extractsFirstElement() {
        // Created user comes as array from Search API
        Map<String, Object> userObject = new HashMap<>();
        userObject.put("id", "user123");
        userObject.put("name", "John Doe");

        List<Map<String, Object>> userArray = Collections.singletonList(userObject);

        Map<String, Object> fields = new HashMap<>();
        fields.put("created_user", userArray);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(userObject, result.get("created_user"));
    }

    @Test
    public void normalizeFieldValue_modifiedUserField_extractsFirstElement() {
        // Modified user comes as array from Search API
        Map<String, Object> userObject = new HashMap<>();
        userObject.put("id", "user456");
        userObject.put("name", "Jane Smith");

        List<Map<String, Object>> userArray = Collections.singletonList(userObject);

        Map<String, Object> fields = new HashMap<>();
        fields.put("modified_user", userArray);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(userObject, result.get("modified_user"));
    }

    @Test
    public void normalizeFieldValue_multiElementTextArray_keepsArray() {
        // Multi-element text arrays should be kept as-is (edge case)
        Map<String, Object> textObject1 = new HashMap<>();
        textObject1.put("text", "Text 1");
        textObject1.put("type", "text");

        Map<String, Object> textObject2 = new HashMap<>();
        textObject2.put("text", "Text 2");
        textObject2.put("type", "text");

        List<Map<String, Object>> multiTextArray = new ArrayList<>();
        multiTextArray.add(textObject1);
        multiTextArray.add(textObject2);

        Map<String, Object> fields = new HashMap<>();
        fields.put("multi_text_field", multiTextArray);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(multiTextArray, result.get("multi_text_field"));
    }

    @Test
    public void normalizeFieldValue_regularString_returnsUnchanged() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("string_field", "plain string");

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals("plain string", result.get("string_field"));
    }

    @Test
    public void normalizeFieldValue_regularNumber_returnsUnchanged() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("number_field", 42);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(42, result.get("number_field"));
    }

    @Test
    public void normalizeFieldValue_regularMap_returnsUnchanged() {
        // Map without type/value structure should be returned as-is
        Map<String, Object> regularMap = new HashMap<>();
        regularMap.put("some_key", "some_value");
        regularMap.put("another_key", 123);

        Map<String, Object> fields = new HashMap<>();
        fields.put("map_field", regularMap);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(regularMap, result.get("map_field"));
    }

    @Test
    public void normalizeFieldValue_listOfStrings_returnsUnchanged() {
        // List of non-map elements should be returned as-is
        List<String> stringList = new ArrayList<>();
        stringList.add("item1");
        stringList.add("item2");

        Map<String, Object> fields = new HashMap<>();
        fields.put("string_list_field", stringList);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(stringList, result.get("string_list_field"));
    }

    @Test
    public void normalizeFieldValue_listOfMapsWithoutSpecialStructure_returnsUnchanged() {
        // List of maps that don't match text field or user field patterns
        Map<String, Object> item1 = new HashMap<>();
        item1.put("custom_key", "value1");

        Map<String, Object> item2 = new HashMap<>();
        item2.put("custom_key", "value2");

        List<Map<String, Object>> mapList = new ArrayList<>();
        mapList.add(item1);
        mapList.add(item2);

        Map<String, Object> fields = new HashMap<>();
        fields.put("custom_list_field", mapList);

        Map<String, Object> result = SearchApiResponseNormalizer.normalizeRecordFields(fields);
        assertEquals(mapList, result.get("custom_list_field"));
    }
}
