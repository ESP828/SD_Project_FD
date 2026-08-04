(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  if (!session || !board) return;

  const currentPath = window.location.pathname + window.location.search;
  if (!board.requireLogin(currentPath)) return;

  const form = document.getElementById("post-editor-form");
  const pageTitle = document.getElementById("write-page-title");
  const modeBadge = document.getElementById("editor-mode-badge");
  const boardTypeSelect = document.getElementById("editor-board-type");
  const categorySelect = document.getElementById("editor-category");
  const titleInput = document.getElementById("editor-post-title");
  const contentInput = document.getElementById("editor-post-content");
  const titleCount = document.getElementById("title-count");
  const contentCount = document.getElementById("content-count");
  const errorMessage = document.getElementById("editor-error");
  const submitButton = document.getElementById("editor-submit-button");
  const cancelLink = document.getElementById("editor-cancel-link");
  const listLink = document.querySelector("[data-list-link]");
  const postId = board.readPostId();
  const boardTypeLabels = {
    GENERAL: "일반 커뮤니티",
    BUSINESS: "사업자 커뮤니티",
  };
  const categoryLabels = {
    GENERAL: "자유 이야기",
    NOTICE: "공지",
    RECOMMENDATION: "맛집 추천",
    REVIEW: "방문 후기",
    QUESTION: "질문",
    TRAVEL: "맛집 여행",
  };
  let originalPost = null;
  let editorOptions = null;

  if (!form) return;
  submitButton.disabled = true;

  function normalizeOptions(value) {
    const uniqueKnownValues = (values, labels) => Array.from(new Set(
      (Array.isArray(values) ? values : []).filter((item) =>
        Object.prototype.hasOwnProperty.call(labels, item),
      ),
    ));
    const boardTypes = uniqueKnownValues(value?.boardTypes, boardTypeLabels);
    const categories = uniqueKnownValues(value?.categories, categoryLabels);
    if (!boardTypes.length || !categories.length) {
      throw new Error("작성 가능한 게시판 정보를 확인하지 못했습니다.");
    }
    return {
      boardTypes,
      categories,
      canManageAllPosts: value?.canManageAllPosts === true,
    };
  }

  function appendOption(select, value, label) {
    const option = board.element("option", "", label);
    option.value = value;
    select.append(option);
  }

  function populateOptions(options) {
    boardTypeSelect.replaceChildren();
    options.boardTypes.forEach((value) =>
      appendOption(boardTypeSelect, value, boardTypeLabels[value]),
    );

    categorySelect.replaceChildren();
    options.categories.forEach((value) =>
      appendOption(categorySelect, value, categoryLabels[value]),
    );
  }

  function updateCounts() {
    titleCount.textContent = String(titleInput.value.length);
    contentCount.textContent = String(contentInput.value.length);
  }

  function setListLinks(boardType) {
    const href = board.listPath(boardType);
    listLink.href = href;
    if (!postId) cancelLink.href = href;
  }

  function setUnavailable(message) {
    form.classList.add("is-unavailable");
    errorMessage.textContent = message || "게시글 작성 권한을 확인하지 못했습니다.";
    submitButton.disabled = true;
  }

  async function loadForEdit() {
    const payload = await Api.get(`/board/posts/${postId}`);
    originalPost = payload.data;
    if (!originalPost?.ownedByCurrentUser && !editorOptions.canManageAllPosts) {
      throw new Error("본인이 작성한 게시글만 수정할 수 있습니다.");
    }
    if (!editorOptions.boardTypes.includes(originalPost.boardType)) {
      throw new Error("이 게시 공간의 글을 수정할 권한이 없습니다.");
    }
    if (!editorOptions.categories.includes(originalPost.category)) {
      throw new Error(
        originalPost.category === "NOTICE"
          ? "관리자만 공지 게시글을 수정할 수 있습니다."
          : "이 카테고리의 글을 수정할 권한이 없습니다.",
      );
    }

    pageTitle.textContent = "이야기 수정";
    document.title = "이야기 수정 · 푸드덕";
    modeBadge.textContent = "수정";
    submitButton.textContent = "수정 저장";
    boardTypeSelect.value = originalPost.boardType;
    categorySelect.value = originalPost.category;
    titleInput.value = originalPost.title || "";
    contentInput.value = originalPost.content || "";
    boardTypeSelect.disabled = !editorOptions.canManageAllPosts;
    cancelLink.href = board.detailPath(postId);
    setListLinks(originalPost.boardType);
    updateCounts();
  }

  async function initialize() {
    try {
      const payload = await Api.get("/board/posts/editor-options");
      editorOptions = normalizeOptions(payload.data);
      populateOptions(editorOptions);

      const requestedBoardType = new URLSearchParams(window.location.search)
        .get("boardType");
      boardTypeSelect.value = editorOptions.boardTypes.includes(requestedBoardType)
        ? requestedBoardType
        : editorOptions.boardTypes[0];
      setListLinks(boardTypeSelect.value);
      updateCounts();

      if (postId) {
        await loadForEdit();
      }
      submitButton.disabled = false;
    } catch (error) {
      setUnavailable(error.message);
    }
  }

  titleInput.addEventListener("input", updateCounts);
  contentInput.addEventListener("input", updateCounts);
  boardTypeSelect.addEventListener("change", () => {
    if (!postId) setListLinks(boardTypeSelect.value);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    errorMessage.textContent = "";
    const body = {
      boardType: boardTypeSelect.value,
      category: categorySelect.value,
      restaurantId: originalPost?.restaurantId || null,
      title: titleInput.value.trim(),
      content: contentInput.value.trim(),
    };
    if (!editorOptions?.boardTypes.includes(body.boardType)) {
      errorMessage.textContent = "사용할 수 없는 게시 공간입니다.";
      return;
    }
    if (!editorOptions.categories.includes(body.category)) {
      errorMessage.textContent = body.category === "NOTICE"
        ? "관리자만 공지 카테고리를 사용할 수 있습니다."
        : "사용할 수 없는 카테고리입니다.";
      return;
    }
    if (!body.title || !body.content) {
      errorMessage.textContent = "제목과 내용을 입력해 주세요.";
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = postId ? "저장 중..." : "등록 중...";
    try {
      const payload = postId
        ? await Api.put(`/board/posts/${postId}`, body)
        : await Api.post("/board/posts", body);
      window.location.assign(board.detailPath(payload.data.postId));
    } catch (error) {
      errorMessage.textContent = error.message || "게시글을 저장하지 못했습니다.";
      submitButton.disabled = false;
      submitButton.textContent = postId ? "수정 저장" : "등록하기";
    }
  });

  initialize();
})();
