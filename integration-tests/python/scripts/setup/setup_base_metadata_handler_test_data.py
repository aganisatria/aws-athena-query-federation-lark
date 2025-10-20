#!/usr/bin/env python3
"""
Sets up test data for Base Metadata Handler testing.

The Base Metadata Handler is the default handler that reads metadata from AWS Glue
catalog but can work with multiple sources. This script will:

1. Create comprehensive Lark Base test data
2. Set up Glue catalog with proper table definitions
3. Configure multiple test scenarios (different database/table combinations)
4. Test all supported field types and edge cases
5. Set up environment variables for Base Metadata Handler testing

Prerequisites:
- AWS credentials for Glue operations
- Lark app with bitable permissions
- S3 bucket for Glue catalog storage
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
GLUE_CRAWLER_LAMBDA_NAME = os.getenv("GLUE_CRAWLER_LAMBDA_NAME", "lark-base-glue-crawler")

# Base metadata handler specific databases
TEST_DATABASES = [
    "base_handler_regression_db",
    "base_handler_edge_cases_db",
    "base_handler_performance_db"
]


class GlueCatalogManager:
    """Manages Glue catalog for Base Metadata Handler testing"""

    def __init__(self, region=AWS_REGION, verbose=False):
        self.region = region
        self.verbose = verbose
        self.glue_client = boto3.client('glue', region_name=region)
        self.lambda_client = boto3.client('lambda', region_name=region)

    def create_database(self, database_name, description=""):
        """Create a Glue database"""
        if self.verbose: print(f"[INFO] Creating Glue database: {database_name}")

        try:
            self.glue_client.create_database(
                DatabaseInput={
                    'Name': database_name,
                    'Description': description or f"Database for Base Metadata Handler testing: {database_name}"
                }
            )
            if self.verbose: print(f"[SUCCESS] Database {database_name} created")
        except self.glue_client.exceptions.AlreadyExistsException:
            if self.verbose: print(f"[INFO] Database {database_name} already exists")
        except Exception as e:
            raise Exception(f"Failed to create database {database_name}: {str(e)}")

    def create_table_from_lark_base(self, database_name, table_name, base_token, table_id, field_mappings=None):
        """Create a Glue table definition from Lark Base metadata"""
        if self.verbose: print(f"[INFO] Creating Glue table: {database_name}.{table_name}")

        if field_mappings is None:
            field_mappings = create_test_fields()

        # Convert Lark field types to Glue types
        columns = []
        for field in field_mappings:
            column_name = field["field_name"]
            column_type = self._convert_lark_type_to_glue_type(field["type"])
            columns.append({"Name": column_name, "Type": column_type})

        # Add reserved system fields
        reserved_fields = [
            {"Name": "$reserved_record_id", "Type": "string"},
            {"Name": "$reserved_table_id", "Type": "string"},
            {"Name": "$reserved_base_id", "Type": "string"},
        ]
        columns.extend(reserved_fields)

        table_input = {
            'Name': table_name,
            'StorageDescriptor': {
                'Columns': columns,
                'Location': f'lark://{base_token}/{table_id}',
                'InputFormat': 'org.apache.hadoop.mapred.TextInputFormat',
                'OutputFormat': 'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat',
                'SerdeInfo': {
                    'SerializationLibrary': 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe',
                    'Parameters': {
                        'serialization.format': '',
                        'lark.base.token': base_token,
                        'lark.table.id': table_id
                    }
                }
            },
            'Parameters': {
                'larkBaseId': base_token,
                'larkTableId': table_id,
                'larkBaseDataSourceId': base_token,
                'larkTableDataSourceId': table_id,
                'crawlingMethod': 'DIRECT',
                'sourceType': 'LARK_BASE',
                'classification': 'lark',
                'typeOfData': 'lark-base',
                'areColumnsQuoted': 'false',
                'larkFieldMappings': json.dumps({f["field_name"]: f["type"] for f in field_mappings})
            },
            'TableType': 'EXTERNAL_TABLE'
        }

        try:
            self.glue_client.create_table(
                DatabaseName=database_name,
                TableInput=table_input
            )
            if self.verbose: print(f"[SUCCESS] Table {table_name} created in {database_name}")
        except self.glue_client.exceptions.AlreadyExistsException:
            if self.verbose: print(f"[INFO] Table {table_name} already exists in {database_name}")
        except Exception as e:
            raise Exception(f"Failed to create table {table_name}: {str(e)}")

    def _convert_lark_type_to_glue_type(self, lark_type):
        """Convert Lark field type to Glue data type"""
        type_mapping = {
            1: "string",      # TEXT
            2: "decimal(18,4)", # NUMBER
            3: "string",      # SINGLE_SELECT
            4: "array<string>", # MULTI_SELECT
            5: "timestamp",   # DATE_TIME
            7: "boolean",     # CHECKBOX
            9: "tinyint",     # RATING
            10: "decimal(5,2)", # PROGRESS
            11: "array<struct<user_id:string,name:string,emails:array<string>>>", # USER
            12: "array<struct<chat_id:string,name:string>>", # GROUP_CHAT
            13: "string",      # PHONE
            15: "struct<url:string,display_name:string>", # URL
            17: "array<struct<file_token:string,name:string,type:string,size:bigint>>", # ATTACHMENT
            18: "array<struct<record_id:string,primary_field_value:string>>", # SINGLE_LINK
            19: "string",      # LOOKUP (depends on target)
            20: "string",      # FORMULA (depends on result)
            21: "array<struct<record_id:string,primary_field_value:string>>", # DUPLEX_LINK
            22: "struct<address:string,latitude:decimal,longitude:decimal>", # LOCATION
            23: "decimal(18,4)", # CURRENCY
            26: "string",      # EMAIL
            27: "string",      # BARCODE
            1001: "timestamp", # CREATED_TIME
            1002: "timestamp", # MODIFIED_TIME
            1003: "struct<user_id:string,name:string>", # CREATED_USER
            1004: "struct<user_id:string,name:string>", # MODIFIED_USER
            1005: "string",    # AUTO_NUMBER
        }
        return type_mapping.get(lark_type, "string")

    def invoke_crawler_for_source(self, handler_type, source_info):
        """Invoke Glue crawler Lambda for a specific source"""
        if self.verbose: print(f"[INFO] Invoking crawler for {handler_type} source")

        payload = {
            "handlerType": handler_type,
            "payload": source_info
        }

        try:
            response = self.lambda_client.invoke(
                FunctionName=GLUE_CRAWLER_LAMBDA_NAME,
                InvocationType='Event',  # Async invocation
                Payload=json.dumps(payload)
            )

            if response['StatusCode'] == 200:
                if self.verbose: print(f"[SUCCESS] Crawler invoked for {handler_type}")
                return True
            else:
                print(f"[WARNING] Crawler invocation returned status: {response['StatusCode']}")
                return False

        except Exception as e:
            if self.verbose: print(f"[WARNING] Failed to invoke crawler: {str(e)}")
            return False

    def verify_table_exists(self, database_name, table_name):
        """Verify that a table exists in Glue catalog"""
        try:
            response = self.glue_client.get_table(
                DatabaseName=database_name,
                Name=table_name
            )
            table = response['Table']
            column_count = len(table['StorageDescriptor']['Columns'])
            if self.verbose: print(f"[INFO] Table {table_name} exists with {column_count} columns")
            return table
        except self.glue_client.exceptions.EntityNotFoundException:
            if self.verbose: print(f"[WARNING] Table {table_name} not found in {database_name}")
            return None


def create_edge_case_fields():
    """Create fields that test edge cases"""
    return create_test_fields() + [
        {"field_name": "empty_field", "type": 1, "property": {}},  # Will be empty
        {"field_name": "null_test_field", "type": 2, "property": {}},  # Will be null
        {"field_name": "long_text_field", "type": 1, "property": {}},  # Long text
        {"field_name": "special_chars_field", "type": 1, "property": {}},  # Special characters
        {"field_name": "unicode_field", "type": 1, "property": {}},  # Unicode characters
    ]


def create_edge_case_records():
    """Create records that test edge cases"""
    records = create_test_records()

    # Add edge case records
    edge_records = [
        {
            "fields": {
                "product_name": "",  # Empty string
                "category": None,   # Null value
                "price": "0.00",
                "in_stock": False,
                "created_date": 0,
                "description": "This is a very long text that exceeds normal field length limits to test how the system handles long text fields with various characters and content including numbers #special @characters and unicode like 测试 🚀",
                "tags": [],
                "rating": 0,
                "empty_field": "",
                "null_test_field": None,
                "long_text_field": "x" * 1000,  # Very long string
                "special_chars_field": "!@#$%^&*()_+-=[]{}|;':\",./<>?",
                "unicode_field": "Hello 世界 🌍 ñáéíóú ß 中文 العربية русский"
            }
        },
        {
            "fields": {
                "product_name": "Product with 'quotes' and \\slashes\\",
                "category": "Electronics",
                "price": "999999.99",  # High value
                "in_stock": True,
                "created_date": 253402300799000,  # Year 9999 edge case
                "description": "Newlines\nand\ttabs\r\nand carriage returns",
                "tags": ["Special", "Test", "Category"],
                "rating": 3,
                "empty_field": "not empty",
                "null_test_field": "not null",
                "long_text_field": "",
                "special_chars_field": "normal",
                "unicode_field": "Normal text"
            }
        }
    ]

    return records + edge_records


def main():
    parser = argparse.ArgumentParser(description="Set up Base Metadata Handler test data")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--skip-lark-setup", action="store_true",
                       help="Skip Lark Base setup if already done")
    parser.add_argument("--invoke-crawlers", action="store_true",
                       help="Invoke Glue crawlers to populate catalog")
    args = parser.parse_args()

    if not all([LARK_APP_ID, LARK_APP_SECRET]):
        print("[ERROR] Missing required environment variables:")
        if not LARK_APP_ID: print("  - LARK_APP_ID")
        if not LARK_APP_SECRET: print("  - LARK_APP_SECRET")
        return 1

    print("=" * 80)
    print("Base Metadata Handler Test Data Setup")
    print("=" * 80)
    print(f"Test Databases: {', '.join(TEST_DATABASES)}")
    print(f"AWS Region: {AWS_REGION}")
    print("=" * 80)

    try:
        # Initialize managers
        lark_api = LarkAPI(verbose=args.verbose)
        glue_manager = GlueCatalogManager(verbose=args.verbose)

        # Step 1: Create Glue databases
        print("\n[STEP 1] Creating Glue databases...")
        for db_name in TEST_DATABASES:
            glue_manager.create_database(db_name, f"Test database for Base Metadata Handler: {db_name}")

        # Step 2: Create Lark Base test data (if not skipped)
        base_token = None
        table_id = None
        base_id = None

        if not args.skip_lark_setup:
            print("\n[STEP 2] Creating Lark Base test data...")

            # Create main test base
            base_name = "base_handler_test_data"
            base_token = lark_api.create_base(base_name)
            base_id = base_token

            # Create multiple tables for different test scenarios
            test_tables = {
                "standard_test_table": create_test_fields(),
                "edge_case_test_table": create_edge_case_fields(),
                "performance_test_table": create_test_fields()  # Same fields, more data
            }

            created_tables = {}

            for table_name, fields in test_tables.items():
                table_id = lark_api.create_table(base_token, table_name, fields)
                created_tables[table_name] = table_id

                # Add appropriate test records
                if table_name == "standard_test_table":
                    records = create_test_records()
                elif table_name == "edge_case_test_table":
                    records = create_edge_case_records()
                elif table_name == "performance_test_table":
                    # Create more records for performance testing
                    base_records = create_test_records()
                    records = []
                    for i in range(10):  # 10x more data
                        for record in base_records:
                            new_record = {
                                "fields": record["fields"].copy()
                            }
                            # Add index to distinguish records
                            if "product_name" in new_record["fields"]:
                                new_record["fields"]["product_name"] = f"{new_record['fields']['product_name']} (Batch {i})"
                            records.append(new_record)

                lark_api.add_records(base_token, table_id, records)
                print(f"[SUCCESS] Created table {table_name} with {len(records)} records")

            # Use the first table for default testing
            table_id = created_tables["standard_test_table"]

        else:
            print("\n[STEP 2] Skipping Lark Base setup (using existing data)")
            base_token = os.getenv("BASE_HANDLER_BASE_TOKEN")
            table_id = os.getenv("BASE_HANDLER_TABLE_ID")
            base_id = os.getenv("BASE_HANDLER_BASE_ID")

            if not all([base_token, table_id, base_id]):
                print("[ERROR] Missing environment variables for existing data:")
                if not base_token: print("  - BASE_HANDLER_BASE_TOKEN")
                if not table_id: print("  - BASE_HANDLER_TABLE_ID")
                if not base_id: print("  - BASE_HANDLER_BASE_ID")
                return 1

        # Step 3: Create Glue table definitions
        print("\n[STEP 3] Creating Glue table definitions...")

        # Map Lark tables to Glue databases/tables
        table_mappings = [
            {
                "glue_database": "base_handler_regression_db",
                "glue_table": "regression_test_table",
                "description": "Main regression testing table"
            },
            {
                "glue_database": "base_handler_edge_cases_db",
                "glue_table": "edge_case_test_table",
                "description": "Edge cases and boundary condition testing"
            },
            {
                "glue_database": "base_handler_performance_db",
                "glue_table": "performance_test_table",
                "description": "Performance testing with larger dataset"
            },
            {
                "glue_database": "base_handler_regression_db",
                "glue_table": "alternative_names_test",
                "description": "Same data with alternative table name"
            }
        ]

        created_glue_tables = []
        for mapping in table_mappings:
            glue_manager.create_table_from_lark_base(
                database_name=mapping["glue_database"],
                table_name=mapping["glue_table"],
                base_token=base_token,
                table_id=table_id,
                field_mappings=create_test_fields() if "edge_case" not in mapping["glue_table"] else create_edge_case_fields()
            )
            created_glue_tables.append(mapping)
            print(f"[SUCCESS] Created Glue table: {mapping['glue_database']}.{mapping['glue_table']}")

        # Step 4: Invoke crawlers (optional)
        if args.invoke_crawlers:
            print("\n[STEP 4] Invoking Glue crawlers...")
            for mapping in created_glue_tables:
                source_info = {
                    "larkBaseDataSourceId": base_token,
                    "larkTableDataSourceId": table_id,
                    "databaseName": mapping["glue_database"],
                    "tableName": mapping["glue_table"]
                }
                glue_manager.invoke_crawler_for_source("LARK_BASE", source_info)

            print("[INFO] Waiting 30 seconds for crawlers to complete...")
            time.sleep(30)

        # Step 5: Verify setup
        print("\n[STEP 5] Verifying setup...")
        for mapping in created_glue_tables:
            table = glue_manager.verify_table_exists(mapping["glue_database"], mapping["glue_table"])
            if table:
                print(f"✓ {mapping['glue_database']}.{mapping['glue_table']}")
            else:
                print(f"✗ {mapping['glue_database']}.{mapping['glue_table']}")

        # Print summary
        print("\n" + "=" * 80)
        print("Setup Summary")
        print("=" * 80)

        print(f"\n✅ Glue Infrastructure:")
        for db_name in TEST_DATABASES:
            print(f"  Database: {db_name}")

        print(f"\n✅ Glue Tables Created:")
        for mapping in created_glue_tables:
            print(f"  {mapping['glue_database']}.{mapping['glue_table']} - {mapping['description']}")

        if not args.skip_lark_setup:
            print(f"\n✅ Lark Base Test Data:")
            print(f"  Base Token: {base_token}")
            print(f"  Table ID: {table_id}")
            print(f"  Base ID: {base_id}")

        print(f"\n🔗 Environment Variables for Testing:")
        print(f"export BASE_HANDLER_GLUE_DATABASE_1=\"{TEST_DATABASES[0]}\"")
        print(f"export BASE_HANDLER_GLUE_TABLE_1=\"regression_test_table\"")
        print(f"export BASE_HANDLER_GLUE_DATABASE_2=\"{TEST_DATABASES[1]}\"")
        print(f"export BASE_HANDLER_GLUE_TABLE_2=\"edge_case_test_table\"")
        print(f"export BASE_HANDLER_GLUE_DATABASE_3=\"{TEST_DATABASES[2]}\"")
        print(f"export BASE_HANDLER_GLUE_TABLE_3=\"performance_test_table\"")
        print(f"export BASE_HANDLER_BASE_TOKEN=\"{base_token}\"")
        print(f"export BASE_HANDLER_TABLE_ID=\"{table_id}\"")
        print(f"export BASE_HANDLER_BASE_ID=\"{base_id}\"")

        print(f"\n✅ Base Metadata Handler setup completed!")
        print("You can now run Base Metadata Handler tests with these environment variables.")

        return 0

    except Exception as e:
        print(f"\n[ERROR] Setup failed: {str(e)}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        return 1


if __name__ == "__main__":
    exit(main())