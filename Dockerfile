FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 soundstream \
    && adduser -S -D -H -u 10001 -G soundstream soundstream
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER soundstream
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --output-document=/dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
