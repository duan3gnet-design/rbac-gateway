# =============================================================
# Dockerfile — auth-service  (GraalVM Native Image)
# Build từ ROOT của multi-module project (context = rbac-gateway/)
#   docker build -f auth-service/Dockerfile -t auth-service .
# =============================================================

FROM container-registry.oracle.com/graalvm/native-image:25 AS builder

ARG MAVEN_VERSION=3.9.16
RUN microdnf install -y wget tar gzip && \
    wget -q "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
         -O /tmp/maven.tar.gz && \
    tar -xzf /tmp/maven.tar.gz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn && \
    rm /tmp/maven.tar.gz

WORKDIR /build

COPY pom.xml ./
COPY api-gateway/pom.xml           ./api-gateway/
COPY auth-service/pom.xml          ./auth-service/
COPY resource-service/pom.xml      ./resource-service/
COPY migration/pom.xml             ./migration/
COPY common-observability/pom.xml  ./common-observability/
COPY eureka-server/pom.xml         ./eureka-server/

RUN mvn dependency:go-offline -q || true

COPY common-observability/src ./common-observability/src
COPY auth-service/src         ./auth-service/src

# ── Bước 1: install common-observability vào ~/.m2 ───────────────────────────
RUN mvn install \
      -pl common-observability \
      -am -DskipTests -Dmaven.test.skip=true -q

# ── Bước 2: package với -Pnative ─────────────────────────────────────────────
# Lifecycle đầy đủ khi -Pnative active:
#   compile → process-aot → compile (AOT sources) → package (fat jar)
# process-aot sinh AuthServiceApplication__ApplicationContextInitializer
# vào target/spring-aot/main/sources/ rồi compile lại vào target/classes/
# Fat jar cuối cùng chứa cả AOT classes + META-INF/spring/aot.factories
RUN mvn package -Pnative \
      -pl auth-service \
      -DskipTests -Dmaven.test.skip=true -q

# ── Bước 3: native:compile đọc classpath từ fat jar → chạy native-image ──────
# Tách riêng khỏi bước 2 vì native:compile cần fat jar đã hoàn chỉnh.
# Không dùng lifecycle fork (native:compile thay vì mvn package native:compile)
# để tránh process-aot chạy 2 lần.
RUN mvn native:compile -Pnative \
      -pl auth-service \
      -DskipTests -q

# -------------------------------------------------------------
# Runtime
# -------------------------------------------------------------
FROM container-registry.oracle.com/graalvm/jdk:25 AS runtime

WORKDIR /app

COPY --from=builder /build/auth-service/target/auth-service ./auth-service

#USER nonroot

EXPOSE 8081

ENTRYPOINT ["/app/auth-service"]

CMD ["-Dorg.jboss.logging.provider=slf4j"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD ["/bin/sh", "-c", "wget -qO- http://localhost:8081/actuator/health || exit 1"]
