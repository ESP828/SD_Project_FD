(() => {
  const session = window.FooduckSession;
  const profile = window.FooduckProfile;
  const gate = document.getElementById("mypage-gate");
  const content = document.getElementById("mypage-content");

  if (!session || !profile || !gate || !content) {
    return;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
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

  function field(label, control, className = "") {
    const wrapper = element("label", `mypage-profile-field ${className}`.trim());
    wrapper.append(element("span", "", label), control);
    return wrapper;
  }

  function input(type, name, value = "") {
    const control = document.createElement("input");
    control.type = type;
    control.name = name;
    control.value = value || "";
    return control;
  }

  function localDateValue(date = new Date()) {
    const year = String(date.getFullYear());
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function createProfileForm(data, onCancel) {
    const form = element("form", "mypage-profile-form");
    const loginId = input("text", "loginId", data.loginId || "소셜 로그인 계정");
    const email = input("email", "email", data.email || "등록된 이메일 없음");
    const createdAt = input("text", "createdAt", profile.formatDate(data.createdAt));
    [loginId, email, createdAt].forEach((control) => {
      control.readOnly = true;
      control.setAttribute("aria-readonly", "true");
    });

    const nickname = input("text", "nickname", data.nickname);
    nickname.required = true;
    nickname.minLength = 2;
    nickname.maxLength = 30;
    nickname.autocomplete = "nickname";

    const gender = document.createElement("select");
    gender.name = "gender";
    [
      ["UNSPECIFIED", "미설정"],
      ["MALE", "남성"],
      ["FEMALE", "여성"],
      ["OTHER", "기타"],
    ].forEach(([value, label]) => {
      const option = element("option", "", label);
      option.value = value;
      option.selected = value === (data.gender || "UNSPECIFIED");
      gender.append(option);
    });

    const birthDate = input("date", "birthDate", data.birthDate);
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    birthDate.max = localDateValue(yesterday);

    const status = element("p", "mypage-profile-form-status");
    status.setAttribute("aria-live", "polite");
    const actions = element("div", "mypage-profile-form-actions");
    const cancelButton = element("button", "button button-secondary", "취소");
    cancelButton.type = "button";
    const saveButton = element("button", "button button-primary", "저장하기");
    saveButton.type = "submit";
    actions.append(cancelButton, saveButton);

    form.append(
      field("로그인 아이디", loginId),
      field("이메일", email),
      field("닉네임", nickname),
      field("가입일", createdAt),
      field("성별", gender),
      field("생년월일", birthDate),
      status,
      actions,
    );

    cancelButton.addEventListener("click", onCancel);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      status.className = "mypage-profile-form-status";
      status.textContent = "저장하고 있습니다.";
      saveButton.disabled = true;
      cancelButton.disabled = true;
      try {
        const payload = await Api.patch("/mypage/profile", {
          nickname: nickname.value,
          gender: gender.value,
          birthDate: birthDate.value || null,
        });
        render(payload.data || data, payload.message || "내 정보가 수정되었습니다.");
      } catch (error) {
        status.classList.add("is-error");
        status.textContent = error.message || "내 정보를 수정하지 못했습니다.";
        saveButton.disabled = false;
        cancelButton.disabled = false;
      }
    });
    return form;
  }

  function detailPath(tab) {
    return `/pages/mypage/detail.html?tab=${encodeURIComponent(tab)}`;
  }

  const activityIcons = Object.freeze({
    favorites: "favorite",
    presets: "map",
    reviews: "rate_review",
    posts: "article",
    comments: "chat_bubble",
    notifications: "notifications",
  });

  function activity(label, count, tab) {
    const normalizedCount = Math.max(0, Number(count) || 0);
    const formattedCount = new Intl.NumberFormat("ko-KR").format(normalizedCount);
    const card = element("a", "activity-card");
    card.href = detailPath(tab);
    card.setAttribute("aria-label", `${label} ${formattedCount}개 목록 보기`);

    const icon = element("span", "activity-card-icon");
    const materialIcon = element("span", "material-symbols-rounded", activityIcons[tab]);
    materialIcon.setAttribute("aria-hidden", "true");
    icon.append(materialIcon);

    const copy = element("span", "activity-card-content");
    copy.append(
      element("span", "activity-card-label", label),
      element("strong", "activity-card-value", `${formattedCount}개`),
    );

    card.append(
      icon,
      copy,
      element("em", "activity-card-link", "목록 보기 →"),
    );
    return card;
  }

  function action(label, subcopy, href) {
    const node = href ? element("a", "mypage-action") : element("span", "mypage-action is-disabled");
    if (href) node.href = href;
    const copy = element("span", "mypage-action-copy");
    copy.append(element("strong", "", label), element("small", "", subcopy));
    node.append(copy, element("span", "mypage-action-state", href ? "→" : "준비 중"));
    return node;
  }

  function createWithdrawalPanel(data) {
    const panel = element("section", "mypage-side-panel mypage-danger-panel");
    const header = element("div", "mypage-panel-header");
    const title = element("div");
    title.append(
      element("h3", "", "계정 관리"),
      element("p", "", "탈퇴 후에는 계정으로 다시 로그인할 수 없습니다."),
    );
    header.append(title);
    const body = element("div", "mypage-danger-body");

    function showSummary() {
      const copy = element(
        "p",
        "",
        "작성한 콘텐츠는 보존되며, 사업자 계정의 운영 중 음식점은 운영 중지됩니다.",
      );
      const openButton = element("button", "button button-secondary mypage-withdraw-button", "회원 탈퇴");
      openButton.type = "button";
      openButton.addEventListener("click", showForm);
      body.replaceChildren(copy, openButton);
    }

    function showForm() {
      const form = element("form", "mypage-withdraw-form");
      let password = null;
      if (data.loginId) {
        password = input("password", "currentPassword");
        password.required = true;
        password.maxLength = 128;
        password.autocomplete = "current-password";
        form.append(field("현재 비밀번호", password, "mypage-withdraw-field"));
      }

      const confirmation = input("text", "confirmation");
      confirmation.required = true;
      confirmation.maxLength = 20;
      confirmation.autocomplete = "off";
      confirmation.placeholder = "회원탈퇴";
      form.append(field('확인을 위해 "회원탈퇴" 입력', confirmation, "mypage-withdraw-field"));

      const status = element("p", "mypage-withdraw-status");
      status.setAttribute("role", "status");
      const actions = element("div", "mypage-withdraw-actions");
      const cancelButton = element("button", "button button-secondary button-sm", "취소");
      const submitButton = element("button", "button button-sm mypage-withdraw-submit", "탈퇴하기");
      cancelButton.type = "button";
      submitButton.type = "submit";
      cancelButton.addEventListener("click", showSummary);
      actions.append(cancelButton, submitButton);
      form.append(status, actions);

      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        status.className = "mypage-withdraw-status";
        status.textContent = "회원 탈퇴를 처리하고 있습니다.";
        submitButton.disabled = true;
        cancelButton.disabled = true;
        try {
          await Api.patch("/mypage/account/withdraw", {
            currentPassword: password ? password.value : null,
            confirmation: confirmation.value,
          });
          await Api.logout().catch(() => Api.clearToken());
          window.location.assign("/");
        } catch (error) {
          status.classList.add("is-error");
          status.textContent = error.message || "회원 탈퇴를 처리하지 못했습니다.";
          submitButton.disabled = false;
          cancelButton.disabled = false;
        }
      });

      body.replaceChildren(form);
      (password || confirmation).focus();
    }

    showSummary();
    panel.append(header, body);
    return panel;
  }

  function render(data, successMessage = "") {
    content.replaceChildren();

    const profileActions = [];
    if (session.canManageBusiness) {
      profileActions.push({
        label: "사업자 페이지",
        href: "/pages/business/index.html",
      });
    }
    if (session.isAdmin) {
      profileActions.push({
        label: "관리자 페이지",
        href: "/pages/admin/index.html",
      });
    }
    const summary = profile.createSummary(data, profileActions);

    const activities = element("section", "activity-grid activity-grid--mypage");
    activities.setAttribute("aria-label", "내 활동 요약");
    activities.append(
      activity("찜한 맛집", data.favoriteCount, "favorites"),
      activity("내 보물지도", data.presetCount, "presets"),
      activity("작성한 리뷰", data.reviewCount, "reviews"),
      activity("작성한 글", data.postCount, "posts"),
      activity("작성한 댓글", data.commentCount, "comments"),
      activity("새 알림", data.unreadNotificationCount, "notifications"),
    );

    const layout = element("div", "mypage-layout");
    const accountPanel = element("section", "mypage-panel");
    const accountHeader = element("div", "mypage-panel-header");
    const accountTitle = element("div");
    accountTitle.append(
      element("h3", "", "내 정보"),
      element("p", "", "내 프로필에 등록된 정보"),
    );
    const editButton = element("button", "button button-secondary button-sm", "수정하기");
    editButton.type = "button";
    accountHeader.append(accountTitle, editButton);
    const details = element("dl", "account-details");
    details.append(
      detail("닉네임", data.nickname),
      detail("가입일", profile.formatDate(data.createdAt)),
      detail("성별", genderLabel(data.gender)),
      detail("생년월일", data.birthDate ? profile.formatDate(data.birthDate) : "미설정"),
    );
    accountPanel.append(accountHeader, details);
    if (successMessage) {
      const feedback = element("p", "mypage-profile-feedback", successMessage);
      feedback.setAttribute("role", "status");
      accountPanel.append(feedback);
    }
    editButton.addEventListener("click", () => {
      editButton.hidden = true;
      const form = createProfileForm(data, () => {
        form.replaceWith(details);
        editButton.hidden = false;
      });
      details.replaceWith(form);
      form.elements.nickname.focus();
    });

    const side = element("aside", "mypage-side-stack");
    const actionPanel = element("section", "mypage-side-panel");
    const actionHeader = element("div", "mypage-panel-header");
    const actionTitle = element("div");
    actionTitle.append(
      element("h3", "", "내 활동 바로가기"),
      element("p", "", "활동별 기록을 한곳에서 확인해 보세요."),
    );
    actionHeader.append(actionTitle);
    const actionList = element("div", "mypage-action-list");
    actionList.append(
      action("찜한 맛집", "저장해 둔 맛집 모아보기", detailPath("favorites")),
      action("내 보물지도", "등록한 보물지도 확인·수정", detailPath("presets")),
      action("작성한 리뷰", "내가 남긴 리뷰 모아보기", detailPath("reviews")),
      action("작성한 게시글", "커뮤니티 게시글 모아보기", detailPath("posts")),
      action("작성한 댓글", "커뮤니티 댓글 모아보기", detailPath("comments")),
      action("알림", "새로 도착한 알림 확인하기", detailPath("notifications")),
    );
    if (data.loginId) {
      actionList.append(
        action("비밀번호 변경", "현재 비밀번호를 확인하고 새로 설정", "/pages/mypage/change-password.html"),
      );
    }
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
      element("span", "", "알림을 확인하고 읽음 처리할 수 있습니다."),
    );
    notification.append(notificationImage, notificationCopy);
    notificationPanel.append(notification);
    side.append(actionPanel, createWithdrawalPanel(data), notificationPanel);
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
