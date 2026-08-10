(() => {
  const session = window.FooduckSession;
  const detailLayout = window.FooduckDetailLayout;
  const gate = document.getElementById("mypage-detail-gate");
  const loginLink = document.getElementById("mypage-detail-login");
  const content = document.getElementById("mypage-detail-content");

  if (!session || !detailLayout || !gate || !loginLink || !content) {
    return;
  }

  const tabs = {
    favorites: {
      label: "찜한 가게",
      title: "찜한 가게",
      description: "관심 있게 저장한 맛집 목록입니다.",
      endpoint: "/mypage/favorites",
      countKey: "favoriteCount",
      emptyIcon: "favorite",
      emptyTitle: "찜한 가게가 없습니다.",
      emptyCopy: "맛집 찾기에서 마음에 드는 가게를 저장해 보세요.",
    },
    reviews: {
      label: "내 리뷰",
      title: "내가 작성한 리뷰",
      description: "음식점에 남긴 별점과 리뷰를 확인합니다.",
      endpoint: "/mypage/reviews",
      countKey: "reviewCount",
      emptyIcon: "rate_review",
      emptyTitle: "작성한 리뷰가 없습니다.",
      emptyCopy: "방문한 음식점에 첫 리뷰를 남겨보세요.",
    },
    posts: {
      label: "내 게시글",
      title: "내가 작성한 게시글",
      description: "커뮤니티에 작성한 게시글을 확인합니다.",
      endpoint: "/mypage/posts",
      countKey: "postCount",
      emptyIcon: "article",
      emptyTitle: "작성한 게시글이 없습니다.",
      emptyCopy: "커뮤니티에서 맛있는 이야기를 공유해 보세요.",
    },
    comments: {
      label: "내 댓글",
      title: "내가 작성한 댓글",
      description: "커뮤니티 게시글에 남긴 댓글을 확인합니다.",
      endpoint: "/mypage/comments",
      countKey: "commentCount",
      emptyIcon: "chat_bubble",
      emptyTitle: "작성한 댓글이 없습니다.",
      emptyCopy: "관심 있는 게시글에 의견을 남겨보세요.",
    },
    notifications: {
      label: "읽지 않은 알림",
      title: "읽지 않은 알림",
      description: "아직 확인하지 않은 새로운 소식입니다.",
      endpoint: "/mypage/notifications/unread",
      countKey: "unreadNotificationCount",
      emptyIcon: "notifications",
      emptyTitle: "새로운 알림이 없습니다.",
      emptyCopy: "새 알림이 도착하면 이곳에서 확인할 수 있습니다.",
    },
  };

  const requestedTab = new URLSearchParams(window.location.search).get("tab");
  const activeTab = Object.prototype.hasOwnProperty.call(tabs, requestedTab)
    ? requestedTab
    : "favorites";
  const activeConfig = tabs[activeTab];

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function detailPath(tab) {
    return `/pages/mypage/detail.html?tab=${encodeURIComponent(tab)}`;
  }

  function boardDetailPath(postId) {
    return `/pages/board/detail.html?postId=${encodeURIComponent(postId)}`;
  }

  function searchPath(name) {
    return `/pages/search/index.html?q=${encodeURIComponent(name || "")}`;
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

  function countFor(overview, tab) {
    return Number(overview[tabs[tab].countKey] || 0);
  }

  function safeInternalUrl(value) {
    if (!value) return null;
    try {
      const url = new URL(value, window.location.origin);
      if (url.origin !== window.location.origin) return null;
      return `${url.pathname}${url.search}${url.hash}`;
    } catch {
      return null;
    }
  }

  function createCardTop(title, badgeText, href) {
    const top = element("div", "mypage-detail-card-top");
    const heading = element("h3");
    if (href) {
      const link = element("a", "", title);
      link.href = href;
      heading.append(link);
    } else {
      heading.textContent = title;
    }
    top.append(heading);
    if (badgeText) top.append(element("span", "mypage-detail-badge", badgeText));
    return top;
  }

  function createFooter(dateText, href, linkText) {
    const footer = element("div", "mypage-detail-card-footer");
    footer.append(element("time", "", formatDate(dateText)));
    if (href) {
      const link = element("a", "", linkText);
      link.href = href;
      footer.append(link);
    }
    return footer;
  }

  function favoriteCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = searchPath(item.restaurantName);
    card.append(
      createCardTop(item.restaurantName || "이름 없는 가게", item.categoryName || "카테고리 없음", href),
      element("p", "", item.address || "주소 정보 없음"),
    );
    if (item.description) card.append(element("p", "", item.description));
    card.append(createFooter(item.createdAt, href, "가게 찾아보기 →"));
    return card;
  }

  function reviewCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = searchPath(item.restaurantName);
    card.append(createCardTop(item.restaurantName || "음식점", "내 리뷰", href));
    card.append(
      element("p", "mypage-detail-stars", "★".repeat(Math.max(0, Math.min(5, item.rating || 0)))),
      element("p", "", item.content || "작성한 리뷰 내용이 없습니다."),
      createFooter(item.createdAt, href, "가게 찾아보기 →"),
    );
    return card;
  }

  function postCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = boardDetailPath(item.postId);
    card.append(createCardTop(item.title || "제목 없는 게시글", item.category || "일반", href));
    const meta = element("div", "mypage-detail-meta");
    meta.append(
      element("span", "", `조회 ${item.viewCount || 0}`),
      element("span", "", `추천 ${item.likeCount || 0}`),
      element("span", "", `댓글 ${item.commentCount || 0}`),
    );
    card.append(meta, createFooter(item.createdAt, href, "게시글 보기 →"));
    return card;
  }

  function commentCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = boardDetailPath(item.postId);
    card.append(
      createCardTop(item.postTitle || "원본 게시글", "내 댓글", href),
      element("p", "", item.content || "댓글 내용이 없습니다."),
      createFooter(item.createdAt, href, "원본 글 보기 →"),
    );
    return card;
  }

  function notificationLabel(type) {
    return {
      COMMENT: "새 댓글",
      POST_LIKE_MILESTONE: "게시글 추천",
      BUSINESS_APPROVED: "사업자 승인",
      BUSINESS_REJECTED: "사업자 반려",
    }[type] || "알림";
  }

  function notificationCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = safeInternalUrl(item.targetUrl);
    card.append(
      createCardTop(notificationLabel(item.type), "읽지 않음"),
      element("p", "", item.content || "새로운 알림이 도착했습니다."),
      createFooter(item.createdAt, href, href ? "관련 화면 보기 →" : null),
    );
    return card;
  }

  function renderItems(items) {
    if (!items.length) {
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
    const renderer = {
      favorites: favoriteCard,
      reviews: reviewCard,
      posts: postCard,
      comments: commentCard,
      notifications: notificationCard,
    }[activeTab];
    items.forEach((item) => list.append(renderer(item)));
    return list;
  }

  function render(overview, items) {
    content.replaceChildren();
    document.title = `${activeConfig.label} · 마이페이지 · 푸드덕`;

    const layout = element("div", "mypage-detail-layout");
    const main = element("section", "mypage-detail-main");
    const heading = element("header", "mypage-detail-heading");
    const headingCopy = element("div");
    headingCopy.append(
      element("h2", "", activeConfig.title),
      element("p", "", activeConfig.description),
    );
    heading.append(
      headingCopy,
      element("span", "mypage-detail-count", `${countFor(overview, activeTab)}개`),
    );
    main.append(heading, renderItems(items));
    const sidebarItems = Object.entries(tabs).map(([tab, config]) => ({
      label: config.label,
      href: detailPath(tab),
      count: countFor(overview, tab),
      current: tab === activeTab,
    }));
    layout.append(detailLayout.createSidebar({
      profile: overview,
      homeHref: "/pages/mypage/index.html",
      homeLabel: "← 마이페이지 홈",
      ariaLabel: "마이페이지 상세 메뉴",
      items: sidebarItems,
    }), main);
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
      element("h2", "", "마이페이지 상세 정보를 불러오지 못했습니다."),
      element("p", "", error.message || "잠시 후 다시 시도해 주세요."),
    );
    content.append(wrapper);
    window.FooduckIcons?.enhance(content);
  }

  if (!session.authenticated) {
    content.hidden = true;
    gate.hidden = false;
    loginLink.href =
      "/pages/auth/login.html?next=" +
      encodeURIComponent(`${window.location.pathname}${window.location.search}`);
    return;
  }

  Promise.all([
    Api.get("/mypage/overview"),
    Api.get(activeConfig.endpoint),
  ])
    .then(([overviewPayload, activityPayload]) => {
      const overview = overviewPayload.data || {};
      const items = Array.isArray(activityPayload.data) ? activityPayload.data : [];
      render(overview, items);
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
