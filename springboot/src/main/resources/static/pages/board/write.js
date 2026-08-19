(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  if (!session || !board) return;

  const currentPath = window.location.pathname + window.location.search;
  if (!board.requireLogin(currentPath)) return;

  const writeParams = new URLSearchParams(window.location.search);
  const requestedReturnTo = writeParams.get("returnTo");
  const requestedNewsPageValue = Number.parseInt(writeParams.get("newsPage"), 10);
  const requestedNewsPage = Number.isInteger(requestedNewsPageValue) && requestedNewsPageValue >= 0
    ? requestedNewsPageValue
    : 0;

  const MAX_MEDIA_COUNT = 10;
  const MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  const MAX_VIDEO_BYTES = 100 * 1024 * 1024;
  const WRITE_EMOJIS = (
    "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 " +
    "😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 🤫 🤔 🫡 🤐 🤨 😐 😑 😶 🫥 😶‍🌫️ 😏 😒 🙄 😬 " +
    "😮‍💨 🤥 🫨 🙂‍↔️ 🙂‍↕️ 😌 😔 😪 🤤 😴 🫩 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 😵‍💫 🤯 " +
    "🤠 🥳 🥸 😎 🤓 🧐 😕 🫤 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 " +
    "😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿"
  ).split(" ");
  const IMAGE_EXTENSIONS = new Set([
    "jpg", "jpeg", "png", "gif", "webp", "bmp",
    "tif", "tiff", "avif", "heic", "heif",
  ]);
  const VIDEO_EXTENSIONS = new Set([
    "mp4", "webm", "ogv", "m4v", "mov", "mkv",
    "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "3g2",
  ]);

  const form = document.getElementById("post-editor-form");
  const pageTitle = document.getElementById("write-page-title");
  const modeBadge = document.getElementById("editor-mode-badge");
  const boardTypeSelect = document.getElementById("editor-board-type");
  const categorySelect = document.getElementById("editor-category");
  const titleInput = document.getElementById("editor-post-title");
  const contentInput = document.getElementById("editor-post-content");
  const titleCount = document.getElementById("title-count");
  const contentCount = document.getElementById("content-count");
  const emojiToggle = document.getElementById("editor-emoji-toggle");
  const emojiPanel = document.getElementById("editor-emoji-panel");
  const errorMessage = document.getElementById("editor-error");
  const submitButton = document.getElementById("editor-submit-button");
  const cancelLink = document.getElementById("editor-cancel-link");
  const listLink = document.querySelector("[data-list-link]");
  const mediaInput = document.getElementById("board-media-input");
  const mediaSelectButton = document.getElementById("board-media-select-button");
  const mediaList = document.getElementById("board-media-list");
  const mediaStatus = document.getElementById("board-media-status");

  let postId = board.readPostId();
  let originalPost = null;
  let businessAccessAllowed = false;
  let existingMedia = [];
  let selectedMedia = [];
  let selectedMediaSequence = 0;
  let mediaBusy = false;
  let writeLogoutInFlight = false;
  let editorBaseline = null;
  let allowEditorNavigation = false;
  const removedMediaIds = new Set();

  if (!form) return;

  // 수정 진입 여부는 URL만으로 즉시 알 수 있으므로 상세 API 응답을 기다리지 않고
  // 첫 렌더 단계에서 수정 화면 문구를 먼저 맞춘다. 실제 게시글 로드 후
  // markAsEditMode()가 권한과 카테고리를 기준으로 최종 상태를 다시 확정한다.
  if (postId) {
    const requestedNewsEdit = writeParams.get("from") === "NEWS";
    pageTitle.textContent = requestedNewsEdit ? "가게 소식 수정" : "이야기 수정";
    document.title = `${requestedNewsEdit ? "가게 소식" : "이야기"} 수정 · 푸드덕`;
    modeBadge.textContent = "수정";
    submitButton.textContent = "수정 저장";
  }

  let editorEmojiOpen = false;

  function closeEditorEmojiPanel() {
    if (!emojiPanel || !emojiToggle) return;
    emojiPanel.hidden = true;
    emojiToggle.setAttribute("aria-expanded", "false");
    editorEmojiOpen = false;
  }

  function openEditorEmojiPanel() {
    if (!emojiPanel || !emojiToggle) return;
    emojiPanel.hidden = false;
    emojiToggle.setAttribute("aria-expanded", "true");
    editorEmojiOpen = true;
  }

  function insertEditorEmoji(emoji) {
    if (!contentInput || !emoji) return;

    const value = contentInput.value || "";
    const start = Number.isInteger(contentInput.selectionStart)
      ? contentInput.selectionStart
      : value.length;
    const end = Number.isInteger(contentInput.selectionEnd)
      ? contentInput.selectionEnd
      : start;
    const nextValue = `${value.slice(0, start)}${emoji}${value.slice(end)}`;

    if (contentInput.maxLength > 0 && nextValue.length > contentInput.maxLength) {
      errorMessage.textContent =
        `내용은 최대 ${contentInput.maxLength.toLocaleString("ko-KR")}자까지 입력할 수 있습니다.`;
      return;
    }

    contentInput.value = nextValue;
    const nextCaret = start + emoji.length;
    contentInput.focus({ preventScroll: true });
    contentInput.setSelectionRange(nextCaret, nextCaret);
    contentInput.dispatchEvent(new Event("input", { bubbles: true }));
  }

  function initializeEditorEmojiPicker() {
    if (!emojiToggle || !emojiPanel || !contentInput) return;

    const grid = board.element("div", "comment-emoji-grid");
    WRITE_EMOJIS.forEach((emoji) => {
      const button = board.element("button", "comment-emoji-option", emoji);
      button.type = "button";
      button.setAttribute("aria-label", `${emoji} 이모지 입력`);
      button.addEventListener("click", () => {
        insertEditorEmoji(emoji);
      });
      grid.append(button);
    });

    emojiPanel.replaceChildren(
      board.element("strong", "comment-emoji-panel-title", "이모지"),
      grid,
    );

    emojiToggle.addEventListener("click", (event) => {
      event.stopPropagation();
      if (editorEmojiOpen) closeEditorEmojiPanel();
      else openEditorEmojiPanel();
    });

    emojiPanel.addEventListener("click", (event) => {
      event.stopPropagation();
    });

    document.addEventListener("click", () => {
      if (editorEmojiOpen) closeEditorEmojiPanel();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape" || !editorEmojiOpen) return;
      closeEditorEmojiPanel();
      emojiToggle.focus({ preventScroll: true });
    });
  }

  function safeBoardListReturnPath(value) {
    if (!value) return null;
    try {
      const url = new URL(value, window.location.origin);
      if (url.origin !== window.location.origin) return null;
      if (url.pathname !== "/board") return null;
      return `${url.pathname}${url.search}`;
    } catch (_error) {
      return null;
    }
  }

  const listReturnPath = safeBoardListReturnPath(requestedReturnTo);

  function pathWithListReturn(path) {
    if (!listReturnPath || !path) return path;
    const url = new URL(path, window.location.origin);
    url.searchParams.set("returnTo", listReturnPath);
    return `${url.pathname}${url.search}`;
  }

  function currentEditorSnapshot() {
    return {
      boardType: boardTypeSelect?.value || "GENERAL",
      category: categorySelect?.value || "",
      title: titleInput?.value || "",
      content: contentInput?.value || "",
    };
  }

  function captureEditorBaseline() {
    editorBaseline = currentEditorSnapshot();
  }

  function hasUnsavedEditorChanges() {
    if (!editorBaseline) return false;
    const current = currentEditorSnapshot();
    return (
      current.boardType !== editorBaseline.boardType ||
      current.category !== editorBaseline.category ||
      current.title !== editorBaseline.title ||
      current.content !== editorBaseline.content ||
      selectedMedia.length > 0 ||
      removedMediaIds.size > 0
    );
  }

  function confirmEditorLeave() {
    return (
      !hasUnsavedEditorChanges() ||
      window.confirm("작성 중인 내용이 있습니다. 페이지를 나가시겠습니까?")
    );
  }

  function initializeEditorNavigationGuard() {
    document.addEventListener("click", (event) => {
      if (allowEditorNavigation || event.defaultPrevented) return;
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const link = event.target.closest("a[href]");
      if (!link || link.hasAttribute("download")) return;
      if (link.target && link.target.toLowerCase() !== "_self") return;

      const rawHref = link.getAttribute("href");
      if (!rawHref || rawHref.startsWith("javascript:")) return;

      let targetUrl;
      try {
        targetUrl = new URL(link.href, window.location.href);
      } catch (_error) {
        return;
      }

      const currentUrl = new URL(window.location.href);
      const sameDocument =
        targetUrl.origin === currentUrl.origin &&
        targetUrl.pathname === currentUrl.pathname &&
        targetUrl.search === currentUrl.search;
      if (sameDocument && targetUrl.hash !== currentUrl.hash) return;

      if (!hasUnsavedEditorChanges()) return;

      event.preventDefault();
      if (!confirmEditorLeave()) return;

      // 내부 링크 이동은 직접 확인한 뒤 beforeunload에서 같은 경고가
      // 한 번 더 뜨지 않도록 잠시 허용한다. 실제 이동이 취소되면
      // 다음 이벤트 루프에서 다시 보호 상태로 돌린다.
      allowEditorNavigation = true;
      window.location.assign(targetUrl.href);
      window.setTimeout(() => {
        allowEditorNavigation = false;
      }, 0);
    }, true);
  }

  function initializeWriteLogoutEntryPoint() {
    document.addEventListener("click", async (event) => {
      const button = event.target.closest(".site-header [data-logout]");
      if (!button || !session.authenticated) return;

      // 공통 헤더는 로그아웃 뒤 홈으로 이동한다. 게시글 작성 화면에서는
      // 작성 권한이 사라지므로 현재 게시판 목록으로 돌아가도록 먼저 처리한다.
      event.preventDefault();
      event.stopImmediatePropagation();
      if (writeLogoutInFlight) return;
      if (!confirmEditorLeave()) return;

      writeLogoutInFlight = true;
      button.disabled = true;
      allowEditorNavigation = true;

      try {
        await Api.logout();
      } catch (_error) {
        Api.clearToken();
      } finally {
        window.location.assign(
          listReturnPath || board.listPath(boardTypeSelect?.value || "GENERAL"),
        );
      }
    }, true);
  }

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

  function isNewsPost(post = originalPost) {
    return post?.category === "NEWS";
  }

  function ensureNewsCategoryOption() {
    if ([...categorySelect.options].some((option) => option.value === "NEWS")) {
      return;
    }
    const option = board.element("option", "", "가게 소식");
    option.value = "NEWS";
    categorySelect.append(option);
  }

  function newsSource(post) {
    const hasPublicRestaurant = post?.publicRestaurantId != null;
    const hasOwnedRestaurant = post?.restaurantId != null;
    if (hasPublicRestaurant === hasOwnedRestaurant) return null;
    return hasPublicRestaurant
      ? { source: "public", id: post.publicRestaurantId }
      : { source: "owned", id: post.restaurantId };
  }

  function newsDetailPath(targetPostId) {
    const params = new URLSearchParams({
      postId: String(targetPostId),
      from: "NEWS",
      newsPage: String(requestedNewsPage),
    });
    return `/board/detail?${params.toString()}`;
  }

  function newsWritePath(targetPostId) {
    const params = new URLSearchParams({
      postId: String(targetPostId),
      from: "NEWS",
      newsPage: String(requestedNewsPage),
    });
    return `/board/write?${params.toString()}`;
  }

  function restaurantNewsPath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const params = new URLSearchParams({
      source: target.source,
      id: String(target.id),
      tab: "news",
      newsPage: String(requestedNewsPage),
    });
    return `/restaurant/detail?${params.toString()}`;
  }

  function newsUpdatePath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const restaurantId = encodeURIComponent(target.id);
    const targetPostId = encodeURIComponent(post.postId);
    return target.source === "public"
      ? `/board/posts/restaurants/public/${restaurantId}/news/${targetPostId}`
      : `/board/posts/restaurants/${restaurantId}/news/${targetPostId}`;
  }

  function setEditorBackLink(href, label) {
    listLink.href = href;
    const icon = board.element("span", "material-symbols-rounded", "arrow_back");
    icon.setAttribute("aria-hidden", "true");
    listLink.replaceChildren(icon, document.createTextNode(` ${label}`));
  }

  function updateCounts() {
    titleCount.textContent = String(titleInput.value.length);
    contentCount.textContent = String(contentInput.value.length);
  }

  function setListLinks(boardType) {
    if (isNewsPost()) {
      setEditorBackLink(
        restaurantNewsPath(originalPost) || newsDetailPath(postId),
        "가게 소식으로 돌아가기",
      );
      cancelLink.href = newsDetailPath(postId);
      return;
    }
    const href = listReturnPath || board.listPath(boardType);
    setEditorBackLink(href, "커뮤니티 목록");
    if (!postId) cancelLink.href = href;
  }

  function extensionOf(fileName) {
    const dot = String(fileName || "").lastIndexOf(".");
    return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
  }

  function mediaKind(fileName) {
    const extension = extensionOf(fileName);
    if (IMAGE_EXTENSIONS.has(extension)) return "IMAGE";
    if (VIDEO_EXTENSIONS.has(extension)) return "VIDEO";
    return null;
  }

  function formatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (value < 1024) return `${value}B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)}KB`;
    return `${(value / 1024 / 1024).toFixed(1)}MB`;
  }

  function activeExistingMedia() {
    return existingMedia.filter(
      (media) => !removedMediaIds.has(Number(media.postMediaId)),
    );
  }

  function totalMediaCount() {
    return activeExistingMedia().length + selectedMedia.length;
  }

  function setMediaStatus(message, isError = false) {
    if (!mediaStatus) return;
    mediaStatus.textContent = message || "";
    mediaStatus.classList.toggle("is-error", Boolean(isError));
  }

  function appendPreviewFallback(preview, kind) {
    preview.replaceChildren();
    const icon = board.element(
      "span",
      "material-symbols-rounded",
      kind === "IMAGE" ? "image" : "movie",
    );
    icon.setAttribute("aria-hidden", "true");
    preview.append(icon);
  }

  function createMediaPreview({ kind, source, mimeType, name }) {
    const preview = board.element("div", "board-write-media__preview");
    if (!source) {
      appendPreviewFallback(preview, kind);
      return preview;
    }

    if (kind === "IMAGE") {
      const image = new Image();
      image.src = source;
      image.alt = `${name || "첨부 이미지"} 미리보기`;
      image.loading = "lazy";
      image.addEventListener("error", () => {
        appendPreviewFallback(preview, kind);
      }, { once: true });
      preview.append(image);
      return preview;
    }

    const video = document.createElement("video");
    const canPlay = !mimeType || video.canPlayType(mimeType) !== "";
    if (!canPlay) {
      appendPreviewFallback(preview, kind);
      return preview;
    }
    video.src = source;
    video.controls = true;
    video.preload = "metadata";
    video.addEventListener("error", () => {
      appendPreviewFallback(preview, kind);
    }, { once: true });
    preview.append(video);
    return preview;
  }

  function createMediaItem({
    kind,
    source,
    mimeType,
    name,
    size,
    actionLabel,
    onRemove,
  }) {
    const item = board.element("article", "board-write-media__item");
    item.append(createMediaPreview({ kind, source, mimeType, name }));

    const meta = board.element("div", "board-write-media__meta");
    meta.append(
      board.element("strong", "", name || "이름 없는 첨부파일"),
      board.element(
        "small",
        "",
        `${kind === "IMAGE" ? "사진" : "동영상"} · ${formatBytes(size)}`,
      ),
    );

    const removeButton = board.element(
      "button",
      "button button-sm button-secondary board-write-media__remove",
      actionLabel,
    );
    removeButton.type = "button";
    removeButton.disabled = mediaBusy;
    removeButton.addEventListener("click", onRemove);
    meta.append(removeButton);
    item.append(meta);
    return item;
  }

  function renderMediaList() {
    if (!mediaList) return;
    mediaList.replaceChildren();

    const stored = activeExistingMedia();
    stored.forEach((media) => {
      const kind = media.mediaType === "IMAGE" ? "IMAGE" : "VIDEO";
      mediaList.append(createMediaItem({
        kind,
        source: media.mediaUrl,
        mimeType: media.mimeType,
        name: media.originalName,
        size: media.fileSize,
        actionLabel: "삭제",
        onRemove: () => {
          removedMediaIds.add(Number(media.postMediaId));
          renderMediaList();
        },
      }));
    });

    selectedMedia.forEach((entry) => {
      const kind = mediaKind(entry.file.name);
      mediaList.append(createMediaItem({
        kind,
        source: entry.previewUrl,
        mimeType: entry.file.type,
        name: entry.file.name,
        size: entry.file.size,
        actionLabel: "선택 취소",
        onRemove: () => {
          URL.revokeObjectURL(entry.previewUrl);
          selectedMedia = selectedMedia.filter(
            (candidate) => candidate.key !== entry.key,
          );
          renderMediaList();
        },
      }));
    });

    if (!stored.length && !selectedMedia.length) {
      mediaList.append(
        board.element(
          "p",
          "board-write-media__empty",
          "첨부된 사진이나 동영상이 없습니다.",
        ),
      );
    }

    if (!mediaBusy) {
      setMediaStatus(`${totalMediaCount()}/${MAX_MEDIA_COUNT}개 첨부`);
    }
  }

  function validateSelectedFile(file) {
    const kind = mediaKind(file.name);
    if (!kind) {
      return `${file.name}: 지원하지 않는 사진·동영상 형식입니다.`;
    }
    if (file.size < 1) {
      return `${file.name}: 비어 있는 파일입니다.`;
    }
    if (kind === "IMAGE" && file.size > MAX_IMAGE_BYTES) {
      return `${file.name}: 사진 20MB 제한을 초과했습니다.`;
    }
    if (kind === "VIDEO" && file.size > MAX_VIDEO_BYTES) {
      return `${file.name}: 동영상 100MB 제한을 초과했습니다.`;
    }
    const duplicated = selectedMedia.some(
      (entry) =>
        entry.file.name === file.name &&
        entry.file.size === file.size &&
        entry.file.lastModified === file.lastModified,
    );
    if (duplicated) {
      return `${file.name}: 이미 선택한 파일입니다.`;
    }
    return null;
  }

  function addSelectedFiles(files) {
    const errors = [];
    for (const file of files) {
      if (totalMediaCount() >= MAX_MEDIA_COUNT) {
        errors.push(`첨부파일은 최대 ${MAX_MEDIA_COUNT}개까지 등록할 수 있습니다.`);
        break;
      }
      const validationError = validateSelectedFile(file);
      if (validationError) {
        errors.push(validationError);
        continue;
      }
      selectedMedia.push({
        key: ++selectedMediaSequence,
        file,
        previewUrl: URL.createObjectURL(file),
      });
    }
    renderMediaList();
    if (errors.length) {
      setMediaStatus(errors.join(" "), true);
    }
  }

  function setMediaBusy(busy) {
    mediaBusy = busy;
    if (mediaSelectButton) mediaSelectButton.disabled = busy;
    if (mediaInput) mediaInput.disabled = busy;
    renderMediaList();
  }

  function markAsEditMode(savedPost) {
    postId = savedPost.postId;
    originalPost = savedPost;
    const newsPost = isNewsPost(savedPost);
    if (newsPost) ensureNewsCategoryOption();
    pageTitle.textContent = newsPost ? "가게 소식 수정" : "이야기 수정";
    document.title = `${newsPost ? "가게 소식" : "이야기"} 수정 · 푸드덕`;
    modeBadge.textContent = "수정";
    submitButton.textContent = "수정 저장";
    boardTypeSelect.disabled = newsPost || !session.isAdmin;
    categorySelect.disabled = newsPost;
    cancelLink.href = newsPost
      ? newsDetailPath(postId)
      : pathWithListReturn(board.detailPath(postId));
    window.history.replaceState(
      {},
      "",
      newsPost
        ? newsWritePath(postId)
        : pathWithListReturn(board.writePath(savedPost.boardType, postId)),
    );
  }

  async function loadForEdit() {
    try {
      const payload = await Api.get(`/board/posts/${postId}`);
      originalPost = payload.data;
      if (isNewsPost()) {
        if (originalPost.newsManageableByCurrentUser !== true) {
          throw new Error("이 가게 소식을 수정할 권한이 없습니다.");
        }
      } else {
        if (!originalPost?.ownedByCurrentUser && !session.isAdmin) {
          throw new Error("본인이 작성한 게시글만 수정할 수 있습니다.");
        }
        if (
          originalPost.boardType === "BUSINESS" &&
          !businessAccessAllowed
        ) {
          throw new Error("사업자 커뮤니티 게시글을 수정할 권한이 없습니다.");
        }
      }

      existingMedia = Array.isArray(originalPost.media)
        ? [...originalPost.media]
        : [];
      markAsEditMode(originalPost);
      boardTypeSelect.value = originalPost.boardType;
      categorySelect.value = originalPost.category;
      titleInput.value = originalPost.title || "";
      contentInput.value = originalPost.content || "";
      setListLinks(originalPost.boardType);
      updateCounts();
      renderMediaList();
    } catch (error) {
      form.classList.add("is-unavailable");
      errorMessage.textContent = error.message || "게시글을 불러오지 못했습니다.";
      submitButton.disabled = true;
      if (mediaSelectButton) mediaSelectButton.disabled = true;
      if (emojiToggle) emojiToggle.disabled = true;
      closeEditorEmojiPanel();
    }
  }

  function uploadMediaFile(targetPostId, entry, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const url =
        `/api/board/posts/${encodeURIComponent(targetPostId)}/media`;

      xhr.open("POST", url, true);
      xhr.withCredentials = true;
      xhr.setRequestHeader("Accept", "application/json");
      xhr.setRequestHeader(
        "Content-Type",
        entry.file.type || "application/octet-stream",
      );
      xhr.setRequestHeader(
        "X-File-Name",
        encodeURIComponent(entry.file.name),
      );

      const token = Api.getToken();
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);

      xhr.upload.addEventListener("progress", (event) => {
        if (typeof onProgress !== "function") return;
        onProgress(
          Number(event.loaded) || 0,
          event.lengthComputable ? Number(event.total) || 0 : 0,
        );
      });

      xhr.addEventListener("load", () => {
        const responseType = xhr.getResponseHeader("content-type") || "";
        let payload = xhr.responseText;
        if (responseType.includes("application/json")) {
          try {
            payload = JSON.parse(xhr.responseText || "null");
          } catch (_error) {
            payload = null;
          }
        }

        if (xhr.status < 200 || xhr.status >= 300) {
          if (xhr.status === 401) Api.clearToken();
          const message =
            typeof payload === "object" && payload
              ? payload.message
              : `첨부파일 업로드에 실패했습니다. (${xhr.status})`;
          reject(
            new Error(message || "첨부파일 업로드에 실패했습니다."),
          );
          return;
        }
        resolve(payload);
      });

      xhr.addEventListener("error", () => {
        reject(new Error("첨부파일을 서버로 전송하지 못했습니다."));
      });
      xhr.addEventListener("abort", () => {
        reject(new Error("첨부파일 업로드가 취소되었습니다."));
      });

      xhr.send(entry.file);
    });
  }

  async function saveMediaChanges(targetPostId) {
    const failures = [];
    setMediaBusy(true);

    const mediaIdsToDelete = [...removedMediaIds];
    for (let index = 0; index < mediaIdsToDelete.length; index += 1) {
      const mediaId = mediaIdsToDelete[index];
      setMediaStatus(
        `기존 첨부파일 삭제 중 (${index + 1}/${mediaIdsToDelete.length})`,
      );
      try {
        await Api.delete(`/board/posts/${targetPostId}/media/${mediaId}`);
        removedMediaIds.delete(mediaId);
        existingMedia = existingMedia.filter(
          (media) => Number(media.postMediaId) !== Number(mediaId),
        );
      } catch (error) {
        // 실패한 삭제 요청은 남겨 두어 다음 저장 때 다시 시도할 수 있게 한다.
        failures.push(`삭제 실패: ${error.message}`);
      }
    }

    const filesToUpload = [...selectedMedia];
    for (let index = 0; index < filesToUpload.length; index += 1) {
      const entry = filesToUpload[index];
      setMediaStatus(
        `${entry.file.name} 업로드 중 (${index + 1}/${filesToUpload.length})`,
      );
      try {
        const payload = await uploadMediaFile(
          targetPostId,
          entry,
          (loaded, total) => {
            if (total > 0 && loaded >= total) {
              setMediaStatus(
                `${entry.file.name} 전송 완료 · 서버 저장 중 ` +
                  `(${index + 1}/${filesToUpload.length})`,
              );
              return;
            }
            const progress = total > 0
              ? `${Math.min(99, Math.round((loaded / total) * 100))}%`
              : formatBytes(loaded);
            setMediaStatus(
              `${entry.file.name} 업로드 중 ${progress} ` +
                `(${index + 1}/${filesToUpload.length})`,
            );
          },
        );
        existingMedia.push(payload.data);
        URL.revokeObjectURL(entry.previewUrl);
        selectedMedia = selectedMedia.filter(
          (candidate) => candidate.key !== entry.key,
        );
      } catch (error) {
        failures.push(`${entry.file.name}: ${error.message}`);
      }
    }

    setMediaBusy(false);
    renderMediaList();
    return failures;
  }

  initializeEditorEmojiPicker();

  titleInput.addEventListener("input", updateCounts);
  contentInput.addEventListener("input", updateCounts);
  boardTypeSelect.addEventListener("change", () => {
    if (!postId) setListLinks(boardTypeSelect.value);
  });

  mediaSelectButton?.addEventListener("click", () => {
    mediaInput?.click();
  });
  mediaInput?.addEventListener("change", () => {
    addSelectedFiles([...mediaInput.files]);
    mediaInput.value = "";
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    errorMessage.textContent = "";
    const newsEdit = Boolean(postId && isNewsPost());
    const title = titleInput.value.trim();
    const content = contentInput.value.trim();
    const body = newsEdit
      ? { title, content }
      : {
        boardType: boardTypeSelect.value,
        category: categorySelect.value,
        restaurantId: originalPost?.restaurantId || null,
        title,
        content,
      };
    if (!body.title || !body.content) {
      errorMessage.textContent = "제목과 내용을 입력해 주세요.";
      return;
    }
    if (totalMediaCount() > MAX_MEDIA_COUNT) {
      errorMessage.textContent =
        `첨부파일은 최대 ${MAX_MEDIA_COUNT}개까지 등록할 수 있습니다.`;
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = postId ? "저장 중..." : "등록 중...";
    if (emojiToggle) emojiToggle.disabled = true;
    closeEditorEmojiPanel();
    try {
      const updatePath = newsEdit ? newsUpdatePath(originalPost) : null;
      if (newsEdit && !updatePath) {
        throw new Error("가게 소식의 식당 정보를 확인할 수 없습니다.");
      }
      const payload = postId
        ? await Api.put(updatePath || `/board/posts/${postId}`, body)
        : await Api.post("/board/posts", body);
      const savedPostId = payload.data?.postId ?? postId;

      if (!postId) {
        existingMedia = Array.isArray(payload.data.media)
          ? [...payload.data.media]
          : [];
        markAsEditMode(payload.data);
      } else if (newsEdit) {
        originalPost = {
          ...originalPost,
          ...(payload.data || {}),
          postId: savedPostId,
          title,
          content,
        };
      } else {
        originalPost = payload.data;
      }

      // 본문 저장은 이미 끝났으므로 이후 첨부 처리만 실패하더라도
      // 제목/내용을 다시 미저장 상태로 보지 않는다.
      captureEditorBaseline();

      const failures = await saveMediaChanges(savedPostId);
      board.invalidateBoardCache();

      if (failures.length) {
        errorMessage.textContent =
          `게시글은 저장되었지만 일부 첨부 처리가 실패했습니다. ${failures.join(" ")}`;
        submitButton.disabled = false;
        submitButton.textContent = "수정 저장";
        if (emojiToggle) emojiToggle.disabled = false;
        return;
      }

      allowEditorNavigation = true;
      window.location.assign(
        newsEdit
          ? newsDetailPath(savedPostId)
          : pathWithListReturn(board.detailPath(savedPostId)),
      );
    } catch (error) {
      errorMessage.textContent =
        error.message || "게시글을 저장하지 못했습니다.";
      submitButton.disabled = false;
      submitButton.textContent = postId ? "수정 저장" : "등록하기";
      if (emojiToggle) emojiToggle.disabled = false;
      setMediaBusy(false);
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
    renderMediaList();
    submitButton.disabled = false;

    if (postId) await loadForEdit();
    captureEditorBaseline();
  }

  window.addEventListener("beforeunload", (event) => {
    if (allowEditorNavigation || !hasUnsavedEditorChanges()) return;
    event.preventDefault();
    // 최신 브라우저는 사용자 지정 문구 대신 자체 경고문을 표시한다.
    // returnValue도 함께 설정해 새로고침/뒤로가기 보호를 유지한다.
    event.returnValue = true;
  });

  window.addEventListener("pagehide", () => {
    selectedMedia.forEach((entry) => URL.revokeObjectURL(entry.previewUrl));
  });

  initializeEditorNavigationGuard();
  initializeWriteLogoutEntryPoint();
  initializeEditor();
})();
