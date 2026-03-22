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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bulletins.sortedByDescending { it.id }) { bulletin ->
                    BulletinCard(bulletin)
                }
            }
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
