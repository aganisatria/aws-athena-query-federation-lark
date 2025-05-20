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

import com.amazonaws.glue.lark.base.crawler.model.ColumnParameters;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.glue.model.Column;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
class UtilTest {

    @Test
    void constructDatabaseLocationURIPrefix() {
        String result = Util.constructDatabaseLocationURIPrefix("LarkBase", "baseDs123:tableDs456");
        assertEquals("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456", result);
    }

    @Test
    void constructDatabaseLocationURI() {
        String result = Util.constructDatabaseLocationURI("LarkBase", "baseDs123:tableDs456", "base789");
        assertEquals("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=base789", result);
    }

    @Test
    void constructTableLocationURI() {
        String result = Util.constructTableLocationURI("LarkBase", "baseDs123:tableDs456", "base789", "table101");
        assertEquals("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=base789/Table=table101", result);
    }

    @Test
    void constructCrawlingMethod() {
        String result = Util.constructCrawlingMethod("LarkBase");
        assertEquals("CrawlingMethod=LarkBase", result);
    }

    @Test
    void constructLarkBaseDataSourceId() {
        String result = Util.constructLarkBaseDataSourceId("baseDs123:tableDs456");
        assertEquals("DataSource=baseDs123:tableDs456", result);
    }

    @Test
    void constructLarkBaseBaseId() {
        String result = Util.constructLarkBaseBaseId("base789");
        assertEquals("Base=base789", result);
    }

    @Test
    void constructLarkBaseTableId() {
        String result = Util.constructLarkBaseTableId("table101");
        assertEquals("Table=table101", result);
    }

    @Test
    void extractDatabaseIdFromLocationURI() {
        String locationURI = "lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=base789";
        String result = Util.extractDatabaseIdFromLocationURI(locationURI);
        assertEquals("base789", result);

        assertNull(Util.extractDatabaseIdFromLocationURI(null));
        assertNull(Util.extractDatabaseIdFromLocationURI(""));
        assertNull(Util.extractDatabaseIdFromLocationURI("invalid-uri"));

        assertThrows(RuntimeException.class, () -> Util.extractDatabaseIdFromLocationURI("lark-base-flag/Base="));
    }

    @Test
    void constructColumns() {
        ArrayList<ColumnParameters> params = new ArrayList<>();
        params.add(
            ColumnParameters.builder()
                    .columnName("col1")
                    .columnType("string")
                    .larkBaseFieldId("fieldId123")
                    .larkBaseColumnType("TEXT")
                    .larkBaseId("baseId456")
                    .larkBaseTableId("tableId789")
                    .build()
        );
        params.add(
            ColumnParameters.builder()
                    .columnName("col1")
                    .columnType("string")
                    .larkBaseFieldId("fieldId123")
                    .larkBaseColumnType("TEXT")
                    .larkBaseId("baseId456")
                    .larkBaseTableId("tableId789")
                    .build()
        );

        Collection<Column> results = Util.constructColumns(params);
        assertEquals(2, results.size());

        List<Column> columnList = new ArrayList<>(results);
        assertEquals("col1", columnList.get(0).name());
        assertEquals("string", columnList.get(0).type());
        assertEquals("LarkBaseId=baseId456/LarkBaseTableId=tableId789/LarkBaseFieldId=fieldId123/LarkBaseFieldName=col1/LarkBaseFieldType=TEXT", columnList.get(0).comment());
    }

    @Test
    void doesGlueDatabasesNameValid() {
        assertFalse(Util.doesGlueDatabasesNameValid(List.of("valid_name", "another_valid_123")));
        assertTrue(Util.doesGlueDatabasesNameValid(List.of("invalid-name")));
        assertTrue(Util.doesGlueDatabasesNameValid(List.of("same_name", "same_name")));
    }

    @Test
    void sanitizeGlueRelatedName() {
        assertEquals("test_name_123", Util.sanitizeGlueRelatedName("test-name-123"));
        assertEquals("special_chars_", Util.sanitizeGlueRelatedName("special@chars!"));
        assertEquals("mixed_case_name", Util.sanitizeGlueRelatedName("Mixed-Case-Name"));
    }
}
