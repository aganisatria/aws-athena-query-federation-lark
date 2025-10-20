#!/usr/bin/env python3
"""
Script to check the schema (fields) of a Lark Base table.
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


class LarkAPI:
    """Simple Lark API wrapper."""
    def __init__(self):
        self._tenant_access_token = None
        self._token_expiry = 0

    def _get_tenant_access_token(self):
        if time.time() < self._token_expiry:
            return self._tenant_access_token

        url = "https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"
        headers = {"Content-Type": "application/json"}
        payload = {"app_id": LARK_APP_ID, "app_secret": LARK_APP_SECRET}

        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()

        if "tenant_access_token" not in data:
            raise KeyError("Failed to retrieve tenant_access_token")

        self._tenant_access_token = data["tenant_access_token"]
        self._token_expiry = time.time() + data["expire"] - 300
        return self._tenant_access_token

    def list_fields(self, base_id, table_id):
        """List all fields from a table."""
        url = f"https://open.larksuite.com/open-apis/bitable/v1/apps/{base_id}/tables/{table_id}/fields"
        headers = {
            "Authorization": f"Bearer {self._get_tenant_access_token()}",
            "Content-Type": "application/json"
        }

        all_fields = []
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

            all_fields.extend(data["data"]["items"])
            has_more = data["data"]["has_more"]
            page_token = data["data"].get("page_token")

        return all_fields


def main():
    # Check both metadata mapping table and the actual data table
    tables_to_check = [
        {
            "name": "Metadata Mapping Table",
            "base_id": "EEMGbnS87a2W1IsaJKhjds3fpwe",
            "table_id": "tblCGeqbqp03ivAY"
        },
        {
            "name": "Data Type Test Table",
            "base_id": "EEMGbnS87a2W1IsaJKhjds3fpwe",
            "table_id": "tblehzVRm83N1vOX"
        }
    ]

    api = LarkAPI()

    for table_info in tables_to_check:
        print("\n" + "=" * 80)
        print(f"  {table_info['name']}")
        print("=" * 80)
        print(f"Base ID:  {table_info['base_id']}")
        print(f"Table ID: {table_info['table_id']}")
        print("=" * 80)

        print("\n[INFO] Fetching fields...")
        fields = api.list_fields(table_info['base_id'], table_info['table_id'])

        print(f"\n[SUCCESS] Found {len(fields)} field(s)\n")

        for i, field in enumerate(fields, 1):
            field_name = field.get("field_name")
            field_type = field.get("type")
            field_id = field.get("field_id")
            print(f"{i}. {field_name}")
            print(f"   Type: {field_type}")
            print(f"   ID: {field_id}")


if __name__ == "__main__":
    main()
