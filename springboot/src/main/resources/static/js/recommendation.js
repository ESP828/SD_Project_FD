/**
 * 맛집 추천 페이지 연동 모듈 (개인화 추천 + 맛집 랭킹)
 */
document.addEventListener("DOMContentLoaded", async () => {
  await RecommendationPage.init();
});

const RecommendationPage = {
  // 기본 위치 (강남역 기준 fallback)
  defaultLocation: {
    latitude: 37.4979,
    longitude: 127.0276
  },

  init: async function () {
    const container = document.getElementById("recommendation-content");
    if (!container) return;

    // 1. 기본 전체 페이지 레이아웃 렌더링
    this.renderInitialLayout(container);

    // 2. 나를 위한 맛집 (개인화 추천 API) 데이터 로드
    this.loadPersonalRecommendations();

    // 3. 우측 맛집 랭킹 데이터 로드
    this.loadRankingRecommendations();
  },

  /**
   * 브라우저 GPS 위치 구하기
   */
  getCurrentLocation: function () {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(this.defaultLocation);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
        (err) => resolve(this.defaultLocation),
        { timeout: 5000 }
      );
    });
  },

  /**
   * 1. 초기 기본 레이아웃 구성
   */
  renderInitialLayout: function (container) {
    container.innerHTML = `
      <div class="recommendation-grid" style="display: grid; grid-template-columns: 2fr 1fr; gap: 24px;">
        <!-- 좌측: 나를 위한 맛집 영역 -->
        <section id="personal-section" class="surface-card" style="padding: 24px; border-radius: 16px; background: #fff;">
          <div class="section-header" style="margin-bottom: 20px;">
            <h2 style="font-size: 20px; font-weight: bold; margin: 0 0 6px 0;">나를 위한 맛집</h2>
            <p style="font-size: 13px; color: #666; margin: 0;">로그인 계정의 찜·선호도와 현재 음식점 활동 데이터를 반영합니다.</p>
          </div>

          <!-- 📌 개인화 카드 들어갈 컨테이너 (기본 오리 캐릭터 상태) -->
          <div id="personal-cards-container">
            <div style="text-align: center; padding: 40px 0;">
              <img src="/images/characters/cooking.png" alt="오리" style="width: 120px; margin-bottom: 16px; opacity: 0.9;" />
              <h3 style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">추천에 사용할 음식점 데이터가 아직 없습니다</h3>
              <p style="font-size: 13px; color: #888; margin-bottom: 20px;">음식점·메뉴가 등록되면 이곳에 가게 정보가 표시됩니다.</p>
              <a href="/pages/map/index.html" class="button button-secondary" style="display: inline-block; padding: 8px 20px; border-radius: 20px; border: 1px solid #ddd; text-decoration: none; color: #333; font-weight: bold;">Kakao Map에서 먼저 찾기</a>
            </div>
          </div>
        </section>

        <!-- 우측: 맛집 랭킹 영역 -->
        <section id="ranking-section" class="surface-card" style="padding: 24px; border-radius: 16px; background: #fff;">
          <div class="section-header" style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
            <h2 style="font-size: 18px; font-weight: bold; margin: 0;">맛집 랭킹</h2>
            <span style="font-size: 12px; color: #888;">평점·리뷰·찜 집계 순</span>
          </div>
          <div id="ranking-cards-container">
            <p style="color: #999; font-size: 13px;">랭킹 불러오는 중...</p>
          </div>
        </section>
      </div>
    `;
  },

  /**
   * 2. 💡 [핵심] "나를 위한 맛집" (GET /api/recommendations/personal) 데이터 가져오기
   */
  loadPersonalRecommendations: async function () {
    const container = document.getElementById("personal-cards-container");
    if (!container) return;

    try {
      const coords = await this.getCurrentLocation();
      const token = localStorage.getItem("accessToken") || localStorage.getItem("token");

      const headers = { "Content-Type": "application/json" };
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      // 백엔드 개인화 추천 API 호출
      const res = await fetch(`/api/recommendations/personal?latitude=${coords.latitude}&longitude=${coords.longitude}&radiusMeters=3000&limit=10`, {
        method: "GET",
        headers: headers
      });

      const result = await res.json();

      // 찜 데이터가 없으면 기본 오리 UI 유지
      if (!result.data || !result.data.hasPreferenceData || result.data.items.length === 0) {
        console.log("찜/선호 데이터 없음: 오리 UI 표출 유지");
        return;
      }

      // 찜 데이터가 있으면 카드 목록으로 교체!
      const items = result.data.items;
      let html = `
        <div style="margin-bottom: 16px; padding: 10px 14px; background: #fff8e1; border-radius: 8px; color: #f39c12; font-size: 13px; font-weight: bold;">
          📢 ${result.data.userPreferenceSummary}
        </div>
        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px;">
      `;

      items.forEach(item => {
        const reason = (item.reasons && item.reasons.length > 0) ? item.reasons[0] : '회원님 취향 맞춤 맛집';
        html += `
          <div class="surface-card" style="border: 1px solid #eee; padding: 14px; border-radius: 12px; background: #fafafa;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
              <h4 style="margin: 0; font-size: 15px; font-weight: bold;">${item.restaurantName}</h4>
              <span style="font-size: 11px; background: #ffe0b2; color: #e65100; padding: 2px 6px; border-radius: 4px;">${item.categoryName || '음식점'}</span>
            </div>
            <p style="font-size: 12px; color: #666; margin: 4px 0 8px 0;">📍 ${item.address}</p>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span style="font-size: 11px; color: #2e7d32; font-weight: bold;">💡 ${reason}</span>
              <span style="font-size: 11px; color: #888;">약 ${item.distanceMeters}m</span>
            </div>
          </div>
        `;
      });

      html += `</div>`;
      container.innerHTML = html;

    } catch (err) {
      console.error("개인화 추천 로딩 중 예외 발생:", err);
    }
  },

  /**
   * 3. 맛집 랭킹 (GET /api/recommendations) 데이터 가져오기
   */
  loadRankingRecommendations: async function () {
    const container = document.getElementById("ranking-cards-container");
    if (!container) return;

    try {
      const token = localStorage.getItem("accessToken") || localStorage.getItem("token");
      const headers = {};
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const res = await fetch("/api/recommendations", { headers });
      const result = await res.json();

      if (!result.data || !result.data.items || result.data.items.length === 0) {
        container.innerHTML = `<p style="color: #888; font-size: 13px;">등록된 랭킹 데이터가 없습니다.</p>`;
        return;
      }

      let html = `<div style="display: flex; flex-direction: column; gap: 10px;">`;
      result.data.items.slice(0, 5).forEach((item, index) => {
        html += `
          <div style="display: flex; align-items: center; gap: 12px; padding: 10px; border-bottom: 1px solid #f0f0f0;">
            <span style="font-weight: bold; font-size: 16px; color: #ff8a00; width: 20px;">${index + 1}</span>
            <div style="flex: 1;">
              <h5 style="margin: 0 0 2px 0; font-size: 14px;">${item.restaurantName || item.name}</h5>
              <span style="font-size: 11px; color: #888;">${item.categoryName || ''}</span>
            </div>
          </div>
        `;
      });
      html += `</div>`;
      container.innerHTML = html;

    } catch (err) {
      console.error("랭킹 로딩 실패:", err);
      container.innerHTML = `<p style="color: #888; font-size: 13px;">랭킹 목록을 불러올 수 없습니다.</p>`;
    }
  }
};
