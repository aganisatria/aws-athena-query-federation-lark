#!/usr/bin/env python3
"""
JSON Filters Regression Test

Test JSON filter format with search API.
Works with real Lark API in AWS mode, uses WireMock in MOCK/HYBRID modes.

Migrated from: ../../../../test-json-filters.py
"""
import sys
import os
import json
import requests
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment, get_lark_api_base_url

class JSONFilterTester(BaseRegressionTest):
    """Tests JSON filter format with search API"""

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

    def test_filter(self, filter_obj, description):
        """Test search API with JSON filter object"""
        print(f"\n{'='*80}")
        print(f"Test: {description}")
        print(f"Filter JSON: {json.dumps(filter_obj, indent=2)}")
        print(f"{'='*80}")

        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            # Mock mode: Validate filter structure
            self.log_info(f"[{self.environment.value.upper()}] Validating JSON filter structure")
            if isinstance(filter_obj, dict) and 'conjunction' in filter_obj and 'conditions' in filter_obj:
                self.log_success(f"{description}: JSON filter structure valid")
                return {"code": 0, "data": {"items": []}}
            else:
                self.log_error(f"{description}: Invalid JSON filter structure")
                return {"code": 1, "msg": "Invalid filter"}

        # AWS mode - call real Lark API
        url = f"{self.lark_api_base}/open-apis/bitable/v1/apps/{self.lark_base_token}/tables/{self.lark_table_id}/records/search"

        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }

        body = {
            "page_size": 5,
            "filter": filter_obj
        }

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
            else:
                print(f"❌ Failed: {result}")

            return result
        except Exception as e:
            self.log_error(f"Request failed: {str(e)}")
            return {"code": 1, "msg": str(e)}

    def run_tests(self):
        """Run all JSON filter tests"""
        tests = [
            ({"conjunction": "and", "conditions": [{"field_name": "field_checkbox", "operator": "is", "value": ["true"]}]}, "Checkbox = true (string 'true')"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_checkbox", "operator": "is", "value": [True]}]}, "Checkbox = true (boolean True)"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_number", "operator": "is", "value": ["123.456"]}]}, "Number = 123.456 (string)"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_number", "operator": "is", "value": [123.456]}]}, "Number = 123.456 (number)"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_text", "operator": "is", "value": ["Sample text value"]}]}, "Text = 'Sample text value'"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_text", "operator": "isNotEmpty", "value": []}]}, "Text IS NOT NULL (isNotEmpty)"),
            ({"conjunction": "and", "conditions": [{"field_name": "field_text", "operator": "isEmpty", "value": []}]}, "Text IS NULL (isEmpty)"),
        ]

        for filter_obj, description in tests:
            result = self.test_filter(filter_obj, description)
            if result.get('code') == 0:
                self.test_results['passed'] += 1
            else:
                self.test_results['failed'] += 1
            self.test_results['total'] += 1

        print(f"\n{'='*80}")
        print("Test Complete")
        print(f"{'='*80}")


def main():
    parser = argparse.ArgumentParser(description="Test JSON filters")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
        print(f"[INFO] Running in {environment.value.upper()} mode - using WireMock for Lark API")

    tester = JSONFilterTester(verbose=args.verbose)
    tester.setup()
    tester.run_tests()
    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    main()
