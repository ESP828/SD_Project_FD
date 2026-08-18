(() => {
  const SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  const RECOMMENDATION_PATH = "/pages/recommendation/index.html";
  const RECOMMENDATION_LOGIN_PATH =
    "/pages/auth/login.html?next=" +
    encodeURIComponent(RECOMMENDATION_PATH);
  const AUTHORITY_LABELS = Object.freeze({
    ROLE_USER: "일반 사용자",
    ROLE_BUSINESS: "사업자",
    ROLE_ADMIN: "관리자",
  });
  const AUTHORITY_PRIORITY = Object.freeze([
    "ROLE_ADMIN",
    "ROLE_BUSINESS",
    "ROLE_USER",
  ]);

  const ICON_PATHS = {
    notifications: [
      "M18 8a6 6 0 0 0-12 0c0 6.8-3 7-3 9h18c0-2-3-2.2-3-9",
      "M10 21h4",
    ],
    notifications_off: [
      "M13.7 21h-3.4",
      "M6.3 6.3A6 6 0 0 0 6 8c0 6.8-3 7-3 9h14",
      "M18 13V8a6 6 0 0 0-8.3-5.5",
      "M3 3l18 18",
    ],
    menu: ["M4 7h16", "M4 12h16", "M4 17h16"],
    close: ["M6 6l12 12", "M18 6L6 18"],
    map: [
      "M9 18l-6 3V6l6-3 6 3 6-3v15l-6 3-6-3z",
      "M9 3v15",
      "M15 6v15",
    ],
    arrow_forward: ["M5 12h14", "M13 6l6 6-6 6"],
    arrow_back: ["M19 12H5", "M11 18l-6-6 6-6"],
    chevron_left: ["M15 18l-6-6 6-6"],
    chevron_right: ["M9 18l6-6-6-6"],
    favorite: [
      "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6a5.5 5.5 0 0 0 1-8.8z",
    ],
    check_circle: [
      "M22 11.1V12a10 10 0 1 1-5.9-9.1",
      "M22 4L12 14.1l-3-3",
    ],
    search: ["M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z", "M21 21l-4.3-4.3"],
    visibility: [
      "M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z",
      "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    ],
    tune: [
      "M4 7h10",
      "M18 7h2",
      "M14 4v6",
      "M4 17h2",
      "M10 17h10",
      "M10 14v6",
    ],
    near_me: ["M21 3L10 14", "M21 3l-7 18-4-7-7-4 18-7z"],
    auto_awesome: [
      "M12 3l1.1 3.4L16.5 7.5l-3.4 1.1L12 12l-1.1-3.4-3.4-1.1 3.4-1.1L12 3z",
      "M19 14l.7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7L19 14z",
      "M5 13l.7 1.8 1.8.7-1.8.7L5 18l-.7-1.8-1.8-.7 1.8-.7L5 13z",
    ],
    forum: [
      "M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8z",
      "M7 8h10",
      "M7 12h7",
    ],
    lock: [
      "M5 10h14v11H5z",
      "M8 10V7a4 4 0 0 1 8 0v3",
      "M12 14v3",
    ],
    login: ["M14 8l4 4-4 4", "M18 12H7", "M10 4H4v16h6"],
    logout: ["M10 17l5-5-5-5", "M15 12H3", "M14 4h6v16h-6"],
    person_add: [
      "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
      "M2 21a7 7 0 0 1 14 0",
      "M19 8v6",
      "M16 11h6",
    ],
    verified_user: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M9 12l2 2 4-5",
    ],
    home: ["M3 11l9-8 9 8", "M5 10v11h14V10", "M9 21v-7h6v7"],
    construction: [
      "M14.5 6.5l3-3 3 3-3 3",
      "M13 8l-9 9v3h3l9-9",
      "M5 4l15 15",
    ],
    shield_person: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M12 11a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
      "M8.5 16a3.5 3.5 0 0 1 7 0",
    ],
    database: [
      "M20 5c0 1.7-3.6 3-8 3S4 6.7 4 5s3.6-3 8-3 8 1.3 8 3z",
      "M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5",
      "M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7",
    ],
    edit: [
      "M12 20h9",
      "M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4L16.5 3.5z",
    ],
    delete: [
      "M4 7h16",
      "M9 7V4h6v3",
      "M7 7l1 14h8l1-14",
      "M10 11v6",
      "M14 11v6",
    ],
    thumb_up: [
      "M7 10v11H3V10h4z",
      "M7 19h10a2 2 0 0 0 2-1.6l2-8A2 2 0 0 0 19 7h-5l1-4-2-1-6 8",
    ],
    schedule: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 6v6l4 2"],
    location_on: [
      "M21 10c0 7-9 12-9 12S3 17 3 10a9 9 0 1 1 18 0z",
      "M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    ],
    open_in_new: ["M14 3h7v7", "M21 3l-9 9", "M18 13v7H4V6h7"],
    call: [
      "M5 3h4l2 5-2.5 1.5a16 16 0 0 0 6 6L16 13l5 2v4c0 1.1-.9 2-2 2C10.2 21 3 13.8 3 5a2 2 0 0 1 2-2z",
    ],
    progress_activity: [
      "M12 2a10 10 0 0 1 10 10",
      "M22 12a10 10 0 0 1-10 10",
      "M12 22A10 10 0 0 1 2 12",
    ],
    my_location: [
      "M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16z",
      "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
      "M12 2v2",
      "M12 20v2",
      "M2 12h2",
      "M20 12h2",
    ],
    touch_app: [
      "M9 11V5a2 2 0 0 1 4 0v6",
      "M13 10V8a2 2 0 0 1 4 0v4",
      "M17 11a2 2 0 0 1 4 0v4c0 4-3 7-7 7h-2c-3 0-5-2-7-5l-2-3a2 2 0 0 1 3-2l3 2",
    ],
    person: [
      "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
      "M4 22a8 8 0 0 1 16 0",
    ],
    storefront: ["M3 10h18l-2-6H5l-2 6z", "M5 10v10h14V10", "M9 20v-6h6v6"],
    admin_panel_settings: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
      "M8.5 17a3.5 3.5 0 0 1 7 0",
    ],
    rate_review: [
      "M4 4h16v13H9l-5 4V4z",
      "M12 8l1.2 2.4L16 11l-2.8.6L12 14l-1.2-2.4L8 11l2.8-.6L12 8z",
    ],
    article: [
      "M6 3h9l5 5v13H6z",
      "M15 3v5h5",
      "M9 12h6",
      "M9 16h6",
    ],
    chat_bubble: [
      "M6 4h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H9l-5 4V7a3 3 0 0 1 3-3z",
    ],
    add: ["M12 5v14", "M5 12h14"],
    error: [
      "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z",
      "M12 7v6",
      "M12 17h.01",
    ],
    movie: [
      "M3 5h18v14H3z",
      "M3 9h18",
      "M7 5l2 4",
      "M13 5l2 4",
      "M17 5l2 4",
    ],
    store: [
      "M4 10h16v10H4z",
      "M3 10l2-6h14l2 6",
      "M8 20v-6h5v6",
      "M4 10c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2",
    ],
    campaign: [
      "M4 10v4",
      "M7 9h4l7-4v14l-7-4H7z",
      "M7 15l1.5 5h3L10 15",
      "M20 9v6",
    ],
  };

  function setIcon(element, iconName) {
    if (!element) {
      return;
    }
    const paths = ICON_PATHS[iconName];
    if (!paths) {
      element.dataset.iconError = iconName || "unknown";
      console.warn(`[FooduckIcons] 등록되지 않은 아이콘: ${iconName}`);
      return;
    }
    delete element.dataset.iconError;
    const svg = document.createElementNS(SVG_NAMESPACE, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.setAttribute("focusable", "false");
    paths.forEach((pathData) => {
      const path = document.createElementNS(SVG_NAMESPACE, "path");
      path.setAttribute("d", pathData);
      svg.append(path);
    });
    element.replaceChildren(svg);
    element.dataset.iconName = iconName;
  }

  function enhanceIcons(root = document) {
    const icons = [];
    if (root instanceof Element && root.matches(".material-symbols-rounded")) {
      icons.push(root);
    }
    if (root.querySelectorAll) {
      icons.push(...root.querySelectorAll(".material-symbols-rounded"));
    }
    icons.forEach((element) => {
      if (!element.dataset.iconName) {
        setIcon(element, element.textContent.trim());
      }
    });
  }

  function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (text !== undefined && text !== null) {
      element.textContent = text;
    }
    return element;
  }

  function primaryAuthorityCode(authorities) {
    const authorityCodes = Array.isArray(authorities) ? authorities : [];
    return AUTHORITY_PRIORITY.find((code) => authorityCodes.includes(code)) || "ROLE_USER";
  }

  function authorityLabel(code) {
    return AUTHORITY_LABELS[code] || code || AUTHORITY_LABELS.ROLE_USER;
  }

  function formatProfileDate(value) {
    if (!value) {
      return "정보 없음";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(date);
  }

  function createProfileSummary(data = {}, actions = []) {
    const summary = createElement("section", "profile-summary");
    const profileImage = createElement("div", "profile-image");
    const showFallbackImage = () => {
      const fallback = createElement("span", "material-symbols-rounded", "person");
      fallback.setAttribute("aria-hidden", "true");
      profileImage.replaceChildren(fallback);
      enhanceIcons(profileImage);
    };

    if (data.profileImageUrl) {
      const image = new Image();
      image.src = data.profileImageUrl;
      image.alt = `${data.nickname || "회원"} 프로필`;
      image.addEventListener("error", showFallbackImage, { once: true });
      profileImage.append(image);
    } else {
      showFallbackImage();
    }

    const profileCopy = createElement("div", "profile-copy");
    profileCopy.append(
      createElement("h2", "", `${data.nickname || "회원"}님, 반가워요`),
      createElement(
        "p",
        "",
        `${data.loginId || "소셜 계정"} · 가입 ${formatProfileDate(data.createdAt)}`,
      ),
    );
    const authorityList = createElement("div", "authority-list");
    const primaryAuthority = primaryAuthorityCode(data.authorities);
    authorityList.append(
      createElement("span", "authority-badge", authorityLabel(primaryAuthority)),
    );
    profileCopy.append(authorityList);
    summary.append(profileImage, profileCopy);

    const validActions = Array.isArray(actions)
      ? actions.filter((action) => action?.label && action?.href)
      : [];
    if (validActions.length > 0) {
      const actionList = createElement("div", "profile-summary-actions");
      validActions.forEach((action) => {
        const link = createElement("a", "button button-secondary", action.label);
        link.href = action.href;
        actionList.append(link);
      });
      summary.append(actionList);
    }

    enhanceIcons(summary);
    return summary;
  }

  function decodeToken(token) {
    try {
      const segment = token.split(".")[1];
      if (!segment) {
        return null;
      }
      const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
      const padded = normalized.padEnd(
        normalized.length + ((4 - (normalized.length % 4)) % 4),
        "=",
      );
      return JSON.parse(decodeURIComponent(
        Array.from(atob(padded))
          .map((character) =>
            `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`,
          )
          .join(""),
      ));
    } catch (_error) {
      return null;
    }
  }

  function readSession() {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      return {
        authenticated: false,
        accountId: null,
        loginId: null,
        authorities: [],
      };
    }
    const payload = decodeToken(token);
    if (!payload || (payload.exp && payload.exp * 1000 <= Date.now())) {
      localStorage.removeItem("accessToken");
      return {
        authenticated: false,
        accountId: null,
        loginId: null,
        authorities: [],
      };
    }
    return {
      authenticated: true,
      accountId: Number(payload.sub) || null,
      loginId: payload.loginId || null,
      authorities: Array.isArray(payload.authorities)
        ? payload.authorities.filter((value) => typeof value === "string")
        : [],
    };
  }

  const session = readSession();
  const hasAuthority = (authority) => session.authorities.includes(authority);
  const canManageBusiness =
    hasAuthority("ROLE_BUSINESS") || hasAuthority("ROLE_ADMIN");
  const isAdmin = hasAuthority("ROLE_ADMIN");

  function recommendationHref() {
    return session.authenticated
      ? RECOMMENDATION_PATH
      : RECOMMENDATION_LOGIN_PATH;
  }

  function renderHeader(host) {
    const active = host.dataset.activeNav || "";
    const items = [
      { id: "home", label: "홈", href: "/" },
      { id: "search", label: "검색", href: "/pages/search/index.html" },
      { id: "map", label: "맛집찾기", href: "/pages/map/index.html" },
      {
        id: "recommendation",
        label: "맛집추천",
        href: recommendationHref(),
        protectedRecommendation: true,
      },
      { id: "board", label: "커뮤니티", href: "/pages/board/index.html" },
      { id: "presset", label: "보물지도", href: "/pages/presset/index.html" },
    ];

    const nav = items.map((item) => {
      const current = item.id === active ? ' aria-current="page"' : "";
      const guard = item.protectedRecommendation
        ? ' data-recommendation-link'
        : "";
      return `<a href="${item.href}"${current}${guard}>${item.label}</a>`;
    }).join("");

    const authAction = session.authenticated
      ? `<button class="button button-sm button-outline-gray header-auth-button" type="button" data-logout>
           <span class="material-symbols-rounded" aria-hidden="true">logout</span>
           로그아웃
         </button>`
      : `<a class="button button-sm button-secondary header-auth-button header-signup-button"
              href="/pages/auth/signup.html">
           회원가입
         </a>
         <a class="button button-sm button-orange header-auth-button" href="/pages/auth/login.html">
           로그인
         </a>`;

    host.innerHTML = `
      <header class="site-header">
        <div class="header-shell">
          <a class="brand" href="/" aria-label="푸드덕 홈">
            <img src="/images/logos/brand-horizontal.png" alt="foodduck">
          </a>
          <nav id="site-nav" class="nav" aria-label="주요 메뉴">${nav}</nav>
          <div class="header-actions">
            <a class="icon-button" href="/pages/mypage/index.html" aria-label="마이페이지">
              <span class="material-symbols-rounded" aria-hidden="true">person</span>
            </a>
            <a class="icon-button" href="/pages/mypage/detail.html?tab=notifications"
               aria-label="알림" data-notification-link>
              <span class="material-symbols-rounded" aria-hidden="true">notifications</span>
              <span class="notification-badge" data-notification-badge hidden></span>
            </a>
            ${authAction}
            <button class="nav-toggle" type="button" data-nav-toggle
                    aria-controls="site-nav" aria-expanded="false" aria-label="메뉴 열기">
              <span class="material-symbols-rounded" aria-hidden="true">menu</span>
            </button>
          </div>
        </div>
      </header>`;
  }

  function renderFooter(host) {
    host.innerHTML = `
      <footer class="site-footer">
        <div class="container footer-shell">
          <div class="footer-brand">
            <img src="/images/logos/brand-horizontal.png" alt="foodduck">
            <p>맛집 탐색·추천·커뮤니티를 연결하는 Soldesk Project2 팀 프로젝트입니다.</p>
          </div>
        </div>
        <div class="container footer-bottom">
          <span>© Fooduck Project2</span>
          <span>Spring Boot · Vanilla HTML/CSS/JavaScript · Kakao Map</span>
        </div>
      </footer>`;
  }

  function currentQuickTarget() {
    const path = window.location.pathname;
    if (path === "/" || path.endsWith("/index.html") && !path.includes("/pages/")) {
      return "home";
    }
    if (path.includes("/pages/mypage/")) {
      return "mypage";
    }
    if (path.includes("/pages/search/")) {
      return "search";
    }
    return "";
  }

  function renderQuickRemote() {
    const existing = document.querySelector("[data-quick-remote]");
    if (existing) {
      existing.remove();
    }

    const items = [
      { id: "home", label: "홈", icon: "home", href: "/" },
      ...(session.authenticated
        ? [{
            id: "mypage",
            label: "마이페이지",
            icon: "person",
            href: "/pages/mypage/index.html",
          }]
        : []),
      {
        id: "search",
        label: "검색",
        icon: "search",
        href: "/pages/search/index.html",
      },
    ];
    const active = currentQuickTarget();
    const links = items.map((item) => {
      const current = item.id === active ? ' aria-current="page"' : "";
      return `
        <a href="${item.href}"${current} aria-label="${item.label} 페이지로 이동">
          <span class="material-symbols-rounded" aria-hidden="true">${item.icon}</span>
          <small>${item.label}</small>
        </a>`;
    }).join("");

    const remote = document.createElement("nav");
    remote.className = "quick-remote";
    remote.dataset.quickRemote = "";
    remote.setAttribute("aria-label", "페이지 빠른 이동");
    remote.innerHTML = `
      <span class="quick-remote-title">빠른 이동</span>
      ${links}`;
    document.body.append(remote);
  }

  document.querySelectorAll("[data-site-header]").forEach(renderHeader);
  document.querySelectorAll("[data-site-footer]").forEach(renderFooter);
  renderQuickRemote();

  async function refreshNotificationBadges() {
    if (!session.authenticated) return;
    try {
      const payload = await Api.get("/notifications/unread-count");
      const count = Math.max(0, Number(payload?.data?.count) || 0);
      document.querySelectorAll("[data-notification-badge]").forEach((badge) => {
        badge.textContent = count > 99 ? "99+" : String(count);
        badge.hidden = count === 0;
      });
      document.querySelectorAll("[data-notification-link]").forEach((link) => {
        link.setAttribute("aria-label", count > 0 ? `읽지 않은 알림 ${count}개` : "알림");
      });
    } catch {
      document.querySelectorAll("[data-notification-badge]").forEach((badge) => {
        badge.hidden = true;
      });
    }
  }

  refreshNotificationBadges();

  document.querySelectorAll("[data-recommendation-link]").forEach((link) => {
    link.href = recommendationHref();
    if (!session.authenticated) {
      link.title = "맞춤 추천은 로그인 후 이용할 수 있습니다.";
    }
  });

  document.querySelectorAll("[data-logout]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        await Api.logout();
      } catch (error) {
        localStorage.removeItem("accessToken");
      }
      window.location.assign("/");
    });
  });

  document.querySelectorAll(".site-header").forEach((header) => {
    const navToggle = header.querySelector("[data-nav-toggle]");
    if (!navToggle) {
      return;
    }
    navToggle.addEventListener("click", () => {
      const isOpen = header.classList.toggle("is-nav-open");
      navToggle.setAttribute("aria-expanded", String(isOpen));
      navToggle.setAttribute("aria-label", isOpen ? "메뉴 닫기" : "메뉴 열기");
      setIcon(
        navToggle.querySelector(".material-symbols-rounded"),
        isOpen ? "close" : "menu",
      );
    });
    header.querySelectorAll(".nav a").forEach((link) => {
      link.addEventListener("click", () => {
        header.classList.remove("is-nav-open");
        navToggle.setAttribute("aria-expanded", "false");
        setIcon(navToggle.querySelector(".material-symbols-rounded"), "menu");
      });
    });
  });

  enhanceIcons();

  const iconObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      mutation.addedNodes.forEach((node) => {
        if (node.nodeType === Node.ELEMENT_NODE) {
          enhanceIcons(node);
        }
      });
    });
  });
  iconObserver.observe(document.body, { childList: true, subtree: true });

  window.FooduckIcons = { set: setIcon, enhance: enhanceIcons };
  window.FooduckSession = {
    ...session,
    canManageBusiness,
    isAdmin,
    hasAuthority,
    recommendationHref,
  };
  window.FooduckProfile = {
    AUTHORITY_LABELS,
    authorityLabel,
    createSummary: createProfileSummary,
    formatDate: formatProfileDate,
    primaryAuthorityCode,
  };
  window.FooduckNotifications = {
    refreshUnreadCount: refreshNotificationBadges,
  };

  function initializeScrollTopButton() {
    if (document.querySelector(".board-scroll-top")) return;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "board-scroll-top";
    button.textContent = "↑";
    button.title = "맨 위로 이동";
    button.setAttribute("aria-label", "맨 위로 이동");
    button.hidden = true;
    document.body.append(button);

    let ticking = false;
    const updateVisibility = () => {
      button.hidden = window.scrollY <= 450;
      ticking = false;
    };

    window.addEventListener("scroll", () => {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(updateVisibility);
    }, { passive: true });

    button.addEventListener("click", () => {
      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      window.scrollTo({
        top: 0,
        behavior: reduceMotion ? "auto" : "smooth",
      });
    });

    updateVisibility();
  }

  initializeScrollTopButton();
})();
