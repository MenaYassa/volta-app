package com.example.volta.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import androidx.core.net.toUri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.volta.model.GoogleAuthConfig
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.viewmodel.VoltaViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder

enum class WebAuthMode {
    DIRECT_GOOGLE,
    PORTAL
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebGoogleSignInDialog(
    viewModel: VoltaViewModel,
    serverUrl: String,
    onTokenReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(WebAuthMode.DIRECT_GOOGLE) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var isExchangingToken by remember { mutableStateOf(false) }
    var exchangeStatus by remember { mutableStateOf<String?>(null) }
    var manualTokenInput by remember { mutableStateOf("") }

    val portalBaseUrl = remember(serverUrl) {
        val clean = serverUrl.trim().trimEnd('/')
        if (clean.isNotBlank()) clean else GoogleAuthConfig.REDIRECT_URI
    }

    val currentTargetUrl = remember(authMode, portalBaseUrl) {
        if (authMode == WebAuthMode.DIRECT_GOOGLE) {
            GoogleAuthConfig.getGoogleOAuthUrl()
        } else {
            portalBaseUrl
        }
    }

    fun handleCapturedIdToken(idToken: String) {
        if (isExchangingToken) return
        isExchangingToken = true
        exchangeStatus = "Verifying Google ID token with Volta server..."
        viewModel.signInWithGoogle(idToken) { result ->
            isExchangingToken = false
            result.onSuccess { auth ->
                exchangeStatus = "✓ Welcome, ${auth.name}!"
                Toast.makeText(context, "Welcome, ${auth.name}!", Toast.LENGTH_SHORT).show()
                onTokenReceived(auth.token)
            }.onFailure { err ->
                exchangeStatus = "Volta verification failed: ${err.message}"
                Toast.makeText(context, "Auth error: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun checkAndExtractAuthUrl(url: String): Boolean {
        if (url.isBlank()) return false
        try {
            // Check for id_token in hash fragment or query
            if (url.contains("id_token=")) {
                val match = Regex("""id_token=([^&]+)""").find(url)
                if (match != null) {
                    val raw = match.groupValues[1]
                    val decoded = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                    if (decoded.isNotBlank()) {
                        handleCapturedIdToken(decoded)
                        return true
                    }
                }
            }

            // Check for volta token in query or fragment
            if (url.contains("token=") || url.contains("volta_token=")) {
                val match = Regex("""(token|volta_token)=([^&]+)""").find(url)
                if (match != null) {
                    val raw = match.groupValues[2]
                    val decoded = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                    if (decoded.startsWith("volta_") || decoded.startsWith("voltra_")) {
                        onTokenReceived(decoded)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
        return false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (authMode == WebAuthMode.DIRECT_GOOGLE) "Google Direct Sign-In" else "Volta Web Portal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authMode == WebAuthMode.DIRECT_GOOGLE) "OAuth 2.0 Web Client" else portalBaseUrl,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, currentTargetUrl.toUri())
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Mode Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = authMode == WebAuthMode.DIRECT_GOOGLE,
                        onClick = {
                            authMode = WebAuthMode.DIRECT_GOOGLE
                            webViewInstance?.loadUrl(GoogleAuthConfig.getGoogleOAuthUrl())
                        },
                        label = { Text("Direct Google OAuth (Instant)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = authMode == WebAuthMode.PORTAL,
                        onClick = {
                            authMode = WebAuthMode.PORTAL
                            webViewInstance?.loadUrl(portalBaseUrl)
                        },
                        label = { Text("Web Portal", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                // Loading Progress Bar
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = VoltaBlue,
                    )
                }

                // Status Banner (if exchanging token)
                if (exchangeStatus != null || isExchangingToken) {
                    Surface(
                        color = if (isExchangingToken) VoltaBlue.copy(alpha = 0.12f) else VoltaGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isExchangingToken) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = VoltaBlue)
                            }
                            Text(
                                text = exchangeStatus ?: "Signing in...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // In-App WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportMultipleWindows(true)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                // Use modern Chrome mobile user agent so Google accounts sign-in flow isn't restricted
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                                class VoltaTokenBridge {
                                    @JavascriptInterface
                                    fun onTokenReceived(token: String) {
                                        val clean = token.trim()
                                        if (clean.isNotBlank()) {
                                            post {
                                                onTokenReceived(clean)
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onCredentialReceived(credential: String) {
                                        val clean = credential.trim()
                                        if (clean.isNotBlank()) {
                                            post {
                                                handleCapturedIdToken(clean)
                                            }
                                        }
                                    }
                                }

                                addJavascriptInterface(VoltaTokenBridge(), "VoltaBridge")

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                    ): Boolean {
                                        val newWebView = WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                    val url = request?.url?.toString() ?: ""
                                                    if (checkAndExtractAuthUrl(url)) return true
                                                    return false
                                                }

                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    if (url != null && checkAndExtractAuthUrl(url)) return
                                                    super.onPageStarted(view, url, favicon)
                                                }
                                            }
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = newWebView
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        if (checkAndExtractAuthUrl(url)) return true
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        if (url != null && checkAndExtractAuthUrl(url)) return
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        if (url != null && checkAndExtractAuthUrl(url)) return

                                        // Inject script to monitor localStorage for tokens and hook Google Identity callback
                                        val injectJs = """
                                            (function() {
                                                // Monitor localStorage
                                                var checkToken = function() {
                                                    try {
                                                        var tok = localStorage.getItem('volta_token') || localStorage.getItem('voltra_token');
                                                        if (tok && tok.length > 5 && window.VoltaBridge) {
                                                            window.VoltaBridge.onTokenReceived(tok);
                                                        }
                                                    } catch(e) {}
                                                };
                                                setInterval(checkToken, 500);
                                                checkToken();

                                                // Hook Google Identity Services credential response
                                                try {
                                                    if (typeof handleGoogleCredentialResponse === 'function') {
                                                        var origHandler = handleGoogleCredentialResponse;
                                                        window.handleGoogleCredentialResponse = function(response) {
                                                            if (response && response.credential && window.VoltaBridge) {
                                                                window.VoltaBridge.onCredentialReceived(response.credential);
                                                            }
                                                            return origHandler(response);
                                                        };
                                                    }
                                                } catch(e) {}
                                            })();
                                        """.trimIndent()
                                        evaluateJavascript(injectJs, null)
                                    }
                                }

                                loadUrl(currentTargetUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Quick Paste Token Fallback
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualTokenInput,
                            onValueChange = { manualTokenInput = it },
                            placeholder = { Text("Paste token if signed in via Chrome...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )

                        Button(
                            onClick = {
                                val input = manualTokenInput.trim()
                                if (input.isNotBlank()) {
                                    onTokenReceived(input)
                                } else {
                                    // Try reading from clipboard
                                    val clip = clipboardManager.getText()?.text?.trim() ?: ""
                                    if (clip.isNotBlank()) {
                                        manualTokenInput = clip
                                        onTokenReceived(clip)
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty. Paste a token first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
