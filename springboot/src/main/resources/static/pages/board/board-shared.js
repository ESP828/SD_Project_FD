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
    authorMenu.setAttribute("aria-label", "작성자 활동 메뉴");
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

  function authorMenuStat(label, count) {
    const item = element("div", "author-menu-stat");
    item.append(
      element("span", "", label),
      element("strong", "", String(count || 0)),
    );
    return item;
  }

  function authorMenuShortcut(label, href) {
    const link = element("a", "author-menu-shortcut", label);
    link.href = href;
    return link;
  }

  function renderAuthorMenuLoading(author, context = "COMMUNITY") {
    const menu = ensureAuthorMenu();
    menu.replaceChildren(
      element("strong", "author-menu-nickname", author.authorNickname || "작성자"),
      element(
        "p",
        "author-menu-loading",
        context === "NEWS"
          ? "커뮤니티와 가게 소식, 리뷰 활동을 불러오는 중입니다."
          : "작성자의 공개 활동을 불러오는 중입니다.",
      ),
    );
  }

  function authorMenuRecentPostSection(title, posts, { newsActivity = false } = {}) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", title));
    const items = Array.isArray(posts) ? posts : [];
    if (items.length === 0) {
      section.append(
        element(
          "p",
          "author-menu-recent-empty",
          newsActivity
            ? "표시할 이전 가게 소식이 없습니다."
            : "표시할 이전 게시글이 없습니다.",
        ),
      );
      return section;
    }

    const list = element("ul", "author-menu-recent-list");
    items.forEach((post) => {
      const item = element("li", "author-menu-recent-item");
      const link = element("a", "author-menu-recent-link");
      link.href = newsActivity
        ? `${detailPath(post.postId)}&from=NEWS`
        : detailPath(post.postId);
      link.append(
        element("span", "author-menu-recent-post-title", post.title || "제목 없음"),
        element(
          "small",
          "author-menu-recent-meta",
          newsActivity
            ? `가게 소식 · 커뮤니티 외 · ${formatDate(post.createdAt)}`
            : `${categoryLabel(post.category)} · ${formatDate(post.createdAt)}`,
        ),
      );
      item.append(link);
      list.append(item);
    });
    section.append(list);
    return section;
  }

  function authorMenuRecentCommentSection(title, comments, { newsActivity = false } = {}) {
    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", title));
    const items = Array.isArray(comments) ? comments : [];
    if (items.length === 0) {
      section.append(
        element(
          "p",
          "author-menu-recent-empty",
          newsActivity
            ? "표시할 이전 가게 소식 댓글이 없습니다."
            : "표시할 이전 댓글이 없습니다.",
        ),
      );
      return section;
    }

    const list = element("ul", "author-menu-recent-list");
    items.forEach((comment) => {
      const item = element("li", "author-menu-recent-item");
      const link = element("a", "author-menu-recent-link");
      link.href = newsActivity
        ? `${detailPath(comment.postId)}&from=NEWS`
        : detailPath(comment.postId);
      link.append(
        element(
          "span",
          "author-menu-recent-content",
          comment.content || "댓글 내용 없음",
        ),
        element(
          "small",
          "author-menu-recent-meta",
          newsActivity
            ? `가게 소식 · ${comment.postTitle || "소식"} · ${formatDate(comment.createdAt)}`
            : `${comment.postTitle || "게시글"} · ${formatDate(comment.createdAt)}`,
        ),
      );
      item.append(link);
      list.append(item);
    });
    section.append(list);
    return section;
  }

  function authorMenuReviewPath(review) {
    const publicRestaurantId = Number(review?.publicRestaurantId);
    if (Number.isSafeInteger(publicRestaurantId) && publicRestaurantId > 0) {
      return `/pages/restaurant/detail.html?source=public&id=${encodeURIComponent(publicRestaurantId)}&tab=review`;
    }
    const restaurantId = Number(review?.restaurantId);
    if (Number.isSafeInteger(restaurantId) && restaurantId > 0) {
      return `/pages/restaurant/detail.html?source=owned&id=${encodeURIComponent(restaurantId)}&tab=review`;
    }
    return null;
  }

  function authorMenuReviewSection(summary) {
    const group = element("section", "author-menu-review-group");
    const heading = element("div", "author-menu-group-heading author-menu-group-heading--review");
    heading.append(
      element("strong", "author-menu-group-title", "리뷰 활동"),
      element(
        "small",
        "author-menu-group-copy",
        "음식점 상세에 공개되어 있는 이 작성자의 리뷰입니다.",
      ),
    );

    const stats = element("div", "author-menu-stats author-menu-stats--review");
    stats.append(authorMenuStat("작성한 리뷰", summary.reviewCount));

    const section = element("section", "author-menu-recent");
    section.append(element("strong", "author-menu-recent-title", "최근 리뷰"));
    const reviews = Array.isArray(summary.recentReviews) ? summary.recentReviews : [];
    if (reviews.length === 0) {
      section.append(
        element("p", "author-menu-recent-empty", "표시할 공개 리뷰가 없습니다."),
      );
    } else {
      const list = element("ul", "author-menu-recent-list");
      reviews.forEach((review) => {
        const item = element("li", "author-menu-recent-item");
        const href = authorMenuReviewPath(review);
        const body = href
          ? element("a", "author-menu-recent-link")
          : element("div", "author-menu-recent-link author-menu-recent-link--static");
        if (href) body.href = href;

        const rating = Math.max(0, Math.min(5, Number(review.rating) || 0));
        body.append(
          element(
            "span",
            "author-menu-review-restaurant",
            review.restaurantName || "음식점",
          ),
          element(
            "span",
            "author-menu-review-rating",
            `${"★".repeat(rating)}${"☆".repeat(5 - rating)} · ${rating}점`,
          ),
          element(
            "span",
            "author-menu-review-content",
            review.content || "작성한 리뷰 내용이 없습니다.",
          ),
          element(
            "small",
            "author-menu-recent-meta",
            `공개 리뷰 · ${formatDate(review.createdAt)}`,
          ),
        );
        item.append(body);
        list.append(item);
      });
      section.append(list);
    }

    group.append(heading, stats, section);
    return group;
  }

  function renderAuthorMenuSummary(summary, context = "COMMUNITY") {
    const menu = ensureAuthorMenu();
    const newsContext = context === "NEWS";
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
      element(
        "span",
        "author-menu-caption",
        newsContext ? "가게 소식 작성자" : "커뮤니티 활동",
      ),
    );

    const contextNote = newsContext
      ? element("div", "author-menu-context-note")
      : null;
    if (contextNote) {
      contextNote.append(
        element(
          "strong",
          "author-menu-context-title",
          "현재 보고 있는 항목은 커뮤니티 게시글이 아닌 가게 소식입니다.",
        ),
        element(
          "p",
          "author-menu-context-copy",
          "아래에서 커뮤니티 활동, 가게 소식 활동, 공개 리뷰를 구분해 표시합니다.",
        ),
      );
    }

    const stats = element("div", "author-menu-stats");
    stats.append(
      authorMenuStat(newsContext ? "커뮤니티 게시글" : "작성한 게시글", summary.postCount),
      authorMenuStat(newsContext ? "커뮤니티 댓글" : "작성한 댓글", summary.commentCount),
    );

    const communityHeading = newsContext
      ? element("div", "author-menu-group-heading")
      : null;
    if (communityHeading) {
      communityHeading.append(
        element("strong", "author-menu-group-title", "커뮤니티 활동"),
        element(
          "small",
          "author-menu-group-copy",
          "현재 계정에서 열람 가능한 커뮤니티에 남긴 기록입니다.",
        ),
      );
    }

    const recentSection = authorMenuRecentPostSection(
      newsContext ? "커뮤니티 이전글" : "이전 작성글",
      summary.recentPosts,
    );
    const recentCommentSection = authorMenuRecentCommentSection(
      newsContext ? "커뮤니티 이전댓글" : "이전 댓글",
      summary.recentComments,
    );

    const newsGroup = newsContext
      ? element("section", "author-menu-news-group")
      : null;
    if (newsGroup) {
      const heading = element("div", "author-menu-group-heading author-menu-group-heading--news");
      heading.append(
        element("strong", "author-menu-group-title", "가게 소식 활동"),
        element(
          "small",
          "author-menu-group-copy",
          "커뮤니티 목록에는 노출되지 않는 가게 소식 글·댓글입니다.",
        ),
      );
      const newsStats = element("div", "author-menu-stats author-menu-stats--news");
      newsStats.append(
        authorMenuStat("가게 소식 글", summary.newsPostCount),
        authorMenuStat("가게 소식 댓글", summary.newsCommentCount),
      );
      newsGroup.append(
        heading,
        newsStats,
        authorMenuRecentPostSection(
          "가게 소식 이전글",
          summary.recentNewsPosts,
          { newsActivity: true },
        ),
        authorMenuRecentCommentSection(
          "가게 소식 이전댓글",
          summary.recentNewsComments,
          { newsActivity: true },
        ),
      );
    }

    const reviewGroup = authorMenuReviewSection(summary);

    const footer = element("div", "author-menu-footer");
    const isCurrentUser = Number(window.FooduckSession?.accountId)
      === Number(summary.accountId);
    if (isCurrentUser) {
      const privateHeading = element("div", "author-menu-private-heading");
      privateHeading.append(
        element("strong", "author-menu-private-title", "내 마이페이지"),
        element(
          "small",
          "author-menu-private-copy",
          "본인 계정에서만 열 수 있는 개인 활동 바로가기입니다.",
        ),
      );

      const myPageLink = element("a", "author-menu-link", "마이페이지 홈");
      myPageLink.href = "/pages/mypage/index.html";

      const shortcuts = element("nav", "author-menu-shortcuts");
      shortcuts.setAttribute("aria-label", "마이페이지 바로가기");
      shortcuts.append(
        authorMenuShortcut("찜한 가게", "/pages/mypage/detail.html?tab=favorites"),
        authorMenuShortcut("내 리뷰", "/pages/mypage/detail.html?tab=reviews"),
        authorMenuShortcut("알림", "/pages/mypage/detail.html?tab=notifications"),
      );
      footer.append(privateHeading, myPageLink, shortcuts);
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

    const children = [header];
    if (contextNote) children.push(contextNote);
    if (communityHeading) children.push(communityHeading);
    children.push(stats, recentSection, recentCommentSection);
    if (newsGroup) children.push(newsGroup);
    children.push(reviewGroup, footer);
    menu.replaceChildren(...children);
  }

  function renderAuthorMenuError(message) {
    const menu = ensureAuthorMenu();
    menu.replaceChildren(
      element("strong", "author-menu-nickname", "작성자 정보"),
      element("p", "author-menu-error", message || "활동 정보를 불러오지 못했습니다."),
    );
  }

  async function openAuthorMenu(author, trigger, event, context = "COMMUNITY") {
    event.preventDefault();
    event.stopPropagation();
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
    renderAuthorMenuLoading(author, context);
    menu.hidden = false;
    positionAuthorMenu(trigger);

    const accountId = Number(author.authorAccountId);
    try {
      const params = new URLSearchParams();
      const excludePostId = Number(author.postId);
      if (Number.isSafeInteger(excludePostId) && excludePostId > 0) {
        params.set("excludePostId", String(excludePostId));
      }
      if (context === "NEWS") {
        params.set("includeNewsActivity", "true");
      }
      const query = params.size > 0 ? `?${params.toString()}` : "";
      const payload = await Api.get(
        `/board/posts/authors/${encodeURIComponent(accountId)}/summary${query}`,
      );
      const summary = payload.data;
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuSummary(summary, context);
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
    trigger.addEventListener("click", (event) => {
      openAuthorMenu(author, trigger, event, context);
    });
    trigger.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      openAuthorMenu(author, trigger, event, context);
    });
  }

  function authorIdentity(
    author,
    {
      showNickname = true,
      showAuthorMenu = false,
      authorMenuContext = "COMMUNITY",
    } = {},
  ) {
    const wrapper = element("span", "author-identity");
    if (!author?.authorLoginId) {
      wrapper.append(element("strong", "author-login-id", "소셜 계정"));
    }
    if (showNickname && author?.authorNickname) {
      const nickname = element("span", "author-nickname", author.authorNickname);
      if (showAuthorMenu && author.authorAccountId) {
        enableAuthorMenu(
          nickname,
          author,
          authorMenuContext === "NEWS" ? "NEWS" : "COMMUNITY",
        );
      }
      wrapper.append(nickname);
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
