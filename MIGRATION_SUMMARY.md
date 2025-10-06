# Lark Base Search API Migration Summary

## Overview
Successfully migrated from deprecated Lark Base List Records API to the new Search Records API with full push down predicate support.

## API Migration

### Old API (Deprecated)
- **Endpoint**: `GET /bitable/v1/apps/{app_token}/tables/{table_id}/records`
- **Filter Format**: FQL string format: `CurrentValue.[field_name] = value`
- **Limitations**: Limited filter capabilities, string-based format

### New API (Current)
- **Endpoint**: `POST /bitable/v1/apps/{app_token}/tables/{table_id}/records/search`
- **Filter Format**: JSON object with structured conditions
- **Benefits**: More powerful filtering, better performance, official support

## Changes Made

### 1. New Filter Translator (`SearchApiFilterTranslator.java`)
Created a new translator that converts Athena constraints to Lark Search API JSON format:

**Supported Operators:**
- `is` - Equality (=)
- `isNot` - Inequality (!=)
- `isGreater` - Greater than (>)
- `isGreaterEqual` - Greater than or equal (>=)
- `isLess` - Less than (<)
- `isLessEqual` - Less than or equal (<=)
- `isEmpty` - IS NULL
- `isNotEmpty` - IS NOT NULL

**Key Methods:**
- `toFilterJson()` - Converts constraints to filter JSON
- `toSortJson()` - Converts ORDER BY to sort JSON
- `toSplitFilterJson()` - Handles parallel split filters

### 2. Updated Request Model (`SearchRecordsRequest.java`)
Added `@JsonRawValue` annotation to `filter` and `sort` fields to ensure they serialize as JSON objects instead of escaped strings.

**Before:**
```java
@JsonProperty("filter")
private final String filter;
```

**After:**
```java
@JsonProperty("filter")
@JsonRawValue
private final String filter;
```

### 3. Updated Service Layer (`LarkBaseService.java`)
- Changed from GET to POST request
- Updated to use `/records/search` endpoint
- Request body includes filter and sort as JSON objects

### 4. Updated Handlers
- `BaseMetadataHandler.java` - Uses `SearchApiFilterTranslator` instead of `ConstraintTranslator`
- `BaseRecordHandler.java` - Updated split filter logic

### 5. Response Normalization (`SearchApiResponseNormalizer.java`)
Transforms Search API responses to match List API structure for backward compatibility:
- TEXT fields: Extracts text from `[{text, type}]` arrays
- FORMULA/LOOKUP: Unwraps `{type, value}` structure
- CREATED_USER/MODIFIED_USER: Converts array to single object for STRUCT compatibility

### 6. Fixed Timestamp Handling (`LarkBaseTypeUtils.java`)
Restored `DATEMILLI` type for timestamp fields (was accidentally changed to `null`).

## Supported Field Types for Push Down

| Field Type | Operators Supported | Notes |
|-----------|-------------------|-------|
| CHECKBOX | `=`, `IS NOT NULL` | `IS NOT NULL` → `= true` |
| NUMBER | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Full numeric comparison support |
| TEXT | `=`, `IS NULL`, `IS NOT NULL` | Exact match only |
| BARCODE | `=`, `IS NULL`, `IS NOT NULL` | Same as TEXT |
| SINGLE_SELECT | `=`, `IS NULL`, `IS NOT NULL` | Exact match only |
| PHONE | `=`, `IS NULL`, `IS NOT NULL` | Same as TEXT |
| EMAIL | `=`, `IS NULL`, `IS NOT NULL` | Same as TEXT |
| PROGRESS | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Same as NUMBER |
| CURRENCY | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Same as NUMBER |
| RATING | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Same as NUMBER |
| DATE_TIME | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Timestamp in milliseconds |
| CREATED_TIME | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Timestamp in milliseconds |
| MODIFIED_TIME | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` | Timestamp in milliseconds |

**Not Supported for Push Down:**
- MULTI_SELECT
- ATTACHMENT
- USER
- LINK
- FORMULA (read-only)
- LOOKUP (read-only)

## Test Results

### Regression Test Suite: 21/21 Tests Passed ✅

**Filter Tests:**
- ✅ Checkbox = true (1 row)
- ✅ Checkbox = false (12 rows)
- ✅ Number > 100 (5 rows)
- ✅ Number = 123.456 (1 row)
- ✅ Number >= 0 (7 rows)
- ✅ Number < 0 (1 row)
- ✅ Text = exact match (1 row)
- ✅ Text IS NOT NULL (12 rows)
- ✅ Text IS NULL (1 row)
- ✅ Single Select = Option A (1 row)
- ✅ Single Select = Option C (1 row)
- ✅ Multiple AND conditions (0 rows)
- ✅ Checkbox AND Number (1 row)

**Sorting Tests:**
- ✅ ORDER BY number ASC (13 rows)
- ✅ ORDER BY number DESC (13 rows)
- ✅ ORDER BY text ASC (12 rows)
- ✅ ORDER BY timestamp DESC (4 rows)
- ✅ ORDER BY timestamp ASC (4 rows)

**Combined Tests:**
- ✅ Filter + Sort (number > 0) (6 rows)
- ✅ Multiple filters + Sort (1 row)
- ✅ Text filter + Sort (12 rows)

## Example Translations

### Simple Equality
**SQL:**
```sql
WHERE field_number = 123.456
```

**Filter JSON:**
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "is",
      "value": ["123.456"]
    }
  ]
}
```

