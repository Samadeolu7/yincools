package com.java.yincools.web;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Tomcat doesn't know the .webmanifest extension by default -- without this it's served as application/octet-stream, which some browsers treat as non-installable. */
@Configuration
public class WebServerConfig {

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
