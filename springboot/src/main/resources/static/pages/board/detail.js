(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const postId = board?.readPostId();
  const fromBest =
    new URLSearchParams(window.location.search).get("from") === "BEST";
  const state = { post: null };

  const detailContent = document.getElementById("post-detail-content");
  const listLink = document.getElementById("detail-list-link");
  const relatedPostList = document.getElementById("related-post-list");
  const restaurantSide = document.getElementById("detail-restaurant-side");
  const commentCount = document.getElementById("detail-comment-count");
  const commentForm = document.getElementById("comment-form");
  const commentContent = document.getElementById("comment-content");
  const commentLoginNote = document.getElementById("comment-login-note");
  const commentList = document.getElementById("comment-list");
  const toast = document.getElementById("board-toast");

  if (!session || !board || !detailContent) return;

  const {
    authorIdentity,
    categoryLabel,
    detailPath,
    element,
    formatDate,
    invalidateBoardCache,
    mapHref,
    readBoardCache,
    showToast,
    writeBoardCache,
  } = board;

  function actionButton(label, className, handler) {
    const button = element("button", className, label);
    button.type = "button";
    button.addEventListener("click", handler);
    return button;
  }

  function detailBadge(text, className = "post-badge") {
    return element("span", className, text);
  }

  function renderRestaurantSide(restaurant) {
    if (!restaurant) {
      restaurantSide.hidden = true;
      restaurantSide.replaceChildren();
      return;
    }
    restaurantSide.hidden = false;
    restaurantSide.replaceChildren(
      element("span", "board-step-label", "Related restaurant"),
      element("h2", "", restaurant.name),
      element("p", "", restaurant.address || "주소 정보 없음"),
    );
    const link = element("a", "button button-sm button-secondary", "지도에서 보기");
    link.href = mapHref(restaurant);
    restaurantSide.append(link);
  }

  function renderRelatedPosts(posts) {
    if (!relatedPostList) return;
    relatedPostList.replaceChildren();
    if (!posts.length) {
      relatedPostList.append(
        element("li", "best-loading", "함께 볼 만한 글이 아직 없습니다."),
      );
      return;
    }
    posts.forEach((post) => {
      const item = element("li");
      const link = element("a");
      link.href = detailPath(post.postId);
      link.setAttribute("aria-label", `${post.title} 상세 보기`);
      link.append(element("span", "best-rank", categoryLabel(post.category).slice(0, 1)));
      const copy = element("span", "best-copy");
      copy.append(
        element("strong", "", post.title),
        element(
          "small",
          "",
          `${categoryLabel(post.category)} · 추천 ${post.likeCount || 0} · 댓글 ${post.commentCount || 0}`,
        ),
      );
      link.append(copy);
      item.append(link);
      relatedPostList.append(item);
    });
  }

  async function loadRelatedPosts() {
    if (!relatedPostList) return;
    const path = `/board/posts/${postId}/related?size=5`;
    const cached = readBoardCache(path);
    if (cached) {
      renderRelatedPosts(cached.data || []);
      if (cached.fresh) return;
    } else {
      relatedPostList.replaceChildren(
        element("li", "best-loading", "불러오는 중"),
      );
    }

    try {
      const payload = await Api.get(path);
      const posts = payload.data || [];
      writeBoardCache(path, posts);
      renderRelatedPosts(posts);
    } catch (error) {
      if (!cached) {
        relatedPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
      }
    }
  }

  function renderPost(post) {
    state.post = post;
    document.title = `${post.title} · 푸드덕`;
    listLink.href = fromBest
      ? "/pages/board/index.html?boardType=BEST"
      : board.listPath(post.boardType);
    detailContent.replaceChildren();

    const badges = element("div", "detail-badges");
    badges.append(detailBadge(categoryLabel(post.category)));
    badges.append(
      detailBadge(
        post.boardType === "BUSINESS" ? "사업자 커뮤니티" : "일반 커뮤니티",
        "post-board-badge",
      ),
    );

    const heading = element("header", "detail-heading");
    heading.append(badges, element("h1", "", post.title));

    const meta = element("div", "detail-meta");
    meta.append(
      authorIdentity(post, { showAuthorMenu: true }),
      element("span", "", formatDate(post.createdAt)),
      element("span", "", `조회 ${post.viewCount || 0}`),
      element("span", "", `추천 ${post.likeCount || 0}`),
    );
    heading.append(meta);
    detailContent.append(heading);

    if (post.restaurant) {
      const restaurant = element("div", "detail-restaurant");
      const copy = element("span");
      copy.append(
        element("strong", "", post.restaurant.name),
        element("small", "", post.restaurant.address || "주소 정보 없음"),
      );
      const link = element("a", "button button-sm button-secondary", "지도에서 보기");
      link.href = mapHref(post.restaurant);
      restaurant.append(copy, link);
      detailContent.append(restaurant);
    }

    detailContent.append(element("div", "detail-body", post.content));
    const actions = element("div", "detail-actions");
    const likeButton = actionButton(
      `${post.likedByCurrentUser ? "추천 취소" : "추천"} · ${post.likeCount || 0}`,
      post.likedByCurrentUser
        ? "button button-sm button-primary"
        : "button button-sm button-secondary",
      toggleLike,
    );
    actions.append(likeButton);
    if ((!fromBest && post.ownedByCurrentUser) || session.isAdmin) {
      const editLink = element("a", "button button-sm button-secondary", "수정");
      editLink.href = board.writePath(post.boardType, post.postId);
      actions.append(
        editLink,
        actionButton("삭제", "button button-sm button-danger", deletePost),
      );
    }
    detailContent.append(actions);
    renderRestaurantSide(post.restaurant);
    window.FooduckIcons?.enhance(detailContent);
  }

  function renderPostError(message) {
    detailContent.replaceChildren();
    const wrapper = element("div", "board-error");
    const image = new Image();
    image.src = "/images/characters/error.png";
    image.alt = "";
    wrapper.append(
      image,
      element("strong", "", "게시글을 표시할 수 없습니다."),
      element("span", "", message || "게시글 주소를 다시 확인해 주세요."),
    );
    const link = element("a", "button button-sm button-secondary", "커뮤니티 목록");
    link.href = "/pages/board/index.html";
    wrapper.append(link);
    detailContent.append(wrapper);
  }

  async function loadPost() {
    const path = `/board/posts/${postId}`;
    const cached = readBoardCache(path);
    if (cached?.data) {
      renderPost(cached.data);
      if (cached.fresh) return;
    }

    try {
      const payload = await Api.get(path);
      writeBoardCache(path, payload.data);
      renderPost(payload.data);
    } catch (error) {
      if (!cached?.data) throw error;
    }
  }

  async function toggleLike() {
    if (!board.requireLogin(window.location.pathname + window.location.search)) return;
    try {
      const payload = state.post.likedByCurrentUser
        ? await Api.delete(`/board/posts/${postId}/like`)
        : await Api.post(`/board/posts/${postId}/like`, {});
      invalidateBoardCache();
      renderPost({
        ...state.post,
        likedByCurrentUser: payload.data.liked,
        likeCount: payload.data.likeCount,
      });
      showToast(toast, payload.message);
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  async function deletePost() {
    if (!window.confirm("게시글과 연결된 댓글·추천을 삭제하시겠습니까?")) return;
    try {
      const payload = await Api.delete(`/board/posts/${postId}`);
      invalidateBoardCache();
      window.alert(payload.message);
      window.location.assign(board.listPath(state.post.boardType));
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  function renderComments(pageData) {
    const comments = pageData.content || [];
    commentCount.textContent = String(pageData.totalElements || 0);
    commentList.replaceChildren();
    if (!comments.length) {
      commentList.append(
        element("p", "comment-empty", "첫 댓글을 함께 남겨 보세요."),
      );
      return;
    }
    comments.forEach((comment) => {
      const item = element("article", "comment-item");
      const top = element("div", "comment-top");
      top.append(
        authorIdentity(comment),
        element("span", "comment-date", formatDate(comment.createdAt)),
      );
      item.append(top, element("div", "comment-content", comment.content));
      if (comment.ownedByCurrentUser || session.isAdmin) {
        const actions = element("div", "comment-actions");
        actions.append(
          actionButton("수정", "comment-action", () => editComment(comment)),
          actionButton("삭제", "comment-action", () => deleteComment(comment)),
        );
        item.append(actions);
      }
      commentList.append(item);
    });
  }

  async function loadComments() {
    const path = `/board/posts/${postId}/comments?page=0&size=30`;
    const cached = readBoardCache(path);
    if (cached) {
      renderComments(cached.data || {});
      if (cached.fresh) return;
    }

    try {
      const payload = await Api.get(path);
      const pageData = payload.data || {};
      writeBoardCache(path, pageData);
      renderComments(pageData);
    } catch (error) {
      if (!cached) throw error;
    }
  }

  async function editComment(comment) {
    const content = window.prompt("수정할 댓글을 입력해 주세요.", comment.content);
    if (content === null) return;
    if (!content.trim()) {
      showToast(toast, "댓글 내용을 입력해 주세요.", true);
      return;
    }
    try {
      const payload = await Api.put(`/board/comments/${comment.commentId}`, {
        content: content.trim(),
      });
      invalidateBoardCache();
      showToast(toast, payload.message);
      await loadComments();
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  async function deleteComment(comment) {
    if (!window.confirm("댓글을 삭제하시겠습니까?")) return;
    try {
      const payload = await Api.delete(`/board/comments/${comment.commentId}`);
      invalidateBoardCache();
      showToast(toast, payload.message);
      await loadComments();
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  commentForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!board.requireLogin(window.location.pathname + window.location.search)) return;
    const content = commentContent.value.trim();
    if (!content) {
      showToast(toast, "댓글 내용을 입력해 주세요.", true);
      return;
    }
    try {
      const payload = await Api.post(`/board/posts/${postId}/comments`, { content });
      invalidateBoardCache();
      commentContent.value = "";
      showToast(toast, payload.message);
      await loadComments();
    } catch (error) {
      showToast(toast, error.message, true);
    }
  });

  if (!session.authenticated) {
    commentLoginNote.textContent = "댓글 작성 시 로그인 화면으로 이동합니다.";
  } else {
    commentLoginNote.textContent = `@${session.loginId || "소셜 계정"}으로 작성합니다.`;
  }

  async function loadPage() {
    if (!postId) {
      renderPostError("유효한 게시글 번호가 없습니다.");
      renderRelatedPosts([]);
      commentForm.hidden = true;
      return;
    }
    try {
      await Promise.all([
        loadPost(),
        loadComments(),
        loadRelatedPosts(),
      ]);
    } catch (error) {
      renderPostError(error.message);
      commentForm.hidden = true;
    }
  }

  loadPage();
})();