(() => {
  const session = window.FooduckSession;
  const gate = document.querySelector("#preset-admin-gate");
  const dashboard = document.querySelector("#preset-admin-dashboard");
  if (!session?.isAdmin) { gate.hidden = false; return; }
  dashboard.hidden = false;

  const body = document.querySelector("#preset-admin-body");
  const dialog = document.querySelector("#preset-dialog");
  const form = document.querySelector("#preset-form");
  const message = document.querySelector("#preset-form-message");
  const requestedId = Number(new URLSearchParams(location.search).get("presetId"));
  let presets = [];
  let editingId = null;
  const pendingPresetDeleteIds = new Set();

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  }

  function openForm(preset) {
    editingId = preset?.presetId || null;
    document.querySelector("#preset-dialog-title").textContent = editingId ? "보물지도 수정" : "보물지도 등록";
    form.elements.title.value = preset?.title || "";
    form.elements.category.value = preset?.category || "";
    form.elements.displayOrder.value = preset?.displayOrder ?? 0;
    form.elements.status.value = preset?.status || "ACTIVE";
    message.textContent = "";
    dialog.showModal();
  }

  function render() {
    if (!presets.length) {
      body.innerHTML = '<tr><td colspan="7">등록된 보물지도가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = presets.map((preset) => `<tr>
      <td>${escapeHtml(preset.title)}</td><td>${escapeHtml(preset.category)}</td><td>${escapeHtml(preset.status)}</td>
      <td>${preset.restaurantCount}</td><td>${preset.tagCount}</td><td>${preset.favoriteCount}</td>
      <td><button class="button button-secondary button-sm" data-edit="${preset.presetId}">수정</button><button class="button button-danger button-sm" data-delete="${preset.presetId}">삭제</button></td>
    </tr>`).join("");
  }

  async function load() {
    try {
      const response = await Api.get("/admin/presets");
      presets = response.data || [];
      render();
      if (Number.isSafeInteger(requestedId) && requestedId > 0) {
        const target = presets.find((preset) => preset.presetId === requestedId);
        if (target) openForm(target);
      }
    } catch (error) {
      body.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  document.querySelector("#preset-create").addEventListener("click", () => openForm());
  dialog.querySelector("[data-close]").addEventListener("click", () => dialog.close());
  body.addEventListener("click", async (event) => {
    const edit = event.target.closest("[data-edit]");
    if (edit) {
      openForm(presets.find((preset) => preset.presetId === Number(edit.dataset.edit)));
      return;
    }
    const remove = event.target.closest("[data-delete]");
    if (!remove) return;

    const presetId = Number(remove.dataset.delete);
    const preset = presets.find((item) => Number(item.presetId) === presetId);
    if (!Number.isSafeInteger(presetId) || presetId <= 0 || !preset || pendingPresetDeleteIds.has(presetId)) {
      return;
    }

    pendingPresetDeleteIds.add(presetId);
    try {
      const confirmed = await window.FooduckConfirm.open({
        title: "보물지도를 삭제할까요?",
        message: `“${preset.title || "제목 없는 보물지도"}”을 삭제 상태로 변경합니다. 일반 화면에서 숨겨지고 대표 이미지도 제거됩니다.`,
        confirmLabel: "보물지도 삭제",
        pendingLabel: "삭제 중...",
        errorMessage: "보물지도를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        danger: true,
        onConfirm: () => Api.delete(`/admin/presets/${encodeURIComponent(presetId)}`),
      });
      if (confirmed) await load();
    } finally {
      pendingPresetDeleteIds.delete(presetId);
    }
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
      title: form.elements.title.value.trim(),
      category: form.elements.category.value.trim(), displayOrder: Number(form.elements.displayOrder.value) || 0,
      status: form.elements.status.value,
    };
    try {
      if (editingId) await Api.put(`/admin/presets/${editingId}`, payload);
      else await Api.post("/admin/presets", payload);
      dialog.close();
      await load();
    } catch (error) { message.textContent = error.message; }
  });
  load();
})();
