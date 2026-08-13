package com.example.backend.recommendation.ai;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
public class DocumentBuilder {


    public String build(PublicRestaurant restaurant) {

        Set<String> words = new LinkedHashSet<>();


        // 기본 정보
        add(words, restaurant.getName());

        add(words, restaurant.getCategoryLargeName());
        add(words, restaurant.getCategorySmallName());

        add(words, restaurant.getSidoName());
        add(words, restaurant.getSigunguName());

        add(words, restaurant.getRoadAddress());


        // 카테고리 기반 키워드
        addCategoryKeywords(
                words,
                restaurant
        );


        // 지역 기반 키워드
        appendRegionKeywords(
                words,
                restaurant
        );


        return String.join(
                " ",
                words
        );
    }


    /**
     * 문자열 추가
     * null / 공백 제거
     */
    private void add(
            Set<String> words,
            String value
    ){

        if(value == null || value.isBlank()){
            return;
        }


        words.add(value.trim());

    }



    /**
     * 카테고리 기반 확장 키워드
     */
    private void addCategoryKeywords(
        Set<String> words,
        PublicRestaurant restaurant
){

    List<String> categories = List.of(
            restaurant.getCategoryLargeName(),
            restaurant.getCategorySmallName()
    );


    categories.forEach(category -> {


        if(category == null){
            return;
        }


        CATEGORY_KEYWORDS.forEach(
                (key, keywords) -> {


                    if(category.contains(key)){


                        keywords.forEach(
                                keyword ->
                                        add(words,keyword)
                        );

                    }

                }
        );

    });

}
private void appendRegionKeywords(
        Set<String> words,
        PublicRestaurant restaurant
){

    add(words, restaurant.getSidoName());
    add(words, restaurant.getSigunguName());


    String address = restaurant.getRoadAddress();


    if(address == null || address.isBlank()){
        return;
    }


    extractAddressKeyword(
            words,
            address
    );

}
private void extractAddressKeyword(
        Set<String> words,
        String address
){

    String[] tokens =
            address.split(" ");


    for(String token : tokens){


        if(token.length() < 2){
            continue;
        }


        List<String> normalized =
                normalizeAddress(token);


        normalized.forEach(
                value ->
                        add(words,value)
        );

    }

}
private List<String> normalizeAddress(
        String value
){

    List<String> result = new ArrayList<>();


    result.add(value);


    String temp = value;


    String[] suffixes = {

            "특별시",
            "광역시",
            "도",
            "시",
            "군",
            "구",
            "동",
            "읍",
            "면",
            "가"

    };


    for(String suffix : suffixes){


        if(temp.endsWith(suffix)
                && temp.length() > suffix.length()+1){


            temp =
                    temp.substring(
                            0,
                            temp.length()-suffix.length()
                    );


            result.add(temp);

        }

    }


    return result;

}


    /**
     * 카테고리 → 검색 키워드 변환
     */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS =
            Map.of(


                    "이탈리안",
                    List.of(
                            "파스타",
                            "피자",
                            "와인",
                            "데이트",
                            "분위기"
                    ),


                    "양식",
                    List.of(
                            "파스타",
                            "스테이크",
                            "데이트",
                            "레스토랑"
                    ),


                    "카페",
                    List.of(
                            "커피",
                            "디저트",
                            "브런치",
                            "조용한",
                            "공부"
                    ),


                    "초밥",
                    List.of(
                            "일식",
                            "오마카세",
                            "데이트",
                            "신선한"
                    ),


                    "한식",
                    List.of(
                            "식사",
                            "점심",
                            "저녁",
                            "가족",
                            "한식"
                    )

            );

}
