const mapStatus = document.getElementById("map-status");
const mapPlaceholder = document.getElementById("map-placeholder");
const locationButton = document.getElementById("current-location-button");
const searchForm = document.getElementById("place-search-form");
const keywordInput = document.getElementById("place-keyword");
const placeResults = document.getElementById("place-results");
const resultCount = document.getElementById("result-count");
const resultTitle = document.getElementById("result-title");
const categoryButtons = document.querySelectorAll("[data-category-keyword]");
const mapPage = document.querySelector(".map-page");
const sidebarToggleButton = document.getElementById("sidebar-toggle-btn");
const sidebarToggleIcon = sidebarToggleButton.querySelector(".material-symbols-rounded");
const searchAreaButton = document.getElementById("search-area-button");

let lastSearchKeyword = "";

const markerAssetRoot = "/images/markers";
const markerImageCache = new Map();

let kakaoMap;
let currentPositionMarker;
let placeMarkers = [];
let activeMarkerEntry;
let activeInfoWindow;

function setMapStatus(message, isError = false) {
  mapStatus.textContent = message;
  mapStatus.classList.toggle("is-error", isError);
}

function loadKakaoSdk(javascriptKey) {
  return new Promise((resolve, reject) => {
    if (window.kakao?.maps) {
      window.kakao.maps.load(resolve);
      return;
    }

    const script = document.createElement("script");
    script.async = true;
    script.src =
      `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(javascriptKey)}` +
      "&autoload=false";
    script.onload = () => window.kakao.maps.load(resolve);
    script.onerror = () => reject(new Error("카카오맵 SDK를 불러오지 못했습니다."));
    document.head.appendChild(script);
  });
}

function getMarkerImage(assetName) {
  if (markerImageCache.has(assetName)) {
    return markerImageCache.get(assetName);
  }

  const image = new kakao.maps.MarkerImage(
    `${markerAssetRoot}/${assetName}`,
    new kakao.maps.Size(72, 48),
    { offset: new kakao.maps.Point(36, 48) },
  );
  markerImageCache.set(assetName, image);
  return image;
}

function resolveCategoryMarker(place) {
  const category = `${place.category_name || ""} ${place.category_group_name || ""}`;

  if (/카페|커피/.test(category)) return "category_cafe.png";
  if (/디저트|제과|베이커리|아이스크림/.test(category)) return "category_dessert.png";
  if (/중식|중국/.test(category)) return "category_chinese.png";
  if (/일식|일본|초밥|스시/.test(category)) return "category_japanese.png";
  if (/양식|이탈리안|프렌치|스테이크/.test(category)) return "category_western.png";
  if (/패스트푸드|햄버거|피자/.test(category)) return "category_fastfood.png";
  if (/술집|호프|주점|바/.test(category)) return "category_pub.png";
  if (/한식|국밥|고기|분식/.test(category)) return "category_korean.png";
  return "state_default.svg";
}

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

  const showSearchAreaButton = () => {
    if (lastSearchKeyword) {
      searchAreaButton.hidden = false;
    }
  };
  kakao.maps.event.addListener(kakaoMap, "dragend", showSearchAreaButton);
  kakao.maps.event.addListener(kakaoMap, "zoom_changed", showSearchAreaButton);
}

function clearPlaceMarkers() {
  placeMarkers.forEach(({ marker }) => marker.setMap(null));
  placeMarkers = [];
  activeMarkerEntry = undefined;
  if (activeInfoWindow) {
    activeInfoWindow.close();
    activeInfoWindow = undefined;
  }
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
  image.setAttribute("aria-hidden", "true");

  const copy = document.createElement("p");
  copy.textContent = message;

  empty.append(image, copy);
  placeResults.append(empty);
}

