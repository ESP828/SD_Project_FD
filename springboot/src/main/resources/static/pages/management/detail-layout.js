(() => {
  const session = window.FooduckSession;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function createProfile(profileData = {}) {
    const profile = element("div", "mypage-detail-profile");
    const avatar = element("div", "mypage-detail-avatar");
    const displayName = profileData.nickname || profileData.loginId || "회원";
    const initial = displayName.trim().charAt(0) || "회";

    if (profileData.profileImageUrl) {
      const image = new Image();
      image.src = profileData.profileImageUrl;
      image.alt = `${displayName} 프로필`;
      image.addEventListener("error", () => {
        avatar.replaceChildren(element("span", "", initial));
      }, { once: true });
      avatar.append(image);
    } else {
      avatar.append(element("span", "", initial));
    }

    const copy = element("div");
    copy.append(
      element("strong", "", displayName),
      element(
        "span",
        "",
        profileData.email || profileData.loginId || "계정 정보",
      ),
    );
    profile.append(avatar, copy);
    return profile;
  }

  function createSidebar({
    profile = {},
    homeHref,
    homeLabel,
    ariaLabel,
    items = [],
  }) {
    const sidebar = element("aside", "mypage-detail-sidebar");
    sidebar.append(createProfile(profile));

    const home = element("a", "mypage-detail-home-link", homeLabel);
    home.href = homeHref;
    sidebar.append(home);

    const nav = element("nav", "mypage-detail-nav");
    nav.setAttribute("aria-label", ariaLabel);
    items.forEach((item) => {
      const link = element("a");
      link.href = item.href;
      link.append(document.createTextNode(item.label));
      if (item.count !== undefined && item.count !== null) {
        link.append(
          element("span", "", new Intl.NumberFormat("ko-KR").format(Number(item.count) || 0)),
        );
      }
      if (item.current) link.setAttribute("aria-current", "page");
      nav.append(link);
    });
    sidebar.append(nav);
    return sidebar;
  }

  const managementConfigs = {
    business: {
      allowed: () => Boolean(session?.canManageBusiness),
      overviewPath: "/business/overview",
      homeHref: "/pages/business/index.html",
      homeLabel: "← 사업자 페이지",
      ariaLabel: "사업자 상세 메뉴",
      items: [
        ["restaurants", "내 음식점", "/pages/business/detail.html?tab=restaurants", "restaurantCount"],
        ["active", "운영 중", "/pages/business/detail.html?tab=active", "activeRestaurantCount"],
        ["news", "가게 소식", "/pages/business/detail.html?tab=news", "newsCount"],
        ["reviews", "받은 리뷰", "/pages/business/detail.html?tab=reviews", "reviewCount"],
        ["favorites", "찜 현황", "/pages/business/detail.html?tab=favorites", "favoriteCount"],
      ],
    },
    admin: {
      allowed: () => Boolean(session?.isAdmin),
      overviewPath: "/admin/overview",
      homeHref: "/pages/admin/index.html",
      homeLabel: "← 관리자 페이지",
      ariaLabel: "관리자 상세 메뉴",
      items: [
        ["accounts", "계정 관리", "/pages/admin/accounts.html", "accountCount"],
        ["applications", "사업자 신청", "/pages/admin/business-applications.html", "pendingBusinessApplicationCount"],
        ["restaurants", "음식점 관리", "/pages/admin/restaurants.html", "activeRestaurantCount"],
        ["community", "커뮤니티 관리", "/pages/admin/community.html", "communityPostCount"],
        ["presets", "Preset 관리", "/pages/admin/presets.html", "activePresetCount"],
      ],
    },
  };

  function renderManagementSidebar(host, config, activeItem, profileData, overview) {
    const items = config.items.map(([id, label, href, countKey]) => ({
      label,
      href,
      count: overview ? overview[countKey] : undefined,
      current: id === activeItem,
    }));
    const sidebar = createSidebar({
      profile: profileData,
      homeHref: config.homeHref,
      homeLabel: config.homeLabel,
      ariaLabel: config.ariaLabel,
      items,
    });
    host.className = sidebar.className;
    host.replaceChildren(...sidebar.childNodes);
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
    createSidebar,
    mountManagementSidebar,
  };

  document.querySelectorAll("[data-management-sidebar]").forEach((host) => {
    mountManagementSidebar(host);
  });
})();
