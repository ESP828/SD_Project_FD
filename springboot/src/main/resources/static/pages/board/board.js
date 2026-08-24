(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const emojis = window.FooduckEmojis;
  const initialParams = new URLSearchParams(window.location.search);
  const requestedValue = initialParams.get("boardType");
  const requestedBoardType = ["BUSINESS", "BEST", "POPULAR"].includes(requestedValue)
    ? requestedValue
    : "GENERAL";
  const requestedCategory = [
    "GENERAL",
    "RECOMMENDATION",
    "REVIEW",
    "QUESTION",
    "TRAVEL",
  ].includes(initialParams.get("category"))
    ? initialParams.get("category")
    : "";
  const requestedSort = ["LATEST", "LIKES", "COMMENTS"].includes(initialParams.get("sort"))
    ? initialParams.get("sort")
    : "LATEST";
  const requestedKeyword = String(initialParams.get("keyword") || "").trim().slice(0, 100);
  const requestedPage = Math.max(
    0,
    Math.min(9999, (Number.parseInt(initialParams.get("page"), 10) || 1) - 1),
  );
  const initialBoardType = requestedBoardType === "BUSINESS"
    ? "GENERAL"
    : requestedBoardType;
  let businessAccessAllowed = Boolean(session?.canManageBusiness);
  const POPULAR_PAGE_SIZE = 20;
  const state = {
    boardType: initialBoardType,
    lastBoardType: initialBoardType === "BUSINESS" ? "BUSINESS" : "GENERAL",
    category: requestedCategory,
    keyword: requestedKeyword,
    sort: requestedSort,
    page: requestedPage,
    size: 7,
  };

  const boardList = document.getElementById("board-list");
  const totalCount = document.getElementById("board-total-count");
  const totalSummary = document.getElementById("board-total-summary");
  const boardListCriteria = document.getElementById("board-list-criteria");
  const popularRankRange = document.getElementById("popular-rank-range");
  const pagination = document.getElementById("board-pagination");
  const bestPostPanel = document.getElementById("best-post-panel");
  const bestPostList = document.getElementById("best-post-list");
  const unansweredPostList = document.getElementById("unanswered-post-list");
  const boardHeading = document.getElementById("board-heading");
  const boardTabPanel = document.getElementById("board-tabpanel");
  const businessTab = document.getElementById("business-board-tab");
  const searchForm = document.getElementById("board-search-form");
  const categorySelect = document.getElementById("board-category");
  const keywordInput = document.getElementById("board-keyword");
  const sortSelect = document.getElementById("board-sort");
  const resetButton = document.getElementById("board-reset-button");
  const writeLinks = Array.from(document.querySelectorAll("[data-write-link]"));
  let postRequestGeneration = 0;
  let bestRequestGeneration = 0;
  let unansweredRequestGeneration = 0;
  let boardAuthPopupController = null;
  let pendingBoardLoginAction = null;
  let boardLogoutInFlight = false;
  let cachedFallbackNoticeShown = false;

  if (!session || !board || !boardList || !searchForm) {
    return;
  }

  const {
    authorIdentity,
    categoryLabel,
    createAuthPopupController,
    detailPath,
    element,
    formatDate,
    icon,
    readBoardCache,
    showToast,
    writeBoardCache,
  } = board;

  function emojiTextElement(tagName, className, value) {
    const node = element(tagName, className, "");
    if (emojis) emojis.renderText(node, value);
    else node.textContent = String(value ?? "");
    return node;
  }

  function isEdited(item) {
    return item?.edited === true;
  }

  function canUseCacheAfterError(error) {
    return ![401, 403, 404].includes(Number(error?.status));
  }

  const boardToast = document.createElement("div");
  boardToast.className = "board-toast";
  boardToast.setAttribute("role", "status");
  boardToast.setAttribute("aria-live", "polite");
  boardToast.hidden = true;
  document.body.append(boardToast);

  function consumeBoardFlashMessage() {
    board.consumeFeedbackFlash?.(boardToast);
  }

  function showCachedFallbackNoticeOnce() {
    if (cachedFallbackNoticeShown) return;
    cachedFallbackNoticeShown = true;
    showToast(
      boardToast,
      "최신 내용을 불러오지 못해 잠시 저장된 내용을 보여드리고 있습니다.",
    );
  }

  function renderCachedContentNotice() {
    cachedFallbackNoticeShown = true;
    if (boardList.querySelector(".board-cache-notice")) return;
    const notice = element(
      "div",
      "board-cache-notice",
      "최신 내용을 불러오지 못해 잠시 저장된 게시글을 보여드리고 있습니다.",
    );
    notice.setAttribute("role", "status");
    boardList.prepend(notice);
  }

  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function syncSearchControls() {
    if (categorySelect) categorySelect.value = state.category;
    if (keywordInput) keywordInput.value = state.keyword;
    if (sortSelect) sortSelect.value = state.sort;
  }

  function listUrlFromState() {
    const params = new URLSearchParams();
    if (state.boardType !== "GENERAL") params.set("boardType", state.boardType);

    if (["GENERAL", "BUSINESS"].includes(state.boardType)) {
      if (state.category) params.set("category", state.category);
      if (state.keyword) params.set("keyword", state.keyword);
      if (state.sort !== "LATEST") params.set("sort", state.sort);
    }

    if (state.page > 0) {
      params.set("page", String(state.page + 1));
    }

    const query = params.toString();
    return `/board${query ? `?${query}` : ""}`;
  }

  function writeHrefFromCurrentList(boardType = state.lastBoardType) {
    const url = new URL(board.writePath(boardType), window.location.origin);
    url.searchParams.set("returnTo", listUrlFromState());
    return `${url.pathname}${url.search}`;
  }

  function syncListUrl(historyMode = "replace") {
    if (historyMode === "none") return;
    const nextUrl = listUrlFromState();
    const currentUrl = `${window.location.pathname}${window.location.search}`;
    if (nextUrl === currentUrl) return;
    if (historyMode === "push") {
      window.history.pushState(null, "", nextUrl);
      return;
    }
    window.history.replaceState(null, "", nextUrl);
  }

  function detailHrefFromCurrentList(postId, sourceView = null) {
    const params = new URLSearchParams({ postId: String(postId) });
    if (sourceView) params.set("from", sourceView);
    params.set("returnTo", listUrlFromState());
    return `/board/detail?${params.toString()}`;
  }

  function ensureBoardAuthPopupController() {
    if (boardAuthPopupController) return boardAuthPopupController;
    boardAuthPopupController = createAuthPopupController({
      popupName: "fooduck-board-list-login",
      onAuthenticated: () => {
        const action = pendingBoardLoginAction;
        pendingBoardLoginAction = null;
        if (typeof action === "function") window.setTimeout(action, 0);
      },
      onClosed: () => {
        pendingBoardLoginAction = null;
      },
      onBlocked: ({ loginUrl }) => {
        pendingBoardLoginAction = null;
        // 팝업이 막힌 환경에서는 현재 창의 일반 로그인 화면으로 이어간다.
        window.location.assign(loginUrl);
      },
    });
    return boardAuthPopupController;
  }

  function openBoardLogin({ nextPath = listUrlFromState(), onSuccess = null } = {}) {
    pendingBoardLoginAction = typeof onSuccess === "function" ? onSuccess : null;
    return ensureBoardAuthPopupController().open({ nextPath });
  }

  function initializeBoardAuthEntryPoints() {
    document.addEventListener("click", (event) => {
      const loginLink = event.target.closest(
        '.site-header a.header-auth-button[href^="/auth/login"]',
      );
      if (!loginLink || session.authenticated) return;
      event.preventDefault();
      const nextPath = listUrlFromState();
      window.location.assign(`/auth/login?next=${encodeURIComponent(nextPath)}`);
    });

    document.addEventListener("click", async (event) => {
      const logoutButton = event.target.closest(".site-header [data-logout]");
      if (!logoutButton || !session.authenticated) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      if (boardLogoutInFlight) return;
      boardLogoutInFlight = true;
      logoutButton.disabled = true;
      try {
        await Api.logout();
      } catch (_error) {
        Api.clearToken();
      } finally {
        window.location.reload();
      }
    }, true);
  }

  function renderLoading() {
    boardList.replaceChildren();
    const loading = element("div", "board-loading");
    loading.append(icon("progress_activity"), "게시글을 불러오고 있습니다.");
    boardList.append(loading);
  }

  function renderListError(error) {
    boardList.replaceChildren();
    const wrapper = element("div", "board-error");
    const image = new Image();
    image.src = "/images/characters/error.png";
    image.alt = "";
    wrapper.append(
      image,
      element("strong", "", "게시글을 불러오지 못했습니다."),
      element("span", "", error.message || "잠시 후 다시 시도해 주세요."),
    );
    const retry = element("button", "button button-sm button-secondary", "다시 시도");
    retry.type = "button";
    retry.addEventListener("click", loadPosts);
    wrapper.append(retry);
    boardList.append(wrapper);
  }

  function createPostRow(post, index = 0) {
    const isNotice = post.pinned === true || post.category === "NOTICE";
    const article = element("a", isNotice ? "post-row post-row--notice" : "post-row");
    article.href = detailHrefFromCurrentList(
      post.postId,
      state.boardType === "BEST"
        ? "BEST"
        : state.boardType === "POPULAR"
          ? "POPULAR"
          : null,
    );
    article.setAttribute("aria-label", `${post.title} 상세 보기`);

    const main = element("div", "post-row-main");
    const badges = element("div", "post-badge-row");
    if (state.boardType === "BEST" || state.boardType === "POPULAR") {
      const rank = state.boardType === "POPULAR"
        ? state.page * POPULAR_PAGE_SIZE + index + 1
        : state.page * state.size + index + 1;
      const rankBadge = element(
        "span",
        "post-badge",
        state.boardType === "POPULAR" ? `#${rank}` : `베스트 ${rank}위`,
      );
      if (state.boardType === "POPULAR") {
        rankBadge.setAttribute("aria-label", `인기 ${rank}위`);
        rankBadge.title = `인기 ${rank}위`;
      }
      badges.append(rankBadge);
    }
    if (isNotice) {
      badges.append(element("span", "post-badge post-badge--notice", "공지 · 상단 고정"));
    }
    if (post.category !== "NOTICE") {
      const categoryClass = String(post.category || "GENERAL").toLowerCase();
      badges.append(
        element(
          "span",
          `post-badge post-badge--category post-badge--category-${categoryClass}`,
          categoryLabel(post.category),
        ),
      );
    }
    if (["BEST", "POPULAR"].includes(state.boardType) || post.boardType === "BUSINESS") {
      badges.append(
        element(
          "span",
          "post-board-badge",
          post.boardType === "BUSINESS" ? "사업자 커뮤니티" : "일반 커뮤니티",
        ),
      );
    }
    main.append(
      badges,
      element("h3", "", post.title),
      emojiTextElement("p", "post-preview", post.contentPreview || "내용 미리보기 없음"),
    );

    const meta = element("div", "post-meta");
    meta.append(
      authorIdentity(post, { showAuthorMenu: true }),
      element(
        "span",
        "",
        `${
          ["BEST", "POPULAR"].includes(state.boardType)
            ? formatWaitingDate(post.createdAt)
            : formatDate(post.createdAt)
        }${isEdited(post) ? " · 수정됨" : ""}`,
      ),
    );
    if (post.restaurant?.name) {
      meta.append(element("span", "", `관련 맛집 · ${post.restaurant.name}`));
    }
    main.append(meta);

    const stats = element("div", "post-stats");
    const views = element("span");
    views.append(icon("visibility"), String(post.viewCount || 0));
    const comments = element("span");
    comments.append(icon("forum"), String(post.commentCount || 0));
    const likes = element("span");
    likes.append(icon("thumb_up"), String(post.likeCount || 0));
    stats.append(views, comments, likes);

    article.append(main, stats);
    return article;
  }

  function syncListRankingMeta(pageData, posts = []) {
    const isPopular = state.boardType === "POPULAR";

    if (totalSummary) {
      totalSummary.textContent = "개의 이야기";
    }

    if (boardListCriteria) {
      if (isPopular) {
        boardListCriteria.textContent = "기준 : 최근 30일, 추천순";
        boardListCriteria.hidden = false;
      } else if (state.boardType === "BEST") {
        boardListCriteria.textContent = "기준 : 추천 3개 이상";
        boardListCriteria.hidden = false;
      } else {
        boardListCriteria.textContent = "";
        boardListCriteria.hidden = true;
      }
    }

    if (!popularRankRange) return;

    if (!isPopular || !posts.length) {
      popularRankRange.hidden = true;
      popularRankRange.textContent = "";
      return;
    }

    const pageNumber = Math.max(0, Number(pageData?.page) || state.page || 0);
    const pageSize = Math.max(1, Number(pageData?.size) || POPULAR_PAGE_SIZE);
    const startRank = pageNumber * pageSize + 1;
    const endRank = startRank + posts.length - 1;

    popularRankRange.textContent = `${startRank}–${endRank}위`;
    popularRankRange.hidden = false;
  }

  function renderPosts(pageData) {
    const totalElements = Math.max(0, Number(pageData.totalElements) || 0);
    totalCount.textContent = String(totalElements);
    boardList.replaceChildren();
    const posts = pageData.content || [];
    syncListRankingMeta(pageData, posts);
    if (!posts.length) {
      const empty = element("div", "board-empty");
      const image = new Image();
      image.src = "/images/characters/laptop.png";
      image.alt = "";
      const hasSearchCondition = ["GENERAL", "BUSINESS"].includes(state.boardType)
        && Boolean(state.category || state.keyword);
      empty.append(
        image,
        element(
          "strong",
          "",
          state.boardType === "BEST"
            ? "아직 베스트 게시글이 없습니다."
            : state.boardType === "POPULAR"
              ? "아직 인기 이야기가 없습니다."
              : hasSearchCondition
                ? "검색 결과가 없습니다."
                : "아직 등록된 이야기가 없습니다.",
        ),
        element(
          "span",
          "",
          state.boardType === "BEST"
            ? "추천을 3개 이상 받은 글이 여기에 표시됩니다."
            : state.boardType === "POPULAR"
              ? "최근 30일 동안 추천을 많이 받은 이야기가 여기에 표시됩니다."
              : hasSearchCondition
                ? "검색어나 카테고리를 바꿔 다시 찾아보세요."
                : "첫 번째 맛집 이야기를 함께 나눠 보세요.",
        ),
      );
      if (hasSearchCondition) {
        const clearButton = element(
          "button",
          "button button-sm button-secondary",
          "검색 조건 초기화",
        );
        clearButton.type = "button";
        clearButton.addEventListener("click", () => resetSearchConditions("push"));
        empty.append(clearButton);
      }
      boardList.append(empty);
    } else {
      posts.forEach((post, index) => boardList.append(createPostRow(post, index)));
    }
    renderPagination(pageData);
    window.FooduckIcons?.enhance(boardList);
  }

  function renderPagination(pageData) {
    pagination.replaceChildren();
    if (!pageData.totalPages || pageData.totalPages <= 1) {
      return;
    }

    const previous = element("button", "page-button", "‹");
    previous.type = "button";
    previous.disabled = pageData.first;
    previous.setAttribute("aria-label", "이전 페이지");
    previous.addEventListener("click", () => changePage(state.page - 1));
    pagination.append(previous);

    const start = Math.max(0, Math.min(state.page - 2, pageData.totalPages - 5));
    const end = Math.min(pageData.totalPages, start + 5);
    for (let page = start; page < end; page += 1) {
      const button = element("button", "page-button", String(page + 1));
      button.type = "button";
      button.classList.toggle("is-active", page === state.page);
      if (page === state.page) button.setAttribute("aria-current", "page");
      button.addEventListener("click", () => changePage(page));
      pagination.append(button);
    }

    const next = element("button", "page-button", "›");
    next.type = "button";
    next.disabled = pageData.last;
    next.setAttribute("aria-label", "다음 페이지");
    next.addEventListener("click", () => changePage(state.page + 1));
    pagination.append(next);
  }

  function changePage(page) {
    if (page < 0) return;
    state.page = page;
    syncListUrl("push");
    loadPosts();
    document.querySelector(".board-content")?.scrollIntoView({
      behavior: prefersReducedMotion() ? "auto" : "smooth",
      block: "start",
    });
  }

  function normalizePostPage(data) {
    return data || {};
  }

  function correctOutOfRangePage(pageData) {
    const totalPages = Math.max(0, Number(pageData?.totalPages) || 0);
    const correctedPage = totalPages > 0
      ? Math.min(state.page, totalPages - 1)
      : 0;
    if (correctedPage === state.page) return false;
    state.page = correctedPage;
    syncListUrl("replace");
    loadPosts();
    return true;
  }

  async function loadPosts(options = {}) {
    const forceRefresh = options.forceRefresh === true;
    const generation = ++postRequestGeneration;
    const isBest = state.boardType === "BEST";
    const isPopular = state.boardType === "POPULAR";
    const params = new URLSearchParams();

    if (isPopular) {
      params.set("page", String(state.page));
      params.set("size", String(POPULAR_PAGE_SIZE));
    } else {
      params.set("page", String(state.page));
      params.set("size", String(state.size));
      if (!isBest) {
        params.set("boardType", state.boardType);
        params.set("sort", state.sort);
        if (state.category) params.set("category", state.category);
        if (state.keyword) params.set("keyword", state.keyword);
      }
    }

    const path = isPopular
      ? `/board/posts/popular?${params.toString()}`
      : isBest
        ? `/board/posts/best/community?${params.toString()}`
        : `/board/posts?${params.toString()}`;
    const cached = forceRefresh ? null : readBoardCache(path);
    if (cached) {
      if (generation !== postRequestGeneration) return;
      const cachedPage = normalizePostPage(cached.data);
      if (correctOutOfRangePage(cachedPage)) return;
      renderPosts(cachedPage);
      if (cached.fresh) return;
    } else {
      if (generation !== postRequestGeneration) return;
      renderLoading();
    }

    try {
      const payload = await Api.get(path, { cache: "no-store" });
      const data = payload.data || {};
      writeBoardCache(path, data);
      if (generation !== postRequestGeneration) return;
      const pageData = normalizePostPage(data);
      if (correctOutOfRangePage(pageData)) return;
      renderPosts(pageData);
    } catch (error) {
      if (generation !== postRequestGeneration) return;
      if (!cached || !canUseCacheAfterError(error)) {
        renderListError(error);
      } else {
        renderCachedContentNotice();
      }
    }
  }

  function renderBestPosts(posts) {
    bestPostList.replaceChildren();
    if (!posts.length) {
      bestPostList.append(element("li", "best-loading", "인기 게시글이 없습니다."));
      return;
    }
    posts.forEach((post, index) => {
      const item = element("li");
      const link = element("a");
      link.href = detailHrefFromCurrentList(post.postId);
      link.append(element("span", "best-rank", String(index + 1)));
      const copy = element("span", "best-copy");
      copy.append(
        element("strong", "", post.title),
        element(
          "small",
          "",
          `추천 ${post.likeCount || 0} · 댓글 ${post.commentCount || 0}`,
        ),
      );
      link.append(copy);
      item.append(link);
      bestPostList.append(item);
    });
  }

  async function loadBestPosts(options = {}) {
    const forceRefresh = options.forceRefresh === true;
    const generation = ++bestRequestGeneration;
    const isRankedView = ["BEST", "POPULAR"].includes(state.boardType);
    if (bestPostPanel) bestPostPanel.hidden = isRankedView;
    if (isRankedView) return;
    const params = new URLSearchParams({
      boardType: state.boardType,
      size: "3",
    });
    const path = `/board/posts/best?${params.toString()}`;
    const cached = forceRefresh ? null : readBoardCache(path);
    if (cached) {
      if (generation !== bestRequestGeneration) return;
      renderBestPosts(cached.data || []);
      if (cached.fresh) return;
    } else {
      if (generation !== bestRequestGeneration) return;
      bestPostList.replaceChildren(element("li", "best-loading", "불러오는 중"));
    }

    try {
      const payload = await Api.get(path, { cache: "no-store" });
      const posts = payload.data || [];
      writeBoardCache(path, posts);
      if (generation !== bestRequestGeneration) return;
      renderBestPosts(posts);
    } catch (error) {
      if (generation !== bestRequestGeneration) return;
      if (!cached || !canUseCacheAfterError(error)) {
        bestPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
      } else {
        showCachedFallbackNoticeOnce();
      }
    }
  }

  function formatWaitingDate(value) {
    const date = new Date(value);
    const elapsed = Date.now() - date.getTime();
    if (Number.isNaN(date.getTime()) || elapsed < 0) return formatDate(value);
    const minutes = Math.floor(elapsed / 60_000);
    if (minutes < 1) return "방금 전";
    if (minutes < 60) return `${minutes}분 전`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}시간 전`;
    if (hours < 48) return "어제";
    return formatDate(value);
  }

  function renderUnansweredPosts(posts) {
    if (!unansweredPostList) return;
    unansweredPostList.replaceChildren();
    if (!posts.length) {
      unansweredPostList.append(
        element("li", "best-loading", "모든 질문에 답변이 달렸습니다."),
      );
      return;
    }
    posts.forEach((post) => {
      const item = element("li");
      const link = element("a");
      link.href = detailHrefFromCurrentList(post.postId);
      link.setAttribute("aria-label", `${post.title} 답변하러 가기`);
      link.append(element("span", "best-rank", "Q"));
      const copy = element("span", "best-copy");
      const author = post.authorNickname
        || (!post.authorLoginId ? "소셜 계정" : "작성자 정보 없음");
      copy.append(
        element("strong", "", post.title),
        element("small", "", `${author} · ${formatWaitingDate(post.createdAt)}`),
      );
      link.append(copy);
      item.append(link);
      unansweredPostList.append(item);
    });
  }

  async function loadUnansweredPosts(options = {}) {
    const forceRefresh = options.forceRefresh === true;
    if (!unansweredPostList) return;
    const generation = ++unansweredRequestGeneration;
    const params = new URLSearchParams({
      boardType: ["BEST", "POPULAR"].includes(state.boardType)
        ? state.lastBoardType
        : state.boardType,
      size: "3",
    });
    const path = `/board/posts/unanswered?${params.toString()}`;
    const cached = forceRefresh ? null : readBoardCache(path);
    if (cached) {
      if (generation !== unansweredRequestGeneration) return;
      renderUnansweredPosts(cached.data || []);
      if (cached.fresh) return;
    } else {
      if (generation !== unansweredRequestGeneration) return;
      unansweredPostList.replaceChildren(
        element("li", "best-loading", "불러오는 중"),
      );
    }

    try {
      const payload = await Api.get(path, { cache: "no-store" });
      const posts = payload.data || [];
      writeBoardCache(path, posts);
      if (generation !== unansweredRequestGeneration) return;
      renderUnansweredPosts(posts);
    } catch (error) {
      if (generation !== unansweredRequestGeneration) return;
      if (!cached || !canUseCacheAfterError(error)) {
        unansweredPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
      } else {
        showCachedFallbackNoticeOnce();
      }
    }
  }

  function loadBoardContent(options = {}) {
    return Promise.all([
      loadPosts(options),
      loadBestPosts(options),
      loadUnansweredPosts(options),
    ]);
  }

  async function prefetchBusinessFirstPage() {
    const params = new URLSearchParams({
      page: "0",
      size: String(state.size),
      boardType: "BUSINESS",
      sort: "LATEST",
    });
    const path = `/board/posts?${params.toString()}`;
    const cached = readBoardCache(path);
    if (cached?.fresh) return;

    try {
      const payload = await Api.get(path);
      writeBoardCache(path, payload.data || {});
    } catch (_error) {
      // 사전 조회 실패는 현재 화면 로딩에 영향을 주지 않는다.
    }
  }

  function scheduleBusinessPrefetch() {
    const prefetch = () => {
      prefetchBusinessFirstPage();
    };
    if (typeof window.requestIdleCallback === "function") {
      window.requestIdleCallback(prefetch, { timeout: 2_000 });
      return;
    }
    window.setTimeout(prefetch, 250);
  }

  function syncBoardNavigation(historyMode = "replace") {
    const isBest = state.boardType === "BEST";
    const isPopular = state.boardType === "POPULAR";
    const isRankedView = isBest || isPopular;
    let activeTabId = "board-tab-general";
    document.querySelectorAll("[data-board-type]").forEach((tab) => {
      const active = tab.dataset.boardType === state.boardType;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
      tab.tabIndex = active ? 0 : -1;
      if (active && tab.id) activeTabId = tab.id;
    });
    if (boardTabPanel) {
      boardTabPanel.setAttribute("aria-labelledby", activeTabId);
    }
    boardHeading.textContent = isBest
      ? "베스트 커뮤니티"
      : isPopular
        ? "인기 이야기"
        : state.boardType === "BUSINESS"
          ? "사업자 커뮤니티"
          : "일반 커뮤니티";
    if (totalSummary) {
      totalSummary.textContent = "개의 이야기";
    }
    if (boardListCriteria) {
      if (isPopular) {
        boardListCriteria.textContent = "기준 : 최근 30일, 추천순";
        boardListCriteria.hidden = false;
      } else if (isBest) {
        boardListCriteria.textContent = "기준 : 추천 3개 이상";
        boardListCriteria.hidden = false;
      } else {
        boardListCriteria.textContent = "";
        boardListCriteria.hidden = true;
      }
    }
    if (popularRankRange && !isPopular) {
      popularRankRange.hidden = true;
      popularRankRange.textContent = "";
    }
    searchForm.hidden = isRankedView;
    writeLinks.forEach((link) => {
      link.hidden = isRankedView;
      link.href = writeHrefFromCurrentList(state.lastBoardType);
    });
    syncListUrl(historyMode);
  }

  function switchBoard(boardType) {
    if (!["GENERAL", "BUSINESS", "BEST", "POPULAR"].includes(boardType)) return;
    if (boardType === "BUSINESS" && !businessAccessAllowed) return;
    if (boardType === state.boardType) return;
    state.boardType = boardType;
    if (["GENERAL", "BUSINESS"].includes(boardType)) {
      state.lastBoardType = boardType;
    }
    state.page = 0;
    syncBoardNavigation("push");
    loadBoardContent();
  }

  const boardTabs = Array.from(document.querySelectorAll("[data-board-type]"));
  boardTabs.forEach((tab) => {
    tab.addEventListener("click", () => switchBoard(tab.dataset.boardType));
    tab.addEventListener("keydown", (event) => {
      if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
      const visibleTabs = boardTabs.filter((candidate) => !candidate.hidden);
      const currentIndex = visibleTabs.indexOf(tab);
      if (currentIndex < 0 || !visibleTabs.length) return;

      event.preventDefault();
      let nextIndex = currentIndex;
      if (event.key === "Home") nextIndex = 0;
      if (event.key === "End") nextIndex = visibleTabs.length - 1;
      if (event.key === "ArrowLeft") {
        nextIndex = (currentIndex - 1 + visibleTabs.length) % visibleTabs.length;
      }
      if (event.key === "ArrowRight") {
        nextIndex = (currentIndex + 1) % visibleTabs.length;
      }

      const nextTab = visibleTabs[nextIndex];
      nextTab.focus({ preventScroll: true });
    });
  });

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.category = categorySelect.value;
    state.keyword = keywordInput.value.trim();
    state.sort = sortSelect.value;
    state.page = 0;
    syncListUrl("push");
    loadPosts();
  });

  function resetSearchConditions(historyMode = "push") {
    searchForm.reset();
    state.category = "";
    state.keyword = "";
    state.sort = "LATEST";
    state.page = 0;
    syncSearchControls();
    syncListUrl(historyMode);
    loadPosts();
  }

  resetButton.addEventListener("click", () => resetSearchConditions("push"));

  writeLinks.forEach((link) => {
    link.addEventListener("click", (event) => {
      if (session.authenticated) return;
      event.preventDefault();
      const target = link.getAttribute("href");
      openBoardLogin({
        nextPath: target || listUrlFromState(),
        onSuccess: () => {
          if (target) window.location.assign(target);
          else window.location.reload();
        },
      });
    });
  });

  function initializeScrollTopButton() {
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
      const visible = window.scrollY > 450;
      button.hidden = !visible;
      document.body.classList.toggle("board-scroll-top-visible", visible);
      ticking = false;
    };

    window.addEventListener("scroll", () => {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(updateVisibility);
    }, { passive: true });

    button.addEventListener("click", () => {
      window.scrollTo({
        top: 0,
        behavior: prefersReducedMotion() ? "auto" : "smooth",
      });
    });

    updateVisibility();
  }

  async function initializeBoard() {
    const businessAccessPromise = board.canUseBusinessBoard().then((allowed) => {
      businessAccessAllowed = allowed;
      if (businessTab) businessTab.hidden = !businessAccessAllowed;
      return allowed;
    });

    if (requestedBoardType === "BUSINESS" && await businessAccessPromise) {
      state.boardType = "BUSINESS";
      state.lastBoardType = "BUSINESS";
    }

    syncBoardNavigation();
    await loadBoardContent();
    if (await businessAccessPromise && state.boardType !== "BUSINESS") {
      scheduleBusinessPrefetch();
    }
  }

  function restoreStateFromLocation() {
    const params = new URLSearchParams(window.location.search);
    const boardTypeValue = params.get("boardType");
    let nextBoardType = ["BUSINESS", "BEST", "POPULAR"].includes(boardTypeValue)
      ? boardTypeValue
      : "GENERAL";
    if (nextBoardType === "BUSINESS" && !businessAccessAllowed) {
      nextBoardType = "GENERAL";
    }
    const categoryValue = params.get("category");
    state.boardType = nextBoardType;
    if (["GENERAL", "BUSINESS"].includes(nextBoardType)) {
      state.lastBoardType = nextBoardType;
    }
    state.category = ["GENERAL", "RECOMMENDATION", "REVIEW", "QUESTION", "TRAVEL"].includes(categoryValue)
      ? categoryValue
      : "";
    const sortValue = params.get("sort");
    state.sort = ["LATEST", "LIKES", "COMMENTS"].includes(sortValue)
      ? sortValue
      : "LATEST";
    state.keyword = String(params.get("keyword") || "").trim().slice(0, 100);
    state.page = Math.max(
      0,
      Math.min(9999, (Number.parseInt(params.get("page"), 10) || 1) - 1),
    );
    syncSearchControls();
    syncBoardNavigation("none");
  }

  window.addEventListener("popstate", () => {
    restoreStateFromLocation();
    loadBoardContent();
  });

  window.addEventListener("pageshow", (event) => {
    if (event.persisted) loadBoardContent({ forceRefresh: true });
  });

  window.addEventListener(board.cacheInvalidatedEvent, () => {
    loadBoardContent({ forceRefresh: true });
  });

  window.addEventListener("pagehide", () => {
    boardAuthPopupController?.stop({ closePopup: true });
    pendingBoardLoginAction = null;
  });

  initializeBoardAuthEntryPoints();
  initializeScrollTopButton();
  consumeBoardFlashMessage();
  initializeBoard();
})();
