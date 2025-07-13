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
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.amazonaws.athena.connectors.lark.base.model.enums.UITypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.amazonaws.athena.connectors.lark.base.BaseConstants.RESERVED_SPLIT_KEY;

/**
 * Translates Athena constraints into Lark Bitable FQL (Filter Query Language) format.
 * Refers to Lark API documentation:
 * <a href="https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-record/filter">...</a>
 */
public final class ConstraintTranslator {
    private static final Logger logger = LoggerFactory.getLogger(ConstraintTranslator.class);

    private static final String LARK_CURRENT_VALUE = "CurrentValue";
    public static final String LARK_AND = "AND";
    private static final String LARK_OR = "OR";
    private static final String LARK_OPERATOR_EQ = "=";
    private static final String LARK_OPERATOR_NE = "!=";
    private static final String LARK_OPERATOR_GT = ">";
    private static final String LARK_OPERATOR_GE = ">=";
    private static final String LARK_OPERATOR_LT = "<";
    private static final String LARK_OPERATOR_LE = "<=";
    private static final String LARK_EMPTY_STRING = "\"\"";

    private static final String LARK_SORT_ASC = "ASC";
    private static final String LARK_SORT_DESC = "DESC";

    private ConstraintTranslator() {}

    /**
     * Builds the FQL column expression string.
     */
    private static String buildColumnExpression(String column) {
        return String.format("%s.[%s]", LARK_CURRENT_VALUE, column);
    }

    private static String translateSingleRangeToFql(Range range, String columnExpr, UITypeEnum fieldUiType) {
        if (range == null) {
            return null;
        }
        Marker low = range.getLow();
        Marker high = range.getHigh();
        List<String> conditions = new ArrayList<>();

        if (!low.isLowerUnbounded()) {
            String lowOp = (low.getBound() == Marker.Bound.EXACTLY) ? LARK_OPERATOR_GE : LARK_OPERATOR_GT;
            Object lowValue = convertValueIfNeeded(low.getValue(), fieldUiType);
            conditions.add(String.format("%s%s%s", columnExpr, lowOp, formatValue(lowValue)));
        }
        if (!high.isUpperUnbounded()) {
            String highOp = (high.getBound() == Marker.Bound.EXACTLY) ? LARK_OPERATOR_LE : LARK_OPERATOR_LT;
            Object highValue = convertValueIfNeeded(high.getValue(), fieldUiType);
            conditions.add(String.format("%s%s%s", columnExpr, highOp, formatValue(highValue)));
        }

        if (conditions.isEmpty()) {
            return null;
        }
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        return String.format("%s(%s)", LARK_AND, String.join(",", conditions));
    }

