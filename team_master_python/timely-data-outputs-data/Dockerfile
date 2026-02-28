# Playwright(Chromium)入りの公式イメージを使う（依存込みで安定）
FROM mcr.microsoft.com/playwright/python:v1.57.0-jammy

WORKDIR /app

# 依存関係（boto3はS3操作に必須）
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# スクリプトを配置
COPY BmData_aws_S3.py /app/BmData_aws_S3.py

# 実行（ECS/Fargate向け）
CMD ["python", "-u", "/app/BmData_aws_S3.py"]
