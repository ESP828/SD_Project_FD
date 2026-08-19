(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  function renderCard(preset, rank) {
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
      img.fetchPriority = "low";
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

    // 이미지 드래그가 포인터 이벤트를 가로채지 않도록 하고 디코딩은 렌더링과 분리한다.
    list.querySelectorAll("img").forEach((img) => {
      img.draggable = false;
      img.decoding = "async";
    });

    let animationFrame = 0;
    let isVisible = !("IntersectionObserver" in window);
    let pointerPaused = false;
    let focusPaused = false;
    let lastFrameTime = 0;
    const frameInterval = 1000 / 30;
    const pixelsPerMillisecond = 0.036;

    function isUserPaused() {
      return pointerPaused || focusPaused;
    }

    function stopAnimation() {
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      animationFrame = 0;
      lastFrameTime = 0;
    }

    function scheduleAnimation() {
      if (animationFrame || !isVisible || document.hidden || isUserPaused()) return;
      animationFrame = window.requestAnimationFrame(step);
    }

    function step(timestamp) {
      animationFrame = 0;
      if (!isVisible || document.hidden || isUserPaused()) return;
      if (!lastFrameTime) lastFrameTime = timestamp;
      const elapsed = Math.min(timestamp - lastFrameTime, 100);
      if (elapsed >= frameInterval) {
        lastFrameTime = timestamp;
        const loopPoint = list.scrollWidth / 2;
        if (loopPoint > 0) {
          list.scrollLeft += elapsed * pixelsPerMillisecond;
          if (list.scrollLeft >= loopPoint) list.scrollLeft -= loopPoint;
        }
      }
      scheduleAnimation();
    }

    const visibilityObserver = "IntersectionObserver" in window
      ? new IntersectionObserver(([entry]) => {
        isVisible = Boolean(entry?.isIntersecting);
        if (isVisible) scheduleAnimation();
        else stopAnimation();
      }, { rootMargin: "120px 0px", threshold: 0.01 })
      : null;

    visibilityObserver?.observe(list);
    const pauseForPointer = () => {
      pointerPaused = true;
      stopAnimation();
    };
    const resumeFromPointer = () => {
      pointerPaused = false;
      scheduleAnimation();
    };
    const pauseForFocus = () => {
      focusPaused = true;
      stopAnimation();
    };
    const resumeFromFocus = () => {
      window.requestAnimationFrame(() => {
        focusPaused = list.contains(document.activeElement);
        scheduleAnimation();
      });
    };
    const handleDocumentVisibility = () => {
      if (document.hidden) stopAnimation();
      else scheduleAnimation();
    };
    list.addEventListener("pointerenter", pauseForPointer, { passive: true });
    list.addEventListener("pointerleave", resumeFromPointer, { passive: true });
    list.addEventListener("focusin", pauseForFocus);
    list.addEventListener("focusout", resumeFromFocus);
    document.addEventListener("visibilitychange", handleDocumentVisibility);
    window.addEventListener("pagehide", () => {
      stopAnimation();
      visibilityObserver?.disconnect();
      document.removeEventListener("visibilitychange", handleDocumentVisibility);
    }, { once: true });
    scheduleAnimation();
  }

  Api.get("/presets?page=0&size=4&sort=popular")
    .then((payload) => {
      const presets = Array.isArray(payload.data?.content) ? payload.data.content : [];
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
      startAutoScroll();
    })
    .catch(() => {
      list.setAttribute("aria-busy", "false");
      list.innerHTML = '<p class="home-preset-state">인기 보물지도를 불러오지 못했습니다.</p>';
    });
})();
