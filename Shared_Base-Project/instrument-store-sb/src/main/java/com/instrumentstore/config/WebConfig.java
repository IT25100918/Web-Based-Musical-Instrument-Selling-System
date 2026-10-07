package com.instrumentstore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Maps the URL path /uploads/** to a real "uploads" folder next to the project
 * (not inside src/main/resources), so uploaded product images survive
 * even though they weren't part of the original build.
 *
 * Resolved to an ABSOLUTE path (based on the JVM's working directory at
 * startup) rather than a bare relative "uploads/" - a relative location only
 * resolves correctly if the app happens to be launched from the project
 * root. If it's launched from anywhere else (double-clicking the jar,
 * a different terminal cwd, a scheduled task, etc.) the relative path
 * points at the wrong folder and every uploaded image 404s even though
 * ProductCatalogShoppingCartController saved the file successfully.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AccountSessionInterceptor accountSessionInterceptor;

    public WebConfig(AccountSessionInterceptor accountSessionInterceptor) {
        this.accountSessionInterceptor = accountSessionInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        // toUri() gives a valid file: URL on every OS (a bare "file:" + "C:\\..." is not one),
        // and the trailing slash is mandatory or Spring treats the location as a single file.
        String location = uploadDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

    // 1) Every page: re-check the logged-in account (suspended? password changed? role changed?)
    // 2) Every /admin/** URL requires a logged-in staff session (see AdminAuthInterceptor).
    // Interceptors run in the order they are registered, so the account check comes first.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accountSessionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/images/**", "/uploads/**", "/error", "/logout");
        registry.addInterceptor(new AdminAuthInterceptor())
                .addPathPatterns("/admin/**");
    }
}
