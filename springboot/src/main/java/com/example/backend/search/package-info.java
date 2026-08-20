/**
 * 공공데이터 음식점(public_restaurant)과 사업자가 직접 등록한 음식점(restaurant)을
 * 하나의 결과로 합쳐서 내려주는 통합 검색 모듈.
 *
 * <p>기존 {@code /api/public/map/**}(공공데이터 전용)는 그대로 두고, 검색 화면과 지도 화면이
 * 두 종류의 음식점을 함께 볼 수 있도록 별도 진입점을 추가한다. 두 테이블의 PK가 서로 겹치므로
 * 결과 항목마다 {@code sourceType}(PUBLIC/OWNED)을 함께 내려 프론트에서 상세 경로를 분기한다.
 */
package com.example.backend.search;
