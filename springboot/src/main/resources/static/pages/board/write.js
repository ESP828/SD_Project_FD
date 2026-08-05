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
  let originalPost = null;
  let businessAccessAllowed = false;

  if (!form) return;

  function populateOptions() {
    boardTypeSelect.replaceChildren();
    const general = board.element("option", "", "일반 커뮤니티");
    general.value = "GENERAL";
    boardTypeSelect.append(general);
    if (businessAccessAllowed) {
      const business = board.element("option", "", "사업자 커뮤니티");
      business.value = "BUSINESS";
      boardTypeSelect.append(business);
    }

    categorySelect.replaceChildren();
    const categories = [
      ["GENERAL", "자유 이야기"],
      ["RECOMMENDATION", "맛집 추천"],
      ["REVIEW", "방문 후기"],
      ["QUESTION", "질문"],
      ["TRAVEL", "맛집 여행"],
    ];
    if (session.isAdmin) categories.unshift(["NOTICE", "공지"]);
    categories.forEach(([value, label]) => {
      const option = board.element("option", "", label);
      option.value = value;
      categorySelect.append(option);
    });
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

  async function loadForEdit() {
    try {
      const payload = await Api.get(`/board/posts/${postId}`);
      originalPost = payload.data;
      if (!originalPost?.ownedByCurrentUser && !session.isAdmin) {
        throw new Error("본인이 작성한 게시글만 수정할 수 있습니다.");
      }
      if (
        originalPost.boardType === "BUSINESS" &&
        !businessAccessAllowed
      ) {
        throw new Error("사업자 커뮤니티 게시글을 수정할 권한이 없습니다.");
      }

      pageTitle.textContent = "이야기 수정";
      document.title = "이야기 수정 · 푸드덕";
      modeBadge.textContent = "수정";
      submitButton.textContent = "수정 저장";
      boardTypeSelect.value = originalPost.boardType;
      categorySelect.value = originalPost.category;
      titleInput.value = originalPost.title || "";
      contentInput.value = originalPost.content || "";
      boardTypeSelect.disabled = !session.isAdmin;
      cancelLink.href = board.detailPath(postId);
      setListLinks(originalPost.boardType);
      updateCounts();
    } catch (error) {
      form.classList.add("is-unavailable");
      errorMessage.textContent = error.message || "게시글을 불러오지 못했습니다.";
      submitButton.disabled = true;
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

  async function initializeEditor() {
    submitButton.disabled = true;
    businessAccessAllowed = await board.canUseBusinessBoard();
    populateOptions();

    const requestedBoardType =
      new URLSearchParams(window.location.search).get("boardType") === "BUSINESS" &&
      businessAccessAllowed
        ? "BUSINESS"
        : "GENERAL";
    boardTypeSelect.value = requestedBoardType;
    setListLinks(requestedBoardType);
    updateCounts();
    submitButton.disabled = false;

    if (postId) await loadForEdit();
  }

  initializeEditor();
})();
