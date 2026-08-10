package com.example.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Path presetImageDir;

    public WebMvcConfig(@Value("${app.upload.preset-image-dir}") String presetImageDir) {
        this.presetImageDir = Paths.get(presetImageDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/preset-images/**")
                .addResourceLocations("file:" + presetImageDir + "/");
    }
}
