/**
 * Spring Boot 정적 HTML 화면용 공통 REST 호출 도구.
 * 동일 출처의 `/api` 경로를 사용한다.
 */
const Api = {
  baseUrl: "/api",
  _refreshPromise: null,

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

  async request(path, { method = "GET", body, auth = true, _retried = false } = {}) {
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
      // 액세스 토큰 만료(401)면, 리프레시 토큰 쿠키로 새 토큰을 받아 원요청을 한 번만 재시도한다.
      if (response.status === 401 && auth && !_retried && path !== "/auth/refresh") {
        const refreshed = await this._refreshAccessToken();
        if (refreshed) {
          return this.request(path, { method, body, auth, _retried: true });
        }
      }
      if (response.status === 401) {
        this.clearToken();
      }
      const message =
        typeof payload === "object" && payload
          ? payload.message
          : `요청에 실패했습니다. (${response.status})`;
      const error = new Error(message || `요청에 실패했습니다. (${response.status})`);
      error.name = "ApiError";
      error.status = response.status;
      error.code = typeof payload === "object" && payload ? payload.code : null;
      error.data = typeof payload === "object" && payload ? payload.data : null;
      throw error;
    }

    return payload;
  },

  _refreshAccessToken() {
    if (!this._refreshPromise) {
      this._refreshPromise = this.request("/auth/refresh", { method: "POST", auth: false })
        .then((response) => {
          this.setToken(response.data.token);
          return true;
        })
        .catch(() => false)
        .finally(() => {
          this._refreshPromise = null;
        });
    }
    return this._refreshPromise;
  },

  async logout() {
    try {
      await this.request("/auth/logout", { method: "POST", auth: false });
    } finally {
      this.clearToken();
    }
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
