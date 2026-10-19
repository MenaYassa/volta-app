package com.example.volta.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.volta.model.GoogleAuthConfig
import com.example.volta.model.WhoAmIResponse
import com.example.volta.theme.VoltaAmber
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.viewmodel.VoltaViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.net.Uri
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    text: String = "Sign in with Google"
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("google_sign_in_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = VoltaBlue
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Authenticating with Volta...",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun GoogleUserProfileCard(
    whoAmI: WhoAmIResponse,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Avatar, Name, Email, Admin Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Profile Avatar Initials Circle
                    val initials = if (whoAmI.name.isNotBlank()) {
                        whoAmI.name.split(" ")
                            .filter { it.isNotBlank() }
                            .take(2)
                            .map { it.first().uppercaseChar() }
                            .joinToString("")
                    } else if (whoAmI.email.isNotBlank()) {
                        whoAmI.email.take(2).uppercase()
                    } else "V"

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(VoltaBlue.copy(alpha = 0.15f), CircleShape)
                            .border(1.5.dp, VoltaBlue, CircleShape)
                    ) {
                        Text(
                            text = initials,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = VoltaBlue
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = whoAmI.name.ifBlank { whoAmI.email.substringBefore("@") }.ifBlank { "Volta User" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (whoAmI.isAdmin) {
                                TagBadge(
                                    text = "ADMIN",
                                    color = VoltaAmber,
                                    bgColor = VoltaAmber.copy(alpha = 0.16f)
                                )
                            }
                        }
                        if (whoAmI.email.isNotBlank()) {
                            Text(
                                text = whoAmI.email,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Plan Tier Badge
                val isLifetime = whoAmI.expiresAt == null
                TagBadge(
                    text = if (isLifetime) "Lifetime / Free Tier" else if (whoAmI.subscriptionExpired) "Expired" else "Active Tier",
                    color = if (isLifetime) VoltaGreen else if (whoAmI.subscriptionExpired) VoltaRed else VoltaBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Details Grid: Quotas & Expiration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = VoltaBlue, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Claimed Strips:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${whoAmI.strips.size} of ${whoAmI.maxStrips} allocated",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (whoAmI.expiresAt == null) {
                    Text(
                        text = "✓ 1 Free Lifetime Strip",
                        fontSize = 11.sp,
                        color = VoltaGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sign Out / Switch Account Action
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltaRed),
                border = BorderStroke(1.dp, VoltaRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Out of Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun VoltaLoginCard(
    viewModel: VoltaViewModel,
    onSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by viewModel.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isGoogleLoading by remember { mutableStateOf(false) }
    var fallbackToken by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showWebAuthDialog by remember { mutableStateOf(false) }
    var showSha1Help by remember { mutableStateOf(false) }

    // Google Sign-In Activity Result Launcher with robust result unpacking
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                val email = account.email ?: "your Google account"
                if (!idToken.isNullOrBlank()) {
                    isGoogleLoading = true
                    viewModel.signInWithGoogle(idToken) { authRes ->
                        isGoogleLoading = false
                        authRes.onSuccess {
                            Toast.makeText(context, "Welcome, ${it.name.ifBlank { email }}!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "Volta authentication failed"
                            Toast.makeText(context, "Sign-in error: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    isGoogleLoading = false
                    showSha1Help = true
                    errorMessage = "Signed in as $email, but Google Play Services did not return an ID token for the Web Client ID.\n\nTap 'Sign In with Google (Web Direct)' below for instant one-tap login."
                    Toast.makeText(context, "Use Web Direct Sign-In below", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                isGoogleLoading = false
                val code = e.statusCode
                if (code == 10 || code == 12500) {
                    showSha1Help = true
                    errorMessage = "Google Play Services Error $code (Developer Configuration): Ensure Android Client ID (${GoogleAuthConfig.ANDROID_CLIENT_ID}) matches package '${context.packageName}' and SHA-1. You can use 'Sign In with Google (Web Direct)' below immediately without waiting."
                } else if (code == 12501) {
                    // Canceled by user
                    errorMessage = null
                } else {
                    errorMessage = "Google Sign-In failed (Code $code): ${e.message}"
                }
                if (errorMessage != null) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isGoogleLoading = false
                errorMessage = "Google Sign-In error: ${e.message}"
                Toast.makeText(context, "Sign-In error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            isGoogleLoading = false
            if (result.resultCode != Activity.RESULT_OK) {
                errorMessage = "Google account picker was dismissed."
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = VoltaBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Sign In & Onboarding",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sign in with your Google account as the primary method to claim strips, unlock multi-device sync, and manage hardware safety.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Method: Google Sign-In Button (Play Services)
            GoogleSignInButton(
                isLoading = isGoogleLoading,
                onClick = {
                    isGoogleLoading = true
                    errorMessage = null
                    viewModel.fetchGoogleClientId { clientId ->
                        try {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(clientId)
                                .requestEmail()
                                .requestProfile()
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            // Crucial: sign out from GoogleSignInClient first so cached account without ID token is invalidated
                            client.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(client.signInIntent)
                            }
                        } catch (e: Exception) {
                            isGoogleLoading = false
                            errorMessage = "Google Sign In unavailable: ${e.message}"
                            Toast.makeText(context, "Google sign in error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Alternative: In-App Web Sign-In (Direct Google Identity Services)
            OutlinedButton(
                onClick = { showWebAuthDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.2.dp, VoltaBlue.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_web_sign_in_button")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sign In with Google (Web Direct)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = VoltaRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VoltaRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠ $errorMessage",
                        color = VoltaRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Google Cloud Console Configuration / Error 10 Helper Card
            if (showSha1Help) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = VoltaAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VoltaAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = VoltaAmber, modifier = Modifier.size(18.dp))
                            Text("Google Cloud Configuration Notice", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VoltaAmber)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "OAuth Configuration in Google Cloud Console project ${GoogleAuthConfig.PROJECT_ID}:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Package: ${context.packageName}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("SHA-1: ${GoogleAuthConfig.SHA1_FINGERPRINT}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Android Client: ${GoogleAuthConfig.ANDROID_CLIENT_ID}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Web Client: ${GoogleAuthConfig.WEB_CLIENT_ID}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(
                                        AnnotatedString("Package: ${context.packageName}\nSHA-1: ${GoogleAuthConfig.SHA1_FINGERPRINT}\nProject: ${GoogleAuthConfig.PROJECT_ID}")
                                    )
                                    Toast.makeText(context, "Credentials copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Info", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { showWebAuthDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Use Web Sign-In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Divider: OR ENTER TOKEN (FALLBACK)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "  OR ENTER USER TOKEN (FALLBACK)  ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Token input fallback
            val trimmed = fallbackToken.trim()
            val isValidStandard = trimmed.startsWith("volta_usr_")
            val isValidLegacy = trimmed.startsWith("voltra_usr_")
            OutlinedTextField(
                value = fallbackToken,
                onValueChange = { fallbackToken = it },
                label = { Text("Volta User Token") },
                placeholder = { Text("volta_usr_...") },
                supportingText = {
                    when {
                        trimmed.isBlank() -> Text("Standard prefix: volta_usr_... (or legacy voltra_usr_...)")
                        isValidStandard -> Text("✓ Standard Volta token", color = VoltaGreen, fontWeight = FontWeight.SemiBold)
                        isValidLegacy -> Text("✓ Legacy Volta token recognized", color = VoltaGreen, fontWeight = FontWeight.SemiBold)
                        else -> Text("Token should start with volta_usr_...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fallback_user_token_input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (trimmed.isNotBlank()) {
                        viewModel.saveToken(trimmed)
                        Toast.makeText(context, "Token saved & applying...", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
                },
                enabled = trimmed.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_token_fallback_button")
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign In with Token")
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    try {
                        val portalUrl = settings.serverUrl.ifBlank { "https://volta.03092017.xyz" }
                        val intent = Intent(Intent.ACTION_VIEW, portalUrl.toUri())
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = VoltaBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Web Portal in Chrome", fontSize = 12.sp, color = VoltaBlue)
                }
            }
        }
    }

    if (showWebAuthDialog) {
        WebGoogleSignInDialog(
            viewModel = viewModel,
            serverUrl = settings.serverUrl,
            onTokenReceived = { token ->
                showWebAuthDialog = false
                viewModel.saveToken(token)
                Toast.makeText(context, "Google authentication successful!", Toast.LENGTH_SHORT).show()
                onSuccess()
            },
            onDismiss = { showWebAuthDialog = false }
        )
    }
}

@Composable
fun VoltaSignInDialog(
    viewModel: VoltaViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sign In to Volta", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            VoltaLoginCard(
                viewModel = viewModel,
                onSuccess = onDismiss
            )
        },
        confirmButton = {}
    )
}
