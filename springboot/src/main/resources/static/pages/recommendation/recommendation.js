/**
 * 맛집 추천 페이지 연동 모듈 (개인화 추천 + 맛집 랭킹)
 */
document.addEventListener("DOMContentLoaded", async () => {
  await RecommendationPage.init();
});

/**
 * 💡 가게 이름 정제 함수 (글자 수 제한 없음)
 */
function cleanRestaurantName(rawName) {
  if (!rawName) return "식당";

  let name = rawName.trim();

  // 1. 단어/구 단위 2회 이상 연속 반복 제거
  name = name.replace(/([가-힣a-zA-Z0-9]{2,})[\s\-\_\,]+\1/g, "$1");

  // 2. 문장 앞뒤 절반 중복 패턴 제거
  const halfLen = Math.floor(name.length / 2);
  for (let len = halfLen; len >= 2; len--) {
    const prefix = name.substring(0, len).trim();
    const rest = name.substring(len).trim();
    if (rest.startsWith(prefix)) {
      name = prefix + rest.substring(prefix.length);
      break;
    }
  }

  return name;
}

function goToRestaurantDetail(targetId, targetName) {
  console.log("🔍 [식당 클릭 이동]:", { targetId, targetName });

  // 1. 유효한 ID가 존재하는 경우 (검색 상세보기 링크 규격 적용)
  if (targetId && targetId !== "undefined" && targetId !== "null" && targetId !== "") {
    window.location.href = `/pages/restaurant/detail.html?source=public&id=${targetId}`;
    return;
  }

  // 2. ID가 없을 경우 상호명 검색으로 이동
  if (targetName && targetName !== "undefined" && targetName !== "null" && targetName !== "") {
    window.location.href = `/pages/search/index.html?keyword=${encodeURIComponent(targetName)}`;
    return;
  }

  alert("식당 정보를 찾을 수 없습니다.");
}

