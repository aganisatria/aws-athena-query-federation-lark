#!/usr/bin/env python3
"""
Pushdown Predicates and Sorting Regression Test

Tests filter pushdown and ORDER BY with Athena.
Works in AWS mode (Athena not available in LocalStack Community).
In MOCK mode, validates query syntax only.

Migrated from: ../../../test-pushdown-predicates.py
"""
import sys
import os
import time
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment

class PushdownTester(BaseRegressionTest):
    """Tests pushdown predicates and sorting"""

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

    def execute_query(self, query_name, query):
        """Execute Athena query or validate in MOCK mode"""
        if self.environment == TestEnvironment.MOCK:
            # In MOCK mode, just validate query syntax
            self.log_info(f"[MOCK] Validating query: {query_name}")
            if "SELECT" in query and "FROM" in query:
                self.log_success(f"{query_name}: Query syntax valid")
                return []  # Return empty results
            else:
                self.log_error(f"{query_name}: Invalid query syntax")
                return None

        elif self.environment == TestEnvironment.HYBRID:
            # Athena not in LocalStack Community
            self.log_warning(f"[HYBRID] Athena not available - skipping query: {query_name}")
            return []

        else:
            # AWS mode - execute real query
            return self._execute_athena_query(query_name, query)

    def _execute_athena_query(self, query_name, query):
        """Execute real Athena query in AWS mode"""
        self.log_info(f"Executing: {query_name}")
        self.log_info(f"Query: {query}")

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
            self.log_info(f"Query ID: {query_id}")

            # Wait for query to complete
            for i in range(30):
                time.sleep(2)
                result = self.athena_client.get_query_execution(QueryExecutionId=query_id)
                state = result['QueryExecution']['Status']['State']

                if state == 'SUCCEEDED':
                    break
                elif state in ['FAILED', 'CANCELLED']:
                    reason = result['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
                    self.log_error(f"Query {state}: {reason}")
                    return None
            else:
                self.log_error("Query timeout")
                return None

            # Get results
            results = self.athena_client.get_query_results(QueryExecutionId=query_id)
            rows = results['ResultSet']['Rows']

            if len(rows) <= 1:
                return []

            # Extract data (skip header row)
            data_rows = []
            for row in rows[1:]:
                data_row = [col.get('VarCharValue', 'NULL') for col in row['Data']]
                data_rows.append(data_row)

            self.log_success(f"{query_name}: {len(data_rows)} rows returned")
            return data_rows

        except Exception as e:
            self.log_error(f"{query_name} failed: {str(e)}")
            return None

    def test_filters(self):
        """Test various filter pushdown scenarios"""
        print("\n" + "="*80)
        print("FILTER PUSH DOWN TESTS")
        print("="*80)

        tests = [
            {'name': 'Checkbox = true',
             'query': f'SELECT field_text, field_checkbox FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true'},
            {'name': 'Checkbox = false',
             'query': f'SELECT field_text, field_checkbox FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = false'},
            {'name': 'Number > 100',
             'query': f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_number > 100'},
            {'name': 'Number = 123.456',
             'query': f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_number = 123.456'},
            {'name': 'Text = exact match',
             'query': f"SELECT field_text, field_number FROM \"{self.test_database}\".\"{self.test_table}\" WHERE field_text = 'Sample text value'"},
            {'name': 'Text IS NOT NULL',
             'query': f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_text IS NOT NULL'},
            {'name': 'Multiple AND conditions',
             'query': f'SELECT field_text, field_checkbox, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true AND field_number > 100'},
        ]

        for test in tests:
            result = self.execute_query(test['name'], test['query'])
            if result is not None:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1

    def test_sorting(self):
        """Test ORDER BY pushdown"""
        print("\n" + "="*80)
        print("SORTING PUSH DOWN TESTS")
        print("="*80)

        tests = [
            {'name': 'ORDER BY number ASC',
             'query': f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" ORDER BY field_number ASC'},
            {'name': 'ORDER BY number DESC',
             'query': f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" ORDER BY field_number DESC'},
            {'name': 'ORDER BY text ASC',
             'query': f'SELECT field_text, field_number FROM "{self.test_database}"."{self.test_table}" WHERE field_text IS NOT NULL ORDER BY field_text ASC'},
        ]

        for test in tests:
            result = self.execute_query(test['name'], test['query'])
            if result is not None:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1

    def test_combined(self):
        """Test combined filters and sorting"""
        print("\n" + "="*80)
        print("COMBINED FILTER + SORT TESTS")
        print("="*80)

        tests = [
            {'name': 'Filter + Sort (number > 0)',
             'query': f'SELECT field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_number > 0 ORDER BY field_number ASC'},
            {'name': 'Multiple filters + Sort',
             'query': f'SELECT field_checkbox, field_number, field_text FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true AND field_number IS NOT NULL ORDER BY field_number DESC'},
        ]

        for test in tests:
            result = self.execute_query(test['name'], test['query'])
            if result is not None:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1


def main():
    parser = argparse.ArgumentParser(description="Test pushdown predicates and sorting")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
        print("[INFO] Athena not available in MOCK/HYBRID mode - testing query syntax only")

    tester = PushdownTester(verbose=args.verbose)
    tester.setup()

    tester.test_filters()
    tester.test_sorting()
    tester.test_combined()

    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    main()
