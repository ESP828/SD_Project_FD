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
    activities.append(
      activity("찜한 가게", data.favoriteCount, "favorites"),
      activity("보물지도 리스트", data.presetCount, "presets"),
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
      element("h3", "", "내 활동"),
      element("p", "", "현재 연결된 화면과 후속 API 상태"),
    );
    actionHeader.append(actionTitle);
    const actionList = element("div", "mypage-action-list");
    actionList.append(
      action("찜한 가게", "저장한 맛집 확인", detailPath("favorites")),
      action("보물지도 리스트", "내가 만든 보물지도 확인·수정", detailPath("presets")),
      action("내 리뷰", "작성한 리뷰 확인", detailPath("reviews")),
      action("내 게시글", "작성한 게시글 확인", detailPath("posts")),
      action("내 댓글", "작성한 댓글 확인", detailPath("comments")),
      action("읽지 않은 알림", "새 알림 확인", detailPath("notifications")),
    );
    if (data.loginId) {
      actionList.append(
        action("비밀번호 변경", "현재 비밀번호를 확인하고 새로 설정", "/pages/mypage/change-password.html"),
      );
    }
    actionList.append(action("계정 설정", "회원 탈퇴 API 연결 예정"));
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
    // 마이페이지 또는 메인페이지의 "나를 위한 맛집" 로드 함수
async function loadPersonalRecommendations(lat, lng) {
  const token = localStorage.getItem('accessToken');

  try {
    const response = await fetch(`/api/recommendations/personal?latitude=${lat}&longitude=${lng}`, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    const result = await response.json();
    const data = result.data;
    const container = document.getElementById('personal-recommend-container');

    // 💡 1. 찜/선호 데이터가 없는 경우 (이미지 UI 렌더링)
    if (!data.hasPreferenceData || data.items.length === 0) {
      container.innerHTML = `
        <div class="empty-recommend-box" style="text-align: center; padding: 30px 0;">
          <img src="/images/duck_heart.png" alt="오리" style="width: 120px; margin-bottom: 15px;" />
          <h3 style="font-weight: bold; margin-bottom: 8px;">추천에 사용할 음식점 데이터가 아직 없습니다</h3>
          <p style="color: #666; font-size: 14px; margin-bottom: 20px;">
            음식점·메뉴가 등록되면 이곳에 가게 이미지, 이름, 메뉴와 가격이 표시됩니다.
          </p>
          <a href="/pages/map/map.html" class="btn-find-kakao" style="display: inline-block; padding: 10px 24px; border: 1px solid #ddd; border-radius: 20px; text-decoration: none; color: #333; font-weight: bold;">
            Kakao Map에서 먼저 찾기
          </a>
        </div>
      `;
      return;
    }

    // 💡 2. 찜 데이터가 있는 경우 (나를 위한 맛집 카드 목록 렌더링)
    let cardsHtml = `<p class="pref-summary" style="color: #f39c12; font-weight: bold; margin-bottom: 15px;">${data.userPreferenceSummary}</p>`;
    cardsHtml += '<div class="restaurant-card-grid">';

    data.items.forEach(item => {
      cardsHtml += `
        <div class="restaurant-card">
          <h4>${item.restaurantName}</h4>
          <span class="badge">${item.categoryName}</span>
          <p class="address">${item.address}</p>
          <p class="score">취향 매칭 점수: <strong>${Math.round(item.score * 100)}점</strong></p>
          <p class="reason">💡 ${item.reasons[0] || ''}</p>
        </div>
      `;
    });

    cardsHtml += '</div>';
    container.innerHTML = cardsHtml;

  } catch (error) {
    console.error('개인화 추천 로딩 실패:', error);
  }
}
})();
