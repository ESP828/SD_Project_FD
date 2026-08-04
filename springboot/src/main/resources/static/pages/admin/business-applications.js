(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("apps-access-gate");
  const dashboard = document.getElementById("apps-dashboard");
  if (!session || !gate || !dashboard) return;

  if (!session.isAdmin) {
    gate.hidden = false;
    return;
  }
  dashboard.hidden = false;

  const statusButtons = document.querySelectorAll(".apps-status-btn");
  const tableBody = document.getElementById("apps-table-body");
  const countLabel = document.getElementById("apps-count");

  let currentStatus = "";
  let applications = [];

  const STATUS_LABELS = { PENDING: "대기", APPROVED: "승인", REJECTED: "거절", CANCELED: "취소" };

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
    const filtered = currentStatus
      ? applications.filter((app) => app.status === currentStatus)
      : applications;

    if (filtered.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="8" class="apps-empty">조건에 맞는 신청이 없습니다.</td></tr>';
      countLabel.textContent = "";
      return;
    }

    tableBody.innerHTML = filtered.map((app) => {
      const applicant = escapeHtml(app.applicantLoginId || app.applicantNickname || "알 수 없음");
      const nickname = escapeHtml(app.applicantNickname || "");
      const actions = app.status === "PENDING"
        ? `
          <button type="button" class="button button-primary button-sm" data-approve="${app.applicationId}">승인</button>
          <button type="button" class="button button-sm apps-reject-btn" data-reject="${app.applicationId}">거절</button>
        `
        : app.status === "REJECTED" && app.rejectReason
          ? `<span class="apps-reject-reason">사유: ${escapeHtml(app.rejectReason)}</span>`
          : "-";

      return `
      <tr data-application-id="${app.applicationId}">
        <td class="apps-applicant"><strong>${applicant}</strong><span>${nickname}</span></td>
        <td>${escapeHtml(app.businessName)}</td>
        <td>${escapeHtml(app.businessNumber)}</td>
        <td>${escapeHtml(app.representativeName)}</td>
        <td>${escapeHtml(app.contact)}</td>
        <td><span class="apps-badge apps-badge--${app.status}">${STATUS_LABELS[app.status] || app.status}</span></td>
        <td>${formatDate(app.createdAt)}</td>
        <td class="apps-actions">${actions}</td>
      </tr>
      `;
    }).join("");

    countLabel.textContent = `총 ${filtered.length}건`;
  }

  async function loadApplications() {
    tableBody.innerHTML = '<tr><td colspan="8" class="apps-loading">불러오는 중...</td></tr>';
    try {
      const response = await Api.get("/admin/business-applications");
      applications = response.data || [];
      renderRows();
    } catch (error) {
      tableBody.innerHTML = `<tr><td colspan="8" class="apps-empty">${error.message || "신청 목록을 불러오지 못했습니다."}</td></tr>`;
      countLabel.textContent = "";
    }
  }

  statusButtons.forEach((button) => {
    button.addEventListener("click", () => {
      statusButtons.forEach((b) => b.classList.remove("is-active"));
      button.classList.add("is-active");
      currentStatus = button.dataset.status;
      renderRows();
    });
  });

  tableBody.addEventListener("click", async (event) => {
    const approveButton = event.target.closest("[data-approve]");
    const rejectButton = event.target.closest("[data-reject]");

    if (approveButton) {
      const applicationId = approveButton.getAttribute("data-approve");
      try {
        await Api.patch(`/admin/business-applications/${applicationId}/approve`);
        loadApplications();
      } catch (error) {
        window.alert(error.message || "승인 처리 중 오류가 발생했습니다.");
      }
      return;
    }

    if (rejectButton) {
      const applicationId = rejectButton.getAttribute("data-reject");
      const rejectReason = window.prompt("거절 사유를 입력하세요.");
      if (!rejectReason || !rejectReason.trim()) return;
      try {
        await Api.patch(`/admin/business-applications/${applicationId}/reject?rejectReason=${encodeURIComponent(rejectReason.trim())}`);
        loadApplications();
      } catch (error) {
        window.alert(error.message || "거절 처리 중 오류가 발생했습니다.");
      }
    }
  });

  loadApplications();
})();
