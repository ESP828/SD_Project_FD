/**
 * Spring Boot 정적 HTML 화면용 공통 REST 호출 도구.
 * 동일 출처의 `/api` 경로를 사용한다.
 */
const Api = {
  baseUrl: "/api",

  getToken() {
    return localStorage.getItem("accessToken");
  },

  setToken(token) {
    if (!token) {
      throw new Error("저장할 인증 토큰이 없습니다.");
    }
    localStorage.setItem("accessToken", token);
  },

  clearToken() {
    localStorage.removeItem("accessToken");
  },

  async request(path, { method = "GET", body, auth = true } = {}) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    const token = this.getToken();
    if (auth && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin",
    });

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json")
      ? await response.json()
      : await response.text();

    if (!response.ok) {
      if (response.status === 401) {
        this.clearToken();
      }
      const message =
        typeof payload === "object" && payload
          ? payload.message
          : `요청에 실패했습니다. (${response.status})`;
      throw new Error(message || `요청에 실패했습니다. (${response.status})`);
    }

    return payload;
  },

  get(path, options = {}) {
    return this.request(path, { ...options, method: "GET" });
  },

  post(path, body, options = {}) {
    return this.request(path, { ...options, method: "POST", body });
  },

  put(path, body, options = {}) {
    return this.request(path, { ...options, method: "PUT", body });
  },

  patch(path, body, options = {}) {
    return this.request(path, { ...options, method: "PATCH", body });
  },

  delete(path, options = {}) {
    return this.request(path, { ...options, method: "DELETE" });
  },
};
