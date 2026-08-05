(() => {
  const session = window.FooduckSession;
  const loginGate = document.getElementById("business-login-gate");
  const application = document.getElementById("business-application");
  const dashboard = document.getElementById("business-dashboard");
  if (!session || !loginGate || !application || !dashboard) return;

  const state = {
    form: application.querySelector(".application-form"),
    submitButton: application.querySelector("button[type='submit']"),
    statusList: document.getElementById("business-application-list"),
  };

  if (!session.authenticated) {
    loginGate.hidden = false;
    return;
  }

  if (session.canManageBusiness) {
    dashboard.hidden = false;
    return;
  }

  application.hidden = false;
  state.submitButton.disabled = false;
  state.submitButton.textContent = "신청 제출";
  state.form.querySelectorAll("input, textarea").forEach((element) => {
    element.disabled = false;
  });

  Api.get("/business/applications")
    .then((response) => {
      const applications = Array.isArray(response.data) ? response.data : [];
      if (state.statusList) {
        state.statusList.innerHTML = applications.length === 0
          ? "<li>아직 제출한 신청이 없습니다.</li>"
          : applications.map((item) => `<li><strong>${item.businessName}</strong> · ${item.status}${item.rejectReason ? ` · ${item.rejectReason}` : ""}</li>`).join("");
      }
      if (applications.some((item) => item.status === "PENDING")) {
        state.submitButton.disabled = true;
        state.submitButton.textContent = "승인 대기 중";
      }
    })
    .catch((error) => {
      if (state.statusList) state.statusList.innerHTML = `<li>${error.message || "신청 내역을 불러오지 못했습니다."}</li>`;
    });

  state.form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
      businessName: state.form.elements.businessName.value.trim(),
      businessNumber: state.form.elements.businessNumber.value.trim(),
      representativeName: state.form.elements.representativeName.value.trim(),
      openedAt: state.form.elements.openedAt.value,
      contact: state.form.elements.contact.value.trim(),
      reason: state.form.elements.reason.value.trim(),
    };

    state.submitButton.disabled = true;
    state.submitButton.textContent = "제출 중...";
    try {
      const result = await Api.post("/business/applications", payload);
      state.submitButton.textContent = "신청 완료";
      state.form.reset();
      if (state.statusList) {
        state.statusList.innerHTML = `<li>신청이 접수되었습니다. 상태: ${result.data.status}</li>`;
      }
    } catch (error) {
      state.submitButton.disabled = false;
      state.submitButton.textContent = "신청 제출";
      window.alert(error.message || "신청 처리 중 오류가 발생했습니다.");
    }
  });
})();
