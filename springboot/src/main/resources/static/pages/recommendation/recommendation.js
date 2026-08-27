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

/**
 * 거리 표시: 1km 이상이면 "1.2km"처럼 km 단위(소수 첫째자리)로, 1km 미만이면 "350m"처럼
 * m 단위(정수)로 보여준다. 전부 소수점 첫째자리 km로만 나오면 가까운 거리 차이가
 * "0.1km"로 뭉뚱그려져서 구분이 안 되는 문제가 있었다.
 */
function formatDistance(distanceMeters) {
  const meters = Number(distanceMeters);
  if (!Number.isFinite(meters) || meters < 0) return "거리 정보 없음";
  if (meters >= 1000) return `약 ${(meters / 1000).toFixed(1)}km`;
  return `약 ${Math.round(meters)}m`;
}

/**
 * 개인화 추천 사유를 배지로 바꾼다. 사유 문구는 백엔드가 "실제로 계산에 쓴 신호"에 대해서만
 * 내려주므로, 여기서 임의로 배지를 추가하지 않는다(화면 설명과 점수가 어긋나면 안 된다).
 */
function buildPersonalReasonBadge(reason) {
  const text = String(reason || "");
  let icon = "fa-lightbulb";
  let variant = "is-taste";
  if (text.includes("찜한") || text.includes("찜하신")) {
    icon = "fa-heart";
  } else if (text.includes("이용자 평점")) {
    icon = "fa-star";
    variant = "is-quality";
  } else if (text.includes("이용자 반응")) {
    icon = "fa-user-group";
    variant = "is-demographic";
  }
  return `<span class="personal-reason-badge ${variant}"><i class="fa-solid ${icon}" aria-hidden="true"></i> ${text}</span>`;
}

/** 카드에 표시할 "★ 4.6 · 리뷰 23개". 리뷰가 없으면 아무것도 표시하지 않는다. */
function buildRatingLine(item) {
  const reviewCount = Number(item.reviewCount);
  const averageRating = Number(item.averageRating);
  if (!Number.isFinite(reviewCount) || reviewCount <= 0 || !Number.isFinite(averageRating)) {
    return "";
  }
  return `<span class="personal-rating-line"><i class="fa-solid fa-star" aria-hidden="true"></i> ${averageRating.toFixed(1)} <span class="personal-rating-divider">·</span> 리뷰 ${reviewCount}개</span>`;
}

/**
 * 매장 썸네일용 카테고리 배경색(너무 진하지 않은 파스텔톤). map.js의 카테고리 분류
 * 기준과 맞춘다.
 */
function resolveCategoryTint(categoryText) {
  const category = categoryText || "";
  if (/한식|국밥|고기/.test(category)) return "#f7e3e3";
  if (/일식|초밥|스시/.test(category)) return "#fbe6d3";
  if (/중식|중국/.test(category)) return "#faf0d0";
  if (/양식|이탈리안|프렌치|스테이크/.test(category)) return "#e2eaf8";
  if (/아시안|베트남|태국/.test(category)) return "#e2f2e4";
  if (/카페|커피|디저트|제과|베이커리/.test(category)) return "#efe4d8";
  if (/패스트푸드|햄버거|피자|버거/.test(category)) return "#faf1cd";
  if (/분식/.test(category)) return "#f7e3ee";
  if (/술집|호프|주점|바/.test(category)) return "#ede0e2";
  if (/구내식당|뷔페/.test(category)) return "#e8ebee";
  return "#f0f0f0";
}

/**
 * 매장 썸네일 <img> 태그를 만든다. 카카오/네이버 이미지 검색으로 캐싱해둔 실사진이 있으면
 * 그걸 채워서 보여주고(object-fit: cover), 없거나 로딩에 실패하면 우리 로고를
 * 카테고리별 배경색 위에 올려서 대체한다.
 */
