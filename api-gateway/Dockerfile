# =============================================================
# Dockerfile — api-gateway
# Build từ ROOT của multi-module project (context = rbac-gateway/)
# docker build -f api-gateway/Dockerfile -t api-gateway .
# =============================================================

# -------------------------------------------------------------
# Stage 1: Build toàn bộ multi-module
# (cần build từ root vì api-gateway depends on auth-service artifact)
# -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache layer: copy tất cả pom.xml trước, download deps, rồi mới copy src
# → re-build khi chỉ thay đổi code sẽ dùng lại cache deps
COPY pom.xml ./
COPY api-gateway/pom.xml       ./api-gateway/
COPY auth-service/pom.xml      ./auth-service/
COPY resource-service/pom.xml  ./resource-service/
COPY migration/pom.xml         ./migration/

RUN mvn dependency:go-offline -pl api-gateway,auth-service -am -q

# Copy source của các module cần thiết
COPY auth-service/src      ./auth-service/src
COPY api-gateway/src       ./api-gateway/src
COPY resource-service/src  ./resource-service/src
COPY migration/src         ./migration/src

# Build chỉ api-gateway và auth-service (dependency)
RUN mvn package -pl api-gateway,auth-service -am -DskipTests -q

# -------------------------------------------------------------
# Stage 2: Extract Spring Boot layers
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/api-gateway/target/api-gateway-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# -------------------------------------------------------------
# Stage 3: Runtime — JRE only
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app

# Copy layers theo thứ tự ít thay đổi → thường xuyên thay đổi
COPY --from=extractor /extract/dependencies/          ./
COPY --from=extractor /extract/spring-boot-loader/    ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/           ./

# JVM tuning: ZGC cho WebFlux reactive — low-latency, phù hợp perf test
ENV JAVA_OPTS="\
  -server \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseContainerSupport \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
