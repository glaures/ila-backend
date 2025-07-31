package sandbox27.ila.infrastructure.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class JwtFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilter(JwtValidator jwtValidator) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>();

        JwtFilter jwtFilter = new JwtFilter(jwtValidator);
        registration.setFilter(jwtFilter);

        // Filter nur auf /api/*
        registration.addUrlPatterns("/*");

        // Name und Reihenfolge des Filters (optional)
        registration.setName("JwtFilter");
        registration.setOrder(1);

        return registration;
    }
}
