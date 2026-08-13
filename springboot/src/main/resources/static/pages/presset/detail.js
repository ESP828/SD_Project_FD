(() => {
  const content = document.querySelector("#preset-detail-content");
  const requestedQuery = new URLSearchParams(location.search);
  const requestedId = Number(requestedQuery.get("presetId"));
  let openEditOnRender = requestedQuery.get("edit") === "1";
  let preset;
  let selectedCategory = "전체";
  let restaurantStatusMessage = "";
  let restaurantStatusError = false;

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function imagePlaceholder() {
    const placeholder = element("span", "preset-image-placeholder", "이미지 없음");
    placeholder.setAttribute("aria-label", "이미지 없음");
    return placeholder;
  }

  function safeImage(source, alt, className) {
    if (!source) return imagePlaceholder();
    const image = new Image();
    image.className = className;
    image.src = source;
    image.alt = alt;
    image.loading = "lazy";
    image.addEventListener("error", () => {
      image.replaceWith(imagePlaceholder());
    }, { once: true });
    return image;
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

  function mapPath(restaurantId, editable = false) {
    const query = new URLSearchParams({ presetId: requestedId });
    if (restaurantId) query.set("restaurantId", restaurantId);
    if (editable) query.set("edit", "1");
    return `/pages/map/index.html?${query.toString()}`;
  }

  function requireLogin() {
    if (window.FooduckSession?.authenticated) return true;
    const next = `${location.pathname}${location.search}`;
    location.assign(`/pages/auth/login.html?next=${encodeURIComponent(next)}`);
    return false;
  }

  async function reloadPreset() {
    const payload = await Api.get(`/presets/${requestedId}`);
    render(payload.data || {});
  }

  async function removeRestaurant(button, restaurant) {
    if (!requireLogin()) return;
    if (!confirm(`${restaurant.name || "선택한 맛집"}을(를) 이 Presset에서 삭제할까요?`)) return;
    button.disabled = true;
    try {
      const response = await Api.delete(`/presets/${requestedId}/restaurants/${restaurant.restaurantId}`);
      restaurantStatusMessage = response.message || "Presset에서 맛집을 삭제했습니다.";
      restaurantStatusError = false;
      await reloadPreset();
    } catch (error) {
      restaurantStatusMessage = error.message || "맛집을 삭제하지 못했습니다.";
      restaurantStatusError = true;
      button.disabled = false;
      alert(restaurantStatusMessage);
    }
  }

  async function togglePresetFavorite(button) {
    if (!requireLogin()) return;
    button.disabled = true;
    try {
      const response = preset.favoriteByCurrentUser
        ? await Api.delete(`/presets/${requestedId}/favorite`)
        : await Api.post(`/presets/${requestedId}/favorite`);
      preset.favoriteByCurrentUser = Boolean(response.data?.favoriteByCurrentUser);
      preset.favoriteCount = Number(response.data?.favoriteCount) || 0;
      button.classList.toggle("is-active", preset.favoriteByCurrentUser);
      button.setAttribute("aria-pressed", String(preset.favoriteByCurrentUser));
      button.textContent = preset.favoriteByCurrentUser ? "♥ 저장됨" : "♡ Presset 찜";
      document.querySelector("[data-preset-favorite-count]").textContent = `저장 ${preset.favoriteCount.toLocaleString("ko-KR")}`;
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  function createRestaurantCard(restaurant, index) {
    const card = element("article", "preset-restaurant-card surface-card");
    const visual = element("div", "preset-restaurant-visual");
    visual.append(
      safeImage(restaurant.imageUrl, `${restaurant.name || "음식점"} 대표 이미지`, "preset-restaurant-image"),
      element("span", "preset-restaurant-number", String(index + 1).padStart(2, "0")),
    );

    const body = element("div", "preset-restaurant-body");
    body.append(
      element("span", "preset-restaurant-category", restaurant.categoryName || "기타"),
      element("h3", "", restaurant.name || "이름 없는 음식점"),
    );
    const address = [restaurant.address, restaurant.addressDetail].filter(Boolean).join(" ");
    if (address) body.append(element("p", "preset-restaurant-subline", address));
    if (restaurant.presetDescription) body.append(element("p", "preset-pick-reason", restaurant.presetDescription));

    const tags = element("div", "preset-tag-list");
    if (restaurant.categoryName) tags.append(element("span", "preset-tag", `#${restaurant.categoryName}`));
    if (tags.childElementCount) body.append(tags);

    const facts = element("dl", "preset-restaurant-facts");
    facts.append(
      element("dt", "", "영업시간"),
      element("dd", "", restaurant.openingHours || "영업시간 정보 없음"),
      element("dt", "", "평점"),
      element("dd", "", `⭐ ${Number(restaurant.averageRating || 0).toFixed(1)} · 리뷰 ${restaurant.reviewCount || 0}개`),
    );
    body.append(facts);

    const actions = element("div", "preset-restaurant-actions");

    if (restaurant.coordinateAvailable) {
      const map = element("a", "button button-secondary preset-restaurant-map-btn", "지도에서 보기");
      map.href = mapPath(restaurant.restaurantId, preset.isOwner);
      actions.append(map);
    } else {
      actions.append(element("span", "preset-coordinate-missing", "지도 위치 미등록"));
    }
    if (preset.isOwner) {
      const remove = element("button", "button button-secondary preset-restaurant-remove", "Presset에서 삭제");
      remove.type = "button";
      remove.addEventListener("click", () => removeRestaurant(remove, restaurant));
      actions.append(remove);
    }
    card.append(visual, body, actions);
    return card;
  }

  function renderRestaurants(host) {
    host.replaceChildren();
    const all = Array.isArray(preset.restaurants) ? preset.restaurants : [];
    const visible = selectedCategory === "전체"
      ? all
      : all.filter((restaurant) => (restaurant.categoryName || "기타") === selectedCategory);
    if (!visible.length) {
      const empty = element("div", "preset-state preset-state--surface surface-card");
      empty.append(element("h3", "", "해당 종류의 음식점이 없습니다."));
      host.append(empty);
      return;
    }
    visible.forEach((restaurant) => host.append(createRestaurantCard(restaurant, all.indexOf(restaurant))));
  }

  function toggleEditForm(hero) {
    const form = hero.querySelector("#preset-edit-form");
    if (!form) return;
    form.hidden = !form.hidden;
    if (!form.hidden) form.querySelector('[name="title"]').focus();
  }

  function createEditForm(data) {
    const form = element("form", "preset-create-form preset-edit-form surface-card");
    form.id = "preset-edit-form";
    form.hidden = true;
    form.noValidate = true;

    const field = (labelText, name, value, type = "text") => {
      const wrapper = element("div", "preset-form-field");
      const label = element("label", "", labelText);
      label.htmlFor = `preset-edit-${name}`;
      const input = type === "textarea" ? element("textarea") : element("input");
      input.id = `preset-edit-${name}`;
      input.name = name;
      if (type !== "textarea") input.type = type;
      if (type === "textarea") input.value = value || "";
      else input.value = value || "";
      wrapper.append(label, input);
      return wrapper;
    };

    const grid = element("div", "preset-form-grid");
    grid.append(
      field("제목", "title", data.title),
      field("카테고리", "category", data.category),
    );

    const imageField = element("div", "preset-form-field");
    const imageLabel = element("label", "", "이미지 첨부");
    imageLabel.htmlFor = "preset-edit-image";
    const imageInput = element("input");
    imageInput.type = "file";
    imageInput.id = "preset-edit-image";
    imageInput.name = "image";
    imageInput.accept = "image/jpeg,image/png,image/webp";
    const imageHint = element("p", "preset-field-hint", "jpg, png, webp / 5MB 이하. 새로 첨부하면 기존 이미지를 대체합니다.");
    const imagePreview = element("img", "preset-image-preview");
    imagePreview.alt = "첨부한 이미지 미리보기";
    if (data.imageUrl) {
      imagePreview.src = data.imageUrl;
    } else {
      imagePreview.hidden = true;
    }
    imageInput.addEventListener("change", () => {
      const file = imageInput.files?.[0];
      if (file) {
        imagePreview.src = URL.createObjectURL(file);
        imagePreview.hidden = false;
      } else if (data.imageUrl) {
        imagePreview.src = data.imageUrl;
        imagePreview.hidden = false;
      } else {
        imagePreview.hidden = true;
      }
    });
    imageField.append(imageLabel, imageInput, imageHint, imagePreview);
    grid.append(imageField);

    const publicField = element("fieldset", "preset-form-field preset-public-field");
    const publicLabel = element("label", "preset-public-toggle");
    const publicInput = element("input");
    publicInput.type = "checkbox";
    publicInput.name = "isPublic";
    publicInput.checked = data.isPublic !== false;
    publicLabel.append(publicInput, element("span"), document.createTextNode("다른 사용자에게 이 프리셋 공개"));
    publicField.append(publicLabel);
    grid.append(publicField);

    form.append(grid);

    const footer = element("div", "preset-form-footer");
    const submit = element("button", "button button-primary", "저장");
    submit.type = "submit";
    const cancel = element("button", "button button-secondary", "취소");
    cancel.type = "button";
    cancel.addEventListener("click", () => { form.hidden = true; });
    const result = element("div", "preset-form-result");
    footer.append(submit, cancel, result);
    form.append(footer);

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      submit.disabled = true;
      result.textContent = "";
      try {
        const body = new FormData();
        body.append("data", new Blob([JSON.stringify({
          title: form.elements.title.value.trim(),
          category: form.elements.category.value.trim(),
          isPublic: form.elements.isPublic.checked,
        })], { type: "application/json" }));
        const file = form.elements.image.files?.[0];
        if (file) body.append("image", file);
        await Api.put(`/presets/${requestedId}`, body);
        await reloadPreset();
      } catch (error) {
        result.textContent = error.message || "수정 중 오류가 발생했습니다.";
        submit.disabled = false;
      }
    });

    return form;
  }

  function render(data) {
    preset = data;
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    document.title = `${data.title || "Presset"} · 푸드덕`;
    const breadcrumbTitle = document.getElementById("preset-breadcrumb-title");
    if (breadcrumbTitle) breadcrumbTitle.textContent = data.title || "상세";

    const hero = element("section", "preset-detail-hero surface-card");
    let visual = null;
    if (data.imageUrl) {
      visual = element("div", "preset-detail-visual");
      visual.append(safeImage(data.imageUrl, `${data.title || "Presset"} 대표 이미지`, "preset-detail-image"));
      hero.classList.add("preset-detail-hero--with-image");
    }
    const copy = element("div", "preset-detail-copy");
    copy.append(element("h1", "", data.title || "맛집 Presset"), createCategoryBadges(data.category));
    const tags = element("div", "preset-tag-list");
    (data.tags || []).forEach((tag) => tags.append(element("span", "preset-tag", `#${tag.tagName}`)));
    if (tags.childElementCount) copy.append(tags);

    const stats = element("div", "preset-detail-stats");
    const restaurantCount = Number(data.restaurantCount ?? data.restaurants?.length ?? 0);
    const restaurantLimit = Number(data.restaurantLimit || 15);
    stats.append(
      element("span", "", `🍴 맛집 ${restaurantCount}곳 · 최대 ${restaurantLimit}곳`),
      element("span", "", `👁 조회 ${Number(data.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    const favoriteCount = element("span", "", `🤍 찜 ${Number(data.favoriteCount || 0).toLocaleString("ko-KR")}`);
    favoriteCount.dataset.presetFavoriteCount = "";
    stats.append(favoriteCount);
    copy.append(stats);

    const actions = element("div", "preset-detail-actions");
    const favorite = element("button", "button preset-detail-favorite", data.favoriteByCurrentUser ? "♥ 저장됨" : "♡ Presset 찜");
    favorite.type = "button";
    favorite.classList.toggle("is-active", data.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(data.favoriteByCurrentUser)));
    favorite.addEventListener("click", () => togglePresetFavorite(favorite));
    const map = element("a", "button button-primary", data.isOwner ? "지도에서 맛집 관리" : "전체 맛집 지도 보기");
    map.href = mapPath(undefined, data.isOwner);
    actions.append(favorite, map);
    if (data.isOwner) {
      const edit = element("button", "button button-secondary", "Presset 수정하기");
      edit.type = "button";
      edit.addEventListener("click", () => toggleEditForm(hero));
      actions.append(edit);
    }
    copy.append(actions);
    if (visual) hero.append(visual);
    hero.append(copy);
    hero.append(createEditForm(data));

    const section = element("section", "preset-restaurants");
    const heading = element("div", "section-header");
    heading.append(element("h2", "", "이 Presset에 포함된 맛집"));
    section.append(heading);

    const categories = ["전체", ...new Set((data.restaurants || []).map((restaurant) => restaurant.categoryName || "기타"))];
    if (!categories.includes(selectedCategory)) selectedCategory = "전체";
    const categoryBar = element("div", "preset-filters preset-filters--detail");
    const restaurantList = element("div", "preset-restaurant-list");
    categories.forEach((category) => {
      const button = element("button", "preset-filter", category);
      button.type = "button";
      button.setAttribute("aria-pressed", String(category === selectedCategory));
      button.addEventListener("click", () => {
        selectedCategory = category;
        categoryBar.querySelectorAll("button").forEach((item) => item.setAttribute("aria-pressed", String(item === button)));
        renderRestaurants(restaurantList);
      });
      categoryBar.append(button);
    });
    section.append(categoryBar, restaurantList);
    renderRestaurants(restaurantList);
    const shouldOpenEditForm = data.isOwner && openEditOnRender;
    openEditOnRender = false;
    content.append(hero, section);
    if (shouldOpenEditForm) toggleEditForm(hero);
  }

  function renderError(message) {
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    const state = element("div", "preset-state preset-state--surface surface-card");
    const back = element("a", "button button-secondary", "목록으로 돌아가기");
    back.href = "/pages/presset/index.html";
    state.append(element("h2", "", "Presset을 표시할 수 없습니다."), element("p", "", message), back);
    content.append(state);
  }

  if (!Number.isSafeInteger(requestedId) || requestedId <= 0) {
    renderError("올바른 Presset 번호가 필요합니다.");
    return;
  }
  reloadPreset()
    .catch((error) => renderError(error.message || "잠시 후 다시 시도해 주세요."));
})();
