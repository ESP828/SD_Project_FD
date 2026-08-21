(() => {
  const session = window.FooduckSession;
  const board = window.FooduckBoard;
  const emojis = window.FooduckEmojis;
  const postId = board?.readPostId();
  const detailParams = new URLSearchParams(window.location.search);
  const sourceView = detailParams.get("from");
  const fromBest = sourceView === "BEST";
  const fromPopular = sourceView === "POPULAR";
  const requestedReturnTo = detailParams.get("returnTo");
  const requestedNewsPageValue = Number.parseInt(detailParams.get("newsPage"), 10);
  const requestedNewsPage = Number.isInteger(requestedNewsPageValue) && requestedNewsPageValue >= 0
    ? requestedNewsPageValue
    : 0;
  const state = { post: null };
  const NOTICE_CATEGORY_OPTIONS = [
    ["GENERAL", "자유 이야기"],
    ["RECOMMENDATION", "맛집 추천"],
    ["REVIEW", "방문 후기"],
    ["QUESTION", "질문"],
    ["TRAVEL", "맛집 여행"],
  ];
  const MEDIA_POLL_BASE_DELAY = 2500;
  const MEDIA_POLL_MAX_DELAY = 15000;
  const MEDIA_POLL_MAX_FAILURES = 5;
  const COMMENT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
  const COMMENT_PAGE_SIZE = 5;
  const BOARD_FLASH_KEY = "fooduck:board:flash:v1";
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
  let postDeleteInFlight = false;
  let noticeUnpinInFlight = false;
  let noticePinInFlight = false;
  let sessionNicknamePromise = null;
  let cachedFallbackNoticeShown = false;
  const commentDeleteInFlight = new Set();

  const detailContent = document.getElementById("post-detail-content");
  const listLink = document.getElementById("detail-list-link");
  const relatedPostList = document.getElementById("related-post-list");
  const unansweredPostList = document.getElementById("detail-unanswered-post-list");
  const restaurantSide = document.getElementById("detail-restaurant-side");
  const commentCount = document.getElementById("detail-comment-count");
  const commentForm = document.getElementById("comment-form");
  const commentContent = document.getElementById("comment-content");
  const commentList = document.getElementById("comment-list");
  const commentPagination = document.getElementById("comment-pagination");
  const commentWriteShortcut = document.getElementById("comment-write-shortcut");
  const commentLoadStatus = document.getElementById("comment-load-status");
  const commentCharacterCount = document.getElementById("comment-character-count");
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
  const commentEmojiToggle = document.getElementById("comment-emoji-toggle");
  const commentEmojiPanel = document.getElementById("comment-emoji-panel");
  const commentSubmitButton = commentForm?.querySelector('button[type="submit"]');
  const toast = document.getElementById("board-toast");

  if (!session || !board || !detailContent) return;

  const {
    authorIdentity,
    categoryLabel,
    createAuthPopupController,
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

  function emojiTextElement(tagName, className, value) {
    const node = element(tagName, className, "");
    if (emojis) emojis.renderText(node, value);
    else node.textContent = String(value ?? "");
    return node;
  }

  function resizeCommentTextarea(textarea, minimumHeight = 78) {
    if (!(textarea instanceof HTMLTextAreaElement)) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.max(textarea.scrollHeight, minimumHeight)}px`;
  }

  let activeEmojiPicker = null;

  function closeEmojiPicker(picker = activeEmojiPicker) {
    if (!picker) return;
    if (picker.panel?.isConnected) picker.panel.hidden = true;
    if (picker.toggle?.isConnected) picker.toggle.setAttribute("aria-expanded", "false");
    if (activeEmojiPicker === picker) activeEmojiPicker = null;
  }

  function openEmojiPicker(picker) {
    if (!picker?.panel || !picker?.toggle) return;
    if (activeEmojiPicker && activeEmojiPicker !== picker) closeEmojiPicker(activeEmojiPicker);
    picker.panel.hidden = false;
    picker.toggle.setAttribute("aria-expanded", "true");
    activeEmojiPicker = picker;
  }

  function insertCommentEmoji(textarea, emoji) {
    if (!textarea || !emoji) return false;
    if (emojis?.insertIntoEditor?.(textarea, emoji)) return true;
    const value = textarea.value || "";
    const start = Number.isInteger(textarea.selectionStart) ? textarea.selectionStart : value.length;
    const end = Number.isInteger(textarea.selectionEnd) ? textarea.selectionEnd : start;
    const nextValue = `${value.slice(0, start)}${emoji}${value.slice(end)}`;

    if (textarea.maxLength > 0 && nextValue.length > textarea.maxLength) {
      showToast(toast, `댓글은 최대 ${textarea.maxLength}자까지 입력할 수 있습니다.`, true);
      return false;
    }

    textarea.value = nextValue;
    const nextCaret = start + emoji.length;
    textarea.focus({ preventScroll: true });
    textarea.setSelectionRange(nextCaret, nextCaret);
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
    return true;
  }

  function setupCommentEmojiPicker(textarea, toggle, panel) {
    if (!textarea || !toggle || !panel) return null;

    if (!emojis) return null;
    emojis.attachEditor?.(textarea);
    emojis.populatePicker(panel, {
      gridClass: "comment-emoji-grid fooduck-custom-emoji-grid",
      buttonClass: "comment-emoji-option fooduck-custom-emoji-option",
      title: "이모지",
      onSelect: (emoji) => insertCommentEmoji(textarea, emoji.code),
    });

    const picker = { textarea, toggle, panel };
    toggle.addEventListener("click", (event) => {
      event.stopPropagation();
      if (panel.hidden) openEmojiPicker(picker);
      else closeEmojiPicker(picker);
    });
    panel.addEventListener("click", (event) => event.stopPropagation());
    return picker;
  }

  function isEdited(item) {
    return item?.edited === true;
  }

  function canUseCacheAfterError(error) {
    return ![401, 403, 404].includes(Number(error?.status));
  }

  function showCachedFallbackNoticeOnce(message =
    "최신 내용을 불러오지 못해 잠시 저장된 내용을 보여드리고 있습니다.") {
    if (cachedFallbackNoticeShown) return;
    cachedFallbackNoticeShown = true;
    showToast(toast, message);
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

  function communityReturnPath(post = state.post) {
    if (listReturnPath) return listReturnPath;
    if (fromBest) return "/board?boardType=BEST";
    if (fromPopular) return "/board?boardType=POPULAR";
    return board.listPath(post?.boardType);
  }

  function detailHrefPreservingReturn(nextPostId) {
    const params = new URLSearchParams({ postId: String(nextPostId) });
    if (listReturnPath) {
      params.set("returnTo", listReturnPath);
    } else if (fromBest) {
      params.set("from", "BEST");
    } else if (fromPopular) {
      params.set("from", "POPULAR");
    }
    return `/board/detail?${params.toString()}`;
  }

  function isCommentSubmitEnter(event) {
    return (
      event.key === "Enter" &&
      !event.shiftKey &&
      !event.isComposing &&
      event.keyCode !== 229
    );
  }

  function updateCharacterCount(target, counter) {
    if (!target || !counter) return;
    const maxLength = Number(target.maxLength) > 0 ? Number(target.maxLength) : 1000;
    const length = target.value.length;
    counter.textContent = `${length} / ${maxLength}`;
    counter.classList.toggle("is-near-limit", length >= Math.floor(maxLength * 0.8));
  }

  function isPinnedPost(post = state.post) {
    return post?.pinned === true || post?.category === "NOTICE";
  }

  function chooseNoticeCategory({
    title,
    message,
    confirmLabel,
    initialCategory,
  }) {
    return new Promise((resolve) => {
      const dialog = document.createElement("dialog");
      dialog.className = "board-dialog comment-confirm-dialog notice-category-dialog";

      const shell = element("div", "dialog-shell comment-confirm-shell");
      const heading = element("div", "comment-confirm-heading");
      const iconWrap = element("span", "comment-confirm-icon notice-category-dialog__icon");
      const noticeIcon = element("span", "material-symbols-rounded", "campaign");
      noticeIcon.setAttribute("aria-hidden", "true");
      iconWrap.append(noticeIcon);
      const copy = element("div", "comment-confirm-copy");
      copy.append(element("h2", "", title), element("p", "", message));
      heading.append(iconWrap, copy);

      const field = element("label", "notice-category-dialog__field");
      field.append(element("span", "", "카테고리"));
      const select = element("select", "notice-category-dialog__select");
      NOTICE_CATEGORY_OPTIONS.forEach(([value, label]) => {
        const option = element("option", "", label);
        option.value = value;
        select.append(option);
      });
      const safeInitial = NOTICE_CATEGORY_OPTIONS.some(([value]) => value === initialCategory)
        ? initialCategory
        : "GENERAL";
      select.value = safeInitial;
      field.append(select);

      const actions = element("div", "comment-confirm-actions");
      const cancel = element("button", "button button-sm button-secondary", "취소");
      cancel.type = "button";
      const confirm = element("button", "button button-sm button-primary", confirmLabel);
      confirm.type = "button";
      actions.append(cancel, confirm);
      shell.append(heading, field, actions);
      dialog.append(shell);
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

      cancel.addEventListener("click", () => finish(null));
      confirm.addEventListener("click", () => finish(select.value));
      dialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        finish(null);
      });
      dialog.addEventListener("click", (event) => {
        if (event.target === dialog) finish(null);
      });

      dialog.showModal();
      select.focus();
    });
  }

  function confirmBoardAction({
    title,
    message,
    confirmLabel = "삭제",
    danger = true,
    iconName = danger ? "delete" : "edit",
  }) {
    return new Promise((resolve) => {
      const dialog = document.createElement("dialog");
      dialog.className = "board-dialog comment-confirm-dialog";

      const shell = element("div", "dialog-shell comment-confirm-shell");
      const heading = element("div", "comment-confirm-heading");
      const iconWrap = element("span", "comment-confirm-icon");
      const warningIcon = element("span", "material-symbols-rounded", iconName);
      warningIcon.setAttribute("aria-hidden", "true");
      iconWrap.append(warningIcon);
      const copy = element("div", "comment-confirm-copy");
      copy.append(
        element("h2", "", title),
        element("p", "", message),
      );
      heading.append(iconWrap, copy);

      const actions = element("div", "comment-confirm-actions");
      const cancel = element("button", "button button-sm button-secondary", "취소");
      cancel.type = "button";
      const confirm = element(
        "button",
        danger ? "button button-sm button-danger" : "button button-sm button-primary",
        confirmLabel,
      );
      confirm.type = "button";
      actions.append(cancel, confirm);
      shell.append(heading, actions);
      dialog.append(shell);
      document.body.append(dialog);
      window.FooduckIcons?.enhance(dialog);

      let settled = false;
      const finish = (result) => {
        if (settled) return;
        settled = true;
        dialog.close();
        dialog.remove();
        resolve(result);
      };

      cancel.addEventListener("click", () => finish(false));
      confirm.addEventListener("click", () => finish(true));
      dialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        finish(false);
      });
      dialog.addEventListener("click", (event) => {
        if (event.target === dialog) finish(false);
      });

      dialog.showModal();
      cancel.focus();
    });
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
      newsPage: String(requestedNewsPage),
    });
    return `/restaurant/detail?${params.toString()}`;
  }

  function restaurantInfoPath(post) {
    const target = newsSource(post);
    if (!target) return null;
    const params = new URLSearchParams({
      source: target.source,
      id: String(target.id),
      tab: "info",
    });
    return `/restaurant/detail?${params.toString()}`;
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
      newsPage: String(requestedNewsPage),
    });
    return `/board/write?${params.toString()}`;
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
  let pendingCommentImageRetry = null;
  let commentImageRetryNotice = null;
  let detailAuthPopupController = null;
  let pendingDetailLoginAction = null;
  let detailLoginSuccessMessage = "로그인되었습니다.";
  let detailLogoutInFlight = false;
  let likeInFlight = false;
  let activeLikeButton = null;
  let activeReplyForm = null;
  let activeReplyPreviewUrl = null;
  let activeCommentEditForm = null;
  let replyDiscardPromptOpen = false;
  let commentEditDiscardPromptOpen = false;
  let detailLeavePromptOpen = false;
  let allowDetailNavigation = false;
  let currentCommentPage = 0;
  let totalCommentPages = 0;
  let commentPageLoading = false;
  const expandedReplyThreadIds = new Set();

  function decodeAccessToken(token) {
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

  function handleDetailLoginAuthenticated() {
    const token = Api.getToken();
    if (!token) return;
    const payload = decodeAccessToken(token) || {};
    session.authenticated = true;
    session.accountId = Number(payload.sub) || session.accountId || null;
    session.loginId = payload.loginId || session.loginId || null;
    session.nickname = payload.nickname || session.nickname || null;
    session.authorities = Array.isArray(payload.authorities)
      ? payload.authorities.filter((value) => typeof value === "string")
      : session.authorities || [];
    session.canManageBusiness = session.authorities.includes("ROLE_BUSINESS")
      || session.authorities.includes("ROLE_ADMIN");
    session.isAdmin = session.authorities.includes("ROLE_ADMIN");
    session.hasAuthority = (authority) => session.authorities.includes(authority);
    void hydrateSessionNickname();

    const action = pendingDetailLoginAction;
    const message = detailLoginSuccessMessage;
    pendingDetailLoginAction = null;
    detailLoginSuccessMessage = "로그인되었습니다.";
    showToast(toast, message);
    if (typeof action === "function") {
      window.setTimeout(() => {
        try {
          const result = action();
          if (result && typeof result.catch === "function") {
            result.catch((error) => {
              showToast(toast, error?.message || "요청을 처리하지 못했습니다.", true);
            });
          }
        } catch (error) {
          showToast(toast, error?.message || "요청을 처리하지 못했습니다.", true);
        }
      }, 0);
    }
  }

  async function continueDetailLoginWithoutPopup(loginUrl) {
    if (hasUnsavedDetailDrafts()) {
      const confirmed = await confirmBoardAction({
        title: "현재 화면에서 로그인할까요?",
        message: "로그인 팝업이 차단되었습니다. 현재 화면에서 로그인하면 작성 중인 댓글·답글·수정 내용과 첨부한 사진은 저장되지 않습니다.",
        confirmLabel: "로그인으로 이동",
        danger: false,
        iconName: "login",
      });
      if (!confirmed) {
        pendingDetailLoginAction = null;
        detailLoginSuccessMessage = "로그인되었습니다.";
        showToast(toast, "팝업을 허용한 뒤 다시 로그인해 주세요.", true);
        return;
      }
    }

    pendingDetailLoginAction = null;
    detailLoginSuccessMessage = "로그인되었습니다.";
    allowDetailNavigation = true;
    window.location.assign(loginUrl);
  }

  function ensureDetailAuthPopupController() {
    if (detailAuthPopupController) return detailAuthPopupController;
    detailAuthPopupController = createAuthPopupController({
      popupName: "fooduck-board-detail-login",
      onAuthenticated: handleDetailLoginAuthenticated,
      onClosed: () => {
        pendingDetailLoginAction = null;
        detailLoginSuccessMessage = "로그인되었습니다.";
        showToast(toast, "로그인이 취소되었습니다.", true);
      },
      onBlocked: ({ loginUrl }) => {
        void continueDetailLoginWithoutPopup(loginUrl);
      },
    });
    return detailAuthPopupController;
  }

  function completeDetailLoginIfReady() {
    return ensureDetailAuthPopupController().completeIfReady();
  }

  function openDetailLogin({ onSuccess = null, successMessage = "로그인되었습니다." } = {}) {
    const nextPath = `${window.location.pathname}${window.location.search}`;
    pendingDetailLoginAction = typeof onSuccess === "function" ? onSuccess : null;
    detailLoginSuccessMessage = successMessage;
    return ensureDetailAuthPopupController().open({ nextPath });
  }

  function initializeDetailLoginEntryPoints() {
    document.addEventListener("click", (event) => {
      const link = event.target.closest(
        '.site-header a.header-auth-button[href^="/auth/login"]',
      );
      if (!link || session.authenticated) return;
      event.preventDefault();
      openDetailLogin({
        successMessage: "로그인되었습니다. 현재 게시글로 돌아갑니다.",
        onSuccess: () => window.location.reload(),
      });
    });
  }

  function initializeDetailLogoutEntryPoint() {
    document.addEventListener("click", async (event) => {
      const button = event.target.closest(".site-header [data-logout]");
      if (!button || !session.authenticated) return;

      // 공통 헤더는 로그아웃 후 홈으로 이동한다. 상세 화면에서는 그 동작을
      // 먼저 가로채 현재 게시글 URL을 유지한 채 인증 상태만 새로 반영한다.
      event.preventDefault();
      event.stopImmediatePropagation();
      if (detailLogoutInFlight) return;
      if (!(await confirmDetailPageLeave())) return;

      detailLogoutInFlight = true;
      button.disabled = true;
      allowDetailNavigation = true;

      try {
        await Api.logout();
      } catch (_error) {
        Api.clearToken();
      } finally {
        window.location.reload();
      }
    }, true);
  }

  function mainCommentHasDraft() {
    return Boolean(commentContent?.value.trim() || selectedCommentImage);
  }

  function commentEditorHasDraft(form = activeCommentEditForm) {
    if (!form) return false;
    const textarea = form.querySelector(".comment-edit-textarea");
    const initialValue = form.dataset.initialValue || "";
    return Boolean(
      textarea && textarea.value.trim() !== initialValue.trim()
    );
  }

  function hasUnsavedDetailDrafts() {
    return (
      mainCommentHasDraft() ||
      replyComposerHasDraft() ||
      commentEditorHasDraft()
    );
  }

  async function confirmDetailPageLeave() {
    if (!hasUnsavedDetailDrafts()) return true;
    if (detailLeavePromptOpen) return false;

    detailLeavePromptOpen = true;
    try {
      return await confirmBoardAction({
        title: "작성 중인 내용을 버리고 이동할까요?",
        message: "입력한 댓글·답글·수정 내용과 첨부한 사진은 저장되지 않습니다.",
        confirmLabel: "나가기",
        danger: false,
        iconName: "edit",
      });
    } finally {
      detailLeavePromptOpen = false;
    }
  }

  function initializeDetailNavigationGuard() {
    document.addEventListener("click", async (event) => {
      if (allowDetailNavigation || event.defaultPrevented) return;
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const link = event.target.closest("a[href]");
      if (!link || link.hasAttribute("download")) return;
      if (link.target && link.target.toLowerCase() !== "_self") return;

      // 상세 화면의 로그인 링크는 팝업만 열고 현재 페이지를 떠나지 않는다.
      if (
        !session.authenticated &&
        link.matches('.site-header a.header-auth-button[href^="/auth/login"]')
      ) {
        return;
      }

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
      if (!hasUnsavedDetailDrafts()) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      if (!(await confirmDetailPageLeave())) return;

      allowDetailNavigation = true;
      window.location.assign(targetUrl.href);
      window.setTimeout(() => {
        allowDetailNavigation = false;
      }, 0);
    }, true);
  }

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

  function clearCommentImageRetryNotice() {
    pendingCommentImageRetry = null;
    commentImageRetryNotice?.remove();
    commentImageRetryNotice = null;
  }

  function showCommentImageRetryNotice(
    commentId,
    file,
    error,
    { label = "댓글" } = {},
  ) {
    if (!commentId || !file) return;
    clearCommentImageRetryNotice();
    pendingCommentImageRetry = { commentId, file, label };

    const notice = element("div", "comment-upload-retry");
    const copy = element("div", "comment-upload-retry__copy");
    copy.append(
      element("strong", "", `${label}은 등록됐지만 사진을 올리지 못했습니다.`),
      element(
        "span",
        "",
        error?.message || "사진만 다시 올릴 수 있습니다.",
      ),
    );

    const actions = element("div", "comment-upload-retry__actions");
    const retryButton = element(
      "button",
      "button button-sm button-primary",
      "사진 다시 올리기",
    );
    retryButton.type = "button";
    const dismissButton = element(
      "button",
      "button button-sm button-secondary",
      "닫기",
    );
    dismissButton.type = "button";

    retryButton.addEventListener("click", async () => {
      const retry = pendingCommentImageRetry;
      if (!retry) return;
      retryButton.disabled = true;
      dismissButton.disabled = true;
      retryButton.textContent = "올리는 중";
      try {
        await uploadCommentImage(retry.commentId, retry.file);
        invalidateBoardCache();
        clearCommentImageRetryNotice();
        showToast(toast, `${retry.label || "댓글"} 사진이 등록되었습니다.`);
        if (!replyComposerHasDraft() && !commentEditorHasDraft()) {
          try {
            await loadCommentPage(currentCommentPage, {
              highlightCommentId: retry.commentId,
              scrollToHighlight: false,
            });
          } catch (reloadError) {
            showToast(toast, reloadError.message, true);
          }
        }
      } catch (retryError) {
        retryButton.disabled = false;
        dismissButton.disabled = false;
        retryButton.textContent = "사진 다시 올리기";
        copy.querySelector("span").textContent =
          retryError.message || "사진 업로드에 다시 실패했습니다.";
      }
    });

    dismissButton.addEventListener("click", clearCommentImageRetryNotice);
    actions.append(retryButton, dismissButton);
    notice.append(copy, actions);
    commentForm.before(notice);
    commentImageRetryNotice = notice;
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
      link.href = detailHrefPreservingReturn(post.postId);
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

  async function loadRelatedPosts(options = {}) {
    if (!relatedPostList) return;
    const forceRefresh = options.forceRefresh === true;
    const preserveOnError = options.preserveOnError === true;
    const path = `/board/posts/${postId}/related?size=5`;
    const cached = readBoardCache(path);
    if (cached) {
      renderRelatedPosts(cached.data || []);
      if (cached.fresh && !forceRefresh) return;
    } else if (!preserveOnError) {
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
      if (!canUseCacheAfterError(error)) {
        relatedPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
        return;
      }
      if (cached) {
        showCachedFallbackNoticeOnce();
        return;
      }
      if (preserveOnError) {
        showCachedFallbackNoticeOnce(
          "최신 게시판 정보를 확인하지 못해 현재 화면을 그대로 유지했습니다.",
        );
        return;
      }
      relatedPostList.replaceChildren(
        element("li", "best-loading", error.message || "불러오지 못했습니다."),
      );
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
      link.href = detailHrefPreservingReturn(post.postId);
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

  async function loadUnansweredPosts(options = {}) {
    if (!unansweredPostList || !state.post) return;
    const forceRefresh = options.forceRefresh === true;
    const preserveOnError = options.preserveOnError === true;
    const params = new URLSearchParams({
      boardType: state.post.boardType === "BUSINESS" ? "BUSINESS" : "GENERAL",
      size: "4",
    });
    const path = `/board/posts/unanswered?${params.toString()}`;
    const cached = readBoardCache(path);
    if (cached) {
      renderUnansweredPosts(cached.data || []);
      if (cached.fresh && !forceRefresh) return;
    } else if (!preserveOnError) {
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
      if (!canUseCacheAfterError(error)) {
        unansweredPostList.replaceChildren(
          element("li", "best-loading", error.message || "불러오지 못했습니다."),
        );
        return;
      }
      if (cached) {
        showCachedFallbackNoticeOnce();
        return;
      }
      if (preserveOnError) {
        showCachedFallbackNoticeOnce(
          "최신 게시판 정보를 확인하지 못해 현재 화면을 그대로 유지했습니다.",
        );
        return;
      }
      unansweredPostList.replaceChildren(
        element("li", "best-loading", error.message || "불러오지 못했습니다."),
      );
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
    item.dataset.mediaType = String(media.mediaType || "").toUpperCase();

    if (isVideoMedia(media) && mediaProcessingStatus(media) !== "READY") {
      renderMediaProcessing(item, media);
      return item;
    }
    if (!media.mediaUrl) return null;

    const name = media.originalName || "첨부파일";
    if (media.mediaType === "IMAGE") {
      item.classList.add("detail-media-item--image");
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

  function syncImageOrderBadges(section) {
    if (!section) return;

    const imageItems = [...section.querySelectorAll(
      '.detail-media-item[data-media-type="IMAGE"]',
    )];

    imageItems.forEach((item, index) => {
      let badge = item.querySelector(".detail-media-order");
      if (!badge) {
        badge = element("span", "detail-media-order");
        badge.setAttribute("aria-hidden", "true");
        item.append(badge);
      }
      badge.textContent = String(index + 1);
    });
  }

  function renderPostMedia(mediaItems) {
    if (!Array.isArray(mediaItems) || !mediaItems.length) return null;

    const section = element("section", "detail-media-list");
    section.setAttribute("aria-label", "게시글 첨부 미디어");
    mediaItems.forEach((media) => {
      const item = renderPostMediaItem(media);
      if (item) section.append(item);
    });
    syncImageOrderBadges(section);
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

    if (section && section.childElementCount) {
      syncImageOrderBadges(section);
    } else if (section) {
      section.remove();
    }
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
    if (Array.isArray(mediaItems) && mediaItems.some(isProcessingMedia)) {
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
            state.post.media.some(isProcessingMedia)) {
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
      newsReturnPath || communityReturnPath(post),
      newsReturnPath ? "가게 소식으로 돌아가기" : "커뮤니티 목록",
    );
    detailContent.replaceChildren();

    const badges = element("div", "detail-badges");
    if (isPinnedPost(post)) {
      badges.append(detailBadge("공지 · 상단 고정", "post-badge post-badge--notice"));
    }
    if (post.category !== "NOTICE") {
      badges.append(detailBadge(categoryLabel(post.category)));
    }
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
      authorIdentity(post, {
        showAuthorMenu: true,
        authorMenuContext: newsPost ? "NEWS" : "COMMUNITY",
        authorActivityCueMode: "full",
      }),
      element(
        "span",
        "",
        `${formatDate(post.createdAt)}${isEdited(post) ? " · 수정됨" : ""}`,
      ),
      element("span", "", `조회 ${post.viewCount || 0}`),
    );
    const likeMeta = element("span", "detail-like-count", `추천 ${post.likeCount || 0}`);
    likeMeta.dataset.likeCount = "true";
    meta.append(likeMeta);
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
      const source = newsSource(post);
      const link = element("a", "button button-sm button-secondary", "가게 상세 보기");
      if (source) {
        const params = new URLSearchParams({ source: source.source, id: String(source.id) });
        link.href = `/restaurant/detail?${params.toString()}`;
      } else {
        link.href = mapHref(post.restaurant);
      }
      restaurant.append(copy, link);
      detailContent.append(restaurant);
    }

    detailContent.append(emojiTextElement("div", "detail-body", post.content));
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
    likeButton.disabled = likeInFlight;
    activeLikeButton = likeButton;
    actions.append(likeButton);
    const canManage = newsPost
      ? post.newsManageableByCurrentUser === true && Boolean(newsTarget)
      : post.ownedByCurrentUser || session.isAdmin;
    if (canManage) {
      if (!newsPost && session.isAdmin && isPinnedPost(post)) {
        const unpinButton = actionButton(
          "공지에서 내리기",
          "button button-sm button-secondary",
          unpinNotice,
        );
        unpinButton.disabled = noticeUnpinInFlight;
        actions.append(unpinButton);
      } else if (!newsPost && session.isAdmin && !isPinnedPost(post)) {
        const pinButton = actionButton(
          "공지로 올리기",
          "button button-sm button-secondary",
          pinNotice,
        );
        pinButton.disabled = noticePinInFlight;
        actions.append(pinButton);
      }
      const editLink = element("a", "button button-sm button-secondary", "수정");
      if (newsPost) {
        editLink.href = newsWritePath(post);
      } else {
        const editUrl = new URL(
          board.writePath(post.boardType, post.postId),
          window.location.origin,
        );
        editUrl.searchParams.set("returnTo", communityReturnPath(post));
        editLink.href = `${editUrl.pathname}${editUrl.search}`;
      }
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
    if (commentPagination) {
      commentPagination.hidden = true;
      commentPagination.replaceChildren();
    }
    if (commentWriteShortcut) commentWriteShortcut.hidden = true;
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
    link.href = listReturnPath || "/board";
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
      const payload = await Api.get(path, { cache: "no-store" });
      updateCachedPostViewCount(postId, payload.data?.viewCount);
      writeBoardCache(path, payload.data);
      renderPost(payload.data);
    } catch (error) {
      if (!cached?.data || !canUseCacheAfterError(error)) throw error;
      const cachedViewCount = Number(cached.data.viewCount) || 0;
      renderPost({
        ...cached.data,
        viewCount: cachedViewCount + 1,
      });
      showCachedFallbackNoticeOnce(
        "최신 내용을 불러오지 못해 잠시 저장된 게시글을 보여드리고 있습니다.",
      );
    }
  }

  function updateLikeUi({ liked, likeCount }) {
    if (!state.post) return;
    const parsedLikeCount = Number(likeCount);
    const nextLikeCount = Number.isFinite(parsedLikeCount)
      ? Math.max(0, parsedLikeCount)
      : Number(state.post.likeCount) || 0;
    state.post = {
      ...state.post,
      likedByCurrentUser: Boolean(liked),
      likeCount: nextLikeCount,
    };

    const likeMeta = detailContent.querySelector('[data-like-count="true"]');
    if (likeMeta) likeMeta.textContent = `추천 ${state.post.likeCount}`;

    if (activeLikeButton) {
      activeLikeButton.textContent =
        `${state.post.likedByCurrentUser ? "추천 취소" : "추천"} · ${state.post.likeCount}`;
      activeLikeButton.classList.toggle(
        "button-primary",
        state.post.likedByCurrentUser,
      );
      activeLikeButton.classList.toggle(
        "button-secondary",
        !state.post.likedByCurrentUser,
      );
    }
  }

  async function toggleLike() {
    if (!session.authenticated) {
      openDetailLogin({
        successMessage: "로그인되었습니다. 추천을 이어갑니다.",
        onSuccess: () => toggleLike(),
      });
      return;
    }
    if (likeInFlight) return;

    likeInFlight = true;
    if (activeLikeButton) activeLikeButton.disabled = true;
    try {
      const payload = state.post.likedByCurrentUser
        ? await Api.delete(`/board/posts/${postId}/like`)
        : await Api.post(`/board/posts/${postId}/like`, {});
      invalidateBoardCache();
      updateLikeUi({
        liked: payload.data.liked,
        likeCount: payload.data.likeCount,
      });
      showToast(toast, payload.message);
    } catch (error) {
      showToast(toast, error.message, true);
    } finally {
      likeInFlight = false;
      if (activeLikeButton) activeLikeButton.disabled = false;
    }
  }

  async function unpinNotice(event) {
    const post = state.post;
    if (!post || !isPinnedPost(post) || !session.isAdmin || noticeUnpinInFlight) return;

    const category = await chooseNoticeCategory({
      title: "공지를 어디로 내릴까요?",
      message: "공지에서 내린 뒤 사용할 카테고리를 선택해 주세요.",
      confirmLabel: "공지에서 내리기",
      initialCategory: post.category,
    });
    if (!category) return;

    noticeUnpinInFlight = true;
    const button = event?.currentTarget instanceof HTMLButtonElement
      ? event.currentTarget
      : null;
    if (button) button.disabled = true;

    try {
      const params = new URLSearchParams({ category });
      const payload = await Api.patch(
        `/board/posts/${postId}/unpin?${params.toString()}`,
        {},
        { cache: "no-store" },
      );
      invalidateBoardCache({ global: true });
      renderPost({
        ...post,
        ...(payload.data || {}),
        category,
        pinned: false,
      });
      showToast(toast, payload.message || "공지를 내렸습니다.");
    } catch (error) {
      showToast(toast, error.message || "공지를 내리지 못했습니다.", true);
    } finally {
      noticeUnpinInFlight = false;
      if (button?.isConnected) button.disabled = false;
    }
  }

  async function pinNotice(event) {
    const post = state.post;
    if (!post || isPinnedPost(post) || !session.isAdmin || noticePinInFlight) return;

    const category = await chooseNoticeCategory({
      title: "이 글을 공지로 올릴까요?",
      message: "공지로 올린 뒤 표시할 카테고리를 선택해 주세요.",
      confirmLabel: "공지로 올리기",
      initialCategory: post.category,
    });
    if (!category) return;

    noticePinInFlight = true;
    const button = event?.currentTarget instanceof HTMLButtonElement ? event.currentTarget : null;
    if (button) button.disabled = true;
    try {
      const params = new URLSearchParams({ category });
      const payload = await Api.patch(
        `/board/posts/${postId}/pin?${params.toString()}`,
        {},
        { cache: "no-store" },
      );
      invalidateBoardCache({ global: true });
      renderPost({
        ...post,
        ...(payload.data || {}),
        category,
        pinned: true,
      });
      showToast(toast, payload.message || "공지로 올렸습니다.");
    } catch (error) {
      showToast(toast, error.message || "공지로 올리지 못했습니다.", true);
    } finally {
      noticePinInFlight = false;
      if (button?.isConnected) button.disabled = false;
    }
  }

  async function deletePost(event) {
    if (postDeleteInFlight) return;

    postDeleteInFlight = true;
    const deleteButton = event?.currentTarget instanceof HTMLButtonElement
      ? event.currentTarget
      : null;
    if (deleteButton) deleteButton.disabled = true;
    let navigationStarted = false;

    try {
      if (!(await confirmDetailPageLeave())) return;

      const newsPost = isNewsPost();
      const deletePath = newsPost ? newsDeletePath(state.post) : `/board/posts/${postId}`;
      const returnPath = newsPost
        ? restaurantNewsPath(state.post)
        : communityReturnPath(state.post);
      if (!deletePath || !returnPath) {
        showToast(toast, "가게 소식의 식당 정보를 확인할 수 없습니다.", true);
        return;
      }
      const message = newsPost
        ? "이 가게 소식과 연결된 댓글과 추천도 함께 삭제됩니다."
        : "이 게시글과 연결된 댓글과 추천도 함께 삭제됩니다.";
      const confirmed = await confirmBoardAction({
        title: newsPost ? "가게 소식을 삭제할까요?" : "게시글을 삭제할까요?",
        message,
        confirmLabel: newsPost ? "가게 소식 삭제" : "게시글 삭제",
      });
      if (!confirmed) return;

      const payload = await Api.delete(deletePath);
      invalidateBoardCache();
      if (newsPost) {
        // 가게 소식은 게시판 밖 식당 화면으로 돌아가므로 기존 완료 안내를 유지한다.
        window.alert(payload.message);
      } else {
        try {
          window.sessionStorage.setItem(
            BOARD_FLASH_KEY,
            payload.message || "게시글이 삭제되었습니다.",
          );
        } catch (_error) {
          // 저장 공간을 사용할 수 없어도 삭제 완료 후 이동은 계속한다.
        }
      }
      allowDetailNavigation = true;
      navigationStarted = true;
      window.location.assign(returnPath);
    } catch (error) {
      showToast(toast, error.message, true);
    } finally {
      if (!navigationStarted) {
        postDeleteInFlight = false;
        if (deleteButton?.isConnected) deleteButton.disabled = false;
      }
    }
  }

  function replyComposerHasDraft(form = activeReplyForm) {
    if (!form) return false;
    const textarea = form.querySelector(".comment-reply-textarea");
    const initialValue = form.dataset.initialValue || "";
    const hasText = Boolean(textarea && textarea.value.trim() !== initialValue.trim());
    const preview = form.querySelector(".comment-image-preview");
    const hasImage = Boolean(preview && !preview.hidden);
    return hasText || hasImage;
  }

  function closeReplyComposer() {
    if (activeReplyPreviewUrl) {
      URL.revokeObjectURL(activeReplyPreviewUrl);
      activeReplyPreviewUrl = null;
    }
    activeReplyForm?.remove();
    activeReplyForm = null;
  }

  async function confirmReplyComposerDiscard(nextCommentId) {
    if (!activeReplyForm) return true;
    if (String(activeReplyForm.dataset.replyCommentId || "") === String(nextCommentId || "")) {
      activeReplyForm.querySelector(".comment-reply-textarea")?.focus({ preventScroll: true });
      return false;
    }
    if (!replyComposerHasDraft(activeReplyForm)) {
      closeReplyComposer();
      return true;
    }
    if (replyDiscardPromptOpen) return false;

    const currentForm = activeReplyForm;
    replyDiscardPromptOpen = true;
    try {
      const discard = await confirmBoardAction({
        title: "작성 중인 답글을 버릴까요?",
        message: "입력한 내용과 첨부한 사진은 저장되지 않습니다.",
        confirmLabel: "내용 버리기",
        danger: false,
        iconName: "edit",
      });
      if (!discard || activeReplyForm !== currentForm) return false;
      closeReplyComposer();
      return true;
    } finally {
      replyDiscardPromptOpen = false;
    }
  }

  function closeCommentEditor() {
    if (!activeCommentEditForm) return;
    const item = activeCommentEditForm.closest(".comment-item");
    const content = item?.querySelector(":scope > .comment-content");
    const actions = item?.querySelector(":scope > .comment-actions");
    if (content) content.hidden = false;
    if (actions) actions.hidden = false;
    activeCommentEditForm.remove();
    activeCommentEditForm = null;
  }

  async function confirmCommentEditorDiscard(nextCommentId = null) {
    if (!activeCommentEditForm) return true;
    if (
      nextCommentId != null &&
      String(activeCommentEditForm.dataset.editCommentId || "") === String(nextCommentId)
    ) {
      activeCommentEditForm
        .querySelector(".comment-edit-textarea")
        ?.focus({ preventScroll: true });
      return false;
    }
    if (!commentEditorHasDraft(activeCommentEditForm)) {
      closeCommentEditor();
      return true;
    }
    if (commentEditDiscardPromptOpen) return false;

    const currentForm = activeCommentEditForm;
    commentEditDiscardPromptOpen = true;
    try {
      const discard = await confirmBoardAction({
        title: "수정 중인 댓글을 버릴까요?",
        message: "바꾼 내용은 저장되지 않습니다.",
        confirmLabel: "내용 버리기",
        danger: false,
        iconName: "edit",
      });
      if (!discard || activeCommentEditForm !== currentForm) return false;
      closeCommentEditor();
      return true;
    } finally {
      commentEditDiscardPromptOpen = false;
    }
  }

  async function confirmInlineCommentDraftDiscard() {
    if (!(await confirmReplyComposerDiscard(null))) return false;
    if (!(await confirmCommentEditorDiscard(null))) return false;
    return true;
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

  async function openReplyComposer(comment, mountTarget) {
    if (!session.authenticated) {
      openDetailLogin({
        successMessage: "로그인되었습니다. 답글 작성을 이어갑니다.",
        onSuccess: () => openReplyComposer(comment, mountTarget),
      });
      return;
    }
    if (!(await confirmCommentEditorDiscard(null))) return;
    if (!(await confirmReplyComposerDiscard(comment.commentId))) return;

    const rootParentId = comment.parentCommentId || comment.commentId;
    const targetName = replyTargetName(comment);
    let selectedReplyImage = null;

    const form = element("form", "comment-reply-form");
    form.dataset.replyCommentId = String(comment.commentId);
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
    form.dataset.initialValue = textarea.value;

    const inputMeta = element("div", "comment-input-meta comment-input-meta--compact comment-input-meta--footer");
    const characterCount = element("span", "comment-character-count");
    inputMeta.append(characterCount);
    updateCharacterCount(textarea, characterCount);
    resizeCommentTextarea(textarea, 78);

    const tools = element("div", "comment-image-tools");
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.className = "sr-only";
    fileInput.accept = ".jpg,.jpeg,.png,.gif,.webp,image/jpeg,image/png,image/gif,image/webp";
    const imageButton = element("button", "comment-image-select", "사진 첨부");
    imageButton.type = "button";
    const emojiButton = element("button", "comment-emoji-toggle", "🐸 이모지");
    emojiButton.type = "button";
    const emojiPanel = element("div", "comment-emoji-panel");
    emojiPanel.hidden = true;
    emojiPanel.setAttribute("role", "group");
    emojiPanel.setAttribute("aria-label", "이모지 선택");
    const emojiPanelId = `comment-reply-emoji-${comment.commentId}`;
    emojiPanel.id = emojiPanelId;
    emojiButton.setAttribute("aria-controls", emojiPanelId);
    emojiButton.setAttribute("aria-expanded", "false");
    const imageNote = element("span", "", "사진 1장 · 최대 5MB");
    tools.append(fileInput, imageButton, emojiButton, imageNote);

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
    const submitTools = element("div", "comment-submit-tools");
    submitTools.append(tools, inputMeta);
    const submitActions = element("div", "comment-reply-actions");
    const cancel = element("button", "comment-action", "취소");
    cancel.type = "button";
    const submit = element("button", "button button-sm button-primary", "답글 등록");
    submit.type = "submit";
    submit.disabled = true;
    submitActions.append(cancel, submit);
    submitRow.append(submitTools, submitActions);

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
    cancel.addEventListener("click", async () => {
      await confirmReplyComposerDiscard(null);
    });
    textarea.addEventListener("input", () => {
      syncReplySubmitState();
      updateCharacterCount(textarea, characterCount);
      resizeCommentTextarea(textarea, 78);
    });
    textarea.addEventListener("keydown", (event) => {
      if (!isCommentSubmitEnter(event)) return;
      event.preventDefault();
      if (submit.disabled || !hasReplyBody(textarea.value, targetName)) return;
      form.requestSubmit();
    });

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
      emojiButton.disabled = true;
      closeEmojiPicker();
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
          showCommentImageRetryNotice(
            createdCommentId,
            imageFile,
            imageUploadError,
            { label: "답글" },
          );
        } else {
          showToast(
            toast,
            imageFile ? "답글과 사진이 등록되었습니다." : "답글이 등록되었습니다.",
          );
        }
        expandedReplyThreadIds.add(String(rootParentId));
        await loadCommentPage(currentCommentPage, {
          highlightCommentId: createdCommentId,
          scrollToHighlight: true,
        });
      } catch (error) {
        showToast(toast, error.message, true);
      } finally {
        syncReplySubmitState();
        imageButton.disabled = false;
        emojiButton.disabled = false;
      }
    });

    form.append(label, textarea, emojiPanel, preview, submitRow);
    setupCommentEmojiPicker(textarea, emojiButton, emojiPanel);
    mountTarget.append(form);
    activeReplyForm = form;
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  function shouldIgnoreCommentAreaReplyClick(event, item) {
    const target = event.target;
    if (!(target instanceof Element)) return true;

    if (target.closest(
      "button, a, input, textarea, select, label, [role='button'], .comment-actions, .comment-reply-form, .comment-edit-form",
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
      authorIdentity(comment, {
        showAuthorMenu: true,
        authorMenuContext: isNewsPost() ? "NEWS" : "COMMUNITY",
      }),
      element(
        "span",
        "comment-date",
        `${formatDate(comment.createdAt)}${isEdited(comment) ? " · 수정됨" : ""}`,
      ),
    );
    item.append(top, emojiTextElement("div", "comment-content", comment.content));

    const image = commentImageNode(comment);
    if (image) item.append(image);

    const actions = element("div", "comment-actions");
    actions.append(
      actionButton("답글", "comment-action", () => openReplyComposer(comment, item)),
    );
    if (comment.ownedByCurrentUser || session.isAdmin) {
      actions.append(
        actionButton("수정", "comment-action", () => editComment(comment, item)),
        actionButton(
          "삭제",
          "comment-action",
          (event) => deleteComment(comment, hasReplies, event.currentTarget),
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

  function appendCommentThreads(comments) {
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
        const threadId = String(comment.commentId);
        const thread = element("section", "comment-thread");
        thread.append(renderCommentItem(comment, { hasReplies: replies.length > 0 }));

        if (replies.length) {
          const replyListId = `comment-replies-${comment.commentId}`;
          const isExpanded = expandedReplyThreadIds.has(threadId);
          const replyToggle = element(
            "button",
            "comment-replies-toggle",
            isExpanded ? `답글 ${replies.length}개 숨기기` : `답글 ${replies.length}개 보기`,
          );
          replyToggle.type = "button";
          replyToggle.setAttribute("aria-controls", replyListId);
          replyToggle.setAttribute("aria-expanded", String(isExpanded));

          const replyList = element("div", "comment-replies");
          replyList.id = replyListId;
          replyList.hidden = !isExpanded;
          replies.forEach((reply) => {
            replyList.append(renderCommentItem(reply, { isReply: true }));
          });

          replyToggle.addEventListener("click", () => {
            const willExpand = replyList.hidden;
            replyList.hidden = !willExpand;
            replyToggle.setAttribute("aria-expanded", String(willExpand));
            replyToggle.textContent = willExpand
              ? `답글 ${replies.length}개 숨기기`
              : `답글 ${replies.length}개 보기`;
            if (willExpand) {
              expandedReplyThreadIds.add(threadId);
            } else {
              expandedReplyThreadIds.delete(threadId);
            }
          });

          thread.append(replyToggle, replyList);
        }
        commentList.append(thread);
      });
  }

  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function commentPageTokens(page, totalPages) {
    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, index) => index);
    }

    const pages = new Set([0, totalPages - 1]);
    for (let offset = -2; offset <= 2; offset += 1) {
      const candidate = page + offset;
      if (candidate >= 0 && candidate < totalPages) pages.add(candidate);
    }

    const sorted = [...pages].sort((a, b) => a - b);
    const tokens = [];
    sorted.forEach((value, index) => {
      if (index > 0 && value - sorted[index - 1] > 1) tokens.push("ellipsis");
      tokens.push(value);
    });
    return tokens;
  }

  function setCommentPaginationBusy(busy) {
    if (!commentPagination) return;
    commentPagination.setAttribute("aria-busy", busy ? "true" : "false");
    commentPagination.querySelectorAll("button").forEach((button) => {
      button.disabled = busy;
    });
  }

  function renderCommentPagination(pageData = null) {
    if (!commentPagination) return;

    if (pageData) {
      currentCommentPage = Math.max(0, Number(pageData.page) || 0);
      totalCommentPages = Math.max(0, Number(pageData.totalPages) || 0);
    }

    commentPagination.replaceChildren();
    if (totalCommentPages <= 1) {
      commentPagination.hidden = true;
      return;
    }

    const makeButton = (label, page, options = {}) => {
      const button = element(
        "button",
        options.direction
          ? "comment-page-button comment-page-button--direction"
          : "comment-page-button",
        label,
      );
      button.type = "button";
      button.dataset.page = String(page);
      if (options.current) {
        button.classList.add("is-current");
        button.setAttribute("aria-current", "page");
      }
      if (options.label) button.setAttribute("aria-label", options.label);
      if (options.disabled || options.current) button.disabled = true;
      button.addEventListener("click", () => goToCommentPage(page));
      return button;
    };

    const previous = makeButton("이전", currentCommentPage - 1, {
      direction: true,
      disabled: currentCommentPage <= 0,
      label: "이전 댓글 페이지",
    });
    commentPagination.append(previous);

    commentPageTokens(currentCommentPage, totalCommentPages).forEach((token) => {
      if (token === "ellipsis") {
        const ellipsis = element("span", "comment-page-ellipsis", "…");
        ellipsis.setAttribute("aria-hidden", "true");
        commentPagination.append(ellipsis);
        return;
      }
      commentPagination.append(
        makeButton(String(token + 1), token, {
          current: token === currentCommentPage,
          label: `${token + 1}번째 댓글 페이지`,
        }),
      );
    });

    const next = makeButton("다음", currentCommentPage + 1, {
      direction: true,
      disabled: currentCommentPage >= totalCommentPages - 1,
      label: "다음 댓글 페이지",
    });
    commentPagination.append(next);
    commentPagination.hidden = false;
    setCommentPaginationBusy(commentPageLoading);
  }

  function announceCommentStatus(message) {
    if (!commentLoadStatus) return;
    commentLoadStatus.textContent = "";
    window.requestAnimationFrame(() => {
      commentLoadStatus.textContent = message || "";
    });
  }

  function scrollToCommentHeading() {
    const heading = document.getElementById("comment-heading");
    const target = heading?.closest(".comment-heading-row") || heading;
    target?.scrollIntoView({
      behavior: prefersReducedMotion() ? "auto" : "smooth",
      block: "start",
    });
  }

  function highlightComment(commentId, options = {}) {
    if (!commentId) return;
    window.requestAnimationFrame(() => {
      const target = document.getElementById(`comment-${commentId}`);
      if (!target) return;
      target.classList.remove("comment-item--new");
      void target.offsetWidth;
      target.classList.add("comment-item--new");
      if (options.scroll !== false) {
        target.scrollIntoView({
          behavior: prefersReducedMotion() ? "auto" : "smooth",
          block: "center",
        });
      }
      window.setTimeout(() => target.classList.remove("comment-item--new"), 1800);
    });
  }

  function renderCommentLoadError(error, page = currentCommentPage) {
    closeReplyComposer();
    closeCommentEditor();
    commentList.replaceChildren();
    if (commentPagination) {
      commentPagination.hidden = true;
      commentPagination.replaceChildren();
    }
    commentCount.textContent = String(state.post?.commentCount ?? "-");

    const wrapper = element("div", "comment-load-error");
    wrapper.append(
      element("strong", "", "댓글을 불러오지 못했습니다."),
      element(
        "span",
        "",
        error?.message || "잠시 후 다시 시도해 주세요.",
      ),
    );
    const retry = element(
      "button",
      "button button-sm button-secondary",
      "다시 시도",
    );
    retry.type = "button";
    retry.addEventListener("click", async () => {
      retry.disabled = true;
      try {
        await loadCommentPage(page);
      } catch (retryError) {
        renderCommentLoadError(retryError, page);
      }
    });
    wrapper.append(retry);
    commentList.append(wrapper);
    announceCommentStatus("댓글을 불러오지 못했습니다. 다시 시도할 수 있습니다.");
  }

  function renderComments(pageData, options = {}) {
    const comments = pageData.content || [];

    currentCommentPage = Math.max(0, Number(pageData.page) || 0);
    totalCommentPages = Math.max(0, Number(pageData.totalPages) || 0);
    commentCount.textContent = String(
      pageData.totalCommentCount ?? pageData.totalElements ?? 0,
    );

    closeReplyComposer();
    closeCommentEditor();
    commentList.replaceChildren();

    if (!comments.length) {
      commentList.append(
        element("p", "comment-empty", "첫 댓글을 함께 남겨 보세요."),
      );
    } else {
      appendCommentThreads(comments);
    }

    renderCommentPagination(pageData);
    if (options.highlightCommentId) {
      highlightComment(options.highlightCommentId, {
        scroll: options.scrollToHighlight !== false,
      });
    }
  }

  async function fetchCommentPage(page, options = {}) {
    const normalizedPage = Math.max(0, Number(page) || 0);
    const forceRefresh = options.forceRefresh === true;
    const path = `/board/posts/${postId}/comments?page=${normalizedPage}&size=${COMMENT_PAGE_SIZE}`;
    const cached = readBoardCache(path);
    if (cached?.fresh && !forceRefresh) return cached.data || {};

    try {
      const payload = await Api.get(path);
      const pageData = payload.data || {};
      writeBoardCache(path, pageData);
      return pageData;
    } catch (error) {
      if (cached && canUseCacheAfterError(error)) {
        showCachedFallbackNoticeOnce(
          "최신 댓글을 불러오지 못해 잠시 저장된 댓글을 보여드리고 있습니다.",
        );
        announceCommentStatus(
          "최신 댓글을 불러오지 못해 저장된 댓글을 표시했습니다.",
        );
        return cached.data || {};
      }
      throw error;
    }
  }

  async function loadCommentPage(page = currentCommentPage, options = {}) {
    if (commentPageLoading) return null;

    commentPageLoading = true;
    setCommentPaginationBusy(true);
    commentList.setAttribute("aria-busy", "true");
    try {
      let requestedPage = Math.max(0, Number(page) || 0);
      let pageData = await fetchCommentPage(requestedPage, {
        forceRefresh: options.forceRefresh === true,
      });
      const pageCount = Math.max(0, Number(pageData.totalPages) || 0);

      if (pageCount > 0 && requestedPage >= pageCount) {
        requestedPage = pageCount - 1;
        pageData = await fetchCommentPage(requestedPage, {
          forceRefresh: options.forceRefresh === true,
        });
      }

      renderComments(pageData, options);
      if (options.announce) announceCommentStatus(options.announce);
      if (options.scrollToHeading) scrollToCommentHeading();
      return pageData;
    } finally {
      commentPageLoading = false;
      commentList.setAttribute("aria-busy", "false");
      renderCommentPagination();
    }
  }

  async function goToCommentPage(page) {
    const targetPage = Math.max(0, Math.min(Number(page) || 0, totalCommentPages - 1));
    if (commentPageLoading || targetPage === currentCommentPage) return;
    if (!(await confirmInlineCommentDraftDiscard())) return;

    try {
      await loadCommentPage(targetPage, {
        announce: `댓글 ${targetPage + 1}페이지를 불러왔습니다.`,
        scrollToHeading: true,
      });
    } catch (error) {
      showToast(toast, error.message, true);
    }
  }

  async function loadNewestRootComment(commentId) {
    if (commentPageLoading) return;

    commentPageLoading = true;
    setCommentPaginationBusy(true);
    commentList.setAttribute("aria-busy", "true");
    try {
      const firstPage = await fetchCommentPage(0);
      const pageCount = Math.max(0, Number(firstPage.totalPages) || 0);
      const lastPage = Math.max(0, pageCount - 1);
      const pageData = lastPage === 0 ? firstPage : await fetchCommentPage(lastPage);
      renderComments(pageData, {
        highlightCommentId: commentId,
        scrollToHighlight: true,
      });
      announceCommentStatus(
        pageCount > 1
          ? `댓글이 등록되어 마지막 ${lastPage + 1}페이지로 이동했습니다.`
          : "댓글이 등록되었습니다.",
      );
    } finally {
      commentPageLoading = false;
      commentList.setAttribute("aria-busy", "false");
      renderCommentPagination();
    }
  }

  async function editComment(comment, item) {
    if (!item) return;
    if (!(await confirmReplyComposerDiscard(null))) return;
    if (!(await confirmCommentEditorDiscard(comment.commentId))) return;

    const contentNode = item.querySelector(":scope > .comment-content");
    const actionsNode = item.querySelector(":scope > .comment-actions");
    const dateNode = item.querySelector(":scope > .comment-top .comment-date");
    if (!contentNode || !actionsNode) return;

    const form = element("form", "comment-edit-form");
    const textarea = document.createElement("textarea");
    const inputId = `comment-edit-${comment.commentId}`;
    textarea.id = inputId;
    textarea.className = "comment-edit-textarea";
    textarea.maxLength = 1000;
    textarea.rows = 3;
    textarea.value = comment.content || "";
    textarea.setAttribute("aria-label", "댓글 수정 내용");
    form.dataset.editCommentId = String(comment.commentId);
    form.dataset.initialValue = textarea.value;

    const inputMeta = element("div", "comment-input-meta comment-input-meta--compact");
    inputMeta.append(element("span", "", "Enter로 수정 · Shift + Enter로 줄바꿈"));
    const characterCount = element("span", "comment-character-count");
    inputMeta.append(characterCount);
    updateCharacterCount(textarea, characterCount);

    const actions = element("div", "comment-edit-actions");
    const cancel = element("button", "button button-sm button-secondary", "취소");
    cancel.type = "button";
    const save = element("button", "button button-sm button-primary", "수정 완료");
    save.type = "submit";
    actions.append(cancel, save);
    form.append(textarea, inputMeta, actions);

    const syncEditState = () => {
      const value = textarea.value.trim();
      const original = String(comment.content || "").trim();
      save.disabled = !value || value === original;
      updateCharacterCount(textarea, characterCount);
      resizeCommentTextarea(textarea, 86);
    };

    contentNode.hidden = true;
    actionsNode.hidden = true;
    contentNode.after(form);
    activeCommentEditForm = form;
    syncEditState();

    form.addEventListener("click", (event) => event.stopPropagation());
    cancel.addEventListener("click", async () => {
      await confirmCommentEditorDiscard(null);
    });
    textarea.addEventListener("input", syncEditState);
    textarea.addEventListener("keydown", async (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        await confirmCommentEditorDiscard(null);
        return;
      }
      if (!isCommentSubmitEnter(event)) return;
      event.preventDefault();
      if (save.disabled) return;
      form.requestSubmit();
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const content = textarea.value.trim();
      if (!content) {
        showToast(toast, "댓글 내용을 입력해 주세요.", true);
        return;
      }
      if (content === String(comment.content || "").trim()) {
        closeCommentEditor();
        return;
      }

      save.disabled = true;
      cancel.disabled = true;
      textarea.disabled = true;
      try {
        const payload = await Api.put(`/board/comments/${comment.commentId}`, { content });
        invalidateBoardCache();
        const updated = payload.data || {};
        Object.assign(comment, updated, {
          content: updated.content ?? content,
          edited: updated.edited ?? true,
        });
        if (emojis) emojis.renderText(contentNode, comment.content);
        else contentNode.textContent = comment.content;
        if (dateNode) {
          dateNode.textContent = `${formatDate(comment.createdAt)} · 수정됨`;
        }
        closeCommentEditor();
        showToast(toast, payload.message || "댓글이 수정되었습니다.");
      } catch (error) {
        showToast(toast, error.message, true);
        save.disabled = false;
        cancel.disabled = false;
        textarea.disabled = false;
        syncEditState();
      }
    });

    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  async function deleteComment(comment, hasReplies = false, deleteButton = null) {
    const commentId = Number(comment?.commentId);
    if (!Number.isFinite(commentId) || commentDeleteInFlight.has(commentId)) return;

    commentDeleteInFlight.add(commentId);
    if (deleteButton instanceof HTMLButtonElement) deleteButton.disabled = true;
    try {
      const confirmed = await confirmBoardAction({
        title: "댓글을 삭제할까요?",
        message: hasReplies
          ? "이 댓글에 달린 답글도 함께 삭제되며, 삭제한 내용은 되돌릴 수 없습니다."
          : "삭제한 댓글은 되돌릴 수 없습니다.",
        confirmLabel: "댓글 삭제",
      });
      if (!confirmed) return;
      if (!(await confirmInlineCommentDraftDiscard())) return;

      const payload = await Api.delete(`/board/comments/${commentId}`);
      invalidateBoardCache();
      showToast(toast, payload.message);
      await loadCommentPage(currentCommentPage);
    } catch (error) {
      showToast(toast, error.message, true);
    } finally {
      commentDeleteInFlight.delete(commentId);
      if (deleteButton?.isConnected) deleteButton.disabled = false;
    }
  }

  setupCommentEmojiPicker(commentContent, commentEmojiToggle, commentEmojiPanel);

  document.addEventListener("click", () => closeEmojiPicker());
  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape" || !activeEmojiPicker) return;
    const toggle = activeEmojiPicker.toggle;
    closeEmojiPicker();
    toggle?.focus({ preventScroll: true });
  });

  commentImageSelect?.addEventListener("click", () => {
    commentImageInput?.click();
  });

  commentImageInput?.addEventListener("change", () => {
    selectCommentImage(commentImageInput.files?.[0] || null);
  });

  commentImageRemove?.addEventListener("click", () => {
    clearCommentImageSelection();
  });

  updateCharacterCount(commentContent, commentCharacterCount);
  resizeCommentTextarea(commentContent, 105);
  commentContent.addEventListener("input", () => {
    updateCharacterCount(commentContent, commentCharacterCount);
    resizeCommentTextarea(commentContent, 105);
  });

  commentWriteShortcut?.addEventListener("click", () => {
    if (commentForm.hidden) return;
    commentForm.scrollIntoView({
      behavior: prefersReducedMotion() ? "auto" : "smooth",
      block: "center",
    });
    commentContent.focus({ preventScroll: true });
  });

  commentContent.addEventListener("keydown", (event) => {
    if (!isCommentSubmitEnter(event)) return;
    event.preventDefault();
    if (commentSubmitButton?.disabled || !commentContent.value.trim()) return;
    commentForm.requestSubmit();
  });

  commentForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!session.authenticated) {
      if (completeDetailLoginIfReady()) {
        window.setTimeout(() => commentForm.requestSubmit(), 0);
      } else {
        openDetailLogin({
          successMessage: "로그인되었습니다. 작성 중인 댓글을 등록합니다.",
          onSuccess: () => commentForm.requestSubmit(),
        });
      }
      return;
    }
    const content = commentContent.value.trim();
    if (!content) {
      showToast(toast, "댓글 내용을 입력해 주세요.", true);
      return;
    }

    const imageFile = selectedCommentImage;
    if (commentSubmitButton) commentSubmitButton.disabled = true;
    if (commentImageSelect) commentImageSelect.disabled = true;
    if (commentEmojiToggle) commentEmojiToggle.disabled = true;
    closeEmojiPicker();
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
      emojis?.refreshEditor?.(commentContent);
      updateCharacterCount(commentContent, commentCharacterCount);
      resizeCommentTextarea(commentContent, 105);
      clearCommentImageSelection();
      if (imageUploadError) {
        showToast(
          toast,
          `댓글은 등록됐지만 사진 업로드에 실패했습니다. ${imageUploadError.message}`,
          true,
        );
        showCommentImageRetryNotice(createdCommentId, imageFile, imageUploadError);
      } else {
        showToast(
          toast,
          imageFile ? "댓글과 사진이 등록되었습니다." : payload.message,
        );
      }
      await loadNewestRootComment(createdCommentId);
    } catch (error) {
      showToast(toast, error.message, true);
    } finally {
      if (commentSubmitButton) commentSubmitButton.disabled = false;
      if (commentImageSelect) commentImageSelect.disabled = false;
      if (commentEmojiToggle) commentEmojiToggle.disabled = false;
    }
  });

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
      const visible = window.scrollY > 450;
      button.hidden = !visible;
      document.body.classList.toggle("board-scroll-top-visible", visible);
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
      if (commentWriteShortcut) commentWriteShortcut.hidden = true;
      return;
    }

    const nicknameTask = hydrateSessionNickname();
    try {
      await loadPost();
      await nicknameTask;
    } catch (error) {
      renderPostError(error.message);
      commentForm.hidden = true;
      if (commentWriteShortcut) commentWriteShortcut.hidden = true;
      return;
    }

    const commentTask = loadCommentPage(0).catch((error) => {
      renderCommentLoadError(error, 0);
      return null;
    });
    const relatedTask = isNewsPost()
      ? Promise.resolve(renderRelatedPosts([]))
      : loadRelatedPosts();
    await Promise.all([commentTask, relatedTask, loadUnansweredPosts()]);
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

  window.addEventListener("beforeunload", (event) => {
    if (allowDetailNavigation || !hasUnsavedDetailDrafts()) return;
    event.preventDefault();
    // 최신 브라우저는 사용자 지정 문구 대신 자체 경고문을 표시한다.
    event.returnValue = true;
  });

  window.addEventListener("pagehide", () => {
    mediaPollingDisposed = true;
    clearMediaPoll();
    clearCommentImageSelection();
    clearCommentImageRetryNotice();
    detailAuthPopupController?.stop({ closePopup: true });
    pendingDetailLoginAction = null;
    closeReplyComposer();
    document.querySelector(".detail-image-viewer")?.remove();
    document.body.classList.remove("is-image-viewer-open");
  });

  window.addEventListener("pageshow", (event) => {
    mediaPollingDisposed = false;
    if (Array.isArray(state.post?.media) &&
        state.post.media.some(isProcessingMedia)) {
      scheduleMediaPoll(0);
    }

    if (!event.persisted || !state.post) return;

    loadCommentPage(currentCommentPage, { forceRefresh: true }).catch((error) => {
      if (!canUseCacheAfterError(error)) {
        renderCommentLoadError(error, currentCommentPage);
        return;
      }
      showCachedFallbackNoticeOnce(
        "최신 게시판 정보를 확인하지 못해 현재 화면을 그대로 유지했습니다.",
      );
    });
    if (!isNewsPost()) {
      void loadRelatedPosts({ forceRefresh: true, preserveOnError: true });
    }
    void loadUnansweredPosts({ forceRefresh: true, preserveOnError: true });
  });

  initializeDetailNavigationGuard();
  initializeDetailLoginEntryPoints();
  initializeDetailLogoutEntryPoint();
  initializeScrollTopButton();
  loadPage();
})();
