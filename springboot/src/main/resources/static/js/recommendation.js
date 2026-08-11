/**
 * 자연어 맛집 추천 API 연동 모듈
 */
const RecommendationApp = {
    // 기본 위치 (위치 권한 거부 시 강남역 기준 fallback)
    defaultLocation: {
        latitude: 37.4979,
        longitude: 127.0276
    },

    /**
     * 브라우저 GPS 위치 가져오기
     */
    getCurrentLocation: function () {
        return new Promise((resolve) => {
            if (!navigator.geolocation) {
                console.warn("GPS를 지원하지 않는 브라우저입니다. 기본 위치를 사용합니다.");
                resolve(this.defaultLocation);
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (position) => {
                    resolve({
                        latitude: position.coords.latitude,
                        longitude: position.coords.longitude
                    });
                },
                (error) => {
                    console.warn("위치 정보 수집 거부/실패. 기본 위치를 사용합니다.", error);
                    resolve(this.defaultLocation);
                },
                { timeout: 5000 }
            );
        });
    },

    /**
     * 자연어 추천 API 호출
     */
    searchRecommendations: async function (queryText) {
        if (!queryText || !queryText.trim()) {
            alert("검색어를 입력해 주세요!");
            return;
        }

        const resultContainer = document.getElementById("recommendation-results");
        const loadingSpinner = document.getElementById("recommendation-loading");

        // UI 상태 초기화
        if (loadingSpinner) loadingSpinner.style.display = "block";
        if (resultContainer) resultContainer.innerHTML = "";

        try {
            // 1. 현재 사용자 GPS 위치 구하기
            const coords = await this.getCurrentLocation();

            // 2. 저장된 JWT Access Token 가져오기
            const token = localStorage.getItem("accessToken");

            // 3. API 요청 헤더 설정
            const headers = {
                "Content-Type": "application/json"
            };
            if (token) {
                headers["Authorization"] = `Bearer ${token}`;
            }

            // 4. POST /api/recommendations/query 호출
            const response = await fetch("/api/recommendations/query", {
                method: "POST",
                headers: headers,
                body: JSON.stringify({
                    query: queryText,
                    latitude: coords.latitude,
                    longitude: coords.longitude,
                    radiusMeters: 2000, // 2km 반경
                    limit: 5            // 상위 5개 추출
                })
            });

            const result = await response.json();

            if (result.success && result.data && result.data.items) {
                this.renderCards(result.data.items);
            } else {
                if (resultContainer) {
                    resultContainer.innerHTML = `<p class="no-result">${result.message || '추천 매장을 찾지 못했습니다.'}</p>`;
                }
            }
        } catch (error) {
            console.error("추천 API 호출 중 오류 발생:", error);
            if (resultContainer) {
                resultContainer.innerHTML = `<p class="error-msg">서버와 통신 중 오류가 발생했습니다.</p>`;
            }
        } finally {
            if (loadingSpinner) loadingSpinner.style.display = "none";
        }
    },

    /**
     * 추천 결과를 화면 카드 UI로 렌더링
     */
    renderCards: function (items) {
        const container = document.getElementById("recommendation-results");
        if (!container) return;

        if (items.length === 0) {
            container.innerHTML = `<p class="no-result">검색 조건에 맞는 맛집이 없습니다. 다른 검색어를 입력해 보세요!</p>`;
            return;
        }

        const cardsHtml = items.map(item => {
            // 추천 이유 태그 생성
            const reasonsHtml = (item.reasons || []).map(reason =>
                `<span class="reason-tag">💡 ${reason}</span>`
            ).join('');

            return `
                <div class="restaurant-card" data-id="${item.sourceId}">
                    <div class="card-header">
                        <h3 class="restaurant-name">${item.restaurantName}</h3>
                        <span class="category-badge">${item.categoryName || '음식점'}</span>
                    </div>
                    <p class="address">📍 ${item.address}</p>
                    <div class="card-footer">
                        <span class="distance">약 ${item.distanceMeters}m 거리</span>
                        <span class="score-badge">매칭점수 ${(item.score * 100).toFixed(0)}점</span>
                    </div>
                    ${reasonsHtml ? `<div class="reasons-wrapper">${reasonsHtml}</div>` : ''}
                </div>
            `;
        }).join('');

        container.innerHTML = cardsHtml;
    }
};
