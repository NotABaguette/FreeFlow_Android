package com.freeflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeflow.app.data.ChatMessage
import com.freeflow.app.data.ConnectionStatus
import com.freeflow.app.identity.Contact
import com.freeflow.app.ui.components.MessageBubble
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(viewModel: MainViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedContact by viewModel.selectedContact.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    if (selectedContact != null) {
        ChatDetailScreen(
            viewModel = viewModel,
            contact = selectedContact!!,
            onBack = { viewModel.selectContact(null) }
        )
    } else {
        ChatListScreen(
            viewModel = viewModel,
            contacts = contacts,
            messages = messages,
            connectionStatus = connectionStatus
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListScreen(
    viewModel: MainViewModel,
    contacts: List<Contact>,
    messages: List<ChatMessage>,
    connectionStatus: ConnectionStatus
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "FreeFlow",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Connected"
                                ConnectionStatus.CONNECTING -> "Connecting..."
                                ConnectionStatus.DISCONNECTED -> "Disconnected"
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
                    IconButton(onClick = { viewModel.pollMessages() }) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync Inbox",
                            tint = FreeFlowBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add a contact to start messaging",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(contacts) { contact ->
                    val contactMessages = messages.filter {
                        it.contactFingerprint == contact.fingerprintHex
                    }
                    val lastMessage = contactMessages.lastOrNull()

                    ContactListItem(
                        contact = contact,
                        lastMessage = lastMessage,
                        unreadCount = contactMessages.count { !it.isOutgoing && it.timestamp > System.currentTimeMillis() - 60000 },
                        onClick = { viewModel.selectContact(contact) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactListItem(
    contact: Contact,
    lastMessage: ChatMessage?,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(FreeFlowBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = FreeFlowBlue,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkOnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (lastMessage != null) {
                        Text(
                            text = formatDate(lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastMessage?.text ?: contact.fingerprintHex.take(16),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = FreeFlowBlue,
                            contentColor = Color.White
                        ) {
                            Text("$unreadCount")
                        }
                    }
                }
            }
        }
    }
    Divider(
        modifier = Modifier.padding(start = 76.dp),
        color = DarkSurfaceVariant,
        thickness = 0.5.dp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailScreen(
    viewModel: MainViewModel,
    contact: Contact,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val chatMessages = messages.filter { it.contactFingerprint == contact.fingerprintHex }
        .sortedBy { it.timestamp }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            contact.fingerprintHex,
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = DarkSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Message", color = DarkOnSurfaceVariant)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FreeFlowBlue,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            cursorColor = FreeFlowBlue
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendMessage(contact.fingerprintHex, messageText.trim())
                                    messageText = ""
                                }
                            }
                        ),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(contact.fingerprintHex, messageText.trim())
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = FreeFlowBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (chatMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = FreeFlowGreen.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "End-to-end encrypted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Messages are encrypted with ChaCha20-Poly1305",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatMessages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "now"
        diff < 3600000 -> "${diff / 60000}m"
        diff < 86400000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
