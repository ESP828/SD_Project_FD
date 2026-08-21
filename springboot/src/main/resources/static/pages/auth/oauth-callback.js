(async () => {
  const message = document.getElementById("callback-message");
  const spinner = document.getElementById("callback-spinner");
  const retry = document.getElementById("callback-retry");
  const params = new URLSearchParams(window.location.search);
  const ticket = params.get("ticket");
  const authNextStorageKey = "fooduck:auth:next";
  let nextPath = "/";

  try {
    const storedNextPath = sessionStorage.getItem(authNextStorageKey) || "";
    if (storedNextPath.startsWith("/") && !storedNextPath.startsWith("//")) {
      const target = new URL(storedNextPath, window.location.origin);
      if (target.origin === window.location.origin) {
        nextPath = `${target.pathname}${target.search}${target.hash}`;
        retry.href = `/auth/login?next=${encodeURIComponent(nextPath)}`;
      } else {
        sessionStorage.removeItem(authNextStorageKey);
      }
    }
  } catch (_error) {
    nextPath = "/";
    try {
      sessionStorage.removeItem(authNextStorageKey);
    } catch (_storageError) {
      // 저장 공간을 사용할 수 없으면 기존처럼 홈으로 이동한다.
    }
  }

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
    try {
      sessionStorage.removeItem(authNextStorageKey);
    } catch (_error) {
      // 저장 공간 정리에 실패해도 검증된 경로 이동은 계속한다.
    }
    message.textContent = nextPath === "/"
      ? "로그인되었습니다. 홈으로 이동합니다."
      : "로그인되었습니다. 이전 페이지로 이동합니다.";
    window.location.replace(nextPath);
  } catch (error) {
    spinner.hidden = true;
    retry.hidden = false;
    message.textContent = error.message;
  }
})();
