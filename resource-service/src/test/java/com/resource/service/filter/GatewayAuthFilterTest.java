package com.resource.service.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayAuthFilter Unit Tests")
class GatewayAuthFilterTest {

    @InjectMocks
    private GatewayAuthFilter gatewayAuthFilter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // Happy path
    // =========================================================

    @Test
    @DisplayName("Set Authentication khi có đủ cả hai header")
    void shouldSetAuthentication_whenBothHeadersPresent() throws Exception {
        request.addHeader("X-User-Name",  "alice");
        request.addHeader("X-User-Roles", "ROLE_USER");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Parse nhiều role phân cách bởi dấu phẩy")
    void shouldParseMultipleRoles() throws Exception {
        request.addHeader("X-User-Name",  "bob");
        request.addHeader("X-User-Roles", "ROLE_USER,ROLE_ADMIN");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Trim khoảng trắng trong từng role")
    void shouldTrimWhitespaceInRoles() throws Exception {
        request.addHeader("X-User-Name",  "carol");
        request.addHeader("X-User-Roles", " ROLE_USER , ROLE_ADMIN ");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    // =========================================================
    // Missing headers
    // =========================================================

    @Test
    @DisplayName("Không set Authentication khi thiếu X-User-Name")
    void shouldSkip_whenUserNameMissing() throws Exception {
        request.addHeader("X-User-Roles", "ROLE_USER");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Không set Authentication khi thiếu X-User-Roles")
    void shouldSkip_whenRolesMissing() throws Exception {
        request.addHeader("X-User-Name", "dave");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Không set Authentication khi cả hai header đều thiếu")
    void shouldSkip_whenBothHeadersMissing() throws Exception {
        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // =========================================================
    // Filter chain always called
    // =========================================================

    @Test
    @DisplayName("Luôn gọi filterChain.doFilter dù thiếu header")
    void shouldAlwaysCallFilterChain() throws Exception {
        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    // =========================================================
    // Token properties
    // =========================================================

    @Test
    @DisplayName("Credentials trong token phải là null")
    void credentialsShouldBeNull() throws Exception {
        request.addHeader("X-User-Name",  "eve");
        request.addHeader("X-User-Roles", "ROLE_USER");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getCredentials()).isNull();
    }

    @Test
    @DisplayName("isAuthenticated() == true khi đã có authorities")
    void shouldBeAuthenticated_whenRoleProvided() throws Exception {
        request.addHeader("X-User-Name",  "frank");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");

        gatewayAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }
}
