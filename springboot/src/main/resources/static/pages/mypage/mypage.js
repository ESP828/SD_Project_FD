(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("mypage-gate");
  const content = document.getElementById("mypage-content");

  if (!session || !gate || !content) {
    return;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function formatDate(value) {
    if (!value) return "정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(date);
  }

  function authorityLabel(code) {
    return {
      ROLE_USER: "일반 사용자",
      ROLE_BUSINESS: "사업자",
      ROLE_ADMIN: "관리자",
    }[code] || code;
  }

  function genderLabel(gender) {
    return {
      MALE: "남성",
      FEMALE: "여성",
      OTHER: "기타",
      UNSPECIFIED: "미설정",
    }[gender] || "미설정";
  }

  function detail(label, value) {
    const wrapper = element("div");
    wrapper.append(element("dt", "", label), element("dd", "", value || "정보 없음"));
    return wrapper;
  }

  function detailPath(tab) {
    return `/pages/mypage/detail.html?tab=${encodeURIComponent(tab)}`;
  }

  function activity(label, count, tab) {
    const card = element("a", "activity-card");
    card.href = detailPath(tab);
    card.setAttribute("aria-label", `${label} ${count || 0}개 상세 보기`);
    card.append(
      element("span", "", label),
      element("strong", "", new Intl.NumberFormat("ko-KR").format(count || 0)),
      element("em", "", "상세 보기 →"),
    );
    return card;
  }

  function action(label, subcopy, href) {
    const node = href ? element("a", "mypage-action") : element("span", "mypage-action is-disabled");
    if (href) node.href = href;
    const copy = element("span");
    copy.append(element("strong", "", label), element("small", "", subcopy));
    node.append(copy, element("span", "", href ? "→" : "준비 중"));
    return node;
  }

  function render(data) {
    content.replaceChildren();

    const summary = element("section", "profile-summary");
    const profile = element("div", "profile-image");
    if (data.profileImageUrl) {
      const image = new Image();
      image.src = data.profileImageUrl;
      image.alt = `${data.nickname} 프로필`;
      image.addEventListener("error", () => {
        profile.replaceChildren();
        const fallback = element("span", "material-symbols-rounded", "person");
        fallback.setAttribute("aria-hidden", "true");
        profile.append(fallback);
        window.FooduckIcons?.enhance(profile);
      }, { once: true });
      profile.append(image);
    } else {
      const fallback = element("span", "material-symbols-rounded", "person");
      fallback.setAttribute("aria-hidden", "true");
      profile.append(fallback);
    }

    const profileCopy = element("div", "profile-copy");
    profileCopy.append(
      element("h2", "", `${data.nickname || "회원"}님, 반가워요`),
      element("p", "", `${data.loginId || "소셜 계정"} · 가입 ${formatDate(data.createdAt)}`),
    );
    const authorities = element("div", "authority-list");
    (data.authorities || []).forEach((code) => {
      authorities.append(element("span", "authority-badge", authorityLabel(code)));
    });
    profileCopy.append(authorities);
    const mapButton = element("a", "button button-secondary", "맛집 찾기");
    mapButton.href = "/pages/map/index.html";
    summary.append(profile, profileCopy, mapButton);

    const activities = element("section", "activity-grid");
    activities.append(
      activity("찜한 가게", data.favoriteCount, "favorites"),
      activity("작성한 리뷰", data.reviewCount, "reviews"),
      activity("작성한 게시글", data.postCount, "posts"),
      activity("작성한 댓글", data.commentCount, "comments"),
      activity("읽지 않은 알림", data.unreadNotificationCount, "notifications"),
    );

    const layout = element("div", "mypage-layout");
    const accountPanel = element("section", "mypage-panel");
    const accountHeader = element("div", "mypage-panel-header");
    const accountTitle = element("div");
    accountTitle.append(
      element("h3", "", "내 정보"),
      element("p", "", "현재 계정 테이블에 저장된 정보"),
    );
    accountHeader.append(accountTitle);
    const details = element("dl", "account-details");
    details.append(
      detail("계정 번호", String(data.accountId)),
      detail("로그인 아이디", data.loginId || "소셜 로그인 계정"),
      detail("이메일", data.email),
      detail("닉네임", data.nickname),
      detail("성별", genderLabel(data.gender)),
      detail("생년월일", data.birthDate || "미설정"),
      detail("계정 상태", data.status),
      detail("가입일", formatDate(data.createdAt)),
    );
    accountPanel.append(accountHeader, details);

    const side = element("aside", "mypage-side-stack");
    const actionPanel = element("section", "mypage-side-panel");
    const actionHeader = element("div", "mypage-panel-header");
    const actionTitle = element("div");
    actionTitle.append(
      element("h3", "", "내 활동"),
      element("p", "", "현재 연결된 화면과 후속 API 상태"),
    );
    actionHeader.append(actionTitle);
    const actionList = element("div", "mypage-action-list");
    actionList.append(
      action("찜한 가게", "저장한 맛집 확인", detailPath("favorites")),
      action("내 리뷰", "작성한 리뷰 확인", detailPath("reviews")),
      action("내 게시글", "작성한 게시글 확인", detailPath("posts")),
      action("내 댓글", "작성한 댓글 확인", detailPath("comments")),
      action("읽지 않은 알림", "새 알림 확인", detailPath("notifications")),
      session.canManageBusiness
        ? action("사업자 관리", "내 가게와 사업자 기능", "/pages/business/index.html")
        : action("사업자 권한 신청", "신청 API 연결 예정", "/pages/business/index.html#application"),
    );
    if (data.loginId) {
      actionList.append(
        action("비밀번호 변경", "현재 비밀번호를 확인하고 새로 설정", "/pages/mypage/change-password.html"),
      );
    }
    actionList.append(
      session.isAdmin
        ? action("관리자 페이지", "계정·신청·가게 관리", "/pages/admin/index.html")
        : action("계정 설정", "회원 탈퇴 API 연결 예정"),
    );
    actionPanel.append(actionHeader, actionList);

    const notificationPanel = element("section", "mypage-side-panel");
    notificationPanel.id = "notifications";
    const notification = element("a", "notification-summary");
    notification.href = detailPath("notifications");
    const notificationImage = new Image();
    notificationImage.src = "/images/characters/notification.png";
    notificationImage.alt = "";
    const notificationCopy = element("div");
    notificationCopy.append(
      element("strong", "", `읽지 않은 알림 ${data.unreadNotificationCount || 0}개`),
      element("span", "", "알림 목록·읽음 처리 API는 후속 연결 대상입니다."),
    );
    notification.append(notificationImage, notificationCopy);
    notificationPanel.append(notification);
    side.append(actionPanel, notificationPanel);
    layout.append(accountPanel, side);

    content.append(summary, activities, layout);
    window.FooduckIcons?.enhance(content);
  }

  function renderError(error) {
    content.replaceChildren();
    const wrapper = element("div", "mypage-error");
    const image = new Image();
    image.src = "/images/characters/error.png";
    image.alt = "";
    image.width = 120;
    wrapper.append(
      image,
      element("h2", "", "마이페이지 정보를 불러오지 못했습니다."),
      element("p", "", error.message || "잠시 후 다시 시도해 주세요."),
    );
    content.append(wrapper);
  }

  if (!session.authenticated) {
    content.hidden = true;
    gate.hidden = false;
    return;
  }

  Api.get("/mypage/overview")
    .then((payload) => render(payload.data || {}))
    .catch((error) => {
      if (!localStorage.getItem("accessToken")) {
        window.location.assign(
          "/pages/auth/login.html?next=" +
          encodeURIComponent("/pages/mypage/index.html"),
        );
        return;
      }
      renderError(error);
    });
})();