function createResultRow(place, index, markerEntry) {
  const article = document.createElement("article");
  article.className = "place-result";
  article.dataset.resultIndex = String(index);

  const markerImage = document.createElement("img");
  markerImage.className = "place-result-marker";
  markerImage.src = `${markerAssetRoot}/${markerEntry.assetName}`;
  markerImage.alt = "";
  markerImage.setAttribute("aria-hidden", "true");

  const body = document.createElement("div");
  body.className = "place-result-body";

  const selectButton = document.createElement("button");
  selectButton.className = "place-result-select";
  selectButton.type = "button";

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
  category.textContent =
    place.category_name?.split(" > ").slice(-1)[0] ||
    place.category_group_name ||
    "음식점";

  const address = document.createElement("span");
  address.className = "place-result-address";
  const addressIcon = document.createElement("span");
  addressIcon.className = "material-symbols-rounded";
  addressIcon.setAttribute("aria-hidden", "true");
  addressIcon.textContent = "location_on";
  const addressText = document.createElement("span");
  addressText.textContent =
    place.road_address_name || place.address_name || "주소 정보 없음";
  address.append(addressIcon, addressText);

  selectButton.append(top, category, address);

  if (place.phone) {
    const phone = document.createElement("span");
    phone.className = "place-result-phone";
    const phoneIcon = document.createElement("span");
    phoneIcon.className = "material-symbols-rounded";
    phoneIcon.setAttribute("aria-hidden", "true");
    phoneIcon.textContent = "call";
    const phoneText = document.createElement("span");
    phoneText.textContent = place.phone;
    phone.append(phoneIcon, phoneText);
    selectButton.append(phone);
  }

  selectButton.addEventListener("click", () => {
    selectPlace(index);
  });
  body.append(selectButton);

  if (place.place_url?.startsWith("http")) {
    const detailLink = document.createElement("a");
    detailLink.className = "place-result-link";
    detailLink.href = place.place_url;
    detailLink.target = "_blank";
    detailLink.rel = "noopener noreferrer";
    detailLink.textContent = "카카오 장소 상세";

    const linkIcon = document.createElement("span");
    linkIcon.className = "material-symbols-rounded";
    linkIcon.setAttribute("aria-hidden", "true");
    linkIcon.textContent = "open_in_new";
    detailLink.append(linkIcon);
    body.append(detailLink);
  }

  article.append(markerImage, body);
  return article;
}

function renderPlaceResults(results) {
  placeResults.replaceChildren();
  results.forEach((place, index) => {
    placeResults.append(createResultRow(place, index, placeMarkers[index]));
  });
}

function createInfoWindowContent(place) {
  const wrapper = document.createElement("div");
  wrapper.className = "fooduck-info-window";

  const name = document.createElement("strong");
  name.textContent = place.place_name;

  const address = document.createElement("span");
  address.textContent =
    place.road_address_name || place.address_name || "주소 정보 없음";

  wrapper.append(name, address);
  return wrapper.outerHTML;
}

function selectPlace(index, moveMap = true) {
  const markerEntry = placeMarkers[index];
  if (!markerEntry) {
    return;
  }

  if (activeMarkerEntry) {
    activeMarkerEntry.marker.setImage(getMarkerImage(activeMarkerEntry.assetName));
  }

  document.querySelector(".place-result.is-active")?.classList.remove("is-active");

  activeMarkerEntry = markerEntry;
  markerEntry.marker.setImage(getMarkerImage("state_selected.svg"));
  document
    .querySelector(`[data-result-index="${index}"]`)
    ?.classList.add("is-active");

  if (activeInfoWindow) {
    activeInfoWindow.close();
  }
  activeInfoWindow = new kakao.maps.InfoWindow({
    content: createInfoWindowContent(markerEntry.place),
    removable: true,
  });
  activeInfoWindow.open(kakaoMap, markerEntry.marker);

  if (moveMap) {
    kakaoMap.panTo(markerEntry.position);
  }
  setMapStatus(`“${markerEntry.place.place_name}” 위치를 선택했습니다.`);
}

function metersToLatDelta(meters) {
  return meters / 111320;
}

function metersToLngDelta(meters, atLat) {
  return meters / (111320 * Math.cos((atLat * Math.PI) / 180));
}

function boundsAroundCenter(center, radiusMeters) {
  const latDelta = metersToLatDelta(radiusMeters);
  const lngDelta = metersToLngDelta(radiusMeters, center.getLat());
  const bounds = new kakao.maps.LatLngBounds();
  bounds.extend(new kakao.maps.LatLng(center.getLat() - latDelta, center.getLng() - lngDelta));
  bounds.extend(new kakao.maps.LatLng(center.getLat() + latDelta, center.getLng() + lngDelta));
  return bounds;
}

