#!/usr/bin/env python3
"""
Glue Lark Base Crawler Regression Test

Tests the Glue Lark Base Crawler Lambda function functionality.
Works in all three test modes: MOCK, HYBRID, AWS

Migrated from: ../../test-glue-crawler.py

Usage:
    # MOCK mode (default)
    export TEST_ENVIRONMENT=mock
    python test_glue_crawler.py [--validate-only] [--verbose]

    # HYBRID mode (LocalStack + Mocks)
    export TEST_ENVIRONMENT=hybrid
    python test_glue_crawler.py

    # AWS mode (real AWS)
    export TEST_ENVIRONMENT=aws
    python test_glue_crawler.py
"""
import sys
import os
import json
import time
import argparse
from typing import Dict, List, Any

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from clients import AWSClientFactory
from config import (
    get_environment,
    TestEnvironment,
    CRAWLER_FUNCTION_NAME,
    LARK_BASE_TOKEN,
    LARK_TABLE_ID
)


# Expected field mappings from connector code
EXPECTED_TYPE_MAPPINGS = {
    "field_text": "string",
    "field_barcode": "string",
    "field_single_select": "string",
    "field_phone": "string",
    "field_email": "string",
    "field_auto_number": "string",
    "field_number": "decimal",
    "field_progress": "decimal",
    "field_currency": "decimal",
    "field_rating": "tinyint",
    "field_checkbox": "boolean",
    "field_date_time": "timestamp",
    "field_created_time": "timestamp",
    "field_modified_time": "timestamp",
    "field_multi_select": "array<string>",
}


class GlueCrawlerTester(BaseRegressionTest):
    """Tests the Glue Lark Base Crawler"""

    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.glue_client = None
        self.lambda_client = None

    def setup(self):
        """Setup test clients."""
        super().setup()

        # Create clients based on environment
        self.glue_client = self.factory.create_glue_client()

        # Lambda client only for HYBRID/AWS modes
        if self.environment in [TestEnvironment.HYBRID, TestEnvironment.AWS]:
            try:
                self.lambda_client = self.factory.create_lambda_client()
            except ValueError:
                self.log_warning("Lambda client not available in MOCK mode")
                self.lambda_client = None

    def invoke_crawler_lambda(self) -> Dict[str, Any]:
        """
        Invoke the Glue crawler Lambda function.

        In MOCK mode: Simulates crawler by directly populating Glue catalog
        In HYBRID/AWS mode: Invokes real Lambda function
        """
        self.log_info(f"Invoking Glue crawler Lambda: {CRAWLER_FUNCTION_NAME}")

        if self.environment == TestEnvironment.MOCK:
            return self._simulate_crawler_mock()
        else:
            return self._invoke_crawler_lambda_real()

    def _simulate_crawler_mock(self) -> Dict[str, Any]:
        """Simulate crawler invocation in MOCK mode."""
        self.log_info("[MOCK] Simulating crawler by populating Glue catalog")

        # In MOCK mode, the table is already created in setup
        # Just return success
        self.log_success("Crawler simulation completed (table pre-populated)")
        return {"statusCode": 200, "body": "Mock crawler success"}

    def _invoke_crawler_lambda_real(self) -> Dict[str, Any]:
        """Invoke real Lambda function in HYBRID/AWS modes."""
        if not self.lambda_client:
            self.log_error("Lambda client not available")
            return {}

        # Payload matching MainLarkBasePayload record
        payload = {
            "handlerType": "LARK_BASE",
            "payload": {
                "larkBaseDataSourceId": LARK_BASE_TOKEN,
                "larkTableDataSourceId": LARK_TABLE_ID
            }
        }

        try:
            response = self.lambda_client.invoke(
                FunctionName=CRAWLER_FUNCTION_NAME,
                InvocationType='RequestResponse',
                Payload=json.dumps(payload)
            )

            response_payload = json.loads(response['Payload'].read())

            if response['StatusCode'] == 200:
                self.log_success("Crawler Lambda invoked successfully")
                if self.verbose:
                    self.log_info(f"Response: {json.dumps(response_payload, indent=2)}")
                return response_payload
            else:
                self.log_error(f"Lambda invocation failed with status: {response['StatusCode']}")
                return {}

        except Exception as e:
            if self.environment == TestEnvironment.HYBRID:
                self.log_warning(f"Lambda not deployed to LocalStack (expected): {str(e)}")
                self.log_info("To deploy Lambda: See integration-tests/README.md for instructions")
                self.log_info("Skipping Lambda invocation - will validate mock Glue catalog only")
                # Return success to continue with catalog validation
                return {"statusCode": 200, "body": "Lambda not deployed - using mock catalog"}
            else:
                self.log_error(f"Failed to invoke Lambda: {str(e)}")
            return {}

    def validate_glue_database(self, database_name: str) -> bool:
        """Validate Glue database exists."""
        self.log_info(f"Validating Glue database: {database_name}")

        try:
            response = self.glue_client.get_database(Name=database_name)
            self.log_success(f"Database '{database_name}' exists")

            if self.verbose:
                db_info = response['Database']
                self.log_info(f"Description: {db_info.get('Description', 'N/A')}")

            self.test_results["total"] += 1
            self.test_results["passed"] += 1
            return True

        except Exception as e:
            self.log_error(f"Database '{database_name}' not found: {str(e)}")
            self.test_results["total"] += 1
            self.test_results["failed"] += 1
            return False

    def validate_glue_table(self, database_name: str, table_name: str) -> Dict[str, Any]:
        """Validate Glue table exists and return metadata."""
        self.log_info(f"Validating Glue table: {database_name}.{table_name}")

        try:
            response = self.glue_client.get_table(
                DatabaseName=database_name,
                Name=table_name
            )

            table = response['Table']
            column_count = len(table['StorageDescriptor']['Columns'])
            self.log_success(f"Table '{table_name}' exists with {column_count} columns")

            self.test_results["total"] += 1
            self.test_results["passed"] += 1

            if self.verbose:
                self.log_info(f"Table parameters: {json.dumps(table.get('Parameters', {}), indent=2)}")

            return table

        except Exception as e:
            self.log_error(f"Table '{table_name}' not found: {str(e)}")
            self.test_results["total"] += 1
            self.test_results["failed"] += 1
            return {}

    def validate_field_types(self, table: Dict[str, Any]) -> List[str]:
        """Validate field types match expected mappings."""
        print("\n" + "=" * 80)
        print("Field Type Validation")
        print("=" * 80 + "\n")

        if not table:
            self.log_error("No table metadata to validate")
            return []

        columns = table['StorageDescriptor']['Columns']
        mismatches = []

        for column in columns:
            field_name = column['Name']
            actual_type = column['Type']

            # Skip reserved fields
            if field_name.startswith('$reserved_'):
                continue

            # Check if field is expected
            if field_name not in EXPECTED_TYPE_MAPPINGS:
                self.log_warning(f"Unexpected field: {field_name} ({actual_type})")
                self.test_results["warnings"] += 1
                continue

            expected_type = EXPECTED_TYPE_MAPPINGS[field_name]

            # Normalize types for comparison
            actual_normalized = actual_type.lower().replace(" ", "")
            expected_normalized = expected_type.lower().replace(" ", "")

            if actual_normalized == expected_normalized:
                self.log_success(f"{field_name}: {actual_type} ✓")
                self.test_results["total"] += 1
                self.test_results["passed"] += 1
            else:
                self.log_error(f"{field_name}: Expected '{expected_type}', got '{actual_type}' ✗")
                mismatches.append(f"{field_name}: {expected_type} → {actual_type}")
                self.test_results["total"] += 1
                self.test_results["failed"] += 1

        return mismatches

    def validate_table_parameters(self, table: Dict[str, Any]) -> bool:
        """Validate table parameters contain required metadata."""
        print("\n" + "=" * 80)
        print("Table Parameters Validation")
        print("=" * 80 + "\n")

        required_params = [
            "larkBaseId",
            "larkTableId",
            "larkBaseDataSourceId",
            "larkTableDataSourceId",
            "crawlingMethod"
        ]

        params = table.get('Parameters', {})
        all_present = True

        for param in required_params:
            if param in params:
                self.log_success(f"{param} exists: {params[param]}")
                self.test_results["total"] += 1
                self.test_results["passed"] += 1
            else:
                self.log_error(f"{param} missing")
                all_present = False
                self.test_results["total"] += 1
                self.test_results["failed"] += 1

        return all_present


