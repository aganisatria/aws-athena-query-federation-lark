#!/usr/bin/env python3
"""
Script to check the current metadata mapping table entries in Lark Base.
This helps verify what tables the Glue crawler is configured to process.
"""
import os
import requests
import time
import json
from dotenv import load_dotenv

# Load environment variables
dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

LARK_APP_ID = os.getenv("LARK_APP_ID")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET")

# Metadata mapping table configuration
METADATA_BASE_ID = "EEMGbnS87a2W1IsaJKhjds3fpwe"
METADATA_TABLE_ID = "tblCGeqbqp03ivAY"


class LarkAPI:
    """Simple Lark API wrapper."""
    def __init__(self):
        self._tenant_access_token = None
        self._token_expiry = 0

    def _get_tenant_access_token(self):
        if time.time() < self._token_expiry:
            return self._tenant_access_token

        print("[INFO] Getting tenant access token...")
        url = "https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"
        headers = {"Content-Type": "application/json"}
        payload = {"app_id": LARK_APP_ID, "app_secret": LARK_APP_SECRET}

        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()

        if "tenant_access_token" not in data:
            print(f"[ERROR] Authentication failed: {data}")
            raise KeyError("Failed to retrieve tenant_access_token")

        self._tenant_access_token = data["tenant_access_token"]
        self._token_expiry = time.time() + data["expire"] - 300
        return self._tenant_access_token

    def list_records(self, base_id, table_id):
        """List all records from a table."""
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{base_id}/tables/{table_id}/records"
        headers = {
            "Authorization": f"Bearer {self._get_tenant_access_token()}",
            "Content-Type": "application/json"
        }

        all_records = []
        has_more = True
        page_token = None

        while has_more:
            params = {"page_size": 100}
            if page_token:
                params["page_token"] = page_token

            response = requests.get(url, headers=headers, params=params)
            response.raise_for_status()
            data = response.json()

            if data.get("code") != 0:
                print(f"[ERROR] API error: {data}")
                break

            all_records.extend(data["data"]["items"])
            has_more = data["data"]["has_more"]
            page_token = data["data"].get("page_token")

        return all_records


def main():
    print("=" * 80)
    print("  Metadata Mapping Table Check")
    print("=" * 80)
    print(f"Base ID:  {METADATA_BASE_ID}")
    print(f"Table ID: {METADATA_TABLE_ID}")
    print("=" * 80)

    api = LarkAPI()

    print("\n[INFO] Fetching records from metadata mapping table...")
    records = api.list_records(METADATA_BASE_ID, METADATA_TABLE_ID)

    print(f"\n[SUCCESS] Found {len(records)} record(s)\n")

    for i, record in enumerate(records, 1):
        print(f"Record #{i}:")
        print(f"  Record ID: {record.get('record_id')}")
        print(f"  Fields:")
        fields = record.get("fields", {})
        for field_name, field_value in fields.items():
            print(f"    {field_name}: {field_value}")
        print()

    # Print formatted summary
    print("=" * 80)
    print("  Summary")
    print("=" * 80)
    if records:
        print("\nCurrent Mappings:")
        for i, record in enumerate(records, 1):
            fields = record.get("fields", {})
            database_name = fields.get("database_name", "N/A")
            table_name = fields.get("table_name", "N/A")
            base_id = fields.get("table_lark_base_id", "N/A")
            table_id = fields.get("table_lark_table_id", "N/A")

            print(f"\n  Mapping #{i}:")
            print(f"    Database: {database_name}")
            print(f"    Table:    {table_name}")
            print(f"    Base ID:  {base_id}")
            print(f"    Table ID: {table_id}")
    else:
        print("\n  No mappings found!")

    print("\n" + "=" * 80)


if __name__ == "__main__":
    main()