function toPlaceLike(item) {
  return {
    place_name: item.name,
    category_name: item.categoryName || "",
    category_group_name: "",
    road_address_name: item.roadAddress || "",
    address_name: item.lotAddress || "",
    phone: "",
    place_url: "",
    y: item.lat,
    x: item.lon,
  };
}

async function fetchRestaurantsInBounds(bounds, keyword) {
  const sw = bounds.getSouthWest();
  const ne = bounds.getNorthEast();
  const params = new URLSearchParams({
    swLat: sw.getLat(),
    swLng: sw.getLng(),
    neLat: ne.getLat(),
    neLng: ne.getLng(),
  });
  if (keyword) {
    params.set("keyword", keyword);
  }
  const response = await Api.get(`/public/map/restaurants?${params.toString()}`, { auth: false });
  return response.data ?? [];
}

async function searchPlaces(keyword, { nearby = false } = {}) {
  if (!kakaoMap) {
    setMapStatus("지도가 아직 준비되지 않았습니다.", true);
    return;
  }

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
    });

    const markerEntry = { marker, position, place, assetName };
    kakao.maps.event.addListener(marker, "click", () => {
      selectPlace(index, false);
      document
        .querySelector(`[data-result-index="${index}"]`)
        ?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
    placeMarkers.push(markerEntry);
    resultBounds.extend(position);
  });

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
      const position = new kakao.maps.LatLng(coords.latitude, coords.longitude);
      kakaoMap.setCenter(position);
      kakaoMap.setLevel(4);

      if (currentPositionMarker) {
        currentPositionMarker.setPosition(position);
      } else {
        currentPositionMarker = new kakao.maps.Marker({
          map: kakaoMap,
          position,
          image: getMarkerImage("detail_open.svg"),
          zIndex: 100,
        });
      }
      setMapStatus("현재 위치로 이동했습니다. 카테고리를 누르면 주변을 검색합니다.");
    },
    () => setMapStatus("위치 권한이 없거나 현재 위치를 확인할 수 없습니다.", true),
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 },
  );
});

searchForm.addEventListener("submit", (event) => {
  event.preventDefault();
  searchPlaces(keywordInput.value);
});

categoryButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const keyword = button.dataset.categoryKeyword;
    keywordInput.value = keyword;
    searchPlaces(keyword, { nearby: true });
  });
});

searchAreaButton.addEventListener("click", () => {
  if (lastSearchKeyword) {
    searchPlaces(lastSearchKeyword, { nearby: false });
  }
});

function setSidebarCollapsed(collapsed) {
  mapPage.classList.toggle("sidebar-collapsed", collapsed);
  sidebarToggleButton.setAttribute("aria-expanded", String(!collapsed));
  sidebarToggleButton.setAttribute("aria-label", collapsed ? "검색 패널 펼치기" : "검색 패널 접기");
  sidebarToggleIcon.textContent = collapsed ? "chevron_right" : "chevron_left";
  try {
    localStorage.setItem("mapSidebarCollapsed", collapsed ? "1" : "0");
  } catch {
    // 프라이빗 브라우징 등으로 localStorage를 못 쓰면 상태 기억만 생략한다.
  }
}

mapPage.addEventListener("transitionend", (event) => {
  if (event.propertyName === "width" && event.target.classList.contains("map-sidebar") && kakaoMap) {
    kakaoMap.relayout();
  }
});

sidebarToggleButton.addEventListener("click", () => {
  setSidebarCollapsed(!mapPage.classList.contains("sidebar-collapsed"));
});

if (localStorage.getItem("mapSidebarCollapsed") === "1") {
  setSidebarCollapsed(true);
}

(async () => {
  try {
    const response = await Api.get("/public/map/config", { auth: false });
    if (!response.data.configured) {
      setMapStatus("Kakao Maps JavaScript 키 설정이 필요합니다.", true);
      renderEmptyResults("서버의 Kakao Map 공개 설정을 확인해 주세요.");
      return;
    }

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
})();
