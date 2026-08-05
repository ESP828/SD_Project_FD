(() => {
  const list = document.querySelector("#home-preset-list");
  if (!list) return;

  const fallbackImage = "/images/foodduck-guide.png";

  function image(source, title) {
    const node = new Image();
    node.src = source || fallbackImage;
    node.alt = `${title || "Presset"} 대표 이미지`;
    node.loading = "lazy";
    node.addEventListener("error", () => {
      node.src = fallbackImage;
    }, { once: true });
    return node;
  }

  function renderCard(preset) {
    const link = document.createElement("a");
    link.className = "home-preset-card";
    link.href = `/pages/presset/detail.html?presetId=${encodeURIComponent(preset.presetId)}`;

    const visual = document.createElement("div");
    visual.className = "home-preset-visual";
    visual.append(image(preset.imageUrl, preset.title));

    const body = document.createElement("div");
    body.className = "home-preset-body";
    const title = document.createElement("h3");
    title.textContent = preset.title || "맛집 Presset";
    const summary = document.createElement("p");
    summary.textContent = preset.summary || "선별된 맛집을 확인해 보세요.";
    const meta = document.createElement("span");
    meta.textContent = `맛집 ${preset.restaurantCount || 0}곳 · 저장 ${preset.favoriteCount || 0}`;
    body.append(title, summary, meta);
    link.append(visual, body);
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
      presets.forEach((preset) => list.append(renderCard(preset)));
    })
    .catch(() => {
      list.setAttribute("aria-busy", "false");
      list.innerHTML = '<p class="home-preset-state">인기 Presset을 불러오지 못했습니다.</p>';
    });
})();
