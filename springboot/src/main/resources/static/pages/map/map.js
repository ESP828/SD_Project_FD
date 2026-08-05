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
  const presetContext = document.getElementById("preset-map-context");
  const presetSummary = document.getElementById("preset-map-summary");
  const presetBack = document.getElementById("preset-map-back");

<<<<<<< Updated upstream
let lastSearchKeyword = "";
=======
  const query = new URLSearchParams(location.search);
  const presetMode = query.has("presetId");
  const presetId = Number(query.get("presetId"));
  const requestedRestaurantId = Number(query.get("restaurantId"));
  const SEARCH_RADIUS_METERS = 500;
  const markerAssetRoot = "/images/markers";
  const markerImageCache = new Map();
>>>>>>> Stashed changes

  let kakaoMap;
  let currentPositionMarker;
  let markerEntries = new Map();
  let activeMarkerEntry;
  let activeInfoWindow;
  let lastSearchKeyword = "";
  let presetItems = [];

  function setMapStatus(message, isError = false) {
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

<<<<<<< Updated upstream
function initializeMap() {
  const defaultCenter = new kakao.maps.LatLng(37.5665, 126.978);
  kakaoMap = new kakao.maps.Map(document.getElementById("map"), {
    center: defaultCenter,
    level: 5,
  });
  kakaoMap.addControl(
    new kakao.maps.ZoomControl(),
    kakao.maps.ControlPosition.RIGHT,
  );
  mapPlaceholder.hidden = true;
  setMapStatus("서울 시청을 기준으로 지도를 열었습니다.");
=======
  function initializeMap() {
    kakaoMap = new kakao.maps.Map(document.getElementById("map"), {
      center: new kakao.maps.LatLng(37.5665, 126.978),
      level: 3,
    });
    kakaoMap.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
    mapPlaceholder.hidden = true;
    const showSearchAreaButton = () => {
      if (!presetMode && lastSearchKeyword) searchAreaButton.hidden = false;
    };
    kakao.maps.event.addListener(kakaoMap, "dragend", showSearchAreaButton);
    kakao.maps.event.addListener(kakaoMap, "zoom_changed", showSearchAreaButton);
  }
>>>>>>> Stashed changes

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
    activeMarkerEntry = undefined;
    if (activeInfoWindow) activeInfoWindow.close();
    activeInfoWindow = undefined;
  }

  function createInfoWindowContent(place) {
    const wrapper = document.createElement("div");
    wrapper.className = "fooduck-info-window";
    const name = document.createElement("strong");
    name.textContent = place.place_name;
    const address = document.createElement("span");
    address.textContent = place.road_address_name || place.address_name || "주소 정보 없음";
    wrapper.append(name, address);
    return wrapper.outerHTML;
  }

  function selectRestaurant(restaurantId, moveMap = true) {
    const key = String(restaurantId);
    const row = placeResults.querySelector(`[data-restaurant-id="${key}"]`);
    placeResults.querySelector(".place-result.is-active")?.classList.remove("is-active");
    row?.classList.add("is-active");
    const entry = markerEntries.get(key);
    if (!entry) {
      setMapStatus("이 음식점은 등록된 좌표가 없어 목록에서만 확인할 수 있습니다.", true);
      return;
    }
    if (activeMarkerEntry) activeMarkerEntry.marker.setImage(getMarkerImage(activeMarkerEntry.assetName));
    activeMarkerEntry = entry;
    entry.marker.setImage(getMarkerImage("state_selected.svg"));
    if (activeInfoWindow) activeInfoWindow.close();
    activeInfoWindow = new kakao.maps.InfoWindow({ content: createInfoWindowContent(entry.place), removable: true });
    activeInfoWindow.open(kakaoMap, entry.marker);
    if (moveMap) kakaoMap.panTo(entry.position);
    setMapStatus(`“${entry.place.place_name}” 위치를 선택했습니다.`);
  }

  function requireLogin() {
    if (window.FooduckSession?.authenticated) return true;
    location.assign(`/pages/auth/login.html?next=${encodeURIComponent(`${location.pathname}${location.search}`)}`);
    return false;
  }

  async function toggleRestaurantFavorite(button, place) {
    if (!requireLogin()) return;
    button.disabled = true;
    try {
      const response = place.favoriteByCurrentUser
        ? await Api.delete(`/restaurants/${place.restaurantId}/favorite`)
        : await Api.post(`/restaurants/${place.restaurantId}/favorite`);
      place.favoriteByCurrentUser = Boolean(response.data?.favoriteByCurrentUser);
      button.classList.toggle("is-active", place.favoriteByCurrentUser);
      button.textContent = place.favoriteByCurrentUser ? "♥ 저장됨" : "♡ 저장";
    } catch (error) {
      alert(error.message);
    } finally {
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
    if (presetMode) {
      const favorite = document.createElement("button");
      favorite.className = "place-result-link place-result-favorite";
      favorite.type = "button";
      favorite.classList.toggle("is-active", place.favoriteByCurrentUser);
      favorite.textContent = place.favoriteByCurrentUser ? "♥ 저장됨" : "♡ 저장";
      favorite.addEventListener("click", () => toggleRestaurantFavorite(favorite, place));
      body.append(favorite);
    }
    article.append(markerImage, body);
    return article;
  }

<<<<<<< Updated upstream
  const normalizedKeyword = keyword.trim();
  if (!normalizedKeyword) {
    setMapStatus("검색어를 입력해 주세요.", true);
    keywordInput.focus();
    return;
  }

  lastSearchKeyword = normalizedKeyword;
  searchAreaButton.hidden = true;

  setMapStatus(`“${normalizedKeyword}” 검색 중입니다.`);
  placeResults.innerHTML =
    '<div class="result-loading"><span class="material-symbols-rounded" aria-hidden="true">progress_activity</span><p>맛집을 찾고 있습니다.</p></div>';

  const bounds = nearby
    ? boundsAroundCenter(kakaoMap.getCenter(), 3000)
    : kakaoMap.getBounds();

  let items;
  try {
    items = await fetchRestaurantsInBounds(bounds, normalizedKeyword);
  } catch (error) {
    clearPlaceMarkers();
    setResultsState(0);
    renderEmptyResults("장소 검색 중 오류가 발생했습니다.");
    setMapStatus("장소 검색에 실패했습니다.", true);
    return;
  }

  categoryButtons.forEach((button) => {
    button.classList.toggle(
      "is-active",
      button.dataset.categoryKeyword === normalizedKeyword,
    );
  });

  if (items.length === 0) {
    clearPlaceMarkers();
    setResultsState(0);
    renderEmptyResults("검색 결과가 없습니다. 다른 지역이나 음식점 이름을 입력해 보세요.");
    setMapStatus("검색 결과가 없습니다.", true);
    return;
  }

  clearPlaceMarkers();
  const results = items.map(toPlaceLike);
  const resultBounds = new kakao.maps.LatLngBounds();

  results.forEach((place, index) => {
    const position = new kakao.maps.LatLng(place.y, place.x);
    const assetName = resolveCategoryMarker(place);
    const marker = new kakao.maps.Marker({
      map: kakaoMap,
      position,
      image: getMarkerImage(assetName),
      zIndex: index + 1,
=======
  function renderItems(items, title, fitBounds = true) {
    clearMarkers();
    placeResults.replaceChildren();
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
>>>>>>> Stashed changes
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

  async function loadPreset() {
    if (!Number.isSafeInteger(presetId) || presetId <= 0) {
      setResultsState(0, "Presset 지도");
      renderEmptyResults("올바른 presetId가 필요합니다. Presset 상세에서 지도 보기를 이용해 주세요.");
      setMapStatus("올바른 presetId가 필요합니다.", true);
      return;
    }
    const response = await Api.get(`/presets/${presetId}/map-restaurants`);
    const data = response.data || {};
    presetItems = (data.restaurants || []).map(toPresetPlace);
    pageTitle.textContent = data.title || "Presset 지도";
    presetSummary.textContent = data.summary || "이 Presset에 포함된 맛집만 표시합니다.";
    presetBack.href = `/pages/presset/detail.html?presetId=${encodeURIComponent(presetId)}`;
    document.title = `${data.title || "Presset"} 지도 · 푸드덕`;
    renderPresetFilters();
    renderItems(presetItems, data.title || "Presset 지도");
    const missing = presetItems.filter((item) => !item.coordinateAvailable).length;
    setMapStatus(`${presetItems.length}곳 중 ${presetItems.length - missing}곳을 지도에 표시했습니다.${missing ? ` 좌표 미등록 ${missing}곳` : ""}`);
    if (Number.isSafeInteger(requestedRestaurantId) && requestedRestaurantId > 0) {
      selectRestaurant(requestedRestaurantId);
      placeResults.querySelector(`[data-restaurant-id="${requestedRestaurantId}"]`)?.scrollIntoView({ block: "nearest" });
    }
  }

  function boundsAroundCenter(center) {
    const latDelta = SEARCH_RADIUS_METERS / 111320;
    const lngDelta = SEARCH_RADIUS_METERS / (111320 * Math.cos((center.getLat() * Math.PI) / 180));
    const bounds = new kakao.maps.LatLngBounds();
    bounds.extend(new kakao.maps.LatLng(center.getLat() - latDelta, center.getLng() - lngDelta));
    bounds.extend(new kakao.maps.LatLng(center.getLat() + latDelta, center.getLng() + lngDelta));
    return bounds;
  }

  async function searchPlaces(keyword) {
    const normalized = keyword.trim();
    if (!kakaoMap || !normalized) {
      setMapStatus(!kakaoMap ? "지도가 아직 준비되지 않았습니다." : "검색어를 입력해 주세요.", true);
      return;
    }
    lastSearchKeyword = normalized;
    searchAreaButton.hidden = true;
    const bounds = boundsAroundCenter(kakaoMap.getCenter());
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();
    const params = new URLSearchParams({ swLat: sw.getLat(), swLng: sw.getLng(), neLat: ne.getLat(), neLng: ne.getLng(), keyword: normalized });
    try {
      setMapStatus(`“${normalized}” 검색 중입니다.`);
      const response = await Api.get(`/public/map/restaurants?${params}`, { auth: false });
      const results = (response.data || []).map(toPublicPlace);
      renderItems(results, "검색 결과");
      setMapStatus(results.length ? `${results.length}개의 장소를 표시했습니다.` : "검색 결과가 없습니다.", !results.length);
    } catch (error) {
      renderEmptyResults("장소 검색 중 오류가 발생했습니다.");
      setResultsState(0);
      setMapStatus(error.message, true);
    }
  }

  searchForm.addEventListener("submit", (event) => { event.preventDefault(); searchPlaces(keywordInput.value); });
  categoryList.querySelectorAll("[data-category-keyword]").forEach((button) => {
    button.addEventListener("click", () => { keywordInput.value = button.dataset.categoryKeyword; searchPlaces(button.dataset.categoryKeyword); });
  });
<<<<<<< Updated upstream

  if (!nearby) {
    kakaoMap.setBounds(resultBounds);
  }
  renderPlaceResults(results);
  setResultsState(results.length);
  setMapStatus(`${results.length}개의 장소를 표시했습니다.`);
}

locationButton.addEventListener("click", () => {
  if (!kakaoMap) {
    setMapStatus("지도가 아직 준비되지 않았습니다.", true);
    return;
  }
  if (!navigator.geolocation) {
    setMapStatus("이 브라우저는 위치 확인을 지원하지 않습니다.", true);
    return;
  }

  setMapStatus("현재 위치를 확인하고 있습니다.");
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
=======
  searchAreaButton.addEventListener("click", () => searchPlaces(lastSearchKeyword));
  locationButton.addEventListener("click", () => {
    if (!kakaoMap || !navigator.geolocation) {
      setMapStatus("현재 위치를 확인할 수 없습니다.", true);
      return;
    }
    navigator.geolocation.getCurrentPosition(({ coords }) => {
>>>>>>> Stashed changes
      const position = new kakao.maps.LatLng(coords.latitude, coords.longitude);
      kakaoMap.setCenter(position);
      kakaoMap.setLevel(4);
      if (currentPositionMarker) currentPositionMarker.setPosition(position);
      else currentPositionMarker = new kakao.maps.Marker({ map: kakaoMap, position, image: getMarkerImage("detail_open.svg"), zIndex: 100 });
      setMapStatus("현재 위치로 이동했습니다.");
    }, () => setMapStatus("위치 권한이 없거나 현재 위치를 확인할 수 없습니다.", true));
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

<<<<<<< Updated upstream
function setSidebarCollapsed(collapsed) {
  mapPage.classList.toggle("sidebar-collapsed", collapsed);
  sidebarToggleButton.setAttribute("aria-expanded", String(!collapsed));
  sidebarToggleButton.setAttribute("aria-label", collapsed ? "검색 패널 펼치기" : "검색 패널 접기");
  sidebarToggleIcon.textContent = collapsed ? "chevron_right" : "chevron_left";
  try {
    localStorage.setItem("mapSidebarCollapsed", collapsed ? "1" : "0");
  } catch {
    // 프라이빗 브라우징 등으로 localStorage를 못 쓰면 상태 기억만 생략한다.
=======
  if (presetMode) {
    mapPage.classList.add("preset-map-mode");
    presetContext.hidden = false;
    searchForm.hidden = true;
    locationButton.hidden = true;
    searchAreaButton.hidden = true;
>>>>>>> Stashed changes
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
      const keyword = query.get("q")?.trim();
      if (keyword) {
        keywordInput.value = keyword;
        await searchPlaces(keyword);
      } else {
        setResultsState(0);
        setMapStatus("검색어를 입력하거나 카테고리를 선택해 주세요.");
        renderEmptyResults("검색어를 입력하거나 카테고리를 선택하면 맛집을 보여드려요.");
      }
    } catch (error) {
      setMapStatus(error.message || "지도를 준비하지 못했습니다.", true);
      renderEmptyResults(error.message || "잠시 후 다시 시도해 주세요.");
    }
<<<<<<< Updated upstream

    await loadKakaoSdk(response.data.javascriptKey);
    initializeMap();

    const queryKeyword = new URLSearchParams(window.location.search).get("q");
    const initialKeyword = queryKeyword?.trim() || "서울 맛집";
    keywordInput.value = initialKeyword;
    searchPlaces(initialKeyword);
  } catch (error) {
    setMapStatus(error.message, true);
    renderEmptyResults(error.message);
  }
=======
  })();
>>>>>>> Stashed changes
})();
