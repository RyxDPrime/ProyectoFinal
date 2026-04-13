# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew --no-daemon clean jar -x test

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV PORT=7070
EXPOSE 7070

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

