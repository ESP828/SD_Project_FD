(() => {
  const content = document.getElementById("recommendation-content");
  const fallbackImage = "/images/foodduck-guide.png";
  const session = window.FooduckSession;
  const recommendationPath = "/pages/recommendation/index.html";
  const loginPath = "/pages/auth/login.html?next=" +
    encodeURIComponent(recommendationPath);

  if (!session) {
    return;
  }

  if (!session.authenticated) {
    window.location.replace(loginPath);
    return;
  }

  if (!content) {
    return;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function createImage(url, alt) {
    const wrapper = element("div");
    const placeholder = element("span", "", "이미지 없음");
    wrapper.append(placeholder);
    if (!url) {
      return wrapper;
    }

    const image = new Image();
    image.alt = alt;
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.src = url;
    image.addEventListener("error", () => {
      image.src = fallbackImage;
      image.classList.add("is-fallback");
    }, { once: true });
    wrapper.append(image);
    return wrapper;
  }

  function mapHref(item) {
    const params = new URLSearchParams({ q: item.restaurantName || "맛집" });
    return `/pages/map/index.html?${params.toString()}`;
  }

  function formatPrice(price) {
    return Number.isFinite(price)
      ? `${new Intl.NumberFormat("ko-KR").format(price)}원`
      : "가격 정보 없음";
  }

  function createPersonalCard(item) {
    const article = element("article", "personal-card");
    const image = createImage(item.imageUrl, `${item.restaurantName} 이미지`);
    image.className = "personal-card-image";

    const body = element("div", "personal-card-body");
    body.append(
      element("span", "personal-card-category", item.categoryName || "카테고리 정보 없음"),
      element("h3", "", item.restaurantName || "가게 이름 없음"),
      element("p", "personal-menu", item.menuName || "메뉴 정보 없음"),
      element("p", "personal-price", formatPrice(item.menuPrice)),
      element("span", "personal-reason", item.reason || "현재 DB 정보 기준"),
    );

    const detail = element("a", "button button-sm button-secondary", "지도에서 상세보기");
    detail.href = mapHref(item);
    body.append(detail);
    article.append(image, body);
    return article;
  }

  function createEmptyPersonal() {
    const empty = element("div", "recommendation-empty");
    const image = new Image();
    image.src = "/images/characters/heart.png";
    image.alt = "";
    image.setAttribute("aria-hidden", "true");
    empty.append(
      image,
      element("h3", "", "추천에 사용할 음식점 데이터가 아직 없습니다"),
      element("p", "", "음식점·메뉴가 등록되면 이곳에 가게 이미지, 이름, 메뉴와 가격이 표시됩니다."),
    );
    const mapLink = element("a", "button button-secondary", "Kakao Map에서 먼저 찾기");
    mapLink.href = "/pages/map/index.html";
    empty.append(mapLink);
    return empty;
  }

  function createRankingRow(item, rank) {
    const row = element("div", "ranking-row");
    row.append(element("span", "ranking-number", String(rank)));

    const image = createImage(item?.imageUrl, item ? `${item.restaurantName} 이미지` : "");
    image.className = "ranking-image";
    row.append(image);

    const copy = element("div", "ranking-copy");
    if (item) {
      copy.append(
        element("strong", "", item.restaurantName || "가게 이름 없음"),
        element(
          "span",
          "",
          `${item.categoryName || "카테고리 없음"} · 평점 ${Number(item.averageRating || 0).toFixed(1)} · 리뷰 ${item.reviewCount || 0}`,
        ),
      );
    } else {
      copy.append(
        element("strong", "", "등록 데이터 없음"),
        element("span", "", "음식점 데이터 연결 대기"),
      );
    }
    row.append(copy);

    if (item) {
      const detail = element("a", "ranking-detail", "상세보기");
      detail.href = mapHref(item);
      row.append(detail);
    } else {
      const detail = element("span", "ranking-detail", "상세보기");
      detail.setAttribute("aria-disabled", "true");
      row.append(detail);
    }
    return row;
  }

  function createBestMenus(items) {
    const list = element("ol", "best-menu-list");
    for (let index = 0; index < 5; index += 1) {
      const item = items[index];
      const row = element("li");
      if (item) {
        const link = element("a");
        link.href = mapHref(item);
        link.append(element("span", "best-menu-rank", String(index + 1)));
        const copy = element("span", "best-menu-copy");
        copy.append(
          element("strong", "", item.menuName),
          element("small", "", item.restaurantName),
        );
        link.append(copy);
        row.append(link);
      } else {
        const empty = element("div", "best-menu-empty");
        empty.append(
          element("span", "best-menu-rank", String(index + 1)),
          element("span", "best-menu-copy", "메뉴 데이터 없음"),
        );
        row.append(empty);
      }
      list.append(row);
    }
    return list;
  }

  function render(data) {
    content.replaceChildren();

    const grid = element("div", "recommendation-grid");
    const primary = element("section", "recommendation-primary");
    primary.id = "personal-section";
    const primaryHeader = element("div", "recommendation-section-header");
    const primaryTitle = element("div");
    primaryTitle.append(
      element("h2", "", "나를 위한 맛집"),
      element("p", "", "로그인 계정의 찜·선호도와 현재 음식점 활동 데이터를 반영합니다."),
    );
    const controls = element("div", "carousel-controls");
    const previous = element("button", "carousel-button", "‹");
    previous.type = "button";
    previous.setAttribute("aria-label", "이전 추천");
    const next = element("button", "carousel-button", "›");
    next.type = "button";
    next.setAttribute("aria-label", "다음 추천");
    controls.append(previous, next);
    primaryHeader.append(primaryTitle, controls);
    primary.append(primaryHeader);

    const track = element("div", "personal-track");
    if (data.recommendations?.length) {
      data.recommendations.forEach((item) => track.append(createPersonalCard(item)));
    } else {
      track.classList.add("personal-track--empty");
      track.append(createEmptyPersonal());
    }
    previous.addEventListener("click", () => track.scrollBy({ left: -360, behavior: "smooth" }));
    next.addEventListener("click", () => track.scrollBy({ left: 360, behavior: "smooth" }));
    primary.append(track);

    const rankingPanel = element("aside", "ranking-panel");
    const rankingHeader = element("div", "recommendation-section-header");
    const rankingTitle = element("div");
    rankingTitle.append(
      element("h3", "", "맛집 랭킹"),
      element("p", "", "평점·리뷰·찜 집계 순"),
    );
    rankingHeader.append(rankingTitle);
    rankingPanel.append(rankingHeader);
    const rankingList = element("div", "ranking-list");
    rankingList.setAttribute("aria-label", "맛집 랭킹 5개");
    for (let index = 0; index < 5; index += 1) {
      rankingList.append(createRankingRow(data.ranking?.[index], index + 1));
    }
    rankingPanel.append(rankingList);
    grid.append(primary, rankingPanel);

    const lower = element("div", "recommendation-lower");
    const signalPanel = element("section", "signal-panel");
    const signalHeader = element("div", "recommendation-section-header");
    const signalTitle = element("div");
    signalTitle.append(
      element("h3", "", "추천을 만든 신호"),
      element("p", "", "현재 DB에서 확인 가능한 정보만 표시합니다."),
    );
    signalHeader.append(signalTitle);
    signalPanel.append(signalHeader);
    const signalList = element("div", "signal-list");
    (data.signals || []).forEach((signal) => {
      const item = element("div", "signal-item");
      item.append(
        element("span", "", signal.label),
        element("strong", "", signal.value),
      );
      signalList.append(item);
    });
    signalPanel.append(signalList);

    const bestPanel = element("aside", "best-menu-panel");
    const bestHeader = element("div", "recommendation-section-header");
    const bestTitle = element("div");
    bestTitle.append(
      element("h3", "", "베스트 메뉴"),
      element("p", "", "메뉴를 누르면 Kakao Map에서 가게를 찾습니다."),
    );
    bestHeader.append(bestTitle);
    bestPanel.append(bestHeader, createBestMenus(data.bestMenus || []));
    lower.append(signalPanel, bestPanel);

    content.append(grid, lower);
    window.FooduckIcons?.enhance(content);
  }

  function renderError(error) {
    content.replaceChildren();
    const wrapper = element("div", "recommendation-error");
    const image = new Image();
    image.src = "/images/characters/error.png";
    image.alt = "";
    image.width = 120;
    wrapper.append(
      image,
      element("h2", "", "추천 데이터를 불러오지 못했습니다"),
      element("p", "", error.message || "잠시 후 다시 확인해 주세요."),
    );
    const mapLink = element("a", "button button-secondary", "Kakao Map으로 이동");
    mapLink.href = "/pages/map/index.html";
    wrapper.append(mapLink);
    content.append(wrapper);
  }

  Api.get("/recommendations")
    .then((payload) => render(payload.data || {}))
    .catch((error) => {
      if (!localStorage.getItem("accessToken")) {
        window.location.assign(loginPath);
        return;
      }
      renderError(error);
    });
})();
