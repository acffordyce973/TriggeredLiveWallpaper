package com.antigravity.triggeredwallpaper.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

class MapBridge(private val onMarkerMovedCallback: (Double, Double) -> Unit) {
	@JavascriptInterface
	fun onMarkerMoved(doubleLat: Double, doubleLng: Double) {
		onMarkerMovedCallback(doubleLat, doubleLng)
	}
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveGeofenceMap(
	doubleLatitude: Double,
	doubleLongitude: Double,
	floatRadiusMeters: Float,
	modifier: Modifier = Modifier,
	onLocationChanged: (Double, Double) -> Unit
) {
	var webViewInstance by remember { mutableStateOf<WebView?>(null) }
	var boolIsMapLoaded by remember { mutableStateOf(false) }

	LaunchedEffect(doubleLatitude, doubleLongitude) {
		if (boolIsMapLoaded) {
			webViewInstance?.evaluateJavascript(
				"setCoordinates($doubleLatitude, $doubleLongitude);",
				null
			)
		}
	}

	LaunchedEffect(floatRadiusMeters) {
		if (boolIsMapLoaded) {
			webViewInstance?.evaluateJavascript(
				"setRadius($floatRadiusMeters);",
				null
			)
		}
	}

	AndroidView(
		modifier = modifier
			.fillMaxWidth()
			.height(260.dp)
			.clip(RoundedCornerShape(12.dp))
			.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
		factory = { ctx ->
			val assetLoader = WebViewAssetLoader.Builder()
				.addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
				.build()

			WebView(ctx).apply {
				settings.apply {
					javaScriptEnabled = true
					domStorageEnabled = true
					allowFileAccess = false
					allowContentAccess = false
					mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
					cacheMode = WebSettings.LOAD_DEFAULT
					userAgentString = "TriggeredWallpaper/1.0 (Android; Contact: support@antigravity.com) " + userAgentString
				}

				webChromeClient = object : WebChromeClient() {
					override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
						Log.e("MapDebug", "JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
						return true
					}
				}

				addJavascriptInterface(
					MapBridge { doubleLat, doubleLng ->
						post {
							onLocationChanged(doubleLat, doubleLng)
						}
					},
					"AndroidBridge"
				)

				webViewClient = object : WebViewClient() {
					override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
						val uri = request?.url ?: return null
						val intercepted = assetLoader.shouldInterceptRequest(uri)
						if (intercepted != null) {
							return intercepted
						}
						return super.shouldInterceptRequest(view, request)
					}

					override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
						super.onReceivedError(view, request, error)
						Log.e("MapDebug", "Resource Error: ${request?.url} -> ${error?.description} (${error?.errorCode})")
					}

					override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
						super.onReceivedHttpError(view, request, errorResponse)
						Log.e("MapDebug", "HTTP Error: ${request?.url} -> ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")
					}

					override fun onPageFinished(view: WebView?, url: String?) {
						super.onPageFinished(view, url)
						Log.e("MapDebug", "onPageFinished: $url")
						boolIsMapLoaded = true
						evaluateJavascript(
							"setInitialLocation($doubleLatitude, $doubleLongitude, $floatRadiusMeters);",
							null
						)
					}
				}

				setOnTouchListener { v, event ->
					when (event.action) {
						MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
							v.parent?.requestDisallowInterceptTouchEvent(true)
						}
						MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
							v.parent?.requestDisallowInterceptTouchEvent(false)
						}
					}
					false
				}

				loadUrl("https://appassets.androidplatform.net/assets/map.html")
				webViewInstance = this
			}
		},
		update = { webView ->
			webViewInstance = webView
		}
	)
}
