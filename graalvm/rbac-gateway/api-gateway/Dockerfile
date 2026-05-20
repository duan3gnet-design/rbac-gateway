# =============================================================
# Dockerfile — api-gateway  (GraalVM Native Image)
# Build từ ROOT của multi-module project (context = rbac-gateway/)
#   docker build -f api-gateway/Dockerfile -t api-gateway .
# =============================================================

FROM container-registry.oracle.com/graalvm/native-image:21 AS builder

ARG MAVEN_VERSION=3.9.16
RUN microdnf install -y wget tar gzip && \
    wget -q "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
         -O /tmp/maven.tar.gz && \
    tar -xzf /tmp/maven.tar.gz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn && \
    rm /tmp/maven.tar.gz

WORKDIR /build

# ── Layer 1: tất cả pom.xml — cache deps khi chỉ thay source ────────────────
COPY pom.xml ./
COPY api-gateway/pom.xml           ./api-gateway/
COPY auth-service/pom.xml          ./auth-service/
COPY resource-service/pom.xml      ./resource-service/
COPY migration/pom.xml             ./migration/
COPY common-observability/pom.xml  ./common-observability/
COPY eureka-server/pom.xml         ./eureka-server/

# ── Layer 2: download tất cả deps bên ngoài ──────────────────────────────────
# Dùng go-offline cho toàn bộ reactor trước, không filter -pl
# để tránh missing transitive deps của inter-module artifacts.
RUN mvn dependency:go-offline -q || true

# ── Layer 3: copy source theo đúng thứ tự dependency ─────────────────────────
# common-observability không có src phức tạp, copy trước
COPY common-observability/src      ./common-observability/src
COPY auth-service/src              ./auth-service/src
COPY api-gateway/src               ./api-gateway/src
COPY resource-service/src          ./resource-service/src
COPY migration/src                 ./migration/src

# ── Build ─────────────────────────────────────────────────────────────────────
#
# Thứ tự build 3 bước — quan trọng, không được gộp:
#
# 1. install common-observability + auth-service vào ~/.m2
#    (api-gateway depend cả hai)
# 2. package api-gateway:
#    spring-boot:repackage → fat jar
#    spring-boot:process-aot (bind vào prepare-package) →
#      sinh target/spring-aot/main/sources/**/__ApplicationContextInitializer.java
#      compile lại vào target/classes
#      ghi META-INF/spring/aot.factories
# 3. native:compile: đọc classpath từ fat jar (đã có AOT classes) → native-image
#
RUN mvn install \
      -pl common-observability,auth-service \
      -am -DskipTests -Dmaven.test.skip=true -q

RUN mvn -Pnative native:compile \
      -pl api-gateway \
      -DskipTests -Dmaven.test.skip=true -q

# -------------------------------------------------------------
# Runtime — distroless/static (nonroot, ~2MB, không có shell)
# Binary fully static → chạy được mà không cần glibc/musl
# -------------------------------------------------------------
FROM gcr.io/distroless/static-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=builder /build/api-gateway/target/api-gateway ./api-gateway

USER nonroot

EXPOSE 8080

ENTRYPOINT ["/app/api-gateway"]
CMD ["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
  CMD ["/bin/sh", "-c", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
