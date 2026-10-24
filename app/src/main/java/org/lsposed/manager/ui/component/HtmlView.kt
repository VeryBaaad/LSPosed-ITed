/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 - 2026 LSPosed Contributors
 */

package org.lsposed.manager.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.LayoutDirection
import org.lsposed.manager.App
import org.lsposed.manager.ui.theme.LocalDarkTheme
import org.lsposed.manager.util.NavUtil

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlContent(
    html: String?,
    modifier: Modifier = Modifier,
    baseUrl: String = "https://github.com",
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "rtl" else "ltr"

    val body = remember(html, dark, direction) {
        val content = html ?: "<center>${context.getString(org.lsposed.manager.R.string.list_empty)}</center>"
        val template = runCatching {
            (if (dark) App.HTML_TEMPLATE_DARK else App.HTML_TEMPLATE).get()
        }.getOrElse {
            "<html dir=\"@dir@\"><body>@body@</body></html>"
        }
        template.replace("@dir@", direction).replace("@body@", content)
    }

    key(body) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = true
                    allowContentAccess = false
                    allowFileAccess = false
                    setGeolocationEnabled(false)
                    cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                    textZoom = 100
                    blockNetworkImage = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url ?: return false
                        return openUrl(ctx, url)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val uri = url?.let(Uri::parse) ?: return false
                        return openUrl(ctx, uri)
                    }
                }
                loadDataWithBaseURL(baseUrl, body, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
    }
}

private fun openUrl(context: Context, uri: Uri): Boolean {
    return try {
        if (uri.scheme == "http" || uri.scheme == "https") {
            val activity = unwrapActivity(context)
            if (activity != null) {
                NavUtil.startURL(activity, uri)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            true
        } else {
            false
        }
    } catch (_: Throwable) {
        false
    }
}

private fun unwrapActivity(context: Context): android.app.Activity? {
    var current: Context? = context
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
