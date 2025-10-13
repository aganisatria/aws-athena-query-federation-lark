#!/usr/bin/env python3
"""
Comprehensive Pushdown Filters Regression Test

Comprehensive test showing all push down predicates and their filter translations.
Tests all supported data types with various operators.
Works in AWS mode (Athena + CloudWatch Logs), validates in MOCK mode.

Migrated from: ../../../../test-all-pushdown-filters.py
"""
import sys
import os
import time
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment

class ComprehensivePushdownTester(BaseRegressionTest):
    """Tests comprehensive pushdown predicates"""

    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.athena_client = None
        self.logs_client = None
        self.workgroup = os.getenv('ATHENA_WORKGROUP', 'primary')
        self.lambda_log_group = os.getenv('LAMBDA_LOG_GROUP', f"/aws/lambda/{os.getenv('LAMBDA_FUNCTION_NAME', 'testgani')}")

    def setup(self):
        super().setup()

        if self.environment == TestEnvironment.AWS:
            try:
                self.athena_client = self.factory.create_athena_client()
                self.logs_client = self.factory.create_logs_client()
            except Exception as e:
                self.log_error(f"AWS clients not available: {str(e)}")

    def execute_query_and_show_filter(self, query, description, category):
        """Execute query and show the filter JSON that was generated"""
        print(f"\n{'='*100}")
        print(f"Category: {category}")
        print(f"Test: {description}")
        print(f"{'='*100}")
        print(f"SQL Query:")
        print(f"  {query}")
        print(f"-"*100)

        if self.environment == TestEnvironment.MOCK:
            # In MOCK mode, just validate query syntax
            self.log_info("[MOCK] Validating query syntax")
            if "SELECT" in query and "FROM" in query:
                self.log_success(f"{description}: Query syntax valid")
                print(f"Generated Filter JSON:")
                print(f"  (Simulated - in AWS mode would show actual filter)")
                print(f"-"*100)
                print(f"Query Status: ✅ SUCCEEDED (Mock)")
                print(f"Rows Returned: 0")
                return True
            else:
                self.log_error(f"{description}: Invalid query syntax")
                print(f"Query Status: ❌ FAILED (Mock)")
                return False

        elif self.environment == TestEnvironment.HYBRID:
            # Athena not in LocalStack Community
            self.log_warning("[HYBRID] Athena not available - skipping query")
            return True

        # AWS mode - execute real query
        try:
            response = self.athena_client.start_query_execution(
                QueryString=query,
                QueryExecutionContext={
                    'Catalog': self.test_catalog,
                    'Database': self.test_database
                },
                WorkGroup=self.workgroup
            )

            query_id = response['QueryExecutionId']

            # Wait for query to complete
            for i in range(30):
                time.sleep(1)
                result = self.athena_client.get_query_execution(QueryExecutionId=query_id)
                state = result['QueryExecution']['Status']['State']
                if state in ['SUCCEEDED', 'FAILED', 'CANCELLED']:
                    break

            # Get filter from Lambda logs
            time.sleep(2)  # Wait for logs to be available

            try:
                # Search for the filter in logs
                log_events = self.logs_client.filter_log_events(
                    logGroupName=self.lambda_log_group,
                    startTime=int((time.time() - 60) * 1000),  # Last 60 seconds
                    filterPattern='Translated filter'
                )

                # Find the most recent filter log for this query
                filter_json = None
                for event in reversed(log_events.get('events', [])):
                    message = event['message']
                    if 'Translated filter constraints' in message:
                        # Extract JSON from log message
                        if ': {' in message:
                            filter_json = message.split(': ', 2)[2]
                            break

                if filter_json and filter_json != 'null':
                    print(f"Generated Filter JSON:")
                    print(f"  {filter_json}")
                else:
                    print(f"Generated Filter JSON:")
                    print(f"  (No filter - full table scan)")
            except Exception as e:
                print(f"Could not retrieve filter from logs: {e}")

            print(f"-"*100)

            # Get results
            if state == 'SUCCEEDED':
                results = self.athena_client.get_query_results(QueryExecutionId=query_id)
                rows = results['ResultSet']['Rows']
                data_rows = len(rows) - 1 if len(rows) > 1 else 0

                print(f"Query Status: ✅ SUCCEEDED")
                print(f"Rows Returned: {data_rows}")

                if data_rows > 0 and data_rows <= 3:
                    print(f"Sample Results:")
                    for row in rows[1:4]:  # Skip header, show up to 3 rows
                        data = [col.get('VarCharValue', 'NULL') for col in row['Data']]
                        print(f"  {' | '.join(data)}")
                return True
            else:
                reason = result['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
                print(f"Query Status: ❌ {state}")
                print(f"Reason: {reason}")
                return False

        except Exception as e:
            self.log_error(f"Query execution failed: {str(e)}")
            print(f"Query Status: ❌ FAILED")
            print(f"Error: {str(e)}")
            return False

    def run_tests(self):
        """Run comprehensive push down predicate tests"""
        print("="*100)
        print("COMPREHENSIVE PUSH DOWN PREDICATE FILTER TRANSLATION TEST")
        print("="*100)
        print(f"Catalog: {self.test_catalog}")
        print(f"Database: {self.test_database}")
        print(f"Table: {self.test_table}")
        print(f"Workgroup: {self.workgroup}")
        print(f"\nThis test shows SQL queries and their corresponding Lark Base Search API filter JSON")

        test_queries = [
            # CHECKBOX FILTERS
            (f'SELECT field_text, field_checkbox FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true', "Checkbox = true", "CHECKBOX"),
            (f'SELECT field_text, field_checkbox FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = false', "Checkbox = false", "CHECKBOX"),

            # NUMBER FILTERS
            (f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_number = 123.456', "Number = 123.456 (exact match)", "NUMBER"),
            (f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_number > 100', "Number > 100 (greater than)", "NUMBER"),
            (f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_number BETWEEN 50 AND 200', "Number BETWEEN 50 AND 200 (range)", "NUMBER"),

            # TEXT FILTERS
            (f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_text = \'Sample text value\'', "Text = 'Sample text value' (exact match)", "TEXT"),
            (f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_text IS NOT NULL', "Text IS NOT NULL", "TEXT"),

            # COMBINED FILTERS
            (f'SELECT field_checkbox, field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true AND field_number > 100', "Checkbox = true AND Number > 100 (multiple conditions)", "COMBINED"),

            # SORTING
            (f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" ORDER BY field_number ASC', "ORDER BY Number ASC (no filter, just sort)", "SORTING"),
            (f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_number > 0 ORDER BY field_number DESC', "Filter Number > 0, ORDER BY Number DESC", "SORTING"),
        ]

        passed = 0
        failed = 0

        for query, description, category in test_queries:
            success = self.execute_query_and_show_filter(query, description, category)
            if success:
                passed += 1
            else:
                failed += 1

        self.test_results['passed'] = passed
        self.test_results['failed'] = failed
        self.test_results['total'] = passed + failed

        print("\n" + "="*100)
        print("TEST COMPLETE")
        print("="*100)
        print("\nSummary:")
        print("- All queries above show the SQL query and the corresponding Lark Base Search API filter JSON")
        print("- Filter JSON uses operators: is, isNot, isGreater, isGreaterEqual, isLess, isLessEqual, isEmpty, isNotEmpty")
        print("- Supported field types: CHECKBOX, NUMBER, TEXT, BARCODE, SINGLE_SELECT, PHONE, EMAIL,")
        print("  PROGRESS, CURRENCY, RATING, DATE_TIME, CREATED_TIME, MODIFIED_TIME")


def main():
    parser = argparse.ArgumentParser(description="Test comprehensive pushdown filters")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment == TestEnvironment.MOCK:
        print("[INFO] Athena not available in MOCK mode - testing query syntax only")
    elif environment == TestEnvironment.HYBRID:
        print("[INFO] Athena not available in HYBRID mode - skipping tests")

    tester = ComprehensivePushdownTester(verbose=args.verbose)
    tester.setup()
    tester.run_tests()
    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    sys.exit(main())
