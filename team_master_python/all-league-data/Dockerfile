FROM mcr.microsoft.com/playwright/python:v1.57.0-jammy

WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY allLeagueData_aws_S3.py /app/allLeagueData_aws_S3.py

CMD ["python", "-u", "/app/allLeagueData_aws_S3.py"]
