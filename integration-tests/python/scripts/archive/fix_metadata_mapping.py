#!/usr/bin/env python3
"""
Script to fix the metadata mapping table entry.
Changes the 'name' field from 'athena_lark_base_regression_test1' to 'athena_lark_base_regression_test'.
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
RECORD_ID = "recuZOBFMsRqZR"  # From previous check

# Fixed values
CORRECT_NAME = "athena_lark_base_regression_test"


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

    def update_record(self, base_id, table_id, record_id, fields):
        """Update a record in a table."""
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{base_id}/tables/{table_id}/records/{record_id}"
        headers = {
            "Authorization": f"Bearer {self._get_tenant_access_token()}",
            "Content-Type": "application/json"
        }
        payload = {"fields": fields}

        response = requests.put(url, headers=headers, json=payload)
        response.raise_for_status()
        data = response.json()

        if data.get("code") != 0:
            print(f"[ERROR] API error: {data}")
            raise Exception(f"Failed to update record: {data.get('msg')}")

        return data

    def get_record(self, base_id, table_id, record_id):
        """Get a single record."""
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{base_id}/tables/{table_id}/records/{record_id}"
        headers = {
            "Authorization": f"Bearer {self._get_tenant_access_token()}",
            "Content-Type": "application/json"
        }

        response = requests.get(url, headers=headers)
        response.raise_for_status()
        data = response.json()

        if data.get("code") != 0:
            print(f"[ERROR] API error: {data}")
            raise Exception(f"Failed to get record: {data.get('msg')}")

        return data["data"]["record"]


def main():
    print("=" * 80)
    print("  Fix Metadata Mapping Table")
    print("=" * 80)
    print(f"Base ID:   {METADATA_BASE_ID}")
    print(f"Table ID:  {METADATA_TABLE_ID}")
    print(f"Record ID: {RECORD_ID}")
    print("=" * 80)

    api = LarkAPI()

    print("\n[INFO] Fetching current record...")
    current_record = api.get_record(METADATA_BASE_ID, METADATA_TABLE_ID, RECORD_ID)
    current_fields = current_record.get("fields", {})

    print("\nCurrent Values:")
    for field_name, field_value in current_fields.items():
        print(f"  {field_name}: {field_value}")

    current_name = current_fields.get("name")
    print(f"\n[INFO] Current 'name' field value: '{current_name}'")

    if current_name == CORRECT_NAME:
        print(f"[SUCCESS] The 'name' field is already correct: '{CORRECT_NAME}'")
        print("No update needed!")
        return

    print(f"[INFO] Updating 'name' field to: '{CORRECT_NAME}'")

    # Update only the 'name' field
    updated_fields = {"name": CORRECT_NAME}

    result = api.update_record(METADATA_BASE_ID, METADATA_TABLE_ID, RECORD_ID, updated_fields)

    print("\n[SUCCESS] Record updated successfully!")

    # Verify the update
    print("\n[INFO] Verifying update...")
    updated_record = api.get_record(METADATA_BASE_ID, METADATA_TABLE_ID, RECORD_ID)
    updated_record_fields = updated_record.get("fields", {})

    print("\nUpdated Values:")
    for field_name, field_value in updated_record_fields.items():
        if field_name == "name":
            print(f"  {field_name}: {field_value} ✓")
        else:
            print(f"  {field_name}: {field_value}")

    print("\n" + "=" * 80)
    print("  Summary")
    print("=" * 80)
    print(f"Changed: name = '{current_name}' → '{CORRECT_NAME}'")
    print("The Glue crawler should now create tables in database: " + CORRECT_NAME)
    print("=" * 80)


if __name__ == "__main__":
    main()
