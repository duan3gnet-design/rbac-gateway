package com.api.gateway.admin.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.EurekaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Đọc danh sách service instances từ Eureka registry nội bộ.
 * Dùng {@link EurekaClient} (đã inject sẵn qua spring-cloud-starter-netflix-eureka-client)
 * để tránh HTTP round-trip sang Eureka Server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EurekaAdminService {

    private final EurekaClient eurekaClient;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    // ─── Services ────────────────────────────────────────────────────────────

    /**
     * Trả về danh sách tất cả service instances đang UP trong Eureka registry.
     */
    public List<AdminDtos.EurekaServiceResponse> getRegisteredServices() {
        try {
            return eurekaClient.getApplications()
                    .getRegisteredApplications()
                    .stream()
                    .flatMap(app -> app.getInstances().stream()
                            .filter(inst -> inst.getStatus() ==
                                    com.netflix.appinfo.InstanceInfo.InstanceStatus.UP)
                            .map(inst -> new AdminDtos.EurekaServiceResponse(
                                    app.getName(),
                                    inst.getInstanceId(),
                                    normalizeUrl(inst.getHomePageUrl(), inst.getIPAddr(), inst.getPort()),
                                    inst.getIPAddr(),
                                    inst.getPort()
                            ))
                    )
                    .toList();
        } catch (Exception e) {
            log.warn("[EurekaAdminService] Không thể lấy danh sách services: {}", e.getMessage());
            return List.of();
        }
    }

    // ─── Mappings ────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách endpoints từ /actuator/mappings của một service instance.
     *
     * <p>Tìm instance trong Eureka theo serviceId (case-insensitive), sau đó
     * call {@code {homePageUrl}/actuator/mappings} và parse cấu trúc JSON của
     * Spring Boot Actuator trả về.</p>
     *
     * <p>Chỉ lấy các mapping thuộc {@code dispatcherServlet} (Spring MVC)
     * hoặc {@code dispatcherHandler} (Spring WebFlux), bỏ qua actuator,
     * error, và các path nội bộ.</p>
     *
     * @param serviceId tên service trong Eureka (case-insensitive)
     * @return danh sách {@link AdminDtos.ServiceMappingResponse}
     */
    public List<AdminDtos.ServiceMappingResponse> getServiceMappings(String serviceId) {
        String baseUrl = resolveBaseUrl(serviceId);
        if (baseUrl == null) {
            log.warn("[EurekaAdminService] Không tìm thấy instance UP cho service: {}", serviceId);
            return List.of();
        }

        try {
            RestClient client = restClientBuilder.build();
            String json = client.get()
                    .uri(baseUrl + "/actuator/mappings")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            return parseMappings(json);
        } catch (Exception e) {
            log.warn("[EurekaAdminService] Không thể lấy mappings từ {}: {}", serviceId, e.getMessage());
            return List.of();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Tìm homePageUrl của instance UP đầu tiên khớp serviceId.
     */
    private String resolveBaseUrl(String serviceId) {
        return eurekaClient.getApplications()
                .getRegisteredApplications()
                .stream()
                .filter(app -> app.getName().equalsIgnoreCase(serviceId))
                .findFirst()
                .flatMap(app -> app.getInstances().stream()
                        .filter(inst -> inst.getStatus() ==
                                com.netflix.appinfo.InstanceInfo.InstanceStatus.UP)
                        .findFirst()
                )
                .map(inst -> normalizeUrl(inst.getHomePageUrl(), inst.getIPAddr(), inst.getPort()))
                .orElse(null);
    }

    /**
     * Parse cấu trúc JSON của Spring Boot Actuator /actuator/mappings.
     * <p>
     * Cấu trúc:
     * <pre>
     * {
     *   "contexts": {
     *     "application": {
     *       "mappings": {
     *         "dispatcherServlets": {                  // Spring MVC
     *           "dispatcherServlet": [ { "handler": {...}, "predicate": "..." } ]
     *         },
     *         "dispatcherHandlers": {                  // Spring WebFlux
     *           "webHandler": [ { "handler": {...}, "predicate": "..." } ]
     *         }
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * Từ mỗi entry, trích xuất patternValue và methods.
     */
    private List<AdminDtos.ServiceMappingResponse> parseMappings(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        List<AdminDtos.ServiceMappingResponse> result = new ArrayList<>();

        // Duyệt qua tất cả contexts (thường chỉ có "application")
        JsonNode contexts = root.path("contexts");
        for (JsonNode ctx : contexts) {
            JsonNode mappings = ctx.path("mappings");

            // Spring MVC: dispatcherServlets
            JsonNode mvcNode = mappings.path("dispatcherServlets");
            if (!mvcNode.isMissingNode()) {
                for (JsonNode entries : mvcNode) {
                    extractEntries(entries, result);
                }
            }

            // Spring WebFlux: dispatcherHandlers
            JsonNode fluxNode = mappings.path("dispatcherHandlers");
            if (!fluxNode.isMissingNode()) {
                for (JsonNode entries : fluxNode) {
                    extractEntries(entries, result);
                }
            }
        }

        return result.stream()
                .filter(m -> !m.path().startsWith("/actuator")
                          && !m.path().startsWith("/error")
                          && !m.path().isBlank())
                .distinct()
                .sorted(Comparator.comparing(AdminDtos.ServiceMappingResponse::path))
                .toList();
    }

    /**
     * Trích xuất path + methods từ mảng mapping entries.
     * Mỗi entry có dạng:
     * <pre>
     * {
     *   "predicate": "{GET /api/auth/login}",       // MVC đơn giản
     *   "details": {
     *     "requestMappingConditions": {
     *       "patterns": ["/api/auth/login"],
     *       "methods": ["GET"]
     *     }
     *   }
     * }
     * </pre>
     */
    private void extractEntries(JsonNode entries, List<AdminDtos.ServiceMappingResponse> result) {
        if (!entries.isArray()) return;
        for (JsonNode entry : entries) {
            // Ưu tiên lấy từ requestMappingConditions (chính xác nhất)
            JsonNode conditions = entry.path("details").path("requestMappingConditions");
            if (!conditions.isMissingNode()) {
                List<String> paths   = toStringList(conditions.path("patterns"));
                List<String> methods = toStringList(conditions.path("methods"));
                for (String path : paths) {
                    if (!path.isBlank()) {
                        result.add(new AdminDtos.ServiceMappingResponse(path, methods));
                    }
                }
                continue;
            }

            // Fallback: parse chuỗi predicate, ví dụ "{GET /api/auth/login}"
            String predicate = entry.path("predicate").asText("");
            AdminDtos.ServiceMappingResponse parsed = parsePredicate(predicate);
            if (parsed != null) result.add(parsed);
        }
    }

    /**
     * Parse chuỗi predicate dạng "{GET /api/auth/login}" hoặc "/api/auth/login".
     */
    private AdminDtos.ServiceMappingResponse parsePredicate(String predicate) {
        if (predicate.isBlank()) return null;
        // Dạng: {GET /api/...} hoặc {[GET, POST] /api/...}
        String clean = predicate.replaceAll("[{}\\[\\]]", "").trim();
        String[] parts = clean.split("\\s+", 2);
        if (parts.length == 2) {
            String methodPart = parts[0];
            String path = parts[1].trim();
            if (path.isBlank()) return null;
            List<String> methods = List.of(methodPart.split(",\\s*"));
            return new AdminDtos.ServiceMappingResponse(path, methods);
        }
        if (parts.length == 1 && parts[0].startsWith("/")) {
            return new AdminDtos.ServiceMappingResponse(parts[0], List.of());
        }
        return null;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode el : node) list.add(el.asText());
        }
        return list;
    }

    private String normalizeUrl(String homePageUrl, String ipAddr, int port) {
        if (homePageUrl != null && !homePageUrl.isBlank() && !homePageUrl.equals("/")) {
            return homePageUrl.replaceAll("/$", "");
        }
        return "http://" + ipAddr + ":" + port;
    }
}
