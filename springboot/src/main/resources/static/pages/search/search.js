(() => {
  const PAGE_SIZE = 12;

  // AI 추천으로 받아올 결과 수의 범위.
  const MIN_AI_RESULT_LIMIT = 1;
  const MAX_AI_RESULT_LIMIT = 300;

  /**
   * 검색어에서 결정적으로 결과 수를 정한다.
   *
   * 예전에는 1200을 요청해 백엔드 상한 1000을 그대로 받아왔고, 화면에는 늘
   * "총 1000개 추천 결과"가 떴다. 그 1000은 추천 결과가 아니라 반경 안 후보 전량이라
   * 사용자에게 오해를 주고, 매 검색마다 1000건을 직렬화하는 비용도 컸다.
   *
   * 같은 글자를 넣으면 언제나 같은 개수가 나와야 하므로 난수를 쓰지 않고
   * FNV-1a 32비트 해시를 쓴다. 공백은 무시하므로 "혼밥 맛집"과 "혼밥맛집"은 같은 값이 된다.
   */
  function aiResultLimit(query) {
    const compact = String(query || "").replace(/\s+/g, "");
    if (compact.length === 0) {
      return MIN_AI_RESULT_LIMIT;
    }
    let hash = 0x811c9dc5;
    for (let index = 0; index < compact.length; index += 1) {
      hash ^= compact.charCodeAt(index);
      hash = Math.imul(hash, 0x01000193) >>> 0;
    }
    const span = MAX_AI_RESULT_LIMIT - MIN_AI_RESULT_LIMIT + 1;
    return MIN_AI_RESULT_LIMIT + (hash % span);
  }

  const form = document.getElementById("restaurant-search-form");
  const keywordInput = document.getElementById("search-keyword");
  const categorySelect = document.getElementById("search-category");
  const regionInput = document.getElementById("search-region");
  const genderSelect = document.getElementById("search-gender");
  const ageGroupSelect = document.getElementById("search-age-group");
  const filterToggle = document.getElementById("search-filter-toggle");
  const filterPanel = document.getElementById("search-filter-panel");

  // ✨ AI 토글 버튼 및 제출 버튼 요소
  const aiSearchToggle = document.getElementById("ai-search-toggle");
  const searchSubmitBtn = document.getElementById("search-submit");
  const submitBtnText = document.getElementById("submit-btn-text");
  const recommendLink = document.getElementById("search-recommend-link");

  const results = document.getElementById("search-results");
  const resultHeading = document.getElementById("result-heading");
  const count = document.getElementById("search-result-count");
  const status = document.getElementById("search-status");
  const paginationContainer = document.getElementById("search-pagination");
  const quickButtons = document.querySelectorAll("[data-quick-category]");

  let isAiMode = false;
  let currentPage = 0;

  // 💡 AI 추천 전체 결과를 저장해두는 전역 배열
  let aiRecommendationItems = [];
  let aiRecommendationMeta = null;
  let publicSearchPageData = null;
  let renderedSearchMode = null;

  // 위치 권한이 없거나 조회에 실패했을 때 기준으로 삼는 기본 위치: 신논현역.
  const DEFAULT_LOCATION = { latitude: 37.5048, longitude: 127.0255 };

  // 상세페이지에 다녀와도 마지막 검색 상태가 남아 있도록 하는 저장소 키.
  // AI 모드는 사용자가 직접 끌 때까지 유지해야 해서 localStorage, 검색 결과는 탭 단위로만
  // 살아 있으면 되므로 sessionStorage를 쓴다.
  const AI_MODE_KEY = "fooduck:search-ai-mode";
  const SEARCH_CACHE_KEY = "fooduck:search-cache";
  const SEARCH_CACHE_VERSION = 3;
  const SEARCH_CACHE_TTL_MS = 30 * 60 * 1000;
  const MAX_CACHED_AI_ITEMS = 1200;

  function setFilterPanelOpen(isOpen) {
    filterPanel.hidden = !isOpen;
    filterToggle.setAttribute("aria-expanded", String(isOpen));
  }

  function setStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle("is-error", isError);
  }

  function recommendationMeta(data) {
    if (!data) return null;
    return {
      engineUsed: data.engineUsed || null,
      fallback: Boolean(data.fallback),
      fallbackReason: data.fallbackReason || null,
      relaxedFilters: Array.isArray(data.relaxedFilters) ? data.relaxedFilters : [],
      resolvedConstraints: Array.isArray(data.resolvedConstraints) ? data.resolvedConstraints : [],
    };
  }

  function updateAiToggleUI(active) {
  isAiMode = active;

  if (aiSearchToggle) {
    aiSearchToggle.classList.toggle("is-active", isAiMode);
    aiSearchToggle.setAttribute("aria-pressed", String(isAiMode));
  }
  if (searchSubmitBtn) {
    searchSubmitBtn.classList.toggle("ai-mode", isAiMode);
  }
  if (submitBtnText) {
    submitBtnText.textContent = isAiMode ? "✨ AI 검색하기" : "검색하기";
  }

  // ✨ AI 모드가 켜지면 빠른 검색 버튼의 push(활성) 상태를 취소하고 select 초기화
  if (isAiMode) {
    categorySelect.value = "";
    quickButtons.forEach((button) => {
      button.classList.remove("is-active");
    });
  }

  // ✨ AI 모드에서도 빠른 검색 버튼으로 "OOO 추천해줘" 검색어를 바로 채울 수 있게 둔다.
}

  // AI 모드는 사용자가 직접 끄기 전까지 유지한다(브라우저를 닫았다 열어도 그대로).
  function storeAiModePreference(active) {
    try {
      localStorage.setItem(AI_MODE_KEY, active ? "1" : "0");
    } catch (_error) {
      /* 저장 실패해도 검색 자체는 진행한다 */
    }
  }

  function readAiModePreference() {
    try {
      return localStorage.getItem(AI_MODE_KEY) === "1";
    } catch (_error) {
      return false;
    }
  }

  function currentConditions() {
    return {
      keyword: keywordInput.value.trim(),
      region: regionInput.value.trim(),
      category: isAiMode ? "" : categorySelect.value,
      gender: genderSelect ? genderSelect.value : "",
      ageGroup: ageGroupSelect ? ageGroupSelect.value : "",
      aiMode: isAiMode,
    };
  }

  // 같은 조건으로 돌아왔는지 판단하는 키. 조건이 하나라도 다르면 캐시를 쓰지 않는다.
  function conditionKey(conditions = currentConditions()) {
    return JSON.stringify(conditions);
  }

  function saveSearchCache(mode, page, data) {
    try {
      const conditions = currentConditions();
      const payload = {
        version: SEARCH_CACHE_VERSION,
        key: conditionKey(conditions),
        conditions,
        mode,
        page,
        searchUrl: currentSearchUrl(),
        scrollY: Math.max(0, window.scrollY || 0),
        savedAt: Date.now(),
        data,
      };
      sessionStorage.setItem(SEARCH_CACHE_KEY, JSON.stringify(payload));
    } catch (_error) {
      /* 용량 초과 등으로 저장하지 못해도 검색 동작에는 영향이 없다 */
    }
  }

  function readSearchCache() {
    try {
      const raw = sessionStorage.getItem(SEARCH_CACHE_KEY);
      if (!raw) return null;
      const cache = JSON.parse(raw);
      if (!cache || cache.version !== SEARCH_CACHE_VERSION) return null;
      if (Date.now() - Number(cache.savedAt || 0) > SEARCH_CACHE_TTL_MS) return null;
      return cache;
    } catch (_error) {
      return null;
    }
  }

  function currentSearchUrl() {
    return `${window.location.pathname}${window.location.search}${window.location.hash}`;
  }

  function persistVisibleSearchState() {
    if (renderedSearchMode === "ai" && aiRecommendationItems.length > 0) {
      saveSearchCache("ai", currentPage, {
        items: aiRecommendationItems.slice(0, MAX_CACHED_AI_ITEMS),
        meta: aiRecommendationMeta,
      });
      return;
    }
    if (renderedSearchMode === "public" && publicSearchPageData) {
      saveSearchCache("public", currentPage, publicSearchPageData);
    }
  }

  function restoreScrollPosition(cache) {
    const scrollY = Number(cache?.scrollY);
    if (!Number.isFinite(scrollY) || scrollY < 0) return;
    window.requestAnimationFrame(() => {
      window.scrollTo({ top: scrollY, left: 0, behavior: "auto" });
    });
  }

  function markerFor(place) {
    const category = place.categoryName || "";
    if (/카페|커피/.test(category)) return "category_cafe.png";
    if (/디저트|제과|베이커리|아이스크림|빵|도넛|떡|한과/.test(category)) return "category_dessert.png";
    if (/중식|중국/.test(category)) return "category_chinese.png";
    if (/일식|일본|초밥|스시/.test(category)) return "category_japanese.png";
    if (/양식|서양식|이탈리안|프렌치|스테이크/.test(category)) return "category_western.png";
    if (/패스트푸드|햄버거|피자|버거/.test(category)) return "category_fastfood.png";
    if (/술집|호프|주점|바/.test(category)) return "category_pub.png";
    if (/한식|국밥|고기|분식/.test(category)) return "category_korean.png";
    return "state_default.svg";
  }

  function createTextRow(className, iconName, text) {
    const row = document.createElement("p");
    row.className = className;
    if (iconName) {
      const icon = document.createElement("span");
      icon.className = "material-symbols-rounded";
      icon.setAttribute("aria-hidden", "true");
      icon.textContent = iconName;
      row.append(icon);
    }
    const copy = document.createElement("span");
    copy.textContent = text;
    row.append(copy);
    return row;
  }

  function createResultCard(item, isRecommendation = false) {
    const article = document.createElement("article");
    article.className = "surface-card search-result-card";

    const visual = document.createElement("div");
    visual.className = "search-result-visual";
    const image = document.createElement("img");
    image.src = `/images/markers/${markerFor(item)}`;
    image.alt = "";
    image.setAttribute("aria-hidden", "true");
    visual.append(image);

    const body = document.createElement("div");
    body.className = "search-result-body";

    const category = document.createElement("span");
    category.className = "search-result-category";
    category.textContent = item.categoryName || "음식점";

    const title = document.createElement("h3");
    title.textContent = isRecommendation ? item.restaurantName : item.name;

    const address = createTextRow(
      "search-result-address",
      "location_on",
      isRecommendation
        ? item.address || "주소 정보 없음"
        : item.roadAddress || item.lotAddress || "주소 정보 없음",
    );
    body.append(category, title, address);

    if (isRecommendation) {
      const distanceLabel = Number.isFinite(item.distanceMeters)
        ? item.distanceMeters < 1000
          ? `약 ${Math.round(item.distanceMeters)}m 거리`
          : `약 ${(item.distanceMeters / 1000).toFixed(1)}km 거리`
        : "거리 정보 없음";
      const distanceInfo = createTextRow(
        "search-result-distance",
        "near_me",
        `${distanceLabel} (매칭점수 ${(item.score * 100).toFixed(0)}점)`,
      );
      distanceInfo.style.fontSize = "0.85rem";
      distanceInfo.style.color = "#007bff";
      body.append(distanceInfo);

      if (item.reasons && item.reasons.length > 0) {
        const reasonsWrap = document.createElement("div");
        reasonsWrap.className = "search-result-reasons";
        reasonsWrap.style.marginTop = "8px";

        item.reasons.forEach((reason) => {
          const badge = document.createElement("span");
          badge.className = "reason-badge";
          badge.style.cssText = "display:inline-block; background:#ebf8ff; color:#2b6cb0; font-size:12px; padding:2px 8px; border-radius:12px; margin-right:4px; margin-top:4px;";
          badge.textContent = `💡 ${reason}`;
          reasonsWrap.append(badge);
        });
        body.append(reasonsWrap);
      }
    }

    const actions = document.createElement("div");
    actions.className = "search-result-actions";

    // 공공데이터 음식점과 사업자 등록 음식점은 ID 체계가 달라서 sourceType으로 상세 경로를 나눈다.
    const sourceType = (item.sourceType || "PUBLIC").toLowerCase();
    const sourceId = isRecommendation ? item.sourceId : item.id;
    const restaurantName = isRecommendation ? item.restaurantName : item.name;
    const detailLink = document.createElement("a");
    detailLink.className = "button button-primary";
    const detailParams = new URLSearchParams({
      source: sourceType,
      id: String(sourceId),
      returnTo: currentSearchUrl(),
    });
    detailLink.href = `/restaurant/detail?${detailParams.toString()}`;
    detailLink.textContent = "상세보기";
    detailLink.addEventListener("pointerdown", persistVisibleSearchState);
    detailLink.addEventListener("click", persistVisibleSearchState);

    const mapLink = document.createElement("a");
    mapLink.className = "button button-secondary";
    // 공공데이터 매장은 ID로 바로 이동시켜 정확한 위치에 포커싱한다.
    // (이름만 넘기면 지도의 현재 위치 기준 반경 내에서만 검색되어 못 찾는 경우가 있었음)
    mapLink.href = sourceType === "public"
      ? `/map?restaurantId=${encodeURIComponent(sourceId)}`
      : `/map?q=${encodeURIComponent(restaurantName)}`;
    mapLink.textContent = "지도에서 찾기";

    actions.append(detailLink, mapLink);
    body.append(actions);

    article.append(visual, body);
    return article;
  }

  function renderEmpty(message) {
    results.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "surface-card search-empty";
    const image = document.createElement("img");
    image.src = "/images/characters/error.png";
    image.alt = "";
    image.setAttribute("aria-hidden", "true");
    const title = document.createElement("h3");
    title.textContent = "검색 결과가 없습니다";
    const copy = document.createElement("p");
    copy.textContent = message;
    empty.append(image, title, copy);
    results.append(empty);
    count.textContent = "0";
  }

  // 페이징 UI 업데이트 (일반 / AI 통합 지원). 뒤쪽 페이지로 가려고 다음 버튼을
  // 계속 누르지 않아도 되도록, 현재 페이지 주변 번호를 바로 눌러 이동할 수 있게 한다.
  function updatePagination(response) {
    const pageData = window.FooduckPagination.normalize(response, {
      contentKey: "items",
      totalKey: "totalCount",
      pageKey: "page",
    });
    window.FooduckPagination.render(paginationContainer, pageData, (page) => {
      runSearch(page);
      scrollToTop();
    });
  }

function updateUrlState(page = 0) {
  const params = new URLSearchParams();
  const keyword = keywordInput.value.trim();
  const region = regionInput.value.trim();
  const category = categorySelect.value;
  const gender = genderSelect ? genderSelect.value : "";
  const ageGroup = ageGroupSelect ? ageGroupSelect.value : "";

  if (keyword) params.set("keyword", keyword);
  if (region) params.set("region", region);

  // ✨ AI 모드가 아닐 때만 category를 URL 파라미터에 추가
  if (!isAiMode && category) {
    params.set("category", category);
  }
  if (gender) params.set("gender", gender);
  if (ageGroup) params.set("ageGroup", ageGroup);
  // 상세페이지에서 돌아왔을 때 AI 모드가 풀리지 않도록 URL에도 남긴다.
  if (isAiMode) params.set("ai", "1");

  params.set("page", String(Math.max(page, 0)));

  const query = params.toString();
  const url = query ? `${window.location.pathname}?${query}` : window.location.pathname;
  window.history.replaceState(null, "", url);
}

  // 현재 위치는 페이지 로드 시 한 번만 요청해서 캐시해두고, 이후 검색마다 재사용한다.
  let currentLocationPromise = null;
  function getCurrentLocation() {
    if (!currentLocationPromise) {
      currentLocationPromise = new Promise((resolve) => {
        if (!navigator.geolocation) {
          resolve(DEFAULT_LOCATION);
          return;
        }
        navigator.geolocation.getCurrentPosition(
          (pos) => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
          () => resolve(DEFAULT_LOCATION),
          { timeout: 5000 }
        );
      });
    }
    return currentLocationPromise;
  }
  // 기본적으로 현재 위치를 기준으로 검색하도록 페이지 진입 시 미리 위치 권한을 요청해둔다.
  getCurrentLocation();

  function renderPublicPage(data, { cache = true } = {}) {
    resultHeading.hidden = false;
    currentPage = data.page;
    publicSearchPageData = data;
    renderedSearchMode = "public";

    if (!data.items || data.items.length === 0) {
      renderEmpty("다른 검색어나 지역으로 다시 찾아보세요.");
      setStatus("조건에 맞는 맛집을 찾지 못했습니다.");
      paginationContainer.replaceChildren();
      if (cache) saveSearchCache("public", data.page, data);
      return;
    }

    results.replaceChildren(...data.items.map((item) => createResultCard(item)));
    count.textContent = String(data.totalCount);
    setStatus(`총 ${data.totalCount}개 중 ${data.page + 1}페이지 결과입니다.`);
    updatePagination(data);
    if (cache) saveSearchCache("public", data.page, data);
  }

  // 1. 일반 키워드/지역/카테고리 검색 (공공데이터 + 사업자 등록 음식점 통합)
  async function runPublicSearch(page = 0) {
    resultHeading.hidden = false;
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
    });
    const keyword = keywordInput.value.trim();
    const region = regionInput.value.trim();
    const category = categorySelect.value;
    if (keyword) params.set("keyword", keyword);
    if (region) params.set("region", region);
    if (category) params.set("category", category);

    updateUrlState(page);
    setStatus("검색 중입니다.");
    results.setAttribute("aria-busy", "true");

    try {
      const response = await Api.get(
        `/public/search/restaurants?${params.toString()}`,
        { auth: false },
      );
      results.removeAttribute("aria-busy");
      renderPublicPage(response.data);
    } catch (error) {
      results.removeAttribute("aria-busy");
      renderEmpty("검색 요청을 완료하지 못했습니다.");
      setStatus("검색 요청에 실패했습니다.", true);
    }
  }

  // ✨ AI 결과 렌더링 전용 페이징 함수 (12개 단위 슬라이싱)
  function renderAiPage(page = 0, { cache = true } = {}) {
    currentPage = page;
    renderedSearchMode = "ai";
    updateUrlState(page);

    const totalCount = aiRecommendationItems.length;
    const totalPages = Math.ceil(totalCount / PAGE_SIZE);
    const start = page * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    const pagedItems = aiRecommendationItems.slice(start, end);

    results.replaceChildren(...pagedItems.map((item) => createResultCard(item, true)));
    count.textContent = String(totalCount);
    const notices = [];
    const relaxedLabels = {
      CATEGORY: "카테고리",
      PRICE_DATA_UNAVAILABLE: "가격",
      RATING_DATA_UNAVAILABLE: "평점",
      DEMOGRAPHIC_PROFILE_UNAVAILABLE: "성별·연령",
      ATMOSPHERE_DATA_UNAVAILABLE: "분위기",
      SUITABILITY_DATA_UNAVAILABLE: "동반자·이용 목적",
      AMENITY_DATA_UNAVAILABLE: "편의시설",
      HOURS_DATA_UNAVAILABLE: "영업시간",
      MENU_ATTRIBUTE_DATA_UNAVAILABLE: "식단·메뉴 속성",
      AVAILABILITY_DATA_UNAVAILABLE: "예약·대기",
      QUALITY_GUARANTEE_DATA_UNAVAILABLE: "만족도 보장",
      MULTIPLE_LOCATIONS_DATA_UNAVAILABLE: "복수 위치",
      SEMANTIC_EVIDENCE_LOW: "의미 일치 근거",
    };
    // 분위기는 사실상 모든 검색에서 완화되어 안내로서 의미가 없다. 문구에서만 감추고
    // 응답의 relaxedFilters 자체는 그대로 둔다(디버깅에 계속 쓰인다).
    const hiddenRelaxedFilters = new Set(["ATMOSPHERE_DATA_UNAVAILABLE"]);
    const relaxed = (aiRecommendationMeta?.relaxedFilters || [])
      .filter((filter) => !hiddenRelaxedFilters.has(filter))
      .map((filter) => relaxedLabels[filter] || filter);
    if (relaxed.length > 0) {
      notices.push(`${relaxed.join(", ")} 조건은 데이터 부족으로 완화했습니다.`);
    }
    const resolvedLabels = {
      PARKING_VERIFIED_PUBLIC_DATA: "주차",
      PET_FRIENDLY_VERIFIED_PUBLIC_DATA: "애견동반",
      PLAYROOM_VERIFIED_PUBLIC_DATA: "놀이방",
      WIFI_VERIFIED_PUBLIC_DATA: "와이파이",
      MULTILINGUAL_MENU_VERIFIED_PUBLIC_DATA: "다국어 메뉴",
      DELIVERY_VERIFIED_PUBLIC_DATA: "배달",
      MIN_RATING_VERIFIED_FOODUCK_REVIEWS: "평점",
      MIN_RATING_VERIFIED_OFFICIAL_DATA: "공식 평점",
      MAX_PRICE_VERIFIED_PUBLIC_MENU: "메뉴 가격",
      LATE_HOURS_VERIFIED_PUBLIC_DATA: "늦은 시간 영업",
      EARLY_OPENING_VERIFIED_PUBLIC_DATA: "아침 영업",
      SUNDAY_HOURS_VERIFIED_PUBLIC_DATA: "일요일 영업",
      ALL_DAY_HOURS_VERIFIED_PUBLIC_DATA: "24시간 영업",
      VEGAN_MENU_LABEL_VERIFIED_PUBLIC_DATA: "비건 표기 메뉴",
      VEGETARIAN_MENU_LABEL_VERIFIED_PUBLIC_DATA: "채식 표기 메뉴",
      GLUTEN_FREE_MENU_LABEL_VERIFIED_PUBLIC_DATA: "글루텐프리 표기 메뉴",
      QUALITY_AWARD_VERIFIED_PUBLIC_DATA: "공식 어워드",
    };
    const resolved = (aiRecommendationMeta?.resolvedConstraints || [])
      .map((constraint) => resolvedLabels[constraint] || constraint);
    if (resolved.length > 0) {
      notices.push(`${resolved.join(", ")} 조건은 검증된 근거로 적용했습니다.`);
    }
    if (aiRecommendationMeta?.fallback) {
      notices.push("KURE 연결이 지연되어 TF-IDF 대체 모델로 계산했습니다.");
    }
    const noticeText = notices.length > 0 ? ` ${notices.join(" ")}` : "";
    setStatus(`총 ${totalCount}개 추천 결과 중 ${page + 1}페이지입니다.${noticeText}`);

    updatePagination({
      page: currentPage,
      totalPages: totalPages,
      hasPrevPage: currentPage > 0,
      hasNextPage: currentPage < totalPages - 1,
    });
    if (cache) {
      saveSearchCache("ai", page, {
        items: aiRecommendationItems.slice(0, MAX_CACHED_AI_ITEMS),
        meta: aiRecommendationMeta,
      });
    }
  }

  // 2. AI 자연어 맛집 추천 API
  async function runRecommendationSearch(page = 0) {
    resultHeading.hidden = false;

    let keyword = keywordInput.value.trim();
    const region = regionInput.value.trim();
    const category = categorySelect.value;

    let fullQuery = keyword;
    if (!fullQuery) {
      if (region || category) {
        fullQuery = `${region} ${category}`.trim();
      } else {
        fullQuery = "맛집 추천해줘";
      }
    } else {
      if (region && !fullQuery.includes(region)) fullQuery = `${region} ${fullQuery}`;
      if (category && !fullQuery.includes(category)) fullQuery = `${fullQuery} ${category}`;
    }

    setStatus("🤖 자연어 분석 및 맞춤 맛집을 계산 중입니다...");
    results.setAttribute("aria-busy", "true");

    try {
      const coords = await getCurrentLocation();

      const response = await Api.post("/recommendations/query", {
        query: fullQuery,
        latitude: coords.latitude,
        longitude: coords.longitude,
        radiusMeters: 2000,
        limit: aiResultLimit(fullQuery), // 검색어로 정해지는 1~300건, 프론트에서 12개씩 페이징
        // 상세 조건에서 고른 성별·연령대는 이번 검색에만 반영되는 값이다(회원 정보 변경 아님).
        gender: selectedGender(),
        ageGroup: selectedAgeGroup(),
      });

      results.removeAttribute("aria-busy");
      const data = response.data;

      if (!data || !data.items || data.items.length === 0) {
        aiRecommendationItems = [];
        aiRecommendationMeta = recommendationMeta(data);
        renderEmpty("다른 검색어나 지역으로 다시 찾아보세요.");
        setStatus("조건에 맞는 맛집을 찾지 못했습니다.");
        paginationContainer.replaceChildren();
        return;
      }

      // 결과 보관 후 12개씩 잘라서 출력 실행
      aiRecommendationItems = data.items;
      aiRecommendationMeta = recommendationMeta(data);
      renderAiPage(page);

    } catch (error) {
      results.removeAttribute("aria-busy");
      if (error.status === 401) {
        alert("세션이 만료되었습니다. 일반 검색으로 전환합니다.");
        updateAiToggleUI(false);
        storeAiModePreference(false);
        await runPublicSearch(0);
        return;
      }
      renderEmpty("검색 요청을 완료하지 못했습니다.");
      setStatus("검색 요청에 실패했습니다.", true);
    }
  }

  // "선택 안 함"은 개인화 가점을 아예 쓰지 않겠다는 뜻이라 서버에 값을 넘기지 않는다.
  function selectedGender() {
    const value = genderSelect ? genderSelect.value : "";
    return value && value !== "NONE" ? value : null;
  }

  function selectedAgeGroup() {
    const value = Number(ageGroupSelect ? ageGroupSelect.value : "");
    return Number.isInteger(value) && value > 0 ? value : null;
  }

  // 3. 통합 검색 및 페이지 변경 분기
  function runSearch(page = 0) {
    if (isAiMode) {
      // AI 모드이고 이미 결과가 캐싱되어 있다면 API 재호출 없이 페이지만 이동
      if (aiRecommendationItems.length > 0 && page !== 0) {
        renderAiPage(page);
      } else {
        runRecommendationSearch(page);
      }
    } else {
      runPublicSearch(page);
    }
  }

  // 상세페이지 등에서 돌아왔을 때, 같은 조건이면 API를 다시 부르지 않고 이전 결과를 그대로 되살린다.
  function restoreLastSearch() {
    const cache = readSearchCache();
    if (!cache || cache.key !== conditionKey()) return false;

    if (cache.mode === "ai") {
      const items = Array.isArray(cache.data?.items) ? cache.data.items : [];
      if (!items.length) return false;
      aiRecommendationItems = items;
      aiRecommendationMeta = cache.data?.meta || null;
      renderAiPage(Number(cache.page) || 0, { cache: false });
    } else {
      if (!cache.data?.items?.length) return false;
      renderPublicPage(cache.data, { cache: false });
    }
    setStatus(`${status.textContent} (마지막 검색 결과)`);
    restoreScrollPosition(cache);
    return true;
  }

  // -----------------------------------------------------------------
  // 이벤트 리스너 등록
  // -----------------------------------------------------------------

  if (aiSearchToggle) {
    aiSearchToggle.addEventListener("click", () => {
      // AI 모드 토글 시 기존 결과 초기화 후 검색 재실행
      aiRecommendationItems = [];
      aiRecommendationMeta = null;
      updateAiToggleUI(!isAiMode);
      storeAiModePreference(isAiMode);
      runSearch(0);
    });
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    aiRecommendationItems = []; // 새 검색 시 캐시 초기화
    aiRecommendationMeta = null;
    runSearch(0);
  });

  filterToggle.addEventListener("click", () => {
    setFilterPanelOpen(filterPanel.hidden);
  });

  // ✨ AI 모드에서 빠른 검색으로 채운 검색어인지 판단하기 위한 값들("OOO 추천해줘" 패턴 전체).
  const quickCategoryValues = Array.from(quickButtons).map((button) => button.dataset.quickCategory);

  quickButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const selectedCategory = button.dataset.quickCategory;
      const isAlreadyActive = button.classList.contains("is-active");

      if (isAlreadyActive) {
        button.classList.remove("is-active");
        categorySelect.value = "";
        if (isAiMode && keywordInput.value === `${selectedCategory} 추천해줘`) {
          keywordInput.value = "";
        }
      } else {
        categorySelect.value = selectedCategory;
        quickButtons.forEach((item) =>
          item.classList.toggle("is-active", item === button)
        );
        if (isAiMode) {
          const currentValue = keywordInput.value.trim();
          // 검색어가 비어 있거나, 이전에 다른 빠른 검색 버튼으로 채워둔 "OOO 추천해줘" 그대로면
          // 새로 누른 카테고리로 바꿔 채운다. 사용자가 직접 검색어를 수정했으면 건드리지 않는다.
          const wasQuickFilled = currentValue === ""
            || quickCategoryValues.some((category) => currentValue === `${category} 추천해줘`);
          if (wasQuickFilled) {
            keywordInput.value = `${selectedCategory} 추천해줘`;
          }
        }
      }
      aiRecommendationItems = [];
      aiRecommendationMeta = null;
      runSearch(0);
    });
  });

  categorySelect.addEventListener("change", () => {
    quickButtons.forEach((button) =>
      button.classList.toggle(
        "is-active",
        button.dataset.quickCategory === categorySelect.value
      )
    );
  });
