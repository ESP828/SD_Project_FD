(() => {
  const PAGE_SIZE = 12;

  const form = document.getElementById("restaurant-search-form");
  const keywordInput = document.getElementById("search-keyword");
  const categorySelect = document.getElementById("search-category");
  const regionInput = document.getElementById("search-region");
  const filterToggle = document.getElementById("search-filter-toggle");
  const filterPanel = document.getElementById("search-filter-panel");
  const results = document.getElementById("search-results");
  const resultHeading = document.getElementById("result-heading");
  const count = document.getElementById("search-result-count");
  const status = document.getElementById("search-status");
  const previousButton = document.getElementById("search-prev");
  const nextButton = document.getElementById("search-next");
  const pageLabel = document.getElementById("search-page-label");
  const quickButtons = document.querySelectorAll("[data-quick-category]");

  let currentPage = 0;

  function setFilterPanelOpen(isOpen) {
    filterPanel.hidden = !isOpen;
    filterToggle.setAttribute("aria-expanded", String(isOpen));
  }

  function setStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle("is-error", isError);
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
    category.textContent = place.categoryName || "음식점";

    const title = document.createElement("h3");
    title.textContent = place.name;

    const address = createTextRow(
      "search-result-address",
      "location_on",
      place.roadAddress || place.lotAddress || "주소 정보 없음",
    );
    body.append(category, title, address);

    const actions = document.createElement("div");
    actions.className = "search-result-actions";
    const detailLink = document.createElement("a");
    detailLink.className = "button button-primary";
    detailLink.href = `/pages/restaurant/detail.html?source=public&id=${place.id}`;
    detailLink.textContent = "상세보기";
    const mapLink = document.createElement("a");
    mapLink.className = "button button-secondary";
    mapLink.href = `/pages/map/index.html?q=${encodeURIComponent(place.name)}`;
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

  function updatePagination(response) {
    const current = response.page + 1;
    const total = Math.max(1, response.totalPages);
    pageLabel.textContent = `${current} / ${total}`;
    previousButton.disabled = !response.hasPrevPage;
    nextButton.disabled = !response.hasNextPage;
  }

  async function runSearch(page = 0) {
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

    window.history.replaceState(null, "", `${window.location.pathname}?${params.toString()}`);

    setStatus("검색 중입니다.");
    results.setAttribute("aria-busy", "true");

    try {
      const response = await Api.get(`/public/map/restaurants/search?${params.toString()}`, { auth: false });
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

      results.replaceChildren(...data.items.map(createResultCard));
      count.textContent = String(data.totalCount);
      setStatus(`총 ${data.totalCount}개 중 ${data.page + 1}페이지 결과입니다.`);
      updatePagination(data);
    } catch (error) {
      results.removeAttribute("aria-busy");
      renderEmpty("검색 요청을 완료하지 못했습니다.");
      setStatus("검색 요청에 실패했습니다.", true);
    }
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch(0);
  });

  filterToggle.addEventListener("click", () => {
    setFilterPanelOpen(filterPanel.hidden);
  });

  quickButtons.forEach((button) => {
    button.addEventListener("click", () => {
      categorySelect.value = button.dataset.quickCategory;
      quickButtons.forEach((item) =>
        item.classList.toggle("is-active", item === button),
      );
      runSearch(0);
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
    if (currentPage > 0) {
      runSearch(currentPage - 1);
    }
  });
  nextButton.addEventListener("click", () => {
    runSearch(currentPage + 1);
  });

  setStatus("검색 조건을 입력해 주세요.");

  const initialParams = new URLSearchParams(window.location.search);
  const initialKeyword = initialParams.get("keyword") || initialParams.get("q") || "";
  const initialRegion = initialParams.get("region") || "";
  const initialCategory = initialParams.get("category") || "";
  const initialPage = Number(initialParams.get("page")) || 0;

  if (initialKeyword) keywordInput.value = initialKeyword;
  if (initialRegion) regionInput.value = initialRegion;
  if (initialCategory) {
    categorySelect.value = initialCategory;
    quickButtons.forEach((button) =>
      button.classList.toggle("is-active", button.dataset.quickCategory === initialCategory),
    );
    setFilterPanelOpen(true);
  } else if (initialRegion) {
    setFilterPanelOpen(true);
  }

  if (initialKeyword || initialRegion || initialCategory) {
    runSearch(initialPage);
  }
})();
