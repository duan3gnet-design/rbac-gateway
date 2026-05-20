package com.auth.service.config;

import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.DecoratingProxy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public class AuthNativeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // 1. Sửa lỗi mảng UUID cũ (nếu bạn muốn gộp vào đây)
        hints.reflection().registerType(java.util.UUID[].class);

        // 2. Sửa lỗi Proxy của Spring Security mới bị
        hints.proxies().registerJdkProxy(
                AuthenticationSuccessHandler.class,
                SpringProxy.class,
                Advised.class,
                DecoratingProxy.class
        );
    }
}
