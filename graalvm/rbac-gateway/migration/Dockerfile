# =============================================================
# Dockerfile — migration (Flyway)  (GraalVM Native Image)
# Build từ ROOT của multi-module project (context = rbac-gateway/)
#   docker build -f migration/Dockerfile -t migration .
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

COPY pom.xml ./
COPY api-gateway/pom.xml           ./api-gateway/
COPY auth-service/pom.xml          ./auth-service/
COPY resource-service/pom.xml      ./resource-service/
COPY migration/pom.xml             ./migration/
COPY common-observability/pom.xml  ./common-observability/
COPY eureka-server/pom.xml         ./eureka-server/

# migration không phụ thuộc common-observability hay các module khác
# nên dependency:go-offline đủ, không cần install thêm
RUN mvn dependency:go-offline -pl migration -am -q || true

COPY migration/src ./migration/src

RUN mvn -Pnative native:compile \
      -pl migration \
      -am -DskipTests -Dmaven.test.skip=true -q

FROM gcr.io/distroless/static-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=builder /build/migration/target/migration ./migration

USER nonroot

ENTRYPOINT ["/app/migration"]
