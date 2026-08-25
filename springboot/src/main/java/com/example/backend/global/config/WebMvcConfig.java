package com.example.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

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

    /**
     * /pages/xxx/index.html 같은 정적 파일 경로 대신 깨끗한 URL로 접근할 수 있도록
     * 서버에서 forward 처리한다. 실제 정적 리소스는 그대로 /pages/** 밑에 있고,
     * 여기 등록된 경로는 그 파일로 내부 포워드만 한다(브라우저 주소는 그대로 유지됨).
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        Map<String, String> routes = Map.ofEntries(
                Map.entry("/admin", "/pages/admin/index.html"),
                Map.entry("/admin/accounts", "/pages/admin/accounts.html"),
                Map.entry("/admin/business-applications", "/pages/admin/business-applications.html"),
                Map.entry("/admin/community", "/pages/admin/community.html"),
                Map.entry("/admin/presets", "/pages/admin/presets.html"),
                Map.entry("/admin/restaurants", "/pages/admin/restaurants.html"),
                Map.entry("/auth/login", "/pages/auth/login.html"),
                Map.entry("/auth/signup", "/pages/auth/signup.html"),
                Map.entry("/auth/find-id", "/pages/auth/find-id.html"),
                Map.entry("/auth/find-password", "/pages/auth/find-password.html"),
                Map.entry("/auth/oauth-callback", "/pages/auth/oauth-callback.html"),
                Map.entry("/board", "/pages/board/index.html"),
                Map.entry("/board/detail", "/pages/board/detail.html"),
                Map.entry("/board/write", "/pages/board/write.html"),
                Map.entry("/business", "/pages/business/index.html"),
                Map.entry("/business/detail", "/pages/business/detail.html"),
                Map.entry("/business/restaurant-form", "/pages/business/restaurant-form.html"),
                Map.entry("/game", "/pages/game/index.html"),
                Map.entry("/map", "/pages/map/index.html"),
                Map.entry("/mypage", "/pages/mypage/index.html"),
                Map.entry("/mypage/detail", "/pages/mypage/detail.html"),
                Map.entry("/mypage/change-password", "/pages/mypage/change-password.html"),
                Map.entry("/presset", "/pages/presset/index.html"),
                Map.entry("/presset/detail", "/pages/presset/detail.html"),
                Map.entry("/presset/map", "/pages/presset/map.html"),
                Map.entry("/presset/register", "/pages/presset/register.html"),
                Map.entry("/recommendation", "/pages/recommendation/index.html"),
                Map.entry("/restaurant", "/pages/restaurant/index.html"),
                Map.entry("/restaurant/detail", "/pages/restaurant/detail.html"),
                Map.entry("/search", "/pages/search/index.html")
        );
        routes.forEach((cleanPath, target) ->
                registry.addViewController(cleanPath).setViewName("forward:" + target));
    }
}
