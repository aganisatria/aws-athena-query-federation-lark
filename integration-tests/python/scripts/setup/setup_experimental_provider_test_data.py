#!/usr/bin/env python3
"""
Sets up test data for Experimental Metadata Provider testing.

The experimental provider stores metadata mappings in Athena itself rather than
Lark or Glue. This script will:

1. Create test Lark Base data (same as regular setup)
2. Create an Athena catalog table to store metadata mappings
3. Populate the metadata table with mappings for the test data
4. Set up environment variables for experimental provider testing

Prerequisites:
- Athena workgroup with query results location configured
- Lark app with bitable permissions
- Glue database for Athena catalog table
"""
import os
import sys
import time
import argparse
import json
import boto3
from botocore.exceptions import ClientError
from dotenv import load_dotenv

# Add parent directory to path for imports
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from scripts.setup_lark_test_data import LarkAPI, create_test_fields, create_test_records

# Load environment variables from .env file
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

# Configuration
LARK_APP_ID = os.getenv("LARK_APP_ID")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET")
LARK_FOLDER_TOKEN = os.getenv("LARK_FOLDER_TOKEN")

# AWS Configuration
AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
ATHENA_WORKGROUP = os.getenv("ATHENA_WORKGROUP", "primary")
GLUE_DATABASE = os.getenv("GLUE_DATABASE", "athena_lark_base_regression_test")  # Reuse existing database
OUTPUT_LOCATION = os.getenv("OUTPUT_LOCATION", "s3://aws-athena-query-results-561443929794-us-east-1/")

# Experimental provider specific
METADATA_TABLE_NAME = "lark_metadata_mappings"