function scrollToTop() {
  if (resultHeading) {
    resultHeading.scrollIntoView({ behavior: "smooth", block: "start" });
  } else {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
}

  // BFCache 복원과 일반 페이지 이동 모두에서 마지막으로 보던 결과를 보존한다.
  window.addEventListener("pagehide", persistVisibleSearchState);
  window.addEventListener("pageshow", (event) => {
    if (!event.persisted) return;
    const params = new URLSearchParams(window.location.search);
    if (params.get("ai") === "1" && !isAiMode) {
      updateAiToggleUI(true);
      storeAiModePreference(true);
    }
    restoreLastSearch();
  });

  // 초기화 및 실행
  setStatus("검색 조건을 입력해 주세요.");

  const initialParams = new URLSearchParams(window.location.search);
  const initialKeyword = initialParams.get("keyword") || initialParams.get("q") || "";
  const initialRegion = initialParams.get("region") || "";
  const initialCategory = initialParams.get("category") || "";
  const initialGender = initialParams.get("gender") || "";
  const initialAgeGroup = initialParams.get("ageGroup") || "";
  const parsedInitialPage = Number(initialParams.get("page"));
  const initialPage = Number.isInteger(parsedInitialPage)
    ? Math.max(parsedInitialPage, 0)
    : 0;

  if (recommendLink) {
    recommendLink.href = window.FooduckSession?.recommendationHref?.() || "/recommendation";
  }

  if (initialKeyword) keywordInput.value = initialKeyword;
  if (initialRegion) regionInput.value = initialRegion;
  if (initialCategory) {
    categorySelect.value = initialCategory;
    quickButtons.forEach((button) =>
      button.classList.toggle(
        "is-active",
        button.dataset.quickCategory === initialCategory,
      ),
    );
    setFilterPanelOpen(true);
  } else if (initialRegion) {
    setFilterPanelOpen(true);
  }
  if (genderSelect && initialGender) genderSelect.value = initialGender;
  if (ageGroupSelect && initialAgeGroup) ageGroupSelect.value = initialAgeGroup;
  if (initialGender || initialAgeGroup) setFilterPanelOpen(true);

  // AI 모드는 URL에 남아 있으면 그 값을, 없으면 사용자가 마지막으로 켜둔 상태를 따른다.
  const restoredAiMode = initialParams.has("ai")
    ? initialParams.get("ai") === "1"
    : readAiModePreference();
  if (restoredAiMode) {
    updateAiToggleUI(true);
    if (initialParams.get("ai") === "1") storeAiModePreference(true);
  }

  // 조건이 하나라도 있거나 AI 검색 상태로 돌아온 경우에만 결과를 되살리거나 다시 검색한다.
  const hasSearchCondition = Boolean(initialKeyword || initialRegion || initialCategory)
    || (isAiMode && initialParams.has("ai"));
  if (hasSearchCondition && !restoreLastSearch()) {
    runSearch(initialPage);
  }
})();
