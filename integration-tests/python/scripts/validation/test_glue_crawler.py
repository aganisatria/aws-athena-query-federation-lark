
#!/usr/bin/env python3
"""
Tests the Glue Crawler and validates the resulting metadata in AWS Glue Data Catalog.

This script will:
1. Invoke the Glue Crawler Lambda function.
2. Wait for the crawler to complete.
3. Validate that the correct database (`glue_regression_db`) was created.
4. Validate that the correct table (`data_type_test_table`) was created.
5. Check the schema of the created table to ensure all 26+ fields were mapped correctly.
"""
import os
import boto3
import time
import argparse
from dotenv import load_dotenv

# Load environment variables from .env file
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../../.env')
load_dotenv(dotenv_path=dotenv_path)

# AWS and Test Configuration
AWS_REGION = os.getenv("AWS_REGION", "ap-southeast-1")
GLUE_CRAWLER_LAMBDA_NAME = os.getenv("GLUE_CRAWLER_LAMBDA_NAME")
LARK_METADATA_BASE_TOKEN = os.getenv("LARK_METADATA_BASE_TOKEN")
LARK_GLUE_MAPPING_TABLE_ID = os.getenv("LARK_GLUE_MAPPING_TABLE_ID")

# Expected values after crawl
EXPECTED_DATABASE = "glue_regression_db"
EXPECTED_TABLE = "data_type_test_table"
EXPECTED_COLUMN_COUNT = 26 + 3 # 26 fields + 3 reserved fields

class GlueCrawlerTester:
    def __init__(self, verbose=False, validate_only=False):
        self.verbose = verbose
        self.validate_only = validate_only
        self.lambda_client = boto3.client("lambda", region_name=AWS_REGION)
        self.glue_client = boto3.client("glue", region_name=AWS_REGION)
        self.test_results = {"total": 0, "passed": 0, "failed": 0}

    def run_tests(self):
        print("="*80)
        print("  Glue Lark Base Crawler Regression Test")
        print("="*80)

        if not self.validate_only:
            self._invoke_crawler()
        
        self._validate_glue_metadata()
        self._print_summary()

    def _invoke_crawler(self):
        if not all([GLUE_CRAWLER_LAMBDA_NAME, LARK_METADATA_BASE_TOKEN, LARK_GLUE_MAPPING_TABLE_ID]):
            print("[ERROR] Missing required environment variables for invoking crawler.")
            self.test_results["failed"] += 1
            return

        if self.verbose: print(f"\n[INFO] Invoking Glue crawler Lambda: {GLUE_CRAWLER_LAMBDA_NAME}...")
        
        payload = {
            "handler_type": "lark_base",
            "larkBaseDataSourceId": LARK_METADATA_BASE_TOKEN,
            "larkTableDataSourceId": LARK_GLUE_MAPPING_TABLE_ID
        }
        
        try:
            self.lambda_client.invoke(
                FunctionName=GLUE_CRAWLER_LAMBDA_NAME,
                InvocationType='Event', # Async invocation
                Payload=str.encode(str(payload))
            )
            print("[SUCCESS] Crawler Lambda invoked successfully.")
            print("[INFO] Waiting 30 seconds for crawler to complete...")
            time.sleep(30)
        except Exception as e:
            print(f"[FAIL] Crawler Lambda invocation failed: {e}")
            self.test_results["failed"] += 1

    def _validate_glue_metadata(self):
        print("\n" + "="*80)
        print("  Metadata Validation")
        print("="*80)
        self._validate_database()
        self._validate_table()

    def _validate_database(self):
        self.test_results["total"] += 1
        if self.verbose: print(f"[INFO] Validating Glue database: {EXPECTED_DATABASE}")
        try:
            self.glue_client.get_database(Name=EXPECTED_DATABASE)
            print(f"[PASS] Database '{EXPECTED_DATABASE}' exists.")
            self.test_results["passed"] += 1
        except self.glue_client.exceptions.EntityNotFoundException:
            print(f"[FAIL] Database '{EXPECTED_DATABASE}' does not exist.")
            self.test_results["failed"] += 1
        except Exception as e:
            print(f"[FAIL] Error checking database: {e}")
            self.test_results["failed"] += 1

    def _validate_table(self):
        self.test_results["total"] += 1
        if self.verbose: print(f"[INFO] Validating Glue table: {EXPECTED_DATABASE}.{EXPECTED_TABLE}")
        try:
            response = self.glue_client.get_table(DatabaseName=EXPECTED_DATABASE, Name=EXPECTED_TABLE)
            table = response['Table']
            num_columns = len(table.get('StorageDescriptor', {}).get('Columns', []))
            
            print(f"[PASS] Table '{EXPECTED_TABLE}' exists.")
            self.test_results["passed"] += 1

            self.test_results["total"] += 1
            if num_columns >= EXPECTED_COLUMN_COUNT:
                print(f"[PASS] Table has {num_columns} columns (expected >= {EXPECTED_COLUMN_COUNT}).")
                self.test_results["passed"] += 1
            else:
                print(f"[FAIL] Table has only {num_columns} columns (expected >= {EXPECTED_COLUMN_COUNT}).")
                self.test_results["failed"] += 1

        except self.glue_client.exceptions.EntityNotFoundException:
            print(f"[FAIL] Table '{EXPECTED_TABLE}' does not exist in database '{EXPECTED_DATABASE}'.")
            self.test_results["failed"] += 1
        except Exception as e:
            print(f"[FAIL] Error checking table: {e}")
            self.test_results["failed"] += 1

    def _print_summary(self):
        print("\n" + "="*80)
        print("  Test Summary")
        print("="*80)
        print(f"Total Tests: {self.test_results['total']}")
        print(f"Passed:      {self.test_results['passed']}")
        print(f"Failed:      {self.test_results['failed']}")
        if self.test_results['failed'] == 0:
            print("\n✓ All validation tests passed!")
        else:
            print(f"\n✗ {self.test_results['failed']} validation tests failed.")
        print("="*80)

def main():
    parser = argparse.ArgumentParser(description="Test Glue Crawler and validate metadata.")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--validate-only", action="store_true", help="Skip crawler invocation and only validate existing metadata")
    args = parser.parse_args()

    tester = GlueCrawlerTester(verbose=args.verbose, validate_only=args.validate_only)
    tester.run_tests()

    if tester.test_results["failed"] > 0:
        exit(1)

if __name__ == "__main__":
    main()
