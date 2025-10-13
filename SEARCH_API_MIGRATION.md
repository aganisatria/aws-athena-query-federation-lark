# Search API vs List API Response Format Differences

## Field Type Changes

Based on actual API testing with real Lark data:

### 1. TEXT Fields
**List API**: Plain string
```json
"field_text": "Sample text value"
```

**Search API**: Array of text objects
```json
"field_text": [{"text": "Sample text value", "type": "text"}]
```

### 2. FORMULA Fields (Text type)
**List API**: Array of text objects
```json
"field_formula": [{"text": "value", "type": "text"}]
```

**Search API**: Wrapped structure
```json
"field_formula": {"type": 1, "value": [{"text": "value", "type": "text"}]}
```

### 3. FORMULA Fields (Number type)
**List API**: Plain number
```json
"field_formula_3": 45936.0452430556
```

**Search API**: Wrapped structure
```json
"field_formula_3": {"type": 5, "value": [1759711938000]}
```

### 4. FORMULA Fields (Array type)
**List API**: Plain array
```json
"field_formula_2": ["optpQnarHo"]
```

**Search API**: Wrapped structure
```json
"field_formula_2": {"type": 3, "value": ["Sample text value+1-555-1234"]}
```

### 5. CURRENCY Fields
**List API**: String
```json
"field_currency": "1"
```

**Search API**: Number
```json
"field_currency": 1
```

### 6. NUMBER Fields
**List API**: String
```json
"field_number": "123.456"
```

**Search API**: Number
```json
"field_number": 123.456
```

### 7. USER/CREATED_USER/MODIFIED_USER Fields
**List API**: Single object
```json
"field_created_user": {"avatar_url": "...", "id": "...", "name": "..."}
```

**Search API**: Array
```json
"field_created_user": [{"avatar_url": "...", "id": "...", "name": "..."}]
```

### 8. LINK Fields (DUPLEX_LINK, SINGLE_LINK)
**List API**: Detailed structure
```json
"field_duplex_link": [{
  "record_ids": ["recuYGurzt0tfn"],
  "table_id": "tbl73oJM2AJrB2Ud",
  "text": "test",
  "text_arr": ["test"],
  "type": "text"
}]
```

**Search API**: Simplified
```json
"field_duplex_link": {"link_record_ids": ["recuYGurzt0tfn"]}
```

### 9. LOOKUP Fields
**List API**: Array
```json
"field_lookup": [{"text": "test", "type": "text"}]
```

**Search API**: Wrapped structure
```json
"field_lookup": {"type": 1, "value": [{"text": "test", "type": "text"}]}
```

### 10. Record ID Field (Auto)
**List API**: Both `id` and `record_id`
```json
{
  "id": "recuYGr0ZICv2B",
  "record_id": "recuYGr0ZICv2B",
  "fields": {...}
}
```

**Search API**: Only `record_id`
```json
{
  "record_id": "recuYGr0ZICv2B",
  "fields": {...}
}
```

## Unchanged Fields

These fields remain the same between both APIs:
- CHECKBOX: `boolean`
- DATE_TIME/CREATED_TIME/MODIFIED_TIME: `number` (timestamp)
- AUTO_NUMBER: `string`
- SINGLE_SELECT: `string`
- MULTI_SELECT: `array of strings`
- ATTACHMENT: `array of objects`
- LOCATION: `object`
- URL: `object`
- GROUP_CHAT: `array of objects`

## Strategy

We need to normalize the search API response to match what the existing extractors expect:

1. **TEXT fields**: Extract from `[{text, type}]` array → plain string
2. **FORMULA fields**: Unwrap `{type, value}` structure → extract value
3. **CURRENCY/NUMBER**: Already numbers (better than strings!)
4. **USER fields**: Already arrays (current code expects object, need to handle both)
5. **LINK fields**: Map `{link_record_ids}` to old structure for compatibility
6. **LOOKUP fields**: Unwrap like FORMULA fields

## Implementation Plan

Create a response normalizer that transforms search API responses to match the structure expected by existing extractors.
