(() => {
  const emojis = window.FooduckEmojis;
  const loading = document.getElementById("store-loading");
  const errorView = document.getElementById("store-error");
  const errorMessage = document.getElementById("store-error-message");
  const content = document.getElementById("store-content");
  if (!loading || !errorView || !content) return;

  function ensureStoreScrollTopButton() {
    if (document.querySelector(".board-scroll-top")) return;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "board-scroll-top";
    button.textContent = "↑";
    button.title = "맨 위로 이동";
    button.setAttribute("aria-label", "맨 위로 이동");
    button.hidden = true;
    document.body.append(button);

    let ticking = false;
    let isVisible = false;
    const updateVisibility = () => {
      const nextVisible = window.scrollY > 450;
      if (nextVisible !== isVisible) {
        isVisible = nextVisible;
        button.hidden = !nextVisible;
      }
      ticking = false;
    };

    window.addEventListener(
      "scroll",
      () => {
        if (ticking) return;
        ticking = true;
        window.requestAnimationFrame(updateVisibility);
      },
      { passive: true },
    );

    button.addEventListener("click", () => {
      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      window.scrollTo({
        top: 0,
        behavior: reduceMotion ? "auto" : "smooth",
      });
    });

    updateVisibility();
  }

  ensureStoreScrollTopButton();

  const params = new URLSearchParams(window.location.search);
  const source = params.get("source") === "public" ? "public" : "owned";
  const id = params.get("id");
  const returnTo = safeSearchReturnPath(params.get("returnTo"));
  const requestedTab = params.get("tab");
  const requestedNewsPageValue = Number.parseInt(params.get("newsPage"), 10);
  const requestedNewsPage = Number.isInteger(requestedNewsPageValue) && requestedNewsPageValue >= 0
    ? requestedNewsPageValue
    : 0;

  if (!id) {
    showError("가게 정보를 찾을 수 없습니다.");
    return;
  }

  function safeSearchReturnPath(value) {
    if (!value) return null;
    try {
      const target = new URL(value, window.location.origin);
      const isSearchPath = target.pathname === "/search"
        || target.pathname === "/pages/search/index.html";
      if (target.origin !== window.location.origin || !isSearchPath) return null;
      return `${target.pathname}${target.search}${target.hash}`;
    } catch (_error) {
      return null;
    }
  }

  function cameFromSearchPage() {
    return safeSearchReturnPath(document.referrer) !== null;
  }

  const backButton = document.getElementById("store-back-button");
  if (backButton) {
    backButton.addEventListener("click", () => {
      if (window.history.length > 1 && cameFromSearchPage()) {
        window.history.back();
      } else if (returnTo) {
        window.location.assign(returnTo);
      } else if (window.history.length > 1) {
        window.history.back();
      } else {
        window.location.href = "/search";
      }
    });
  }

  const badgesEl = document.getElementById("store-badges");
  const nameEl = document.getElementById("store-name");
  const ownedBadge = document.getElementById("store-owned-badge");
  const addressEl = document.getElementById("store-address");
  const favoriteBtn = document.getElementById("store-favorite-btn");
  const statsRow = document.getElementById("store-stats-row");
  const tabsEl = document.getElementById("store-tabs");
  const basicInfo = document.getElementById("store-basic-info");
  const sourceNote = document.getElementById("store-source-note");

  const panels = {
    news: document.getElementById("tab-news"),
    menu: document.getElementById("tab-menu"),
    review: document.getElementById("tab-review"),
    info: document.getElementById("tab-info"),
  };

  function showError(message) {
    loading.hidden = true;
    errorView.hidden = false;
    errorMessage.textContent = message;
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    }[char]));
  }

  function customEmojiButtonsHtml(buttonClass, dataAttribute) {
    if (!emojis) return "";
    return emojis.items.map((emoji) => `
      <button type="button" class="${buttonClass}" ${dataAttribute}="${escapeHtml(emoji.code)}"
              aria-label="Pepe ${escapeHtml(emoji.label)} 이모지 입력" title="Pepe ${escapeHtml(emoji.label)}">
        <img class="fooduck-custom-emoji-picker-image" src="${escapeHtml(emoji.src)}"
             alt="" loading="lazy" decoding="async">
      </button>
    `).join("");
  }

  function renderCustomEmojiText(target, value) {
    if (!target) return;
    if (emojis) emojis.renderText(target, value);
    else target.textContent = String(value ?? "");
  }

  function formatDate(value, pattern = { year: "numeric", month: "2-digit", day: "2-digit" }) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("ko-KR", pattern).format(date);
  }

  function formatDataYm(dataYm) {
    if (!dataYm || dataYm.length !== 6) return "-";
    return `${dataYm.slice(0, 4)}.${dataYm.slice(4, 6)}`;
  }

  const TAB_LABELS = { menu: "메뉴", news: "소식", review: "리뷰", info: "정보" };
  const loadedTabs = new Set();
  let activeTab = "menu";
  let tabOrder = [];
  let storeId = null;
  let isOwner = false;
  let newsPage = requestedTab === "news" ? requestedNewsPage : 0;
  let reviewPage = 0;
  const REVIEW_PAGE_SIZE = 5;
  const REVIEW_MAX_MEDIA_COUNT = 10;
  const REVIEW_MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  const REVIEW_MAX_VIDEO_BYTES = 100 * 1024 * 1024;
  const REVIEW_IMAGE_EXTENSIONS = new Set([
    "jpg", "jpeg", "png", "gif", "webp", "bmp",
    "tif", "tiff", "avif", "heic", "heif",
  ]);
  const REVIEW_VIDEO_EXTENSIONS = new Set([
    "mp4", "webm", "ogv", "m4v", "mov", "mkv",
    "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "3g2",
  ]);
  const NEWS_PAGE_SIZE = 4;
  const NEWS_COMMENT_PAGE_SIZE = 5;
  const NEWS_COMMENT_ALL_PAGE_SIZE = 100;
  const NEWS_COMMENT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
  const NEWS_COMMENT_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
  ]);
  const NEWS_COMMENT_IMAGE_NAME_PATTERN = /\.(?:jpe?g|png|gif|webp)$/i;
  const NEWS_MAX_MEDIA_COUNT = 10;
  const NEWS_MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  const NEWS_MAX_VIDEO_BYTES = 100 * 1024 * 1024;
  const NEWS_IMAGE_EXTENSIONS = new Set([
    "jpg", "jpeg", "png", "gif", "webp", "bmp",
    "tif", "tiff", "avif", "heic", "heif",
  ]);
  const NEWS_VIDEO_EXTENSIONS = new Set([
    "mp4", "webm", "ogv", "m4v", "mov", "mkv",
    "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "3g2",
  ]);
  let newsSelectedMedia = [];
  let newsSelectedMediaSequence = 0;
  let newsMediaBusy = false;
  let newsCreatedPostId = null;
  let newsRequestGeneration = 0;
  const newsCommentStates = new Map();
  const newsCommentEditInFlight = new Set();
  const newsCommentDeleteInFlight = new Set();
  const newsMediaPollTimers = new Map();
  const session = window.FooduckSession || {};
  let isLoggedIn = Boolean(session.authenticated);
  let isAdmin = Boolean(session.isAdmin);
  let newsCommentAuthPopupController = null;
  let pendingNewsCommentLoginAction = null;
  let newsSummaryResizeTicking = false;
  let sessionNicknamePromise = null;

  async function hydrateSessionNickname() {
    if (!session.authenticated || session.nickname) return session.nickname || null;
    if (sessionNicknamePromise) return sessionNicknamePromise;
    sessionNicknamePromise = Api.get("/mypage/overview")
      .then((payload) => {
        const nickname = String(payload?.data?.nickname || "").trim();
        if (nickname) session.nickname = nickname;
        return session.nickname || null;
      })
      .catch(() => null)
      .finally(() => {
        sessionNicknamePromise = null;
      });
    return sessionNicknamePromise;
  }

  window.addEventListener("resize", () => {
    if (newsSummaryResizeTicking) return;
    newsSummaryResizeTicking = true;
    window.requestAnimationFrame(() => {
      updateNewsSummaryTruncation();
      newsSummaryResizeTicking = false;
    });
  }, { passive: true });

  function renderTabs(order) {
    tabOrder = order;
    tabsEl.innerHTML = order.map((key) => `
      <button type="button" class="store-tab${key === activeTab ? " is-active" : ""}" data-tab="${key}">${TAB_LABELS[key]}</button>
    `).join("");
    tabsEl.querySelectorAll("[data-tab]").forEach((button) => {
      button.addEventListener("click", () => activateTab(button.dataset.tab));
    });
  }

  function activateTab(key) {
    if (!tabOrder.includes(key)) return;
    const previousTab = activeTab;
    activeTab = key;
    tabsEl.querySelectorAll("[data-tab]").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.tab === key);
    });
    Object.entries(panels).forEach(([panelKey, panel]) => {
      panel.hidden = panelKey !== key;
    });
    if (previousTab === "news" && key !== "news") {
      clearNewsMediaPolls();
    }
    if (!loadedTabs.has(key)) {
      loadedTabs.add(key);
      loadTabContent(key);
    } else if (key === "news") {
      scheduleNewsSummaryTruncationCheck();
      resumeNewsMediaPolling();
    }
  }

  function loadTabContent(key) {
    if (key === "menu") loadMenu();
    if (key === "review") loadReviews();
    if (key === "news") loadNews();
  }

  async function loadMenu() {
    panels.menu.innerHTML = '<div class="store-empty">메뉴를 불러오는 중입니다.</div>';
    try {
      const path = source === "public"
        ? `/public/map/restaurants/${storeId}/menu`
        : `/public/restaurants/${storeId}/menu`;
      const response = await Api.get(path, { auth: false });
      const items = response.data || [];
      renderMenuPanel(items);
    } catch (error) {
      panels.menu.innerHTML = `<div class="store-empty">${error.message || "메뉴를 불러오지 못했습니다."}</div>`;
    }
  }

  function renderMenuPanel(items) {
    if (items.length === 0) {
      const emptyMessage = source === "public"
        ? "등록된 메뉴가 없습니다.<br>사업자가 푸드덕에 가게를 직접 등록하면 정확한 메뉴를 볼 수 있어요."
        : "등록된 메뉴가 없습니다.";
      panels.menu.innerHTML = `<div class="store-section-card"><div class="store-empty">${emptyMessage}</div></div>`;
      return;
    }
    const disclaimer = source === "public"
      ? '<p class="store-menu-disclaimer">* 공공데이터 기반 예시 메뉴로 실제 메뉴·가격과 다를 수 있습니다.</p>'
      : "";
    panels.menu.innerHTML = `
      <div class="store-section-card">
        <h2>전체 메뉴</h2>
        ${disclaimer}
        <div class="store-menu-list">
          ${items.map(menuItemHtml).join("")}
        </div>
      </div>
    `;
  }

  function menuItemHtml(item) {
    const price = item.price != null ? `${item.price.toLocaleString("ko-KR")}원` : "가격 미정";
    const badge = item.representative ? '<span class="store-menu-representative">대표</span>' : "";
    const soldOut = item.status === "SOLD_OUT" ? " (품절)" : "";
    return `
      <div class="store-menu-item">
        <span>
          <span class="store-menu-name">${badge}${escapeHtml(item.name)}${soldOut}</span>
          ${item.description ? `<span class="store-menu-desc">${escapeHtml(item.description)}</span>` : ""}
        </span>
        <span class="store-menu-price">${price}</span>
      </div>
    `;
  }

  function reviewEmojiPanelHtml() {
    return `
      <div class="store-review-emoji-panel" data-review-emoji-panel hidden role="group" aria-label="Pepe 이모지 선택">
        ${customEmojiButtonsHtml("store-review-emoji-option", "data-review-emoji")}
      </div>
    `;
  }

  function reviewMediaGalleryHtml(mediaItems) {
    const items = Array.isArray(mediaItems) ? mediaItems : [];
    if (items.length === 0) return "";
    return `
      <div class="store-review-media-gallery">
        ${items.map((media) => {
          const url = escapeHtml(media?.url || "");
          const name = escapeHtml(media?.originalName || "리뷰 첨부파일");
          if (media?.mediaType === "IMAGE") {
            return `<button type="button" class="store-review-media-item is-image" data-review-image-viewer aria-label="${name} 크게 보기">
              <img src="${url}" alt="${name}" loading="lazy">
            </button>`;
          }
          return `<div class="store-review-media-item is-video">
            <video src="${url}" controls preload="metadata" aria-label="${name}"></video>
          </div>`;
        }).join("")}
      </div>
    `;
  }

  function reviewExistingMediaEditorHtml(mediaItems) {
    const items = Array.isArray(mediaItems) ? mediaItems : [];
    if (items.length === 0) return "";
    return items.map((media) => {
      const id = Number(media?.reviewMediaId);
      if (!Number.isSafeInteger(id) || id <= 0) return "";
      const url = escapeHtml(media?.url || "");
      const name = escapeHtml(media?.originalName || "첨부파일");
      const preview = media?.mediaType === "IMAGE"
        ? `<img src="${url}" alt="${name}" loading="lazy">`
        : `<video src="${url}" preload="metadata" muted aria-label="${name}"></video>`;
      return `
        <div class="store-review-media-edit-item" data-review-existing-media-item data-review-media-id="${id}">
          ${preview}
          <div class="store-review-media-edit-copy">
            <strong>${name}</strong>
            <span>${reviewFormatBytes(Number(media?.fileSize) || 0)}</span>
          </div>
          <button type="button" class="store-review-media-remove" data-review-remove-existing-media="${id}" aria-label="${name} 삭제">
            <span class="material-symbols-rounded" aria-hidden="true">close</span>
          </button>
        </div>
      `;
    }).join("");
  }

  function reviewEditorHtml({ mode = "create", review = null } = {}) {
    const editing = mode === "edit" && review;
    const rating = editing ? Number(review.rating) || 0 : 0;
    const content = editing ? String(review.content || "") : "";
    const existingMedia = editing && Array.isArray(review.media) ? review.media : [];
    return `
      <div class="store-write-form store-review-editor" id="store-review-form"
           data-review-mode="${editing ? "edit" : "create"}"
           data-review-existing-media-count="${existingMedia.length}"
           ${editing ? `data-review-id="${review.reviewId}"` : ""}>
        <h3>${editing ? "리뷰 수정" : "리뷰 작성"}</h3>
        <div class="store-rating-input" id="store-review-rating" role="radiogroup" aria-label="별점">
          ${[1, 2, 3, 4, 5].map((n) => `
            <button type="button" data-rating="${n}" aria-label="${n}점"
                    class="${n <= rating ? "is-selected" : ""}">★</button>
          `).join("")}
        </div>
        <textarea id="store-review-content" maxlength="1000" placeholder="솔직한 리뷰를 남겨주세요 (선택)">${escapeHtml(content)}</textarea>
        <div class="store-review-editor-meta">
          <div class="store-review-editor-tools">
            <button type="button" class="button button-secondary button-sm store-review-emoji-toggle"
                    data-review-emoji-toggle aria-expanded="false">🐸 이모지</button>
            <input type="file" data-review-media-input accept="image/*,video/*" multiple hidden>
            <button type="button" class="button button-secondary button-sm" data-review-media-pick>사진·동영상</button>
          </div>
          <span data-review-char-count>${content.length} / 1000</span>
        </div>
        <p class="store-review-media-help">사진·동영상 합계 최대 10개 · 사진 20MB 이하 · 동영상 100MB 이하</p>
        <p class="store-review-media-status" data-review-media-status hidden aria-live="polite"></p>
        ${reviewEmojiPanelHtml()}
        <div class="store-review-media-editor-list" data-review-existing-media-list>
          ${reviewExistingMediaEditorHtml(existingMedia)}
        </div>
        <div class="store-review-media-editor-list" data-review-selected-media-list></div>
        <div class="store-review-editor-actions">
          ${editing ? '<button type="button" class="button button-secondary button-sm" data-review-edit-cancel>취소</button>' : ""}
          <button type="button" class="button button-primary button-sm" id="store-review-submit">${editing ? "수정 완료" : "리뷰 등록"}</button>
        </div>
      </div>
    `;
  }

  async function refreshReviewSummary() {
    if (source !== "owned" || !storeId || !statsRow) return;
    try {
      const response = await Api.get(`/public/restaurants/${storeId}`, { auth: isLoggedIn });
      const store = response.data;
      if (!store) return;
      const values = statsRow.querySelectorAll("strong");
      if (values[0]) values[0].textContent = store.averageRating != null ? Number(store.averageRating).toFixed(1) : "-";
      if (values[1]) values[1].textContent = `${Number(store.reviewCount || 0).toLocaleString("ko-KR")}건`;
    } catch (_error) {
      // 상단 요약 갱신 실패가 리뷰 작성/수정/삭제 결과를 막지 않도록 한다.
    }
  }

  function reviewWriteAreaHtml(myReview) {
    if (!isLoggedIn) {
      return '<div class="store-write-signin">리뷰를 작성하려면 로그인해 주세요.</div>';
    }
    if (!myReview) {
      return `<div id="store-review-write-area">${reviewEditorHtml()}</div>`;
    }
    return `
      <div id="store-review-write-area" class="store-review-owned-notice">
        <div>
          <strong>이미 이 가게에 리뷰를 작성했습니다.</strong>
          <p>한 가게에는 리뷰를 하나만 작성할 수 있습니다. 작성한 리뷰는 수정하거나 삭제할 수 있습니다.</p>
        </div>
        <div class="store-review-owned-actions">
          <button type="button" class="button button-secondary button-sm" data-review-edit="${myReview.reviewId}">내 리뷰 수정</button>
          <button type="button" class="button button-danger button-sm" data-review-delete="${myReview.reviewId}">삭제</button>
        </div>
      </div>
    `;
  }

  function reviewItemHtml(review) {
    const owned = review?.ownedByCurrentUser === true;
    return `
      <div class="store-review-item${owned ? " is-owned" : ""}" data-review-card="${review.reviewId ?? ""}">
        <div class="store-review-head">
          <span class="store-review-author" data-review-id="${review.reviewId ?? ""}">${escapeHtml(review.authorNickname)}</span>
          <span class="store-review-rating">★ ${review.rating}.0</span>
        </div>
        ${owned ? '<span class="store-review-own-badge">내 리뷰</span>' : ""}
        ${review.content ? `<p class="store-review-content">${escapeHtml(review.content)}</p>` : ""}
        ${reviewMediaGalleryHtml(review.media)}
        <div class="store-review-footer">
          <p class="store-review-date">${formatDate(review.createdAt, { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })}${review.edited ? " · 수정됨" : ""}</p>
        </div>
      </div>
    `;
  }

  function reviewPaginationHtml(pageData) {
    const totalPages = Math.max(0, Number(pageData?.totalPages) || 0);
    if (totalPages <= 1) return "";
    const currentPage = Math.max(0, Number(pageData?.page) || 0);
    const block = window.FooduckPagination.block(currentPage, totalPages);
    const buttons = [];
    for (let page = block.start; page < block.end; page += 1) {
      buttons.push(`
        <button type="button" class="store-review-page-button${page === currentPage ? " is-active" : ""}"
                data-review-page="${page}"${page === currentPage ? ' aria-current="page"' : ""}>${page + 1}</button>
      `);
    }
    return `
      <nav class="store-review-pagination" aria-label="리뷰 페이지">
        <button type="button" class="store-review-page-button" data-review-page="${block.previousPage}"
                aria-label="이전 페이지 묶음"${block.hasPrevious ? "" : " disabled"}>‹</button>
        ${buttons.join("")}
        <button type="button" class="store-review-page-button" data-review-page="${block.nextPage}"
                aria-label="다음 페이지 묶음"${block.hasNext ? "" : " disabled"}>›</button>
      </nav>
    `;
  }

  function updateReviewCharacterCount(textarea, counter) {
    if (!textarea || !counter) return;
    counter.textContent = `${textarea.value.length} / 1000`;
  }

  function insertReviewEmoji(textarea, emoji) {
    if (!textarea || !emoji) return;
    if (emojis?.insertIntoEditor?.(textarea, emoji)) return;
    const start = Number.isInteger(textarea.selectionStart) ? textarea.selectionStart : textarea.value.length;
    const end = Number.isInteger(textarea.selectionEnd) ? textarea.selectionEnd : start;
    const nextValue = `${textarea.value.slice(0, start)}${emoji}${textarea.value.slice(end)}`;
    if (nextValue.length > textarea.maxLength) return;
    textarea.value = nextValue;
    const caret = start + emoji.length;
    textarea.focus({ preventScroll: true });
    textarea.setSelectionRange(caret, caret);
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
  }

  function resizeStoreWriteTextarea(textarea, minimumHeight = 90) {
    if (!(textarea instanceof HTMLTextAreaElement)) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.max(textarea.scrollHeight, minimumHeight)}px`;
  }

  function bindReviewEmojiPicker(form) {
    const textarea = form?.querySelector("#store-review-content");
    const toggle = form?.querySelector("[data-review-emoji-toggle]");
    const panel = form?.querySelector("[data-review-emoji-panel]");
    const counter = form?.querySelector("[data-review-char-count]");
    if (!textarea || !toggle || !panel) return;

    emojis?.attachEditor?.(textarea);
    const syncReviewTextarea = () => {
      updateReviewCharacterCount(textarea, counter);
      resizeStoreWriteTextarea(textarea, 90);
    };
    syncReviewTextarea();
    window.requestAnimationFrame(syncReviewTextarea);
    textarea.addEventListener("input", syncReviewTextarea);
    toggle.addEventListener("click", () => {
      const nextOpen = panel.hidden;
      panel.hidden = !nextOpen;
      toggle.setAttribute("aria-expanded", String(nextOpen));
    });
    panel.querySelectorAll("[data-review-emoji]").forEach((button) => {
      button.addEventListener("click", () => insertReviewEmoji(textarea, button.dataset.reviewEmoji));
    });
  }

  function reviewFormatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  }

  function reviewMediaExtension(fileName) {
    const name = String(fileName || "");
    const dot = name.lastIndexOf(".");
    return dot > 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : "";
  }

  function validateReviewMediaFile(file) {
    if (!file || Number(file.size) <= 0) return "비어 있는 파일은 첨부할 수 없습니다.";
    const extension = reviewMediaExtension(file.name);
    const image = REVIEW_IMAGE_EXTENSIONS.has(extension);
    const video = REVIEW_VIDEO_EXTENSIONS.has(extension);
    if (!image && !video) return "지원하지 않는 파일입니다. 사진 또는 동영상 파일을 선택해 주세요.";
    if (image && file.size > REVIEW_MAX_IMAGE_BYTES) return "사진은 한 파일당 20MB 이하만 첨부할 수 있습니다.";
    if (video && file.size > REVIEW_MAX_VIDEO_BYTES) return "동영상은 한 파일당 100MB 이하만 첨부할 수 있습니다.";
    const declaredType = String(file.type || "").toLowerCase();
    if (declaredType && declaredType !== "application/octet-stream") {
      if (image && !declaredType.startsWith("image/")) return "사진 파일 형식을 확인해 주세요.";
      if (video && !declaredType.startsWith("video/")) return "동영상 파일 형식을 확인해 주세요.";
    }
    return null;
  }

  function reviewRawUpload(reviewId, file, onProgress, retried = false) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open(
        "POST",
        `${Api.baseUrl}/reviews/${encodeURIComponent(reviewId)}/media`,
        true,
      );
      xhr.withCredentials = true;
      xhr.setRequestHeader("Accept", "application/json");
      xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");
      xhr.setRequestHeader("X-File-Name", encodeURIComponent(file.name || "review-media"));
      const token = Api.getToken();
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);

      xhr.upload.addEventListener("progress", (event) => {
        if (typeof onProgress !== "function") return;
        onProgress(
          Number(event.loaded) || 0,
          event.lengthComputable ? Number(event.total) || 0 : 0,
        );
      });

      xhr.addEventListener("load", async () => {
        if (xhr.status === 401 && !retried && typeof Api._refreshAccessToken === "function") {
          try {
            const refreshed = await Api._refreshAccessToken();
            if (refreshed) {
              resolve(await reviewRawUpload(reviewId, file, onProgress, true));
              return;
            }
          } catch (_error) {
            // 아래 공통 오류 처리로 이어진다.
          }
        }

        let payload = xhr.responseText;
        try {
          payload = xhr.responseText ? JSON.parse(xhr.responseText) : null;
        } catch (_error) {
          // JSON이 아닌 응답은 원문을 사용한다.
        }
        if (xhr.status < 200 || xhr.status >= 300) {
          if (xhr.status === 401) Api.clearToken();
          const message = typeof payload === "object" && payload
            ? payload.message
            : `첨부파일 업로드에 실패했습니다. (${xhr.status})`;
          reject(new Error(message || "첨부파일 업로드에 실패했습니다."));
          return;
        }
        resolve(payload);
      });
      xhr.addEventListener("error", () => reject(new Error("첨부파일을 서버로 전송하지 못했습니다.")));
      xhr.addEventListener("abort", () => reject(new Error("첨부파일 업로드가 취소되었습니다.")));
      xhr.send(file);
    });
  }

  function openReviewImageViewer(sourceImage, name) {
    if (!sourceImage?.src || document.querySelector(".detail-image-viewer")) return;

    const viewer = document.createElement("div");
    viewer.className = "detail-image-viewer";
    viewer.setAttribute("role", "dialog");
    viewer.setAttribute("aria-modal", "true");
    viewer.setAttribute("aria-label", `${name || "리뷰 사진"} 크게 보기`);

    const closeButton = document.createElement("button");
    closeButton.type = "button";
    closeButton.className = "detail-image-viewer__close";
    closeButton.setAttribute("aria-label", "사진 크게 보기 닫기");
    const closeIcon = document.createElement("span");
    closeIcon.className = "material-symbols-rounded";
    closeIcon.setAttribute("aria-hidden", "true");
    closeIcon.textContent = "close";
    closeButton.append(closeIcon);

    const expandedImage = new Image();
    expandedImage.className = "detail-image-viewer__image";
    expandedImage.src = sourceImage.currentSrc || sourceImage.src;
    expandedImage.alt = sourceImage.alt || name || "리뷰 사진";

    const previouslyFocused = document.activeElement;
    const closeViewer = () => {
      document.removeEventListener("keydown", handleViewerKeydown);
      document.body.classList.remove("is-image-viewer-open");
      viewer.remove();
      if (previouslyFocused instanceof HTMLElement && document.contains(previouslyFocused)) {
        previouslyFocused.focus();
      }
    };
    const handleViewerKeydown = (event) => {
      if (event.key === "Escape") closeViewer();
    };

    closeButton.addEventListener("click", closeViewer);
    viewer.addEventListener("click", (event) => {
      if (event.target === viewer) closeViewer();
    });
    document.addEventListener("keydown", handleViewerKeydown);

    viewer.append(expandedImage, closeButton);
    document.body.append(viewer);
    document.body.classList.add("is-image-viewer-open");
    window.FooduckIcons?.enhance(closeButton);
    closeButton.focus();
  }

  function bindReviewForm({ onEditCancel = null } = {}) {
    const form = document.getElementById("store-review-form");
    if (!form) return;
    const editing = form.dataset.reviewMode === "edit";
    const reviewId = Number(form.dataset.reviewId);
    const existingMediaCount = Math.max(0, Number(form.dataset.reviewExistingMediaCount) || 0);
    const removedMediaIds = new Set();
    let selectedMedia = [];
    let selectedMediaSequence = 0;
    let selectedRating = 0;
    const selectedButton = [...form.querySelectorAll("[data-rating].is-selected")].at(-1);
    if (selectedButton) selectedRating = Number(selectedButton.dataset.rating);

    const ratingButtons = form.querySelectorAll("[data-rating]");
    ratingButtons.forEach((button) => {
      button.addEventListener("click", () => {
        selectedRating = Number(button.dataset.rating);
        ratingButtons.forEach((candidate) => {
          candidate.classList.toggle("is-selected", Number(candidate.dataset.rating) <= selectedRating);
        });
      });
    });
    bindReviewEmojiPicker(form);
    window.FooduckIcons?.enhance(form);

    const mediaInput = form.querySelector("[data-review-media-input]");
    const mediaPickButton = form.querySelector("[data-review-media-pick]");
    const selectedMediaList = form.querySelector("[data-review-selected-media-list]");
    const mediaStatus = form.querySelector("[data-review-media-status]");
    const setMediaStatus = (message, isError = false) => {
      if (!mediaStatus) return;
      mediaStatus.textContent = message || "";
      mediaStatus.hidden = !message;
      mediaStatus.classList.toggle("is-error", Boolean(isError));
    };

    const activeMediaCount = () => existingMediaCount - removedMediaIds.size + selectedMedia.length;
    const renderSelectedMedia = () => {
      if (!selectedMediaList) return;
      selectedMediaList.innerHTML = selectedMedia.map((entry) => `
        <div class="store-review-media-edit-item is-new" data-review-selected-media-id="${entry.id}">
          <span class="store-review-media-file-icon material-symbols-rounded" aria-hidden="true">${REVIEW_VIDEO_EXTENSIONS.has(reviewMediaExtension(entry.file.name)) ? "movie" : "image"}</span>
          <div class="store-review-media-edit-copy">
            <strong>${escapeHtml(entry.file.name || "첨부파일")}</strong>
            <span>${reviewFormatBytes(entry.file.size)}</span>
          </div>
          <button type="button" class="store-review-media-remove" data-review-remove-selected-media="${entry.id}" aria-label="선택한 첨부파일 제거">
            <span class="material-symbols-rounded" aria-hidden="true">close</span>
          </button>
        </div>
      `).join("");
      window.FooduckIcons?.enhance(selectedMediaList);
      selectedMediaList.querySelectorAll("[data-review-remove-selected-media]").forEach((button) => {
        button.addEventListener("click", () => {
          const id = Number(button.dataset.reviewRemoveSelectedMedia);
          selectedMedia = selectedMedia.filter((entry) => entry.id !== id);
          renderSelectedMedia();
          if (selectedMedia.length > 0) {
            setMediaStatus(`${activeMediaCount()}/${REVIEW_MAX_MEDIA_COUNT}개 첨부 준비됨`);
          } else {
            setMediaStatus("");
          }
        });
      });
    };

    form.querySelectorAll("[data-review-remove-existing-media]").forEach((button) => {
      button.addEventListener("click", () => {
        const mediaId = Number(button.dataset.reviewRemoveExistingMedia);
        if (!Number.isSafeInteger(mediaId) || mediaId <= 0) return;
        const item = button.closest("[data-review-existing-media-item]");
        if (removedMediaIds.has(mediaId)) {
          removedMediaIds.delete(mediaId);
          item?.classList.remove("is-removed");
          button.setAttribute("aria-label", "첨부파일 삭제");
        } else {
          removedMediaIds.add(mediaId);
          item?.classList.add("is-removed");
          button.setAttribute("aria-label", "첨부파일 삭제 취소");
        }
      });
    });

    mediaPickButton?.addEventListener("click", () => mediaInput?.click());
    mediaInput?.addEventListener("change", () => {
      const files = [...(mediaInput.files || [])];
      mediaInput.value = "";
      for (const file of files) {
        const validationMessage = validateReviewMediaFile(file);
        if (validationMessage) {
          window.alert(`${file.name || "첨부파일"}: ${validationMessage}`);
          continue;
        }
        if (activeMediaCount() >= REVIEW_MAX_MEDIA_COUNT) {
          window.alert(`리뷰에는 사진과 동영상을 합해 최대 ${REVIEW_MAX_MEDIA_COUNT}개까지 첨부할 수 있습니다.`);
          break;
        }
        selectedMediaSequence += 1;
        selectedMedia.push({ id: selectedMediaSequence, file });
      }
      renderSelectedMedia();
      if (selectedMedia.length > 0) {
        setMediaStatus(`${activeMediaCount()}/${REVIEW_MAX_MEDIA_COUNT}개 첨부 준비됨`);
      } else {
        setMediaStatus("");
      }
    });

    form.querySelector("[data-review-edit-cancel]")?.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (typeof onEditCancel === "function") {
        onEditCancel();
      }
    });

    const submitButton = document.getElementById("store-review-submit");
    submitButton?.addEventListener("click", async () => {
      if (!selectedRating) {
        window.alert("별점을 선택해 주세요.");
        return;
      }
      if (activeMediaCount() > REVIEW_MAX_MEDIA_COUNT) {
        window.alert(`리뷰에는 사진과 동영상을 합해 최대 ${REVIEW_MAX_MEDIA_COUNT}개까지 첨부할 수 있습니다.`);
        return;
      }

      const contentInput = document.getElementById("store-review-content");
      const reviewContent = contentInput?.value.trim() || "";
      submitButton.disabled = true;
      let savedReviewId = editing ? reviewId : null;
      let baseReviewSaved = false;
      try {
        if (editing) {
          if (!Number.isSafeInteger(reviewId) || reviewId <= 0) throw new Error("수정할 리뷰를 찾을 수 없습니다.");
          await Api.put(`/reviews/${reviewId}`, { rating: selectedRating, content: reviewContent || null });
          baseReviewSaved = true;
        } else {
          const writePath = source === "public"
            ? `/map/restaurants/${storeId}/reviews`
            : `/restaurants/${storeId}/reviews`;
          const response = await Api.post(writePath, { rating: selectedRating, content: reviewContent || null });
          savedReviewId = Number(response?.data?.reviewId);
          if (!Number.isSafeInteger(savedReviewId) || savedReviewId <= 0) {
            throw new Error("등록된 리뷰 번호를 확인하지 못했습니다.");
          }
          baseReviewSaved = true;
          reviewPage = 0;
        }

        const mediaIdsToDelete = [...removedMediaIds];
        for (let index = 0; index < mediaIdsToDelete.length; index += 1) {
          setMediaStatus(`기존 첨부파일 삭제 중 (${index + 1}/${mediaIdsToDelete.length})`);
          await Api.delete(`/reviews/${savedReviewId}/media/${mediaIdsToDelete[index]}`);
        }

        const filesToUpload = [...selectedMedia];
        for (let index = 0; index < filesToUpload.length; index += 1) {
          const entry = filesToUpload[index];
          const fileLabel = entry.file.name || "첨부파일";
          setMediaStatus(`${fileLabel} 업로드 중 (${index + 1}/${filesToUpload.length})`);
          await reviewRawUpload(savedReviewId, entry.file, (loaded, total) => {
            if (total > 0 && loaded >= total) {
              setMediaStatus(`${fileLabel} 전송 완료 · 서버 저장 중 (${index + 1}/${filesToUpload.length})`);
              return;
            }
            const progress = total > 0
              ? `${Math.min(99, Math.round((loaded / total) * 100))}%`
              : reviewFormatBytes(loaded);
            setMediaStatus(`${fileLabel} 업로드 중 ${progress} (${index + 1}/${filesToUpload.length})`);
          });
        }

        if (filesToUpload.length || mediaIdsToDelete.length) {
          setMediaStatus("첨부파일 저장 완료");
        }
        await refreshReviewSummary();
        await loadReviews();
      } catch (error) {
        if (baseReviewSaved) {
          setMediaStatus(error.message || "첨부파일 처리 중 문제가 발생했습니다.", true);
          window.alert(`리뷰 내용은 저장되었지만 첨부파일 처리 중 문제가 발생했습니다.\n${error.message || "첨부파일을 다시 확인해 주세요."}`);
          await refreshReviewSummary();
          await loadReviews();
        } else {
          window.alert(error.message || (editing ? "리뷰 수정에 실패했습니다." : "리뷰 등록에 실패했습니다."));
        }
      } finally {
        submitButton.disabled = false;
      }
    });
  }

  function reviewConfirmDelete() {
    return new Promise((resolve) => {
      const dialog = document.createElement("dialog");
      dialog.className = "board-dialog comment-confirm-dialog";
      dialog.innerHTML = `
        <div class="dialog-shell comment-confirm-shell">
          <div class="comment-confirm-heading">
            <span class="comment-confirm-icon"><span class="material-symbols-rounded" aria-hidden="true">delete</span></span>
            <div class="comment-confirm-copy">
              <h2>리뷰를 삭제하시겠습니까?</h2>
              <p>삭제한 리뷰와 첨부한 사진·동영상은 되돌릴 수 없습니다. 삭제 후 이 가게에 새 리뷰를 작성할 수 있습니다.</p>
            </div>
          </div>
          <div class="comment-confirm-actions">
            <button type="button" class="button button-sm button-secondary" data-review-confirm-cancel>취소</button>
            <button type="button" class="button button-sm button-danger" data-review-confirm-delete>삭제</button>
          </div>
        </div>
      `;
      document.body.append(dialog);
      window.FooduckIcons?.enhance(dialog);
      let settled = false;
      const finish = (value) => {
        if (settled) return;
        settled = true;
        dialog.close();
        dialog.remove();
        resolve(value);
      };
      dialog.querySelector("[data-review-confirm-cancel]")?.addEventListener("click", () => finish(false));
      dialog.querySelector("[data-review-confirm-delete]")?.addEventListener("click", () => finish(true));
      dialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        finish(false);
      });
      dialog.addEventListener("click", (event) => {
        if (event.target === dialog) finish(false);
      });
      dialog.showModal();
      dialog.querySelector("[data-review-confirm-cancel]")?.focus();
    });
  }

  function findReviewById(pageData, reviewId) {
    const id = Number(reviewId);
    if (Number(pageData?.myReview?.reviewId) === id) return pageData.myReview;
    return (pageData?.items || []).find((review) => Number(review.reviewId) === id) || null;
  }

  function bindReviewActions(pageData, scope = panels.review) {
    scope.querySelectorAll("[data-review-image-viewer]").forEach((button) => {
      button.addEventListener("click", () => {
        const image = button.querySelector("img");
        if (!image) return;
        openReviewImageViewer(image, image.alt || "리뷰 사진");
      });
    });

    scope.querySelectorAll("[data-review-edit]").forEach((button) => {
      button.addEventListener("click", () => {
        const review = findReviewById(pageData, button.dataset.reviewEdit);
        if (!review?.ownedByCurrentUser) return;
        const writeArea = document.getElementById("store-review-write-area");
        if (!writeArea) return;

        // 수정 취소는 서버를 다시 조회하지 않고 수정 직전 화면을 그대로 복원한다.
        // loadReviews()를 호출하면 불필요한 인증 요청이 발생하고, 401 시 Api가 토큰을
        // 지울 수 있어 취소만 눌렀는데 로그아웃된 것처럼 보이는 문제가 생길 수 있다.
        const previousClassName = writeArea.className;
        const previousHtml = writeArea.innerHTML;

        writeArea.className = "";
        writeArea.innerHTML = reviewEditorHtml({ mode: "edit", review });
        bindReviewForm({
          onEditCancel: () => {
            writeArea.className = previousClassName;
            writeArea.innerHTML = previousHtml;
            bindReviewActions(pageData, writeArea);
            window.FooduckIcons?.enhance(writeArea);
          },
        });
        writeArea.scrollIntoView({ behavior: "smooth", block: "center" });
      });
    });

    scope.querySelectorAll("[data-review-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        const review = findReviewById(pageData, button.dataset.reviewDelete);
        if (!review?.ownedByCurrentUser) return;
        if (!(await reviewConfirmDelete())) return;
        button.disabled = true;
        try {
          await Api.delete(`/reviews/${review.reviewId}`);
          await refreshReviewSummary();
          await loadReviews();
        } catch (error) {
          window.alert(error.message || "리뷰 삭제에 실패했습니다.");
          button.disabled = false;
        }
      });
    });

    scope.querySelectorAll("[data-review-page]").forEach((button) => {
      button.addEventListener("click", async () => {
        const nextPage = Number(button.dataset.reviewPage);
        if (!Number.isInteger(nextPage) || nextPage < 0 || nextPage === reviewPage) return;
        reviewPage = nextPage;
        await loadReviews();
        panels.review.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    });
  }

  async function bindReviewAuthorMenus(items) {
    const board = window.FooduckBoard;
    if (!board?.authorIdentity) return;

    const reviewById = new Map();
    const query = new URLSearchParams();
    items.forEach((review) => {
      const reviewId = Number(review?.reviewId);
      if (!Number.isSafeInteger(reviewId) || reviewId <= 0) return;
      reviewById.set(reviewId, review);
      query.append("reviewIds", String(reviewId));
    });
    if (reviewById.size === 0) return;

    let links;
    try {
      const payload = await Api.get(
        `/board/posts/authors/reviews?${query.toString()}`,
        { auth: false },
      );
      links = Array.isArray(payload?.data) ? payload.data : [];
    } catch (_error) {
      return;
    }

    const accountIdByReviewId = new Map(
      links.map((link) => [Number(link.reviewId), Number(link.authorAccountId)]),
    );
    panels.review.querySelectorAll("[data-review-id]").forEach((host) => {
      const reviewId = Number(host.dataset.reviewId);
      const review = reviewById.get(reviewId);
      const authorAccountId = accountIdByReviewId.get(reviewId);
      if (!review?.authorNickname || !Number.isSafeInteger(authorAccountId) || authorAccountId <= 0) return;

      host.replaceChildren(board.authorIdentity(
        { ...review, authorAccountId },
        {
          showAuthorMenu: true,
          showLoginIdentity: false,
          showRole: false,
          authorMenuContext: "REVIEW",
          authorActivityCueMode: "compact",
        },
      ));
    });
  }

  async function loadReviews() {
    panels.review.innerHTML = '<div class="store-empty">리뷰를 불러오는 중입니다.</div>';
    try {
      const readPath = source === "public"
        ? `/public/map/restaurants/${storeId}/reviews/page?page=${reviewPage}&size=${REVIEW_PAGE_SIZE}`
        : `/public/restaurants/${storeId}/reviews/page?page=${reviewPage}&size=${REVIEW_PAGE_SIZE}`;
      const response = await Api.get(readPath, { auth: isLoggedIn });
      const pageData = response.data || {};
      const items = Array.isArray(pageData.items) ? pageData.items : [];
      const totalElements = Math.max(0, Number(pageData.totalElements) || 0);
      const totalPages = Math.max(0, Number(pageData.totalPages) || 0);

      if (totalPages > 0 && reviewPage >= totalPages) {
        reviewPage = totalPages - 1;
        await loadReviews();
        return;
      }

      const listHtml = items.length === 0
        ? '<div class="store-empty">아직 작성된 리뷰가 없습니다.</div>'
        : `<div class="store-review-list">${items.map(reviewItemHtml).join("")}</div>`;
      panels.review.innerHTML = `
        <div class="store-section-card">
          <h2>리뷰 ${totalElements.toLocaleString("ko-KR")}건</h2>
          <div id="store-sentiment-card" class="store-sentiment-card" hidden></div>
          ${reviewWriteAreaHtml(pageData.myReview || null)}
          ${listHtml}
          ${reviewPaginationHtml(pageData)}
        </div>
      `;
      items.forEach((review) => {
        const card = panels.review.querySelector(`[data-review-card="${CSS.escape(String(review.reviewId ?? ""))}"]`);
        renderCustomEmojiText(card?.querySelector(".store-review-content"), review.content || "");
      });
      bindReviewAuthorMenus(items);
      bindReviewForm();
      bindReviewActions(pageData);
      loadSentimentSummary();
    } catch (error) {
      panels.review.innerHTML = `<div class="store-empty">${error.message || "리뷰를 불러오지 못했습니다."}</div>`;
    }
  }

  // AI(Naive Bayes) 리뷰 감성분석 요약 카드. 감성분석 서비스가 꺼져 있거나 아직 준비되지
  // 않아도(sentiment-api.base-url 미설정 등) 리뷰 화면 자체는 정상 동작해야 하므로,
  // 실패하면 카드를 그냥 숨긴 채 조용히 넘어간다. 공공데이터 매장/사업자 등록 매장 둘 다 지원한다.
  async function loadSentimentSummary() {
    const card = document.getElementById("store-sentiment-card");
    if (!card) return;
    try {
      const path = source === "public"
        ? `/public/map/restaurants/${storeId}/sentiment-summary`
        : `/public/restaurants/${storeId}/sentiment-summary`;
      const response = await Api.get(path, { auth: false });
      const summary = response.data;
      if (!summary || summary.reviewCount === 0) return;
      const ratio = Math.round(summary.positiveRatio);
      card.hidden = false;
      card.innerHTML = `
        <div class="store-sentiment-head">
          <span class="material-symbols-rounded" aria-hidden="true">auto_awesome</span>
          <strong>AI 리뷰 분석</strong>
          <span class="store-sentiment-ratio">${ratio}% 긍정</span>
        </div>
        <div class="store-sentiment-bar">
          <div class="store-sentiment-bar-fill" style="width:${ratio}%"></div>
        </div>
        <p class="store-sentiment-caption">긍정 ${summary.positiveCount}건 · 부정 ${summary.negativeCount}건 (리뷰 ${summary.reviewCount}건 분석)</p>
      `;
    } catch (_error) {
      // 감성분석 서비스 호출 실패는 리뷰 화면 전체에 영향을 주지 않는다.
    }
  }

  function canWriteNews() {
    return source === "public"
      ? isLoggedIn && isAdmin
      : isOwner;
  }

  function newsApiPath() {
    const encodedStoreId = encodeURIComponent(storeId);
    return source === "public"
      ? `/board/posts/restaurants/public/${encodedStoreId}/news`
      : `/board/posts/restaurants/${encodedStoreId}/news`;
  }

  function newsDeleteApiPath(postId) {
    return `${newsApiPath()}/${encodeURIComponent(postId)}`;
  }

  function newsBoardPath(pageName, postId) {
    const query = new URLSearchParams({
      postId: String(postId),
      from: "NEWS",
      newsPage: String(newsPage),
    });
    return `/pages/board/${pageName}.html?${query.toString()}`;
  }

  function newsSummaryHtml(news) {
    const title = escapeHtml(news.title || "소식");
    const content = escapeHtml(news.content || "");
    if (news.postId == null) {
      return `
        <div class="store-news-summary-link is-static">
          <p class="store-news-title">${title}</p>
          <div class="store-news-content-wrap">
            <p class="store-news-content">${content}</p>
          </div>
        </div>
      `;
    }
    const href = escapeHtml(newsBoardPath("detail", news.postId));
    const label = escapeHtml(`${news.title || "소식"} 전체 내용 보기`);
    return `
      <a class="store-news-summary-link" href="${href}" aria-label="${label}">
        <p class="store-news-title">${title}</p>
        <div class="store-news-content-wrap">
          <p class="store-news-content">${content}</p>
        </div>
        <span class="store-news-read-more" data-news-read-more hidden>전체 내용 보기 <span aria-hidden="true">→</span></span>
      </a>
    `;
  }

  function updateNewsSummaryTruncation() {
    if (panels.news.hidden) return;
    panels.news.querySelectorAll(".store-news-summary-link:not(.is-static)").forEach((link) => {
      const contentNode = link.querySelector(".store-news-content");
      const readMore = link.querySelector("[data-news-read-more]");
      if (!contentNode || !readMore) return;

      link.classList.remove("is-truncated");
      readMore.hidden = true;

      const truncated = contentNode.scrollHeight > contentNode.clientHeight + 1;
      link.classList.toggle("is-truncated", truncated);
      readMore.hidden = !truncated;
    });
  }

  function scheduleNewsSummaryTruncationCheck() {
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(updateNewsSummaryTruncation);
    });
  }

  function newsMediaStatus(media) {
    const storedUrl = String(media?.mediaUrl || "");
    if (!media?.processingStatus && storedUrl === "db:processing") return "PROCESSING";
    if (!media?.processingStatus && storedUrl === "db:failed") return "FAILED";
    const status = String(media?.processingStatus || "READY").toUpperCase();
    return ["QUEUED", "PROCESSING", "FAILED"].includes(status) ? status : "READY";
  }

  function newsMediaUrl(media) {
    if (media?.mediaUrl) return String(media.mediaUrl);
    if (media?.postMediaId == null) return "";
    return `/api/board/posts/media/${encodeURIComponent(media.postMediaId)}`;
  }

  function newsFormatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (value < 1024) return `${value}B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)}KB`;
    return `${(value / 1024 / 1024).toFixed(1)}MB`;
  }

  function newsMediaDownloadUrl(media) {
    const url = newsMediaUrl(media);
    if (!url.startsWith("/api/board/posts/media/")) return url;
    const separator = url.includes("?") ? "&" : "?";
    return `${url}${separator}download=true`;
  }

  function newsMediaProcessingHtml(media, status) {
    const failed = status === "FAILED";
    const progress = Math.max(0, Math.min(100, Math.round(Number(media?.processingProgress) || 0)));
    const name = escapeHtml(media?.originalName || "첨부 동영상");
    const message = escapeHtml(
      media?.processingMessage || (failed
        ? "게시글 수정 화면에서 영상을 다시 첨부해 주세요."
        : "처리가 끝나면 이 자리에 동영상이 자동으로 표시됩니다."),
    );
    return `
      <article class="detail-media-item store-news-media-processing-card ${failed ? "is-failed" : "is-processing"}">
        <div class="detail-media-processing" role="group" aria-label="동영상 서버 처리 상태" aria-live="off">
          <span class="material-symbols-rounded detail-media-processing__icon" aria-hidden="true">${failed ? "close" : "progress_activity"}</span>
          <div class="detail-media-processing__copy">
            <strong class="detail-media-processing__title">${failed
              ? "동영상 처리에 실패했습니다."
              : `이 동영상은 서버에서 처리 중입니다. (${progress}%)`}</strong>
            <span class="detail-media-processing__message">${message}</span>
            ${failed ? "" : `<progress class="detail-media-processing__progress" max="100" value="${progress}" aria-label="동영상 서버 처리 진행률"></progress>`}
            <small class="detail-media-processing__file">${name} · ${newsFormatBytes(media?.fileSize)}</small>
          </div>
        </div>
      </article>
    `;
  }

  function newsReadyImageHtml(media) {
    const name = escapeHtml(media?.originalName || "첨부 이미지");
    const url = newsMediaUrl(media);
    if (!url) return "";
    const safeUrl = escapeHtml(url);
    return `
      <button type="button" class="store-news-media-photo" aria-label="${name} 크게 보기" data-news-image-viewer>
        <img src="${safeUrl}" alt="${name}" loading="lazy">
      </button>
    `;
  }

  function openNewsImageViewer(sourceImage) {
    if (!sourceImage?.src || document.querySelector(".detail-image-viewer")) return;

    const name = sourceImage.alt || "첨부 이미지";
    const viewer = document.createElement("div");
    viewer.className = "detail-image-viewer";
    viewer.setAttribute("role", "dialog");
    viewer.setAttribute("aria-modal", "true");
    viewer.setAttribute("aria-label", `${name} 크게 보기`);

    const expandedImage = new Image();
    expandedImage.className = "detail-image-viewer__image";
    expandedImage.src = sourceImage.currentSrc || sourceImage.src;
    expandedImage.alt = name;

    const closeButton = document.createElement("button");
    closeButton.type = "button";
    closeButton.className = "detail-image-viewer__close";
    closeButton.setAttribute("aria-label", "사진 크게 보기 닫기");
    closeButton.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">close</span>';

    const previouslyFocused = document.activeElement;
    const closeViewer = () => {
      document.removeEventListener("keydown", handleViewerKeydown);
      document.body.classList.remove("is-image-viewer-open");
      viewer.remove();
      if (previouslyFocused instanceof HTMLElement && document.contains(previouslyFocused)) {
        previouslyFocused.focus();
      }
    };
    const handleViewerKeydown = (event) => {
      if (event.key === "Escape") closeViewer();
    };

    closeButton.addEventListener("click", closeViewer);
    viewer.addEventListener("click", (event) => {
      if (event.target === viewer) closeViewer();
    });
    document.addEventListener("keydown", handleViewerKeydown);

    viewer.append(expandedImage, closeButton);
    document.body.append(viewer);
    document.body.classList.add("is-image-viewer-open");
    window.FooduckIcons?.enhance(closeButton);
    closeButton.focus();
  }

  function newsReadyVideoHtml(media) {
    const name = escapeHtml(media?.originalName || "첨부 동영상");
    const url = newsMediaUrl(media);
    if (!url) return "";
    const safeUrl = escapeHtml(url);
    const downloadUrl = escapeHtml(newsMediaDownloadUrl(media));
    return `
      <article class="detail-media-item store-news-media-item store-news-media-item--video">
        <div class="detail-media-video store-news-media-video">
          <video src="${safeUrl}" controls preload="metadata" aria-label="${name}"></video>
          <div class="detail-media-loading" role="status" aria-live="polite">
            <span class="material-symbols-rounded detail-media-loading__icon" aria-hidden="true">progress_activity</span>
            <span class="detail-media-loading__text">동영상을 불러오는 중...</span>
          </div>
        </div>
        <div class="detail-media-meta">
          <span>${name} · ${newsFormatBytes(media?.fileSize)}</span>
          <a href="${downloadUrl}">원본 다운로드</a>
        </div>
      </article>
    `;
  }

  function newsMediaKey(media, index) {
    const id = media?.postMediaId ?? media?.mediaId ?? media?.id;
    if (id != null) return `id-${String(id)}`;
    return `index-${index}-${String(media?.originalName || "media")}`;
  }

  function newsReadyImages(mediaItems) {
    return mediaItems.filter((media) =>
      newsMediaStatus(media) === "READY" &&
      String(media?.mediaType || "").toUpperCase() === "IMAGE" &&
      Boolean(newsMediaUrl(media))
    );
  }

  function newsImageGridHtml(readyImages) {
    if (readyImages.length === 0) return "";
    const countClass = readyImages.length === 1
      ? "is-count-1"
      : readyImages.length === 2
        ? "is-count-2"
        : readyImages.length === 3
          ? "is-count-3"
          : readyImages.length === 4
            ? "is-count-4"
            : "is-count-many";
    return `
      <div class="store-news-media-images ${countClass}" aria-label="첨부 이미지 ${readyImages.length}장">
        ${readyImages.map(newsReadyImageHtml).join("")}
      </div>
    `;
  }

  function newsSecondaryMediaHtml(media) {
    const status = newsMediaStatus(media);
    if (status !== "READY") return newsMediaProcessingHtml(media, status);
    return newsReadyVideoHtml(media);
  }

  function newsMediaSignature(media) {
    const status = newsMediaStatus(media);
    return [
      status,
      Math.round(Number(media?.processingProgress) || 0),
      String(media?.processingMessage || ""),
      String(media?.mediaType || ""),
      newsMediaUrl(media),
      Number(media?.fileSize) || 0,
      String(media?.originalName || ""),
    ].join("|");
  }

  function createNewsMediaNode(media, key) {
    const template = document.createElement("template");
    template.innerHTML = newsSecondaryMediaHtml(media).trim();
    const node = template.content.firstElementChild;
    if (!node) return null;
    node.dataset.newsMediaKey = key;
    node.dataset.newsMediaSignature = newsMediaSignature(media);
    return node;
  }

  function bindNewsMediaRuntime(host) {
    window.FooduckIcons?.enhance(host);
    host.querySelectorAll("[data-news-image-viewer]").forEach((button) => {
      if (button.dataset.newsViewerBound === "true") return;
      const image = button.querySelector("img");
      if (!image) return;
      button.dataset.newsViewerBound = "true";
      button.addEventListener("click", () => openNewsImageViewer(image));
    });

    host.querySelectorAll(".store-news-media-video").forEach((frame) => {
      if (frame.dataset.newsVideoBound === "true") return;
      const video = frame.querySelector("video");
      const loading = frame.querySelector(".detail-media-loading");
      if (!video || !loading) return;
      frame.dataset.newsVideoBound = "true";

      const finishLoading = () => {
        loading.hidden = true;
        frame.classList.add("is-ready");
      };

      if (video.readyState >= 2) {
        finishLoading();
      } else {
        video.addEventListener("loadeddata", finishLoading, { once: true });
        video.addEventListener("canplay", finishLoading, { once: true });
      }

      video.addEventListener("error", () => {
        const item = frame.closest(".store-news-media-item");
        if (!item) return;
        const download = video.currentSrc || video.getAttribute("src") || "";
        item.innerHTML = `
          <div class="detail-media-fallback">
            <span class="material-symbols-rounded" aria-hidden="true">movie</span>
            <span>동영상을 재생할 수 없습니다. 원본 파일을 내려받아 확인해 주세요.</span>
            ${download ? `<a class="button button-sm button-secondary" href="${escapeHtml(download)}">원본 다운로드</a>` : ""}
          </div>
        `;
        window.FooduckIcons?.enhance(item);
      }, { once: true });
    });
  }

  function findNewsMediaHost(postId) {
    const target = String(postId ?? "");
    return [...panels.news.querySelectorAll("[data-news-media-post-id]")]
      .find((node) => node.dataset.newsMediaPostId === target) || null;
  }

  function clearNewsMediaPoll(postId) {
    const key = String(postId ?? "");
    const timer = newsMediaPollTimers.get(key);
    if (timer != null) window.clearTimeout(timer);
    newsMediaPollTimers.delete(key);
  }

  function clearNewsMediaPolls() {
    newsMediaPollTimers.forEach((timer) => window.clearTimeout(timer));
    newsMediaPollTimers.clear();
  }

  function canPollNewsMedia() {
    return activeTab === "news" && !panels.news.hidden && document.visibilityState !== "hidden";
  }

  function scheduleNewsMediaPoll(postId, generation, attempt) {
    clearNewsMediaPoll(postId);
    if (!canPollNewsMedia() || attempt >= 120) return;
    const key = String(postId);
    const timer = window.setTimeout(() => {
      newsMediaPollTimers.delete(key);
      if (canPollNewsMedia()) void loadNewsMedia(postId, generation, attempt);
    }, 2500);
    newsMediaPollTimers.set(key, timer);
  }

  function updateNewsMediaHost(host, mediaItems) {
    const readyImages = newsReadyImages(mediaItems);
    const imageSignature = readyImages.map((media, index) => [
      newsMediaKey(media, index),
      newsMediaUrl(media),
    ].join(":" )).join("|");
    let imageGrid = host.querySelector(":scope > .store-news-media-images");
    if (host.dataset.newsImageSignature !== imageSignature) {
      const html = newsImageGridHtml(readyImages).trim();
      if (html) {
        const template = document.createElement("template");
        template.innerHTML = html;
        const nextGrid = template.content.firstElementChild;
        if (imageGrid) imageGrid.replaceWith(nextGrid);
        else host.prepend(nextGrid);
        imageGrid = nextGrid;
        bindNewsMediaRuntime(nextGrid);
      } else if (imageGrid) {
        imageGrid.remove();
        imageGrid = null;
      }
      host.dataset.newsImageSignature = imageSignature;
    }

    const readyImageSet = new Set(readyImages);
    const secondaryItems = mediaItems
      .map((media, index) => ({ media, index }))
      .filter(({ media }) => !readyImageSet.has(media));
    let secondary = host.querySelector(":scope > .store-news-media-secondary");
    if (secondaryItems.length > 0 && !secondary) {
      secondary = document.createElement("div");
      secondary.className = "store-news-media-secondary";
      host.append(secondary);
    }

    if (secondary) {
      const expectedKeys = new Set();
      secondaryItems.forEach(({ media, index }) => {
        const key = newsMediaKey(media, index);
        expectedKeys.add(key);
        const signature = newsMediaSignature(media);
        const existing = [...secondary.children]
          .find((node) => node.dataset.newsMediaKey === key) || null;
        if (existing?.dataset.newsMediaSignature === signature) return;

        const nextNode = createNewsMediaNode(media, key);
        if (!nextNode) return;
        if (existing) existing.replaceWith(nextNode);
        else secondary.append(nextNode);
        bindNewsMediaRuntime(nextNode);
      });
      [...secondary.children].forEach((node) => {
        if (!expectedKeys.has(node.dataset.newsMediaKey || "")) node.remove();
      });
      if (secondary.childElementCount === 0) {
        secondary.remove();
        secondary = null;
      }
    }

    host.hidden = !(imageGrid || secondary);
  }

  function renderNewsMediaError(host, postId) {
    host.hidden = false;
    host.querySelector(".store-news-media-load-error")?.remove();
    const errorView = document.createElement("div");
    errorView.className = "store-news-media-load-error";
    errorView.setAttribute("role", "status");
    errorView.innerHTML = `
      <span>첨부파일을 불러오지 못했습니다.</span>
      <button type="button" class="button button-secondary button-sm"
              data-news-media-retry="${escapeHtml(postId)}">다시 시도</button>
    `;
    host.append(errorView);
    errorView.querySelector("[data-news-media-retry]")?.addEventListener("click", () => {
      errorView.remove();
      if (host.childElementCount === 0) {
        host.innerHTML = '<div class="store-news-media-loading">첨부파일을 불러오는 중입니다.</div>';
      }
      void loadNewsMedia(postId, newsMediaGeneration, 0);
    });
  }

  let newsMediaGeneration = 0;

  async function loadNewsMedia(postId, generation, attempt = 0) {
    if (generation !== newsMediaGeneration) return;
    const host = findNewsMediaHost(postId);
    if (!host || !host.isConnected) return;

    try {
      const response = await Api.get(
        `/board/posts/${encodeURIComponent(postId)}/media`,
        { auth: false },
      );
      if (generation !== newsMediaGeneration) return;
      const mediaItems = Array.isArray(response.data) ? response.data : [];
      host.querySelector(".store-news-media-loading")?.remove();
      host.querySelector(".store-news-media-load-error")?.remove();
      updateNewsMediaHost(host, mediaItems);

      const processing = mediaItems.some((media) => {
        const status = newsMediaStatus(media);
        return status === "QUEUED" || status === "PROCESSING";
      });
      host.dataset.newsMediaProcessing = String(processing);
      if (processing) scheduleNewsMediaPoll(postId, generation, attempt + 1);
      else clearNewsMediaPoll(postId);
    } catch (_error) {
      if (generation !== newsMediaGeneration) return;
      clearNewsMediaPoll(postId);
      renderNewsMediaError(host, postId);
    }
  }

  function bindNewsMedia(items) {
    const generation = newsMediaGeneration;
    items.forEach((news) => {
      if (news?.postId != null) void loadNewsMedia(news.postId, generation);
    });
  }

  function resumeNewsMediaPolling() {
    if (!canPollNewsMedia()) return;
    const generation = newsMediaGeneration;
    panels.news.querySelectorAll('[data-news-media-processing="true"]').forEach((host) => {
      const postId = host.dataset.newsMediaPostId;
      if (postId) void loadNewsMedia(postId, generation, 0);
    });
  }

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      clearNewsMediaPolls();
    } else if (activeTab === "news") {
      resumeNewsMediaPolling();
    }
  });

  function newsRestaurantReturnPath() {
    const url = new URL(window.location.href);
    url.searchParams.set("tab", "news");
    url.searchParams.set("newsPage", String(newsPage));
    return `${url.pathname}${url.search}`;
  }

  function getNewsCommentState(postId) {
    const key = String(postId);
    let state = newsCommentStates.get(key);
    if (!state) {
      state = {
        postId: key,
        post: null,
        open: false,
        loaded: false,
        loading: false,
        page: 0,
        totalPages: 0,
        totalCount: null,
        pageData: null,
        allLoaded: false,
        allComments: null,
        allLoading: false,
        imageRetry: null,
        generation: 0,
        expandedReplyIds: new Set(),
        cacheNotice: "",
        errorMessage: "",
      };
      newsCommentStates.set(key, state);
    }
    return state;
  }

  function seedNewsCommentCount(news) {
    if (news?.postId == null) return null;
    const state = getNewsCommentState(news.postId);
    state.post = news;
    const listCount = Number(news.commentCount);
    if (!state.loaded && Number.isFinite(listCount)) {
      state.totalCount = Math.max(0, listCount);
    }
    return state;
  }

  function newsCommentsToggleHtml(news) {
    if (news?.postId == null) return "";
    const postId = String(news.postId);
    const state = seedNewsCommentCount(news);
    const label = state?.open
      ? "댓글 닫기"
      : state?.totalCount != null
        ? `댓글 ${Math.max(0, Number(state.totalCount) || 0)}개 보기`
        : "댓글 보기";
    return `
      <button type="button" class="button button-sm button-secondary store-news-comments-toggle"
              data-news-comments-toggle="${escapeHtml(postId)}"
              aria-controls="store-news-comments-${escapeHtml(postId)}"
              aria-expanded="${state?.open === true}">${label}</button>
    `;
  }

  function newsCommentsPanelHtml(news) {
    if (news?.postId == null) return "";
    const postId = String(news.postId);
    const state = newsCommentStates.get(postId);
    return `
      <section class="store-news-comments-panel" id="store-news-comments-${escapeHtml(postId)}"
               data-news-comments-post-id="${escapeHtml(postId)}"${state?.open === true ? "" : " hidden"}>
        <div class="store-news-comments-body" data-news-comments-body="${escapeHtml(postId)}"></div>
      </section>
    `;
  }

  function newsCommentElement(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text !== "") node.textContent = text;
    return node;
  }

  function newsCommentEmojiElement(tagName, className, text) {
    const node = newsCommentElement(tagName, className);
    renderCustomEmojiText(node, text);
    return node;
  }

  function newsCommentContentElement(comment, isReply = false) {
    const value = String(comment?.content ?? "");
    if (!isReply) return newsCommentEmojiElement("p", "comment-content", value);

    const mentionMatch = value.match(/^(@\S+)([\s\S]*)$/);
    if (!mentionMatch) return newsCommentEmojiElement("p", "comment-content", value);

    const node = newsCommentElement("p", "comment-content");
    node.append(newsCommentElement("span", "comment-content-mention", mentionMatch[1]));

    if (mentionMatch[2]) {
      const body = newsCommentElement("span", "comment-content-body");
      renderCustomEmojiText(body, mentionMatch[2]);
      node.append(body);
    }
    return node;
  }

  let activeNewsEmojiPicker = null;
  let newsEmojiGlyphsPrewarmed = false;

  function closeNewsEmojiPicker(picker = activeNewsEmojiPicker) {
    if (!picker) return;
    if (picker.panel?.isConnected) picker.panel.hidden = true;
    if (picker.toggle?.isConnected) picker.toggle.setAttribute("aria-expanded", "false");
    if (activeNewsEmojiPicker === picker) activeNewsEmojiPicker = null;
  }

  function closeNewsEmojiPickerInside(scope) {
    if (!scope || !activeNewsEmojiPicker?.panel) return;
    if (scope.contains(activeNewsEmojiPicker.panel)) closeNewsEmojiPicker(activeNewsEmojiPicker);
  }

  function openNewsEmojiPicker(picker) {
    if (!picker?.panel || !picker?.toggle) return;
    if (activeNewsEmojiPicker && activeNewsEmojiPicker !== picker) {
      closeNewsEmojiPicker(activeNewsEmojiPicker);
    }
    picker.panel.hidden = false;
    picker.toggle.setAttribute("aria-expanded", "true");
    activeNewsEmojiPicker = picker;
  }

  function insertNewsEmoji(textarea, emoji) {
    if (!(textarea instanceof HTMLTextAreaElement) || !emoji) return false;
    if (emojis?.insertIntoEditor?.(textarea, emoji)) return true;
    const value = textarea.value || "";
    const start = Number.isInteger(textarea.selectionStart) ? textarea.selectionStart : value.length;
    const end = Number.isInteger(textarea.selectionEnd) ? textarea.selectionEnd : start;
    const nextValue = `${value.slice(0, start)}${emoji}${value.slice(end)}`;

    if (textarea.maxLength > 0 && nextValue.length > textarea.maxLength) {
      const label = textarea.id === "store-news-content" ? "소식 내용" : "댓글";
      showNewsCommentToast(`${label}은 최대 ${textarea.maxLength}자까지 입력할 수 있습니다.`, true);
      return false;
    }

    textarea.value = nextValue;
    const nextCaret = start + emoji.length;
    textarea.focus({ preventScroll: true });
    textarea.setSelectionRange(nextCaret, nextCaret);
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
    return true;
  }

  function prewarmNewsEmojiPicker(panel) {
    if (!panel || !emojis) return;
    const warm = () => {
      if (!panel.isConnected) return;
      try {
        emojis.items.forEach((emoji) => {
          const image = new Image();
          image.src = emoji.src;
        });
        const wasHidden = panel.hidden;
        const previousVisibility = panel.style.visibility;
        panel.style.visibility = "hidden";
        panel.hidden = false;
        void panel.offsetHeight;
        panel.hidden = wasHidden;
        panel.style.visibility = previousVisibility;
      } catch (_error) {
        // 사전 로딩 실패는 picker 기능 자체에 영향을 주지 않는다.
      }
    };
    if ("requestIdleCallback" in window) {
      window.requestIdleCallback(warm, { timeout: 450 });
    } else {
      window.setTimeout(warm, 80);
    }
  }

  function setupNewsEmojiPicker(textarea, toggle, panel) {
    if (!(textarea instanceof HTMLTextAreaElement) || !toggle || !panel) return null;

    if (!emojis) return null;
    const editorApi = emojis.attachEditor?.(textarea);
    bindNewsCommentEditorSubmitEnter(textarea, editorApi?.editor);
    emojis.populatePicker(panel, {
      gridClass: "comment-emoji-grid fooduck-custom-emoji-grid",
      buttonClass: "comment-emoji-option fooduck-custom-emoji-option",
      title: "이모지",
      onSelect: (emoji) => insertNewsEmoji(textarea, emoji.code),
    });

    const picker = { textarea, toggle, panel };
    toggle.addEventListener("click", (event) => {
      event.stopPropagation();
      if (panel.hidden) openNewsEmojiPicker(picker);
      else closeNewsEmojiPicker(picker);
    });
    panel.addEventListener("click", (event) => event.stopPropagation());
    prewarmNewsEmojiPicker(panel);
    return picker;
  }

  document.addEventListener("click", () => closeNewsEmojiPicker());
  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape" || !activeNewsEmojiPicker) return;
    const toggle = activeNewsEmojiPicker.toggle;
    closeNewsEmojiPicker();
    toggle?.focus?.({ preventScroll: true });
  });

  function syncNewsCommentToggle(postId) {
    const state = newsCommentStates.get(String(postId));
    const button = panels.news.querySelector(`[data-news-comments-toggle="${CSS.escape(String(postId))}"]`);
    if (!button || !state) return;
    button.setAttribute("aria-expanded", String(state.open));
    button.textContent = state.open
      ? "댓글 닫기"
      : state.totalCount != null
        ? `댓글 ${Math.max(0, Number(state.totalCount) || 0)}개 보기`
        : "댓글 보기";
  }

  let newsCommentToast = null;

  function showNewsCommentToast(message, isError = false) {
    if (!message) return;
    if (!newsCommentToast) {
      newsCommentToast = document.createElement("div");
      newsCommentToast.className = "board-toast store-news-toast";
      newsCommentToast.setAttribute("role", "status");
      newsCommentToast.setAttribute("aria-live", "polite");
      newsCommentToast.hidden = true;
      document.body.append(newsCommentToast);
    }
    const scrollTopButton = document.querySelector(".board-scroll-top");
    newsCommentToast.classList.toggle(
      "is-scroll-top-offset",
      Boolean(scrollTopButton && !scrollTopButton.hidden),
    );
    if (typeof window.FooduckBoard?.showToast === "function") {
      window.FooduckBoard.showToast(newsCommentToast, message, isError);
    } else {
      window.alert(message);
    }
  }

  function decodeNewsAccessToken(token) {
    try {
      const encoded = String(token || "").split(".")[1];
      if (!encoded) return null;
      const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
      const binary = window.atob(padded);
      const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes));
    } catch (_error) {
      return null;
    }
  }

  function markNewsCommentAuthenticated() {
    const token = Api.getToken();
    if (!token) return false;
    const payload = decodeNewsAccessToken(token) || {};
    isLoggedIn = true;
    try {
      session.authenticated = true;
      session.loginId = payload.loginId || session.loginId || null;
      session.nickname = payload.nickname || session.nickname || null;
      const authorities = Array.isArray(payload.authorities) ? payload.authorities : [];
      isAdmin = authorities.includes("ROLE_ADMIN") || Boolean(session.isAdmin);
      session.isAdmin = isAdmin;
    } catch (_error) {
      // 공통 세션 객체를 직접 갱신할 수 없는 경우에도 현재 댓글 작성은 계속한다.
    }
    return true;
  }

  function ensureNewsCommentAuthPopupController() {
    if (newsCommentAuthPopupController) return newsCommentAuthPopupController;
    const createController = window.FooduckBoard?.createAuthPopupController;
    if (typeof createController !== "function") return null;
    newsCommentAuthPopupController = createController({
      popupName: "fooduck-store-news-comment-login",
      onAuthenticated: () => {
        markNewsCommentAuthenticated();
        const action = pendingNewsCommentLoginAction;
        pendingNewsCommentLoginAction = null;
        void hydrateSessionNickname().finally(() => {
          showNewsCommentToast("로그인되었습니다. 댓글 작성을 이어갑니다.");
          if (typeof action === "function") window.setTimeout(action, 0);
        });
      },
      onClosed: () => {
        pendingNewsCommentLoginAction = null;
        showNewsCommentToast("로그인이 취소되었습니다.", true);
      },
      onBlocked: ({ loginUrl }) => {
        const confirmed = !hasNewsInlineCommentDraft() || window.confirm(
          "로그인 팝업이 차단되었습니다. 현재 화면에서 로그인하면 작성 중인 댓글·답글과 첨부한 사진은 저장되지 않습니다. 로그인 화면으로 이동하시겠습니까?",
        );
        if (!confirmed) {
          pendingNewsCommentLoginAction = null;
          showNewsCommentToast("팝업을 허용한 뒤 다시 로그인해 주세요.", true);
          return;
        }
        window.location.assign(loginUrl);
      },
    });
    return newsCommentAuthPopupController;
  }

  function openNewsCommentLogin(onSuccess = null) {
    if (markNewsCommentAuthenticated()) {
      if (typeof onSuccess === "function") window.setTimeout(onSuccess, 0);
      return true;
    }
    pendingNewsCommentLoginAction = typeof onSuccess === "function" ? onSuccess : null;
    const controller = ensureNewsCommentAuthPopupController();
    if (controller) {
      controller.open({ nextPath: newsRestaurantReturnPath() });
      return false;
    }
    window.FooduckBoard?.requireLogin?.(newsRestaurantReturnPath());
    return false;
  }

  function isNewsCommentSubmitEnter(event) {
    return (
      event.key === "Enter" &&
      !event.shiftKey &&
      !event.isComposing &&
      event.keyCode !== 229
    );
  }

  function isWithdrawnNewsAuthor(author) {
    return author?.authorNickname === "탈퇴한 회원";
  }

  function bindNewsCommentEditorSubmitEnter(textarea, editor) {
    if (!(textarea instanceof HTMLTextAreaElement) || !(editor instanceof HTMLElement)) return;

    let composing = false;
    let submitAfterComposition = false;
    const submitFromTextarea = () => {
      textarea.dispatchEvent(new KeyboardEvent("keydown", {
        key: "Enter",
        bubbles: true,
        cancelable: true,
      }));
    };

    editor.addEventListener("compositionstart", () => {
      composing = true;
    });
    editor.addEventListener("compositionend", () => {
      composing = false;
      if (!submitAfterComposition) return;
      submitAfterComposition = false;
      window.setTimeout(submitFromTextarea, 0);
    });
    editor.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" || event.shiftKey) return;

      if (event.isComposing || composing || event.keyCode === 229) {
        submitAfterComposition = true;
        event.preventDefault();
        event.stopImmediatePropagation();
        return;
      }

      event.preventDefault();
      event.stopImmediatePropagation();
      submitAfterComposition = false;
      submitFromTextarea();
    }, true);
  }

  function updateNewsCommentCharacterCount(target, counter) {
    if (!target || !counter) return;
    const maxLength = Number(target.maxLength) > 0 ? Number(target.maxLength) : 1000;
    const length = target.value.length;
    counter.textContent = `${length} / ${maxLength}`;
    counter.classList.toggle("is-near-limit", length >= Math.floor(maxLength * 0.8));
  }

  function validateNewsCommentImage(file) {
    if (!file) return null;
    if (file.size < 1) return "비어 있는 사진은 첨부할 수 없습니다.";
    if (file.size > NEWS_COMMENT_IMAGE_MAX_BYTES) {
      return "댓글 사진은 5MB 이하만 첨부할 수 있습니다.";
    }
    if (!NEWS_COMMENT_IMAGE_NAME_PATTERN.test(file.name || "") ||
        (file.type && !NEWS_COMMENT_IMAGE_TYPES.has(file.type))) {
      return "댓글에는 JPG, PNG, WEBP, GIF 사진만 첨부할 수 있습니다.";
    }
    return null;
  }

  async function uploadNewsCommentImage(commentId, file) {
    const headers = {
      Accept: "application/json",
      "Content-Type": file.type || "application/octet-stream",
      "X-File-Name": encodeURIComponent(file.name || "comment-image"),
    };
    const token = Api.getToken();
    if (token) headers.Authorization = `Bearer ${token}`;

    const response = await fetch(
      `/api/board/comments/${encodeURIComponent(commentId)}/image`,
      {
        method: "POST",
        headers,
        body: file,
        credentials: "same-origin",
      },
    );
    const responseType = response.headers.get("content-type") || "";
    const payload = responseType.includes("application/json")
      ? await response.json()
      : await response.text();

    if (!response.ok) {
      if (response.status === 401) Api.clearToken();
      const message = typeof payload === "object" && payload
        ? payload.message
        : `댓글 사진 업로드에 실패했습니다. (${response.status})`;
      throw new Error(message || "댓글 사진 업로드에 실패했습니다.");
    }
    return payload;
  }

  function newsInlineCommentDrafts(scope = panels.news) {
    return [...scope.querySelectorAll("textarea[data-news-comment-draft]")].filter((textarea) => {
      const initial = String(textarea.dataset.initialValue || "").trim();
      return textarea.value.trim() !== initial;
    });
  }

  function newsInlineCommentImageDraftForms(scope = panels.news) {
    return [...scope.querySelectorAll('[data-news-comment-image-draft="true"]')];
  }

  function hasNewsInlineCommentDraft(scope = panels.news) {
    return newsInlineCommentDrafts(scope).length > 0 ||
      newsInlineCommentImageDraftForms(scope).length > 0;
  }

  function discardNewsInlineCommentDrafts(scope = panels.news, except = null) {
    newsInlineCommentDrafts(scope).forEach((textarea) => {
      if (textarea === except) return;
      textarea.value = textarea.dataset.initialValue || "";
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
    });
    newsInlineCommentImageDraftForms(scope).forEach((form) => {
      if (except && form.contains(except)) return;
      if (typeof form._clearNewsCommentImage === "function") {
        form._clearNewsCommentImage();
      }
    });
    scope.querySelectorAll(".store-news-comment-edit-form").forEach((form) => {
      if (except && form.contains(except)) return;
      closeNewsCommentEditor(form);
    });
  }

  function confirmNewsInlineCommentDraftDiscard(scope, message = "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 이동하시겠습니까?") {
    if (!hasNewsInlineCommentDraft(scope)) return true;
    return window.confirm(message);
  }

  function newsCommentPageButtons(state) {
    const nav = newsCommentElement("nav", "comment-pagination");
    nav.setAttribute("aria-label", "가게 소식 댓글 페이지");
    if (state.totalPages <= 1) {
      nav.hidden = true;
      return nav;
    }

    const makeButton = (label, page, options = {}) => {
      const button = newsCommentElement(
        "button",
        options.direction
          ? "comment-page-button comment-page-button--direction"
          : "comment-page-button",
        label,
      );
      button.type = "button";
      if (options.current) {
        button.classList.add("is-current");
        button.setAttribute("aria-current", "page");
      }
      if (options.label) button.setAttribute("aria-label", options.label);
      button.disabled = Boolean(options.disabled || options.current || state.loading);
      button.addEventListener("click", () => {
        const panel = panels.news.querySelector(`[data-news-comments-post-id="${CSS.escape(state.postId)}"]`);
        if (panel && !confirmNewsInlineCommentDraftDiscard(
          panel,
          "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 댓글 페이지를 이동하시겠습니까?",
        )) return;
        void loadNewsComments(state.postId, page);
      });
      return button;
    };

    const block = window.FooduckPagination.block(state.page, state.totalPages);

    nav.append(makeButton("이전", block.previousPage, {
      direction: true,
      disabled: !block.hasPrevious,
      label: "이전 댓글 페이지 묶음",
    }));

    for (let page = block.start; page < block.end; page += 1) {
      nav.append(makeButton(String(page + 1), page, {
        current: page === state.page,
        label: `${page + 1}번째 댓글 페이지`,
      }));
    }

    nav.append(makeButton("다음", block.nextPage, {
      direction: true,
      disabled: !block.hasNext,
      label: "다음 댓글 페이지 묶음",
    }));
    return nav;
  }

  function bindNewsCommentAuthor(host, comment) {
    const board = window.FooduckBoard;
    if (board?.authorIdentity && comment?.authorAccountId && comment?.authorNickname) {
      host.replaceChildren(board.authorIdentity(comment, {
        showNickname: true,
        showAuthorMenu: true,
        authorMenuContext: "NEWS",
        authorActivityCueMode: "full",
      }));
      return;
    }
    host.textContent = comment?.authorNickname || comment?.authorLoginId || "작성자";
  }

  function newsCommentImageNode(comment) {
    if (!comment?.hasImage || !comment?.imageUrl) return null;
    const wrap = newsCommentElement("div", "comment-image-wrap");
    const image = new Image();
    image.className = "comment-image";
    image.src = comment.imageUrl;
    image.alt = comment.imageOriginalName || "댓글 첨부 사진";
    image.loading = "lazy";
    image.tabIndex = 0;
    image.setAttribute("role", "button");
    image.setAttribute(
      "aria-label",
      `${comment.imageOriginalName || "댓글 첨부 사진"} 크게 보기`,
    );
    image.addEventListener("click", () => {
      openNewsImageViewer(image);
    });
    image.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      openNewsImageViewer(image);
    });
    image.addEventListener("error", () => {
      wrap.replaceChildren(newsCommentElement("span", "comment-image-error", "사진을 불러오지 못했습니다."));
    }, { once: true });
    wrap.append(image);
    return wrap;
  }

  function resizeNewsCommentTextarea(textarea, minimumHeight = 78) {
    if (!(textarea instanceof HTMLTextAreaElement)) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.max(textarea.scrollHeight, minimumHeight)}px`;
  }

  function newsReplyTargetName(comment) {
    const raw = comment?.authorNickname || comment?.authorLoginId || "작성자";
    return String(raw).replace(/^@+/, "").trim() || "작성자";
  }

  function newsReplyContentValue(value, targetName) {
    const body = String(value || "").trim();
    return body ? `@${targetName} ${body}` : `@${targetName}`;
  }

  function openNewsReplyComposer(postId, comment, mountTarget) {
    if (isWithdrawnNewsAuthor(comment)) return;
    if (!isLoggedIn) {
      openNewsCommentLogin(() => openNewsReplyComposer(postId, comment, mountTarget));
      return;
    }
    const panel = mountTarget.closest("[data-news-comments-post-id]");
    if (!panel) return;
    if (!confirmCloseNewsCommentEditor(panel)) return;
    const mainDrafts = newsInlineCommentDrafts(panel).filter((textarea) => !textarea.closest(".store-news-comment-reply-form"));
    const mainImageDrafts = newsInlineCommentImageDraftForms(panel).filter((form) => !form.classList.contains("store-news-comment-reply-form"));
    if (mainDrafts.length > 0 || mainImageDrafts.length > 0) {
      if (!window.confirm("작성 중인 댓글을 버리고 답글을 작성하시겠습니까?")) return;
      mainDrafts.forEach((textarea) => {
        textarea.value = textarea.dataset.initialValue || "";
        textarea.dispatchEvent(new Event("input", { bubbles: true }));
      });
      mainImageDrafts.forEach((form) => form._clearNewsCommentImage?.());
    }
    const existing = panel.querySelector(".store-news-comment-reply-form");
    if (existing) {
      if (!confirmNewsInlineCommentDraftDiscard(
        existing,
        "작성 중인 답글을 버리고 다른 답글을 작성하시겠습니까?",
      )) return;
      existing._clearNewsCommentImage?.();
      existing.remove();
    }

    const targetName = newsReplyTargetName(comment);
    const rootParentId = comment.parentCommentId || comment.commentId;
    let selectedImage = null;
    let previewUrl = null;

    const form = newsCommentElement("form", "comment-reply-form store-news-comment-reply-form");
    const target = newsCommentElement("div", "comment-reply-target", `@${targetName}님에게 답글 남기기`);
    const replyMentionText = `@${targetName}`;
    const replyEditor = newsCommentElement("div", "comment-reply-editor");
    const replyMention = newsCommentElement("span", "comment-reply-mention", replyMentionText);
    replyMention.setAttribute("aria-hidden", "true");
    const textarea = document.createElement("textarea");
    textarea.className = "comment-reply-textarea";
    textarea.maxLength = Math.max(1, 1000 - replyMentionText.length - 1);
    textarea.rows = 3;
    textarea.value = "";
    textarea.placeholder = "답글을 입력하세요";
    textarea.dataset.newsCommentDraft = "true";
    textarea.dataset.initialValue = "";
    textarea.setAttribute("aria-label", `${targetName}님에게 답글 내용`);
    replyEditor.append(replyMention, textarea);

    const inputMeta = newsCommentElement(
      "div",
      "comment-input-meta comment-input-meta--compact comment-input-meta--footer",
    );
    const characterCount = newsCommentElement("span", "comment-character-count");
    inputMeta.append(characterCount);
    characterCount.textContent = `${replyMentionText.length} / 1000`;
    resizeNewsCommentTextarea(textarea, 78);

    const tools = newsCommentElement("div", "comment-image-tools");
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.className = "sr-only";
    fileInput.accept = ".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp";
    const imageButton = newsCommentElement("button", "comment-image-select", "사진 첨부");
    imageButton.type = "button";
    const emojiButton = newsCommentElement("button", "comment-emoji-toggle", "🐸 이모지");
    emojiButton.type = "button";
    const emojiPanel = newsCommentElement("div", "comment-emoji-panel");
    emojiPanel.hidden = true;
    emojiPanel.setAttribute("role", "group");
    emojiPanel.setAttribute("aria-label", "이모지 선택");
    const emojiPanelId = `store-news-reply-emoji-${comment.commentId}`;
    emojiPanel.id = emojiPanelId;
    emojiButton.setAttribute("aria-controls", emojiPanelId);
    emojiButton.setAttribute("aria-expanded", "false");
    tools.append(fileInput, imageButton, emojiButton, newsCommentElement("span", "", "사진 1장 · 최대 5MB"));

    const preview = newsCommentElement("div", "comment-image-preview");
    preview.hidden = true;
    const previewImage = new Image();
    previewImage.alt = "답글 첨부 사진 미리보기";
    const previewCopy = newsCommentElement("div", "comment-image-preview-copy");
    const previewName = newsCommentElement("strong");
    const previewSize = newsCommentElement("span");
    previewCopy.append(previewName, previewSize);
    const removeImage = newsCommentElement("button", "comment-image-remove", "선택 취소");
    removeImage.type = "button";
    preview.append(previewImage, previewCopy, removeImage);

    const row = newsCommentElement("div", "comment-reply-submit-row");
    const submitTools = newsCommentElement("div", "comment-submit-tools");
    submitTools.append(tools, inputMeta);
    const actions = newsCommentElement("div", "comment-reply-actions");
    const cancel = newsCommentElement("button", "comment-action", "취소");
    cancel.type = "button";
    const submit = newsCommentElement("button", "button button-sm button-primary", "답글 등록");
    submit.type = "submit";
    submit.disabled = true;
    actions.append(cancel, submit);
    row.append(submitTools, actions);

    const hasBody = () => Boolean(textarea.value.trim());
    const isPostWithdrawn = () => isWithdrawnNewsAuthor(getNewsCommentState(postId).post);
    const sync = () => {
      submit.disabled = isPostWithdrawn() || !hasBody();
      characterCount.textContent = `${newsReplyContentValue(textarea.value, targetName).length} / 1000`;
      resizeNewsCommentTextarea(textarea, 78);
    };
    const clearImage = () => {
      selectedImage = null;
      fileInput.value = "";
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        previewUrl = null;
      }
      previewImage.removeAttribute("src");
      previewName.textContent = "";
      previewSize.textContent = "";
      preview.hidden = true;
      form.dataset.newsCommentImageDraft = "false";
    };
    form._clearNewsCommentImage = clearImage;

    imageButton.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", () => {
      const file = fileInput.files?.[0] || null;
      const error = validateNewsCommentImage(file);
      if (error) {
        showNewsCommentToast(error, true);
        clearImage();
        return;
      }
      clearImage();
      if (!file) return;
      selectedImage = file;
      previewUrl = URL.createObjectURL(file);
      previewImage.src = previewUrl;
      previewImage.alt = `${file.name || "답글 첨부 사진"} 미리보기`;
      previewName.textContent = file.name || "첨부 사진";
      previewSize.textContent = newsFormatBytes(file.size);
      preview.hidden = false;
      form.dataset.newsCommentImageDraft = "true";
    });
    removeImage.addEventListener("click", clearImage);

    textarea.addEventListener("input", sync);
    textarea.addEventListener("keydown", (event) => {
      if (!isNewsCommentSubmitEnter(event)) return;
      event.preventDefault();
      if (isPostWithdrawn()) {
        showNewsCommentToast("작성자가 탈퇴한 게시물에는 댓글을 작성할 수 없습니다.", true);
        sync();
        return;
      }
      if (!submit.disabled) form.requestSubmit();
    });
    cancel.addEventListener("click", () => {
      if (!confirmNewsInlineCommentDraftDiscard(form, "작성 중인 답글을 버리시겠습니까?")) return;
      clearImage();
      closeNewsEmojiPickerInside(form);
      form.remove();
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (isPostWithdrawn()) {
        showNewsCommentToast("작성자가 탈퇴한 게시물에는 댓글을 작성할 수 없습니다.", true);
        sync();
        return;
      }
      if (!hasBody()) return;
      const otherDrafts = newsInlineCommentDrafts(panel).filter((candidate) => candidate !== textarea);
      const otherImageDrafts = newsInlineCommentImageDraftForms(panel).filter((candidate) => candidate !== form);
      if (otherDrafts.length > 0 || otherImageDrafts.length > 0) {
        if (!window.confirm("작성 중인 다른 댓글을 버리고 이 답글을 등록하시겠습니까?")) return;
        discardNewsInlineCommentDrafts(panel, textarea);
      }

      const imageFile = selectedImage;
      submit.disabled = true;
      textarea.disabled = true;
      imageButton.disabled = true;
      emojiButton.disabled = true;
      closeNewsEmojiPicker();
      try {
        const payload = await Api.post(`/board/posts/${encodeURIComponent(postId)}/comments`, {
          content: newsReplyContentValue(textarea.value, targetName),
          parentCommentId: rootParentId,
        });
        const createdCommentId = payload.data?.commentId;
        let imageUploadError = null;
        if (imageFile && createdCommentId) {
          try {
            await uploadNewsCommentImage(createdCommentId, imageFile);
          } catch (error) {
            imageUploadError = error;
          }
        }

        window.FooduckBoard?.invalidateBoardCache?.();
        clearImage();
        const state = getNewsCommentState(postId);
        state.expandedReplyIds.add(String(rootParentId));
        if (imageUploadError && createdCommentId) {
          state.imageRetry = {
            commentId: createdCommentId,
            file: imageFile,
            label: "답글",
            errorMessage: imageUploadError.message || "사진만 다시 올릴 수 있습니다.",
          };
          showNewsCommentToast(`답글은 등록됐지만 사진 업로드에 실패했습니다. ${state.imageRetry.errorMessage}`, true);
        } else {
          showNewsCommentToast(imageFile ? "답글과 사진이 등록되었습니다." : "답글이 등록되었습니다.");
        }
        if (state.allLoaded) await loadAllNewsComments(postId, { forceRefresh: true });
        else await loadNewsComments(postId, state.page, { forceRefresh: true });
      } catch (error) {
        textarea.disabled = false;
        imageButton.disabled = false;
        emojiButton.disabled = false;
        sync();
        const status = panel.querySelector("[data-news-comment-status]");
        if (status) status.textContent = error.message || "답글 등록에 실패했습니다.";
      }
    });

    form.append(target, replyEditor, emojiPanel, preview, row);
    setupNewsEmojiPicker(textarea, emojiButton, emojiPanel);
    mountTarget.append(form);
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  function closeNewsCommentEditor(form) {
    if (!(form instanceof HTMLFormElement)) return;
    const item = form.closest(".comment-item");
    const content = item?.querySelector(":scope > .comment-content");
    const actions = item?.querySelector(":scope > .comment-actions");
    if (content) content.hidden = false;
    if (actions) actions.hidden = false;
    closeNewsEmojiPickerInside(form);
    form.remove();
  }

  function newsCommentEditorHasDraft(form) {
    if (!(form instanceof HTMLFormElement)) return false;
    const textarea = form.querySelector(".comment-edit-textarea");
    if (!(textarea instanceof HTMLTextAreaElement)) return false;
    return textarea.value.trim() !== String(textarea.dataset.initialValue || "").trim();
  }

  function confirmCloseNewsCommentEditor(panel, nextCommentId = null) {
    const form = panel?.querySelector(".store-news-comment-edit-form");
    if (!(form instanceof HTMLFormElement)) return true;
    const currentId = String(form.dataset.editCommentId || "");
    if (nextCommentId != null && currentId === String(nextCommentId)) {
      form.querySelector(".comment-edit-textarea")?.focus({ preventScroll: true });
      return false;
    }
    if (newsCommentEditorHasDraft(form) && !window.confirm(
      "수정 중인 댓글이 있습니다. 바꾼 내용을 버리고 계속하시겠습니까?",
    )) return false;
    closeNewsCommentEditor(form);
    return true;
  }

  async function editNewsComment(postId, comment, item) {
    const commentId = Number(comment?.commentId);
    if (!Number.isFinite(commentId) || newsCommentEditInFlight.has(commentId)) return;
    if (!(comment?.ownedByCurrentUser || isAdmin)) return;

    const panel = item?.closest("[data-news-comments-post-id]");
    if (!panel) return;
    if (!confirmCloseNewsCommentEditor(panel, commentId)) return;

    const otherDrafts = newsInlineCommentDrafts(panel);
    const otherImageDrafts = newsInlineCommentImageDraftForms(panel);
    if (otherDrafts.length > 0 || otherImageDrafts.length > 0) {
      if (!window.confirm(
        "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 이 댓글을 수정하시겠습니까?",
      )) return;
      discardNewsInlineCommentDrafts(panel);
    }

    const contentNode = item.querySelector(":scope > .comment-content");
    const actionsNode = item.querySelector(":scope > .comment-actions");
    const dateNode = item.querySelector(":scope > .comment-top .comment-date");
    if (!contentNode || !actionsNode) return;

    const form = newsCommentElement("form", "comment-edit-form store-news-comment-edit-form");
    form.dataset.editCommentId = String(commentId);

    const textarea = document.createElement("textarea");
    textarea.className = "comment-edit-textarea";
    textarea.maxLength = 1000;
    textarea.rows = 3;
    textarea.value = comment.content || "";
    textarea.dataset.newsCommentDraft = "true";
    textarea.dataset.initialValue = textarea.value;
    textarea.setAttribute("aria-label", "댓글 수정 내용");

    const inputMeta = newsCommentElement("div", "comment-input-meta comment-input-meta--compact");
    inputMeta.append(newsCommentElement("span", "", "Enter로 수정 · Shift + Enter로 줄바꿈"));
    const characterCount = newsCommentElement("span", "comment-character-count");
    inputMeta.append(characterCount);
    updateNewsCommentCharacterCount(textarea, characterCount);

    const emojiTools = newsCommentElement("div", "comment-image-tools comment-edit-emoji-tools");
    const emojiButton = newsCommentElement("button", "comment-emoji-toggle", "🐸 이모지");
    emojiButton.type = "button";
    const emojiPanel = newsCommentElement("div", "comment-emoji-panel");
    emojiPanel.hidden = true;
    emojiPanel.setAttribute("role", "group");
    emojiPanel.setAttribute("aria-label", "이모지 선택");
    const emojiPanelId = `store-news-edit-emoji-${commentId}`;
    emojiPanel.id = emojiPanelId;
    emojiButton.setAttribute("aria-controls", emojiPanelId);
    emojiButton.setAttribute("aria-expanded", "false");
    emojiTools.append(emojiButton);

    const actions = newsCommentElement("div", "comment-edit-actions");
    const cancel = newsCommentElement("button", "button button-sm button-secondary", "취소");
    cancel.type = "button";
    const save = newsCommentElement("button", "button button-sm button-primary", "수정 완료");
    save.type = "submit";
    actions.append(cancel, save);
    form.append(textarea, inputMeta, emojiTools, emojiPanel, actions);
    setupNewsEmojiPicker(textarea, emojiButton, emojiPanel);

    const sync = () => {
      const content = textarea.value.trim();
      const original = String(comment.content || "").trim();
      save.disabled = !content || content === original || newsCommentEditInFlight.has(commentId);
      updateNewsCommentCharacterCount(textarea, characterCount);
      resizeNewsCommentTextarea(textarea, 86);
    };

    contentNode.hidden = true;
    actionsNode.hidden = true;
    contentNode.after(form);
    sync();

    form.addEventListener("click", (event) => event.stopPropagation());
    textarea.addEventListener("input", sync);
    cancel.addEventListener("click", () => {
      if (newsCommentEditorHasDraft(form) && !window.confirm("수정 중인 내용을 버리시겠습니까?")) return;
      closeNewsCommentEditor(form);
    });
    textarea.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        cancel.click();
        return;
      }
      if (!isNewsCommentSubmitEnter(event)) return;
      event.preventDefault();
      if (!save.disabled) form.requestSubmit();
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = textarea.value.trim();
      const original = String(comment.content || "").trim();
      if (!content) {
        showNewsCommentToast("댓글 내용을 입력해 주세요.", true);
        return;
      }
      if (content === original) {
        closeNewsCommentEditor(form);
        return;
      }
      if (newsCommentEditInFlight.has(commentId)) return;

      newsCommentEditInFlight.add(commentId);
      save.disabled = true;
      cancel.disabled = true;
      textarea.disabled = true;
      emojiButton.disabled = true;
      closeNewsEmojiPicker();
      try {
        const payload = await Api.put(`/board/comments/${encodeURIComponent(commentId)}`, { content });
        window.FooduckBoard?.invalidateBoardCache?.();
        const updated = payload.data || {};
        Object.assign(comment, updated, {
          content: updated.content ?? content,
          edited: updated.edited ?? true,
        });
        renderCustomEmojiText(contentNode, comment.content);
        if (dateNode) {
          dateNode.textContent = `${formatDate(comment.createdAt, { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })} · 수정됨`;
        }
        closeNewsCommentEditor(form);
        showNewsCommentToast(payload.message || "댓글이 수정되었습니다.");
      } catch (error) {
        showNewsCommentToast(error.message || "댓글 수정에 실패했습니다.", true);
        cancel.disabled = false;
        textarea.disabled = false;
        emojiButton.disabled = false;
      } finally {
        newsCommentEditInFlight.delete(commentId);
        if (form.isConnected) sync();
      }
    });

    textarea.focus({ preventScroll: true });
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  async function deleteNewsComment(postId, comment, hasReplies, button) {
    const commentId = Number(comment?.commentId);
    if (!Number.isFinite(commentId) || newsCommentDeleteInFlight.has(commentId)) return;
    const panel = button.closest("[data-news-comments-post-id]");
    if (panel && !confirmNewsInlineCommentDraftDiscard(
      panel,
      "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 댓글을 삭제하시겠습니까?",
    )) return;
    const message = hasReplies
      ? "이 댓글과 달린 답글을 함께 삭제하시겠습니까?"
      : "이 댓글을 삭제하시겠습니까?";
    if (!window.confirm(message)) return;

    newsCommentDeleteInFlight.add(commentId);
    button.disabled = true;
    try {
      await Api.delete(`/board/comments/${encodeURIComponent(commentId)}`);
      window.FooduckBoard?.invalidateBoardCache?.();
      const state = getNewsCommentState(postId);
      if (state.allLoaded) await loadAllNewsComments(postId, { forceRefresh: true });
      else await loadNewsComments(postId, state.page, { forceRefresh: true });
    } catch (error) {
      const panel = button.closest("[data-news-comments-post-id]");
      const status = panel?.querySelector("[data-news-comment-status]");
      if (status) status.textContent = error.message || "댓글 삭제에 실패했습니다.";
    } finally {
      newsCommentDeleteInFlight.delete(commentId);
      if (button.isConnected) button.disabled = false;
    }
  }

  function shouldIgnoreNewsCommentAreaReplyClick(event, item) {
    const target = event.target;
    if (!(target instanceof Element)) return true;

    if (target.closest(
      "button, a, input, textarea, select, label, [role='button'], .comment-actions, .comment-reply-form",
    )) {
      return true;
    }

    const selection = window.getSelection();
    if (
      selection &&
      !selection.isCollapsed &&
      selection.toString().trim() &&
      selection.containsNode(item, true)
    ) {
      return true;
    }

    return false;
  }

  function renderNewsCommentItem(postId, comment, options = {}) {
    const withdrawnAuthor = isWithdrawnNewsAuthor(comment);
    const item = newsCommentElement(
      "article",
      options.isReply ? "comment-item comment-reply" : "comment-item",
    );
    item.id = `store-news-comment-${comment.commentId}`;

    const top = newsCommentElement("div", "comment-top");
    const author = newsCommentElement("span", "comment-author");
    bindNewsCommentAuthor(author, comment);
    const date = newsCommentElement(
      "span",
      "comment-date",
      `${formatDate(comment.createdAt, { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })}${comment.edited ? " · 수정됨" : ""}`,
    );
    top.append(author, date);

    const contentNode = newsCommentContentElement(comment, Boolean(options.isReply));
    item.append(top, contentNode);
    const image = newsCommentImageNode(comment);
    if (image) item.append(image);

    const actions = newsCommentElement("div", "comment-actions");
    if (!withdrawnAuthor) {
      const reply = newsCommentElement("button", "comment-action", "답글");
      reply.type = "button";
      reply.addEventListener("click", () => openNewsReplyComposer(postId, comment, item));
      actions.append(reply);
    }

    if (comment.ownedByCurrentUser || isAdmin) {
      const edit = newsCommentElement("button", "comment-action", "수정");
      edit.type = "button";
      edit.addEventListener("click", () => editNewsComment(postId, comment, item));
      const remove = newsCommentElement("button", "comment-action", "삭제");
      remove.type = "button";
      remove.addEventListener("click", () => deleteNewsComment(postId, comment, Boolean(options.hasReplies), remove));
      actions.append(edit, remove);
    }
    item.append(actions);
    if (!withdrawnAuthor) {
      item.classList.add("comment-item--replyable");
      item.addEventListener("click", (event) => {
        if (item.querySelector(":scope > .store-news-comment-reply-form")) return;
        if (shouldIgnoreNewsCommentAreaReplyClick(event, item)) return;
        openNewsReplyComposer(postId, comment, item);
      });
    }
    return item;
  }

  function appendNewsCommentThreads(postId, list, comments, state) {
    const repliesByParent = new Map();
    comments.forEach((comment) => {
      if (!comment.parentCommentId) return;
      const key = String(comment.parentCommentId);
      const replies = repliesByParent.get(key) || [];
      replies.push(comment);
      repliesByParent.set(key, replies);
    });

    comments.filter((comment) => !comment.parentCommentId).forEach((comment) => {
      const replies = repliesByParent.get(String(comment.commentId)) || [];
      const thread = newsCommentElement("section", "comment-thread");
      thread.append(renderNewsCommentItem(postId, comment, { hasReplies: replies.length > 0 }));
      if (replies.length) {
        const key = String(comment.commentId);
        const expanded = state.expandedReplyIds.has(key);
        const toggle = newsCommentElement(
          "button",
          "comment-replies-toggle",
          expanded ? `답글 ${replies.length}개 숨기기` : `답글 ${replies.length}개 보기`,
        );
        toggle.type = "button";
        toggle.setAttribute("aria-expanded", String(expanded));
        const repliesHost = newsCommentElement("div", "comment-replies");
        repliesHost.hidden = !expanded;
        replies.forEach((reply) => {
          repliesHost.append(renderNewsCommentItem(postId, reply, { isReply: true }));
        });
        toggle.addEventListener("click", () => {
          const willExpand = repliesHost.hidden;
          repliesHost.hidden = !willExpand;
          toggle.setAttribute("aria-expanded", String(willExpand));
          toggle.textContent = willExpand
            ? `답글 ${replies.length}개 숨기기`
            : `답글 ${replies.length}개 보기`;
          if (willExpand) state.expandedReplyIds.add(key);
          else state.expandedReplyIds.delete(key);
        });
        thread.append(toggle, repliesHost);
      }
      list.append(thread);
    });
  }

  function renderNewsCommentImageRetry(postId, state) {
    const retry = state.imageRetry;
    if (!retry?.commentId || !retry?.file) return null;

    const notice = newsCommentElement("div", "comment-upload-retry");
    const copy = newsCommentElement("div", "comment-upload-retry__copy");
    copy.append(
      newsCommentElement("strong", "", `${retry.label || "댓글"}은 등록됐지만 사진을 올리지 못했습니다.`),
      newsCommentElement("span", "", retry.errorMessage || "사진만 다시 올릴 수 있습니다."),
    );
    const actions = newsCommentElement("div", "comment-upload-retry__actions");
    const retryButton = newsCommentElement("button", "button button-sm button-primary", "사진 다시 올리기");
    const dismissButton = newsCommentElement("button", "button button-sm button-secondary", "닫기");
    retryButton.type = "button";
    dismissButton.type = "button";

    retryButton.addEventListener("click", async () => {
      retryButton.disabled = true;
      dismissButton.disabled = true;
      retryButton.textContent = "올리는 중";
      try {
        await uploadNewsCommentImage(retry.commentId, retry.file);
        window.FooduckBoard?.invalidateBoardCache?.();
        state.imageRetry = null;
        showNewsCommentToast(`${retry.label || "댓글"} 사진이 등록되었습니다.`);
        if (state.allLoaded) await loadAllNewsComments(postId, { forceRefresh: true });
        else await loadNewsComments(postId, state.page, { forceRefresh: true });
      } catch (error) {
        retryButton.disabled = false;
        dismissButton.disabled = false;
        retryButton.textContent = "사진 다시 올리기";
        const message = copy.querySelector("span");
        if (message) message.textContent = error.message || "사진 업로드에 다시 실패했습니다.";
      }
    });
    dismissButton.addEventListener("click", () => {
      state.imageRetry = null;
      notice.remove();
    });
    actions.append(retryButton, dismissButton);
    notice.append(copy, actions);
    return notice;
  }

  function renderNewsCommentForm(postId, state) {
    let selectedImage = null;
    let previewUrl = null;
    const withdrawnAuthor = isWithdrawnNewsAuthor(state?.post);

    const form = newsCommentElement("form", "comment-form comment-form--bottom store-news-comment-form");
    const heading = newsCommentElement("div", "comment-form-heading");
    heading.append(
      newsCommentElement("strong", "", "댓글 작성"),
      newsCommentElement(
        "span",
        "",
        withdrawnAuthor
          ? "작성자가 탈퇴한 게시물에는 댓글을 작성할 수 없습니다."
          : "이야기를 읽고 의견을 남겨 보세요.",
      ),
    );

    const label = newsCommentElement("label", "sr-only", "댓글 내용");
    const textareaId = `store-news-comment-content-${postId}`;
    label.htmlFor = textareaId;
    const textarea = document.createElement("textarea");
    textarea.id = textareaId;
    textarea.maxLength = 1000;
    textarea.rows = 4;
    textarea.disabled = withdrawnAuthor;
    textarea.placeholder = withdrawnAuthor
      ? "작성자가 탈퇴하여 댓글을 작성할 수 없습니다."
      : "맛있는 이야기에 댓글을 남겨 보세요.";
    textarea.dataset.newsCommentDraft = "true";
    textarea.dataset.initialValue = "";

    const inputMeta = newsCommentElement("div", "comment-input-meta comment-input-meta--footer");
    const characterCount = newsCommentElement("span", "comment-character-count");
    inputMeta.append(characterCount);
    updateNewsCommentCharacterCount(textarea, characterCount);
    resizeNewsCommentTextarea(textarea, 105);

    const tools = newsCommentElement("div", "comment-image-tools");
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.className = "sr-only";
    fileInput.accept = ".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp";
    const imageButton = newsCommentElement("button", "comment-image-select", "사진 첨부");
    imageButton.type = "button";
    imageButton.disabled = withdrawnAuthor;
    fileInput.disabled = withdrawnAuthor;
    const emojiButton = newsCommentElement("button", "comment-emoji-toggle", "🐸 이모지");
    emojiButton.type = "button";
    emojiButton.disabled = withdrawnAuthor;
    const emojiPanel = newsCommentElement("div", "comment-emoji-panel");
    emojiPanel.hidden = true;
    emojiPanel.setAttribute("role", "group");
    emojiPanel.setAttribute("aria-label", "이모지 선택");
    const emojiPanelId = `store-news-comment-emoji-${postId}`;
    emojiPanel.id = emojiPanelId;
    emojiButton.setAttribute("aria-controls", emojiPanelId);
    emojiButton.setAttribute("aria-expanded", "false");
    tools.append(fileInput, imageButton, emojiButton, newsCommentElement("span", "", "사진 1장 · 최대 5MB"));

    const preview = newsCommentElement("div", "comment-image-preview");
    preview.hidden = true;
    const previewImage = new Image();
    previewImage.alt = "댓글 첨부 사진 미리보기";
    const previewCopy = newsCommentElement("div", "comment-image-preview-copy");
    const previewName = newsCommentElement("strong");
    const previewSize = newsCommentElement("span");
    previewCopy.append(previewName, previewSize);
    const removeImage = newsCommentElement("button", "comment-image-remove", "선택 취소");
    removeImage.type = "button";
    preview.append(previewImage, previewCopy, removeImage);

    const submitRow = newsCommentElement("div", "comment-submit-row");
    const submitTools = newsCommentElement("div", "comment-submit-tools");
    submitTools.append(tools, inputMeta);
    const submit = newsCommentElement("button", "button button-sm button-primary", "댓글 등록");
    submit.type = "submit";
    submit.disabled = withdrawnAuthor;
    submitRow.append(submitTools);
    if (isLoggedIn || withdrawnAuthor) {
      submitRow.append(submit);
    } else {
      const loginNote = newsCommentElement(
        "p",
        "",
        "댓글 등록 시 로그인 화면으로 이동합니다.",
      );
      submitRow.append(loginNote, submit);
    }

    const clearImage = () => {
      selectedImage = null;
      fileInput.value = "";
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        previewUrl = null;
      }
      previewImage.removeAttribute("src");
      previewName.textContent = "";
      previewSize.textContent = "";
      preview.hidden = true;
      form.dataset.newsCommentImageDraft = "false";
    };
    form._clearNewsCommentImage = clearImage;

    imageButton.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", () => {
      const file = fileInput.files?.[0] || null;
      const error = validateNewsCommentImage(file);
      if (error) {
        showNewsCommentToast(error, true);
        clearImage();
        return;
      }
      clearImage();
      if (!file) return;
      selectedImage = file;
      previewUrl = URL.createObjectURL(file);
      previewImage.src = previewUrl;
      previewImage.alt = `${file.name || "댓글 첨부 사진"} 미리보기`;
      previewName.textContent = file.name || "첨부 사진";
      previewSize.textContent = newsFormatBytes(file.size);
      preview.hidden = false;
      form.dataset.newsCommentImageDraft = "true";
    });
    removeImage.addEventListener("click", clearImage);

    const syncSubmit = () => {
      updateNewsCommentCharacterCount(textarea, characterCount);
      resizeNewsCommentTextarea(textarea, 105);
    };
    textarea.addEventListener("input", syncSubmit);
    textarea.addEventListener("keydown", (event) => {
      if (!isNewsCommentSubmitEnter(event)) return;
      event.preventDefault();
      if (withdrawnAuthor) {
        showNewsCommentToast("작성자가 탈퇴한 게시물에는 댓글을 작성할 수 없습니다.", true);
        return;
      }
      if (textarea.value.trim()) form.requestSubmit();
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (withdrawnAuthor) {
        showNewsCommentToast("작성자가 탈퇴한 게시물에는 댓글을 작성할 수 없습니다.", true);
        return;
      }
      const value = textarea.value.trim();
      if (!value) {
        showNewsCommentToast("댓글 내용을 입력해 주세요.", true);
        return;
      }
      if (!isLoggedIn) {
        openNewsCommentLogin(() => form.requestSubmit());
        return;
      }

      const panel = form.closest("[data-news-comments-post-id]");
      const otherDrafts = panel
        ? newsInlineCommentDrafts(panel).filter((candidate) => candidate !== textarea)
        : [];
      const otherImageDrafts = panel
        ? newsInlineCommentImageDraftForms(panel).filter((candidate) => candidate !== form)
        : [];
      if (otherDrafts.length > 0 || otherImageDrafts.length > 0) {
        if (!window.confirm("작성 중인 답글을 버리고 이 댓글을 등록하시겠습니까?")) return;
        discardNewsInlineCommentDrafts(panel, textarea);
      }

      const imageFile = selectedImage;
      textarea.disabled = true;
      submit.disabled = true;
      imageButton.disabled = true;
      emojiButton.disabled = true;
      closeNewsEmojiPicker();
      try {
        const payload = await Api.post(`/board/posts/${encodeURIComponent(postId)}/comments`, { content: value });
        const createdCommentId = payload.data?.commentId;
        let imageUploadError = null;
        if (imageFile && createdCommentId) {
          try {
            await uploadNewsCommentImage(createdCommentId, imageFile);
          } catch (error) {
            imageUploadError = error;
          }
        }

        window.FooduckBoard?.invalidateBoardCache?.();
        textarea.value = "";
        emojis?.refreshEditor?.(textarea);
        clearImage();
        updateNewsCommentCharacterCount(textarea, characterCount);
        resizeNewsCommentTextarea(textarea, 105);
        if (imageUploadError && createdCommentId) {
          state.imageRetry = {
            commentId: createdCommentId,
            file: imageFile,
            label: "댓글",
            errorMessage: imageUploadError.message || "사진만 다시 올릴 수 있습니다.",
          };
          showNewsCommentToast(`댓글은 등록됐지만 사진 업로드에 실패했습니다. ${state.imageRetry.errorMessage}`, true);
        } else {
          showNewsCommentToast(imageFile ? "댓글과 사진이 등록되었습니다." : (payload.message || "댓글이 등록되었습니다."));
        }
        if (state.allLoaded) {
          await loadAllNewsComments(postId, { forceRefresh: true });
        } else {
          const rootCount = Math.max(0, Number(state.pageData?.totalElements) || 0);
          const targetPage = Math.floor(rootCount / NEWS_COMMENT_PAGE_SIZE);
          await loadNewsComments(postId, targetPage, { forceRefresh: true });
        }
      } catch (error) {
        textarea.disabled = withdrawnAuthor;
        submit.disabled = withdrawnAuthor;
        imageButton.disabled = withdrawnAuthor;
        emojiButton.disabled = withdrawnAuthor;
        const panel = form.closest("[data-news-comments-post-id]");
        const status = panel?.querySelector("[data-news-comment-status]");
        if (status) status.textContent = error.message || "댓글 등록에 실패했습니다.";
      }
    });

    form.append(heading, label, textarea, emojiPanel, preview, submitRow);
    setupNewsEmojiPicker(textarea, emojiButton, emojiPanel);
    return form;
  }

  function renderNewsComments(postId) {
    const state = newsCommentStates.get(String(postId));
    const body = panels.news.querySelector(`[data-news-comments-body="${CSS.escape(String(postId))}"]`);
    if (!state || !body) return;
    body.replaceChildren();

    if (state.loading && !state.loaded) {
      body.append(newsCommentElement("p", "store-news-comment-loading", "댓글을 불러오는 중입니다."));
      return;
    }

    if (!state.loaded || !state.pageData) {
      if (state.errorMessage) {
        const error = newsCommentElement("div", "store-news-comment-load-error");
        error.append(newsCommentElement("span", "", state.errorMessage));
        const retry = newsCommentElement("button", "button button-secondary button-sm", "다시 시도");
        retry.type = "button";
        retry.addEventListener("click", () => loadNewsComments(postId, state.page || 0));
        error.append(retry);
        body.append(error);
      } else {
        body.append(newsCommentElement("p", "store-news-comment-loading", "댓글 보기를 눌러 댓글을 불러올 수 있습니다."));
      }
      return;
    }

    const header = newsCommentElement("div", "store-news-comments-heading");
    header.append(newsCommentElement("strong", "", `댓글 ${Math.max(0, Number(state.totalCount) || 0)}개`));
    if (!state.allLoaded && state.totalPages > 1) {
      const loadAll = newsCommentElement(
        "button",
        "store-news-comments-detail-link",
        state.allLoading ? "불러오는 중..." : "전체 댓글 불러오기",
      );
      loadAll.type = "button";
      loadAll.disabled = state.allLoading || state.loading;
      loadAll.addEventListener("click", () => {
        const panel = loadAll.closest("[data-news-comments-post-id]");
        if (panel && !confirmNewsInlineCommentDraftDiscard(
          panel,
          "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 전체 댓글을 불러오시겠습니까?",
        )) return;
        void loadAllNewsComments(postId);
      });
      header.append(loadAll);
    } else if (state.allLoaded && state.totalCount > NEWS_COMMENT_PAGE_SIZE) {
      header.append(newsCommentElement("span", "store-news-comments-all-loaded", "전체 댓글을 불러왔습니다."));
    }
    body.append(header);

    const status = newsCommentElement("p", "store-news-comment-status", state.cacheNotice || state.errorMessage || "");
    status.dataset.newsCommentStatus = "true";
    status.setAttribute("role", "status");
    body.append(status);

    const imageRetry = renderNewsCommentImageRetry(postId, state);
    if (imageRetry) body.append(imageRetry);

    const comments = state.allLoaded
      ? (Array.isArray(state.allComments) ? state.allComments : [])
      : (Array.isArray(state.pageData.content) ? state.pageData.content : []);
    const list = newsCommentElement("div", "comment-list store-news-comment-list");
    if (!comments.length) {
      list.append(newsCommentElement("p", "comment-empty", "첫 댓글을 남겨 보세요."));
    } else {
      appendNewsCommentThreads(postId, list, comments, state);
    }
    body.append(list);
    if (!state.allLoaded) body.append(newsCommentPageButtons(state));
    body.append(renderNewsCommentForm(postId, state));
  }

  async function fetchNewsCommentPage(postId, page, size = NEWS_COMMENT_PAGE_SIZE, options = {}) {
    const normalizedPage = Math.max(0, Number(page) || 0);
    const normalizedSize = Math.max(1, Math.min(NEWS_COMMENT_ALL_PAGE_SIZE, Number(size) || NEWS_COMMENT_PAGE_SIZE));
    const forceRefresh = options.forceRefresh === true;
    const path = `/board/posts/${encodeURIComponent(postId)}/comments?page=${normalizedPage}&size=${normalizedSize}`;
    const board = window.FooduckBoard;
    const cached = forceRefresh ? null : (board?.readBoardCache?.(path) || null);
    if (cached?.fresh) return { data: cached.data || {}, cacheFallback: false };
    try {
      const response = await Api.get(path, forceRefresh ? { cache: "no-store" } : {});
      const pageData = response.data || {};
      board?.writeBoardCache?.(path, pageData);
      return { data: pageData, cacheFallback: false };
    } catch (error) {
      if (cached && canUseNewsCacheAfterError(error)) {
        return { data: cached.data || {}, cacheFallback: true };
      }
      throw error;
    }
  }

  async function loadNewsComments(postId, page = 0, options = {}) {
    const state = getNewsCommentState(postId);
    const generation = ++state.generation;
    const requestedPage = Math.max(0, Number(page) || 0);
    state.loading = true;
    state.allLoading = false;
    state.allLoaded = false;
    state.allComments = null;
    state.open = true;
    state.errorMessage = "";
    syncNewsCommentToggle(postId);
    renderNewsComments(postId);
    try {
      const result = await fetchNewsCommentPage(postId, requestedPage, NEWS_COMMENT_PAGE_SIZE, {
        forceRefresh: options.forceRefresh === true,
      });
      if (generation !== state.generation) return;
      const pageData = result.data || {};
      const totalPages = Math.max(0, Number(pageData.totalPages) || 0);
      if (totalPages > 0 && requestedPage >= totalPages) {
        state.loading = false;
        await loadNewsComments(postId, totalPages - 1, options);
        return;
      }
      state.page = Math.max(0, Number(pageData.page) || 0);
      state.totalPages = totalPages;
      state.totalCount = Math.max(0, Number(pageData.totalCommentCount ?? pageData.totalElements) || 0);
      state.pageData = pageData;
      state.loaded = true;
      state.cacheNotice = result.cacheFallback
        ? "최신 댓글을 불러오지 못해 잠시 저장된 댓글을 보여드리고 있습니다."
        : "";
    } catch (error) {
      if (generation !== state.generation) return;
      state.cacheNotice = "";
      state.errorMessage = error.message || "댓글을 불러오지 못했습니다.";
      if (!state.loaded) {
        state.pageData = null;
        state.totalCount = null;
      }
    } finally {
      if (generation === state.generation) {
        state.loading = false;
        syncNewsCommentToggle(postId);
        renderNewsComments(postId);
      }
    }
  }

  async function loadAllNewsComments(postId, options = {}) {
    const state = getNewsCommentState(postId);
    const generation = ++state.generation;
    state.loading = true;
    state.allLoading = true;
    state.open = true;
    state.errorMessage = "";
    syncNewsCommentToggle(postId);
    renderNewsComments(postId);

    try {
      const firstResult = await fetchNewsCommentPage(postId, 0, NEWS_COMMENT_ALL_PAGE_SIZE, {
        forceRefresh: options.forceRefresh === true,
      });
      if (generation !== state.generation) return;
      const firstPage = firstResult.data || {};
      const totalPages = Math.max(0, Number(firstPage.totalPages) || 0);
      const combined = Array.isArray(firstPage.content) ? [...firstPage.content] : [];
      let usedCacheFallback = firstResult.cacheFallback;

      for (let page = 1; page < totalPages; page += 1) {
        const result = await fetchNewsCommentPage(postId, page, NEWS_COMMENT_ALL_PAGE_SIZE, {
          forceRefresh: options.forceRefresh === true,
        });
        if (generation !== state.generation) return;
        if (Array.isArray(result.data?.content)) combined.push(...result.data.content);
        usedCacheFallback = usedCacheFallback || result.cacheFallback;
      }

      state.page = 0;
      state.totalPages = totalPages;
      state.totalCount = Math.max(0, Number(firstPage.totalCommentCount ?? firstPage.totalElements) || 0);
      state.pageData = firstPage;
      state.allComments = combined;
      state.allLoaded = true;
      state.loaded = true;
      state.cacheNotice = usedCacheFallback
        ? "최신 댓글 일부를 불러오지 못해 저장된 댓글이 함께 표시될 수 있습니다."
        : "";
    } catch (error) {
      if (generation !== state.generation) return;
      state.errorMessage = error.message || "전체 댓글을 불러오지 못했습니다.";
    } finally {
      if (generation === state.generation) {
        state.loading = false;
        state.allLoading = false;
        syncNewsCommentToggle(postId);
        renderNewsComments(postId);
      }
    }
  }

  function bindNewsInlineComments() {
    panels.news.querySelectorAll("[data-news-comments-toggle]").forEach((button) => {
      const postId = button.dataset.newsCommentsToggle;
      if (!postId) return;
      button.addEventListener("click", () => {
        const state = getNewsCommentState(postId);
        const panel = panels.news.querySelector(`[data-news-comments-post-id="${CSS.escape(postId)}"]`);
        if (!panel) return;
        state.open = !state.open;
        panel.hidden = !state.open;
        syncNewsCommentToggle(postId);
        if (!state.open) return;
        if (state.loaded) renderNewsComments(postId);
        else void loadNewsComments(postId, 0);
      });
    });

    panels.news.querySelectorAll("[data-news-comments-post-id]").forEach((panel) => {
      const postId = panel.dataset.newsCommentsPostId;
      const state = postId ? newsCommentStates.get(postId) : null;
      if (!state?.open) return;
      panel.hidden = false;
      if (state.loaded || state.loading || state.errorMessage) renderNewsComments(postId);
      else void loadNewsComments(postId, state.page || 0);
    });
  }

  function newsLikeButtonHtml(news) {
    if (news?.postId == null) return "";
    const liked = news.likedByCurrentUser === true;
    const likeCount = Math.max(0, Number(news.likeCount) || 0);
    return `
      <button type="button"
              class="button button-sm ${liked ? "button-primary" : "button-secondary"} store-news-like"
              data-news-like="${escapeHtml(news.postId)}"
              data-news-liked="${liked}"
              aria-pressed="${liked}">
        ${liked ? "추천 취소" : "추천"} · ${likeCount}
      </button>
    `;
  }

  function newsManageActionsHtml(news, itemCount) {
    if (!canWriteNews() || news.postId == null) return "";
    return `
      <div class="store-news-actions">
        <a class="button button-secondary button-sm"
           href="${escapeHtml(newsBoardPath("write", news.postId))}">수정</a>
        <button type="button" class="button button-secondary button-sm store-news-delete"
                data-news-delete="${escapeHtml(news.postId)}"
                data-news-item-count="${itemCount}">삭제</button>
      </div>
    `;
  }

  function newsWriteButtonHtml() {
    if (!canWriteNews()) return "";
    return `
      <button type="button" class="button button-primary button-sm" id="store-news-write-toggle"
              aria-controls="store-news-form" aria-expanded="false">글쓰기</button>
    `;
  }

  function newsWriteFormHtml() {
    if (!canWriteNews()) return "";
    return `
      <div class="store-write-form store-news-form" id="store-news-form" hidden>
        <h3>소식 작성</h3>
        <label class="store-news-field-label" for="store-news-title">제목 <span id="store-news-title-count">0/200</span></label>
        <input type="text" id="store-news-title" maxlength="200" placeholder="제목">
        <label class="store-news-field-label" for="store-news-content">내용 <span id="store-news-content-count">0/10000</span></label>
        <textarea id="store-news-content" maxlength="10000" placeholder="소식 내용을 입력하세요"></textarea>
        <div class="board-write-content-tools">
          <button type="button" id="store-news-emoji-toggle"
                  class="comment-emoji-toggle board-write-emoji-toggle"
                  aria-label="소식 내용에 이모지 입력"
                  aria-expanded="false"
                  aria-controls="store-news-emoji-panel">
            <span class="board-write-action-emoji" aria-hidden="true">🐸</span>
            <span>이모지</span>
          </button>
        </div>
        <div id="store-news-emoji-panel"
             class="comment-emoji-panel board-write-emoji-panel"
             role="group"
             aria-label="이모지 선택"
             hidden></div>
        <div class="store-news-attachment">
          <div class="store-news-attachment__head">
            <div>
              <strong>사진 · 동영상</strong>
              <small>사진 20MB, 동영상 100MB · 최대 10개</small>
            </div>
            <button type="button" class="button button-secondary button-sm" id="store-news-media-select">파일 선택</button>
          </div>
          <input type="file" id="store-news-media-input" accept="image/*,video/*" multiple hidden>
          <div class="store-news-attachment-list" id="store-news-media-list"></div>
          <p class="store-news-attachment-status" id="store-news-media-status" role="status" aria-live="polite"></p>
        </div>
        <button type="button" class="button button-primary button-sm" id="store-news-submit">소식 등록</button>
      </div>
    `;
  }

  function newsFileExtension(fileName) {
    const dot = String(fileName || "").lastIndexOf(".");
    return dot < 0 ? "" : String(fileName).slice(dot + 1).toLowerCase();
  }

  function newsSelectedMediaKind(fileName) {
    const extension = newsFileExtension(fileName);
    if (NEWS_IMAGE_EXTENSIONS.has(extension)) return "IMAGE";
    if (NEWS_VIDEO_EXTENSIONS.has(extension)) return "VIDEO";
    return null;
  }

  function setNewsMediaStatus(message, isError = false) {
    const status = document.getElementById("store-news-media-status");
    if (!status) return;
    status.textContent = message || "";
    status.classList.toggle("is-error", Boolean(isError));
  }

  function resetNewsSelectedMedia() {
    newsSelectedMedia.forEach((entry) => URL.revokeObjectURL(entry.previewUrl));
    newsSelectedMedia = [];
    newsMediaBusy = false;
  }

  function setNewsMediaBusy(busy) {
    newsMediaBusy = busy;
    const form = document.getElementById("store-news-form");
    const selectButton = document.getElementById("store-news-media-select");
    const mediaInput = document.getElementById("store-news-media-input");
    const submitButton = document.getElementById("store-news-submit");
    const emojiButton = document.getElementById("store-news-emoji-toggle");
    if (selectButton) selectButton.disabled = busy;
    if (mediaInput) mediaInput.disabled = busy;
    if (submitButton) submitButton.disabled = busy;
    if (emojiButton) emojiButton.disabled = busy || newsCreatedPostId != null;
    if (busy || newsCreatedPostId != null) closeNewsEmojiPicker();
    form?.querySelectorAll('input[type="text"], textarea').forEach((field) => {
      field.disabled = busy || newsCreatedPostId != null;
    });
    renderNewsSelectedMedia();
  }

  function newsSelectedMediaPreview(entry) {
    const kind = newsSelectedMediaKind(entry.file.name);
    const safeName = escapeHtml(entry.file.name || "첨부파일");
    if (kind === "IMAGE") {
      return `<img src="${escapeHtml(entry.previewUrl)}" alt="${safeName} 미리보기">`;
    }
    return `
      <video src="${escapeHtml(entry.previewUrl)}" preload="metadata" muted aria-label="${safeName} 미리보기"></video>
      <span class="store-news-attachment-video-badge">동영상</span>
    `;
  }

  function renderNewsSelectedMedia() {
    const list = document.getElementById("store-news-media-list");
    if (!list) return;
    if (newsSelectedMedia.length === 0) {
      list.innerHTML = '<p class="store-news-attachment-empty">선택한 사진이나 동영상이 없습니다.</p>';
      if (!newsMediaBusy) setNewsMediaStatus(`0/${NEWS_MAX_MEDIA_COUNT}개 첨부`);
      return;
    }
    list.innerHTML = newsSelectedMedia.map((entry) => {
      const kind = newsSelectedMediaKind(entry.file.name);
      return `
        <article class="store-news-attachment-item">
          <div class="store-news-attachment-preview">${newsSelectedMediaPreview(entry)}</div>
          <div class="store-news-attachment-meta">
            <strong title="${escapeHtml(entry.file.name)}">${escapeHtml(entry.file.name)}</strong>
            <small>${kind === "IMAGE" ? "사진" : "동영상"} · ${newsFormatBytes(entry.file.size)}</small>
            <button type="button" class="button button-secondary button-sm" data-news-media-remove="${entry.key}"${newsMediaBusy ? " disabled" : ""}>선택 취소</button>
          </div>
        </article>
      `;
    }).join("");
    list.querySelectorAll("[data-news-media-remove]").forEach((button) => {
      button.addEventListener("click", () => {
        if (newsMediaBusy) return;
        const key = Number(button.dataset.newsMediaRemove);
        const target = newsSelectedMedia.find((entry) => entry.key === key);
        if (target) URL.revokeObjectURL(target.previewUrl);
        newsSelectedMedia = newsSelectedMedia.filter((entry) => entry.key !== key);
        renderNewsSelectedMedia();
      });
    });
    if (!newsMediaBusy) {
      setNewsMediaStatus(`${newsSelectedMedia.length}/${NEWS_MAX_MEDIA_COUNT}개 첨부`);
    }
  }

  function validateNewsSelectedFile(file) {
    const kind = newsSelectedMediaKind(file.name);
    if (!kind) return `${file.name}: 지원하지 않는 사진·동영상 형식입니다.`;
    if (file.size < 1) return `${file.name}: 비어 있는 파일입니다.`;
    if (kind === "IMAGE" && file.size > NEWS_MAX_IMAGE_BYTES) {
      return `${file.name}: 사진 20MB 제한을 초과했습니다.`;
    }
    if (kind === "VIDEO" && file.size > NEWS_MAX_VIDEO_BYTES) {
      return `${file.name}: 동영상 100MB 제한을 초과했습니다.`;
    }
    const duplicated = newsSelectedMedia.some((entry) =>
      entry.file.name === file.name &&
      entry.file.size === file.size &&
      entry.file.lastModified === file.lastModified
    );
    if (duplicated) return `${file.name}: 이미 선택한 파일입니다.`;
    return null;
  }

  function addNewsSelectedFiles(files) {
    const errors = [];
    for (const file of files) {
      if (newsSelectedMedia.length >= NEWS_MAX_MEDIA_COUNT) {
        errors.push(`첨부파일은 최대 ${NEWS_MAX_MEDIA_COUNT}개까지 등록할 수 있습니다.`);
        break;
      }
      const validationError = validateNewsSelectedFile(file);
      if (validationError) {
        errors.push(validationError);
        continue;
      }
      newsSelectedMedia.push({
        key: ++newsSelectedMediaSequence,
        file,
        previewUrl: URL.createObjectURL(file),
      });
    }
    renderNewsSelectedMedia();
    if (errors.length) setNewsMediaStatus(errors.join(" "), true);
  }

  function uploadNewsMediaFile(targetPostId, entry, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open(
        "POST",
        `/api/board/posts/${encodeURIComponent(targetPostId)}/media`,
        true,
      );
      xhr.withCredentials = true;
      xhr.setRequestHeader("Accept", "application/json");
      xhr.setRequestHeader(
        "Content-Type",
        entry.file.type || "application/octet-stream",
      );
      xhr.setRequestHeader("X-File-Name", encodeURIComponent(entry.file.name));
      const token = Api.getToken?.();
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
          if (xhr.status === 401) Api.clearToken?.();
          const message = typeof payload === "object" && payload
            ? payload.message
            : `첨부파일 업로드에 실패했습니다. (${xhr.status})`;
          reject(new Error(message || "첨부파일 업로드에 실패했습니다."));
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

  async function uploadNewsSelectedMedia(targetPostId) {
    const failures = [];
    const failedEntries = [];
    const filesToUpload = [...newsSelectedMedia];
    for (let index = 0; index < filesToUpload.length; index += 1) {
      const entry = filesToUpload[index];
      setNewsMediaStatus(
        `${entry.file.name} 업로드 중 (${index + 1}/${filesToUpload.length})`,
      );
      try {
        await uploadNewsMediaFile(targetPostId, entry, (loaded, total) => {
          if (total > 0 && loaded >= total) {
            setNewsMediaStatus(
              `${entry.file.name} 전송 완료 · 서버 저장 중 (${index + 1}/${filesToUpload.length})`,
            );
            return;
          }
          const progress = total > 0
            ? `${Math.min(99, Math.round((loaded / total) * 100))}%`
            : newsFormatBytes(loaded);
          setNewsMediaStatus(
            `${entry.file.name} 업로드 중 ${progress} (${index + 1}/${filesToUpload.length})`,
          );
        });
        URL.revokeObjectURL(entry.previewUrl);
      } catch (error) {
        failedEntries.push(entry);
        failures.push(`${entry.file.name}: ${error.message || "업로드 실패"}`);
      }
    }
    newsSelectedMedia = failedEntries;
    return failures;
  }

  function bindNewsForm() {
    const toggleButton = document.getElementById("store-news-write-toggle");
    const form = document.getElementById("store-news-form");
    const titleInput = document.getElementById("store-news-title");
    const contentInput = document.getElementById("store-news-content");
    const titleCount = document.getElementById("store-news-title-count");
    const contentCount = document.getElementById("store-news-content-count");
    const emojiToggle = document.getElementById("store-news-emoji-toggle");
    const emojiPanel = document.getElementById("store-news-emoji-panel");
    setupNewsEmojiPicker(contentInput, emojiToggle, emojiPanel);
    const updateCounts = () => {
      if (titleCount) titleCount.textContent = `${titleInput?.value.length || 0}/200`;
      if (contentCount) contentCount.textContent = `${contentInput?.value.length || 0}/10000`;
    };
    const syncNewsContent = () => {
      updateCounts();
      resizeStoreWriteTextarea(contentInput, 90);
    };
    titleInput?.addEventListener("input", updateCounts);
    contentInput?.addEventListener("input", syncNewsContent);
    syncNewsContent();

    if (toggleButton && form) {
      toggleButton.addEventListener("click", () => {
        const willOpen = form.hidden;
        form.hidden = !willOpen;
        toggleButton.setAttribute("aria-expanded", String(willOpen));
        toggleButton.textContent = willOpen ? "닫기" : "글쓰기";
        if (!willOpen) closeNewsEmojiPicker();
        if (willOpen) {
          resizeStoreWriteTextarea(contentInput, 90);
          window.requestAnimationFrame(() => resizeStoreWriteTextarea(contentInput, 90));
          titleInput?.focus();
        }
      });
    }

    const mediaSelectButton = document.getElementById("store-news-media-select");
    const mediaInput = document.getElementById("store-news-media-input");
    mediaSelectButton?.addEventListener("click", () => mediaInput?.click());
    mediaInput?.addEventListener("change", () => {
      addNewsSelectedFiles([...(mediaInput.files || [])]);
      mediaInput.value = "";
    });
    renderNewsSelectedMedia();

    const submitButton = document.getElementById("store-news-submit");
    if (!submitButton) return;
    submitButton.addEventListener("click", async () => {
      const title = titleInput?.value.trim() || "";
      const body = contentInput?.value.trim() || "";
      const retryingAttachments = newsCreatedPostId != null;
      if (!retryingAttachments && (!title || !body)) {
        window.alert("제목과 내용을 입력해 주세요.");
        return;
      }
      if (hasNewsInlineCommentDraft()) {
        if (!window.confirm("작성 중인 댓글이나 답글이 있습니다. 이를 버리고 가게 소식을 등록하시겠습니까?")) return;
        discardNewsInlineCommentDrafts();
      }
      if (retryingAttachments && newsSelectedMedia.length === 0) {
        newsCreatedPostId = null;
        await loadNews(0);
        return;
      }

      setNewsMediaBusy(true);
      try {
        if (!retryingAttachments) {
          setNewsMediaStatus("소식을 등록하는 중입니다.");
          const created = await Api.post(newsApiPath(), { title, content: body });
          newsCreatedPostId = created?.data?.postId ?? null;
          if (newsSelectedMedia.length > 0 && newsCreatedPostId == null) {
            throw new Error("소식은 등록되었지만 첨부파일을 연결할 게시글 정보를 확인하지 못했습니다.");
          }
        }

        const failures = newsCreatedPostId == null
          ? []
          : await uploadNewsSelectedMedia(newsCreatedPostId);
        window.FooduckBoard?.invalidateBoardCache?.();

        if (failures.length > 0) {
          setNewsMediaBusy(false);
          submitButton.textContent = "첨부 다시 시도";
          setNewsMediaStatus(
            `소식은 등록되었습니다. 실패한 첨부 ${failures.length}개만 남겨 두었습니다. 다시 시도해 주세요.`,
            true,
          );
          return;
        }

        newsCreatedPostId = null;
        resetNewsSelectedMedia();
        await loadNews(0);
      } catch (error) {
        if (newsCreatedPostId != null) {
          window.FooduckBoard?.invalidateBoardCache?.();
          setNewsMediaBusy(false);
          submitButton.textContent = newsSelectedMedia.length > 0
            ? "첨부 다시 시도"
            : "소식 목록 새로고침";
          setNewsMediaStatus(
            `소식은 등록되었지만 첨부파일 처리 중 문제가 발생했습니다. ${error.message || "첨부파일 업로드에 실패했습니다."}`,
            true,
          );
        } else {
          setNewsMediaBusy(false);
          setNewsMediaStatus(error.message || "소식 등록에 실패했습니다.", true);
          window.alert(error.message || "소식 등록에 실패했습니다.");
        }
      }
    });
  }

  function newsPaginationHtml(pageData) {
    const totalPages = Math.max(0, Number(pageData.totalPages) || 0);
    if (totalPages <= 1) return "";

    const currentPage = Math.max(0, Number(pageData.page) || 0);
    const block = window.FooduckPagination.block(currentPage, totalPages);
    const pageButtons = [];
    for (let page = block.start; page < block.end; page += 1) {
      pageButtons.push(`
        <button type="button" class="store-news-page-button${page === currentPage ? " is-active" : ""}"
                data-news-page="${page}"${page === currentPage ? ' aria-current="page"' : ""}>${page + 1}</button>
      `);
    }
    return `
      <nav class="store-news-pagination" aria-label="소식 페이지">
        <button type="button" class="store-news-page-button" aria-label="이전 페이지 묶음"
                data-news-page="${block.previousPage}"${block.hasPrevious ? "" : " disabled"}>‹</button>
        ${pageButtons.join("")}
        <button type="button" class="store-news-page-button" aria-label="다음 페이지 묶음"
                data-news-page="${block.nextPage}"${block.hasNext ? "" : " disabled"}>›</button>
      </nav>
    `;
  }

  function bindNewsPanelActions() {
    bindNewsForm();
    panels.news.querySelectorAll("[data-news-like]").forEach((button) => {
      button.addEventListener("click", async () => {
        const board = window.FooduckBoard;
        if (!board?.requireLogin?.(window.location.pathname + window.location.search)) {
          return;
        }
        const postId = button.dataset.newsLike;
        if (!postId) return;

        const liked = button.dataset.newsLiked === "true";
        button.disabled = true;
        try {
          const response = liked
            ? await Api.delete(`/board/posts/${encodeURIComponent(postId)}/like`)
            : await Api.post(`/board/posts/${encodeURIComponent(postId)}/like`, {});
          const nextLiked = response.data?.liked === true;
          const nextCount = Math.max(0, Number(response.data?.likeCount) || 0);
          button.dataset.newsLiked = String(nextLiked);
          button.setAttribute("aria-pressed", String(nextLiked));
          button.classList.toggle("button-primary", nextLiked);
          button.classList.toggle("button-secondary", !nextLiked);
          button.textContent = `${nextLiked ? "추천 취소" : "추천"} · ${nextCount}`;
          board.invalidateBoardCache?.();
        } catch (error) {
          window.alert(error.message || "추천 처리 중 오류가 발생했습니다.");
        } finally {
          button.disabled = false;
        }
      });
    });
    panels.news.querySelectorAll("[data-news-page]").forEach((button) => {
      button.addEventListener("click", () => {
        const targetPage = Number(button.dataset.newsPage);
        if (Number.isInteger(targetPage) && targetPage >= 0 && targetPage !== newsPage) {
          if (!confirmNewsDraftDiscard()) return;
          void loadNews(targetPage).then(() => {
            panels.news.querySelector(".store-section-card")?.scrollIntoView({
              behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
              block: "start",
            });
          });
        }
      });
    });
    panels.news.querySelectorAll("[data-news-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        if (!canWriteNews()) return;
        const postId = button.dataset.newsDelete;
        if (!postId) return;
        if (!confirmNewsDraftDiscard()) return;
        if (!window.confirm("이 소식을 삭제하시겠습니까?")) return;

        const itemCount = Number(button.dataset.newsItemCount);
        button.disabled = true;
        try {
          await Api.delete(newsDeleteApiPath(postId));
          window.FooduckBoard?.invalidateBoardCache?.();
          const targetPage = newsPage > 0 && itemCount === 1
            ? newsPage - 1
            : newsPage;
          await loadNews(targetPage);
        } catch (error) {
          window.alert(error.message || "소식 삭제에 실패했습니다.");
          button.disabled = false;
        }
      });
    });
    bindNewsInlineComments();
    document.getElementById("store-news-retry")?.addEventListener("click", () => {
      if (!confirmNewsDraftDiscard()) return;
      void loadNews(newsPage);
    });
  }

  function bindNewsAuthors(items) {
    const board = window.FooduckBoard;
    if (!board?.authorIdentity) return;

    panels.news.querySelectorAll("[data-news-author-index]").forEach((host) => {
      const index = Number(host.dataset.newsAuthorIndex);
      const news = Number.isInteger(index) ? items[index] : null;
      if (!news?.authorAccountId || !news.authorNickname) return;
      host.replaceChildren(board.authorIdentity(news, {
        showNickname: true,
        showAuthorMenu: true,
        authorMenuContext: "NEWS",
        authorActivityCueMode: "full",
      }));
    });
  }

  function renderNewsCard(bodyHtml, paginationHtml = "") {
    panels.news.innerHTML = `
      <div class="store-section-card">
        <div class="store-news-header">
          <h2>가게 소식</h2>
          ${newsWriteButtonHtml()}
        </div>
        ${newsWriteFormHtml()}
        ${bodyHtml}
        ${paginationHtml}
      </div>
    `;
    bindNewsPanelActions();
  }

  function renderNewsPanel(pageData) {
    const items = Array.isArray(pageData.content) ? pageData.content : [];
    const bodyHtml = items.length === 0
      ? '<div class="store-empty">아직 등록된 소식이 없습니다.</div>'
      : `<div class="store-news-list">${items.map((news, index) => `
          <article class="store-news-item">
            <div class="store-news-item-head">
              ${newsSummaryHtml(news)}
              ${newsManageActionsHtml(news, items.length)}
            </div>
            <div class="store-news-media" data-news-media-post-id="${escapeHtml(news.postId ?? "")}" hidden></div>
            <div class="store-news-meta">
              <div class="store-news-meta-copy">
                <span class="store-news-author" data-news-author-index="${index}">${escapeHtml(news.authorNickname || "-")}</span>
                <span class="store-news-date">${formatDate(news.createdAt)}${news.edited ? " · 수정됨" : ""} · 조회 ${Number(news.viewCount || 0).toLocaleString("ko-KR")}</span>
              </div>
              <div class="store-news-engagement">
                ${newsLikeButtonHtml(news)}
                ${newsCommentsToggleHtml(news)}
              </div>
            </div>
            ${newsCommentsPanelHtml(news)}
          </article>
        `).join("")}</div>`;
    renderNewsCard(bodyHtml, newsPaginationHtml(pageData));
    const contentNodes = panels.news.querySelectorAll(".store-news-list .store-news-content");
    items.forEach((news, index) => renderCustomEmojiText(contentNodes[index], news.content || ""));
    bindNewsAuthors(items);
    bindNewsMedia(items);
    scheduleNewsSummaryTruncationCheck();
  }

  function renderNewsError(error) {
    const message = escapeHtml(error.message || "소식을 불러오지 못했습니다.");
    renderNewsCard(`
      <div class="store-empty store-news-error">
        <span>${message}</span>
        <button type="button" class="button button-secondary button-sm" id="store-news-retry">다시 시도</button>
      </div>
    `);
  }

  function canUseNewsCacheAfterError(error) {
    return ![401, 403, 404].includes(Number(error?.status));
  }

  function newsCacheHasCommentCounts(cached) {
    const items = Array.isArray(cached?.data?.content) ? cached.data.content : [];
    return items.every((item) => item?.commentCount != null
      && Number.isFinite(Number(item.commentCount)));
  }

  function renderNewsCachedNotice() {
    const card = panels.news.querySelector(".store-section-card");
    if (!card || card.querySelector(".store-news-cache-notice")) return;
    const notice = document.createElement("p");
    notice.className = "store-news-cache-notice";
    notice.setAttribute("role", "status");
    notice.textContent = "최신 소식을 불러오지 못해 잠시 저장된 내용을 보여드리고 있습니다.";
    const header = card.querySelector(".store-news-header");
    if (header) header.insertAdjacentElement("afterend", notice);
    else card.prepend(notice);
  }

  async function loadNews(page = newsPage) {
    const generation = ++newsRequestGeneration;
    const requestedPage = Number.isInteger(page) && page >= 0 ? page : 0;
    const board = window.FooduckBoard;
    const requestParams = new URLSearchParams({
      page: String(requestedPage),
      size: String(NEWS_PAGE_SIZE),
    });
    const path = `${newsApiPath()}?${requestParams.toString()}`;
    const cachedCandidate = board?.readBoardCache?.(path) || null;
    const cached = newsCacheHasCommentCounts(cachedCandidate)
      ? cachedCandidate
      : null;

    clearNewsMediaPolls();
    newsMediaGeneration += 1;
    newsPage = requestedPage;

    if (cached) {
      const cachedPageData = cached.data || {};
      if (generation !== newsRequestGeneration) return;
      newsPage = Number.isInteger(cachedPageData.page) && cachedPageData.page >= 0
        ? cachedPageData.page
        : requestedPage;
      renderNewsPanel({ ...cachedPageData, page: newsPage });
      if (cached.fresh) return;
    } else {
      panels.news.innerHTML = '<div class="store-empty">소식을 불러오는 중입니다.</div>';
    }

    try {
      const response = await Api.get(path);
      if (generation !== newsRequestGeneration) return;
      const pageData = response.data || {};
      const totalPages = Math.max(0, Number(pageData.totalPages) || 0);
      if (totalPages > 0 && requestedPage >= totalPages) {
        await loadNews(totalPages - 1);
        return;
      }
      board?.writeBoardCache?.(path, pageData);
      newsPage = Number.isInteger(pageData.page) && pageData.page >= 0
        ? pageData.page
        : requestedPage;

      if (cached) {
        clearNewsMediaPolls();
        newsMediaGeneration += 1;
      }
      renderNewsPanel({ ...pageData, page: newsPage });
    } catch (error) {
      if (generation !== newsRequestGeneration) return;
      if (!cached || !canUseNewsCacheAfterError(error)) {
        clearNewsMediaPolls();
        newsMediaGeneration += 1;
        renderNewsError(error);
      } else {
        renderNewsCachedNotice();
      }
    }
  }

  function renderStat(label, value) {
    const wrapper = document.createElement("div");
    wrapper.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong>`;
    return wrapper.outerHTML;
  }

  function bindStoreOwnerAuthor(store) {
    const host = basicInfo.querySelector("[data-store-owner-author]");
    if (!host) return;

    const nickname = store?.ownerNickname || "사장님";
    const authorAccountId = Number(store?.ownerAccountId);
    const board = window.FooduckBoard;

    if (
      board?.authorIdentity
      && Number.isSafeInteger(authorAccountId)
      && authorAccountId > 0
      && nickname !== "탈퇴한 회원"
    ) {
      host.replaceChildren(board.authorIdentity(
        {
          authorAccountId,
          authorNickname: nickname,
        },
        {
          showNickname: true,
          showAuthorMenu: true,
          showLoginIdentity: false,
          showRole: false,
          authorMenuContext: "COMMUNITY",
          authorActivityCueMode: "full",
        },
      ));
      return;
    }

    host.textContent = nickname;
  }

  function renderOwnedDetail(store) {
    storeId = store.restaurantId;
    isOwner = Boolean(store.isOwner);

    nameEl.textContent = store.name;
    addressEl.textContent = [store.address, store.addressDetail].filter(Boolean).join(" ");

    const statusBadge = store.status === "ACTIVE"
      ? '<span class="store-badge store-badge--active">영업중</span>'
      : '<span class="store-badge store-badge--inactive">영업 준비중</span>';
    badgesEl.innerHTML = statusBadge
      + (store.categoryName ? `<span class="store-badge store-badge--category">${escapeHtml(store.categoryName)}</span>` : "");

    statsRow.innerHTML = [
      renderStat("평점", store.averageRating != null ? store.averageRating.toFixed(1) : "-"),
      renderStat("리뷰", `${store.reviewCount}건`),
      renderStat("확인 메뉴", `${store.menuCount}개`),
      renderStat("등록일", formatDate(store.createdAt)),
    ].join("");

    favoriteBtn.hidden = !window.FooduckSession || !window.FooduckSession.authenticated;
    favoriteBtn.classList.toggle("is-favorited", store.favoritedByMe);
    favoriteBtn.setAttribute("aria-pressed", String(store.favoritedByMe));
    favoriteBtn.addEventListener("click", async () => {
      favoriteBtn.disabled = true;
      try {
        const response = store.favoritedByMe
          ? await Api.delete(`/restaurants/${storeId}/favorite`)
          : await Api.post(`/restaurants/${storeId}/favorite`);
        const favorited = Boolean(response.data?.favoriteByCurrentUser);
        store.favoritedByMe = favorited;
        favoriteBtn.classList.toggle("is-favorited", favorited);
        favoriteBtn.setAttribute("aria-pressed", String(favorited));
      } catch (error) {
        window.alert(error.message || "찜 처리 중 오류가 발생했습니다.");
      } finally {
        favoriteBtn.disabled = false;
      }
    });

    ownedBadge.hidden = false;

    // 영업시간은 가게마다 저장 형식이 달라(구글 표기 등) 한 줄로 길게 이어지는 경우가 있다.
    // 요일 단위로 끊어 "요일 : 영업시간" 한 줄씩 보여주고, 못 끊으면 원문을 그대로 쓴다.
    const parsedHours = store.openingHours
      ? window.FooduckHours?.parse(store.openingHours)
      : null;
    const openingHoursHtml = parsedHours
      ? `<ul class="store-hours-list">${parsedHours.map((entry) => `
          <li><span>${escapeHtml(entry.label)}</span><span>${escapeHtml(entry.value)}</span></li>`).join("")}</ul>`
      : escapeHtml(
        (store.openingHours && window.FooduckHours?.normalize(store.openingHours))
        || store.openingHours
        || "-",
      );
    panels.info.innerHTML = `
      <div class="store-section-card">
        <h2>가게 정보</h2>
        <dl class="store-basic-info">
          <div><dt>카테고리</dt><dd>${escapeHtml(store.categoryName || "-")}</dd></div>
          <div><dt>주소</dt><dd>${escapeHtml(addressEl.textContent || "-")}</dd></div>
          <div><dt>전화번호</dt><dd>${escapeHtml(store.phone || "-")}</dd></div>
          <div><dt>영업시간</dt><dd>${openingHoursHtml}</dd></div>
          <div><dt>휴무일</dt><dd>${escapeHtml(store.closedDays || "-")}</dd></div>
        </dl>
      </div>
    `;

    basicInfo.innerHTML = `
      <div class="store-owner-profile">
        <img class="store-owner-avatar" src="${escapeHtml(store.ownerProfileImageUrl || "/images/characters/waving.png")}" alt="">
        <div>
          <strong data-store-owner-author></strong>
          <span>${escapeHtml(store.phone || "전화번호 미등록")}</span>
        </div>
      </div>
    `;
    bindStoreOwnerAuthor(store);


    renderTabs(["menu", "news", "review", "info"]);
    activateTab(tabOrder.includes(requestedTab) ? requestedTab : "menu");
  }

  function renderPublicDetail(store) {
    storeId = store.id;
    isOwner = false;

    nameEl.textContent = store.name + (store.branchName ? ` ${store.branchName}` : "");
    const address = store.roadAddress || store.lotAddress || "-";
    addressEl.textContent = address;

    const categoryName = store.categorySmallName || store.categoryLargeName;
    badgesEl.innerHTML = categoryName
      ? `<span class="store-badge store-badge--category">${escapeHtml(categoryName)}</span>`
      : "";

    statsRow.hidden = true;

    favoriteBtn.hidden = !window.FooduckSession || !window.FooduckSession.authenticated;
    favoriteBtn.classList.toggle("is-favorited", store.favoritedByMe);
    favoriteBtn.setAttribute("aria-pressed", String(store.favoritedByMe));
    favoriteBtn.addEventListener("click", async () => {
      favoriteBtn.disabled = true;
      try {
        const response = store.favoritedByMe
          ? await Api.delete(`/map/restaurants/${storeId}/favorite`)
          : await Api.post(`/map/restaurants/${storeId}/favorite`);
        const favorited = Boolean(response.data?.favoriteByCurrentUser);
        store.favoritedByMe = favorited;
        favoriteBtn.classList.toggle("is-favorited", favorited);
        favoriteBtn.setAttribute("aria-pressed", String(favorited));
      } catch (error) {
        window.alert(error.message || "찜 처리 중 오류가 발생했습니다.");
      } finally {
        favoriteBtn.disabled = false;
      }
    });

    panels.info.innerHTML = `
      <div class="store-section-card">
        <h2>가게 정보</h2>
        <dl class="store-basic-info">
          <div><dt>업종(대분류)</dt><dd>${escapeHtml(store.categoryLargeName || "-")}</dd></div>
          <div><dt>업종(소분류)</dt><dd>${escapeHtml(store.categorySmallName || "-")}</dd></div>
          <div><dt>도로명 주소</dt><dd>${escapeHtml(store.roadAddress || "-")}</dd></div>
          <div><dt>지번 주소</dt><dd>${escapeHtml(store.lotAddress || "-")}</dd></div>
        </dl>
      </div>
    `;

    basicInfo.innerHTML = '<div class="store-empty">사업자가 푸드덕에 가게를 직접 등록하면 사업자 정보가 표시됩니다.</div>';
    sourceNote.textContent = `가게 기본정보 출처: 공공데이터 · ${formatDataYm(store.dataYm)} 기준`;

    renderTabs(["menu", "news", "review", "info"]);
    activateTab(tabOrder.includes(requestedTab) ? requestedTab : "menu");
  }

  function hasNewsDraft() {
    if (!canWriteNews()) return false;
    if (newsCreatedPostId != null) return newsSelectedMedia.length > 0;
    const title = document.getElementById("store-news-title")?.value.trim() || "";
    const body = document.getElementById("store-news-content")?.value.trim() || "";
    return Boolean(title || body || newsSelectedMedia.length > 0);
  }

  function discardNewsDraft() {
    const titleInput = document.getElementById("store-news-title");
    const contentInput = document.getElementById("store-news-content");
    if (titleInput) titleInput.value = "";
    if (contentInput) {
      contentInput.value = "";
      emojis?.refreshEditor?.(contentInput);
      resizeStoreWriteTextarea(contentInput, 90);
    }
    newsCreatedPostId = null;
    resetNewsSelectedMedia();
  }

  function confirmNewsDraftDiscard() {
    if (newsMediaBusy) {
      window.alert("가게 소식이나 첨부파일을 저장하는 중입니다. 저장이 끝난 뒤 이동해 주세요.");
      return false;
    }
    const newsDraft = hasNewsDraft();
    const commentDraft = hasNewsInlineCommentDraft();
    if (!newsDraft && !commentDraft) return true;

    let message = "작성 중인 내용을 버리고 이동하시겠습니까?";
    if (newsDraft && commentDraft) {
      message = "작성 중인 가게 소식과 댓글 또는 답글이 있습니다. 작성 내용을 버리고 이동하시겠습니까?";
    } else if (commentDraft) {
      message = "작성 중인 댓글이나 답글이 있습니다. 작성 내용을 버리고 이동하시겠습니까?";
    } else if (newsCreatedPostId != null) {
      message = "소식은 등록되었지만 아직 올리지 못한 첨부파일이 있습니다. 남은 첨부파일을 포기하고 이동하시겠습니까?";
    } else {
      message = "작성 중인 가게 소식이 있습니다. 작성 내용을 버리고 이동하시겠습니까?";
    }

    const confirmed = window.confirm(message);
    if (confirmed && newsDraft) discardNewsDraft();
    return confirmed;
  }

  window.addEventListener("beforeunload", (event) => {
    if (!hasNewsDraft() && !hasNewsInlineCommentDraft()) return;
    event.preventDefault();
    event.returnValue = "";
  });

  async function init() {
    try {
      const nicknameTask = hydrateSessionNickname();
      if (source === "owned") {
        const response = await Api.get(`/public/restaurants/${id}`);
        await nicknameTask;
        loading.hidden = true;
        content.hidden = false;
        renderOwnedDetail(response.data);
        window.FooduckBoard?.consumeFeedbackFlash?.();
      } else {
        const response = await Api.get(`/public/map/restaurants/${id}`);
        await nicknameTask;
        loading.hidden = true;
        content.hidden = false;
        renderPublicDetail(response.data);
        window.FooduckBoard?.consumeFeedbackFlash?.();
      }
    } catch (error) {
      showError(error.message || "가게 정보를 불러오지 못했습니다.");
    }
  }

  init();
})();
