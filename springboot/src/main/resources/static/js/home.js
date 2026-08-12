(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  function renderCard(preset, rank) {
    const link = document.createElement("a");
    link.className = "home-preset-card";
    link.href = `/pages/presset/detail.html?presetId=${encodeURIComponent(preset.presetId)}`;

    const visual = document.createElement("div");
    visual.className = "home-preset-visual";
    if (preset.imageUrl) {
      const img = new Image();
      img.src = preset.imageUrl;
      img.alt = "";
      img.loading = "lazy";
      visual.append(img);
    }
    const badge = document.createElement("span");
    badge.className = "home-preset-rank";
    badge.textContent = String(rank);
    visual.append(badge);
    link.append(visual);

    const body = document.createElement("div");
    body.className = "home-preset-body";
    const title = document.createElement("h3");
    title.textContent = preset.title || "맛집 Presset";
    body.append(title);

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

    const meta = document.createElement("span");
    meta.className = "home-preset-meta";
    meta.textContent = `🍴 맛집 ${preset.restaurantCount || 0}곳 · 🔖 저장 ${preset.favoriteCount || 0}회`;
    body.append(meta);

    const cta = document.createElement("span");
    cta.className = "home-preset-cta";
    cta.textContent = "presset 보러가기 →";
    body.append(cta);

    link.append(body);
    return link;
  }

  Api.get("/presets?page=0&size=4&sort=popular")
    .then((payload) => {
      const presets = Array.isArray(payload.data?.content) ? payload.data.content : [];
      list.replaceChildren();
      list.setAttribute("aria-busy", "false");
      if (!presets.length) {
        const state = document.createElement("p");
        state.className = "home-preset-state";
        state.textContent = "현재 공개된 Presset이 없습니다.";
        list.append(state);
        return;
      }
      presets.forEach((preset, index) => list.append(renderCard(preset, index + 1)));
    })
    .catch(() => {
      list.setAttribute("aria-busy", "false");
      list.innerHTML = '<p class="home-preset-state">인기 Presset을 불러오지 못했습니다.</p>';
    });
})();
