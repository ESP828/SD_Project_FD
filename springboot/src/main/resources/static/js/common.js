(() => {
  const SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  const RECOMMENDATION_PATH = "/recommendation";
  const RECOMMENDATION_LOGIN_PATH =
    "/auth/login?next=" +
    encodeURIComponent(RECOMMENDATION_PATH);
  const AUTHORITY_LABELS = Object.freeze({
    ROLE_USER: "일반 사용자",
    ROLE_BUSINESS: "사업자",
    ROLE_ADMIN: "관리자",
  });
  const AUTHORITY_PRIORITY = Object.freeze([
    "ROLE_ADMIN",
    "ROLE_BUSINESS",
    "ROLE_USER",
  ]);

  const FOODUCK_CUSTOM_EMOJI_BASE = "/images/emojis/pepe/";
  const FOODUCK_CUSTOM_EMOJIS = Object.freeze([
    [":pepe_laugh:", "웃음", "pepe_laugh.gif"],
    [":pepe_popcorn:", "팝콘", "pepe_popcorn.gif"],
    [":pepe_sip:", "한 모금", "pepe_sip.png"],
    [":pepe_thinking:", "생각", "pepe_thinking.png"],
    [":pepe_perfect:", "완벽", "pepe_perfect.png"],
    [":pepe_clap:", "박수", "pepe_clap.gif"],
    [":pepe_love:", "좋아", "pepe_love.png"],
    [":pepe_cool:", "쿨", "pepe_cool.png"],
    [":pepe_blush:", "부끄", "pepe_blush.gif"],
    [":pepe_bored:", "심심", "pepe_bored.png"],
    [":pepe_sad:", "슬픔", "pepe_sad.png"],
    [":pepe_please:", "제발", "pepe_please.gif"],
    [":pepe_why:", "왜", "pepe_why.png"],
    [":pepe_rain:", "비", "pepe_rain.gif"],
    [":pepe_pray:", "기도", "pepe_pray.gif"],
    [":pepe_rich:", "부자", "pepe_rich.gif"],
    [":pepe_dnd:", "게임", "pepe_dnd.gif"],
    [":pepe_bbq:", "바비큐", "pepe_bbq.png"],
    [":pepe_wine:", "와인", "pepe_wine.png"],
    [":pepe_nerd:", "공부", "pepe_nerd.jpg"],
    [":pepe_uwu:", "우우", "pepe_uwu.png"],
    [":pepe_dance:", "댄스", "pepe_dance.gif"],
    [":pepe_tongue:", "메롱", "pepe_tongue.gif"],
    [":pepe_true:", "인정", "pepe_true.png"],
    [":pepe_hands:", "눈물", "pepe_hands.jpg"],
    [":pepe_bye:", "안녕", "pepe_bye.gif"],
    [":pepe_shades:", "선글라스", "pepe_shades.png"],
    [":pepe_confident:", "자신감", "pepe_confident.png"],
    [":pepe_bee:", "벌", "pepe_bee.png"],
    [":pepe_wilt:", "시무룩", "pepe_wilt.png"],
    [":pepe_pop:", "팝", "pepe_pop.png"],
    [":pepe_pair:", "친구", "pepe_pair.png"],
  ].map(([code, label, file]) => Object.freeze({
    code,
    label,
    file,
    src: `${FOODUCK_CUSTOM_EMOJI_BASE}${file}`,
  })));
  const FOODUCK_CUSTOM_EMOJI_BY_CODE = new Map(
    FOODUCK_CUSTOM_EMOJIS.map((emoji) => [emoji.code, emoji]),
  );
  const FOODUCK_CUSTOM_EMOJI_PATTERN = /:pepe_[a-z0-9_-]+:/g;

  function createCustomEmojiImage(emoji, className = "fooduck-custom-emoji-inline") {
    const image = new Image();
    image.src = emoji.src;
    image.alt = `[Pepe ${emoji.label}]`;
    image.title = `Pepe ${emoji.label}`;
    image.className = className;
    image.loading = "lazy";
    image.decoding = "async";
    image.dataset.fooduckEmojiCode = emoji.code;
    return image;
  }

  function renderCustomEmojiText(target, value) {
    if (!(target instanceof Element)) return target;
    const text = String(value ?? "");
    const fragment = document.createDocumentFragment();
    let lastIndex = 0;
    FOODUCK_CUSTOM_EMOJI_PATTERN.lastIndex = 0;
    let match;
    while ((match = FOODUCK_CUSTOM_EMOJI_PATTERN.exec(text)) !== null) {
      if (match.index > lastIndex) {
        fragment.append(document.createTextNode(text.slice(lastIndex, match.index)));
      }
      const emoji = FOODUCK_CUSTOM_EMOJI_BY_CODE.get(match[0]);
      fragment.append(
        emoji
          ? createCustomEmojiImage(emoji)
          : document.createTextNode(match[0]),
      );
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < text.length) {
      fragment.append(document.createTextNode(text.slice(lastIndex)));
    }
    target.replaceChildren(fragment);
    return target;
  }

  const FOODUCK_CUSTOM_EMOJI_EDITORS = new WeakMap();

  function serializeCustomEmojiEditor(editor) {
    const serializeNode = (node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        return String(node.nodeValue || "").replaceAll("\u200B", "");
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return "";
      if (node.matches?.("img[data-fooduck-emoji-code]")) {
        return node.dataset.fooduckEmojiCode || "";
      }
      if (node.tagName === "BR") return "\n";

      let value = "";
      node.childNodes.forEach((child) => {
        value += serializeNode(child);
      });
      if ((node.tagName === "DIV" || node.tagName === "P") && node.nextSibling && !value.endsWith("\n")) {
        value += "\n";
      }
      return value;
    };

    let value = "";
    editor.childNodes.forEach((node) => {
      value += serializeNode(node);
    });
    return value;
  }

  function attachCustomEmojiEditor(textarea) {
    if (!(textarea instanceof HTMLTextAreaElement)) return null;
    const existing = FOODUCK_CUSTOM_EMOJI_EDITORS.get(textarea);
    if (existing) return existing;

    const computed = window.getComputedStyle(textarea);
    const editor = document.createElement("div");
    editor.className = `${textarea.className || ""} fooduck-custom-emoji-editor`.trim();
    editor.contentEditable = "true";
    editor.setAttribute("role", "textbox");
    editor.setAttribute("aria-multiline", "true");
    editor.setAttribute("aria-label", textarea.getAttribute("aria-label") || "내용 입력");
    if (textarea.required) editor.setAttribute("aria-required", "true");
    const describedBy = textarea.getAttribute("aria-describedby");
    if (describedBy) editor.setAttribute("aria-describedby", describedBy);
    editor.dataset.placeholder = textarea.getAttribute("placeholder") || "";
    const measuredHeight = Number.parseFloat(computed.height);
    editor.style.minHeight = Number.isFinite(measuredHeight) && measuredHeight >= 48
      ? computed.height
      : `${Math.max(96, (Number(textarea.rows) || 4) * 24)}px`;
    editor.style.fontFamily = computed.fontFamily;
    editor.style.fontSize = computed.fontSize;
    editor.style.fontWeight = computed.fontWeight;
    editor.style.lineHeight = computed.lineHeight;
    editor.style.letterSpacing = computed.letterSpacing;
    editor.style.padding = computed.padding;
    editor.style.border = computed.border;
    editor.style.borderRadius = computed.borderRadius;
    editor.style.backgroundColor = computed.backgroundColor;
    editor.style.color = computed.color;

    textarea.insertAdjacentElement("afterend", editor);
    textarea.classList.add("fooduck-custom-emoji-source");
    textarea.setAttribute("aria-hidden", "true");
    textarea.tabIndex = -1;

    let syncingFromEditor = false;
    let lastValidValue = String(textarea.value || "");
    let savedRange = null;

    const render = () => {
      const text = String(textarea.value || "");
      const fragment = document.createDocumentFragment();
      let lastIndex = 0;
      FOODUCK_CUSTOM_EMOJI_PATTERN.lastIndex = 0;
      let match;
      while ((match = FOODUCK_CUSTOM_EMOJI_PATTERN.exec(text)) !== null) {
        if (match.index > lastIndex) {
          fragment.append(document.createTextNode(text.slice(lastIndex, match.index)));
        }
        const emoji = FOODUCK_CUSTOM_EMOJI_BY_CODE.get(match[0]);
        if (emoji) {
          const image = createCustomEmojiImage(emoji);
          image.contentEditable = "false";
          fragment.append(image);
        } else {
          fragment.append(document.createTextNode(match[0]));
        }
        lastIndex = match.index + match[0].length;
      }
      if (lastIndex < text.length) {
        fragment.append(document.createTextNode(text.slice(lastIndex)));
      }
      editor.replaceChildren(fragment);
      lastValidValue = text;
    };

    const captureSelection = () => {
      const selection = window.getSelection();
      if (!selection?.rangeCount) return;
      const range = selection.getRangeAt(0);
      if (editor.contains(range.commonAncestorContainer)) {
        savedRange = range.cloneRange();
      }
    };

    const placeCaretAfter = (node) => {
      const selection = window.getSelection();
      if (!selection) return;
      const range = document.createRange();
      range.setStartAfter(node);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
      savedRange = range.cloneRange();
    };

    const syncSource = () => {
      const nextValue = serializeCustomEmojiEditor(editor);
      if (textarea.maxLength > 0 && nextValue.length > textarea.maxLength) {
        textarea.value = lastValidValue;
        render();
        return false;
      }
      textarea.value = nextValue;
      lastValidValue = nextValue;
      syncingFromEditor = true;
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
      syncingFromEditor = false;
      return true;
    };

    const insertPlainText = (text) => {
      const selection = window.getSelection();
      let range = savedRange;
      if (!range || !editor.contains(range.commonAncestorContainer)) {
        range = document.createRange();
        range.selectNodeContents(editor);
        range.collapse(false);
      }
      selection.removeAllRanges();
      selection.addRange(range);
      range.deleteContents();
      const node = document.createTextNode(text);
      range.insertNode(node);
      placeCaretAfter(node);
      syncSource();
    };

    const insertEmoji = (code) => {
      const emoji = FOODUCK_CUSTOM_EMOJI_BY_CODE.get(code);
      if (!emoji) return false;

      const selection = window.getSelection();
      let range = savedRange;
      if (!range || !editor.contains(range.commonAncestorContainer)) {
        range = document.createRange();
        range.selectNodeContents(editor);
        range.collapse(false);
      }
      selection.removeAllRanges();
      selection.addRange(range);
      range.deleteContents();
      const image = createCustomEmojiImage(emoji);
      image.contentEditable = "false";
      range.insertNode(image);
      placeCaretAfter(image);
      editor.focus({ preventScroll: true });
      return syncSource();
    };

    editor.addEventListener("input", () => {
      syncSource();
      captureSelection();
    });
    editor.addEventListener("keyup", captureSelection);
    editor.addEventListener("mouseup", captureSelection);
    editor.addEventListener("focus", captureSelection);
    editor.addEventListener("keydown", (event) => {
      if (event.key !== "Enter") return;
      event.preventDefault();
      insertPlainText("\n");
    });
    editor.addEventListener("paste", (event) => {
      event.preventDefault();
      insertPlainText(event.clipboardData?.getData("text/plain") || "");
    });
    textarea.addEventListener("input", () => {
      if (!syncingFromEditor) render();
    });
    textarea.addEventListener("focus", () => editor.focus({ preventScroll: true }));
    textarea.form?.addEventListener("reset", () => window.setTimeout(render, 0));

    const api = { editor, insertEmoji, refresh: render };
    FOODUCK_CUSTOM_EMOJI_EDITORS.set(textarea, api);
    render();
    return api;
  }

  function insertCustomEmojiIntoEditor(textarea, code) {
    const api = FOODUCK_CUSTOM_EMOJI_EDITORS.get(textarea);
    if (!api) return false;
    return api.insertEmoji(code);
  }

  function refreshCustomEmojiEditor(textarea) {
    FOODUCK_CUSTOM_EMOJI_EDITORS.get(textarea)?.refresh();
  }

  function populateCustomEmojiPicker(panel, options = {}) {
    if (!(panel instanceof Element)) return null;
    const grid = document.createElement("div");
    grid.className = options.gridClass || "fooduck-custom-emoji-grid";
    FOODUCK_CUSTOM_EMOJIS.forEach((emoji) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = options.buttonClass || "fooduck-custom-emoji-option";
      button.setAttribute("aria-label", `Pepe ${emoji.label} 이모지 입력`);
      button.title = `Pepe ${emoji.label}`;
      button.dataset.fooduckEmojiCode = emoji.code;
      button.append(createCustomEmojiImage(emoji, "fooduck-custom-emoji-picker-image"));
      button.addEventListener("click", () => options.onSelect?.(emoji));
      grid.append(button);
    });

    const children = [];
    if (options.showTitle !== false) {
      const title = document.createElement("strong");
      title.className = options.titleClass || "comment-emoji-panel-title";
      title.textContent = options.title || "Pepe 이모지";
      children.push(title);
    }
    children.push(grid);
    panel.replaceChildren(...children);
    return grid;
  }

  const ICON_PATHS = {
    notifications: [
      "M18 8a6 6 0 0 0-12 0c0 6.8-3 7-3 9h18c0-2-3-2.2-3-9",
      "M10 21h4",
    ],
    notifications_off: [
      "M13.7 21h-3.4",
      "M6.3 6.3A6 6 0 0 0 6 8c0 6.8-3 7-3 9h14",
      "M18 13V8a6 6 0 0 0-8.3-5.5",
      "M3 3l18 18",
    ],
    menu: ["M4 7h16", "M4 12h16", "M4 17h16"],
    list: [
      "M8 6h13",
      "M8 12h13",
      "M8 18h13",
      "M3.5 6h.01",
      "M3.5 12h.01",
      "M3.5 18h.01",
    ],
    close: ["M6 6l12 12", "M18 6L6 18"],
    map: [
      "M9 18l-6 3V6l6-3 6 3 6-3v15l-6 3-6-3z",
      "M9 3v15",
      "M15 6v15",
    ],
    arrow_forward: ["M5 12h14", "M13 6l6 6-6 6"],
    arrow_back: ["M19 12H5", "M11 18l-6-6 6-6"],
    chevron_left: ["M15 18l-6-6 6-6"],
    chevron_right: ["M9 18l6-6-6-6"],
    favorite: [
      "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6a5.5 5.5 0 0 0 1-8.8z",
    ],
    check_circle: [
      "M22 11.1V12a10 10 0 1 1-5.9-9.1",
      "M22 4L12 14.1l-3-3",
    ],
    search: ["M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16z", "M21 21l-4.3-4.3"],
    visibility: [
      "M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z",
      "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    ],
    tune: [
      "M4 7h10",
      "M18 7h2",
      "M14 4v6",
      "M4 17h2",
      "M10 17h10",
      "M10 14v6",
    ],
    near_me: ["M21 3L10 14", "M21 3l-7 18-4-7-7-4 18-7z"],
    auto_awesome: [
      "M12 3l1.1 3.4L16.5 7.5l-3.4 1.1L12 12l-1.1-3.4-3.4-1.1 3.4-1.1L12 3z",
      "M19 14l.7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7L19 14z",
      "M5 13l.7 1.8 1.8.7-1.8.7L5 18l-.7-1.8-1.8-.7 1.8-.7L5 13z",
    ],
    forum: [
      "M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8z",
      "M7 8h10",
      "M7 12h7",
    ],
    lock: [
      "M5 10h14v11H5z",
      "M8 10V7a4 4 0 0 1 8 0v3",
      "M12 14v3",
    ],
    login: ["M14 8l4 4-4 4", "M18 12H7", "M10 4H4v16h6"],
    logout: ["M10 17l5-5-5-5", "M15 12H3", "M14 4h6v16h-6"],
    person_add: [
      "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
      "M2 21a7 7 0 0 1 14 0",
      "M19 8v6",
      "M16 11h6",
    ],
    verified_user: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M9 12l2 2 4-5",
    ],
    home: ["M3 11l9-8 9 8", "M5 10v11h14V10", "M9 21v-7h6v7"],
    construction: [
      "M14.5 6.5l3-3 3 3-3 3",
      "M13 8l-9 9v3h3l9-9",
      "M5 4l15 15",
    ],
    shield_person: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M12 11a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
      "M8.5 16a3.5 3.5 0 0 1 7 0",
    ],
    database: [
      "M20 5c0 1.7-3.6 3-8 3S4 6.7 4 5s3.6-3 8-3 8 1.3 8 3z",
      "M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5",
      "M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7",
    ],
    edit: [
      "M12 20h9",
      "M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4L16.5 3.5z",
    ],
    delete: [
      "M4 7h16",
      "M9 7V4h6v3",
      "M7 7l1 14h8l1-14",
      "M10 11v6",
      "M14 11v6",
    ],
    thumb_up: [
      "M7 10v11H3V10h4z",
      "M7 19h10a2 2 0 0 0 2-1.6l2-8A2 2 0 0 0 19 7h-5l1-4-2-1-6 8",
    ],
    schedule: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 6v6l4 2"],
    location_on: [
      "M21 10c0 7-9 12-9 12S3 17 3 10a9 9 0 1 1 18 0z",
      "M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    ],
    open_in_new: ["M14 3h7v7", "M21 3l-9 9", "M18 13v7H4V6h7"],
    call: [
      "M5 3h4l2 5-2.5 1.5a16 16 0 0 0 6 6L16 13l5 2v4c0 1.1-.9 2-2 2C10.2 21 3 13.8 3 5a2 2 0 0 1 2-2z",
    ],
    progress_activity: [
      "M12 2a10 10 0 0 1 10 10",
      "M22 12a10 10 0 0 1-10 10",
      "M12 22A10 10 0 0 1 2 12",
    ],
    my_location: [
      "M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16z",
      "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
      "M12 2v2",
      "M12 20v2",
      "M2 12h2",
      "M20 12h2",
    ],
    touch_app: [
      "M9 11V5a2 2 0 0 1 4 0v6",
      "M13 10V8a2 2 0 0 1 4 0v4",
      "M17 11a2 2 0 0 1 4 0v4c0 4-3 7-7 7h-2c-3 0-5-2-7-5l-2-3a2 2 0 0 1 3-2l3 2",
    ],
    person: [
      "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
      "M4 22a8 8 0 0 1 16 0",
    ],
    storefront: ["M3 10h18l-2-6H5l-2 6z", "M5 10v10h14V10", "M9 20v-6h6v6"],
    admin_panel_settings: [
      "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
      "M12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
      "M8.5 17a3.5 3.5 0 0 1 7 0",
    ],
    rate_review: [
      "M4 4h16v13H9l-5 4V4z",
      "M12 8l1.2 2.4L16 11l-2.8.6L12 14l-1.2-2.4L8 11l2.8-.6L12 8z",
    ],
    article: [
      "M6 3h9l5 5v13H6z",
      "M15 3v5h5",
      "M9 12h6",
      "M9 16h6",
    ],
    chat_bubble: [
      "M6 4h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H9l-5 4V7a3 3 0 0 1 3-3z",
    ],
    add: ["M12 5v14", "M5 12h14"],
    error: [
      "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z",
      "M12 7v6",
      "M12 17h.01",
    ],
    movie: [
      "M3 5h18v14H3z",
      "M3 9h18",
      "M7 5l2 4",
      "M13 5l2 4",
      "M17 5l2 4",
    ],
    store: [
      "M4 10h16v10H4z",
      "M3 10l2-6h14l2 6",
      "M8 20v-6h5v6",
      "M4 10c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2c0 1.1.9 2 2 2s2-.9 2-2",
    ],
    campaign: [
      "M4 10v4",
      "M7 9h4l7-4v14l-7-4H7z",
      "M7 15l1.5 5h3L10 15",
      "M20 9v6",
    ],
  };

  function setIcon(element, iconName) {
    if (!element) {
      return;
    }
    const paths = ICON_PATHS[iconName];
    if (!paths) {
      element.dataset.iconError = iconName || "unknown";
      console.warn(`[FooduckIcons] 등록되지 않은 아이콘: ${iconName}`);
      return;
    }
    delete element.dataset.iconError;
    const svg = document.createElementNS(SVG_NAMESPACE, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    svg.setAttribute("focusable", "false");
    paths.forEach((pathData) => {
      const path = document.createElementNS(SVG_NAMESPACE, "path");
      path.setAttribute("d", pathData);
      svg.append(path);
    });
    element.replaceChildren(svg);
    element.dataset.iconName = iconName;
  }

  function enhanceIcons(root = document) {
    const icons = [];
    if (root instanceof Element && root.matches(".material-symbols-rounded")) {
      icons.push(root);
    }
    if (root.querySelectorAll) {
      icons.push(...root.querySelectorAll(".material-symbols-rounded"));
    }
    icons.forEach((element) => {
      if (!element.dataset.iconName) {
        setIcon(element, element.textContent.trim());
      }
    });
  }

  function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (text !== undefined && text !== null) {
      element.textContent = text;
    }
    return element;
  }

  function primaryAuthorityCode(authorities) {
    const authorityCodes = Array.isArray(authorities) ? authorities : [];
    return AUTHORITY_PRIORITY.find((code) => authorityCodes.includes(code)) || "ROLE_USER";
  }

  function authorityLabel(code) {
    return AUTHORITY_LABELS[code] || code || AUTHORITY_LABELS.ROLE_USER;
  }

  function formatProfileDate(value) {
    if (!value) {
      return "정보 없음";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(date);
  }

  function createProfileSummary(data = {}, actions = [], options = {}) {
    const summary = createElement("section", "profile-summary");
    const profileImage = createElement("div", "profile-image");
    const showFallbackImage = () => {
      const fallback = createElement("span", "material-symbols-rounded", "person");
      fallback.setAttribute("aria-hidden", "true");
      profileImage.replaceChildren(fallback);
      enhanceIcons(profileImage);
    };

    if (data.profileImageUrl) {
      const image = new Image();
      image.src = data.profileImageUrl;
      image.alt = `${data.nickname || "회원"} 프로필`;
      image.addEventListener("error", showFallbackImage, { once: true });
      profileImage.append(image);
    } else {
      showFallbackImage();
    }

    const profileCopy = createElement("div", "profile-copy");
    profileCopy.append(
      createElement("h2", "", `${data.nickname || "회원"}님, 반가워요`),
      createElement(
        "p",
        "",
        options.hideLoginId
          ? `가입 ${formatProfileDate(data.createdAt)}`
          : `${data.loginId || "소셜 계정"} · 가입 ${formatProfileDate(data.createdAt)}`,
      ),
    );
    const authorityList = createElement("div", "authority-list");
    const primaryAuthority = primaryAuthorityCode(data.authorities);
    authorityList.append(
      createElement("span", "authority-badge", authorityLabel(primaryAuthority)),
    );
    profileCopy.append(authorityList);
    summary.append(profileImage, profileCopy);

    const validActions = Array.isArray(actions)
      ? actions.filter((action) => action?.label && action?.href)
      : [];
    if (validActions.length > 0) {
      const actionList = createElement("div", "profile-summary-actions");
      validActions.forEach((action) => {
        const link = createElement("a", "button button-secondary", action.label);
        link.href = action.href;
        actionList.append(link);
      });
      summary.append(actionList);
    }

    enhanceIcons(summary);
    return summary;
  }

  function decodeToken(token) {
    try {
      const segment = token.split(".")[1];
      if (!segment) {
        return null;
      }
      const normalized = segment.replace(/-/g, "+").replace(/_/g, "/");
      const padded = normalized.padEnd(
        normalized.length + ((4 - (normalized.length % 4)) % 4),
        "=",
      );
      return JSON.parse(decodeURIComponent(
        Array.from(atob(padded))
          .map((character) =>
            `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`,
          )
          .join(""),
      ));
    } catch (_error) {
      return null;
    }
  }

  function readSession() {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      return {
        authenticated: false,
        accountId: null,
        loginId: null,
        authorities: [],
      };
    }
    const payload = decodeToken(token);
    if (!payload || (payload.exp && payload.exp * 1000 <= Date.now())) {
      localStorage.removeItem("accessToken");
      return {
        authenticated: false,
        accountId: null,
        loginId: null,
        authorities: [],
      };
    }
    return {
      authenticated: true,
      accountId: Number(payload.sub) || null,
      loginId: payload.loginId || null,
      authorities: Array.isArray(payload.authorities)
        ? payload.authorities.filter((value) => typeof value === "string")
        : [],
    };
  }

  const session = readSession();
  const hasAuthority = (authority) => session.authorities.includes(authority);
  const canManageBusiness =
    hasAuthority("ROLE_BUSINESS") || hasAuthority("ROLE_ADMIN");
  const isAdmin = hasAuthority("ROLE_ADMIN");

  function recommendationHref() {
    return session.authenticated
      ? RECOMMENDATION_PATH
      : RECOMMENDATION_LOGIN_PATH;
  }

  function renderHeader(host) {
    const active = host.dataset.activeNav || "";
    const items = [
      { id: "home", label: "홈", href: "/" },
      { id: "search", label: "검색", href: "/search" },
      { id: "map", label: "맛집찾기", href: "/map" },
      {
        id: "recommendation",
        label: "맛집추천",
        href: recommendationHref(),
        protectedRecommendation: true,
      },
      { id: "board", label: "커뮤니티", href: "/board" },
      { id: "presset", label: "보물지도", href: "/presset" },
    ];

    const nav = items.map((item) => {
      const current = item.id === active ? ' aria-current="page"' : "";
      const guard = item.protectedRecommendation
        ? ' data-recommendation-link'
        : "";
      return `<a href="${item.href}"${current}${guard}>${item.label}</a>`;
    }).join("");

    const authAction = session.authenticated
      ? `<button class="button button-sm button-outline-gray header-auth-button" type="button" data-logout>
           <span class="material-symbols-rounded" aria-hidden="true">logout</span>
           로그아웃
         </button>`
      : `<a class="button button-sm button-secondary header-auth-button header-signup-button"
              href="/auth/signup">
           회원가입
         </a>
         <a class="button button-sm button-orange header-auth-button" href="/auth/login">
           로그인
         </a>`;

    host.innerHTML = `
      <header class="site-header">
        <div class="header-shell">
          <a class="brand" href="/" aria-label="푸드덕 홈">
            <img src="/images/logos/brand-horizontal.png" alt="foodduck">
          </a>
          <nav id="site-nav" class="nav" aria-label="주요 메뉴">${nav}</nav>
          <div class="header-actions">
            <a class="icon-button" href="/mypage" aria-label="마이페이지">
              <span class="material-symbols-rounded" aria-hidden="true">person</span>
            </a>
            <a class="icon-button" href="/mypage/detail?tab=notifications"
               aria-label="알림" data-notification-link>
              <span class="material-symbols-rounded" aria-hidden="true">notifications</span>
              <span class="notification-badge" data-notification-badge hidden></span>
            </a>
            ${authAction}
            <button class="nav-toggle" type="button" data-nav-toggle
                    aria-controls="site-nav" aria-expanded="false" aria-label="메뉴 열기">
              <span class="material-symbols-rounded" aria-hidden="true">menu</span>
            </button>
          </div>
        </div>
      </header>`;
  }

  function renderFooter(host) {
    host.innerHTML = `
      <footer class="site-footer">
        <div class="container footer-shell">
          <div class="footer-main">
            <div class="footer-brand">
              <img src="/images/logos/brand-horizontal.png" alt="foodduck">
              <p>맛집을 찾고, 취향에 맞게 저장하고, 경험을 나누는 맛집 탐색·보관 서비스</p>
              <span class="footer-brand-accent" aria-hidden="true"></span>
            </div>

            <nav class="footer-links" aria-label="푸드덕 안내" tabindex="0">
              <button type="button" class="footer-link" data-footer-dialog="footer-about-dialog">서비스 소개</button>
              <button type="button" class="footer-link" data-footer-dialog="footer-faq-dialog">자주 묻는 질문</button>
              <button type="button" class="footer-link" data-footer-dialog="footer-terms-dialog">이용약관</button>
              <button type="button" class="footer-link" data-footer-dialog="footer-privacy-dialog">개인정보처리방침</button>
            </nav>
          </div>

          <div class="footer-meta">
            <div class="footer-business">
              <p><strong>상호 푸드덕</strong><span>대표 엄선필</span></p>
              <address>서울특별시 강남구 봉은사로 119, 5층</address>
            </div>

            <div class="footer-notices">
              <p>푸드덕은 맛집 검색과 추천부터 음식점 상세 정보, 찜, 보물지도 보관, 리뷰와 커뮤니티까지 한곳에서 이용할 수 있는 맛집 탐색·보관 서비스입니다.</p>
              <p>푸드덕에 제공되는 음식점의 영업시간, 메뉴, 가격 및 기타 정보는 실제 매장 정보와 다를 수 있으므로 방문 전 최신 정보를 확인해 주세요.</p>
              <p>회원이 작성한 게시글, 댓글 및 리뷰의 내용은 해당 작성자의 의견이며 푸드덕의 공식적인 의견을 의미하지 않습니다.</p>
            </div>
          </div>
        </div>

        <div class="container footer-bottom">
          <span>© 2026 FOODUCK. All rights reserved.</span>
        </div>
      </footer>`;

    // 안내 모달은 실제로 열 때만 DOM에 생성한다.
    // 초기 페이지 렌더에서 긴 서비스 소개/FAQ/법적 문서 DOM을 만들지 않아 공통 페이지 비용을 줄인다.
    const dialogTemplates = {
      "footer-about-dialog": `
        <dialog id="footer-about-dialog" class="footer-dialog" aria-labelledby="footer-about-title">
          <div class="footer-dialog-header">
            <div>
              <span class="footer-dialog-kicker">FOODUCK</span>
              <h2 id="footer-about-title">서비스 소개</h2>
            </div>
            <button type="button" class="footer-dialog-x" data-footer-dialog-close aria-label="서비스 소개 닫기">
              <span class="material-symbols-rounded" aria-hidden="true">close</span>
            </button>
          </div>
          <div class="footer-dialog-body">
            <p class="footer-dialog-lead"><strong>푸드덕은 맛집을 찾고, 취향에 맞게 저장하고, 경험을 나눌 수 있는 맛집 탐색·보관 서비스입니다.</strong></p>
            <p>푸드덕은 음식점을 단순히 검색하는 것에서 끝나지 않고, 원하는 음식점을 찾고 추천받은 맛집을 확인한 뒤 관심 있는 음식점과 맛집 컬렉션을 나만의 공간에 저장해 다시 찾아볼 수 있도록 다양한 기능을 한곳에 제공합니다.</p>

            <h3>맛집 찾기</h3>
            <p>지도와 검색 기능을 통해 원하는 지역과 조건의 음식점을 찾아볼 수 있습니다.</p>
            <p>검색한 음식점은 외부 서비스로 이동하지 않고 푸드덕의 음식점 상세 페이지에서 기본 정보와 메뉴, 리뷰, 소식 등 필요한 정보를 계속 확인할 수 있도록 구성하고 있습니다.</p>

            <h3>맞춤형 맛집 추천</h3>
            <p>로그인 사용자는 서비스 이용 정보와 취향 데이터를 바탕으로 자신에게 어울리는 음식점을 추천받을 수 있습니다.</p>
            <p>추천 기능은 사용자의 취향과 서비스 이용 경험이 쌓일수록 더 잘 맞는 음식점을 찾을 수 있도록 지속적으로 개선하고 있습니다.</p>

            <h3>보물지도와 나만의 맛집 컬렉션</h3>
            <p>푸드덕의 <strong>보물지도</strong>는 하나의 주제와 취향에 맞는 여러 음식점을 모아 보여주는 맛집 컬렉션입니다.</p>
            <p>관심 있는 보물지도를 저장해 나만의 보관함처럼 관리할 수 있으며, 저장한 보물지도는 마이페이지에서 언제든지 다시 확인할 수 있습니다.</p>
            <p>음악 서비스에서 마음에 드는 플레이리스트를 내 라이브러리에 저장하듯, 푸드덕에서는 관심 있는 맛집 컬렉션을 보물지도로 간편하게 보관하고 다시 찾아볼 수 있습니다.</p>

            <h3>찜과 개별 음식점 관리</h3>
            <p>관심 있는 개별 음식점은 <strong>찜</strong>으로 저장해 나중에 다시 찾아볼 수 있습니다.</p>
            <p>찜이 하나의 음식점을 저장하는 기능이라면 보물지도는 여러 음식점으로 구성된 맛집 컬렉션을 저장하는 기능으로, 목적에 따라 나누어 관리할 수 있습니다.</p>

            <h3>리뷰와 커뮤니티</h3>
            <p>푸드덕에서는 실제 사용자가 직접 작성한 리뷰를 통해 음식점에 대한 경험을 공유할 수 있습니다.</p>
            <p>또한 커뮤니티에서는 맛집과 음식에 관한 이야기를 나누거나 질문을 올리고, 다른 이용자의 추천을 받은 글과 인기 있는 이야기를 살펴볼 수 있습니다.</p>

            <h3>푸드덕이 지향하는 경험</h3>
            <p>푸드덕은 여러 서비스를 오가며 맛집을 찾아야 하는 번거로움을 줄이고, <strong>탐색과 추천부터 저장, 보관과 경험 공유까지 자연스럽게 이어지는 맛집 이용 경험</strong>을 제공하는 것을 목표로 합니다.</p>
            <p>사용자가 자신의 취향에 맞는 음식점과 맛집 컬렉션을 쉽게 관리하고, 필요할 때 다시 꺼내보며 다른 이용자의 경험까지 참고할 수 있도록 서비스를 지속적으로 개선해 나가겠습니다.</p>
          </div>
          <button type="button" class="footer-dialog-close" data-footer-dialog-close>닫기</button>
        </dialog>`,

      "footer-faq-dialog": `
        <dialog id="footer-faq-dialog" class="footer-dialog" aria-labelledby="footer-faq-title">
          <div class="footer-dialog-header">
            <div>
              <span class="footer-dialog-kicker">HELP</span>
              <h2 id="footer-faq-title">자주 묻는 질문</h2>
            </div>
            <button type="button" class="footer-dialog-x" data-footer-dialog-close aria-label="자주 묻는 질문 닫기">
              <span class="material-symbols-rounded" aria-hidden="true">close</span>
            </button>
          </div>
          <div class="footer-dialog-body footer-faq-list">
            <section>
              <h3>푸드덕에서는 무엇을 할 수 있나요?</h3>
              <p>지도와 검색을 통해 음식점을 탐색하고 음식점 상세 정보와 리뷰를 확인할 수 있습니다. 관심 있는 개별 음식점은 찜으로, 주제별 맛집 컬렉션은 보물지도로 저장할 수 있으며 맞춤 추천과 커뮤니티도 함께 이용할 수 있습니다.</p>
            </section>
            <section>
              <h3>보물지도는 무엇인가요?</h3>
              <p>보물지도는 특정 주제와 취향에 맞는 여러 음식점을 하나로 묶은 푸드덕의 맛집 컬렉션입니다. 마음에 드는 보물지도를 저장하면 나만의 보관함처럼 관리할 수 있고, 마이페이지에서 언제든지 다시 확인할 수 있습니다.</p>
            </section>
            <section>
              <h3>보물지도와 찜은 어떻게 다른가요?</h3>
              <p><strong>찜</strong>은 관심 있는 음식점 하나를 저장하는 기능이고, <strong>보물지도</strong>는 여러 음식점으로 구성된 맛집 컬렉션을 저장하는 기능입니다. 개별 맛집은 찜으로, 취향이나 목적에 맞는 맛집 묶음은 보물지도로 나누어 보관할 수 있습니다.</p>
            </section>
            <section>
              <h3>맞춤 추천은 어떻게 이용하나요?</h3>
              <p>로그인 사용자는 서비스 이용 정보와 취향 데이터를 활용한 맞춤형 추천 기능을 이용할 수 있습니다. 추천 기능은 사용자에게 더 잘 맞는 음식점을 찾을 수 있도록 계속 개선하고 있습니다.</p>
            </section>
            <section>
              <h3>음식점 정보가 실제 매장과 다른 경우가 있나요?</h3>
              <p>영업시간, 메뉴, 가격 등 음식점 정보는 실제 매장 상황에 따라 변경될 수 있습니다. 방문 전에는 해당 음식점의 최신 정보를 함께 확인하는 것을 권장합니다.</p>
            </section>
            <section>
              <h3>리뷰와 커뮤니티 글은 누가 작성하나요?</h3>
              <p>리뷰, 게시글과 댓글은 푸드덕 이용자가 직접 작성합니다. 각 작성 내용은 해당 이용자의 경험과 의견이며 푸드덕의 공식적인 의견을 의미하지 않습니다.</p>
            </section>
          </div>
          <button type="button" class="footer-dialog-close" data-footer-dialog-close>닫기</button>
        </dialog>`,

      "footer-terms-dialog": `
        <dialog id="footer-terms-dialog" class="footer-dialog" aria-labelledby="footer-terms-title">
          <div class="footer-dialog-header">
            <div>
              <span class="footer-dialog-kicker">LEGAL</span>
              <h2 id="footer-terms-title">이용약관</h2>
            </div>
            <button type="button" class="footer-dialog-x" data-footer-dialog-close aria-label="이용약관 닫기">
              <span class="material-symbols-rounded" aria-hidden="true">close</span>
            </button>
          </div>
          <div class="footer-dialog-body" data-footer-legal-body="terms">
            <p>이용약관을 불러오는 중입니다.</p>
          </div>
          <button type="button" class="footer-dialog-close" data-footer-dialog-close>닫기</button>
        </dialog>`,

      "footer-privacy-dialog": `
        <dialog id="footer-privacy-dialog" class="footer-dialog" aria-labelledby="footer-privacy-title">
          <div class="footer-dialog-header">
            <div>
              <span class="footer-dialog-kicker">PRIVACY</span>
              <h2 id="footer-privacy-title">개인정보처리방침</h2>
            </div>
            <button type="button" class="footer-dialog-x" data-footer-dialog-close aria-label="개인정보처리방침 닫기">
              <span class="material-symbols-rounded" aria-hidden="true">close</span>
            </button>
          </div>
          <div class="footer-dialog-body" data-footer-legal-body="privacy">
            <p>개인정보처리방침을 불러오는 중입니다.</p>
          </div>
          <button type="button" class="footer-dialog-close" data-footer-dialog-close>닫기</button>
        </dialog>`,
    };

    // 동일 페이지에서 약관/개인정보처리방침을 연속으로 열 때 signup.html을 다시 읽지 않는다.
    // 영구 저장소에 법적 문서를 복제하지 않아 원본 변경 시 오래된 내용이 남는 문제도 피한다.
    let signupLegalContentPromise = null;

    function extractSignupLegalContent(sourceDocument) {
      const termsBody = sourceDocument.querySelector("#terms-dialog .legal-body");
      const privacyBody = sourceDocument.querySelector("#privacy-dialog .legal-body");
      if (!termsBody || !privacyBody) {
        throw new Error("회원가입 페이지에서 약관 본문을 찾을 수 없습니다.");
      }
      return {
        terms: termsBody.innerHTML,
        privacy: privacyBody.innerHTML,
      };
    }

    async function loadSignupLegalContent() {
      if (signupLegalContentPromise) return signupLegalContentPromise;

      signupLegalContentPromise = (async () => {
        const currentTerms = document.querySelector("#terms-dialog .legal-body");
        const currentPrivacy = document.querySelector("#privacy-dialog .legal-body");
        if (currentTerms && currentPrivacy) {
          return {
            terms: currentTerms.innerHTML,
            privacy: currentPrivacy.innerHTML,
          };
        }

        const response = await fetch("/auth/signup", {
          method: "GET",
          credentials: "same-origin",
        });
        if (!response.ok) {
          throw new Error(`회원가입 페이지를 불러오지 못했습니다. (${response.status})`);
        }

        const html = await response.text();
        const sourceDocument = new DOMParser().parseFromString(html, "text/html");
        return extractSignupLegalContent(sourceDocument);
      })();

      try {
        return await signupLegalContentPromise;
      } catch (error) {
        signupLegalContentPromise = null;
        throw error;
      }
    }

    async function syncFooterLegalBody(type) {
      const target = host.querySelector(`[data-footer-legal-body="${type}"]`);
      if (!target || target.dataset.loaded === "true") return;

      target.setAttribute("aria-busy", "true");
      target.innerHTML = `<p>${type === "terms" ? "이용약관" : "개인정보처리방침"}을 불러오는 중입니다.</p>`;

      try {
        const contents = await loadSignupLegalContent();
        target.innerHTML = contents[type];
        target.dataset.loaded = "true";
      } catch (error) {
        console.error("[FooduckFooter] 회원가입 약관 콘텐츠 로딩 실패", error);
        target.innerHTML = `<p>${type === "terms" ? "이용약관" : "개인정보처리방침"}을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>`;
      } finally {
        target.removeAttribute("aria-busy");
      }
    }

    function bindFooterDialog(dialog) {
      dialog.querySelectorAll("[data-footer-dialog-close]").forEach((button) => {
        button.addEventListener("click", () => dialog.close());
      });
      dialog.addEventListener("click", (event) => {
        if (event.target === dialog) {
          dialog.close();
        }
      });
    }

    function ensureFooterDialog(dialogId) {
      let dialog = host.querySelector(`#${dialogId}`);
      if (dialog) return dialog;

      const markup = dialogTemplates[dialogId];
      if (!markup) return null;

      const template = document.createElement("template");
      template.innerHTML = markup.trim();
      dialog = template.content.firstElementChild;
      if (!(dialog instanceof HTMLDialogElement)) return null;

      host.append(dialog);
      bindFooterDialog(dialog);
      return dialog;
    }

    host.querySelectorAll("[data-footer-dialog]").forEach((trigger) => {
      trigger.addEventListener("click", async () => {
        const dialogId = trigger.dataset.footerDialog;
        const dialog = ensureFooterDialog(dialogId);
        if (!dialog || typeof dialog.showModal !== "function") return;

        dialog.showModal();

        if (dialogId === "footer-terms-dialog") {
          await syncFooterLegalBody("terms");
        } else if (dialogId === "footer-privacy-dialog") {
          await syncFooterLegalBody("privacy");
        }
      });
    });
  }

  function currentQuickTarget() {
    const path = window.location.pathname;
    if (path === "/") {
      return "home";
    }
    if (path === "/mypage" || path.startsWith("/mypage/")) {
      return "mypage";
    }
    if (path === "/search") {
      return "search";
    }
    return "";
  }

  function renderQuickRemote() {
    const existing = document.querySelector("[data-quick-remote]");
    if (existing) {
      existing.remove();
    }

    // 맛집찾기(지도) 페이지는 지도 화면을 넓게 쓰기 위해 빠른 이동(리모컨)을 표시하지 않는다.
    if (window.location.pathname === "/map") {
      return;
    }

    const items = [
      { id: "home", label: "홈", icon: "home", href: "/" },
      ...(session.authenticated
        ? [{
            id: "mypage",
            label: "마이페이지",
            icon: "person",
            href: "/mypage",
          }]
        : []),
      {
        id: "search",
        label: "검색",
        icon: "search",
        href: "/search",
      },
    ];
    const active = currentQuickTarget();
    const links = items.map((item) => {
      const current = item.id === active ? ' aria-current="page"' : "";
      return `
        <a href="${item.href}"${current} aria-label="${item.label} 페이지로 이동">
          <span class="material-symbols-rounded" aria-hidden="true">${item.icon}</span>
          <small>${item.label}</small>
        </a>`;
    }).join("");

    const remote = document.createElement("nav");
    remote.className = "quick-remote";
    remote.dataset.quickRemote = "";
    remote.setAttribute("aria-label", "페이지 빠른 이동");
    remote.innerHTML = `
      <span class="quick-remote-title">빠른 이동</span>
      ${links}`;
    document.body.append(remote);
  }

  const PASSWORD_RULES = [
    { key: "length", label: "8자 이상", test: (value) => value.length >= 8 },
    { key: "letter", label: "영문 포함", test: (value) => /[A-Za-z]/.test(value) },
    { key: "number", label: "숫자 포함", test: (value) => /\d/.test(value) },
    { key: "special", label: "특수문자 포함", test: (value) => /[^A-Za-z0-9]/.test(value) },
  ];
  let passwordInputSequence = 0;

  function evaluatePassword(value = "") {
    const normalized = String(value);
    const conditions = Object.fromEntries(
      PASSWORD_RULES.map((rule) => [rule.key, rule.test(normalized)]),
    );
    const metCount = Object.values(conditions).filter(Boolean).length;
    const valid = metCount === PASSWORD_RULES.length && normalized.length <= 64;
    let strength = "empty";
    if (normalized) {
      if (valid && normalized.length >= 12) {
        strength = "strong";
      } else if (metCount >= 3) {
        strength = "medium";
      } else {
        strength = "weak";
      }
    }
    return {
      conditions,
      valid,
      strength,
    };
  }

  function passwordMissingLabels(value) {
    const result = evaluatePassword(value);
    return PASSWORD_RULES
      .filter((rule) => !result.conditions[rule.key])
      .map((rule) => rule.label);
  }

  function enhancePasswordPolicy(input, shell) {
    if (!input.hasAttribute("data-password-policy") || input.dataset.passwordPolicyEnhanced === "true") {
      return;
    }
    input.dataset.passwordPolicyEnhanced = "true";

    const feedback = document.createElement("div");
    feedback.className = "password-feedback";
    feedback.id = `${input.id}-feedback`;
    feedback.setAttribute("aria-live", "polite");
    const describedBy = (input.getAttribute("aria-describedby") || "")
      .split(/\s+/)
      .filter(Boolean);
    if (!describedBy.includes(feedback.id)) describedBy.push(feedback.id);
    input.setAttribute("aria-describedby", describedBy.join(" "));
    const list = document.createElement("ul");
    list.className = "password-rule-list";
    const ruleItems = new Map();
    PASSWORD_RULES.forEach((rule) => {
      const item = document.createElement("li");
      item.dataset.passwordRule = rule.key;
      const marker = document.createElement("span");
      marker.className = "password-rule-icon";
      marker.textContent = "○";
      item.append(marker, document.createTextNode(rule.label));
      ruleItems.set(rule.key, item);
      list.append(item);
    });

    const strength = document.createElement("div");
    strength.className = "password-strength";
    strength.innerHTML = `
      <span class="password-strength-track" aria-hidden="true"><i></i></span>
      <span class="password-strength-label">강도: 입력 전</span>`;
    feedback.append(list, strength);
    shell.insertAdjacentElement("afterend", feedback);

    const update = () => {
      const result = evaluatePassword(input.value);
      PASSWORD_RULES.forEach((rule) => {
        const item = ruleItems.get(rule.key);
        const met = result.conditions[rule.key];
        item.classList.toggle("is-met", met);
        item.querySelector(".password-rule-icon").textContent = met ? "✓" : "○";
      });
      feedback.dataset.strength = result.strength;
      const strengthLabel = {
        empty: "입력 전",
        weak: "약함",
        medium: "보통",
        strong: "강함",
      }[result.strength];
      strength.querySelector(".password-strength-label").textContent = `강도: ${strengthLabel}`;

      const missing = passwordMissingLabels(input.value);
      input.setCustomValidity(
        input.value && !result.valid
          ? `비밀번호 조건을 확인해 주세요: ${missing.join(", ")}`
          : "",
      );
    };

    input.addEventListener("input", update);
    input.form?.addEventListener("reset", () => window.requestAnimationFrame(update));
    update();
  }

  function enhancePasswordInput(input) {
    if (!(input instanceof HTMLInputElement) || input.dataset.passwordEnhanced === "true") return;
    if (input.type !== "password") return;

    input.dataset.passwordEnhanced = "true";
    if (!input.id) {
      passwordInputSequence += 1;
      input.id = `fooduck-password-${passwordInputSequence}`;
    }

    const shell = document.createElement("span");
    shell.className = "password-input-shell";
    input.before(shell);
    shell.append(input);

    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "password-visibility-toggle";
    toggle.setAttribute("aria-controls", input.id);
    toggle.setAttribute("aria-label", "비밀번호 보기");
    toggle.setAttribute("aria-pressed", "false");
    const icon = document.createElement("span");
    icon.className = "material-symbols-rounded";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = "visibility";
    toggle.append(icon);
    shell.append(toggle);
    setIcon(icon, "visibility");

    toggle.addEventListener("click", () => {
      const selectionStart = input.selectionStart;
      const selectionEnd = input.selectionEnd;
      const visible = input.type === "text";
      input.type = visible ? "password" : "text";
      toggle.setAttribute("aria-pressed", String(!visible));
      toggle.setAttribute("aria-label", visible ? "비밀번호 보기" : "비밀번호 숨기기");
      setIcon(icon, visible ? "visibility" : "visibility_off");
      input.focus({ preventScroll: true });
      if (selectionStart !== null && selectionEnd !== null) {
        input.setSelectionRange(selectionStart, selectionEnd);
      }
    });

    enhancePasswordPolicy(input, shell);
  }

  function enhancePasswordFields(root = document) {
    if (root instanceof HTMLInputElement) enhancePasswordInput(root);
    root.querySelectorAll?.('input[type="password"]:not([data-password-enhanced="true"])')
      .forEach(enhancePasswordInput);
  }

  document.querySelectorAll("[data-site-header]").forEach(renderHeader);
  document.querySelectorAll("[data-site-footer]").forEach(renderFooter);
  renderQuickRemote();
  enhancePasswordFields();

  const NOTIFICATION_CACHE_STALE_MS = 2 * 60 * 1000;
  const notificationCacheKey = `fooduck:notification-unread:v2:${session.accountId || "unknown"}`;
  const legacyNotificationCacheKey = `fooduck:notification-unread:v1:${session.accountId || "unknown"}`;
  let currentNotificationCount = null;

  try {
    sessionStorage.removeItem(legacyNotificationCacheKey);
  } catch {
    // sessionStorage가 비활성화된 환경에서는 메모리 상태만 사용한다.
  }

  function applyNotificationCount(count) {
    const normalized = Math.max(0, Number(count) || 0);
    currentNotificationCount = normalized;
    document.querySelectorAll("[data-notification-badge]").forEach((badge) => {
      badge.textContent = normalized > 99 ? "99+" : String(normalized);
      badge.hidden = normalized === 0;
    });
    document.querySelectorAll("[data-notification-link]").forEach((link) => {
      link.setAttribute("aria-label", normalized > 0 ? `읽지 않은 알림 ${normalized}개` : "알림");
    });
  }

  function readNotificationCache() {
    try {
      const raw = sessionStorage.getItem(notificationCacheKey);
      if (!raw) return null;
      const cached = JSON.parse(raw);
      const savedAt = Number(cached?.savedAt) || 0;
      const age = Date.now() - savedAt;
      if (!savedAt || age < 0 || age > NOTIFICATION_CACHE_STALE_MS) {
        sessionStorage.removeItem(notificationCacheKey);
        return null;
      }
      return { count: Math.max(0, Number(cached?.count) || 0), age };
    } catch {
      try { sessionStorage.removeItem(notificationCacheKey); } catch {}
      return null;
    }
  }

  function writeNotificationCache(count) {
    try {
      sessionStorage.setItem(notificationCacheKey, JSON.stringify({
        savedAt: Date.now(),
        count: Math.max(0, Number(count) || 0),
      }));
    } catch {
      // 저장 공간 제한/비활성화 시 캐시 없이 기존 흐름으로 동작한다.
    }
  }

  function setNotificationCount(count) {
    const normalized = Math.max(0, Number(count) || 0);
    writeNotificationCache(normalized);
    applyNotificationCount(normalized);
    window.dispatchEvent(new CustomEvent("fooduck:notification-count-changed", {
      detail: { count: normalized },
    }));
    return normalized;
  }

  function invalidateNotificationCache() {
    try {
      sessionStorage.removeItem(notificationCacheKey);
    } catch {
      // 저장소를 사용할 수 없어도 다음 서버 조회는 계속 진행한다.
    }
  }

  async function refreshNotificationBadges(options = {}) {
    if (!session.authenticated) return;

    const force = Boolean(options?.force);
    const cached = force ? null : readNotificationCache();
    if (cached) {
      // 페이지 이동 직후에는 최근 값을 먼저 보여줘 헤더가 API 응답을 기다리지 않게 한다.
      applyNotificationCount(cached.count);
    }

    try {
      const payload = await Api.get("/notifications/unread-count");
      const count = Math.max(0, Number(payload?.data?.count) || 0);
      setNotificationCount(count);
    } catch {
      // 오래된 캐시라도 이미 화면에 표시했다면 네트워크 실패로 갑자기 배지를 지우지 않는다.
      if (!cached && currentNotificationCount === null) {
        document.querySelectorAll("[data-notification-badge]").forEach((badge) => {
          badge.hidden = true;
        });
      }
    }
  }

  refreshNotificationBadges();

  document.querySelectorAll("[data-recommendation-link]").forEach((link) => {
    link.href = recommendationHref();
    if (!session.authenticated) {
      link.title = "맞춤 추천은 로그인 후 이용할 수 있습니다.";
    }
  });

  document.querySelectorAll("[data-logout]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        await Api.logout();
      } catch (error) {
        localStorage.removeItem("accessToken");
      }
      window.location.assign("/");
    });
  });

  document.querySelectorAll(".site-header").forEach((header) => {
    const navToggle = header.querySelector("[data-nav-toggle]");
    if (!navToggle) {
      return;
    }
    navToggle.addEventListener("click", () => {
      const isOpen = header.classList.toggle("is-nav-open");
      navToggle.setAttribute("aria-expanded", String(isOpen));
      navToggle.setAttribute("aria-label", isOpen ? "메뉴 닫기" : "메뉴 열기");
      setIcon(
        navToggle.querySelector(".material-symbols-rounded"),
        isOpen ? "close" : "menu",
      );
    });
    header.querySelectorAll(".nav a").forEach((link) => {
      link.addEventListener("click", () => {
        header.classList.remove("is-nav-open");
        navToggle.setAttribute("aria-expanded", "false");
        setIcon(navToggle.querySelector(".material-symbols-rounded"), "menu");
      });
    });
  });

  enhanceIcons();

  const iconObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      mutation.addedNodes.forEach((node) => {
        if (node.nodeType === Node.ELEMENT_NODE) {
          enhanceIcons(node);
          enhancePasswordFields(node);
        }
      });
    });
  });
  iconObserver.observe(document.body, { childList: true, subtree: true });

  /* ── 영업시간 텍스트 정규화 ────────────────────────────────────────────
     가게마다 저장된 형식이 제각각이다. 구글 지도에서 붙여넣은 영문 표기
     ("Monday\t7 AM–10 PM Tuesday\t..."), "매일 09:00~21:00", 줄바꿈으로 나눈
     한글 표기가 모두 섞여 있고, 특히 영문 표기는 한 줄로 이어져 있어 그대로
     내보내면 줄바꿈 없이 길게 늘어진다.
     요일 단위로 끊어 "요일 : 영업시간" 한 줄씩 보여줄 수 있게 바꾼다.
     요일을 하나도 찾지 못하면 null을 돌려주고, 호출한 쪽이 원문을 그대로 쓴다. */

  const HOURS_DAY_TOKENS = [
    { label: "월요일", aliases: ["monday", "mon", "월요일", "월"] },
    { label: "화요일", aliases: ["tuesday", "tues", "tue", "화요일", "화"] },
    { label: "수요일", aliases: ["wednesday", "wed", "수요일", "수"] },
    { label: "목요일", aliases: ["thursday", "thurs", "thur", "thu", "목요일", "목"] },
    { label: "금요일", aliases: ["friday", "fri", "금요일", "금"] },
    { label: "토요일", aliases: ["saturday", "sat", "토요일", "토"] },
    { label: "일요일", aliases: ["sunday", "sun", "일요일", "일"] },
    { label: "매일", aliases: ["everyday", "every day", "daily", "매일", "연중무휴"] },
    { label: "평일", aliases: ["weekday", "weekdays", "평일"] },
    { label: "주말", aliases: ["weekend", "weekends", "주말"] },
    { label: "공휴일", aliases: ["holiday", "holidays", "공휴일"] },
  ];

  const HOURS_ALIAS_TO_LABEL = new Map();
  HOURS_DAY_TOKENS.forEach(({ label, aliases }) => {
    aliases.forEach((alias) => HOURS_ALIAS_TO_LABEL.set(alias.toLowerCase(), label));
  });

  // 긴 별칭부터 매칭해야 "tues"가 "tue"로 잘리지 않는다.
  const HOURS_ALIAS_PATTERN = [...HOURS_ALIAS_TO_LABEL.keys()]
    .sort((a, b) => b.length - a.length)
    .map((alias) => alias.replace(/ /g, "\\s+"))
    .join("|");

  // 앞뒤가 구분자여야 "매일"의 "일", "1월"의 "월" 같은 걸 요일로 잘못 읽지 않는다.
  const HOURS_DAY_REGEX = new RegExp(
    "(?:^|[\\s,;:·/|()\\[\\]~\\-–—])("
    + HOURS_ALIAS_PATTERN
    + ")(?=[\\s,;:~\\-–—()\\[\\]]|$)",
    "gi",
  );

  const HOURS_RANGE_ONLY = /^[~\-–—]$/;

  function hoursTo24(_match, rawHour, rawMinute, meridiem) {
    let hour = Number(rawHour);
    if (!Number.isFinite(hour) || hour > 12) return _match;
    const upper = meridiem.toUpperCase();
    if (upper === "AM" && hour === 12) hour = 0;
    if (upper === "PM" && hour !== 12) hour += 12;
    return `${String(hour).padStart(2, "0")}:${rawMinute || "00"}`;
  }

  /** "7 AM–10 PM" → "07:00 ~ 22:00" 처럼 표기를 한 가지로 맞춘다. */
  function normalizeHourText(value) {
    return String(value || "")
      .replace(/\s+/g, " ")
      .replace(/\b(\d{1,2})(?::(\d{2}))?\s*(AM|PM)\b/gi, hoursTo24)
      // 시각 사이의 구분자만 물결표로 바꾼다(전화번호 같은 건 건드리지 않는다).
      .replace(/(\d)\s*[~\-–—]\s*(\d)/g, "$1 ~ $2")
      .replace(/\bclosed\b/gi, "휴무")
      .replace(/\bopen 24 hours\b/gi, "24시간 영업")
      .replace(/\b24 hours\b/gi, "24시간 영업")
      .replace(/^[\s:·|]+/, "")
      .replace(/[\s,;·|/]+$/, "")
      .trim();
  }

  /**
   * @returns {{label: string, value: string}[] | null}
   */
  function parseOpeningHours(rawText) {
    const text = String(rawText || "").replace(/\r\n?/g, "\n").trim();
    if (!text) return null;

    const matches = [...text.matchAll(HOURS_DAY_REGEX)];
    if (!matches.length) return null;

    const entries = [];
    matches.forEach((match, index) => {
      const token = match[1];
      const label = HOURS_ALIAS_TO_LABEL.get(token.toLowerCase().replace(/\s+/g, " "));
      if (!label) return;

      const valueStart = match.index + match[0].length;
      const valueEnd = index + 1 < matches.length ? matches[index + 1].index : text.length;
      const value = normalizeHourText(text.slice(valueStart, valueEnd));

      // 앞 요일과 이 요일 사이에 범위 기호만 있으면("월~금", "Mon-Fri") 한 줄로 합친다.
      // 기호가 매칭에 먹혔는지(separator) 앞 항목에 남았는지(value) 둘 다 본다.
      const separator = match[0].slice(0, match[0].length - token.length).trim();
      const previous = entries[entries.length - 1];
      const linksToPrevious = Boolean(previous)
        && (!previous.value || HOURS_RANGE_ONLY.test(previous.value))
        && (HOURS_RANGE_ONLY.test(separator) || HOURS_RANGE_ONLY.test(previous.value));
      if (linksToPrevious) {
        previous.label = `${previous.label} ~ ${label}`;
        previous.value = value;
        return;
      }
      entries.push({ label, value });
    });

    const usable = entries.filter((entry) => entry.value);
    return usable.length ? usable : null;
  }

  /** @returns {string[] | null} ["월요일 : 07:00 ~ 22:00", ...] */
  function openingHoursLines(rawText) {
    const entries = parseOpeningHours(rawText);
    return entries ? entries.map((entry) => `${entry.label} : ${entry.value}`) : null;
  }

  window.FooduckHours = {
    parse: parseOpeningHours,
    lines: openingHoursLines,
    normalize: normalizeHourText,
  };

  window.FooduckIcons = { set: setIcon, enhance: enhanceIcons };
  window.FooduckEmojis = {
    items: FOODUCK_CUSTOM_EMOJIS,
    renderText: renderCustomEmojiText,
    populatePicker: populateCustomEmojiPicker,
    attachEditor: attachCustomEmojiEditor,
    insertIntoEditor: insertCustomEmojiIntoEditor,
    refreshEditor: refreshCustomEmojiEditor,
  };
  window.FooduckSession = {
    ...session,
    canManageBusiness,
    isAdmin,
    hasAuthority,
    recommendationHref,
  };
  window.FooduckProfile = {
    AUTHORITY_LABELS,
    authorityLabel,
    createSummary: createProfileSummary,
    formatDate: formatProfileDate,
    primaryAuthorityCode,
  };
  window.FooduckNotifications = {
    refreshUnreadCount: refreshNotificationBadges,
    setUnreadCount: setNotificationCount,
    invalidateCache: invalidateNotificationCache,
  };
  window.FooduckPassword = {
    evaluate: evaluatePassword,
    isValid: (value) => evaluatePassword(value).valid,
    missingLabels: passwordMissingLabels,
    enhance: enhancePasswordFields,
  };

  function initializeScrollTopButton() {
    if (document.querySelector(".board-scroll-top")) return;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "board-scroll-top";
    button.textContent = "↑";
    button.title = "맨 위로 이동";
    button.setAttribute("aria-label", "맨 위로 이동");
    button.hidden = true;
    document.body.append(button);

    let ticking = false;
    let isVisible = false;
    const updateVisibility = () => {
      const nextVisible = window.scrollY > 450;
      if (nextVisible !== isVisible) {
        isVisible = nextVisible;
        button.hidden = !nextVisible;
      }
      ticking = false;
    };

    window.addEventListener("scroll", () => {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(updateVisibility);
    }, { passive: true });

    button.addEventListener("click", () => {
      const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      window.scrollTo({
        top: 0,
        behavior: reduceMotion ? "auto" : "smooth",
      });
    });

    updateVisibility();
  }

  // 모바일에서 페이지 전체를 화면 폭에 맞춰 확대·축소한다.
  // 기준 폭에서 그린 화면을 그대로 키우고 줄이므로 요소 사이의 비율이 유지된다.
  // 실제 확대는 common.css의 body { zoom: var(--page-scale) }가 맡는다.
  const PAGE_SCALE_BASE_WIDTH = 390;
  const PAGE_SCALE_BREAKPOINT = 640;
  const PAGE_SCALE_MIN = 0.85;
  const PAGE_SCALE_MAX = 1.25;

  function applyPageScale() {
    const width = window.innerWidth || document.documentElement.clientWidth || 0;
    if (!width || width > PAGE_SCALE_BREAKPOINT) {
      document.documentElement.style.removeProperty("--page-scale");
      return;
    }
    const scale = Math.min(
      PAGE_SCALE_MAX,
      Math.max(PAGE_SCALE_MIN, width / PAGE_SCALE_BASE_WIDTH),
    );
    document.documentElement.style.setProperty("--page-scale", scale.toFixed(4));
  }

  function initializePageScale() {
    applyPageScale();
    // 배율 계산은 innerWidth 하나만 읽고 변수 하나만 쓰므로 바로 처리한다.
    // (requestAnimationFrame으로 미루면 화면이 가려진 탭에서 갱신이 밀린다.)
    window.addEventListener("resize", applyPageScale, { passive: true });
    window.addEventListener("orientationchange", applyPageScale, { passive: true });
  }

  initializePageScale();
  initializeScrollTopButton();
})();
