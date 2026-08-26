(() => {
  const content = document.querySelector("#preset-detail-content");
  const requestedQuery = new URLSearchParams(location.search);
  const requestedId = Number(requestedQuery.get("presetId"));
  const savedMessageKey = "fooduck:preset-created";
  let preset;
  let toastTimer = null;
  let presetDeleteInFlight = false;
  const restaurantDeleteInFlight = new Set();

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

  function confirmDeleteAction(options) {
    if (typeof window.FooduckConfirm?.open !== "function") {
      showToast("삭제 확인창을 불러오지 못했습니다. 페이지를 새로고침해 주세요.", true);
      return Promise.resolve(false);
    }
    return window.FooduckConfirm.open({ danger: true, ...options });
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
    if (!preset?.isOwner || presetDeleteInFlight) return;

    const title = preset?.title || "이 보물지도";
    presetDeleteInFlight = true;
    button.disabled = true;
    let navigationStarted = false;
    try {
      await confirmDeleteAction({
        title: "보물지도를 삭제할까요?",
        message: `“${title}”을(를) 삭제하면 복구할 수 없으며 담아둔 맛집도 함께 정리됩니다.`,
        confirmLabel: "보물지도 삭제",
        pendingLabel: "삭제 중…",
        errorMessage: "보물지도를 삭제하지 못했습니다.",
        onConfirm: async () => {
          await Api.delete(`/presets/${requestedId}`);
          try {
            sessionStorage.setItem(savedMessageKey, `“${title}” 보물지도를 삭제했습니다.`);
          } catch (_error) {
            /* 안내 메시지를 남기지 못해도 삭제 자체는 끝났으므로 그대로 이동한다 */
          }
          navigationStarted = true;
          location.assign("/presset");
        },
      });
    } finally {
      if (!navigationStarted) {
        presetDeleteInFlight = false;
        if (button.isConnected) button.disabled = false;
      }
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
    if (!preset?.isOwner) return;

    const restaurantId = Number(restaurant.restaurantId);
    if (!Number.isSafeInteger(restaurantId) || restaurantId <= 0 || restaurantDeleteInFlight.has(restaurantId)) {
      return;
    }
    const restaurantName = restaurant.name || "선택한 맛집";
    restaurantDeleteInFlight.add(restaurantId);
    button.disabled = true;
    try {
      await confirmDeleteAction({
        title: "이 식당을 보물지도에서 삭제할까요?",
        message: `“${restaurantName}” 식당 정보 자체는 삭제되지 않으며 현재 보물지도에서만 제외됩니다.`,
        confirmLabel: "식당 삭제",
        pendingLabel: "삭제 중…",
        errorMessage: `${restaurantName} 식당을 삭제하지 못했습니다.`,
        onConfirm: async () => {
          const response = await Api.delete(`/presets/${requestedId}/restaurants/${restaurantId}`);
          const nextRestaurants = (Array.isArray(preset.restaurants) ? preset.restaurants : [])
            .filter((item) => Number(item.restaurantId) !== restaurantId);
          const responseCount = Number(response.data?.restaurantCount);
          preset = {
            ...preset,
            restaurants: nextRestaurants,
            restaurantCount: Number.isFinite(responseCount) ? responseCount : nextRestaurants.length,
          };
          render(preset);
          showToast(`“${restaurantName}” 식당이 보물지도에서 삭제되었습니다.`);
        },
      });
    } finally {
      restaurantDeleteInFlight.delete(restaurantId);
      if (button.isConnected) button.disabled = false;
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

    const contentArea = element("div", "preset-restaurant-content");
    const titleLink = element("a", "preset-restaurant-title-link");
    titleLink.href = restaurantDetailPath(restaurant.restaurantId);
    titleLink.setAttribute("aria-label", `${restaurant.name || "음식점"} 상세페이지 보기`);
    titleLink.append(element("h3", "", restaurant.name || "이름 정보 없음"));

    const infoLink = element("a", "preset-restaurant-info-link");
    infoLink.href = restaurantDetailPath(restaurant.restaurantId);
    infoLink.setAttribute("aria-label", `${restaurant.name || "음식점"} 주소 및 상세정보 보기`);
    const address = [restaurant.address, restaurant.addressDetail].filter(Boolean).join(" ");
    const basicInfo = [restaurant.categoryName, address].filter(Boolean).join(" · ");
    if (basicInfo) infoLink.append(element("p", "preset-restaurant-subline", basicInfo));

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
    if (facts.childElementCount) infoLink.append(facts);

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
    const footer = element("div", "preset-restaurant-footer");
    footer.append(infoLink);

    if (restaurant.coordinateAvailable) {
      const map = element("a", "button button-secondary preset-restaurant-map-btn", "지도에서 보기");
      map.href = mapPath(restaurant.restaurantId, Boolean(preset.isOwner));
      footer.append(map);
    } else {
      footer.append(element("span", "preset-coordinate-missing", "지도 위치 미등록"));
    }
    // 각 액션은 상세 링크의 바깥에 두어 찜·삭제·지도 클릭이 식당 상세 이동으로 이어지지 않게 한다.
    contentArea.append(actionRow, titleLink, footer);
    card.append(visual, contentArea);
    return card;
  }

  const RESTAURANT_PAGE_SIZE = 10;
  let restaurantPage = 0;

  // 프리셋 하나에 담기는 맛집은 서버에서 이미 전부 받아와 있으므로(최대 담기 개수 제한이 있음),
  // 여기서는 API를 다시 부르지 않고 화면에서만 10개 단위로 잘라 보여준다.
  function renderRestaurants(host, paginationHost) {
    host.replaceChildren();
    const restaurants = Array.isArray(preset.restaurants) ? preset.restaurants : [];
    if (!restaurants.length) {
      const empty = element("div", "preset-state preset-state--surface surface-card");
      empty.append(element("h3", "", "이 보물지도에 등록된 음식점이 없습니다."));
      host.append(empty);
      paginationHost?.replaceChildren();
      return;
    }
    const totalPages = Math.max(1, Math.ceil(restaurants.length / RESTAURANT_PAGE_SIZE));
    restaurantPage = Math.min(restaurantPage, totalPages - 1);
    const start = restaurantPage * RESTAURANT_PAGE_SIZE;
    const pageItems = restaurants.slice(start, start + RESTAURANT_PAGE_SIZE);
    const fragment = document.createDocumentFragment();
    pageItems.forEach((restaurant, index) => fragment.append(createRestaurantCard(restaurant, start + index)));
    host.append(fragment);
    if (paginationHost) {
      window.FooduckPagination.render(
        paginationHost,
        {
          totalPages,
          number: restaurantPage,
          first: restaurantPage === 0,
          last: restaurantPage >= totalPages - 1,
        },
        (page) => {
          restaurantPage = page;
          renderRestaurants(host, paginationHost);
          host.scrollIntoView({ behavior: "smooth", block: "start" });
        },
      );
    }
  }

  function render(data) {
    preset = data;
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    document.title = `${data.title || "보물지도"} · 푸드덕`;

    const hero = element("section", "preset-detail-hero surface-card");
    const visual = element("div", "preset-detail-visual");
    visual.append(safeImage(data.imageUrl, `${data.title || "보물지도"} 대표 이미지`, "preset-detail-image"));

    const copy = element("div", "preset-detail-copy");
    const title = element("h1", "", data.title || "맛집 보물지도");

    const toolbar = element("div", "preset-detail-toolbar");
    const tags = element("div", "preset-detail-tags");
    presetTagNames(data).forEach((tagName) => tags.append(element("span", "preset-category", tagName)));

    const iconActions = element("div", "preset-detail-icon-actions");
    const favorite = element("button", "preset-detail-favorite");
    favorite.type = "button";
    appendFavoriteIcon(favorite);
    favorite.classList.toggle("is-active", data.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(data.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", data.favoriteByCurrentUser ? "보물지도 찜 해제" : "보물지도 찜");
    favorite.addEventListener("click", () => togglePresetFavorite(favorite));
    iconActions.append(favorite);

    if (data.isOwner) {
      const remove = element("button", "preset-restaurant-remove preset-detail-remove");
      remove.type = "button";
      remove.setAttribute("aria-label", `${data.title || "보물지도"} 보물지도 삭제`);
      remove.title = "보물지도 삭제";
      const removeIcon = element("span", "material-symbols-rounded", "close");
      removeIcon.setAttribute("aria-hidden", "true");
      window.FooduckIcons?.set(removeIcon, "close");
      remove.append(removeIcon);
      remove.addEventListener("click", () => deletePreset(remove));
      iconActions.append(remove);
    }
    toolbar.append(tags, iconActions);
    copy.append(toolbar, title);

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
      actions.append(edit);
    }
    copy.append(actions);
    hero.append(visual, copy);

    const section = element("section", "preset-restaurants");
    section.id = "preset-detail-restaurants";

    const sectionHeading = element("div", "preset-restaurants-heading");
    const sectionTitle = element("div", "preset-restaurants-heading-copy");
    const listTitle = element("h2", "", "이 보물지도에 포함된 맛집");
    listTitle.id = "preset-restaurants-title";
    const totalRestaurantCountLabel = element("p", "", `총 ${restaurantCount.toLocaleString("ko-KR")}곳`);
    sectionTitle.append(listTitle, totalRestaurantCountLabel);
    sectionHeading.append(sectionTitle);
    if (data.isOwner) {
      // 목록 패널 헤더 오른쪽 끝에서 바로 이어서 맛집을 담을 수 있게 한다.
      const addHere = element("a", "button button-sm button-primary", "＋ 맛집 추가");
      addHere.href = mapPath(undefined, true);
      sectionHeading.append(addHere);
    }

    const restaurantList = element("div", "preset-restaurant-list");
    const restaurantPagination = element("nav", "fooduck-pagination");
    restaurantPagination.setAttribute("aria-label", "맛집 목록 페이지");
    const listPanel = element("section", "preset-restaurants-panel");
    listPanel.setAttribute("aria-labelledby", listTitle.id);
    listPanel.append(sectionHeading, restaurantList, restaurantPagination);
    section.append(listPanel);
    restaurantPage = 0;
    renderRestaurants(restaurantList, restaurantPagination);
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
