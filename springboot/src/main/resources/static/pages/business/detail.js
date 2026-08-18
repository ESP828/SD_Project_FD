(() => {
  const session = window.FooduckSession;
  const detailLayout = window.FooduckDetailLayout;
  const gate = document.getElementById("business-detail-gate");
  const gateTitle = document.getElementById("business-detail-gate-title");
  const gateCopy = document.getElementById("business-detail-gate-copy");
  const gateLink = document.getElementById("business-detail-gate-link");
  const content = document.getElementById("business-detail-content");

  if (
    !session || !detailLayout || !gate || !gateTitle || !gateCopy ||
    !gateLink || !content
  ) {
    return;
  }

  const tabs = {
    restaurants: {
      label: "내 음식점",
      icon: "storefront",
      title: "내 음식점",
      description: "현재 계정에 연결된 음식점과 운영 상태입니다.",
      countKey: "restaurantCount",
      emptyIcon: "storefront",
      emptyTitle: "연결된 음식점이 없습니다.",
      emptyCopy: "음식점이 연결되면 이곳에서 운영 현황을 확인할 수 있습니다.",
      filter: () => true,
    },
    active: {
      label: "운영 중",
      icon: "store",
      title: "운영 중인 음식점",
      description: "현재 ACTIVE 상태로 노출 중인 음식점입니다.",
      countKey: "activeRestaurantCount",
      emptyIcon: "store",
      emptyTitle: "운영 중인 음식점이 없습니다.",
      emptyCopy: "음식점 상태가 ACTIVE가 되면 이곳에 표시됩니다.",
      filter: (restaurant) => restaurant.status === "ACTIVE",
    },
    news: {
      label: "가게 소식",
      icon: "campaign",
      title: "가게 소식 현황",
      description: "음식점별로 등록된 활성 소식 수를 확인합니다.",
      countKey: "newsCount",
      itemCountKey: "newsCount",
      itemCountLabel: "소식",
      emptyIcon: "campaign",
      emptyTitle: "등록된 가게 소식이 없습니다.",
      emptyCopy: "음식점 상세에서 첫 소식을 작성해 보세요.",
      filter: (restaurant) => Number(restaurant.newsCount) > 0,
    },
    reviews: {
      label: "받은 리뷰",
      icon: "rate_review",
      title: "받은 리뷰 현황",
      description: "내 음식점별 활성 리뷰 수를 확인합니다.",
      countKey: "reviewCount",
      itemCountKey: "reviewCount",
      itemCountLabel: "리뷰",
      emptyIcon: "rate_review",
      emptyTitle: "받은 리뷰가 없습니다.",
      emptyCopy: "리뷰가 등록되면 음식점별 현황을 확인할 수 있습니다.",
      filter: (restaurant) => Number(restaurant.reviewCount) > 0,
    },
    favorites: {
      label: "찜 현황",
      icon: "favorite",
      title: "찜 받은 현황",
      description: "내 음식점별로 사용자에게 저장된 횟수를 확인합니다.",
      countKey: "favoriteCount",
      itemCountKey: "favoriteCount",
      itemCountLabel: "찜",
      emptyIcon: "favorite",
      emptyTitle: "찜 받은 음식점이 없습니다.",
      emptyCopy: "사용자가 음식점을 저장하면 이곳에 표시됩니다.",
      filter: (restaurant) => Number(restaurant.favoriteCount) > 0,
    },
  };

  const requestedTab = new URLSearchParams(window.location.search).get("tab");
  const activeTab = Object.prototype.hasOwnProperty.call(tabs, requestedTab)
    ? requestedTab
    : "restaurants";
  const activeConfig = tabs[activeTab];

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function detailPath(tab) {
    return `/pages/business/detail.html?tab=${encodeURIComponent(tab)}`;
  }

  function restaurantPath(restaurantId) {
    return `/pages/restaurant/detail.html?id=${encodeURIComponent(restaurantId)}`;
  }

  function formatDate(value) {
    if (!value) return "날짜 정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "short",
      day: "numeric",
    }).format(date);
  }

  function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(Number(value) || 0);
  }

  function countFor(overview, tab) {
    return Number(overview[tabs[tab].countKey] || 0);
  }

  function statusLabel(status) {
    return {
      ACTIVE: "운영 중",
      INACTIVE: "운영 중지",
      DELETED: "삭제",
    }[status] || status || "상태 미확인";
  }

  function restaurantCard(restaurant) {
    const card = element("article", "mypage-detail-card");
    const href = restaurantPath(restaurant.restaurantId);
    const top = element("div", "mypage-detail-card-top");
    const heading = element("h3");
    const link = element("a", "", restaurant.name || "이름 없는 음식점");
    link.href = href;
    heading.append(link);
    const badgeText = activeConfig.itemCountKey
      ? `${activeConfig.itemCountLabel} ${formatNumber(restaurant[activeConfig.itemCountKey])}개`
      : statusLabel(restaurant.status);
    top.append(heading, element("span", "mypage-detail-badge", badgeText));

    const meta = element("div", "mypage-detail-meta");
    meta.append(
      element("span", "", restaurant.categoryName || "카테고리 미지정"),
      element("span", "", statusLabel(restaurant.status)),
      element("span", "", `소식 ${formatNumber(restaurant.newsCount)}`),
      element("span", "", `리뷰 ${formatNumber(restaurant.reviewCount)}`),
      element("span", "", `찜 ${formatNumber(restaurant.favoriteCount)}`),
    );

    const footer = element("div", "mypage-detail-card-footer");
    footer.append(
      element("time", "", `${formatDate(restaurant.createdAt)} 등록`),
      element("a", "", "가게 보기 →"),
    );
    footer.lastElementChild.href = href;
    card.append(
      top,
      element("p", "", restaurant.address || "주소 정보 없음"),
      meta,
      footer,
    );
    return card;
  }

  function renderItems(restaurants) {
    const filtered = restaurants
      .filter(activeConfig.filter)
      .sort((left, right) => activeConfig.itemCountKey
        ? Number(right[activeConfig.itemCountKey] || 0) - Number(left[activeConfig.itemCountKey] || 0)
        : 0);
    if (!filtered.length) {
      const empty = element("div", "mypage-detail-empty");
      const icon = element("span", "material-symbols-rounded", activeConfig.emptyIcon);
      icon.setAttribute("aria-hidden", "true");
      empty.append(
        icon,
        element("h3", "", activeConfig.emptyTitle),
        element("p", "", activeConfig.emptyCopy),
      );
      return empty;
    }

    const list = element("div", "mypage-detail-list");
    filtered.forEach((restaurant) => list.append(restaurantCard(restaurant)));
    return list;
  }

  function render(profile, overview) {
    content.replaceChildren();
    document.title = `${activeConfig.label} · 사업자 페이지 · 푸드덕`;
    const restaurants = Array.isArray(overview.restaurants)
      ? overview.restaurants.slice()
      : [];
    const items = Object.entries(tabs).map(([tab, config]) => ({
      label: config.label,
      icon: config.icon,
      href: detailPath(tab),
      count: countFor(overview, tab),
      current: tab === activeTab,
    }));
    const layout = element("div", "mypage-detail-layout");
    const main = element("section", "mypage-detail-main");
    const surface = element("section", "mypage-detail-surface");
    const heading = element("header", "mypage-detail-heading");
    const copy = element("div", "mypage-detail-heading-copy");
    copy.append(
      element("h2", "", activeConfig.title),
      element("p", "", activeConfig.description),
    );
    heading.append(
      copy,
      element("span", "mypage-detail-count", `${formatNumber(countFor(overview, activeTab))}개`),
    );
    const menuBar = detailLayout.createMenuBar({
      ariaLabel: "사업자 상세 메뉴",
      items,
    });
    const body = element("div", "mypage-detail-body");
    body.append(renderItems(restaurants));
    surface.append(heading, menuBar, body);
    main.append(surface);
    layout.append(main);
    content.append(layout);
    window.FooduckIcons?.enhance(content);
  }

  function renderError(error) {
    content.replaceChildren();
    const wrapper = element("div", "mypage-detail-error");
    const icon = element("span", "material-symbols-rounded", "error");
    icon.setAttribute("aria-hidden", "true");
    wrapper.append(
      icon,
      element("h2", "", "사업자 운영 정보를 불러오지 못했습니다."),
      element("p", "", error.message || "잠시 후 다시 시도해 주세요."),
    );
    content.append(wrapper);
    window.FooduckIcons?.enhance(content);
  }

  if (!session.authenticated) {
    content.hidden = true;
    gate.hidden = false;
    gateLink.href = "/pages/auth/login.html?next=" +
      encodeURIComponent(`${window.location.pathname}${window.location.search}`);
    return;
  }

  if (!session.canManageBusiness) {
    content.hidden = true;
    gate.hidden = false;
    gateTitle.textContent = "사업자 권한이 필요합니다";
    gateCopy.textContent = "사업자 페이지에서 권한 신청과 처리 상태를 확인해 주세요.";
    gateLink.textContent = "사업자 페이지";
    gateLink.href = "/pages/business/index.html";
    return;
  }

  Promise.all([
    Api.get("/mypage/overview"),
    Api.get("/business/overview"),
  ])
    .then(([profilePayload, overviewPayload]) => {
      render(profilePayload.data || {}, overviewPayload.data || {});
    })
    .catch((error) => {
      if (!localStorage.getItem("accessToken")) {
        window.location.assign(
          "/pages/auth/login.html?next=" +
          encodeURIComponent(`${window.location.pathname}${window.location.search}`),
        );
        return;
      }
      renderError(error);
    });
})();
