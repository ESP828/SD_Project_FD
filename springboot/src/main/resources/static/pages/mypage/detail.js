(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("mypage-detail-gate");
  const loginLink = document.getElementById("mypage-detail-login");
  const content = document.getElementById("mypage-detail-content");

  if (!session || !gate || !loginLink || !content) {
    return;
  }

  const tabs = {
    presets: {
      label: "보물지도 리스트",
      icon: "map",
      title: "내가 만든 보물지도",
      description: "현재 계정으로 만든 보물지도를 확인하고 수정할 수 있습니다.",
      endpoint: "/mypage/presets",
      countKey: "presetCount",
      emptyIcon: "bookmarks",
      emptyTitle: "만든 보물지도가 없습니다.",
      emptyCopy: "보물지도 페이지에서 나만의 맛집 목록을 만들어 보세요.",
    },
    favorites: {
      label: "찜한 가게",
      icon: "favorite",
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
      icon: "rate_review",
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
      icon: "article",
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
      icon: "chat_bubble",
      title: "내가 작성한 댓글",
      description: "커뮤니티 게시글에 남긴 댓글을 확인합니다.",
      endpoint: "/mypage/comments",
      countKey: "commentCount",
      emptyIcon: "chat_bubble",
      emptyTitle: "작성한 댓글이 없습니다.",
      emptyCopy: "관심 있는 게시글에 의견을 남겨보세요.",
    },
    notifications: {
      label: "알림",
      icon: "notifications",
      title: "내 알림",
      description: "새 알림과 이전에 확인한 알림을 함께 관리합니다.",
      endpoint: "/notifications",
      countKey: "unreadNotificationCount",
      emptyIcon: "notifications",
      emptyTitle: "알림이 없습니다.",
      emptyCopy: "새 알림이 도착하면 이곳에서 확인할 수 있습니다.",
    },
  };

  const requestedTab = new URLSearchParams(window.location.search).get("tab");
  const activeTab = Object.prototype.hasOwnProperty.call(tabs, requestedTab)
    ? requestedTab
    : "presets";
  const activeConfig = tabs[activeTab];
  const state = {
    overview: {},
    items: [],
  };

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function emojiTextElement(tag, className, text) {
    const node = element(tag, className, "");
    if (window.FooduckEmojis) {
      window.FooduckEmojis.renderText(node, text);
    } else {
      node.textContent = String(text ?? "");
    }
    return node;
  }

  function detailPath(tab) {
    return `/mypage/detail?tab=${encodeURIComponent(tab)}`;
  }

  function boardDetailPath(postId) {
    return `/board/detail?postId=${encodeURIComponent(postId)}`;
  }

  function presetDetailPath(presetId, edit = false) {
    const query = new URLSearchParams({ presetId });
    if (edit) query.set("edit", "1");
    return `/presset/detail?${query.toString()}`;
  }

  function searchPath(name) {
    return `/search?q=${encodeURIComponent(name || "")}`;
  }

  function restaurantDetailPath(item) {
    const source = String(item.restaurantSource || "").toUpperCase();
    const restaurantId = Number(item.restaurantId);
    const publicRestaurantId = Number(item.publicRestaurantId);

    if ((source === "PUBLIC" || restaurantId <= 0) && publicRestaurantId > 0) {
      const query = new URLSearchParams({ source: "public", id: String(publicRestaurantId) });
      return `/restaurant/detail?${query.toString()}`;
    }
    if (restaurantId > 0) {
      const query = new URLSearchParams({ source: "owned", id: String(restaurantId) });
      return `/restaurant/detail?${query.toString()}`;
    }
    return searchPath(item.restaurantName);
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
    const href = restaurantDetailPath(item);
    card.append(
      createCardTop(item.restaurantName || "이름 없는 가게", item.categoryName || "카테고리 없음", href),
      element("p", "", item.address || "주소 정보 없음"),
    );
    if (item.description) card.append(element("p", "", item.description));
    card.append(createFooter(item.createdAt, href, "가게 보기 →"));
    return card;
  }

  function presetCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = presetDetailPath(item.presetId);
    card.append(createCardTop(item.title || "제목 없는 보물지도", item.category || "카테고리 없음", href));

    const meta = element("div", "mypage-detail-meta");
    meta.append(
      element("span", "", `맛집 ${Number(item.restaurantCount || 0).toLocaleString("ko-KR")}곳`),
      element("span", "", `찜 ${Number(item.favoriteCount || 0).toLocaleString("ko-KR")}`),
      element("span", "", `조회 ${Number(item.viewCount || 0).toLocaleString("ko-KR")}`),
    );
    card.append(meta);

    const tags = Array.isArray(item.tags)
      ? item.tags.map((tag) => tag.tagName || tag.name).filter(Boolean)
      : [];
    if (tags.length) {
      card.append(element("p", "mypage-detail-preset-tags", tags.map((tag) => `#${tag}`).join(" ")));
    }
    card.append(createFooter(item.createdAt, presetDetailPath(item.presetId, true), "수정하기 →"));
    return card;
  }

  function reviewCard(item) {
    const card = element("article", "mypage-detail-card");
    const href = restaurantDetailPath(item);
    const footer = element("div", "mypage-detail-card-footer");
    const actions = element("div", "mypage-detail-card-actions");
    const viewLink = element("a", "button button-sm button-secondary", "가게 보기");
    const editButton = element("button", "button button-sm button-secondary", "수정");
    const deleteButton = element(
      "button",
      "button button-sm button-secondary mypage-review-delete",
      "삭제",
    );
    viewLink.href = href;
    editButton.type = "button";
    deleteButton.type = "button";
    editButton.addEventListener("click", () => openReviewEditor(card, item, href));
    deleteButton.addEventListener("click", () => deleteReview(item, deleteButton));
    actions.append(viewLink, editButton, deleteButton);
    footer.append(element("time", "", formatDate(item.createdAt)), actions);
    card.append(createCardTop(item.restaurantName || "음식점", "내 리뷰", href));
    card.append(
      element("p", "mypage-detail-stars", "★".repeat(Math.max(0, Math.min(5, item.rating || 0)))),
      emojiTextElement("p", "", item.content || "작성한 리뷰 내용이 없습니다."),
      footer,
    );
    return card;
  }

  function openReviewEditor(card, item, href) {
    const form = element("form", "mypage-review-edit");
    const ratingField = element("label", "mypage-review-field");
    const rating = document.createElement("select");
    rating.name = "rating";
    rating.required = true;
    for (let value = 5; value >= 1; value -= 1) {
      const option = element("option", "", `${value}점`);
      option.value = String(value);
      option.selected = value === Number(item.rating);
      rating.append(option);
    }
    ratingField.append(element("span", "", "별점"), rating);

    const contentField = element("label", "mypage-review-field");
    const reviewContent = document.createElement("textarea");
    reviewContent.name = "content";
    reviewContent.maxLength = 1000;
    reviewContent.rows = 5;
    reviewContent.value = item.content || "";
    contentField.append(element("span", "", "리뷰 내용"), reviewContent);

    const status = element("p", "mypage-review-status");
    status.setAttribute("role", "status");
    const actions = element("div", "mypage-detail-card-actions");
    const cancelButton = element("button", "button button-sm button-secondary", "취소");
    const saveButton = element("button", "button button-sm button-primary", "저장");
    cancelButton.type = "button";
    saveButton.type = "submit";
    actions.append(cancelButton, saveButton);
    form.append(ratingField, contentField, status, actions);

    cancelButton.addEventListener("click", () => render(state.overview, state.items));
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      saveButton.disabled = true;
      cancelButton.disabled = true;
      status.className = "mypage-review-status";
      status.textContent = "저장하고 있습니다.";
      try {
        const payload = await Api.put(`/reviews/${encodeURIComponent(item.reviewId)}`, {
          rating: Number(rating.value),
          content: reviewContent.value,
        });
        const updated = payload.data || {};
        item.rating = Number(updated.rating ?? rating.value);
        item.content = updated.content ?? reviewContent.value;
        item.updatedAt = updated.updatedAt || new Date().toISOString();
        render(state.overview, state.items);
      } catch (error) {
        status.classList.add("is-error");
        status.textContent = error.message || "리뷰를 수정하지 못했습니다.";
        saveButton.disabled = false;
        cancelButton.disabled = false;
      }
    });

    card.replaceChildren(
      createCardTop(item.restaurantName || "음식점", "리뷰 수정", href),
      form,
    );
    reviewContent.focus();
  }

  async function deleteReview(item, button) {
    if (!window.confirm("이 리뷰를 삭제할까요?")) return;
    button.disabled = true;
    try {
      await Api.delete(`/reviews/${encodeURIComponent(item.reviewId)}`);
      state.items = state.items.filter(
        (candidate) => String(candidate.reviewId) !== String(item.reviewId),
      );
      state.overview.reviewCount = Math.max(0, Number(state.overview.reviewCount || 0) - 1);
      render(state.overview, state.items);
    } catch (error) {
      button.disabled = false;
      window.alert(error.message || "리뷰를 삭제하지 못했습니다.");
    }
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
      emojiTextElement("p", "", item.content || "댓글 내용이 없습니다."),
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
    const card = element("article", `mypage-detail-card${item.read ? " is-read" : " is-unread"}`);
    const href = safeInternalUrl(item.targetUrl);
    const footer = element("div", "mypage-detail-card-footer");
    const actions = element("div", "mypage-detail-card-actions");
    footer.append(element("time", "", formatDate(item.createdAt)));

    if (!item.read) {
      const readButton = element("button", "button button-sm button-secondary", "읽음");
      readButton.type = "button";
      readButton.addEventListener("click", async () => {
        readButton.disabled = true;
        try {
          await markNotificationRead(item);
        } catch (error) {
          readButton.disabled = false;
          window.alert(error.message || "알림을 읽음 처리하지 못했습니다.");
        }
      });
      actions.append(readButton);
    }
    if (href) {
      const targetLink = element("a", "button button-sm button-secondary", "관련 화면");
      targetLink.href = href;
      targetLink.addEventListener("click", async (event) => {
        event.preventDefault();
        if (!item.read) {
          try {
            await markNotificationRead(item, false);
          } catch {
            // 읽음 처리 실패가 안전한 내부 화면 이동을 막지는 않는다.
          }
        }
        window.location.assign(href);
      });
      actions.append(targetLink);
    }
    const deleteButton = element("button", "button button-sm button-secondary", "삭제");
    deleteButton.type = "button";
    deleteButton.addEventListener("click", () => deleteNotification(item));
    actions.append(deleteButton);
    footer.append(actions);

    card.append(
      createCardTop(notificationLabel(item.type), item.read ? "읽음" : "읽지 않음"),
      element("p", "", item.content || "새로운 알림이 도착했습니다."),
      footer,
    );
    return card;
  }

  function refreshNotificationView() {
    render(state.overview, state.items);
    window.FooduckNotifications?.setUnreadCount(
      Number(state.overview.unreadNotificationCount || 0),
    );
  }

  async function markNotificationRead(item, rerender = true) {
    if (item.read) return;
    const payload = await Api.patch(`/notifications/${item.notificationId}/read`);
    Object.assign(item, payload.data || {}, { read: true });
    state.overview.unreadNotificationCount = Math.max(
      0,
      Number(state.overview.unreadNotificationCount || 0) - 1,
    );
    if (rerender) {
      refreshNotificationView();
    } else {
      window.FooduckNotifications?.setUnreadCount(
        Number(state.overview.unreadNotificationCount || 0),
      );
    }
  }

  async function deleteNotification(item) {
    if (!window.confirm("이 알림을 삭제할까요?")) return;
    try {
      await Api.delete(`/notifications/${item.notificationId}`);
      if (!item.read) {
        state.overview.unreadNotificationCount = Math.max(
          0,
          Number(state.overview.unreadNotificationCount || 0) - 1,
        );
      }
      state.items = state.items.filter(
        (candidate) => candidate.notificationId !== item.notificationId,
      );
      refreshNotificationView();
    } catch (error) {
      window.alert(error.message || "알림을 삭제하지 못했습니다.");
    }
  }

  async function markAllNotificationsRead() {
    try {
      await Api.patch("/notifications/read-all");
      state.items.forEach((item) => {
        item.read = true;
      });
      state.overview.unreadNotificationCount = 0;
      refreshNotificationView();
    } catch (error) {
      window.alert(error.message || "알림을 모두 읽음 처리하지 못했습니다.");
    }
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
      presets: presetCard,
      favorites: favoriteCard,
      reviews: reviewCard,
      posts: postCard,
      comments: commentCard,
      notifications: notificationCard,
    }[activeTab];
    items.forEach((item) => list.append(renderer(item)));
    return list;
  }

  function createMenuBar(items) {
    const menuBar = element("nav", "mypage-detail-nav");
    menuBar.setAttribute("aria-label", "마이페이지 상세 메뉴");
    menuBar.style.setProperty("--mypage-detail-nav-columns", Math.max(items.length, 1));
    items.forEach((item) => {
      const link = element("a");
      link.href = item.href;
      if (item.current) link.setAttribute("aria-current", "page");
      const label = element("span", "mypage-detail-tab-label");
      const icon = element("span", "material-symbols-rounded", item.icon);
      icon.setAttribute("aria-hidden", "true");
      window.FooduckIcons?.set(icon, item.icon);
      label.append(icon, document.createTextNode(item.label));
      const count = element(
        "span",
        "mypage-detail-tab-count",
        `${new Intl.NumberFormat("ko-KR").format(Number(item.count) || 0)}개`,
      );
      link.append(label, count);
      menuBar.append(link);
    });
    return menuBar;
  }

  function render(overview, items) {
    content.replaceChildren();
    document.title = `${activeConfig.label} · 마이페이지 · 푸드덕`;

    const layout = element("div", "mypage-detail-layout");
    const main = element("section", "mypage-detail-main");
    const surface = element("section", "mypage-detail-surface");
    const heading = element("header", "mypage-detail-heading");
    const headingCopy = element("div", "mypage-detail-heading-copy");
    headingCopy.append(
      element("h2", "", activeConfig.title),
      element("p", "", activeConfig.description),
    );
    const headingActions = element("div", "mypage-detail-heading-actions");
    const countText = activeTab === "notifications"
      ? `미읽음 ${countFor(overview, activeTab)}개`
      : `${countFor(overview, activeTab)}개`;
    headingActions.append(element("span", "mypage-detail-count", countText));
    if (activeTab === "notifications" && countFor(overview, activeTab) > 0) {
      const readAllButton = element("button", "button button-sm button-secondary", "전체 읽음");
      readAllButton.type = "button";
      readAllButton.addEventListener("click", markAllNotificationsRead);
      headingActions.append(readAllButton);
    }
    heading.append(headingCopy, headingActions);
    const menuItems = Object.entries(tabs).map(([tab, config]) => ({
      label: config.label,
      icon: config.icon,
      href: detailPath(tab),
      count: countFor(overview, tab),
      current: tab === activeTab,
    }));
    const menuBar = createMenuBar(menuItems);
    const body = element("div", "mypage-detail-body");
    body.append(renderItems(items));
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
      "/auth/login?next=" +
      encodeURIComponent(`${window.location.pathname}${window.location.search}`);
    return;
  }

  Promise.all([
    Api.get("/mypage/overview"),
    Api.get(activeConfig.endpoint),
  ])
    .then(([overviewPayload, activityPayload]) => {
      state.overview = overviewPayload.data || {};
      state.items = Array.isArray(activityPayload.data) ? activityPayload.data : [];
      render(state.overview, state.items);
      if (activeTab === "notifications") {
        window.FooduckNotifications?.setUnreadCount(
          Number(state.overview.unreadNotificationCount || 0),
        );
      }
    })
    .catch((error) => {
      if (!localStorage.getItem("accessToken")) {
        window.location.assign(
          "/auth/login?next=" +
          encodeURIComponent(`${window.location.pathname}${window.location.search}`),
        );
        return;
      }
      renderError(error);
    });
})();
