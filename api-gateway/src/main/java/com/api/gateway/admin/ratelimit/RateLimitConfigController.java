package com.api.gateway.admin.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API cho Rate Limit Config management.
 *
 * <p>Tất cả endpoint yêu cầu ROLE_ADMIN (enforce tại JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/admin/rate-limits")
@RequiredArgsConstructor
public class RateLimitConfigController {

    private final RateLimitConfigAdminService adminService;

    @GetMapping
    public List<RateLimitConfigDtos.ConfigResponse> getAll() {
        return adminService.getAll();
    }

    @GetMapping("/default")
    public RateLimitConfigDtos.ConfigResponse getGlobalDefault() {
        return adminService.getGlobalDefault();
    }

    @GetMapping("/user/{username}")
    public RateLimitConfigDtos.ConfigResponse getByUsername(@PathVariable String username) {
        return adminService.getByUsername(username);
    }

    @GetMapping("/{id}")
    public RateLimitConfigDtos.ConfigResponse getById(@PathVariable Long id) {
        return adminService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RateLimitConfigDtos.ConfigResponse create(
            @RequestBody RateLimitConfigDtos.CreateRequest req) {
        return adminService.create(req);
    }

    @PutMapping("/{id}")
    public RateLimitConfigDtos.ConfigResponse update(
            @PathVariable Long id,
            @RequestBody RateLimitConfigDtos.UpdateRequest req) {
        return adminService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adminService.delete(id);
    }
}