const RecommendationPage = {
  defaultLocation: {
    latitude: 37.4979,
    longitude: 127.0276
  },

  // 로드된 데이터 저장소
  personalList: [],
  rankingList: [],

  init: async function () {
    const container = document.getElementById("recommendation-content");
    if (!container) return;

    this.renderInitialLayout(container);
    this.loadPersonalRecommendations();
    this.loadRankingRecommendations();
  },

  getCurrentLocation: function () {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(this.defaultLocation);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
        () => resolve(this.defaultLocation),
        { timeout: 5000 }
      );
    });
  },

  renderInitialLayout: function (container) {
    container.innerHTML = `
      <div class="recommendation-grid" style="display: grid; grid-template-columns: 2fr 1fr; gap: 24px;">
        <!-- 좌측: 나를 위한 맛집 영역 -->
        <section id="personal-section" class="surface-card" style="padding: 24px; border-radius: 16px; background: #fff;">
          <div class="section-header" style="margin-bottom: 20px;">
            <h2 style="font-size: 20px; font-weight: bold; margin: 0 0 6px 0;">나를 위한 맛집</h2>
            <p style="font-size: 13px; color: #666; margin: 0;">로그인 계정의 찜·선호도와 현재 음식점 활동 데이터를 반영합니다.</p>
          </div>

          <div id="personal-cards-container">
            <div style="text-align: center; padding: 40px 0;">
              <img src="/images/characters/cooking.png" alt="오리" style="width: 120px; margin-bottom: 16px; opacity: 0.9;" />
              <h3 style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">추천 데이터를 불러오는 중입니다...</h3>
            </div>
          </div>
        </section>

        <!-- 우측: 맛집 랭킹 영역 -->
        <section id="ranking-section" class="surface-card" style="padding: 24px; border-radius: 16px; background: #fff;">
          <div class="section-header" style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
            <h2 style="font-size: 18px; font-weight: bold; margin: 0;">맛집 랭킹</h2>
            <span style="font-size: 12px; color: #888;">찜(40)·평점(30)·리뷰(30)</span>
          </div>
          <div id="ranking-cards-container">
            <p style="color: #999; font-size: 13px;">랭킹 불러오는 중...</p>
          </div>
        </section>
      </div>
    `;
  },

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

      const res = await fetch(`/api/recommendations/personal?latitude=${coords.latitude}&longitude=${coords.longitude}&radiusMeters=3000&limit=10`, {
        method: "GET",
        headers: headers
      });

      const result = await res.json();
      console.log("📥 [개인화 추천 API 원본 데이터]:", result);

      const items = (result && result.data && result.data.items) ? result.data.items : (Array.isArray(result) ? result : []);
      this.personalList = items;

      if (!items || items.length === 0) {
        container.innerHTML = `
          <div style="text-align: center; padding: 40px 0;">
            <img src="/images/characters/cooking.png" alt="오리" style="width: 120px; margin-bottom: 16px; opacity: 0.9;" />
            <h3 style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">추천에 사용할 음식점 데이터가 아직 없습니다</h3>
            <p style="font-size: 13px; color: #888; margin-bottom: 20px;">맛집을 찜 등록하면 이곳에 취향 맞춤 추천이 표시됩니다.</p>
            <a href="/pages/map/index.html" class="button button-secondary" style="display: inline-block; padding: 8px 20px; border-radius: 20px; border: 1px solid #ddd; text-decoration: none; color: #333; font-weight: bold;">Kakao Map에서 먼저 찾기</a>
          </div>
        `;
        return;
      }

      const summaryText = (result.data && (result.data.userPreferenceSummary || result.data.summary)) || '회원 맞춤 추천 맛집';

      // 💡 grid-template-columns: repeat(2, minmax(0, 1fr)) 적용으로 너비 오버플로우 방지
      let html = `
        <div style="margin-bottom: 16px; padding: 10px 14px; background: #fff8e1; border-radius: 8px; color: #f39c12; font-size: 13px; font-weight: bold;">
          📢 ${summaryText}
        </div>
        <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; align-items: stretch;">
      `;

      items.forEach((item) => {
        const resId = item.id || item.restaurantId || item.publicRestaurantId || "";
        const rawName = item.name || item.restaurantName || "식당";
        const cleanName = cleanRestaurantName(rawName);

        const reasonBadges = (item.reasons && item.reasons.length > 0)
          ? item.reasons.map(r => `<span style="font-size: 11px; color: #2e7d32; background: #e8f5e9; padding: 2px 6px; border-radius: 4px; white-space: nowrap;">💡 ${r}</span>`).join('')
          : `<span style="font-size: 11px; color: #2e7d32;">💡 회원님 취향 맞춤 맛집</span>`;

        const distanceKm = item.distanceMeters ? (item.distanceMeters / 1000).toFixed(1) : '0.0';

        html += `
          <div class="surface-card personal-item-card"
               data-id="${resId}"
               data-name="${rawName}"
               style="border: 1px solid #eee; padding: 14px; border-radius: 12px; background: #fafafa; cursor: pointer; display: flex; flex-direction: column; justify-content: space-between; min-width: 0; box-sizing: border-box; transition: transform 0.15s ease, box-shadow 0.15s ease;"
               onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 4px 12px rgba(0,0,0,0.08)';"
               onmouseout="this.style.transform='none'; this.style.boxShadow='none';">

            <!-- 상단: 이름, 거리, 카테고리 -->
            <div style="min-width: 0;">
              <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; margin-bottom: 6px;">
                <div style="display: flex; align-items: baseline; gap: 6px; min-width: 0; flex: 1;">
                  <h4 title="${rawName}" style="margin: 0; font-size: 16px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 1;">
                    ${cleanName}
                  </h4>
                  <span style="font-size: 11px; color: #ff9800; font-weight: bold; flex-shrink: 0;">(약${distanceKm}km)</span>
                </div>
                <span style="font-size: 11px; background: #ffe0b2; color: #e65100; padding: 2px 6px; border-radius: 4px; flex-shrink: 0; white-space: nowrap;">
                  ${item.category || item.categoryName || '음식점'}
                </span>
              </div>

              <!-- 주소 -->
              <p style="font-size: 12px; color: #666; margin: 4px 0 10px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${item.address || ''}">
                📍 ${item.address || '주소 정보 없음'}
              </p>
            </div>

            <!-- 하단: 추천 사유 태그 -->
            <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center; margin-top: auto;">
              ${reasonBadges}
            </div>
          </div>
        `;
      });

      html += `</div>`;
      container.innerHTML = html;

      // 클릭 이벤트 리스너 바인딩
      container.querySelectorAll(".personal-item-card").forEach(el => {
        el.addEventListener("click", () => {
          goToRestaurantDetail(el.dataset.id, el.dataset.name);
        });
      });

    } catch (err) {
      console.error("❌ [개인화 추천 로딩 예외]:", err);
    }
  },

  loadRankingRecommendations: async function () {
    const container = document.getElementById("ranking-cards-container");
    if (!container) return;

    try {
      const coords = await this.getCurrentLocation();
      const token = localStorage.getItem("accessToken") || localStorage.getItem("token");
      const headers = {};
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const res = await fetch(`/api/recommendations/rankings?latitude=${coords.latitude}&longitude=${coords.longitude}&radiusMeters=10000&limit=10`, { headers });
      const result = await res.json();
      console.log("📥 [맛집 랭킹 API 원본 데이터]:", result);

      let list = [];
      if (Array.isArray(result)) {
        list = result;
      } else if (result && result.data) {
        list = Array.isArray(result.data) ? result.data : (result.data.items || []);
      }
      this.rankingList = list;

      if (!list || list.length === 0) {
        container.innerHTML = `<p style="color: #888; font-size: 13px; text-align: center; padding: 20px 0;">등록된 랭킹 데이터가 없습니다.</p>`;
        return;
      }

      let html = `<div style="display: flex; flex-direction: column; gap: 8px;">`;
      list.forEach((item, index) => {
        const rankNum = index + 1;
        const badgeColor = rankNum === 1 ? '#ff4d4f' : (rankNum === 2 ? '#fa8c16' : (rankNum === 3 ? '#faad14' : '#8c8c8c'));

        const resId = item.restaurantId || item.id || item.publicRestaurantId || "";
        const rawName = item.name || item.restaurantName || "식당";
        const name = cleanRestaurantName(rawName);

        const category = item.category || item.categoryName || "음식점";
        const rawRating = typeof item.rawRating === 'number' ? item.rawRating.toFixed(1) : "0.0";
        const reviewCount = item.reviewCount || 0;
        const favoriteCount = item.favoriteCount || 0;
        const isLowReview = reviewCount < 10;

        const reviewBadge = isLowReview
          ? `<span style="font-size: 10px; color: #d46b08; background: #fff7e6; padding: 1px 4px; border-radius: 3px;">리뷰 ${reviewCount}개 (평점50%)</span>`
          : `<span style="font-size: 10px; color: #389e0d; background: #f6ffed; padding: 1px 4px; border-radius: 3px;">리뷰 ${reviewCount}개</span>`;

        html += `
          <div class="ranking-item-card"
               data-id="${resId}"
               data-name="${rawName}"
               style="display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 10px; background: #ffffff; border: 1px solid #f0f0f0; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease;"
               onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 4px 12px rgba(0,0,0,0.08)';"
               onmouseout="this.style.transform='none'; this.style.boxShadow='none';">
            <span style="font-weight: 800; font-size: 16px; color: ${badgeColor}; width: 22px; text-align: center;">${rankNum}</span>

            <div style="flex: 1; min-width: 0;">
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 4px;">
                <h5 title="${rawName}" style="margin: 0; font-size: 18px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${name}</h5>
                <div style="display: flex; align-items: center; gap: 6px; flex-shrink: 0; font-size: 13px;">
                  <span style="color: #e03131; font-weight: bold;">❤️ ${favoriteCount}</span>
                  <span style="color: #f59f00; font-weight: bold;">★ ${rawRating}</span>
                </div>
              </div>

              <div style="display: flex; align-items: center; justify-content: space-between; font-size: 12px;">
                <span style="color: #888;">${category}</span>
                <div>${reviewBadge}</div>
              </div>
            </div>
          </div>
        `;
      });
      html += `</div>`;
      container.innerHTML = html;

      // 💡 안전한 이벤트 리스너 바인딩
      container.querySelectorAll(".ranking-item-card").forEach(el => {
        el.addEventListener("click", () => {
          goToRestaurantDetail(el.dataset.id, el.dataset.name);
        });
      });

    } catch (err) {
      console.error("❌ 랭킹 로딩 실패:", err);
      container.innerHTML = `<p style="color: #888; font-size: 13px; text-align: center; padding: 20px 0;">랭킹 목록을 불러올 수 없습니다.</p>`;
    }
  }
};
