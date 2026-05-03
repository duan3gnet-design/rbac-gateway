# =============================================================
# Dockerfile — eureka-server
# Build từ ROOT của multi-module project (context = rbac-gateway/)
# docker build -f eureka-server/Dockerfile -t eureka-server .
# =============================================================

# -------------------------------------------------------------
# Stage 1: Build
# -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml ./
COPY eureka-server/pom.xml     ./eureka-server/
COPY auth-service/pom.xml      ./auth-service/
COPY api-gateway/pom.xml       ./api-gateway/
COPY resource-service/pom.xml  ./resource-service/
COPY migration/pom.xml         ./migration/

RUN mvn dependency:go-offline -pl eureka-server -am -q

COPY eureka-server/src ./eureka-server/src

RUN mvn package -pl eureka-server -am -DskipTests -q

# -------------------------------------------------------------
# Stage 2: Extract Spring Boot layers
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/eureka-server/target/eureka-server-*.jar app.jar
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

ENV JAVA_OPTS="\
  -server \
  -XX:+UseG1GC \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseContainerSupport \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8761

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8761/actuator/health || exit 1
