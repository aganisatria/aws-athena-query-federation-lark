#!/usr/bin/env python3
"""
Creates the necessary Lark Base tables and records for all regression tests.

This script will:
1. Create a main data base (`athena_regression_data_base`).
2. Create a lookup target table and the main `data_type_test_table`.
3. Add all 26 supported field types to the main data table, including complex
   types like LOOKUP and FORMULA that require manual creation steps.
4. Populate the main data table with 10 edge-case records.
5. Create a separate metadata base (`athena_metadata_source_base`).
6. Create TWO separate mapping tables inside the metadata base:
   - `glue_crawler_source_table`: For the Glue crawler.
   - `direct_provider_source_table`: For the LarkSourceMetadataProvider.
7. Populate the mapping tables to point to the same main data table but with
   different friendly database names (`glue_regression_db` and `direct_regression_db`).
8. Make both bases public and editable as requested.
"""
import os
import requests
import time
import argparse
from dotenv import load_dotenv

# Load environment variables from .env file
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

LARK_APP_ID = os.getenv("LARK_APP_ID")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET")
LARK_FOLDER_TOKEN = os.getenv("LARK_FOLDER_TOKEN")

class LarkAPI:
    """A simple wrapper for the Lark API to handle authentication and common operations."""
    def __init__(self, verbose=False):
        self._tenant_access_token = None
        self._token_expiry = 0
        self.verbose = verbose

    def _get_tenant_access_token(self):
        if time.time() < self._token_expiry:
            return self._tenant_access_token
        
        if self.verbose: print("[INFO] Refreshing tenant access token...")
        url = "https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"
        headers = {"Content-Type": "application/json"}
        payload = {"app_id": LARK_APP_ID, "app_secret": LARK_APP_SECRET}
        
        try:
            response = requests.post(url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
            if "tenant_access_token" not in data:
                print(f"[ERROR] Authentication failed. API response: {data}")
                raise KeyError("Failed to retrieve 'tenant_access_token' from Lark API.")
            self._tenant_access_token = data["tenant_access_token"]
            self._token_expiry = time.time() + data["expire"] - 300
            if self.verbose: print("[SUCCESS] Token obtained successfully.")
            return self._tenant_access_token
        except requests.exceptions.RequestException as e:
            print(f"[ERROR] Failed to get tenant access token: {e}")
            raise

    def _request(self, method, url, **kwargs):
        headers = kwargs.setdefault("headers", {})
        headers["Authorization"] = f"Bearer {self._get_tenant_access_token()}"
        headers.setdefault("Content-Type", "application/json")
        
        response = requests.request(method, url, **kwargs)
        response.raise_for_status()
        return response.json()

    def create_base(self, name):
        url = "https://open.larksuite.com/open-apis/bitable/v1/apps"
        payload = {"name": name, "folder_token": LARK_FOLDER_TOKEN}
        return self._request("POST", url, json=payload)['data']['app']['app_token']

    def create_table(self, app_token, name, fields=None):
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{app_token}/tables"
        payload = {"table": {"name": name, "default_view_name": "Grid", "fields": fields or []}}
        return self._request("POST", url, json=payload)['data']['table_id']

    def add_field(self, app_token, table_id, field_name, field_type, property=None):
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/fields"
        payload = {"field_name": field_name, "type": field_type, "property": property}
        return self._request("POST", url, json=payload)['data']['field']

    def add_records(self, app_token, table_id, records):
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{app_token}/tables/{table_id}/records/batch_create"
        payload = {"records": records}
        return self._request("POST", url, json=payload)

def get_simple_fields_definition():
    return [
        {"field_name": "field_text", "type": 1},
        # {"field_name": "field_number", "type": 2},
        # {"field_name": "field_single_select", "type": 3, "property": {"options": [{"name": "Option A"}, {"name": "Option B"}]}},
        # {"field_name": "field_multi_select", "type": 4, "property": {"options": [{"name": "Opt X"}, {"name": "Opt Y"}]}},
        # {"field_name": "field_date_time", "type": 5},
        # {"field_name": "field_checkbox", "type": 7},
        # {"field_name": "field_user", "type": 11, "property": {"multiple": True}},
        # {"field_name": "field_phone", "type": 13},
        # {"field_name": "field_url", "type": 15},
        # {"field_name": "field_attachment", "type": 17},
        # {"field_name": "field_location", "type": 22},
        # {"field_name": "field_currency", "type": 23, "property": {"formatter": "Rp 0.00"}},
        # {"field_name": "field_rating", "type": 9, "property": {"rating": 5}},
        # {"field_name": "field_barcode", "type": 27},
    ]

def get_records_data(lookup_table_record_ids):
    return [
        {"fields": {"field_text": "Record 1", "field_number": 10.5, "field_checkbox": True, "field_rating": 3, "field_single_select": "Option A", "field_multi_select": ["Opt X"], "field_single_link": [lookup_table_record_ids[0]]}},
        {"fields": {"field_text": "Record 2", "field_number": -5, "field_checkbox": False, "field_rating": 1, "field_single_select": "Option B", "field_multi_select": ["Opt Y"], "field_single_link": [lookup_table_record_ids[1]]}},
        {"fields": {"field_text": "Record 3 with a very long string to test limits" * 5, "field_number": 999999999.999, "field_checkbox": True}},
        {"fields": {"field_text": "Record 4", "field_number": 0, "field_checkbox": False}},
        {"fields": {"field_text": "Record 5", "field_date_time": 788918400000}}, # 1995-01-01
        {"fields": {"field_text": "Record 6", "field_date_time": 2051222400000}}, # 2035-01-01
        {"fields": {"field_text": "Record 7, empty values", "field_multi_select": []}},
        {"fields": {"field_text": "Record 8, single element array", "field_multi_select": ["Opt X"]}},
        {"fields": {"field_text": "Record 9, multiple elements", "field_multi_select": ["Opt X", "Opt Y"]}},
        {"fields": {"field_text": "Record 10, null number", "field_number": None}},
    ]

def main():
    parser = argparse.ArgumentParser(description="Setup Lark Base test data.")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    print("="*80)
    print("  Lark Base Test Data Setup")
    print("="*80)

    api = LarkAPI(verbose=args.verbose)

    # 1. Create Main Data Base
    data_base_name = "athena_regression_data_base"
    if args.verbose: print(f"\n[INFO] Creating Main Data Base: {data_base_name}...")
    data_app_token = api.create_base(data_base_name)
    # api.make_base_public(data_file_token)
    if args.verbose: print(f"[SUCCESS] Main Data Base created with token: {data_app_token}.")

    # 2. Create Lookup Target Table
    lookup_table_name = "lookup_target_table"
    if args.verbose: print(f"[INFO] Creating Lookup Target Table: {lookup_table_name}...")
    lookup_fields = [{"field_name": "target_text", "type": 1}, {"field_name": "target_number", "type": 2}]
    lookup_table_id = api.create_table(data_app_token, lookup_table_name, fields=lookup_fields)
    if args.verbose: print(f"[SUCCESS] Lookup Target Table created with ID: {lookup_table_id}")
    lookup_records = [{"fields": {"target_text": "Lookup Val 1", "target_number": 100}}, {"fields": {"target_text": "Lookup Val 2", "target_number": 200}}]
    lookup_record_ids = [rec['record_id'] for rec in api.add_records(data_app_token, lookup_table_id, lookup_records)['data']['records']]
    if args.verbose: print(f"[SUCCESS] Populated Lookup Target Table.")

    # 3. Create the main data table with simple fields
    data_table_name = "data_type_test_table"
    if args.verbose: print(f"[INFO] Creating Data Table: {data_table_name}...")
    simple_fields = get_simple_fields_definition()
    data_table_id = api.create_table(data_app_token, data_table_name, fields=simple_fields)
    if args.verbose: print(f"[SUCCESS] Data Table created with ID: {data_table_id} and simple fields.")

    # 4. Add complex/manual fields
    if args.verbose: print("[INFO] Adding complex fields...")
    api.add_field(data_app_token, data_table_id, "field_single_link", 18, {"table_id": lookup_table_id})
    lookup_field_info = api.add_field(data_app_token, data_table_id, "field_lookup", 19, {"source_field_name": "field_single_link", "target_field_names": ["target_text", "target_number"]}) # <- BARIS YANG DIPERBAIKI
    api.add_field(data_app_token, data_table_id, "field_formula", 20, {"formula_expression": "BITAND(1, 2)"})
    if args.verbose: print("[SUCCESS] Complex fields added.")

    # 5. Populate the main data table
    records = get_records_data(lookup_record_ids)
    if args.verbose: print(f"[INFO] Populating {len(records)} test records into {data_table_name}...")
    api.add_records(data_app_token, data_table_id, records)
    if args.verbose: print(f"[SUCCESS] Populated {len(records)} records.")

    # 6. Create Metadata Base
    metadata_base_name = "athena_metadata_source_base"
    if args.verbose: print(f"\n[INFO] Creating Metadata Base: {metadata_base_name}...")
    metadata_app_token = api.create_base(metadata_base_name)
    # api.make_base_public(metadata_file_token)
    if args.verbose: print(f"[SUCCESS] Metadata Base created with token: {metadata_app_token}.")

    # 7. Create TWO mapping tables with the same schema
    mapping_schema = [
        {"field_name": "database_name", "type": 1},
        {"field_name": "table_name", "type": 1},
        {"field_name": "table_lark_base_id", "type": 1},
        {"field_name": "table_lark_table_id", "type": 1},
    ]

    # Table 1 for Glue
    glue_mapping_table_name = "glue_crawler_source_table"
    if args.verbose: print(f"[INFO] Creating Glue Crawler mapping table: {glue_mapping_table_name}...")
    glue_mapping_table_id = api.create_table(metadata_app_token, glue_mapping_table_name, fields=mapping_schema)
    glue_mapping_record = [{"fields": {"database_name": "glue_regression_db", "table_name": "data_type_test_table", "table_lark_base_id": data_app_token, "table_lark_table_id": data_table_id}}]
    api.add_records(metadata_app_token, glue_mapping_table_id, glue_mapping_record)
    if args.verbose: print(f"[SUCCESS] Created and populated {glue_mapping_table_name}.")

    # Table 2 for Direct Provider
    direct_mapping_table_name = "direct_provider_source_table"
    if args.verbose: print(f"[INFO] Creating Direct Provider mapping table: {direct_mapping_table_name}...")
    direct_mapping_table_id = api.create_table(metadata_app_token, direct_mapping_table_name, fields=mapping_schema)
    direct_mapping_record = [{"fields": {"database_name": "direct_regression_db", "table_name": "data_type_test_table", "table_lark_base_id": data_app_token, "table_lark_table_id": data_table_id}}]
    api.add_records(metadata_app_token, direct_mapping_table_id, direct_mapping_record)
    if args.verbose: print(f"[SUCCESS] Created and populated {direct_mapping_table_name}.")

    print("\n" + "="*80)
    print("  Setup Summary")
    print("="*80)
    print(f"\nData Layer:")
    print(f"  - Data Base Token: {data_app_token}")
    print(f"  - Data Table ID:   {data_table_id}")
    print(f"\nMetadata Layer:")
    print(f"  - Metadata Base Token: {metadata_app_token}")
    print(f"  - Glue Crawler Mapping Table ID:  {glue_mapping_table_id}")
    print(f"  - Direct Provider Mapping Table ID: {direct_mapping_table_id}")
    
    print("\n" + "="*80)
    print("  ACTION REQUIRED: Update your .env file with these values")
    print("="*80)
    print(f"LARK_DATA_BASE_TOKEN={data_app_token}")
    print(f"LARK_DATA_TABLE_ID={data_table_id}")
    print(f"LARK_METADATA_BASE_TOKEN={metadata_app_token}")
    print(f"LARK_GLUE_MAPPING_TABLE_ID={glue_mapping_table_id}")
    print(f"LARK_DIRECT_MAPPING_TABLE_ID={direct_mapping_table_id}")
    print("="*80)

if __name__ == "__main__":
    main()