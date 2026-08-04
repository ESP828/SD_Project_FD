const findIdForm = document.getElementById("find-id-form");
const findIdFormSection = document.getElementById("find-id-form-section");
const findIdResult = document.getElementById("find-id-result");
const findIdValue = document.getElementById("find-id-value");

const emailInput = document.getElementById("find-id-email");
const sendCodeBtn = document.getElementById("send-code-btn");
const verificationCodeInput = document.getElementById("find-id-code");
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

emailInput.addEventListener("input", () => {
  resetVerificationState();
});

sendCodeBtn.addEventListener("click", async () => {
  const email = emailInput.value.trim();
  if (!emailInput.checkValidity() || !email) {
    setStatus(emailVerifyStatus, "이메일 형식을 확인해 주세요.", false);
    return;
  }
  sendCodeBtn.disabled = true;
  const originalLabel = sendCodeBtn.textContent;
  sendCodeBtn.textContent = "발송 중...";
  setStatus(emailVerifyStatus, "인증번호를 보내고 있습니다. 최대 1분 정도 걸릴 수 있어요.", false);
  try {
    await Api.post("/auth/find-id/verification-code", { email }, { auth: false });
    verificationCodeInput.value = "";
    verificationCodeInput.focus();
    setStatus(emailVerifyStatus, "인증번호를 발송했습니다. 5분 이내에 입력해 주세요.", false);
  } catch (error) {
    setStatus(emailVerifyStatus, error.message, false);
  } finally {
    sendCodeBtn.disabled = false;
    sendCodeBtn.textContent = originalLabel;
  }
});

verifyCodeBtn.addEventListener("click", async () => {
  const email = emailInput.value.trim();
  const code = verificationCodeInput.value.trim();
  if (!/^[0-9]{6}$/.test(code)) {
    setStatus(emailVerifyStatus, "인증번호 6자리를 입력해 주세요.", false);
    return;
  }
  setStatus(emailVerifyStatus, "인증번호를 확인하고 있습니다.", false);
  try {
    const response = await Api.post("/auth/find-id/verify", { email, code }, { auth: false });
    setStatus(emailVerifyStatus, "인증번호가 확인되었습니다.", true);
    findIdValue.textContent = response.data.loginId;
    setTimeout(() => {
      findIdFormSection.hidden = true;
      findIdResult.hidden = false;
    }, 700);
  } catch {
    setStatus(emailVerifyStatus, "인증번호를 확인해주세요.", false);
  }
});

findIdForm.addEventListener("submit", (event) => {
  event.preventDefault();
});