class AthenaMetadataManager:
    """Manages Athena catalog for experimental provider"""

    def __init__(self, region=AWS_REGION, verbose=False):
        self.region = region
        self.verbose = verbose
        self.athena_client = boto3.client('athena', region_name=region)
        self.glue_client = boto3.client('glue', region_name=region)

    def verify_database_exists(self):
        """Verify that the Glue database exists for experimental provider"""
        if self.verbose: print(f"[INFO] Verifying Glue database exists: {GLUE_DATABASE}")

        try:
            self.glue_client.get_database(Name=GLUE_DATABASE)
            if self.verbose: print(f"[SUCCESS] Database {GLUE_DATABASE} exists")
            return True
        except self.glue_client.exceptions.EntityNotFoundException:
            raise Exception(f"Database {GLUE_DATABASE} does not exist. Please run the main setup script first.")
        except Exception as e:
            raise Exception(f"Failed to verify database: {str(e)}")

    def create_metadata_table(self):
        """Create the metadata mapping table in Athena"""
        if self.verbose: print(f"[INFO] Creating metadata table: {METADATA_TABLE_NAME}")

        create_table_sql = f"""
        CREATE EXTERNAL TABLE IF NOT EXISTS {GLUE_DATABASE}.{METADATA_TABLE_NAME} (
            database_name STRING,
            table_name STRING,
            lark_base_app_token STRING,
            lark_table_id STRING,
            lark_base_id STRING,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            description STRING
        )
        STORED AS PARQUET
        LOCATION '{OUTPUT_LOCATION}experimental-metadata/'
        """

        try:
            response = self.athena_client.start_query_execution(
                QueryString=create_table_sql,
                QueryExecutionContext={'Database': GLUE_DATABASE},
                ResultConfiguration={'OutputLocation': OUTPUT_LOCATION},
                WorkGroup=ATHENA_WORKGROUP
            )

            query_id = response['QueryExecutionId']
            self._wait_for_query(query_id)
            if self.verbose: print(f"[SUCCESS] Metadata table {METADATA_TABLE_NAME} created")

        except Exception as e:
            raise Exception(f"Failed to create metadata table: {str(e)}")

    def insert_metadata_mapping(self, database_name, table_name, base_token, table_id, base_id, description=""):
        """Insert a metadata mapping into the catalog table"""
        if self.verbose: print(f"[INFO] Inserting mapping: {database_name}.{table_name}")

        insert_sql = f"""
        INSERT INTO {GLUE_DATABASE}.{METADATA_TABLE_NAME}
        VALUES (
            '{database_name}',
            '{table_name}',
            '{base_token}',
            '{table_id}',
            '{base_id}',
            current_timestamp,
            current_timestamp,
            '{description}'
        )
        """

        try:
            response = self.athena_client.start_query_execution(
                QueryString=insert_sql,
                QueryExecutionContext={'Database': GLUE_DATABASE},
                ResultConfiguration={'OutputLocation': OUTPUT_LOCATION},
                WorkGroup=ATHENA_WORKGROUP
            )

            query_id = response['QueryExecutionId']
            self._wait_for_query(query_id)
            if self.verbose: print(f"[SUCCESS] Mapping inserted for {database_name}.{table_name}")

        except Exception as e:
            raise Exception(f"Failed to insert metadata mapping: {str(e)}")

    def _wait_for_query(self, query_id, timeout=60):
        """Wait for Athena query to complete"""
        start_time = time.time()

        while time.time() - start_time < timeout:
            try:
                response = self.athena_client.get_query_execution(QueryExecutionId=query_id)
                status = response['QueryExecution']['Status']['State']

                if status == 'SUCCEEDED':
                    return True
                elif status == 'FAILED':
                    reason = response['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
                    raise Exception(f"Query failed: {reason}")
                elif status == 'CANCELLED':
                    raise Exception("Query was cancelled")

                time.sleep(2)

            except Exception as e:
                if self.verbose:
                    print(f"[WARNING] Error checking query status: {str(e)}")
                time.sleep(2)

        raise Exception(f"Query {query_id} timed out after {timeout} seconds")

    def verify_mappings(self):
        """Verify that metadata mappings exist in the catalog"""
        if self.verbose: print("[INFO] Verifying metadata mappings")

        select_sql = f"SELECT * FROM {GLUE_DATABASE}.{METADATA_TABLE_NAME} ORDER BY database_name, table_name"

        try:
            response = self.athena_client.start_query_execution(
                QueryString=select_sql,
                QueryExecutionContext={'Database': GLUE_DATABASE},
                ResultConfiguration={'OutputLocation': OUTPUT_LOCATION},
                WorkGroup=ATHENA_WORKGROUP
            )

            query_id = response['QueryExecutionId']
            self._wait_for_query(query_id)

            # Get results
            results_response = self.athena_client.get_query_results(QueryExecutionId=query_id)
            rows = results_response['ResultSet'].get('Rows', [])

            if len(rows) > 1:  # Header row + data rows
                print(f"\n[SUCCESS] Found {len(rows) - 1} metadata mappings:")
                for row in rows[1:]:  # Skip header
                    values = [col.get('VarCharValue', '') for col in row.get('Data', [])]
                    if len(values) >= 4:
                        print(f"  {values[0]}.{values[1]} -> Base: {values[2][:20]}..., Table: {values[3]}")
                return True
            else:
                print("[WARNING] No metadata mappings found")
                return False

        except Exception as e:
            raise Exception(f"Failed to verify mappings: {str(e)}")


def main():
    parser = argparse.ArgumentParser(description="Set up experimental provider test data")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--skip-lark-setup", action="store_true",
                       help="Skip Lark Base setup if already done")
    args = parser.parse_args()

    if not all([LARK_APP_ID, LARK_APP_SECRET]):
        print("[ERROR] Missing required environment variables:")
        if not LARK_APP_ID: print("  - LARK_APP_ID")
        if not LARK_APP_SECRET: print("  - LARK_APP_SECRET")
        return 1

    print("=" * 80)
    print("Experimental Provider Test Data Setup")
    print("=" * 80)
    print(f"Glue Database: {GLUE_DATABASE}")
    print(f"Metadata Table: {METADATA_TABLE_NAME}")
    print(f"Athena Workgroup: {ATHENA_WORKGROUP}")
    print("=" * 80)

    try:
        # Initialize managers
        lark_api = LarkAPI(verbose=args.verbose)
        athena_manager = AthenaMetadataManager(verbose=args.verbose)

        # Step 1: Verify Athena infrastructure exists
        print("\n[STEP 1] Verifying Athena infrastructure...")
        athena_manager.verify_database_exists()
        athena_manager.create_metadata_table()

        # Step 2: Create Lark Base test data (if not skipped)
        base_token = None
        table_id = None
        base_id = None

        if not args.skip_lark_setup:
            print("\n[STEP 2] Creating Lark Base test data...")

            # Create base and table using existing setup script logic
            base_name = "experimental_provider_test_base"
            table_name = "experimental_test_table"

            # Create base
            base_token = lark_api.create_base(base_name)
            base_id = base_token  # In Lark API, base_token is often the same as base_id

            # Create table with test fields
            fields = create_test_fields()
            table_id = lark_api.create_table(base_token, table_name, fields)

            # Add test records
            records = create_test_records()
            lark_api.add_records(base_token, table_id, records)

            print(f"[SUCCESS] Created Lark Base: {base_token}, Table: {table_id}")

        else:
            print("\n[STEP 2] Skipping Lark Base setup (using existing data)")
            # Try to get existing values from environment
            base_token = os.getenv("EXPERIMENTAL_BASE_TOKEN")
            table_id = os.getenv("EXPERIMENTAL_TABLE_ID")
            base_id = os.getenv("EXPERIMENTAL_BASE_ID")

            if not all([base_token, table_id, base_id]):
                print("[ERROR] Missing environment variables for existing data:")
                if not base_token: print("  - EXPERIMENTAL_BASE_TOKEN")
                if not table_id: print("  - EXPERIMENTAL_TABLE_ID")
                if not base_id: print("  - EXPERIMENTAL_BASE_ID")
                return 1

        # Step 3: Create metadata mappings in Athena
        print("\n[STEP 3] Creating metadata mappings...")

        # Define test mappings
        mappings = [
            {
                "database_name": "experimental_db",
                "table_name": "products",
                "base_token": base_token,
                "table_id": table_id,
                "base_id": base_id,
                "description": "Experimental provider test table - products"
            },
            {
                "database_name": "experimental_db",
                "table_name": "test_data",
                "base_token": base_token,
                "table_id": table_id,
                "base_id": base_id,
                "description": "Experimental provider test table - alternative name"
            },
            {
                "database_name": "alternative_experimental_db",
                "table_name": "catalog",
                "base_token": base_token,
                "table_id": table_id,
                "base_id": base_id,
                "description": "Experimental provider test table - different database"
            }
        ]

        for mapping in mappings:
            athena_manager.insert_metadata_mapping(**mapping)

        # Step 4: Verify setup
        print("\n[STEP 4] Verifying setup...")
        athena_manager.verify_mappings()

        # Print summary
        print("\n" + "=" * 80)
        print("Setup Summary")
        print("=" * 80)

        print(f"\n✅ Athena Infrastructure:")
        print(f"  Database: {GLUE_DATABASE}")
        print(f"  Metadata Table: {METADATA_TABLE_NAME}")

        if not args.skip_lark_setup:
            print(f"\n✅ Lark Base Test Data:")
            print(f"  Base Token: {base_token}")
            print(f"  Table ID: {table_id}")
            print(f"  Base ID: {base_id}")

        print(f"\n✅ Metadata Mappings Created:")
        for mapping in mappings:
            print(f"  {mapping['database_name']}.{mapping['table_name']}")

        print(f"\n🔗 Environment Variables for Testing:")
        print(f"export EXPERIMENTAL_GLUE_DATABASE=\"{GLUE_DATABASE}\"")
        print(f"export EXPERIMENTAL_METADATA_TABLE=\"{METADATA_TABLE_NAME}\"")
        print(f"export EXPERIMENTAL_BASE_TOKEN=\"{base_token}\"")
        print(f"export EXPERIMENTAL_TABLE_ID=\"{table_id}\"")
        print(f"export EXPERIMENTAL_BASE_ID=\"{base_id}\"")
        print(f"export EXPERIMENTAL_DATABASE_1=\"experimental_db\"")
        print(f"export EXPERIMENTAL_TABLE_1=\"products\"")
        print(f"export EXPERIMENTAL_DATABASE_2=\"alternative_experimental_db\"")
        print(f"export EXPERIMENTAL_TABLE_2=\"catalog\"")

        print(f"\n✅ Experimental provider setup completed!")
        print("You can now run experimental provider tests with these environment variables.")

        return 0

    except Exception as e:
        print(f"\n[ERROR] Setup failed: {str(e)}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        return 1


if __name__ == "__main__":
    exit(main())