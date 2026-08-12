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

  function closeAuthorMenu() {
    if (!authorMenu || authorMenu.hidden) return;
    authorMenu.hidden = true;
    authorMenu.replaceChildren();
    if (activeAuthorTrigger) {
      activeAuthorTrigger.setAttribute("aria-expanded", "false");
    }
    activeAuthorTrigger = null;
  }

  function ensureAuthorMenu() {
    if (authorMenu) return authorMenu;

    authorMenu = element("section", "author-menu");
    authorMenu.id = "board-author-menu";
    authorMenu.hidden = true;
    authorMenu.setAttribute("role", "dialog");
    authorMenu.setAttribute("aria-label", "작성자 프로필");
    document.body.append(authorMenu);

    document.addEventListener("click", (event) => {
      if (authorMenu.hidden) return;
      if (authorMenu.contains(event.target)) return;
      if (activeAuthorTrigger?.contains(event.target)) return;
      closeAuthorMenu();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeAuthorMenu();
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
    menu.replaceChildren(
      element("strong", "author-menu-nickname", author.authorNickname || "작성자"),
      element("p", "author-menu-loading", "프로필을 불러오는 중입니다."),
    );
  }

  function authorMenuRecentPostSection(posts) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", "이전글"));
    const items = Array.isArray(posts) ? posts : [];
    if (items.length === 0) {
      section.append(element("p", "author-menu-recent-empty", "표시할 이전글이 없습니다."));
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
    section.append(element("strong", "author-menu-recent-title", "이전댓글"));
    const items = Array.isArray(comments) ? comments : [];
    if (items.length === 0) {
      section.append(element("p", "author-menu-recent-empty", "표시할 이전댓글이 없습니다."));
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
    section.append(element("strong", "author-menu-recent-title", "리뷰"));
    const items = Array.isArray(reviews) ? reviews : [];
    if (items.length === 0) {
      section.append(element("p", "author-menu-recent-empty", "표시할 리뷰가 없습니다."));
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
    button.dataset.authorActivityTab = key;
    button.setAttribute("role", "tab");
    button.setAttribute("aria-selected", active ? "true" : "false");
    button.append(
      element("span", "author-menu-tab-label", label),
      element("strong", "author-menu-tab-count", String(count || 0)),
    );
    return button;
  }

  function renderAuthorMenuSummary(summary) {
    const menu = ensureAuthorMenu();
    const header = element("header", "author-menu-header");
    const identity = element("div", "author-menu-identity");
    identity.append(
      element("strong", "author-menu-nickname", summary.nickname || "작성자"),
      element(
        "span",
        "author-menu-account-id",
        `(${summary.accountLabel || "계정 정보 없음"})`,
      ),
    );
    header.append(
      identity,
      element("span", "author-menu-caption", "공개 활동"),
    );

    const lastActivity = element("p", "author-menu-last-activity");
    lastActivity.append(
      element("span", "author-menu-last-activity-label", "마지막 공개 활동"),
      element(
        "strong",
        "author-menu-last-activity-time",
        summary.lastPublicActivityAt
          ? `${formatDate(summary.lastPublicActivityAt)} 활동함`
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
    activityPanel.setAttribute("role", "tabpanel");
    const sections = {
      posts: authorMenuRecentPostSection(summary.recentPosts),
      comments: authorMenuRecentCommentSection(summary.recentComments),
      reviews: authorMenuRecentReviewSection(summary.recentReviews),
    };
    activityPanel.replaceChildren(sections.posts);

    tabs.addEventListener("click", (event) => {
      const button = event.target.closest("[data-author-activity-tab]");
      if (!button || !tabs.contains(button)) return;
      const key = button.dataset.authorActivityTab;
      if (!sections[key]) return;
      tabs.querySelectorAll("[data-author-activity-tab]").forEach((tab) => {
        const active = tab === button;
        tab.classList.toggle("is-active", active);
        tab.setAttribute("aria-selected", active ? "true" : "false");
      });
      activityPanel.replaceChildren(sections[key]);
      positionAuthorMenu(activeAuthorTrigger);
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
  }

  function renderAuthorMenuError(message) {
    const menu = ensureAuthorMenu();
    menu.replaceChildren(
      element("strong", "author-menu-nickname", "작성자 프로필"),
      element("p", "author-menu-error", message || "프로필 정보를 불러오지 못했습니다."),
    );
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

    const accountId = Number(author.authorAccountId);
    try {
      const params = new URLSearchParams();
      const excludePostId = Number(author.postId);
      if (Number.isSafeInteger(excludePostId) && excludePostId > 0) {
        params.set("excludePostId", String(excludePostId));
      }
      const query = params.size > 0 ? `?${params.toString()}` : "";
      const payload = await Api.get(
        `/board/posts/authors/${encodeURIComponent(accountId)}/summary${query}`,
      );
      const summary = payload.data;
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuSummary(summary);
      positionAuthorMenu(trigger);
    } catch (error) {
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuError(error.message);
      positionAuthorMenu(trigger);
    }
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
