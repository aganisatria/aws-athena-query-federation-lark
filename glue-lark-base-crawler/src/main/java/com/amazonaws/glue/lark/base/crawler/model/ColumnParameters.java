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

import com.amazonaws.glue.lark.base.crawler.util.Util;

import static java.util.Objects.requireNonNull;

/**
 * This class is used to store the parameters for the Lark Base Crawler Column
 */
public class ColumnParameters
{
    private final String columnName;
    private final String originalColumnName;
    private final String columnType;
    private final String larkBaseFieldId;
    private final String larkBaseColumnType;
    private final String larkBaseId;
    private final String larkBaseTableId;

    private ColumnParameters(Builder builder)
    {
        this.columnName = Util.sanitizeGlueRelatedName(builder.columnName);
        this.originalColumnName = builder.columnName;
        this.columnType = builder.columnType;
        this.larkBaseFieldId = builder.larkBaseFieldId;
        this.larkBaseColumnType = builder.larkBaseColumnType;
        this.larkBaseId = builder.larkBaseId;
        this.larkBaseTableId = builder.larkBaseTableId;
    }

    public String getColumnName()
    {
        return columnName;
    }

    public String getOriginalColumnName()
    {
        return originalColumnName;
    }

    public String getColumnType()
    {
        return columnType;
    }

    public String getLarkBaseRecordId()
    {
        return larkBaseFieldId;
    }

    public String getLarkBaseColumnType()
    {
        return larkBaseColumnType;
    }

    public String getLarkBaseId()
    {
        return larkBaseId;
    }

    public String getLarkBaseTableId()
    {
        return larkBaseTableId;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private String columnName;
        private String columnType;
        private String larkBaseFieldId;
        private String larkBaseColumnType;
        private String larkBaseId;
        private String larkBaseTableId;

        public Builder columnName(String columnName)
        {
            this.columnName = columnName;
            return this;
        }

        public Builder columnType(String columnType)
        {
            this.columnType = columnType;
            return this;
        }

        public Builder larkBaseFieldId(String larkBaseFieldId)
        {
            this.larkBaseFieldId = larkBaseFieldId;
            return this;
        }

        public Builder larkBaseColumnType(String larkBaseColumnType)
        {
            this.larkBaseColumnType = larkBaseColumnType;
            return this;
        }

        public Builder larkBaseId(String larkBaseId)
        {
            this.larkBaseId = larkBaseId;
            return this;
        }

        public Builder larkBaseTableId(String larkBaseTableId)
        {
            this.larkBaseTableId = larkBaseTableId;
            return this;
        }

        public ColumnParameters build()
        {
            requireNonNull(columnName, "columnName is required");
            requireNonNull(larkBaseFieldId, "larkBaseFieldId is required");
            requireNonNull(larkBaseColumnType, "larkBaseColumnType is required");
            requireNonNull(larkBaseId, "larkBaseId is required");
            requireNonNull(larkBaseTableId, "larkBaseTableId is required");
            return new ColumnParameters(this);
        }
    }
} 
