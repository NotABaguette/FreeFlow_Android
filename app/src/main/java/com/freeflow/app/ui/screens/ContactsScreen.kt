package com.freeflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freeflow.app.identity.Contact
import com.freeflow.app.ui.theme.*
import com.freeflow.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: MainViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val identity by viewModel.identity.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showIdentityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Contacts",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showIdentityDialog = true }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = "My Identity", tint = FreeFlowBlue)
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = FreeFlowBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // My identity card
            item {
                identity?.let { id ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = FreeFlowBlue.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = FreeFlowBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Your Identity",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FreeFlowBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Name: ${id.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkOnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Fingerprint: ${id.fingerprintHex}",
                                style = MaterialTheme.typography.labelMedium,
                                color = FreeFlowBlue,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = DarkOnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No contacts yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = DarkOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(contacts) { contact ->
                ContactItem(
                    contact = contact,
                    onDelete = { viewModel.removeContact(contact.fingerprintHex) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, pubkey ->
                viewModel.addContact(name, pubkey)
                showAddDialog = false
            }
        )
    }

    if (showIdentityDialog) {
        identity?.let { id ->
            IdentityDialog(
                identity = id,
                onDismiss = { showIdentityDialog = false }
            )
        }
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(FreeFlowTeal.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = FreeFlowTeal,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.fingerprintHex,
                    style = MaterialTheme.typography.labelMedium,
                    color = DarkOnSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.publicKeyHex,
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = FreeFlowRed.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Contact") },
            text = { Text("Remove ${contact.name} from contacts?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = FreeFlowRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = DarkSurface
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = DarkSurfaceVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, pubkeyHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pubkey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Contact", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FreeFlowBlue,
                        cursorColor = FreeFlowBlue
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pubkey,
                    onValueChange = { pubkey = it },
                    label = { Text("Public Key (64 hex chars)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FreeFlowBlue,
                        cursorColor = FreeFlowBlue
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), pubkey.trim().lowercase()) },
                enabled = name.isNotBlank() && pubkey.trim().length == 64
            ) {
                Text("Add", color = FreeFlowBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun IdentityDialog(
    identity: com.freeflow.app.identity.Identity,
    onDismiss: () -> Unit
) {
    val pubkeyHex = identity.publicKey.joinToString("") { String.format("%02x", it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Your Identity", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Name", style = MaterialTheme.typography.labelMedium, color = DarkOnSurfaceVariant)
                Text(identity.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Fingerprint", style = MaterialTheme.typography.labelMedium, color = DarkOnSurfaceVariant)
                Text(
                    identity.fingerprintHex,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = FreeFlowBlue
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Public Key", style = MaterialTheme.typography.labelMedium, color = DarkOnSurfaceVariant)
                Text(
                    pubkeyHex,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = DarkOnSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Share your public key with contacts so they can message you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = FreeFlowBlue)
            }
        },
        containerColor = DarkSurface
    )
}
