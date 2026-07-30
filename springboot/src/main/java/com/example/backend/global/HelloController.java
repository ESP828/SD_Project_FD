package com.example.backend.global;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 프론트-백엔드 연동 확인용 테스트 API.
 * 서버가 잘 떴는지 확인할 때 http://localhost:8081/api/hello 로 접속.
 * (SecurityConfig 에서 인증 없이 허용됨)
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "백엔드에서 온 메시지입니다!");
    }
}
