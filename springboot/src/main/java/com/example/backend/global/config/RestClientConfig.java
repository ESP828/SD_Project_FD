package com.example.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                // JDK HttpClient의 기본값(HTTP_2)은 평문 http:// 상대로도 h2c 업그레이드를 시도한다.
                // uvicorn(FastAPI, springboot/ai)은 h2c를 지원하지 않아서 이 업그레이드 요청이
                // 본문이 유실된 요청으로 처리되고, FastAPI는 422("Field required")를 돌려준다 -
                // 그러면 SentimentAnalysisClient 호출부가 이걸 RuntimeException으로 받아 조용히
                // null로 넘겨서, 겉으로는 "감성분석 서비스 호출 실패"처럼 보였다. 사내에서 이
                // RestClient로 부르는 대상(FastAPI, 카카오/NTS 등)은 전부 HTTP/1.1이면 충분하므로
                // 아예 HTTP/1.1로 고정해서 업그레이드 시도 자체를 없앤다.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory);
    }
}

