const signupForm = document.getElementById("signup-form");
const signupMessage = document.getElementById("signup-message");
const query = new URLSearchParams(window.location.search);
const recommendationNote = document.getElementById("recommendation-signup-note");

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

signupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  signupMessage.classList.remove("is-success");
  signupMessage.textContent = "";

  const submitButton = signupForm.querySelector("button[type='submit']");
  const form = new FormData(signupForm);
  submitButton.disabled = true;

  try {
    await Api.post(
      "/auth/signup",
      {
        loginId: form.get("loginId"),
        password: form.get("password"),
        email: form.get("email"),
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
