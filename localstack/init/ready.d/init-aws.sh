#!/bin/sh
set -ux

echo ">>> INIT AWS SCRIPT STARTED <<<"

AWS_REGION="us-east-1"
ENDPOINT_URL="http://localhost:4566"
UPDATE_VIDEO_QUEUE_NAME="video-updated-event"
PROCESS_VIDEO_QUEUE_NAME="video-process-command"

# Nomes dos Buckets S3
INPUT_BUCKET="video-input-storage"
OUTPUT_BUCKET="video-processed-storage"

# --- CONFIGURAÇÃO S3 ---
echo "########### Criando Buckets S3 ###########"

aws --endpoint-url=${ENDPOINT_URL} s3 mb s3://${INPUT_BUCKET} --region ${AWS_REGION}
aws --endpoint-url=${ENDPOINT_URL} s3 mb s3://${OUTPUT_BUCKET} --region ${AWS_REGION}

# Opcional: Listar buckets para confirmar
aws --endpoint-url=${ENDPOINT_URL} s3 ls

# --- CONFIGURAÇÃO SQS ---
echo "########### Criando fila SQS: ${PROCESS_VIDEO_QUEUE_NAME} ###########"
aws --endpoint-url=${ENDPOINT_URL} sqs create-queue \
  --queue-name ${PROCESS_VIDEO_QUEUE_NAME} \
  --region ${AWS_REGION} \
  --attributes VisibilityTimeout=900,MessageRetentionPeriod=86400

sleep 2

echo "########### Criando fila SQS: ${UPDATE_VIDEO_QUEUE_NAME} ###########"
aws --endpoint-url=${ENDPOINT_URL} sqs create-queue \
  --queue-name ${UPDATE_VIDEO_QUEUE_NAME} \
  --region ${AWS_REGION}

# Get URL and ARN for the callback queue
QUEUE_URL="${ENDPOINT_URL}/000000000000/${PROCESS_VIDEO_QUEUE_NAME}"
QUEUE_ARN="arn:aws:sqs:${AWS_REGION}:000000000000:${PROCESS_VIDEO_QUEUE_NAME}"

# --- CONFIGURAÇÃO DE POLÍTICA ---
# Nota: Verifique se a variável ${TOPIC_ARN} está definida em seu ambiente,
# caso contrário, a política abaixo pode falhar ou ficar incompleta.
if [ -z "${TOPIC_ARN+x}" ]; then
    echo "Aviso: TOPIC_ARN não definida. Pulando configuração de política de SNS para SQS."
else
    echo "########### Configurando policy da fila SQS ###########"
    POLICY="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"sqs:SendMessage\",\"Resource\":\"${QUEUE_ARN}\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"${TOPIC_ARN}\"}}}]}"

    aws --endpoint-url=${ENDPOINT_URL} sqs set-queue-attributes \
      --queue-url "${QUEUE_URL}" \
      --attributes "Policy=${POLICY}"
fi

echo "########### Recursos criados com sucesso ###########"
aws --endpoint-url=${ENDPOINT_URL} sqs list-queues