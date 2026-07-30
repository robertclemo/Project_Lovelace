package com.roanokeresistance.lovelace.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.roanokeresistance.lovelace.BuildConfig

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

    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val webView = WebView(context).apply {
                applyBaseSettings()
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    val iitcScript = context.assets.open(IITC_ASSET)
                        .bufferedReader()
                        .use { it.readText() }
                    view.evaluateJavascript(patchIitcScript(iitcScript), null)
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
            webView.webChromeClient = object : WebChromeClient() {
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

            webView.loadUrl(INTEL_URL)
            webView
        },
        onRelease = { it.destroy() }
    )
}
