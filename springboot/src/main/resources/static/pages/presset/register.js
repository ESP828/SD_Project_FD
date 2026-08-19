(() => {
  const listPath = "/pages/presset/index.html?sort=latest";
  const createdMessageKey = "fooduck:preset-created";
  const requestedQuery = new URLSearchParams(location.search);
  const requestedPresetId = requestedQuery.get("presetId");
  const parsedPresetId = Number(requestedPresetId);
  const editPresetId = requestedPresetId !== null && Number.isSafeInteger(parsedPresetId) && parsedPresetId > 0
    ? parsedPresetId
    : null;
  const hasInvalidEditId = requestedPresetId !== null && editPresetId === null;
  const isEditMode = editPresetId !== null;
  const currentPath = `${location.pathname}${location.search}`;

  if (!window.FooduckSession?.authenticated) {
    location.replace(
      `/pages/auth/login.html?next=${encodeURIComponent(currentPath)}`,
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
  const modeLabel = document.querySelector("#preset-register-mode-label");
  const formTitle = document.querySelector("#preset-register-form-title");
  const backLink = document.querySelector("#preset-back-link");
  const cancelLink = document.querySelector("#preset-cancel-link");

  if (
    !createForm || !createSubmit || !resultMessage ||
    !categoryHiddenInput || !categoryInput || !categoryTokenList ||
    !categoryHint || !imageInput || !imagePreview || !imageError ||
    !modeLabel || !formTitle || !backLink || !cancelLink
  ) {
    return;
  }

  const requiredCreateFields = [
    { name: "title", message: "제목을 입력해주세요", errorId: "preset-title-error" },
    { name: "category", message: "태그를 입력해주세요", errorId: "preset-category-error" },
  ];
  const MAX_CATEGORY_TOKENS = 3;
  const categoryTokens = [];
  const ALLOWED_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
  const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  let imagePreviewUrl = null;
  let existingImageUrl = null;
  let formReady = !isEditMode && !hasInvalidEditId;

  function element(tagName, className = "", text = "") {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function detailPath(presetId) {
    return `/pages/presset/detail.html?presetId=${encodeURIComponent(presetId)}`;
  }

  function setBackLink(label, href) {
    const arrow = element("span", "", "←");
    arrow.setAttribute("aria-hidden", "true");
    backLink.replaceChildren(arrow, document.createTextNode(` ${label}`));
    backLink.href = href;
    cancelLink.href = href;
  }

  function applyPageMode() {
    const modeText = isEditMode ? "수정" : "등록";
    document.title = `보물지도 ${modeText} · 푸드덕`;
    modeLabel.textContent = modeText;
    formTitle.textContent = isEditMode
      ? "보물지도 정보를 수정해 주세요"
      : "보물지도 정보를 입력해 주세요";
    createSubmit.textContent = modeText;
    if (isEditMode) {
      setBackLink("보물지도 상세로 돌아가기", detailPath(editPresetId));
    } else {
      setBackLink("보물지도로 돌아가기", "/pages/presset/index.html");
    }
  }

  function parseCategoryTokens(category) {
    return String(category || "")
      .split(",")
      .map((token) => token.trim())
      .filter((token, index, tokens) => token && tokens.indexOf(token) === index)
      .slice(0, MAX_CATEGORY_TOKENS);
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
      ? "태그는 최대 3개까지 입력할 수 있습니다."
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

  function restoreImagePreview() {
    clearImagePreviewUrl();
    if (existingImageUrl) {
      imagePreview.src = existingImageUrl;
      imagePreview.hidden = false;
      return;
    }
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
    return isEditMode
      ? Api.put(`/presets/${editPresetId}`, body)
      : Api.post("/presets", body);
  }

  async function loadPresetForEdit() {
    createForm.inert = true;
    createForm.setAttribute("aria-busy", "true");
    createSubmit.disabled = true;
    setCreateResult("수정할 보물지도 정보를 불러오고 있습니다.");
    try {
      const response = await Api.get(`/presets/${editPresetId}`);
      const data = response.data || {};
      if (!data.isOwner) {
        throw Object.assign(new Error("작성자만 보물지도를 수정할 수 있습니다."), { status: 403 });
      }
      createForm.elements.title.value = data.title || "";
      categoryTokens.splice(0, categoryTokens.length, ...parseCategoryTokens(data.category));
      renderCategoryTokens();
      syncCategoryHiddenInput();
      updateCategoryLimitState();
      createForm.elements.is_public.checked = data.isPublic !== false;
      existingImageUrl = data.imageUrl || null;
      restoreImagePreview();
      formReady = true;
      setCreateResult();
    } catch (error) {
      formReady = false;
      setCreateResult(createErrorMessage(error), "error");
    } finally {
      createForm.inert = !formReady;
      createForm.removeAttribute("aria-busy");
      createSubmit.disabled = !formReady;
    }
  }

  function createErrorMessage(error) {
    if (error instanceof TypeError || !Number.isInteger(error.status)) {
      return "네트워크 연결을 확인한 뒤 다시 시도해 주세요.";
    }
    if (error.status === 401) {
      return "로그인이 필요합니다. 로그인 후 다시 시도해 주세요.";
    }
    if (error.status === 403) {
      return isEditMode
        ? "작성자만 이 보물지도를 수정할 수 있습니다."
        : "보물지도를 등록할 권한이 없습니다.";
    }
    if (error.status === 404) {
      return "수정할 보물지도를 찾을 수 없습니다.";
    }
    if (error.status >= 500) {
      return "서버에서 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }
    if (error.status >= 400) {
      return error.message || "입력값을 확인해 주세요.";
    }
    return `${isEditMode ? "수정" : "등록"} 중 오류가 발생했습니다. 다시 시도해 주세요.`;
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
      restoreImagePreview();
      return;
    }
    if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
      imageError.textContent = "jpg, png, webp 형식의 이미지만 첨부할 수 있습니다.";
      imageInput.value = "";
      restoreImagePreview();
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      imageError.textContent = "이미지 파일은 5MB 이하만 첨부할 수 있습니다.";
      imageInput.value = "";
      restoreImagePreview();
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
    if (!formReady) return;
    if (!validateCreateForm()) return;

    const formData = {
      title: createForm.elements.title.value.trim(),
      category: categoryTokens.slice(),
      isPublic: createForm.elements.is_public.checked,
      image: imageInput.files?.[0] || null,
    };

    createSubmit.disabled = true;
    createSubmit.setAttribute("aria-busy", "true");
    createSubmit.textContent = `${isEditMode ? "수정" : "등록"} 중...`;
    try {
      const response = await submitPreset(formData);
      const savedPresetId = isEditMode
        ? editPresetId
        : response.data?.presetId ?? response.data?.preset_id ?? response.data;
      console.info(`${isEditMode ? "수정" : "등록"}된 보물지도 ID:`, savedPresetId);
      try {
        sessionStorage.setItem(
          createdMessageKey,
          response.message || `보물지도가 ${isEditMode ? "수정" : "등록"}되었습니다.`,
        );
      } catch (_error) {
        // 성공 안내 저장이 불가능해도 완료된 저장과 페이지 이동은 유지한다.
      }
      location.assign(isEditMode ? detailPath(savedPresetId) : listPath);
    } catch (error) {
      console.error(`보물지도 ${isEditMode ? "수정" : "등록"} 실패`, error);
      setCreateResult(createErrorMessage(error), "error");
    } finally {
      createSubmit.disabled = false;
      createSubmit.removeAttribute("aria-busy");
      createSubmit.textContent = isEditMode ? "수정" : "등록";
    }
  });

  window.addEventListener("pagehide", clearImagePreviewUrl, { once: true });
  applyPageMode();
  updateCategoryLimitState();
  if (hasInvalidEditId) {
    formReady = false;
    createForm.inert = true;
    createSubmit.disabled = true;
    setCreateResult("올바른 보물지도 번호가 필요합니다.", "error");
  } else if (isEditMode) {
    loadPresetForEdit();
  }
})();
