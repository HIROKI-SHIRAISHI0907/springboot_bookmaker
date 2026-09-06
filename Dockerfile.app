# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY bookmakers-parents/pom.xml bookmakers-parents/pom.xml
COPY bookmakers-common-data/pom.xml bookmakers-common-data/pom.xml
COPY bookmakers-app/pom.xml bookmakers-app/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -N -f bookmakers-parents/pom.xml -DskipTests -Dmaven.test.skip=true install

COPY bookmakers-common-data/ bookmakers-common-data/
COPY bookmakers-app/ bookmakers-app/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f bookmakers-common-data/pom.xml -DskipTests -Dmaven.test.skip=true clean install && \
    mvn -f bookmakers-app/pom.xml -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bookmakers-app/target/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

