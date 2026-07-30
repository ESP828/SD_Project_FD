(() => {
  const form = document.getElementById("restaurant-search-form");
  const keywordInput = document.getElementById("search-keyword");
  const categorySelect = document.getElementById("search-category");
  const regionInput = document.getElementById("search-region");
  const results = document.getElementById("search-results");
  const count = document.getElementById("search-result-count");
  const status = document.getElementById("search-status");
  const previousButton = document.getElementById("search-prev");
  const nextButton = document.getElementById("search-next");
  const pageLabel = document.getElementById("search-page-label");
  const quickButtons = document.querySelectorAll("[data-quick-category]");

  let placesService;
  let activePagination;
  let sdkReady = false;

  function setStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle("is-error", isError);
  }

  function loadKakaoSdk(javascriptKey) {
    return new Promise((resolve, reject) => {
      if (window.kakao?.maps) {
        window.kakao.maps.load(resolve);
        return;
      }
      const script = document.createElement("script");
      script.async = true;
      script.src =
        `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(javascriptKey)}` +
        "&autoload=false&libraries=services";
      script.onload = () => window.kakao.maps.load(resolve);
      script.onerror = () => reject(new Error("카카오 검색 SDK를 불러오지 못했습니다."));
      document.head.appendChild(script);
    });
  }

  function markerFor(place) {
    const category = `${place.category_name || ""} ${place.category_group_name || ""}`;
    if (/카페|커피/.test(category)) return "category_cafe.svg";
    if (/디저트|제과|베이커리|아이스크림/.test(category)) return "category_dessert.svg";
    if (/중식|중국/.test(category)) return "category_chinese.svg";
    if (/일식|일본|초밥|스시/.test(category)) return "category_japanese.svg";
    if (/양식|이탈리안|프렌치|스테이크/.test(category)) return "category_western.svg";
    if (/패스트푸드|햄버거|피자/.test(category)) return "category_fastfood.svg";
    if (/술집|호프|주점|바/.test(category)) return "category_pub.svg";
    if (/한식|국밥|고기|분식/.test(category)) return "category_korean.svg";
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

  function createResultCard(place) {
    const article = document.createElement("article");
    article.className = "surface-card search-result-card";

    const visual = document.createElement("div");
    visual.className = "search-result-visual";
    const image = document.createElement("img");
    image.src = `/images/markers/${markerFor(place)}`;
    image.alt = "";
    image.setAttribute("aria-hidden", "true");
    visual.append(image);

    const body = document.createElement("div");
    body.className = "search-result-body";

    const category = document.createElement("span");
    category.className = "search-result-category";
    category.textContent =
      place.category_name?.split(" > ").slice(-1)[0] ||
      place.category_group_name ||
      "음식점";

    const title = document.createElement("h3");
    title.textContent = place.place_name;

    const address = createTextRow(
      "search-result-address",
      "location_on",
      place.road_address_name || place.address_name || "주소 정보 없음",
    );
    body.append(category, title, address);

    if (place.phone) {
      body.append(createTextRow("search-result-phone", null, place.phone));
    }

    const actions = document.createElement("div");
    actions.className = "search-result-actions";
    const mapLink = document.createElement("a");
    mapLink.className = "button button-primary";
    mapLink.href = `/pages/map/index.html?q=${encodeURIComponent(place.place_name)}`;
    mapLink.textContent = "지도에서 찾기";
    actions.append(mapLink);

    if (place.place_url?.startsWith("http")) {
      const detailLink = document.createElement("a");
      detailLink.className = "button button-secondary";
      detailLink.href = place.place_url;
      detailLink.target = "_blank";
      detailLink.rel = "noopener noreferrer";
      detailLink.textContent = "상세보기";
      actions.append(detailLink);
    }
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

  function updatePagination(pagination) {
    activePagination = pagination;
    const current = pagination.current;
    const total = Math.max(1, pagination.last);
    pageLabel.textContent = `${current} / ${total}`;
    previousButton.disabled = !pagination.hasPrevPage;
    nextButton.disabled = !pagination.hasNextPage;
  }

  function runSearch() {
    if (!sdkReady || !placesService) {
      setStatus("카카오 검색 기능을 준비하고 있습니다.");
      return;
    }
    const parts = [
      regionInput.value.trim(),
      categorySelect.value,
      keywordInput.value.trim(),
    ].filter(Boolean);
    const query = parts.length ? parts.join(" ") : "서울 맛집";

    setStatus(`“${query}” 검색 중입니다.`);
    results.setAttribute("aria-busy", "true");
    placesService.keywordSearch(query, (places, kakaoStatus, pagination) => {
      results.removeAttribute("aria-busy");
      if (kakaoStatus === kakao.maps.services.Status.ZERO_RESULT) {
        renderEmpty("다른 검색어나 지역으로 다시 찾아보세요.");
        setStatus("조건에 맞는 장소를 찾지 못했습니다.");
        previousButton.disabled = true;
        nextButton.disabled = true;
        pageLabel.textContent = "1 / 1";
        return;
      }
      if (kakaoStatus !== kakao.maps.services.Status.OK) {
        renderEmpty("Kakao Places 요청을 완료하지 못했습니다.");
        setStatus("검색 요청에 실패했습니다.", true);
        return;
      }
      results.replaceChildren(...places.map(createResultCard));
      count.textContent = String(places.length);
      setStatus(`${pagination.current}페이지 결과입니다.`);
      updatePagination(pagination);
    });
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch();
  });

  quickButtons.forEach((button) => {
    button.addEventListener("click", () => {
      categorySelect.value = button.dataset.quickCategory;
      quickButtons.forEach((item) =>
        item.classList.toggle("is-active", item === button),
      );
      runSearch();
    });
  });

  categorySelect.addEventListener("change", () => {
    quickButtons.forEach((button) =>
      button.classList.toggle(
        "is-active",
        button.dataset.quickCategory === categorySelect.value,
      ),
    );
  });

  previousButton.addEventListener("click", () => {
    if (activePagination?.hasPrevPage) {
      activePagination.prevPage();
    }
  });
  nextButton.addEventListener("click", () => {
    if (activePagination?.hasNextPage) {
      activePagination.nextPage();
    }
  });

  (async () => {
    try {
      const response = await Api.get("/public/map/config", { auth: false });
      if (!response.data?.configured || !response.data.javascriptKey) {
        throw new Error("카카오 JavaScript 키가 설정되지 않았습니다.");
      }
      await loadKakaoSdk(response.data.javascriptKey);
      placesService = new kakao.maps.services.Places();
      sdkReady = true;
      setStatus("검색 조건을 입력해 주세요.");

      const query = new URLSearchParams(window.location.search).get("q");
      if (query) {
        keywordInput.value = query;
        runSearch();
      }
    } catch (error) {
      setStatus(error.message, true);
      form.querySelector("button[type='submit']").disabled = true;
    }
  })();
})();