def main():
    parser = argparse.ArgumentParser(description="Test Glue Lark Base Crawler")
    parser.add_argument("--validate-only", action="store_true",
                        help="Only validate existing Glue metadata without invoking Lambda")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    print("=" * 80)
    print("Glue Lark Base Crawler Regression Test")
    print("=" * 80)
    print(f"Environment: {environment.value.upper()}")
    print("=" * 80 + "\n")

    try:
        tester = GlueCrawlerTester(verbose=args.verbose)
        tester.setup()

        # Step 1: Invoke crawler Lambda (unless validate-only)
        if not args.validate_only:
            if environment == TestEnvironment.MOCK:
                print("[MOCK] Skipping Lambda invocation (using pre-populated mock data)")
            else:
                tester.invoke_crawler_lambda()
                # Wait for crawler to complete
                tester.log_info("Waiting 10 seconds for crawler to complete...")
                time.sleep(10)
        else:
            tester.log_info("Skipping Lambda invocation (validate-only mode)")

        # Step 2: Validate Glue database
        database_name = tester.test_database
        if not tester.validate_glue_database(database_name):
            tester.log_error("Database validation failed - aborting further tests")
            tester.print_summary()
            sys.exit(1)

        # Step 3: Validate Glue table
        table_name = tester.test_table
        table = tester.validate_glue_table(database_name, table_name)

        if not table:
            tester.log_error("Table validation failed - aborting further tests")
            tester.print_summary()
            sys.exit(1)

        # Step 4: Validate field types
        tester.validate_field_types(table)

        # Step 5: Validate table parameters
        tester.validate_table_parameters(table)

        # Step 6: Print summary
        tester.print_summary()

        # Cleanup
        tester.teardown()

        # Exit with appropriate code
        if tester.test_results["failed"] > 0:
            sys.exit(1)
        else:
            sys.exit(0)

    except Exception as e:
        print(f"[FAIL] Test failed with error: {str(e)}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
