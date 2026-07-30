// Stub injection point for the real IITC-CE bundle (see §3 of the
// architecture doc). IITC-CE no longer ships a single checked-in
// total-conversion-build.user.js — it's produced by their build.py /
// build_mobile.py pipeline — so this file is a placeholder proving the
// WebView -> evaluateJavascript injection path works end-to-end. Swap
// this file's contents for the real built output once we decide how to
// source/track it (vendored build artifact vs. a Gradle fetch task).
(function () {
  if (window.__lovelaceBootstrapInjected) {
    return;
  }
  window.__lovelaceBootstrapInjected = true;
  console.log("[Lovelace] bootstrap injected into", location.href);

  var banner = document.createElement("div");
  banner.textContent = "Lovelace bootstrap injected (IITC not wired up yet)";
  banner.style.position = "fixed";
  banner.style.top = "0";
  banner.style.left = "0";
  banner.style.right = "0";
  banner.style.zIndex = "999999";
  banner.style.padding = "6px 10px";
  banner.style.fontFamily = "sans-serif";
  banner.style.fontSize = "12px";
  banner.style.background = "#2F80FF";
  banner.style.color = "#fff";
  banner.style.textAlign = "center";
  document.body.appendChild(banner);
})();
