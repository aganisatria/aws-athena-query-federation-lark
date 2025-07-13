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
package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class TableInputParametersTest {

    @Test
    public void builder_allFieldsSet_shouldCreateObjectCorrectly() {
        String tableName = "TestTable";
        String baseId = "base123";
        String tableId = "table456";

        TableInputParameters params = TableInputParameters.builder()
                .larkTableName(tableName)
                .larkBaseId(baseId)
                .larkTableId(tableId)
                .build();

        assertEquals(tableName, params.getLarkTableName());
        assertEquals(baseId, params.getLarkBaseId());
        assertEquals(tableId, params.getLarkTableId());
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkTableName_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkBaseId("base123")
                .larkTableId("table456")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkBaseId_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkTableName("TestTable")
                .larkTableId("table456")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkTableId_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkTableName("TestTable")
                .larkBaseId("base123")
                .build();
    }
}
