#!/bin/bash
set -e

echo "========== LOCALSTACK INIT SCRIPT STARTED =========="

AWS_REGION="us-east-1"
ENDPOINT_URL="http://localhost:4566"
PROCESS_VIDEO_QUEUE_NAME="video-process-command"
UPDATE_VIDEO_QUEUE_NAME="video-updated-event"
INPUT_BUCKET="video-input-storage"
OUTPUT_BUCKET="video-processed-storage"

export AWS_REGION
export AWS_DEFAULT_REGION=$AWS_REGION
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

echo "[INFO] AWS_REGION: $AWS_REGION"
echo "[INFO] ENDPOINT_URL: $ENDPOINT_URL"

echo ""
echo "========== HEALTH CHECK =========="
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
    if curl -f "$ENDPOINT_URL/_localstack/health" 2>/dev/null | grep -q '"services"'; then
        echo "[✓] LocalStack is healthy"
        break
    fi
    attempt=$((attempt + 1))
    echo "[WAIT] Attempt $attempt/$max_attempts..."
    sleep 2
done

if [ $attempt -eq $max_attempts ]; then
    echo "[ERROR] LocalStack did not become healthy after $max_attempts attempts"
    exit 1
fi

echo ""
echo "========== CREATE S3 BUCKETS =========="

echo "[CREATE] Bucket: $INPUT_BUCKET"
aws --endpoint-url="$ENDPOINT_URL" s3 mb "s3://$INPUT_BUCKET" --region "$AWS_REGION" 2>&1 || echo "[SKIP] Bucket already exists"

echo "[CREATE] Bucket: $OUTPUT_BUCKET"
aws --endpoint-url="$ENDPOINT_URL" s3 mb "s3://$OUTPUT_BUCKET" --region "$AWS_REGION" 2>&1 || echo "[SKIP] Bucket already exists"

echo "[LIST] Existing buckets:"
aws --endpoint-url="$ENDPOINT_URL" s3 ls

echo ""
echo "========== CREATE SQS QUEUES =========="

echo "[CREATE] Queue: $PROCESS_VIDEO_QUEUE_NAME"
aws --endpoint-url="$ENDPOINT_URL" sqs create-queue \
  --queue-name "$PROCESS_VIDEO_QUEUE_NAME" \
  --region "$AWS_REGION" \
  --attributes VisibilityTimeout=900,MessageRetentionPeriod=86400 \
  2>&1 || echo "[SKIP] Queue already exists"

echo "[CREATE] Queue: $UPDATE_VIDEO_QUEUE_NAME"
aws --endpoint-url="$ENDPOINT_URL" sqs create-queue \
  --queue-name "$UPDATE_VIDEO_QUEUE_NAME" \
  --region "$AWS_REGION" \
  2>&1 || echo "[SKIP] Queue already exists"

echo "[LIST] Existing queues:"
aws --endpoint-url="$ENDPOINT_URL" sqs list-queues --region "$AWS_REGION" 2>&1 || true

echo ""
echo "========== CONFIGURE S3 → SQS NOTIFICATION =========="

PROCESS_QUEUE_ARN="arn:aws:sqs:${AWS_REGION}:000000000000:${PROCESS_VIDEO_QUEUE_NAME}"
PROCESS_QUEUE_URL="$ENDPOINT_URL/000000000000/${PROCESS_VIDEO_QUEUE_NAME}"

echo "[INFO] Queue ARN: $PROCESS_QUEUE_ARN"
echo "[INFO] Queue URL: $PROCESS_QUEUE_URL"

POLICY="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"s3.amazonaws.com\"},\"Action\":\"sqs:SendMessage\",\"Resource\":\"${PROCESS_QUEUE_ARN}\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"arn:aws:s3:::${INPUT_BUCKET}\"}}}]}"

echo "[SET] Queue policy"
aws --endpoint-url="$ENDPOINT_URL" sqs set-queue-attributes \
  --queue-url "$PROCESS_QUEUE_URL" \
  --region "$AWS_REGION" \
  --attributes "Policy=$POLICY" 2>&1 || echo "[WARN] Could not set policy (may be expected)"

echo "[SET] S3 bucket notification"
aws --endpoint-url="$ENDPOINT_URL" s3api put-bucket-notification-configuration \
  --bucket "$INPUT_BUCKET" \
  --region "$AWS_REGION" \
  --notification-configuration "QueueConfigurations=[{Events=['s3:ObjectCreated:*'],QueueArn='${PROCESS_QUEUE_ARN}',Filter={Key={FilterRules=[{Name='suffix',Value='.mp4'}]}}}]" \
  2>&1 || echo "[WARN] Could not set notification (may be expected)"

echo ""
echo "========== VERIFICATION =========="
echo "[VERIFY] S3 Buckets:"
aws --endpoint-url="$ENDPOINT_URL" s3 ls

echo ""
echo "[VERIFY] SQS Queues:"
aws --endpoint-url="$ENDPOINT_URL" sqs list-queues --region "$AWS_REGION" 2>&1 || true

echo ""
echo "========== ✓ INIT SCRIPT COMPLETED SUCCESSFULLY =========="
