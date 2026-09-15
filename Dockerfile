FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home soundstream
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER soundstream
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
