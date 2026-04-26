# =============================================================
# Dockerfile — auth-service
# Build từ ROOT của multi-module project (context = rbac-gateway/)
# docker build -f auth-service/Dockerfile -t auth-service .
# =============================================================

# -------------------------------------------------------------
# Stage 1: Build
# -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml ./
COPY auth-service/pom.xml      ./auth-service/
COPY api-gateway/pom.xml       ./api-gateway/
COPY resource-service/pom.xml  ./resource-service/
COPY migration/pom.xml         ./migration/

RUN mvn dependency:go-offline -pl auth-service -am -q

COPY auth-service/src ./auth-service/src

RUN mvn package -pl auth-service -am -DskipTests -q

# -------------------------------------------------------------
# Stage 2: Extract Spring Boot layers
# auth-service dùng classifier=exec nên fat jar có suffix "-exec.jar"
# plain jar (không có suffix) được giữ lại cho api-gateway dependency
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/auth-service/target/auth-service-*-exec.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# -------------------------------------------------------------
# Stage 3: Runtime — JRE only
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app

COPY --from=extractor /extract/dependencies/          ./
COPY --from=extractor /extract/spring-boot-loader/    ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/           ./

# JVM tuning: G1GC cho JWT signing/verification (CPU-bound, MVC blocking)
ENV JAVA_OPTS="\
  -server \
  -XX:+UseG1GC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseContainerSupport \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
