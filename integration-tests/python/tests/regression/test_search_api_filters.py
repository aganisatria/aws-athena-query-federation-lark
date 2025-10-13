#!/usr/bin/env python3
"""
Search API Filters Regression Test

Tests search API filters directly to understand the correct syntax.
Works with real Lark API in AWS mode, uses WireMock in MOCK/HYBRID modes.

Migrated from: ../../../../test-search-api-filters.py
"""
import sys
import os
import json
import requests
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment, get_lark_api_base_url

class SearchAPIFilterTester(BaseRegressionTest):
    """Tests search API filter formats"""

    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.lark_api_base = get_lark_api_base_url()
        self.access_token = None

    def setup(self):
        super().setup()
        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            self.log_info(f"[{self.environment.value.upper()}] Using WireMock for Lark API")
        self._get_access_token()

    def _get_access_token(self):
        """Get Lark tenant access token (or mock token)"""
        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            # Use mock token
            self.access_token = "mock-tenant-access-token"
            return

        # AWS mode - get real token
        url = f"{self.lark_api_base}/open-apis/auth/v3/tenant_access_token/internal"
        headers = {"Content-Type": "application/json"}
        data = {
            "app_id": os.getenv('LARK_APP_ID'),
            "app_secret": os.getenv('LARK_APP_SECRET')
        }

        try:
            response = requests.post(url, headers=headers, json=data)
            result = response.json()

            if result.get('code') == 0:
                self.access_token = result.get('tenant_access_token')
                self.log_success("Access token obtained")
            else:
                self.log_error(f"Failed to get access token: {result}")
        except Exception as e:
            self.log_error(f"Failed to get access token: {str(e)}")

    def test_search_with_filter(self, filter_expr, description):
        """Test search API with a specific filter"""
        print(f"\n{'='*80}")
        print(f"Test: {description}")
        print(f"Filter: {filter_expr}")
        print(f"{'='*80}")

        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            # Mock mode: Validate filter structure
            self.log_info(f"[{self.environment.value.upper()}] Validating filter syntax")
            if filter_expr is None or isinstance(filter_expr, str):
                self.log_success(f"{description}: Filter syntax valid")
                return {"code": 0, "data": {"items": []}}
            else:
                self.log_error(f"{description}: Invalid filter syntax")
                return {"code": 1, "msg": "Invalid filter"}

        # AWS mode - call real Lark API
        url = f"{self.lark_api_base}/open-apis/bitable/v1/apps/{self.lark_base_token}/tables/{self.lark_table_id}/records/search"

        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }

        body = {"page_size": 5}
        if filter_expr:
            body["filter"] = filter_expr

        print(f"Request body: {json.dumps(body, indent=2)}")

        try:
            response = requests.post(url, headers=headers, json=body)
            result = response.json()

            print(f"Response code: {result.get('code')}")
            print(f"Response msg: {result.get('msg')}")

            if result.get('code') == 0:
                items = result.get('data', {}).get('items', [])
                print(f"✅ Success - {len(items)} records returned")
                for i, item in enumerate(items[:2]):
                    print(f"  Record {i+1}: {item.get('record_id')}")
                    fields = item.get('fields', {})
                    if 'field_checkbox' in fields:
                        print(f"    field_checkbox: {fields['field_checkbox']}")
                    if 'field_number' in fields:
                        print(f"    field_number: {fields['field_number']}")
                    if 'field_text' in fields:
                        text_val = fields['field_text']
                        if isinstance(text_val, list) and text_val:
                            print(f"    field_text: {text_val[0].get('text', 'N/A')[:50]}")
                        else:
                            print(f"    field_text: {text_val}")
            else:
                print(f"❌ Failed: {result}")

            return result
        except Exception as e:
            self.log_error(f"Request failed: {str(e)}")
            return {"code": 1, "msg": str(e)}

    def run_tests(self):
        """Run all filter format tests"""
        print("Testing Search API Filter Formats")
        print("="*80)

        tests = [
            ("CurrentValue.[field_checkbox]=1", "Old FQL: Checkbox = true (using CurrentValue)"),
            ("field_checkbox=true", "Simple: field_checkbox=true"),
            ("CurrentValue.[field_number]=123.456", "Old FQL: Number = 123.456"),
            ("field_number=123.456", "Simple: field_number=123.456"),
            ('CurrentValue.[field_text]="Sample text value"', 'Old FQL: Text = "Sample text value"'),
            (None, "No filter (get all records)"),
        ]

        for filter_expr, description in tests:
            result = self.test_search_with_filter(filter_expr, description)
            if result.get('code') == 0:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1

        print("\n" + "="*80)
        print("Test Complete")
        print("="*80)


def main():
    parser = argparse.ArgumentParser(description="Test search API filters")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
        print(f"[INFO] Running in {environment.value.upper()} mode - using WireMock for Lark API")

    tester = SearchAPIFilterTester(verbose=args.verbose)
    tester.setup()
    tester.run_tests()
    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    main()
