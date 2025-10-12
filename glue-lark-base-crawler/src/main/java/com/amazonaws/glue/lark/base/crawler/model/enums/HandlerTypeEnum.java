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
package com.amazonaws.glue.lark.base.crawler.model.enums;

/**
 * Enum for Handler Type
 */
public enum HandlerTypeEnum
{
    LARK_BASE("LARK_BASE"),
    LARK_DRIVE("LARK_DRIVE"),
    UNKNOWN("UNKNOWN");

    private final String handlerType;

    HandlerTypeEnum(String handlerType)
    {
        this.handlerType = handlerType;
    }

    public static HandlerTypeEnum fromString(String handlerType)
    {
        for (HandlerTypeEnum value : HandlerTypeEnum.values()) {
            if (value.handlerType.equals(handlerType)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
