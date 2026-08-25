(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("community-access-gate");
  const dashboard = document.getElementById("community-dashboard");
  if (!session || !gate || !dashboard) return;

  if (!session.isAdmin) {
    gate.hidden = false;
    return;
  }
  dashboard.hidden = false;

  const boardButtons = Array.from(document.querySelectorAll(".community-board-button"));
  const searchForm = document.getElementById("community-search-form");
  const categorySelect = document.getElementById("community-category");
  const keywordInput = document.getElementById("community-keyword");
  const resetButton = document.getElementById("community-reset");
  const tableBody = document.getElementById("community-table-body");
  const countLabel = document.getElementById("community-count");
  const previousButton = document.getElementById("community-prev");
  const nextButton = document.getElementById("community-next");
  const pageLabel = document.getElementById("community-page-label");

  const commentsDialog = document.getElementById("community-comments-dialog");
  const commentsPostTitle = document.getElementById("community-comments-post-title");
  const commentsList = document.getElementById("community-comments-list");
  const commentsStatus = document.getElementById("community-comments-status");
  const commentsPreviousButton = document.getElementById("community-comments-prev");
  const commentsNextButton = document.getElementById("community-comments-next");
  const commentsPageLabel = document.getElementById("community-comments-page-label");

  if (
    !searchForm || !categorySelect || !keywordInput || !resetButton ||
    !tableBody || !countLabel || !previousButton || !nextButton || !pageLabel ||
    !commentsDialog || !commentsPostTitle || !commentsList || !commentsStatus ||
    !commentsPreviousButton || !commentsNextButton || !commentsPageLabel
  ) {
    return;
  }

  const state = {
    boardType: "GENERAL",
    category: "",
    keyword: "",
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    posts: [],
    requestVersion: 0,
  };
  const commentState = {
    postId: null,
    postTitle: "",
    page: 0,
    size: 30,
    totalPages: 0,
    comments: [],
    requestVersion: 0,
  };
  const pendingPostDeleteIds = new Set();
  const pendingCommentDeleteIds = new Set();

  const BOARD_LABELS = {
    GENERAL: "일반",
    BUSINESS: "사업자",
  };
  const CATEGORY_LABELS = {
    NOTICE: "공지",
    GENERAL: "일반",
    RECOMMENDATION: "추천",
    REVIEW: "후기",
    QUESTION: "질문",
    TRAVEL: "여행",
  };

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (character) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[character]));
  }

  function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(date);
  }

  function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(Number(value) || 0);
  }

  function postDetailPath(postId) {
    return `/board/detail?postId=${encodeURIComponent(postId)}`;
  }

  function renderPosts() {
    if (!state.posts.length) {
      tableBody.innerHTML = '<tr><td colspan="9" class="community-empty">조건에 맞는 게시글이 없습니다.</td></tr>';
      return;
    }

    tableBody.innerHTML = state.posts.map((post) => {
      const business = post.boardType === "BUSINESS";
      const author = post.authorNickname || post.authorLoginId || "작성자 정보 없음";
      const detailHref = postDetailPath(post.postId);
      return `
        <tr data-post-id="${post.postId}">
          <td><span class="community-badge${business ? " community-badge--business" : ""}">${BOARD_LABELS[post.boardType] || escapeHtml(post.boardType)}</span></td>
          <td>${CATEGORY_LABELS[post.category] || escapeHtml(post.category)}</td>
          <td class="community-title-cell"><a href="${detailHref}">${escapeHtml(post.title || "제목 없는 게시글")}</a></td>
          <td>${escapeHtml(author)}</td>
          <td>${formatNumber(post.viewCount)}</td>
          <td>${formatNumber(post.likeCount)}</td>
          <td>${formatNumber(post.commentCount)}</td>
          <td>${formatDate(post.createdAt)}</td>
          <td class="community-actions">
            <a class="button button-secondary button-sm" href="${detailHref}">보기</a>
            <button type="button" class="button button-secondary button-sm" data-comments="${post.postId}">댓글</button>
            <button type="button" class="button button-sm community-delete-button" data-delete-post="${post.postId}">삭제</button>
          </td>
        </tr>
      `;
    }).join("");
  }

  function updatePostPagination() {
    const displayTotalPages = Math.max(state.totalPages, 1);
    pageLabel.textContent = `${state.page + 1} / ${displayTotalPages}`;
    previousButton.disabled = state.page <= 0;
    nextButton.disabled = state.totalPages === 0 || state.page + 1 >= state.totalPages;
    countLabel.textContent = `${BOARD_LABELS[state.boardType]} 커뮤니티 · 총 ${formatNumber(state.totalElements)}개`;
  }

  async function loadPosts() {
    const requestVersion = ++state.requestVersion;
    tableBody.innerHTML = '<tr><td colspan="9" class="community-loading">게시글을 불러오는 중...</td></tr>';
    const params = new URLSearchParams({
      boardType: state.boardType,
      sort: "LATEST",
      page: String(state.page),
      size: String(state.size),
    });
    if (state.category) params.set("category", state.category);
    if (state.keyword) params.set("keyword", state.keyword);

    try {
      const response = await Api.get(`/board/posts?${params.toString()}`);
      if (requestVersion !== state.requestVersion) return;
      const pageData = response.data || {};
      state.posts = Array.isArray(pageData.content) ? pageData.content : [];
      state.totalPages = Number(pageData.totalPages) || 0;
      state.totalElements = Number(pageData.totalElements) || 0;
      if (state.totalPages > 0 && state.page >= state.totalPages) {
        state.page = state.totalPages - 1;
        await loadPosts();
        return;
      }
      renderPosts();
      updatePostPagination();
    } catch (error) {
      if (requestVersion !== state.requestVersion) return;
      state.posts = [];
      state.totalPages = 0;
      state.totalElements = 0;
      tableBody.innerHTML = `<tr><td colspan="9" class="community-empty">${escapeHtml(error.message || "게시글을 불러오지 못했습니다.")}</td></tr>`;
      updatePostPagination();
    }
  }

  function renderComments() {
    if (!commentState.comments.length) {
      commentsList.innerHTML = '<p class="community-empty">등록된 댓글이 없습니다.</p>';
      return;
    }

    commentsList.innerHTML = commentState.comments.map((comment) => {
      const author = comment.authorNickname || comment.authorLoginId || "작성자 정보 없음";
      return `
        <article class="community-comment-item" data-comment-id="${comment.commentId}">
          <div class="community-comment-meta">
            <strong>${escapeHtml(author)}</strong>
            <span>${formatDate(comment.createdAt)}</span>
          </div>
          <button type="button" class="button button-sm community-delete-button" data-delete-comment="${comment.commentId}">삭제</button>
          <p class="community-comment-content"></p>
        </article>
      `;
    }).join("");

    const contentNodes = commentsList.querySelectorAll(".community-comment-content");
    commentState.comments.forEach((comment, index) => {
      const node = contentNodes[index];
      if (!node) return;
      const content = comment.content || "내용 없는 댓글";
      if (window.FooduckEmojis) {
        window.FooduckEmojis.renderText(node, content);
      } else {
        node.textContent = content;
      }
    });
  }

  function updateCommentPagination(totalElements = 0) {
    const displayTotalPages = Math.max(commentState.totalPages, 1);
    commentsPageLabel.textContent = `${commentState.page + 1} / ${displayTotalPages}`;
    commentsPreviousButton.disabled = commentState.page <= 0;
    commentsNextButton.disabled = commentState.totalPages === 0 || commentState.page + 1 >= commentState.totalPages;
    commentsStatus.textContent = `총 ${formatNumber(totalElements)}개`;
  }

  async function loadComments() {
    if (!commentState.postId) return;
    const requestVersion = ++commentState.requestVersion;
    commentsList.innerHTML = '<p class="community-loading">댓글을 불러오는 중...</p>';
    commentsStatus.textContent = "";
    const params = new URLSearchParams({
      page: String(commentState.page),
      size: String(commentState.size),
    });
    try {
      const response = await Api.get(
        `/board/posts/${encodeURIComponent(commentState.postId)}/comments?${params.toString()}`,
      );
      if (requestVersion !== commentState.requestVersion) return;
      const pageData = response.data || {};
      commentState.comments = Array.isArray(pageData.content) ? pageData.content : [];
      commentState.totalPages = Number(pageData.totalPages) || 0;
      const totalElements = Number(pageData.totalElements) || 0;
      if (commentState.totalPages > 0 && commentState.page >= commentState.totalPages) {
        commentState.page = commentState.totalPages - 1;
        await loadComments();
        return;
      }
      renderComments();
      updateCommentPagination(totalElements);
    } catch (error) {
      if (requestVersion !== commentState.requestVersion) return;
      commentState.comments = [];
      commentState.totalPages = 0;
      commentsList.innerHTML = `<p class="community-empty">${escapeHtml(error.message || "댓글을 불러오지 못했습니다.")}</p>`;
      updateCommentPagination(0);
    }
  }

  function openComments(post) {
    commentState.postId = post.postId;
    commentState.postTitle = post.title || "제목 없는 게시글";
    commentState.page = 0;
    commentState.totalPages = 0;
    commentState.comments = [];
    commentsPostTitle.textContent = commentState.postTitle;
    if (!commentsDialog.open) commentsDialog.showModal();
    loadComments();
  }

  function closeComments() {
    commentState.requestVersion += 1;
    commentState.postId = null;
    commentsDialog.close();
  }

  boardButtons.forEach((button) => {
    button.addEventListener("click", () => {
      boardButtons.forEach((item) => item.classList.remove("is-active"));
      button.classList.add("is-active");
      state.boardType = button.dataset.boardType;
      state.page = 0;
      loadPosts();
    });
  });

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.category = categorySelect.value;
    state.keyword = keywordInput.value.trim();
    state.page = 0;
    loadPosts();
  });

  resetButton.addEventListener("click", () => {
    searchForm.reset();
    state.category = "";
    state.keyword = "";
    state.page = 0;
    loadPosts();
  });

  previousButton.addEventListener("click", () => {
    if (state.page <= 0) return;
    state.page -= 1;
    loadPosts();
  });

  nextButton.addEventListener("click", () => {
    if (state.page + 1 >= state.totalPages) return;
    state.page += 1;
    loadPosts();
  });

  tableBody.addEventListener("click", async (event) => {
    const commentsButton = event.target.closest("[data-comments]");
    if (commentsButton) {
      const postId = Number(commentsButton.dataset.comments);
      const post = state.posts.find((item) => item.postId === postId);
      if (post) openComments(post);
      return;
    }

    const deleteButton = event.target.closest("[data-delete-post]");
    if (!deleteButton) return;
    const postId = Number(deleteButton.dataset.deletePost);
    const post = state.posts.find((item) => item.postId === postId);
    if (
      !Number.isSafeInteger(postId) || postId <= 0 || !post ||
      pendingPostDeleteIds.has(postId)
    ) {
      return;
    }

    pendingPostDeleteIds.add(postId);
    try {
      const confirmed = await window.FooduckConfirm.open({
        title: "게시글을 삭제할까요?",
        message: `“${post.title || "제목 없는 게시글"}” 게시글과 연결된 댓글·추천도 함께 정리됩니다.`,
        confirmLabel: "게시글 삭제",
        pendingLabel: "삭제 중...",
        errorMessage: "게시글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        danger: true,
        onConfirm: () => Api.delete(`/board/posts/${encodeURIComponent(postId)}`),
      });
      if (!confirmed) return;
      if (commentState.postId === postId && commentsDialog.open) closeComments();
      if (state.posts.length === 1 && state.page > 0) state.page -= 1;
      await loadPosts();
    } finally {
      pendingPostDeleteIds.delete(postId);
    }
  });

  commentsList.addEventListener("click", async (event) => {
    const deleteButton = event.target.closest("[data-delete-comment]");
    if (!deleteButton) return;
    const commentId = Number(deleteButton.dataset.deleteComment);
    if (
      !Number.isSafeInteger(commentId) || commentId <= 0 ||
      pendingCommentDeleteIds.has(commentId)
    ) {
      return;
    }

    pendingCommentDeleteIds.add(commentId);
    try {
      const confirmed = await window.FooduckConfirm.open({
        title: "댓글을 삭제할까요?",
        message: "삭제한 댓글은 되돌릴 수 없습니다.",
        confirmLabel: "댓글 삭제",
        pendingLabel: "삭제 중...",
        errorMessage: "댓글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        danger: true,
        onConfirm: () => Api.delete(`/board/comments/${encodeURIComponent(commentId)}`),
      });
      if (!confirmed) return;
      if (commentState.comments.length === 1 && commentState.page > 0) {
        commentState.page -= 1;
      }
      await Promise.all([loadComments(), loadPosts()]);
    } finally {
      pendingCommentDeleteIds.delete(commentId);
    }
  });

  commentsPreviousButton.addEventListener("click", () => {
    if (commentState.page <= 0) return;
    commentState.page -= 1;
    loadComments();
  });

  commentsNextButton.addEventListener("click", () => {
    if (commentState.page + 1 >= commentState.totalPages) return;
    commentState.page += 1;
    loadComments();
  });

  commentsDialog.querySelector("[data-close]").addEventListener("click", closeComments);
  commentsDialog.addEventListener("cancel", (event) => {
    event.preventDefault();
    closeComments();
  });

  loadPosts();
})();
