(() => {
  const PAGE_SIZE = 12;

  const form = document.getElementById("restaurant-search-form");
  const keywordInput = document.getElementById("search-keyword");
  const categorySelect = document.getElementById("search-category");
  const regionInput = document.getElementById("search-region");
  const filterToggle = document.getElementById("search-filter-toggle");
  const filterPanel = document.getElementById("search-filter-panel");

  // ✨ AI 토글 버튼 및 제출 버튼 요소
  const aiSearchToggle = document.getElementById("ai-search-toggle");
  const searchSubmitBtn = document.getElementById("search-submit");
  const submitBtnText = document.getElementById("submit-btn-text");

  const results = document.getElementById("search-results");
  const resultHeading = document.getElementById("result-heading");
  const count = document.getElementById("search-result-count");
  const status = document.getElementById("search-status");
  const previousButton = document.getElementById("search-prev");
  const nextButton = document.getElementById("search-next");
  const pageLabel = document.getElementById("search-page-label");
  const quickButtons = document.querySelectorAll("[data-quick-category]");

  let isAiMode = false;
  let currentPage = 0;

  // 💡 AI 추천 전체 결과를 저장해두는 전역 배열
  let aiRecommendationItems = [];

  const DEFAULT_LOCATION = { latitude: 37.4979, longitude: 127.0276 };

  function setFilterPanelOpen(isOpen) {
    filterPanel.hidden = !isOpen;
    filterToggle.setAttribute("aria-expanded", String(isOpen));
  }

  function setStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle("is-error", isError);
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

  // 빠른 검색 버튼 비활성화/활성화 처리
  quickButtons.forEach((button) => {
    button.disabled = isAiMode;
    button.classList.toggle("is-disabled", isAiMode);
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
      const distanceInfo = createTextRow(
        "search-result-distance",
        "near_me",
        `약 ${(item.distanceMeters / 1000).toFixed(1)}km 거리 (매칭점수 ${(item.score * 100).toFixed(0)}점)`,
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

    const sourceType = isRecommendation
      ? (item.sourceType || "PUBLIC").toLowerCase()
      : "public";
    const sourceId = isRecommendation ? item.sourceId : item.id;
    const restaurantName = isRecommendation ? item.restaurantName : item.name;
    const detailLink = document.createElement("a");
    detailLink.className = "button button-primary";
    detailLink.href = `/restaurant/detail?source=${sourceType}&id=${sourceId}`;
    detailLink.textContent = "상세보기";

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

  // 페이징 UI 업데이트 (일반 / AI 통합 지원)
  function updatePagination(response) {
    const current = response.page + 1;
    const total = Math.max(1, response.totalPages);
    pageLabel.textContent = `${current} / ${total}`;
    previousButton.disabled = !response.hasPrevPage;
    nextButton.disabled = !response.hasNextPage;
  }

function updateUrlState(page = 0) {
  const params = new URLSearchParams();
  const keyword = keywordInput.value.trim();
  const region = regionInput.value.trim();
  const category = categorySelect.value;

  if (keyword) params.set("keyword", keyword);
  if (region) params.set("region", region);

  // ✨ AI 모드가 아닐 때만 category를 URL 파라미터에 추가
  if (!isAiMode && category) {
    params.set("category", category);
  }

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

  // 1. 일반 키워드/지역/카테고리 검색
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
        `/public/map/restaurants/search?${params.toString()}`,
        { auth: false },
      );
      const data = response.data;
      results.removeAttribute("aria-busy");
      currentPage = data.page;

      if (data.items.length === 0) {
        renderEmpty("다른 검색어나 지역으로 다시 찾아보세요.");
        setStatus("조건에 맞는 맛집을 찾지 못했습니다.");
        previousButton.disabled = true;
        nextButton.disabled = true;
        pageLabel.textContent = "1 / 1";
        return;
      }

      results.replaceChildren(...data.items.map((item) => createResultCard(item)));
      count.textContent = String(data.totalCount);
      setStatus(`총 ${data.totalCount}개 중 ${data.page + 1}페이지 결과입니다.`);
      updatePagination(data);
    } catch (error) {
      results.removeAttribute("aria-busy");
      renderEmpty("검색 요청을 완료하지 못했습니다.");
      setStatus("검색 요청에 실패했습니다.", true);
    }
  }

  // ✨ AI 결과 렌더링 전용 페이징 함수 (12개 단위 슬라이싱)
  function renderAiPage(page = 0) {
    currentPage = page;
    updateUrlState(page);

    const totalCount = aiRecommendationItems.length;
    const totalPages = Math.ceil(totalCount / PAGE_SIZE);
    const start = page * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    const pagedItems = aiRecommendationItems.slice(start, end);

    results.replaceChildren(...pagedItems.map((item) => createResultCard(item, true)));
    count.textContent = String(totalCount);
    setStatus(`총 ${totalCount}개 추천 결과 중 ${page + 1}페이지입니다.`);

    updatePagination({
      page: currentPage,
      totalPages: totalPages,
      hasPrevPage: currentPage > 0,
      hasNextPage: currentPage < totalPages - 1,
    });
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
        limit: 1200, // 👈 넉넉히 가져와서 프론트에서 12개씩 페이징
      });

      results.removeAttribute("aria-busy");
      const data = response.data;

      if (!data || !data.items || data.items.length === 0) {
        aiRecommendationItems = [];
        renderEmpty("다른 검색어나 지역으로 다시 찾아보세요.");
        setStatus("조건에 맞는 맛집을 찾지 못했습니다.");
        previousButton.disabled = true;
        nextButton.disabled = true;
        pageLabel.textContent = "1 / 1";
        return;
      }

      // 결과 보관 후 12개씩 잘라서 출력 실행
      aiRecommendationItems = data.items;
      renderAiPage(page);

    } catch (error) {
      results.removeAttribute("aria-busy");
      if (error.status === 401) {
        alert("세션이 만료되었습니다. 일반 검색으로 전환합니다.");
        updateAiToggleUI(false);
        await runPublicSearch(0);
        return;
      }
      renderEmpty("검색 요청을 완료하지 못했습니다.");
      setStatus("검색 요청에 실패했습니다.", true);
    }
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

  // -----------------------------------------------------------------
  // 이벤트 리스너 등록
  // -----------------------------------------------------------------

  if (aiSearchToggle) {
    aiSearchToggle.addEventListener("click", () => {
      const isAuthenticated = Boolean(window.FooduckSession?.authenticated);

      if (!isAuthenticated) {
        if (confirm("AI 추천 검색 기능은 로그인 후 이용하실 수 있습니다.\n로그인 페이지로 이동하시겠습니까?")) {
          window.location.href = "/auth/login";
        }
        return;
      }

      // AI 모드 토글 시 기존 결과 초기화 후 검색 재실행
      aiRecommendationItems = [];
      updateAiToggleUI(!isAiMode);
      runSearch(0);
    });
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    aiRecommendationItems = []; // 새 검색 시 캐시 초기화
    runSearch(0);
  });

  filterToggle.addEventListener("click", () => {
    setFilterPanelOpen(filterPanel.hidden);
  });

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
        if (isAiMode && !keywordInput.value.trim()) {
          keywordInput.value = `${selectedCategory} 추천해줘`;
        }
      }
      aiRecommendationItems = [];
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

  // 이전 페이지 버튼 클릭
  previousButton.addEventListener("click", () => {
    if (currentPage > 0) {
      runSearch(currentPage - 1);
      scrollToTop();
    }
  });

  // 다음 페이지 버튼 클릭
  nextButton.addEventListener("click", () => {
    runSearch(currentPage + 1);
    scrollToTop();
  });

  // 초기화 및 실행
  setStatus("검색 조건을 입력해 주세요.");

  const initialParams = new URLSearchParams(window.location.search);
  const initialKeyword = initialParams.get("keyword") || initialParams.get("q") || "";
  const initialRegion = initialParams.get("region") || "";
  const initialCategory = initialParams.get("category") || "";
  const parsedInitialPage = Number(initialParams.get("page"));
  const initialPage = Number.isInteger(parsedInitialPage)
    ? Math.max(parsedInitialPage, 0)
    : 0;

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

  if (initialKeyword || initialRegion || initialCategory) {
    runSearch(initialPage);
  }
})();
