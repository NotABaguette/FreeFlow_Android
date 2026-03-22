package com.freeflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeflow.app.data.ChatMessage
import com.freeflow.app.data.MessageStatus
import com.freeflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    val bubbleColor = if (isOutgoing) SentBubbleColor else ReceivedBubbleColor
    val textColor = if (isOutgoing) SentTextColor else ReceivedTextColor
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 80.dp, max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (message.status) {
                                MessageStatus.SENDING -> "\u2022"
                                MessageStatus.SENT -> "\u2713"
                                MessageStatus.DELIVERED -> "\u2713\u2713"
                                MessageStatus.FAILED -> "\u2717"
                            },
                            color = when (message.status) {
                                MessageStatus.FAILED -> FreeFlowRed
                                MessageStatus.DELIVERED -> FreeFlowGreen
                                else -> textColor.copy(alpha = 0.6f)
                            },
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
