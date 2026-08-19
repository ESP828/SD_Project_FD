(() => {
  const content = document.querySelector("#preset-detail-content");
  const requestedQuery = new URLSearchParams(location.search);
  const requestedId = Number(requestedQuery.get("presetId"));
  const savedMessageKey = "fooduck:preset-created";
  let preset;
  let selectedCategory = "전체";
  let toastTimer = null;

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function showToast(message, isError = false) {
    document.querySelector("#preset-detail-toast")?.remove();
    if (toastTimer) window.clearTimeout(toastTimer);
    const toast = element("div", `preset-toast${isError ? " is-error" : ""}`, message);
    toast.id = "preset-detail-toast";
    toast.setAttribute("role", "status");
    toast.setAttribute("aria-live", isError ? "assertive" : "polite");
    document.body.append(toast);
    toastTimer = window.setTimeout(() => {
      toast.remove();
      toastTimer = null;
    }, 3200);
  }

  function showSavedMessage() {
    let message = null;
    try {
      message = sessionStorage.getItem(savedMessageKey);
      sessionStorage.removeItem(savedMessageKey);
    } catch (_error) {
      return;
    }
    if (!message) return;
    showToast(message);
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
    image.decoding = "async";
    image.fetchPriority = "low";
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

  function presetTagNames(data) {
    const names = parseCategoryTokens(data.category);
    (Array.isArray(data.tags) ? data.tags : []).forEach((tag) => {
      const name = String(tag?.tagName || "").trim();
      if (name && !names.includes(name)) names.push(name);
    });
    return names;
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
    const restaurantName = restaurant.name || "선택한 맛집";
    button.disabled = true;
    try {
      await Api.delete(`/presets/${requestedId}/restaurants/${restaurant.restaurantId}`);
      await reloadPreset();
      showToast(`“${restaurantName}” 식당이 보물지도에서 삭제되었습니다.`);
    } catch (error) {
      button.disabled = false;
      showToast(error.message || `${restaurantName} 식당을 삭제하지 못했습니다.`, true);
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
      button.setAttribute("aria-label", preset.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
      button.textContent = preset.favoriteByCurrentUser ? "♥" : "♡";
      document.querySelector("[data-preset-favorite-count]")
        ?.replaceChildren(document.createTextNode(`♡ 찜 ${preset.favoriteCount.toLocaleString("ko-KR")}`));
    } catch (error) {
      showToast(error.message || "보물지도 찜 상태를 변경하지 못했습니다.", true);
    } finally {
      button.disabled = false;
    }
  }

  async function toggleRestaurantFavorite(button, restaurant) {
    if (!requireLogin()) return;
    button.disabled = true;
    try {
      const response = restaurant.favoriteByCurrentUser
        ? await Api.delete(`/map/restaurants/${restaurant.restaurantId}/favorite`)
        : await Api.post(`/map/restaurants/${restaurant.restaurantId}/favorite`);
      restaurant.favoriteByCurrentUser = Boolean(response.data?.favoriteByCurrentUser);
      button.classList.toggle("is-active", restaurant.favoriteByCurrentUser);
      button.setAttribute("aria-pressed", String(restaurant.favoriteByCurrentUser));
      button.setAttribute(
        "aria-label",
        restaurant.favoriteByCurrentUser ? `${restaurant.name || "식당"} 찜 해제` : `${restaurant.name || "식당"} 찜`,
      );
      button.textContent = restaurant.favoriteByCurrentUser ? "♥" : "♡";
    } catch (error) {
      showToast(error.message || "식당 찜 상태를 변경하지 못했습니다.", true);
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
    body.append(element("h3", "", restaurant.name || "이름 정보 없음"));
    const address = [restaurant.address, restaurant.addressDetail].filter(Boolean).join(" ");
    const basicInfo = [restaurant.categoryName, address].filter(Boolean).join(" · ");
    if (basicInfo) body.append(element("p", "preset-restaurant-subline", basicInfo));

    const facts = element("div", "preset-restaurant-meta");
    const averageRating = Number(restaurant.averageRating);
    const reviewCount = Number(restaurant.reviewCount);
    if (Number.isFinite(averageRating) && averageRating > 0 && Number.isFinite(reviewCount) && reviewCount > 0) {
      facts.append(element("span", "preset-restaurant-rating", `★ ${averageRating.toFixed(1)} (${reviewCount.toLocaleString("ko-KR")})`));
    }
    if (typeof restaurant.openingHours === "string" && restaurant.openingHours.trim()) {
      facts.append(element("span", "", `영업시간 ${restaurant.openingHours.trim()}`));
    }
    if (facts.childElementCount) body.append(facts);

    const actions = element("div", "preset-restaurant-actions");

    const favorite = element(
      "button",
      "preset-restaurant-favorite",
      restaurant.favoriteByCurrentUser ? "♥" : "♡",
    );
    favorite.type = "button";
    favorite.classList.toggle("is-active", restaurant.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(restaurant.favoriteByCurrentUser)));
    favorite.setAttribute(
      "aria-label",
      restaurant.favoriteByCurrentUser ? `${restaurant.name || "식당"} 찜 해제` : `${restaurant.name || "식당"} 찜`,
    );
    favorite.addEventListener("click", () => toggleRestaurantFavorite(favorite, restaurant));
    actions.append(favorite);

    if (restaurant.coordinateAvailable) {
      const map = element("a", "button button-secondary preset-restaurant-map-btn", "지도에서 보기");
      map.href = mapPath(restaurant.restaurantId, preset.isOwner);
      actions.append(map);
    } else {
      actions.append(element("span", "preset-coordinate-missing", "지도 위치 미등록"));
    }
    if (preset.isOwner) {
      const remove = element("button", "button button-secondary preset-restaurant-remove", "보물지도에서 삭제");
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
      : all.filter((restaurant) => restaurant.categoryName === selectedCategory);
    if (!visible.length) {
      const empty = element("div", "preset-state preset-state--surface surface-card");
      empty.append(element("h3", "", "해당 종류의 음식점이 없습니다."));
      host.append(empty);
      return;
    }
    const fragment = document.createDocumentFragment();
    visible.forEach((restaurant) => fragment.append(createRestaurantCard(restaurant, all.indexOf(restaurant))));
    host.append(fragment);
  }

  function render(data) {
    preset = data;
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    document.title = `${data.title || "보물지도"} · 푸드덕`;
    const breadcrumbTitle = document.getElementById("preset-breadcrumb-title");
    if (breadcrumbTitle) breadcrumbTitle.textContent = data.title || "상세";

    const hero = element("section", "preset-detail-hero surface-card");
    const visual = element("div", "preset-detail-visual");
    visual.append(safeImage(data.imageUrl, `${data.title || "보물지도"} 대표 이미지`, "preset-detail-image"));

    const copy = element("div", "preset-detail-copy");
    copy.append(element("h1", "", data.title || "맛집 보물지도"));
    const tags = element("div", "preset-detail-tags");
    presetTagNames(data).forEach((tagName) => tags.append(element("span", "preset-category", tagName)));
    if (tags.childElementCount) copy.append(tags);

    const stats = element("div", "preset-detail-stats");
    const restaurantCount = Number(data.restaurantCount ?? data.restaurants?.length ?? 0);
    stats.append(
      element("span", "", `🍴 맛집 ${restaurantCount.toLocaleString("ko-KR")}곳`),
      element("span", "", `👁 조회 ${Number(data.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    const favoriteCount = element("span", "", `♡ 찜 ${Number(data.favoriteCount || 0).toLocaleString("ko-KR")}`);
    favoriteCount.dataset.presetFavoriteCount = "";
    stats.append(favoriteCount);
    copy.append(stats);

    const actions = element("div", "preset-detail-actions");
    const explore = element("a", "button button-primary", "둘러보기 →");
    explore.href = "#preset-detail-restaurants";
    const map = element("a", "button button-secondary", "지도에서 보기");
    map.href = mapPath(undefined, data.isOwner);
    actions.append(explore, map);
    if (data.isOwner) {
      const edit = element("a", "button button-secondary preset-detail-edit", "⚙ 보물지도 수정");
      edit.href = `/pages/presset/register.html?presetId=${encodeURIComponent(requestedId)}`;
      actions.append(edit);
    }
    copy.append(actions);

    const favorite = element("button", "preset-detail-favorite", data.favoriteByCurrentUser ? "♥" : "♡");
    favorite.type = "button";
    favorite.classList.toggle("is-active", data.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(data.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", data.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
    favorite.addEventListener("click", () => togglePresetFavorite(favorite));
    hero.append(visual, copy, favorite);

    const section = element("section", "preset-restaurants");
    section.id = "preset-detail-restaurants";

    const sectionHeading = element("div", "preset-restaurants-heading");
    sectionHeading.append(
      element("h2", "", "이 보물지도에 포함된 맛집"),
      element("p", "", `총 ${restaurantCount.toLocaleString("ko-KR")}곳`),
    );

    const categories = [
      "전체",
      ...new Set((data.restaurants || []).map((restaurant) => restaurant.categoryName).filter(Boolean)),
    ];
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
    section.append(sectionHeading, categoryBar, restaurantList);
    renderRestaurants(restaurantList);
    content.append(hero, section);
  }

  function renderError(message) {
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    const state = element("div", "preset-state preset-state--surface surface-card");
    const back = element("a", "button button-secondary", "목록으로 돌아가기");
    back.href = "/pages/presset/index.html";
    state.append(element("h2", "", "보물지도를 표시할 수 없습니다."), element("p", "", message), back);
    content.append(state);
  }

  if (!Number.isSafeInteger(requestedId) || requestedId <= 0) {
    renderError("올바른 보물지도 번호가 필요합니다.");
    return;
  }
  reloadPreset()
    .then(showSavedMessage)
    .catch((error) => renderError(error.message || "잠시 후 다시 시도해 주세요."));
})();
