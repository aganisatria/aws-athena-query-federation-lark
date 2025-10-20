# Archived One-Off Scripts

These scripts were used for one-time debugging/fixing during development. They are archived here for reference but are no longer needed for regular testing.

## Scripts

### check_metadata_mapping.py
**Purpose:** List all records from the Glue crawler metadata mapping table
**Used for:** Debugging why the crawler wasn't creating the expected table
**Date:** 2025-10-19
**Status:** Completed - issue was identified and fixed

### check_table_schema.py
**Purpose:** List all fields/schema from Lark Base tables
**Used for:** Verifying table structure in Lark Base
**Date:** 2025-10-19
**Status:** Completed - schema verified

### fix_metadata_mapping.py
**Purpose:** Update the metadata mapping table to fix database name
**Used for:** Fixing typo in `name` field (athena_lark_base_regression_test1 → athena_lark_base_regression_test)
**Date:** 2025-10-19
**Status:** Completed - fix applied successfully

### test_all_field_types.py
**Purpose:** Ad-hoc testing of all field types in AWS
**Used for:** Manual verification of schema changes (USER, URL, LINK fields)
**Date:** 2025-10-19
**Status:** Replaced by `tests/regression/test_comprehensive_queries.py`

## Note

If you need to run these scripts again, they should still work. However, for regular testing, use the proper test suite in `tests/regression/` instead.
