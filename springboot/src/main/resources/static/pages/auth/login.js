const loginForm = document.getElementById("login-form");
const loginMessage = document.getElementById("login-message");
const query = new URLSearchParams(window.location.search);

function safeNextPath() {
  const value = query.get("next");
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/";
}

const signupLink = document.querySelector(".auth-switch a");
if (signupLink && query.get("next")) {
  const signupQuery = new URLSearchParams({ next: safeNextPath() });
  signupLink.href = `/pages/auth/signup.html?${signupQuery.toString()}`;
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
      },
      { auth: false },
    );
    Api.setToken(response.data.token);
    window.location.replace(safeNextPath());
  } catch (error) {
    loginMessage.textContent = error.message;
  } finally {
    submitButton.disabled = false;
  }
});
