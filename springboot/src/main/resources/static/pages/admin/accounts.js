(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("accounts-access-gate");
  const dashboard = document.getElementById("accounts-dashboard");
  if (!session || !gate || !dashboard) return;

  if (!session.isAdmin) {
    gate.hidden = false;
    return;
  }
  dashboard.hidden = false;

  const roleButtons = document.querySelectorAll(".accounts-role-btn");
  const searchForm = document.getElementById("accounts-search-form");
  const searchInput = document.getElementById("accounts-search-input");
  const tableBody = document.getElementById("accounts-table-body");
  const countLabel = document.getElementById("accounts-count");
  const paginationHost = document.getElementById("accounts-pagination");
  const deleteModeToggle = document.getElementById("accounts-delete-mode-toggle");
  const PAGE_SIZE = 25;

  const editDialog = document.getElementById("account-edit-dialog");
  const editForm = document.getElementById("account-edit-form");
  const editTarget = editDialog.querySelector(".account-edit-target");
  const editRoleSelect = document.getElementById("account-edit-role");
  const editStatusSelect = document.getElementById("account-edit-status");
  const editStatusMsg = document.getElementById("account-edit-status-msg");

  let currentRole = "";
  let editingAccountId = null;
  let isDeleteMode = false;
  let currentAccounts = [];
  let currentPage = 0;
  const pendingAccountDeleteIds = new Set();

  const STATUS_LABELS = { ACTIVE: "활성", INACTIVE: "비활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴" };

  function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    }[char]));
  }

  function renderRows() {
    if (currentAccounts.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="7" class="accounts-empty">조건에 맞는 계정이 없습니다.</td></tr>';
      return;
    }
    tableBody.innerHTML = currentAccounts.map((account) => {
      const displayId = escapeHtml(account.loginId || "소셜 계정");
      const nickname = escapeHtml(account.nickname);
      const isAdminAccount = account.role === "ROLE_ADMIN";
      let actionButton;
      if (isAdminAccount) {
        actionButton = '<span class="accounts-admin-locked">관리자 계정</span>';
      } else if (isDeleteMode) {
        actionButton = `<button type="button" class="button button-sm accounts-delete-btn" data-delete="${account.accountId}" data-display-id="${displayId}">삭제</button>`;
      } else {
        actionButton = `<button type="button" class="button button-secondary button-sm" data-edit="${account.accountId}" data-role="${account.role}" data-status="${account.status}" data-display-id="${displayId}">수정</button>`;
      }
      return `
      <tr data-account-id="${account.accountId}">
        <td>${displayId}</td>
        <td>${nickname}</td>
        <td>${escapeHtml(account.email || "-")}</td>
        <td><span class="accounts-badge accounts-badge--role-${account.role}">${escapeHtml(account.roleLabel)}</span></td>
        <td><span class="accounts-badge accounts-badge--status-${account.status}">${STATUS_LABELS[account.status] || account.status}</span></td>
        <td>${formatDate(account.createdAt)}</td>
        <td class="accounts-actions">${actionButton}</td>
      </tr>
      `;
    }).join("");
  }

  async function loadAccounts(page = 0) {
    tableBody.innerHTML = '<tr><td colspan="7" class="accounts-loading">불러오는 중...</td></tr>';
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (searchInput.value.trim()) params.set("keyword", searchInput.value.trim());
      if (currentRole) params.set("role", currentRole);
      const response = await Api.get(`/admin/accounts?${params.toString()}`);
      const pageData = window.FooduckPagination.normalize(response.data, { pageKey: "page" });
      currentAccounts = pageData.content;
      currentPage = pageData.number;
      renderRows();
      countLabel.textContent = `총 ${pageData.totalElements.toLocaleString("ko-KR")}명`;
      window.FooduckPagination.render(paginationHost, pageData, (nextPage) => {
        loadAccounts(nextPage);
        paginationHost.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    } catch (error) {
      tableBody.innerHTML = `<tr><td colspan="7" class="accounts-empty">${error.message || "계정 목록을 불러오지 못했습니다."}</td></tr>`;
      countLabel.textContent = "";
      paginationHost.replaceChildren();
    }
  }

  deleteModeToggle.addEventListener("click", () => {
    isDeleteMode = !isDeleteMode;
    deleteModeToggle.classList.toggle("is-active", isDeleteMode);
    deleteModeToggle.textContent = isDeleteMode ? "취소" : "삭제";
    renderRows();
  });

  roleButtons.forEach((button) => {
    button.addEventListener("click", () => {
      roleButtons.forEach((b) => b.classList.remove("is-active"));
      button.classList.add("is-active");
      currentRole = button.dataset.role;
      loadAccounts();
    });
  });

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    loadAccounts();
  });

  tableBody.addEventListener("click", async (event) => {
    const editButton = event.target.closest("[data-edit]");
    const deleteButton = event.target.closest("[data-delete]");

    if (editButton) {
      editingAccountId = editButton.getAttribute("data-edit");
      editTarget.textContent = editButton.getAttribute("data-display-id");
      editRoleSelect.value = editButton.getAttribute("data-role");
      const status = editButton.getAttribute("data-status");
      editStatusSelect.value = status === "WITHDRAWN" || status === "INACTIVE" ? "ACTIVE" : status;
      editStatusMsg.textContent = "";
      editDialog.showModal();
      return;
    }

    if (deleteButton) {
      const accountId = Number(deleteButton.getAttribute("data-delete"));
      const account = currentAccounts.find((item) => Number(item.accountId) === accountId);
      if (
        !Number.isSafeInteger(accountId) || accountId <= 0 || !account ||
        account.role === "ROLE_ADMIN" || pendingAccountDeleteIds.has(accountId)
      ) {
        return;
      }

      const displayId = account.loginId || "소셜 계정";
      pendingAccountDeleteIds.add(accountId);
      try {
        const confirmed = await window.FooduckConfirm.open({
          title: "계정을 삭제할까요?",
          message: `“${displayId}” 계정을 탈퇴 처리합니다. 로그인 연결이 해제되고 활성 서비스 이용이 중단됩니다.`,
          confirmLabel: "계정 삭제",
          pendingLabel: "삭제 중...",
          errorMessage: "계정을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
          danger: true,
          onConfirm: () => Api.delete(`/admin/accounts/${encodeURIComponent(accountId)}`),
        });
        if (confirmed) await loadAccounts();
      } finally {
        pendingAccountDeleteIds.delete(accountId);
      }
    }
  });

  editDialog.querySelector("[data-close]").addEventListener("click", () => editDialog.close());

  editForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!editingAccountId) return;
    try {
      await Api.patch(`/admin/accounts/${editingAccountId}`, {
        role: editRoleSelect.value,
        status: editStatusSelect.value,
      });
      editDialog.close();
      loadAccounts();
    } catch (error) {
      editStatusMsg.textContent = error.message || "저장 중 오류가 발생했습니다.";
    }
  });

  loadAccounts();
})();
