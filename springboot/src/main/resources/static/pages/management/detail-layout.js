(() => {
  const session = window.FooduckSession;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function createMenuBar(items = [], ariaLabel = "상세 메뉴") {
    const nav = element("nav", "mypage-detail-nav");
    nav.setAttribute("aria-label", ariaLabel);
    nav.style.setProperty("--mypage-detail-nav-columns", Math.max(items.length, 1));
    const select = element("select", "mypage-detail-nav-select");
    select.setAttribute("aria-label", ariaLabel);
    items.forEach((item) => {
      const link = element("a");
      link.href = item.href;
      const label = element("span", "mypage-detail-tab-label");
      const icon = element("span", "material-symbols-rounded", item.icon);
      icon.setAttribute("aria-hidden", "true");
      window.FooduckIcons?.set(icon, item.icon);
      label.append(icon, document.createTextNode(item.label));
      link.append(label);
      if (item.count !== undefined && item.count !== null) {
        link.append(
          element(
            "span",
            "mypage-detail-tab-count",
            `${new Intl.NumberFormat("ko-KR").format(Number(item.count) || 0)}개`,
          ),
        );
      }
      if (item.current) link.setAttribute("aria-current", "page");
      nav.append(link);

      const option = element("option", "", item.label);
      option.value = item.href;
      option.selected = Boolean(item.current);
      if (item.count !== undefined && item.count !== null) {
        option.textContent += ` (${new Intl.NumberFormat("ko-KR").format(Number(item.count) || 0)}개)`;
      }
      select.append(option);
    });
    select.addEventListener("change", () => {
      if (select.value) window.location.assign(select.value);
    });
    nav.append(select);
    return nav;
  }

  const managementConfigs = {
    business: {
      allowed: () => Boolean(session?.canManageBusiness),
      overviewPath: "/business/overview",
      ariaLabel: "사업자 상세 메뉴",
      items: [
        ["restaurants", "내 음식점", "/business/detail?tab=restaurants", "restaurantCount", "storefront"],
        ["active", "운영 중", "/business/detail?tab=active", "activeRestaurantCount", "store"],
        ["news", "가게 소식", "/business/detail?tab=news", "newsCount", "campaign"],
        ["reviews", "받은 리뷰", "/business/detail?tab=reviews", "reviewCount", "rate_review"],
        ["favorites", "찜 현황", "/business/detail?tab=favorites", "favoriteCount", "favorite"],
      ],
    },
    admin: {
      allowed: () => Boolean(session?.isAdmin),
      overviewPath: "/admin/overview",
      ariaLabel: "관리자 상세 메뉴",
      items: [
        ["accounts", "계정 관리", "/admin/accounts", "accountCount", "person"],
        ["applications", "사업자 신청", "/admin/business-applications", "pendingBusinessApplicationCount", "verified_user"],
        ["restaurants", "음식점 관리", "/admin/restaurants", "activeRestaurantCount", "storefront"],
        ["community", "커뮤니티 관리", "/admin/community", "communityPostCount", "forum"],
        ["presets", "보물지도 관리", "/admin/presets", "activePresetCount", "map"],
      ],
    },
  };

  function renderManagementMenu(host, config, activeItem, overview) {
    const items = config.items.map(([id, label, href, countKey, icon]) => ({
      label,
      icon,
      href,
      count: overview ? overview[countKey] : undefined,
      current: id === activeItem,
    }));
    const menuBar = createMenuBar(items, config.ariaLabel);
    host.className = menuBar.className;
    host.setAttribute("aria-label", config.ariaLabel);
    host.style.setProperty("--mypage-detail-nav-columns", Math.max(items.length, 1));
    host.replaceChildren(...menuBar.childNodes);
    window.FooduckIcons?.enhance(host);
  }

  async function mountManagementMenu(host) {
    const kind = host.dataset.managementSection;
    const activeItem = host.dataset.activeItem;
    const config = managementConfigs[kind];
    if (!config || !session?.authenticated || !config.allowed()) return;

    renderManagementMenu(host, config, activeItem, null);

    try {
      const overviewResult = await Api.get(config.overviewPath);
      renderManagementMenu(host, config, activeItem, overviewResult.data || {});
    } catch (error) {
      console.warn(`${config.ariaLabel} 수치를 불러오지 못했습니다.`, error);
    }
  }

  window.FooduckDetailLayout = {
    createMenuBar,
    mountManagementMenu,
  };

  document.querySelectorAll("[data-management-menu]").forEach((host) => {
    mountManagementMenu(host);
  });
})();
