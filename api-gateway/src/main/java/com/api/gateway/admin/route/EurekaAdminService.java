package com.api.gateway.admin.route;

import com.netflix.discovery.EurekaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /**
     * Trả về danh sách tất cả service instances đang UP trong Eureka registry.
     * Mỗi instance được map thành {@link AdminDtos.EurekaServiceResponse}.
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

    /**
     * homePageUrl Eureka đôi khi là empty string hoặc chỉ có "/".
     * Fallback về http://ipAddr:port để đảm bảo URI luôn hợp lệ.
     */
    private String normalizeUrl(String homePageUrl, String ipAddr, int port) {
        if (homePageUrl != null && !homePageUrl.isBlank() && !homePageUrl.equals("/")) {
            return homePageUrl.replaceAll("/$", "");
        }
        return "http://" + ipAddr + ":" + port;
    }
}
