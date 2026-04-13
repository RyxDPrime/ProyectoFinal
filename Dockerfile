# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace

# Copiamos primero los archivos de build para aprovechar caché de dependencias.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Luego copiamos el código fuente y compilamos el JAR final.
COPY src ./src
RUN ./gradlew --no-daemon clean jar -x test

FROM eclipse-temurin:25-jre-jammy AS runtime

WORKDIR /app

ENV PORT=8000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

RUN useradd --system --uid 10001 --create-home --home-dir /app appuser

COPY --from=build --chown=appuser:appuser /workspace/build/libs/*.jar /app/app.jar

USER appuser

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

