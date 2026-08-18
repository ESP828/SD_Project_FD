(() => {
  const session = window.FooduckSession;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function createMenuBar({ ariaLabel, items = [] }) {
    const nav = element("nav", "mypage-detail-nav");
    nav.setAttribute("aria-label", ariaLabel);
    nav.style.setProperty(
      "--mypage-detail-nav-columns",
      String(Math.max(items.length, 1)),
    );

    items.forEach((item) => {
      const link = element("a");
      link.href = item.href;
      const label = element("span", "mypage-detail-tab-label");
      if (item.icon) {
        const icon = element("span", "material-symbols-rounded", item.icon);
        icon.setAttribute("aria-hidden", "true");
        window.FooduckIcons?.set(icon, item.icon);
        label.append(icon);
      }
      label.append(document.createTextNode(item.label));
      link.append(label);
      if (item.count !== undefined && item.count !== null) {
        link.append(
          element(
            "span",
            "mypage-detail-tab-count",
            new Intl.NumberFormat("ko-KR").format(Number(item.count) || 0),
          ),
        );
      }
      if (item.current) link.setAttribute("aria-current", "page");
      nav.append(link);
    });
    window.FooduckIcons?.enhance(nav);
    return nav;
  }

  function createSidebar({ ariaLabel, items = [] }) {
    return createMenuBar({ ariaLabel, items });
  }

  const managementConfigs = {
    business: {
      allowed: () => Boolean(session?.canManageBusiness),
      overviewPath: "/business/overview",
      homeHref: "/pages/business/index.html",
      homeLabel: "← 사업자 페이지",
      ariaLabel: "사업자 상세 메뉴",
      items: [
        ["restaurants", "내 음식점", "storefront", "/pages/business/detail.html?tab=restaurants", "restaurantCount"],
        ["active", "운영 중", "store", "/pages/business/detail.html?tab=active", "activeRestaurantCount"],
        ["news", "가게 소식", "campaign", "/pages/business/detail.html?tab=news", "newsCount"],
        ["reviews", "받은 리뷰", "rate_review", "/pages/business/detail.html?tab=reviews", "reviewCount"],
        ["favorites", "찜 현황", "favorite", "/pages/business/detail.html?tab=favorites", "favoriteCount"],
      ],
    },
    admin: {
      allowed: () => Boolean(session?.isAdmin),
      overviewPath: "/admin/overview",
      homeHref: "/pages/admin/index.html",
      homeLabel: "← 관리자 페이지",
      ariaLabel: "관리자 상세 메뉴",
      items: [
        ["accounts", "계정 관리", "person", "/pages/admin/accounts.html", "accountCount"],
        ["applications", "사업자 신청", "verified_user", "/pages/admin/business-applications.html", "pendingBusinessApplicationCount"],
        ["restaurants", "음식점 관리", "storefront", "/pages/admin/restaurants.html", "activeRestaurantCount"],
        ["community", "커뮤니티 관리", "forum", "/pages/admin/community.html", "communityPostCount"],
        ["presets", "보물지도 관리", "map", "/pages/admin/presets.html", "activePresetCount"],
      ],
    },
  };

  function placeManagementMenu(host) {
    const layout = host.closest(".mypage-detail-layout");
    const main = layout?.querySelector(":scope > .mypage-detail-main");
    const heading = main?.querySelector(":scope > .mypage-detail-heading");
    if (!main || !heading) return;

    const headingCopy = heading.firstElementChild;
    if (headingCopy) headingCopy.classList.add("mypage-detail-heading-copy");
    main.classList.add("mypage-detail-surface", "management-detail-surface");
    heading.after(host);
  }

  function renderManagementSidebar(host, config, activeItem, profileData, overview) {
    const items = config.items.map(([id, label, icon, href, countKey]) => ({
      label,
      icon,
      href,
      count: overview ? overview[countKey] : undefined,
      current: id === activeItem,
    }));
    const menu = createMenuBar({
      ariaLabel: config.ariaLabel,
      items,
    });
    host.className = menu.className;
    host.setAttribute("role", "navigation");
    host.setAttribute("aria-label", config.ariaLabel);
    host.style.cssText = menu.style.cssText;
    host.replaceChildren(...menu.childNodes);
    placeManagementMenu(host);
  }

  async function mountManagementSidebar(host) {
    const kind = host.dataset.managementSection;
    const activeItem = host.dataset.activeItem;
    const config = managementConfigs[kind];
    if (!config || !session?.authenticated || !config.allowed()) return;

    renderManagementSidebar(host, config, activeItem, { loginId: session.loginId }, null);

    const [profileResult, overviewResult] = await Promise.allSettled([
      Api.get("/mypage/overview"),
      Api.get(config.overviewPath),
    ]);
    const profileData = profileResult.status === "fulfilled"
      ? profileResult.value.data || {}
      : { loginId: session.loginId };
    const overview = overviewResult.status === "fulfilled"
      ? overviewResult.value.data || {}
      : null;
    renderManagementSidebar(host, config, activeItem, profileData, overview);
  }

  window.FooduckDetailLayout = {
    createMenuBar,
    createSidebar,
    mountManagementSidebar,
  };

  document.querySelectorAll("[data-management-sidebar]").forEach((host) => {
    mountManagementSidebar(host);
  });
})();
