#!/usr/bin/env python3
"""
Comprehensive AWS Athena Query Tests for Lark Base Connector

Tests all query capabilities:
- SELECT *, specific columns
- WHERE clauses (=, >, BETWEEN, IS NOT NULL, AND)
- ORDER BY (ASC/DESC)
- LIMIT, OFFSET (pagination)
- COUNT(*), DISTINCT
- Field type validations (USER, URL, CREATED_USER, MODIFIED_USER, LINK)
"""

import os
import sys

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import TEST_DATABASE, TEST_TABLE


class ComprehensiveQueryTest(BaseRegressionTest):
    """Comprehensive query testing for Athena Lark Base Connector."""

    def __init__(self):
        super().__init__(verbose=True)
        import boto3
        import os

        # AWS-specific configuration (this test runs in AWS mode only)
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

    def run_test(self, test_name: str, query: str, expected_min_rows: int = 0) -> bool:
        """Run a single test query and validate results."""
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

                for i, row in enumerate(rows[1:3], 1):  # Show first 2 data rows
                    values = [col.get('VarCharValue', 'NULL') for col in row['Data']]
                    self.log_info(f"  Row {i}: {values[:5]}")  # Show first 5 columns

            # Validate row count
            if row_count < expected_min_rows:
                self.log_error(f"{test_name}: Expected >= {expected_min_rows} rows, got {row_count}")
                return False

            self.log_success(f"{test_name}: {row_count} rows")
            return True

        except Exception as e:
            self.log_error(f"{test_name}: {str(e)}")
            return False

    def _query(self, sql: str) -> str:
        """Format query with catalog/database/table names."""
        return sql.format(
            catalog=self.test_catalog,
            db=self.test_database,
            table=self.test_table
        )

    # SELECT queries
    def test_select_all(self):
        """Test SELECT * query."""
        query = self._query('SELECT * FROM "{catalog}"."{db}"."{table}" LIMIT 5')
        return self.run_test("SELECT *", query, expected_min_rows=1)

    def test_select_specific_columns(self):
        """Test SELECT with specific columns."""
        query = self._query('SELECT field_text, field_number, field_checkbox FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("SELECT columns", query, expected_min_rows=1)

    # WHERE clause tests
    def test_where_equals(self):
        """Test WHERE with equals."""
        query = self._query('SELECT field_text FROM "{catalog}"."{db}"."{table}" WHERE field_checkbox = true LIMIT 5')
        return self.run_test("WHERE equals", query)

    def test_where_greater_than(self):
        """Test WHERE with greater than."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" WHERE field_number > 50 LIMIT 5')
        return self.run_test("WHERE >", query)

    def test_where_between(self):
        """Test WHERE BETWEEN."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" WHERE field_number BETWEEN 50 AND 200 LIMIT 5')
        return self.run_test("WHERE BETWEEN", query)

    def test_where_is_not_null(self):
        """Test WHERE IS NOT NULL."""
        query = self._query('SELECT field_text FROM "{catalog}"."{db}"."{table}" WHERE field_text IS NOT NULL LIMIT 5')
        return self.run_test("WHERE IS NOT NULL", query)

    def test_where_and(self):
        """Test WHERE with AND."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" WHERE field_checkbox = true AND field_number > 50 LIMIT 5')
        return self.run_test("WHERE AND", query)

    # ORDER BY tests
    def test_order_by_asc(self):
        """Test ORDER BY ASC."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" ORDER BY field_number ASC LIMIT 5')
        return self.run_test("ORDER BY ASC", query)

    def test_order_by_desc(self):
        """Test ORDER BY DESC."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" ORDER BY field_number DESC LIMIT 5')
        return self.run_test("ORDER BY DESC", query)

    # LIMIT and pagination
    def test_limit(self):
        """Test LIMIT."""
        query = self._query('SELECT field_text FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("LIMIT", query)

    def test_limit_offset(self):
        """Test LIMIT with OFFSET."""
        query = self._query('SELECT field_text FROM "{catalog}"."{db}"."{table}" ORDER BY field_number LIMIT 3 OFFSET 2')
        return self.run_test("LIMIT OFFSET", query)

    # Combined queries
    def test_combined_where_order_limit(self):
        """Test WHERE + ORDER BY + LIMIT."""
        query = self._query('SELECT field_number FROM "{catalog}"."{db}"."{table}" WHERE field_number > 0 ORDER BY field_number DESC LIMIT 3')
        return self.run_test("WHERE + ORDER + LIMIT", query)

    # Aggregations
    def test_count(self):
        """Test COUNT(*)."""
        query = self._query('SELECT COUNT(*) as total FROM "{catalog}"."{db}"."{table}"')
        return self.run_test("COUNT(*)", query, expected_min_rows=1)

    def test_distinct(self):
        """Test DISTINCT."""
        query = self._query('SELECT DISTINCT field_single_select FROM "{catalog}"."{db}"."{table}" LIMIT 10')
        return self.run_test("DISTINCT", query)

    # Field type tests
    def test_user_field(self):
        """Test USER field (with avatar_url)."""
        query = self._query('SELECT field_user FROM "{catalog}"."{db}"."{table}" WHERE field_user IS NOT NULL LIMIT 2')
        return self.run_test("USER field", query)

    def test_url_field(self):
        """Test URL field (with type)."""
        query = self._query('SELECT field_url FROM "{catalog}"."{db}"."{table}" WHERE field_url IS NOT NULL LIMIT 2')
        return self.run_test("URL field", query)

    def test_created_user_field(self):
        """Test CREATED_USER field (LIST type)."""
        query = self._query('SELECT field_created_user FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("CREATED_USER field", query)

    def test_modified_user_field(self):
        """Test MODIFIED_USER field (LIST type)."""
        query = self._query('SELECT field_modified_user FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("MODIFIED_USER field", query)

    def test_single_link_field(self):
        """Test SINGLE_LINK field (with link_record_ids)."""
        query = self._query('SELECT field_single_link FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("SINGLE_LINK field", query)

    def test_duplex_link_field(self):
        """Test DUPLEX_LINK field (with link_record_ids)."""
        query = self._query('SELECT field_duplex_link FROM "{catalog}"."{db}"."{table}" LIMIT 3')
        return self.run_test("DUPLEX_LINK field", query)

    def run_all_tests(self):
        """Run all comprehensive tests."""
        print("\n" + "=" * 80)
        print("COMPREHENSIVE QUERY TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Database: {self.test_database}")
        print(f"Table: {self.test_table}")
        print("=" * 80)

        # All test methods (organized by category)
        tests = [
            # SELECT
            self.test_select_all,
            self.test_select_specific_columns,
            # WHERE
            self.test_where_equals,
            self.test_where_greater_than,
            self.test_where_between,
            self.test_where_is_not_null,
            self.test_where_and,
            # ORDER BY
            self.test_order_by_asc,
            self.test_order_by_desc,
            # LIMIT
            self.test_limit,
            self.test_limit_offset,
            # Combined
            self.test_combined_where_order_limit,
            # Aggregations
            self.test_count,
            self.test_distinct,
            # Field types
            self.test_user_field,
            self.test_url_field,
            self.test_created_user_field,
            self.test_modified_user_field,
            self.test_single_link_field,
            self.test_duplex_link_field,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


if __name__ == "__main__":
    test = ComprehensiveQueryTest()
    test.setup()
    test.run_all_tests()
    test.teardown()
