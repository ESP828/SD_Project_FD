(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const requestedValue = new URLSearchParams(window.location.search).get("boardType");
  const requestedBoardType = ["BUSINESS", "BEST", "POPULAR", "NEWS"].includes(requestedValue)
    ? requestedValue
    : "GENERAL";
  const initialBoardType = ["BUSINESS", "NEWS"].includes(requestedBoardType)
    ? "GENERAL"
    : requestedBoardType;
  let businessAccessAllowed = Boolean(session?.canManageBusiness);
  const state = {
    boardType: initialBoardType,
    lastBoardType: initialBoardType === "BUSINESS" ? "BUSINESS" : "GENERAL",
    category: "",
    keyword: "",
    sort: "LATEST",
    page: 0,
    size: 7,
  };

  const boardList = document.getElementById("board-list");
  const totalCount = document.getElementById("board-total-count");
  const pagination = document.getElementById("board-pagination");
  const bestPostPanel = document.getElementById("best-post-panel");
  const bestPostList = document.getElementById("best-post-list");
  const unansweredPostList = document.getElementById("unanswered-post-list");
  const boardHeading = document.getElementById("board-heading");
  const totalLabel = document.getElementById("board-total-label");
  const businessTab = document.getElementById("business-board-tab");
  const newsAdminTab = document.getElementById("news-admin-board-tab");
  const newsAdminNotice = document.getElementById("news-admin-notice");
  const boardLayout = document.querySelector(".board-layout");
  const boardSideStack = document.getElementById("board-side-stack");
  const searchForm = document.getElementById("board-search-form");
  const categoryFilter = document.getElementById("board-category-filter");
  const categorySelect = document.getElementById("board-category");
  const keywordInput = document.getElementById("board-keyword");
  const sortSelect = document.getElementById("board-sort");
  const resetButton = document.getElementById("board-reset-button");
  const writeLinks = Array.from(document.querySelectorAll("[data-write-link]"));

  if (!session || !board || !boardList || !searchForm) {
    return;
  }

  const {
    authorIdentity,
    categoryLabel,
    detailPath,
    element,
    formatDate,
    icon,
    readBoardCache,
    writeBoardCache,
  } = board;

  function isEdited(item) {
    return item?.edited === true;
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
    const isNotice = post.category === "NOTICE";
    const article = element("a", isNotice ? "post-row post-row--notice" : "post-row");
    article.href = state.boardType === "BEST"
      ? `${detailPath(post.postId)}&from=BEST`
      : state.boardType === "POPULAR"
        ? `${detailPath(post.postId)}&from=POPULAR`
        : state.boardType === "NEWS"
          ? `${detailPath(post.postId)}&from=NEWS_ADMIN`
          : detailPath(post.postId);
    article.setAttribute("aria-label", `${post.title} 상세 보기`);

    const main = element("div", "post-row-main");
    const badges = element("div", "post-badge-row");
    if (state.boardType === "BEST" || state.boardType === "POPULAR") {
      const rank = state.boardType === "POPULAR"
        ? index + 1
        : state.page * state.size + index + 1;
      const rankLabel = state.boardType === "POPULAR" ? "인기" : "베스트";
      badges.append(element("span", "post-badge", `${rankLabel} ${rank}위`));
    }
    badges.append(
      element(
        "span",
        isNotice
          ? "post-badge post-badge--notice"
          : state.boardType === "NEWS"
            ? "post-badge post-badge--news-admin"
            : "post-badge",
        isNotice ? "공지 · 상단 고정" : categoryLabel(post.category),
      ),
    );
    if (state.boardType === "NEWS") {
      const isPublicNews = post.publicRestaurantId != null;
      const restaurantName = isPublicNews
        ? post.publicRestaurantName
        : post.restaurant?.name;
      const sourceLabel = isPublicNews ? "식당" : "푸드덕 등록 식당";
      const fallbackId = isPublicNews ? post.publicRestaurantId : post.restaurantId;
      badges.append(
        element(
          "span",
          "post-board-badge post-board-badge--news-source",
          `${sourceLabel} · ${restaurantName || `#${fallbackId || "-"}`}`,
        ),
      );
    } else if (["BEST", "POPULAR"].includes(state.boardType) || post.boardType === "BUSINESS") {
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
      element("p", "post-preview", post.contentPreview || "내용 미리보기 없음"),
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
    if (state.boardType !== "NEWS" && post.restaurant?.name) {
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

  function renderPosts(pageData) {
    totalCount.textContent = String(pageData.totalElements || 0);
    boardList.replaceChildren();
    const posts = pageData.content || [];
    if (!posts.length) {
      const empty = element("div", "board-empty");
      const image = new Image();
      image.src = "/images/characters/laptop.png";
      image.alt = "";
      empty.append(
        image,
        element(
          "strong",
          "",
          state.boardType === "BEST"
            ? "아직 베스트 게시글이 없습니다."
            : state.boardType === "POPULAR"
              ? "아직 인기 이야기가 없습니다."
              : state.boardType === "NEWS"
                ? "등록된 가게 소식이 없습니다."
                : "아직 등록된 이야기가 없습니다.",
        ),
        element(
          "span",
          "",
          state.boardType === "BEST"
            ? "최근 7일 안에 추천을 3개 이상 받은 글이 여기에 표시됩니다."
            : state.boardType === "POPULAR"
              ? "추천이 쌓인 이야기가 순위에 따라 여기에 표시됩니다."
              : state.boardType === "NEWS"
                ? "가게 상세의 소식 탭에서 작성된 글이 이 관리자 전용 목록에 모입니다."
                : "첫 번째 맛집 이야기를 함께 나눠 보세요.",
        ),
      );
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
    loadPosts();
    document.querySelector(".board-content")?.scrollIntoView({ behavior: "smooth" });
  }

  function normalizePostPage(data) {
    if (state.boardType !== "POPULAR") {
      return data || {};
    }
    const posts = Array.isArray(data) ? data : [];
    return {
      content: posts,
      page: 0,
      size: posts.length,
      totalElements: posts.length,
      totalPages: posts.length ? 1 : 0,
      first: true,
      last: true,
    };
  }

  async function loadPosts() {
    const isBest = state.boardType === "BEST";
    const isPopular = state.boardType === "POPULAR";
    const isNewsAdmin = state.boardType === "NEWS";
    const params = new URLSearchParams();

    if (isPopular) {
      params.set("size", "20");
    } else {
      params.set("page", String(state.page));
      params.set("size", String(state.size));
      if (!isBest) {
        params.set("sort", state.sort);
        if (state.keyword) params.set("keyword", state.keyword);
        if (!isNewsAdmin) {
          params.set("boardType", state.boardType);
          if (state.category) params.set("category", state.category);
        }
      }
    }

    const path = isPopular
      ? `/board/posts/popular?${params.toString()}`
      : isBest
        ? `/board/posts/best/community?${params.toString()}`
        : isNewsAdmin
          ? `/board/posts/admin/news?${params.toString()}`
          : `/board/posts?${params.toString()}`;
    const cached = readBoardCache(path);
    if (cached) {
      renderPosts(normalizePostPage(cached.data));
      if (cached.fresh) return;
    } else {
      renderLoading();
    }

    try {
      const payload = await Api.get(path);
      const data = payload.data || (isPopular ? [] : {});
      writeBoardCache(path, data);
      renderPosts(normalizePostPage(data));
    } catch (error) {
      if (!cached) renderListError(error);
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
      link.href = detailPath(post.postId);
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

  async function loadBestPosts() {
    const hideAuxiliary = ["BEST", "POPULAR", "NEWS"].includes(state.boardType);
    if (bestPostPanel) bestPostPanel.hidden = hideAuxiliary;
    if (hideAuxiliary) return;
    const params = new URLSearchParams({
      boardType: state.boardType,
      size: "3",
    });
    const path = `/board/posts/best?${params.toString()}`;
    const cached = readBoardCache(path);
    if (cached) {
      renderBestPosts(cached.data || []);
      if (cached.fresh) return;
    } else {
      bestPostList.replaceChildren(element("li", "best-loading", "불러오는 중"));
    }

    try {
      const payload = await Api.get(path);
      const posts = payload.data || [];
      writeBoardCache(path, posts);
      renderBestPosts(posts);
    } catch (error) {
      if (!cached) {
        bestPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
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
      link.href = detailPath(post.postId);
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

  async function loadUnansweredPosts() {
    if (!unansweredPostList || state.boardType === "NEWS") return;
    const params = new URLSearchParams({
      boardType: ["BEST", "POPULAR"].includes(state.boardType)
        ? state.lastBoardType
        : state.boardType,
      size: "3",
    });
    const path = `/board/posts/unanswered?${params.toString()}`;
    const cached = readBoardCache(path);
    if (cached) {
      renderUnansweredPosts(cached.data || []);
      if (cached.fresh) return;
    } else {
      unansweredPostList.replaceChildren(
        element("li", "best-loading", "불러오는 중"),
      );
    }

    try {
      const payload = await Api.get(path);
      const posts = payload.data || [];
      writeBoardCache(path, posts);
      renderUnansweredPosts(posts);
    } catch (error) {
      if (!cached) {
        unansweredPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
      }
    }
  }

  function loadBoardContent() {
    return Promise.all([loadPosts(), loadBestPosts(), loadUnansweredPosts()]);
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

  function syncBoardNavigation() {
    const isBest = state.boardType === "BEST";
    const isPopular = state.boardType === "POPULAR";
    const isNewsAdmin = state.boardType === "NEWS";
    const isRankedView = isBest || isPopular;
    document.querySelectorAll("[data-board-type]").forEach((tab) => {
      const active = tab.dataset.boardType === state.boardType;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
    });
    boardHeading.textContent = isBest
      ? "베스트 커뮤니티"
      : isPopular
        ? "인기 이야기"
        : isNewsAdmin
          ? "가게 소식 관리"
          : state.boardType === "BUSINESS"
            ? "사업자 커뮤니티"
            : "일반 커뮤니티";
    if (totalLabel) totalLabel.textContent = isNewsAdmin ? "개의 가게 소식" : "개의 이야기";
    if (newsAdminNotice) newsAdminNotice.hidden = !isNewsAdmin;
    if (categoryFilter) categoryFilter.hidden = isNewsAdmin;
    searchForm.hidden = isRankedView;
    searchForm.classList.toggle("is-news-admin", isNewsAdmin);
    boardLayout?.classList.toggle("is-news-admin", isNewsAdmin);
    if (boardSideStack) boardSideStack.hidden = isNewsAdmin;
    writeLinks.forEach((link) => {
      link.hidden = isRankedView || isNewsAdmin;
      link.href = board.writePath(state.lastBoardType);
    });
    window.history.replaceState(
      null,
      "",
      isBest
        ? "/pages/board/index.html?boardType=BEST"
        : isPopular
          ? "/pages/board/index.html?boardType=POPULAR"
          : isNewsAdmin
            ? "/pages/board/index.html?boardType=NEWS"
            : state.boardType === "BUSINESS"
              ? "/pages/board/index.html?boardType=BUSINESS"
              : "/pages/board/index.html",
    );
  }

  function switchBoard(boardType) {
    if (!["GENERAL", "BUSINESS", "BEST", "POPULAR", "NEWS"].includes(boardType)) return;
    if (boardType === "BUSINESS" && !businessAccessAllowed) return;
    if (boardType === "NEWS" && !session.isAdmin) return;
    state.boardType = boardType;
    if (["GENERAL", "BUSINESS"].includes(boardType)) {
      state.lastBoardType = boardType;
    }
    state.page = 0;
    syncBoardNavigation();
    loadBoardContent();
  }

  document.querySelectorAll("[data-board-type]").forEach((tab) => {
    tab.addEventListener("click", () => switchBoard(tab.dataset.boardType));
  });

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.category = categorySelect.value;
    state.keyword = keywordInput.value.trim();
    state.sort = sortSelect.value;
    state.page = 0;
    loadPosts();
  });

  resetButton.addEventListener("click", () => {
    searchForm.reset();
    state.category = "";
    state.keyword = "";
    state.sort = "LATEST";
    state.page = 0;
    loadPosts();
  });

  writeLinks.forEach((link) => {
    link.addEventListener("click", (event) => {
      if (session.authenticated) return;
      event.preventDefault();
      board.requireLogin(link.getAttribute("href"));
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

  async function initializeBoard() {
    if (newsAdminTab) newsAdminTab.hidden = !session.isAdmin;

    const businessAccessPromise = board.canUseBusinessBoard().then((allowed) => {
      businessAccessAllowed = allowed;
      if (businessTab) businessTab.hidden = !businessAccessAllowed;
      return allowed;
    });

    if (requestedBoardType === "NEWS" && session.isAdmin) {
      state.boardType = "NEWS";
    } else if (requestedBoardType === "BUSINESS" && await businessAccessPromise) {
      state.boardType = "BUSINESS";
      state.lastBoardType = "BUSINESS";
    }

    syncBoardNavigation();
    await loadBoardContent();
    if (await businessAccessPromise && state.boardType !== "BUSINESS") {
      scheduleBusinessPrefetch();
    }
  }

  window.addEventListener("pageshow", (event) => {
    if (event.persisted) loadBoardContent();
  });

  initializeScrollTopButton();
  initializeBoard();
})();