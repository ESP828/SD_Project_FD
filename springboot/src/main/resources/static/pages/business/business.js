(() => {
  const session = window.FooduckSession;
  const profile = window.FooduckProfile;
  const loading = document.getElementById("business-loading");
  const loginGate = document.getElementById("business-login-gate");
  const profileHost = document.getElementById("business-profile-host");
  const application = document.getElementById("business-application");
  const applicationMetrics = document.getElementById("business-application-metrics");
  const applicationHistory = document.getElementById("business-application-history");
  const applicationStatus = document.getElementById("business-application-status");
  const dashboard = document.getElementById("business-dashboard");
  const businessMetrics = document.getElementById("business-metrics");
  const restaurantPanel = document.getElementById("business-restaurant-panel");
  const actionPanel = document.getElementById("business-action-panel");
  const statusPanel = document.getElementById("business-status-panel");

  if (
    !session || !profile || !loading || !loginGate || !profileHost ||
    !application || !applicationMetrics || !applicationHistory || !applicationStatus ||
    !dashboard || !businessMetrics || !restaurantPanel || !actionPanel || !statusPanel
  ) {
    return;
  }

  const state = {
    applications: [],
    form: application.querySelector(".application-form"),
    submitButton: application.querySelector("button[type='submit']"),
  };
  if (!state.form || !state.submitButton) return;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(Number(value) || 0);
  }

  function formatRating(value) {
    if (value === null || value === undefined || value === "") return "-";
    const rating = Number(value);
    return Number.isFinite(rating) ? rating.toFixed(1) : "-";
  }

  function formatDate(value) {
    if (!value) return "날짜 정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(date);
  }

  function metric(label, value, href, iconName, linkText = "상세 보기 →", formatter = formatNumber) {
    const card = element("a", "activity-card");
    const displayValue = formatter(value);
    card.href = href;
    card.setAttribute("aria-label", `${label} ${displayValue} ${linkText.replace("→", "")}`);

    const icon = element("span", "activity-card-icon");
    const materialIcon = element("span", "material-symbols-rounded", iconName);
    materialIcon.setAttribute("aria-hidden", "true");
    window.FooduckIcons?.set(materialIcon, iconName);
    icon.append(materialIcon);

    const copy = element("span", "activity-card-content");
    copy.append(
      element("span", "activity-card-label", label),
      element("strong", "activity-card-value", displayValue),
    );

    card.append(
      icon,
      copy,
      element("em", "activity-card-link", linkText),
    );
    return card;
  }

  function panelHeader(title, description) {
    const header = element("div", "mypage-panel-header");
    const copy = element("div");
    copy.append(element("h3", "", title), element("p", "", description));
    header.append(copy);
    return header;
  }

  function dashboardAction(label, description, href) {
    const node = href
      ? element("a", "mypage-action")
      : element("span", "mypage-action is-disabled");
    if (href) node.href = href;
    const copy = element("span");
    copy.append(element("strong", "", label), element("small", "", description));
    node.append(copy, element("span", "", href ? "→" : "준비 중"));
    return node;
  }

  function errorPanel(title, message) {
    const wrapper = element("div", "mypage-error management-section-error");
    const image = new Image();
    image.src = "/images/characters/error.png";
    image.alt = "";
    wrapper.append(
      image,
      element("h2", "", title),
      element("p", "", message || "잠시 후 다시 시도해 주세요."),
    );
    return wrapper;
  }

  function statusLabel(status) {
    return {
      PENDING: "승인 대기",
      APPROVED: "승인",
      REJECTED: "거절",
      CANCELED: "취소",
      ACTIVE: "운영 중",
      INACTIVE: "운영 중지",
    }[status] || status || "상태 미확인";
  }

  function statusBadge(status) {
    return element(
      "span",
      `management-status-badge management-status-badge--${String(status || "unknown").toLowerCase()}`,
      statusLabel(status),
    );
  }

  function profileActions() {
    const actions = [
      { label: "마이 페이지", href: "/mypage" },
    ];
    if (session.isAdmin) {
      actions.push({
        label: "관리자 페이지",
        href: "/admin",
      });
    }
    return actions;
  }

  function renderProfile(result) {
    profileHost.replaceChildren(profile.createSummary(result.data || {}, profileActions()));
  }

  function renderProfileError(error) {
    profileHost.replaceChildren(errorPanel(
      "계정 정보를 불러오지 못했습니다.",
      error?.message,
    ));
  }

  function applicationRow(item) {
    const row = element("article", "management-preview-row");
    const copy = element("div", "management-preview-copy");
    const heading = element("div", "management-preview-heading");
    heading.append(
      element("strong", "", item.businessName || "사업자 신청"),
      statusBadge(item.status),
    );
    copy.append(
      heading,
      element("p", "", `${item.representativeName || "대표자 미입력"} · ${formatDate(item.createdAt)} 신청`),
    );
    if (item.rejectReason) {
      copy.append(element("small", "", `거절 사유: ${item.rejectReason}`));
    }
    row.append(copy);
    return row;
  }

  function syncApplicationSubmitState() {
    const pending = state.applications.some((item) => item.status === "PENDING");
    state.submitButton.disabled = pending;
    state.submitButton.textContent = pending ? "승인 대기 중" : "신청 제출";
  }

  function renderApplications(items) {
    state.applications = Array.isArray(items) ? items : [];
    const countStatus = (status) => state.applications.filter((item) => item.status === status).length;
    applicationMetrics.replaceChildren(
      metric("신청 횟수", state.applications.length, "#business-application-history", "article"),
      metric("승인 대기", countStatus("PENDING"), "#business-application-history", "schedule"),
      metric("승인", countStatus("APPROVED"), "#business-application-history", "check_circle"),
      metric("거절", countStatus("REJECTED"), "#business-application-history", "error"),
      metric("취소", countStatus("CANCELED"), "#business-application-history", "close"),
    );

    applicationHistory.replaceChildren();
    if (state.applications.length === 0) {
      applicationHistory.append(element("p", "management-empty", "아직 제출한 신청이 없습니다."));
    } else {
      state.applications.forEach((item) => applicationHistory.append(applicationRow(item)));
    }

    const latest = state.applications[0];
    const image = new Image();
    image.src = "/images/characters/notification.png";
    image.alt = "";
    const copy = element("div");
    if (latest) {
      copy.append(
        element("strong", "", `최근 신청 · ${statusLabel(latest.status)}`),
        element("span", "", `${latest.businessName || "사업자 신청"}의 처리 상태입니다.`),
      );
    } else {
      copy.append(
        element("strong", "", "신청 내역 없음"),
        element("span", "", "사업자 정보를 입력해 첫 신청을 제출할 수 있습니다."),
      );
    }
    applicationStatus.replaceChildren(image, copy);
    syncApplicationSubmitState();
  }

  function renderApplicationError(error) {
    applicationMetrics.replaceChildren(errorPanel(
      "신청 현황을 불러오지 못했습니다.",
      error?.message,
    ));
    applicationHistory.replaceChildren(
      element("p", "management-empty", error?.message || "신청 내역을 불러오지 못했습니다."),
    );
    const copy = element("div");
    copy.append(
      element("strong", "", "신청 상태 확인 실패"),
      element("span", "", "잠시 후 다시 확인해 주세요."),
    );
    applicationStatus.replaceChildren(copy);
    state.submitButton.disabled = false;
    state.submitButton.textContent = "신청 제출";
  }

  function restaurantRow(restaurant) {
    const row = element("article", "management-preview-row");
    const copy = element("div", "management-preview-copy");
    const heading = element("div", "management-preview-heading");
    heading.append(
      element("strong", "", restaurant.name || "이름 없는 음식점"),
      statusBadge(restaurant.status),
    );
    copy.append(
      heading,
      element(
        "p",
        "",
        `${restaurant.categoryName || "카테고리 미지정"} · ${restaurant.address || "주소 정보 없음"}`,
      ),
      element(
        "small",
        "",
        `평점 ${formatRating(restaurant.averageRating)} · 리뷰 ${formatNumber(restaurant.reviewCount)} · 찜 ${formatNumber(restaurant.favoriteCount)} · ${formatDate(restaurant.createdAt)} 등록`,
      ),
    );
    const actions = element("div", "management-preview-actions");
    const viewLink = element("a", "button button-sm button-secondary", "가게 보기");
    viewLink.href = `/restaurant/detail?id=${encodeURIComponent(restaurant.restaurantId)}`;
    const manageLink = element("a", "button button-sm button-primary", "관리");
    manageLink.href = `/business/restaurant-form?id=${encodeURIComponent(restaurant.restaurantId)}`;
    actions.append(viewLink, manageLink);
    row.append(copy, actions);
    return row;
  }

  function renderBusinessOverview(data) {
    const restaurants = Array.isArray(data.restaurants) ? data.restaurants : [];
    businessMetrics.replaceChildren(
      metric("내 음식점", data.restaurantCount, "/business/detail?tab=restaurants", "storefront"),
      metric("운영 중", data.activeRestaurantCount, "/business/detail?tab=active", "store"),
      metric("가게 소식", data.newsCount, "/business/detail?tab=news", "campaign"),
      metric("받은 리뷰", data.reviewCount, "/business/detail?tab=reviews", "rate_review"),
      metric("찜 받은 수", data.favoriteCount, "/business/detail?tab=favorites", "favorite"),
    );

    const restaurantList = element("div", "management-preview-list");
    if (restaurants.length === 0) {
      restaurantList.append(element("p", "management-empty", "현재 계정에 연결된 음식점이 없습니다."));
    } else {
      restaurants.forEach((restaurant) => restaurantList.append(restaurantRow(restaurant)));
    }
    restaurantPanel.replaceChildren(
      panelHeader("내 음식점", "내 계정에 연결된 음식점과 현재 운영 상태"),
      restaurantList,
    );

    const actionList = element("div", "mypage-action-list");
    actionList.append(
      dashboardAction("음식점 등록", "새 음식점 등록", "/business/restaurant-form"),
      dashboardAction("내 음식점 관리", "등록한 음식점 상세 확인", "/business/detail?tab=restaurants"),
      dashboardAction("가게 소식 관리", "음식점별 소식 현황 확인", "/business/detail?tab=news"),
    );
    actionPanel.replaceChildren(
      panelHeader("사업자 기능", "내 음식점 운영에 필요한 기능"),
      actionList,
    );

    const statusLink = element("a", "notification-summary");
    statusLink.href = "/business/detail?tab=active";
    const statusImage = new Image();
    statusImage.src = "/images/characters/cooking.png";
    statusImage.alt = "";
    const statusCopy = element("div");
    statusCopy.append(
      element("strong", "", `운영 중 음식점 ${formatNumber(data.activeRestaurantCount)}곳`),
      element("span", "", `내 음식점 ${formatNumber(data.restaurantCount)}곳의 현재 운영 상태입니다.`),
    );
    statusLink.append(statusImage, statusCopy);
    statusPanel.replaceChildren(statusLink);
    dashboard.querySelector(".mypage-layout").hidden = false;
    window.FooduckIcons?.enhance(dashboard);
  }

  function renderBusinessError(error) {
    businessMetrics.replaceChildren(errorPanel(
      "사업자 현황을 불러오지 못했습니다.",
      error?.message,
    ));
    dashboard.querySelector(".mypage-layout").hidden = true;
  }

  function enableApplicationForm() {
    state.form.querySelectorAll("input, textarea").forEach((field) => {
      field.disabled = false;
    });
    state.submitButton.disabled = false;
  }

  function bindApplicationForm() {
    state.form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const payload = {
        businessName: state.form.elements.businessName.value.trim(),
        businessNumber: state.form.elements.businessNumber.value.trim(),
        representativeName: state.form.elements.representativeName.value.trim(),
        openedAt: state.form.elements.openedAt.value,
        contact: state.form.elements.contact.value.trim(),
        reason: state.form.elements.reason.value.trim(),
      };

      state.submitButton.disabled = true;
      state.submitButton.textContent = "제출 중...";
      try {
        const result = await Api.post("/business/applications", payload);
        state.form.reset();
        const saved = result.data || {};
        renderApplications([
          saved,
          ...state.applications.filter((item) => item.applicationId !== saved.applicationId),
        ]);
      } catch (error) {
        syncApplicationSubmitState();
        window.alert(error.message || "신청 처리 중 오류가 발생했습니다.");
      }
    });
  }

  function redirectToLoginIfNeeded() {
    if (localStorage.getItem("accessToken")) return false;
    window.location.assign(
      "/auth/login?next=" + encodeURIComponent("/business"),
    );
    return true;
  }

  async function init() {
    if (!session.authenticated) {
      loading.hidden = true;
      loginGate.hidden = false;
      return;
    }

    const profileRequest = Api.get("/mypage/overview");
    if (session.canManageBusiness) {
      const [profileResult, overviewResult] = await Promise.allSettled([
        profileRequest,
        Api.get("/business/overview"),
      ]);
      if (redirectToLoginIfNeeded()) return;
      if (profileResult.status === "fulfilled") renderProfile(profileResult.value);
      else renderProfileError(profileResult.reason);
      if (overviewResult.status === "fulfilled") renderBusinessOverview(overviewResult.value.data || {});
      else renderBusinessError(overviewResult.reason);
      loading.hidden = true;
      dashboard.hidden = false;
      return;
    }

    enableApplicationForm();
    bindApplicationForm();
    const [profileResult, applicationsResult] = await Promise.allSettled([
      profileRequest,
      Api.get("/business/applications"),
    ]);
    if (redirectToLoginIfNeeded()) return;
    if (profileResult.status === "fulfilled") renderProfile(profileResult.value);
    else renderProfileError(profileResult.reason);
    if (applicationsResult.status === "fulfilled") {
      renderApplications(applicationsResult.value.data || []);
    } else {
      renderApplicationError(applicationsResult.reason);
    }
    loading.hidden = true;
    application.hidden = false;
    window.FooduckIcons?.enhance(application);
  }

  init();
})();
