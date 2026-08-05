(() => {
  const categoryLabels = {
    GENERAL: "자유 이야기",
    NOTICE: "공지",
    RECOMMENDATION: "맛집 추천",
    REVIEW: "방문 후기",
    QUESTION: "질문",
    TRAVEL: "맛집 여행",
  };

  const roleLabels = {
    USER: "일반 회원",
    BUSINESS: "사업자",
    ADMIN: "관리자",
  };

  let authorMenu = null;
  let activeAuthorTrigger = null;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function icon(name) {
    const node = element("span", "material-symbols-rounded", name);
    node.setAttribute("aria-hidden", "true");
    return node;
  }

  function formatDate(value) {
    if (!value) return "날짜 정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  }

  function categoryLabel(value) {
    return categoryLabels[value] || value || "카테고리 없음";
  }

  function roleLabel(value) {
    return roleLabels[value] || roleLabels.USER;
  }

  function roleClass(value) {
    const normalized = roleLabels[value] ? value.toLowerCase() : "user";
    return `post-role post-role--${normalized}`;
  }

  function closeAuthorMenu() {
    if (!authorMenu || authorMenu.hidden) return;
    authorMenu.hidden = true;
    authorMenu.replaceChildren();
    if (activeAuthorTrigger) {
      activeAuthorTrigger.setAttribute("aria-expanded", "false");
    }
    activeAuthorTrigger = null;
  }

  function ensureAuthorMenu() {
    if (authorMenu) return authorMenu;

    authorMenu = element("section", "author-menu");
    authorMenu.id = "board-author-menu";
    authorMenu.hidden = true;
    authorMenu.setAttribute("role", "dialog");
    authorMenu.setAttribute("aria-label", "작성자 활동 메뉴");
    document.body.append(authorMenu);

    document.addEventListener("click", (event) => {
      if (authorMenu.hidden) return;
      if (authorMenu.contains(event.target)) return;
      if (activeAuthorTrigger?.contains(event.target)) return;
      closeAuthorMenu();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") closeAuthorMenu();
    });
    window.addEventListener("resize", closeAuthorMenu);
    window.addEventListener("scroll", closeAuthorMenu, true);
    return authorMenu;
  }

  function positionAuthorMenu(trigger) {
    if (!authorMenu || authorMenu.hidden) return;
    const rect = trigger.getBoundingClientRect();
    const gap = 8;
    const viewportPadding = 12;
    const menuWidth = authorMenu.offsetWidth;
    const menuHeight = authorMenu.offsetHeight;
    const viewportBottom = window.scrollY + window.innerHeight;
    const preferredTop = window.scrollY + rect.bottom + gap;
    const alternateTop = window.scrollY + rect.top - menuHeight - gap;
    const top = preferredTop + menuHeight <= viewportBottom - viewportPadding
      ? preferredTop
      : Math.max(window.scrollY + viewportPadding, alternateTop);
    const left = Math.min(
      Math.max(window.scrollX + viewportPadding, window.scrollX + rect.left),
      window.scrollX + window.innerWidth - menuWidth - viewportPadding,
    );
    authorMenu.style.top = `${top}px`;
    authorMenu.style.left = `${left}px`;
  }

  function authorMenuStat(label, count) {
    const item = element("div", "author-menu-stat");
    item.append(
      element("span", "", label),
      element("strong", "", String(count || 0)),
    );
    return item;
  }

  function renderAuthorMenuLoading(author) {
    const menu = ensureAuthorMenu();
    menu.replaceChildren(
      element("strong", "author-menu-nickname", author.authorNickname || "작성자"),
      element("p", "author-menu-loading", "활동 정보를 불러오는 중입니다."),
    );
  }

  function renderAuthorMenuSummary(summary) {
    const menu = ensureAuthorMenu();
    const header = element("header", "author-menu-header");
    header.append(
      element("strong", "author-menu-nickname", summary.nickname || "작성자"),
      element("span", "author-menu-caption", "커뮤니티 활동"),
    );

    const stats = element("div", "author-menu-stats");
    stats.append(
      authorMenuStat("작성한 게시글", summary.postCount),
      authorMenuStat("작성한 댓글", summary.commentCount),
    );

    const footer = element("div", "author-menu-footer");
    const isCurrentUser = Number(window.FooduckSession?.accountId)
      === Number(summary.accountId);
    if (isCurrentUser) {
      const myPageLink = element("a", "author-menu-link", "마이페이지");
      myPageLink.href = "/pages/mypage/index.html#notifications";
      footer.append(myPageLink);
    } else {
      const disabledLink = element(
        "span",
        "author-menu-link author-menu-link--disabled",
        "마이페이지",
      );
      disabledLink.setAttribute("aria-disabled", "true");
      footer.append(
        disabledLink,
        element("small", "author-menu-note", "마이페이지는 본인만 열 수 있습니다."),
      );
    }

    menu.replaceChildren(header, stats, footer);
  }

  function renderAuthorMenuError(message) {
    const menu = ensureAuthorMenu();
    menu.replaceChildren(
      element("strong", "author-menu-nickname", "작성자 정보"),
      element("p", "author-menu-error", message || "활동 정보를 불러오지 못했습니다."),
    );
  }

  async function openAuthorMenu(author, trigger, event) {
    event.preventDefault();
    event.stopPropagation();
    if (!author?.authorAccountId) return;

    const menu = ensureAuthorMenu();
    if (activeAuthorTrigger === trigger && !menu.hidden) {
      closeAuthorMenu();
      return;
    }

    if (activeAuthorTrigger) {
      activeAuthorTrigger.setAttribute("aria-expanded", "false");
    }
    activeAuthorTrigger = trigger;
    trigger.setAttribute("aria-expanded", "true");
    renderAuthorMenuLoading(author);
    menu.hidden = false;
    positionAuthorMenu(trigger);

    const accountId = Number(author.authorAccountId);
    try {
      const payload = await Api.get(
        `/board/posts/authors/${encodeURIComponent(accountId)}/summary`,
      );
      const summary = payload.data;
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuSummary(summary);
      positionAuthorMenu(trigger);
    } catch (error) {
      if (activeAuthorTrigger !== trigger || menu.hidden) return;
      renderAuthorMenuError(error.message);
      positionAuthorMenu(trigger);
    }
  }

  function enableAuthorMenu(trigger, author) {
    trigger.classList.add("author-nickname--interactive");
    trigger.tabIndex = 0;
    trigger.setAttribute("role", "button");
    trigger.setAttribute("aria-haspopup", "dialog");
    trigger.setAttribute("aria-controls", "board-author-menu");
    trigger.setAttribute("aria-expanded", "false");
    trigger.addEventListener("click", (event) => {
      openAuthorMenu(author, trigger, event);
    });
    trigger.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      openAuthorMenu(author, trigger, event);
    });
  }

  function authorIdentity(
    author,
    { showNickname = true, showAuthorMenu = false } = {},
  ) {
    const wrapper = element("span", "author-identity");
    if (!author?.authorLoginId) {
      wrapper.append(element("strong", "author-login-id", "소셜 계정"));
    }
    if (showNickname && author?.authorNickname) {
      const nickname = element("span", "author-nickname", author.authorNickname);
      if (showAuthorMenu && author.authorAccountId) {
        enableAuthorMenu(nickname, author);
      }
      wrapper.append(nickname);
    }
    wrapper.append(
      element("span", roleClass(author?.authorRole), roleLabel(author?.authorRole)),
    );
    return wrapper;
  }

  function detailPath(postId) {
    return `/pages/board/detail.html?postId=${encodeURIComponent(postId)}`;
  }

  function writePath(boardType = "GENERAL", postId = null) {
    const params = new URLSearchParams();
    if (postId) {
      params.set("postId", postId);
    } else {
      params.set("boardType", boardType === "BUSINESS" ? "BUSINESS" : "GENERAL");
    }
    return `/pages/board/write.html?${params.toString()}`;
  }

  function listPath(boardType = "GENERAL") {
    return boardType === "BUSINESS"
      ? "/pages/board/index.html?boardType=BUSINESS"
      : "/pages/board/index.html";
  }

  function loginPath(nextPath = window.location.pathname + window.location.search) {
    return `/pages/auth/login.html?next=${encodeURIComponent(nextPath)}`;
  }

  function requireLogin(nextPath) {
    if (window.FooduckSession?.authenticated) return true;
    window.location.assign(loginPath(nextPath));
    return false;
  }

  function mapHref(restaurant) {
    const params = new URLSearchParams({
      q: restaurant?.name || "맛집",
    });
    return `/pages/map/index.html?${params.toString()}`;
  }

  function readPostId() {
    const value = Number(new URLSearchParams(window.location.search).get("postId"));
    return Number.isSafeInteger(value) && value > 0 ? value : null;
  }

  function showToast(host, message, isError = false) {
    if (!host) return;
    host.textContent = message;
    host.classList.toggle("is-error", isError);
    host.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
      host.hidden = true;
    }, 3200);
  }

  window.FooduckBoard = {
    authorIdentity,
    categoryLabel,
    detailPath,
    element,
    formatDate,
    icon,
    listPath,
    mapHref,
    readPostId,
    requireLogin,
    roleLabel,
    showToast,
    writePath,
  };
})();
