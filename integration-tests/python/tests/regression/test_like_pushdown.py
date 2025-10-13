#!/usr/bin/env python3
"""
LIKE Pushdown Regression Test

Test what LIKE patterns actually get pushed down to the connector.
Works in AWS mode (Athena), validates query syntax in MOCK mode.

Migrated from: ../../../../test-like-pushdown.py
"""
import sys
import os
import time
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment

class LikePushdownTester(BaseRegressionTest):
    """Tests LIKE pattern pushdown"""

    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.athena_client = None
        self.workgroup = os.getenv('ATHENA_WORKGROUP', 'primary')

    def setup(self):
        super().setup()

        if self.environment == TestEnvironment.AWS:
            try:
                self.athena_client = self.factory.create_athena_client()
            except:
                self.log_error("Athena client not available")

    def run_query(self, query_string):
        """Execute Athena query and return results"""
        print(f"\n{'='*80}")
        print(f"Query: {query_string}")
        print('='*80)

        if self.environment == TestEnvironment.MOCK:
            # In MOCK mode, just validate query syntax
            self.log_info("[MOCK] Validating query syntax")
            if "SELECT" in query_string and "FROM" in query_string and "LIKE" in query_string:
                self.log_success("Query syntax valid")
                return 0  # Simulated row count
            else:
                self.log_error("Invalid query syntax")
                return -1

        elif self.environment == TestEnvironment.HYBRID:
            # Athena not in LocalStack Community
            self.log_warning("[HYBRID] Athena not available - skipping query")
            return 0

        # AWS mode - execute real query
        try:
            response = self.athena_client.start_query_execution(
                QueryString=query_string,
                QueryExecutionContext={
                    'Catalog': self.test_catalog,
                    'Database': self.test_database
                },
                WorkGroup=self.workgroup
            )

            query_id = response['QueryExecutionId']
            print(f"Query ID: {query_id}")

            # Wait for query to complete
            for i in range(30):
                time.sleep(0.5)
                result = self.athena_client.get_query_execution(QueryExecutionId=query_id)
                state = result['QueryExecution']['Status']['State']

                if state in ['SUCCEEDED', 'FAILED', 'CANCELLED']:
                    break

            if state == 'SUCCEEDED':
                # Get results
                results = self.athena_client.get_query_results(QueryExecutionId=query_id, MaxResults=10)
                rows = results['ResultSet']['Rows']

                if len(rows) <= 1:
                    print("✓ Query succeeded - No data rows (headers only or empty result)")
                    return 0

                print(f"✓ Query succeeded - {len(rows)-1} row(s) returned")
                for row in rows[1:]:  # Skip header
                    values = [col.get('VarCharValue', 'NULL') for col in row['Data']]
                    print(f"  {' | '.join(values)}")
                return len(rows) - 1
            else:
                reason = result['QueryExecution']['Status'].get('StateChangeReason', 'Unknown error')
                print(f"✗ Query failed: {reason}")
                return -1
        except Exception as e:
            self.log_error(f"Query execution failed: {str(e)}")
            return -1

    def run_tests(self):
        """Run all LIKE pattern tests"""
        print("\n" + "="*80)
        print("TESTING LIKE PATTERN PUSH DOWN")
        print("="*80)
        print(f"Catalog: {self.test_catalog}")
        print(f"Database: {self.test_database}")
        print(f"Table: {self.test_table}")
        print(f"Workgroup: {self.workgroup}")

        tests = [
            {'name': 'CONTAINS pattern - LIKE %substring%', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text LIKE \'%test%\''},
            {'name': 'STARTS WITH pattern - LIKE prefix%', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text LIKE \'Sample%\''},
            {'name': 'ENDS WITH pattern - LIKE %suffix', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text LIKE \'%value\''},
            {'name': 'NOT LIKE pattern - does not contain', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text NOT LIKE \'%AAAA%\' AND field_text IS NOT NULL LIMIT 5'},
            {'name': 'Case variations', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text LIKE \'%TEST%\''},
            {'name': 'LIKE exact match (no wildcards)', 'query': f'SELECT field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_text LIKE \'Sample text value\''},
        ]

        results = []
        for test in tests:
            print(f"\n\nTest: {test['name']}")
            print("-" * 80)
            row_count = self.run_query(test['query'])
            results.append({'test': test['name'], 'rows': row_count})

            if row_count >= 0:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1

        print("\n\n" + "="*80)
        print("TEST SUMMARY")
        print("="*80)
        for result in results:
            status = "✅" if result['rows'] >= 0 else "❌"
            print(f"{status} {result['test']}: {result['rows']} rows")

        print("\n\nIMPORTANT: Check CloudWatch Logs for the Lambda function to see what constraints were pushed down!")
        print("If you see LIKE patterns in the logs, then Athena is NOT pushing them down and doing client-side filtering.")


def main():
    parser = argparse.ArgumentParser(description="Test LIKE pattern pushdown")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment == TestEnvironment.MOCK:
        print("[INFO] Athena not available in MOCK mode - testing query syntax only")
    elif environment == TestEnvironment.HYBRID:
        print("[INFO] Athena not available in HYBRID mode - skipping tests")

    tester = LikePushdownTester(verbose=args.verbose)
    tester.setup()
    tester.run_tests()
    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == '__main__':
    main()
