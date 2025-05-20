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

import static java.util.Objects.requireNonNull;

/**
 * Record for Lark Base Crawler Table Input Parameters
 * 
 */
public class TableInputParameters {
    protected final String larkTableName;
    protected final String larkBaseId;
    protected final String larkTableId;

    private TableInputParameters(Builder builder) {
        this.larkTableName = builder.larkTableName;
        this.larkBaseId = builder.larkBaseId;
        this.larkTableId = builder.larkTableId;
    }

    public String getLarkTableName() {
        return larkTableName;
    }

    public String getLarkBaseId() {
        return larkBaseId;
    }

    public String getLarkTableId() {
        return larkTableId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String larkTableName;
        private String larkBaseId;
        private String larkTableId;

        public Builder larkTableName(String larkTableName) {
            this.larkTableName = larkTableName;
            return this;
        }

        public Builder larkBaseId(String larkBaseId) {
            this.larkBaseId = larkBaseId;
            return this;
        }

        public Builder larkTableId(String larkTableId) {
            this.larkTableId = larkTableId;
            return this;
        }

        public TableInputParameters build() {
            requireNonNull(larkTableName, "larkTableName is required");
            requireNonNull(larkBaseId, "larkBaseId is required");
            requireNonNull(larkTableId, "larkTableId is required");
            return new TableInputParameters(this);
        }
    }
}
