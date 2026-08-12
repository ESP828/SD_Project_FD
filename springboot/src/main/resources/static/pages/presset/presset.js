(() => {
  const list = document.querySelector("#preset-list");
  const count = document.querySelector("#preset-count");
  const filters = document.querySelector("#preset-filters");
  const pagination = document.querySelector("#preset-pagination");
  const searchForm = document.querySelector("#preset-search-form");
  const keywordInput = document.querySelector("#preset-keyword");
  const sortSelect = document.querySelector("#preset-sort");
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

  function parseCategoryTokens(category) {
    const tokens = [];
    (category || "").split(",").forEach((part) => {
      const value = part.trim();
      if (value && !tokens.includes(value)) tokens.push(value);
    });
    return tokens;
  }

  function createCategoryBadges(category) {
    const group = element("div", "preset-category-group");
    const tokens = parseCategoryTokens(category);
    if (!tokens.length) {
      group.append(element("span", "preset-category", "테마"));
      return group;
    }
    tokens.forEach((token) => group.append(element("span", "preset-category", token)));
    return group;
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
    image.alt = `${title || "Presset"} 대표 이미지`;
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
      button.setAttribute("aria-label", preset.favoriteByCurrentUser ? "Presset 찜 해제" : "Presset 찜");
      button.textContent = preset.favoriteByCurrentUser ? "♥" : "♡";
      button.closest(".preset-card")?.querySelector("[data-favorite-count]")
        ?.replaceChildren(document.createTextNode(`저장 ${preset.favoriteCount}`));
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  function createCard(preset, rank) {
    const card = element("article", "preset-card");
    const visualLink = element("a", "preset-card-visual");
    visualLink.href = detailPath(preset.presetId);
    if (preset.imageUrl) {
      visualLink.append(safeImage(
        preset.imageUrl,
        preset.title,
        "preset-card-image",
        "preset-image-placeholder preset-card-image-placeholder",
      ));
    } else {
      visualLink.append(imagePlaceholder(
        "preset-image-placeholder preset-card-image-placeholder",
      ));
    }
    if (Number.isInteger(rank)) {
      visualLink.append(element("span", "preset-card-rank", String(rank)));
    }
    visualLink.append(createCategoryBadges(preset.category));

    const favorite = element("button", "preset-favorite-button", preset.favoriteByCurrentUser ? "♥" : "♡");
    favorite.type = "button";
    favorite.classList.toggle("is-active", preset.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(preset.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", preset.favoriteByCurrentUser ? "Presset 찜 해제" : "Presset 찜");
    favorite.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(favorite, preset);
    });
    card.append(visualLink, favorite);

    const body = element("div", "preset-card-body");
    const titleLink = element("a", "preset-card-title", preset.title || "이름 없는 Presset");
    titleLink.href = detailPath(preset.presetId);
    body.append(titleLink);

    const tags = element("div", "preset-tag-list");
    (preset.tags || []).forEach((tag) => tags.append(element("span", "preset-tag", `#${tag.tagName}`)));
    if (tags.childElementCount) body.append(tags);

    const thumbnails = element("div", "preset-thumbnails");
    (preset.thumbnailImageUrls || []).slice(0, 3).forEach((url) => {
      thumbnails.append(safeImage(
        url,
        preset.title,
        "preset-thumbnail",
        "preset-image-placeholder preset-thumbnail-placeholder",
      ));
    });
    if (thumbnails.childElementCount) body.append(thumbnails);

    const meta = element("div", "preset-card-meta");
    meta.append(
      element("span", "", `🍴 맛집 ${preset.restaurantCount || 0}곳`),
      element("span", "", `👁 조회 ${Number(preset.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    const saved = element("span", "", `🔖 저장 ${preset.favoriteCount || 0}`);
    saved.dataset.favoriteCount = "";
    meta.append(saved);
    body.append(meta);

    const actions = element("div", "preset-card-actions");
    const goDetail = element("a", "button button-primary preset-card-cta", "맛집 목록 보기 →");
    goDetail.href = detailPath(preset.presetId);
    const goMap = element("a", "button button-secondary preset-card-cta", "지도에서 보기");
    goMap.href = `/pages/map/index.html?presetId=${encodeURIComponent(preset.presetId)}`;
    actions.append(goDetail, goMap);
    body.append(actions);

    card.append(body);
    return card;
  }

  function renderFilters() {
    filters.replaceChildren();
    const all = element("button", "preset-filter", "▦ 전체");
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
      empty.append(element("h3", "", "조건에 맞는 Presset이 없습니다."), element("p", "", "다른 태그나 검색어를 선택해 보세요."));
      list.append(empty);
    } else {
      const base = (pageData.number || pageData.page || 0) * (pageData.size || state.size || presets.length);
      presets.forEach((preset, index) => list.append(createCard(preset, base + index + 1)));
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
    box.append(element("h3", "", "Presset을 불러오지 못했습니다."), element("p", "", error.message), retry);
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
