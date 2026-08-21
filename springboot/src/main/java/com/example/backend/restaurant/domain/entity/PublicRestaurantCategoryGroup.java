package com.example.backend.restaurant.domain.entity;

import java.util.Map;

/**
 * 공공데이터 소분류(category_small_code, 예: "치킨", "카페")를 검색/필터에 쓸 대분류
 * (한식/양식/중식/일식/아시안/카페·디저트/패스트푸드/분식/주점/구내식당·뷔페)로 묶는다.
 *
 * 공공데이터포털 API가 내려주는 중분류명(indsMclsNm)은 항상 비어 있어서 그대로 쓸 수 없어,
 * 소분류 코드를 기준으로 우리가 직접 정의한 그룹으로 매핑해 category_medium_name에 채워 넣는다.
 * {@link PublicRestaurant#update}에서 매번 다시 계산하므로, 공공데이터 재동기화가 일어나도
 * 이 그룹핑은 그대로 유지된다.
 */
public final class PublicRestaurantCategoryGroup {

    private static final Map<String, String> GROUP_BY_SMALL_CODE = Map.ofEntries(
            // 한식
            Map.entry("I20101", "한식"), Map.entry("I20107", "한식"), Map.entry("I20102", "한식"),
            Map.entry("I20105", "한식"), Map.entry("I20111", "한식"), Map.entry("I20112", "한식"),
            Map.entry("I20109", "한식"), Map.entry("I20110", "한식"), Map.entry("I20103", "한식"),
            Map.entry("I20108", "한식"), Map.entry("I20106", "한식"), Map.entry("I20199", "한식"),
            Map.entry("I20104", "한식"), Map.entry("I20113", "한식"),
            // 중식
            Map.entry("I20201", "중식"), Map.entry("I20202", "중식"),
            // 일식
            Map.entry("I20301", "일식"), Map.entry("I20303", "일식"),
            Map.entry("I20302", "일식"), Map.entry("I20399", "일식"),
            // 양식
            Map.entry("I20401", "양식"), Map.entry("I20402", "양식"),
            Map.entry("I20499", "양식"), Map.entry("I20403", "양식"),
            // 아시안 (분류 안된 외국식 음식점도 여기 포함)
            Map.entry("I20501", "아시안"), Map.entry("I20599", "아시안"), Map.entry("I20601", "아시안"),
            // 카페·디저트
            Map.entry("I21201", "카페·디저트"), Map.entry("I21001", "카페·디저트"),
            Map.entry("I21002", "카페·디저트"), Map.entry("I21008", "카페·디저트"),
            // 패스트푸드 (치킨/피자/버거/토스트·샌드위치·샐러드 + 기타 간이 음식점)
            Map.entry("I21006", "패스트푸드"), Map.entry("I21003", "패스트푸드"),
            Map.entry("I21004", "패스트푸드"), Map.entry("I21005", "패스트푸드"),
            Map.entry("I21099", "패스트푸드"),
            // 분식
            Map.entry("I21007", "분식"),
            // 주점
            Map.entry("I21104", "주점"), Map.entry("I21103", "주점"),
            Map.entry("I21101", "주점"), Map.entry("I21102", "주점"),
            // 구내식당·뷔페
            Map.entry("I20701", "구내식당·뷔페"), Map.entry("I20702", "구내식당·뷔페")
    );

    private PublicRestaurantCategoryGroup() {
    }

    /** 매핑에 없는 소분류 코드(향후 공공데이터에 새 코드가 추가된 경우 등)는 null을 반환한다. */
    public static String resolve(String categorySmallCode) {
        if (categorySmallCode == null) {
            return null;
        }
        return GROUP_BY_SMALL_CODE.get(categorySmallCode);
    }
}
