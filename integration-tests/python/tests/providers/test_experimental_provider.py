#!/usr/bin/env python3
"""
Experimental Metadata Provider Test

Tests the Experimental metadata provider (ExperimentalMetadataProvider).
This provider discovers schema at query-time from base_id and table_id parameters
without requiring pre-configuration in Glue or metadata tables.

Environment Variables Required:
    default_does_activate_experimental_feature=true

Usage:
    export TEST_ENVIRONMENT=aws
    export default_does_activate_experimental_feature=true
    python test_experimental_provider.py

Note: This provider is experimental and extracts schema dynamically from query parameters.
"""
import os
import sys
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest


class ExperimentalProviderTest(BaseRegressionTest):
    """Tests Experimental metadata provider."""

    def __init__(self):
        # Force AWS mode (Experimental provider only works with real AWS)
        os.environ["TEST_ENVIRONMENT"] = "aws"
        super().__init__(verbose=True)

        # Configure Experimental provider
        os.environ["default_does_activate_experimental_feature"] = "true"

        # Experimental provider uses base_id.table_id format
        # Format: database = base_id, table = table_id
        self.test_base_id = os.getenv("TEST_BASE_ID", "EEMGbnS87a2W1IsaJKhjds3fpwe")
        self.test_table_id = os.getenv("TEST_TABLE_ID", "tblCGeqbqp03ivAY")

        # Experimental uses base_id as database and table_id as table name
        self.test_database = self.test_base_id
        self.test_table = self.test_table_id

        import boto3
        self.athena = boto3.client('athena', region_name=os.getenv('AWS_REGION', 'ap-southeast-1'))
        self.s3_bucket = os.getenv('S3_RESULTS_BUCKET', 'aws-athena-query-results-105676898724-ap-southeast-1')
        self.workgroup = os.getenv('ATHENA_WORKGROUP', 'poweruser')

    def _execute_query(self, query: str, database: str = 'default') -> dict:
        """Execute Athena query and return results."""
        response = self.athena.start_query_execution(
            QueryString=query,
            QueryExecutionContext={'Database': database, 'Catalog': self.test_catalog},
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

    def test_experimental_select(self):
        """Test SELECT with experimental provider (query-time schema discovery)."""
        # Note: Experimental provider discovers schema from query parameters
        # The actual implementation may vary based on how base_id/table_id are passed
        query = f'SELECT * FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" LIMIT 5'
        self.log_info(f"Test: SELECT with experimental provider")

        try:
            results = self._execute_query(query, self.test_database)
            rows = results['ResultSet']['Rows']
            row_count = len(rows) - 1  # Exclude header

            if row_count > 0:
                self.log_success(f"SELECT *: Retrieved {row_count} rows")
            else:
                self.log_warning("SELECT *: No rows returned")

        except Exception as e:
            self.log_error(f"SELECT *: {str(e)}")

    def test_experimental_where(self):
        """Test WHERE clause with experimental provider."""
        query = f'SELECT field_text FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" WHERE field_checkbox = true LIMIT 3'
        self.log_info(f"Test: WHERE clause (experimental)")

        try:
            results = self._execute_query(query, self.test_database)
            rows = results['ResultSet']['Rows']
            row_count = len(rows) - 1

            if row_count >= 0:
                self.log_success(f"WHERE clause: Retrieved {row_count} rows")
            else:
                self.log_warning("WHERE clause: No matching rows")

        except Exception as e:
            self.log_error(f"WHERE clause: {str(e)}")

    def test_experimental_count(self):
        """Test COUNT(*) with experimental provider."""
        query = f'SELECT COUNT(*) as total FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}"'
        self.log_info(f"Test: COUNT(*) (experimental)")

        try:
            results = self._execute_query(query, self.test_database)
            rows = results['ResultSet']['Rows']

            if len(rows) > 1:  # Header + 1 data row
                count = rows[1]['Data'][0]['VarCharValue']
                self.log_success(f"COUNT(*): {count} rows in table")
            else:
                self.log_warning("COUNT(*): No result returned")

        except Exception as e:
            self.log_error(f"COUNT(*): {str(e)}")

    def test_experimental_order_by(self):
        """Test ORDER BY with experimental provider."""
        query = f'SELECT field_number FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" ORDER BY field_number DESC LIMIT 5'
        self.log_info(f"Test: ORDER BY (experimental)")

        try:
            results = self._execute_query(query, self.test_database)
            rows = results['ResultSet']['Rows']
            row_count = len(rows) - 1

            if row_count > 0:
                self.log_success(f"ORDER BY: Retrieved {row_count} sorted rows")
            else:
                self.log_warning("ORDER BY: No rows returned")

        except Exception as e:
            self.log_error(f"ORDER BY: {str(e)}")

    def test_experimental_complex_query(self):
        """Test complex query (WHERE + ORDER BY + LIMIT) with experimental provider."""
        query = f'''SELECT field_text, field_number
                    FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}"
                    WHERE field_number > 0
                    ORDER BY field_number DESC
                    LIMIT 3'''
        self.log_info(f"Test: Complex query (experimental)")

        try:
            results = self._execute_query(query, self.test_database)
            rows = results['ResultSet']['Rows']
            row_count = len(rows) - 1

            if row_count > 0:
                self.log_success(f"Complex query: Retrieved {row_count} rows")
            else:
                self.log_warning("Complex query: No matching rows")

        except Exception as e:
            self.log_error(f"Complex query: {str(e)}")

    def run_all_tests(self):
        """Run all Experimental provider tests."""
        print("\n" + "=" * 80)
        print("EXPERIMENTAL METADATA PROVIDER TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Test Base ID: {self.test_base_id}")
        print(f"Test Table ID: {self.test_table_id}")
        print(f"Catalog: {self.test_catalog}")
        print("=" * 80)
        print("NOTE: Experimental provider discovers schema at query-time")
        print("=" * 80)

        tests = [
            self.test_experimental_select,
            self.test_experimental_where,
            self.test_experimental_count,
            self.test_experimental_order_by,
            self.test_experimental_complex_query,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


if __name__ == "__main__":
    test = ExperimentalProviderTest()
    test.setup()
    test.run_all_tests()
    test.teardown()
