(() => {
  const session = window.FooduckSession;
  const loginGate = document.getElementById("business-login-gate");
  const application = document.getElementById("business-application");
  const dashboard = document.getElementById("business-dashboard");
  if (!session || !loginGate || !application || !dashboard) return;

  if (!session.authenticated) {
    loginGate.hidden = false;
  } else if (session.canManageBusiness) {
    dashboard.hidden = false;
  } else {
    application.hidden = false;
  }
})();
