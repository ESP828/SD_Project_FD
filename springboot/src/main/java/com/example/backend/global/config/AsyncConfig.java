package com.example.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 외부 매장 이미지 조회(네이버 지도 - 조회 1건당 헤드리스 Edge 브라우저를 새로 띄움)를
 * 비동기로 처리하기 위한 전용 스레드풀.
 * 요청 처리 스레드(Tomcat worker)와 분리해서 웹 요청 처리에 영향을 주지 않게 하면서도,
 * 브라우저 구동이 무거운 작업이라 동시 실행 수를 작게 제한한다(코어 2 / 최대 2).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "imageFetchExecutor")
    public Executor imageFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("img-fetch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
