(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  // 메인 인기 보물지도는 페이지를 오갈 때마다 같은 응답을 다시 기다릴 필요가 없다.
  // 브라우저 탭이 열려 있는 동안만 짧게 보관해 초기 체감 속도를 높이고,
  // 오래된 데이터는 화면을 막지 않은 채 뒤에서 다음 방문용으로 갱신한다.
  const HOME_PRESET_CACHE_KEY = "fooduck:home:popular-presets:all:v2";
  const HOME_PRESET_CACHE_TTL_MS = 2 * 60 * 1000;
  const HOME_PRESET_CACHE_STALE_MS = 30 * 60 * 1000;
  const HOME_PRESET_PAGE_SIZE = 24;
  const HOME_PRESET_CLONE_ATTRIBUTE = "data-home-preset-clone";
  const HOME_PRESET_KEY_ATTRIBUTE = "data-home-preset-key";
  const originalCardSelector = `.home-preset-card:not([${HOME_PRESET_CLONE_ATTRIBUTE}])`;
  const cloneCardSelector = `.home-preset-card[${HOME_PRESET_CLONE_ATTRIBUTE}]`;
  const reducedMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let autoScrollController = null;

  function showEmptyPresetState() {
    autoScrollController?.destroy();
    autoScrollController = null;
    const state = document.createElement("p");
    state.className = "home-preset-state";
    state.textContent = "현재 이미지가 등록된 보물지도가 없습니다.";
    list.replaceChildren(state);
    list.setAttribute("aria-busy", "false");
  }

  // 이미지 로드에 실패한 카드도 빈 이미지 카드와 동일하게 홈페이지에서만 숨긴다.
  // 원본 보물지도 데이터와 보물지도 목록에는 영향을 주지 않는다.
  list.addEventListener("error", (event) => {
    const image = event.target;
    if (!(image instanceof HTMLImageElement)) return;

    const card = image.closest(".home-preset-card");
    if (!card || !list.contains(card)) return;

    // 같은 카드를 복제한 항목까지 함께 제거해야 두 카드 세트의 폭이 계속 일치한다.
    // 그렇지 않으면 첫 카드의 이미지가 깨졌을 때 루프 기준 노드가 DOM에서 빠져
    // 이동 거리가 0이 되거나, 세트 경계에 빈 공간이 생긴다.
    const itemKey = card.getAttribute(HOME_PRESET_KEY_ATTRIBUTE);
    const failedCards = itemKey === null
      ? [card]
      : Array.from(list.querySelectorAll(".home-preset-card"))
        .filter((candidate) => candidate.getAttribute(HOME_PRESET_KEY_ATTRIBUTE) === itemKey);
    failedCards.forEach((failedCard) => failedCard.remove());

    if (!list.querySelector(originalCardSelector)) {
      showEmptyPresetState();
      return;
    }
    autoScrollController?.refresh();
  }, true);

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

  function renderCard(preset) {
    const thumbnail = [
      preset.imageUrl,
      ...(Array.isArray(preset.thumbnailImageUrls) ? preset.thumbnailImageUrls : []),
    ].find((value) => typeof value === "string" && value.trim())?.trim() || null;
    if (!thumbnail) return null;

    const link = document.createElement("a");
    link.className = "home-preset-card";
    link.href = `/presset/detail?presetId=${encodeURIComponent(preset.presetId)}`;
    link.setAttribute("aria-label", `${preset.title || "보물지도"} 상세 보기`);

    const visual = document.createElement("div");
    visual.className = "home-preset-visual";
    const img = new Image();
    img.src = thumbnail;
    img.alt = "";
    img.loading = "lazy";
    img.decoding = "async";
    img.fetchPriority = "low";
    img.draggable = false;
    visual.append(img);
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
    if (reducedMotionQuery.matches) return null;

    let animationFrame = 0;
    let resizeFrame = 0;
    let isVisible = !("IntersectionObserver" in window);
    let pointerPaused = false;
    let focusPaused = false;
    let isDestroyed = false;
    let lastFrameTime = 0;
    let loopPoint = 0;
    const frameInterval = 1000 / 30;
    const pixelsPerMillisecond = 0.036;

    function originalCards() {
      return Array.from(list.querySelectorAll(originalCardSelector));
    }

    function removeClones() {
      list.querySelectorAll(cloneCardSelector).forEach((clone) => clone.remove());
    }

    function appendCloneSet(cards) {
      const fragment = document.createDocumentFragment();
      cards.forEach((card) => {
        const clone = card.cloneNode(true);
        clone.setAttribute(HOME_PRESET_CLONE_ATTRIBUTE, "");
        clone.setAttribute("aria-hidden", "true");
        clone.tabIndex = -1;
        clone.querySelectorAll("a, button, input, select, textarea, [tabindex]")
          .forEach((interactive) => { interactive.tabIndex = -1; });
        fragment.append(clone);
      });
      list.append(fragment);
    }

    function normalizeScrollLeft(value) {
      if (loopPoint <= 0) return 0;
      return ((value % loopPoint) + loopPoint) % loopPoint;
    }

    function rebuildClones() {
      if (isDestroyed) return;
      const previousLoopPoint = loopPoint;
      const previousScrollLeft = list.scrollLeft;
      stopAnimation();
      removeClones();

      const cards = originalCards();
      cards.forEach((card, index) => {
        card.setAttribute(HOME_PRESET_KEY_ATTRIBUTE, String(index));
      });

      if (cards.length < 2) {
        loopPoint = 0;
        list.scrollLeft = 0;
        return;
      }

      // 한 세트의 폭이 화면보다 짧아도 루프 경계 오른쪽이 비지 않도록
      // 화면을 덮는 데 필요한 만큼 복제 세트를 추가한다.
      appendCloneSet(cards);
      const firstClone = list.querySelector(cloneCardSelector);
      loopPoint = firstClone ? firstClone.offsetLeft - cards[0].offsetLeft : 0;
      let cloneSetCount = 1;
      while (
        loopPoint > 0
        && list.scrollWidth + 0.5 < loopPoint + list.clientWidth
        && cloneSetCount < 20
      ) {
        appendCloneSet(cards);
        cloneSetCount += 1;
      }

      if (loopPoint <= 0) {
        list.scrollLeft = 0;
        return;
      }

      const progress = previousLoopPoint > 0
        ? (((previousScrollLeft % previousLoopPoint) + previousLoopPoint) % previousLoopPoint)
          / previousLoopPoint
        : 0;
      list.scrollLeft = progress * loopPoint;
      scheduleAnimation();
    }

    function isUserPaused() {
      return pointerPaused || focusPaused;
    }

    function stopAnimation() {
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      animationFrame = 0;
      lastFrameTime = 0;
    }

    function scheduleAnimation() {
      if (
        animationFrame
        || isDestroyed
        || loopPoint <= 0
        || !isVisible
        || document.hidden
        || isUserPaused()
      ) return;
      animationFrame = window.requestAnimationFrame(step);
    }

    function step(timestamp) {
      animationFrame = 0;
      if (!isVisible || document.hidden || isUserPaused()) return;
      if (!lastFrameTime) lastFrameTime = timestamp;
      const elapsed = Math.min(timestamp - lastFrameTime, 100);
      if (elapsed >= frameInterval) {
        lastFrameTime = timestamp;
        if (loopPoint > 0) {
          list.scrollLeft = normalizeScrollLeft(
            list.scrollLeft + elapsed * pixelsPerMillisecond,
          );
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
    const handleResize = () => {
      if (resizeFrame || isDestroyed) return;
      resizeFrame = window.requestAnimationFrame(() => {
        resizeFrame = 0;
        rebuildClones();
      });
    };
    const handlePageHide = (event) => {
      stopAnimation();
      if (!event.persisted) destroy();
    };
    const handlePageShow = () => {
      rebuildClones();
      scheduleAnimation();
    };
    const resizeObserver = "ResizeObserver" in window
      ? new ResizeObserver(handleResize)
      : null;

    function destroy() {
      if (isDestroyed) return;
      isDestroyed = true;
      stopAnimation();
      if (resizeFrame) window.cancelAnimationFrame(resizeFrame);
      resizeFrame = 0;
      visibilityObserver?.disconnect();
      resizeObserver?.disconnect();
      list.removeEventListener("pointerenter", pauseForPointer);
      list.removeEventListener("pointerleave", resumeFromPointer);
      list.removeEventListener("focusin", pauseForFocus);
      list.removeEventListener("focusout", resumeFromFocus);
      document.removeEventListener("visibilitychange", handleDocumentVisibility);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("pagehide", handlePageHide);
      window.removeEventListener("pageshow", handlePageShow);
      removeClones();
      list.scrollLeft = 0;
    }

    rebuildClones();
    list.addEventListener("pointerenter", pauseForPointer, { passive: true });
    list.addEventListener("pointerleave", resumeFromPointer, { passive: true });
    list.addEventListener("focusin", pauseForFocus);
    list.addEventListener("focusout", resumeFromFocus);
    document.addEventListener("visibilitychange", handleDocumentVisibility);
    window.addEventListener("resize", handleResize, { passive: true });
    window.addEventListener("pagehide", handlePageHide);
    window.addEventListener("pageshow", handlePageShow);
    resizeObserver?.observe(list);
    visibilityObserver?.observe(list);
    scheduleAnimation();

    return { refresh: rebuildClones, destroy };
  }

  function syncAutoScrollWithMotionPreference() {
    autoScrollController?.destroy();
    autoScrollController = null;
    if (!reducedMotionQuery.matches && list.querySelector(originalCardSelector)) {
      autoScrollController = startAutoScroll();
    }
  }

  function renderPresetPayload(payload) {
    const presets = Array.isArray(payload?.data?.content) ? payload.data.content : [];
    autoScrollController?.destroy();
    autoScrollController = null;
    list.replaceChildren();
    list.setAttribute("aria-busy", "false");
    const cards = presets
      .map((preset) => renderCard(preset))
      .filter(Boolean);
    if (!cards.length) {
      showEmptyPresetState();
      return;
    }
    list.append(...cards);
    syncAutoScrollWithMotionPreference();
  }

  async function fetchAllPresets() {
    const firstPayload = await Api.get(
      `/presets?page=0&size=${HOME_PRESET_PAGE_SIZE}&sort=popular`,
    );
    const firstPage = firstPayload?.data || {};
    const pagePayloads = [firstPayload];
    const reportedTotalPages = Number(firstPage.totalPages);
    const totalPages = Number.isSafeInteger(reportedTotalPages) && reportedTotalPages > 0
      ? reportedTotalPages
      : 1;

    // API의 최대 페이지 크기(24개)를 지키면서 마지막 페이지까지 모두 가져온다.
    // 순차 조회로 요청 폭주를 막고 인기순 카드 순서도 안정적으로 유지한다.
    for (let page = 1; page < totalPages; page += 1) {
      pagePayloads.push(await Api.get(
        `/presets?page=${page}&size=${HOME_PRESET_PAGE_SIZE}&sort=popular`,
      ));
    }

    const seenPresetIds = new Set();
    const content = pagePayloads
      .flatMap((payload) => Array.isArray(payload?.data?.content) ? payload.data.content : [])
      .filter((preset) => {
        const presetId = Number(preset?.presetId);
        if (!Number.isSafeInteger(presetId) || presetId <= 0) return true;
        if (seenPresetIds.has(presetId)) return false;
        seenPresetIds.add(presetId);
        return true;
      });

    return {
      ...firstPayload,
      data: {
        ...firstPage,
        content,
        page: 0,
        size: content.length,
        totalElements: content.length,
        totalPages: content.length ? 1 : 0,
        first: true,
        last: true,
      },
    };
  }

  async function fetchAndCachePresets({ render = true } = {}) {
    const payload = await fetchAllPresets();
    writePresetCache(payload);
    if (render) renderPresetPayload(payload);
    return payload;
  }

  if (typeof reducedMotionQuery.addEventListener === "function") {
    reducedMotionQuery.addEventListener("change", syncAutoScrollWithMotionPreference);
  } else if (typeof reducedMotionQuery.addListener === "function") {
    reducedMotionQuery.addListener(syncAutoScrollWithMotionPreference);
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
