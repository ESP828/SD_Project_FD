const signupForm = document.getElementById("signup-form");
const signupMessage = document.getElementById("signup-message");
const query = new URLSearchParams(window.location.search);
const recommendationNote = document.getElementById("recommendation-signup-note");

const loginIdInput = document.getElementById("signup-login-id");
const loginIdStatus = document.getElementById("login-id-status");
const checkLoginIdBtn = document.getElementById("check-login-id-btn");

const passwordInput = document.getElementById("signup-password");
const passwordConfirmInput = document.getElementById("signup-password-confirm");
const passwordConfirmStatus = document.getElementById("password-confirm-status");

const emailInput = document.getElementById("signup-email");
const sendCodeBtn = document.getElementById("send-code-btn");
const verificationCodeInput = document.getElementById("signup-verification-code");
const verifyCodeBtn = document.getElementById("verify-code-btn");
const emailVerifyStatus = document.getElementById("email-verify-status");

let checkedLoginId = null;
let verifiedEmail = null;

function safeNextPath() {
  const value = query.get("next");
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "";
}

if (recommendationNote && query.get("reason") === "recommendation") {
  recommendationNote.hidden = false;
}

const loginLink = document.querySelector(".auth-switch a");
if (loginLink && safeNextPath()) {
  const loginQuery = new URLSearchParams({ next: safeNextPath() });
  loginLink.href = `/pages/auth/login.html?${loginQuery.toString()}`;
}

function setStatus(element, text, isSuccess) {
  element.textContent = text;
  element.classList.toggle("is-success", Boolean(isSuccess));
}

loginIdInput.addEventListener("input", () => {
  checkedLoginId = null;
  setStatus(loginIdStatus, "", false);
});

checkLoginIdBtn.addEventListener("click", async () => {
  const loginId = loginIdInput.value.trim();
  if (!loginIdInput.checkValidity() || !loginId) {
    setStatus(loginIdStatus, "아이디 형식을 확인해 주세요. (4~20자, 영문/숫자/밑줄)", false);
    return;
  }
  checkLoginIdBtn.disabled = true;
  try {
    const response = await Api.get(
      `/auth/check-login-id?loginId=${encodeURIComponent(loginId)}`,
      { auth: false },
    );
    if (response.data.available) {
      checkedLoginId = loginId;
      setStatus(loginIdStatus, "사용할 수 있는 아이디입니다.", true);
    } else {
      checkedLoginId = null;
      setStatus(loginIdStatus, "이미 사용 중인 아이디입니다.", false);
    }
  } catch (error) {
    checkedLoginId = null;
    setStatus(loginIdStatus, error.message, false);
  } finally {
    checkLoginIdBtn.disabled = false;
  }
});

function updatePasswordConfirmStatus() {
  if (!passwordConfirmInput.value) {
    setStatus(passwordConfirmStatus, "", false);
    return;
  }
  if (passwordInput.value === passwordConfirmInput.value) {
    setStatus(passwordConfirmStatus, "비밀번호가 일치합니다.", true);
  } else {
    setStatus(passwordConfirmStatus, "비밀번호가 일치하지 않습니다.", false);
  }
}

passwordInput.addEventListener("input", updatePasswordConfirmStatus);
passwordConfirmInput.addEventListener("input", updatePasswordConfirmStatus);

emailInput.addEventListener("input", () => {
  verifiedEmail = null;
  verificationCodeInput.value = "";
  verificationCodeInput.disabled = true;
  verifyCodeBtn.disabled = true;
  setStatus(emailVerifyStatus, "", false);
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
    await Api.post("/auth/email/verification-code", { email }, { auth: false });
    verifiedEmail = null;
    verificationCodeInput.disabled = false;
    verifyCodeBtn.disabled = false;
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
  const email = emailInput.value.trim();
  const code = verificationCodeInput.value.trim();
  if (!/^[0-9]{6}$/.test(code)) {
    setStatus(emailVerifyStatus, "인증번호 6자리를 입력해 주세요.", false);
    return;
  }
  verifyCodeBtn.disabled = true;
  try {
    await Api.post("/auth/email/verify", { email, code }, { auth: false });
    verifiedEmail = email;
    setStatus(emailVerifyStatus, "이메일 인증이 완료되었습니다.", true);
  } catch (error) {
    verifiedEmail = null;
    setStatus(emailVerifyStatus, error.message, false);
  } finally {
    verifyCodeBtn.disabled = false;
  }
});

const consentAllInput = document.getElementById("consent-all");
const consentTermsInput = document.getElementById("consent-terms");
const consentPrivacyInput = document.getElementById("consent-privacy");

function updateConsentAll() {
  consentAllInput.checked = consentTermsInput.checked && consentPrivacyInput.checked;
}

consentAllInput.addEventListener("change", () => {
  consentTermsInput.checked = consentAllInput.checked;
  consentPrivacyInput.checked = consentAllInput.checked;
});

consentTermsInput.addEventListener("change", updateConsentAll);
consentPrivacyInput.addEventListener("change", updateConsentAll);

document.querySelectorAll(".consent-view-btn").forEach((button) => {
  button.addEventListener("click", () => {
    const dialog = document.getElementById(button.dataset.dialog);
    if (dialog) {
      dialog.showModal();
    }
  });
});

document.querySelectorAll(".legal-dialog").forEach((dialog) => {
  dialog.querySelector("[data-close]").addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      dialog.close();
    }
  });
});

signupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  signupMessage.classList.remove("is-success");
  signupMessage.textContent = "";

  const loginId = loginIdInput.value.trim();
  const email = emailInput.value.trim();

  if (checkedLoginId !== loginId) {
    signupMessage.textContent = "아이디 중복확인을 완료해 주세요.";
    return;
  }
  if (passwordInput.value !== passwordConfirmInput.value) {
    signupMessage.textContent = "비밀번호가 일치하지 않습니다.";
    return;
  }
  if (verifiedEmail !== email) {
    signupMessage.textContent = "이메일 인증을 완료해 주세요.";
    return;
  }
  if (!consentTermsInput.checked || !consentPrivacyInput.checked) {
    signupMessage.textContent = "이용약관과 개인정보처리방침에 모두 동의해 주세요.";
    return;
  }

  const submitButton = signupForm.querySelector("button[type='submit']");
  const form = new FormData(signupForm);
  submitButton.disabled = true;

  try {
    await Api.post(
      "/auth/signup",
      {
        loginId,
        password: form.get("password"),
        passwordConfirm: form.get("passwordConfirm"),
        email,
        nickname: form.get("nickname"),
      },
      { auth: false },
    );
    signupMessage.classList.add("is-success");
    signupMessage.textContent = "회원가입이 완료되었습니다. 로그인 화면으로 이동합니다.";
    window.setTimeout(() => {
      const next = safeNextPath();
      window.location.replace(
        next
          ? `/pages/auth/login.html?${new URLSearchParams({ next }).toString()}`
          : "/pages/auth/login.html",
      );
    }, 700);
  } catch (error) {
    signupMessage.textContent = error.message;
  } finally {
    submitButton.disabled = false;
  }
});
