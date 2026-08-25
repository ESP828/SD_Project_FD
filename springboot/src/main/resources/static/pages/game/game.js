(() => {
  const tabs = document.querySelectorAll(".game-tab");
  const frame = document.getElementById("game-frame");
  if (!tabs.length || !frame) return;

  // 게임 두 개가 서로 다른 HTML/CSS/JS를 그대로 쓰기 때문에(아이디·전역변수 충돌 방지),
  // 한 페이지에 같이 끼워넣지 않고 iframe으로 완전히 분리해서 보여준다.
  //
  // 사다리 게임은 설정→사다리→결과로 화면이 "누적"돼서 커지는 구조라 매번 실제
  // 콘텐츠 높이에 맞춰 다시 재야 하지만(dynamic), 밸런스게임은 문항마다 화면이
  // 통째로 바뀌면서 시작 화면과 결과 화면의 길이가 원래 서로 달라서, 화면이 바뀔
  // 때마다 박스 크기가 들쭉날쭉해 보였다. 밸런스게임은 가장 긴 화면(결과 화면)
  // 기준으로 넉넉한 고정 높이를 주고, 원본의 min-height:100vh를 그대로 살려서
  // 그 안에서 항상 세로 중앙 정렬되게 한다 — 어떤 화면이든 박스 크기 자체가 안 바뀜.
  const SOURCES = {
    balance: { src: "/pages/game/balance/index.html", fixedHeight: 800 },
    ladder: { src: "/pages/game/ladder/index.html" },
  };

  // 사다리 게임 쪽: body에 min-height:100vh가 걸려 있는데, 이건 "전체 화면을 채우는
  // 독립 페이지"로 만들어진 원본 그대로라 그렇다. iframe 안에서는 iframe 자신의
  // 높이가 곧 100vh라서, 우리가 재는 scrollHeight가 "지금 준 높이"를 그대로
  // 되돌려주는 순환이 생겨 박스가 실제 내용보다 훨씬 커진다. 이 100vh 강제를
  // 풀어야 진짜 필요한 만큼(딱 스크롤 안 생길 만큼)으로 줄어든다.
  function neutralizeFullHeightLayout(doc) {
    if (doc.getElementById("fd-frame-fit-override")) return;
    const style = doc.createElement("style");
    style.id = "fd-frame-fit-override";
    style.textContent = "html,body{min-height:0!important;height:auto!important;}";
    doc.head.appendChild(style);
  }

  let frameMutationObserver = null;

  function fitFrameHeight({ followScroll = false } = {}) {
    let doc;
    try {
      doc = frame.contentDocument;
    } catch (error) {
      return; // 혹시라도 다른 출처면 접근이 막히므로 기본 높이를 그대로 둔다.
    }
    if (!doc || !doc.documentElement) return;
    const previousHeight = Number.parseInt(frame.style.height, 10) || 0;
    const measure = () => Math.max(
      doc.documentElement.scrollHeight,
      doc.body ? doc.body.scrollHeight : 0,
    );
    let height = measure();
    if (height <= previousHeight) {
      // scrollHeight는 "지금 iframe 뷰포트 높이"보다 작게는 절대 안 나온다(콘텐츠가
      // 그보다 짧아도 뷰포트만큼은 채운 걸로 잡힘). 콘텐츠가 늘어난 경우엔 이 값을
      // 그대로 믿어도 되지만(넘친 만큼 이미 정확히 잡힘), 줄어들었을 수도 있는
      // 경우에만 잠깐 확 줄여서 진짜 필요한 높이를 다시 잰다.
      // (게임이 한창 진행 중일 때 매번 이렇게 확 줄이면, 페이지 전체 높이가 순간
      // 짧아지면서 브라우저가 스크롤 위치를 강제로 위로 당겨버려 사용자가 스크롤을
      // 못 내리는 것처럼 느껴지는 문제가 있었다 — 그래서 정말 필요할 때만 줄인다.)
      frame.style.height = "0px";
      height = measure();
    }
    if (height <= 0) return;
    frame.style.height = `${height}px`;
    // 게임 안에서 스스로 하는 scrollIntoView는 iframe 안쪽에만 적용되고 우리 페이지는
    // 안 움직인다(사다리 2단계로 넘어가도 화면이 안 내려가던 이유). 게임 진행 중에
    // 박스가 늘어난 만큼 우리 페이지도 같이 스크롤해서 새로 나온 부분이 보이게 한다.
    if (followScroll && height > previousHeight + 30) {
      window.scrollBy({ top: height - previousHeight, behavior: "smooth" });
    }
  }

  function watchFrameContent() {
    if (frameMutationObserver) frameMutationObserver.disconnect();
    let doc;
    try {
      doc = frame.contentDocument;
    } catch (error) {
      return;
    }
    if (!doc || !doc.body) return;
    // scrollHeight는 자바스크립트로 DOM이 바뀔 때만 늘어나므로(레이아웃 자체 크기 변화가
    // 아니라 ResizeObserver로는 못 잡음), 자식 문서의 DOM 변화를 직접 관찰한다. 사다리
    // 애니메이션은 매 프레임(rAF, 초당 60번)마다 <canvas>의 width/height를 다시
    // 설정하는데(반영 속성이라 이것도 "속성 변화"로 잡힘), 그것까지 다 보고 매번
    // fitFrameHeight를 돌릴 필요는 없어서 class/style/hidden/disabled 정도로만 좁힌다.
    let pendingFit = false;
    const startButton = doc.getElementById("startGame");
    frameMutationObserver = new MutationObserver((mutations) => {
      if (!pendingFit) {
        pendingFit = true;
        requestAnimationFrame(() => {
          pendingFit = false;
          fitFrameHeight({ followScroll: true });
        });
      }
      // "사다리 출발!"을 누른 순간엔 결과가 나오기 전이라 박스 높이가 아직 안 늘어나서
      // (자연히 스크롤로 따라갈 것도 없어서) 위 followScroll이 아무것도 안 한다. 그런데
      // 그 몇 초 사이엔 페이지가 실제로 더 내려갈 데가 없어서(딱 그만큼만 길어서) 사용자가
      // 스크롤을 시도해도 안 움직이는 게, 화면이 사다리 쪽을 안 보여주고 있으면 "막혔다"
      // 처럼 느껴진다. 버튼을 누르는 그 순간 사다리 박스가 화면에 잘 들어오게 스크롤해서
      // 애니메이션이 끝날 때까지 볼 게 다 보이게 해준다.
      const startedLadder = mutations.some((m) =>
        m.type === "attributes" && m.attributeName === "disabled" && m.target === startButton,
      ) && startButton?.disabled;
      if (startedLadder) {
        const canvas = doc.getElementById("ladderCanvas");
        if (canvas) {
          // canvas.getBoundingClientRect()는 iframe 안쪽 뷰포트 기준 좌표라서, iframe
          // 자신의 위치(frameRect.top)를 더해야 부모 페이지 기준 좌표가 된다.
          const frameRect = frame.getBoundingClientRect();
          const canvasRect = canvas.getBoundingClientRect();
          const canvasCenterY = window.scrollY + frameRect.top + canvasRect.top + canvasRect.height / 2;
          window.scrollTo({ top: Math.max(0, canvasCenterY - window.innerHeight / 2), behavior: "smooth" });
        } else {
          frame.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }
    });
    frameMutationObserver.observe(doc.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["class", "style", "hidden", "disabled"],
    });
  }

  frame.addEventListener("load", () => {
    const key = frame.dataset.loaded;
    const entry = SOURCES[key];
    if (entry && entry.fixedHeight) {
      // 고정 높이 게임은 원본의 100vh 레이아웃을 그대로 살려서 그 안에서 중앙
      // 정렬되게 두고, 우리가 따로 관찰·재측정하지 않는다(화면이 바뀌어도 박스는
      // 항상 같은 크기).
      frame.style.height = `${entry.fixedHeight}px`;
      if (frameMutationObserver) frameMutationObserver.disconnect();
      return;
    }
    try {
      const doc = frame.contentDocument;
      if (doc && doc.head) {
        neutralizeFullHeightLayout(doc);
      }
    } catch (error) {
      // 다른 출처면 접근이 막히므로 그냥 원본 레이아웃 그대로 둔다.
    }
    fitFrameHeight();
    watchFrameContent();
    // 스타일 적용·폰트 로딩 등으로 로드 직후 한 번 더 높이가 바뀌는 경우를 대비한 안전장치.
    setTimeout(fitFrameHeight, 300);
  });

  function activate(key, { reset = false } = {}) {
    const entry = SOURCES[key] || SOURCES.balance;
    tabs.forEach((tab) => {
      const isActive = tab.dataset.game === key;
      tab.classList.toggle("is-active", isActive);
      tab.setAttribute("aria-selected", String(isActive));
    });
    if (frame.dataset.loaded !== key) {
      frame.dataset.loaded = key;
      frame.src = entry.src;
      return;
    }
    // 이미 같은 탭이면 src가 안 바뀌어서 브라우저가 다시 안 불러온다. 탭을
    // 누를 때마다(다시 눌러도) 진행 중이던 게임 상태·커진 박스 높이가 리셋되게
    // 강제로 새로고침한다.
    if (reset) {
      try {
        frame.contentWindow.location.reload();
      } catch (error) {
        frame.src = entry.src;
      }
    }
  }

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => activate(tab.dataset.game, { reset: true }));
  });

  const initialTab = document.querySelector(".game-tab.is-active") || tabs[0];
  activate(initialTab.dataset.game);

  // 창 너비가 바뀌면 iframe 안쪽 반응형 레이아웃(줄바꿈 등)도 같이 바뀌므로 다시 잰다.
  // 고정 높이 게임은 그대로 둔다(다시 재면 고정 높이가 깨짐).
  window.addEventListener("resize", () => {
    const entry = SOURCES[frame.dataset.loaded];
    if (entry && entry.fixedHeight) return;
    fitFrameHeight();
  });
})();
