#!/bin/bash
# LocalStack initialization script
# Runs when LocalStack is ready

set -e

echo "========================================="
echo "LocalStack Community Edition - Init Script"
echo "========================================="

# Wait for LocalStack to be fully ready
awslocal --version || echo "Warning: awslocal not available, using aws cli"

# Create test S3 bucket for Lambda deployment
echo "Creating S3 bucket for Lambda deployment..."
awslocal s3 mb s3://test-lambda-deployment || echo "Bucket already exists"

# Create S3 bucket for Athena query results
echo "Creating S3 bucket for Athena results..."
awslocal s3 mb s3://test-athena-results || echo "Bucket already exists"

# Create IAM role for Lambda (if needed)
echo "Creating Lambda execution role..."
awslocal iam create-role \
  --role-name test-lambda-execution-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "lambda.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' 2>/dev/null || echo "Role already exists"

# Attach basic Lambda execution policy
awslocal iam attach-role-policy \
  --role-name test-lambda-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole \
  2>/dev/null || echo "Policy already attached"

echo "========================================="
echo "LocalStack initialization complete!"
echo "Available services:"
echo "  - Lambda: http://localhost:4566"
echo "  - S3: http://localhost:4566"
echo "  - CloudWatch Logs: http://localhost:4566"
echo "========================================="
