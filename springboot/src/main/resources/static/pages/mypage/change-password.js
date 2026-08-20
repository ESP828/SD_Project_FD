(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("change-password-gate");
  const loginLink = document.getElementById("change-password-login");
  const content = document.getElementById("change-password-content");
  const form = document.getElementById("change-password-form");
  const submitButton = document.getElementById("change-password-submit");
  const message = document.getElementById("change-password-message");

  if (!session || !gate || !loginLink || !content || !form || !submitButton || !message) {
    return;
  }

  if (!session.authenticated) {
    content.hidden = true;
    gate.hidden = false;
    loginLink.href = "/auth/login?next=" + encodeURIComponent(location.pathname);
    return;
  }

  const currentPasswordInput = document.getElementById("current-password");
  const newPasswordInput = document.getElementById("new-password");
  const newPasswordConfirmInput = document.getElementById("new-password-confirm");
  const newPasswordConfirmStatus = document.getElementById("new-password-confirm-status");

  function setMessage(text, isSuccess) {
    message.textContent = text;
    message.classList.toggle("is-success", Boolean(isSuccess));
  }

  function setStatus(element, text, isSuccess) {
    element.textContent = text;
    element.classList.toggle("is-success", Boolean(isSuccess));
  }

  function updatePasswordConfirmStatus() {
    if (!newPasswordConfirmInput.value) {
      setStatus(newPasswordConfirmStatus, "", false);
      return;
    }
    if (newPasswordInput.value === newPasswordConfirmInput.value) {
      setStatus(newPasswordConfirmStatus, "비밀번호가 일치합니다.", true);
    } else {
      setStatus(newPasswordConfirmStatus, "비밀번호가 일치하지 않습니다.", false);
    }
  }

  newPasswordInput.addEventListener("input", updatePasswordConfirmStatus);
  newPasswordConfirmInput.addEventListener("input", updatePasswordConfirmStatus);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setMessage("", false);

    if (!window.FooduckPassword?.isValid(newPasswordInput.value)) {
      const missing = window.FooduckPassword?.missingLabels(newPasswordInput.value) || [];
      setMessage(`비밀번호 조건을 확인해 주세요${missing.length ? `: ${missing.join(", ")}` : "."}`, false);
      newPasswordInput.reportValidity();
      return;
    }

    if (newPasswordInput.value !== newPasswordConfirmInput.value) {
      setStatus(newPasswordConfirmStatus, "비밀번호가 일치하지 않습니다.", false);
      return;
    }

    submitButton.disabled = true;
    const originalLabel = submitButton.textContent;
    submitButton.textContent = "변경 중...";
    try {
      await Api.patch("/mypage/password", {
        currentPassword: currentPasswordInput.value,
        newPassword: newPasswordInput.value,
        newPasswordConfirm: newPasswordConfirmInput.value,
      });
      setMessage("비밀번호가 변경되었습니다.", true);
      form.reset();
      window.requestAnimationFrame(updatePasswordConfirmStatus);
    } catch (error) {
      setMessage(error.message, false);
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = originalLabel;
    }
  });
})();
