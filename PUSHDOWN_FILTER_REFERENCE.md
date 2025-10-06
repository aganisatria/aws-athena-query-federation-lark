# Push Down Predicate Filter Reference

This document shows all supported push down predicates and how they translate to Lark Base Search API filter JSON.

## Filter Format

The Lark Base Search API uses this JSON structure:
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_name",
      "operator": "operator_name",
      "value": ["value"]
    }
  ]
}
```

## Supported Operators

| Operator | Description | Example SQL |
|----------|-------------|-------------|
| `is` | Equals | `WHERE field = value` |
| `isNot` | Not equals | `WHERE field != value` |
| `isGreater` | Greater than | `WHERE field > value` |
| `isGreaterEqual` | Greater than or equal | `WHERE field >= value` |
| `isLess` | Less than | `WHERE field < value` |
| `isLessEqual` | Less than or equal | `WHERE field <= value` |
| `isEmpty` | Is NULL | `WHERE field IS NULL` |
| `isNotEmpty` | Is NOT NULL | `WHERE field IS NOT NULL` |

---

## 1. CHECKBOX Fields

### SQL: `WHERE field_checkbox = true`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_checkbox",
      "operator": "is",
      "value": ["true"]
    }
  ]
}
```

### SQL: `WHERE field_checkbox = false`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_checkbox",
      "operator": "is",
      "value": ["false"]
    }
  ]
}
```

### SQL: `WHERE field_checkbox IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_checkbox",
      "operator": "is",
      "value": ["true"]
    }
  ]
}
```
**Note:** For checkbox, `IS NOT NULL` translates to `= true` because checkbox fields can only be true/false in Lark.

---

## 2. NUMBER Fields

### SQL: `WHERE field_number = 123.456`
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

### SQL: `WHERE field_number > 100`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isGreater",
      "value": ["100.000000000000000000"]
    }
  ]
}
```

### SQL: `WHERE field_number >= 100`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isGreaterEqual",
      "value": ["100.000000000000000000"]
    }
  ]
}
```

### SQL: `WHERE field_number < 0`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isLess",
      "value": ["0E-18"]
    }
  ]
}
```

### SQL: `WHERE field_number <= 100`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isLessEqual",
      "value": ["100.000000000000000000"]
    }
  ]
}
```

### SQL: `WHERE field_number BETWEEN 50 AND 200`
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

### SQL: `WHERE field_number IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_number",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

---

## 3. TEXT Fields

### SQL: `WHERE field_text = 'Sample text value'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_text",
      "operator": "is",
      "value": ["Sample text value"]
    }
  ]
}
```

### SQL: `WHERE field_text IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_text",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

### SQL: `WHERE field_text IS NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_text",
      "operator": "isEmpty",
      "value": []
    }
  ]
}
```

---

## 4. BARCODE Fields
Same as TEXT fields - supports `=`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_barcode = 'BC-123456'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_barcode",
      "operator": "is",
      "value": ["BC-123456"]
    }
  ]
}
```

---

## 5. SINGLE_SELECT Fields

### SQL: `WHERE field_single_select = 'Option A'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_single_select",
      "operator": "is",
      "value": ["Option A"]
    }
  ]
}
```

### SQL: `WHERE field_single_select IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_single_select",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

---

## 6. PHONE Fields
Same as TEXT fields - supports `=`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_phone = '+1234567890'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_phone",
      "operator": "is",
      "value": ["+1234567890"]
    }
  ]
}
```

---

## 7. EMAIL Fields
Same as TEXT fields - supports `=`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_email = 'test@example.com'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_email",
      "operator": "is",
      "value": ["test@example.com"]
    }
  ]
}
```

---

## 8. PROGRESS Fields
Same as NUMBER fields - supports `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_progress > 0.5`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_progress",
      "operator": "isGreater",
      "value": ["0.500000000000000000"]
    }
  ]
}
```

---

## 9. CURRENCY Fields
Same as NUMBER fields - supports `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_currency > 1000`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_currency",
      "operator": "isGreater",
      "value": ["1000.000000000000000000"]
    }
  ]
}
```

---

## 10. RATING Fields
Same as NUMBER fields - supports `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL`

### SQL: `WHERE field_rating = 5`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_rating",
      "operator": "is",
      "value": ["5"]
    }
  ]
}
```

---

## 11. DATE_TIME Fields (TIMESTAMP)

**⚠️ IMPORTANT FOR USERS:**
- Users should use `CAST(field_date_time AS DATE)` for date-only queries
- Direct timestamp comparison works but includes time component

### SQL: `WHERE field_date_time IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_date_time",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

