package com.group19.teaching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequireRoleInterceptor requireRoleInterceptor;

    public WebMvcConfig(RequireRoleInterceptor requireRoleInterceptor) {
        this.requireRoleInterceptor = requireRoleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireRoleInterceptor).addPathPatterns("/api/**");
    }
}
