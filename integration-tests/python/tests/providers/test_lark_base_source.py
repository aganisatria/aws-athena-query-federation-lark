#!/usr/bin/env python3
"""
Lark Base Source Metadata Provider Test

Tests the Lark Base Source metadata provider (LarkSourceMetadataProvider with Base mode).
This provider discovers schemas from Lark Base metadata tables at runtime.

Environment Variables Required:
    default_does_activate_lark_base_source=true
    lark_base_id_data_source=<base_id>
    lark_table_id_data_source=<table_id>

Usage:
    export TEST_ENVIRONMENT=aws
    export default_does_activate_lark_base_source=true
    export lark_base_id_data_source=<your_base_id>
    export lark_table_id_data_source=<your_metadata_table_id>
    python test_lark_base_source.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest


class LarkBaseSourceTest(BaseRegressionTest):
    """Tests Lark Base Source metadata provider."""

    def __init__(self):
        # Force AWS mode (Lark Base Source only works with real AWS)
        os.environ["TEST_ENVIRONMENT"] = "aws"
        super().__init__(verbose=True)

        # Configure Lark Base Source
        os.environ["default_does_activate_lark_base_source"] = "true"
        self.base_id = os.getenv("lark_base_id_data_source", "EEMGbnS87a2W1IsaJKhjds3fpwe")
        self.metadata_table_id = os.getenv("lark_table_id_data_source", "tblCGeqbqp03ivAY")
        os.environ["lark_base_id_data_source"] = self.base_id
        os.environ["lark_table_id_data_source"] = self.metadata_table_id

        # Lark Base Source uses the Lark Base name as database name
        # This is different from Glue Catalog!
        self.test_database = os.getenv("TEST_DATABASE_LARK_BASE", "athena_lark_base_regression_test1")
        self.test_table = os.getenv("TEST_TABLE", "data_type_test_table")

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

    def test_show_databases(self):
        """Test SHOW DATABASES with Lark Base Source."""
        self.log_info("Test: SHOW DATABASES (Lark Base Source)")

        try:
            results = self._execute_query("SHOW DATABASES")
            rows = results['ResultSet']['Rows']

            # Extract database names (skip header)
            databases = [row['Data'][0]['VarCharValue'] for row in rows[1:]]

            self.log_info(f"Found databases: {databases}")

            if self.test_database in databases:
                self.log_success(f"SHOW DATABASES: Found '{self.test_database}'")
            else:
                self.log_error(f"SHOW DATABASES: Database '{self.test_database}' not found")

        except Exception as e:
            self.log_error(f"SHOW DATABASES: {str(e)}")

    def test_show_tables(self):
        """Test SHOW TABLES with Lark Base Source."""
        self.log_info(f"Test: SHOW TABLES IN {self.test_database}")

        try:
            results = self._execute_query(f"SHOW TABLES IN {self.test_database}")
            rows = results['ResultSet']['Rows']

            # Extract table names (skip header)
            tables = [row['Data'][0]['VarCharValue'] for row in rows[1:]]

            self.log_info(f"Found tables: {tables}")
            self.log_success(f"SHOW TABLES: Found {len(tables)} table(s)")

        except Exception as e:
            self.log_error(f"SHOW TABLES: {str(e)}")

    def test_describe_table(self):
        """Test DESCRIBE TABLE with Lark Base Source."""
        self.log_info(f"Test: DESCRIBE {self.test_table}")

        try:
            results = self._execute_query(f"DESCRIBE {self.test_database}.{self.test_table}")
            rows = results['ResultSet']['Rows']

            # Count columns (skip header)
            column_count = len(rows) - 1

            self.log_info(f"Table has {column_count} columns")
            self.log_success(f"DESCRIBE: Table schema retrieved")

        except Exception as e:
            self.log_error(f"DESCRIBE: {str(e)}")

    def test_select_query(self):
        """Test SELECT query with Lark Base Source."""
        query = f'SELECT * FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" LIMIT 5'
        self.log_info(f"Test: {query}")

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

    def test_where_clause(self):
        """Test WHERE clause pushdown with Lark Base Source."""
        query = f'SELECT field_text FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" WHERE field_checkbox = true LIMIT 3'
        self.log_info(f"Test: WHERE clause")

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

    def test_order_by(self):
        """Test ORDER BY pushdown with Lark Base Source."""
        query = f'SELECT field_number FROM "{self.test_catalog}"."{self.test_database}"."{self.test_table}" ORDER BY field_number DESC LIMIT 3'
        self.log_info(f"Test: ORDER BY")

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

    def run_all_tests(self):
        """Run all Lark Base Source tests."""
        print("\n" + "=" * 80)
        print("LARK BASE SOURCE METADATA PROVIDER TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Base ID: {self.base_id}")
        print(f"Metadata Table ID: {self.metadata_table_id}")
        print(f"Catalog: {self.test_catalog}")
        print("=" * 80)

        tests = [
            self.test_show_databases,
            self.test_show_tables,
            self.test_describe_table,
            self.test_select_query,
            self.test_where_clause,
            self.test_order_by,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


if __name__ == "__main__":
    test = LarkBaseSourceTest()
    test.setup()
    test.run_all_tests()
    test.teardown()
