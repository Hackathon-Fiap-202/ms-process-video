#!/bin/bash

echo "========== Testing LocalStack =========="
echo ""

ENDPOINT="http://localhost:4566"

echo "1. Testing S3 connection..."
curl -f "$ENDPOINT/_localstack/health" 2>/dev/null | jq . || echo "FAILED"

echo ""
echo "2. Listing S3 buckets..."
docker exec localstack aws s3 ls

echo ""
echo "3. Listing SQS queues..."
docker exec localstack aws sqs list-queues --region us-east-1

echo ""
echo "4. LocalStack logs (last 50 lines)..."
docker logs --tail 50 localstack | grep -i "init\|queue\|bucket\|error" || true

echo ""
echo "========== End of test =========="
