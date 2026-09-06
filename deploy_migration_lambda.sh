#!/bin/bash
# bm-seqmap-migration Lambdaのデプロイ（作成 or 更新）を1本にまとめたスクリプト。
# 何度実行しても安全（既に存在するものはスキップ/更新するだけ）。
#
# 前提: このスクリプトと同じディレクトリに以下がある
#   - lambda_function.zip
#   - lambda-trust-policy.json
#   - iam-policy-bm-seqmap-migration-lambda.json
#
# 実行: bash deploy_migration_lambda.sh

set -euo pipefail

REGION="ap-northeast-1"
ACCOUNT_ID="287190274071"
ROLE_NAME="bmSeqMapMigrationLambdaRole"
FUNCTION_NAME="bm-seqmap-migration"
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"

echo "== 1. IAMロール確認/作成 =="
if aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  echo "ロールは既に存在します: $ROLE_NAME"
else
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document file://lambda-trust-policy.json
  echo "ロールを作成しました: $ROLE_NAME"
fi

echo "== 2. 基本実行権限（CloudWatch Logs）をアタッチ =="
aws iam attach-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

echo "== 3. 移行専用の権限（PutItem + GetObject）をアタッチ =="
aws iam put-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-name bm-seqmap-migration-access \
  --policy-document file://iam-policy-bm-seqmap-migration-lambda.json

echo "== 4. IAMロール反映待ち（新規作成直後はLambda作成が失敗しやすいため）=="
sleep 10

echo "== 5. Lambda関数 作成 or 更新 =="
if aws lambda get-function --function-name "$FUNCTION_NAME" --region "$REGION" >/dev/null 2>&1; then
  echo "関数は既に存在します。コードと設定を更新します。"
  aws lambda update-function-code \
    --function-name "$FUNCTION_NAME" \
    --zip-file fileb://lambda_function.zip \
    --region "$REGION"
  aws lambda wait function-updated --function-name "$FUNCTION_NAME" --region "$REGION"
  aws lambda update-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --timeout 300 \
    --memory-size 256 \
    --region "$REGION"
else
  echo "関数を新規作成します。"
  aws lambda create-function \
    --function-name "$FUNCTION_NAME" \
    --runtime python3.12 \
    --handler lambda_function.lambda_handler \
    --role "$ROLE_ARN" \
    --zip-file fileb://lambda_function.zip \
    --timeout 300 \
    --memory-size 256 \
    --region "$REGION"
fi

echo "== 6. 関数の更新完了待ち =="
aws lambda wait function-updated --function-name "$FUNCTION_NAME" --region "$REGION"

echo "== 完了 =="
echo "実行するには:"
echo "  aws lambda invoke --function-name $FUNCTION_NAME --cli-binary-format raw-in-base64-out --payload '{}' --region $REGION response.json && cat response.json"
