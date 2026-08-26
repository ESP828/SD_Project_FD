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

  function createProfileForm(data, onCancel, onAccountManage) {
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
    const accountButton = element("button", "button button-sm mypage-account-manage-button", "계정탈퇴");
    accountButton.type = "button";
    accountButton.addEventListener("click", onAccountManage);
    const cancelButton = element("button", "button button-secondary button-sm", "취소");
    cancelButton.type = "button";
    const saveButton = element("button", "button button-primary button-sm", "저장하기");
    saveButton.type = "submit";
    actions.append(accountButton, cancelButton, saveButton);

    const fieldsGrid = element("div", "mypage-profile-fields-grid");
    fieldsGrid.append(
      field("로그인 아이디", loginId),
      field("이메일", email),
      field("닉네임", nickname),
      field("가입일", createdAt),
      field("성별", gender),
      field("생년월일", birthDate),
    );
    form.append(fieldsGrid, status, actions);

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
    return `/mypage/detail?tab=${encodeURIComponent(tab)}`;
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

  function createWithdrawalDialog(data) {
    const dialog = element("dialog", "mypage-account-dialog mypage-danger-panel");
    const header = element("div", "mypage-panel-header");
    const title = element("div");
    title.append(
      element("h3", "", "계정 탈퇴"),
      element("p", "", "탈퇴 이후에는 계정으로 다시 로그인할 수 없습니다."),
    );
    const closeButton = element("button", "mypage-dialog-close", "");
    closeButton.type = "button";
    closeButton.setAttribute("aria-label", "닫기");
    const closeIcon = element("span", "material-symbols-rounded", "close");
    closeIcon.setAttribute("aria-hidden", "true");
    closeButton.append(closeIcon);
    closeButton.addEventListener("click", () => dialog.close());
    header.append(title, closeButton);
    const body = element("div", "mypage-danger-body");

    function showForm() {
      const intro = element(
        "p",
        "",
        "작성한 콘텐츠는 보존되며, 사업자 계정의 운영 중 음식점은 운영 중지됩니다.",
      );
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
      cancelButton.addEventListener("click", () => dialog.close());
      actions.append(cancelButton, submitButton);
      form.append(status, actions);

      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        status.className = "mypage-withdraw-status";

        if (!form.checkValidity()) {
          form.reportValidity();
          return;
        }
        if (confirmation.value.trim() !== "회원탈퇴") {
          status.classList.add("is-error");
          status.textContent = '확인 문구로 "회원탈퇴"를 정확히 입력해 주세요.';
          confirmation.focus();
          return;
        }

        const accountImpacts = data.loginId
          ? [
              "• 현재 아이디와 비밀번호로 다시 로그인할 수 없습니다.",
              "• 탈퇴한 계정은 복구되지 않으며, 같은 아이디·이메일로 가입해도 별도의 새 계정이 됩니다.",
            ]
          : ["• 현재 연결된 소셜 계정으로 다시 로그인하거나 재가입할 수 없습니다."];
        const confirmed = window.confirm([
          "정말 회원 탈퇴하시겠습니까?",
          "",
          ...accountImpacts,
          "• 게시글·댓글·리뷰 등 작성 콘텐츠는 보존되고 작성자는 ‘탈퇴한 회원’으로 표시됩니다.",
          "• 운영 중인 음식점이 있다면 운영 중지됩니다.",
          "• 탈퇴 처리는 되돌릴 수 없습니다.",
        ].join("\n"));
        if (!confirmed) return;

        status.textContent = "회원 탈퇴를 처리하고 있습니다.";
        submitButton.disabled = true;
        cancelButton.disabled = true;
        try {
          await Api.patch("/mypage/account/withdraw", {
            currentPassword: password ? password.value : null,
            confirmation: confirmation.value.trim(),
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

      body.replaceChildren(intro, form);
    }

    dialog.addEventListener("close", showForm);
    showForm();
    dialog.append(header, body);
    return dialog;
  }

  function render(data, successMessage = "") {
    content.replaceChildren();

    const profileActions = [];
    if (session.canManageBusiness) {
      profileActions.push({
        label: "사업자 페이지",
        href: "/business",
      });
    }
    if (session.isAdmin) {
      profileActions.push({
        label: "관리자 페이지",
        href: "/admin",
      });
    }
    const summary = profile.createSummary(data, profileActions, { hideLoginId: true });

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
    const accountPanel = element("section", "mypage-panel mypage-account-panel");
    const accountHeader = element("div", "mypage-panel-header");
    const accountTitle = element("div");
    accountTitle.append(
      element("h3", "", "내 정보"),
      element("p", "", "내 프로필에 등록된 정보"),
    );
    const editButton = element("button", "icon-button", "");
    editButton.type = "button";
    editButton.setAttribute("aria-label", "내 정보 수정");
    const editIcon = element("i", "fa-solid fa-pen");
    editIcon.setAttribute("aria-hidden", "true");
    editButton.append(editIcon);
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
    const withdrawalDialog = createWithdrawalDialog(data);
    accountPanel.append(withdrawalDialog);
    editButton.addEventListener("click", () => {
      editButton.hidden = true;
      const form = createProfileForm(
        data,
        () => {
          form.replaceWith(details);
          editButton.hidden = false;
        },
        () => withdrawalDialog.showModal(),
      );
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
      session.canManageBusiness
        ? action("사업자 관리", "내 가게와 사업자 기능", "/business")
        : action("사업자 권한 신청", "내 가게를 등록하고 싶다면 신청해 보세요", "/business#business-application"),
    );
    if (data.loginId) {
      actionList.append(
        action("비밀번호 변경", "현재 비밀번호를 확인하고 새로 설정", "/mypage/change-password"),
      );
    }
    actionPanel.append(actionHeader, actionList);

    side.append(actionPanel);
    layout.append(accountPanel, side);

    content.append(summary, activities, layout);
    window.FooduckIcons?.enhance(content);

    // "내 정보"는 수정하기를 눌러 폼으로 바뀌면 항목이 늘어나 "내 활동 바로가기"보다
    // 길어질 수 있다. 두 박스 세로 길이가 항상 같게, "내 활동 바로가기" 높이에 맞춰
    // 고정해 두고, 폼 내용이 넘치면 박스 밖으로 커지는 대신 안에서 스크롤되게 한다.
    requestAnimationFrame(() => {
      accountPanel.style.height = `${actionPanel.offsetHeight}px`;
    });
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
          "/auth/login?next=" +
          encodeURIComponent("/mypage"),
        );
        return;
      }
      renderError(error);
    });
})();
