#!/usr/bin/env python3
"""
Lark Drive Source Metadata Provider Test

Tests the Lark Drive Source metadata provider (LarkSourceMetadataProvider with Drive mode).
This provider discovers schemas from Lark Drive folder structure at runtime.

Environment Variables Required:
    default_does_activate_lark_drive_source=true
    lark_folder_token_data_source=<folder_token>

Usage:
    export TEST_ENVIRONMENT=aws
    export default_does_activate_lark_drive_source=true
    export lark_folder_token_data_source=T89Yf4MFTlFQLcdGX5njK9vZpDf
    python test_lark_drive_source.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest


class LarkDriveSourceTest(BaseRegressionTest):
    """Tests Lark Drive Source metadata provider."""

    def __init__(self):
        # Force AWS mode (Lark Drive Source only works with real AWS)
        os.environ["TEST_ENVIRONMENT"] = "aws"
        super().__init__(verbose=True)

        # Configure Lark Drive Source
        os.environ["default_does_activate_lark_drive_source"] = "true"
        self.folder_token = os.getenv("lark_folder_token_data_source", "T89Yf4MFTlFQLcdGX5njK9vZpDf")
        os.environ["lark_folder_token_data_source"] = self.folder_token

        # Lark Drive Source uses the Lark Base name from Drive folder
        # This is different from Glue Catalog and Lark Base Source!
        self.test_database = os.getenv("TEST_DATABASE_LARK_DRIVE", "athena_lark_base_regression_test2")
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
        """Test SHOW DATABASES with Lark Drive Source."""
        self.log_info("Test: SHOW DATABASES (Lark Drive Source)")

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
        """Test SHOW TABLES with Lark Drive Source."""
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
        """Test DESCRIBE TABLE with Lark Drive Source."""
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
        """Test SELECT query with Lark Drive Source."""
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

    def run_all_tests(self):
        """Run all Lark Drive Source tests."""
        print("\n" + "=" * 80)
        print("LARK DRIVE SOURCE METADATA PROVIDER TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Folder Token: {self.folder_token}")
        print(f"Catalog: {self.test_catalog}")
        print("=" * 80)

        tests = [
            self.test_show_databases,
            self.test_show_tables,
            self.test_describe_table,
            self.test_select_query,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


if __name__ == "__main__":
    test = LarkDriveSourceTest()
    test.setup()
    test.run_all_tests()
    test.teardown()
