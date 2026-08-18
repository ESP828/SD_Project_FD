(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  const prevButton = document.querySelector("#home-preset-prev");
  const nextButton = document.querySelector("#home-preset-next");
  const PRESET_CACHE_KEY = "fooduck.home.popular-presets.v1";
  const PRESET_CACHE_TTL_MS = 2 * 60 * 1000;
  let autoScrollEnabled = false;

  function updateNavVisibility() {
    if (autoScrollEnabled || !prevButton || !nextButton) return;
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
  window.addEventListener("resize", updateNavVisibility, { passive: true });

  function renderCard(preset) {
    const link = document.createElement("a");
    link.className = "home-preset-card";
    link.href = `/pages/presset/detail.html?presetId=${encodeURIComponent(preset.presetId)}`;
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
      img.decoding = "async";
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

  function readPresetCache() {
    try {
      const raw = sessionStorage.getItem(PRESET_CACHE_KEY);
      if (!raw) return null;
      const cached = JSON.parse(raw);
      const cachedAt = Number(cached?.cachedAt) || 0;
      if (!Array.isArray(cached?.presets) || Date.now() - cachedAt > PRESET_CACHE_TTL_MS) {
        sessionStorage.removeItem(PRESET_CACHE_KEY);
        return null;
      }
      return cached.presets;
    } catch {
      return null;
    }
  }

  function writePresetCache(presets) {
    try {
      sessionStorage.setItem(PRESET_CACHE_KEY, JSON.stringify({
        cachedAt: Date.now(),
        presets,
      }));
    } catch {
      // 저장 공간 제한/차단은 홈 화면 기능에 영향을 주지 않는다.
    }
  }

  function startAutoScroll() {
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (prefersReducedMotion) return;

    const originalCards = Array.from(list.querySelectorAll(":scope > .home-preset-card"));
    if (originalCards.length < 2) return;

    // 카드 세트를 한 번 더 복제해 이어붙여서, 오른쪽에서 왼쪽으로 끊김 없이 계속 흐르도록 만든다.
    originalCards.forEach((card) => list.append(card.cloneNode(true)));
    autoScrollEnabled = true;
    // 자동 스크롤 중에는 이전/다음 버튼을 사용하지 않으므로 매 scroll/resize마다
    // 버튼 상태를 다시 계산하는 기존 리스너도 제거한다.
    list.removeEventListener("scroll", updateNavVisibility);
    window.removeEventListener("resize", updateNavVisibility);

    if (prevButton) prevButton.hidden = true;
    if (nextButton) nextButton.hidden = true;

    // 네이티브 이미지 드래그가 pointerleave를 방해하지 않도록 막는다.
    list.querySelectorAll("img").forEach((img) => {
      img.draggable = false;
    });

    let animationFrameId = 0;
    let isInViewport = false;
    let isPointerInside = false;
    let isFocusInside = list.contains(document.activeElement);
    let loopPoint = 0;

    function updateLoopPoint() {
      loopPoint = list.scrollWidth / 2;
    }

    function shouldAnimate() {
      return isInViewport
        && !document.hidden
        && !isPointerInside
        && !isFocusInside
        && loopPoint > 0;
    }

    function stopAnimation() {
      if (!animationFrameId) return;
      window.cancelAnimationFrame(animationFrameId);
      animationFrameId = 0;
    }

    function step() {
      animationFrameId = 0;
      if (!shouldAnimate()) return;

      list.scrollLeft += 0.6;
      if (list.scrollLeft >= loopPoint) {
        list.scrollLeft -= loopPoint;
      }
      animationFrameId = window.requestAnimationFrame(step);
    }

    function syncAnimationState() {
      if (shouldAnimate()) {
        if (!animationFrameId) {
          animationFrameId = window.requestAnimationFrame(step);
        }
      } else {
        stopAnimation();
      }
    }

    list.addEventListener("pointerenter", () => {
      isPointerInside = true;
      syncAnimationState();
    });
    list.addEventListener("pointerleave", () => {
      isPointerInside = false;
      syncAnimationState();
    });
    list.addEventListener("focusin", () => {
      isFocusInside = true;
      syncAnimationState();
    });
    list.addEventListener("focusout", () => {
      window.requestAnimationFrame(() => {
        isFocusInside = list.contains(document.activeElement);
        syncAnimationState();
      });
    });
    document.addEventListener("visibilitychange", syncAnimationState);

    updateLoopPoint();

    if ("ResizeObserver" in window) {
      const resizeObserver = new ResizeObserver(() => {
        updateLoopPoint();
        syncAnimationState();
      });
      resizeObserver.observe(list);
    } else {
      window.addEventListener("resize", () => {
        updateLoopPoint();
        syncAnimationState();
      }, { passive: true });
    }

    if ("IntersectionObserver" in window) {
      const observer = new IntersectionObserver((entries) => {
        const entry = entries[0];
        isInViewport = Boolean(entry?.isIntersecting && entry.intersectionRatio > 0);
        syncAnimationState();
      }, {
        root: null,
        rootMargin: "120px 0px",
        threshold: 0.01,
      });
      observer.observe(list);
    } else {
      isInViewport = true;
      syncAnimationState();
    }
  }

  function renderPresets(presets) {
    list.replaceChildren();
    list.setAttribute("aria-busy", "false");
    if (!presets.length) {
      const state = document.createElement("p");
      state.className = "home-preset-state";
      state.textContent = "현재 공개된 보물지도가 없습니다.";
      list.append(state);
      return;
    }

    presets.forEach((preset) => list.append(renderCard(preset)));
    window.requestAnimationFrame(updateNavVisibility);
    startAutoScroll();
  }

  const cachedPresets = readPresetCache();
  if (cachedPresets) {
    renderPresets(cachedPresets);
    return;
  }

  Api.get("/presets?page=0&size=4&sort=popular")
    .then((payload) => {
      const presets = Array.isArray(payload.data?.content) ? payload.data.content : [];
      writePresetCache(presets);
      renderPresets(presets);
    })
    .catch(() => {
      list.setAttribute("aria-busy", "false");
      list.innerHTML = '<p class="home-preset-state">인기 보물지도를 불러오지 못했습니다.</p>';
    });
})();
