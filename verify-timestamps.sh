#!/bin/bash
exec_id=$(aws athena start-query-execution \
  --query-string "SELECT field_date_time FROM athena_lark_base_regression_test.data_type_test_table WHERE field_date_time IS NOT NULL ORDER BY field_date_time LIMIT 10" \
  --query-execution-context "Catalog=your-catalog-name,Database=athena_lark_base_regression_test" \
  --work-group poweruser \
  --region ap-southeast-1 \
  --query 'QueryExecutionId' \
  --output text)

echo "Query execution ID: $exec_id"
sleep 10

aws athena get-query-results \
  --query-execution-id "$exec_id" \
  --region ap-southeast-1 \
  --output json | jq -r '.ResultSet.Rows[] | .Data | map(.VarCharValue // "NULL") | @csv'
