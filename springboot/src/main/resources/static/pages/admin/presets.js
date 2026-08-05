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

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  }

  function openForm(preset) {
    editingId = preset?.presetId || null;
    document.querySelector("#preset-dialog-title").textContent = editingId ? "Presset 수정" : "Presset 등록";
    form.elements.title.value = preset?.title || "";
    form.elements.summary.value = preset?.summary || "";
    form.elements.description.value = preset?.description || "";
    form.elements.imageUrl.value = preset?.imageUrl || "";
    form.elements.category.value = preset?.category || "";
    form.elements.displayOrder.value = preset?.displayOrder ?? 0;
    form.elements.status.value = preset?.status || "ACTIVE";
    message.textContent = "";
    dialog.showModal();
  }

  function render() {
    if (!presets.length) {
      body.innerHTML = '<tr><td colspan="7">등록된 Presset이 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = presets.map((preset) => `<tr>
      <td>${escapeHtml(preset.title)}</td><td>${escapeHtml(preset.category)}</td><td>${escapeHtml(preset.status)}</td>
      <td>${preset.restaurantCount}</td><td>${preset.tagCount}</td><td>${preset.favoriteCount}</td>
      <td><button class="button button-secondary button-sm" data-edit="${preset.presetId}">수정</button><button class="button button-secondary button-sm" data-delete="${preset.presetId}">삭제</button></td>
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
    if (edit) openForm(presets.find((preset) => preset.presetId === Number(edit.dataset.edit)));
    const remove = event.target.closest("[data-delete]");
    if (!remove || !confirm("이 Presset을 삭제 상태로 변경할까요?")) return;
    try { await Api.delete(`/admin/presets/${remove.dataset.delete}`); await load(); }
    catch (error) { alert(error.message); }
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
      title: form.elements.title.value.trim(), summary: form.elements.summary.value.trim(),
      description: form.elements.description.value.trim(), imageUrl: form.elements.imageUrl.value.trim(),
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
