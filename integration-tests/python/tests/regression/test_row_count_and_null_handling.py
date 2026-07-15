#!/usr/bin/env python3
"""
Row Count and Null-Field Handling Regression Tests

Covers two production incidents on the Lambda -> Athena boundary, a path that
had never actually been exercised before (existing coverage only went as far
as "Lark API -> Lambda", i.e. mocked unit tests around record/field parsing):

1. A split's fetch loop could ask Lark for one more full page than it needed,
   then emit every record in that page instead of stopping at the row count
   the split was planned for. A table with 606 real records came back from
   Athena as 1000 rows. See BaseRecordHandler.getIterator().

2. The deprecated GET /records API explicitly returns null for empty cells,
   which crashed Map.copyOf in older deserialization code the first time a
   record with a genuinely empty field got paginated through. See
   SearchRecordsResponse.RecordItem / ListRecordsResponse.RecordItem history.

These tests only validate anything meaningful in AWS mode: they cross-check
Athena's result set against the real Lark Base API's own record count and
data, which MOCK/HYBRID mode (mocked Glue, no real Lark data) has no
equivalent of.
"""
import argparse
import os
import sys
import time

import requests

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from config import get_environment, TestEnvironment, get_lark_api_base_url


class RowCountAndNullHandlingTest(BaseRegressionTest):
    """Regression tests for the row-count-overshoot and null-field-NPE bugs."""

    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.lark_api_base = get_lark_api_base_url()
        self.access_token = None
        self.lark_base_token = os.getenv("LARK_BASE_APP_TOKEN", "test_base_token")
        self.lark_table_id = os.getenv("LARK_BASE_TABLE_ID", "test_table_id")

        import boto3
        self.athena = boto3.client('athena', region_name=os.getenv('AWS_REGION', 'ap-southeast-1'))
        self.workgroup = os.getenv('ATHENA_WORKGROUP', 'poweruser')

    def setup(self):
        super().setup()
        self._get_access_token()

    def _get_access_token(self):
        if self.environment != TestEnvironment.AWS:
            self.access_token = "mock-tenant-access-token"
            return

        url = f"{self.lark_api_base}/open-apis/auth/v3/tenant_access_token/internal"
        response = requests.post(url, headers={"Content-Type": "application/json"}, json={
            "app_id": os.getenv('LARK_APP_ID'),
            "app_secret": os.getenv('LARK_APP_SECRET')
        })
        result = response.json()
        if result.get('code') == 0:
            self.access_token = result.get('tenant_access_token')
            self.log_success("Lark access token obtained")
        else:
            raise Exception(f"Failed to get Lark access token: {result}")

    # ---- Lark ground-truth helpers (bypass the connector entirely) ----

    def _lark_search(self, page_size=1, page_token=None):
        url = (f"{self.lark_api_base}/open-apis/bitable/v1/apps/{self.lark_base_token}"
               f"/tables/{self.lark_table_id}/records/search")
        headers = {"Authorization": f"Bearer {self.access_token}", "Content-Type": "application/json"}
        body = {"page_size": page_size}
        if page_token:
            body["page_token"] = page_token
        response = requests.post(url, headers=headers, json=body)
        return response.json()

    def _lark_total(self):
        """Ground-truth record count straight from Lark's own API."""
        result = self._lark_search(page_size=1)
        if result.get('code') != 0:
            raise Exception(f"Lark search failed: {result}")
        return result['data']['total']

    # Fields that GET /records (the deprecated list API) sends back as an explicit
    # `null` for an empty cell - the exact shape that crashed the old Map.copyOf
    # deserialization. POST /records/search (what the connector actually uses)
    # instead just OMITS the key entirely for an empty cell, so "a record with a
    # null field" via the search API means "missing one of these keys", not
    # "has a None value" - there is no None value to find here.
    ALWAYS_POPULATED_FIELDS = {
        "field_text", "field_number", "field_checkbox", "field_single_select",
    }

    def _find_record_with_null_field(self, max_pages=20):
        """Walk Lark pages directly looking for a record with an empty (omitted) field."""
        page_token = None
        for _ in range(max_pages):
            result = self._lark_search(page_size=100, page_token=page_token)
            if result.get('code') != 0:
                raise Exception(f"Lark search failed: {result}")
            for item in result['data'].get('items', []):
                fields = item.get('fields', {})
                if any(v is None for v in fields.values()):
                    return item
                if any(f not in fields for f in self.ALWAYS_POPULATED_FIELDS):
                    return item
            if not result['data'].get('has_more'):
                break
            page_token = result['data'].get('page_token')
        return None

    # ---- Athena helpers ----

    def _execute_athena_query(self, query: str) -> list:
        # No ResultConfiguration: the workgroup already has a default output location.
        response = self.athena.start_query_execution(
            QueryString=query,
            QueryExecutionContext={'Database': self.test_database, 'Catalog': self.test_catalog},
            WorkGroup=self.workgroup
        )
        query_id = response['QueryExecutionId']

        for _ in range(120):
            status_resp = self.athena.get_query_execution(QueryExecutionId=query_id)
            status = status_resp['QueryExecution']['Status']['State']

            if status == 'SUCCEEDED':
                results = self.athena.get_query_results(QueryExecutionId=query_id)
                rows = results.get('ResultSet', {}).get('Rows', [])
                return [[c.get('VarCharValue') for c in row['Data']] for row in rows]
            elif status in ['FAILED', 'CANCELLED']:
                reason = status_resp['QueryExecution']['Status'].get('StateChangeReason', 'Unknown')
                raise Exception(f"Query {status}: {reason}")

            time.sleep(1)

        raise Exception("Query timeout after 120 seconds")

    def _query(self, sql: str) -> str:
        return sql.format(catalog=self.test_catalog, db=self.test_database, table=self.test_table)

    # ---- Category A: row-count-overshoot regression ----

    def test_row_count_matches_lark_total(self):
        """Athena COUNT(*) must exactly equal Lark's own total - not more, not less."""
        self.log_info("\nTest: row count matches Lark total")
        try:
            lark_total = self._lark_total()
            rows = self._execute_athena_query(self._query(
                'SELECT COUNT(*) FROM "{catalog}"."{db}"."{table}"'))
            athena_count = int(rows[1][0])

            if athena_count != lark_total:
                self.log_error(f"row_count_matches_lark_total: Lark total={lark_total}, "
                                f"Athena COUNT(*)={athena_count}")
                return False
            self.log_success(f"row_count_matches_lark_total: {athena_count} rows (matches Lark)")
            return True
        except Exception as e:
            self.log_error(f"row_count_matches_lark_total: {str(e)}")
            return False

    def test_no_duplicate_record_ids(self):
        """COUNT(*) must equal COUNT(DISTINCT $reserved_record_id) - this is the exact
        symptom an over-fetched, un-trimmed page would produce (duplicate rows padding
        out the result instead of a hard row-count mismatch)."""
        self.log_info("\nTest: no duplicate record ids")
        try:
            rows = self._execute_athena_query(self._query(
                'SELECT COUNT(*), COUNT(DISTINCT "$reserved_record_id") '
                'FROM "{catalog}"."{db}"."{table}"'))
            total, distinct = int(rows[1][0]), int(rows[1][1])

            if total != distinct:
                self.log_error(f"no_duplicate_record_ids: COUNT(*)={total} but "
                                f"COUNT(DISTINCT record_id)={distinct} "
                                f"({total - distinct} duplicate row(s))")
                return False
            self.log_success(f"no_duplicate_record_ids: {total} rows, all unique")
            return True
        except Exception as e:
            self.log_error(f"no_duplicate_record_ids: {str(e)}")
            return False

    def test_row_count_stable_across_repeated_queries(self):
        """The same COUNT(*) run twice must return the same number - catches
        non-deterministic over/under-fetching from a fetch loop whose stop
        condition depends on where the target happens to fall relative to a
        page boundary."""
        self.log_info("\nTest: row count stable across repeated queries")
        try:
            query = self._query('SELECT COUNT(*) FROM "{catalog}"."{db}"."{table}"')
            first = int(self._execute_athena_query(query)[1][0])
            second = int(self._execute_athena_query(query)[1][0])

            if first != second:
                self.log_error(f"row_count_stable_across_repeated_queries: "
                                f"first run={first}, second run={second}")
                return False
            self.log_success(f"row_count_stable_across_repeated_queries: {first} rows both times")
            return True
        except Exception as e:
            self.log_error(f"row_count_stable_across_repeated_queries: {str(e)}")
            return False

    def test_top_n_pushdown_exact_limit(self):
        """ORDER BY + LIMIT N must return exactly N rows. calculateEffectiveRowCount
        skips the LIMIT-vs-total capping when an ORDER BY is present (it relies on
        the TOP-N pushdown instead), so this is a distinct code path from a plain
        LIMIT and needs its own check."""
        self.log_info("\nTest: TOP-N pushdown returns exact limit")
        try:
            lark_total = self._lark_total()
            n = min(5, lark_total) if lark_total > 0 else 0
            if n == 0:
                self.log_warning("top_n_pushdown_exact_limit: table is empty, skipping")
                return True

            rows = self._execute_athena_query(self._query(
                'SELECT * FROM "{catalog}"."{db}"."{table}" '
                'ORDER BY "$reserved_record_id" LIMIT ' + str(n)))
            row_count = len(rows) - 1

            if row_count != n:
                self.log_error(f"top_n_pushdown_exact_limit: expected {n} rows, got {row_count}")
                return False
            self.log_success(f"top_n_pushdown_exact_limit: {row_count} rows (matches LIMIT {n})")
            return True
        except Exception as e:
            self.log_error(f"top_n_pushdown_exact_limit: {str(e)}")
            return False

    def test_parallel_split_row_count_matches_lark_total(self):
        """When ACTIVATE_PARALLEL_SPLIT is on, the sum across all parallel splits must
        still exactly match Lark's total. Each split gets its own expectedRowCountForSplit
        (up to PAGE_SIZE), so the same overshoot bug class could in principle hit any
        individual split - this only actually proves something when parallel splits are
        enabled for the deployed connector."""
        self.log_info("\nTest: parallel split row count matches Lark total")
        if os.getenv("ACTIVATE_PARALLEL_SPLIT", "false").lower() != "true":
            self.log_warning("parallel_split_row_count_matches_lark_total: "
                              "ACTIVATE_PARALLEL_SPLIT not enabled for this test run, skipping")
            return True
        try:
            lark_total = self._lark_total()
            rows = self._execute_athena_query(self._query(
                'SELECT COUNT(*) FROM "{catalog}"."{db}"."{table}"'))
            athena_count = int(rows[1][0])

            if athena_count != lark_total:
                self.log_error(f"parallel_split_row_count_matches_lark_total: "
                                f"Lark total={lark_total}, Athena COUNT(*)={athena_count}")
                return False
            self.log_success(f"parallel_split_row_count_matches_lark_total: "
                              f"{athena_count} rows across parallel splits (matches Lark)")
            return True
        except Exception as e:
            self.log_error(f"parallel_split_row_count_matches_lark_total: {str(e)}")
            return False

    # ---- Category B: null-field handling regression ----

    def test_query_succeeds_with_null_fields_present(self):
        """A record with a null field value must be readable via Athena without
        crashing, identified and fetched by its record id."""
        self.log_info("\nTest: query succeeds on record with null field")
        try:
            record = self._find_record_with_null_field()
            if record is None:
                self.log_warning("query_succeeds_with_null_fields_present: no record with a "
                                  "null field found in the first 2000 rows, skipping")
                return True

            record_id = record['record_id']
            fields = record.get('fields', {})
            null_fields = [k for k, v in fields.items() if v is None]
            null_fields += [f for f in self.ALWAYS_POPULATED_FIELDS if f not in fields]

            rows = self._execute_athena_query(self._query(
                'SELECT * FROM "{catalog}"."{db}"."{table}" WHERE "$reserved_record_id" = \''
                + record_id + '\''))

            if len(rows) < 2:
                self.log_error(f"query_succeeds_with_null_fields_present: record {record_id} "
                                f"(null fields: {null_fields}) not found via Athena")
                return False

            self.log_success(f"query_succeeds_with_null_fields_present: record {record_id} "
                              f"with null field(s) {null_fields} read successfully")
            return True
        except Exception as e:
            self.log_error(f"query_succeeds_with_null_fields_present: {str(e)}")
            return False

    def test_full_scan_does_not_crash_on_null_fields(self):
        """A full-table scan - the exact prod crash scenario, a null-valued record
        turning up mid-pagination - must complete without error regardless of where
        in the sequence that record falls."""
        self.log_info("\nTest: full table scan does not crash on null fields")
        try:
            rows = self._execute_athena_query(self._query(
                'SELECT COUNT(*) FROM "{catalog}"."{db}"."{table}"'))
            self.log_success(f"full_scan_does_not_crash_on_null_fields: "
                              f"scanned {rows[1][0]} rows without error")
            return True
        except Exception as e:
            self.log_error(f"full_scan_does_not_crash_on_null_fields: {str(e)}")
            return False

    def run_all_tests(self):
        print("\n" + "=" * 80)
        print("ROW COUNT & NULL-FIELD HANDLING REGRESSION TESTS")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Database: {self.test_database}")
        print(f"Table: {self.test_table}")
        print("=" * 80)

        if self.environment != TestEnvironment.AWS:
            self.log_warning("This suite only validates anything meaningful in AWS mode "
                              "(it cross-checks Athena results against the real Lark API "
                              "and real Athena). Skipping.")
            self.print_summary()
            return

        tests = [
            # Category A: row-count-overshoot regression
            self.test_row_count_matches_lark_total,
            self.test_no_duplicate_record_ids,
            self.test_row_count_stable_across_repeated_queries,
            self.test_top_n_pushdown_exact_limit,
            self.test_parallel_split_row_count_matches_lark_total,
            # Category B: null-field handling regression
            self.test_query_succeeds_with_null_fields_present,
            self.test_full_scan_does_not_crash_on_null_fields,
        ]

        for test in tests:
            try:
                test()
            except Exception as e:
                self.log_error(f"{test.__name__}: Exception: {e}")

        self.print_summary()


def main():
    parser = argparse.ArgumentParser(description="Row count and null-field handling regression tests")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()

    test = RowCountAndNullHandlingTest(verbose=args.verbose)
    test.setup()
    test.run_all_tests()
    test.teardown()

    sys.exit(1 if test.test_results["failed"] > 0 else 0)


if __name__ == "__main__":
    main()
