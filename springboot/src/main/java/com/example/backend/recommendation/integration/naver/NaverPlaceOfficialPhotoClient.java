package com.example.backend.recommendation.integration.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 지도(map.naver.com / m.place.naver.com)에서 "매장주(사장님)가 직접 등록한 공식
 * 사진(mediaSource == business)"만 조회하는 클라이언트.
 *
 * 네이버 지도는 카카오맵과 달리 순수 HTTP 요청으로는 접근이 막혀 있어(캡차 토큰 검증,
 * GraphQL introspection 차단, IP 레이트리밋), 실제 브라우저(Microsoft Edge)를 헤드리스로
 * 띄워서 페이지를 렌더링한 뒤 window.__APOLLO_STATE__에서 데이터를 추출한다. 검색 1건당
 * 브라우저 구동 비용이 들어 느리고 무겁기 때문에, 호출부(캐시 서비스)에서 결과를 캐싱해서
 * 재호출을 최소화해야 한다.
 *
 * 시스템에 Microsoft Edge 브라우저가 설치되어 있어야 하며, Selenium Manager가 필요한
 * msedgedriver를 자동으로 내려받는다(인터넷 접근 필요). 모든 실패는 조용히
 * Optional.empty()로 처리한다.
 */
@Component
public class NaverPlaceOfficialPhotoClient {

    private static final Logger log = LoggerFactory.getLogger(NaverPlaceOfficialPhotoClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0";

    private static final String SEARCH_URL_TEMPLATE = "https://map.naver.com/p/search/%s";
    private static final String PLACE_URL_TEMPLATE = "https://m.place.naver.com/place/%s/home";

    private static final Pattern PLACE_ID_PATTERN = Pattern.compile("/place/(\\d+)");
    private static final Pattern BUSINESS_INDEX_PATTERN = Pattern.compile("business_(\\d+)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 가게 이름으로 검색해 첫 번째 후보의 공식(매장주 등록) 대표 사진 URL을 반환한다.
     * 브라우저 구동/렌더링에 실패하거나 공식 사진이 없으면 Optional.empty().
     */
    public Optional<String> findOfficialPhotoUrl(String restaurantName) {
        if (!StringUtils.hasText(restaurantName)) {
            return Optional.empty();
        }

        WebDriver driver;
        try {
            driver = createDriver();
        } catch (Exception e) {
            log.warn("네이버 지도 조회용 Edge 브라우저 구동 실패: {}", e.getMessage());
            return Optional.empty();
        }

        try {
            String placeId = searchFirstPlaceId(driver, restaurantName);
            if (placeId == null) {
                return Optional.empty();
            }
            return getOfficialPhotoUrl(driver, placeId);
        } catch (Exception e) {
            log.debug("네이버 지도 공식 사진 조회 실패 (name={}): {}", restaurantName, e.getMessage());
            return Optional.empty();
        } finally {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }
    }

    private WebDriver createDriver() {
        EdgeOptions options = new EdgeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1280,900");
        options.addArguments("--lang=ko-KR");
        options.addArguments("user-agent=" + USER_AGENT);
        options.addArguments("--log-level=3");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-notifications");
        options.addArguments("--blink-settings=imagesEnabled=false");
        // 전체 리소스 로딩을 기다리지 않고 DOM 파싱이 끝나면 바로 제어권을 넘김
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // 지도 타일/사진 등 무거운 이미지 리소스를 차단해서 페이지 렌더링을 크게 단축
        Map<String, Object> prefs = Map.of(
                "profile.managed_default_content_settings.images", 2,
                "profile.default_content_setting_values.notifications", 2
        );
        options.setExperimentalOption("prefs", prefs);
        return new EdgeDriver(options);
    }

    private String searchFirstPlaceId(WebDriver driver, String query) {
        String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        driver.get(String.format(SEARCH_URL_TEMPLATE, encoded));

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        try {
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("searchIframe")));
        } catch (Exception e) {
            return null;
        }

        // 고정 sleep 대신, 목록 항목이 생기거나 상세 URL로 리다이렉트될 때까지만 짧게 폴링
        try {
            new WebDriverWait(driver, Duration.ofSeconds(8), Duration.ofMillis(100)).until(d ->
                    !d.findElements(By.cssSelector("#_pcmap_list_scroll_container li")).isEmpty()
                            || d.getCurrentUrl().contains("/place/"));
        } catch (Exception ignored) {
        }

        List<WebElement> items = driver.findElements(By.cssSelector("#_pcmap_list_scroll_container li"));
        if (!items.isEmpty()) {
            for (WebElement li : items) {
                List<WebElement> linkEls = li.findElements(By.cssSelector("a.CtW3e, a.k4f_J"));
                if (linkEls.isEmpty()) continue;
                // 검색 결과 중 첫 번째 항목을 클릭해서 place_id를 얻는다.
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", linkEls.get(0));
                try {
                    new WebDriverWait(driver, Duration.ofSeconds(8), Duration.ofMillis(100))
                            .until(d -> d.getCurrentUrl().contains("/place/"));
                } catch (Exception e) {
                    // 간헐적으로 클릭 이벤트가 늦게 반영되는 경우 한 번 더 클릭 시도
                    try {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", linkEls.get(0));
                        new WebDriverWait(driver, Duration.ofSeconds(8), Duration.ofMillis(100))
                                .until(d -> d.getCurrentUrl().contains("/place/"));
                    } catch (Exception e2) {
                        return null;
                    }
                }
                Matcher m = PLACE_ID_PATTERN.matcher(driver.getCurrentUrl());
                return m.find() ? m.group(1) : null;
            }
            return null;
        }

        // 검색어가 하나의 가게로 바로 매칭되어 상세 화면으로 진입한 경우
        Matcher m = PLACE_ID_PATTERN.matcher(driver.getCurrentUrl());
        return m.find() ? m.group(1) : null;
    }

