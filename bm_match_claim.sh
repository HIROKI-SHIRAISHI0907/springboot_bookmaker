#!/usr/bin/env bash
# =========================================================
# bm_match_claim テーブル作成 + ECSタスクロールへの権限付与
#
# 用途:
#   Flashscoreスクレイパー(3並列ECSタスク)が同じ試合を二重に
#   処理しないよう排他制御するための DynamoDB テーブルを作成し、
#   ECSタスクロールに PutItem/GetItem 権限を追加します。
#   （SeqKeyServiceの修正とセットで使う、Python側「1番」の修正用）
#
# 前提:
#   - AWS CLI v2 がインストール済みで、対象アカウントにログイン済み
#     （aws configure 済み、または AWS_PROFILE / 環境変数で認証情報が
#      通っている状態）であること
#   - このスクリプトを実行するIAMユーザー/ロールに
#     dynamodb:CreateTable, dynamodb:UpdateTimeToLive, iam:PutRolePolicy
#     などの権限があること（通常は管理者権限で1回だけ実行すればOK）
#
# 使い方（どちらでも可）:
#   TASK_ROLE_NAME=bm-scraper-task-role AWS_REGION=ap-northeast-1 \
#     ./setup_bm_match_claim.sh
#
#   ./setup_bm_match_claim.sh bm-scraper-task-role ap-northeast-1 bm_match_claim
#
#   TASK_ROLE_NAME には、ECSタスク定義の taskRoleArn に設定されている
#   IAMロール名（ARNではなく名前部分）を指定してください。
#   既存の bm_seq_counter 用アクセス権限を付けているロールと同じはずです。
# =========================================================
set -euo pipefail

# ---- パラメータ ----
TASK_ROLE_NAME="${1:-${TASK_ROLE_NAME:-}}"
AWS_REGION="${2:-${AWS_REGION:-ap-northeast-1}}"
TABLE_NAME="${3:-${TABLE_NAME:-bm_match_claim}}"
POLICY_NAME="${POLICY_NAME:-BmMatchClaimDynamoDBAccess}"

if [[ -z "${TASK_ROLE_NAME}" ]]; then
  echo "❌ TASK_ROLE_NAME が指定されていません。"
  echo "   例: TASK_ROLE_NAME=bm-scraper-task-role ./setup_bm_match_claim.sh"
  echo "   ECSタスク定義の taskRoleArn に設定されているIAMロール名を指定してください。"
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "❌ aws CLI が見つかりません。AWS CLI v2 をインストールしてください。"
  exit 1
fi

echo "▶ 設定"
echo "   AWS_REGION      = ${AWS_REGION}"
echo "   TABLE_NAME      = ${TABLE_NAME}"
echo "   TASK_ROLE_NAME  = ${TASK_ROLE_NAME}"
echo "   POLICY_NAME     = ${POLICY_NAME}"
echo

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
TABLE_ARN="arn:aws:dynamodb:${AWS_REGION}:${AWS_ACCOUNT_ID}:table/${TABLE_NAME}"

# 実行前にロールの存在を確認しておく（無ければ早めに気づけるように）
if ! aws iam get-role --role-name "${TASK_ROLE_NAME}" >/dev/null 2>&1; then
  echo "❌ IAMロール ${TASK_ROLE_NAME} が見つかりません。ロール名を確認してください。"
  exit 1
fi

# ---------------------------------------------------------
# 1) DynamoDBテーブル作成（既に存在すればスキップ）
# ---------------------------------------------------------
if aws dynamodb describe-table \
      --region "${AWS_REGION}" \
      --table-name "${TABLE_NAME}" >/dev/null 2>&1; then
  echo "✅ [1/3] テーブル ${TABLE_NAME} は既に存在します。作成をスキップします。"
else
  echo "🆕 [1/3] テーブル ${TABLE_NAME} を作成します..."
  aws dynamodb create-table \
    --region "${AWS_REGION}" \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions AttributeName=mid,AttributeType=S \
    --key-schema AttributeName=mid,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --tags Key=Project,Value=bm-scraper Key=Purpose,Value=match-claim-lock \
    >/dev/null

  echo "⏳ テーブルがACTIVEになるまで待機します..."
  aws dynamodb wait table-exists --region "${AWS_REGION}" --table-name "${TABLE_NAME}"
  echo "✅ テーブル作成完了"
fi

# ---------------------------------------------------------
# 2) TTL設定（expires_at属性で自動失効させる）
# ---------------------------------------------------------
echo "🕒 [2/3] TTL(expires_at)を設定します..."
CURRENT_TTL_STATUS="$(aws dynamodb describe-time-to-live \
  --region "${AWS_REGION}" \
  --table-name "${TABLE_NAME}" \
  --query 'TimeToLiveDescription.TimeToLiveStatus' \
  --output text 2>/dev/null || echo "DISABLED")"

if [[ "${CURRENT_TTL_STATUS}" == "ENABLED" || "${CURRENT_TTL_STATUS}" == "ENABLING" ]]; then
  echo "✅ TTLは既に有効(${CURRENT_TTL_STATUS})です。スキップします。"
else
  aws dynamodb update-time-to-live \
    --region "${AWS_REGION}" \
    --table-name "${TABLE_NAME}" \
    --time-to-live-specification "Enabled=true,AttributeName=expires_at" \
    >/dev/null
  echo "✅ TTL設定完了（反映まで数分かかる場合があります）"
fi

# ---------------------------------------------------------
# 3) ECSタスクロールに権限を追加（インラインポリシー）
# ---------------------------------------------------------
echo "🔐 [3/3] IAMロール ${TASK_ROLE_NAME} に権限を追加します..."

POLICY_DOC="$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "BmMatchClaimAccess",
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:DescribeTable"
      ],
      "Resource": "${TABLE_ARN}"
    }
  ]
}
JSON
)"

aws iam put-role-policy \
  --role-name "${TASK_ROLE_NAME}" \
  --policy-name "${POLICY_NAME}" \
  --policy-document "${POLICY_DOC}"

echo "✅ IAMインラインポリシー ${POLICY_NAME} を ${TASK_ROLE_NAME} に付与しました。"
echo
echo "🎉 完了しました。"
echo "   テーブルARN : ${TABLE_ARN}"
echo
echo "Pythonスクリプト側の環境変数（デフォルトのままでよければ設定不要）:"
echo "   CLAIM_TABLE_NAME=${TABLE_NAME}"
echo "   CLAIM_TTL_SEC=150"
