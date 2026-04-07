package com.freeflow.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freeflow.app.data.Bulletin
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun parseTelegramNews(content: String): List<Pair<String, String>> {
    return content.split(" | ").mapNotNull { part ->
        val trimmed = part.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val colonIdx = trimmed.indexOf(": ")
        if (colonIdx in 1..39) {
            Pair(trimmed.substring(0, colonIdx), trimmed.substring(colonIdx + 2))
        } else {
            Pair("", trimmed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinsScreen(viewModel: MainViewModel) {
    val bulletins by viewModel.bulletins.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bulletins",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchBulletin() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fetch", tint = FreeFlowBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (bulletins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Newspaper,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No bulletins yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap refresh to fetch the latest bulletin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    FilledTonalButton(onClick = { viewModel.fetchBulletin() }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Bulletin")
                    }
                }
            }
        } else {
            val sorted = bulletins.sortedByDescending { it.id }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Latest News section
                sorted.firstOrNull()?.let { latest ->
                    item {
                        Text(
                            "Latest News",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Telegram feed from Bulletin #${latest.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                    val newsItems = parseTelegramNews(latest.content)
                    items(newsItems) { (channel, message) ->
                        NewsItemCard(channel = channel, message = message)
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = DarkOnSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Bulletin History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnSurface
                        )
                    }
                }

                items(sorted) { bulletin ->
                    BulletinCard(bulletin)
                }
            }
        }
    }
}

@Composable
private fun NewsItemCard(channel: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E2840)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (channel.isNotEmpty()) {
                Text(
                    text = channel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = FreeFlowBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface
            )
        }
    }
}

@Composable
private fun BulletinCard(bulletin: Bulletin) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = FreeFlowOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Bulletin #${bulletin.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkOnSurface
                    )
                }
                if (bulletin.verified) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = FreeFlowGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = FreeFlowGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = bulletin.content,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    .format(Date(bulletin.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}
