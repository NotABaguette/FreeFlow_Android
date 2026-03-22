package com.freeflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeflow.app.data.ConnectionStatus
import com.freeflow.app.data.LogEntry
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: MainViewModel) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Connection",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Session active"
                                ConnectionStatus.CONNECTING -> "Establishing session..."
                                ConnectionStatus.DISCONNECTED -> "No active session"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> FreeFlowGreen
                                ConnectionStatus.CONNECTING -> FreeFlowOrange
                                ConnectionStatus.DISCONNECTED -> DarkOnSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Logs", tint = DarkOnSurfaceVariant)
                    }
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
        ) {
            // Status card
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = FreeFlowBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatus() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = DarkOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "Ping",
                    icon = Icons.Default.NetworkPing,
                    onClick = { viewModel.ping() },
                    modifier = Modifier.weight(1f),
                    color = FreeFlowTeal
                )
                if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                    ActionButton(
                        text = "Connect",
                        icon = Icons.Default.Link,
                        onClick = { viewModel.connect() },
                        modifier = Modifier.weight(1f),
                        color = FreeFlowGreen
                    )
                } else {
                    ActionButton(
                        text = "Disconnect",
                        icon = Icons.Default.LinkOff,
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.weight(1f),
                        color = FreeFlowRed
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "Check Inbox",
                    icon = Icons.Default.Inbox,
                    onClick = { viewModel.pollMessages() },
                    modifier = Modifier.weight(1f),
                    color = FreeFlowBlue,
                    enabled = connectionStatus == ConnectionStatus.CONNECTED
                )
                ActionButton(
                    text = "Bulletin",
                    icon = Icons.Default.Newspaper,
                    onClick = { viewModel.fetchBulletin() },
                    modifier = Modifier.weight(1f),
                    color = FreeFlowOrange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = FreeFlowGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Protocol Log",
                    style = MaterialTheme.typography.titleMedium,
                    color = FreeFlowGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${logEntries.size} entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }

            // Log entries
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = Color(0xFF0A0E14),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (logEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No log entries yet. Try pinging the oracle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logEntries) { entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FreeFlowBlue,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color,
            disabledContainerColor = color.copy(alpha = 0.05f),
            disabledContentColor = color.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(entry.timestamp))
    val dirColor = if (entry.direction == ">>>") FreeFlowBlue else FreeFlowGreen

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = entry.direction,
            style = MaterialTheme.typography.labelSmall,
            color = dirColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurface.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
    }
}
