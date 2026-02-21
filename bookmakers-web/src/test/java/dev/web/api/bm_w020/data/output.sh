BUCKET="aws-s3-outputs-csv"

aws s3api list-objects-v2 \
  --bucket "$BUCKET" \
  --query "Contents[?ends_with(Key, '.csv')].Key" \
  --output text \
| tr '\t' '\n' \
| while IFS= read -r key; do
    [ -z "$key" ] && continue
    file="$(basename "$key")"
    # 同名があったら上書き事故になるので、存在する場合はスキップ（必要なら外してください）
    if [ -e "./$file" ]; then
      echo "skip (exists): $file"
      continue
    fi
    aws s3 cp "s3://$BUCKET/$key" "./$file"
  done
