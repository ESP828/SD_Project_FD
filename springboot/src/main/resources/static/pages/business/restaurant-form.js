(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("restaurant-form-gate");
  const gateTitle = document.getElementById("restaurant-form-gate-title");
  const gateCopy = document.getElementById("restaurant-form-gate-copy");
  const gateLink = document.getElementById("restaurant-form-gate-link");
  const content = document.getElementById("restaurant-form-content");
  const loading = document.getElementById("restaurant-form-loading");
  const panel = document.getElementById("restaurant-form-panel");
  const form = document.getElementById("restaurant-form");
  const submitButton = document.getElementById("restaurant-form-submit");
  const status = document.getElementById("restaurant-form-status");
  const coordinateStatus = document.getElementById("restaurant-coordinate-status");
  const title = document.getElementById("restaurant-form-title");
  const description = document.getElementById("restaurant-form-description");

  if (
    !session || !gate || !gateTitle || !gateCopy || !gateLink || !content ||
    !loading || !panel || !form || !submitButton || !status || !coordinateStatus ||
    !title || !description
  ) {
    return;
  }

  const rawId = new URLSearchParams(window.location.search).get("id");
  const restaurantId = rawId && /^\d+$/.test(rawId) ? Number(rawId) : null;
  const editing = Number.isSafeInteger(restaurantId) && restaurantId > 0;
  let resolvedCoordinates = null;
  let resolvedAddress = "";
  let coordinateLookup = null;
  let kakaoGeocoderPromise = null;

  function showGate(gateHeading, copy, href, linkLabel) {
    content.hidden = true;
    gate.hidden = false;
    gateTitle.textContent = gateHeading;
    gateCopy.textContent = copy;
    gateLink.href = href;
    gateLink.textContent = linkLabel;
  }

  function setStatus(message, error = false) {
    status.textContent = message || "";
    status.classList.toggle("is-error", error);
  }

  function setCoordinateStatus(message, state = "") {
    coordinateStatus.textContent = message;
    coordinateStatus.classList.toggle("is-success", state === "success");
    coordinateStatus.classList.toggle("is-error", state === "error");
  }

  function normalizedAddress() {
    return form.elements.address.value.trim().replace(/\s+/g, " ");
  }

  function validCoordinates(latitude, longitude) {
    return Number.isFinite(latitude)
      && Number.isFinite(longitude)
      && latitude >= -90
      && latitude <= 90
      && longitude >= -180
      && longitude <= 180
      && !(latitude === 0 && longitude === 0);
  }

  async function getKakaoGeocoder() {
    if (window.kakao?.maps?.services?.Geocoder) {
      return new window.kakao.maps.services.Geocoder();
    }
    if (kakaoGeocoderPromise) return kakaoGeocoderPromise;

    kakaoGeocoderPromise = (async () => {
      const config = await Api.get("/public/map/config", { auth: false });
      const javascriptKey = String(config?.data?.javascriptKey || "").trim();
      if (!javascriptKey) {
        throw new Error("주소 확인에 필요한 지도 설정이 없습니다. 관리자에게 문의해 주세요.");
      }

      await new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.async = true;
        script.dataset.fooduckKakaoGeocoder = "";
        script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(javascriptKey)}&autoload=false&libraries=services`;
        script.addEventListener("load", () => {
          if (!window.kakao?.maps) {
            reject(new Error("카카오 지도 SDK를 초기화하지 못했습니다."));
            return;
          }
          window.kakao.maps.load(() => {
            if (window.kakao?.maps?.services?.Geocoder) resolve();
            else reject(new Error("주소 검색 서비스를 불러오지 못했습니다."));
          });
        }, { once: true });
        script.addEventListener(
          "error",
          () => reject(new Error("주소 검색 서비스를 불러오지 못했습니다. 네트워크를 확인해 주세요.")),
          { once: true },
        );
        document.head.append(script);
      });
      return new window.kakao.maps.services.Geocoder();
    })();

    try {
      return await kakaoGeocoderPromise;
    } catch (error) {
      kakaoGeocoderPromise = null;
      throw error;
    }
  }

  async function resolveAddressCoordinates() {
    const address = normalizedAddress();
    if (!address) throw new Error("좌표를 확인할 주소를 입력해 주세요.");
    if (
      address === resolvedAddress
      && resolvedCoordinates
      && validCoordinates(resolvedCoordinates.latitude, resolvedCoordinates.longitude)
    ) {
      return resolvedCoordinates;
    }
    if (coordinateLookup?.address === address) return coordinateLookup.promise;

    setCoordinateStatus("주소에서 지도 좌표를 확인하고 있습니다.");
    const promise = (async () => {
      const geocoder = await getKakaoGeocoder();
      const result = await new Promise((resolve, reject) => {
        geocoder.addressSearch(address, (items, searchStatus) => {
          if (searchStatus !== window.kakao.maps.services.Status.OK || !items?.length) {
            reject(new Error("입력한 주소의 위치를 찾지 못했습니다. 도로명 주소를 확인해 주세요."));
            return;
          }
          const latitude = Number(items[0].y);
          const longitude = Number(items[0].x);
          if (!validCoordinates(latitude, longitude)) {
            reject(new Error("주소에서 올바른 좌표를 확인하지 못했습니다."));
            return;
          }
          resolve({ latitude, longitude });
        });
      });
      resolvedAddress = address;
      resolvedCoordinates = result;
      setCoordinateStatus("주소 위치를 확인했습니다.", "success");
      return result;
    })();
    coordinateLookup = { address, promise };
    try {
      return await promise;
    } catch (error) {
      setCoordinateStatus(error.message || "주소 위치를 확인하지 못했습니다.", "error");
      throw error;
    } finally {
      if (coordinateLookup?.promise === promise) coordinateLookup = null;
    }
  }

  function fillCategories(items) {
    const select = form.elements.categoryId;
    items.forEach((item) => {
      const option = document.createElement("option");
      option.value = String(item.categoryId);
      option.textContent = item.name || item.categoryCode || `카테고리 ${item.categoryId}`;
      select.append(option);
    });
  }

  function setValue(name, value) {
    form.elements[name].value = value ?? "";
  }

  function fillRestaurant(data) {
    setValue("name", data.name);
    setValue("categoryId", data.categoryId);
    setValue("address", data.address);
    setValue("addressDetail", data.addressDetail);
    setValue("phone", data.phone);
    setValue("closedDays", data.closedDays);
    setValue("openingHours", data.openingHours);
    setValue("description", data.description);
    const latitude = Number(data.latitude);
    const longitude = Number(data.longitude);
    if (validCoordinates(latitude, longitude)) {
      resolvedAddress = normalizedAddress();
      resolvedCoordinates = { latitude, longitude };
      setCoordinateStatus("저장된 주소 위치를 사용합니다.", "success");
    }
  }

  function payload(coordinates) {
    return {
      name: form.elements.name.value.trim(),
      categoryId: form.elements.categoryId.value
        ? Number(form.elements.categoryId.value)
        : null,
      address: form.elements.address.value.trim(),
      addressDetail: form.elements.addressDetail.value.trim() || null,
      phone: form.elements.phone.value.trim() || null,
      openingHours: form.elements.openingHours.value.trim() || null,
      closedDays: form.elements.closedDays.value.trim() || null,
      description: form.elements.description.value.trim() || null,
      latitude: coordinates.latitude,
      longitude: coordinates.longitude,
    };
  }

  form.elements.address.addEventListener("input", () => {
    if (normalizedAddress() !== resolvedAddress) {
      resolvedCoordinates = null;
      setCoordinateStatus("주소를 입력하면 저장 전에 지도 좌표를 자동으로 확인합니다.");
    }
  });

  form.elements.address.addEventListener("blur", () => {
    if (normalizedAddress()) {
      resolveAddressCoordinates().catch(() => {
        // 오류는 주소 안내 영역에 표시하고 제출 시 다시 확인한다.
      });
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    submitButton.disabled = true;
    submitButton.textContent = editing ? "수정 중..." : "등록 중...";
    setStatus(editing ? "음식점 정보를 수정하고 있습니다." : "음식점을 등록하고 있습니다.");
    try {
      const coordinates = await resolveAddressCoordinates();
      const requestBody = payload(coordinates);
      const result = editing
        ? await Api.put(`/business/restaurants/${restaurantId}`, requestBody)
        : await Api.post("/business/restaurants", requestBody);
      setStatus(result.message || "저장되었습니다.");
      window.location.assign("/business/detail?tab=restaurants");
    } catch (error) {
      setStatus(error.message || "음식점 정보를 저장하지 못했습니다.", true);
      submitButton.disabled = false;
      submitButton.textContent = editing ? "수정하기" : "등록하기";
    }
  });

  async function init() {
    if (!session.authenticated) {
      const next = `${window.location.pathname}${window.location.search}`;
      showGate(
        "로그인이 필요합니다",
        "사업자 음식점 관리는 로그인한 계정만 이용할 수 있습니다.",
        `/auth/login?next=${encodeURIComponent(next)}`,
        "로그인",
      );
      return;
    }
    if (!session.canManageBusiness) {
      showGate(
        "사업자 권한이 필요합니다",
        "사업자 페이지에서 권한 신청과 승인 상태를 확인해 주세요.",
        "/business",
        "사업자 페이지",
      );
      return;
    }

    try {
      const requests = [Api.get("/public/restaurant-categories", { auth: false })];
      if (editing) requests.push(Api.get(`/business/restaurants/${restaurantId}`));
      const [categoryPayload, restaurantPayload] = await Promise.all(requests);
      const categories = Array.isArray(categoryPayload.data) ? categoryPayload.data : [];
      if (categories.length === 0) {
        throw new Error("등록 가능한 음식점 카테고리가 없습니다. 관리자에게 문의해 주세요.");
      }
      fillCategories(categories);
      if (editing) {
        title.textContent = "음식점 정보 수정";
        description.textContent = "내가 등록한 음식점의 기본 정보를 수정합니다.";
        submitButton.textContent = "수정하기";
        document.title = "음식점 정보 수정 · 사업자 페이지 · 푸드덕";
        fillRestaurant(restaurantPayload.data || {});
      }
      panel.hidden = false;
    } catch (error) {
      panel.hidden = false;
      submitButton.disabled = true;
      setStatus(error.message || "음식점 정보를 불러오지 못했습니다.", true);
    } finally {
      loading.hidden = true;
    }
  }

  init();
})();
