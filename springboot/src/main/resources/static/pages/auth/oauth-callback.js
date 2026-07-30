(async () => {
  const message = document.getElementById("callback-message");
  const spinner = document.getElementById("callback-spinner");
  const retry = document.getElementById("callback-retry");
  const params = new URLSearchParams(window.location.search);
  const ticket = params.get("ticket");

  // 일회용 티켓이 브라우저 기록에 남아 있지 않도록 즉시 URL에서 제거한다.
  window.history.replaceState({}, document.title, window.location.pathname);

  if (!ticket) {
    spinner.hidden = true;
    retry.hidden = false;
    message.textContent = "소셜 로그인 교환 코드가 없습니다.";
    return;
  }

  try {
    const response = await Api.post(
      "/auth/oauth/exchange",
      { ticket },
      { auth: false },
    );
    Api.setToken(response.data.token);
    message.textContent = "로그인되었습니다. 홈으로 이동합니다.";
    window.location.replace("/");
  } catch (error) {
    spinner.hidden = true;
    retry.hidden = false;
    message.textContent = error.message;
  }
})();
