#!/usr/bin/env python3
"""
Sets up Lark Drive test data for Lark Drive Source metadata provider testing.

This is different from the Lark Drive glue crawler. This script sets up the
folder structure and Lark Base files that the Lark Drive Source metadata provider
will discover at runtime.

The Lark Drive Source metadata provider:
- Scans a Lark Drive folder structure
- Treats folders as databases
- Treats Lark Base files within folders as tables
- Discovers schemas dynamically at Lambda cold start

This script will:
1. Create a folder structure in Lark Drive
2. Create Lark Base files within folders (representing different tables)
3. Set up folder-to-database mapping
4. Create tables with comprehensive test data
5. Generate environment variables for Lark Drive Source testing

Prerequisites:
- Lark app with drive:drive and drive:file permissions
- Parent Lark Drive folder token where test data will be created
"""
import os
import requests
import time
import argparse
import json
from dotenv import load_dotenv

# Load environment variables from .env file
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

LARK_APP_ID = os.getenv("LARK_APP_ID")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET")
LARK_DRIVE_FOLDER_TOKEN = os.getenv("LARK_DRIVE_FOLDER_TOKEN")

class LarkDriveSourceAPI:
    """Wrapper for Lark Drive API operations for Lark Drive Source setup"""

    def __init__(self, verbose=False):
        self._tenant_access_token = None
        self._token_expiry = 0
        self.verbose = verbose
        self.base_url = "https://open.larksuite.com/open-apis"

    def _get_tenant_access_token(self):
        """Get or refresh tenant access token"""
        if time.time() < self._token_expiry and self._tenant_access_token:
            return self._tenant_access_token

        if self.verbose: print("[INFO] Refreshing tenant access token...")
        url = f"{self.base_url}/auth/v3/tenant_access_token/internal"
        headers = {"Content-Type": "application/json"}
        payload = {"app_id": LARK_APP_ID, "app_secret": LARK_APP_SECRET}

        try:
            response = requests.post(url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()

            if data.get("code") != 0:
                raise Exception(f"Auth failed: {data.get('msg')}")

            self._tenant_access_token = data["tenant_access_token"]
            self._token_expiry = time.time() + data["expire"] - 60  # Refresh 1 min early

            if self.verbose: print("[SUCCESS] Token obtained successfully")
            return self._tenant_access_token

        except Exception as e:
            raise Exception(f"Failed to get tenant access token: {str(e)}")

    def _make_request(self, method, endpoint, **kwargs):
        """Make authenticated API request"""
        headers = kwargs.pop('headers', {})
        headers['Authorization'] = f'Bearer {self._get_tenant_access_token()}'
        headers['Content-Type'] = 'application/json'

        url = f"{self.base_url}{endpoint}"

        try:
            response = requests.request(method, url, headers=headers, **kwargs)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            raise Exception(f"API request failed: {str(e)}")

    def create_folder(self, parent_token, folder_name):
        """Create a subfolder (database)"""
        if self.verbose: print(f"[INFO] Creating database folder: {folder_name}")

        payload = {
            "name": folder_name,
            "type": "folder",
            "parent_type": "explorer",
            "parent_node": parent_token
        }

        try:
            response = self._make_request('POST', '/drive/v1/files', json=payload)

            if response.get("code") != 0:
                raise Exception(f"Failed to create folder: {response.get('msg')}")

            folder_token = response["data"]["file"]["token"]
            if self.verbose: print(f"[SUCCESS] Database folder created: {folder_token}")
            return folder_token

        except Exception as e:
            raise Exception(f"Failed to create database folder '{folder_name}': {str(e)}")

    def create_base(self, parent_token, base_name):
        """Create a Lark Base (table) in Drive folder"""
        if self.verbose: print(f"[INFO] Creating Lark Base (table): {base_name}")

        payload = {
            "name": base_name,
            "type": "bitable",
            "parent_type": "explorer",
            "parent_node": parent_token
        }

        try:
            response = self._make_request('POST', '/drive/v1/files', json=payload)

            if response.get("code") != 0:
                raise Exception(f"Failed to create base: {response.get('msg')}")

            base_token = response["data"]["file"]["token"]
            if self.verbose: print(f"[SUCCESS] Lark Base (table) created: {base_token}")
            return base_token

        except Exception as e:
            raise Exception(f"Failed to create Lark Base (table) '{base_name}': {str(e)}")

    def create_table(self, base_token, table_name, fields):
        """Create a table in the base with specified fields"""
        if self.verbose: print(f"[INFO] Creating table structure: {table_name}")

        payload = {
            "table": {
                "name": table_name,
                "default_view_id": "vewxxxxxxxxxxxxxxx",  # Default view
                "field": fields
            }
        }

        try:
            response = self._make_request('POST', f'/open-apis/bitable/v1/apps/{base_token}/tables', json=payload)

            if response.get("code") != 0:
                raise Exception(f"Failed to create table: {response.get('msg')}")

            table_id = response["data"]["table"]["table_id"]
            if self.verbose: print(f"[SUCCESS] Table structure created: {table_id}")
            return table_id

        except Exception as e:
            raise Exception(f"Failed to create table structure '{table_name}': {str(e)}")

    def add_records(self, base_token, table_id, records):
        """Add records to a table"""
        if self.verbose: print(f"[INFO] Adding {len(records)} records to table")

        payload = {"records": records}

        try:
            response = self._make_request('POST', f'/open-apis/bitable/v1/apps/{base_token}/tables/{table_id}/records/batch_create', json=payload)

            if response.get("code") != 0:
                raise Exception(f"Failed to add records: {response.get('msg')}")

            if self.verbose: print(f"[SUCCESS] Added {len(records)} records")
            return response["data"]

        except Exception as e:
            raise Exception(f"Failed to add records: {str(e)}")


def create_comprehensive_test_fields():
    """Create comprehensive field definitions covering all supported Lark types"""
    return [
        {"field_name": "id", "type": 1, "property": {}},  # TEXT
        {"field_name": "name", "type": 1, "property": {}},  # TEXT
        {"field_name": "category", "type": 3, "property": {"options": [{"name": "Electronics"}, {"name": "Clothing"}, {"name": "Books"}, {"name": "Food"}]}},  # SINGLE_SELECT
        {"field_name": "price", "type": 2, "property": {"formatter": "currency", "precision": 2}},  # NUMBER
        {"field_name": "in_stock", "type": 7, "property": {}},  # CHECKBOX
        {"field_name": "created_date", "type": 5, "property": {"date_formatter": "yyyy-MM-dd HH:mm:ss"}},  # DATE_TIME
        {"field_name": "description", "type": 1, "property": {}},  # TEXT
        {"field_name": "tags", "type": 4, "property": {"options": [{"name": "New"}, {"name": "Popular"}, {"name": "Sale"}, {"name": "Limited"}]}},  # MULTI_SELECT
        {"field_name": "rating", "type": 9, "property": {"max": 5, "icon": "star"}},  # RATING
        {"field_name": "progress", "type": 10, "property": {"max": 100, "unit": "%"}},  # PROGRESS
        {"field_name": "email", "type": 26, "property": {}},  # EMAIL
        {"field_name": "phone", "type": 13, "property": {}},  # PHONE
        {"field_name": "website", "type": 15, "property": {}},  # URL
        {"field_name": "location", "type": 22, "property": {}},  # LOCATION
        {"field_name": "barcode", "type": 27, "property": {"type": "CODE128"}},  # BARCODE
        {"field_name": "auto_number", "type": 1005, "property": {"prefix": "ITEM", "start": 1}},  # AUTO_NUMBER
    ]


def create_comprehensive_test_records():
    """Create comprehensive sample test records"""
    return [
        {
            "fields": {
                "id": "1",
                "name": "Laptop Pro 15-inch",
                "category": "Electronics",
                "price": "1299.99",
                "in_stock": True,
                "created_date": 1672531200000,  # 2023-01-01 00:00:00
                "description": "High-performance laptop with M2 Pro chip, 16GB RAM, 512GB SSD",
                "tags": ["New", "Popular"],
                "rating": 5,
                "progress": 100,
                "email": "electronics@store.com",
                "phone": "+1-555-0123",
                "website": "https://example.com/laptop-pro",
                "location": '{"address": "123 Tech St, Silicon Valley, CA", "latitude": 37.7749, "longitude": -122.4194}',
                "barcode": "1234567890123",
                "auto_number": "ITEM001"
            }
        },
        {
            "fields": {
                "id": "2",
                "name": "Wireless Noise-Canceling Headphones",
                "category": "Electronics",
                "price": "349.99",
                "in_stock": True,
                "created_date": 1672617600000,  # 2023-01-02 00:00:00
                "description": "Premium wireless headphones with active noise cancellation",
                "tags": ["Popular", "Limited"],
                "rating": 5,
                "progress": 100,
                "email": "audio@store.com",
                "phone": "+1-555-0124",
                "website": "https://example.com/headphones",
                "location": '{"address": "456 Audio Ave, Music City, TN", "latitude": 36.1627, "longitude": -86.7816}',
                "barcode": "2345678901234",
                "auto_number": "ITEM002"
            }
        },
        {
            "fields": {
                "id": "3",
                "name": "Winter Waterproof Jacket",
                "category": "Clothing",
                "price": "189.99",
                "in_stock": False,
                "created_date": 1672704000000,  # 2023-01-03 00:00:00
                "description": "Warm and waterproof winter jacket with hood",
                "tags": ["Sale"],
                "rating": 4,
                "progress": 100,
                "email": "clothing@store.com",
                "phone": "+1-555-0125",
                "website": "https://example.com/jacket",
                "location": '{"address": "789 Fashion Blvd, Style City, NY", "latitude": 40.7128, "longitude": -74.0060}',
                "barcode": "3456789012345",
                "auto_number": "ITEM003"
            }
        },
        {
            "fields": {
                "id": "4",
                "name": "Python Programming Complete Guide",
                "category": "Books",
                "price": "45.00",
                "in_stock": True,
                "created_date": 1672790400000,  # 2023-01-04 00:00:00
                "description": "Comprehensive guide to Python programming from basics to advanced",
                "tags": ["New", "Popular"],
                "rating": 5,
                "progress": 100,
                "email": "books@store.com",
                "phone": "+1-555-0126",
                "website": "https://example.com/python-book",
                "location": '{"address": "321 Book St, Literary Town, MA", "latitude": 42.3601, "longitude": -71.0589}',
                "barcode": "4567890123456",
                "auto_number": "ITEM004"
            }
        },
        {
            "fields": {
                "id": "5",
                "name": "Organic Green Tea Set",
                "category": "Food",
                "price": "24.99",
                "in_stock": True,
                "created_date": 1672876800000,  # 2023-01-05 00:00:00
                "description": "Premium organic green tea collection from Japan",
                "tags": ["New", "Limited"],
                "rating": 4,
                "progress": 100,
                "email": "food@store.com",
                "phone": "+1-555-0127",
                "website": "https://example.com/tea-set",
                "location": '{"address": "654 Tea Garden Rd, Zen City, CA", "latitude": 34.0522, "longitude": -118.2437}',
                "barcode": "5678901234567",
                "auto_number": "ITEM005"
            }
        }
    ]


def main():
    parser = argparse.ArgumentParser(description="Set up Lark Drive Source test data")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--cleanup", action="store_true", help="Cleanup test data (opens browser)")
    args = parser.parse_args()

    if not all([LARK_APP_ID, LARK_APP_SECRET]):
        print("[ERROR] Missing required environment variables:")
        if not LARK_APP_ID: print("  - LARK_APP_ID")
        if not LARK_APP_SECRET: print("  - LARK_APP_SECRET")
        return 1

    if not LARK_DRIVE_FOLDER_TOKEN:
        print("[ERROR] Missing LARK_DRIVE_FOLDER_TOKEN")
        print("This should be the parent folder where test data will be created")
        return 1

    if args.cleanup:
        print("[INFO] Cleanup not implemented - please manually delete the created folders")
        print(f"Parent folder token: {LARK_DRIVE_FOLDER_TOKEN}")
        return 0

    print("=" * 80)
    print("Lark Drive Source Test Data Setup")
    print("=" * 80)
    print("This sets up data for Lark Drive Source metadata provider (NOT the glue crawler)")
    print("=" * 80)

    try:
        api = LarkDriveSourceAPI(verbose=args.verbose)

        # Get the parent folder
        parent_token = LARK_DRIVE_FOLDER_TOKEN

        # Create test database structure
        databases = {
            "lark_drive_source_db": {
                "description": "Main database for Lark Drive Source testing",
                "tables": {
                    "products": create_comprehensive_test_fields()
                },
                "records": {
                    "products": create_comprehensive_test_records()
                }
            },
            "catalog_db": {
                "description": "Alternative database for catalog testing",
                "tables": {
                    "inventory": [
                        {"field_name": "product_id", "type": 1, "property": {}},
                        {"field_name": "product_name", "type": 1, "property": {}},
                        {"field_name": "quantity", "type": 2, "property": {}},
                        {"field_name": "location", "type": 1, "property": {}},
                        {"field_name": "last_updated", "type": 5, "property": {}},
                        {"field_name": "status", "type": 3, "property": {"options": [{"name": "In Stock"}, {"name": "Low Stock"}, {"name": "Out of Stock"}]}}
                    ]
                },
                "records": {
                    "inventory": [
                        {
                            "fields": {
                                "product_id": "1",
                                "product_name": "Laptop Pro 15-inch",
                                "quantity": "25",
                                "location": "Warehouse A",
                                "last_updated": 1672963200000,
                                "status": "In Stock"
                            }
                        },
                        {
                            "fields": {
                                "product_id": "2",
                                "product_name": "Wireless Headphones",
                                "quantity": "5",
                                "location": "Warehouse B",
                                "last_updated": 1673049600000,
                                "status": "Low Stock"
                            }
                        },
                        {
                            "fields": {
                                "product_id": "3",
                                "product_name": "Winter Jacket",
                                "quantity": "0",
                                "location": "Warehouse C",
                                "last_updated": 1673136000000,
                                "status": "Out of Stock"
                            }
                        }
                    ]
                }
            }
        }

        created_resources = {}

        for db_name, db_config in databases.items():
            print(f"\n[INFO] Creating database: {db_name}")

            # Create folder for database
            folder_token = api.create_folder(parent_token, db_name)
            created_resources[db_name] = {"folder_token": folder_token, "bases": {}}

            for table_name, fields in db_config["tables"].items():
                print(f"[INFO] Creating table: {table_name}")

                # Create base for this table
                base_name = f"{table_name}_table"
                base_token = api.create_base(folder_token, base_name)
                created_resources[db_name]["bases"][table_name] = base_token

                # Create table structure
                table_id = api.create_table(base_token, table_name, fields)

                # Add records if available
                if table_name in db_config["records"]:
                    records = db_config["records"][table_name]
                    api.add_records(base_token, table_id, records)

        # Print summary
        print("\n" + "=" * 80)
        print("Lark Drive Source Setup Summary")
        print("=" * 80)

        print("\n📊 Created Resources:")
        for db_name, db_info in created_resources.items():
            print(f"  Database (Folder): {db_name}")
            print(f"    Folder Token: {db_info['folder_token']}")
            for table_name, base_token in db_info["bases"].items():
                print(f"    Table (Lark Base): {table_name}")
                print(f"      Base Token: {base_token}")

        print(f"\n🔗 Environment Variables for Lark Drive Source Testing:")
        print(f"export LARK_DRIVE_PARENT_FOLDER_TOKEN=\"{parent_token}\"")

        for db_name, db_info in created_resources.items():
            print(f"export LARK_DRIVE_{db_name.upper()}_FOLDER_TOKEN=\"{db_info['folder_token']}\"")
            for table_name, base_token in db_info["bases"].items():
                env_var = f"LARK_DRIVE_{db_name.upper()}_{table_name.upper()}_BASE_TOKEN"
                print(f"export {env_var}=\"{base_token}\"")

        print(f"\n📋 Test Configuration:")
        print(f"# For Lark Drive Source metadata provider testing:")
        print(f"export default_does_activate_lark_drive_source=true")
        print(f"export lark_folder_token_data_source=\"{created_resources['lark_drive_source_db']['folder_token']}\"")
        print(f"export TEST_DATABASE_LARK_DRIVE=\"lark_drive_source_db\"")
        print(f"export TEST_TABLE=\"products\"")

        print(f"\n✅ Lark Drive Source setup completed!")
        print("You can now run Lark Drive Source metadata provider tests with these environment variables.")
        print("Note: This is for the Lark Drive Source metadata provider, NOT the glue crawler.")

        return 0

    except Exception as e:
        print(f"\n[ERROR] Setup failed: {str(e)}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        return 1


if __name__ == "__main__":
    exit(main())