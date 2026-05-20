package com.auth.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(AuthNativeHints.class)
public class NativeConfig {
    // Class này kích hoạt các cấu hình native tối ưu phía trên
}
