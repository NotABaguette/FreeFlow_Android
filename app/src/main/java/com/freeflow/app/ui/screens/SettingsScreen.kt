package com.freeflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freeflow.app.data.AppSettings
import com.freeflow.app.data.EncodingMode
import com.freeflow.app.data.TransportMode
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val identity by viewModel.identity.collectAsState()

    var oracleDomain by remember(settings) { mutableStateOf(settings.oracleDomain) }
    var resolver by remember(settings) { mutableStateOf(settings.resolver) }
    var oraclePubkey by remember(settings) { mutableStateOf(settings.oraclePublicKeyHex) }
    var transport by remember(settings) { mutableStateOf(settings.transport) }
    var encoding by remember(settings) { mutableStateOf(settings.encoding) }
    var relayUrl by remember(settings) { mutableStateOf(settings.relayUrl) }
    var relayApiKey by remember(settings) { mutableStateOf(settings.relayApiKey) }
    var queryDelay by remember(settings) { mutableStateOf(settings.queryDelay.toString()) }
    var devMode by remember(settings) { mutableStateOf(settings.devMode) }
    var showRegenConfirm by remember { mutableStateOf(false) }

    fun save() {
        viewModel.updateSettings(
            AppSettings(
                oracleDomain = oracleDomain.trim(),
                resolver = resolver.trim(),
                oraclePublicKeyHex = oraclePubkey.trim().lowercase(),
                transport = transport,
                encoding = encoding,
                relayUrl = relayUrl.trim(),
                relayApiKey = relayApiKey.trim(),
                queryDelay = queryDelay.toLongOrNull() ?: 3000L,
                devMode = devMode
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Oracle Configuration
            SettingsSection(
                title = "Oracle Configuration",
                icon = Icons.Default.Dns
            ) {
                SettingsTextField(
                    label = "Domain",
                    value = oracleDomain,
                    onValueChange = { oracleDomain = it },
                    placeholder = "cdn-static-eu.net"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTextField(
                    label = "DNS Resolver",
                    value = resolver,
                    onValueChange = { resolver = it },
                    placeholder = "8.8.8.8"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsTextField(
                    label = "Oracle Public Key (hex)",
                    value = oraclePubkey,
                    onValueChange = { oraclePubkey = it },
                    placeholder = "64 hex characters",
                    mono = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transport
            SettingsSection(
                title = "Transport",
                icon = Icons.Default.SwapHoriz
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = transport == TransportMode.DNS,
                        onClick = { transport = TransportMode.DNS },
                        label = { Text("DNS") },
                        leadingIcon = if (transport == TransportMode.DNS) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FreeFlowBlue.copy(alpha = 0.2f),
                            selectedLabelColor = FreeFlowBlue
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = transport == TransportMode.HTTP,
                        onClick = { transport = TransportMode.HTTP },
                        label = { Text("HTTP Relay") },
                        leadingIcon = if (transport == TransportMode.HTTP) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FreeFlowBlue.copy(alpha = 0.2f),
                            selectedLabelColor = FreeFlowBlue
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (transport == TransportMode.HTTP) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        label = "Relay URL",
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        placeholder = "https://relay.example.com"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        label = "API Key",
                        value = relayApiKey,
                        onValueChange = { relayApiKey = it },
                        placeholder = "Optional"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encoding
            SettingsSection(
                title = "DNS Encoding",
                icon = Icons.Default.Code
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = encoding == EncodingMode.PROQUINT,
                        onClick = { encoding = EncodingMode.PROQUINT },
                        label = { Text("Proquint") },
                        leadingIcon = if (encoding == EncodingMode.PROQUINT) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FreeFlowGreen.copy(alpha = 0.2f),
                            selectedLabelColor = FreeFlowGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = encoding == EncodingMode.HEX,
                        onClick = { encoding = EncodingMode.HEX },
                        label = { Text("Hex") },
                        leadingIcon = if (encoding == EncodingMode.HEX) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FreeFlowGreen.copy(alpha = 0.2f),
                            selectedLabelColor = FreeFlowGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (encoding == EncodingMode.PROQUINT)
                        "Proquint: CVCVC words that evade DNS entropy detectors. Required for censored networks."
                    else
                        "Hex: Simple hex encoding. Only for uncensored networks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Query Delay
            SettingsSection(
                title = "Query Timing",
                icon = Icons.Default.Timer
            ) {
                SettingsTextField(
                    label = "Delay between queries (ms)",
                    value = queryDelay,
                    onValueChange = { queryDelay = it },
                    placeholder = "3000"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Recommended: 2000-3000ms for censored networks",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Identity
            SettingsSection(
                title = "Identity",
                icon = Icons.Default.Fingerprint
            ) {
                identity?.let { id ->
                    Text(
                        "Fingerprint: ${id.fingerprintHex}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = FreeFlowBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showRegenConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FreeFlowRed
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Regenerate Identity")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Warning: This will generate a new X25519 key pair. Contacts will need your new public key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FreeFlowRed.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dev Mode
            SettingsSection(
                title = "Developer",
                icon = Icons.Default.DeveloperMode
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Developer Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkOnSurface
                    )
                    Switch(
                        checked = devMode,
                        onCheckedChange = { devMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FreeFlowBlue,
                            checkedTrackColor = FreeFlowBlue.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FreeFlowBlue
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version info
            Text(
                "FreeFlow Android v1.0.0 | Protocol v2.1",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenConfirm = false },
            title = { Text("Regenerate Identity?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will create a new X25519 key pair. Your existing identity will be lost and contacts will need your new public key.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.regenerateIdentity()
                    showRegenConfirm = false
                }) {
                    Text("Regenerate", color = FreeFlowRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = FreeFlowBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkOnSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    mono: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = DarkOnSurfaceVariant.copy(alpha = 0.3f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = if (mono) {
            LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        } else {
            LocalTextStyle.current
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FreeFlowBlue,
            unfocusedBorderColor = DarkSurfaceVariant,
            cursorColor = FreeFlowBlue,
            focusedLabelColor = FreeFlowBlue
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
