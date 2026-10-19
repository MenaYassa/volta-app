package com.example.volta.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volta.model.WhoAmIResponse
import com.example.volta.theme.VoltaBlue
import com.example.volta.theme.VoltaGreen
import com.example.volta.theme.VoltaRed
import com.example.volta.ui.components.GoogleSignInButton
import com.example.volta.ui.components.VoltaSignInDialog
import com.example.volta.viewmodel.VoltaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountExpiredScreen(
    viewModel: VoltaViewModel,
    whoAmI: WhoAmIResponse?,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    var showTokenDialog by remember { mutableStateOf(false) }
    var newTokenInput by remember { mutableStateOf("") }
    var showContactDialog by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }

    val formattedExpiry = remember(whoAmI?.expiresAt) {
        whoAmI?.expiresAt?.let { ts ->
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.format(Date(ts * 1000L))
        } ?: "Unknown Date"
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            icon = { Icon(Icons.Default.ContactSupport, contentDescription = null, tint = VoltaBlue) },
            title = { Text("Contact Administrator", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("To renew your Volta plan or increase your device limits, please contact your system administrator or hosting provider.")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Server: ${settings.serverUrl}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Account: ${whoAmI?.name.orEmpty().ifBlank { whoAmI?.email.orEmpty().ifBlank { "User Token" } }}", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showContactDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = VoltaBlue) },
            title = { Text("Update Access Token", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your renewed Volta token (`volta_usr_...` or legacy `voltra_usr_...`):")
                    OutlinedTextField(
                        value = newTokenInput,
                        onValueChange = { newTokenInput = it },
                        label = { Text("New Token") },
                        placeholder = { Text("volta_usr_...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("renewed_token_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTokenInput.trim()
                        if (clean.isNotBlank()) {
                            viewModel.updateSettings(
                                settings.copy(
                                    serverToken = clean,
                                    isStandalone = false
                                )
                            )
                            showTokenDialog = false
                            viewModel.refreshWhoAmI()
                            viewModel.refresh()
                        }
                    },
                    enabled = newTokenInput.trim().isNotBlank(),
                    modifier = Modifier.testTag("apply_renewed_token_button")
                ) {
                    Text("Apply & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("account_expired_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Lock / Warning Hero Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(VoltaRed.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = VoltaRed,
                    modifier = Modifier.size(42.dp)
                )
            }

            // Lockout Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Account Expired / Suspended",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your Volta cloud subscription has ended or was suspended. Device telemetry sync and remote control are temporarily locked.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // Subscription Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subscription Status",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TagBadge(
                            text = (whoAmI?.status ?: "EXPIRED").uppercase(),
                            color = VoltaRed,
                            bgColor = VoltaRed.copy(alpha = 0.15f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Account Name", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = whoAmI?.name?.ifBlank { "Volta User" } ?: "Volta User",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (whoAmI?.expiresAt != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Expired On", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formattedExpiry,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoltaRed
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Strips Quota", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${whoAmI?.strips?.size ?: 0} of ${whoAmI?.maxStrips ?: 10} strips",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Option: Sign in with Google (Switch Account)
                GoogleSignInButton(
                    onClick = { showSignInDialog = true },
                    text = "Sign in with Google (Switch Account)"
                )

                Button(
                    onClick = { showTokenDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("renew_token_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VoltaBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Renewed Token", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showContactDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_admin_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContactSupport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Contact Administrator to Renew")
                }

                // Fallback: Switch to Local Controller Standalone Mode
                OutlinedButton(
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(isStandalone = true)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("switch_to_standalone_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Standalone / Local LAN Mode")
                }

                TextButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_lock_button")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = VoltaRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out of Account", color = VoltaRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showSignInDialog) {
        VoltaSignInDialog(
            viewModel = viewModel,
            onDismiss = {
                showSignInDialog = false
                viewModel.refreshWhoAmI()
            }
        )
    }
}
