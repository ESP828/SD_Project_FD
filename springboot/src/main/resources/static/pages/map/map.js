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
  const presetAddHint = document.getElementById("preset-add-hint");
  const detailPanel = document.getElementById("place-detail-panel");
  const detailClose = document.getElementById("place-detail-close");
  const detailBody = document.getElementById("place-detail-body");
  const mapElement = document.getElementById("map");

  const query = new URLSearchParams(location.search);
  const presetMode = query.has("presetId");
  const presetId = Number(query.get("presetId"));
  const editMode = presetMode && query.has("edit");
  const requestedRestaurantId = Number(query.get("restaurantId"));
  const SEARCH_RADIUS_METERS = 500;
  // 첫 진입 시 보여줄 "내 주변 맛집"은 검색보다 조금 넓은 반경으로 잡는다.
  const NEARBY_RADIUS_METERS = 1200;
  const markerAssetRoot = "/images/markers";
  const markerImageCache = new Map();

  // 가게 상세로 다녀와도 검색어·결과·지도 위치가 그대로 남아 있도록 탭 단위로 저장해둔다.
  const MAP_STATE_KEY = "fooduck:map-state";
  const MAP_STATE_TTL_MS = 30 * 60 * 1000;
  // 사이드바(320ms)와 가게 상세 패널(280ms) 슬라이드를 모두 덮는 추적 시간.
  const PANEL_TRANSITION_MS = 420;

  let kakaoMap;
  let currentPositionMarker;
  let markerEntries = new Map();
  let currentPlaces = new Map();
  let activeMarkerEntry;
  let lastSearchKeyword = "";
  let lastSearchCategory = "";
  let presetItems = [];
  let detailRequestToken = 0;
  let resultMode = "preset";
  let userLocation = null;
  let currentPlaceList = [];
  let currentResultTitle = "검색 결과";
  let selectedRestaurantId = null;
  // 패널이 슬라이드하는 동안 지도를 붙잡아 두기 위한 기준점과 추적 상태.
  let panelAnchor = null;
  // 우리가 마지막으로 맞춰 놓은 지도 위치. 이 값과 달라졌다면 사용자 조작이나
  // panTo 같은 다른 이동이 있었다는 뜻이라, 기준점을 그 자리에서 다시 잡는다.
  let lastHeldView = null;
  let panelResizeFrame = 0;
  let panelResizeUntil = 0;
  const pendingFavoriteIds = new Set();

  function setMapStatus(message, isError = false) {
    if (!mapStatus) return;
    mapStatus.textContent = message;
    mapStatus.classList.toggle("is-error", isError);
  }

  function setResultsState(count, title = "검색 결과") {
    resultTitle.textContent = title;
    resultCount.textContent = String(count);
  }

  /**
   * 결과가 없을 때는 이유만 알려주지 말고 다음에 할 수 있는 행동까지 같이 제안한다.
   * actions: [{ label, onClick, variant }]
   */
  function renderEmptyResults(message, actions = []) {
    placeResults.replaceChildren();
    const empty = document.createElement("div");
    empty.className = "result-empty";
    const image = document.createElement("img");
    image.src = "/images/characters/error.png";
    image.alt = "";
    const copy = document.createElement("p");
    copy.textContent = message;
    empty.append(image, copy);
    if (actions.length) {
      const actionRow = document.createElement("div");
      actionRow.className = "result-empty-actions";
      actions.forEach((action) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `button button-sm ${action.variant || "button-secondary"}`;
        button.textContent = action.label;
        button.addEventListener("click", action.onClick);
        actionRow.append(button);
      });
      empty.append(actionRow);
    }
    placeResults.append(empty);
  }

  /** 검색 결과가 없을 때 사용자가 바로 이어서 할 수 있는 행동들. */
  function emptySearchActions() {
    const actions = [
      {
        label: "검색어 다시 입력",
        variant: "button-primary",
        onClick: () => {
          keywordInput.focus();
          keywordInput.select();
        },
      },
      {
        label: "검색 초기화",
        onClick: () => {
          keywordInput.value = "";
          lastSearchKeyword = "";
          lastSearchCategory = "";
          searchAreaButton.hidden = true;
          categoryList.querySelectorAll("button").forEach((item) => item.classList.remove("is-active"));
          showInitialResults();
        },
      },
    ];
    if (userLocation) {
      actions.push({ label: "내 주변 맛집 보기", onClick: () => loadNearbyRestaurants() });
    }
    return actions;
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

  // 기본 좌표(서울시청)로 먼저 그렸다가 현재 위치로 옮기면 화면이 한 번 튀기 때문에,
  // 위치를 먼저 확인한 뒤 그 좌표로 지도를 생성한다(실패 시에만 기본 좌표를 쓴다).
  function initializeMap(center, level = 3) {
    kakaoMap = new kakao.maps.Map(mapElement, {
      center: center || new kakao.maps.LatLng(37.5665, 126.978),
      level,
    });
    // TOPRIGHT로 고정해야 "내 위치로 이동" 버튼을 그 아래에 정확히 붙일 수 있다
    // (RIGHT는 지도 높이에 따라 수직 중앙으로 움직여서 위치 계산이 불안정함).
    kakaoMap.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.TOPRIGHT);
    mapPlaceholder.hidden = true;
    const showSearchAreaButton = () => {
      // 검색 중이거나 주변 맛집을 보고 있을 때만 "이 지역에서 찾기"를 노출한다.
      if ((!presetMode || editMode) && (lastSearchKeyword || resultMode === "nearby")) {
        searchAreaButton.hidden = false;
      }
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

  // 공공데이터 음식점과 사업자 등록 음식점은 ID 체계가 달라 상세 경로를 sourceType으로 나눈다.
  // (보물지도에 담기는 맛집도 public_restaurant 기준이라 PUBLIC으로 취급한다.)
  function isOwnedPlace(place) {
    return (place?.sourceType || "PUBLIC") === "OWNED";
  }

  function detailHref(place) {
    const source = isOwnedPlace(place) ? "owned" : "public";
    return `/restaurant/detail?source=${source}&id=${encodeURIComponent(place.restaurantId)}`;
  }

  function closeDetailPanel() {
    detailRequestToken += 1;
    if (!detailPanel.classList.contains("is-open")) return;
    rememberMapAnchor();
    detailPanel.classList.remove("is-open");
    trackPanelResize();
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

  // 요청 중에 button.disabled를 쓰면 커서가 "클릭 금지"로 바뀌어 눌리지 않는 것처럼 보인다.
  // 실제로 막아야 하는 건 짧은 시간 안의 중복 요청뿐이라 처리 중인 ID만 따로 기억한다.
  async function toggleDetailPanelFavorite(button, place) {
    if (!requireLogin()) return;
    const favoriteKey = `${place.sourceType || "PUBLIC"}:${place.restaurantId}`;
    if (pendingFavoriteIds.has(favoriteKey)) return;
    const path = isOwnedPlace(place)
      ? `/restaurants/${place.restaurantId}/favorite`
      : `/map/restaurants/${place.restaurantId}/favorite`;
    pendingFavoriteIds.add(favoriteKey);
    button.classList.add("is-pending");
    button.setAttribute("aria-busy", "true");
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
      pendingFavoriteIds.delete(favoriteKey);
      button.classList.remove("is-pending");
      button.removeAttribute("aria-busy");
    }
  }

  function renderDetailPanel(place, detail, menuItems, reviewPage) {
    const isOwned = isOwnedPlace(place);
    const category = place.category_name || "기타";
    const address = place.road_address_name || place.address_name || "주소 정보 없음";
    const reviews = Array.isArray(reviewPage?.items) ? reviewPage.items : [];
    const reviewCount = Number(reviewPage?.totalElements ?? reviews.length) || 0;

    // 공공데이터 음식점은 평균 별점 집계 API가 따로 없다. 이 패널은 미리보기라
    // 지금 불러온 리뷰만으로 평균을 만들지 않고, 전체 통계가 있는 사업자 등록 매장만 표시한다.
    const ratingAvg = isOwned ? detail?.averageRating : null;

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
        <strong>${ratingAvg != null ? `★ ${ratingAvg.toFixed(1)}` : (reviewCount ? `리뷰 ${reviewCount}건` : "리뷰 없음")}</strong>
        <span>${ratingAvg != null ? `리뷰 ${reviewCount}건` : ""}${isOwned && detail?.phone ? `${ratingAvg != null ? " · " : ""}${detail.phone}` : ""}</span>
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
      <a class="button button-primary place-detail-link"
         href="${detailHref(place)}">상세 페이지에서 더 보기</a>
    `;

    const favoriteButton = document.getElementById("place-detail-favorite-btn");
    favoriteButton.addEventListener("click", () => toggleDetailPanelFavorite(favoriteButton, place));
  }

  // prefetch로 { detailPromise, reviewsPromise, menuPromise }를 넘기면 새로 요청하지 않고
  // 이미 진행 중인 요청을 그대로 재사용한다(포커싱 직후 상세 패널이 바로 뜨도록).
  async function openDetailPanel(place, prefetch) {
    if (!detailPanel.classList.contains("is-open")) {
      rememberMapAnchor();
      detailPanel.classList.add("is-open");
      trackPanelResize();
    }
    detailBody.innerHTML = '<div class="place-detail-loading">불러오는 중입니다...</div>';
    const requestId = (detailRequestToken += 1);
    const isOwned = isOwnedPlace(place);
    const detailRoot = isOwned ? "/public/restaurants" : "/public/map/restaurants";

    try {
      const [detailResponse, reviewsResponse, menuResponse] = await Promise.all([
        prefetch?.detailPromise ?? Api.get(`${detailRoot}/${place.restaurantId}`),
        // 리뷰는 페이지 단위 API만 제공된다. 패널은 미리보기라 첫 3건만 받는다.
        prefetch?.reviewsPromise ?? Api.get(`${detailRoot}/${place.restaurantId}/reviews/page?page=0&size=3`, { auth: false }),
        prefetch?.menuPromise ?? Api.get(`${detailRoot}/${place.restaurantId}/menu`, { auth: false }),
      ]);
      if (requestId !== detailRequestToken) return;
      place.favoriteByCurrentUser = Boolean(detailResponse.data?.favoritedByMe);
      renderDetailPanel(place, detailResponse.data, menuResponse.data || [], reviewsResponse.data || {});
    } catch (error) {
      if (requestId !== detailRequestToken) return;
      detailBody.innerHTML = `<div class="place-detail-error">${escapeHtml(error.message || "가게 정보를 불러오지 못했습니다.")}</div>`;
    }
  }

  function selectRestaurant(restaurantId, moveMap = true, prefetch = null) {
    const key = String(restaurantId);
    selectedRestaurantId = restaurantId;
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
    if (Number.isFinite(place.distanceMeters)) {
      const distance = document.createElement("span");
      distance.className = "place-result-distance";
      distance.textContent = place.distanceMeters < 1000
        ? `내 위치에서 약 ${Math.round(place.distanceMeters)}m`
        : `내 위치에서 약 ${(place.distanceMeters / 1000).toFixed(1)}km`;
      select.append(distance);
    }
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
    // 보물지도에는 공공데이터 음식점만 담을 수 있어서, 사업자 등록 매장에는 추가 버튼을 두지 않는다.
    if (editMode && resultMode === "search" && !isOwnedPlace(place)) {
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
    detail.href = detailHref(place);
    // 새 탭으로 열면 상세페이지의 뒤로가기가 검색 화면으로 빠져 버려서, 같은 탭에서 이동시킨다.
    detail.addEventListener("click", () => saveMapState());
    detail.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">open_in_new</span>상세보기';
    actions.append(detail);
    body.append(actions);
    article.append(markerImage, body);
    return article;
  }

  function renderItems(items, title, fitBounds = true, mode = "preset", emptyState = {}) {
    resultMode = mode;
    currentPlaceList = items;
    currentResultTitle = title;
    presetManageBack.hidden = !(editMode && mode === "search");
    clearMarkers();
    placeResults.replaceChildren();
    items.forEach((place) => currentPlaces.set(String(place.restaurantId), place));
    items.forEach((place, index) => placeResults.append(createResultRow(place, index)));
    setResultsState(items.length, title);
    if (!items.length) {
      renderEmptyResults(
        emptyState.message || "조건에 맞는 음식점이 없습니다.",
        emptyState.actions || [],
      );
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
      // 보물지도에 담기는 맛집은 공공데이터 음식점(public_restaurant) 기준이다.
      sourceType: "PUBLIC",
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

  // 통합 검색 결과(공공데이터 + 사업자 등록)를 지도에서 쓰는 place 형태로 바꾼다.
  function toSearchPlace(item) {
    const latitude = Number(item.lat);
    const longitude = Number(item.lon);
    return {
      sourceType: item.sourceType === "OWNED" ? "OWNED" : "PUBLIC",
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

  // 위치는 지도 생성 전에 한 번 받아두고 이후에는 캐시된 값을 재사용한다.
  function getCurrentLocation() {
    if (userLocation) return Promise.resolve(userLocation);
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(null);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        ({ coords }) => {
          userLocation = { latitude: coords.latitude, longitude: coords.longitude };
          resolve(userLocation);
        },
        () => resolve(null),
        { timeout: 5000 },
      );
    });
  }

  // 음식점 마커(노란 핀)와 헷갈리지 않도록 현재 위치는 파란색 원형 표식으로 따로 그린다.
  function showCurrentPositionMarker(position) {
    if (!kakaoMap) return;
    if (currentPositionMarker) {
      currentPositionMarker.setPosition(position);
      currentPositionMarker.setMap(kakaoMap);
      return;
    }
    const content = document.createElement("div");
    content.className = "map-current-position";
    content.setAttribute("aria-hidden", "true");
    content.innerHTML = '<span class="map-current-position-pulse"></span>'
      + '<span class="map-current-position-dot"></span>';
    currentPositionMarker = new kakao.maps.CustomOverlay({
      map: kakaoMap,
      position,
      content,
      zIndex: 100,
      xAnchor: 0.5,
      yAnchor: 0.5,
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
    showCurrentPositionMarker(position);
    if (showStatus) setMapStatus("현재 위치로 이동했습니다.");
    return true;
  }

  function boundsAroundCenter(center, radiusMeters = SEARCH_RADIUS_METERS) {
    const latDelta = radiusMeters / 111320;
    const lngDelta = radiusMeters / (111320 * Math.cos((center.getLat() * Math.PI) / 180));
    const bounds = new kakao.maps.LatLngBounds();
    bounds.extend(new kakao.maps.LatLng(center.getLat() - latDelta, center.getLng() - lngDelta));
    bounds.extend(new kakao.maps.LatLng(center.getLat() + latDelta, center.getLng() + lngDelta));
    return bounds;
  }

  function boundsParams(bounds) {
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();
    return new URLSearchParams({
      swLat: sw.getLat(),
      swLng: sw.getLng(),
      neLat: ne.getLat(),
      neLng: ne.getLng(),
    });
  }

  function distanceInMeters(fromLat, fromLng, toLat, toLng) {
    const earthRadius = 6371000;
    const toRadian = (value) => (value * Math.PI) / 180;
    const deltaLat = toRadian(toLat - fromLat);
    const deltaLng = toRadian(toLng - fromLng);
    const a = Math.sin(deltaLat / 2) ** 2
      + Math.cos(toRadian(fromLat)) * Math.cos(toRadian(toLat)) * Math.sin(deltaLng / 2) ** 2;
    return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  /**
   * 검색어 없이 특정 지점 주변의 맛집을 거리 가까운 순으로 보여준다.
   * 첫 진입 시 "검색 결과 0"을 띄우는 대신 쓰는 기본 목록이다.
   */
  async function loadRestaurantsAround(center, title) {
    if (!kakaoMap) return false;
    const params = boundsParams(boundsAroundCenter(center, NEARBY_RADIUS_METERS));
    // 거리는 내 위치를 알면 내 위치 기준, 모르면 화면 중심 기준으로 계산한다.
    const originLat = userLocation ? userLocation.latitude : center.getLat();
    const originLng = userLocation ? userLocation.longitude : center.getLng();
    lastSearchKeyword = "";
    lastSearchCategory = "";
    searchAreaButton.hidden = true;
    setMapStatus("주변 맛집을 찾고 있습니다.");
    try {
      const response = await Api.get(`/public/search/restaurants/bounds?${params}`, { auth: false });
      const places = (response.data || [])
        .map(toSearchPlace)
        .map((place) => ({
          ...place,
          distanceMeters: place.coordinateAvailable
            ? distanceInMeters(originLat, originLng, place.y, place.x)
            : null,
        }))
        .sort((a, b) => (a.distanceMeters ?? Infinity) - (b.distanceMeters ?? Infinity));
      // 주변 목록은 보고 있던 중심을 유지해야 해서 마커 기준으로 화면을 다시 맞추지 않는다.
      renderItems(places, title, false, "nearby", {
        message: "이 주변에서는 맛집을 찾지 못했습니다. 지도를 옮기거나 검색어를 입력해 보세요.",
      });
      setMapStatus(places.length
        ? `${title} ${places.length}곳을 가까운 순으로 보여드립니다.`
        : "이 주변에서 맛집을 찾지 못했습니다.");
      saveMapState();
      return places.length > 0;
    } catch (error) {
      renderEmptyResults("주변 맛집을 불러오지 못했습니다.");
      setResultsState(0);
      setMapStatus(error.message || "주변 맛집을 불러오지 못했습니다.", true);
      return false;
    }
  }

  function loadNearbyRestaurants() {
    if (!kakaoMap || !userLocation) return Promise.resolve(false);
    const center = new kakao.maps.LatLng(userLocation.latitude, userLocation.longitude);
    return loadRestaurantsAround(center, "내 주변 맛집");
  }

  /** 검색어 없이 들어왔을 때의 기본 화면: 위치를 알면 주변 맛집, 모르면 안내 문구. */
  async function showInitialResults() {
    if (userLocation && await loadNearbyRestaurants()) return;
    setResultsState(0);
    renderEmptyResults("검색어를 입력하거나 카테고리를 선택하면 맛집을 보여드려요.");
  }

  // 검색 페이지 등에서 특정 매장 ID로 바로 넘어온 경우, 반경 검색이 아니라
  // 해당 매장을 직접 조회해 정확한 좌표로 포커싱한다.
  async function focusRestaurant(id, sourceType = "PUBLIC") {
    lastSearchKeyword = "";
    lastSearchCategory = "";
    searchAreaButton.hidden = true;
    setMapStatus("음식점 정보를 불러오는 중입니다.");
    const isOwned = sourceType === "OWNED";
    const detailRoot = isOwned ? "/public/restaurants" : "/public/map/restaurants";
    // 상세·리뷰·메뉴를 동시에 요청해 둔다 — 포커싱이 끝나자마자 상세 패널도 바로 채워지도록.
    const detailPromise = Api.get(`${detailRoot}/${id}`, { auth: false });
    const reviewsPromise = Api.get(`${detailRoot}/${id}/reviews/page?page=0&size=3`, { auth: false });
    const menuPromise = Api.get(`${detailRoot}/${id}/menu`, { auth: false });
    try {
      const detail = (await detailPromise).data;
      const place = isOwned
        ? {
          sourceType: "OWNED",
          restaurantId: detail.restaurantId ?? detail.id,
          place_name: detail.name,
          category_name: detail.categoryName || "기타",
          category_group_name: "",
          road_address_name: detail.address || "",
          address_name: detail.address || "",
          y: detail.latitude,
          x: detail.longitude,
          favoriteByCurrentUser: Boolean(detail.favoritedByMe),
          coordinateAvailable: validCoordinate(Number(detail.latitude), Number(detail.longitude)),
        }
        : {
          sourceType: "PUBLIC",
          restaurantId: detail.id,
          place_name: detail.branchName ? `${detail.name} ${detail.branchName}` : detail.name,
          category_name: detail.categoryMediumName || detail.categorySmallName || detail.categoryLargeName || "기타",
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
      saveMapState();
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
    lastSearchCategory = "";
    searchAreaButton.hidden = true;

    // 정확히 일치하는 매장명이 DB 전체에서 딱 하나뿐이면, 지도 반경(500m) 제한 없이
    // 그 매장으로 바로 이동·포커싱한다(동명 매장이 여러 곳이면 아래 반경 검색으로 진행).
    try {
      const exactResponse = await Api.get(
        `/public/search/restaurants/find-by-name?name=${encodeURIComponent(normalized)}`,
        { auth: false },
      );
      if (exactResponse.data?.id) {
        await focusRestaurant(exactResponse.data.id, exactResponse.data.sourceType);
        return;
      }
    } catch (error) {
      // 정확 매칭 조회가 실패해도 아래 반경 검색으로 계속 진행한다.
    }

    const params = boundsParams(boundsAroundCenter(kakaoMap.getCenter()));
    params.set("keyword", normalized);
    try {
      setMapStatus(`“${normalized}” 검색 중입니다.`);
      const response = await Api.get(`/public/search/restaurants/bounds?${params}`, { auth: false });
      const results = (response.data || []).map(toSearchPlace);
      renderItems(results, "검색 결과", true, "search", {
        message: `이 지역에서 “${normalized}”에 대한 검색 결과를 찾지 못했습니다.`
          + " 검색어를 다시 확인하거나, 지도를 옮겨 다른 지역에서 찾아보세요.",
        actions: emptySearchActions(),
      });
      setMapStatus(results.length ? `${results.length}개의 장소를 표시했습니다.` : "검색 결과가 없습니다.", !results.length);
      saveMapState();
    } catch (error) {
      renderEmptyResults("장소 검색 중 오류가 발생했습니다.");
      setResultsState(0);
      setMapStatus(error.message, true);
    }
  }

  /**
   * 빠른 카테고리 버튼(한식/중식/... 등, 방금 정리한 대분류 기준) 검색.
   * 매장명이 아니라 category_medium_name과 정확히 일치하는 매장만 찾는다는 점에서
   * 이름을 입력해 찾는 searchPlaces와 다르다.
   */
  async function searchByCategory(category) {
    if (!kakaoMap) {
      setMapStatus("지도가 아직 준비되지 않았습니다.", true);
      return;
    }
    lastSearchKeyword = "";
    lastSearchCategory = category;
    keywordInput.value = "";
    searchAreaButton.hidden = true;

    const params = boundsParams(boundsAroundCenter(kakaoMap.getCenter()));
    params.set("category", category);
    try {
      setMapStatus(`“${category}” 검색 중입니다.`);
      const response = await Api.get(`/public/search/restaurants/bounds?${params}`, { auth: false });
      const results = (response.data || []).map(toSearchPlace);
      renderItems(results, "검색 결과", true, "search", {
        message: `이 지역에서 “${category}” 매장을 찾지 못했습니다. 지도를 옮겨 다른 지역에서 찾아보세요.`,
        actions: emptySearchActions(),
      });
      setMapStatus(results.length ? `${results.length}개의 장소를 표시했습니다.` : "검색 결과가 없습니다.", !results.length);
      saveMapState();
    } catch (error) {
      renderEmptyResults("장소 검색 중 오류가 발생했습니다.");
      setResultsState(0);
      setMapStatus(error.message, true);
    }
  }

  /**
   * 가게 상세로 이동했다가 돌아와도 검색어·결과·지도 위치가 그대로 남도록 저장한다.
   * 보물지도 모드는 presetId로 항상 같은 목록을 다시 불러오므로 저장하지 않는다.
   */
  function saveMapState() {
    if (presetMode || !kakaoMap) return;
    try {
      const center = kakaoMap.getCenter();
      sessionStorage.setItem(MAP_STATE_KEY, JSON.stringify({
        search: location.search,
        savedAt: Date.now(),
        keyword: keywordInput.value,
        lastSearchKeyword,
        lastSearchCategory,
        resultMode,
        title: currentResultTitle,
        places: currentPlaceList,
        selectedRestaurantId,
        center: { lat: center.getLat(), lng: center.getLng() },
        level: kakaoMap.getLevel(),
      }));
    } catch (_error) {
      /* 저장에 실패해도 지도 동작 자체에는 영향이 없다 */
    }
  }

  function readMapState() {
    try {
      const raw = sessionStorage.getItem(MAP_STATE_KEY);
      if (!raw) return null;
      const state = JSON.parse(raw);
      // 다른 조건(q, restaurantId 등)으로 들어온 경우에는 이전 상태를 되살리지 않는다.
      if (!state || state.search !== location.search) return null;
      if (Date.now() - Number(state.savedAt || 0) > MAP_STATE_TTL_MS) return null;
      if (!Array.isArray(state.places) || !state.places.length) return null;
      return state;
    } catch (_error) {
      return null;
    }
  }

  function restoreMapState() {
    const state = readMapState();
    if (!state) return false;
    keywordInput.value = state.keyword || "";
    lastSearchKeyword = state.lastSearchKeyword || "";
    lastSearchCategory = state.lastSearchCategory || "";
    if (lastSearchCategory) {
      categoryList.querySelectorAll("button").forEach((item) => {
        item.classList.toggle("is-active", item.dataset.category === lastSearchCategory);
      });
    }
    if (state.center) {
      kakaoMap.setCenter(new kakao.maps.LatLng(state.center.lat, state.center.lng));
      if (state.level) kakaoMap.setLevel(state.level);
    }
    // 저장해둔 결과를 그대로 그린다(같은 조건이므로 검색 API를 다시 부르지 않는다).
    renderItems(state.places, state.title || "검색 결과", false, state.resultMode || "search");
    if (state.selectedRestaurantId) {
      const row = placeResults.querySelector(`[data-restaurant-id="${state.selectedRestaurantId}"]`);
      row?.classList.add("is-active");
      row?.scrollIntoView({ block: "nearest" });
      selectedRestaurantId = state.selectedRestaurantId;
    }
    if (lastSearchKeyword || lastSearchCategory) searchAreaButton.hidden = false;
    setMapStatus("마지막으로 보던 검색 결과를 그대로 불러왔습니다.");
    return true;
  }

  window.addEventListener("pagehide", saveMapState);

  detailClose.addEventListener("click", () => {
    if (activeMarkerEntry) activeMarkerEntry.marker.setImage(getMarkerImage(activeMarkerEntry.assetName));
    activeMarkerEntry = undefined;
    placeResults.querySelector(".place-result.is-active")?.classList.remove("is-active");
    closeDetailPanel();
  });

  searchForm.addEventListener("submit", (event) => { event.preventDefault(); searchPlaces(keywordInput.value); });
  categoryList.querySelectorAll("[data-category]").forEach((button) => {
    button.addEventListener("click", () => {
      categoryList.querySelectorAll("button").forEach((item) => item.classList.toggle("is-active", item === button));
      searchByCategory(button.dataset.category);
    });
  });
  searchAreaButton.addEventListener("click", () => {
    if (lastSearchCategory) {
      searchByCategory(lastSearchCategory);
      return;
    }
    if (lastSearchKeyword) {
      searchPlaces(lastSearchKeyword);
      return;
    }
    loadRestaurantsAround(kakaoMap.getCenter(), "이 지역 맛집");
  });
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
    rememberMapAnchor();
    mapPage.classList.toggle("sidebar-collapsed", collapsed);
    sidebarToggleButton.setAttribute("aria-expanded", String(!collapsed));
    window.FooduckIcons.set(sidebarToggleIcon, collapsed ? "chevron_right" : "chevron_left");
    try { localStorage.setItem("mapSidebarCollapsed", collapsed ? "1" : "0"); } catch { /* 상태 기억 생략 */ }
  }
  sidebarToggleButton.addEventListener("click", () => {
    setSidebarCollapsed(!mapPage.classList.contains("sidebar-collapsed"));
    trackPanelResize();
  });

  // 페이지 스크롤에 영향받지 않도록 지도 컨테이너의 위치를 map-page 기준으로 잰다.
  function mapOffsetInPage() {
    const rect = mapElement.getBoundingClientRect();
    const pageRect = mapPage.getBoundingClientRect();
    return {
      left: rect.left - pageRect.left,
      top: rect.top - pageRect.top,
      width: rect.width,
      height: rect.height,
    };
  }

  /**
   * 패널을 여닫기 직전에 "화면의 이 지점에 이 좌표가 있었다"를 기록해 둔다.
   * 슬라이드가 진행되는 동안 매 프레임 이 기준으로 다시 계산하기 때문에,
   * 프레임마다 생기는 반올림 오차가 쌓이지 않는다.
   */
  function captureMapAnchor() {
    const { left, top, width, height } = mapOffsetInPage();
    if (!width || !height) {
      panelAnchor = null;
      return;
    }
    panelAnchor = {
      coords: kakaoMap.getProjection().coordsFromContainerPoint(
        new kakao.maps.Point(width / 2, height / 2),
      ),
      pageX: left + width / 2,
      pageY: top + height / 2,
    };
  }

  function markMapHeld() {
    const center = kakaoMap.getCenter();
    lastHeldView = { lat: center.getLat(), lng: center.getLng(), level: kakaoMap.getLevel() };
  }

  function mapMovedElsewhere() {
    if (!lastHeldView) return true;
    const center = kakaoMap.getCenter();
    return center.getLat() !== lastHeldView.lat
      || center.getLng() !== lastHeldView.lng
      || kakaoMap.getLevel() !== lastHeldView.level;
  }

  /**
   * 패널을 여닫기 직전에 호출한다.
   * 지도가 우리가 맞춰 둔 그대로라면 기준점을 새로 잡지 않는다 - 새로 잡으면 픽셀 반올림으로
   * 남은 1px 안팎의 오차가 새 기준으로 굳어져서, 토글할 때마다 대각선으로 조금씩 밀려간다.
   */
  function rememberMapAnchor() {
    if (!kakaoMap) {
      panelAnchor = null;
      lastHeldView = null;
      return;
    }
    if (!panelAnchor || mapMovedElsewhere()) {
      captureMapAnchor();
      markMapHeld();
    }
  }

  /**
   * 검색 패널과 가게 상세 패널은 지도와 나란히 놓인 형제 요소라, 열고 닫는 동안 지도 컨테이너의
   * 왼쪽 모서리가 계속 움직인다. 카카오맵 relayout()은 지도 내용을 컨테이너 좌상단 기준으로
   * 유지하므로, 그냥 두면 보고 있던 지도도 패널을 따라 같이 밀려간다.
   * 기준점이 화면상 원래 자리에 오도록 중심을 다시 잡아, 패널만 움직이고 지도는 멈춰 있게 한다.
   */
  function holdMapView() {
    if (!kakaoMap) return;
    // relayout은 보이는 영역이 바뀌면서 지도 중심 좌표도 함께 바꾸므로, 외부 이동 여부는 그 전에 본다.
    const movedElsewhere = mapMovedElsewhere();
    // 클래스를 바꾼 직후에 불려도 새 크기를 보도록 레이아웃을 먼저 확정시킨다.
    void mapElement.getBoundingClientRect();
    kakaoMap.relayout();
    // panTo·드래그 등 다른 이동이 진행 중이면 붙잡지 않고 그 자리를 새 기준으로 삼는다.
    if (movedElsewhere || !panelAnchor) {
      captureMapAnchor();
      markMapHeld();
      return;
    }
    const { left, top, width, height } = mapOffsetInPage();
    if (!width || !height) return;
    const projection = kakaoMap.getProjection();
    // 가로만 맞추면 setCenter를 반복하면서 생기는 세로 반올림 오차가 그대로 쌓여
    // 토글할 때마다 지도가 대각선으로 조금씩 밀린다. 두 축 모두 기준점에 맞춘다.
    const current = projection.containerPointFromCoords(panelAnchor.coords);
    const deltaX = current.x - (panelAnchor.pageX - left);
    const deltaY = current.y - (panelAnchor.pageY - top);
    if (Math.abs(deltaX) >= 0.01 || Math.abs(deltaY) >= 0.01) {
      kakaoMap.setCenter(projection.coordsFromContainerPoint(
        new kakao.maps.Point(width / 2 + deltaX, height / 2 + deltaY),
      ));
    }
    markMapHeld();
  }

  /**
   * 슬라이드가 끝날 때 한 번만 보정하면, 진행 중에는 지도가 패널을 따라 밀려갔다가
   * 마지막에 제자리로 튕겨 돌아오는 것처럼 보인다. 전환이 도는 동안 매 프레임 붙잡아 둔다.
   */
  function trackPanelResize(durationMs = PANEL_TRANSITION_MS) {
    if (!kakaoMap) return;
    panelResizeUntil = performance.now() + durationMs;
    if (panelResizeFrame) return;
    const step = () => {
      holdMapView();
      if (performance.now() < panelResizeUntil) {
        panelResizeFrame = requestAnimationFrame(step);
        return;
      }
      panelResizeFrame = 0;
    };
    panelResizeFrame = requestAnimationFrame(step);
  }

  function stopPanelResizeTracking() {
    panelResizeUntil = 0;
    if (panelResizeFrame) {
      cancelAnimationFrame(panelResizeFrame);
      panelResizeFrame = 0;
    }
  }

  // 전환이 예정보다 일찍 끝나면 추적을 멈추고 마지막으로 한 번 더 자리를 맞춘다.
  // 버튼 hover 같은 다른 전환까지 relayout을 부르면 지도가 이유 없이 움직이므로 대상을 한정한다.
  mapPage.addEventListener("transitionend", (event) => {
    if (event.propertyName !== "width") return;
    const target = event.target;
    if (!(target instanceof Element) || !target.matches(".map-sidebar, .place-detail-panel")) return;
    stopPanelResizeTracking();
    holdMapView();
  });

  if (localStorage.getItem("mapSidebarCollapsed") === "1") {
    // 페이지를 열면서 저장된 상태를 되살리는 것뿐이라, 접히는 애니메이션을 보여줄 이유가 없다.
    // (이 전환이 지도 생성 시점과 겹치면 지도가 잘못된 크기로 만들어진다.)
    mapPage.classList.add("is-booting");
    setSidebarCollapsed(true);
    // 레이아웃을 한 번 읽어 접힌 상태를 그 자리에서 확정시킨 뒤 전환을 되살린다.
    // (이미 접힌 뒤라 클래스를 지워도 전환이 새로 시작되지 않는다.)
    void mapElement.getBoundingClientRect();
    mapPage.classList.remove("is-booting");
  }

  if (presetMode) {
    mapPage.classList.add("preset-map-mode");
    if (presetBreadcrumb) presetBreadcrumb.hidden = false;
    if (editMode) {
      // 편집 모드로 들어온 사용자는 "맛집 추가"가 목적이므로 사용법을 먼저 알려준다.
      if (presetAddHint) presetAddHint.hidden = false;
    } else {
      searchForm.hidden = true;
      locationButton.hidden = true;
      searchAreaButton.hidden = true;
    }
  }

  (async () => {
    try {
      // 지도 설정과 현재 위치를 함께 확인한 뒤 지도를 만든다.
      // 기본 좌표로 먼저 그렸다가 사용자 위치로 옮기면 첫 화면이 한 번 튀기 때문이다.
      const [config, coords] = await Promise.all([
        Api.get("/public/map/config", { auth: false }),
        presetMode ? Promise.resolve(null) : getCurrentLocation(),
      ]);
      if (config.data?.configured) {
        await loadKakaoSdk(config.data.javascriptKey);
        const initialCenter = coords
          ? new kakao.maps.LatLng(coords.latitude, coords.longitude)
          : null;
        initializeMap(initialCenter, initialCenter ? 4 : 3);
        if (initialCenter) showCurrentPositionMarker(initialCenter);
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
      // 상세페이지 등에서 돌아온 경우에는 마지막으로 보던 검색 결과를 그대로 되살린다.
      if (restoreMapState()) return;
      if (Number.isSafeInteger(requestedRestaurantId) && requestedRestaurantId > 0) {
        await focusRestaurant(requestedRestaurantId, "PUBLIC");
      } else {
        const keyword = query.get("q")?.trim();
        if (keyword) {
          keywordInput.value = keyword;
          await searchPlaces(keyword);
        } else {
          await showInitialResults();
        }
      }
    } catch (error) {
      setMapStatus(error.message || "지도를 준비하지 못했습니다.", true);
      renderEmptyResults(error.message || "잠시 후 다시 시도해 주세요.");
    }
  })();
})();
