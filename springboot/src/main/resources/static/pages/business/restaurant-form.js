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
  const title = document.getElementById("restaurant-form-title");
  const description = document.getElementById("restaurant-form-description");

  if (
    !session || !gate || !gateTitle || !gateCopy || !gateLink || !content ||
    !loading || !panel || !form || !submitButton || !status || !title || !description
  ) {
    return;
  }

  const rawId = new URLSearchParams(window.location.search).get("id");
  const restaurantId = rawId && /^\d+$/.test(rawId) ? Number(rawId) : null;
  const editing = Number.isSafeInteger(restaurantId) && restaurantId > 0;

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
    setValue("latitude", data.latitude);
    setValue("longitude", data.longitude);
    setValue("description", data.description);
  }

  function optionalNumber(value) {
    return value === "" ? null : Number(value);
  }

  function payload() {
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
      latitude: optionalNumber(form.elements.latitude.value),
      longitude: optionalNumber(form.elements.longitude.value),
    };
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    submitButton.disabled = true;
    submitButton.textContent = editing ? "수정 중..." : "등록 중...";
    setStatus(editing ? "음식점 정보를 수정하고 있습니다." : "음식점을 등록하고 있습니다.");
    try {
      const result = editing
        ? await Api.put(`/business/restaurants/${restaurantId}`, payload())
        : await Api.post("/business/restaurants", payload());
      setStatus(result.message || "저장되었습니다.");
      window.location.assign("/pages/business/detail.html?tab=restaurants");
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
        `/pages/auth/login.html?next=${encodeURIComponent(next)}`,
        "로그인",
      );
      return;
    }
    if (!session.canManageBusiness) {
      showGate(
        "사업자 권한이 필요합니다",
        "사업자 페이지에서 권한 신청과 승인 상태를 확인해 주세요.",
        "/pages/business/index.html",
        "사업자 페이지",
      );
      return;
    }

    try {
      const requests = [Api.get("/public/restaurant-categories", { auth: false })];
      if (editing) requests.push(Api.get(`/business/restaurants/${restaurantId}`));
      const [categoryPayload, restaurantPayload] = await Promise.all(requests);
      fillCategories(Array.isArray(categoryPayload.data) ? categoryPayload.data : []);
      if (editing) {
        title.textContent = "음식점 정보 수정";
        description.textContent = "내가 등록한 음식점의 기본 정보를 수정합니다.";
        submitButton.textContent = "수정하기";
        document.title = "음식점 정보 수정 · 사업자 페이지 · 푸드덕";
        fillRestaurant(restaurantPayload.data || {});
      }
      loading.hidden = true;
      panel.hidden = false;
    } catch (error) {
      loading.innerHTML = "";
      const message = document.createElement("strong");
      message.textContent = error.message || "음식점 정보를 불러오지 못했습니다.";
      loading.append(message);
    }
  }

  init();
})();
