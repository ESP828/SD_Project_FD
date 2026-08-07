(() => {
  const list = document.querySelector("#preset-list");
  const count = document.querySelector("#preset-count");
  const filters = document.querySelector("#preset-filters");
  const pagination = document.querySelector("#preset-pagination");
  const searchForm = document.querySelector("#preset-search-form");
  const keywordInput = document.querySelector("#preset-keyword");
  const sortSelect = document.querySelector("#preset-sort");
  const createForm = document.querySelector("#preset-create-form");
  const createSubmit = document.querySelector("#preset-submit");
  const resultMessage = document.querySelector("#result-message");
  const fallbackImage = "/images/foodduck-guide.png";
  const state = { page: 0, size: 12, sort: "popular", tagId: null, keyword: "", tags: [] };
  const requiredCreateFields = [
    { name: "title", message: "제목을 입력해주세요", errorId: "preset-title-error" },
    { name: "summary", message: "요약을 입력해주세요", errorId: "preset-summary-error" },
    { name: "category", message: "카테고리를 입력해주세요", errorId: "preset-category-error" },
  ];

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function detailPath(presetId) {
    return `/pages/presset/detail.html?presetId=${encodeURIComponent(presetId)}`;
  }

  function safeImage(source, title, className) {
    const image = new Image();
    image.className = className;
    image.src = source || fallbackImage;
    image.alt = `${title || "Presset"} 대표 이미지`;
    image.loading = "lazy";
    image.addEventListener("error", () => { image.src = fallbackImage; }, { once: true });
    return image;
  }

  function login() {
    const next = `${location.pathname}${location.search}`;
    location.assign(`/pages/auth/login.html?next=${encodeURIComponent(next)}`);
  }

  function setCreateResult(message = "", type = "") {
    resultMessage.textContent = message;
    resultMessage.classList.toggle("is-success", type === "success");
    resultMessage.classList.toggle("is-error", type === "error");
  }

  function setFieldError(field, errorElement, message) {
    errorElement.textContent = message;
    field.setAttribute("aria-invalid", message ? "true" : "false");
  }

  function clearCreateValidation() {
    requiredCreateFields.forEach(({ name, errorId }) => {
      setFieldError(createForm.elements[name], document.querySelector(`#${errorId}`), "");
    });
  }

  function validateCreateForm() {
    let firstInvalid = null;
    requiredCreateFields.forEach(({ name, message, errorId }) => {
      const field = createForm.elements[name];
      const errorElement = document.querySelector(`#${errorId}`);
      const fieldMessage = field.value.trim() ? "" : message;
      setFieldError(field, errorElement, fieldMessage);
      if (fieldMessage && !firstInvalid) firstInvalid = field;
    });
    firstInvalid?.focus();
    return firstInvalid === null;
  }

  async function submitPreset(formData) {
    const payload = {
      title: formData.title,
      summary: formData.summary,
      description: formData.description || null,
      imageUrl: formData.imageUrl || null,
      category: formData.category,
      isPublic: formData.isPublic,
    };
    return Api.post("/presets", payload);
  }

  function createErrorMessage(error) {
    if (error instanceof TypeError || !Number.isInteger(error.status)) {
      return "네트워크 연결을 확인한 뒤 다시 시도해 주세요.";
    }
    if (error.status === 401) {
      return "로그인이 필요합니다. 로그인 후 다시 시도해 주세요.";
    }
    if (error.status === 403) {
      return "프리셋을 등록할 권한이 없습니다.";
    }
    if (error.status >= 500) {
      return "서버에서 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }
    if (error.status >= 400) {
      return error.message || "입력값을 확인해 주세요.";
    }
    return "등록 중 오류가 발생했습니다. 다시 시도해 주세요.";
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
      button.setAttribute("aria-label", preset.favoriteByCurrentUser ? "Presset 저장 해제" : "Presset 저장");
      button.textContent = preset.favoriteByCurrentUser ? "♥" : "♡";
      button.closest(".preset-card")?.querySelector("[data-favorite-count]")
        ?.replaceChildren(document.createTextNode(`저장 ${preset.favoriteCount}`));
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  function createCard(preset) {
    const card = element("article", "preset-card");
    const visualLink = element("a", "preset-card-visual");
    visualLink.href = detailPath(preset.presetId);
    visualLink.append(safeImage(preset.imageUrl, preset.title, "preset-card-image"));
    visualLink.append(element("span", "preset-category", preset.category || "테마"));

    const favorite = element("button", "preset-favorite-button", preset.favoriteByCurrentUser ? "♥" : "♡");
    favorite.type = "button";
    favorite.classList.toggle("is-active", preset.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(preset.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", preset.favoriteByCurrentUser ? "Presset 저장 해제" : "Presset 저장");
    favorite.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(favorite, preset);
    });
    card.append(visualLink, favorite);

    const body = element("div", "preset-card-body");
    const titleLink = element("a", "preset-card-title", preset.title || "이름 없는 Presset");
    titleLink.href = detailPath(preset.presetId);
    body.append(titleLink, element("p", "preset-card-summary", preset.summary || "선별된 맛집을 확인해 보세요."));

    const tags = element("div", "preset-tag-list");
    (preset.tags || []).forEach((tag) => tags.append(element("span", "preset-tag", `#${tag.tagName}`)));
    if (tags.childElementCount) body.append(tags);

    const thumbnails = element("div", "preset-thumbnails");
    (preset.thumbnailImageUrls || []).slice(0, 3).forEach((url) => {
      thumbnails.append(safeImage(url, preset.title, "preset-thumbnail"));
    });
    if (thumbnails.childElementCount) body.append(thumbnails);

    const meta = element("div", "preset-card-meta");
    meta.append(
      element("span", "", `맛집 ${preset.restaurantCount || 0}곳`),
      element("span", "", `조회 ${Number(preset.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    const saved = element("span", "", `저장 ${preset.favoriteCount || 0}`);
    saved.dataset.favoriteCount = "";
    meta.append(saved);
    body.append(meta);
    card.append(body);
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
      empty.append(element("h3", "", "조건에 맞는 Presset이 없습니다."), element("p", "", "다른 태그나 검색어를 선택해 보세요."));
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

  requiredCreateFields.forEach(({ name, errorId }) => {
    const field = createForm.elements[name];
    const errorElement = document.querySelector(`#${errorId}`);
    field.addEventListener("input", () => {
      if (field.value.trim()) setFieldError(field, errorElement, "");
      setCreateResult();
    });
  });

  createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setCreateResult();
    if (!validateCreateForm()) return;

    const formData = {
      title: createForm.elements.title.value.trim(),
      summary: createForm.elements.summary.value.trim(),
      description: createForm.elements.description.value.trim(),
      imageUrl: createForm.elements.image_url.value.trim(),
      category: createForm.elements.category.value.trim(),
      isPublic: createForm.elements.is_public.checked,
    };

    createSubmit.disabled = true;
    createSubmit.setAttribute("aria-busy", "true");
    createSubmit.textContent = "등록 중...";
    try {
      const response = await submitPreset(formData);
      const presetId = response.data?.presetId ?? response.data?.preset_id ?? response.data;
      console.info("등록된 프리셋 ID:", presetId);
      createForm.reset();
      clearCreateValidation();
      setCreateResult("등록되었습니다", "success");
      state.page = 0;
      state.sort = "latest";
      sortSelect.value = "latest";
      await loadPresets();
    } catch (error) {
      console.error("프리셋 등록 실패", error);
      setCreateResult(createErrorMessage(error), "error");
    } finally {
      createSubmit.disabled = false;
      createSubmit.removeAttribute("aria-busy");
      createSubmit.textContent = "등록";
    }
  });

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

  Promise.all([Api.get("/presets/tags"), Promise.resolve()])
    .then(([payload]) => {
      state.tags = Array.isArray(payload.data) ? payload.data : [];
      renderFilters();
    })
    .catch(() => renderFilters())
    .finally(loadPresets);
})();
