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

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

public class LarkDatabaseRecordTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        String id = "dbId123";
        String name = "MyDatabase";
        LarkDatabaseRecord record = new LarkDatabaseRecord(id, name);

        assertEquals(id, record.id());
        assertEquals(name, record.name());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        LarkDatabaseRecord record1a = new LarkDatabaseRecord("id1", "name1");
        LarkDatabaseRecord record1b = new LarkDatabaseRecord("id1", "name1");
        LarkDatabaseRecord record2 = new LarkDatabaseRecord("id2", "name2");

        assertEquals(record1a, record1b);
        assertNotEquals(record1a, record2);
        assertEquals(record1a.hashCode(), record1b.hashCode());
    }

    @Test
    public void twoArgConstructor_defaultsWhitelistAndBlacklistToEmpty() {
        LarkDatabaseRecord record = new LarkDatabaseRecord("id1", "name1");

        assertEquals(Collections.emptySet(), record.whitelistTableIds());
        assertEquals(Collections.emptySet(), record.blacklistTableIds());
    }

    @Test
    public void fourArgConstructor_shouldHoldWhitelistAndBlacklistValues() {
        Set<String> whitelist = Set.of("tbl1", "tbl2");
        Set<String> blacklist = Set.of("tbl3");

        LarkDatabaseRecord record = new LarkDatabaseRecord("id1", "name1", whitelist, blacklist);

        assertEquals(whitelist, record.whitelistTableIds());
        assertEquals(blacklist, record.blacklistTableIds());
    }
}
