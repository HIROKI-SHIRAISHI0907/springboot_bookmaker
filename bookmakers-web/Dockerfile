# syntax=docker/dockerfile:1.6

# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY . .

# 依存モジュールを先にインストール（Docker内 ~/.m2 へ）
RUN mvn -DskipTests -f bookmakers-common-data/pom.xml install
RUN mvn -DskipTests -f bookmakers-batch/pom.xml install

# web をビルド
RUN mvn -DskipTests -f bookmakers-web/pom.xml package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bookmakers-web/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
