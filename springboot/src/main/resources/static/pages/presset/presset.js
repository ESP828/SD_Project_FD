(() => {
  const list = document.querySelector("#preset-list");
  const count = document.querySelector("#preset-count");
  const filters = document.querySelector("#preset-filters");
  const pagination = document.querySelector("#preset-pagination");
  const searchForm = document.querySelector("#preset-search-form");
  const keywordInput = document.querySelector("#preset-keyword");
  const sortSelect = document.querySelector("#preset-sort");
  const filterToggle = document.querySelector("#preset-filter-toggle");
  const filterToggleText = document.querySelector("[data-filter-toggle-text]");
  const filterPanel = document.querySelector("#preset-filter-panel");
  const registerLink = document.querySelector("[data-preset-register-link]");
  const toast = document.querySelector("#preset-toast");
  const registerPath = "/pages/presset/register.html";
  const createdMessageKey = "fooduck:preset-created";
  const requestedSort = new URLSearchParams(location.search).get("sort");
  const initialSort = ["popular", "latest", "favorite"].includes(requestedSort)
    ? requestedSort
    : "popular";
  const state = {
    page: 0,
    size: 12,
    sort: initialSort,
    tagId: null,
    keyword: "",
    tags: [],
  };

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function detailPath(presetId) {
    return `/pages/presset/detail.html?presetId=${encodeURIComponent(presetId)}`;
  }

  function imagePlaceholder(className = "preset-image-placeholder") {
    const placeholder = element("span", className, "이미지 없음");
    placeholder.setAttribute("aria-label", "등록 이미지 없음");
    return placeholder;
  }

  function safeImage(source, title, className, placeholderClassName) {
    const image = new Image();
    image.className = className;
    image.src = source;
    image.alt = `${title || "보물지도"} 대표 이미지`;
    image.loading = "lazy";
    image.addEventListener("error", () => {
      image.replaceWith(imagePlaceholder(placeholderClassName));
    }, { once: true });
    return image;
  }

  function login() {
    const next = `${location.pathname}${location.search}`;
    location.assign(`/pages/auth/login.html?next=${encodeURIComponent(next)}`);
  }

  function showCreatedMessage() {
    let message = null;
    try {
      message = sessionStorage.getItem(createdMessageKey);
      sessionStorage.removeItem(createdMessageKey);
    } catch (_error) {
      return;
    }
    if (!message || !toast) return;
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => {
      toast.hidden = true;
    }, 3200);
  }

  async function toggleFavorite(button, preset) {
    if (!window.FooduckSession?.authenticated) {
      login();
      return;
    }
    button.disabled = true;
    try {
      const payload = preset.favoriteByCurrentUser
        ? await Api.delete(`/presets/${preset.presetId}/favorite`)
        : await Api.post(`/presets/${preset.presetId}/favorite`);
      preset.favoriteByCurrentUser = Boolean(payload.data?.favoriteByCurrentUser);
      preset.favoriteCount = Number(payload.data?.favoriteCount) || 0;
      button.classList.toggle("is-active", preset.favoriteByCurrentUser);
      button.setAttribute("aria-pressed", String(preset.favoriteByCurrentUser));
      button.setAttribute("aria-label", preset.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
      button.textContent = preset.favoriteByCurrentUser ? "♥" : "♡";
      button.closest(".preset-card")?.querySelector("[data-favorite-count]")
        ?.replaceChildren(document.createTextNode(`♡ 저장 ${preset.favoriteCount}`));
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  function restaurantDetailPath(restaurantId) {
    return `/pages/restaurant/detail.html?source=owned&id=${encodeURIComponent(restaurantId)}`;
  }

  function createRouteMap(preset) {
    if (preset.imageUrl) {
      const map = element("a", "preset-card-map");
      map.href = detailPath(preset.presetId);
      map.setAttribute("aria-label", `${preset.title || "보물지도"} 상세 보기`);
      map.append(safeImage(
        preset.imageUrl,
        preset.title,
        "preset-card-cover",
        "preset-image-placeholder preset-card-map-placeholder",
      ));
      return map;
    }

    const map = element("div", "preset-card-map");

    const urls = Array.isArray(preset.thumbnailImageUrls) ? preset.thumbnailImageUrls.slice(0, 3) : [];
    const restaurantIds = Array.isArray(preset.thumbnailRestaurantIds) ? preset.thumbnailRestaurantIds : [];
    const stops = urls.map((url, index) => ({ url, restaurantId: restaurantIds[index] }));
    if (!stops.length) {
      map.append(imagePlaceholder("preset-image-placeholder preset-card-map-placeholder"));
      return map;
    }

    const anchors = [[18, 26], [58, 50], [18, 78]];
    if (stops.length > 1) {
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.setAttribute("class", "preset-card-map-path");
      svg.setAttribute("viewBox", "0 0 100 100");
      svg.setAttribute("preserveAspectRatio", "none");
      const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
      polyline.setAttribute("points", anchors.slice(0, stops.length).map(([x, y]) => `${x},${y}`).join(" "));
      svg.append(polyline);
      map.append(svg);
    }

    stops.forEach(({ url, restaurantId }, index) => {
      const stop = element(
        restaurantId ? "a" : "div",
        `preset-card-map-stop preset-card-map-stop--${index + 1}`,
      );
      if (restaurantId) {
        stop.href = restaurantDetailPath(restaurantId);
        stop.setAttribute("aria-label", "식당 상세 보기");
        stop.addEventListener("click", (event) => event.stopPropagation());
      }
      stop.append(element("span", "preset-card-map-badge", String(index + 1)));
      stop.append(safeImage(
        url,
        preset.title,
        "preset-card-map-thumb",
        "preset-image-placeholder preset-card-map-thumb-placeholder",
      ));
      map.append(stop);
    });

    return map;
  }

  function createCard(preset) {
    const card = element("article", "preset-card");
    card.append(createRouteMap(preset));

    const favorite = element("button", "preset-favorite-button", preset.favoriteByCurrentUser ? "♥" : "♡");
    favorite.type = "button";
    favorite.classList.toggle("is-active", preset.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(preset.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", preset.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
    favorite.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(favorite, preset);
    });
    card.append(favorite);

    const body = element("div", "preset-card-body");

    const badge = element("span", "preset-card-badge", `📍 ${preset.category || "보물지도"}`);
    body.append(badge);

    const titleLink = element("a", "preset-card-title", preset.title || "이름 없는 보물지도");
    titleLink.href = detailPath(preset.presetId);
    body.append(titleLink);

    const tags = element("div", "preset-card-chip-list");
    (preset.tags || []).slice(0, 3).forEach((tag) => tags.append(element("span", "preset-card-chip", tag.tagName)));
    if (tags.childElementCount) body.append(tags);

    body.append(element("div", "preset-card-divider"));

    const meta = element("div", "preset-card-meta");
    meta.append(element("span", "", `🍴 맛집 ${preset.restaurantCount || 0}곳`));
    meta.append(element("span", "", `👁 조회 ${Number(preset.viewCount || 0).toLocaleString("ko-KR")}`));
    if (Number.isFinite(preset.favoriteCount)) {
      const favoriteCount = element(
        "span",
        "",
        `♡ 저장 ${Number(preset.favoriteCount || 0).toLocaleString("ko-KR")}`,
      );
      favoriteCount.setAttribute("data-favorite-count", "");
      meta.append(favoriteCount);
    }
    body.append(meta);

    const actions = element("div", "preset-card-actions");
    const goDetail = element("a", "button button-primary preset-card-cta", "둘러보기 →");
    goDetail.href = detailPath(preset.presetId);
    const goMap = element("a", "button button-secondary preset-card-map-link", "지도에서 보기");
    const mapQuery = new URLSearchParams({ presetId: preset.presetId });
    if (preset.isOwner) mapQuery.set("edit", "1");
    goMap.href = `/pages/map/index.html?${mapQuery.toString()}`;
    actions.append(goDetail, goMap);
    body.append(actions);

    card.append(body);

    card.addEventListener("click", (event) => {
      if (event.target.closest("a, button")) return;
      location.assign(detailPath(preset.presetId));
    });

    return card;
  }

  function renderFilters() {
    filters.replaceChildren();
    const all = element("button", "preset-filter", "전체");
    all.type = "button";
    all.setAttribute("aria-pressed", String(state.tagId === null));
    all.addEventListener("click", () => selectTag(null));
    filters.append(all);
    state.tags.forEach((tag) => {
      const button = element("button", "preset-filter", tag.tagName);
      button.type = "button";
      button.setAttribute("aria-pressed", String(state.tagId === tag.tagId));
      button.addEventListener("click", () => selectTag(tag.tagId));
      filters.append(button);
    });
  }

  function selectTag(tagId) {
    state.tagId = tagId;
    state.page = 0;
    renderFilters();
    loadPresets();
  }

  function renderPagination(pageData) {
    pagination.replaceChildren();
    if ((pageData.totalPages || 0) <= 1) return;
    const add = (label, page, disabled, current = false) => {
      const button = element("button", "preset-page-button", label);
      button.type = "button";
      button.disabled = disabled;
      if (current) button.setAttribute("aria-current", "page");
      button.addEventListener("click", () => {
        state.page = page;
        loadPresets();
        document.querySelector("#preset-collection")?.scrollIntoView({ behavior: "smooth" });
      });
      pagination.append(button);
    };
    add("이전", Math.max(0, pageData.page - 1), pageData.first);
    for (let page = 0; page < pageData.totalPages; page += 1) {
      add(String(page + 1), page, false, page === pageData.page);
    }
    add("다음", pageData.page + 1, pageData.last);
  }

  function renderPage(pageData) {
    const presets = Array.isArray(pageData.content) ? pageData.content : [];
    list.replaceChildren();
    list.setAttribute("aria-busy", "false");
    count.textContent = `총 ${Number(pageData.totalElements || 0).toLocaleString("ko-KR")}개의 맛집 모음`;
    list.classList.toggle("preset-list--state", presets.length === 0);
    if (!presets.length) {
      const empty = element("div", "preset-state preset-state--surface");
      empty.append(element("h3", "", "조건에 맞는 보물지도가 없습니다."), element("p", "", "다른 태그나 검색어를 선택해 보세요."));
      list.append(empty);
    } else {
      presets.forEach((preset) => list.append(createCard(preset)));
    }
    renderPagination(pageData);
  }

  function renderError(error) {
    list.replaceChildren();
    list.classList.add("preset-list--state");
    list.setAttribute("aria-busy", "false");
    const retry = element("button", "button button-secondary", "다시 시도");
    retry.type = "button";
    retry.addEventListener("click", loadPresets);
    const box = element("div", "preset-state preset-state--surface");
    box.append(element("h3", "", "보물지도를 불러오지 못했습니다."), element("p", "", error.message), retry);
    list.append(box);
  }

  async function loadPresets() {
    list.setAttribute("aria-busy", "true");
    const params = new URLSearchParams({ page: state.page, size: state.size, sort: state.sort });
    if (state.tagId) params.set("tagId", state.tagId);
    if (state.keyword) params.set("keyword", state.keyword);
    try {
      const payload = await Api.get(`/presets?${params.toString()}`);
      renderPage(payload.data || {});
    } catch (error) {
      renderError(error);
    }
  }

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.keyword = keywordInput.value.trim();
    state.page = 0;
    loadPresets();
  });
  sortSelect.addEventListener("change", () => {
    state.sort = sortSelect.value;
    state.page = 0;
    loadPresets();
  });
  filterToggle?.addEventListener("click", () => {
    const expanded = filterToggle.getAttribute("aria-expanded") === "true";
    filterToggle.setAttribute("aria-expanded", String(!expanded));
    filterPanel.hidden = expanded;
    if (filterToggleText) {
      filterToggleText.textContent = expanded ? "상세 조건" : "상세 조건 접기";
    }
  });

  sortSelect.value = state.sort;
  if (registerLink && !window.FooduckSession?.authenticated) {
    registerLink.href =
      `/pages/auth/login.html?next=${encodeURIComponent(registerPath)}`;
  }
  showCreatedMessage();

  Promise.all([Api.get("/presets/tags"), Promise.resolve()])
    .then(([payload]) => {
      state.tags = Array.isArray(payload.data) ? payload.data : [];
      renderFilters();
    })
    .catch(() => renderFilters())
    .finally(loadPresets);
})();
