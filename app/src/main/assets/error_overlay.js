// On-screen error overlay so JS errors are visible without needing a
// USB/logcat connection to the device — useful for testing on hardware
// where we can't easily attach a debugger.
(function () {
  if (window.__lovelaceErrorOverlay) {
    return;
  }
  window.__lovelaceErrorOverlay = true;

  var box = document.createElement('div');
  box.id = 'lovelace-error-overlay';
  box.style.position = 'fixed';
  box.style.bottom = '0';
  box.style.left = '0';
  box.style.right = '0';
  box.style.maxHeight = '45%';
  box.style.overflowY = 'auto';
  box.style.zIndex = '2147483647';
  box.style.background = 'rgba(180,0,0,0.92)';
  box.style.color = '#fff';
  box.style.fontFamily = 'monospace';
  box.style.fontSize = '11px';
  box.style.lineHeight = '1.4';
  box.style.padding = '6px';
  box.style.display = 'none';
  box.style.whiteSpace = 'pre-wrap';
  box.style.wordBreak = 'break-word';
  (document.documentElement || document.body).appendChild(box);

  function log(msg) {
    box.style.display = 'block';
    var line = document.createElement('div');
    line.style.borderBottom = '1px solid rgba(255,255,255,0.3)';
    line.style.paddingBottom = '2px';
    line.style.marginBottom = '2px';
    line.textContent = msg;
    box.appendChild(line);
  }

  window.addEventListener('error', function (e) {
    log('ERROR: ' + e.message + ' @ ' + (e.filename || '?') + ':' + e.lineno + ':' + e.colno);
  });
  window.addEventListener('unhandledrejection', function (e) {
    var reason = e.reason;
    var text = reason && reason.message ? reason.message : String(reason);
    log('UNHANDLED REJECTION: ' + text);
  });
  // CSP and Trusted Types violations don't throw or fire 'error' — they're
  // silently blocked and only reported through this event (or the console).
  window.addEventListener('securitypolicyviolation', function (e) {
    log('CSP VIOLATION: blocked-uri=' + e.blockedURI + ' directive=' + e.violatedDirective + ' source=' + e.sourceFile + ':' + e.lineNumber);
  });

  var origConsoleError = console.error;
  console.error = function () {
    log('console.error: ' + Array.prototype.slice.call(arguments).join(' '));
    origConsoleError.apply(console, arguments);
  };
  var origConsoleWarn = console.warn;
  console.warn = function () {
    log('console.warn: ' + Array.prototype.slice.call(arguments).join(' '));
    origConsoleWarn.apply(console, arguments);
  };

  log('Lovelace error overlay active');
})();
