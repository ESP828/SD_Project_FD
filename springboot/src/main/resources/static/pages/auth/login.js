const loginForm = document.getElementById("login-form");
const loginMessage = document.getElementById("login-message");
const query = new URLSearchParams(window.location.search);
const AUTH_NEXT_STORAGE_KEY = "fooduck:auth:next";

function safeNextPath() {
  const value = query.get("next");
  if (!value || !value.startsWith("/") || value.startsWith("//")) return "/";
  try {
    const target = new URL(value, window.location.origin);
    if (target.origin !== window.location.origin) return "/";
    return `${target.pathname}${target.search}${target.hash}`;
  } catch (_error) {
    return "/";
  }
}

const nextPath = safeNextPath();
try {
  if (nextPath === "/") sessionStorage.removeItem(AUTH_NEXT_STORAGE_KEY);
  else sessionStorage.setItem(AUTH_NEXT_STORAGE_KEY, nextPath);
} catch (_error) {
  // 저장 공간을 사용할 수 없어도 일반 로그인 next 이동은 그대로 동작한다.
}

const signupLink = document.querySelector(".auth-switch a");
if (signupLink && query.get("next")) {
  const signupQuery = new URLSearchParams({ next: nextPath });
  signupLink.href = `/auth/signup?${signupQuery.toString()}`;
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginMessage.textContent = "";

  const submitButton = loginForm.querySelector("button[type='submit']");
  const form = new FormData(loginForm);
  submitButton.disabled = true;

  try {
    const response = await Api.post(
      "/auth/login",
      {
        loginId: form.get("loginId"),
        password: form.get("password"),
        rememberLogin: Boolean(form.get("rememberLogin")),
      },
      { auth: false },
    );
    Api.setToken(response.data.token);
    try {
      sessionStorage.removeItem(AUTH_NEXT_STORAGE_KEY);
    } catch (_error) {
      // 저장 공간 정리에 실패해도 검증된 next 이동은 계속한다.
    }
    window.location.replace(nextPath);
  } catch (error) {
    loginMessage.textContent = error.message;
  } finally {
    submitButton.disabled = false;
  }
});
