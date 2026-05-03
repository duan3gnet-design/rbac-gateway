# =============================================================
# Dockerfile — migration (Flyway standalone)
# Build từ ROOT của multi-module project (context = rbac-gateway/)
# docker build -f migration/Dockerfile -t migration .
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
COPY eureka-server/pom.xml     ./eureka-server/

RUN mvn dependency:go-offline -pl migration -am -q

COPY migration/src ./migration/src

RUN mvn package -pl migration -am -DskipTests -q

# -------------------------------------------------------------
# Stage 2: Runtime — JRE only, chạy plain JAR trực tiếp
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app

COPY --from=builder /build/migration/target/migration-*.jar app.jar

ENV JAVA_OPTS="\
  -XX:MaxRAMPercentage=50.0 \
  -XX:+UseContainerSupport \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
