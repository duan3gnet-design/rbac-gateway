# =============================================================
# Dockerfile — resource-service  (GraalVM Native Image)
# Build từ ROOT của multi-module project (context = rbac-gateway/)
#   docker build -f resource-service/Dockerfile -t resource-service .
# =============================================================

FROM container-registry.oracle.com/graalvm/native-image:21 AS builder

ARG MAVEN_VERSION=3.9.15
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
COPY resource-service/src     ./resource-service/src

RUN mvn install \
      -pl common-observability \
      -am -DskipTests -Dmaven.test.skip=true -q

RUN mvn -Pnative native:compile \
      -pl resource-service \
      -DskipTests -Dmaven.test.skip=true -q

FROM gcr.io/distroless/static-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=builder /build/resource-service/target/resource-service ./resource-service

USER nonroot

EXPOSE 8082

ENTRYPOINT ["/app/resource-service"]
CMD ["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"]

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD ["/bin/sh", "-c", "wget -qO- http://localhost:8082/actuator/health || exit 1"]
