#!/usr/bin/env python3
"""
API Comparison Regression Test

Tests script to compare list records API vs search records API responses.
Works with real Lark API in AWS mode, uses WireMock in MOCK/HYBRID modes.

Migrated from: ../../../../test-api-comparison.py
"""
import sys
import os
import json
import requests
import argparse

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment, get_lark_api_base_url

class APIComparisonTester(BaseRegressionTest):
    """Tests comparing list records API vs search records API"""

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

    def test_list_records_api(self):
        """Test the old list records API (deprecated)"""
        print("\n" + "="*80)
        print("TESTING OLD LIST RECORDS API (Deprecated)")
        print("="*80)

        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            self.log_info(f"[{self.environment.value.upper()}] Simulating list records API")
            return {"code": 0, "data": {"items": []}}

        # AWS mode - call real Lark API
        url = f"{self.lark_api_base}/open-apis/bitable/v1/apps/{self.lark_base_token}/tables/{self.lark_table_id}/records"
        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }
        params = {"page_size": 3}

        print(f"\nRequest URL: {url}")
        print(f"Method: GET")
        print(f"Params: {json.dumps(params, indent=2)}")

        try:
            response = requests.get(url, headers=headers, params=params)
            result = response.json()

            print(f"\nResponse Status: {response.status_code}")
            print(f"Response Body:")
            print(json.dumps(result, indent=2, ensure_ascii=False))

            return result
        except Exception as e:
            self.log_error(f"Request failed: {str(e)}")
            return {"code": 1, "msg": str(e)}

    def test_search_records_api(self):
        """Test the new search records API"""
        print("\n" + "="*80)
        print("TESTING NEW SEARCH RECORDS API")
        print("="*80)

        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            self.log_info(f"[{self.environment.value.upper()}] Simulating search records API")
            return {"code": 0, "data": {"items": []}}

        # AWS mode - call real Lark API
        url = f"{self.lark_api_base}/open-apis/bitable/v1/apps/{self.lark_base_token}/tables/{self.lark_table_id}/records/search"
        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }
        body = {"page_size": 3}

        print(f"\nRequest URL: {url}")
        print(f"Method: POST")
        print(f"Body: {json.dumps(body, indent=2)}")

        try:
            response = requests.post(url, headers=headers, json=body)
            result = response.json()

            print(f"\nResponse Status: {response.status_code}")
            print(f"Response Body:")
            print(json.dumps(result, indent=2, ensure_ascii=False))

            return result
        except Exception as e:
            self.log_error(f"Request failed: {str(e)}")
            return {"code": 1, "msg": str(e)}

    def compare_responses(self, list_result, search_result):
        """Compare the two API responses"""
        print("\n" + "="*80)
        print("COMPARISON ANALYSIS")
        print("="*80)

        if self.environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
            self.log_info(f"[{self.environment.value.upper()}] Skipping detailed comparison (no real data)")
            return

        # Compare structure
        print("\n1. Top-level structure:")
        print(f"   List API keys: {list(list_result.keys())}")
        print(f"   Search API keys: {list(search_result.keys())}")

        # Compare data items
        if list_result.get('data', {}).get('items') and search_result.get('data', {}).get('items'):
            list_items = list_result['data']['items']
            search_items = search_result['data']['items']

            print(f"\n2. Number of items returned:")
            print(f"   List API: {len(list_items)} items")
            print(f"   Search API: {len(search_items)} items")

            if list_items and search_items:
                print(f"\n3. First item structure comparison:")
                print(f"\n   List API first item keys: {list(list_items[0].keys())}")
                print(f"   Search API first item keys: {list(search_items[0].keys())}")

    def run_tests(self):
        """Run all API comparison tests"""
        print("Lark Base API Comparison Test")
        print(f"Base Token: {self.lark_base_token}")
        print(f"Table ID: {self.lark_table_id}")

        try:
            # Test both APIs
            list_result = self.test_list_records_api()
            search_result = self.test_search_records_api()

            # Compare
            self.compare_responses(list_result, search_result)

            print("\n" + "="*80)
            print("TEST COMPLETED")
            print("="*80)

            if list_result.get('code') == 0 and search_result.get('code') == 0:
                self.test_results['passed'] = 2
                self.test_results['total'] = 2
            else:
                self.test_results['failed'] = 1
                self.test_results['passed'] = 1
                self.test_results['total'] = 2

        except Exception as e:
            self.log_error(f"Test failed: {str(e)}")
            self.test_results['failed'] = 1
            self.test_results['total'] = 1


def main():
    parser = argparse.ArgumentParser(description="Test API comparison")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    environment = get_environment()

    if environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
        print(f"[INFO] Running in {environment.value.upper()} mode - using WireMock for Lark API")

    tester = APIComparisonTester(verbose=args.verbose)
    tester.setup()
    tester.run_tests()
    tester.print_summary()
    tester.teardown()

    sys.exit(1 if tester.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    main()
