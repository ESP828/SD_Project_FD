(() => {
  const content = document.querySelector("#preset-detail-content");
  const fallbackImage = "/images/foodduck-guide.png";
  const requestedId = Number(new URLSearchParams(location.search).get("presetId"));
  let preset;
  let selectedCategory = "전체";

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function safeImage(source, alt, className) {
    const image = new Image();
    image.className = className;
    image.src = source || fallbackImage;
    image.alt = alt;
    image.loading = "lazy";
    image.addEventListener("error", () => { image.src = fallbackImage; }, { once: true });
    return image;
  }

  function mapPath(restaurantId) {
    const query = new URLSearchParams({ presetId: requestedId });
    if (restaurantId) query.set("restaurantId", restaurantId);
    return `/pages/map/index.html?${query.toString()}`;
  }

  function requireLogin() {
    if (window.FooduckSession?.authenticated) return true;
    const next = `${location.pathname}${location.search}`;
    location.assign(`/pages/auth/login.html?next=${encodeURIComponent(next)}`);
    return false;
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
      button.textContent = preset.favoriteByCurrentUser ? "♥ 저장됨" : "♡ Presset 저장";
      document.querySelector("[data-preset-favorite-count]").textContent = `저장 ${preset.favoriteCount.toLocaleString("ko-KR")}`;
    } catch (error) {
      alert(error.message);
    } finally {
      button.disabled = false;
    }
  }

  async function toggleRestaurantFavorite(button, restaurant) {
    if (!requireLogin()) return;
    button.disabled = true;
    try {
      const response = restaurant.favoriteByCurrentUser
        ? await Api.delete(`/restaurants/${restaurant.restaurantId}/favorite`)
        : await Api.post(`/restaurants/${restaurant.restaurantId}/favorite`);
      restaurant.favoriteByCurrentUser = Boolean(response.data?.favoriteByCurrentUser);
      button.classList.toggle("is-active", restaurant.favoriteByCurrentUser);
      button.setAttribute("aria-pressed", String(restaurant.favoriteByCurrentUser));
      button.setAttribute("aria-label", restaurant.favoriteByCurrentUser ? "음식점 저장 해제" : "음식점 저장");
      button.textContent = restaurant.favoriteByCurrentUser ? "♥" : "♡";
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

    const favorite = element("button", "preset-restaurant-favorite", restaurant.favoriteByCurrentUser ? "♥" : "♡");
    favorite.type = "button";
    favorite.classList.toggle("is-active", restaurant.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(restaurant.favoriteByCurrentUser)));
    favorite.setAttribute("aria-label", restaurant.favoriteByCurrentUser ? "음식점 저장 해제" : "음식점 저장");
    favorite.addEventListener("click", () => toggleRestaurantFavorite(favorite, restaurant));
    visual.append(favorite);

    const body = element("div", "preset-restaurant-body");
    body.append(
      element("span", "preset-restaurant-category", restaurant.categoryName || "기타"),
      element("h3", "", restaurant.name || "이름 없는 음식점"),
    );
    if (restaurant.presetDescription) body.append(element("p", "preset-pick-reason", restaurant.presetDescription));

    const facts = element("dl", "preset-restaurant-facts");
    const address = [restaurant.address, restaurant.addressDetail].filter(Boolean).join(" ");
    if (address) facts.append(element("dt", "", "주소"), element("dd", "", address));
    facts.append(
      element("dt", "", "영업시간"),
      element("dd", "", restaurant.openingHours || "영업시간 정보 없음"),
      element("dt", "", "평점"),
      element("dd", "", `${Number(restaurant.averageRating || 0).toFixed(1)} · 리뷰 ${restaurant.reviewCount || 0}개`),
    );
    body.append(facts);

    if (restaurant.coordinateAvailable) {
      const map = element("a", "button button-secondary", "지도에서 보기");
      map.href = mapPath(restaurant.restaurantId);
      body.append(map);
    } else {
      body.append(element("span", "preset-coordinate-missing", "지도 위치 미등록"));
    }
    card.append(visual, body);
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

  function render(data) {
    preset = data;
    content.replaceChildren();
    content.setAttribute("aria-busy", "false");
    document.title = `${data.title || "Presset"} · 푸드덕`;

    const hero = element("section", "preset-detail-hero surface-card");
    const visual = element("div", "preset-detail-visual");
    visual.append(safeImage(data.imageUrl, `${data.title || "Presset"} 대표 이미지`, "preset-detail-image"));
    const copy = element("div", "preset-detail-copy");
    copy.append(element("span", "preset-category", data.category || "테마"), element("h1", "", data.title || "맛집 Presset"));
    const tags = element("div", "preset-tag-list");
    (data.tags || []).forEach((tag) => tags.append(element("span", "preset-tag", `#${tag.tagName}`)));
    if (tags.childElementCount) copy.append(tags);
    copy.append(element("p", "preset-detail-summary", data.summary || ""));
    if (data.description) copy.append(element("p", "preset-detail-description", data.description));

    const stats = element("div", "preset-detail-stats");
    stats.append(
      element("span", "", `맛집 ${(data.restaurants || []).length}곳`),
      element("span", "", `조회 ${Number(data.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    const favoriteCount = element("span", "", `저장 ${Number(data.favoriteCount || 0).toLocaleString("ko-KR")}`);
    favoriteCount.dataset.presetFavoriteCount = "";
    stats.append(favoriteCount);
    copy.append(stats);

    const actions = element("div", "preset-detail-actions");
    const favorite = element("button", "button preset-detail-favorite", data.favoriteByCurrentUser ? "♥ 저장됨" : "♡ Presset 저장");
    favorite.type = "button";
    favorite.classList.toggle("is-active", data.favoriteByCurrentUser);
    favorite.setAttribute("aria-pressed", String(Boolean(data.favoriteByCurrentUser)));
    favorite.addEventListener("click", () => togglePresetFavorite(favorite));
    const map = element("a", "button button-primary", "전체 맛집 지도 보기");
    map.href = mapPath();
    actions.append(favorite, map);
    if (window.FooduckSession?.isAdmin) {
      const edit = element("a", "button button-secondary", "Presset 수정하기");
      edit.href = `/pages/admin/presets.html?presetId=${encodeURIComponent(requestedId)}`;
      actions.append(edit);
    }
    copy.append(actions);
    hero.append(visual, copy);

    const section = element("section", "preset-restaurants");
    const heading = element("div", "section-header");
    heading.append(element("h2", "", "이 Presset에 포함된 맛집"));
    section.append(heading);

    const categories = ["전체", ...new Set((data.restaurants || []).map((restaurant) => restaurant.categoryName || "기타"))];
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
    content.append(hero, section);
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
  Api.get(`/presets/${requestedId}`)
    .then((payload) => render(payload.data || {}))
    .catch((error) => renderError(error.message || "잠시 후 다시 시도해 주세요."));
})();
