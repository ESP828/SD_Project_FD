(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const postId = board?.readPostId();
  const sourceView = new URLSearchParams(window.location.search).get("from");
  const fromBest = sourceView === "BEST";
  const fromPopular = sourceView === "POPULAR";
  const state = { post: null };
  const MEDIA_POLL_BASE_DELAY = 2500;
  const MEDIA_POLL_MAX_DELAY = 15000;
  const MEDIA_POLL_MAX_FAILURES = 5;
  const COMMENT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
  const COMMENT_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
  ]);
  const COMMENT_IMAGE_NAME_PATTERN = /\.(?:jpe?g|png|gif|webp)$/i;
  let mediaPollTimer = null;
  let mediaPollInFlight = false;
  let mediaPollDelay = MEDIA_POLL_BASE_DELAY;
  let mediaPollFailures = 0;
  let mediaPollGeneration = 0;
  let mediaPollingHalted = false;
  let mediaPollingDisposed = false;

  const detailContent = document.getElementById("post-detail-content");
  const listLink = document.getElementById("detail-list-link");
  const relatedPostList = document.getElementById("related-post-list");
  const unansweredPostList = document.getElementById("detail-unanswered-post-list");
  const restaurantSide = document.getElementById("detail-restaurant-side");
  const commentCount = document.getElementById("detail-comment-count");
  const commentForm = document.getElementById("comment-form");
  const commentContent = document.getElementById("comment-content");
  const commentLoginNote = document.getElementById("comment-login-note");
  const commentList = document.getElementById("comment-list");
  const commentImageInput = document.getElementById("comment-image-input");
  const commentImageSelect = document.getElementById("comment-image-select");
  const commentImagePreview = document.getElementById("comment-image-preview");
  const commentImagePreviewImage =
    document.getElementById("comment-image-preview-image");
  const commentImagePreviewName =
    document.getElementById("comment-image-preview-name");
  const commentImagePreviewSize =
    document.getElementById("comment-image-preview-size");
  const commentImageRemove = document.getElementById("comment-image-remove");
  const commentSubmitButton = commentForm?.querySelector('button[type="submit"]');
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
    updateCachedPostViewCount,
    writeBoardCache,
  } = board;

  function isEdited(item) {
    return item?.edited === true;
  }

  function isNewsPost(post = state.post) {
    return post?.category === "NEWS";
  }

  function newsSource(post) {
    const hasPublicRestaurant = post?.publicRestaurantId != null;
    const hasOwnedRestaurant = post?.restaurantId != null;
    if (hasPublicRestaurant === hasOwnedRestaurant) return null;
    return hasPublicRestaurant
      ? { source: "public", id: post.publicRestaurantId }
      : { source: "owned", id: post.restaurantId };
  }

  function restaurantNewsPath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const params = new URLSearchParams({
      source: target.source,
      id: String(target.id),
      tab: "news",
    });
    return `/pages/restaurant/detail.html?${params.toString()}`;
  }

  function restaurantInfoPath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const params = new URLSearchParams({
      source: target.source,
      id: String(target.id),
      tab: "info",
    });
    return `/pages/restaurant/detail.html?${params.toString()}`;
  }

  function newsRestaurantApiPath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const restaurantId = encodeURIComponent(target.id);
    return target.source === "public"
      ? `/public/map/restaurants/${restaurantId}`
      : `/public/restaurants/${restaurantId}`;
  }

  function newsRestaurantFallback(post) {
    const target = newsSource(post);
    if (!target) return null;
    if (target.source === "owned" && post?.restaurant) {
      return {
        name: post.restaurant.name || "등록 식당",
        category: null,
        address: post.restaurant.address || "주소 정보 없음",
        phone: null,
        openingHours: null,
        sourceLabel: "푸드덕 등록 식당",
      };
    }
    return {
      name: "연결된 가게",
      category: null,
      address: "가게 정보를 불러오지 못했습니다.",
      phone: null,
      openingHours: null,
      sourceLabel: "식당",
    };
  }

  function normalizeNewsRestaurant(post, restaurant) {
    const target = newsSource(post);
    if (!target || !restaurant) return newsRestaurantFallback(post);

    if (target.source === "public") {
      return {
        name: restaurant.name || "연결된 가게",
        category:
          restaurant.categorySmallName ||
          restaurant.categoryMediumName ||
          restaurant.categoryLargeName ||
          null,
        address: restaurant.roadAddress || restaurant.lotAddress || "주소 정보 없음",
        phone: null,
        openingHours: null,
        sourceLabel: "식당",
      };
    }

    const fullAddress = [restaurant.address, restaurant.addressDetail]
      .filter(Boolean)
      .join(" ");
    return {
      name: restaurant.name || post?.restaurant?.name || "연결된 가게",
      category: restaurant.categoryName || null,
      address: fullAddress || post?.restaurant?.address || "주소 정보 없음",
      phone: restaurant.phone || null,
      openingHours: restaurant.openingHours || null,
      sourceLabel: "푸드덕 등록 식당",
    };
  }

  function renderNewsRestaurantCardContent(card, post, restaurant, options = {}) {
    const infoPath = restaurantInfoPath(post);
    const source = newsSource(post);
    const data = restaurant || newsRestaurantFallback(post);
    if (!card || !data || !source) return;

    card.classList.toggle("is-loading", options.loading === true);
    card.classList.toggle("has-error", options.error === true);
    card.replaceChildren();

    const copy = element("div", "news-restaurant-card__copy");
    const eyebrow = element("div", "news-restaurant-card__eyebrow");
    const icon = element("span", "material-symbols-rounded", "storefront");
    icon.setAttribute("aria-hidden", "true");
    eyebrow.append(icon, document.createTextNode(" 이 소식의 가게"));

    const headingRow = element("div", "news-restaurant-card__heading");
    headingRow.append(
      element("strong", "news-restaurant-card__name", data.name),
      element("span", "news-restaurant-card__source", data.sourceLabel),
    );
    copy.append(eyebrow, headingRow);

    const details = element("div", "news-restaurant-card__details");
    if (data.category) {
      const category = element("span", "news-restaurant-card__detail");
      const categoryIcon = element("span", "material-symbols-rounded", "storefront");
      categoryIcon.setAttribute("aria-hidden", "true");
      category.append(categoryIcon, document.createTextNode(data.category));
      details.append(category);
    }
    const address = element("span", "news-restaurant-card__detail");
    const addressIcon = element("span", "material-symbols-rounded", "location_on");
    addressIcon.setAttribute("aria-hidden", "true");
    address.append(addressIcon, document.createTextNode(data.address));
    details.append(address);
    if (data.phone) {
      const phone = element("span", "news-restaurant-card__detail");
      const phoneIcon = element("span", "material-symbols-rounded", "call");
      phoneIcon.setAttribute("aria-hidden", "true");
      phone.append(phoneIcon, document.createTextNode(data.phone));
      details.append(phone);
    }
    if (data.openingHours) {
      const hours = element("span", "news-restaurant-card__detail");
      const hoursIcon = element("span", "material-symbols-rounded", "schedule");
      hoursIcon.setAttribute("aria-hidden", "true");
      hours.append(hoursIcon, document.createTextNode(data.openingHours));
      details.append(hours);
    }
    copy.append(details);

    const action = element("a", "button button-sm button-secondary news-restaurant-card__action", "가게 정보 보기");
    action.href = infoPath || restaurantNewsPath(post) || "#";
    const arrow = element("span", "material-symbols-rounded", "arrow_forward");
    arrow.setAttribute("aria-hidden", "true");
    action.append(document.createTextNode(" "), arrow);

    card.append(copy, action);
    window.FooduckIcons?.enhance(card);
  }

  function renderNewsRestaurantCard(post) {
    const target = newsSource(post);
    if (!target) return null;

    const card = element("section", "news-restaurant-card is-loading");
    card.setAttribute("aria-label", "이 소식의 가게 정보");
    renderNewsRestaurantCardContent(
      card,
      post,
      target.source === "owned" ? newsRestaurantFallback(post) : {
        name: "가게 정보를 불러오는 중입니다.",
        category: null,
        address: "잠시만 기다려 주세요.",
        phone: null,
        openingHours: null,
        sourceLabel: target.source === "public" ? "식당" : "푸드덕 등록 식당",
      },
      { loading: true },
    );

    const path = newsRestaurantApiPath(post);
    if (!path) return card;

    Api.get(path, { auth: false })
      .then((payload) => {
        if (!card.isConnected || state.post?.postId !== post.postId) return;
        renderNewsRestaurantCardContent(
          card,
          post,
          normalizeNewsRestaurant(post, payload?.data),
        );
      })
      .catch(() => {
        if (!card.isConnected || state.post?.postId !== post.postId) return;
        renderNewsRestaurantCardContent(
          card,
          post,
          newsRestaurantFallback(post),
          { error: true },
        );
      });

    return card;
  }

  function newsWritePath(post) {
    const params = new URLSearchParams({
      postId: String(post.postId),
      from: "NEWS",
    });
    return `/pages/board/write.html?${params.toString()}`;
  }

  function newsDeletePath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const restaurantId = encodeURIComponent(target.id);
    const targetPostId = encodeURIComponent(post.postId);
    return target.source === "public"
      ? `/board/posts/restaurants/public/${restaurantId}/news/${targetPostId}`
      : `/board/posts/restaurants/${restaurantId}/news/${targetPostId}`;
  }

  function setBackLink(href, label) {
    listLink.href = href;
    const icon = element("span", "material-symbols-rounded", "arrow_back");
    icon.setAttribute("aria-hidden", "true");
    listLink.replaceChildren(icon, document.createTextNode(` ${label}`));
  }

  let selectedCommentImage = null;
  let commentImagePreviewUrl = null;
  let activeReplyForm = null;
  let activeReplyPreviewUrl = null;

  function clearCommentImageSelection() {
    selectedCommentImage = null;
    if (commentImageInput) commentImageInput.value = "";
    if (commentImagePreviewUrl) {
      URL.revokeObjectURL(commentImagePreviewUrl);
      commentImagePreviewUrl = null;
    }
    if (commentImagePreviewImage) commentImagePreviewImage.removeAttribute("src");
    if (commentImagePreviewName) commentImagePreviewName.textContent = "";
    if (commentImagePreviewSize) commentImagePreviewSize.textContent = "";
    if (commentImagePreview) commentImagePreview.hidden = true;
  }

  function selectCommentImage(file) {
    if (!file) {
      clearCommentImageSelection();
      return;
    }
    if (file.size < 1) {
      showToast(toast, "비어 있는 사진은 첨부할 수 없습니다.", true);
      clearCommentImageSelection();
      return;
    }
    if (file.size > COMMENT_IMAGE_MAX_BYTES) {
      showToast(toast, "댓글 사진은 5MB 이하만 첨부할 수 있습니다.", true);
      clearCommentImageSelection();
      return;
    }
    if (!COMMENT_IMAGE_NAME_PATTERN.test(file.name || "") ||
        (file.type && !COMMENT_IMAGE_TYPES.has(file.type))) {
      showToast(
        toast,
        "댓글에는 JPG, PNG, WEBP, GIF 사진만 첨부할 수 있습니다.",
        true,
      );
      clearCommentImageSelection();
      return;
    }

    clearCommentImageSelection();
    selectedCommentImage = file;
    commentImagePreviewUrl = URL.createObjectURL(file);
    if (commentImagePreviewImage) {
      commentImagePreviewImage.src = commentImagePreviewUrl;
      commentImagePreviewImage.alt = `${file.name || "댓글 첨부 사진"} 미리보기`;
    }
    if (commentImagePreviewName) {
      commentImagePreviewName.textContent = file.name || "첨부 사진";
    }
    if (commentImagePreviewSize) {
      commentImagePreviewSize.textContent = formatBytes(file.size);
    }
    if (commentImagePreview) commentImagePreview.hidden = false;
  }

  async function uploadCommentImage(commentId, file) {
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
      const message =
        typeof payload === "object" && payload
          ? payload.message
          : `댓글 사진 업로드에 실패했습니다. (${response.status})`;
      throw new Error(message || "댓글 사진 업로드에 실패했습니다.");
    }
    return payload;
  }

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
    const visiblePosts = (posts || [])
      .filter((post) => String(post.postId) !== String(postId))
      .slice(0, 3);
    if (!visiblePosts.length) {
      unansweredPostList.append(
        element("li", "best-loading", "모든 질문에 답변이 달렸습니다."),
      );
      return;
    }
    visiblePosts.forEach((post) => {
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
    if (!unansweredPostList || !state.post) return;
    const params = new URLSearchParams({
      boardType: state.post.boardType === "BUSINESS" ? "BUSINESS" : "GENERAL",
      size: "4",
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

  function formatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (value < 1024) return `${value}B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)}KB`;
    return `${(value / 1024 / 1024).toFixed(1)}MB`;
  }

  function mediaDownloadHref(media) {
    const url = String(media.mediaUrl || "");
    if (!url.startsWith("/api/board/posts/media/")) return url;
    const separator = url.includes("?") ? "&" : "?";
    return `${url}${separator}download=true`;
  }

  function openImageViewer(sourceImage, name) {
    if (!sourceImage?.src) return;

    if (document.querySelector(".detail-image-viewer")) return;

    const viewer = element("div", "detail-image-viewer");
    viewer.setAttribute("role", "dialog");
    viewer.setAttribute("aria-modal", "true");
    viewer.setAttribute("aria-label", `${name || "첨부 사진"} 크게 보기`);

    const closeButton = element(
      "button",
      "detail-image-viewer__close",
    );
    closeButton.type = "button";
    closeButton.setAttribute("aria-label", "사진 크게 보기 닫기");
    closeButton.append(
      element("span", "material-symbols-rounded", "close"),
    );

    const expandedImage = new Image();
    expandedImage.className = "detail-image-viewer__image";
    expandedImage.src = sourceImage.currentSrc || sourceImage.src;
    expandedImage.alt = sourceImage.alt || name || "첨부 사진";

    const previouslyFocused = document.activeElement;
    const closeViewer = () => {
      document.removeEventListener("keydown", handleViewerKeydown);
      document.body.classList.remove("is-image-viewer-open");
      viewer.remove();
      if (previouslyFocused instanceof HTMLElement &&
          document.contains(previouslyFocused)) {
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

  function renderMediaFallback(
    item,
    media,
    message,
    actionLabel = "파일 다운로드",
  ) {
    item.replaceChildren();
    const fallback = element("div", "detail-media-fallback");
    const icon = element(
      "span",
      "material-symbols-rounded",
      media.mediaType === "IMAGE" ? "image" : "movie",
    );
    icon.setAttribute("aria-hidden", "true");
    fallback.append(
      icon,
      element("span", "", message || "브라우저에서 바로 표시할 수 없습니다."),
    );
    const download = element(
      "a",
      "button button-sm button-secondary",
      actionLabel,
    );
    download.href = mediaDownloadHref(media);
    fallback.append(download);
    item.append(fallback);
  }

  function mediaProcessingStatus(media) {
    const storedUrl = String(media?.mediaUrl || "");
    if (!media?.processingStatus && storedUrl === "db:processing") {
      return "PROCESSING";
    }
    if (!media?.processingStatus && storedUrl === "db:failed") {
      return "FAILED";
    }
    const status = String(media?.processingStatus || "READY").toUpperCase();
    return ["QUEUED", "PROCESSING", "FAILED"].includes(status)
      ? status
      : "READY";
  }

  function mediaProcessingProgress(media) {
    const progress = Number(media?.processingProgress);
    if (!Number.isFinite(progress)) return 0;
    return Math.max(0, Math.min(100, Math.round(progress)));
  }

  function isProcessingMedia(media) {
    const status = mediaProcessingStatus(media);
    return status === "QUEUED" || status === "PROCESSING";
  }

  function isVideoMedia(media) {
    return Boolean(media && media.mediaType !== "IMAGE");
  }

  function renderMediaProcessing(item, media) {
    const status = mediaProcessingStatus(media);
    const progressValue = mediaProcessingProgress(media);
    const failed = status === "FAILED";
    item.classList.toggle("is-processing", !failed);
    item.classList.toggle("is-failed", failed);

    const panel = element("div", "detail-media-processing");
    panel.setAttribute("role", "group");
    panel.setAttribute("aria-label", "동영상 서버 처리 상태");
    panel.setAttribute("aria-live", "off");

    const icon = element(
      "span",
      "material-symbols-rounded detail-media-processing__icon",
      failed ? "close" : "progress_activity",
    );
    icon.setAttribute("aria-hidden", "true");

    const copy = element("div", "detail-media-processing__copy");
    const title = element(
      "strong",
      "detail-media-processing__title",
      failed
        ? "동영상 처리에 실패했습니다."
        : `이 동영상은 서버에서 처리 중입니다. (${progressValue}%)`,
    );
    const message = element(
      "span",
      "detail-media-processing__message",
      media.processingMessage || (failed
        ? "게시글 수정 화면에서 영상을 다시 첨부해 주세요."
        : "처리가 끝나면 이 자리에 동영상이 자동으로 표시됩니다."),
    );
    copy.append(title, message);

    if (!failed) {
      const progress = document.createElement("progress");
      progress.className = "detail-media-processing__progress";
      progress.max = 100;
      progress.value = progressValue;
      progress.setAttribute("aria-label", "동영상 서버 처리 진행률");
      copy.append(progress);
    }
    copy.append(
      element(
        "small",
        "detail-media-processing__file",
        `${media.originalName || "첨부 동영상"} · ${formatBytes(media.fileSize)}`,
      ),
    );
    panel.append(icon, copy);
    item.replaceChildren(panel);
  }

  function updateMediaProcessing(item, media) {
    const progressValue = mediaProcessingProgress(media);
    item.dataset.processingStatus = mediaProcessingStatus(media);
    const titleText =
      `이 동영상은 서버에서 처리 중입니다. (${progressValue}%)`;
    const title = item.querySelector(".detail-media-processing__title");
    if (title.textContent !== titleText) title.textContent = titleText;

    const messageText = media.processingMessage ||
      "처리가 끝나면 이 자리에 동영상이 자동으로 표시됩니다.";
    const message = item.querySelector(".detail-media-processing__message");
    if (message.textContent !== messageText) message.textContent = messageText;

    const progress = item.querySelector(".detail-media-processing__progress");
    if (progress && Number(progress.value) !== progressValue) {
      progress.value = progressValue;
    }

    const fileText =
      `${media.originalName || "첨부 동영상"} · ${formatBytes(media.fileSize)}`;
    const file = item.querySelector(".detail-media-processing__file");
    if (file.textContent !== fileText) file.textContent = fileText;
  }

  function renderPostMediaItem(media) {
    if (!media) return null;
    const item = element("article", "detail-media-item");
    item.dataset.postMediaId = String(media.postMediaId || "");
    item.dataset.processingStatus = mediaProcessingStatus(media);

    if (isVideoMedia(media) && mediaProcessingStatus(media) !== "READY") {
      renderMediaProcessing(item, media);
      return item;
    }
    if (!media.mediaUrl) return null;

    const name = media.originalName || "첨부파일";
    if (media.mediaType === "IMAGE") {
      const image = new Image();
      image.src = media.mediaUrl;
      image.alt = name;
      image.loading = "lazy";
      image.tabIndex = 0;
      image.setAttribute("role", "button");
      image.setAttribute("aria-label", `${name} 크게 보기`);
      image.addEventListener("click", () => openImageViewer(image, name));
      image.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        openImageViewer(image, name);
      });
      image.addEventListener("error", () => {
        renderMediaFallback(
          item,
          media,
          "이 브라우저에서는 해당 사진 형식을 표시할 수 없습니다.",
        );
      }, { once: true });
      item.append(image);
    } else {
      const databaseMedia = String(media.mediaUrl).startsWith(
        "/api/board/posts/media/",
      );
      const video = document.createElement("video");
      const canPlay =
        !media.mimeType || video.canPlayType(media.mimeType) !== "";
      if (!databaseMedia && !media.mimeType) {
        renderMediaFallback(
          item,
          media,
          "외부 동영상 링크입니다.",
          "영상 링크 열기",
        );
      } else if (!canPlay) {
        renderMediaFallback(
          item,
          media,
          "이 브라우저에서는 해당 동영상 형식을 재생할 수 없습니다.",
        );
      } else {
        const videoFrame = element("div", "detail-media-video");
        const loading = element("div", "detail-media-loading");
        loading.setAttribute("role", "status");
        loading.setAttribute("aria-live", "polite");

        const loadingIcon = element(
          "span",
          "material-symbols-rounded detail-media-loading__icon",
          "progress_activity",
        );
        loadingIcon.setAttribute("aria-hidden", "true");
        loading.append(
          loadingIcon,
          element("span", "detail-media-loading__text", "동영상을 불러오는 중..."),
        );

        const finishLoading = () => {
          loading.hidden = true;
          videoFrame.classList.add("is-ready");
        };

        video.controls = true;
        video.preload = "auto";
        video.addEventListener("loadeddata", finishLoading, { once: true });
        video.addEventListener("canplay", finishLoading, { once: true });
        video.addEventListener("error", () => {
          renderMediaFallback(
            item,
            media,
            "동영상을 재생할 수 없습니다. 원본 파일을 내려받아 확인해 주세요.",
          );
        }, { once: true });
        videoFrame.append(video, loading);
        item.append(videoFrame);
        video.src = media.mediaUrl;
      }
    }

    if (!item.querySelector(".detail-media-fallback")) {
      const meta = element("div", "detail-media-meta");
      meta.append(
        element("span", "", `${name} · ${formatBytes(media.fileSize)}`),
      );
      const download = element("a", "", "원본 다운로드");
      download.href = mediaDownloadHref(media);
      meta.append(download);
      item.append(meta);
    }
    return item;
  }

  function renderPostMedia(mediaItems) {
    if (!Array.isArray(mediaItems) || !mediaItems.length) return null;

    const section = element("section", "detail-media-list");
    section.setAttribute("aria-label", "게시글 첨부 미디어");
    mediaItems.forEach((media) => {
      const item = renderPostMediaItem(media);
      if (item) section.append(item);
    });
    return section.childElementCount ? section : null;
  }

  function findRenderedMediaItem(postMediaId) {
    const targetId = String(postMediaId || "");
    return [...detailContent.querySelectorAll(".detail-media-item")]
      .find((item) => item.dataset.postMediaId === targetId) || null;
  }

  function reconcilePostMedia(mediaItems) {
    const nextMedia = Array.isArray(mediaItems) ? mediaItems : [];
    let section = detailContent.querySelector(".detail-media-list");
    const nextIds = new Set(
      nextMedia.map((media) => String(media.postMediaId || "")),
    );

    section?.querySelectorAll(".detail-media-item").forEach((item) => {
      if (!nextIds.has(item.dataset.postMediaId || "")) item.remove();
    });

    nextMedia.forEach((media) => {
      const nextStatus = mediaProcessingStatus(media);
      const currentItem = findRenderedMediaItem(media.postMediaId);
      if (!currentItem) {
        const nextItem = renderPostMediaItem(media);
        if (!nextItem) return;
        if (!section) {
          section = element("section", "detail-media-list");
          section.setAttribute("aria-label", "게시글 첨부 미디어");
          const actions = detailContent.querySelector(".detail-actions");
          detailContent.insertBefore(section, actions || null);
        }
        section.append(nextItem);
        window.FooduckIcons?.enhance(nextItem);
        return;
      }

      const currentStatus = currentItem.dataset.processingStatus || "READY";
      if (isProcessingMedia(media) &&
          ["QUEUED", "PROCESSING"].includes(currentStatus)) {
        updateMediaProcessing(currentItem, media);
        return;
      }
      if (currentStatus === nextStatus) return;

      const nextItem = renderPostMediaItem(media);
      if (nextItem) {
        currentItem.replaceWith(nextItem);
        window.FooduckIcons?.enhance(nextItem);
      } else {
        currentItem.remove();
      }
    });

    if (section && !section.childElementCount) section.remove();
  }

  function clearMediaPoll() {
    if (mediaPollTimer !== null) {
      window.clearTimeout(mediaPollTimer);
      mediaPollTimer = null;
    }
  }

  function scheduleMediaPoll(delay = MEDIA_POLL_BASE_DELAY) {
    clearMediaPoll();
    if (
      mediaPollingDisposed ||
      mediaPollingHalted ||
      document.hidden ||
      mediaPollInFlight ||
      !state.post
    ) return;
    mediaPollTimer = window.setTimeout(pollMediaStatus, delay);
  }

  function startMediaStatusPolling(mediaItems) {
    clearMediaPoll();
    mediaPollGeneration += 1;
    mediaPollDelay = MEDIA_POLL_BASE_DELAY;
    mediaPollFailures = 0;
    mediaPollingHalted = false;
    if (Array.isArray(mediaItems) && mediaItems.some(isVideoMedia)) {
      scheduleMediaPoll(0);
    }
  }

  function haltMediaStatusPolling(message) {
    mediaPollingHalted = true;
    clearMediaPoll();
    detailContent.querySelectorAll(
      ".detail-media-item.is-processing .detail-media-processing__message",
    ).forEach((node) => {
      node.textContent = message;
    });
  }

  async function pollMediaStatus() {
    if (mediaPollInFlight || mediaPollingDisposed || document.hidden) return;
    mediaPollTimer = null;
    mediaPollInFlight = true;
    const generation = mediaPollGeneration;
    let shouldContinue = false;
    try {
      const payload = await Api.get(`/board/posts/${postId}/media`);
      if (mediaPollingDisposed || generation !== mediaPollGeneration ||
          !state.post) return;
      const mediaItems = Array.isArray(payload.data) ? payload.data : [];
      const wasProcessing = Array.isArray(state.post.media) &&
        state.post.media.some(isProcessingMedia);
      reconcilePostMedia(mediaItems);
      state.post = { ...state.post, media: mediaItems };
      mediaPollDelay = MEDIA_POLL_BASE_DELAY;
      mediaPollFailures = 0;
      shouldContinue = mediaItems.some(isProcessingMedia);
      if (wasProcessing && !shouldContinue) invalidateBoardCache();
    } catch (error) {
      if (generation !== mediaPollGeneration) return;
      if ([401, 403, 404].includes(Number(error?.status))) {
        renderPostError(error.message);
        return;
      }
      mediaPollFailures += 1;
      if (mediaPollFailures >= MEDIA_POLL_MAX_FAILURES) {
        haltMediaStatusPolling(
          "처리 상태를 확인할 수 없습니다. 잠시 후 페이지를 새로고침해 주세요.",
        );
        return;
      }
      mediaPollDelay = Math.min(
        MEDIA_POLL_MAX_DELAY,
        mediaPollDelay * 2,
      );
      shouldContinue = Array.isArray(state.post?.media) &&
        state.post.media.some(isProcessingMedia);
    } finally {
      mediaPollInFlight = false;
      if (generation !== mediaPollGeneration) {
        if (Array.isArray(state.post?.media) &&
            state.post.media.some(isVideoMedia)) {
          scheduleMediaPoll(0);
        }
      } else if (shouldContinue) {
        scheduleMediaPoll(mediaPollDelay);
      }
    }
  }

  function renderPost(post) {
    state.post = post;
    const newsPost = isNewsPost(post);
    const newsTarget = newsPost ? newsSource(post) : null;
    const newsReturnPath = newsPost ? restaurantNewsPath(post) : null;
    document.title = `${post.title} · 푸드덕`;
    setBackLink(
      newsReturnPath || (fromBest
        ? "/pages/board/index.html?boardType=BEST"
        : fromPopular
          ? "/pages/board/index.html?boardType=POPULAR"
          : board.listPath(post.boardType)),
      newsReturnPath ? "가게 소식으로 돌아가기" : "커뮤니티 목록",
    );
    detailContent.replaceChildren();

    const badges = element("div", "detail-badges");
    badges.append(detailBadge(categoryLabel(post.category)));
    badges.append(
      detailBadge(
        newsPost
          ? newsTarget?.source === "public"
            ? "식당 소식"
            : "등록 식당 소식"
          : post.boardType === "BUSINESS"
            ? "사업자 커뮤니티"
            : "일반 커뮤니티",
        "post-board-badge",
      ),
    );

    const heading = element("header", "detail-heading");
    heading.append(badges, element("h1", "", post.title));

    const meta = element("div", "detail-meta");
    meta.append(
      authorIdentity(post, { showAuthorMenu: true }),
      element(
        "span",
        "",
        `${formatDate(post.createdAt)}${isEdited(post) ? " · 수정됨" : ""}`,
      ),
      element("span", "", `조회 ${post.viewCount || 0}`),
    );
    meta.append(element("span", "", `추천 ${post.likeCount || 0}`));
    heading.append(meta);
    detailContent.append(heading);

    if (newsPost) {
      const newsRestaurantCard = renderNewsRestaurantCard(post);
      if (newsRestaurantCard) detailContent.append(newsRestaurantCard);
    } else if (post.restaurant) {
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
    const mediaSection = renderPostMedia(post.media);
    if (mediaSection) detailContent.append(mediaSection);
    startMediaStatusPolling(post.media);

    const actions = element("div", "detail-actions");
    const likeButton = actionButton(
      `${post.likedByCurrentUser ? "추천 취소" : "추천"} · ${post.likeCount || 0}`,
      post.likedByCurrentUser
        ? "button button-sm button-primary"
        : "button button-sm button-secondary",
      toggleLike,
    );
    actions.append(likeButton);
    const canManage = newsPost
      ? post.newsManageableByCurrentUser === true && Boolean(newsTarget)
      : (!fromBest && post.ownedByCurrentUser) || session.isAdmin;
    if (canManage) {
      const editLink = element("a", "button button-sm button-secondary", "수정");
      editLink.href = newsPost
        ? newsWritePath(post)
        : board.writePath(post.boardType, post.postId);
      actions.append(
        editLink,
        actionButton("삭제", "button button-sm button-danger", deletePost),
      );
    }
    if (actions.childElementCount) detailContent.append(actions);
    renderRestaurantSide(newsPost ? null : post.restaurant);
    window.FooduckIcons?.enhance(detailContent);
  }

  function renderPostError(message) {
    clearMediaPoll();
    mediaPollGeneration += 1;
    mediaPollingHalted = true;
    state.post = null;
    invalidateBoardCache();
    renderRestaurantSide(null);
    relatedPostList?.replaceChildren();
    unansweredPostList?.replaceChildren();
    commentForm.hidden = true;
    commentList.replaceChildren();
    commentCount.textContent = "0";
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
      const cachedViewCount = Number(cached.data.viewCount) || 0;
      renderPost({
        ...cached.data,
        viewCount: cachedViewCount + 1,
      });
    }

    try {
      const payload = await Api.get(path);
      updateCachedPostViewCount(postId, payload.data?.viewCount);
      writeBoardCache(path, payload.data);
      renderPost(payload.data);
    } catch (error) {
      if (!cached?.data) throw error;
      renderPost(cached.data);
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
    const newsPost = isNewsPost();
    const deletePath = newsPost ? newsDeletePath(state.post) : `/board/posts/${postId}`;
    const returnPath = newsPost
      ? restaurantNewsPath(state.post)
      : board.listPath(state.post.boardType);
    if (!deletePath || !returnPath) {
      showToast(toast, "가게 소식의 식당 정보를 확인할 수 없습니다.", true);
      return;
    }
    const message = newsPost
      ? "이 가게 소식과 연결된 댓글·추천을 삭제하시겠습니까?"
      : "게시글과 연결된 댓글·추천을 삭제하시겠습니까?";
    if (!window.confirm(message)) return;
    try {
      const payload = await Api.delete(deletePath);
      invalidateBoardCache();
      window.alert(payload.message);
      window.location.assign(returnPath);
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  function closeReplyComposer() {
    if (activeReplyPreviewUrl) {
      URL.revokeObjectURL(activeReplyPreviewUrl);
      activeReplyPreviewUrl = null;
    }
    activeReplyForm?.remove();
    activeReplyForm = null;
  }

  function commentImageNode(comment) {
    if (!comment.hasImage || !comment.imageUrl) return null;

    const imageWrap = element("div", "comment-image-wrap");
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
      openImageViewer(
        image,
        comment.imageOriginalName || "댓글 첨부 사진",
      );
    });
    image.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      openImageViewer(
        image,
        comment.imageOriginalName || "댓글 첨부 사진",
      );
    });
    image.addEventListener("error", () => {
      imageWrap.replaceChildren(
        element("span", "comment-image-error", "사진을 불러오지 못했습니다."),
      );
    }, { once: true });
    imageWrap.append(image);
    return imageWrap;
  }

  function replyTargetName(comment) {
    const raw = comment.authorNickname || comment.authorLoginId || "작성자";
    return String(raw).replace(/^@+/, "").trim() || "작성자";
  }

  function hasReplyBody(value, targetName) {
    const content = String(value || "").trim();
    if (!content) return false;
    return content !== `@${targetName}`;
  }

  function validateReplyImage(file) {
    if (!file) return null;
    if (file.size < 1) return "비어 있는 사진은 첨부할 수 없습니다.";
    if (file.size > COMMENT_IMAGE_MAX_BYTES) {
      return "댓글 사진은 5MB 이하만 첨부할 수 있습니다.";
    }
    if (!COMMENT_IMAGE_NAME_PATTERN.test(file.name || "") ||
        (file.type && !COMMENT_IMAGE_TYPES.has(file.type))) {
      return "댓글에는 JPG, PNG, WEBP, GIF 사진만 첨부할 수 있습니다.";
    }
    return null;
  }

  function openReplyComposer(comment, mountTarget) {
    if (!board.requireLogin(window.location.pathname + window.location.search)) return;
    closeReplyComposer();

    const rootParentId = comment.parentCommentId || comment.commentId;
    const targetName = replyTargetName(comment);
    let selectedReplyImage = null;

    const form = element("form", "comment-reply-form");
    const label = element(
      "div",
      "comment-reply-target",
      `@${targetName}님에게 답글 남기기`,
    );
    const textarea = document.createElement("textarea");
    textarea.className = "comment-reply-textarea";
    textarea.maxLength = 1000;
    textarea.rows = 3;
    textarea.setAttribute("aria-label", `${targetName}님에게 답글`);
    textarea.value = `@${targetName} `;

    const tools = element("div", "comment-image-tools");
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.className = "sr-only";
    fileInput.accept = ".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp";
    const imageButton = element("button", "comment-image-select", "사진 첨부");
    imageButton.type = "button";
    const imageNote = element("span", "", "사진 1장 · 최대 5MB");
    tools.append(fileInput, imageButton, imageNote);

    const preview = element("div", "comment-image-preview");
    preview.hidden = true;
    const previewImage = new Image();
    previewImage.alt = "답글 첨부 사진 미리보기";
    const previewCopy = element("div", "comment-image-preview-copy");
    const previewName = element("strong");
    const previewSize = element("span");
    previewCopy.append(previewName, previewSize);
    const removeImage = element("button", "comment-image-remove", "선택 취소");
    removeImage.type = "button";
    preview.append(previewImage, previewCopy, removeImage);

    const submitRow = element("div", "comment-reply-submit-row");
    const cancel = element("button", "comment-action", "취소");
    cancel.type = "button";
    const submit = element("button", "button button-sm button-primary", "답글 등록");
    submit.type = "submit";
    submit.disabled = true;
    submitRow.append(cancel, submit);

    function syncReplySubmitState() {
      submit.disabled = !hasReplyBody(textarea.value, targetName);
    }

    function clearReplyImage() {
      selectedReplyImage = null;
      fileInput.value = "";
      if (activeReplyPreviewUrl) {
        URL.revokeObjectURL(activeReplyPreviewUrl);
        activeReplyPreviewUrl = null;
      }
      previewImage.removeAttribute("src");
      previewName.textContent = "";
      previewSize.textContent = "";
      preview.hidden = true;
    }

    imageButton.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", () => {
      const file = fileInput.files?.[0] || null;
      const error = validateReplyImage(file);
      if (error) {
        showToast(toast, error, true);
        clearReplyImage();
        return;
      }
      clearReplyImage();
      if (!file) return;
      selectedReplyImage = file;
      activeReplyPreviewUrl = URL.createObjectURL(file);
      previewImage.src = activeReplyPreviewUrl;
      previewImage.alt = `${file.name || "답글 첨부 사진"} 미리보기`;
      previewName.textContent = file.name || "첨부 사진";
      previewSize.textContent = formatBytes(file.size);
      preview.hidden = false;
    });
    removeImage.addEventListener("click", clearReplyImage);
    cancel.addEventListener("click", closeReplyComposer);
    textarea.addEventListener("input", syncReplySubmitState);

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = textarea.value.trim();
      if (!hasReplyBody(content, targetName)) {
        syncReplySubmitState();
        showToast(toast, "답글 내용을 입력해 주세요.", true);
        return;
      }

      const imageFile = selectedReplyImage;
      submit.disabled = true;
      imageButton.disabled = true;
      try {
        const payload = await Api.post(`/board/posts/${postId}/comments`, {
          content,
          parentCommentId: rootParentId,
        });
        const createdCommentId = payload.data?.commentId;
        let imageUploadError = null;
        if (imageFile && createdCommentId) {
          try {
            await uploadCommentImage(createdCommentId, imageFile);
          } catch (error) {
            imageUploadError = error;
          }
        }

        invalidateBoardCache();
        clearReplyImage();
        closeReplyComposer();
        if (imageUploadError) {
          showToast(
            toast,
            `답글은 등록됐지만 사진 업로드에 실패했습니다. ${imageUploadError.message}`,
            true,
          );
        } else {
          showToast(
            toast,
            imageFile ? "답글과 사진이 등록되었습니다." : "답글이 등록되었습니다.",
          );
        }
        await loadComments();
      } catch (error) {
        showToast(toast, error.message, true);
      } finally {
        syncReplySubmitState();
        imageButton.disabled = false;
      }
    });

    form.append(label, textarea, tools, preview, submitRow);
    mountTarget.append(form);
    activeReplyForm = form;
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  function shouldIgnoreCommentAreaReplyClick(event, item) {
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

  function renderCommentItem(comment, options = {}) {
    const { isReply = false, hasReplies = false } = options;
    const item = element(
      "article",
      isReply ? "comment-item comment-reply" : "comment-item",
    );
    item.id = `comment-${comment.commentId}`;

    const top = element("div", "comment-top");
    top.append(
      authorIdentity(comment, { showAuthorMenu: true }),
      element(
        "span",
        "comment-date",
        `${formatDate(comment.createdAt)}${isEdited(comment) ? " · 수정됨" : ""}`,
      ),
    );
    item.append(top, element("div", "comment-content", comment.content));

    const image = commentImageNode(comment);
    if (image) item.append(image);

    const actions = element("div", "comment-actions");
    actions.append(
      actionButton("답글", "comment-action", () => openReplyComposer(comment, item)),
    );
    if (comment.ownedByCurrentUser || session.isAdmin) {
      actions.append(
        actionButton("수정", "comment-action", () => editComment(comment)),
        actionButton(
          "삭제",
          "comment-action",
          () => deleteComment(comment, hasReplies),
        ),
      );
    }
    item.append(actions);
    item.classList.add("comment-item--replyable");
    item.addEventListener("click", (event) => {
      if (activeReplyForm && item.contains(activeReplyForm)) return;
      if (shouldIgnoreCommentAreaReplyClick(event, item)) return;
      openReplyComposer(comment, item);
    });
    return item;
  }

  function renderComments(pageData) {
    closeReplyComposer();
    const comments = pageData.content || [];
    commentCount.textContent = String(
      pageData.totalCommentCount ?? pageData.totalElements ?? 0,
    );
    commentList.replaceChildren();
    if (!comments.length) {
      commentList.append(
        element("p", "comment-empty", "첫 댓글을 함께 남겨 보세요."),
      );
      return;
    }

    const repliesByParent = new Map();
    comments.forEach((comment) => {
      if (!comment.parentCommentId) return;
      const replies = repliesByParent.get(comment.parentCommentId) || [];
      replies.push(comment);
      repliesByParent.set(comment.parentCommentId, replies);
    });

    comments
      .filter((comment) => !comment.parentCommentId)
      .forEach((comment) => {
        const replies = repliesByParent.get(comment.commentId) || [];
        const thread = element("section", "comment-thread");
        thread.append(renderCommentItem(comment, { hasReplies: replies.length > 0 }));

        if (replies.length) {
          const replyList = element("div", "comment-replies");
          replies.forEach((reply) => {
            replyList.append(renderCommentItem(reply, { isReply: true }));
          });
          thread.append(replyList);
        }
        commentList.append(thread);
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

  async function deleteComment(comment, hasReplies = false) {
    const message = hasReplies
      ? "댓글을 삭제하면 연결된 답글도 함께 삭제됩니다. 삭제하시겠습니까?"
      : "댓글을 삭제하시겠습니까?";
    if (!window.confirm(message)) return;
    try {
      const payload = await Api.delete(`/board/comments/${comment.commentId}`);
      invalidateBoardCache();
      showToast(toast, payload.message);
      await loadComments();
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  commentImageSelect?.addEventListener("click", () => {
    commentImageInput?.click();
  });

  commentImageInput?.addEventListener("change", () => {
    selectCommentImage(commentImageInput.files?.[0] || null);
  });

  commentImageRemove?.addEventListener("click", () => {
    clearCommentImageSelection();
  });

  commentForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!board.requireLogin(window.location.pathname + window.location.search)) return;
    const content = commentContent.value.trim();
    if (!content) {
      showToast(toast, "댓글 내용을 입력해 주세요.", true);
      return;
    }

    const imageFile = selectedCommentImage;
    if (commentSubmitButton) commentSubmitButton.disabled = true;
    if (commentImageSelect) commentImageSelect.disabled = true;
    try {
      const payload = await Api.post(`/board/posts/${postId}/comments`, { content });
      const createdCommentId = payload.data?.commentId;
      let imageUploadError = null;
      if (imageFile && createdCommentId) {
        try {
          await uploadCommentImage(createdCommentId, imageFile);
        } catch (error) {
          imageUploadError = error;
        }
      }

      invalidateBoardCache();
      commentContent.value = "";
      clearCommentImageSelection();
      if (imageUploadError) {
        showToast(
          toast,
          `댓글은 등록됐지만 사진 업로드에 실패했습니다. ${imageUploadError.message}`,
          true,
        );
      } else {
        showToast(
          toast,
          imageFile ? "댓글과 사진이 등록되었습니다." : payload.message,
        );
      }
      await loadComments();
    } catch (error) {
      showToast(toast, error.message, true);
    } finally {
      if (commentSubmitButton) commentSubmitButton.disabled = false;
      if (commentImageSelect) commentImageSelect.disabled = false;
    }
  });

  if (!session.authenticated) {
    commentLoginNote.textContent = "댓글 작성 시 로그인 화면으로 이동합니다.";
  } else {
    commentLoginNote.textContent = `@${session.loginId || "소셜 계정"}으로 작성합니다.`;
  }

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

  async function loadPage() {
    if (!postId) {
      renderPostError("유효한 게시글 번호가 없습니다.");
      renderRelatedPosts([]);
      renderUnansweredPosts([]);
      commentForm.hidden = true;
      return;
    }
    try {
      const postPromise = loadPost();
      await Promise.all([
        postPromise,
        loadComments(),
        postPromise.then(() => {
          if (isNewsPost()) {
            renderRelatedPosts([]);
            return;
          }
          return loadRelatedPosts();
        }),
        postPromise.then(loadUnansweredPosts),
      ]);
    } catch (error) {
      renderPostError(error.message);
      commentForm.hidden = true;
    }
  }

  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      clearMediaPoll();
      return;
    }
    if (Array.isArray(state.post?.media) &&
        state.post.media.some(isProcessingMedia)) {
      scheduleMediaPoll(0);
    }
  });

  window.addEventListener("pagehide", () => {
    mediaPollingDisposed = true;
    clearMediaPoll();
    clearCommentImageSelection();
    closeReplyComposer();
    document.querySelector(".detail-image-viewer")?.remove();
    document.body.classList.remove("is-image-viewer-open");
  });

  window.addEventListener("pageshow", () => {
    mediaPollingDisposed = false;
    if (Array.isArray(state.post?.media) &&
        state.post.media.some(isProcessingMedia)) {
      scheduleMediaPoll(0);
    }
  });

  initializeScrollTopButton();
  loadPage();
})();
