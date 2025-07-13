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
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TableOnUpdateDatabaseProcessResultTest {

    @Test
    public void recordCreation_withStringLists_shouldHoldValues() {
        List<String> itemsToProcess = Arrays.asList("processItem1", "processItem2");
        List<String> itemsToKeep = Collections.singletonList("keepItem1");

        TableOnUpdateDatabaseProcessResult<String> result =
                new TableOnUpdateDatabaseProcessResult<>(itemsToProcess, itemsToKeep);

        assertEquals(itemsToProcess, result.itemsToProcess());
        assertEquals(itemsToKeep, result.itemsToKeep());
    }

    @Test
    public void recordCreation_withIntegerLists_shouldHoldValues() {
        List<Integer> itemsToProcess = Arrays.asList(1, 2, 3);
        List<Integer> itemsToKeep = Arrays.asList(4, 5);

        TableOnUpdateDatabaseProcessResult<Integer> result =
                new TableOnUpdateDatabaseProcessResult<>(itemsToProcess, itemsToKeep);

        assertEquals(itemsToProcess, result.itemsToProcess());
        assertEquals(itemsToKeep, result.itemsToKeep());
    }

    @Test
    public void recordCreation_withEmptyLists_shouldHoldEmptyLists() {
        List<Object> itemsToProcess = Collections.emptyList();
        List<Object> itemsToKeep = Collections.emptyList();

        TableOnUpdateDatabaseProcessResult<Object> result =
                new TableOnUpdateDatabaseProcessResult<>(itemsToProcess, itemsToKeep);

        assertTrue(result.itemsToProcess().isEmpty());
        assertTrue(result.itemsToKeep().isEmpty());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        List<String> listP1 = List.of("a");
        List<String> listK1 = List.of("b");
        List<String> listP2 = List.of("c");

        TableOnUpdateDatabaseProcessResult<String> result1a = new TableOnUpdateDatabaseProcessResult<>(listP1, listK1);
        TableOnUpdateDatabaseProcessResult<String> result1b = new TableOnUpdateDatabaseProcessResult<>(listP1, listK1);
        TableOnUpdateDatabaseProcessResult<String> result2 = new TableOnUpdateDatabaseProcessResult<>(listP2, listK1);
        TableOnUpdateDatabaseProcessResult<String> result3 = new TableOnUpdateDatabaseProcessResult<>(listP1, listP2);


        assertEquals(result1a, result1b);
        assertNotEquals(result1a, result2);
        assertNotEquals(result1a, result3);
        assertEquals(result1a.hashCode(), result1b.hashCode());
    }
}
