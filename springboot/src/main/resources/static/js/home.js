(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  const prevButton = document.querySelector("#home-preset-prev");
  const nextButton = document.querySelector("#home-preset-next");

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

    let paused = false;
    list.addEventListener("pointerenter", () => { paused = true; });
    list.addEventListener("pointerleave", () => { paused = false; });
    list.addEventListener("touchstart", () => { paused = true; }, { passive: true });
    list.addEventListener("touchend", () => { paused = false; });

    function step() {
      if (!paused) {
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
      window.requestAnimationFrame(updateNavVisibility);
      startAutoScroll();
    })
    .catch(() => {
      list.setAttribute("aria-busy", "false");
      list.innerHTML = '<p class="home-preset-state">인기 보물지도를 불러오지 못했습니다.</p>';
    });
})();
