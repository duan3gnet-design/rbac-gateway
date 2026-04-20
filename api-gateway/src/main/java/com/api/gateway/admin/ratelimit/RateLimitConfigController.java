package com.api.gateway.admin.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin REST API cho Rate Limit Config management.
 *
 * <p>Tất cả endpoint yêu cầu ROLE_ADMIN (enforce tại JwtAuthenticationFilter).
 *
 * <pre>
 * GET    /api/admin/rate-limits                  — lấy tất cả configs
 * GET    /api/admin/rate-limits/default           — lấy global default
 * GET    /api/admin/rate-limits/user/{username}   — lấy override của user cụ thể
 * GET    /api/admin/rate-limits/{id}              — lấy config theo id
 * POST   /api/admin/rate-limits                   — tạo per-user override (hoặc update global default nếu username null)
 * PUT    /api/admin/rate-limits/{id}              — update config
 * DELETE /api/admin/rate-limits/{id}              — xóa per-user override
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/rate-limits")
@RequiredArgsConstructor
public class RateLimitConfigController {

    private final RateLimitConfigAdminService adminService;

    @GetMapping
    public Flux<RateLimitConfigDtos.ConfigResponse> getAll() {
        return adminService.getAll();
    }

    @GetMapping("/default")
    public Mono<RateLimitConfigDtos.ConfigResponse> getGlobalDefault() {
        return adminService.getGlobalDefault();
    }

    @GetMapping("/user/{username}")
    public Mono<RateLimitConfigDtos.ConfigResponse> getByUsername(@PathVariable String username) {
        return adminService.getByUsername(username);
    }

    @GetMapping("/{id}")
    public Mono<RateLimitConfigDtos.ConfigResponse> getById(@PathVariable Long id) {
        return adminService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RateLimitConfigDtos.ConfigResponse> create(
            @RequestBody RateLimitConfigDtos.CreateRequest req) {
        return adminService.create(req);
    }

    @PutMapping("/{id}")
    public Mono<RateLimitConfigDtos.ConfigResponse> update(
            @PathVariable Long id,
            @RequestBody RateLimitConfigDtos.UpdateRequest req) {
        return adminService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return adminService.delete(id);
    }
}
