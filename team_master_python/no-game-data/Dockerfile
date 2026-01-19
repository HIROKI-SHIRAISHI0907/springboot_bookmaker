# Playwright公式イメージ（Chromiumインストール済み）
FROM mcr.microsoft.com/playwright/python:v1.57.0-jammy

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    LANG=ja_JP.UTF-8 \
    TZ=Asia/Tokyo

WORKDIR /app

# requirements を先にコピー（キャッシュ効かせる）
COPY requirements.txt .

RUN pip install --upgrade pip \
    && pip install -r requirements.txt

COPY NoGame_aws_S3.py /app/NoGame_aws_S3.py

# ECSでログをすぐ出す（バッファリング抑制）
ENV PYTHONUNBUFFERED=1

CMD ["python", "/app/NoGame_aws_S3.py"]
