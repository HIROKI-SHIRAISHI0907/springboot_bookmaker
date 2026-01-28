# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY bookmakers-parents/pom.xml bookmakers-parents/pom.xml
COPY bookmakers-common-data/pom.xml bookmakers-common-data/pom.xml
COPY bookmakers-batch/pom.xml bookmakers-batch/pom.xml
COPY bookmakers-web/pom.xml bookmakers-web/pom.xml
COPY bookmakers-app/pom.xml bookmakers-app/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f bookmakers-parents/pom.xml -DskipTests -Dmaven.test.skip=true dependency:go-offline

COPY bookmakers-parents/ bookmakers-parents/
COPY bookmakers-common-data/ bookmakers-common-data/
COPY bookmakers-batch/ bookmakers-batch/
COPY bookmakers-web/ bookmakers-web/
COPY bookmakers-app/ bookmakers-app/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f bookmakers-parents/pom.xml -DskipTests -Dmaven.test.skip=true clean package


# --- web runtime ---
FROM eclipse-temurin:21-jre AS web
WORKDIR /app
COPY --from=build /workspace/bookmakers-web/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]


# --- app runtime ---
FROM eclipse-temurin:21-jre AS app
WORKDIR /app
COPY --from=build /workspace/bookmakers-app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]


# --- batch runtime ---
FROM eclipse-temurin:21-jre AS batch
WORKDIR /app
COPY --from=build /workspace/bookmakers-batch/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
