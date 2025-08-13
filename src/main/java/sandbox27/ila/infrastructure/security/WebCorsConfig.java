package sandbox27.ila.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig {

    @Value("${cors.origin}")
    String origin;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // alle Pfade
                        .allowedOriginPatterns(origin, "http://localhost:3000")
                        .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                        .allowedHeaders("*") // oder spezifisch: "Authorization", "Content-Type", ...
                        .exposedHeaders("*") // falls du Token oder andere Header zurückgeben willst
                        .allowCredentials(true); // erlaubt Cookies & Auth-Header
            }
        };
    }
}