function buildThumbnailImgTag(imageUrl, categoryText, sizePx, borderRadiusPx) {
  const bg = resolveCategoryTint(categoryText);
  const baseStyle = `width: ${sizePx}px; height: ${sizePx}px; flex-shrink: 0; border-radius: ${borderRadiusPx}px; box-sizing: border-box;`;
  const padding = Math.round(sizePx * 0.12);

  if (imageUrl) {
    // 실사진 로딩에 실패하면(깨진 링크 등) 로고+배경색으로 바꿔치기한다.
    const onerror = `this.onerror=null; this.src='/images/logos/symbol-96.png'; this.style.objectFit='contain'; this.style.padding='${padding}px'; this.style.background='${bg}';`;
    return `<img src="${imageUrl}" alt="" aria-hidden="true" onerror="${onerror}"
                 style="${baseStyle} object-fit: cover; background: ${bg};">`;
  }

  return `<img src="/images/logos/symbol-96.png" alt="" aria-hidden="true"
               style="${baseStyle} object-fit: contain; padding: ${padding}px; background: ${bg};">`;
}

function goToRestaurantDetail(targetId, targetName) {
  console.log("🔍 [식당 클릭 이동]:", { targetId, targetName });

  // 1. 유효한 ID가 있으면 지도로 바로 이동해서 그 매장에 포커싱한다(맛집 추천은 전부
  //    공공데이터 매장이라 sourceType은 항상 PUBLIC이다 - map.js가 ?restaurantId=로
  //    들어오면 자동으로 focusRestaurant를 호출한다).
  if (targetId && targetId !== "undefined" && targetId !== "null" && targetId !== "") {
    window.location.href = `/map?restaurantId=${encodeURIComponent(targetId)}`;
    return;
  }

  // 2. ID가 없을 경우 상호명으로 지도 검색
  if (targetName && targetName !== "undefined" && targetName !== "null" && targetName !== "") {
    window.location.href = `/map?q=${encodeURIComponent(targetName)}`;
    return;
  }

  alert("식당 정보를 찾을 수 없습니다.");
}

