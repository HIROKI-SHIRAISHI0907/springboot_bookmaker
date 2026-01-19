# Dockerfile
# Playwright(Chromium)入りの公式イメージを使う（依存込みで安定）
# PlaywrightのPython版と同じバージョンの公式Dockerイメージを合わせる
FROM mcr.microsoft.com/playwright/python:v1.57.0-jammy

WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY FutureData_aws_S3.py /app/FutureData_aws_S3.py

# ECSでログをすぐ出す（バッファリング抑制）
ENV PYTHONUNBUFFERED=1

CMD ["python", "/app/FutureData_aws_S3.py"]
