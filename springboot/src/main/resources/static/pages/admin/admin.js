(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("admin-access-gate");
  const dashboard = document.getElementById("admin-dashboard");
  if (!session || !gate || !dashboard) return;

  if (!session.isAdmin) {
    gate.hidden = false;
    return;
  }

  dashboard.hidden = false;

  const list = document.getElementById("admin-application-list");
  if (!list) return;

  Api.get("/admin/business-applications")
    .then((response) => {
      if (!Array.isArray(response.data) || response.data.length === 0) {
        list.innerHTML = "<li>신청 내역이 없습니다.</li>";
        return;
      }

      list.innerHTML = response.data.map((item) => `
        <li class="management-list-item">
          <strong>${item.businessName}</strong>
          <span>${item.representativeName} · ${item.contact}</span>
          <span>상태: ${item.status}</span>
          <button type="button" data-approve="${item.applicationId}">승인</button>
          <button type="button" data-reject="${item.applicationId}">거절</button>
        </li>
      `).join("");
    })
    .catch((error) => {
      list.innerHTML = `<li>${error.message || "신청 목록을 불러오지 못했습니다."}</li>`;
    });

  list.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-approve], button[data-reject]");
    if (!button) return;

    const applicationId = button.getAttribute("data-approve") || button.getAttribute("data-reject");
    try {
      if (button.hasAttribute("data-approve")) {
        await Api.patch(`/admin/business-applications/${applicationId}/approve`);
      } else {
        const rejectReason = window.prompt("거절 사유를 입력하세요.");
        if (!rejectReason || !rejectReason.trim()) return;
        await Api.patch(`/admin/business-applications/${applicationId}/reject?rejectReason=${encodeURIComponent(rejectReason.trim())}`);
      }
      window.location.reload();
    } catch (error) {
      window.alert(error.message || "처리 중 오류가 발생했습니다.");
    }
  });
})();