    /**
     * Appends a range condition (from a SortedRangeSet) to the FQL filter builder.
     */
    private static void appendRangeCondition(StringBuilder builder, String column, SortedRangeSet rangeSet, UITypeEnum fieldUiType) {
        String columnExpr = buildColumnExpression(column);

        // 1. Handle Single Value case (Equality atau IS NULL SQL)
        if (rangeSet.isSingleValue()) {
            Object value = rangeSet.getSingleValue();
            if (value == null) {
                if (fieldUiType == UITypeEnum.CHECKBOX) {
                    logger.info("Translating single value null (IS NULL) for Checkbox column '{}' to FQL: {} = 0", column, columnExpr);
                    builder.append(String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, "0"));
                } else {
                    logger.info("Translating single value null (IS NULL) to FQL: {} = \"\"", columnExpr);
                    builder.append(String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, LARK_EMPTY_STRING));
                }
            } else {
                Object convertedValue = convertValueIfNeeded(value, fieldUiType);
                builder.append(String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, formatValue(convertedValue)));
            }
            return;
        }

        if (!rangeSet.isNullAllowed()) {
            boolean isEffectivelyIsNotNull = false;
            try {
                List<Range> ranges = rangeSet.getRanges().getOrderedRanges();
                if (ranges != null && ranges.size() == 1) {
                    Range onlyRange = ranges.get(0);
                    if (onlyRange.getLow().isNullValue() && onlyRange.getLow().getBound() == Marker.Bound.ABOVE &&
                            onlyRange.getHigh().isNullValue() && onlyRange.getHigh().getBound() == Marker.Bound.BELOW) {
                        isEffectivelyIsNotNull = true;
                        logger.debug("Detected IS NOT NULL pattern from specific ordered range for column '{}'", column);
                    }
                }

                if (!isEffectivelyIsNotNull) {
                    Range spanForIsNotNullCheck = rangeSet.getSpan();
                    if (spanForIsNotNullCheck != null &&
                            spanForIsNotNullCheck.getLow().isLowerUnbounded() &&
                            spanForIsNotNullCheck.getHigh().isUpperUnbounded()) {
                        isEffectivelyIsNotNull = true;
                        logger.debug("Detected IS NOT NULL pattern from unbounded span for column '{}'", column);
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not fully determine if SortedRangeSet for column '{}' is unbounded for IS NOT NULL check due to error. Attempting span check. Error: {}", column, e.getMessage());
                try {
                    Range spanForIsNotNullCheck = rangeSet.getSpan();
                    if (spanForIsNotNullCheck != null &&
                            spanForIsNotNullCheck.getLow().isLowerUnbounded() &&
                            spanForIsNotNullCheck.getHigh().isUpperUnbounded()) {
                        isEffectivelyIsNotNull = true;
                        logger.debug("Detected IS NOT NULL pattern from unbounded span (fallback) for column '{}'", column);
                    }
                } catch (Exception e2) {
                    logger.warn("Span check for IS NOT NULL also failed for column '{}'", column, e2);
                }
            }

            if (isEffectivelyIsNotNull) {
                if (fieldUiType == UITypeEnum.CHECKBOX) {
                    logger.info("Translating IS NOT NULL for Checkbox column '{}' to FQL: {} = 1", column, columnExpr);
                    builder.append(String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, "1"));
                } else {
                    logger.info("Translating SortedRangeSet (nullAllowed=false, effectively unbounded/IS NOT NULL) to FQL (NOT({}=\"\")) for column: {}", columnExpr, column);
                    builder.append(String.format("NOT(%s%s%s)",
                            columnExpr, LARK_OPERATOR_EQ, LARK_EMPTY_STRING));
                }
                return;
            }
        }

        // 2. Attempt to detect the 'Not Equal' pattern from SortedRangeSet
        boolean handledNePattern = false;
        try {
            Ranges rangesObj = rangeSet.getRanges();
            if (rangesObj != null) {
                List<Range> ranges = rangesObj.getOrderedRanges();
                if (ranges != null && ranges.size() == 2) {
                    Range range1 = ranges.get(0);
                    Range range2 = ranges.get(1);
                    Marker high1 = range1.getHigh();
                    Marker low2 = range2.getLow();

                    if (range1.getLow().isLowerUnbounded() && high1.getBound() == Marker.Bound.BELOW &&
                            low2.getBound() == Marker.Bound.ABOVE && range2.getHigh().isUpperUnbounded() &&
                            Objects.equals(high1.getValue(), low2.getValue())) {
                        Object excludedValue = convertValueIfNeeded(high1.getValue(), fieldUiType);
                        logger.info("Detected SortedRangeSet pattern for '!=' condition for column '{}', value: {}", column, excludedValue);
                        builder.append(String.format("%s %s %s", columnExpr, LARK_OPERATOR_NE, formatValue(excludedValue)));
                        handledNePattern = true;
                    }
                }
            }
        } catch (UnsupportedOperationException uoe) {
            logger.warn("getOrderedRanges() not supported/failed for column '{}' while detecting '!=' pattern. Error: {}", column, uoe.getMessage());
        } catch (Exception e) {
            logger.warn("Error attempting to detect '!=' pattern in SortedRangeSet for column '{}'. Error: {}", column, e.getMessage());
        }

        if (handledNePattern) {
            return;
        }

        // 3. Handle multiple ranges (seperti dari NOT IN)
        List<String> individualRangeConditions = new ArrayList<>();
        boolean processedViaOrderedRanges = false;

        try {
            List<Range> orderedRanges = rangeSet.getRanges().getOrderedRanges();
            if (orderedRanges != null && !orderedRanges.isEmpty()) {
                logger.info("Processing SortedRangeSet for column '{}' with {} ordered ranges (main path).", column, orderedRanges.size());
                for (Range currentRange : orderedRanges) {
                    String conditionFromRange = translateSingleRangeToFql(currentRange, columnExpr, fieldUiType);
                    if (conditionFromRange != null && !conditionFromRange.isEmpty()) {
                        individualRangeConditions.add(conditionFromRange);
                    }
                }
                if (!individualRangeConditions.isEmpty()) {
                    processedViaOrderedRanges = true;
                }
            }
        } catch (UnsupportedOperationException uoe) {
            logger.warn("getOrderedRanges() not supported for column '{}' (main path), will try getSpan(). Error: {}", column, uoe.getMessage());
        } catch (Exception e) {
            logger.error("Error processing orderedRanges for column '{}' (main path), will try getSpan(). Error: {}", column, e.getMessage(), e);
        }

        if (!processedViaOrderedRanges) {
            logger.info("No conditions derived from orderedRanges for column '{}' (main path), attempting getSpan().", column);
            if (rangeSet.isNone()) {
                logger.info("RangeSet for column {} isNone (via getSpan path). No condition added.", column);
                return;
            }
            if (rangeSet.isAll() && rangeSet.isNullAllowed()){
                logger.info("RangeSet for column {} isAll and NullAllowed (via getSpan path). No condition added.", column);
                return;
            }

            Range span = rangeSet.getSpan();
            String conditionFromSpan = translateSingleRangeToFql(span, columnExpr, fieldUiType);
            if (conditionFromSpan != null && !conditionFromSpan.isEmpty()) {
                individualRangeConditions.add(conditionFromSpan);
            }
        }

        if (individualRangeConditions.isEmpty()) {
            logger.info("No conditions derived from SortedRangeSet for column {} after all processing attempts. RangeSet: {}", column, rangeSet);
            return;
        }

        boolean addExplicitEmptyStringCheck = false;
        if (!rangeSet.isNullAllowed() &&
                (fieldUiType == UITypeEnum.TEXT ||
                        fieldUiType == UITypeEnum.SINGLE_SELECT ||
                        fieldUiType == UITypeEnum.BARCODE ||
                        fieldUiType == UITypeEnum.PHONE ||
                        fieldUiType == UITypeEnum.EMAIL)) {

            final String emptyStringFqlCondition = String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, LARK_EMPTY_STRING);
            boolean alreadyCheckingForEmpty = individualRangeConditions.stream()
                    .anyMatch(s -> s.equals(emptyStringFqlCondition));

            if (!alreadyCheckingForEmpty) {
                logger.info("Adding explicit OR CurrentValue.[{}]=\"\" for string field based on user hypothesis.", column);
                addExplicitEmptyStringCheck = true;
            }
        }

        if (addExplicitEmptyStringCheck) {
            individualRangeConditions.add(String.format("%s%s%s", columnExpr, LARK_OPERATOR_EQ, LARK_EMPTY_STRING));
        }

        if (individualRangeConditions.size() == 1) {
            builder.append(individualRangeConditions.get(0));
        } else {
            builder.append(String.format("%s(%s)", LARK_OR, String.join(",", individualRangeConditions)));
        }
        logger.info("Final range condition for column {}: {}", column, builder);
    }

    /**
     * Appends an equatable condition (IN, NOT IN) to the FQL filter builder.
     */
    private static void appendEquatableCondition(StringBuilder builder, String column, EquatableValueSet valueSet, UITypeEnum fieldUiType) {
        String columnExpr = buildColumnExpression(column);
        boolean isWhiteList = valueSet.isWhiteList();
        String operator = isWhiteList ? LARK_OPERATOR_EQ : LARK_OPERATOR_NE;
        String logicalOperator = isWhiteList ? LARK_OR : LARK_AND;

        int valueCount = valueSet.getValueBlock().getRowCount();
        if (valueCount == 0) { return; }

        if (valueCount == 1) {
            Object value = valueSet.getValue(0);
            Object convertedValue = convertValueIfNeeded(value, fieldUiType);
            builder.append(String.format("%s%s%s", columnExpr, operator, formatValue(convertedValue)));
            return;
        }

        String conditions = IntStream.range(0, valueCount)
                .mapToObj(i -> {
                    Object value = valueSet.getValue(i);
                    Object convertedValue = convertValueIfNeeded(value, fieldUiType);
                    return String.format("%s%s%s", columnExpr, operator, formatValue(convertedValue));
                })
                .collect(Collectors.joining(","));

        builder.append(String.format("%s(%s)", logicalOperator, conditions));
    }

    /**
     * Converts boolean values to integers (1 for true, 0 for false) if the field type is Checkbox.
     * Otherwise, returns the original value.
     *
     * @param value       The original value from ValueSet.
     * @param fieldUiType The UI Type of the field.
     * @return The converted value (Integer 1 or 0) or the original value.
     */
    private static Object convertValueIfNeeded(Object value, UITypeEnum fieldUiType) {
        if (fieldUiType == UITypeEnum.CHECKBOX && value instanceof Boolean b) {
            logger.info("Converting Checkbox value {} to integer", b);
            return b ? 1 : 0;
        }
        return value;
    }

    /**
     * Formats a Java object value into its corresponding FQL string representation.
     */
    private static String formatValue(Object value) {
        if (value == null) { return LARK_EMPTY_STRING; }

        if (value instanceof String s) {
            return "\"" + escapeString(s) + "\"";
        } else if (value instanceof Number n) {
            return n.toString();
        } else if (value instanceof Boolean b) {
            return b.toString();
        } else {
            return "\"" + escapeString(value.toString()) + "\"";
        }
    }

    /**
     * Helper to escape special characters (specifically double quotes) within a string value.
     */
    private static String escapeString(String text) {
        // Escape double quotes for FQL compatibility
        return text.replace("\"", "\\\"");
    }

    /**
     * Converts Athena ORDER BY constraints to the Lark Bitable sort JSON format.
     */
    public static String toSortJson(List<OrderByField> orderByFields, List<AthenaFieldLarkBaseMapping> fieldNameMappings) {
        if (orderByFields == null || orderByFields.isEmpty()) { return null; }

        String sortExpressions = orderByFields.stream()
                .map(field -> {
                    String lowercaseColumnName = field.getColumnName();
                    String originalColumnName = getOriginalColumnName(lowercaseColumnName, fieldNameMappings);
                    if (originalColumnName == null || originalColumnName.isEmpty()) {
                        logger.warn("Skipping ORDER BY for column with null or empty name derived from: {}", lowercaseColumnName);
                        return null;
                    }

                    String directionEnumName = field.getDirection().name();
                    String direction = LARK_SORT_ASC;
                    if (directionEnumName.contains("DESC")) {
                        direction = LARK_SORT_DESC;
                    }

                    return String.format("\"%s %s\"", escapeString(originalColumnName), direction);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        return sortExpressions.isEmpty() ? "[]" : "[" + sortExpressions + "]";
    }

    /**
     * Converts Athena constraints into the Lark Bitable FQL filter format.
     * Includes logging to inspect incoming ValueSet details.
     *
     * @param constraints       Map of lowercase column names to ValueSet from Athena.
     * @param fieldNameMappings Map from lowercase column names to original-cased names.
     * @return Filter string in Lark FQL format, or an empty string if no filters or an error occurs.
     */
    public static String toFilterJson(Map<String, ValueSet> constraints, List<AthenaFieldLarkBaseMapping> fieldNameMappings) {
        if (constraints == null || constraints.isEmpty()) {
            return "";
        }

        for (Map.Entry<String, ValueSet> entry : constraints.entrySet()) {
            String logColName = entry.getKey();
            ValueSet logValueSet = entry.getValue();
            String typeName = (logValueSet == null) ? "null" : logValueSet.getClass().getSimpleName();
            logger.info("- Column: ''{}'', ValueSet Type: {}, Details: {}", logColName, typeName, logValueSet);
        }

        List<String> individualFilters = new ArrayList<>();
        try {
            for (Map.Entry<String, ValueSet> entry : constraints.entrySet()) {
                String lowercaseColumnName = entry.getKey();
                ValueSet valueSet = entry.getValue();

                AthenaFieldLarkBaseMapping mapping = findMappingForColumn(lowercaseColumnName, fieldNameMappings);

                if (mapping == null) {
                    logger.warn("ConstraintTranslator: No mapping found for Athena column: '{}'. Skipping constraint.", lowercaseColumnName);
                    continue;
                }

                String originalColumnName = mapping.larkBaseFieldName();
                UITypeEnum fieldUiType = mapping.nestedUIType().uiType();

                if (originalColumnName == null || originalColumnName.isEmpty()) {
                    logger.warn("ConstraintTranslator: Lark name is null or empty in mapping for Athena column: '{}'. Skipping constraint.", lowercaseColumnName);
                    continue;
                }

                if (!isUiTypeAllowedForPushdown(fieldUiType)) {
                    logger.info("ConstraintTranslator: Skipping pushdown for column '{}' (Lark: '{}') because its UI type ({}) is not supported for filter pushdown.",
                            lowercaseColumnName, originalColumnName, fieldUiType);
                    continue;
                }

                StringBuilder conditionBuilder = new StringBuilder();

                if (valueSet instanceof SortedRangeSet srs) {
                    appendRangeCondition(conditionBuilder, originalColumnName, srs, fieldUiType);
                } else if (valueSet instanceof EquatableValueSet evs) {
                    appendEquatableCondition(conditionBuilder, originalColumnName, evs, fieldUiType);
                } else if (valueSet instanceof AllOrNoneValueSet aon) {
                    if (!aon.isAll()) {
                        if (fieldUiType != UITypeEnum.CHECKBOX) {
                            logger.info("Translating AllOrNoneValueSet(isAll=false) to IS NULL (field = \"\") check for column: {}", originalColumnName);
                            conditionBuilder.append(String.format("%s %s %s",
                                    buildColumnExpression(originalColumnName), LARK_OPERATOR_EQ, LARK_EMPTY_STRING));
                        }
                    }
                } else if (valueSet != null) {
                    logger.warn("Unsupported ValueSet type: {}", valueSet.getClass().getName());
                }

                if (!conditionBuilder.isEmpty()) {
                    individualFilters.add(conditionBuilder.toString());
                }
            }

            if (individualFilters.isEmpty()) { return ""; }
            if (individualFilters.size() == 1) { return individualFilters.get(0); }
            return String.format("%s(%s)", LARK_AND, String.join(", ", individualFilters));

        } catch (Exception e) {
            logger.warn("Returning empty filter string due to error: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Helper method to get the original-cased column name from the mapping.
     */
    private static String getOriginalColumnName(String lowercaseColumnName, List<AthenaFieldLarkBaseMapping> fieldNameMappings) {
        if (lowercaseColumnName == null || lowercaseColumnName.isEmpty()) { return null; }
        if (fieldNameMappings == null) {
            logger.warn("fieldNameMappings map is null. Using lowercase column name: {}", lowercaseColumnName);
            return lowercaseColumnName;
        }
        String originalName = fieldNameMappings.stream()
                .filter(mapping -> lowercaseColumnName.equals(mapping.athenaName()))
                .map(AthenaFieldLarkBaseMapping::larkBaseFieldName)
                .findFirst()
                .orElse(null);

        if (originalName == null) {
            logger.warn("No mapping found for lowercase column: {}. Using lowercase name as fallback.", lowercaseColumnName);
            return lowercaseColumnName;
        }
        return originalName;
    }

    /**
     * Finds the AthenaFieldLarkBaseMapping for a given lowercase Athena column name.
     *
     * @param lowercaseAthenaName The lowercase Athena column name.
     * @param mappings            The list of mappings.
     * @return The mapping object, or null if not found.
     */
    private static AthenaFieldLarkBaseMapping findMappingForColumn(String lowercaseAthenaName, List<AthenaFieldLarkBaseMapping> mappings) {
        if (mappings == null || lowercaseAthenaName == null) return null;
        return mappings.stream()
                .filter(m -> lowercaseAthenaName.equals(m.athenaName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a given Lark UI Type is allowed for filter pushdown based on the defined rules.
     *
     * @param uiType The UITypeEnum to check.
     * @return true if pushdown is allowed, false otherwise.
     */
    private static boolean isUiTypeAllowedForPushdown(UITypeEnum uiType) {
        return switch (uiType) {
            case TEXT, BARCODE, SINGLE_SELECT, PHONE, NUMBER, PROGRESS, CURRENCY, RATING, CHECKBOX -> true;
            default -> false;
        };
    }

    /**
     * Converts the split start and end index into a filter string for Lark Bitable.
     *
     * @param startIndex The start index of the split.
     * @param endIndex   The end index of the split.
     * @return Filter string in Lark FQL format.
     */
    public static String toSplitFilterJson(String existingFilter, long startIndex, long endIndex) {
        if (startIndex <= 0 || endIndex <= 0) {
            return existingFilter;
        }

        String newFilter = "AND(" + LARK_CURRENT_VALUE + ".[" + RESERVED_SPLIT_KEY + "]" + LARK_OPERATOR_GE +
                startIndex + "," + LARK_CURRENT_VALUE + ".[" + RESERVED_SPLIT_KEY + "]" + LARK_OPERATOR_LE +
                endIndex + ")";

        if (existingFilter.isBlank()) {
            return newFilter;
        }

        return "AND(" + existingFilter + "," + newFilter + ")";
    }
}