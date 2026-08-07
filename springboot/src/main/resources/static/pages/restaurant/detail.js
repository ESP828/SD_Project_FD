(() => {
  const loading = document.getElementById("store-loading");
  const errorView = document.getElementById("store-error");
  const errorMessage = document.getElementById("store-error-message");
  const content = document.getElementById("store-content");
  if (!loading || !errorView || !content) return;

  const params = new URLSearchParams(window.location.search);
  const source = params.get("source") === "public" ? "public" : "owned";
  const id = params.get("id");

  if (!id) {
    showError("가게 정보를 찾을 수 없습니다.");
    return;
  }

  const badgesEl = document.getElementById("store-badges");
  const nameEl = document.getElementById("store-name");
  const addressEl = document.getElementById("store-address");
  const favoriteBtn = document.getElementById("store-favorite-btn");
  const statsRow = document.getElementById("store-stats-row");
  const tabsEl = document.getElementById("store-tabs");
  const basicInfo = document.getElementById("store-basic-info");
  const sourceNote = document.getElementById("store-source-note");

  const panels = {
    home: document.getElementById("tab-home"),
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

  const TAB_LABELS = { home: "홈", news: "소식", menu: "메뉴", review: "리뷰", info: "정보" };
  const loadedTabs = new Set();
  let activeTab = "home";
  let tabOrder = [];
  let storeId = null;
  let isOwner = false;
  const isLoggedIn = Boolean(window.FooduckSession && window.FooduckSession.authenticated);

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
      renderHomeMenuPreview(items);
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

  function newsWriteFormHtml() {
    if (!isOwner) return "";
    return `
      <div class="store-write-form" id="store-news-form">
        <h3>소식 작성</h3>
        <input type="text" id="store-news-title" maxlength="200" placeholder="제목">
        <textarea id="store-news-content" maxlength="4000" placeholder="소식 내용을 입력하세요"></textarea>
        <button type="button" class="button button-primary button-sm" id="store-news-submit">소식 등록</button>
      </div>
    `;
  }

  function bindNewsForm() {
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
        await Api.post(`/restaurants/${storeId}/news`, { title, content });
        await loadNews();
      } catch (error) {
        window.alert(error.message || "소식 등록에 실패했습니다.");
      } finally {
        submitButton.disabled = false;
      }
    });
  }

  async function loadNews() {
    if (source === "public") {
      panels.news.innerHTML = `
        <div class="store-section-card">
          <div class="store-empty">공공데이터 출처 가게는 등록된 사장님이 없어 소식 기능을 지원하지 않습니다.<br>사업자가 푸드덕에 가게를 직접 등록하면 소식을 볼 수 있어요.</div>
        </div>
      `;
      return;
    }
    panels.news.innerHTML = '<div class="store-empty">소식을 불러오는 중입니다.</div>';
    try {
      const response = await Api.get(`/public/restaurants/${storeId}/news`, { auth: false });
      const items = response.data || [];
      const listHtml = items.length === 0
        ? '<div class="store-empty">아직 등록된 소식이 없습니다.</div>'
        : items.map((news) => `
            <div class="store-news-item">
              <p class="store-news-title">${escapeHtml(news.title)}</p>
              <p class="store-news-content">${escapeHtml(news.content)}</p>
              <p class="store-news-date">${formatDate(news.createdAt)}</p>
            </div>
          `).join("");
      panels.news.innerHTML = `
        <div class="store-section-card">
          <h2>가게 소식</h2>
          ${newsWriteFormHtml()}
          ${listHtml}
        </div>
      `;
      bindNewsForm();
    } catch (error) {
      panels.news.innerHTML = `<div class="store-empty">${error.message || "소식을 불러오지 못했습니다."}</div>`;
    }
  }

  function renderHomeMenuPreview(items) {
    const homeMenuSlot = document.getElementById("store-home-menu-preview");
    if (!homeMenuSlot) return;
    const preview = items.slice(0, 4);
    if (preview.length === 0) {
      homeMenuSlot.innerHTML = '<div class="store-empty">등록된 메뉴가 없습니다.</div>';
      return;
    }
    homeMenuSlot.innerHTML = `<div class="store-menu-list">${preview.map(menuItemHtml).join("")}</div>`;
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

    panels.home.innerHTML = `
      <div class="store-section-card">
        <h2>가게 소개</h2>
        <p>${store.description ? escapeHtml(store.description) : "등록된 소개글이 없습니다."}</p>
      </div>
      <div class="store-section-card">
        <div class="store-section-head">
          <h2>대표 메뉴</h2>
          <button type="button" class="store-section-link" data-tab-link="menu">전체 메뉴 →</button>
        </div>
        <div id="store-home-menu-preview"><div class="store-empty">메뉴를 불러오는 중입니다.</div></div>
      </div>
    `;
    panels.home.querySelector("[data-tab-link='menu']").addEventListener("click", () => activateTab("menu"));

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
      <div><dt>영업시간</dt><dd>${escapeHtml(store.openingHours || "정보 없음")}</dd></div>
      <div><dt>휴무일</dt><dd>${escapeHtml(store.closedDays || "정보 없음")}</dd></div>
      <div><dt>전화번호</dt><dd>${escapeHtml(store.phone || "정보 없음")}</dd></div>
      <div><dt>평점</dt><dd>${store.averageRating != null ? `★ ${store.averageRating.toFixed(1)} (${store.reviewCount})` : "아직 리뷰가 없습니다"}</dd></div>
    `;
    sourceNote.textContent = "가게 기본정보 출처: 사업자 직접 등록";

    renderTabs(["home", "news", "menu", "review", "info"]);
    activateTab("home");
    loadedTabs.add("menu");
    loadMenu();
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

    statsRow.innerHTML = [
      renderStat("업종", categoryName || "-"),
      renderStat("정보 출처", "공공데이터"),
      renderStat("기준 시점", formatDataYm(store.dataYm)),
    ].join("");

    favoriteBtn.hidden = true;

    panels.home.innerHTML = `
      <div class="store-section-card">
        <h2>가게 정보</h2>
        <p>공공데이터포털에 등록된 사업체 정보를 기반으로 제공하는 기본 정보입니다. 영업시간·전화번호·메뉴 등 상세 정보는 사업자가 푸드덕에 직접 가게를 등록하면 표시됩니다.</p>
      </div>
    `;

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

    basicInfo.innerHTML = `
      <div><dt>업종</dt><dd>${escapeHtml(categoryName || "정보 없음")}</dd></div>
      <div><dt>주소</dt><dd>${escapeHtml(address)}</dd></div>
    `;
    sourceNote.textContent = `가게 기본정보 출처: 공공데이터 · ${formatDataYm(store.dataYm)} 기준`;

    renderTabs(["home", "news", "menu", "review", "info"]);
    activateTab("home");
  }

  async function init() {
    try {
      if (source === "owned") {
        const response = await Api.get(`/public/restaurants/${id}`);
        loading.hidden = true;
        content.hidden = false;
        renderOwnedDetail(response.data);
      } else {
        const response = await Api.get(`/public/map/restaurants/${id}`, { auth: false });
        loading.hidden = true;
        content.hidden = false;
        renderPublicDetail(response.data);
      }
    } catch (error) {
      showError(error.message || "가게 정보를 불러오지 못했습니다.");
    }
  }

  init();
})();
