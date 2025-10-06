/*-
 * #%L
 * athena-lark-base
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
package com.amazonaws.athena.connectors.lark.base.translator;

import com.amazonaws.athena.connector.lambda.domain.predicate.*;
import com.amazonaws.athena.connectors.lark.base.model.AthenaFieldLarkBaseMapping;
import com.amazonaws.athena.connectors.lark.base.model.NestedUIType;
import com.amazonaws.athena.connectors.lark.base.model.enums.UITypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class SearchApiFilterTranslatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testToFilterJson_nullConstraints_returnsNull() {
        String filterJson = SearchApiFilterTranslator.toFilterJson(null, Collections.emptyList());
        assertNull(filterJson);
    }

    @Test
    public void testToFilterJson_emptyConstraints_returnsNull() {
        String filterJson = SearchApiFilterTranslator.toFilterJson(new HashMap<>(), Collections.emptyList());
        assertNull(filterJson);
    }

    // Note: Cannot test constraint creation in unit tests as AllOrNoneValueSet constructors are not public
    // Integration tests (regression tests) cover the actual filter translation

    @Test
    public void testToSortJson_nullOrderByFields_returnsNull() {
        String sortJson = SearchApiFilterTranslator.toSortJson(null, Collections.emptyList());
        assertNull(sortJson);
    }

    @Test
    public void testToSortJson_emptyOrderByFields_returnsNull() {
        String sortJson = SearchApiFilterTranslator.toSortJson(Collections.emptyList(), Collections.emptyList());
        assertNull(sortJson);
    }

    @Test
    public void testToSortJson_singleField_ascending() throws Exception {
        List<OrderByField> orderByFields = Collections.singletonList(
            new OrderByField("field_number", OrderByField.Direction.ASC_NULLS_FIRST));

        List<AthenaFieldLarkBaseMapping> mappings = Collections.singletonList(
            new AthenaFieldLarkBaseMapping("field_number", "field_number",
                new NestedUIType(UITypeEnum.NUMBER, null)));

        String sortJson = SearchApiFilterTranslator.toSortJson(orderByFields, mappings);

        assertNotNull(sortJson);
        JsonNode sort = OBJECT_MAPPER.readTree(sortJson);
        assertEquals(1, sort.size());
        assertEquals("field_number", sort.get(0).get("field_name").asText());
        assertFalse(sort.get(0).get("desc").asBoolean());
    }

    @Test
    public void testToSortJson_singleField_descending() throws Exception {
        List<OrderByField> orderByFields = Collections.singletonList(
            new OrderByField("field_number", OrderByField.Direction.DESC_NULLS_LAST));

        List<AthenaFieldLarkBaseMapping> mappings = Collections.singletonList(
            new AthenaFieldLarkBaseMapping("field_number", "field_number",
                new NestedUIType(UITypeEnum.NUMBER, null)));

        String sortJson = SearchApiFilterTranslator.toSortJson(orderByFields, mappings);

        assertNotNull(sortJson);
        JsonNode sort = OBJECT_MAPPER.readTree(sortJson);
        assertTrue(sort.get(0).get("desc").asBoolean());
    }

    @Test
    public void testToSortJson_multipleFields() throws Exception {
        List<OrderByField> orderByFields = Arrays.asList(
            new OrderByField("field_number", OrderByField.Direction.DESC_NULLS_LAST),
            new OrderByField("field_text", OrderByField.Direction.ASC_NULLS_FIRST));

        List<AthenaFieldLarkBaseMapping> mappings = Arrays.asList(
            new AthenaFieldLarkBaseMapping("field_number", "field_number",
                new NestedUIType(UITypeEnum.NUMBER, null)),
            new AthenaFieldLarkBaseMapping("field_text", "field_text",
                new NestedUIType(UITypeEnum.TEXT, null)));

        String sortJson = SearchApiFilterTranslator.toSortJson(orderByFields, mappings);

        assertNotNull(sortJson);
        JsonNode sort = OBJECT_MAPPER.readTree(sortJson);
        assertEquals(2, sort.size());
        assertEquals("field_number", sort.get(0).get("field_name").asText());
        assertTrue(sort.get(0).get("desc").asBoolean());
        assertEquals("field_text", sort.get(1).get("field_name").asText());
        assertFalse(sort.get(1).get("desc").asBoolean());
    }

    @Test
    public void testToSplitFilterJson_withExistingFilter() throws Exception {
        String existingFilter = "{\"conjunction\":\"and\",\"conditions\":[{\"field_name\":\"field_number\",\"operator\":\"is\",\"value\":[\"123\"]}]}";

        String splitFilter = SearchApiFilterTranslator.toSplitFilterJson(existingFilter, 1, 100);

        assertNotNull(splitFilter);
        JsonNode filter = OBJECT_MAPPER.readTree(splitFilter);
        JsonNode conditions = filter.get("conditions");
        assertEquals(3, conditions.size()); // 1 existing + 2 split conditions

        boolean hasStartCondition = false;
        boolean hasEndCondition = false;
        for (JsonNode condition : conditions) {
            if ("$reserved_split_key".equals(condition.get("field_name").asText())) {
                String operator = condition.get("operator").asText();
                if ("isGreaterEqual".equals(operator)) {
                    hasStartCondition = true;
                    assertEquals("1", condition.get("value").get(0).asText());
                } else if ("isLessEqual".equals(operator)) {
                    hasEndCondition = true;
                    assertEquals("100", condition.get("value").get(0).asText());
                }
            }
        }
        assertTrue(hasStartCondition);
        assertTrue(hasEndCondition);
    }

    @Test
    public void testToSplitFilterJson_noExistingFilter() throws Exception {
        String splitFilter = SearchApiFilterTranslator.toSplitFilterJson(null, 1, 100);

        assertNotNull(splitFilter);
        JsonNode filter = OBJECT_MAPPER.readTree(splitFilter);
        JsonNode conditions = filter.get("conditions");
        assertEquals(2, conditions.size()); // Only split conditions

        assertEquals("$reserved_split_key", conditions.get(0).get("field_name").asText());
        assertEquals("isGreaterEqual", conditions.get(0).get("operator").asText());
        assertEquals("1", conditions.get(0).get("value").get(0).asText());

        assertEquals("$reserved_split_key", conditions.get(1).get("field_name").asText());
        assertEquals("isLessEqual", conditions.get(1).get("operator").asText());
        assertEquals("100", conditions.get(1).get("value").get(0).asText());
    }

    @Test
    public void testToSplitFilterJson_invalidRange_returnsExisting() {
        String existingFilter = "{\"conjunction\":\"and\",\"conditions\":[]}";

        String result = SearchApiFilterTranslator.toSplitFilterJson(existingFilter, 0, 0);
        assertEquals(existingFilter, result);

        result = SearchApiFilterTranslator.toSplitFilterJson(existingFilter, -1, 100);
        assertEquals(existingFilter, result);

        result = SearchApiFilterTranslator.toSplitFilterJson(existingFilter, 100, -1);
        assertEquals(existingFilter, result);
    }

    @Test
    public void testToSplitFilterJson_blankExistingFilter() throws Exception {
        String splitFilter = SearchApiFilterTranslator.toSplitFilterJson("", 1, 100);

        assertNotNull(splitFilter);
        JsonNode filter = OBJECT_MAPPER.readTree(splitFilter);
        JsonNode conditions = filter.get("conditions");
        assertEquals(2, conditions.size()); // Only split conditions, no existing
    }
}