const RecommendationPage = {
  // 위치 권한이 없거나 조회에 실패했을 때 기준으로 삼는 기본 위치: 신논현역.
  defaultLocation: {
    latitude: 37.5048,
    longitude: 127.0255
  },

  // 로드된 데이터 저장소
  personalList: [],
  rankingList: [],

  // 실제 GPS 대신 defaultLocation(신논현역)을 썼는지 여부. 개인화 추천/랭킹 두 섹션이
  // 위치를 각각 요청하므로, getCurrentLocation()의 캐시된 프로미스와 함께 한 번만 계산해서 공유한다.
  usedFallbackLocation: false,
  locationPromise: null,

  init: async function () {
    const container = document.getElementById("recommendation-content");
    if (!container) return;

    this.renderInitialLayout(container);
    await Promise.all([
      this.loadPersonalRecommendations(),
      this.loadRankingRecommendations()
    ]);
  },

  getCurrentLocation: function () {
    // 두 섹션이 각각 부르더라도 위치 권한 팝업은 한 번만 뜨게, 그리고 폴백 여부
    // 판단도 한 번만 하게 프로미스를 캐시해서 재사용한다.
    if (!this.locationPromise) {
      this.locationPromise = new Promise((resolve) => {
        if (!navigator.geolocation) {
          this.usedFallbackLocation = true;
          this.showLocationFallbackNotice();
          resolve(this.defaultLocation);
          return;
        }
        navigator.geolocation.getCurrentPosition(
          (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
          () => {
            this.usedFallbackLocation = true;
            this.showLocationFallbackNotice();
            resolve(this.defaultLocation);
          },
          { timeout: 5000 }
        );
      });
    }
    return this.locationPromise;
  },

  // "현재 위치를 찾을 수 없어 신논현역을 기준으로 추천했습니다" 안내를 보여준다.
  // 맛집찾기(지도)와 달리 추천은 신논현역인 척 조용히 넘어가지 않고, 왜 이
  // 매장들이 추천됐는지 사용자에게 밝혀야 해서 명시적인 배너로 알려준다.
  showLocationFallbackNotice: function () {
    const notice = document.getElementById("reco-location-notice");
    if (notice) notice.hidden = false;
  },

  renderInitialLayout: function (container) {
    container.innerHTML = `
      <div id="reco-location-notice" role="status"
           style="display: flex; align-items: center; gap: 8px; margin-bottom: 14px; padding: 10px 14px; background: #e7f5ff; border-radius: 8px; color: #1971c2; font-size: 13px; font-weight: bold;"
           hidden>
        <i class="fa-solid fa-location-crosshairs" aria-hidden="true"></i> 현재 위치를 찾을 수 없어 신논현역을 기준으로 추천했습니다.
      </div>

      <div class="reco-tabs" role="tablist" aria-label="맛집 추천 구분">
        <button id="reco-tab-personal" class="reco-tab is-active" type="button" role="tab"
                aria-selected="true" aria-controls="personal-section">나를 위한 맛집</button>
        <button id="reco-tab-ranking" class="reco-tab" type="button" role="tab"
                aria-selected="false" aria-controls="ranking-section">맛집 랭킹</button>
      </div>

      <!-- 나를 위한 맛집 영역 -->
      <section id="personal-section" class="surface-card" role="tabpanel" aria-labelledby="reco-tab-personal"
               style="padding: 24px; border-radius: 16px; background: #fff;">
        <div class="section-header" style="margin-bottom: 20px;">
          <h2 style="font-size: 20px; font-weight: bold; margin: 0 0 6px 0;">나를 위한 맛집</h2>
          <p style="font-size: 13px; color: #666; margin: 0;">로그인 계정의 찜·선호도와 현재 음식점 활동 데이터를 반영합니다.</p>
        </div>

        <div id="personal-cards-container">
          <div class="skeleton-personal-grid" aria-hidden="true">
            <div class="skeleton-block skeleton-personal-card"></div>
            <div class="skeleton-block skeleton-personal-card"></div>
            <div class="skeleton-block skeleton-personal-card"></div>
            <div class="skeleton-block skeleton-personal-card"></div>
          </div>
        </div>
      </section>

      <!-- 맛집 랭킹 영역 -->
      <section id="ranking-section" class="surface-card" role="tabpanel" aria-labelledby="reco-tab-ranking" hidden
               style="padding: 24px; border-radius: 16px; background: #fff;">
        <div class="section-header" style="margin-bottom: 16px;">
          <h2 style="font-size: 20px; font-weight: bold; margin: 0 0 6px 0;">맛집 랭킹</h2>
          <p style="font-size: 13px; color: #666; margin: 0;">AI 리뷰 분석 긍정비율·평점·찜 개수를 종합해서 매긴 순위입니다.</p>
        </div>
        <div id="ranking-cards-container">
          <div class="skeleton-ranking-list" aria-hidden="true">
            <div class="skeleton-block skeleton-ranking-row"></div>
            <div class="skeleton-block skeleton-ranking-row"></div>
            <div class="skeleton-block skeleton-ranking-row"></div>
            <div class="skeleton-block skeleton-ranking-row"></div>
            <div class="skeleton-block skeleton-ranking-row"></div>
          </div>
        </div>
      </section>
    `;

    const tabPersonal = document.getElementById("reco-tab-personal");
    const tabRanking = document.getElementById("reco-tab-ranking");
    const personalSection = document.getElementById("personal-section");
    const rankingSection = document.getElementById("ranking-section");

    function activateTab(name) {
      const isPersonal = name === "personal";
      tabPersonal.classList.toggle("is-active", isPersonal);
      tabPersonal.setAttribute("aria-selected", String(isPersonal));
      tabRanking.classList.toggle("is-active", !isPersonal);
      tabRanking.setAttribute("aria-selected", String(!isPersonal));
      personalSection.hidden = !isPersonal;
      rankingSection.hidden = isPersonal;
    }

    tabPersonal.addEventListener("click", () => activateTab("personal"));
    tabRanking.addEventListener("click", () => activateTab("ranking"));
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

      const responseData = result && result.data ? result.data : null;
      const items = (responseData && responseData.items) ? responseData.items : (Array.isArray(result) ? result : []);
      this.personalList = items;

      if (!items || items.length === 0) {
        const requiresLogin = responseData && responseData.personalizationLevel === "ANONYMOUS";
        const needsFavoriteData = responseData && (
          responseData.personalizationLevel === "NO_FAVORITES"
          || responseData.personalizationLevel === "INSUFFICIENT_TASTE"
        );
        const emptyTitle = requiresLogin
          ? "로그인 후 나만의 추천을 확인해보세요"
          : (needsFavoriteData ? "아직 찜한 맛집이 없어요" : "취향에 맞는 주변 맛집을 찾지 못했습니다");
        const emptyMessage = requiresLogin
          ? "찜과 리뷰 기록을 안전하게 연결하려면 로그인이 필요합니다."
          : (needsFavoriteData
            ? "맛집을 하나 이상 찜하면 나만의 추천이 시작됩니다."
            : "검색 범위를 넓히거나 다른 위치에서 다시 확인해 주세요.");
        // 빈 상태에는 별도 이동 버튼을 두지 않는다.
        container.innerHTML = `
          <div class="recommendation-empty">
            <img src="/images/characters/cooking.png" alt="오리" style="width: 120px; margin-bottom: 16px; opacity: 0.9;" />
            <h3 style="font-size: 16px; font-weight: bold; margin-bottom: 8px;">${emptyTitle}</h3>
            <p style="font-size: 13px; color: #888;">${emptyMessage}</p>
          </div>
        `;
        return;
      }

      const summaryText = (responseData && (responseData.userPreferenceSummary || responseData.summary)) || '회원 맞춤 추천 맛집';

      // 💡 grid-template-columns: repeat(2, minmax(0, 1fr)) 적용으로 너비 오버플로우 방지
      let html = `
        <div style="margin-bottom: 16px; padding: 10px 14px; background: #fff8e1; border-radius: 8px; color: #f39c12; font-size: 13px; font-weight: bold;">
          <i class="fa-solid fa-bullhorn" aria-hidden="true"></i> ${summaryText}
        </div>
        <div class="personal-cards-grid">
      `;

      items.forEach((item) => {
        const resId = item.id || item.restaurantId || item.publicRestaurantId || "";
        const rawName = item.name || item.restaurantName || "식당";
        const cleanName = cleanRestaurantName(rawName);

        const reasonBadges = (item.reasons && item.reasons.length > 0)
          ? item.reasons.map(buildPersonalReasonBadge).join('')
          : `<span class="personal-reason-badge is-taste"><i class="fa-solid fa-lightbulb" aria-hidden="true"></i> 회원님 취향 맞춤 맛집</span>`;
        const ratingLine = buildRatingLine(item);

        const distanceLabel = formatDistance(item.distanceMeters);
        const thumbnailTag = buildThumbnailImgTag(item.imageUrl, item.category || item.categoryName, 56, 12);

        html += `
          <div class="surface-card personal-item-card"
               data-id="${resId}"
               data-name="${rawName}"
               style="border: 1px solid #eee; padding: 14px; border-radius: 12px; background: #fafafa; cursor: pointer; display: flex; gap: 12px; align-items: flex-start; min-width: 0; box-sizing: border-box; transition: transform 0.15s ease, box-shadow 0.15s ease;"
               onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 4px 12px rgba(0,0,0,0.08)';"
               onmouseout="this.style.transform='none'; this.style.boxShadow='none';">

            <!-- 왼쪽: 매장 이미지 (사진 없으면 카테고리 마커 아이콘) -->
            ${thumbnailTag}

            <!-- 오른쪽: 이름/거리/카테고리/주소/추천 사유 -->
            <div style="min-width: 0; flex: 1; display: flex; flex-direction: column; justify-content: space-between;">
              <div>
                <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; margin-bottom: 6px;">
                  <div style="display: flex; align-items: baseline; gap: 6px; min-width: 0; flex: 1;">
                    <h4 title="${rawName}" style="margin: 0; font-size: 16px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex-shrink: 1;">
                      ${cleanName}
                    </h4>
                    <span style="font-size: 11px; color: #ff9800; font-weight: bold; flex-shrink: 0;">(${distanceLabel})</span>
                  </div>
                  <span style="font-size: 11px; background: #ffe0b2; color: #e65100; padding: 2px 6px; border-radius: 4px; flex-shrink: 0; white-space: nowrap;">
                    ${item.category || item.categoryName || '음식점'}
                  </span>
                </div>

                <!-- 평점/리뷰 수 (리뷰가 있는 매장에만 표시) -->
                ${ratingLine}

                <!-- 주소 -->
                <p style="font-size: 12px; color: #666; margin: 4px 0 10px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${item.address || ''}">
                  <i class="fa-solid fa-location-dot" aria-hidden="true"></i> ${item.address || '주소 정보 없음'}
                </p>
              </div>

              <!-- 하단: 추천 사유 태그 -->
              <div style="display: flex; flex-wrap: wrap; gap: 4px; align-items: center;">
                ${reasonBadges}
              </div>
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
        // 리뷰가 적을수록 평점·긍정비율이 전체 평균 쪽으로 보정되어 반영된다(베이지안 평균).
        const isLowReview = reviewCount < 8;

        const reviewBadge = isLowReview
          ? `<span style="font-size: 10px; color: #d46b08; background: #fff7e6; padding: 1px 4px; border-radius: 3px;">리뷰 ${reviewCount}개 (신뢰도 보정됨)</span>`
          : `<span style="font-size: 10px; color: #389e0d; background: #f6ffed; padding: 1px 4px; border-radius: 3px;">리뷰 ${reviewCount}개</span>`;

        const positiveBadge = typeof item.positiveRatio === "number"
          ? `<span style="font-size: 10px; color: #1971c2; background: #e7f5ff; padding: 1px 4px; border-radius: 3px;"><i class="fa-solid fa-sparkles" aria-hidden="true"></i> AI 긍정 ${Math.round(item.positiveRatio)}%</span>`
          : "";

        const thumbnailTag = buildThumbnailImgTag(item.imageUrl, category, 48, 10);

        html += `
          <div class="ranking-item-card"
               data-id="${resId}"
               data-name="${rawName}"
               style="display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 10px; background: #ffffff; border: 1px solid #f0f0f0; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease;"
               onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 4px 12px rgba(0,0,0,0.08)';"
               onmouseout="this.style.transform='none'; this.style.boxShadow='none';">
            <span style="font-weight: 800; font-size: 16px; color: ${badgeColor}; width: 22px; text-align: center; flex-shrink: 0;">${rankNum}</span>

            ${thumbnailTag}

            <div style="flex: 1; min-width: 0;">
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 4px;">
                <h5 title="${rawName}" style="margin: 0; font-size: 18px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${name}</h5>
                <div style="display: flex; align-items: center; gap: 6px; flex-shrink: 0; font-size: 13px;">
                  <span style="color: #e03131; font-weight: bold;"><i class="fa-solid fa-heart" aria-hidden="true"></i> ${favoriteCount}</span>
                  <span style="color: #f59f00; font-weight: bold;"><i class="fa-solid fa-star" aria-hidden="true"></i> ${rawRating}</span>
                </div>
              </div>

              <div style="display: flex; align-items: center; justify-content: space-between; font-size: 12px;">
                <span style="color: #888;">${category}</span>
                <div style="display: flex; align-items: center; gap: 4px;">${positiveBadge}${reviewBadge}</div>
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
