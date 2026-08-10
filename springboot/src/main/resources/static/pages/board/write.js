(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  if (!session || !board) return;

  const currentPath = window.location.pathname + window.location.search;
  if (!board.requireLogin(currentPath)) return;

  const MAX_MEDIA_COUNT = 10;
  const MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  const MAX_VIDEO_BYTES = 100 * 1024 * 1024;
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
  const removedMediaIds = new Set();

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
    pageTitle.textContent = "이야기 수정";
    document.title = "이야기 수정 · 푸드덕";
    modeBadge.textContent = "수정";
    submitButton.textContent = "수정 저장";
    boardTypeSelect.disabled = !session.isAdmin;
    cancelLink.href = board.detailPath(postId);
    window.history.replaceState(
      {},
      "",
      board.writePath(savedPost.boardType, postId),
    );
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
        removedMediaIds.delete(mediaId);
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
    if (totalMediaCount() > MAX_MEDIA_COUNT) {
      errorMessage.textContent =
        `첨부파일은 최대 ${MAX_MEDIA_COUNT}개까지 등록할 수 있습니다.`;
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = postId ? "저장 중..." : "등록 중...";
    try {
      const payload = postId
        ? await Api.put(`/board/posts/${postId}`, body)
        : await Api.post("/board/posts", body);

      if (!postId) {
        existingMedia = Array.isArray(payload.data.media)
          ? [...payload.data.media]
          : [];
        markAsEditMode(payload.data);
      } else {
        originalPost = payload.data;
      }

      const failures = await saveMediaChanges(payload.data.postId);
      board.invalidateBoardCache();

      if (failures.length) {
        errorMessage.textContent =
          `게시글은 저장되었지만 일부 첨부 처리가 실패했습니다. ${failures.join(" ")}`;
        submitButton.disabled = false;
        submitButton.textContent = "수정 저장";
        return;
      }

      window.location.assign(board.detailPath(payload.data.postId));
    } catch (error) {
      errorMessage.textContent =
        error.message || "게시글을 저장하지 못했습니다.";
      submitButton.disabled = false;
      submitButton.textContent = postId ? "수정 저장" : "등록하기";
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
  }

  window.addEventListener("beforeunload", () => {
    selectedMedia.forEach((entry) => URL.revokeObjectURL(entry.previewUrl));
  });

  initializeEditor();
})();