#!/usr/bin/env python3
"""
Populate the default regression test table (data_type_test_table) with enough
records to exercise the row-count-overshoot and null-field-handling regression
tests in test_row_count_and_null_handling.py.

Adds ~TOTAL_NEW_RECORDS records so the table crosses the PAGE_SIZE=500 page
boundary at least once (matching the scale of the real production incident:
606 records across 2 pages). A rotating subset of records deliberately omits
several fields so empty-cell handling gets exercised at multiple points across
the pagination sequence, on multiple field types - not just one hardcoded
field/position like the original incident.

Only writes to fields that are actually settable via the API. System/computed
fields (record_id, CreatedTime, ModifiedTime, CreatedUser, ModifiedUser,
AutoNumber, Formula, Lookup) and relational fields that need real linked
record ids or user/chat ids (SingleLink, DuplexLink, User, GroupChat,
Attachment, Location) are left alone.
"""
import os
import sys
import time

import requests
from dotenv import load_dotenv

dotenv_path = os.path.join(os.path.dirname(__file__), '../../../.env')
load_dotenv(dotenv_path=dotenv_path)

LARK_APP_ID = os.getenv("LARK_APP_ID")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET")

BASE_TOKEN = "EEMGbnS87a2W1IsaJKhjds3fpwe"
TABLE_ID = "tblehzVRm83N1vOX"

TOTAL_NEW_RECORDS = 537
BATCH_SIZE = 100
NULL_EVERY = 7  # ~1 in 7 records deliberately drops a rotating subset of fields

DROP_POOL = [
    "field_phone", "field_email", "field_url", "field_barcode",
    "field_currency_2", "field_currency_3", "field_date_time_5",
    "field_date_time_9", "field_multi_select", "field_rating_2",
]


def get_token():
    url = "https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"
    resp = requests.post(url, headers={"Content-Type": "application/json"},
                          json={"app_id": LARK_APP_ID, "app_secret": LARK_APP_SECRET})
    data = resp.json()
    if "tenant_access_token" not in data:
        raise RuntimeError(f"Auth failed: {data}")
    return data["tenant_access_token"]


def gen_record_fields(i, with_nulls):
    base_ts = 1700000000000 + i * 86400000  # spread across days

    fields = {
        "field_text": f"Populate Test Record {i}",
        "field_number": round(i * 1.5, 2),
        "field_checkbox": (i % 2 == 0),
        "field_single_select": ["Option A", "Option B", "Option C"][i % 3],
        "field_multi_select": [["Tag1"], ["Tag1", "Tag2"], ["Tag2", "Tag3"], ["Tag3"]][i % 4],
        "field_phone": f"+62812{i:07d}",
        "field_email": f"populate.test.{i}@example.com",
        "field_currency": round(i * 10.5, 2),
        "field_currency_2": round(i * 3.25, 2),
        "field_currency_3": round(i * 7.75, 2),
        "field_rating": (i % 5) + 1,
        "field_rating_2": (i % 3) + 1,
        "field_progress": round((i % 100) / 100.0, 2),
        "field_progress_2": round((i % 50) / 50.0, 2),
        "field_progress_3": round((i % 20) / 20.0, 2),
        "field_progress_4": round((i % 10) / 10.0, 2),
        "field_date_time": base_ts,
        "field_date_time_2": base_ts + 3600000,
        "field_date_time_3": base_ts + 7200000,
        "field_date_time_4": base_ts + 10800000,
        "field_date_time_5": base_ts + 14400000,
        "field_date_time_6": base_ts + 18000000,
        "field_date_time_7": base_ts + 21600000,
        "field_date_time_8": base_ts + 25200000,
        "field_date_time_9": base_ts + 28800000,
        "field_url": {"link": f"https://example.com/record/{i}", "text": f"Link {i}"},
        "field_barcode": f"{i:010d}",
    }

    if with_nulls:
        num_to_drop = 2 + (i % 3)
        start = i % len(DROP_POOL)
        for j in range(num_to_drop):
            fields.pop(DROP_POOL[(start + j) % len(DROP_POOL)], None)

    return fields


def main():
    token = get_token()
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    create_url = (f"https://open.larksuite.com/open-apis/bitable/v1/apps/{BASE_TOKEN}"
                  f"/tables/{TABLE_ID}/records/batch_create")

    print(f"Populating {TOTAL_NEW_RECORDS} records into base={BASE_TOKEN} table={TABLE_ID}")
    print(f"Batch size: {BATCH_SIZE}, null-field records: ~1 in {NULL_EVERY}\n")

    created = 0
    for batch_start in range(0, TOTAL_NEW_RECORDS, BATCH_SIZE):
        batch_end = min(batch_start + BATCH_SIZE, TOTAL_NEW_RECORDS)
        records = [
            {"fields": gen_record_fields(i, with_nulls=(i % NULL_EVERY == 0))}
            for i in range(batch_start, batch_end)
        ]

        resp = requests.post(create_url, headers=headers, json={"records": records})
        result = resp.json()

        if result.get("code") != 0:
            print(f"[ERROR] Batch {batch_start}-{batch_end} failed: {result}")
            sys.exit(1)

        batch_created = len(result["data"]["records"])
        created += batch_created
        print(f"[OK] Batch {batch_start}-{batch_end}: created {batch_created} "
              f"records (running total: {created})")
        time.sleep(0.5)

    print(f"\nDone. Created {created} new records.")

    search_url = (f"https://open.larksuite.com/open-apis/bitable/v1/apps/{BASE_TOKEN}"
                  f"/tables/{TABLE_ID}/records/search")
    resp = requests.post(search_url, headers=headers, json={"page_size": 1})
    total = resp.json()["data"]["total"]
    print(f"Final total records in table: {total}")


if __name__ == "__main__":
    main()
