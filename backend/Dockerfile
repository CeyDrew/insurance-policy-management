# syntax=docker/dockerfile:1

# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers); they run in CI and with ./mvnw verify, not inside the image build.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

# --- runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/data && chown app /app/data
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER app
ENV TZ=Europe/Istanbul \
    CLAIM_DOCUMENT_DIR=/app/data/claim-documents \
    BATCH_IMPORT_DIR=/app/data/imports \
    BATCH_REPORT_DIR=/app/data/reports \
    BATCH_COLLECTION_DIR=/app/data/collections
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
