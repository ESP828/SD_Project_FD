(() => {
  const loading = document.getElementById("store-loading");
  const errorView = document.getElementById("store-error");
  const errorMessage = document.getElementById("store-error-message");
  const content = document.getElementById("store-content");
  if (!loading || !errorView || !content) return;

  const params = new URLSearchParams(window.location.search);
  const source = params.get("source") === "public" ? "public" : "owned";
  const id = params.get("id");
  const requestedTab = params.get("tab");

  if (!id) {
    showError("가게 정보를 찾을 수 없습니다.");
    return;
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
  let newsPage = 0;
  const NEWS_PAGE_SIZE = 10;
  const session = window.FooduckSession || {};
  const isLoggedIn = Boolean(session.authenticated);
  const isAdmin = Boolean(session.isAdmin);

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
    activeTab = key;
    tabsEl.querySelectorAll("[data-tab]").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.tab === key);
    });
    Object.entries(panels).forEach(([panelKey, panel]) => {
      panel.hidden = panelKey !== key;
    });
    if (!loadedTabs.has(key)) {
      loadedTabs.add(key);
      loadTabContent(key);
    }
  }

  function loadTabContent(key) {
    if (key === "menu") loadMenu();
    if (key === "review") loadReviews();
    if (key === "news") loadNews();
  }

  async function loadMenu() {
    if (source === "public") {
      panels.menu.innerHTML = `
        <div class="store-section-card">
          <div class="store-empty">공공데이터 출처 가게는 메뉴 정보를 제공하지 않습니다.<br>사업자가 푸드덕에 가게를 직접 등록하면 메뉴를 볼 수 있어요.</div>
        </div>
      `;
      return;
    }
    panels.menu.innerHTML = '<div class="store-empty">메뉴를 불러오는 중입니다.</div>';
    try {
      const response = await Api.get(`/public/restaurants/${storeId}/menu`, { auth: false });
      const items = response.data || [];
      renderMenuPanel(items);
    } catch (error) {
      panels.menu.innerHTML = `<div class="store-empty">${error.message || "메뉴를 불러오지 못했습니다."}</div>`;
    }
  }

  function renderMenuPanel(items) {
    if (items.length === 0) {
      panels.menu.innerHTML = '<div class="store-section-card"><div class="store-empty">등록된 메뉴가 없습니다.</div></div>';
      return;
    }
    panels.menu.innerHTML = `
      <div class="store-section-card">
        <h2>전체 메뉴</h2>
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

  function reviewWriteFormHtml() {
    if (!isLoggedIn) {
      return '<div class="store-write-signin">리뷰를 작성하려면 로그인해 주세요.</div>';
    }
    return `
      <div class="store-write-form" id="store-review-form">
        <h3>리뷰 작성</h3>
        <div class="store-rating-input" id="store-review-rating" role="radiogroup" aria-label="별점">
          ${[1, 2, 3, 4, 5].map((n) => `<button type="button" data-rating="${n}" aria-label="${n}점">★</button>`).join("")}
        </div>
        <textarea id="store-review-content" maxlength="1000" placeholder="솔직한 리뷰를 남겨주세요 (선택)"></textarea>
        <button type="button" class="button button-primary button-sm" id="store-review-submit">리뷰 등록</button>
      </div>
    `;
  }

  function bindReviewForm() {
    const form = document.getElementById("store-review-form");
    if (!form) return;
    let selectedRating = 0;
    const ratingButtons = form.querySelectorAll("[data-rating]");
    ratingButtons.forEach((button) => {
      button.addEventListener("click", () => {
        selectedRating = Number(button.dataset.rating);
        ratingButtons.forEach((b) => b.classList.toggle("is-selected", Number(b.dataset.rating) <= selectedRating));
      });
    });
    const submitButton = document.getElementById("store-review-submit");
    submitButton.addEventListener("click", async () => {
      if (!selectedRating) {
        window.alert("별점을 선택해 주세요.");
        return;
      }
      const content = document.getElementById("store-review-content").value.trim();
      submitButton.disabled = true;
      try {
        const writePath = source === "public"
          ? `/map/restaurants/${storeId}/reviews`
          : `/restaurants/${storeId}/reviews`;
        await Api.post(writePath, { rating: selectedRating, content: content || null });
        await loadReviews();
      } catch (error) {
        window.alert(error.message || "리뷰 등록에 실패했습니다.");
      } finally {
        submitButton.disabled = false;
      }
    });
  }

  async function loadReviews() {
    panels.review.innerHTML = '<div class="store-empty">리뷰를 불러오는 중입니다.</div>';
    try {
      const readPath = source === "public"
        ? `/public/map/restaurants/${storeId}/reviews`
        : `/public/restaurants/${storeId}/reviews`;
      const response = await Api.get(readPath, { auth: false });
      const items = response.data || [];
      const listHtml = items.length === 0
        ? '<div class="store-empty">아직 작성된 리뷰가 없습니다.</div>'
        : `<div class="store-review-list">${items.map((review) => `
            <div class="store-review-item">
              <div class="store-review-head">
                <span class="store-review-author">${escapeHtml(review.authorNickname)}</span>
                <span class="store-review-rating">★ ${review.rating}.0</span>
              </div>
              ${review.content ? `<p class="store-review-content">${escapeHtml(review.content)}</p>` : ""}
              <p class="store-review-date">${formatDate(review.createdAt)}</p>
            </div>
          `).join("")}</div>`;
      panels.review.innerHTML = `
        <div class="store-section-card">
          <h2>리뷰 ${items.length}건</h2>
          ${reviewWriteFormHtml()}
          ${listHtml}
        </div>
      `;
      bindReviewForm();
    } catch (error) {
      panels.review.innerHTML = `<div class="store-empty">${error.message || "리뷰를 불러오지 못했습니다."}</div>`;
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
          <p class="store-news-content">${content}</p>
        </div>
      `;
    }
    const href = escapeHtml(newsBoardPath("detail", news.postId));
    const label = escapeHtml(`${news.title || "소식"} 상세 보기`);
    return `
      <a class="store-news-summary-link" href="${href}" aria-label="${label}">
        <p class="store-news-title">${title}</p>
        <p class="store-news-content">${content}</p>
      </a>
    `;
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
      <a class="store-news-media-photo" href="${safeUrl}" target="_blank" rel="noopener" aria-label="${name} 원본 보기">
        <img src="${safeUrl}" alt="${name}" loading="lazy">
      </a>
    `;
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
          <video src="${safeUrl}" controls preload="auto" aria-label="${name}"></video>
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

  function newsMediaHtml(mediaItems) {
    if (!Array.isArray(mediaItems) || mediaItems.length === 0) return "";

    const readyImages = mediaItems.filter((media) =>
      newsMediaStatus(media) === "READY" &&
      String(media?.mediaType || "").toUpperCase() === "IMAGE" &&
      Boolean(newsMediaUrl(media))
    );
    const secondaryItems = mediaItems.filter((media) => !readyImages.includes(media));

    let imageGrid = "";
    if (readyImages.length > 0) {
      const countClass = readyImages.length === 1
        ? "is-count-1"
        : readyImages.length === 2
          ? "is-count-2"
          : readyImages.length === 3
            ? "is-count-3"
            : readyImages.length === 4
              ? "is-count-4"
              : "is-count-many";
      imageGrid = `
        <div class="store-news-media-images ${countClass}" aria-label="첨부 이미지 ${readyImages.length}장">
          ${readyImages.map(newsReadyImageHtml).join("")}
        </div>
      `;
    }

    const secondaryHtml = secondaryItems.map((media) => {
      const status = newsMediaStatus(media);
      if (status !== "READY") return newsMediaProcessingHtml(media, status);
      return newsReadyVideoHtml(media);
    }).join("");

    return `${imageGrid}${secondaryHtml ? `<div class="store-news-media-secondary">${secondaryHtml}</div>` : ""}`;
  }

  function bindNewsMediaRuntime(host) {
    window.FooduckIcons?.enhance(host);
    host.querySelectorAll(".store-news-media-video").forEach((frame) => {
      const video = frame.querySelector("video");
      const loading = frame.querySelector(".detail-media-loading");
      if (!video || !loading) return;

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
      const html = newsMediaHtml(mediaItems);
      host.innerHTML = html;
      host.hidden = !html;
      if (html) bindNewsMediaRuntime(host);

      const processing = mediaItems.some((media) => {
        const status = newsMediaStatus(media);
        return status === "QUEUED" || status === "PROCESSING";
      });
      if (processing && attempt < 120) {
        window.setTimeout(() => loadNewsMedia(postId, generation, attempt + 1), 2500);
      }
    } catch (_error) {
      host.hidden = true;
    }
  }

  function bindNewsMedia(items) {
    const generation = newsMediaGeneration;
    items.forEach((news) => {
      if (news?.postId != null) {
        void loadNewsMedia(news.postId, generation);
      }
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
           href="${escapeHtml(newsBoardPath("write", news.postId))}">수정·첨부</a>
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
        <input type="text" id="store-news-title" maxlength="200" placeholder="제목">
        <textarea id="store-news-content" maxlength="10000" placeholder="소식 내용을 입력하세요"></textarea>
        <button type="button" class="button button-primary button-sm" id="store-news-submit">소식 등록</button>
      </div>
    `;
  }

  function bindNewsForm() {
    const toggleButton = document.getElementById("store-news-write-toggle");
    const form = document.getElementById("store-news-form");
    if (toggleButton && form) {
      toggleButton.addEventListener("click", () => {
        const willOpen = form.hidden;
        form.hidden = !willOpen;
        toggleButton.setAttribute("aria-expanded", String(willOpen));
        toggleButton.textContent = willOpen ? "닫기" : "글쓰기";
        if (willOpen) {
          document.getElementById("store-news-title")?.focus();
        }
      });
    }

    const submitButton = document.getElementById("store-news-submit");
    if (!submitButton) return;
    submitButton.addEventListener("click", async () => {
      const title = document.getElementById("store-news-title").value.trim();
      const content = document.getElementById("store-news-content").value.trim();
      if (!title || !content) {
        window.alert("제목과 내용을 입력해 주세요.");
        return;
      }
      submitButton.disabled = true;
      try {
        await Api.post(newsApiPath(), { title, content });
        await loadNews(0);
      } catch (error) {
        window.alert(error.message || "소식 등록에 실패했습니다.");
      } finally {
        submitButton.disabled = false;
      }
    });
  }

  function newsPaginationHtml(pageData) {
    const totalPages = Math.max(0, Number(pageData.totalPages) || 0);
    if (totalPages <= 1) return "";

    const currentPage = Math.max(0, Number(pageData.page) || 0);
    const first = pageData.first === true || currentPage === 0;
    const last = pageData.last === true || currentPage + 1 >= totalPages;
    return `
      <nav class="store-news-pagination" aria-label="소식 페이지">
        <button type="button" class="button button-secondary button-sm"
                data-news-page="${currentPage - 1}"${first ? " disabled" : ""}>이전</button>
        <span>${currentPage + 1} / ${totalPages}</span>
        <button type="button" class="button button-secondary button-sm"
                data-news-page="${currentPage + 1}"${last ? " disabled" : ""}>다음</button>
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
        if (Number.isInteger(targetPage) && targetPage >= 0) {
          loadNews(targetPage);
        }
      });
    });
    panels.news.querySelectorAll("[data-news-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        if (!canWriteNews()) return;
        const postId = button.dataset.newsDelete;
        if (!postId || !window.confirm("이 소식을 삭제하시겠습니까?")) return;

        const itemCount = Number(button.dataset.newsItemCount);
        button.disabled = true;
        try {
          await Api.delete(newsDeleteApiPath(postId));
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
    document.getElementById("store-news-retry")?.addEventListener("click", () => {
      loadNews(newsPage);
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
                <span class="store-news-date">${formatDate(news.createdAt)}</span>
              </div>
              ${newsLikeButtonHtml(news)}
            </div>
          </article>
        `).join("")}</div>`;
    renderNewsCard(bodyHtml, newsPaginationHtml(pageData));
    bindNewsAuthors(items);
    bindNewsMedia(items);
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

  async function loadNews(page = newsPage) {
    const requestedPage = Number.isInteger(page) && page >= 0 ? page : 0;
    newsMediaGeneration += 1;
    newsPage = requestedPage;
    panels.news.innerHTML = '<div class="store-empty">소식을 불러오는 중입니다.</div>';
    const params = new URLSearchParams({
      page: String(requestedPage),
      size: String(NEWS_PAGE_SIZE),
    });
    try {
      const response = await Api.get(`${newsApiPath()}?${params.toString()}`);
      const pageData = response.data || {};
      newsPage = Number.isInteger(pageData.page) && pageData.page >= 0
        ? pageData.page
        : requestedPage;
      renderNewsPanel({ ...pageData, page: newsPage });
    } catch (error) {
      renderNewsError(error);
    }
  }

  function renderStat(label, value) {
    const wrapper = document.createElement("div");
    wrapper.innerHTML = `<span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong>`;
    return wrapper.outerHTML;
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

    panels.info.innerHTML = `
      <div class="store-section-card">
        <h2>가게 정보</h2>
        <dl class="store-basic-info">
          <div><dt>카테고리</dt><dd>${escapeHtml(store.categoryName || "-")}</dd></div>
          <div><dt>주소</dt><dd>${escapeHtml(addressEl.textContent || "-")}</dd></div>
          <div><dt>전화번호</dt><dd>${escapeHtml(store.phone || "-")}</dd></div>
          <div><dt>영업시간</dt><dd>${escapeHtml(store.openingHours || "-")}</dd></div>
          <div><dt>휴무일</dt><dd>${escapeHtml(store.closedDays || "-")}</dd></div>
        </dl>
      </div>
    `;

    basicInfo.innerHTML = `
      <div class="store-owner-profile">
        <img class="store-owner-avatar" src="${escapeHtml(store.ownerProfileImageUrl || "/images/characters/waving.png")}" alt="">
        <div>
          <strong>${escapeHtml(store.ownerNickname || "사장님")}</strong>
          <span>${escapeHtml(store.phone || "전화번호 미등록")}</span>
        </div>
      </div>
    `;
    sourceNote.textContent = "가게 기본정보 출처: 사업자 직접 등록";

    renderTabs(["menu", "news", "review", "info"]);
    activateTab(tabOrder.includes(requestedTab) ? requestedTab : "menu");
  }

  function renderPublicDetail(store) {
    storeId = store.id;
    isOwner = false;

    nameEl.textContent = store.name + (store.branchName ? ` ${store.branchName}` : "");
    const address = store.roadAddress || store.lotAddress || "-";
    addressEl.textContent = address;

    const categoryName = store.categorySmallName || store.categoryMediumName || store.categoryLargeName;
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
          <div><dt>업종(중분류)</dt><dd>${escapeHtml(store.categoryMediumName || "-")}</dd></div>
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

  function initializeScrollTopButton() {
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

  async function init() {
    try {
      if (source === "owned") {
        const response = await Api.get(`/public/restaurants/${id}`);
        loading.hidden = true;
        content.hidden = false;
        renderOwnedDetail(response.data);
      } else {
        const response = await Api.get(`/public/map/restaurants/${id}`);
        loading.hidden = true;
        content.hidden = false;
        renderPublicDetail(response.data);
      }
    } catch (error) {
      showError(error.message || "가게 정보를 불러오지 못했습니다.");
    }
  }

  initializeScrollTopButton();
  init();
})();
