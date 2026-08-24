(() => {
  const list = document.querySelector("#preset-list");
  const count = document.querySelector("#preset-count");
  const heading = document.querySelector("#preset-heading");
  const pagination = document.querySelector("#preset-pagination");
  const searchForm = document.querySelector("#preset-search-form");
  const keywordInput = document.querySelector("#preset-keyword");
  const tagSelect = document.querySelector("#preset-tag-select");
  const sortSelect = document.querySelector("#preset-sort-select");
  const searchReset = document.querySelector("#preset-search-reset");
  const viewTabs = Array.from(document.querySelectorAll("[data-preset-sort]"));
  const registerLinks = Array.from(document.querySelectorAll("[data-preset-register-link]"));
  const toast = document.querySelector("#preset-toast");
  const registerPath = "/presset/register";
  const createdMessageKey = "fooduck:preset-created";
  const initialParams = new URLSearchParams(location.search);
  const requestedSort = initialParams.get("sort");
  const initialSort = ["popular", "latest", "favorite"].includes(requestedSort)
    ? requestedSort
    : "latest";
  const requestedTagId = Number.parseInt(initialParams.get("tagId"), 10);
  const requestedPage = Number.parseInt(initialParams.get("page"), 10);
  const state = {
    page: Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage - 1 : 0,
    size: 12,
    sort: initialSort,
    tagId: Number.isSafeInteger(requestedTagId) && requestedTagId > 0 ? requestedTagId : null,
    keyword: normalizeKeyword(initialParams.get("keyword")),
    tags: [],
  };
  let requestGeneration = 0;

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function normalizeKeyword(value) {
    return String(value || "")
      .replace(/#+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 100);
  }

  function listUrlFromState() {
    const params = new URLSearchParams();
    if (state.sort !== "latest") params.set("sort", state.sort);
    if (state.tagId) params.set("tagId", String(state.tagId));
    if (state.keyword) params.set("keyword", state.keyword);
    if (state.page > 0) params.set("page", String(state.page + 1));
    const query = params.toString();
    return `/presset${query ? `?${query}` : ""}`;
  }

  function syncListUrl(historyMode = "replace") {
    const nextUrl = listUrlFromState();
    const currentUrl = `${location.pathname}${location.search}`;
    if (nextUrl === currentUrl) return;
    if (historyMode === "push") {
      history.pushState(null, "", nextUrl);
      return;
    }
    history.replaceState(null, "", nextUrl);
  }

  function syncSearchControls() {
    keywordInput.value = state.keyword;
    tagSelect.value = state.tagId === null ? "" : String(state.tagId);
    sortSelect.value = state.sort;
  }

  function syncViewTabs() {
    let activeLabel = "";
    viewTabs.forEach((tab) => {
      const active = tab.dataset.presetSort === state.sort;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
      tab.tabIndex = active ? 0 : -1;
      if (active) activeLabel = tab.textContent.trim();
    });
    // 목록 패널 제목은 선택한 보기 탭의 이름을 그대로 사용한다.
    if (heading && activeLabel) heading.textContent = activeLabel;
  }

  function restoreStateFromLocation() {
    const params = new URLSearchParams(location.search);
    const sort = params.get("sort");
    const tagId = Number.parseInt(params.get("tagId"), 10);
    const page = Number.parseInt(params.get("page"), 10);
    state.sort = ["popular", "latest", "favorite"].includes(sort) ? sort : "latest";
    state.tagId = Number.isSafeInteger(tagId) && tagId > 0 ? tagId : null;
    state.keyword = normalizeKeyword(params.get("keyword"));
    state.page = Number.isSafeInteger(page) && page > 0 ? page - 1 : 0;
    syncSearchControls();
    syncViewTabs();
  }

  function detailPath(presetId) {
    return `/presset/detail?presetId=${encodeURIComponent(presetId)}`;
  }

  /**
   * 찜 버튼 안에 하트 아이콘을 넣는다.
   * 찜/해제 상태는 클래스와 aria 속성으로만 표현하고 버튼 내용은 건드리지 않는다.
   * (textContent를 갈아치우면 아이콘 SVG가 지워진다.)
   */
  function appendFavoriteIcon(button) {
    const icon = element("span", "material-symbols-rounded", "favorite");
    icon.setAttribute("aria-hidden", "true");
    window.FooduckIcons?.set(icon, "favorite");
    button.append(icon);
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
    image.decoding = "async";
    image.fetchPriority = "low";
    image.addEventListener("error", () => {
      image.replaceWith(imagePlaceholder(placeholderClassName));
    }, { once: true });
    return image;
  }

  function login() {
    const next = `${location.pathname}${location.search}`;
    location.assign(`/auth/login?next=${encodeURIComponent(next)}`);
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
      button.closest(".preset-card")?.querySelector("[data-favorite-count]")
        ?.replaceChildren(document.createTextNode(`♡ 저장 ${preset.favoriteCount.toLocaleString("ko-KR")}`));
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  function categoryTokens(category) {
    return String(category || "")
      .split(",")
      .map((token) => token.trim())
      .filter(Boolean);
  }

  function cardTagNames(preset) {
    const names = categoryTokens(preset.category);
    (Array.isArray(preset.tags) ? preset.tags : []).forEach((tag) => {
      const name = String(tag?.tagName || "").trim();
      if (name && !names.includes(name)) names.push(name);
    });
    return names;
  }

  function createCardVisual(preset) {
    const visual = element("a", "preset-card-visual");
    visual.href = detailPath(preset.presetId);
    visual.setAttribute("aria-label", `${preset.title || "보물지도"} 상세 보기`);
    const thumbnail = (Array.isArray(preset.thumbnailImageUrls) ? preset.thumbnailImageUrls : [])
      .find((url) => typeof url === "string" && url.trim());
    const imageSource = preset.imageUrl || thumbnail;
    if (imageSource) {
      visual.append(safeImage(
        imageSource,
        preset.title,
        "preset-card-cover",
        "preset-image-placeholder preset-card-visual-placeholder",
      ));
    } else {
      visual.append(imagePlaceholder("preset-image-placeholder preset-card-visual-placeholder"));
    }
    return visual;
  }

  function createCard(preset) {
    const card = element("article", "preset-card");
    card.append(createCardVisual(preset));

    const info = element("div", "preset-card-info");
    const tags = element("div", "preset-card-tag-list");
    cardTagNames(preset).forEach((tagName) => {
      tags.append(element("span", "preset-card-tag", tagName));
    });
    if (tags.childElementCount) info.append(tags);

    const titleLink = element("a", "preset-card-title", preset.title || "이름 없는 보물지도");
    titleLink.href = detailPath(preset.presetId);
    info.append(titleLink);

    const meta = element("div", "preset-card-meta");
    meta.append(element("span", "", `🍴 맛집 ${Number(preset.restaurantCount || 0).toLocaleString("ko-KR")}곳`));
    meta.append(element("span", "", `👁 조회 ${Number(preset.viewCount || 0).toLocaleString("ko-KR")}`));
    const favoriteCount = element(
      "span",
      "",
      `♡ 저장 ${Number(preset.favoriteCount || 0).toLocaleString("ko-KR")}`,
    );
    favoriteCount.setAttribute("data-favorite-count", "");
    meta.append(favoriteCount);
    info.append(meta);
    card.append(info);

    const description = element("div", "preset-card-description");
    const descriptionText = typeof preset.description === "string" ? preset.description.trim() : "";
    if (descriptionText) {
      description.append(element("p", "", descriptionText));
    } else {
      description.classList.add("is-empty");
      description.setAttribute("aria-hidden", "true");
    }
    card.append(description);

    const actions = element("div", "preset-card-actions");

    const favorite = element("button", "preset-favorite-button");
    favorite.type = "button";
    appendFavoriteIcon(favorite);
    favorite.classList.toggle("is-active", preset.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(preset.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", preset.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
    favorite.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(favorite, preset);
    });
    const goMap = element("a", "button button-secondary preset-card-map-link", "지도에서 보기");
    const mapQuery = new URLSearchParams({ presetId: preset.presetId });
    if (preset.isOwner) mapQuery.set("edit", "1");
    goMap.href = `/map?${mapQuery.toString()}`;
    actions.append(favorite, goMap);
    card.append(actions);

    card.addEventListener("click", (event) => {
      if (event.target.closest("a, button")) return;
      location.assign(detailPath(preset.presetId));
    });

    return card;
  }

  function renderTagSelectOptions() {
    tagSelect.replaceChildren();
    const all = element("option", "", "태그 전체");
    all.value = "";
    tagSelect.append(all);
    state.tags.forEach((tag) => {
      const option = element("option", "", `#${tag.tagName}`);
      option.value = String(tag.tagId);
      tagSelect.append(option);
    });
    tagSelect.value = state.tagId === null ? "" : String(state.tagId);
  }

  function selectView(sort) {
    if (!["latest", "popular", "favorite"].includes(sort)) return;
    if (state.sort === sort) {
      syncSearchControls();
      return;
    }
    state.sort = sort;
    state.page = 0;
    syncViewTabs();
    syncSearchControls();
    syncListUrl("push");
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
        syncListUrl("push");
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
    const conditions = [];
    const selectedTag = state.tags.find((tag) => tag.tagId === state.tagId);
    if (state.keyword) conditions.push(`“${state.keyword}” 검색`);
    if (selectedTag) conditions.push(`#${selectedTag.tagName}`);
    const conditionText = conditions.length ? ` · ${conditions.join(" · ")}` : "";
    count.textContent = `총 ${Number(pageData.totalElements || 0).toLocaleString("ko-KR")}개의 보물지도${conditionText}`;
    list.classList.toggle("preset-list--state", presets.length === 0);
    if (!presets.length) {
      const empty = element("div", "preset-state preset-state--surface");
      const description = state.keyword
        ? `“${state.keyword}”이(가) 제목 또는 태그에 포함된 보물지도가 없습니다.`
        : "다른 태그를 선택하거나 검색 조건을 초기화해 보세요.";
      empty.append(
        element("h3", "", "조건에 맞는 보물지도가 없습니다."),
        element("p", "", description),
      );
      list.append(empty);
    } else {
      const fragment = document.createDocumentFragment();
      presets.forEach((preset) => fragment.append(createCard(preset)));
      list.append(fragment);
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
    const generation = ++requestGeneration;
    list.setAttribute("aria-busy", "true");
    const params = new URLSearchParams({ page: state.page, size: state.size, sort: state.sort });
    if (state.tagId) params.set("tagId", state.tagId);
    if (state.keyword) params.set("keyword", state.keyword);
    try {
      const payload = await Api.get(`/presets?${params.toString()}`);
      if (generation !== requestGeneration) return;
      renderPage(payload.data || {});
    } catch (error) {
      if (generation !== requestGeneration) return;
      renderError(error);
    }
  }

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.keyword = normalizeKeyword(keywordInput.value);
    const selectedTagId = Number.parseInt(tagSelect.value, 10);
    state.tagId = Number.isSafeInteger(selectedTagId) && selectedTagId > 0
      ? selectedTagId
      : null;
    state.sort = ["latest", "popular", "favorite"].includes(sortSelect.value)
      ? sortSelect.value
      : "latest";
    state.page = 0;
    syncSearchControls();
    syncViewTabs();
    syncListUrl("push");
    loadPresets();
  });

  searchReset.addEventListener("click", () => {
    state.keyword = "";
    state.tagId = null;
    state.sort = "latest";
    state.page = 0;
    syncSearchControls();
    syncViewTabs();
    syncListUrl("push");
    loadPresets();
    keywordInput.focus();
  });

  viewTabs.forEach((tab) => {
    tab.addEventListener("click", () => selectView(tab.dataset.presetSort));
    tab.addEventListener("keydown", (event) => {
      if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
      event.preventDefault();
      const currentIndex = viewTabs.indexOf(tab);
      let nextIndex = currentIndex;
      if (event.key === "Home") nextIndex = 0;
      if (event.key === "End") nextIndex = viewTabs.length - 1;
      if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + viewTabs.length) % viewTabs.length;
      if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % viewTabs.length;
      viewTabs[nextIndex]?.focus({ preventScroll: true });
    });
  });

  window.addEventListener("popstate", () => {
    restoreStateFromLocation();
    loadPresets();
  });

  syncSearchControls();
  syncViewTabs();
  syncListUrl();
  if (!window.FooduckSession?.authenticated) {
    registerLinks.forEach((link) => {
      link.href = `/auth/login?next=${encodeURIComponent(registerPath)}`;
    });
  }
  showCreatedMessage();

  Promise.all([Api.get("/presets/tags"), Promise.resolve()])
    .then(([payload]) => {
      state.tags = Array.isArray(payload.data) ? payload.data : [];
      renderTagSelectOptions();
      syncSearchControls();
    })
    .catch(() => {
      renderTagSelectOptions();
      syncSearchControls();
    })
    .finally(loadPresets);
})();
