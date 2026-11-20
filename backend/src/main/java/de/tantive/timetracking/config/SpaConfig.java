package de.tantive.timetracking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Serves the built Angular app from the same server (local installation) and falls back to index.html for client routes. */
@Configuration
public class SpaConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override protected Resource getResource(String path, Resource location) throws java.io.IOException {
                        if (path.startsWith("api/")) return null;
                        Resource r = location.createRelative(path);
                        return r.exists() && r.isReadable() ? r : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