    private Optional<String> getOfficialPhotoUrl(WebDriver driver, String placeId) {
        driver.switchTo().defaultContent();
        driver.get(String.format(PLACE_URL_TEMPLATE, placeId));

        String stateJson = null;
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            Object result = js.executeScript(
                    "return window.__APOLLO_STATE__ ? JSON.stringify(window.__APOLLO_STATE__) : null;");
            if (result != null) {
                stateJson = String.valueOf(result);
                break;
            }
            sleep(150);
        }
        if (stateJson == null) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(stateJson);
        } catch (Exception e) {
            return Optional.empty();
        }

        List<JsonNode> businessPhotos = new ArrayList<>();
        root.fields().forEachRemaining(entry -> {
            if (!entry.getKey().startsWith("PlaceDetailTopPhotoItem")) return;
            JsonNode value = entry.getValue();
            if (value.isObject() && "business".equals(value.path("mediaSource").asText(null))) {
                businessPhotos.add(value);
            }
        });

        if (businessPhotos.isEmpty()) {
            return Optional.empty();
        }

        // id가 business_1, business_2, ... 형태이므로 숫자 순으로 정렬해 대표(첫 번째) 사진을 고른다.
        businessPhotos.sort((a, b) -> Integer.compare(
                extractBusinessIndex(a.path("id").asText(null)),
                extractBusinessIndex(b.path("id").asText(null))
        ));

        JsonNode chosen = businessPhotos.get(0);
        String url = chosen.path("originalUrl").asText(null);
        if (!StringUtils.hasText(url)) {
            url = chosen.path("thumbnailUrl").asText(null);
        }
        return StringUtils.hasText(url) ? Optional.of(url) : Optional.empty();
    }

    private int extractBusinessIndex(String id) {
        Matcher m = BUSINESS_INDEX_PATTERN.matcher(id == null ? "" : id);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
