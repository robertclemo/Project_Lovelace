package com.roanokeresistance.lovelace.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.roanokeresistance.lovelace.BuildConfig
import com.roanokeresistance.lovelace.viewportsync.LeaderState
import com.roanokeresistance.lovelace.viewportsync.Viewport
import com.roanokeresistance.lovelace.viewportsync.ViewportSyncManager

private const val INTEL_URL = "https://intel.ingress.com/intel"
private const val IITC_ASSET = "iitc_total_conversion.user.js"

// IITC-CE's build wraps the script as `function wrapper(plugin_info) {...}`
// then self-injects by creating a <script> tag and appending its text via
// document.createTextNode. That DOM-based injection is meant for
// Greasemonkey-style contexts and gets blocked by intel.ingress.com's
// Trusted Types CSP (require-trusted-types-for 'script'). We already run
// this whole file directly in page context via evaluateJavascript, so that
// self-reinjection is both redundant and the actual cause of the blank
// map — truncate it and call wrapper() ourselves instead.
private const val IITC_SELF_INJECT_MARKER = "// inject code into site context"

private fun patchIitcScript(rawScript: String): String {
    val markerIndex = rawScript.indexOf(IITC_SELF_INJECT_MARKER)
    if (markerIndex < 0) {
        return rawScript
    }
    return rawScript.substring(0, markerIndex) + "\nwrapper({});"
}

private const val VIEWPORT_BRIDGE_NAME = "LovelaceViewport"

// Leaflet's 'moveend' fires once per pan/zoom gesture settling, not on every
// drag frame, so it's already the "on-change" throttle §6 of the
// architecture doc calls for — no extra debounce timer needed here.
// window.map exists early as a placeholder from the page's own Google Maps
// API load, well before IITC's async bootstrap replaces it with the real
// Leaflet instance — checking truthiness alone attaches to the wrong object
// (no .on method) and throws, so poll for the Leaflet API shape instead.
private const val VIEWPORT_HOOK_SCRIPT = """
(function() {
  if (window.__lovelaceViewportHooked) return;
  window.__lovelaceViewportHooked = true;
  function hook() {
    if (!window.map || typeof window.map.on !== 'function') { setTimeout(hook, 500); return; }
    console.log('[lovelace] viewport hook attached');
    window.map.on('moveend', function() {
      var c = window.map.getCenter();
      $VIEWPORT_BRIDGE_NAME.onViewportChanged(c.lat, c.lng, window.map.getZoom());
    });
  }
  hook();
})();
"""

private class ViewportBridge(private val report: (Double, Double, Float) -> Unit) {
    @JavascriptInterface
    fun onViewportChanged(lat: Double, lng: Double, zoom: Float) {
        report(lat, lng, zoom)
    }
}

private fun WebView.applyBaseSettings() {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    // Some sites treat the "; wv" WebView marker as an unsupported
    // browser; stripping it matches what IITC Mobile does.
    settings.userAgentString = settings.userAgentString.replace("; wv", "")
}

/**
 * WebView loading the live intel map (§3 of the architecture doc), with
 * the IITC-CE core script (built from source via their build.py — see
 * THIRD_PARTY_NOTICES_IITC-CE_LICENSE.txt at the repo root) injected once
 * the page finishes loading. The player signs into Ingress/Google
 * normally inside this WebView, same as a desktop browser — nothing here
 * intercepts or stores those credentials; cookies are only enabled so the
 * login session persists across app restarts, exactly like a normal
 * browser tab would. IITC plugins aren't bundled yet — this is core-only.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val viewportSync = remember { ViewportSyncManager() }

    var leaderState by remember { mutableStateOf<LeaderState?>(null) }
    val isLeading = leaderState?.leaderUid == currentUser?.uid
    val isLeadingState = rememberUpdatedState(isLeading)
    var webView by remember { mutableStateOf<WebView?>(null) }

    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    LaunchedEffect(Unit) {
        viewportSync.observeLeaderState().collect { state ->
            leaderState = state
            val viewport = state?.viewport
            if (viewport != null && state.leaderUid != currentUser?.uid) {
                webView?.evaluateJavascript(
                    "window.map && window.map.setView([${viewport.lat},${viewport.lng}], ${viewport.zoom});",
                    null
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val bridge = ViewportBridge { lat, lng, zoom ->
                    if (isLeadingState.value) {
                        viewportSync.publishViewport(Viewport(lat, lng, zoom))
                    }
                }

                val webViewInstance = WebView(context).apply {
                    applyBaseSettings()
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    addJavascriptInterface(bridge, VIEWPORT_BRIDGE_NAME)
                }
                webView = webViewInstance

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webViewInstance, true)
                }

                webViewInstance.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        val iitcScript = context.assets.open(IITC_ASSET)
                            .bufferedReader()
                            .use { it.readText() }
                        view.evaluateJavascript(patchIitcScript(iitcScript), null)
                        view.evaluateJavascript(VIEWPORT_HOOK_SCRIPT, null)
                        // The Ingress login sets its session cookie mid-page-load;
                        // flush explicitly so it survives a WebView/process restart
                        // instead of relying on the OS to persist it eventually.
                        CookieManager.getInstance().flush()
                    }
                }

                // Google Identity Services (and some OAuth flows) call
                // window.open() for a popup-based sign-in handshake. WebView has
                // no tab/window concept by default, so without this override it
                // silently navigates the *main* WebView into the popup's URL
                // instead — permanently stranding it on an internal Google
                // helper page (e.g. accounts.google.com/gsi/transform) that was
                // never meant to be shown as a full page. This was the actual
                // cause of the blank map after login, not a rendering bug.
                webViewInstance.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d("MapScreenJS", "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                        return true
                    }

                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message
                    ): Boolean {
                        val popupWebView = WebView(context).apply { applyBaseSettings() }
                        val dialog = Dialog(context).apply {
                            setContentView(
                                popupWebView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            setOnCancelListener { popupWebView.destroy() }
                        }

                        popupWebView.webViewClient = WebViewClient()
                        popupWebView.webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView) {
                                dialog.dismiss()
                                popupWebView.destroy()
                            }
                        }

                        val transport = resultMsg.obj as WebView.WebViewTransport
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()

                        dialog.show()
                        return true
                    }
                }

                webViewInstance.loadUrl(INTEL_URL)
                webViewInstance
            },
            onRelease = { it.destroy() }
        )

        LeaderControl(
            leaderState = leaderState,
            isLeading = isLeading,
            // IITC's own chrome occupies the rest of the screen edges: the
            // tab row across the top, zoom controls top-left, and a status
            // bar along the bottom — this is the one clear spot, just under
            // the layer-selector icon.
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 8.dp),
            onBecomeLeader = {
                currentUser?.let { user ->
                    viewportSync.becomeLeader(user.uid, user.displayName ?: user.email ?: "Agent")
                }
            },
            onStopLeading = { viewportSync.stopLeading() }
        )
    }
}

@Composable
private fun LeaderControl(
    leaderState: LeaderState?,
    isLeading: Boolean,
    modifier: Modifier = Modifier,
    onBecomeLeader: () -> Unit,
    onStopLeading: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            when {
                isLeading -> Button(onClick = onStopLeading) { Text("Leading — tap to stop") }
                leaderState != null -> Button(onClick = onBecomeLeader) {
                    Text("Following ${leaderState.leaderName} — tap to take over")
                }
                else -> Button(onClick = onBecomeLeader) { Text("Become Leader") }
            }
        }
    }
}
