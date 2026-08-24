package com.example.backend.recommendation.service;

import com.example.backend.recommendation.integration.naver.NaverPlaceOfficialPhotoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 네이버 지도 매장주 공식 사진(NaverPlaceOfficialPhotoClient)을 가게 이름으로 조회해서
 * 인메모리에 1시간 캐시해두는 서비스.
 *
 * - 맛집 추천 화면에 진입할 때마다 목록에 나온 가게들을 전부 조회하게 되므로, 같은 PC에서
 *   반복 방문 시 매번 네이버 지도(헤드리스 브라우저)를 호출하지 않도록 결과(성공/실패 모두)를
 *   캐시한다.
 * - 캐시는 서버 프로세스 메모리에 저장되며 TTL은 1시간이다. TTL이 지나면 다음 조회 시
 *   자동으로 다시 네이버 지도에서 가져온다.
 * - 조회는 전용 스레드풀(imageFetchExecutor)에서 비동기로 수행된다. 조회 1건마다 헤드리스
 *   Edge 브라우저를 새로 띄우는 무거운 작업이라, 스레드풀 크기가 곧 동시 브라우저 구동 수의
 *   상한이 된다(AsyncConfig에서 별도로 작게 제한한다).
 */
@Service
public class RestaurantOfficialImageCacheService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantOfficialImageCacheService.class);

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final NaverPlaceOfficialPhotoClient naverPlaceOfficialPhotoClient;
    private final Executor imageFetchExecutor;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RestaurantOfficialImageCacheService(
            NaverPlaceOfficialPhotoClient naverPlaceOfficialPhotoClient,
            @Qualifier("imageFetchExecutor") Executor imageFetchExecutor
    ) {
        this.naverPlaceOfficialPhotoClient = naverPlaceOfficialPhotoClient;
        this.imageFetchExecutor = imageFetchExecutor;
    }

    /**
     * 가게 이름 하나에 대한 대표 이미지 URL을 비동기로 조회한다. 캐시에 유효한 값이 있으면
     * 그 값을 즉시 담은 완료된 Future를 반환하고, 없거나 만료됐으면 전용 스레드풀에서
     * 네이버 지도를 조회한 뒤 캐시에 저장한다. 이미지가 없는 가게도 "없음"으로 캐시해서
     * 매번 재조회(브라우저 재구동)하지 않는다.
     */
    public CompletableFuture<String> getImageUrlAsync(String restaurantName) {
        if (restaurantName == null || restaurantName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String key = restaurantName.trim();
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.imageUrl());
        }

        return CompletableFuture.supplyAsync(() -> {
            String imageUrl = naverPlaceOfficialPhotoClient.findOfficialPhotoUrl(key).orElse(null);
            cache.put(key, new CacheEntry(imageUrl, Instant.now().plus(CACHE_TTL)));
            return imageUrl;
        }, imageFetchExecutor).exceptionally(e -> {
            log.warn("가게 공식 이미지 비동기 조회 실패 (name={}): {}", key, e.getMessage());
            return null;
        });
    }

    /**
     * 여러 가게 이름을 한 번에 병렬로 조회해서 이름 -> 이미지URL(없으면 null) 맵으로 반환한다.
     * 추천/랭킹 목록 페이지가 열릴 때마다 화면에 나온 가게 전체를 한꺼번에 조회하기 위해 쓴다.
     */
    public CompletableFuture<Map<String, String>> getImageUrlsAsync(List<String> restaurantNames) {
        List<String> distinctNames = restaurantNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        // Map.entry()/Map.of()는 값이 null이면 NPE를 던지므로(이미지가 없는 가게가 흔함),
        // 이름 -> Future<이미지URL> 매핑을 별도 Map으로 들고 있다가 완료 후 직접 채운다.
        Map<String, CompletableFuture<String>> nameToFuture = new ConcurrentHashMap<>();
        for (String name : distinctNames) {
            nameToFuture.put(name, getImageUrlAsync(name));
        }

        return CompletableFuture.allOf(nameToFuture.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, String> result = new ConcurrentHashMap<>();
                    nameToFuture.forEach((name, future) -> {
                        String url = future.join();
                        if (url != null) {
                            result.put(name, url);
                        }
                    });
                    return result;
                });
    }

    private record CacheEntry(String imageUrl, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
