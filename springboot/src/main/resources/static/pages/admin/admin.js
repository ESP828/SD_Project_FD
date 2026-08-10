(() => {
  const session = window.FooduckSession;
  const profile = window.FooduckProfile;
  const gate = document.getElementById("admin-access-gate");
  const dashboard = document.getElementById("admin-dashboard");
  const loading = document.getElementById("admin-loading");
  const profileHost = document.getElementById("admin-profile-host");
  const overviewError = document.getElementById("admin-overview-error");
  const overviewContent = document.getElementById("admin-overview-content");
  const metrics = document.getElementById("admin-metrics");
  const pendingPanel = document.getElementById("admin-pending-panel");
  const actionPanel = document.getElementById("admin-action-panel");
  const statusPanel = document.getElementById("admin-status-panel");

  if (
    !session || !profile || !gate || !dashboard || !loading || !profileHost ||
    !overviewError || !overviewContent || !metrics || !pendingPanel || !actionPanel || !statusPanel
  ) {
    return;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(Number(value) || 0);
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

  function metric(label, value, href, linkText = "상세 보기 →") {
    const card = element("a", "activity-card");
    card.href = href;
    card.setAttribute("aria-label", `${label} ${formatNumber(value)} ${linkText.replace("→", "")}`);
    card.append(
      element("span", "", label),
      element("strong", "", formatNumber(value)),
      element("em", "", linkText),
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
    const node = element("a", "mypage-action");
    node.href = href;
    const copy = element("span");
    copy.append(element("strong", "", label), element("small", "", description));
    node.append(copy, element("span", "", "→"));
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

  function pendingRow(application) {
    const row = element("article", "management-preview-row");
    const copy = element("div", "management-preview-copy");
    const heading = element("div", "management-preview-heading");
    heading.append(
      element(
        "strong",
        "",
        application.applicantNickname || application.applicantLoginId || "신청자",
      ),
      element(
        "span",
        "management-status-badge management-status-badge--pending",
        "승인 대기",
      ),
    );
    copy.append(
      heading,
      element(
        "p",
        "",
        `${application.businessName || "사업자 신청"} · 대표 ${application.representativeName || "미입력"}`,
      ),
      element("small", "", `${formatDate(application.createdAt)} 신청`),
    );
    const link = element("a", "button button-sm button-secondary", "상세 관리 →");
    link.href = "/pages/admin/business-applications.html";
    row.append(copy, link);
    return row;
  }

  function renderProfile(response) {
    profileHost.replaceChildren(profile.createSummary(response.data || {}, [
      { label: "사업자 페이지", href: "/pages/business/index.html" },
      { label: "마이 페이지", href: "/pages/mypage/index.html" },
    ]));
  }

  function renderProfileError(error) {
    profileHost.replaceChildren(errorPanel(
      "계정 정보를 불러오지 못했습니다.",
      error?.message,
    ));
  }

  function renderOverview(data) {
    metrics.replaceChildren(
      metric("전체 계정", data.accountCount, "/pages/admin/accounts.html"),
      metric("승인 대기", data.pendingBusinessApplicationCount, "/pages/admin/business-applications.html", "처리하기 →"),
      metric("활성 음식점", data.activeRestaurantCount, "/pages/admin/restaurants.html"),
      metric("커뮤니티 게시글", data.communityPostCount, "/pages/admin/community.html"),
      metric("활성 Preset", data.activePresetCount, "/pages/admin/presets.html"),
    );

    const pendingApplications = Array.isArray(data.pendingBusinessApplications)
      ? data.pendingBusinessApplications
      : [];
    const pendingList = element("div", "management-preview-list");
    if (pendingApplications.length === 0) {
      pendingList.append(element("p", "management-empty", "현재 처리가 필요한 사업자 신청이 없습니다."));
    } else {
      pendingApplications.forEach((application) => pendingList.append(pendingRow(application)));
    }
    pendingPanel.replaceChildren(
      panelHeader("사업자 승인 대기", "처리가 필요한 최근 사업자 신청"),
      pendingList,
    );

    const actionList = element("div", "mypage-action-list");
    actionList.append(
      dashboardAction("계정 관리", "권한·상태·계정 관리", "/pages/admin/accounts.html"),
      dashboardAction("사업자 신청 관리", "승인·거절 처리", "/pages/admin/business-applications.html"),
      dashboardAction("음식점 관리", "음식점 정보·상태 관리", "/pages/admin/restaurants.html"),
      dashboardAction("커뮤니티 관리", "게시글·댓글 관리", "/pages/admin/community.html"),
      dashboardAction("Preset 관리", "Preset·음식점·태그 연결 관리", "/pages/admin/presets.html"),
    );
    actionPanel.replaceChildren(
      panelHeader("관리 메뉴", "세부 관리 화면으로 빠르게 이동"),
      actionList,
    );

    const statusLink = element("a", "notification-summary");
    statusLink.href = "/pages/admin/business-applications.html";
    const image = new Image();
    image.src = "/images/characters/notification.png";
    image.alt = "";
    const copy = element("div");
    copy.append(
      element("strong", "", `승인 대기 ${formatNumber(data.pendingBusinessApplicationCount)}건`),
      element("span", "", "사업자 신청 관리에서 처리 상태를 확인하세요."),
    );
    statusLink.append(image, copy);
    statusPanel.replaceChildren(statusLink);

    overviewError.replaceChildren();
    overviewContent.hidden = false;
    window.FooduckIcons?.enhance(overviewContent);
  }

  function renderOverviewError(error) {
    overviewContent.hidden = true;
    overviewError.replaceChildren(errorPanel(
      "관리 현황을 불러오지 못했습니다.",
      error?.message,
    ));
  }

  function redirectToLoginIfNeeded() {
    if (localStorage.getItem("accessToken")) return false;
    window.location.assign(
      "/pages/auth/login.html?next=" + encodeURIComponent("/pages/admin/index.html"),
    );
    return true;
  }

  async function init() {
    if (!session.isAdmin) {
      gate.hidden = false;
      return;
    }

    dashboard.hidden = false;
    const [profileResult, overviewResult] = await Promise.allSettled([
      Api.get("/mypage/overview"),
      Api.get("/admin/overview"),
    ]);
    if (redirectToLoginIfNeeded()) return;

    if (profileResult.status === "fulfilled") renderProfile(profileResult.value);
    else renderProfileError(profileResult.reason);
    if (overviewResult.status === "fulfilled") renderOverview(overviewResult.value.data || {});
    else renderOverviewError(overviewResult.reason);
    loading.hidden = true;
  }

  init();
})();
