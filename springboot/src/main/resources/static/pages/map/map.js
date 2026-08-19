(() => {
  const mapStatus = document.getElementById("map-status");
  const mapPlaceholder = document.getElementById("map-placeholder");
  const locationButton = document.getElementById("current-location-button");
  const searchForm = document.getElementById("place-search-form");
  const keywordInput = document.getElementById("place-keyword");
  const placeResults = document.getElementById("place-results");
  const resultCount = document.getElementById("result-count");
  const resultTitle = document.getElementById("result-title");
  const categoryList = document.querySelector(".category-list");
  const mapPage = document.querySelector(".map-page");
  const sidebarToggleButton = document.getElementById("sidebar-toggle-btn");
  const sidebarToggleIcon = sidebarToggleButton.querySelector(".material-symbols-rounded");
  const searchAreaButton = document.getElementById("search-area-button");
  const pageTitle = document.getElementById("map-page-title");
  const presetBreadcrumb = document.getElementById("preset-map-breadcrumb");
  const presetManageBack = document.getElementById("preset-manage-back");
  const detailPanel = document.getElementById("place-detail-panel");
  const detailClose = document.getElementById("place-detail-close");
  const detailBody = document.getElementById("place-detail-body");

  const query = new URLSearchParams(location.search);
  const presetMode = query.has("presetId");
  const presetId = Number(query.get("presetId"));
  const editMode = presetMode && query.has("edit");
  const requestedRestaurantId = Number(query.get("restaurantId"));
  const SEARCH_RADIUS_METERS = 500;
  const markerAssetRoot = "/images/markers";
  const markerImageCache = new Map();

  let kakaoMap;
  let currentPositionMarker;
  let markerEntries = new Map();
  let currentPlaces = new Map();
  let activeMarkerEntry;
  let lastSearchKeyword = "";
  let presetItems = [];
  let detailRequestToken = 0;
  let resultMode = "preset";

  function setMapStatus(message, isError = false) {
    if (!mapStatus) return;
    mapStatus.textContent = message;
    mapStatus.classList.toggle("is-error", isError);
  }

  function setResultsState(count, title = "검색 결과") {
    resultTitle.textContent = title;
    resultCount.textContent = String(count);
  }

  function renderEmptyResults(message) {
    placeResults.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "result-empty";
    const image = document.createElement("img");
    image.src = "/images/characters/error.png";
    image.alt = "";
    const copy = document.createElement("p");
    copy.textContent = message;
    empty.append(image, copy);
    placeResults.append(empty);
  }

  function loadKakaoSdk(javascriptKey) {
    return new Promise((resolve, reject) => {
      if (window.kakao?.maps) {
        window.kakao.maps.load(resolve);
        return;
      }
      const script = document.createElement("script");
      script.async = true;
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(javascriptKey)}&autoload=false`;
      script.onload = () => window.kakao.maps.load(resolve);
      script.onerror = () => reject(new Error("카카오맵 SDK를 불러오지 못했습니다."));
      document.head.appendChild(script);
    });
  }

  function initializeMap() {
    kakaoMap = new kakao.maps.Map(document.getElementById("map"), {
      center: new kakao.maps.LatLng(37.5665, 126.978),
      level: 3,
    });
    // TOPRIGHT로 고정해야 "내 위치로 이동" 버튼을 그 아래에 정확히 붙일 수 있다
    // (RIGHT는 지도 높이에 따라 수직 중앙으로 움직여서 위치 계산이 불안정함).
    kakaoMap.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.TOPRIGHT);
    mapPlaceholder.hidden = true;
    const showSearchAreaButton = () => {
      if ((!presetMode || editMode) && lastSearchKeyword) searchAreaButton.hidden = false;
    };
    kakao.maps.event.addListener(kakaoMap, "dragend", showSearchAreaButton);
    kakao.maps.event.addListener(kakaoMap, "zoom_changed", showSearchAreaButton);
  }

  function getMarkerImage(assetName) {
    if (markerImageCache.has(assetName)) return markerImageCache.get(assetName);
    const image = new kakao.maps.MarkerImage(
      `${markerAssetRoot}/${assetName}`,
      new kakao.maps.Size(42, 56),
      { offset: new kakao.maps.Point(21, 56) },
    );
    markerImageCache.set(assetName, image);
    return image;
  }

  function resolveCategoryMarker(place) {
    const category = `${place.category_name || ""} ${place.category_group_name || ""}`;
    if (/카페|커피|디저트|제과|베이커리/.test(category)) return "category_cafe.png";
    if (/중식|중국/.test(category)) return "category_chinese.png";
    if (/일식|일본|초밥|스시/.test(category)) return "category_japanese.png";
    if (/양식|이탈리안|프렌치|스테이크/.test(category)) return "category_western.png";
    if (/패스트푸드|햄버거|피자/.test(category)) return "category_fastfood.png";
    if (/술집|호프|주점|바/.test(category)) return "category_pub.png";
    if (/한식|국밥|고기|분식/.test(category)) return "category_korean.png";
    return "state_default.svg";
  }

  function validCoordinate(latitude, longitude) {
    return Number.isFinite(latitude) && Number.isFinite(longitude)
      && latitude >= -90 && latitude <= 90
      && longitude >= -180 && longitude <= 180
      && !(latitude === 0 && longitude === 0);
  }

  function clearMarkers() {
    markerEntries.forEach((entry) => entry.marker.setMap(null));
    markerEntries.clear();
    currentPlaces.clear();
    activeMarkerEntry = undefined;
    closeDetailPanel();
  }

  function detailHref(restaurantId) {
    const source = presetMode ? "owned" : "public";
    return `/restaurant/detail?source=${source}&id=${encodeURIComponent(restaurantId)}`;
  }

  function closeDetailPanel() {
    detailRequestToken += 1;
    detailPanel.classList.remove("is-open");
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    }[char]));
  }

  function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
  }

  async function toggleDetailPanelFavorite(button, place) {
    if (!requireLogin()) return;
    const source = presetMode ? "owned" : "public";
    const path = source === "owned"
      ? `/restaurants/${place.restaurantId}/favorite`
      : `/map/restaurants/${place.restaurantId}/favorite`;
    button.disabled = true;
    try {
      const response = place.favoriteByCurrentUser
        ? await Api.delete(path)
        : await Api.post(path);
      place.favoriteByCurrentUser = Boolean(response.data?.favoriteByCurrentUser);
      button.classList.toggle("is-favorited", place.favoriteByCurrentUser);
      button.setAttribute("aria-pressed", String(place.favoriteByCurrentUser));
    } catch (error) {
      window.alert(error.message || "찜 처리 중 오류가 발생했습니다.");
    } finally {
      button.disabled = false;
    }
  }

  function renderDetailPanel(place, detail, menuItems, reviews) {
    const isOwned = presetMode;
    const category = place.category_name || "기타";
    const address = place.road_address_name || place.address_name || "주소 정보 없음";

    const ratingAvg = isOwned
      ? detail?.averageRating
      : (reviews.length ? reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length : null);
    const reviewCount = isOwned ? (detail?.reviewCount ?? 0) : reviews.length;

    const menuHtml = menuItems.length
      ? (isOwned ? "" : '<p class="place-detail-menu-disclaimer">* 공공데이터 기반 예시 메뉴로 실제와 다를 수 있습니다.</p>')
        + menuItems.slice(0, 4).map((item) => `
            <div class="place-detail-menu-item">
              <strong>${escapeHtml(item.name)}</strong>
              <span>${item.price != null ? `${item.price.toLocaleString("ko-KR")}원` : "가격 미정"}</span>
            </div>
          `).join("")
      : '<div class="place-detail-empty">등록된 메뉴가 없습니다.</div>';

    const reviewHtml = reviews.length
      ? reviews.slice(0, 3).map((review) => `
          <div class="place-detail-review-item">
            <div class="place-detail-review-head">
              <span>${escapeHtml(review.authorNickname || "익명")}</span>
              <span>★ ${review.rating}.0</span>
            </div>
            <p>${review.content ? escapeHtml(review.content) : "내용 없음"} · ${formatDate(review.createdAt)}</p>
          </div>
        `).join("")
      : '<div class="place-detail-empty">아직 작성된 리뷰가 없습니다.</div>';

    detailBody.innerHTML = `
      <div class="place-detail-header">
        <div>
          <span class="place-detail-category">${escapeHtml(category)}</span>
          <h2>${escapeHtml(place.place_name)}</h2>
          <p class="place-detail-address">${escapeHtml(isOwned && detail?.addressDetail ? `${address} ${detail.addressDetail}` : address)}</p>
        </div>
        <button type="button" class="place-detail-favorite${place.favoriteByCurrentUser ? " is-favorited" : ""}"
                id="place-detail-favorite-btn" aria-pressed="${Boolean(place.favoriteByCurrentUser)}" aria-label="찜하기">
          <span class="material-symbols-rounded" aria-hidden="true">favorite</span>
        </button>
      </div>
      <div class="place-detail-rating">
        <strong>${ratingAvg != null ? `★ ${ratingAvg.toFixed(1)}` : "리뷰 없음"}</strong>
        <span>리뷰 ${reviewCount}건${isOwned && detail?.phone ? ` · ${detail.phone}` : ""}</span>
      </div>
      ${isOwned && detail?.openingHours ? `<div class="place-detail-empty" style="margin-bottom:20px;">${escapeHtml(detail.openingHours)}${detail?.closedDays ? ` · 휴무 ${escapeHtml(detail.closedDays)}` : ""}</div>` : ""}
      <div class="place-detail-section">
        <h3>메뉴</h3>
        ${menuHtml}
      </div>
      <div class="place-detail-section">
        <h3>리뷰</h3>
        ${reviewHtml}
      </div>
      <a class="button button-primary place-detail-link" target="_blank" rel="noopener"
         href="${detailHref(place.restaurantId)}">상세 페이지에서 더 보기</a>
    `;

    const favoriteButton = document.getElementById("place-detail-favorite-btn");
    favoriteButton.addEventListener("click", () => toggleDetailPanelFavorite(favoriteButton, place));
  }

  // prefetch로 { detailPromise, reviewsPromise, menuPromise }를 넘기면 새로 요청하지 않고
  // 이미 진행 중인 요청을 그대로 재사용한다(포커싱 직후 상세 패널이 바로 뜨도록).
  async function openDetailPanel(place, prefetch) {
    detailPanel.classList.add("is-open");
    detailBody.innerHTML = '<div class="place-detail-loading">불러오는 중입니다...</div>';
    const requestId = (detailRequestToken += 1);
    const isOwned = presetMode;

    try {
      const [detailResponse, reviewsResponse, menuResponse] = await Promise.all([
        prefetch?.detailPromise ?? Api.get(isOwned ? `/public/restaurants/${place.restaurantId}` : `/public/map/restaurants/${place.restaurantId}`),
        prefetch?.reviewsPromise ?? Api.get(isOwned ? `/public/restaurants/${place.restaurantId}/reviews` : `/public/map/restaurants/${place.restaurantId}/reviews`, { auth: false }),
        prefetch?.menuPromise ?? Api.get(isOwned ? `/public/restaurants/${place.restaurantId}/menu` : `/public/map/restaurants/${place.restaurantId}/menu`, { auth: false }),
      ]);
      if (requestId !== detailRequestToken) return;
      place.favoriteByCurrentUser = Boolean(detailResponse.data?.favoritedByMe);
      renderDetailPanel(place, detailResponse.data, menuResponse.data || [], reviewsResponse.data || []);
    } catch (error) {
      if (requestId !== detailRequestToken) return;
      detailBody.innerHTML = `<div class="place-detail-error">${escapeHtml(error.message || "가게 정보를 불러오지 못했습니다.")}</div>`;
    }
  }

  function selectRestaurant(restaurantId, moveMap = true, prefetch = null) {
    const key = String(restaurantId);
    const row = placeResults.querySelector(`[data-restaurant-id="${key}"]`);
    placeResults.querySelector(".place-result.is-active")?.classList.remove("is-active");
    row?.classList.add("is-active");
    const entry = markerEntries.get(key);
    const place = entry?.place || currentPlaces.get(key);
    if (!place) {
      setMapStatus("음식점 정보를 찾을 수 없습니다.", true);
      return;
    }
    if (entry) {
      if (activeMarkerEntry) activeMarkerEntry.marker.setImage(getMarkerImage(activeMarkerEntry.assetName));
      activeMarkerEntry = entry;
      entry.marker.setImage(getMarkerImage("state_selected.svg"));
      if (moveMap) kakaoMap.panTo(entry.position);
      setMapStatus(`“${place.place_name}” 위치를 선택했습니다.`);
    } else {
      setMapStatus("이 음식점은 등록된 좌표가 없어 지도에는 표시되지 않습니다.", true);
    }
    openDetailPanel(place, prefetch);
  }

  function requireLogin() {
    if (window.FooduckSession?.authenticated) return true;
    location.assign(`/auth/login?next=${encodeURIComponent(`${location.pathname}${location.search}`)}`);
    return false;
  }

  async function addToPreset(button, place) {
    button.disabled = true;
    try {
      await Api.post(`/presets/${presetId}/restaurants/${place.restaurantId}`);
      button.classList.add("is-added");
      button.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">check_circle</span> 추가됨';
      window.FooduckIcons?.enhance(button);
      await new Promise((resolve) => setTimeout(resolve, 500));
      await fetchPresetData();
      renderPresetFilters();
      setMapStatus(`"${place.place_name}"을(를) presset에 추가했습니다.`);
      if (resultMode === "search" && lastSearchKeyword) {
        await searchPlaces(lastSearchKeyword);
      } else {
        renderItems(presetItems, pageTitle.textContent, true, "preset");
      }
    } catch (error) {
      window.alert(error.message || "추가 중 오류가 발생했습니다.");
      button.disabled = false;
    }
  }

  async function removeFromPreset(button, place) {
    if (!window.confirm(`"${place.place_name}"을(를) 이 presset에서 삭제할까요?`)) return;
    button.disabled = true;
    try {
      if (place.restaurantId > 0) {
        await Api.delete(`/presets/${presetId}/restaurants/${place.restaurantId}`);
      }
      presetItems = presetItems.filter((item) => item.restaurantId !== place.restaurantId);
      renderItems(presetItems, pageTitle.textContent);
      setMapStatus(`"${place.place_name}"을(를) presset에서 삭제했습니다.`);
    } catch (error) {
      window.alert(error.message || "삭제 중 오류가 발생했습니다.");
      button.disabled = false;
    }
  }

  function createResultRow(place, index) {
    const article = document.createElement("article");
    article.className = "place-result";
    article.dataset.restaurantId = String(place.restaurantId);
    if (!place.coordinateAvailable) article.classList.add("has-no-coordinate");
    const markerImage = document.createElement("img");
    markerImage.className = "place-result-marker";
    markerImage.src = `${markerAssetRoot}/${resolveCategoryMarker(place)}`;
    markerImage.alt = "";
    const body = document.createElement("div");
    body.className = "place-result-body";
    const select = document.createElement("button");
    select.className = "place-result-select";
    select.type = "button";
    const top = document.createElement("span");
    top.className = "place-result-top";
    const name = document.createElement("span");
    name.className = "place-result-name";
    name.textContent = place.place_name;
    const number = document.createElement("span");
    number.className = "place-result-index";
    number.textContent = String(index + 1).padStart(2, "0");
    top.append(name, number);
    const category = document.createElement("span");
    category.className = "place-result-category";
    category.textContent = place.category_name || "기타";
    const address = document.createElement("span");
    address.className = "place-result-address";
    address.textContent = place.road_address_name || place.address_name || "주소 정보 없음";
    select.append(top, category, address);
    if (!place.coordinateAvailable) {
      const missing = document.createElement("span");
      missing.className = "place-coordinate-missing";
      missing.textContent = "지도 좌표 없음";
      select.append(missing);
    }
    select.addEventListener("click", () => selectRestaurant(place.restaurantId));
    body.append(select);
    const actions = document.createElement("div");
    actions.className = "place-result-actions";
    if (editMode && resultMode === "preset") {
      const remove = document.createElement("button");
      remove.className = "place-result-link place-result-remove";
      remove.type = "button";
      remove.setAttribute("aria-label", `${place.place_name} presset에서 삭제`);
      remove.innerHTML = '<i class="fa-solid fa-trash-can" aria-hidden="true"></i> 삭제';
      remove.addEventListener("click", (event) => {
        event.stopPropagation();
        removeFromPreset(remove, place);
      });
      actions.append(remove);
    }
    if (editMode && resultMode === "search") {
      const add = document.createElement("button");
      add.className = "place-result-link place-result-add";
      add.type = "button";
      add.setAttribute("aria-label", `${place.place_name} 보물지도에 추가`);
      add.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">add</span> 보물지도에 추가';
      add.addEventListener("click", (event) => {
        event.stopPropagation();
        addToPreset(add, place);
      });
      actions.append(add);
    }
    const detail = document.createElement("a");
    detail.className = "place-result-link place-result-detail";
    detail.href = detailHref(place.restaurantId);
    detail.target = "_blank";
    detail.rel = "noopener";
    detail.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">open_in_new</span>상세보기';
    actions.append(detail);
    body.append(actions);
    article.append(markerImage, body);
    return article;
  }

  function renderItems(items, title, fitBounds = true, mode = "preset") {
    resultMode = mode;
    presetManageBack.hidden = !(editMode && mode === "search");
    clearMarkers();
    placeResults.replaceChildren();
    items.forEach((place) => currentPlaces.set(String(place.restaurantId), place));
    items.forEach((place, index) => placeResults.append(createResultRow(place, index)));
    setResultsState(items.length, title);
    if (!items.length) {
      renderEmptyResults("조건에 맞는 음식점이 없습니다.");
      return;
    }
    if (!kakaoMap) return;
    const bounds = new kakao.maps.LatLngBounds();
    let markerCount = 0;
    items.forEach((place) => {
      if (!place.coordinateAvailable) return;
      const position = new kakao.maps.LatLng(place.y, place.x);
      const assetName = resolveCategoryMarker(place);
      const marker = new kakao.maps.Marker({ map: kakaoMap, position, image: getMarkerImage(assetName), zIndex: markerCount + 1 });
      const entry = { marker, position, place, assetName };
      markerEntries.set(String(place.restaurantId), entry);
      kakao.maps.event.addListener(marker, "click", () => {
        selectRestaurant(place.restaurantId, false);
        placeResults.querySelector(`[data-restaurant-id="${place.restaurantId}"]`)
          ?.scrollIntoView({ behavior: "smooth", block: "nearest" });
      });
      bounds.extend(position);
      markerCount += 1;
    });
    if (fitBounds && markerCount > 0) kakaoMap.setBounds(bounds);
  }

  function toPresetPlace(item) {
    const latitude = Number(item.latitude);
    const longitude = Number(item.longitude);
    return {
      restaurantId: Number(item.restaurantId),
      place_name: item.name || "이름 없는 음식점",
      category_name: item.categoryName || "기타",
      category_group_name: "",
      road_address_name: [item.address, item.addressDetail].filter(Boolean).join(" "),
      address_name: item.address || "",
      y: latitude,
      x: longitude,
      favoriteByCurrentUser: Boolean(item.favoriteByCurrentUser),
      coordinateAvailable: Boolean(item.coordinateAvailable) && validCoordinate(latitude, longitude),
    };
  }

  function toPublicPlace(item) {
    const latitude = Number(item.lat);
    const longitude = Number(item.lon);
    return {
      restaurantId: Number(item.id),
      place_name: item.name,
      category_name: item.categoryName || "",
      category_group_name: "",
      road_address_name: item.roadAddress || "",
      address_name: item.lotAddress || "",
      y: latitude,
      x: longitude,
      coordinateAvailable: validCoordinate(latitude, longitude),
    };
  }

  function renderPresetFilters() {
    const categories = ["전체", ...new Set(presetItems.map((item) => item.category_name || "기타"))];
    categoryList.replaceChildren();
    categories.forEach((category, index) => {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = category;
      button.classList.toggle("is-active", index === 0);
      button.addEventListener("click", () => {
        categoryList.querySelectorAll("button").forEach((item) => item.classList.toggle("is-active", item === button));
        const visible = category === "전체" ? presetItems : presetItems.filter((item) => item.category_name === category);
        renderItems(visible, pageTitle.textContent);
        const missing = visible.filter((item) => !item.coordinateAvailable).length;
        setMapStatus(`${visible.length}곳 중 ${visible.length - missing}곳을 지도에 표시했습니다.${missing ? ` 좌표 미등록 ${missing}곳` : ""}`);
      });
      categoryList.append(button);
    });
  }

  async function fetchPresetData() {
    const response = await Api.get(`/presets/${presetId}/map-restaurants`);
    const data = response.data || {};
    presetItems = (data.restaurants || []).map(toPresetPlace);
    pageTitle.textContent = editMode ? `${data.title || "보물지도"} 맛집 관리` : (data.title || "보물지도");
    document.title = `${data.title || "보물지도"} 지도 · 푸드덕`;
    return data;
  }

  async function loadPreset() {
    if (!Number.isSafeInteger(presetId) || presetId <= 0) {
      setResultsState(0, "보물지도");
      renderEmptyResults("올바른 presetId가 필요합니다. 보물지도 상세에서 지도 보기를 이용해 주세요.");
      setMapStatus("올바른 presetId가 필요합니다.", true);
      return;
    }
    const data = await fetchPresetData();
    renderPresetFilters();
    renderItems(presetItems, data.title || "보물지도", true, "preset");
    const missing = presetItems.filter((item) => !item.coordinateAvailable).length;
    setMapStatus(`${presetItems.length}곳 중 ${presetItems.length - missing}곳을 지도에 표시했습니다.${missing ? ` 좌표 미등록 ${missing}곳` : ""}`);
    if (Number.isSafeInteger(requestedRestaurantId) && requestedRestaurantId > 0) {
      selectRestaurant(requestedRestaurantId);
      placeResults.querySelector(`[data-restaurant-id="${requestedRestaurantId}"]`)?.scrollIntoView({ block: "nearest" });
    }
  }

  function getCurrentLocation() {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(null);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude }),
        () => resolve(null),
        { timeout: 5000 },
      );
    });
  }

  async function centerOnCurrentLocation(showStatus = true) {
    if (!kakaoMap) return false;
    const coords = await getCurrentLocation();
    if (!coords) {
      if (showStatus) setMapStatus("현재 위치를 확인할 수 없거나 위치 권한이 없습니다.", true);
      return false;
    }
    const position = new kakao.maps.LatLng(coords.latitude, coords.longitude);
    kakaoMap.setCenter(position);
    kakaoMap.setLevel(4);
    if (currentPositionMarker) currentPositionMarker.setPosition(position);
    else currentPositionMarker = new kakao.maps.Marker({ map: kakaoMap, position, image: getMarkerImage("detail_open.svg"), zIndex: 100 });
    if (showStatus) setMapStatus("현재 위치로 이동했습니다.");
    return true;
  }

  function boundsAroundCenter(center) {
    const latDelta = SEARCH_RADIUS_METERS / 111320;
    const lngDelta = SEARCH_RADIUS_METERS / (111320 * Math.cos((center.getLat() * Math.PI) / 180));
    const bounds = new kakao.maps.LatLngBounds();
    bounds.extend(new kakao.maps.LatLng(center.getLat() - latDelta, center.getLng() - lngDelta));
    bounds.extend(new kakao.maps.LatLng(center.getLat() + latDelta, center.getLng() + lngDelta));
    return bounds;
  }

  // 검색 페이지 등에서 특정 매장 ID로 바로 넘어온 경우, 반경 검색이 아니라
  // 해당 매장을 직접 조회해 정확한 좌표로 포커싱한다.
  async function focusPublicRestaurant(id) {
    lastSearchKeyword = "";
    searchAreaButton.hidden = true;
    setMapStatus("음식점 정보를 불러오는 중입니다.");
    // 상세·리뷰·메뉴를 동시에 요청해 둔다 — 포커싱이 끝나자마자 상세 패널도 바로 채워지도록.
    const detailPromise = Api.get(`/public/map/restaurants/${id}`, { auth: false });
    const reviewsPromise = Api.get(`/public/map/restaurants/${id}/reviews`, { auth: false });
    const menuPromise = Api.get(`/public/map/restaurants/${id}/menu`, { auth: false });
    try {
      const detail = (await detailPromise).data;
      const place = {
        restaurantId: detail.id,
        place_name: detail.branchName ? `${detail.name} ${detail.branchName}` : detail.name,
        category_name: detail.categorySmallName || detail.categoryLargeName || "기타",
        category_group_name: "",
        road_address_name: detail.roadAddress || "",
        address_name: detail.lotAddress || "",
        y: detail.lat,
        x: detail.lon,
        favoriteByCurrentUser: Boolean(detail.favoritedByMe),
        coordinateAvailable: validCoordinate(detail.lat, detail.lon),
      };
      renderItems([place], "검색 결과", true, "search");
      if (place.coordinateAvailable) {
        selectRestaurant(place.restaurantId, true, { detailPromise, reviewsPromise, menuPromise });
      } else {
        setMapStatus("이 음식점은 등록된 좌표가 없어 지도에는 표시되지 않습니다.", true);
      }
    } catch (error) {
      renderEmptyResults("음식점 정보를 불러오지 못했습니다.");
      setResultsState(0);
      setMapStatus(error.message || "음식점 정보를 불러오지 못했습니다.", true);
    }
  }

  async function searchPlaces(keyword) {
    const normalized = keyword.trim();
    if (!kakaoMap || !normalized) {
      setMapStatus(!kakaoMap ? "지도가 아직 준비되지 않았습니다." : "검색어를 입력해 주세요.", true);
      return;
    }
    lastSearchKeyword = normalized;
    searchAreaButton.hidden = true;

    // 정확히 일치하는 매장명이 DB 전체에서 딱 하나뿐이면, 지도 반경(500m) 제한 없이
    // 그 매장으로 바로 이동·포커싱한다(동명 매장이 여러 곳이면 아래 반경 검색으로 진행).
    try {
      const exactResponse = await Api.get(
        `/public/map/restaurants/find-by-name?name=${encodeURIComponent(normalized)}`,
        { auth: false },
      );
      if (exactResponse.data?.id) {
        await focusPublicRestaurant(exactResponse.data.id);
        return;
      }
    } catch (error) {
      // 정확 매칭 조회가 실패해도 아래 반경 검색으로 계속 진행한다.
    }

    const bounds = boundsAroundCenter(kakaoMap.getCenter());
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();
    const params = new URLSearchParams({ swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng(), keyword: normalized });
    try {
      setMapStatus(`“${normalized}” 검색 중입니다.`);
      const response = await Api.get(`/public/map/restaurants?${params}`, { auth: false });
      const results = (response.data || []).map(toPublicPlace);
      renderItems(results, "검색 결과", true, "search");
      setMapStatus(results.length ? `${results.length}개의 장소를 표시했습니다.` : "검색 결과가 없습니다.", !results.length);
    } catch (error) {
      renderEmptyResults("장소 검색 중 오류가 발생했습니다.");
      setResultsState(0);
      setMapStatus(error.message, true);
    }
  }

  detailClose.addEventListener("click", () => {
    if (activeMarkerEntry) activeMarkerEntry.marker.setImage(getMarkerImage(activeMarkerEntry.assetName));
    activeMarkerEntry = undefined;
    placeResults.querySelector(".place-result.is-active")?.classList.remove("is-active");
    closeDetailPanel();
  });

  searchForm.addEventListener("submit", (event) => { event.preventDefault(); searchPlaces(keywordInput.value); });
  categoryList.querySelectorAll("[data-category-keyword]").forEach((button) => {
    button.addEventListener("click", () => { keywordInput.value = button.dataset.categoryKeyword; searchPlaces(button.dataset.categoryKeyword); });
  });
  searchAreaButton.addEventListener("click", () => searchPlaces(lastSearchKeyword));
  presetManageBack.addEventListener("click", () => {
    keywordInput.value = "";
    loadPreset();
  });
  locationButton.addEventListener("click", () => {
    if (!kakaoMap) {
      setMapStatus("현재 위치를 확인할 수 없습니다.", true);
      return;
    }
    centerOnCurrentLocation(true);
  });

  function setSidebarCollapsed(collapsed) {
    mapPage.classList.toggle("sidebar-collapsed", collapsed);
    sidebarToggleButton.setAttribute("aria-expanded", String(!collapsed));
    window.FooduckIcons.set(sidebarToggleIcon, collapsed ? "chevron_right" : "chevron_left");
    try { localStorage.setItem("mapSidebarCollapsed", collapsed ? "1" : "0"); } catch { /* 상태 기억 생략 */ }
  }
  sidebarToggleButton.addEventListener("click", () => setSidebarCollapsed(!mapPage.classList.contains("sidebar-collapsed")));
  mapPage.addEventListener("transitionend", () => kakaoMap?.relayout());
  if (localStorage.getItem("mapSidebarCollapsed") === "1") setSidebarCollapsed(true);

  if (presetMode) {
    mapPage.classList.add("preset-map-mode");
    if (presetBreadcrumb) presetBreadcrumb.hidden = false;
    if (!editMode) {
      searchForm.hidden = true;
      locationButton.hidden = true;
      searchAreaButton.hidden = true;
    }
  }

  (async () => {
    try {
      const config = await Api.get("/public/map/config", { auth: false });
      if (config.data?.configured) {
        await loadKakaoSdk(config.data.javascriptKey);
        initializeMap();
      } else {
        mapPlaceholder.querySelector("strong").textContent = "지도 설정이 필요합니다";
        mapPlaceholder.querySelector("small").textContent = "목록은 확인할 수 있지만 마커는 표시되지 않습니다.";
      }
      if (presetMode) {
        await loadPreset();
        return;
      }
      if (!kakaoMap) {
        setMapStatus("Kakao Maps JavaScript 키 설정이 필요합니다.", true);
        renderEmptyResults("서버의 Kakao Map 공개 설정을 확인해 주세요.");
        return;
      }
      // 기본적으로 현재 위치를 기준으로 지도를 표시한다(권한 거부/실패 시 기본 중심 유지).
      await centerOnCurrentLocation(false);
      if (Number.isSafeInteger(requestedRestaurantId) && requestedRestaurantId > 0) {
        await focusPublicRestaurant(requestedRestaurantId);
      } else {
        const keyword = query.get("q")?.trim();
        if (keyword) {
          keywordInput.value = keyword;
          await searchPlaces(keyword);
        } else {
          setResultsState(0);
          renderEmptyResults("검색어를 입력하거나 카테고리를 선택하면 맛집을 보여드려요.");
        }
      }
    } catch (error) {
      setMapStatus(error.message || "지도를 준비하지 못했습니다.", true);
      renderEmptyResults(error.message || "잠시 후 다시 시도해 주세요.");
    }
  })();
})();
