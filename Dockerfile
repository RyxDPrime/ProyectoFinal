FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon clean shadowJar -x test

FROM eclipse-temurin:25-jre-jammy AS runtime

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

RUN useradd --system --uid 10001 --create-home --home-dir /app appuser

COPY --from=build --chown=appuser:appuser /workspace/build/libs/proyecto_final-1.0.0.jar /app/app.jar

USER appuser

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