### Range Query
**SQL:**
```sql
WHERE field_number BETWEEN 50 AND 200
```

**Filter JSON:**
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isGreaterEqual",
      "value": ["50.000000000000000000"]
    },
    {
      "field_name": "field_number",
      "operator": "isLessEqual",
      "value": ["200.000000000000000000"]
    }
  ]
}
```

### Multiple Conditions
**SQL:**
```sql
WHERE field_checkbox = true AND field_number > 100
```

**Filter JSON:**
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_checkbox",
      "operator": "is",
      "value": ["true"]
    },
    {
      "field_name": "field_number",
      "operator": "isGreater",
      "value": ["100.000000000000000000"]
    }
  ]
}
```

### Date Query (Recommended for Users)
**SQL:**
```sql
WHERE CAST(field_date_time AS DATE) = DATE '1995-05-15'
```

**Filter JSON:**
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_date_time",
      "operator": "isGreaterEqual",
      "value": ["799632000000"]
    },
    {
      "field_name": "field_date_time",
      "operator": "isLess",
      "value": ["799718400000"]
    }
  ]
}
```

### Sorting
**SQL:**
```sql
ORDER BY field_number DESC
```

**Sort JSON:**
```json
[
  {
    "field_name": "field_number",
    "desc": true
  }
]
```

## User Guidelines

### Date Queries
Users should use `CAST(field_date_time AS DATE)` for date-only comparisons:

**✅ Recommended:**
```sql
WHERE CAST(field_date_time AS DATE) = DATE '2025-01-15'
WHERE CAST(field_date_time AS DATE) > DATE '2025-01-01'
WHERE CAST(field_date_time AS DATE) BETWEEN DATE '2025-01-01' AND DATE '2025-12-31'
```

**❌ Not Recommended:**
```sql
WHERE field_date_time = TIMESTAMP '2025-01-15 00:00:00'  -- Includes time component
```

### Performance Tips
1. Use push down predicates whenever possible to reduce data transfer
2. Combine filters with AND for best performance
3. Avoid OR conditions (not supported for push down)
4. Use specific value comparisons when possible

## Files Changed

### New Files
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/translator/SearchApiFilterTranslator.java`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/model/request/SearchRecordsRequest.java`
- `PUSHDOWN_FILTER_REFERENCE.md`
- `MIGRATION_SUMMARY.md`
- `test-pushdown-predicates.py`
- `test-json-filters.py`

### Modified Files
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/service/LarkBaseService.java`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseRecordHandler.java`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/util/LarkBaseTypeUtils.java`

## Remaining Work

### glue-lark-base-crawler Module
The Glue crawler module still needs similar updates to migrate from list API to search API:
- Update `LarkBaseService.java` in crawler module
- Add `SearchRecordsRequest.java` to crawler module
- Update response normalization

## References

- [Lark Search API Documentation](https://open.larksuite.com/document/uAjLw4CM/ukTMukTMukTM/reference/bitable-v1/app-table-record/search)
- [Lark Filter Guide](https://open.larksuite.com/document/uAjLw4CM/ukTMukTMukTM/reference/bitable-v1/app-table-record/record-filter-guide)
- [Push Down Filter Reference](./PUSHDOWN_FILTER_REFERENCE.md)
