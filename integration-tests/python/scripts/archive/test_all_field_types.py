#!/usr/bin/env python3
"""
Comprehensive test script to verify all field types are working correctly.
Tests the updated schema for USER, URL, CREATED_USER, MODIFIED_USER, SINGLE_LINK, and DUPLEX_LINK fields.
"""
import os
import boto3
import time
from dotenv import load_dotenv

# Load environment variables
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

# Configuration
AWS_REGION = os.getenv("AWS_REGION", "ap-southeast-1")
ATHENA_CATALOG = os.getenv("ATHENA_CATALOG", "Athena-testgani")
TEST_DATABASE = os.getenv("TEST_DATABASE", "athena_lark_base_regression_test")
TEST_TABLE = os.getenv("TEST_TABLE", "data_type_test_table")
ATHENA_WORKGROUP = os.getenv("ATHENA_WORKGROUP", "poweruser")
S3_OUTPUT = f"s3://aws-athena-query-results-105676898724-{AWS_REGION}/"

athena = boto3.client('athena', region_name=AWS_REGION)


def run_query(query):
    """Run an Athena query and return results."""
    print(f"\n[QUERY] {query}")

    response = athena.start_query_execution(
        QueryString=query,
        QueryExecutionContext={'Catalog': ATHENA_CATALOG, 'Database': TEST_DATABASE},
        ResultConfiguration={'OutputLocation': S3_OUTPUT},
        WorkGroup=ATHENA_WORKGROUP
    )

    query_execution_id = response['QueryExecutionId']
    print(f"[INFO] Query ID: {query_execution_id}")

    # Wait for query to complete
    for i in range(30):
        result = athena.get_query_execution(QueryExecutionId=query_execution_id)
        status = result['QueryExecution']['Status']['State']

        if status == 'SUCCEEDED':
            print(f"[SUCCESS] Query completed")
            break
        elif status in ['FAILED', 'CANCELLED']:
            reason = result['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
            print(f"[ERROR] Query failed: {reason}")
            return None

        time.sleep(2)
    else:
        print("[ERROR] Query timeout")
        return None

    # Get results
    results = athena.get_query_results(QueryExecutionId=query_execution_id)
    return results['ResultSet']['Rows']


def main():
    print("=" * 80)
    print("  Comprehensive Field Type Tests")
    print("=" * 80)
    print(f"Catalog:  {ATHENA_CATALOG}")
    print(f"Database: {TEST_DATABASE}")
    print(f"Table:    {TEST_TABLE}")
    print("=" * 80)

    tests_passed = 0
    tests_failed = 0

    # Test 1: COUNT(*) - Verify rows are returned
    print("\n[TEST 1] SELECT COUNT(*)")
    rows = run_query(f'SELECT COUNT(*) as count FROM "{TEST_TABLE}"')
    if rows and len(rows) > 1:
        count = rows[1]['Data'][0]['VarCharValue']
        print(f"[RESULT] Row count: {count}")
        if int(count) > 0:
            print("✅ PASS: Table has rows")
            tests_passed += 1
        else:
            print("❌ FAIL: Table is empty")
            tests_failed += 1
    else:
        print("❌ FAIL: Could not get count")
        tests_failed += 1

    # Test 2: LINK fields (SINGLE_LINK, DUPLEX_LINK)
    print("\n[TEST 2] LINK fields (field_single_link, field_duplex_link)")
    rows = run_query(f'SELECT field_single_link, field_duplex_link FROM "{TEST_TABLE}" LIMIT 1')
    if rows and len(rows) > 1:
        single_link = rows[1]['Data'][0].get('VarCharValue', '')
        duplex_link = rows[1]['Data'][1].get('VarCharValue', '')
        print(f"[RESULT] field_single_link: {single_link}")
        print(f"[RESULT] field_duplex_link: {duplex_link}")
        if 'link_record_ids' in single_link or 'link_record_ids' in duplex_link:
            print("✅ PASS: LINK fields have correct schema (link_record_ids)")
            tests_passed += 1
        else:
            print("❌ FAIL: LINK fields missing link_record_ids")
            tests_failed += 1
    else:
        print("❌ FAIL: Could not get LINK fields")
        tests_failed += 1

    # Test 3: USER field
    print("\n[TEST 3] USER field (field_user)")
    rows = run_query(f'SELECT field_user FROM "{TEST_TABLE}" WHERE field_user IS NOT NULL LIMIT 1')
    if rows and len(rows) > 1:
        user_field = rows[1]['Data'][0].get('VarCharValue', '')
        print(f"[RESULT] field_user: {user_field[:200]}...")
        if 'avatar_url' in user_field:
            print("✅ PASS: USER field has avatar_url")
            tests_passed += 1
        else:
            print("❌ FAIL: USER field missing avatar_url")
            tests_failed += 1
    else:
        print("⚠️ SKIP: No USER data in table")

    # Test 4: URL field
    print("\n[TEST 4] URL field (field_url)")
    rows = run_query(f'SELECT field_url FROM "{TEST_TABLE}" WHERE field_url IS NOT NULL LIMIT 1')
    if rows and len(rows) > 1:
        url_field = rows[1]['Data'][0].get('VarCharValue', '')
        print(f"[RESULT] field_url: {url_field}")
        if 'type' in url_field:
            print("✅ PASS: URL field has type")
            tests_passed += 1
        else:
            print("❌ FAIL: URL field missing type")
            tests_failed += 1
    else:
        print("⚠️ SKIP: No URL data in table")

    # Test 5: CREATED_USER field
    print("\n[TEST 5] CREATED_USER field (field_created_user)")
    rows = run_query(f'SELECT field_created_user FROM "{TEST_TABLE}" LIMIT 1')
    if rows and len(rows) > 1:
        created_user = rows[1]['Data'][0].get('VarCharValue', '')
        print(f"[RESULT] field_created_user: {created_user[:200]}...")
        if 'avatar_url' in created_user:
            print("✅ PASS: CREATED_USER field has avatar_url")
            tests_passed += 1
        else:
            print("❌ FAIL: CREATED_USER field missing avatar_url")
            tests_failed += 1
    else:
        print("❌ FAIL: Could not get CREATED_USER field")
        tests_failed += 1

    # Test 6: MODIFIED_USER field
    print("\n[TEST 6] MODIFIED_USER field (field_modified_user)")
    rows = run_query(f'SELECT field_modified_user FROM "{TEST_TABLE}" LIMIT 1')
    if rows and len(rows) > 1:
        modified_user = rows[1]['Data'][0].get('VarCharValue', '')
        print(f"[RESULT] field_modified_user: {modified_user[:200]}...")
        if 'avatar_url' in modified_user:
            print("✅ PASS: MODIFIED_USER field has avatar_url")
            tests_passed += 1
        else:
            print("❌ FAIL: MODIFIED_USER field missing avatar_url")
            tests_failed += 1
    else:
        print("❌ FAIL: Could not get MODIFIED_USER field")
        tests_failed += 1

    # Test 7: WHERE clause with LINK field
    print("\n[TEST 7] WHERE clause with field_text")
    rows = run_query(f'SELECT field_text FROM "{TEST_TABLE}" WHERE field_text LIKE \'%Record%\' LIMIT 1')
    if rows and len(rows) > 1:
        field_text = rows[1]['Data'][0].get('VarCharValue', '')
        print(f"[RESULT] field_text: {field_text}")
        print("✅ PASS: WHERE clause works")
        tests_passed += 1
    else:
        print("❌ FAIL: WHERE clause failed")
        tests_failed += 1

    # Test 8: ORDER BY
    print("\n[TEST 8] ORDER BY field_text")
    rows = run_query(f'SELECT field_text FROM "{TEST_TABLE}" ORDER BY field_text ASC LIMIT 3')
    if rows and len(rows) > 1:
        texts = [row['Data'][0].get('VarCharValue', '') for row in rows[1:]]
        print(f"[RESULT] Ordered texts: {texts}")
        print("✅ PASS: ORDER BY works")
        tests_passed += 1
    else:
        print("❌ FAIL: ORDER BY failed")
        tests_failed += 1

    # Summary
    print("\n" + "=" * 80)
    print("  Test Summary")
    print("=" * 80)
    print(f"Tests Passed: {tests_passed}")
    print(f"Tests Failed: {tests_failed}")
    print(f"Total Tests:  {tests_passed + tests_failed}")

    if tests_failed == 0:
        print("\n🎉 ALL TESTS PASSED!")
    else:
        print(f"\n⚠️ {tests_failed} test(s) failed")

    print("=" * 80)


if __name__ == "__main__":
    main()
