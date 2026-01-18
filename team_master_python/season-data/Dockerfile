# Dockerfile
# Playwright(Chromium)入りの公式イメージを使う（依存込みで安定）
# PlaywrightのPython版と同じバージョンの公式Dockerイメージを合わせる
FROM mcr.microsoft.com/playwright/python:v1.57.0-jammy

WORKDIR /app

# 依存関係（boto3はS3操作に必須）
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# あなたのスクリプトを配置（ファイル名は任意。ここでは TeamSeasonDateData_aws_S3.py）
COPY TeamSeasonDateData_aws_S3.py /app/TeamSeasonDateData_aws_S3.py

# 実行
CMD ["python", "/app/TeamSeasonDateData_aws_S3.py"]
