package com.example.backend.recommendation.ai;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentV2Builder {

    public static final int DOCUMENT_VERSION = 2;

    private final DocumentBuilder documentBuilder;

    public DocumentV2Builder(DocumentBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }

    public String build(PublicRestaurant restaurant, PublicRestaurantEvidence evidence) {
        List<String> parts = new ArrayList<>();
        append(parts, documentBuilder.build(restaurant));
        if (evidence == null) {
            return String.join(" ", parts);
        }
        appendWhenTrue(parts, evidence.parkingAvailable(), "주차 가능");
        appendWhenTrue(parts, evidence.wifiAvailable(), "와이파이 제공");
        appendWhenTrue(parts, evidence.playroomAvailable(), "놀이방 제공");
        appendWhenTrue(parts, evidence.multilingualMenuAvailable(), "다국어 메뉴판 제공");
        appendWhenTrue(parts, evidence.deliveryAvailable(), "배달 가능");
        appendWhenTrue(parts, evidence.smartOrderAvailable(), "스마트오더 가능");
        appendLabeled(parts, "대표메뉴", evidence.representativeMenu());
        appendLabeled(parts, "검증메뉴", evidence.verifiedMenuNames());
        if (evidence.typicalMenuPrice() != null) {
            String priceEvidence = "공식 메뉴 대표가격 " + evidence.typicalMenuPrice() + "원";
            if (evidence.minimumMenuPrice() != null) {
                priceEvidence += " 최저가격 " + evidence.minimumMenuPrice() + "원";
            }
            if (evidence.pricedMenuCount() > 0) {
                priceEvidence += " 가격표본 " + evidence.pricedMenuCount() + "개";
            }
            append(parts, priceEvidence);
        }
        appendWhenTrue(parts, evidence.veganLabeledMenuAvailable(), "비건 표기 메뉴 있음");
        appendWhenTrue(parts, evidence.vegetarianLabeledMenuAvailable(), "채식 표기 메뉴 있음");
        appendWhenTrue(parts, evidence.glutenFreeLabeledMenuAvailable(), "글루텐프리 표기 메뉴 있음");
        appendLabeled(parts, "해시태그", evidence.hashtags());
        appendLabeled(parts, "영업시간", evidence.openingHours());
        appendLabeled(parts, "휴무일", evidence.closedDays());
        appendLabeled(parts, "예약정보", evidence.reservationInfo());
        appendLabeled(parts, "면적정보", evidence.areaInfo());
        appendLabeled(parts, "공식 어워드", evidence.awardDescription());
        appendScore(parts, "공식 RTI 지수", evidence.rtiScore());
        appendScore(parts, "공식 수용태세 지수", evidence.acceptanceScore());
        appendScore(parts, "공식 인기도", evidence.popularityScore());
        if (evidence.officialRating() != null) {
            append(parts, String.format(
                    Locale.ROOT,
                    "공식 외부평점 %s %.2f",
                    evidence.officialRatingProvider(),
                    evidence.officialRating()
            ));
        }
        // FOODUCK 리뷰 평점/리뷰 수는 일부러 문서에 넣지 않는다.
        // 이 문서는 KURE 임베딩 인덱스의 원본이라 텍스트가 바뀌면 코퍼스 해시가 달라지고,
        // 그러면 인덱스 전체가 stale로 판정되어 KURE가 통째로 내려간다.
        // 리뷰는 사용자가 언제든 남기므로, 리뷰 한 건에 검색 엔진이 죽는 구조가 된다.
        // 평점과 리뷰 수는 RestaurantQualityService가 QualityScore로 따로 반영하므로
        // 여기서 빼도 추천에 쓰이는 정보는 잃지 않는다.
        // 반면 위의 "공식 외부평점"은 evidence 테이블의 정적 값이라 그대로 둔다.
        return String.join(" ", parts);
    }

    private static void appendWhenTrue(List<String> parts, Boolean condition, String value) {
        if (Boolean.TRUE.equals(condition)) {
            append(parts, value);
        }
    }

    private static void appendLabeled(List<String> parts, String label, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            append(parts, label + " " + normalized);
        }
    }

    private static void appendScore(List<String> parts, String label, Double value) {
        if (value != null) {
            append(parts, String.format(Locale.ROOT, "%s %.2f", label, value));
        }
    }

    private static void append(List<String> parts, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            parts.add(normalized);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
