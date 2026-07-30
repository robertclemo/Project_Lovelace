package com.roanokeresistance.lovelace.map

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val INTEL_URL = "https://intel.ingress.com/intel"
private const val IITC_ASSET = "iitc_total_conversion.user.js"

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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val webView = WebView(context).apply {
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
                    view.evaluateJavascript(iitcScript, null)
                }
            }

            webView.loadUrl(INTEL_URL)
            webView
        },
        onRelease = { it.destroy() }
    )
}
