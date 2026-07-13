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

import java.util.Collections;
import java.util.Set;

/**
 * Record for Lark Database Record
 * @param id The database ID
 * @param name The database name
 * @param whitelistTableIds Lark table IDs to exclusively crawl for this database, from the control table's
 *                          "whitelist_tables" column. If empty, all tables in this database are crawled
 *                          (subject to blacklistTableIds).
 * @param blacklistTableIds Lark table IDs to always exclude from crawling for this database, from the control
 *                          table's "blacklist_tables" column. Takes precedence over whitelistTableIds.
 */
public record LarkDatabaseRecord(String id, String name, Set<String> whitelistTableIds, Set<String> blacklistTableIds)
{
    public LarkDatabaseRecord(String id, String name)
    {
        this(id, name, Collections.emptySet(), Collections.emptySet());
    }
}
