(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  const prevButton = document.querySelector("#home-preset-prev");
  const nextButton = document.querySelector("#home-preset-next");

  // 메인 인기 보물지도는 페이지를 오갈 때마다 같은 응답을 다시 기다릴 필요가 없다.
  // 브라우저 탭이 열려 있는 동안만 짧게 보관해 초기 체감 속도를 높이고,
  // 오래된 데이터는 화면을 막지 않은 채 뒤에서 다음 방문용으로 갱신한다.
  const HOME_PRESET_CACHE_KEY = "fooduck:home:popular-presets:v1";
  const HOME_PRESET_CACHE_TTL_MS = 2 * 60 * 1000;
  const HOME_PRESET_CACHE_STALE_MS = 30 * 60 * 1000;

  function readPresetCache() {
    try {
      const raw = sessionStorage.getItem(HOME_PRESET_CACHE_KEY);
      if (!raw) return null;
      const cached = JSON.parse(raw);
      const savedAt = Number(cached?.savedAt) || 0;
      const age = Date.now() - savedAt;
      if (!savedAt || age < 0 || age > HOME_PRESET_CACHE_STALE_MS || !cached?.payload) {
        sessionStorage.removeItem(HOME_PRESET_CACHE_KEY);
        return null;
      }
      return { payload: cached.payload, age };
    } catch {
      try { sessionStorage.removeItem(HOME_PRESET_CACHE_KEY); } catch {}
      return null;
    }
  }

  function writePresetCache(payload) {
    try {
      sessionStorage.setItem(HOME_PRESET_CACHE_KEY, JSON.stringify({
        savedAt: Date.now(),
        payload,
      }));
    } catch {
      // 저장 공간 제한/비활성화 시 캐시 없이 기존 흐름으로 동작한다.
    }
  }

  function updateNavVisibility() {
    if (!prevButton || !nextButton) return;
    const maxScroll = list.scrollWidth - list.clientWidth;
    if (maxScroll <= 4) {
      prevButton.hidden = true;
      nextButton.hidden = true;
      return;
    }
    prevButton.hidden = list.scrollLeft <= 4;
    nextButton.hidden = list.scrollLeft >= maxScroll - 4;
  }

  function scrollByCard(direction) {
    const card = list.querySelector(".home-preset-card");
    const step = card ? card.getBoundingClientRect().width + 20 : list.clientWidth;
    list.scrollBy({ left: direction * step, behavior: "smooth" });
  }

  prevButton?.addEventListener("click", () => scrollByCard(-1));
  nextButton?.addEventListener("click", () => scrollByCard(1));
  list.addEventListener("scroll", updateNavVisibility, { passive: true });
  window.addEventListener("resize", updateNavVisibility);

  function renderCard(preset, rank) {
    const link = document.createElement("a");
    link.className = "home-preset-card";
    link.href = `/presset/detail?presetId=${encodeURIComponent(preset.presetId)}`;
    link.setAttribute("aria-label", `${preset.title || "보물지도"} 상세 보기`);

    const visual = document.createElement("div");
    visual.className = "home-preset-visual";
    const thumbnail = preset.imageUrl
      || (Array.isArray(preset.thumbnailImageUrls) ? preset.thumbnailImageUrls[0] : null);
    if (thumbnail) {
      const img = new Image();
      img.src = thumbnail;
      img.alt = "";
      img.loading = "lazy";
      visual.append(img);
    }
    link.append(visual);

    const body = document.createElement("div");
    body.className = "home-preset-body";

    const title = document.createElement("h3");
    title.textContent = preset.title || "맛집 보물지도";
    body.append(title);

    if (preset.description) {
      const desc = document.createElement("p");
      desc.className = "home-preset-desc";
      desc.textContent = preset.description;
      body.append(desc);
    }

    if (Array.isArray(preset.tags) && preset.tags.length) {
      const tagRow = document.createElement("div");
      tagRow.className = "home-preset-tags";
      preset.tags.slice(0, 3).forEach((tag) => {
        const pill = document.createElement("span");
        pill.textContent = tag.tagName;
        tagRow.append(pill);
      });
      body.append(tagRow);
    }

    const meta = document.createElement("div");
    meta.className = "home-preset-meta";
    const restaurantMeta = document.createElement("span");
    restaurantMeta.textContent = `🍴 맛집 ${preset.restaurantCount || 0}곳`;
    meta.append(restaurantMeta);
    if (typeof preset.favoriteCount === "number") {
      const saveMeta = document.createElement("span");
      saveMeta.textContent = `❤ 저장 ${preset.favoriteCount}`;
      meta.append(saveMeta);
    }
    body.append(meta);

    link.append(body);

    const arrow = document.createElement("span");
    arrow.className = "home-preset-arrow";
    arrow.innerHTML = '<span class="material-symbols-rounded" aria-hidden="true">arrow_forward</span>';
    link.append(arrow);

    return link;
  }

  function startAutoScroll() {
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (prefersReducedMotion) return;

    const originalCards = Array.from(list.children);
    if (originalCards.length < 2) return;

    // 카드 세트를 한 번 더 복제해 이어붙여서, 오른쪽에서 왼쪽으로 끊김 없이 계속 흐르도록 만든다.
    originalCards.forEach((card) => list.append(card.cloneNode(true)));

    if (prevButton) prevButton.hidden = true;
    if (nextButton) nextButton.hidden = true;

    // 카드 이미지의 네이티브 드래그 제스처가 포인터를 캡처해 pointerleave가 유실되면
    // 이전 방식(enter/leave 이벤트로 paused 플래그를 토글)은 영영 멈춘 것처럼 보였다.
    // 매 프레임 :hover/포커스 상태를 직접 계산해 상태가 어긋날 여지를 없앤다.
    list.querySelectorAll("img").forEach((img) => {
      img.draggable = false;
    });

    function isPaused() {
      return list.matches(":hover") || list.contains(document.activeElement);
    }

    function step() {
      if (!isPaused()) {
        const loopPoint = list.scrollWidth / 2;
        list.scrollLeft += 0.6;
        if (list.scrollLeft >= loopPoint) {
          list.scrollLeft -= loopPoint;
        }
      }
      window.requestAnimationFrame(step);
    }
    window.requestAnimationFrame(step);
  }

  function renderPresetPayload(payload) {
    const presets = Array.isArray(payload?.data?.content) ? payload.data.content : [];
    list.replaceChildren();
    list.setAttribute("aria-busy", "false");
    if (!presets.length) {
      const state = document.createElement("p");
      state.className = "home-preset-state";
      state.textContent = "현재 공개된 보물지도가 없습니다.";
      list.append(state);
      return;
    }
    presets.forEach((preset, index) => list.append(renderCard(preset, index + 1)));
    window.requestAnimationFrame(updateNavVisibility);
    startAutoScroll();
  }

  function fetchAndCachePresets({ render = true } = {}) {
    return Api.get("/presets?page=0&size=4&sort=popular")
      .then((payload) => {
        writePresetCache(payload);
        if (render) renderPresetPayload(payload);
        return payload;
      });
  }

  const cachedPresets = readPresetCache();
  if (cachedPresets) {
    // 캐시가 있으면 네트워크 응답을 기다리지 않고 즉시 보여준다.
    renderPresetPayload(cachedPresets.payload);

    // 2분 이내의 캐시는 그대로 사용한다. 오래된 캐시는 현재 화면을 다시 그리지 않고
    // 잠시 뒤 조용히 갱신해 다음 메인 진입 때 최신 데이터를 바로 쓸 수 있게 한다.
    if (cachedPresets.age > HOME_PRESET_CACHE_TTL_MS) {
      window.setTimeout(() => {
        fetchAndCachePresets({ render: false }).catch(() => {});
      }, 1200);
    }
  } else {
    fetchAndCachePresets()
      .catch(() => {
        list.setAttribute("aria-busy", "false");
        list.innerHTML = '<p class="home-preset-state">인기 보물지도를 불러오지 못했습니다.</p>';
      });
  }
})();
