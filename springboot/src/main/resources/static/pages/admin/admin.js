(() => {
  const session = window.FooduckSession;
  const gate = document.getElementById("admin-access-gate");
  const dashboard = document.getElementById("admin-dashboard");
  if (!session || !gate || !dashboard) return;

  if (session.isAdmin) {
    dashboard.hidden = false;
  } else {
    gate.hidden = false;
  }
})();
