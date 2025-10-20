#!/usr/bin/env python3
"""
Comprehensive Field Type Nullability Test - ALL Field Types

Tests nullability (IS NULL/IS NOT NULL) for ALL Lark field types:
- Simple types: TEXT, NUMBER, CHECKBOX, DATE_TIME, SINGLE_SELECT, etc.
- Complex types: USER, LOOKUP, CREATED_USER, MODIFIED_USER, ATTACHMENT
- Struct types: URL, LOCATION, SINGLE_LINK, DUPLEX_LINK
- Array types: MULTI_SELECT, GROUP_CHAT, ATTACHMENT

This comprehensive test identifies exactly which field types fail with WHERE clauses.
"""

import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import TEST_DATABASE, TEST_TABLE


class ComprehensiveNullabilityTest(BaseRegressionTest):
    """Test nullability for ALL field types."""

    def __init__(self):
        super().__init__(verbose=True)
        import boto3
        import os

        # AWS-specific configuration
        self.athena = boto3.client('athena', region_name=os.getenv('AWS_REGION', 'ap-southeast-1'))
        self.s3_bucket = os.getenv('S3_RESULTS_BUCKET', 'aws-athena-query-results-105676898724-ap-southeast-1')
        self.workgroup = os.getenv('ATHENA_WORKGROUP', 'poweruser')

    def _execute_athena_query(self, query: str) -> dict:
        """Execute Athena query and return results."""
        import time

        response = self.athena.start_query_execution(
            QueryString=query,
            QueryExecutionContext={'Database': self.test_database, 'Catalog': self.test_catalog},
            ResultConfiguration={'OutputLocation': f's3://{self.s3_bucket}/'},
            WorkGroup=self.workgroup
        )

        query_id = response['QueryExecutionId']

        # Wait for completion
        for _ in range(60):
            status_resp = self.athena.get_query_execution(QueryExecutionId=query_id)
            status = status_resp['QueryExecution']['Status']['State']

            if status == 'SUCCEEDED':
                return self.athena.get_query_results(QueryExecutionId=query_id)
            elif status in ['FAILED', 'CANCELLED']:
                reason = status_resp['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
                raise Exception(f"Query {status}: {reason}")

            time.sleep(1)

        raise Exception("Query timeout after 60 seconds")

    def run_field_test(self, test_name: str, field_name: str, predicate: str, expected_to_fail: bool = False) -> bool:
        """Run a single field test and validate results."""
        query = f'SELECT {field_name} FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" WHERE {field_name} {predicate} LIMIT 2'

        self.log_info(f"\nTest: {test_name}")
        self.log_info(f"Query: {query}")

        try:
            results = self._execute_athena_query(query)

            # Count rows (exclude header)
            rows = results.get('ResultSet', {}).get('Rows', [])
            row_count = len(rows) - 1 if rows else 0

            if self.verbose and row_count > 0:
                # Print first few rows
                headers = [col.get('VarCharValue', '') for col in rows[0]['Data']]
                self.log_info(f"Columns: {', '.join(headers)}")
                self.log_info(f"Rows: {row_count}")

                for i, row in enumerate(rows[1:2], 1):  # Show first data row
                    values = [col.get('VarCharValue', 'NULL') for col in row['Data']]
                    self.log_info(f"  Row {i}: {values}")

            if expected_to_fail:
                self.log_error(f"{test_name}: Expected to fail but succeeded with {row_count} rows")
                return False

            self.log_success(f"{test_name}: {row_count} rows")
            return True

        except Exception as e:
            if expected_to_fail:
                if "Lists have one child Field. Found: none" in str(e):
                    self.log_info(f"{test_name}: Failed as expected with Arrow validation error")
                    return True
                elif "NOT_SUPPORTED" in str(e):
                    self.log_info(f"{test_name}: Failed as expected with NOT_SUPPORTED error")
                    return True
                else:
                    self.log_error(f"{test_name}: Failed with unexpected error: {str(e)}")
                    return False
            else:
                self.log_error(f"{test_name}: {str(e)}")
                return False

    # Simple type tests - should work
    def test_text_field_is_not_null(self):
        """Test TEXT field with IS NOT NULL."""
        return self.run_field_test("TEXT IS NOT NULL", "field_text", "IS NOT NULL", expected_to_fail=False)

    def test_text_field_is_null(self):
        """Test TEXT field with IS NULL."""
        return self.run_field_test("TEXT IS NULL", "field_text", "IS NULL", expected_to_fail=False)

    def test_number_field_is_not_null(self):
        """Test NUMBER field with IS NOT NULL."""
        return self.run_field_test("NUMBER IS NOT NULL", "field_number", "IS NOT NULL", expected_to_fail=False)

    def test_number_field_is_null(self):
        """Test NUMBER field with IS NULL."""
        return self.run_field_test("NUMBER IS NULL", "field_number", "IS NULL", expected_to_fail=False)

    def test_checkbox_field_is_not_null(self):
        """Test CHECKBOX field with IS NOT NULL."""
        return self.run_field_test("CHECKBOX IS NOT NULL", "field_checkbox", "IS NOT NULL", expected_to_fail=False)

    def test_checkbox_field_is_null(self):
        """Test CHECKBOX field with IS NULL."""
        return self.run_field_test("CHECKBOX IS NULL", "field_checkbox", "IS NULL", expected_to_fail=False)

    def test_single_select_field_is_not_null(self):
        """Test SINGLE_SELECT field with IS NOT NULL."""
        return self.run_field_test("SINGLE_SELECT IS NOT NULL", "field_single_select", "IS NOT NULL", expected_to_fail=False)

    def test_single_select_field_is_null(self):
        """Test SINGLE_SELECT field with IS NULL."""
        return self.run_field_test("SINGLE_SELECT IS NULL", "field_single_select", "IS NULL", expected_to_fail=False)

    def test_phone_field_is_not_null(self):
        """Test PHONE field with IS NOT NULL."""
        return self.run_field_test("PHONE IS NOT NULL", "field_phone", "IS NOT NULL", expected_to_fail=False)

    def test_phone_field_is_null(self):
        """Test PHONE field with IS NULL."""
        return self.run_field_test("PHONE IS NULL", "field_phone", "IS NULL", expected_to_fail=False)

    def test_email_field_is_not_null(self):
        """Test EMAIL field with IS NOT NULL."""
        return self.run_field_test("EMAIL IS NOT NULL", "field_email", "IS NOT NULL", expected_to_fail=False)

    def test_email_field_is_null(self):
        """Test EMAIL field with IS NULL."""
        return self.run_field_test("EMAIL IS NULL", "field_email", "IS NULL", expected_to_fail=False)

    def test_barcode_field_is_not_null(self):
        """Test BARCODE field with IS NOT NULL."""
        return self.run_field_test("BARCODE IS NOT NULL", "field_barcode", "IS NOT NULL", expected_to_fail=False)

    def test_barcode_field_is_null(self):
        """Test BARCODE field with IS NULL."""
        return self.run_field_test("BARCODE IS NULL", "field_barcode", "IS NULL", expected_to_fail=False)

    def test_auto_number_field_is_not_null(self):
        """Test AUTO_NUMBER field with IS NOT NULL."""
        return self.run_field_test("AUTO_NUMBER IS NOT NULL", "field_auto_number", "IS NOT NULL", expected_to_fail=False)

    def test_auto_number_field_is_null(self):
        """Test AUTO_NUMBER field with IS NULL."""
        return self.run_field_test("AUTO_NUMBER IS NULL", "field_auto_number", "IS NULL", expected_to_fail=False)

    # Numeric type tests
    def test_currency_field_is_not_null(self):
        """Test CURRENCY field with IS NOT NULL."""
        return self.run_field_test("CURRENCY IS NOT NULL", "field_currency", "IS NOT NULL", expected_to_fail=False)

    def test_currency_field_is_null(self):
        """Test CURRENCY field with IS NULL."""
        return self.run_field_test("CURRENCY IS NULL", "field_currency", "IS NULL", expected_to_fail=False)

    def test_progress_field_is_not_null(self):
        """Test PROGRESS field with IS NOT NULL."""
        return self.run_field_test("PROGRESS IS NOT NULL", "field_progress", "IS NOT NULL", expected_to_fail=False)

    def test_progress_field_is_null(self):
        """Test PROGRESS field with IS NULL."""
        return self.run_field_test("PROGRESS IS NULL", "field_progress", "IS NULL", expected_to_fail=False)

    def test_rating_field_is_not_null(self):
        """Test RATING field with IS NOT NULL."""
        return self.run_field_test("RATING IS NOT NULL", "field_rating", "IS NOT NULL", expected_to_fail=False)

    def test_rating_field_is_null(self):
        """Test RATING field with IS NULL."""
        return self.run_field_test("RATING IS NULL", "field_rating", "IS NULL", expected_to_fail=False)

    # DateTime type tests
    def test_date_time_field_is_not_null(self):
        """Test DATE_TIME field with IS NOT NULL."""
        return self.run_field_test("DATE_TIME IS NOT NULL", "field_date_time", "IS NOT NULL", expected_to_fail=False)

    def test_date_time_field_is_null(self):
        """Test DATE_TIME field with IS NULL."""
        return self.run_field_test("DATE_TIME IS NULL", "field_date_time", "IS NULL", expected_to_fail=False)

    def test_created_time_field_is_not_null(self):
        """Test CREATED_TIME field with IS NOT NULL."""
        return self.run_field_test("CREATED_TIME IS NOT NULL", "field_created_time", "IS NOT NULL", expected_to_fail=False)

    def test_created_time_field_is_null(self):
        """Test CREATED_TIME field with IS NULL."""
        return self.run_field_test("CREATED_TIME IS NULL", "field_created_time", "IS NULL", expected_to_fail=False)

    def test_modified_time_field_is_not_null(self):
        """Test MODIFIED_TIME field with IS NOT NULL."""
        return self.run_field_test("MODIFIED_TIME IS NOT NULL", "field_modified_time", "IS NOT NULL", expected_to_fail=False)

    def test_modified_time_field_is_null(self):
        """Test MODIFIED_TIME field with IS NULL."""
        return self.run_field_test("MODIFIED_TIME IS NULL", "field_modified_time", "IS NULL", expected_to_fail=False)

    # Complex LOOKUP type tests - expected to fail with Arrow validation error
    def test_user_field_is_not_null(self):
        """Test USER field with IS NOT NULL."""
        return self.run_field_test("USER IS NOT NULL", "field_user", "IS NOT NULL", expected_to_fail=True)

    def test_user_field_is_null(self):
        """Test USER field with IS NULL."""
        return self.run_field_test("USER IS NULL", "field_user", "IS NULL", expected_to_fail=True)

    def test_lookup_field_is_not_null(self):
        """Test LOOKUP field with IS NOT NULL."""
        return self.run_field_test("LOOKUP IS NOT NULL", "field_lookup", "IS NOT NULL", expected_to_fail=True)

    def test_lookup_field_is_null(self):
        """Test LOOKUP field with IS NULL."""
        return self.run_field_test("LOOKUP IS NULL", "field_lookup", "IS NULL", expected_to_fail=True)

    def test_created_user_field_is_not_null(self):
        """Test CREATED_USER field with IS NOT NULL."""
        return self.run_field_test("CREATED_USER IS NOT NULL", "field_created_user", "IS NOT NULL", expected_to_fail=True)

    def test_created_user_field_is_null(self):
        """Test CREATED_USER field with IS NULL."""
        return self.run_field_test("CREATED_USER IS NULL", "field_created_user", "IS NULL", expected_to_fail=True)

    def test_modified_user_field_is_not_null(self):
        """Test MODIFIED_USER field with IS NOT NULL."""
        return self.run_field_test("MODIFIED_USER IS NOT NULL", "field_modified_user", "IS NOT NULL", expected_to_fail=True)

    def test_modified_user_field_is_null(self):
        """Test MODIFIED_USER field with IS NULL."""
        return self.run_field_test("MODIFIED_USER IS NULL", "field_modified_user", "IS NULL", expected_to_fail=True)

    # Array type tests
    def test_multi_select_field_is_not_null(self):
        """Test MULTI_SELECT field with IS NOT NULL."""
        return self.run_field_test("MULTI_SELECT IS NOT NULL", "field_multi_select", "IS NOT NULL", expected_to_fail=False)

    def test_multi_select_field_is_null(self):
        """Test MULTI_SELECT field with IS NULL."""
        return self.run_field_test("MULTI_SELECT IS NULL", "field_multi_select", "IS NULL", expected_to_fail=False)

    def test_group_chat_field_is_not_null(self):
        """Test GROUP_CHAT field with IS NOT NULL."""
        return self.run_field_test("GROUP_CHAT IS NOT NULL", "field_group_chat", "IS NOT NULL", expected_to_fail=False)

    def test_group_chat_field_is_null(self):
        """Test GROUP_CHAT field with IS NULL."""
        return self.run_field_test("GROUP_CHAT IS NULL", "field_group_chat", "IS NULL", expected_to_fail=False)

    def test_attachment_field_is_not_null(self):
        """Test ATTACHMENT field with IS NOT NULL."""
        return self.run_field_test("ATTACHMENT IS NOT NULL", "field_attachment", "IS NOT NULL", expected_to_fail=False)

    def test_attachment_field_is_null(self):
        """Test ATTACHMENT field with IS NULL."""
        return self.run_field_test("ATTACHMENT IS NULL", "field_attachment", "IS NULL", expected_to_fail=False)

    # Struct type tests - expected to fail with NOT_SUPPORTED
    def test_url_field_is_not_null(self):
        """Test URL field with IS NOT NULL."""
        return self.run_field_test("URL IS NOT NULL", "field_url", "IS NOT NULL", expected_to_fail=True)

    def test_url_field_is_null(self):
        """Test URL field with IS NULL."""
        return self.run_field_test("URL IS NULL", "field_url", "IS NULL", expected_to_fail=True)

    def test_location_field_is_not_null(self):
        """Test LOCATION field with IS NOT NULL."""
        return self.run_field_test("LOCATION IS NOT NULL", "field_location", "IS NOT NULL", expected_to_fail=False)

    def test_location_field_is_null(self):
        """Test LOCATION field with IS NULL."""
        return self.run_field_test("LOCATION IS NULL", "field_location", "IS NULL", expected_to_fail=False)

    def test_single_link_field_is_not_null(self):
        """Test SINGLE_LINK field with IS NOT NULL."""
        return self.run_field_test("SINGLE_LINK IS NOT NULL", "field_single_link", "IS NOT NULL", expected_to_fail=False)

    def test_single_link_field_is_null(self):
        """Test SINGLE_LINK field with IS NULL."""
        return self.run_field_test("SINGLE_LINK IS NULL", "field_single_link", "IS NULL", expected_to_fail=False)

    def test_duplex_link_field_is_not_null(self):
        """Test DUPLEX_LINK field with IS NOT NULL."""
        return self.run_field_test("DUPLEX_LINK IS NOT NULL", "field_duplex_link", "IS NOT NULL", expected_to_fail=False)

    def test_duplex_link_field_is_null(self):
        """Test DUPLEX_LINK field with IS NULL."""
        return self.run_field_test("DUPLEX_LINK IS NULL", "field_duplex_link", "IS NULL", expected_to_fail=False)

    # Formula field tests
    def test_formula_field_is_not_null(self):
        """Test FORMULA field with IS NOT NULL."""
        return self.run_field_test("FORMULA IS NOT NULL", "field_formula", "IS NOT NULL", expected_to_fail=False)

    def test_formula_field_is_null(self):
        """Test FORMULA field with IS NULL."""
        return self.run_field_test("FORMULA IS NULL", "field_formula", "IS NULL", expected_to_fail=False)

    def run_all_tests(self):
        """Run all comprehensive nullability tests."""
        print("\n" + "=" * 80)
        print("COMPREHENSIVE FIELD TYPE NULLABILITY TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Database: {self.test_database}")
        print(f"Table: {self.test_table}")
        print("=" * 80)

        # All test methods organized by category
        tests = [
            # Simple Types
            self.test_text_field_is_not_null,
            self.test_text_field_is_null,
            self.test_number_field_is_not_null,
            self.test_number_field_is_null,
            self.test_checkbox_field_is_not_null,
            self.test_checkbox_field_is_null,
            self.test_single_select_field_is_not_null,
            self.test_single_select_field_is_null,
            self.test_phone_field_is_not_null,
            self.test_phone_field_is_null,
            self.test_email_field_is_not_null,
            self.test_email_field_is_null,
            self.test_barcode_field_is_not_null,
            self.test_barcode_field_is_null,
            self.test_auto_number_field_is_not_null,
            self.test_auto_number_field_is_null,

            # Numeric Types
            self.test_currency_field_is_not_null,
            self.test_currency_field_is_null,
            self.test_progress_field_is_not_null,
            self.test_progress_field_is_null,
            self.test_rating_field_is_not_null,
            self.test_rating_field_is_null,

            # DateTime Types
            self.test_date_time_field_is_not_null,
            self.test_date_time_field_is_null,
            self.test_created_time_field_is_not_null,
            self.test_created_time_field_is_null,
            self.test_modified_time_field_is_not_null,
            self.test_modified_time_field_is_null,

            # LOOKUP Types (expected to fail with Arrow validation error)
            self.test_user_field_is_not_null,
            self.test_user_field_is_null,
            self.test_lookup_field_is_not_null,
            self.test_lookup_field_is_null,
            self.test_created_user_field_is_not_null,
            self.test_created_user_field_is_null,
            self.test_modified_user_field_is_not_null,
            self.test_modified_user_field_is_null,

            # Array Types
            self.test_multi_select_field_is_not_null,
            self.test_multi_select_field_is_null,
            self.test_group_chat_field_is_not_null,
            self.test_group_chat_field_is_null,
            self.test_attachment_field_is_not_null,
            self.test_attachment_field_is_null,

            # Struct Types
            self.test_url_field_is_not_null,    # Expected NOT_SUPPORTED
            self.test_url_field_is_null,        # Expected NOT_SUPPORTED
            self.test_location_field_is_not_null,
            self.test_location_field_is_null,
            self.test_single_link_field_is_not_null,
            self.test_single_link_field_is_null,
            self.test_duplex_link_field_is_not_null,
            self.test_duplex_link_field_is_null,

            # Formula Types
            self.test_formula_field_is_not_null,
            self.test_formula_field_is_null,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


if __name__ == "__main__":
    test = ComprehensiveNullabilityTest()
    test.setup()
    test.run_all_tests()
    test.teardown()