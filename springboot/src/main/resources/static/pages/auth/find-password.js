const findPasswordForm = document.getElementById("find-password-form");
const findPasswordMessage = document.getElementById("find-password-message");
const goChangePasswordWrap = document.getElementById("go-change-password-wrap");

const loginIdInput = document.getElementById("find-password-login-id");
const emailInput = document.getElementById("find-password-email");
const sendCodeBtn = document.getElementById("send-code-btn");
const verificationCodeInput = document.getElementById("find-password-code");
const verifyCodeBtn = document.getElementById("verify-code-btn");
const emailVerifyStatus = document.getElementById("email-verify-status");

function setStatus(element, text, isSuccess) {
  element.textContent = text;
  element.classList.toggle("is-success", Boolean(isSuccess));
}

function resetVerificationState() {
  verificationCodeInput.value = "";
  setStatus(emailVerifyStatus, "", false);
}

loginIdInput.addEventListener("input", () => {
  resetVerificationState();
  findPasswordMessage.classList.remove("is-success");
  findPasswordMessage.textContent = "";
  goChangePasswordWrap.hidden = true;
});

emailInput.addEventListener("input", () => {
  resetVerificationState();
  findPasswordMessage.classList.remove("is-success");
  findPasswordMessage.textContent = "";
  goChangePasswordWrap.hidden = true;
});

sendCodeBtn.addEventListener("click", async () => {
  const loginId = loginIdInput.value.trim();
  const email = emailInput.value.trim();
  if (!loginId) {
    setStatus(emailVerifyStatus, "아이디를 입력해 주세요.", false);
    return;
  }
  if (!emailInput.checkValidity() || !email) {
    setStatus(emailVerifyStatus, "이메일 형식을 확인해 주세요.", false);
    return;
  }
  sendCodeBtn.disabled = true;
  const originalLabel = sendCodeBtn.textContent;
  sendCodeBtn.textContent = "발송 중...";
  setStatus(emailVerifyStatus, "인증번호를 보내고 있습니다. 최대 1분 정도 걸릴 수 있어요.", false);
  try {
    await Api.post("/auth/find-password/verification-code", { loginId, email }, { auth: false });
    verificationCodeInput.value = "";
    verificationCodeInput.focus();
    setStatus(emailVerifyStatus, "인증번호를 발송했습니다. 5분 이내에 입력해 주세요.", true);
  } catch (error) {
    setStatus(emailVerifyStatus, error.message, false);
  } finally {
    sendCodeBtn.disabled = false;
    sendCodeBtn.textContent = originalLabel;
  }
});

verifyCodeBtn.addEventListener("click", async () => {
  const loginId = loginIdInput.value.trim();
  const email = emailInput.value.trim();
  const code = verificationCodeInput.value.trim();
  if (!/^[0-9]{6}$/.test(code)) {
    setStatus(emailVerifyStatus, "인증번호 6자리를 입력해 주세요.", false);
    return;
  }
  const originalLabel = verifyCodeBtn.textContent;
  verifyCodeBtn.textContent = "발급 중...";
  setStatus(emailVerifyStatus, "인증번호를 확인하고 임시 비밀번호를 발급하고 있습니다. 최대 1분 정도 걸릴 수 있어요.", false);
  try {
    await Api.post("/auth/find-password/verify", { loginId, email, code }, { auth: false });
    findPasswordMessage.classList.add("is-success");
    findPasswordMessage.textContent = "임시 비밀번호를 이메일로 발송했습니다. 이메일을 확인한 뒤 로그인해 주세요.";
    setStatus(emailVerifyStatus, "", false);
    goChangePasswordWrap.hidden = false;
    loginIdInput.disabled = true;
    emailInput.disabled = true;
    sendCodeBtn.disabled = true;
    verificationCodeInput.disabled = true;
    verifyCodeBtn.disabled = true;
  } catch (error) {
    findPasswordMessage.classList.remove("is-success");
    findPasswordMessage.textContent = "";
    setStatus(emailVerifyStatus, error.message, false);
  } finally {
    verifyCodeBtn.textContent = originalLabel;
  }
});

findPasswordForm.addEventListener("submit", (event) => {
  event.preventDefault();
});