### SQL: `WHERE field_date_time > TIMESTAMP '2020-01-01 00:00:00'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_date_time",
      "operator": "isGreater",
      "value": ["1577836800000"]
    }
  ]
}
```
**Note:** Timestamp values are converted to milliseconds since epoch

---

## 12. DATE Queries (Recommended for Users)

### SQL: `WHERE CAST(field_date_time AS DATE) = DATE '1995-05-15'`
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
**Note:** Date equality is converted to a range: `>= start_of_day AND < start_of_next_day`

### SQL: `WHERE CAST(field_date_time AS DATE) > DATE '2000-01-01'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_date_time",
      "operator": "isGreaterEqual",
      "value": ["946684800000"]
    }
  ]
}
```

### SQL: `WHERE CAST(field_date_time AS DATE) BETWEEN DATE '1990-01-01' AND DATE '2020-01-01'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_date_time",
      "operator": "isGreaterEqual",
      "value": ["631152000000"]
    },
    {
      "field_name": "field_date_time",
      "operator": "isLessEqual",
      "value": ["1577836800000"]
    }
  ]
}
```

---

## 13. CREATED_TIME Fields

### SQL: `WHERE field_created_time IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_created_time",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

### SQL: `WHERE field_created_time > TIMESTAMP '2025-01-01 00:00:00'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_created_time",
      "operator": "isGreater",
      "value": ["1735689600000"]
    }
  ]
}
```

---

## 14. MODIFIED_TIME Fields

### SQL: `WHERE field_modified_time IS NOT NULL`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_modified_time",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

### SQL: `WHERE field_modified_time > TIMESTAMP '2025-01-01 00:00:00'`
```json
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "field_modified_time",
      "operator": "isGreater",
      "value": ["1735689600000"]
    }
  ]
}
```

---

## 15. Combined Filters (Multiple AND Conditions)

### SQL: `WHERE field_checkbox = true AND field_number > 100`
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

### SQL: `WHERE field_number BETWEEN 50 AND 200 AND field_single_select = 'Option A'`
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
    },
    {
      "field_name": "field_single_select",
      "operator": "is",
      "value": ["Option A"]
    }
  ]
}
```

### SQL: `WHERE field_checkbox = true AND field_date_time IS NOT NULL AND field_text IS NOT NULL`
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
      "field_name": "field_date_time",
      "operator": "isNotEmpty",
      "value": []
    },
    {
      "field_name": "field_text",
      "operator": "isNotEmpty",
      "value": []
    }
  ]
}
```

---

## 16. Sorting (ORDER BY)

Sort is passed separately from filter in the Search API request:

### SQL: `ORDER BY field_number ASC`
```json
[
  {
    "field_name": "field_number",
    "desc": false
  }
]
```

### SQL: `ORDER BY field_date_time DESC`
```json
[
  {
    "field_name": "field_date_time",
    "desc": true
  }
]
```

### SQL: `ORDER BY field_number DESC, field_text ASC`
```json
[
  {
    "field_name": "field_number",
    "desc": true
  },
  {
    "field_name": "field_text",
    "desc": false
  }
]
```

---

## Supported Field Types Summary

| Lark Field Type | Push Down Support | Supported Operators |
|----------------|-------------------|---------------------|
| CHECKBOX | ✅ Yes | `=`, `IS NOT NULL` |
| NUMBER | ✅ Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| TEXT | ✅ Yes | `=`, `IS NULL`, `IS NOT NULL` |
| BARCODE | ✅ Yes | `=`, `IS NULL`, `IS NOT NULL` |
| SINGLE_SELECT | ✅ Yes | `=`, `IS NULL`, `IS NOT NULL` |
| PHONE | ✅ Yes | `=`, `IS NULL`, `IS NOT NULL` |
| EMAIL | ✅ Yes | `=`, `IS NULL`, `IS NOT NULL` |
| PROGRESS | ✅ Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| CURRENCY | ✅ Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| RATING | ✅ Yes | `=`, `!=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| DATE_TIME | ✅ Yes | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| CREATED_TIME | ✅ Yes | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| MODIFIED_TIME | ✅ Yes | `=`, `>`, `>=`, `<`, `<=`, `BETWEEN`, `IS NULL`, `IS NOT NULL` |
| MULTI_SELECT | ❌ No | Not supported for pushdown |
| ATTACHMENT | ❌ No | Not supported for pushdown |
| USER | ❌ No | Not supported for pushdown |
| LINK | ❌ No | Not supported for pushdown |
| FORMULA | ❌ No | Not supported for pushdown |
| LOOKUP | ❌ No | Not supported for pushdown |

---

## Important Notes for Users

1. **Date Queries**: Users should use `CAST(field_date_time AS DATE)` for date-only comparisons:
   ```sql
   -- Recommended for date queries
   WHERE CAST(field_date_time AS DATE) = DATE '2025-01-15'
   WHERE CAST(field_date_time AS DATE) > DATE '2025-01-01'
   WHERE CAST(field_date_time AS DATE) BETWEEN DATE '2025-01-01' AND DATE '2025-12-31'
   ```

2. **Timestamp Precision**: Timestamp values are stored as milliseconds since epoch in Lark Base.

3. **Checkbox NULL Handling**: `field_checkbox IS NOT NULL` translates to `field_checkbox = true` because checkbox fields in Lark are always true or false.

4. **Multiple Conditions**: All conditions are combined with AND (conjunction = "and").

5. **OR Conditions**: OR conditions are not supported for push down and will result in full table scans.

6. **Case Sensitivity**: Text comparisons are case-sensitive in Lark Base.

7. **Field Names**: Filter JSON uses the original Lark Base field names, not the sanitized Athena column names.
