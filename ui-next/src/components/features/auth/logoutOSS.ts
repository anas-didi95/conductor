/**
 * OSS logout: POST to the server's /logout endpoint (Spring Security default).
 * Called only when static resources protection is enabled.
 */
export async function logoutOSS(): Promise<void> {
  try {
    await fetch("/logout", {
      method: "POST",
      credentials: "include",
      redirect: "follow",
    });
  } catch {
    // /logout not available — fall through to redirect
  }
  // Always redirect — either to login page (protected mode) or home
  window.location.href = "/login.html";
}
