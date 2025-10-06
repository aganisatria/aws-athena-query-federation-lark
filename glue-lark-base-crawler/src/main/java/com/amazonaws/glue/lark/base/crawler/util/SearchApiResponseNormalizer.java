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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes Lark Base Search API responses to match List API format.
 *
 * The Search API returns different field formats than the List API:
 * - TEXT fields: Search API returns [{text, type}], List API returns plain string
 * - FORMULA/LOOKUP fields: Search API wraps in {type, value}, List API returns value directly
 * - NUMBER/CURRENCY: Search API returns numbers, List API returns strings
 * - USER fields: Search API returns arrays, List API returns arrays (same)
 * - CREATED_USER/MODIFIED_USER: Search API returns arrays, but schema expects single object
 */
public final class SearchApiResponseNormalizer
{
    private static final Logger logger = LoggerFactory.getLogger(SearchApiResponseNormalizer.class);

    private SearchApiResponseNormalizer() {}

    /**
     * Normalizes all fields in a record from Search API format to List API format
     * @param searchFields The fields from Search API response
     * @return Normalized fields matching List API format
     */
    public static Map<String, Object> normalizeRecordFields(Map<String, Object> searchFields)
    {
        if (searchFields == null) {
            return new HashMap<>();
        }

        Map<String, Object> normalized = new HashMap<>();

        for (Map.Entry<String, Object> entry : searchFields.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            Object normalizedValue = normalizeFieldValue(fieldName, value);
            normalized.put(fieldName, normalizedValue);
        }

        return normalized;
    }

    /**
     * Normalizes a single field value
     * @param fieldName The field name (for logging)
     * @param value The value from Search API
     * @return Normalized value
     */
    private static Object normalizeFieldValue(String fieldName, Object value)
    {
        if (value == null) {
            return null;
        }

        // Handle wrapped formula/lookup format: {"type": N, "value": [...]}
        if (value instanceof Map) {
            Map<String, Object> mapValue = (Map<String, Object>) value;

            // Check if this is a wrapped value structure
            if (mapValue.containsKey("type") && mapValue.containsKey("value")) {
                Object unwrappedValue = mapValue.get("value");
                logger.debug("Unwrapping formula/lookup value for field '{}': {} -> {}",
                    fieldName, value, unwrappedValue);
                // Recursively normalize the unwrapped value
                return normalizeFieldValue(fieldName, unwrappedValue);
            }
        }

        // Handle array values
        if (value instanceof List) {
            List<?> listValue = (List<?>) value;

            if (listValue.isEmpty()) {
                return listValue;
            }

            Object firstItem = listValue.get(0);

            // Handle TEXT field format: [{"text": "value", "type": "text"}]
            if (firstItem instanceof Map) {
                Map<String, Object> firstMap = (Map<String, Object>) firstItem;

                // CREATED_USER and MODIFIED_USER: extract first element for STRUCT compatibility
                // These fields come as arrays from Search API but schema expects single object
                if (firstMap.containsKey("id") && firstMap.containsKey("name") &&
                    (fieldName.contains("created_user") || fieldName.contains("modified_user"))) {
                    logger.debug("Converting {}_user array to single object for field '{}'",
                        fieldName.contains("created") ? "created" : "modified", fieldName);
                    return firstItem;
                }

                // Text fields: extract text from single-element arrays
                if (firstMap.containsKey("text") && firstMap.containsKey("type") &&
                    "text".equals(firstMap.get("type"))) {
                    // For single element text arrays, extract the text value
                    if (listValue.size() == 1) {
                        String extractedText = (String) firstMap.get("text");
                        logger.debug("Extracting text from single-element array for field '{}': {} -> {}",
                            fieldName, value, extractedText);
                        return extractedText;
                    }
                    // For multi-element arrays (shouldn't happen for TEXT fields, but keep array)
                    logger.debug("Keeping multi-element text array for field '{}': {}", fieldName, value);
                }
            }
        }

        // Return value unchanged if no normalization needed
        return value;
    }
}
