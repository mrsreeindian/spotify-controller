package com.spotify.controller.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.remote.SpotifyAuthManager
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    credentials: SpotifyCredentials,
    onSave: (clientId: String, secret: String, directToken: String) -> Unit,
    onStartOAuth: (clientId: String) -> Unit,
    onTestConnection: suspend (clientId: String, secret: String, directToken: String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val albumColors = LocalAlbumArtColors.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf(credentials.clientId) }
    var clientSecret by remember { mutableStateOf(credentials.clientSecret) }
    var directToken by remember { mutableStateOf(credentials.directDevToken) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = albumColors.tonalPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = "Setup",
                                tint = albumColors.tonalOnPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Spotify Setup",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect with Spotify Developer Platform",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Guide Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "How to connect:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FilledTonalButton(
                                onClick = { uriHandler.openUri("https://developer.spotify.com/dashboard") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInBrowser,
                                    contentDescription = "Open Dashboard",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dev Portal", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Open Spotify Developer Dashboard and click 'Create App'.\n" +
                                    "2. In App Settings, set Redirect URI to:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Redirect URI chip with copy button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = SpotifyAuthManager.REDIRECT_URI,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = albumColors.tonalPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Redirect URI", SpotifyAuthManager.REDIRECT_URI))
                                    Toast.makeText(context, "Copied Redirect URI!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy URI",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "3. Copy your Client ID and paste below.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input: Client ID
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Spotify Client ID (Required)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = albumColors.tonalPrimary,
                        focusedLabelColor = albumColors.tonalPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Input: Client Secret (Optional)
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client Secret (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = albumColors.tonalPrimary,
                        focusedLabelColor = albumColors.tonalPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Input: Direct Developer Token (Optional)
                OutlinedTextField(
                    value = directToken,
                    onValueChange = { directToken = it },
                    label = { Text("Or Direct Access Token (Optional Dev Testing)") },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = albumColors.tonalPrimary,
                        focusedLabelColor = albumColors.tonalPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Test Status Message
                if (testStatus != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (testIsSuccess) Color(0xFF1B3822) else Color(0xFF3E1F1F),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (testIsSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (testIsSuccess) Color(0xFF70F891) else Color(0xFFFF8A80),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = testStatus.orEmpty(),
                            fontSize = 13.sp,
                            color = if (testIsSuccess) Color(0xFF70F891) else Color(0xFFFF8A80)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                // Button 1: Authorize via Spotify Login (OAuth PKCE)
                Button(
                    onClick = {
                        val cleanId = clientId.trim()
                        val cleanSecret = clientSecret.trim()
                        val cleanDirect = SpotifyCredentials.cleanToken(directToken)
                        clientId = cleanId
                        clientSecret = cleanSecret
                        directToken = cleanDirect
                        onSave(cleanId, cleanSecret, cleanDirect)
                        if (cleanId.isNotBlank()) {
                            onStartOAuth(cleanId)
                        } else {
                            Toast.makeText(context, "Please enter your Spotify Client ID first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = albumColors.tonalPrimary,
                        contentColor = albumColors.tonalOnPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Authorize with Spotify Login", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button 2: Test Connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatus = null
                                val cleanId = clientId.trim()
                                val cleanSecret = clientSecret.trim()
                                val cleanDirect = SpotifyCredentials.cleanToken(directToken)
                                clientId = cleanId
                                clientSecret = cleanSecret
                                directToken = cleanDirect

                                val result = onTestConnection(cleanId, cleanSecret, cleanDirect)
                                result.onSuccess { message ->
                                    testIsSuccess = true
                                    testStatus = message
                                    // Auto-save on successful test
                                    onSave(cleanId, cleanSecret, cleanDirect)
                                }.onFailure { err ->
                                    testIsSuccess = false
                                    testStatus = err.message ?: "Connection test failed."
                                }
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text("Test Connection")
                        }
                    }

                    // Button 3: Save & Close
                    OutlinedButton(
                        onClick = {
                            val cleanId = clientId.trim()
                            val cleanSecret = clientSecret.trim()
                            val cleanDirect = SpotifyCredentials.cleanToken(directToken)
                            onSave(cleanId, cleanSecret, cleanDirect)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save & Close")
                    }
                }
            }
        }
    }
}
