(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const requestedBoardType =
    new URLSearchParams(window.location.search).get("boardType") === "BUSINESS"
      ? "BUSINESS"
      : "GENERAL";
  const state = {
    boardType:
      requestedBoardType === "BUSINESS" && session?.canManageBusiness
        ? "BUSINESS"
        : "GENERAL",
    category: "",
    keyword: "",
    sort: "LATEST",
    page: 0,
    size: 10,
  };

  const boardList = document.getElementById("board-list");
  const totalCount = document.getElementById("board-total-count");
  const pagination = document.getElementById("board-pagination");
  const bestPostList = document.getElementById("best-post-list");
  const boardHeading = document.getElementById("board-heading");
  const businessTab = document.getElementById("business-board-tab");
  const searchForm = document.getElementById("board-search-form");
  const categorySelect = document.getElementById("board-category");
  const keywordInput = document.getElementById("board-keyword");
  const sortSelect = document.getElementById("board-sort");
  const resetButton = document.getElementById("board-reset-button");
  const writeLinks = Array.from(document.querySelectorAll("[data-write-link]"));

  if (!session || !board || !boardList || !searchForm) {
    return;
  }

  const { authorIdentity, categoryLabel, detailPath, element, formatDate, icon } =
    board;

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

  function createPostRow(post) {
    const article = element("a", "post-row");
    article.href = detailPath(post.postId);
    article.setAttribute("aria-label", `${post.title} 상세 보기`);

    const main = element("div", "post-row-main");
    const badges = element("div", "post-badge-row");
    badges.append(element("span", "post-badge", categoryLabel(post.category)));
    if (post.boardType === "BUSINESS") {
      badges.append(element("span", "post-board-badge", "사업자 커뮤니티"));
    }
    main.append(
      badges,
      element("h3", "", post.title),
      element("p", "post-preview", post.contentPreview || "내용 미리보기 없음"),
    );

    const meta = element("div", "post-meta");
    meta.append(authorIdentity(post), element("span", "", formatDate(post.createdAt)));
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
        element("strong", "", "아직 등록된 이야기가 없습니다."),
        element("span", "", "첫 번째 맛집 이야기를 함께 나눠 보세요."),
      );
      boardList.append(empty);
    } else {
      posts.forEach((post) => boardList.append(createPostRow(post)));
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

  async function loadPosts() {
    renderLoading();
    const params = new URLSearchParams({
      boardType: state.boardType,
      sort: state.sort,
      page: String(state.page),
      size: String(state.size),
    });
    if (state.category) params.set("category", state.category);
    if (state.keyword) params.set("keyword", state.keyword);

    try {
      const payload = await Api.get(`/board/posts?${params.toString()}`);
      renderPosts(payload.data || {});
    } catch (error) {
      renderListError(error);
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
    bestPostList.replaceChildren(element("li", "best-loading", "불러오는 중"));
    try {
      const params = new URLSearchParams({
        boardType: state.boardType,
        size: "3",
      });
      const payload = await Api.get(`/board/posts/best?${params.toString()}`);
      renderBestPosts(payload.data || []);
    } catch (error) {
      bestPostList.replaceChildren(
        element("li", "best-loading", error.message || "불러오지 못했습니다."),
      );
    }
  }

  function syncBoardNavigation() {
    document.querySelectorAll("[data-board-type]").forEach((tab) => {
      const active = tab.dataset.boardType === state.boardType;
      tab.classList.toggle("is-active", active);
      tab.setAttribute("aria-selected", String(active));
    });
    boardHeading.textContent =
      state.boardType === "BUSINESS" ? "사업자 커뮤니티" : "일반 커뮤니티";
    writeLinks.forEach((link) => {
      link.href = board.writePath(state.boardType);
    });
    window.history.replaceState(
      null,
      "",
      state.boardType === "BUSINESS"
        ? "/pages/board/index.html?boardType=BUSINESS"
        : "/pages/board/index.html",
    );
  }

  function switchBoard(boardType) {
    if (boardType === "BUSINESS" && !session.canManageBusiness) return;
    state.boardType = boardType;
    state.page = 0;
    syncBoardNavigation();
    loadPosts();
    loadBestPosts();
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

  if (session.canManageBusiness) {
    businessTab.hidden = false;
  }
  syncBoardNavigation();
  loadPosts();
  loadBestPosts();
})();
