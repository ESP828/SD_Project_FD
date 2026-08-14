(() => {
  const BOARD_CACHE_PREFIX = "fooduck:board:v1:";
  const BOARD_CACHE_FRESH_MS = 60_000;
  const BOARD_CACHE_MAX_AGE_MS = 5 * 60_000;
  const categoryLabels = {
    GENERAL: "자유 이야기",
    NOTICE: "공지",
    NEWS: "가게 소식",
    RECOMMENDATION: "맛집 추천",
    REVIEW: "방문 후기",
    QUESTION: "질문",
    TRAVEL: "맛집 여행",
  };

  const roleLabels = {
    USER: "일반 회원",
    BUSINESS: "사업자",
    ADMIN: "관리자",
  };

  let authorMenu = null;
  let activeAuthorTrigger = null;
  let businessAccessPromise = null;
  let authorActivityHint = null;
  let authorActivityHintTarget = null;
  let authorActivityHintShownInSession = false;
  let authorActivityHintTimer = null;
  let authorActivityTooltip = null;
  let authorActivityTooltipTarget = null;
  const AUTHOR_ACTIVITY_HINT_KEY = "fooduck:author-profile-hint:v1";

  function cacheScope() {
    const session = window.FooduckSession;
    if (!session?.authenticated) return "guest";
    const accountKey = session.accountId || session.loginId || "unknown";
    return `account-${encodeURIComponent(String(accountKey))}`;
  }

  function cacheKey(resource) {
    return `${BOARD_CACHE_PREFIX}${cacheScope()}:${resource}`;
  }

  function readCacheEntry(resource) {
    const key = cacheKey(resource);
    try {
      const raw = window.sessionStorage.getItem(key);
      if (!raw) return null;
      const entry = JSON.parse(raw);
      const hasData = entry
        && Object.prototype.hasOwnProperty.call(entry, "data");
      const age = Date.now() - Number(entry?.savedAt);
      if (!hasData || !Number.isFinite(age) || age < 0 || age > BOARD_CACHE_MAX_AGE_MS) {
        window.sessionStorage.removeItem(key);
        return null;
      }
      return {
        data: entry.data,
        fresh: age <= BOARD_CACHE_FRESH_MS,
      };
    } catch (_error) {
      return null;
    }
  }

  function writeCacheEntry(resource, data) {
    try {
      window.sessionStorage.setItem(
        cacheKey(resource),
        JSON.stringify({ savedAt: Date.now(), data }),
      );
    } catch (_error) {
      // 저장 공간을 사용할 수 없는 환경에서는 기존 API 호출 방식으로 동작한다.
    }
  }

  function readBoardCache(path) {
    return readCacheEntry(`content:${path}`);
  }

  function writeBoardCache(path, data) {
    writeCacheEntry(`content:${path}`, data);
  }

  function updateCachedPostViewCount(postId, viewCount) {
    const targetPostId = Number(postId);
    const targetViewCount = Number(viewCount);
    if (!Number.isSafeInteger(targetPostId) || targetPostId <= 0) return;
    if (!Number.isFinite(targetViewCount) || targetViewCount < 0) return;

    const prefix = `${BOARD_CACHE_PREFIX}${cacheScope()}:content:`;

    function updateValue(value) {
      if (!value || typeof value !== "object") return false;

      let changed = false;
      if (
        Number(value.postId) === targetPostId
        && Object.prototype.hasOwnProperty.call(value, "viewCount")
      ) {
        value.viewCount = targetViewCount;
        changed = true;
      }

      Object.values(value).forEach((nestedValue) => {
        if (updateValue(nestedValue)) changed = true;
      });
      return changed;
    }

    try {
      for (let index = window.sessionStorage.length - 1; index >= 0; index -= 1) {
        const key = window.sessionStorage.key(index);
        if (!key?.startsWith(prefix)) continue;

        const raw = window.sessionStorage.getItem(key);
        if (!raw) continue;
        const entry = JSON.parse(raw);
        if (!entry || !Object.prototype.hasOwnProperty.call(entry, "data")) continue;
        if (!updateValue(entry.data)) continue;

        window.sessionStorage.setItem(key, JSON.stringify(entry));
      }
    } catch (_error) {
      // 조회수 캐시 동기화 실패가 게시글 조회를 막지 않도록 한다.
    }
  }

  function invalidateBoardCache() {
    try {
      for (let index = window.sessionStorage.length - 1; index >= 0; index -= 1) {
        const key = window.sessionStorage.key(index);
        if (
          key?.startsWith(BOARD_CACHE_PREFIX)
          && key.includes(":content:")
        ) {
          window.sessionStorage.removeItem(key);
        }
      }
    } catch (_error) {
      // 캐시 제거 실패가 게시판 변경 작업을 막지 않도록 한다.
    }
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function icon(name) {
    const node = element("span", "material-symbols-rounded", name);
    node.setAttribute("aria-hidden", "true");
    return node;
  }

  function formatDate(value) {
    if (!value) return "날짜 정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  }

  function categoryLabel(value) {
    return categoryLabels[value] || value || "카테고리 없음";
  }

  function roleLabel(value) {
    return roleLabels[value] || roleLabels.USER;
  }

  function roleClass(value) {
    const normalized = roleLabels[value] ? value.toLowerCase() : "user";
    return `post-role post-role--${normalized}`;
  }

  function hasSeenAuthorActivityHint() {
    if (authorActivityHintShownInSession) return true;
    try {
      return window.localStorage.getItem(AUTHOR_ACTIVITY_HINT_KEY) === "1";
    } catch (_error) {
      return false;
    }
  }

  function markAuthorActivityHintSeen() {
    authorActivityHintShownInSession = true;
    try {
      window.localStorage.setItem(AUTHOR_ACTIVITY_HINT_KEY, "1");
    } catch (_error) {
      // localStorage를 사용할 수 없는 환경에서는 현재 페이지 세션에서만 다시 표시하지 않는다.
    }
  }

  function closeAuthorActivityHint() {
    if (authorActivityHintTimer) {
      window.clearTimeout(authorActivityHintTimer);
      authorActivityHintTimer = null;
    }
    if (authorActivityHintTarget) {
      authorActivityHintTarget.classList.remove("is-onboarding-target");
      authorActivityHintTarget = null;
    }
    if (!authorActivityHint) return;
    authorActivityHint.remove();
    authorActivityHint = null;
  }

  function positionFloatingPanel(panel, trigger, gap = 10) {
    if (!panel || !trigger?.isConnected) return null;
    const rect = trigger.getBoundingClientRect();
    const panelRect = panel.getBoundingClientRect();
    const viewportPadding = 12;
    const canPlaceBelow = rect.bottom + gap + panelRect.height <= window.innerHeight - viewportPadding;
    const top = canPlaceBelow
      ? rect.bottom + gap
      : Math.max(viewportPadding, rect.top - panelRect.height - gap);
    const left = Math.min(
      Math.max(viewportPadding, rect.left + rect.width / 2 - panelRect.width / 2),
      window.innerWidth - panelRect.width - viewportPadding,
    );
    panel.dataset.placement = canPlaceBelow ? "below" : "above";
    panel.style.top = `${top}px`;
    panel.style.left = `${left}px`;
    return canPlaceBelow ? "below" : "above";
  }

  function positionAuthorActivityHint(trigger) {
    positionFloatingPanel(authorActivityHint, trigger, 10);
  }

  function closeAuthorActivityTooltip() {
    if (authorActivityTooltip) {
      authorActivityTooltip.remove();
      authorActivityTooltip = null;
    }
    authorActivityTooltipTarget = null;
  }

  function showAuthorActivityTooltip(trigger) {
    if (!trigger?.isConnected || authorActivityHint) return;
    const message = trigger.dataset.authorActivityTooltip;
    if (!message) return;
    closeAuthorActivityTooltip();
    const tooltip = element("div", "author-activity-tooltip", message);
    tooltip.setAttribute("role", "tooltip");
    document.body.append(tooltip);
    authorActivityTooltip = tooltip;
    authorActivityTooltipTarget = trigger;
    positionFloatingPanel(tooltip, trigger, 8);
  }

  function maybeShowAuthorActivityHint(trigger) {
    window.setTimeout(() => {
      if (hasSeenAuthorActivityHint() || authorActivityHint || !trigger?.isConnected) return;
      const rect = trigger.getBoundingClientRect();
      const visible = rect.width > 0
        && rect.height > 0
        && rect.bottom >= 0
        && rect.top <= window.innerHeight;
      if (!visible) return;

      closeAuthorActivityTooltip();
      const hint = element("aside", "author-activity-onboarding");
      hint.setAttribute("role", "status");
      hint.setAttribute("aria-live", "polite");
      const copy = element("div", "author-activity-onboarding-copy");
      copy.append(
        element("strong", "author-activity-onboarding-title", "작성자 프로필을 확인해보세요"),
        element("span", "author-activity-onboarding-text", "닉네임이나 프로필 버튼을 눌러 글 · 댓글 · 리뷰를 볼 수 있어요."),
      );
      const close = element("button", "author-activity-onboarding-close", "×");
      close.type = "button";
      close.setAttribute("aria-label", "작성자 프로필 안내 닫기");
      close.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        closeAuthorActivityHint();
      });
      hint.append(copy, close);
      document.body.append(hint);
      authorActivityHint = hint;
      authorActivityHintTarget = trigger;
      trigger.classList.add("is-onboarding-target");
      markAuthorActivityHintSeen();
      positionAuthorActivityHint(trigger);
      window.addEventListener("resize", closeAuthorActivityHint, { once: true });
      window.addEventListener("scroll", closeAuthorActivityHint, { once: true, capture: true });
      authorActivityHintTimer = window.setTimeout(closeAuthorActivityHint, 7_000);
    }, 700);
  }

  function closeAuthorMenu({ restoreFocus = false } = {}) {
    if (!authorMenu || authorMenu.hidden) return;
    const returnTarget = activeAuthorTrigger;
    authorMenu.hidden = true;
    authorMenu.removeAttribute("aria-busy");
    authorMenu.replaceChildren();
    if (returnTarget) {
      returnTarget.setAttribute("aria-expanded", "false");
    }
    activeAuthorTrigger = null;
    if (restoreFocus && returnTarget?.isConnected) {
      returnTarget.focus({ preventScroll: true });
    }
  }

  function ensureAuthorMenu() {
    if (authorMenu) return authorMenu;

    authorMenu = element("section", "author-menu");
    authorMenu.id = "board-author-menu";
    authorMenu.hidden = true;
    authorMenu.setAttribute("role", "dialog");
    authorMenu.setAttribute("aria-label", "작성자 프로필");
    authorMenu.tabIndex = -1;
    document.body.append(authorMenu);

    document.addEventListener("click", (event) => {
      if (authorMenu.hidden) return;
      if (authorMenu.contains(event.target)) return;
      if (activeAuthorTrigger?.contains(event.target)) return;
      closeAuthorMenu();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape" || authorMenu.hidden) return;
      event.preventDefault();
      closeAuthorMenu({ restoreFocus: true });
    });
    window.addEventListener("resize", closeAuthorMenu);
    window.addEventListener("scroll", (event) => {
      if (event.target === authorMenu) return;
      closeAuthorMenu();
    }, true);
    return authorMenu;
  }

  function positionAuthorMenu(trigger) {
    if (!authorMenu || authorMenu.hidden) return;
    const rect = trigger.getBoundingClientRect();
    const gap = 8;
    const viewportPadding = 12;
    const menuWidth = authorMenu.offsetWidth;
    const menuHeight = authorMenu.offsetHeight;
    const viewportBottom = window.scrollY + window.innerHeight;
    const preferredTop = window.scrollY + rect.bottom + gap;
    const alternateTop = window.scrollY + rect.top - menuHeight - gap;
    const top = preferredTop + menuHeight <= viewportBottom - viewportPadding
      ? preferredTop
      : Math.max(window.scrollY + viewportPadding, alternateTop);
    const left = Math.min(
      Math.max(window.scrollX + viewportPadding, window.scrollX + rect.left),
      window.scrollX + window.innerWidth - menuWidth - viewportPadding,
    );
    authorMenu.style.top = `${top}px`;
    authorMenu.style.left = `${left}px`;
  }

  function authorActivityType(post) {
    if (post?.category === "NEWS") return "소식";
    if (post?.boardType === "BUSINESS") return "사업자";
    return "일반";
  }

  function authorActivityMeta(post) {
    const type = authorActivityType(post);
    if (post?.category === "NEWS") {
      return `${type} · ${formatDate(post.createdAt)}`;
    }
    return `${type} · ${categoryLabel(post?.category)} · ${formatDate(post?.createdAt)}`;
  }

  function authorPostDetailHref(post) {
    const path = detailPath(post.postId);
    return post?.category === "NEWS" ? `${path}&from=NEWS` : path;
  }

  function renderAuthorMenuLoading(author) {
    const menu = ensureAuthorMenu();
    const skeleton = element("div", "author-menu-skeleton");
    skeleton.setAttribute("aria-hidden", "true");

    const header = element("div", "author-menu-skeleton-header");
    header.append(
      element("span", "author-menu-skeleton-avatar"),
      element("span", "author-menu-skeleton-line author-menu-skeleton-line--name"),
      element("span", "author-menu-skeleton-badge"),
    );

    const status = element("div", "author-menu-skeleton-status");
    status.append(
      element("span", "author-menu-skeleton-dot"),
      element("span", "author-menu-skeleton-line author-menu-skeleton-line--status"),
    );

    const tabs = element("div", "author-menu-skeleton-tabs");
    for (let index = 0; index < 3; index += 1) {
      tabs.append(element("span", "author-menu-skeleton-tab"));
    }

    const activity = element("div", "author-menu-skeleton-activity");
    activity.append(
      element("span", "author-menu-skeleton-line author-menu-skeleton-line--title"),
      element("span", "author-menu-skeleton-card"),
      element("span", "author-menu-skeleton-card"),
      element("span", "author-menu-skeleton-card author-menu-skeleton-card--short"),
    );

    skeleton.append(header, status, tabs, activity, element("span", "author-menu-skeleton-footer"));
    const liveStatus = element(
      "p",
      "sr-only author-menu-loading-status",
      `${author.authorNickname || "작성자"} 프로필을 불러오는 중입니다.`,
    );
    liveStatus.setAttribute("role", "status");
    liveStatus.setAttribute("aria-live", "polite");
    menu.replaceChildren(skeleton, liveStatus);
  }

  function authorMenuEmptyState(iconName, title, description) {
    const empty = element("div", "author-menu-empty");
    const emptyIcon = icon(iconName);
    emptyIcon.classList.add("author-menu-empty-icon");
    empty.append(
      emptyIcon,
      element("strong", "author-menu-empty-title", title),
      element("p", "author-menu-empty-copy", description),
    );
    return empty;
  }

  function authorMenuRecentPostSection(posts) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", "최근 글"));
    const items = Array.isArray(posts) ? posts : [];
    if (items.length === 0) {
      section.append(authorMenuEmptyState(
        "article",
        "아직 작성한 글이 없습니다",
        "작성자가 남긴 공개 게시글이 생기면 여기에 표시됩니다.",
      ));
      return section;
    }

    const list = element("ul", "author-menu-recent-list");
    items.forEach((post) => {
      const item = element("li", "author-menu-recent-item");
      const link = element("a", "author-menu-recent-link");
      link.href = authorPostDetailHref(post);
      const titleRow = element("span", "author-menu-recent-title-row");
      titleRow.append(
        element("span", `author-menu-source author-menu-source--${authorActivityType(post) === "소식" ? "news" : "community"}`, authorActivityType(post)),
        element("span", "author-menu-recent-post-title", post.title || "제목 없음"),
      );
      link.append(
        titleRow,
        element("small", "author-menu-recent-meta", authorActivityMeta(post)),
      );
      item.append(link);
      list.append(item);
    });
    section.append(list);
    return section;
  }

  function authorMenuRecentCommentSection(comments) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", "최근 댓글"));
    const items = Array.isArray(comments) ? comments : [];
    if (items.length === 0) {
      section.append(authorMenuEmptyState(
        "chat_bubble",
        "아직 작성한 댓글이 없습니다",
        "작성자가 남긴 공개 댓글이 생기면 여기에 표시됩니다.",
      ));
      return section;
    }

    const list = element("ul", "author-menu-recent-list");
    items.forEach((comment) => {
      const item = element("li", "author-menu-recent-item");
      const link = element("a", "author-menu-recent-link");
      link.href = authorPostDetailHref(comment);
      const contentRow = element("span", "author-menu-recent-title-row");
      contentRow.append(
        element("span", `author-menu-source author-menu-source--${authorActivityType(comment) === "소식" ? "news" : "community"}`, authorActivityType(comment)),
        element("span", "author-menu-recent-content", comment.content || "댓글 내용 없음"),
      );
      link.append(
        contentRow,
        element(
          "small",
          "author-menu-recent-meta",
          `${comment.postTitle || (comment.category === "NEWS" ? "가게 소식" : "게시글")} · ${formatDate(comment.createdAt)}`,
        ),
      );
      item.append(link);
      list.append(item);
    });
    section.append(list);
    return section;
  }

  function authorMenuRecentReviewSection(reviews) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", "최근 리뷰"));
    const items = Array.isArray(reviews) ? reviews : [];
    if (items.length === 0) {
      section.append(authorMenuEmptyState(
        "rate_review",
        "아직 작성한 리뷰가 없습니다",
        "작성자가 남긴 공개 리뷰가 생기면 가게와 별점을 함께 확인할 수 있습니다.",
      ));
      return section;
    }

    const list = element("ul", "author-menu-recent-list");
    items.forEach((review) => {
      const item = element("li", "author-menu-recent-item");
      const link = element("a", "author-menu-recent-link");
      const source = review.restaurantSource === "public" ? "public" : "owned";
      link.href = `/pages/restaurant/detail.html?source=${source}&id=${encodeURIComponent(review.storeId)}&tab=review`;
      link.append(
        element("span", "author-menu-recent-post-title", review.restaurantName || "가게 정보 없음"),
        element(
          "span",
          "author-menu-recent-content author-menu-review-content",
          review.content || "내용 없이 별점만 남긴 리뷰",
        ),
        element(
          "small",
          "author-menu-recent-meta",
          `★ ${review.rating || 0} · ${formatDate(review.createdAt)}`,
        ),
      );
      item.append(link);
      list.append(item);
    });
    section.append(list);
    return section;
  }

  function authorMenuTab(label, count, key, active) {
    const button = element("button", `author-menu-tab${active ? " is-active" : ""}`);
    button.type = "button";
    button.id = `board-author-menu-tab-${key}`;
    button.dataset.authorActivityTab = key;
    button.setAttribute("role", "tab");
    button.setAttribute("aria-controls", "board-author-menu-panel");
    button.setAttribute("aria-selected", active ? "true" : "false");
    button.tabIndex = active ? 0 : -1;
    button.append(
      element("strong", "author-menu-tab-count", String(count || 0)),
      element("span", "author-menu-tab-label", label),
    );
    return button;
  }

  function renderAuthorMenuSummary(summary) {
    const menu = ensureAuthorMenu();
    const nickname = summary.nickname || "작성자";
    const header = element("header", "author-menu-header");
    const profile = element("div", "author-menu-profile");
    const avatar = element("span", "author-menu-avatar", Array.from(nickname)[0] || "?");
    avatar.setAttribute("aria-hidden", "true");
    const identity = element("div", "author-menu-identity");
    identity.append(
      element("strong", "author-menu-nickname", nickname),
      element(
        "span",
        "author-menu-account-id",
        summary.accountLabel || "계정 정보 없음",
      ),
    );
    profile.append(avatar, identity);
    header.append(
      profile,
      element("span", "author-menu-caption", "프로필"),
    );

    const lastActivity = element("div", "author-menu-last-activity");
    lastActivity.append(
      element("span", "author-menu-last-activity-dot"),
      element("span", "author-menu-last-activity-label", "마지막 공개 활동"),
      element(
        "strong",
        "author-menu-last-activity-time",
        summary.lastPublicActivityAt
          ? `${formatDate(summary.lastPublicActivityAt)}`
          : "공개 활동 기록 없음",
      ),
    );

    const tabs = element("div", "author-menu-tabs");
    tabs.setAttribute("role", "tablist");
    const tabDefinitions = [
      ["글", summary.postCount, "posts"],
      ["댓글", summary.commentCount, "comments"],
      ["리뷰", summary.reviewCount, "reviews"],
    ];
    tabDefinitions.forEach(([label, count, key], index) => {
      tabs.append(authorMenuTab(label, count, key, index === 0));
    });

    const activityPanel = element("div", "author-menu-activity-panel");
    activityPanel.id = "board-author-menu-panel";
    activityPanel.setAttribute("role", "tabpanel");
    activityPanel.setAttribute("aria-labelledby", "board-author-menu-tab-posts");
    const sections = {
      posts: authorMenuRecentPostSection(summary.recentPosts),
      comments: authorMenuRecentCommentSection(summary.recentComments),
      reviews: authorMenuRecentReviewSection(summary.recentReviews),
    };
    activityPanel.dataset.activeAuthorActivityTab = "posts";
    activityPanel.replaceChildren(sections.posts);

    let authorActivitySwitchId = 0;

    function stabilizeAuthorActivityPanelHeight() {
      const activeKey = activityPanel.dataset.activeAuthorActivityTab || "posts";
      const activeSection = sections[activeKey] || sections.posts;
      let maxHeight = 0;

      activityPanel.style.visibility = "hidden";
      activityPanel.style.minHeight = "";

      Object.values(sections).forEach((section) => {
        activityPanel.replaceChildren(section);
        maxHeight = Math.max(maxHeight, activityPanel.getBoundingClientRect().height);
      });

      activityPanel.replaceChildren(activeSection);
      if (maxHeight > 0) {
        activityPanel.style.minHeight = `${Math.ceil(maxHeight)}px`;
      }
      activityPanel.style.visibility = "";
    }

    async function switchAuthorActivityPanel(key) {
      const nextSection = sections[key];
      if (!nextSection) return;
      if (activityPanel.dataset.activeAuthorActivityTab === key) return;

      const switchId = ++authorActivitySwitchId;
      activityPanel.dataset.activeAuthorActivityTab = key;
      const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches;
      activityPanel.getAnimations({ subtree: true }).forEach((animation) => animation.cancel());

      if (reduceMotion || typeof activityPanel.animate !== "function") {
        activityPanel.replaceChildren(nextSection);
        return;
      }

      const currentSection = activityPanel.firstElementChild;
      if (currentSection) {
        const exitAnimation = currentSection.animate(
          [
            { opacity: 1 },
            { opacity: 0 },
          ],
          {
            duration: 70,
            easing: "ease-out",
            fill: "forwards",
          },
        );
        try {
          await exitAnimation.finished;
        } catch (_) {
          return;
        }
      }

      if (switchId !== authorActivitySwitchId) return;

      activityPanel.replaceChildren(nextSection);
      const enterAnimation = nextSection.animate(
        [
          { opacity: 0 },
          { opacity: 1 },
        ],
        {
          duration: 140,
          easing: "cubic-bezier(0.16, 1, 0.3, 1)",
          fill: "both",
        },
      );

      try {
        await enterAnimation.finished;
      } catch (_) {
        return;
      }
      if (switchId !== authorActivitySwitchId) return;

      enterAnimation.cancel();
    }

    function activateAuthorMenuTab(button, { moveFocus = false } = {}) {
      if (!button || !tabs.contains(button)) return;
      const key = button.dataset.authorActivityTab;
      if (!sections[key]) return;

      tabs.querySelectorAll("[data-author-activity-tab]").forEach((tab) => {
        const active = tab === button;
        tab.classList.toggle("is-active", active);
        tab.setAttribute("aria-selected", active ? "true" : "false");
        tab.tabIndex = active ? 0 : -1;
      });
      activityPanel.setAttribute("aria-labelledby", button.id);
      if (moveFocus) button.focus({ preventScroll: true });
      switchAuthorActivityPanel(key);
    }

    tabs.addEventListener("click", (event) => {
      const button = event.target.closest("[data-author-activity-tab]");
      activateAuthorMenuTab(button);
    });

    tabs.addEventListener("keydown", (event) => {
      const current = event.target.closest("[data-author-activity-tab]");
      if (!current || !tabs.contains(current)) return;
      const buttons = [...tabs.querySelectorAll("[data-author-activity-tab]")];
      const currentIndex = buttons.indexOf(current);
      let nextIndex = currentIndex;

      if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % buttons.length;
      else if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + buttons.length) % buttons.length;
      else if (event.key === "Home") nextIndex = 0;
      else if (event.key === "End") nextIndex = buttons.length - 1;
      else return;

      event.preventDefault();
      activateAuthorMenuTab(buttons[nextIndex], { moveFocus: true });
    });

    const footer = element("div", "author-menu-footer");
    const isCurrentUser = Number(window.FooduckSession?.accountId)
      === Number(summary.accountId);
    if (isCurrentUser) {
      const myPageLink = element("a", "author-menu-link", "마이페이지");
      myPageLink.href = "/pages/mypage/index.html#notifications";
      footer.append(myPageLink);
    } else {
      const disabledLink = element(
        "span",
        "author-menu-link author-menu-link--disabled",
        "마이페이지",
      );
      disabledLink.setAttribute("aria-disabled", "true");
      footer.append(
        disabledLink,
        element("small", "author-menu-note", "마이페이지는 본인만 열 수 있습니다."),
      );
    }

    menu.replaceChildren(header, lastActivity, tabs, activityPanel, footer);
    stabilizeAuthorActivityPanelHeight();
  }

  function renderAuthorMenuError(message, onRetry) {
    const menu = ensureAuthorMenu();
    const error = element("div", "author-menu-error-state");
    const errorIcon = icon("error");
    errorIcon.classList.add("author-menu-error-icon");
    error.append(
      errorIcon,
      element("strong", "author-menu-error-title", "프로필을 불러오지 못했습니다"),
      element("p", "author-menu-error", message || "잠시 후 다시 시도해 주세요."),
    );
    if (typeof onRetry === "function") {
      const retry = element("button", "author-menu-retry", "다시 시도");
      retry.type = "button";
      retry.addEventListener("click", onRetry);
      error.append(retry);
    }
    menu.replaceChildren(error);
  }

  async function loadAuthorMenuSummary(author, trigger, context = "COMMUNITY") {
    const menu = ensureAuthorMenu();
    menu.setAttribute("aria-busy", "true");
    renderAuthorMenuLoading(author);
    positionAuthorMenu(trigger);

    const accountId = Number(author.authorAccountId);
    try {
      const payload = await Api.get(
        `/board/posts/authors/${encodeURIComponent(accountId)}/summary`,
      );
      const summary = payload.data;
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuSummary(summary);
      menu.setAttribute("aria-busy", "false");
      positionAuthorMenu(trigger);
    } catch (error) {
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      menu.setAttribute("aria-busy", "false");
      renderAuthorMenuError(error.message, () => {
        if (activeAuthorTrigger !== trigger || menu.hidden) return;
        loadAuthorMenuSummary(author, trigger, context);
      });
      positionAuthorMenu(trigger);
    }
  }

  async function openAuthorMenu(author, trigger, event, context = "COMMUNITY") {
    event.preventDefault();
    event.stopPropagation();
    markAuthorActivityHintSeen();
    closeAuthorActivityHint();
    if (!author?.authorAccountId) return;

    const menu = ensureAuthorMenu();
    if (activeAuthorTrigger === trigger && !menu.hidden) {
      closeAuthorMenu();
      return;
    }

    if (activeAuthorTrigger) {
      activeAuthorTrigger.setAttribute("aria-expanded", "false");
    }
    activeAuthorTrigger = trigger;
    trigger.setAttribute("aria-expanded", "true");
    renderAuthorMenuLoading(author);
    menu.hidden = false;
    positionAuthorMenu(trigger);
    requestAnimationFrame(() => {
      if (!menu.hidden && activeAuthorTrigger === trigger) {
        menu.focus({ preventScroll: true });
      }
    });
    await loadAuthorMenuSummary(author, trigger, context);
  }

  function enableAuthorMenu(trigger, author, context = "COMMUNITY") {
    trigger.classList.add("author-nickname--interactive");
    trigger.tabIndex = 0;
    trigger.setAttribute("role", "button");
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", "board-author-menu");
    trigger.setAttribute("aria-expanded", "false");
    trigger.setAttribute("aria-label", `${author.authorNickname || "작성자"} 프로필 보기`);
    trigger.dataset.authorActivityTooltip = "작성자 프로필 보기 · 글 · 댓글 · 리뷰 확인";
    trigger.addEventListener("mouseenter", () => showAuthorActivityTooltip(trigger));
    trigger.addEventListener("mouseleave", closeAuthorActivityTooltip);
    trigger.addEventListener("focus", () => showAuthorActivityTooltip(trigger));
    trigger.addEventListener("blur", closeAuthorActivityTooltip);
    trigger.addEventListener("click", (event) => {
      closeAuthorActivityTooltip();
      openAuthorMenu(author, trigger, event, context);
    });
    trigger.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      closeAuthorActivityTooltip();
      openAuthorMenu(author, trigger, event, context);
    });
    maybeShowAuthorActivityHint(trigger);
  }

  function authorActivityCue(mode = "compact") {
    const normalized = mode === "full" ? "full" : "compact";
    const cue = element(
      "span",
      `author-activity-cue author-activity-cue--${normalized}`,
    );
    cue.setAttribute("aria-hidden", "true");

    const person = icon("person");
    person.classList.add("author-activity-person");
    cue.append(
      person,
      element(
        "span",
        "author-activity-cue-label",
        normalized === "full" ? "프로필 보기" : "프로필",
      ),
    );

    const arrow = icon("chevron_right");
    arrow.classList.add("author-activity-arrow");
    cue.append(arrow);
    return cue;
  }

  function authorIdentity(
    author,
    {
      showNickname = true,
      showAuthorMenu = false,
      authorMenuContext = "COMMUNITY",
      authorActivityCueMode = "compact",
    } = {},
  ) {
    const wrapper = element("span", "author-identity");
    if (!author?.authorLoginId) {
      wrapper.append(element("strong", "author-login-id", "소셜 계정"));
    }
    if (showNickname && author?.authorNickname) {
      const nickname = element("span", "author-nickname", author.authorNickname);
      if (showAuthorMenu && author.authorAccountId) {
        const trigger = element(
          "span",
          `author-activity-trigger author-activity-trigger--${authorActivityCueMode === "full" ? "full" : "compact"}`,
        );
        trigger.append(nickname, authorActivityCue(authorActivityCueMode));
        enableAuthorMenu(
          trigger,
          author,
          authorMenuContext === "NEWS" ? "NEWS" : "COMMUNITY",
        );
        wrapper.append(trigger);
      } else {
        wrapper.append(nickname);
      }
    }
    wrapper.append(
      element("span", roleClass(author?.authorRole), roleLabel(author?.authorRole)),
    );
    return wrapper;
  }

  async function canUseBusinessBoard() {
    const session = window.FooduckSession;
    if (!session?.authenticated) return false;
    if (session.canManageBusiness || session.isAdmin) return true;

    const cached = readCacheEntry("access:business");
    if (cached?.fresh) return Boolean(cached.data);

    if (!businessAccessPromise) {
      businessAccessPromise = Api.get("/board/posts/business-access")
        .then((payload) => {
          const allowed = Boolean(payload?.data);
          writeCacheEntry("access:business", allowed);
          return allowed;
        })
        .catch(() => false);
    }
    return businessAccessPromise;
  }

  function detailPath(postId) {
    return `/pages/board/detail.html?postId=${encodeURIComponent(postId)}`;
  }

  function writePath(boardType = "GENERAL", postId = null) {
    const params = new URLSearchParams();
    if (postId) {
      params.set("postId", postId);
    } else {
      params.set("boardType", boardType === "BUSINESS" ? "BUSINESS" : "GENERAL");
    }
    return `/pages/board/write.html?${params.toString()}`;
  }

  function listPath(boardType = "GENERAL") {
    return boardType === "BUSINESS"
      ? "/pages/board/index.html?boardType=BUSINESS"
      : "/pages/board/index.html";
  }

  function loginPath(nextPath = window.location.pathname + window.location.search) {
    return `/pages/auth/login.html?next=${encodeURIComponent(nextPath)}`;
  }

  function requireLogin(nextPath) {
    if (window.FooduckSession?.authenticated) return true;
    window.location.assign(loginPath(nextPath));
    return false;
  }

  const BOARD_AUTH_POPUP_PATHS = new Set([
    "/pages/auth/login.html",
    "/pages/auth/signup.html",
    "/pages/auth/find-id.html",
    "/pages/auth/find-password.html",
    "/pages/auth/oauth-callback.html",
  ]);
  const BOARD_AUTH_POPUP_STYLE_ID = "fooduck-board-auth-popup-style";
  const BOARD_AUTH_POPUP_POLL_MS = 300;
  const BOARD_AUTH_POPUP_LAYOUT_WATCH_MS = 1200;

  function safeBoardAuthNextPath(nextPath) {
    const value = String(nextPath || "").trim();
    return value.startsWith("/") && !value.startsWith("//") ? value : "/";
  }

  function boardAuthPopupFeatures() {
    const screenRef = window.screen || {};
    const availableWidth = Number(screenRef.availWidth) || 1024;
    const availableHeight = Number(screenRef.availHeight) || 900;
    const availableLeft = Number(screenRef.availLeft) || 0;
    const availableTop = Number(screenRef.availTop) || 0;
    const width = Math.max(320, Math.min(540, availableWidth - 32));
    const height = Math.max(360, Math.min(860, availableHeight - 56));
    const left = Math.round(availableLeft + Math.max(0, (availableWidth - width) / 2));
    const top = Math.round(availableTop + Math.max(0, (availableHeight - height) / 2));
    return [
      "popup=yes",
      `width=${Math.round(width)}`,
      `height=${Math.round(height)}`,
      `left=${left}`,
      `top=${top}`,
      "resizable=no",
      "scrollbars=yes",
    ].join(",");
  }

  function createAuthPopupController({
    popupName,
    onAuthenticated = null,
    onClosed = null,
    onBlocked = null,
  } = {}) {
    let popup = null;
    let pollTimer = null;
    let layoutFrame = null;
    let popupDocument = null;
    let storageHandler = null;
    let activeNextPath = "/";

    function stop({ closePopup = false } = {}) {
      if (pollTimer) {
        window.clearInterval(pollTimer);
        pollTimer = null;
      }
      if (layoutFrame) {
        window.cancelAnimationFrame(layoutFrame);
        layoutFrame = null;
      }
      if (storageHandler) {
        window.removeEventListener("storage", storageHandler);
        storageHandler = null;
      }
      if (closePopup && popup && !popup.closed) {
        try {
          popup.close();
        } catch (_error) {
          // 브라우저가 창 제어를 제한하면 열린 창은 그대로 둔다.
        }
      }
      popup = null;
      popupDocument = null;
    }

    function syncAuthPopupLinks(documentRef) {
      if (!documentRef?.querySelectorAll) return;
      documentRef.querySelectorAll('a[href^="/pages/auth/"]').forEach((link) => {
        try {
          const targetUrl = new URL(link.href, window.location.origin);
          if (!BOARD_AUTH_POPUP_PATHS.has(targetUrl.pathname)) return;
          if (!targetUrl.searchParams.has("next")) {
            targetUrl.searchParams.set("next", activeNextPath);
            link.href = `${targetUrl.pathname}${targetUrl.search}${targetUrl.hash}`;
          }
        } catch (_error) {
          // 주소를 해석할 수 없는 링크는 원래 동작을 유지한다.
        }
      });
    }

    function prepareAuthPopupDocument(documentRef) {
      if (!documentRef?.head) return false;

      if (!documentRef.getElementById(BOARD_AUTH_POPUP_STYLE_ID)) {
        const style = documentRef.createElement("style");
        style.id = BOARD_AUTH_POPUP_STYLE_ID;
        style.textContent = `
          .quick-remote,
          .site-header #site-nav,
          .site-header .header-actions {
            display: none !important;
          }
        `;
        documentRef.head.appendChild(style);
      }

      syncAuthPopupLinks(documentRef);
      if (documentRef.readyState === "loading") {
        documentRef.addEventListener("DOMContentLoaded", () => {
          syncAuthPopupLinks(documentRef);
        }, { once: true });
      }
      return true;
    }

    function applyPopupLayout() {
      if (!popup || popup.closed) return "closed";
      try {
        const popupHref = popup.location.href;
        const pathname = popup.location.pathname;
        if (popupHref === "about:blank") return "pending";
        if (!BOARD_AUTH_POPUP_PATHS.has(pathname)) {
          return pathname === "/" || pathname === "" ? "pending" : "other";
        }

        const documentRef = popup.document;
        if (!prepareAuthPopupDocument(documentRef)) return "pending";

        if (popupDocument !== documentRef) {
          popupDocument = documentRef;
          try {
            popup.addEventListener("pagehide", () => {
              const previousDocument = documentRef;
              window.setTimeout(() => {
                if (!popup || popup.closed) return;
                startLayoutWatch(previousDocument);
              }, 0);
            }, { once: true });
          } catch (_error) {
            // 문서가 바뀌는 시점은 아래 주기 확인에서도 다시 보정한다.
          }
        }
        return "applied";
      } catch (_error) {
        // 소셜 로그인처럼 다른 출처의 화면으로 이동한 동안에는 관여하지 않는다.
        return "foreign";
      }
    }

    function startLayoutWatch(previousDocument = null) {
      if (layoutFrame) {
        window.cancelAnimationFrame(layoutFrame);
        layoutFrame = null;
      }
      const startedAt = window.performance?.now?.() ?? Date.now();

      const applyWhenReady = (frameTime) => {
        layoutFrame = null;
        if (!popup || popup.closed) return;
        const now = Number.isFinite(frameTime)
          ? frameTime
          : (window.performance?.now?.() ?? Date.now());
        if (now - startedAt > BOARD_AUTH_POPUP_LAYOUT_WATCH_MS) return;

        if (previousDocument) {
          try {
            if (popup.document === previousDocument) {
              layoutFrame = window.requestAnimationFrame(applyWhenReady);
              return;
            }
          } catch (_error) {
            // 다른 출처로 이동한 경우 빠른 확인은 멈추고 주기 확인에 맡긴다.
            return;
          }
        }

        const status = applyPopupLayout();
        if (status === "applied" || status === "other" || status === "foreign" || status === "closed") {
          return;
        }
        layoutFrame = window.requestAnimationFrame(applyWhenReady);
      };

      applyWhenReady(startedAt);
    }

    function completeIfReady() {
      if (!Api.getToken()) return false;
      stop({ closePopup: true });
      if (typeof onAuthenticated === "function") onAuthenticated();
      return true;
    }

    function open({ nextPath = window.location.pathname + window.location.search } = {}) {
      stop({ closePopup: true });
      activeNextPath = safeBoardAuthNextPath(nextPath);
      const loginUrl = `/pages/auth/login.html?next=${encodeURIComponent(activeNextPath)}`;
      const openedPopup = window.open(
        loginUrl,
        popupName || "fooduck-board-auth-login",
        boardAuthPopupFeatures(),
      );

      if (!openedPopup) {
        if (typeof onBlocked === "function") {
          onBlocked({ loginUrl, nextPath: activeNextPath });
        }
        return false;
      }

      popup = openedPopup;
      startLayoutWatch();
      storageHandler = (event) => {
        if (event.key === "accessToken" && event.newValue) completeIfReady();
      };
      window.addEventListener("storage", storageHandler);
      pollTimer = window.setInterval(() => {
        if (completeIfReady()) return;
        if (!popup || popup.closed) {
          stop();
          if (typeof onClosed === "function") onClosed();
          return;
        }

        const status = applyPopupLayout();
        if (status === "pending" && !layoutFrame) startLayoutWatch();
      }, BOARD_AUTH_POPUP_POLL_MS);

      try {
        popup.focus();
      } catch (_error) {
        // 포커스를 옮길 수 없어도 로그인 창 자체는 그대로 사용한다.
      }
      return true;
    }

    return {
      completeIfReady,
      open,
      stop,
    };
  }

  function mapHref(restaurant) {
    const params = new URLSearchParams({
      q: restaurant?.name || "맛집",
    });
    return `/pages/map/index.html?${params.toString()}`;
  }

  function readPostId() {
    const value = Number(new URLSearchParams(window.location.search).get("postId"));
    return Number.isSafeInteger(value) && value > 0 ? value : null;
  }

  function showToast(host, message, isError = false) {
    if (!host) return;
    host.textContent = message;
    host.classList.toggle("is-error", isError);
    host.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
      host.hidden = true;
    }, 3200);
  }

  window.FooduckBoard = {
    authorIdentity,
    canUseBusinessBoard,
    categoryLabel,
    createAuthPopupController,
    detailPath,
    element,
    formatDate,
    icon,
    invalidateBoardCache,
    listPath,
    mapHref,
    readBoardCache,
    readPostId,
    requireLogin,
    roleLabel,
    showToast,
    updateCachedPostViewCount,
    writeBoardCache,
    writePath,
  };
})();
