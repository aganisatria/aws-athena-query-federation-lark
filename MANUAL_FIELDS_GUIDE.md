# Manual Field Creation Guide

**Purpose**: Guide for manually adding Lark Base field types that cannot be created via API

---

## Overview

The Lark Base API has limitations on which field types can be created programmatically. This guide provides instructions for manually adding the 5 field types that **MUST be created through the Lark Base UI**.

---

## Field Types That Require Manual Creation

| Field Name | Field Type | Type Number | Athena Type | Notes |
|-----------|-----------|-------------|-------------|-------|
| `field_barcode` | BARCODE | 27 | string | Barcode scanner input |
| `field_email` | EMAIL | 26 | string | Email validation |
| `field_progress` | PROGRESS | 10 | decimal(38,18) | Progress bar (0-1) |
| `field_rating` | RATING | 9 | tinyint | Star rating (0-5) |
| `field_group_chat` | GROUP_CHAT | 12 | array\<struct\> | Lark group chat reference |

---

## Step-by-Step Instructions

### Prerequisites

1. Run the setup script first:
   ```bash
   python3 setup-lark-test-data.py --verbose
   ```

2. Note the Base URL from the output:
   ```
   🔗 Base URL: https://base.larksuite.com/{base_app_token}
   ```

### Adding Manual Fields

#### 1. BARCODE Field

1. **Open the Lark Base**:
   - Navigate to: `https://base.larksuite.com/{base_app_token}`
   - Open table: `data_type_test_table`

2. **Add Field**:
   - Click the **'+'** button to add a new field
   - Select field type: **Barcode**
   - Field name: `field_barcode`
   - Click **Confirm**

3. **Add Test Data**:
   Add the following barcode values to test records:
   - Record 1: `123456789012`
   - Record 2: `` (empty)
   - Record 3: `999999999999`
   - Record 4: `ABC-DEF-123`

---

#### 2. EMAIL Field

1. **Add Field**:
   - Click the **'+'** button to add a new field
   - Select field type: **Email**
   - Field name: `field_email`
   - Click **Confirm**

2. **Add Test Data**:
   Add the following email values to test records:
   - Record 1: `test@example.com`
   - Record 2: `` (empty)
   - Record 3: `user+tag@domain.co.uk`
   - Record 4: `negative.test@test.org`

---

#### 3. PROGRESS Field

1. **Add Field**:
   - Click the **'+'** button to add a new field
   - Select field type: **Progress**
   - Field name: `field_progress`
   - Click **Confirm**

2. **Add Test Data**:
   Add the following progress values (as percentages):
   - Record 1: 75% (0.75)
   - Record 2: 0% (0.0)
   - Record 3: 100% (1.0)
   - Record 4: 0% (0.0)
   - Record 5: 50% (0.5)

---

#### 4. RATING Field

1. **Add Field**:
   - Click the **'+'** button to add a new field
   - Select field type: **Rating**
   - Field name: `field_rating`
   - Configure: 5-star rating system
   - Click **Confirm**

2. **Add Test Data**:
   Add the following rating values (stars):
   - Record 1: 4 stars
   - Record 2: 0 stars
   - Record 3: 5 stars (max)
   - Record 4: 0 stars
   - Record 5: 3 stars

---

#### 5. GROUP_CHAT Field

1. **Add Field**:
   - Click the **'+'** button to add a new field
   - Select field type: **Group Chat**
   - Field name: `field_group_chat`
   - Click **Confirm**

2. **Add Test Data**:
   Add the following group chat references:
   - Record 1: Select any Lark group chat (if available)
   - Record 2: `` (empty)
   - Record 3: Select multiple group chats (if field supports multiple)

   **Note**: You need to have access to Lark group chats to populate this field.

---

## Verification Checklist

After adding all manual fields, verify:

- [ ] All 5 fields are added to the `data_type_test_table`
- [ ] Field names match exactly: `field_barcode`, `field_email`, `field_progress`, `field_rating`, `field_group_chat`
- [ ] Test data is populated according to the instructions above
- [ ] Table should now have **26 total fields** (21 auto + 5 manual)

---

## Expected Test Data Summary

### Complete Field Coverage (26 Fields)

After manual field addition, your table should have all these fields:

**API-Created Fields (21)**:
1. field_text
2. field_phone
3. field_single_select
4. field_number
5. field_currency
6. field_checkbox
7. field_date_time
8. field_created_time
9. field_modified_time
10. field_multi_select
11. field_user
12. field_attachment
13. field_url
14. field_location
15. field_created_user
16. field_modified_user
17. field_auto_number
18. field_single_link
19. field_duplex_link
20. field_formula
21. field_lookup

**Manually-Created Fields (5)**:
22. field_barcode ⚠️
23. field_email ⚠️
24. field_progress ⚠️
25. field_rating ⚠️
26. field_group_chat ⚠️

---

## Sample Data Values for Manual Fields

Use these sample values when populating test records:

| Record # | field_barcode | field_email | field_progress | field_rating | field_group_chat |
|---------|---------------|-------------|----------------|--------------|------------------|
| 1 | 123456789012 | test@example.com | 0.75 (75%) | 4 ⭐ | [Group1] |
| 2 | (empty) | (empty) | 0.0 (0%) | 0 ⭐ | (empty) |
| 3 | 999999999999 | user+tag@domain.co.uk | 1.0 (100%) | 5 ⭐ | [Group1, Group2] |
| 4 | ABC-DEF-123 | negative.test@test.org | 0.0 (0%) | 0 ⭐ | (empty) |
| 5 | (empty) | (empty) | 0.5 (50%) | 3 ⭐ | (empty) |

---

## Troubleshooting

### Issue: Can't find field type in UI

**Solution**: Lark Base field types may have different names in different language settings. Try:
- Switch UI language to English
- Look for similar field types (e.g., "Barcode Scanner" for BARCODE)
- Check Lark documentation for your region

### Issue: Field name already exists

**Solution**: Delete the existing field and recreate with exact name:
- Right-click on field header → Delete
- Recreate with correct name and type

### Issue: Cannot populate GROUP_CHAT field

**Solution**:
- Ensure you have access to at least one Lark group chat
- If no group chats available, leave this field empty
- The connector will handle null values correctly

---

## Next Steps

After completing manual field creation:

1. **Verify field count**:
   ```bash
   # Total fields should be 26
   ```

2. **Run Glue Crawler**:
   ```bash
   python3 test-glue-crawler.py --verbose
   ```

3. **Run Regression Tests**:
   ```bash
   export TEST_DATABASE="athena_lark_base_regression_test"
   export TEST_TABLE="data_type_test_table"
   ./regression-test-plan.sh
   ```

---

## Why These Fields Cannot Be Created via API

According to Lark API documentation, the field creation endpoint only supports these type codes:
```
[1,2,3,4,5,7,11,13,15,17,18,20,21,22,23,1001,1002,1003,1004,1005]
```

The following types are **NOT** in the supported list:
- **9** (RATING)
- **10** (PROGRESS)
- **12** (GROUP_CHAT)
- **26** (EMAIL)
- **27** (BARCODE)

This is a limitation of the Lark Base API, not our connector.

---

## References

- [Lark Base API - Create Field](https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-field/create)
- [Lark Base API - Field Types](https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-field/guide)
- [Setup Script: setup-lark-test-data.py](./setup-lark-test-data.py)

---

**End of Manual Fields Guide**
