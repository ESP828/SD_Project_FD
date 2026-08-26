(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("restaurants-access-gate");
  const dashboard = document.getElementById("restaurants-dashboard");
  if (!session || !gate || !dashboard) return;

  if (!session.isAdmin) {
    gate.hidden = false;
    return;
  }
  dashboard.hidden = false;

  const statusButtons = document.querySelectorAll(".restaurants-status-btn");
  const searchForm = document.getElementById("restaurants-search-form");
  const searchInput = document.getElementById("restaurants-search-input");
  const tableBody = document.getElementById("restaurants-table-body");
  const countLabel = document.getElementById("restaurants-count");
  const paginationHost = document.getElementById("restaurants-pagination");
  const PAGE_SIZE = 25;

  const editDialog = document.getElementById("restaurant-edit-dialog");
  const editForm = document.getElementById("restaurant-edit-form");
  const editCategory = document.getElementById("restaurant-edit-category");
  const editOwner = document.getElementById("restaurant-edit-owner");
  const editCreated = document.getElementById("restaurant-edit-created");
  const editName = document.getElementById("restaurant-edit-name");
  const editAddress = document.getElementById("restaurant-edit-address");
  const editAddressDetail = document.getElementById("restaurant-edit-address-detail");
  const editPhone = document.getElementById("restaurant-edit-phone");
  const editStatus = document.getElementById("restaurant-edit-status");
  const editHours = document.getElementById("restaurant-edit-hours");
  const editClosedDays = document.getElementById("restaurant-edit-closed-days");
  const editDescription = document.getElementById("restaurant-edit-description");
  const editStatusMsg = document.getElementById("restaurant-edit-status-msg");

  let currentStatus = "";
  let editingRestaurantId = null;
  let currentPage = 0;

  const STATUS_LABELS = { ACTIVE: "활성", INACTIVE: "비활성", DELETED: "삭제" };

  function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    }[char]));
  }

  function renderRows(restaurants) {
    if (restaurants.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="8" class="restaurants-empty">조건에 맞는 음식점이 없습니다.</td></tr>';
      return;
    }
    tableBody.innerHTML = restaurants.map((restaurant) => `
      <tr data-restaurant-id="${restaurant.restaurantId}">
        <td>${escapeHtml(restaurant.name)}</td>
        <td>${escapeHtml(restaurant.categoryName || "-")}</td>
        <td class="restaurants-address">${escapeHtml(restaurant.address)}</td>
        <td>${escapeHtml(restaurant.phone || "-")}</td>
        <td>${escapeHtml(restaurant.ownerNickname || restaurant.ownerLoginId || "-")}</td>
        <td><span class="restaurants-badge restaurants-badge--${restaurant.status}">${STATUS_LABELS[restaurant.status] || restaurant.status}</span></td>
        <td>${formatDate(restaurant.createdAt)}</td>
        <td>
          <button type="button" class="button button-secondary button-sm" data-edit="${restaurant.restaurantId}">상세·수정</button>
        </td>
      </tr>
    `).join("");
  }

  let currentRestaurants = [];

  async function loadRestaurants(page = 0) {
    tableBody.innerHTML = '<tr><td colspan="8" class="restaurants-loading">불러오는 중...</td></tr>';
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (searchInput.value.trim()) params.set("keyword", searchInput.value.trim());
      if (currentStatus) params.set("status", currentStatus);
      const response = await Api.get(`/admin/restaurants?${params.toString()}`);
      const pageData = window.FooduckPagination.normalize(response.data, { pageKey: "page" });
      currentRestaurants = pageData.content;
      currentPage = pageData.number;
      renderRows(currentRestaurants);
      countLabel.textContent = `총 ${pageData.totalElements.toLocaleString("ko-KR")}곳`;
      window.FooduckPagination.render(paginationHost, pageData, (nextPage) => {
        loadRestaurants(nextPage);
        paginationHost.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    } catch (error) {
      tableBody.innerHTML = `<tr><td colspan="8" class="restaurants-empty">${error.message || "음식점 목록을 불러오지 못했습니다."}</td></tr>`;
      countLabel.textContent = "";
      paginationHost.replaceChildren();
    }
  }

  statusButtons.forEach((button) => {
    button.addEventListener("click", () => {
      statusButtons.forEach((b) => b.classList.remove("is-active"));
      button.classList.add("is-active");
      currentStatus = button.dataset.status;
      loadRestaurants();
    });
  });

  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    loadRestaurants();
  });

  tableBody.addEventListener("click", (event) => {
    const editButton = event.target.closest("[data-edit]");
    if (!editButton) return;

    const restaurantId = editButton.getAttribute("data-edit");
    const restaurant = currentRestaurants.find((item) => String(item.restaurantId) === restaurantId);
    if (!restaurant) return;

    editingRestaurantId = restaurantId;
    editCategory.textContent = restaurant.categoryName || "-";
    editOwner.textContent = restaurant.ownerNickname || restaurant.ownerLoginId || "-";
    editCreated.textContent = formatDate(restaurant.createdAt);
    editName.value = restaurant.name || "";
    editAddress.value = restaurant.address || "";
    editAddressDetail.value = restaurant.addressDetail || "";
    editPhone.value = restaurant.phone || "";
    editStatus.value = restaurant.status;
    editHours.value = restaurant.openingHours || "";
    editClosedDays.value = restaurant.closedDays || "";
    editDescription.value = restaurant.description || "";
    editStatusMsg.textContent = "";
    editDialog.showModal();
  });

  editDialog.querySelector("[data-close]").addEventListener("click", () => editDialog.close());

  editForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!editingRestaurantId) return;
    try {
      await Api.patch(`/admin/restaurants/${editingRestaurantId}`, {
        name: editName.value.trim(),
        address: editAddress.value.trim(),
        addressDetail: editAddressDetail.value.trim(),
        phone: editPhone.value.trim(),
        openingHours: editHours.value.trim(),
        closedDays: editClosedDays.value.trim(),
        description: editDescription.value.trim(),
        status: editStatus.value,
      });
      editDialog.close();
      loadRestaurants();
    } catch (error) {
      editStatusMsg.textContent = error.message || "저장 중 오류가 발생했습니다.";
    }
  });

  loadRestaurants();
})();
