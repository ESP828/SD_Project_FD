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
const verificationField = document.getElementById("signup-verification-field");
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
  loginLink.href = `/auth/login?${loginQuery.toString()}`;
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
  verificationField.classList.remove("is-expanded");
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

  // 실제 메일 발송(수십 초 걸릴 수 있는 SMTP 통신)이 끝나기 전에 입력란부터 펼쳐서,
  // 버튼을 누르자마자 "인증번호 입력란이 나타나는" 반응을 바로 보여준다. 발송이 끝나기 전에도
  // 입력 자체는 막지 않는다 - 틀린 번호를 넣으면 인증확인 쪽에서 다시 확인하라고 안내하면
  // 되므로, 여기서 입력을 잠가 둘 이유가 없다.
  verificationField.classList.add("is-expanded");
  verificationCodeInput.disabled = false;
  verifyCodeBtn.disabled = false;
  verificationCodeInput.value = "";
  verificationCodeInput.focus();
  setStatus(emailVerifyStatus, "인증번호를 보내고 있습니다. 최대 1분 정도 걸릴 수 있어요.", false);
  try {
    await Api.post("/auth/email/verification-code", { email }, { auth: false });
    verifiedEmail = null;
    setStatus(emailVerifyStatus, "인증번호를 발송했습니다. 5분 이내에 입력해 주세요.", true);
  } catch (error) {
    // 발송이 실제로 실패했으면 존재하지 않는 인증번호를 입력하게 둘 수 없으니 다시 접는다.
    verificationField.classList.remove("is-expanded");
    verificationCodeInput.disabled = true;
    verifyCodeBtn.disabled = true;
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
    setStatus(emailVerifyStatus, "인증번호를 다시 확인해 주세요.", false);
    return;
  }
  verifyCodeBtn.disabled = true;
  try {
    await Api.post("/auth/email/verify", { email, code }, { auth: false });
    verifiedEmail = email;
    setStatus(emailVerifyStatus, "이메일 인증이 완료되었습니다.", true);
  } catch (error) {
    // 코드가 틀렸을 때 서버 에러 문구를 그대로 보여주는 대신, 다시 확인해 보라는
    // 한 가지 안내로 통일한다(만료/횟수초과/불일치를 사용자가 굳이 구분할 필요는 없다).
    verifiedEmail = null;
    setStatus(emailVerifyStatus, "인증번호를 다시 확인해 주세요.", false);
  } finally {
    verifyCodeBtn.disabled = false;
  }
});

const consentAllInput = document.getElementById("consent-all");
const consentTermsInput = document.getElementById("consent-terms");
const consentPrivacyInput = document.getElementById("consent-privacy");
const consentAgeInput = document.getElementById("consent-age");

function updateConsentAll() {
  consentAllInput.checked = consentTermsInput.checked
    && consentPrivacyInput.checked
    && consentAgeInput.checked;
}

consentAllInput.addEventListener("change", () => {
  consentTermsInput.checked = consentAllInput.checked;
  consentPrivacyInput.checked = consentAllInput.checked;
  consentAgeInput.checked = consentAllInput.checked;
});

consentTermsInput.addEventListener("change", updateConsentAll);
consentPrivacyInput.addEventListener("change", updateConsentAll);
consentAgeInput.addEventListener("change", updateConsentAll);

document.querySelectorAll(".consent-view-btn").forEach((button) => {
  button.addEventListener("click", () => {
    const dialog = document.getElementById(button.dataset.dialog);
    if (dialog) {
      dialog.showModal();
    }
  });
});

document.querySelectorAll(".legal-dialog").forEach((dialog) => {
  dialog.querySelectorAll("[data-close]").forEach((button) => {
    button.addEventListener("click", () => dialog.close());
  });
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
  if (!window.FooduckPassword?.isValid(passwordInput.value)) {
    const missing = window.FooduckPassword?.missingLabels(passwordInput.value) || [];
    signupMessage.textContent = `비밀번호 조건을 확인해 주세요${missing.length ? `: ${missing.join(", ")}` : "."}`;
    passwordInput.reportValidity();
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
  if (!consentTermsInput.checked || !consentPrivacyInput.checked || !consentAgeInput.checked) {
    signupMessage.textContent = "필수 약관에 동의하고 만 14세 이상임을 확인해 주세요.";
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
        ageConfirmed: consentAgeInput.checked,
      },
      { auth: false },
    );
    signupMessage.classList.add("is-success");
    signupMessage.textContent = "회원가입이 완료되었습니다. 로그인 화면으로 이동합니다.";
    window.setTimeout(() => {
      const next = safeNextPath();
      window.location.replace(
        next
          ? `/auth/login?${new URLSearchParams({ next }).toString()}`
          : "/auth/login",
      );
    }, 700);
  } catch (error) {
    signupMessage.textContent = error.message;
  } finally {
    submitButton.disabled = false;
  }
});
