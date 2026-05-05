package com.api.gateway.config;

import com.api.gateway.filter.JwtAuthenticationFilter;
import com.api.gateway.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Đăng ký JwtAuthenticationFilter làm HandlerInterceptor với priority cao nhất.
 *
 * <p>Interceptor chạy trước mọi handler (controller / gateway proxy),
 * tương đương với order = -100 của GlobalFilter trong WebFlux.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationFilter)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE);
        registry.addInterceptor(rateLimitFilter)
                .addPathPatterns("/**");
    }
}
