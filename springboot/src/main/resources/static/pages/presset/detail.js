(() => {
  const content = document.querySelector("#preset-detail-content");
  const requestedQuery = new URLSearchParams(location.search);
  const requestedId = Number(requestedQuery.get("presetId"));
  const savedMessageKey = "fooduck:preset-created";
  let preset;
  let selectedCategory = "전체";
  let restaurantCountLabel = null;
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

  // editable을 넘기면 지도에서 목록 삭제와 검색 결과 추가까지 할 수 있다.
  // 소유자가 아닌 사용자에게는 editable을 붙이지 않아 조회 전용으로 열린다.
  function mapPath(restaurantId, editable = false) {
    const query = new URLSearchParams({ presetId: requestedId });
    if (restaurantId) query.set("restaurantId", restaurantId);
    if (editable) query.set("edit", "1");
    return `/map?${query.toString()}`;
  }

  // 보물지도에 담기는 맛집은 공공데이터 음식점(public_restaurant) 기준이다.
  function restaurantDetailPath(restaurantId) {
    return `/restaurant/detail?source=public&id=${encodeURIComponent(restaurantId)}`;
  }

  async function deletePreset(button) {
    if (!requireLogin()) return;
    const title = preset?.title || "이 보물지도";
    if (!window.confirm(`“${title}”을(를) 삭제할까요?\n삭제하면 목록에서 사라지고 담아둔 맛집도 함께 정리됩니다.`)) {
      return;
    }
    button.disabled = true;
    try {
      await Api.delete(`/presets/${requestedId}`);
      try {
        sessionStorage.setItem(savedMessageKey, `“${title}” 보물지도를 삭제했습니다.`);
      } catch (_error) {
        /* 안내 메시지를 남기지 못해도 삭제 자체는 끝났으므로 그대로 이동한다 */
      }
      location.assign("/presset");
    } catch (error) {
      button.disabled = false;
      showToast(error.message || "보물지도를 삭제하지 못했습니다.", true);
    }
  }

  function requireLogin() {
    if (window.FooduckSession?.authenticated) return true;
    const next = `${location.pathname}${location.search}`;
    location.assign(`/auth/login?next=${encodeURIComponent(next)}`);
    return false;
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

  async function reloadPreset() {
    const payload = await Api.get(`/presets/${requestedId}`);
    render(payload.data || {});
  }

  async function removeRestaurant(button, restaurant) {
    if (!requireLogin()) return;
    const restaurantName = restaurant.name || "선택한 맛집";
    // 삭제 버튼이 찜 버튼 옆의 작은 아이콘이라 실수로 눌렸을 때를 대비해 한 번 확인한다.
    if (!window.confirm(`“${restaurantName}”을(를) 이 보물지도에서 삭제할까요?`)) return;
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

    const body = element("a", "preset-restaurant-body preset-restaurant-body-link");
    body.href = restaurantDetailPath(restaurant.restaurantId);
    body.setAttribute("aria-label", `${restaurant.name || "음식점"} 상세페이지 보기`);
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
      // 한 줄짜리 칩이라 줄로 나누진 못해도, 표기는 "요일 : 시간" 형식으로 맞춘다.
      const parsedHours = window.FooduckHours?.parse(restaurant.openingHours);
      const hoursText = parsedHours
        ? parsedHours.map((entry) => `${entry.label} : ${entry.value}`).join(" · ")
        : (window.FooduckHours?.normalize(restaurant.openingHours) || restaurant.openingHours.trim());
      facts.append(element("span", "", `영업시간 ${hoursText}`));
    }
    if (facts.childElementCount) body.append(facts);

    const actions = element("div", "preset-restaurant-actions");

    // 찜과 삭제(X)는 카드 오른쪽 위 모서리에 나란히 둔다(왼쪽 찜, 오른쪽 삭제).
    const actionRow = element("div", "preset-restaurant-action-row");

    const favorite = element("button", "preset-restaurant-favorite");
    favorite.type = "button";
    appendFavoriteIcon(favorite);
    favorite.classList.toggle("is-active", restaurant.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(restaurant.favoriteByCurrentUser)));
    favorite.setAttribute(
      "aria-label",
      restaurant.favoriteByCurrentUser ? `${restaurant.name || "식당"} 찜 해제` : `${restaurant.name || "식당"} 찜`,
    );
    favorite.addEventListener("click", () => toggleRestaurantFavorite(favorite, restaurant));
    actionRow.append(favorite);

    // 보물지도에서 빼는 버튼은 소유자에게만 보이고, 찜 버튼 오른쪽에 여백을 두고 놓인다.
    if (preset.isOwner) {
      const remove = element("button", "preset-restaurant-remove");
      remove.type = "button";
      remove.setAttribute("aria-label", `${restaurant.name || "식당"} 보물지도에서 삭제`);
      remove.title = "보물지도에서 삭제";
      const removeIcon = element("span", "material-symbols-rounded", "close");
      removeIcon.setAttribute("aria-hidden", "true");
      window.FooduckIcons?.set(removeIcon, "close");
      remove.append(removeIcon);
      remove.addEventListener("click", () => removeRestaurant(remove, restaurant));
      actionRow.append(remove);
    }

    actions.append(actionRow);

    if (restaurant.coordinateAvailable) {
      const map = element("a", "button button-secondary preset-restaurant-map-btn", "지도에서 보기");
      map.href = mapPath(restaurant.restaurantId, Boolean(preset.isOwner));
      actions.append(map);
    } else {
      actions.append(element("span", "preset-coordinate-missing", "지도 위치 미등록"));
    }
    // 정보 링크와 액션 영역을 형제 요소로 유지해 하트·삭제·지도 버튼 클릭이 상세 이동으로 이어지지 않게 한다.
    card.append(visual, body, actions);
    return card;
  }

  function renderRestaurants(host) {
    host.replaceChildren();
    const all = Array.isArray(preset.restaurants) ? preset.restaurants : [];
    const visible = selectedCategory === "전체"
      ? all
      : all.filter((restaurant) => restaurant.categoryName === selectedCategory);
    // 패널 헤더의 개수는 지금 보고 있는 목록과 같은 수를 알려 준다.
    if (restaurantCountLabel) {
      const shown = selectedCategory === "전체"
        ? Number(preset.restaurantCount ?? all.length ?? 0)
        : visible.length;
      restaurantCountLabel.textContent = `총 ${shown.toLocaleString("ko-KR")}곳`;
    }
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
    // 소유자는 지도에서 보기로 들어가도 목록 삭제와 검색 결과 추가를 바로 쓸 수 있다.
    const map = element("a", "button button-secondary", "지도에서 보기");
    map.href = mapPath(undefined, Boolean(data.isOwner));
    actions.append(map);
    if (data.isOwner) {
      const edit = element("a", "button button-secondary preset-detail-edit", "⚙ 보물지도 수정");
      edit.href = `/presset/register?presetId=${encodeURIComponent(requestedId)}`;
      const remove = element("button", "button button-secondary preset-detail-delete", "🗑 보물지도 삭제");
      remove.type = "button";
      remove.addEventListener("click", () => deletePreset(remove));
      actions.append(edit, remove);
    }
    copy.append(actions);

    const favorite = element("button", "preset-detail-favorite");
    favorite.type = "button";
    appendFavoriteIcon(favorite);
    favorite.classList.toggle("is-active", data.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(data.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", data.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
    favorite.addEventListener("click", () => togglePresetFavorite(favorite));
    hero.append(visual, copy, favorite);

    const section = element("section", "preset-restaurants");
    section.id = "preset-detail-restaurants";

    const sectionHeading = element("div", "preset-restaurants-heading");
    const sectionTitle = element("div", "preset-restaurants-heading-copy");
    const listTitle = element("h2", "", "이 보물지도에 포함된 맛집");
    listTitle.id = "preset-restaurants-title";
    restaurantCountLabel = element("p", "", `총 ${restaurantCount.toLocaleString("ko-KR")}곳`);
    sectionTitle.append(listTitle, restaurantCountLabel);
    sectionHeading.append(sectionTitle);
    if (data.isOwner) {
      // 목록 패널 헤더 오른쪽 끝에서 바로 이어서 맛집을 담을 수 있게 한다.
      const addHere = element("a", "button button-sm button-primary", "＋ 맛집 추가");
      addHere.href = mapPath(undefined, true);
      sectionHeading.append(addHere);
    }

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
    // 필터는 목록 패널 바깥 위쪽에 두고, 제목·개수·추가 버튼과 목록은 한 패널로 묶는다.
    const listPanel = element("section", "preset-restaurants-panel");
    listPanel.setAttribute("aria-labelledby", listTitle.id);
    listPanel.append(sectionHeading, restaurantList);
    section.append(categoryBar, listPanel);
    renderRestaurants(restaurantList);
    content.append(hero, section);
  }

  function renderError(message) {
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    const state = element("div", "preset-state preset-state--surface surface-card");
    const back = element("a", "button button-secondary", "목록으로 돌아가기");
    back.href = "/presset";
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
