package com.roanokeresistance.lovelace.plugins

import org.json.JSONObject

// Plugin source straight from the IITC-CE repo is unbuilt: it just declares
// `var setup = function() {...}` and relies on IITC-CE's own Python build
// tool to wrap it with a self-injecting footer that registers `setup` with
// `window.bootPlugins`/`window.iitcLoaded`. We don't run that build step, so
// this harness reproduces just enough of it: the raw source runs inside our
// own IIFE (keeping its `var setup` out of the global scope), and we grab
// that local `setup` and register it with IITC's real boot mechanism
// ourselves.
private const val PLUGIN_BOOT_HARNESS_TEMPLATE = """
(function() {
%s
;(function(pluginSetup) {
  if (typeof pluginSetup !== 'function') return;
  if (!pluginSetup.info) pluginSetup.info = {};
  if (window.iitcLoaded) { pluginSetup(); }
  else { window.bootPlugins = window.bootPlugins || []; window.bootPlugins.push(pluginSetup); }
})(typeof setup !== 'undefined' ? setup : undefined);
})();
"""

fun wrapPluginScript(rawScript: String): String =
    PLUGIN_BOOT_HARNESS_TEMPLATE.format(rawScript)

fun wrapPluginStyle(css: String): String =
    "(function(){var s=document.createElement('style');s.textContent=${JSONObject.quote(css)};document.head.appendChild(s);})();"
