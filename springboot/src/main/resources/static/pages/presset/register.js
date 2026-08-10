(() => {
  const registerPath = "/pages/presset/register.html";
  const listPath = "/pages/presset/index.html?sort=latest";
  const createdMessageKey = "fooduck:preset-created";

  if (!window.FooduckSession?.authenticated) {
    location.replace(
      `/pages/auth/login.html?next=${encodeURIComponent(registerPath)}`,
    );
    return;
  }

  const createForm = document.querySelector("#preset-create-form");
  const createSubmit = document.querySelector("#preset-submit");
  const resultMessage = document.querySelector("#result-message");
  const categoryHiddenInput = document.querySelector("#preset-category");
  const categoryInput = document.querySelector("#preset-category-input");
  const categoryTokenList = document.querySelector("#preset-category-token-list");
  const categoryHint = document.querySelector("#preset-category-hint");
  const imageInput = document.querySelector("#preset-image-input");
  const imagePreview = document.querySelector("#preset-image-preview");
  const imageError = document.querySelector("#preset-image-error");

  if (
    !createForm || !createSubmit || !resultMessage ||
    !categoryHiddenInput || !categoryInput || !categoryTokenList ||
    !categoryHint || !imageInput || !imagePreview || !imageError
  ) {
    return;
  }

  const requiredCreateFields = [
    { name: "title", message: "제목을 입력해주세요", errorId: "preset-title-error" },
    { name: "category", message: "카테고리를 입력해주세요", errorId: "preset-category-error" },
  ];
  const MAX_CATEGORY_TOKENS = 3;
  const categoryTokens = [];
  const ALLOWED_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
  const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  let imagePreviewUrl = null;

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function syncCategoryHiddenInput() {
    categoryHiddenInput.value = categoryTokens.join(", ");
    categoryHiddenInput.dispatchEvent(new Event("input", { bubbles: true }));
  }

  function renderCategoryTokens() {
    categoryTokenList.replaceChildren();
    categoryTokens.forEach((token, index) => {
      const item = element("li", "preset-token");
      const remove = element("button", "preset-token-remove", "×");
      remove.type = "button";
      remove.setAttribute("aria-label", `${token} 삭제`);
      remove.addEventListener("click", () => {
        categoryTokens.splice(index, 1);
        renderCategoryTokens();
        syncCategoryHiddenInput();
        updateCategoryLimitState();
        categoryInput.focus();
      });
      item.append(document.createTextNode(token), remove);
      categoryTokenList.append(item);
    });
  }

  function updateCategoryLimitState() {
    const atLimit = categoryTokens.length >= MAX_CATEGORY_TOKENS;
    categoryInput.disabled = atLimit;
    categoryHint.textContent = atLimit
      ? "카테고리는 최대 3개까지 입력할 수 있습니다."
      : "쉼표(,) 또는 Enter로 구분해 최대 3개까지 입력할 수 있습니다.";
    categoryHint.classList.toggle("is-limit", atLimit);
  }

  function addCategoryToken(rawValue) {
    const value = rawValue.trim();
    if (!value) return false;
    if (categoryTokens.length >= MAX_CATEGORY_TOKENS) return false;
    if (categoryTokens.includes(value)) return false;
    categoryTokens.push(value);
    return true;
  }

  function commitCategoryInputValue() {
    const added = addCategoryToken(categoryInput.value);
    categoryInput.value = "";
    if (added) {
      renderCategoryTokens();
      syncCategoryHiddenInput();
    }
    updateCategoryLimitState();
  }

  function clearImagePreviewUrl() {
    if (!imagePreviewUrl) return;
    URL.revokeObjectURL(imagePreviewUrl);
    imagePreviewUrl = null;
  }

  function hideImagePreview() {
    clearImagePreviewUrl();
    imagePreview.hidden = true;
    imagePreview.removeAttribute("src");
  }

  function setCreateResult(message = "", type = "") {
    resultMessage.textContent = message;
    resultMessage.classList.toggle("is-success", type === "success");
    resultMessage.classList.toggle("is-error", type === "error");
  }

  function setFieldError(field, errorElement, message) {
    errorElement.textContent = message;
    field.setAttribute("aria-invalid", message ? "true" : "false");
  }

  function validateCreateForm() {
    let firstInvalid = null;
    requiredCreateFields.forEach(({ name, message, errorId }) => {
      const field = createForm.elements[name];
      const errorElement = document.querySelector(`#${errorId}`);
      const fieldMessage = field.value.trim() ? "" : message;
      setFieldError(field, errorElement, fieldMessage);
      if (fieldMessage && !firstInvalid) {
        firstInvalid = field.name === "category" ? categoryInput : field;
      }
    });
    firstInvalid?.focus();
    return firstInvalid === null;
  }

  async function submitPreset(formData) {
    const payload = {
      title: formData.title,
      category: formData.category.join(", "),
      isPublic: formData.isPublic,
    };
    const body = new FormData();
    body.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    if (formData.image) body.append("image", formData.image);
    return Api.post("/presets", body);
  }

  function createErrorMessage(error) {
    if (error instanceof TypeError || !Number.isInteger(error.status)) {
      return "네트워크 연결을 확인한 뒤 다시 시도해 주세요.";
    }
    if (error.status === 401) {
      return "로그인이 필요합니다. 로그인 후 다시 시도해 주세요.";
    }
    if (error.status === 403) {
      return "프리셋을 등록할 권한이 없습니다.";
    }
    if (error.status >= 500) {
      return "서버에서 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }
    if (error.status >= 400) {
      return error.message || "입력값을 확인해 주세요.";
    }
    return "등록 중 오류가 발생했습니다. 다시 시도해 주세요.";
  }

  categoryInput.addEventListener("keydown", (event) => {
    if (event.key === "," || event.key === "Enter") {
      event.preventDefault();
      commitCategoryInputValue();
    }
  });

  categoryInput.addEventListener("input", () => {
    if (!categoryInput.value.includes(",")) return;
    const parts = categoryInput.value.split(",");
    const trailing = parts.pop();
    parts.forEach((part) => addCategoryToken(part));
    categoryInput.value = trailing;
    renderCategoryTokens();
    syncCategoryHiddenInput();
    updateCategoryLimitState();
  });

  categoryInput.addEventListener("blur", () => {
    if (categoryInput.value.trim()) commitCategoryInputValue();
  });

  imageInput.addEventListener("change", () => {
    imageError.textContent = "";
    const file = imageInput.files?.[0];
    if (!file) {
      hideImagePreview();
      return;
    }
    if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
      imageError.textContent = "jpg, png, webp 형식의 이미지만 첨부할 수 있습니다.";
      imageInput.value = "";
      hideImagePreview();
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      imageError.textContent = "이미지 파일은 5MB 이하만 첨부할 수 있습니다.";
      imageInput.value = "";
      hideImagePreview();
      return;
    }
    clearImagePreviewUrl();
    imagePreviewUrl = URL.createObjectURL(file);
    imagePreview.src = imagePreviewUrl;
    imagePreview.hidden = false;
  });

  requiredCreateFields.forEach(({ name, errorId }) => {
    const field = createForm.elements[name];
    const errorElement = document.querySelector(`#${errorId}`);
    field.addEventListener("input", () => {
      if (field.value.trim()) setFieldError(field, errorElement, "");
      setCreateResult();
    });
  });

  createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setCreateResult();
    if (!validateCreateForm()) return;

    const formData = {
      title: createForm.elements.title.value.trim(),
      category: categoryTokens.slice(),
      isPublic: createForm.elements.is_public.checked,
      image: imageInput.files?.[0] || null,
    };

    createSubmit.disabled = true;
    createSubmit.setAttribute("aria-busy", "true");
    createSubmit.textContent = "등록 중...";
    try {
      const response = await submitPreset(formData);
      const presetId = response.data?.presetId ?? response.data?.preset_id ?? response.data;
      console.info("등록된 프리셋 ID:", presetId);
      try {
        sessionStorage.setItem(
          createdMessageKey,
          response.message || "프리셋이 등록되었습니다.",
        );
      } catch (_error) {
        // 성공 안내를 저장하지 못해도 이미 완료된 등록과 목록 이동은 유지한다.
      }
      location.assign(listPath);
    } catch (error) {
      console.error("프리셋 등록 실패", error);
      setCreateResult(createErrorMessage(error), "error");
    } finally {
      createSubmit.disabled = false;
      createSubmit.removeAttribute("aria-busy");
      createSubmit.textContent = "등록";
    }
  });

  window.addEventListener("pagehide", clearImagePreviewUrl, { once: true });
  updateCategoryLimitState();
})();
